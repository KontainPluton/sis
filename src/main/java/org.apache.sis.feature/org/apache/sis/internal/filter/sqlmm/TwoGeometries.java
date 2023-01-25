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
package org.apache.sis.internal.filter.sqlmm;

import java.util.List;
import java.util.Arrays;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;
import org.apache.sis.filter.Optimization;
import org.apache.sis.internal.feature.AttributeConvention;
import org.apache.sis.internal.feature.Geometries;
import org.apache.sis.internal.feature.GeometryWrapper;

// Branch-dependent imports
import org.opengis.feature.FeatureType;
import org.opengis.feature.PropertyNotFoundException;
import org.opengis.filter.Expression;
import org.opengis.filter.Literal;
import org.opengis.filter.ValueReference;


/**
 * SQLMM spatial functions taking two geometry operands.
 *
 * @author  Johann Sorel (Geomatys)
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.3
 *
 * @param  <R>  the type of resources (e.g. {@link org.opengis.feature.Feature}) used as inputs.
 * @param  <G>  the implementation type of geometry objects.
 *
 * @since 1.1
 * @module
 */
class TwoGeometries<R,G> extends SpatialFunction<R> {
    /**
     * For cross-version compatibility.
     */
    private static final long serialVersionUID = -5209470632171445234L;

    /**
     * The expression giving the geometries.
     */
    final Expression<? super R, GeometryWrapper<G>> geometry1, geometry2;

    /**
     * Creates a new function for geometries represented by the given parameter.
     */
    TwoGeometries(final SQLMM operation, final Expression<? super R, ?>[] parameters, final Geometries<G> library) {
        super(operation, parameters);
        geometry1 = toGeometryWrapper(library, parameters[0]);
        geometry2 = toGeometryWrapper(library, parameters[1]);
    }

    /**
     * Creates a new expression of the same type than this expression, but with an optimized geometry.
     * The optimization may be a geometry computed immediately if all operator parameters are literals.
     */
    @Override
    public Expression<R,Object> recreate(final Expression<? super R, ?>[] effective) {
        return new TwoGeometries<>(operation, effective, getGeometryLibrary());
    }

    /**
     * If the CRS of the first argument is known in advance and the second argument is a literal,
     * transforms the second geometry to the CRS of the first argument. The transformed geometry
     * is always the second argument because according SQLMM specification, operations shall be
     * executed in the CRS of the first argument.
     */
    @Override
    public Expression<? super R, ?> optimize(final Optimization optimization) {
        final FeatureType featureType = optimization.getFeatureType();
        if (featureType != null) {
            final Expression<? super R, ?> p1 = unwrap(geometry1);
            if (p1 instanceof ValueReference<?,?> && unwrap(geometry2) instanceof Literal<?,?>) try {
                final CoordinateReferenceSystem targetCRS = AttributeConvention.getCRSCharacteristic(
                        featureType, featureType.getProperty(((ValueReference<?,?>) p1).getXPath()));
                if (targetCRS != null) {
                    final GeometryWrapper<G> literal = geometry2.apply(null);
                    if (literal != null) {
                        final GeometryWrapper<G> tr = literal.transform(targetCRS);
                        if (tr != literal) {
                            @SuppressWarnings({"unchecked","rawtypes"})
                            final Expression<? super R, ?>[] effective = getParameters().toArray(new Expression[0]);  // TODO: use generator in JDK9.
                            effective[1] = Optimization.literal(tr);
                            return recreate(effective);
                        }
                    }
                }
            } catch (PropertyNotFoundException | TransformException e) {
                warning(e, true);
            }
        }
        return super.optimize(optimization);
    }

    /**
     * Returns a handler for the library of geometric objects used by this expression.
     */
    @Override
    final Geometries<?> getGeometryLibrary() {
        return getGeometryLibrary(geometry1);
    }

    /**
     * Returns the sub-expressions that will be evaluated to provide the parameters to the function.
     */
    @Override
    public List<Expression<? super R, ?>> getParameters() {
        return Arrays.asList(unwrap(geometry1), unwrap(geometry2));     // TODO: use List.of(…) with JDK9.
    }

    /**
     * Evaluates the two first expressions as geometry objects,
     * applies the operation and returns the result.
     */
    @Override
    public Object apply(R input) {
        final GeometryWrapper<G> value = geometry1.apply(input);
        if (value != null) {
            final GeometryWrapper<G> other = geometry2.apply(input);
            if (other != null) try {
                return value.operation(operation, other);
            } catch (TransformException | RuntimeException e) {
                warning(e, false);
            }
        }
        return null;
    }


    /**
     * SQLMM spatial functions taking a single geometry operand with one argument.
     */
    static final class WithArgument<R,G> extends TwoGeometries<R,G> {
        /** For cross-version compatibility. */
        private static final long serialVersionUID = -121819663224041806L;

        /**
         * The first argument after the geometries.
         */
        final Expression<? super R, ?> argument;

        /**
         * Creates a new function for geometries represented by the given parameter.
         */
        WithArgument(final SQLMM operation, final Expression<? super R, ?>[] parameters, final Geometries<G> library) {
            super(operation, parameters, library);
            argument = parameters[2];
        }

        /**
         * Creates a new expression of the same type than this expression, but with an optimized geometry.
         * The optimization may be a geometry computed immediately if all operator parameters are literals.
         */
        @Override
        public Expression<R,Object> recreate(final Expression<? super R, ?>[] effective) {
            return new WithArgument<>(operation, effective, getGeometryLibrary());
        }

        /**
         * Returns the sub-expressions that will be evaluated to provide the parameters to the function.
         */
        @Override
        public List<Expression<? super R, ?>> getParameters() {
            return Arrays.asList(unwrap(geometry1), unwrap(geometry2), argument);   // TODO: use List.of(…) with JDK9.
        }

        /**
         * Evaluates the two first expressions as geometry objects,
         * applies the operation and returns the result.
         */
        @Override
        public Object apply(R input) {
            final GeometryWrapper<G> value = geometry1.apply(input);
            if (value != null) {
                final GeometryWrapper<G> other = geometry2.apply(input);
                if (other != null) try {
                    return value.operationWithArgument(operation, other, argument.apply(input));
                } catch (TransformException | RuntimeException e) {
                    warning(e, false);
                }
            }
            return null;
        }
    }
}
