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
package org.apache.sis.internal.filter;

import org.apache.sis.util.Static;
import org.apache.sis.internal.filter.sqlmm.SQLMM;


/**
 * Names of some expressions used in Apache SIS.
 * This class defines only the names that need to be references from at least two different classes.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.1
 * @since   1.1
 * @module
 */
public final class FunctionNames extends Static {
    /** Value of {@link org.opengis.filter.NullOperator#getOperatorType()}. */
    public static final String PROPERTY_IS_NULL = "PROPERTY_IS_NULL";

    /** Value of {@link org.opengis.filter.NilOperator#getOperatorType()}. */
    public static final String PROPERTY_IS_NIL = "PROPERTY_IS_NIL";

    /** Value of {@link org.opengis.filter.LikeOperator#getOperatorType()}. */
    public static final String PROPERTY_IS_LIKE = "PROPERTY_IS_LIKE";

    /** Value of {@link org.opengis.filter.BetweenComparisonOperator#getOperatorType()}. */
    public static final String PROPERTY_IS_BETWEEN = "PROPERTY_IS_BETWEEN";

    /** Value of {@link org.opengis.filter.Literal#getFunctionName()}. */
    public static final String Literal = "Literal";

    /** Value of {@link org.opengis.filter.ValueReference#getFunctionName()}. */
    public static final String ValueReference = "ValueReference";

    /** The "Add" (+) arithmetic expression. */
    public static final String Add = "Add";

    /** The "Subtract" (−) arithmetic expression. */
    public static final String Subtract = "Subtract";

    /** The "Multiply" (×) arithmetic expression. */
    public static final String Multiply = "Multiply";

    /** The "Divide" (÷) arithmetic expression. */
    public static final String Divide = "Divide";

    /** Name of {@link SQLMM#ST_Contains}. */
    public static final String ST_Contains = "ST_Contains";

    /** Name of {@link SQLMM#ST_Crosses}. */
    public static final String ST_Crosses = "ST_Crosses";

    /** Name of {@link SQLMM#ST_Disjoint}. */
    public static final String ST_Disjoint = "ST_Disjoint";

    /** Name of {@link SQLMM#ST_Equals}. */
    public static final String ST_Equals = "ST_Equals";

    /** Name of {@link SQLMM#ST_Intersects}. */
    public static final String ST_Intersects = "ST_Intersects";

    /** Name of {@link SQLMM#ST_Overlaps}. */
    public static final String ST_Overlaps = "ST_Overlaps";

    /** Name of {@link SQLMM#ST_Touches}. */
    public static final String ST_Touches = "ST_Touches";

    /** Name of {@link SQLMM#ST_Within}. */
    public static final String ST_Within = "ST_Within";

    /**
     * Do not allow instantiation of this class.
     */
    private FunctionNames() {
    }
}
