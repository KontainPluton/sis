/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sis.filter;

import org.apache.sis.util.ArgumentChecks;
import org.opengis.filter.Expression;
import org.opengis.filter.Filter;
import org.opengis.filter.LikeOperator;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;


/**
 * A character string comparison operator with pattern matching.
 *
 * @author  Johann Sorel (Geomatys)
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.1
 *
 * @param  <R>  the type of resources (e.g. {@link org.opengis.feature.Feature}) used as inputs.
 *
 * @since 1.1
 * @module
 */
final class LikeFilter<R> extends FilterNode<R> implements LikeOperator<R>, Optimization.OnFilter<R> {
    /**
     * For cross-version compatibility.
     */
    private static final long serialVersionUID = 4074943825474742252L;

    /**
     * The source of values to compare against the pattern.
     */
    private final Expression<? super R, ?> expression;

    /**
     * The pattern to match against expression values. The {@link #wildcard}, {@link #singleChar}
     * and {@link #escape} characters have special meanings.
     */
    private final String pattern;

    /**
     * The pattern character for matching any sequence of characters.
     *
     * @see #getWildCard()
     */
    private final char wildcard;

    /**
     * The pattern character for matching exactly one character.
     *
     * @see #getSingleChar()
     */
    private final char singleChar;

    /**
     * The pattern character for indicating that the next character should be matched literally.
     *
     * @see #getEscapeChar()
     */
    private final char escape;

    /**
     * Specifies how a filter expression processor should perform string comparisons.
     *
     * @see #isMatchingCase()
     */
    private final boolean isMatchingCase;

    /**
     * The regular expression.
     */
    private final Pattern regex;

    /**
     * Creates a new operator.
     *
     * @param expression      source of values to compare against the pattern.
     * @param pattern         pattern to match against expression values.
     * @param wildcard        pattern character for matching any sequence of characters.
     * @param singleChar      pattern character for matching exactly one character.
     * @param escape          pattern character for indicating that the next character should be matched literally.
     * @param isMatchingCase  specifies how a filter expression processor should perform string comparisons.
     */
    LikeFilter(final Expression<? super R, ?> expression, final String pattern,
            final char wildcard, final char singleChar, final char escape, final boolean isMatchingCase)
    {
        ArgumentChecks.ensureNonNull("pattern", pattern);
        this.expression     = expression;
        this.pattern        = pattern;
        this.wildcard       = wildcard;
        this.singleChar     = singleChar;
        this.escape         = escape;
        this.isMatchingCase = isMatchingCase;
        /*
         * Creates a regular expression from the given pattern.
         */
        final int n = pattern.length();
        final StringBuilder sb = new StringBuilder(n*2);
        for (int i=0; i<n; i++) {
            char c = pattern.charAt(i);
            if (c == wildcard) {
                sb.append(".*");
                continue;
            } else if (c == singleChar) {
                sb.append('.');
                continue;
            } else if (c == escape) {
                if (++i >= n) break;
                c = pattern.charAt(i);
            }
            if (isMetaCharacter(c)) {
                sb.append('\\');
            }
            sb.append(c);
        }
        int flags = Pattern.CANON_EQ |          // Apply canonical decomposition before to match.
                    Pattern.UNICODE_CASE |      // Case matching according Unicode standard instead of ASCII.
                    Pattern.UNICODE_CHARACTER_CLASS;
        if (!isMatchingCase) {
            flags |= Pattern.CASE_INSENSITIVE;
        }
        regex = Pattern.compile(sb.toString(), flags);
    }

    /**
     * Creates a new filter of the same type but different parameters.
     */
    private LikeFilter(final LikeFilter<R> original, final Expression<? super R, ?> expression) {
        this.expression     = expression;
        this.pattern        = original.pattern;
        this.wildcard       = original.wildcard;
        this.singleChar     = original.singleChar;
        this.escape         = original.escape;
        this.isMatchingCase = original.isMatchingCase;
        this.regex          = original.regex;
    }

    /**
     * Creates a new filter of the same type but different parameters.
     */
    @Override
    public Filter<R> recreate(final Expression<? super R, ?>[] effective) {
        return new LikeFilter<>(this, effective[0]);
    }

    /**
     * Returns the children of this node for displaying purposes.
     * This is used by {@link #toString()}, {@link #hashCode()} and {@link #equals(Object)} implementations.
     */
    @Override
    protected Collection<?> getChildren() {
        // TODO: use List.of(…) in JDK9.
        return Arrays.asList(expression, pattern);
    }

    /**
     * Returns the expression whose values will be compared by this operator, together with the pattern.
     */
    @Override
    public List<Expression<? super R, ?>> getExpressions() {
        // TODO: use List.of(…) in JDK9.
        return Arrays.asList(expression, new LeafExpression.Literal<>(pattern));
    }

    /**
     * Returns the pattern character for matching any sequence of characters.
     * For the SQL "{@code LIKE}" operator, this property is the {@code %} character.
     */
    @Override
    public char getWildCard() {
        return wildcard;
    }

    /**
     * Returns the pattern character for matching exactly one character.
     * For the SQL "{@code LIKE}" operator, this property is the {@code _} character.
     */
    @Override
    public char getSingleChar() {
        return singleChar;
    }

    /**
     * Returns the pattern character for indicating that the next character should be matched literally.
     * For the SQL "{@code LIKE}" operator, this property is the {@code '} character.
     */
    @Override
    public char getEscapeChar() {
        return escape;
    }

    /**
     * Specifies how a filter expression processor should perform string comparisons.
     */
    @Override
    public boolean isMatchingCase() {
        return isMatchingCase;
    }

    /**
     * Returns {@code true} if the expression value computed from the given object matches the pattern.
     */
    @Override
    public boolean test(final R object) {
        final Object value = expression.apply(object);
        return (value != null) && regex.matcher(value.toString()).matches();
    }

    /**
     * Returns {@code true} if given character is a regular expression meta-character.
     */
    static boolean isMetaCharacter(final char c) {
        return (c == '.' || c == '?'  || c == '$') ||
               (c >= '[' && c <= '^') ||    //  [ \ ] ^
               (c >= '(' && c <= '+') ||    //  ( ) * +
               (c >= '{' && c <= '}');      //  { | }
    }
}
