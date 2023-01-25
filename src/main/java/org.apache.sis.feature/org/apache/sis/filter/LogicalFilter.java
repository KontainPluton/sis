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

import org.apache.sis.internal.util.CollectionsExt;
import org.apache.sis.internal.util.UnmodifiableArrayList;
import org.apache.sis.util.ArgumentChecks;
import org.apache.sis.util.resources.Errors;
import org.opengis.filter.Filter;
import org.opengis.filter.LogicalOperator;
import org.opengis.filter.LogicalOperatorName;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;


/**
 * Logical filter (AND, OR) using an arbitrary number of operands.
 * Operands are other filters.
 *
 * @author  Johann Sorel (Geomatys)
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.2
 *
 * @param  <R>  the type of resources (e.g. {@link org.opengis.feature.Feature}) used as inputs.
 *
 * @since 1.1
 * @module
 */
abstract class LogicalFilter<R> extends FilterNode<R> implements LogicalOperator<R>, Optimization.OnFilter<R> {
    /**
     * For cross-version compatibility.
     */
    private static final long serialVersionUID = 3696645262873257479L;

    /**
     * The filter on which to apply the logical operator.
     */
    protected final Filter<? super R>[] operands;

    /**
     * Creates a new logical operator applied on the given operands.
     *
     * @param  op  operands of the new operator.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    LogicalFilter(final Collection<? extends Filter<? super R>> op) {
        ArgumentChecks.ensureNonEmpty("operands", op);
        operands = op.toArray(new Filter[op.size()]);
        if (operands.length < 2) {
            throw new IllegalArgumentException(Errors.format(Errors.Keys.TooFewArguments_2, 2, operands.length));
        }
        for (int i=0; i<operands.length; i++) {
            ArgumentChecks.ensureNonNullElement("operands", i, operands[i]);
        }
    }

    /**
     * Creates a new logical operator with the two given operands.
     * This method does not verify if the operands are non-null;
     * this check should be already done by the caller.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    LogicalFilter(final Filter<? super R> operand1, final Filter<? super R> operand2) {
        operands = new Filter[] {operand1, operand2};
    }

    /**
     * Creates a new logical operator of the same kind than this operator.
     *
     * @param  op  operands of the new operator.
     * @return the new operator.
     */
    protected abstract LogicalFilter<R> createSameType(Collection<? extends Filter<? super R>> op);

    /**
     * Returns a list containing all of the child filters of this object.
     */
    @Override
    public final List<Filter<? super R>> getOperands() {
        return UnmodifiableArrayList.wrap(operands);
    }

    /**
     * Returns the children of this node for displaying purposes.
     * This is used by {@link #toString()}, {@link #hashCode()} and {@link #equals(Object)} implementations.
     */
    @Override
    protected final Collection<?> getChildren() {
        return getOperands();
    }


    /**
     * The "And" operation (⋀).
     */
    static final class And<R> extends LogicalFilter<R> {
        /** For cross-version compatibility. */
        private static final long serialVersionUID = 152892064260384713L;

        /** Creates a new operator for the given operands. */
        And(final Collection<? extends Filter<? super R>> op) {
            super(op);
        }

        /** Creates a new operator for the two given operands. */
        And(final Filter<? super R> operand1, final Filter<? super R> operand2) {
            super(operand1, operand2);
        }

        /** Creates a new logical operator of the same kind than this operator. */
        @Override protected LogicalFilter<R> createSameType(Collection<? extends Filter<? super R>> op) {
            return new And<>(op);
        }

        /** Identification of the operation. */
        @Override public LogicalOperatorName getOperatorType() {
            return LogicalOperatorName.AND;
        }

        /** Symbol of the operation. */
        @Override protected char symbol() {
            return operands.length <= 2 ? '∧' : '⋀';
        }

        /** Executes the logical operation. */
        @Override public boolean test(final R object) {
            for (final Filter<? super R> filter : operands) {
                if (!filter.test(object)) {
                    return false;
                }
            }
            return true;
        }

        /** Tries to optimize this filter. */
        @Override public Filter<? super R> optimize(final Optimization optimization) {
            return optimize(optimization, Filter.include(), Filter.exclude());
        }
    }


    /**
     * The "Or" operation (⋁).
     */
    static final class Or<R> extends LogicalFilter<R> {
        /** For cross-version compatibility. */
        private static final long serialVersionUID = 3805785720811330282L;

        /** Creates a new operator for the given operands. */
        Or(final Collection<? extends Filter<? super R>> op) {
            super(op);
        }

        /** Creates a new operator for the two given operands. */
        Or(final Filter<? super R> operand1, final Filter<? super R> operand2) {
            super(operand1, operand2);
        }

        /** Creates a new logical operator of the same kind than this operator. */
        @Override protected LogicalFilter<R> createSameType(Collection<? extends Filter<? super R>> op) {
            return new Or<>(op);
        }

        /** Identification of the operation. */
        @Override public LogicalOperatorName getOperatorType() {
            return LogicalOperatorName.OR;
        }

        /** Symbol of the operation. */
        @Override protected char symbol() {
            return operands.length <= 2 ? '∨' : '⋁';
        }

        /** Executes the logical operation. */
        @Override public boolean test(final R object) {
            for (Filter<? super R> filter : operands) {
                if (filter.test(object)) {
                    return true;
                }
            }
            return false;
        }

        /** Tries to optimize this filter. */
        @Override public Filter<? super R> optimize(final Optimization optimization) {
            return optimize(optimization, Filter.exclude(), Filter.include());
        }
    }


    /**
     * The negation filter (¬).
     */
    static final class Not<R> extends FilterNode<R> implements LogicalOperator<R>, Optimization.OnFilter<R> {
        /** For cross-version compatibility. */
        private static final long serialVersionUID = -1296823195138427781L;

        /** The filter to negate. */
        private final Filter<? super R> operand;

        /** Creates a new operator. */
        Not(final Filter<? super R> operand) {
            ArgumentChecks.ensureNonNull("operand", operand);
            this.operand = operand;
        }

        /** Identification of the operation. */
        @Override public LogicalOperatorName getOperatorType() {
            return LogicalOperatorName.NOT;
        }

        /** Symbol of the operation. */
        @Override protected char symbol() {
            return '¬';
        }

        /** Returns the singleton expression tested by this operator. */
        @Override protected final Collection<?> getChildren() {
            return getOperands();
        }

        /** Returns the singleton filter used by this operation. */
        @Override public List<Filter<? super R>> getOperands() {
            return Collections.singletonList(operand);
        }

        /** Evaluates this filter on the given object. */
        @Override public boolean test(final R object) {
            return !operand.test(object);
        }

        /** Tries to optimize this filter. */
        @Override public Filter<? super R> optimize(final Optimization optimization) {
            final Filter<? super R> effective = optimization.apply(operand);
            if (effective == Filter.include()) return Filter.exclude();
            if (effective == Filter.exclude()) return Filter.include();
            if (effective instanceof Not<?>) {
                return ((Not<? super R>) effective).operand;            // NOT(NOT(C)) == C
            } else {
                /*
                 * TODO:
                 * NOT(EQUALS(A,B)) = NOT_EQUALS(A,B)
                 * NOT(NOT_EQUALS(A,B)) = EQUALS(A,B)
                 */
            }
            return (effective != operand) ? new Not<>(effective) : this;
        }
    }

    /**
     * Returns the filter to use with duplicated operands removed,
     * nested operations of the same type inlined, and literal values processed immediately.
     * If no simplification has been applied, then this method returns {@code this}.
     *
     * @param  optimization  the simplifications or optimizations to apply on this filter.
     * @param  ignore        the filter to ignore (literal "true" or "false").
     * @param  shortCircuit  the filter to use if found (literal "true" or "false").
     */
    final Filter<? super R> optimize(final Optimization optimization,
            final Filter<R> ignore, final Filter<R> shortCircuit)
    {
        boolean unchanged = true;               // Will be `false` if at least one simplification has been applied.
        final Class<?> inline = getClass();     // Filter class for which to expand operands in the optimized filter.
        final Collection<Filter<? super R>> effective = new LinkedHashSet<>();
        for (Filter<? super R> f : operands) {
            unchanged &= (f == (f = optimization.apply(f)));
            if (f == ignore) {
                unchanged = false;
            } else if (f == shortCircuit) {
                return shortCircuit;
            } else if (f.getClass() != inline) {
                unchanged &= effective.add(f);
            } else {
                unchanged = false;
                for (Filter<? super R> s : ((LogicalFilter<? super R>) f).operands) {
                    if (f != ignore) {
                        if (f == shortCircuit) {
                            return shortCircuit;
                        } else {
                            // No need to check for the type because `f` has already been inlined.
                            assert s.getClass() != inline;
                            effective.add(optimization.apply(s));
                        }
                    }
                }
            }
        }
        /*
         * Simplification after we finished to inline nested logical operators:
         *
         *     A AND NOT(A) = FALSE
         *     A OR  NOT(A) = TRUE
         */
        for (Filter<? super R> f : effective) {
            if (LogicalOperatorName.NOT.equals(f.getOperatorType())) {
                if (effective.containsAll(((LogicalOperator<?>) f).getOperands())) {
                    return shortCircuit;
                }
            }
        }
        /*
         * TODO:
         * - Replace BETWEEN(A,B,C) by A >= B and A <= C in order to allow optimizations below.
         * - Replace A >= B && A >= C by A >= MAX(B,C) with max evaluated immediately if possible.
         * - Same for MIN.
         * - Restore A >= B && A >= C as BETWEEN(A,B,C) where B or C may be MAX/MIN.
         */
        if (unchanged) {
            return this;
        }
        final Filter<? super R> c = CollectionsExt.singletonOrNull(effective);
        return (c != null) ? c : createSameType(effective);
    }
}
