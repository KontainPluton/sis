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

import org.apache.sis.geometry.WraparoundMethod;
import org.apache.sis.internal.feature.Geometries;
import org.apache.sis.internal.feature.GeometryWrapper;
import org.apache.sis.internal.feature.SpatialOperationContext;
import org.apache.sis.util.ArgumentChecks;
import org.opengis.filter.BinarySpatialOperator;
import org.opengis.filter.Expression;
import org.opengis.filter.SpatialOperatorName;
import org.opengis.geometry.Envelope;

import java.util.Collection;


/**
 * Spatial operations between two geometries.
 * The nature of the operation depends on {@link #getOperatorType()}.
 * A standard set of spatial operators is equal, disjoin, touches,
 * within, overlaps, crosses, intersects, contains, beyond and BBOX.
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
final class BinarySpatialFilter<R,G> extends BinaryGeometryFilter<R,G> implements BinarySpatialOperator<R> {
    /**
     * For cross-version compatibility.
     */
    private static final long serialVersionUID = -7600403345673820881L;

    /**
     * Nature of the operation applied by this {@code BinarySpatialFilter}.
     */
    private final SpatialOperatorName operatorType;

    /**
     * Creates a spatial operator for {@link SpatialOperatorName#BBOX}.
     *
     * @param  library   the geometry library to use.
     * @param  geometry  expression fetching the geometry to check for interaction with bounds.
     * @param  bounds    the bounds to check geometry against.
     * @return a filter checking for interactions of the bounding boxes.
     */
    BinarySpatialFilter(final Geometries<G> library, final Expression<? super R, ?> geometry,
                    final Envelope bounds, final WraparoundMethod wraparound)
    {
        super(library, geometry, new LeafExpression.Transformed<>(library.toGeometry2D(bounds, wraparound),
                                 new LeafExpression.Literal<>(bounds)), null);
        operatorType = SpatialOperatorName.BBOX;
    }

    /**
     * Creates a spatial operator all types other than BBOX.
     *
     * @param  operatorType  nature of the operation applied by this {@code BinarySpatialFilter}.
     * @param  library       the geometry library to use.
     * @param  geometry1     expression fetching the first geometry of the binary operator.
     * @param  geometry2     expression fetching the second geometry of the binary operator.
     * @return a filter for the specified operation between the two geometries.
     */
    BinarySpatialFilter(final SpatialOperatorName operatorType,
                    final Geometries<G> library,
                    final Expression<? super R, ?> geometry1,
                    final Expression<? super R, ?> geometry2)
    {
        super(library, geometry1, geometry2, null);
        this.operatorType = operatorType;
        ArgumentChecks.ensureNonNull("operatorType", operatorType);
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
        return new BinarySpatialFilter<>(operatorType, getGeometryLibrary(expression1), geometry1, geometry2);
    }

    /**
     * Identification of this operation.
     */
    @Override
    public SpatialOperatorName getOperatorType() {
        return operatorType;
    }

    /**
     * Returns the first expression to be evaluated.
     */
    @Override
    public Expression<? super R, ?> getOperand1() {
        return original(expression1);
    }

    /**
     * Returns the second expression to be evaluated.
     */
    @Override
    public Expression<? super R, ?> getOperand2() {
        return original(expression2);
    }

    /**
     * Returns the two expressions used as parameters by this filter.
     */
    @Override
    protected Collection<?> getChildren() {
        return getExpressions();
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
                return left.predicate(operatorType, right, context);
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
