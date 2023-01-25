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
package org.apache.sis.internal.metadata.sql;

import java.sql.SQLException;
import java.sql.SQLDataException;
import java.sql.DatabaseMetaData;
import org.apache.sis.util.Static;
import org.apache.sis.util.Characters;
import org.apache.sis.util.CharSequences;
import org.apache.sis.util.Workaround;
import org.apache.sis.util.resources.Errors;


/**
 * Utilities relative to the SQL language.
 *
 *     <strong>DO NOT USE</strong>
 *
 * This class is for Apache SIS internal usage and may change in any future version.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.0
 * @since   0.7
 * @module
 */
public final class SQLUtilities extends Static {
    /**
     * Do not allow instantiation of this class.
     */
    private SQLUtilities() {
    }

    /**
     * Returns a simplified form of the URL (truncated before the first {@code ?} or {@code ;} character),
     * for logging or informative purpose only.
     *
     * @param  metadata  the metadata of the database.
     * @return a simplified version of database URL.
     * @throws SQLException if an error occurred while fetching the URL.
     */
    public static String getSimplifiedURL(final DatabaseMetaData metadata) throws SQLException {
        String url = metadata.getURL();
        int s1 = url.indexOf('?'); if (s1 < 0) s1 = url.length();
        int s2 = url.indexOf(';'); if (s2 < 0) s2 = url.length();
        return url.substring(0, Math.min(s1, s2));
    }

    /**
     * Converts the given string to a boolean value, or returns {@code null} if the value is unrecognized.
     * This method recognizes "true", "false", "yes", "no", "t", "f", 0 and 1 (case insensitive).
     * An empty string is interpreted as {@code null}.
     *
     * @param  text  the characters to convert to a boolean value, or {@code null}.
     * @return the given characters as a boolean value, or {@code null} if the given text was null or empty.
     * @throws SQLDataException if the given text is non-null and non-empty but not recognized.
     *
     * @see Boolean#parseBoolean(String)
     *
     * @since 0.8
     */
    public static Boolean parseBoolean(final String text) throws SQLException {
        if (text == null) {
            return null;
        }
        switch (text.length()) {
            case 0: return null;
            case 1: {
                switch (text.charAt(0)) {
                    case '0': case 'n': case 'N': case 'f': case 'F': return Boolean.FALSE;
                    case '1': case 'y': case 'Y': case 't': case 'T': return Boolean.TRUE;
                }
                break;
            }
            default: {
                if (text.equalsIgnoreCase("true")  || text.equalsIgnoreCase("yes")) return Boolean.TRUE;
                if (text.equalsIgnoreCase("false") || text.equalsIgnoreCase("no"))  return Boolean.FALSE;
                break;
            }
        }
        throw new SQLDataException(Errors.format(Errors.Keys.CanNotConvertValue_2, text, Boolean.class));
    }

    /**
     * Returns the given pattern with {@code '_'} and {@code '%'} characters escaped by the database-specific
     * escape characters. This method should be invoked for escaping the values of all {@link DatabaseMetaData}
     * method arguments with a name ending by {@code "Pattern"}. Note that not all arguments are pattern; please
     * checks carefully {@link DatabaseMetaData} javadoc for each method.
     *
     * <div class="note"><b>Example:</b> if a method expects an argument named {@code tableNamePattern},
     * then that argument value should be escaped. But if the argument name is only {@code tableName},
     * then the value should not be escaped.</div>
     *
     * @param  pattern  the pattern to escape, or {@code null} if none.
     * @param  escape   value of {@link DatabaseMetaData#getSearchStringEscape()}.
     * @return escaped strings, or the same instance than {@code pattern} if there are no characters to escape.
     */
    public static String escape(final String pattern, final String escape) {
        if (pattern != null) {
            StringBuilder buffer = null;
            for (int i = pattern.length(); --i >= 0;) {
                final char c = pattern.charAt(i);
                if (c == '_' || c == '%') {
                    if (buffer == null) {
                        buffer = new StringBuilder(pattern);
                    }
                    buffer.insert(i, escape);
                }
            }
            if (buffer != null) {
                return buffer.toString();
            }
        }
        return pattern;
    }

    /**
     * Returns a SQL LIKE pattern for the given identifier. The identifier is optionally returned in all lower cases
     * for allowing case-insensitive searches. Punctuations are replaced by any sequence of characters ({@code '%'})
     * and non-ASCII letters or digits are replaced by any single character ({@code '_'}). This method avoid to put
     * a {@code '%'} symbol as the first character since it prevents some databases to use their index.
     *
     * @param  identifier   the identifier to get as a SQL LIKE pattern.
     * @param  i            index of the first character to use in the given {@code identifier}.
     * @param  end          index after the last character to use in the given {@code identifier}.
     * @param  allowSuffix  whether to append a final {@code '%'} wildcard at the end of the pattern.
     * @param  toLower      whether to convert characters to lower case.
     * @param  buffer       buffer where to append the SQL LIKE pattern.
     */
    public static void toLikePattern(final String identifier, int i, final int end,
            final boolean allowSuffix, final boolean toLower, final StringBuilder buffer)
    {
        final int bs = buffer.length();
        while (i < end) {
            final int c = identifier.codePointAt(i);
            if (Character.isLetterOrDigit(c)) {
                if (c < 128) {                      // Use only ASCII characters in the search.
                    buffer.appendCodePoint(toLower ? Character.toLowerCase(c) : c);
                } else {
                    appendIfNotRedundant(buffer, '_');
                }
            } else {
                final int length = buffer.length();
                if (length == bs) {
                    buffer.appendCodePoint(c != '%' ? c : '_');
                } else if (buffer.charAt(length - 1) != '%') {
                    buffer.append('%');
                }
            }
            i += Character.charCount(c);
        }
        if (allowSuffix) {
            appendIfNotRedundant(buffer, '%');
        }
        for (i=bs; (i = buffer.indexOf("_%", i)) >= 0;) {
            buffer.deleteCharAt(i);
        }
    }

    /**
     * Appends the given wildcard character to the given buffer if the buffer does not ends with {@code '%'}.
     */
    private static void appendIfNotRedundant(final StringBuilder buffer, final char wildcard) {
        final int length = buffer.length();
        if (length == 0 || buffer.charAt(length - 1) != '%') {
            buffer.append(wildcard);
        }
    }

    /**
     * Workaround for what seems to be a Derby 10.11 bug, which seems to behave as if the LIKE pattern
     * had a trailing % wildcard. This can be verified with the following query on the EPSG database:
     *
     * {@preformat sql
     *   SELECT COORD_REF_SYS_CODE, COORD_REF_SYS_NAME FROM EPSG."Coordinate Reference System"
     *    WHERE COORD_REF_SYS_NAME LIKE 'NTF%Paris%Lambert%zone%I'
     * }
     *
     * which returns "NTF (Paris) / Lambert zone I" as expected but also zones II and III.
     *
     * @param  expected  the string to search.
     * @param  actual    the string found in the database.
     * @return {@code true} if the given string can be accepted.
     */
    @Workaround(library = "Derby", version = "10.11")
    public static boolean filterFalsePositive(final String expected, final String actual) {
        return CharSequences.equalsFiltered(expected, actual, Characters.Filter.LETTERS_AND_DIGITS, false);
    }
}
