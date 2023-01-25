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

import java.math.BigDecimal;
import java.math.BigInteger;
import org.opengis.util.ScopedName;
import org.apache.sis.feature.builder.FeatureTypeBuilder;
import org.apache.sis.feature.builder.PropertyTypeBuilder;
import org.apache.sis.internal.feature.FeatureExpression;
import org.apache.sis.internal.filter.FunctionNames;
import org.apache.sis.util.UnconvertibleObjectException;
import org.apache.sis.util.resources.Errors;
import org.apache.sis.math.Fraction;

// Branch-dependent imports
import org.opengis.feature.AttributeType;
import org.opengis.feature.FeatureType;
import org.opengis.filter.Expression;


/**
 * Arithmetic operations between two numerical values.
 * The nature of the operation depends on the subclass.
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
abstract class ArithmeticFunction<R> extends BinaryFunction<R,Number,Number>
        implements FeatureExpression<R,Number>, Optimization.OnExpression<R,Number>
{
    /**
     * For cross-version compatibility.
     */
    private static final long serialVersionUID = 2818625862630588268L;

    /**
     * Creates a new arithmetic function.
     */
    ArithmeticFunction(final Expression<? super R, ? extends Number> expression1,
                       final Expression<? super R, ? extends Number> expression2)
    {
        super(expression1, expression2);
    }

    /**
     * Creates an attribute type for numeric values of the given name.
     * The attribute is mandatory, unbounded and has no default value.
     *
     * @param  name  name of the attribute to create.
     * @return an attribute of the given name for numbers.
     */
    static AttributeType<Number> createNumericType(final String name) {
        return createType(Number.class, name);
    }

    /**
     * Returns the type of results computed by this arithmetic function.
     */
    protected abstract AttributeType<Number> expectedType();

    /**
     * Returns the type of values computed by this expression.
     */
    @Override
    public final Class<?> getValueClass() {
        return Number.class;
    }

    /**
     * Provides the type of results computed by this expression. That type depends only
     * on the {@code ArithmeticFunction} subclass and is given by {@link #expectedType()}.
     */
    @Override
    public final PropertyTypeBuilder expectedType(FeatureType ignored, FeatureTypeBuilder addTo) {
        return addTo.addProperty(expectedType());
    }

    /**
     * Evaluates the expression for producing a result of numeric type.
     * This method delegates to one of the {@code applyAs(…)} methods.
     * If no {@code applyAs(…)} implementations can return null values,
     * this this method never return {@code null}.
     */
    @Override
    public final Number apply(final R feature) {
        final Number left  = expression1.apply(feature);
        if (left != null) {
            final Number right = expression2.apply(feature);
            if (right != null) {
                return apply(left, right);
            }
        }
        return null;
    }

    /**
     * Returns {@code this} if this expression provides values of the specified type,
     * or otherwise returns an expression doing conversions on-the-fly.
     *
     * @throws ClassCastException if the specified type is not a supported target type.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <N> Expression<R,N> toValueType(final Class<N> target) {
        if (target.isAssignableFrom(Number.class)) {
            return (Expression<R,N>) this;
        } else try {
            return new ConvertFunction<>(this, Number.class, target);
        } catch (UnconvertibleObjectException e) {
            throw (ClassCastException) new ClassCastException(Errors.format(
                    Errors.Keys.CanNotConvertValue_2, getFunctionName(), target)).initCause(e);
        }
    }

    /**
     * The "Add" (+) expression.
     */
    static final class Add<R> extends ArithmeticFunction<R> {
        /** For cross-version compatibility during (de)serialization. */
        private static final long serialVersionUID = 5445433312445869201L;

        /** Description of results of the {@code "Add"} expression. */
        private static final AttributeType<Number> TYPE = createNumericType(FunctionNames.Add);
        @Override protected AttributeType<Number> expectedType() {return TYPE;}

        /** Creates a new expression for the {@code "Add"} operation. */
        Add(final Expression<? super R, ? extends Number> expression1,
            final Expression<? super R, ? extends Number> expression2)
        {
            super(expression1, expression2);
        }

        /** Creates a new expression of the same type but different parameters. */
        @Override public Expression<R,Number> recreate(final Expression<? super R, ?>[] effective) {
            return new Add<>(effective[0].toValueType(Number.class),
                             effective[1].toValueType(Number.class));
        }

        /** Identification of the {@code "Add"} operation. */
        private static final ScopedName NAME = createName(FunctionNames.Add);
        @Override public ScopedName getFunctionName() {return NAME;}
        @Override protected char symbol() {return '+';}

        /** Applies this expression to the given operands. */
        @Override protected Number applyAsDouble  (double     left, double     right) {return left + right;}
        @Override protected Number applyAsFraction(Fraction   left, Fraction   right) {return left.add(right);}
        @Override protected Number applyAsDecimal (BigDecimal left, BigDecimal right) {return left.add(right);}
        @Override protected Number applyAsInteger (BigInteger left, BigInteger right) {return left.add(right);}
        @Override protected Number applyAsLong    (long       left, long       right) {return Math.addExact(left, right);}
    }


    /**
     * The "Subtract" (−) expression.
     */
    static final class Subtract<R> extends ArithmeticFunction<R> {
        /** For cross-version compatibility during (de)serialization. */
        private static final long serialVersionUID = 3048878022726271508L;

        /** Description of results of the {@code "Subtract"} expression. */
        private static final AttributeType<Number> TYPE = createNumericType(FunctionNames.Subtract);
        @Override protected AttributeType<Number> expectedType() {return TYPE;}

        /** Creates a new expression for the {@code "Subtract"} operation. */
        Subtract(final Expression<? super R, ? extends Number> expression1,
                 final Expression<? super R, ? extends Number> expression2)
        {
            super(expression1, expression2);
        }

        /** Creates a new expression of the same type but different parameters. */
        @Override public Expression<R,Number> recreate(final Expression<? super R, ?>[] effective) {
            return new Subtract<>(effective[0].toValueType(Number.class),
                                  effective[1].toValueType(Number.class));
        }

        /** Identification of the {@code "Subtract"} operation. */
        private static final ScopedName NAME = createName(FunctionNames.Subtract);
        @Override public ScopedName getFunctionName() {return NAME;}
        @Override protected char symbol() {return '−';}

        /** Applies this expression to the given operands. */
        @Override protected Number applyAsDouble  (double     left, double     right) {return left - right;}
        @Override protected Number applyAsFraction(Fraction   left, Fraction   right) {return left.subtract(right);}
        @Override protected Number applyAsDecimal (BigDecimal left, BigDecimal right) {return left.subtract(right);}
        @Override protected Number applyAsInteger (BigInteger left, BigInteger right) {return left.subtract(right);}
        @Override protected Number applyAsLong    (long       left, long       right) {return Math.subtractExact(left, right);}
    }


    /**
     * The "Multiply" (×) expression.
     */
    static final class Multiply<R> extends ArithmeticFunction<R> {
        /** For cross-version compatibility during (de)serialization. */
        private static final long serialVersionUID = -1300022614832645625L;

        /** Description of results of the {@code "Multiply"} expression. */
        private static final AttributeType<Number> TYPE = createNumericType(FunctionNames.Multiply);
        @Override protected AttributeType<Number> expectedType() {return TYPE;}

        /** Creates a new expression for the {@code "Multiply"} operation. */
        Multiply(final Expression<? super R, ? extends Number> expression1,
                 final Expression<? super R, ? extends Number> expression2)
        {
            super(expression1, expression2);
        }

        /** Creates a new expression of the same type but different parameters. */
        @Override public Expression<R,Number> recreate(final Expression<? super R, ?>[] effective) {
            return new Multiply<>(effective[0].toValueType(Number.class),
                                  effective[1].toValueType(Number.class));
        }

        /** Identification of the {@code "Multiply"} operation. */
        private static final ScopedName NAME = createName(FunctionNames.Multiply);
        @Override public ScopedName getFunctionName() {return NAME;}
        @Override protected char symbol() {return '×';}

        /** Applies this expression to the given operands. */
        @Override protected Number applyAsDouble  (double     left, double     right) {return left * right;}
        @Override protected Number applyAsFraction(Fraction   left, Fraction   right) {return left.multiply(right);}
        @Override protected Number applyAsDecimal (BigDecimal left, BigDecimal right) {return left.multiply(right);}
        @Override protected Number applyAsInteger (BigInteger left, BigInteger right) {return left.multiply(right);}
        @Override protected Number applyAsLong    (long       left, long       right) {return Math.multiplyExact(left, right);}
    }


    /**
     * The "Divide" (÷) expression.
     */
    static final class Divide<R> extends ArithmeticFunction<R> {
        /** For cross-version compatibility during (de)serialization. */
        private static final long serialVersionUID = -7709291845568648891L;

        /** Description of results of the {@code "Divide"} expression. */
        private static final AttributeType<Number> TYPE = createNumericType(FunctionNames.Divide);
        @Override protected AttributeType<Number> expectedType() {return TYPE;}

        /** Creates a new expression for the {@code "Divide"} operation. */
        Divide(final Expression<? super R, ? extends Number> expression1,
               final Expression<? super R, ? extends Number> expression2)
        {
            super(expression1, expression2);
        }

        /** Creates a new expression of the same type but different parameters. */
        @Override public Expression<R,Number> recreate(final Expression<? super R, ?>[] effective) {
            return new Divide<>(effective[0].toValueType(Number.class),
                                effective[1].toValueType(Number.class));
        }

        /** Identification of the {@code "Divide"} operation. */
        private static final ScopedName NAME = createName(FunctionNames.Divide);
        @Override public ScopedName getFunctionName() {return NAME;}
        @Override protected char symbol() {return '÷';}

        /** Divides the given integers, changing the type if the result is not an integer. */
        @Override protected Number applyAsDouble  (double     left, double     right) {return left / right;}
        @Override protected Number applyAsFraction(Fraction   left, Fraction   right) {return left.divide(right);}
        @Override protected Number applyAsDecimal (BigDecimal left, BigDecimal right) {return left.divide(right);}
        @Override protected Number applyAsInteger (BigInteger left, BigInteger right) {
            BigInteger[] r = left.divideAndRemainder(right);
            if (BigInteger.ZERO.equals(r[1])) {
                return r[0];
            } else {
                return new Fraction(r[1].intValueExact(), right.intValueExact()).add(
                       new Fraction(r[0].intValueExact(), 1));
            }
        }

        /** Divides the given integers, changing the type if the result is not an integer. */
        @Override protected Number applyAsLong(final long left, final long right) {
            final long r = left / right;
            if (left % right == 0) {
                return r;
            } else {
                return new Fraction(Math.toIntExact(left), Math.toIntExact(right));
            }
        }
    }
}
