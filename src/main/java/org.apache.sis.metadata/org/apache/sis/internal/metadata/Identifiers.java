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
package org.apache.sis.internal.metadata;

import java.util.Locale;
import org.opengis.metadata.Identifier;
import org.opengis.metadata.citation.Citation;
import org.opengis.util.InternationalString;
import org.apache.sis.internal.util.Constants;
import org.apache.sis.internal.util.CollectionsExt;
import org.apache.sis.metadata.iso.citation.Citations;
import org.apache.sis.util.CharSequences;
import org.apache.sis.util.Characters;
import org.apache.sis.util.Deprecable;
import org.apache.sis.util.Static;
import org.apache.sis.util.resources.Errors;


/**
 * Methods working on {@link Identifier} instances.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @since   1.2
 * @version 1.0
 * @module
 */
public final class Identifiers extends Static {
    /**
     * Do not allow instantiation of this class.
     */
    private Identifiers() {
    }

    /**
     * Returns {@code true} if the given code is {@code "EPSG"} while the codespace is {@code "IOGP"} or {@code "OGP"}
     * (ignoring case). This particular combination of code and codespace is handled in a special way.
     *
     * <p>This method can be used for identifying where in Apache SIS source code the relationship between
     * EPSG authority and IOGP code space is hard-coded.</p>
     *
     * @param  codeSpace  the identifier code space, or {@code null}.
     * @param  code       the identifier code, or {@code null}.
     * @return {@code true} if the given identifier is {@code "IOGP:EPSG"}.
     *
     * @see Citations#EPSG
     */
    public static boolean isEPSG(final String codeSpace, final String code) {
        return Constants.EPSG.equalsIgnoreCase(code) &&
              (Constants.IOGP.equalsIgnoreCase(codeSpace) || "OGP".equalsIgnoreCase(codeSpace) ||
               Constants.EPSG.equalsIgnoreCase(codeSpace));
        // "OGP" is a legacy abbreviation that existed before "IOGP".
    }

    /**
     * Return {@code true} if the given object is deprecated.
     */
    private static boolean isDeprecated(final Object object) {
        return (object instanceof Deprecable) && ((Deprecable) object).isDeprecated();
    }

    /**
     * Returns a "unlocalized" string representation of the given international string, or {@code null} if none
     * or if the string is deprecated. This method is used by {@link #getIdentifier(Citation, boolean)}, which
     * is why we don't want the localized string.
     */
    private static String toString(final InternationalString title) {
        return (title != null && !isDeprecated(title))
               ? CharSequences.trimWhitespaces(title.toString(Locale.ROOT)) : null;
    }

    /**
     * Infers an identifier from the given citation, or returns {@code null} if no identifier has been found.
     * This method removes leading and trailing {@linkplain Character#isWhitespace(int) whitespaces}.
     * See {@link Citations#getIdentifier(Citation)} for the public documentation of this method.
     *
     * <h4>Which method to use</h4>
     * Guidelines:
     * <ul>
     *   <li>For information purpose (e.g. some {@code toString()} methods), use {@code getIdentifier(…, false)}.</li>
     *   <li>For WKT formatting, use {@code getIdentifier(…, true)} in order to preserve formatting characters.</li>
     *   <li>For assigning a value to a {@code codeSpace} field, use {@link Citations#toCodeSpace(Citation)}.</li>
     * </ul>
     *
     * Use {@code toCodeSpace(…)} method when assigning values to be returned by methods like
     * {@link Identifier#getCodeSpace()}, since those values are likely to be compared without special
     * care about ignorable identifier characters. But if the intent is to format a more complex string
     * like WKT or {@code toString()}, then we suggest to use {@code getIdentifier(citation, true)} instead,
     * which will produce the same result but preserving the ignorable characters, which can be useful
     * for formatting purpose.
     *
     * @param  citation  the citation for which to get the identifier, or {@code null}.
     * @param  strict    {@code true} for returning a non-null value only if the identifier is a valid Unicode identifier.
     * @return a non-empty identifier for the given citation without leading or trailing whitespaces,
     *         or {@code null} if the given citation is null or does not declare any identifier or title.
     *
     * @see <a href="https://issues.apache.org/jira/browse/SIS-201">SIS-201</a>
     */
    public static String getIdentifier(final Citation citation, final boolean strict) {
        if (citation != null) {
            boolean isUnicode = false;      // Whether `identifier` is a Unicode identifier.
            String identifier = null;       // The best identifier found so far.
            String codeSpace  = null;       // Code space of the identifier, or null if none.
            for (final Identifier id : CollectionsExt.nonNull(citation.getIdentifiers())) {
                if (id != null && !isDeprecated(id)) {
                    final String candidate = CharSequences.trimWhitespaces(id.getCode());
                    if (candidate != null && !candidate.isEmpty()) {
                        /*
                         * For a non-empty identifier, verify if both the code and its codespace are valid
                         * Unicode identifiers. If a codespace exists, then the code does not need to begin
                         * with a "Unicode identifier start" (it may be a "Unicode identifier part").
                         */
                        String cs = CharSequences.trimWhitespaces(id.getCodeSpace());
                        if (cs == null || cs.isEmpty()) {
                            cs = null;
                            isUnicode = CharSequences.isUnicodeIdentifier(candidate);
                        } else {
                            isUnicode = CharSequences.isUnicodeIdentifier(cs);
                            if (isUnicode) for (int i = 0; i < candidate.length();) {
                                final int c = candidate.codePointAt(i);
                                if (!Character.isUnicodeIdentifierPart(c) &&
                                        (strict || (c != '.' && c != '-')))
                                {
                                    /*
                                     * Above special case for '.' and '-' characters is documented
                                     * in the public Citations.getIdentifier(Citation) method.
                                     */
                                    isUnicode = false;
                                    break;
                                }
                                i += Character.charCount(c);
                            }
                        }
                        /*
                         * If we found a Unicode identifier, we are done and we can exit the loop.
                         * Otherwise retain the first identifier and continue the search for Unicode identifier.
                         */
                        if (identifier == null || isUnicode) {
                            identifier = candidate;
                            codeSpace  = cs;
                            if (isUnicode) break;
                        }
                    }
                }
            }
            /*
             * If no identifier has been found, fallback on the first title or alternate title.
             * We search for alternate titles because ISO specification said that those titles
             * are often used for abbreviations. Again we give preference to Unicode identifiers,
             * which are typically alternate titles.
             */
            if (identifier == null) {
                identifier = toString(citation.getTitle());     // Whitepaces removed by toString(…).
                if (identifier != null) {
                    if (identifier.isEmpty()) {
                        identifier = null;
                    } else {
                        isUnicode = CharSequences.isUnicodeIdentifier(identifier);
                    }
                }
                if (!isUnicode) {
                    for (final InternationalString i18n : CollectionsExt.nonNull(citation.getAlternateTitles())) {
                        final String candidate = toString(i18n);
                        if (candidate != null && !candidate.isEmpty()) {
                            isUnicode = CharSequences.isUnicodeIdentifier(candidate);
                            if (identifier == null || isUnicode) {
                                identifier = candidate;
                                if (isUnicode) break;
                            }
                        }
                    }
                }
            }
            /*
             * Finished searching in the identifiers, title and alternate titles. If the identifier that
             * we found is not a valid Unicode identifier, we will return it only if the caller did not
             * asked for strictly valid Unicode identifier.
             */
            if (isUnicode || !strict) {
                if (codeSpace != null && !isEPSG(codeSpace, identifier)) {
                    return codeSpace + (strict ? '_' : Constants.DEFAULT_SEPARATOR) + identifier;
                } else {
                    return identifier;
                }
            }
        }
        return null;
    }

    /**
     * Returns {@code true} if the given identifier authority matches the given {@code authority}.
     * If one of the authority is null, then the comparison fallback on the given {@code codeSpace}.
     * If the code spaces are also null, then this method conservatively returns {@code false}.
     *
     * @param  identifier  the identifier to compare.
     * @param  authority   the desired authority, or {@code null}.
     * @param  codeSpace   the desired code space or {@code null}, used as a fallback if an authority is null.
     * @return {@code true} if the authority or code space (as a fallback only) matches.
     */
    private static boolean authorityMatches(final Identifier identifier, final Citation authority, final String codeSpace) {
        if (authority != null) {
            final Citation other = identifier.getAuthority();
            if (other != null) {
                return Citations.identifierMatches(authority, other);
            }
        }
        if (codeSpace != null) {
            final String other = identifier.getCodeSpace();
            if (other != null) {
                return CharSequences.equalsFiltered(codeSpace, other, Characters.Filter.UNICODE_IDENTIFIER, true);
            }
        }
        return false;
    }

    /**
     * Determines whether a match or mismatch is found between the two given collections of identifiers.
     * If any of the given collections is {@code null} or empty, then this method returns {@code null}.
     *
     * <p>According ISO 19162 (<cite>Well known text representation of coordinate reference systems</cite>),
     * {@linkplain org.apache.sis.referencing.AbstractIdentifiedObject#getIdentifiers() identifiers} should have precedence over
     * {@linkplain org.apache.sis.referencing.AbstractIdentifiedObject#getName() name} for identifying {@code IdentifiedObject}s,
     * at least in the case of {@linkplain org.apache.sis.referencing.operation.DefaultOperationMethod operation methods} and
     * {@linkplain org.apache.sis.parameter.AbstractParameterDescriptor parameters}.</p>
     *
     * @param  id1  the first collection of identifiers, or {@code null}.
     * @param  id2  the second collection of identifiers, or {@code null}.
     * @return {@code TRUE} or {@code FALSE} on match or mismatch respectively, or {@code null} if this method
     *         can not determine if there is a match or mismatch.
     */
    public static Boolean hasCommonIdentifier(final Iterable<? extends Identifier> id1,
                                              final Iterable<? extends Identifier> id2)
    {
        if (id1 != null && id2 != null) {
            boolean hasFound = false;
            for (final Identifier identifier : id1) {
                final Citation authority = identifier.getAuthority();
                final String   codeSpace = identifier.getCodeSpace();
                for (final Identifier other : id2) {
                    if (authorityMatches(identifier, authority, codeSpace)) {
                        if (CharSequences.equalsFiltered(identifier.getCode(), other.getCode(), Characters.Filter.UNICODE_IDENTIFIER, true)) {
                            return Boolean.TRUE;
                        }
                        hasFound = true;
                    }
                }
            }
            if (hasFound) {
                return Boolean.FALSE;
            }
        }
        return null;
    }

    /**
     * Returns a message saying that a property is missing for an object having the given identifier.
     *
     * @param  owner     identifier of the object for which a property is missing.
     * @param  property  name of the missing property.
     * @return a message saying that a value is missing for the given property in the specified identified object.
     *
     * @since 1.2
     */
    public static String missingValueForProperty(final Identifier owner, final String property) {
        return (owner == null)
                ? Errors.format(Errors.Keys.MissingValueForProperty_1, property)
                : Errors.format(Errors.Keys.MissingValueForProperty_2, owner, property);
    }
}
