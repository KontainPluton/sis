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
package org.apache.sis.internal.sql.feature;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.opengis.util.CodeList;
import org.apache.sis.internal.filter.FunctionNames;
import org.apache.sis.internal.filter.Visitor;

// Branch-dependent imports
import org.opengis.feature.Feature;
import org.opengis.filter.Filter;
import org.opengis.filter.Literal;
import org.opengis.filter.Expression;
import org.opengis.filter.ValueReference;
import org.opengis.filter.LogicalOperator;
import org.opengis.filter.LogicalOperatorName;
import org.opengis.filter.ComparisonOperatorName;
import org.opengis.filter.BinaryComparisonOperator;
import org.opengis.filter.SpatialOperatorName;
import org.opengis.filter.BetweenComparisonOperator;


/**
 * Converter from filters/expressions to the {@code WHERE} part of SQL statement.
 * This base class handles ANSI compliant SQL. Subclasses can add database-specific syntax.
 *
 * <p>As soon as a filter or expression is not supported by this interpreter, the writing
 * of the SQL statement stops and next filters operations will be executed with Java code.</p>
 *
 * <h2>Implementation notes</h2>
 * For now, we over-use parenthesis to ensure consistent operator priority.
 * In the future, we could evolve this component to provide more elegant transcription of filter groups.
 *
 * <h2>Thread-safety</h2>
 * Instances of this classes shall be unmodified after construction and thus thread-safe.
 * Information about the state of a conversion to SQL is stored in {@link SelectionClause}.
 *
 * @author  Alexis Manin (Geomatys)
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.1
 * @since   1.1
 * @module
 */
public class SelectionClauseWriter extends Visitor<Feature, SelectionClause> {
    /**
     * The default instance.
     */
    protected static final SelectionClauseWriter DEFAULT = new SelectionClauseWriter();

    /**
     * Creates a new converter from filters/expressions to SQL.
     */
    private SelectionClauseWriter() {
        setFilterHandler(LogicalOperatorName.AND, new Logic(" AND ", false));
        setFilterHandler(LogicalOperatorName.OR,  new Logic(" OR ",  false));
        setFilterHandler(LogicalOperatorName.NOT, new Logic( "NOT ", true));
        setFilterHandler(ComparisonOperatorName.PROPERTY_IS_EQUAL_TO,                 new Comparison(" = "));
        setFilterHandler(ComparisonOperatorName.PROPERTY_IS_NOT_EQUAL_TO,             new Comparison(" <> "));
        setFilterHandler(ComparisonOperatorName.PROPERTY_IS_GREATER_THAN,             new Comparison(" > "));
        setFilterHandler(ComparisonOperatorName.PROPERTY_IS_GREATER_THAN_OR_EQUAL_TO, new Comparison(" >= "));
        setFilterHandler(ComparisonOperatorName.PROPERTY_IS_LESS_THAN,                new Comparison(" < "));
        setFilterHandler(ComparisonOperatorName.PROPERTY_IS_LESS_THAN_OR_EQUAL_TO,    new Comparison(" <= "));
        setFilterHandler(ComparisonOperatorName.valueOf(FunctionNames.PROPERTY_IS_BETWEEN), (f,sql) -> {
            final BetweenComparisonOperator<Feature>  filter = (BetweenComparisonOperator<Feature>) f;
            /* Nothing to append */  if (write(sql, filter.getExpression()))    return;
            sql.append(" BETWEEN "); if (write(sql, filter.getLowerBoundary())) return;
            sql.append(" AND ");         write(sql, filter.getUpperBoundary());
        });
        setNullAndNilHandlers((filter, sql) -> {
            final List<Expression<? super Feature, ?>> expressions = filter.getExpressions();
            if (expressions.size() == 1) {
                write(sql, expressions.get(0));
                sql.append(" IS NULL");
            } else {
                sql.invalidate();
            }
        });
        /*
         * Spatial filters.
         */
        setFilterHandler(SpatialOperatorName.CONTAINS,   new Function(FunctionNames.ST_Contains));
        setFilterHandler(SpatialOperatorName.CROSSES,    new Function(FunctionNames.ST_Crosses));
        setFilterHandler(SpatialOperatorName.DISJOINT,   new Function(FunctionNames.ST_Disjoint));
        setFilterHandler(SpatialOperatorName.EQUALS,     new Function(FunctionNames.ST_Equals));
        setFilterHandler(SpatialOperatorName.INTERSECTS, new Function(FunctionNames.ST_Intersects));
        setFilterHandler(SpatialOperatorName.OVERLAPS,   new Function(FunctionNames.ST_Overlaps));
        setFilterHandler(SpatialOperatorName.TOUCHES,    new Function(FunctionNames.ST_Touches));
        setFilterHandler(SpatialOperatorName.WITHIN,     new Function(FunctionNames.ST_Within));
        /*
         * Expression visitor.
         */
        setExpressionHandler(FunctionNames.Add,      new Arithmetic(" + "));
        setExpressionHandler(FunctionNames.Subtract, new Arithmetic(" - "));
        setExpressionHandler(FunctionNames.Divide,   new Arithmetic(" / "));
        setExpressionHandler(FunctionNames.Multiply, new Arithmetic(" * "));
        setExpressionHandler(FunctionNames.Literal, (e,sql) -> sql.appendLiteral(((Literal<Feature,?>) e).getValue()));
        setExpressionHandler(FunctionNames.ValueReference, (e,sql) -> sql.appendColumnName((ValueReference<Feature,?>) e));
        // Filters created from Filter Encoding XML can specify "PropertyName" instead of "Value reference".
        setExpressionHandler("PropertyName", getExpressionHandler(FunctionNames.ValueReference));
    }

    /**
     * Creates a new converter initialized to the same handlers than the specified converter.
     * The given source is usually {@link #DEFAULT}.
     *
     * @param  source  the converter from which to copy the handlers.
     */
    protected SelectionClauseWriter(final SelectionClauseWriter source) {
        super(source, true, false);
    }

    /**
     * Creates a new converter of the same class than {@code this} and initialized with the same data.
     * This method is invoked before to remove handlers for functions that are unsupported on the target
     * database software.
     *
     * @return a converter initialized to a copy of {@code this}.
     */
    protected SelectionClauseWriter duplicate() {
        return new SelectionClauseWriter(this);
    }

    /**
     * Returns a writer without the functions that are unsupported by the database software.
     * If the database supports all functions, then this method returns {@code this}.
     * Otherwise it returns a copy of {@code this} with unsupported functions removed.
     * This method should be invoked at most once for a {@link Database} instance.
     *
     * @param  database  information about the database software.
     * @return a writer with unsupported functions removed.
     */
    final SelectionClauseWriter removeUnsupportedFunctions(final Database<?> database) {
        final Map<String,SpatialOperatorName> unsupported = new HashMap<>();
        try (Connection c = database.source.getConnection()) {
            final DatabaseMetaData metadata = c.getMetaData();
            /*
             * Get the names of all spatial functions for which a handler is registered.
             * All those handlers should be instances of `Function`, otherwise we do not
             * know how to determine whether the function is supported or not.
             */
            final boolean lowerCase = metadata.storesLowerCaseIdentifiers();
            final boolean upperCase = metadata.storesUpperCaseIdentifiers();
            for (final SpatialOperatorName type : SpatialOperatorName.values()) {
                final BiConsumer<Filter<Feature>, SelectionClause> function = getFilterHandler(type);
                if (function instanceof Function) {
                    String name = ((Function) function).name;
                    if (lowerCase) name = name.toLowerCase(Locale.US);
                    if (upperCase) name = name.toUpperCase(Locale.US);
                    unsupported.put(name, type);
                }
            }
            /*
             * Remove from above map all functions that are supported by the database.
             * This list is potentially large so we do not put those items in a map.
             */
            final String pattern = (lowerCase ? "st_%" : "ST\\_%").replace("\\", metadata.getSearchStringEscape());
            try (ResultSet r = metadata.getFunctions(database.catalogOfSpatialTables,
                                                     database.schemaOfSpatialTables, pattern))
            {
                while (r.next()) {
                    unsupported.remove(r.getString("FUNCTION_NAME"));
                }
            }
        } catch (SQLException e) {
            /*
             * If this exception happens before `unsupported` entries were removed,
             * this is equivalent to assuming that all functions are unsupported.
             */
            database.listeners.warning(e);
        }
        /*
         * Remaining functions are unsupported functions.
         */
        if (unsupported.isEmpty()) {
            return this;
        }
        final SelectionClauseWriter copy = duplicate();
        copy.removeFilterHandlers(unsupported.values());
        return copy;
    }

    /**
     * Invoked when an unsupported filter is found. The SQL string is marked as invalid and
     * may be truncated (later) to the length that it has the last time that it was valid.
     */
    @Override
    protected final void typeNotFound(CodeList<?> type, Filter<Feature> filter, SelectionClause sql) {
        sql.invalidate();
    }

    /**
     * Invoked when an unsupported expression is found. The SQL string is marked as invalid
     * and may be truncated (later) to the length that it has the last time that it was valid.
     */
    @Override
    protected final void typeNotFound(String type, Expression<Feature,?> expression, SelectionClause sql) {
        sql.invalidate();
    }

    /**
     * Executes the registered action for the given filter.
     *
     * <h4>Note on type safety</h4>
     * This method applies a theoretically unsafe cast, which is okay in the context of this class.
     * See <cite>Note on parameterized type</cite> section in {@link Visitor#visit(Filter, Object)}.
     *
     * @param  sql     where to write the result of all actions.
     * @param  filter  the filter for which to execute an action based on its type.
     * @return value of {@link SelectionClause#isInvalid} flag, for allowing caller to short-circuit.
     */
    @SuppressWarnings("unchecked")
    final boolean write(final SelectionClause sql, final Filter<? super Feature> filter) {
        visit((Filter<Feature>) filter, sql);
        return sql.isInvalid;
    }

    /**
     * Executes the registered action for the given expression.
     *
     * <h4>Note on type safety</h4>
     * This method applies a theoretically unsafe cast, which is okay in the context of this class.
     * See <cite>Note on parameterized type</cite> section in {@link Visitor#visit(Filter, Object)}.
     *
     * @param  sql         where to write the result of all actions.
     * @param  expression  the expression for which to execute an action based on its type.
     * @return value of {@link SelectionClause#isInvalid} flag, for allowing caller to short-circuit.
     */
    @SuppressWarnings("unchecked")
    private boolean write(final SelectionClause sql, final Expression<? super Feature, ?> expression) {
        visit((Expression<Feature, ?>) expression, sql);
        return sql.isInvalid;
    }

    /**
     * Writes the expressions of a filter as a binary operator.
     * The filter must have exactly two expressions, otherwise the SQL will be declared invalid.
     *
     * @param sql       where to append the SQL clause.
     * @param filter    the filter for which to append the expressions.
     * @param operator  the operator to write between the expressions.
     */
    protected final void writeBinaryOperator(final SelectionClause sql, final Filter<Feature> filter, final String operator) {
        writeParameters(sql, filter.getExpressions(), operator, true);
    }

    /**
     * Writes the parameters of a function or a binary operator.
     *
     * @param sql          where to append the SQL clause.
     * @param expressions  the expressions to write.
     * @param separator    the separator to insert between expression.
     * @param binary       whether the list of expressions shall contain exactly 2 elements.
     */
    private void writeParameters(final SelectionClause sql, final List<Expression<? super Feature, ?>> expressions,
                                 final String separator, final boolean binary)
    {
        final int n = expressions.size();
        if (binary && n != 2) {
            sql.invalidate();
            return;
        }
        // No check for n=0 because we want "()" in that case.
        sql.append('(');
        for (int i=0; i<n; i++) {
            if (i != 0) sql.append(separator);
            if (write(sql, expressions.get(i))) return;
        }
        sql.append(')');
    }




    /**
     * Handler for converting an {@code AND}, {@code OR} or {@code NOT} filter into SQL clauses.
     * The filter can contain an arbitrary amount of operands, all separated by the same keyword.
     * All operands are grouped between parenthesis.
     */
    private final class Logic implements BiConsumer<Filter<Feature>, SelectionClause> {
        /**
         * The {@code AND}, {@code OR} or {@code NOT} keyword.
         * Shall contain a trailing space and eventually a leading space.
         */
        private final String operator;

        /**
         * Whether this operator is the unary operator. In that case exactly one operand is expected
         * and the keyword will be written before the operand instead of between the operands.
         */
        private final boolean unary;

        /** Creates a handler using the given SQL keyword. */
        Logic(final String operator, final boolean unary) {
            this.operator = operator;
            this.unary    = unary;
        }

        /** Invoked when a logical filter needs to be converted to SQL clause. */
        @Override public void accept(final Filter<Feature> f, final SelectionClause sql) {
            final LogicalOperator<Feature> filter = (LogicalOperator<Feature>) f;
            final List<Filter<? super Feature>> operands = filter.getOperands();
            final int n = operands.size();
            if (unary ? (n != 1) : (n == 0)) {
                sql.invalidate();
            } else {
                if (unary) {
                    sql.append(operator);
                }
                sql.append('(');
                for (int i=0; i<n; i++) {
                    if (i != 0) sql.append(operator);
                    if (write(sql, operands.get(i))) return;
                }
                sql.append(')');
            }
        }
    }




    /**
     * Handler for converting {@code =}, {@code <}, {@code >}, {@code <=} or {@code >=} filter
     * into SQL clauses. The filter is expected to contain exactly two operands, otherwise the
     * SQL is declared invalid.
     */
    private final class Comparison implements BiConsumer<Filter<Feature>, SelectionClause> {
        /** The comparison operator symbol. */
        private final String operator;

        /** Creates a new handler for the given operator. */
        Comparison(final String operator) {
            this.operator = operator;
        }

        /** Invoked when a comparison needs to be converted to SQL clause. */
        @Override public void accept(final Filter<Feature> f, final SelectionClause sql) {
            final BinaryComparisonOperator<Feature> filter = (BinaryComparisonOperator<Feature>) f;
            if (filter.isMatchingCase()) {
                writeBinaryOperator(sql, filter, operator);
            } else {
                sql.invalidate();
            }
        }
    }




    /**
     * Handler for converting {@code +}, {@code -}, {@code *} or {@code /} filter into SQL clauses.
     * The filter is expected to contain exactly two operands, otherwise the SQL is declared invalid.
     */
    private final class Arithmetic implements BiConsumer<Expression<Feature,?>, SelectionClause> {
        /** The arithmetic operator symbol. */
        private final String operator;

        /** Creates a new handler for the given operator. */
        Arithmetic(final String operator) {
            this.operator = operator;
        }

        /** Invoked when an arithmetic expression needs to be converted to SQL clause. */
        @Override public void accept(final Expression<Feature,?> expression, final SelectionClause sql) {
            writeParameters(sql, expression.getParameters(), operator, true);
        }
    }




    /**
     * Appends a function name with an arbitrary number of parameters (potentially zero).
     * This method stops immediately if a parameter can not be expressed in SQL, leaving
     * the trailing part of the SQL in an invalid state.
     */
    private final class Function implements BiConsumer<Filter<Feature>, SelectionClause> {
        /** Name the function. */
        final String name;

        /** Creates a function of the given name. */
        Function(final String name) {
            this.name = name;
        }

        /** Writes the function as an SQL statement. */
        @Override public void accept(final Filter<Feature> filter, final SelectionClause sql) {
            sql.append(name);
            writeParameters(sql, filter.getExpressions(), ", ", false);
        }
    }
}
