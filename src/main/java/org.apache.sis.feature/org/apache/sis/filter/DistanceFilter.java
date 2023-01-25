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

import org.apache.sis.internal.feature.Geometries;
import org.apache.sis.internal.feature.GeometryWrapper;
import org.apache.sis.internal.feature.SpatialOperationContext;
import org.apache.sis.util.ArgumentChecks;
import org.opengis.filter.DistanceOperator;
import org.opengis.filter.DistanceOperatorName;
import org.opengis.filter.Expression;
import org.opengis.filter.Literal;
import org.opengis.geometry.Geometry;

import javax.measure.Quantity;
import javax.measure.quantity.Length;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;


/**
 * Spatial operations between two geometries and using a distance.
 * The nature of the operation depends on the subclass.
 *
 * <div class="note"><b>Note:</b>
 * this class has 3 parameters, but the third one is not an expression.
 * It still a "binary" operator if we count only the expressions.</div>
 *
 * @author  Johann Sorel (Geomatys)
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.1
 *
 * @param  <R>  the type of resources (e.g. {@link org.opengis.feature.Feature}) used as inputs.
 * @param  <G>  the implementation type of geometry objects.
 *
 * @since 1.1
 * @module
 */
final class DistanceFilter<R,G> extends BinaryGeometryFilter<R,G> implements DistanceOperator<R> {
    /**
     * For cross-version compatibility.
     */
    private static final long serialVersionUID = -5304631042699647889L;

    /**
     * Nature of the operation applied by this {@code DistanceOperator}.
     */
    private final DistanceOperatorName operatorType;

    /**
     * The buffer distance around the geometry of the second expression.
     */
    private final Quantity<Length> distance;

    /**
     * Creates a new spatial function.
     *
     * @param  operatorType  nature of the operation applied by this {@code DistanceOperator}.
     * @param  library       the geometry library to use.
     * @param  geometry1     expression fetching the first geometry of the binary operator.
     * @param  geometry2     expression fetching the second geometry of the binary operator.
     * @param  distance      the buffer distance around the geometry of the second expression.
     */
    DistanceFilter(final DistanceOperatorName operatorType,
                   final Geometries<G> library,
                   final Expression<? super R, ?> geometry1,
                   final Expression<? super R, ?> geometry2,
                   final Quantity<Length> distance)
    {
        super(library, geometry1, geometry2, distance.getUnit().getSystemUnit());
        ArgumentChecks.ensureNonNull("operatorType", operatorType);
        this.operatorType = operatorType;
        this.distance     = distance;
    }

    /**
     * Recreates a new filter of the same type and with the same parameters, but using the given expressions.
     * This method is invoked when it is possible to simplify or optimize at least one of the expressions that
     * were given in the original call to the constructor.
     */
    @Override
    protected BinaryGeometryFilter<R,G> recreate(final Expression<? super R, ?> geometry1,
                                                 final Expression<? super R, ?> geometry2)
    {
        return new DistanceFilter<>(operatorType, getGeometryLibrary(expression1), geometry1, geometry2, distance);
    }

    /**
     * Identification of this operation.
     */
    @Override
    public DistanceOperatorName getOperatorType() {
        return operatorType;
    }

    /**
     * Returns the two expressions used as parameters by this filter.
     */
    @Override
    public List<Expression<? super R, ?>> getExpressions() {
        return Arrays.asList(original(expression1), original(expression2),          // TODO: use List.of(…) with JDK9.
                             new LeafExpression.Literal<>(distance));
    }

    /**
     * Returns the two expressions together with the distance parameter.
     * This is used for information purpose only, for example in order to build a string representation.
     */
    @Override
    protected Collection<?> getChildren() {
        return Arrays.asList(original(expression1), original(expression2), distance);   // TODO: use List.of(…) with JDK9.
    }

    /**
     * Returns the buffer distance around the geometry that will be used when comparing features geometries.
     */
    @Override
    public Quantity<Length> getDistance() {
        return distance;
    }

    /**
     * Returns the literal geometry from which distances are measured.
     *
     * @throws IllegalStateException if the geometry is not a literal.
     */
    @Override
    public Geometry getGeometry() {
        final Literal<? super R, ? extends GeometryWrapper<G>> literal;
        if (expression2 instanceof Literal<?,?>) {
            literal = (Literal<? super R, ? extends GeometryWrapper<G>>) expression2;
        } else if (expression1 instanceof Literal<?,?>) {
            literal = (Literal<? super R, ? extends GeometryWrapper<G>>) expression1;
        } else {
            throw new IllegalStateException();
        }
        return literal.getValue();
    }

    /**
     * Given an object, determines if the test(s) represented by this filter are passed.
     *
     * @param  object  the object (often a {@link Feature} instance) to evaluate.
     * @return {@code true} if the test(s) are passed for the provided object.
     */
    @Override
    public boolean test(final R object) {
        final GeometryWrapper<G> left = expression1.apply(object);
        if (left != null) {
            final GeometryWrapper<G> right = expression2.apply(object);
            if (right != null) try {
                return left.predicate(operatorType, right, distance, context);
            } catch (RuntimeException e) {
                warning(e, true);
            }
        }
        return negativeResult();
    }

    /**
     * Returns the value to return when a test can not be applied.
     */
    @Override
    protected boolean negativeResult() {
        return SpatialOperationContext.negativeResult(operatorType);
    }
}
