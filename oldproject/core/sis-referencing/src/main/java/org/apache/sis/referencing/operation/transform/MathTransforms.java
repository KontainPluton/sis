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
package org.apache.sis.referencing.operation.transform;

import java.util.Map;
import java.util.List;
import java.util.Collections;
import java.util.Optional;
import java.awt.geom.AffineTransform;
import org.opengis.util.FactoryException;
import org.opengis.geometry.Envelope;
import org.opengis.geometry.DirectPosition;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.operation.Matrix;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.MathTransform1D;
import org.opengis.referencing.operation.MathTransform2D;
import org.opengis.referencing.operation.TransformException;
import org.opengis.referencing.operation.MathTransformFactory;
import org.apache.sis.internal.referencing.DirectPositionView;
import org.apache.sis.internal.referencing.ExtendedPrecisionMatrix;
import org.apache.sis.internal.referencing.j2d.AffineTransform2D;
import org.apache.sis.referencing.operation.matrix.AffineTransforms2D;
import org.apache.sis.referencing.operation.matrix.MatrixSIS;
import org.apache.sis.referencing.operation.matrix.Matrices;
import org.apache.sis.util.ArgumentChecks;
import org.apache.sis.util.ArraysExt;
import org.apache.sis.util.Static;


/**
 * Utility methods creating or working on {@link MathTransform} instances.
 * This class centralizes in one place some of the most commonly used functions this package.
 * The {@code MathTransforms} class provides the following services:
 *
 * <ul>
 *   <li>Create various SIS implementations of {@link MathTransform}.</li>
 *   <li>Perform non-standard operations on arbitrary instances.</li>
 * </ul>
 *
 * The factory static methods are provided as convenient alternatives to the GeoAPI {@link MathTransformFactory}
 * interface. However users seeking for more implementation neutrality are encouraged to limit themselves to the
 * GeoAPI factory interfaces instead.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.3
 *
 * @see MathTransformFactory
 *
 * @since 0.5
 * @module
 */
public final class MathTransforms extends Static {
    /**
     * Do not allow instantiation of this class.
     */
    private MathTransforms() {
    }

    /**
     * Returns an identity transform of the specified dimension.
     *
     * <p>Special cases:</p>
     * <ul>
     *   <li>If {@code dimension == 1}, then the returned transform implements {@link MathTransform1D}.</li>
     *   <li>If {@code dimension == 2}, then the returned transform implements {@link MathTransform2D}.</li>
     * </ul>
     *
     * @param  dimension  number of dimensions of the transform to be returned.
     * @return an identity transform of the specified dimension.
     */
    public static LinearTransform identity(final int dimension) {
        ArgumentChecks.ensurePositive("dimension", dimension);
        return IdentityTransform.create(dimension);
    }

    /**
     * Creates an affine transform which applies the same translation for all dimensions.
     * For each dimension, input values <var>x</var> are converted into output values <var>y</var>
     * using the following equation:
     *
     * <blockquote><var>y</var> = <var>x</var> + {@code offset}</blockquote>
     *
     * @param  dimension  number of input and output dimensions.
     * @param  offset     the {@code offset} term in the linear equation.
     * @return an affine transform applying the specified translation.
     *
     * @since 1.0
     */
    public static LinearTransform uniformTranslation(final int dimension, final double offset) {
        ArgumentChecks.ensurePositive("dimension", dimension);
        if (offset == 0) {
            return IdentityTransform.create(dimension);
        }
        switch (dimension) {
            case 0:  return IdentityTransform.create(0);
            case 1:  return LinearTransform1D.create(1, offset);
            case 2:  return new AffineTransform2D(1, 0, 0, 1, offset, offset);
            default: return new TranslationTransform(dimension, offset);
        }
    }

    /**
     * Creates an affine transform which applies the given translation.
     * The source and target dimensions of the transform are the length of the given vector.
     *
     * @param  vector  the translation vector.
     * @return an affine transform applying the specified translation.
     *
     * @since 1.0
     */
    public static LinearTransform translation(final double... vector) {
        ArgumentChecks.ensureNonNull("vector", vector);
        final LinearTransform tr;
        switch (vector.length) {
            case 0:  return IdentityTransform.create(0);
            case 1:  return LinearTransform1D.create(1, vector[0]);
            case 2:  tr = new AffineTransform2D(1, 0, 0, 1, vector[0], vector[1]); break;
            default: tr = new TranslationTransform(vector); break;
        }
        return tr.isIdentity() ? IdentityTransform.create(vector.length) : tr;
    }

    /**
     * Creates an affine transform which applies the given scale.
     * The source and target dimensions of the transform are the length of the given vector.
     *
     * @param  factors  the scale factors.
     * @return an affine transform applying the specified scales.
     *
     * @since 1.0
     */
    public static LinearTransform scale(final double... factors) {
        ArgumentChecks.ensureNonNull("factors", factors);
        final LinearTransform tr;
        switch (factors.length) {
            case 0:  return IdentityTransform.create(0);
            case 1:  return LinearTransform1D.create(factors[0], 0);
            case 2:  tr = new AffineTransform2D(factors[0], 0, 0, factors[1], 0, 0); break;
            default: tr = new ScaleTransform(factors); break;
        }
        return tr.isIdentity() ? IdentityTransform.create(factors.length) : tr;
    }

    /**
     * Creates a one-dimensional affine transform for the given coefficients.
     * Input values <var>x</var> will be converted into output values <var>y</var> using the following equation:
     *
     * <blockquote><var>y</var>  =  <var>x</var> ⋅ {@code scale} + {@code offset}</blockquote>
     *
     * @param  scale   the {@code scale}  term in the linear equation.
     * @param  offset  the {@code offset} term in the linear equation.
     * @return the linear transform for the given scale and offset.
     *
     * @see org.apache.sis.measure.Units#converter(Number, Number)
     */
    public static LinearTransform linear(final double scale, final double offset) {
        return LinearTransform1D.create(scale, offset);
    }

    /**
     * Creates an arbitrary linear transform from the specified matrix. Usually the matrix
     * {@linkplain org.apache.sis.referencing.operation.matrix.MatrixSIS#isAffine() is affine},
     * but this is not mandatory. Non-affine matrix will define a projective transform.
     *
     * <p>If the transform input dimension is {@code M}, and output dimension is {@code N},
     * then the given matrix shall have size {@code [N+1][M+1]}.
     * The +1 in the matrix dimensions allows the matrix to do a shift, as well as a rotation.
     * The {@code [M][j]} element of the matrix will be the <var>j</var>'th coordinate of the moved origin.</p>
     *
     * @param  matrix  the matrix used to define the linear transform.
     * @return the linear (usually affine) transform.
     *
     * @see #getMatrix(MathTransform)
     * @see DefaultMathTransformFactory#createAffineTransform(Matrix)
     */
    public static LinearTransform linear(final Matrix matrix) {
        ArgumentChecks.ensureNonNull("matrix", matrix);
        final int sourceDimension = matrix.getNumCol() - 1;
        final int targetDimension = matrix.getNumRow() - 1;
        if (sourceDimension == targetDimension) {
            if (matrix.isIdentity()) {
                return identity(sourceDimension);
            }
            if (Matrices.isAffine(matrix)) {
                switch (sourceDimension) {
                    case 1: {
                        return linear(matrix.getElement(0,0), matrix.getElement(0,1));
                    }
                    case 2: {
                        if (matrix instanceof ExtendedPrecisionMatrix) {
                            return new AffineTransform2D(((ExtendedPrecisionMatrix) matrix));
                        } else {
                            return new AffineTransform2D(
                                    matrix.getElement(0,0), matrix.getElement(1,0),
                                    matrix.getElement(0,1), matrix.getElement(1,1),
                                    matrix.getElement(0,2), matrix.getElement(1,2));
                        }
                    }
                }
            } else if (sourceDimension == 2) {
                return new ProjectiveTransform2D(matrix);
            }
        }
        final LinearTransform candidate = CopyTransform.create(matrix);
        if (candidate != null) {
            return candidate;
        }
        return new ProjectiveTransform(matrix).optimize();
    }

    /**
     * Returns a linear (usually affine) transform which approximates the given transform in the vicinity of the given position.
     * If the given transform is already an instance of {@link LinearTransform}, then it is returned as-is.
     * Otherwise an approximation for the given position is created using the
     * {@linkplain MathTransform#derivative(DirectPosition) transform derivative} at that position.
     *
     * <h4>Invariant</h4>
     * Transforming the given {@code position} using the given {@code transform} produces the same result
     * (ignoring rounding error) than transforming the same {@code position} using the returned transform.
     * This invariant holds only for that particular position; the transformation of any other positions
     * may produce different results.
     *
     * @param  transform  the transform to approximate by an affine transform.
     * @param  position   position in source CRS around which to get the an affine transform approximation.
     * @return a transform approximating the given transform around the given position.
     * @throws TransformException if an error occurred while transforming the given position
     *         or computing the derivative at that position.
     *
     * @since 1.0
     *
     * @see #getMatrix(MathTransform, DirectPosition)
     */
    public static LinearTransform linear(final MathTransform transform, final DirectPosition position) throws TransformException {
        if (transform instanceof LinearTransform) {
            // We accept null position here for consistency with MathTransform.derivative(DirectPosition).
            ArgumentChecks.ensureDimensionMatches("position", transform.getSourceDimensions(), position);
            return (LinearTransform) transform;
        } else {
            return linear(getMatrix(transform, position));
        }
    }

    /**
     * Creates a transform for the <i>y=f(x)</i> function where <var>y</var> are computed by a linear interpolation.
     * Both {@code preimage} (the <var>x</var>) and {@code values} (the <var>y</var>) arguments can be null:
     *
     * <ul>
     *   <li>If both {@code preimage} and {@code values} arrays are non-null, then they must have the same length.</li>
     *   <li>If both {@code preimage} and {@code values} arrays are null, then this method returns the identity transform.</li>
     *   <li>If only {@code preimage} is null, then the <var>x</var> values are taken as {0, 1, 2, …, {@code values.length} - 1}.</li>
     *   <li>If only {@code values} is null, then the <var>y</var> values are taken as {0, 1, 2, …, {@code preimage.length} - 1}.</li>
     * </ul>
     *
     * All {@code preimage} elements shall be real numbers (not NaN) sorted in increasing or decreasing order.
     * Elements in the {@code values} array do not need to be ordered, but the returned transform will be invertible
     * only if all values are real numbers sorted in increasing or decreasing order.
     * Furthermore the returned transform is affine (i.e. implement the {@link LinearTransform} interface)
     * if the interval between each {@code preimage} and {@code values} element is constant.
     *
     * <p>The current implementation uses linear interpolation. This may be changed in a future SIS version.</p>
     *
     * @param  preimage  the input values (<var>x</var>) in the function domain, or {@code null}.
     * @param  values    the output values (<var>y</var>) in the function range, or {@code null}.
     * @return the <i>y=f(x)</i> function.
     *
     * @see org.opengis.coverage.InterpolationMethod
     *
     * @since 0.7
     */
    public static MathTransform1D interpolate(final double[] preimage, final double[] values) {
        return LinearInterpolator1D.create(preimage, values);
    }

    /**
     * Creates a transform defined as one transform applied globally except in sub-areas where more accurate
     * transforms are available. Such constructs appear in some datum shift files. The result of transforming
     * a point by the returned {@code MathTransform} is as if iterating over all given {@link Envelope}s in
     * no particular order, find the smallest one containing the point to transform (envelope border considered
     * inclusive), then use the associated {@link MathTransform} for transforming the point.
     * If the point is not found in any envelope, then the global transform is applied.
     *
     * <p>The following constraints apply:</p>
     * <ul>
     *   <li>The global transform must be a reasonable approximation of the specialized transforms
     *       (this is required for calculating the inverse transform).</li>
     *   <li>All transforms in the {@code specializations} map must have the same number of source and target
     *       dimensions than the {@code global} transform.</li>
     *   <li>All envelopes in the {@code specializations} map must have the same number of dimensions
     *       than the global transform <em>source</em> dimensions.</li>
     *   <li>In current implementation, each envelope must either be fully included in another envelope,
     *       or not overlap any other envelope.</li>
     * </ul>
     *
     * @param  global  the transform to use globally where there is no suitable specialization.
     * @param  specializations  more accurate transforms available in some sub-areas.
     * @return a transform applying the given global transform except in sub-areas where specializations are available.
     * @throws IllegalArgumentException if a constraint is not met.
     *
     * @since 1.0
     */
    public static MathTransform specialize(final MathTransform global, final Map<Envelope,MathTransform> specializations) {
        ArgumentChecks.ensureNonNull("generic", global);
        ArgumentChecks.ensureNonNull("specializations", specializations);
        final SpecializableTransform tr;
        if (specializations.isEmpty()) {
            return global;
        } else if (global.getSourceDimensions() == 2 && global.getTargetDimensions() == 2) {
            tr = new SpecializableTransform2D(global, specializations);
        } else {
            tr = new SpecializableTransform(global, specializations);
        }
        final MathTransform substitute = tr.getSubstitute();
        return (substitute != null) ? substitute : tr;
    }

    /**
     * Creates a transform which passes through a subset of coordinates to another transform.
     * This method returns a transform having the following dimensions:
     *
     * {@preformat java
     *     Source: firstAffectedCoordinate + subTransform.getSourceDimensions() + numTrailingCoordinates
     *     Target: firstAffectedCoordinate + subTransform.getTargetDimensions() + numTrailingCoordinates
     * }
     *
     * Affected coordinates will range from {@code firstAffectedCoordinate} inclusive to
     * {@code dimTarget - numTrailingCoordinates} exclusive.
     *
     * @param  firstAffectedCoordinate  index of the first affected coordinate.
     * @param  subTransform             the sub-transform to apply on modified coordinates.
     * @param  numTrailingCoordinates   number of trailing coordinates to pass through.
     * @return a pass-through transform, potentially as a {@link PassThroughTransform} instance but not necessarily.
     *
     * @since 1.0
     */
    public static MathTransform passThrough(final int firstAffectedCoordinate,
                                            final MathTransform subTransform,
                                            final int numTrailingCoordinates)
    {
        ArgumentChecks.ensureNonNull ("subTransform",            subTransform);
        ArgumentChecks.ensurePositive("firstAffectedCoordinate", firstAffectedCoordinate);
        ArgumentChecks.ensurePositive("numTrailingCoordinates",  numTrailingCoordinates);
        if (firstAffectedCoordinate == 0 && numTrailingCoordinates == 0) {
            return subTransform;
        }
        if (subTransform.isIdentity()) {
            final int dimension = subTransform.getSourceDimensions();
            if (dimension == subTransform.getTargetDimensions()) {
                return IdentityTransform.create(firstAffectedCoordinate + dimension + numTrailingCoordinates);
            }
        }
        return PassThroughTransform.create(firstAffectedCoordinate, subTransform, numTrailingCoordinates);
    }

    /**
     * Puts together a list of independent math transforms, each of them operating on a subset of coordinate values.
     * This method is often used for defining 4-dimensional (<var>x</var>,<var>y</var>,<var>z</var>,<var>t</var>)
     * transform as an aggregation of 3 simpler transforms operating on (<var>x</var>,<var>y</var>), (<var>z</var>)
     * and (<var>t</var>) values respectively.
     *
     * <p>Invariants:</p>
     * <ul>
     *   <li>The {@linkplain AbstractMathTransform#getSourceDimensions() source dimensions} of the returned transform
     *       is equal to the sum of the source dimensions of all given transforms.</li>
     *   <li>The {@linkplain AbstractMathTransform#getTargetDimensions() target dimensions} of the returned transform
     *       is equal to the sum of the target dimensions of all given transforms.</li>
     * </ul>
     *
     * @param  components  the transforms to aggregate in a single transform, in the given order.
     * @return the aggregation of all given transforms, or {@code null} if the given {@code components} array was empty.
     *
     * @see PassThroughTransform
     * @see org.apache.sis.referencing.CRS#compound(CoordinateReferenceSystem...)
     * @see org.apache.sis.geometry.Envelopes#compound(Envelope...)
     *
     * @since 0.6
     */
    public static MathTransform compound(final MathTransform... components) {
        ArgumentChecks.ensureNonNull("components", components);
        int sum = 0;
        final int[] dimensions = new int[components.length];
        for (int i=0; i<components.length; i++) {
            final MathTransform tr = components[i];
            ArgumentChecks.ensureNonNullElement("components", i, tr);
            sum += (dimensions[i] = tr.getSourceDimensions());
        }
        MathTransform compound = null;
        int firstAffectedCoordinate = 0;
        for (int i=0; i<components.length; i++) {
            MathTransform tr = components[i];
            tr = passThrough(firstAffectedCoordinate, tr, sum - (firstAffectedCoordinate += dimensions[i]));
            if (compound == null) {
                compound = tr;
            } else {
                compound = concatenate(compound, tr);
            }
        }
        assert isValid(getSteps(compound)) : compound;
        return compound;
    }

    /**
     * Concatenates the two given transforms. The returned transform will implement
     * {@link MathTransform1D} or {@link MathTransform2D} if the dimensions of the
     * concatenated transform are equal to 1 or 2 respectively.
     *
     * @param  tr1  the first math transform.
     * @param  tr2  the second math transform.
     * @return the concatenated transform.
     * @throws MismatchedDimensionException if the output dimension of the first transform
     *         does not match the input dimension of the second transform.
     *
     * @see DefaultMathTransformFactory#createConcatenatedTransform(MathTransform, MathTransform)
     */
    public static MathTransform concatenate(final MathTransform tr1, final MathTransform tr2)
            throws MismatchedDimensionException
    {
        ArgumentChecks.ensureNonNull("tr1", tr1);
        ArgumentChecks.ensureNonNull("tr2", tr2);
        final MathTransform tr;
        try {
            tr = ConcatenatedTransform.create(tr1, tr2, null);
        } catch (FactoryException e) {
            throw new IllegalArgumentException(e);              // Should never happen actually.
        }
        assert isValid(getSteps(tr)) : tr;
        return tr;
    }

    /**
     * Concatenates the given one-dimensional transforms. This is a convenience methods
     * delegating to {@link #concatenate(MathTransform, MathTransform)} and casting the
     * result to a {@link MathTransform1D} instance.
     *
     * @param  tr1  the first math transform.
     * @param  tr2  the second math transform.
     * @return the concatenated transform.
     * @throws MismatchedDimensionException if the output dimension of the first transform
     *         does not match the input dimension of the second transform.
     */
    public static MathTransform1D concatenate(MathTransform1D tr1, MathTransform1D tr2)
            throws MismatchedDimensionException
    {
        return (MathTransform1D) concatenate((MathTransform) tr1, (MathTransform) tr2);
    }

    /**
     * Concatenates the given two-dimensional transforms. This is a convenience methods
     * delegating to {@link #concatenate(MathTransform, MathTransform)} and casting the
     * result to a {@link MathTransform2D} instance.
     *
     * @param  tr1  the first math transform.
     * @param  tr2  the second math transform.
     * @return the concatenated transform.
     * @throws MismatchedDimensionException if the output dimension of the first transform
     *         does not match the input dimension of the second transform.
     */
    public static MathTransform2D concatenate(MathTransform2D tr1, MathTransform2D tr2)
            throws MismatchedDimensionException
    {
        return bidimensional(concatenate((MathTransform) tr1, (MathTransform) tr2));
    }

    /**
     * Concatenates the three given transforms. This is a convenience methods doing its job
     * as two consecutive concatenations.
     *
     * @param  tr1  the first math transform.
     * @param  tr2  the second math transform.
     * @param  tr3  the third math transform.
     * @return the concatenated transform.
     * @throws MismatchedDimensionException if the output dimension of a transform
     *         does not match the input dimension of next transform.
     */
    public static MathTransform concatenate(MathTransform tr1, MathTransform tr2, MathTransform tr3)
            throws MismatchedDimensionException
    {
        ArgumentChecks.ensureNonNull("tr1", tr1);
        ArgumentChecks.ensureNonNull("tr2", tr2);
        ArgumentChecks.ensureNonNull("tr3", tr3);
        return concatenate(concatenate(tr1, tr2), tr3);
    }

    /**
     * Concatenates the three given one-dimensional transforms. This is a convenience methods
     * delegating to {@link #concatenate(MathTransform, MathTransform, MathTransform)} and
     * casting the result to a {@link MathTransform1D} instance.
     *
     * @param  tr1  the first math transform.
     * @param  tr2  the second math transform.
     * @param  tr3  the third math transform.
     * @return the concatenated transform.
     * @throws MismatchedDimensionException if the output dimension of a transform
     *         does not match the input dimension of next transform.
     */
    public static MathTransform1D concatenate(MathTransform1D tr1, MathTransform1D tr2, MathTransform1D tr3)
            throws MismatchedDimensionException
    {
        return (MathTransform1D) concatenate((MathTransform) tr1, (MathTransform) tr2, (MathTransform) tr3);
    }

    /**
     * Concatenates the three given two-dimensional transforms. This is a convenience methods
     * delegating to {@link #concatenate(MathTransform, MathTransform, MathTransform)} and
     * casting the result to a {@link MathTransform2D} instance.
     *
     * @param  tr1  the first math transform.
     * @param  tr2  the second math transform.
     * @param  tr3  the third math transform.
     * @return the concatenated transform.
     * @throws MismatchedDimensionException if the output dimension of a transform
     *         does not match the input dimension of next transform.
     */
    public static MathTransform2D concatenate(MathTransform2D tr1, MathTransform2D tr2, MathTransform2D tr3)
            throws MismatchedDimensionException
    {
        return bidimensional(concatenate((MathTransform) tr1, (MathTransform) tr2, (MathTransform) tr3));
    }

    /**
     * Returns the given transform as a {@link MathTransform2D} instance.
     * If the given transform is {@code null} or already implements the {@link MathTransform2D} interface,
     * then it is returned as-is. Otherwise the given transform is wrapped in an adapter.
     *
     * @param  transform  the transform to have as {@link MathTransform2D} instance, or {@code null}.
     * @return the given transform as a {@link MathTransform2D}, or {@code null} if the argument was null.
     * @throws MismatchedDimensionException if the number of source and target dimensions is not 2.
     *
     * @since 1.1
     */
    public static MathTransform2D bidimensional(final MathTransform transform) {
        if (transform == null || transform instanceof MathTransform2D) {
            return (MathTransform2D) transform;
        } else {
            ArgumentChecks.ensureDimensionsMatch("transform", 2, 2, transform);
            return new TransformAdapter2D(transform);
        }
    }

    /**
     * Makes sure that the given list does not contains two consecutive linear transforms
     * (because their matrices should have been multiplied together).
     * This is used for assertion purposes only.
     */
    static boolean isValid(final List<MathTransform> steps) {
        boolean wasLinear = false;
        for (final MathTransform step : steps) {
            if (step instanceof LinearTransform) {
                if (wasLinear) return false;
                wasLinear = true;
            } else {
                wasLinear = false;
            }
        }
        return true;
    }

    /**
     * Returns all single components of the given (potentially concatenated) transform.
     * This method makes the following choice:
     *
     * <ul>
     *   <li>If {@code transform} is {@code null}, returns an empty list.</li>
     *   <li>Otherwise if {@code transform} is the result of calls to {@code concatenate(…)} methods, returns
     *       all steps making the transformation chain. Nested concatenated transforms (if any) are flattened.
     *       Note that some steps may have have been merged together, resulting in a shorter list.</li>
     *   <li>Otherwise returns the given transform in a list of size 1.</li>
     * </ul>
     *
     * @param  transform  the transform for which to get the components, or {@code null}.
     * @return all single math transforms performed by this concatenated transform.
     */
    public static List<MathTransform> getSteps(final MathTransform transform) {
        if (transform != null) {
            if (transform instanceof ConcatenatedTransform) {
                return ((ConcatenatedTransform) transform).getSteps();
            } else {
                return Collections.singletonList(transform);
            }
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * If the given transform is linear, returns its coefficients as a matrix.
     * More specifically:
     *
     * <ul>
     *   <li>If the given transform is an instance of {@link LinearTransform},
     *       returns {@link LinearTransform#getMatrix()}.</li>
     *   <li>Otherwise if the given transform is an instance of {@link AffineTransform},
     *       returns its coefficients in a {@link org.apache.sis.referencing.operation.matrix.Matrix3} instance.</li>
     *   <li>Otherwise returns {@code null}.</li>
     * </ul>
     *
     * @param  transform  the transform for which to get the matrix, or {@code null}.
     * @return the matrix of the given transform, or {@code null} if none.
     *
     * @see #linear(Matrix)
     * @see LinearTransform#getMatrix()
     */
    public static Matrix getMatrix(final MathTransform transform) {
        if (transform instanceof LinearTransform) {
            return ((LinearTransform) transform).getMatrix();
        }
        if (transform instanceof AffineTransform) {
            return AffineTransforms2D.toMatrix((AffineTransform) transform);
        }
        return null;
    }

    /**
     * Returns the coefficients of an affine transform in the vicinity of the given position.
     * If the given transform is linear, then this method produces a result identical to {@link #getMatrix(MathTransform)}.
     * Otherwise the returned matrix can be used for {@linkplain #linear(Matrix) building a linear transform} which can be
     * used as an approximation of the given transform for short distances around the given position.
     *
     * @param  transform  the transform to approximate by an affine transform.
     * @param  position   position in source CRS around which to get the coefficients of an affine transform approximation.
     * @return the matrix of the given transform around the given position.
     * @throws TransformException if an error occurred while transforming the given position or computing the derivative at
     *         that position.
     *
     * @since 1.0
     *
     * @see #linear(MathTransform, DirectPosition)
     */
    public static Matrix getMatrix(final MathTransform transform, final DirectPosition position) throws TransformException {
        ArgumentChecks.ensureNonNull("transform", transform);
        final int srcDim = transform.getSourceDimensions();
        ArgumentChecks.ensureDimensionMatches("position", srcDim, position);            // Null position is okay for now.
        final Matrix affine = getMatrix(transform);
        if (affine != null) {
            return affine;
            // We accept null position here for consistency with MathTransform.derivative(DirectPosition).
        }
        ArgumentChecks.ensureNonNull("position", position);
        final int tgtDim = transform.getTargetDimensions();
        double[] pts = new double[Math.max(srcDim + 1, tgtDim)];
        for (int i=0; i<srcDim; i++) {
            pts[i] = position.getOrdinate(i);
        }
        final Matrix d = derivativeAndTransform(transform, pts, 0, pts, 0);
        final MatrixSIS a = Matrices.createZero(tgtDim + 1, srcDim + 1);
        for (int j=0; j<tgtDim; j++) {
            for (int i=0; i<srcDim; i++) {
                a.setElement(j, i, d.getElement(j, i));
            }
            a.setElement(j, srcDim, pts[j]);
            pts[j] = -position.getOrdinate(j);                  // To be used by a.translate(pts) later.
        }
        a.setElement(tgtDim, srcDim, 1);
        /*
         * At this point, the translation column in the matrix is set as if the coordinate system origin
         * was at the given position. We want to keep the original coordinate system origin. We do that
         * be applying a translation in the opposite direction before the affine transform. Translation
         * terms were opportunistically set in the previous loop.
         */
        pts = ArraysExt.resize(pts, srcDim + 1);
        pts[srcDim] = 1;
        a.translate(pts);
        return a;
    }

    /**
     * A buckle method for calculating derivative and coordinate transformation in a single step.
     * The transform result is stored in the given destination array, and the derivative matrix
     * is returned. Invoking this method is equivalent to the following code, except that it may
     * execute faster with some {@code MathTransform} implementations:
     *
     * {@preformat java
     *     DirectPosition ptSrc = ...;
     *     DirectPosition ptDst = ...;
     *     Matrix matrixDst = derivative(ptSrc);
     *     ptDst = transform(ptSrc, ptDst);
     * }
     *
     * @param  transform  the transform to use.
     * @param  srcPts     the array containing the source coordinate.
     * @param  srcOff     the offset to the point to be transformed in the source array.
     * @param  dstPts     the array into which the transformed coordinate is returned.
     * @param  dstOff     the offset to the location of the transformed point that is stored in the destination array.
     * @return the matrix of the transform derivative at the given source position.
     * @throws TransformException if the point can not be transformed
     *         or if a problem occurred while calculating the derivative.
     *
     * @see #tangent(MathTransform, DirectPosition)
     * @see MathTransform#derivative(DirectPosition)
     * @see Matrices#createAffine(Matrix, DirectPosition)
     */
    public static Matrix derivativeAndTransform(final MathTransform transform,
                                                final double[] srcPts, final int srcOff,
                                                final double[] dstPts, final int dstOff)
            throws TransformException
    {
        if (transform instanceof AbstractMathTransform) {
            return ((AbstractMathTransform) transform).transform(srcPts, srcOff, dstPts, dstOff, true);
        }
        // Must be calculated before to transform the coordinate.
        final Matrix derivative = transform.derivative(
                new DirectPositionView.Double(srcPts, srcOff, transform.getSourceDimensions()));
        if (dstPts != null) {
            transform.transform(srcPts, srcOff, dstPts, dstOff, 1);
        }
        return derivative;
    }

    /**
     * Computes a linear approximation of the given math transform at the given position.
     * If the given transform is already an instance of {@link LinearTransform}, then it is returned as-is.
     * Otherwise an affine transform is created from the {@linkplain MathTransform#derivative(DirectPosition)
     * transform derivative} and the tangent point coordinates. The returned transform has the same number of
     * source and target dimensions than the given transform.
     *
     * <p>If the given transform is a one dimensional curve, then this method computes the tangent at the given
     * position. The same computation is generalized to any number of dimensions (computes a tangent plane if the
     * given transform is two-dimensional, <i>etc.</i>).</p>
     *
     * @param  toApproximate  the non-linear transform to approximate by a linear transform.
     * @param  tangentPoint   the point where to compute a linear approximation.
     * @return linear approximation of the given math transform at the given position.
     * @throws TransformException if the point can not be transformed
     *         or if a problem occurred while calculating the derivative.
     *
     * @since 1.1
     */
    public static LinearTransform tangent(final MathTransform toApproximate, final DirectPosition tangentPoint)
            throws TransformException
    {
        ArgumentChecks.ensureNonNull("toApproximate", toApproximate);
        if (toApproximate instanceof LinearTransform) {
            return (LinearTransform) toApproximate;
        }
        final int srcDim = toApproximate.getSourceDimensions();
        ArgumentChecks.ensureDimensionMatches("tangentPoint", srcDim, tangentPoint);
        final int tgtDim = toApproximate.getTargetDimensions();
        final double[] coordinates = new double[Math.max(srcDim, tgtDim)];
        for (int i=0; i<srcDim; i++) {
            coordinates[i] = tangentPoint.getOrdinate(i);
        }
        final Matrix derivative = derivativeAndTransform(toApproximate, coordinates, 0, coordinates, 0);
        final MatrixSIS m = Matrices.createAffine(derivative, new DirectPositionView.Double(coordinates, 0, tgtDim));
        for (int i=0; i<srcDim; i++) {
            m.convertBefore(i, null, -tangentPoint.getOrdinate(i));
        }
        final LinearTransform tangent = linear(m);
        assert tangent.getSourceDimensions() == srcDim;
        assert tangent.getTargetDimensions() == tgtDim;
        return tangent;
    }

    /**
     * Returns source coordinate values where the transform is mathematically and numerically applicable.
     * This is <em>not</em> the domain of validity for which a coordinate reference system has been defined,
     * because this method ignores "real world" considerations such as datum and country boundaries.
     * This method is for allowing callers to crop their data for removing areas that may cause numerical problems,
     * for example latitudes too close to a pole before Mercator projection.
     *
     * <p>See {@link AbstractMathTransform#getDomain(DomainDefinition)} for more information.
     * This static method delegates to above-cited method if possible, or returns an empty value otherwise.</p>
     *
     * @param  evaluated  transform for which to evaluate a domain, or {@code null}.
     * @return estimation of a domain where this transform is considered numerically applicable.
     * @throws TransformException if the domain can not be estimated.
     *
     * @see AbstractMathTransform#getDomain(DomainDefinition)
     * @see org.opengis.referencing.operation.CoordinateOperation#getDomainOfValidity()
     *
     * @since 1.3
     */
    public static Optional<Envelope> getDomain(final MathTransform evaluated) throws TransformException {
        if (evaluated instanceof AbstractMathTransform) {
            return ((AbstractMathTransform) evaluated).getDomain(new DomainDefinition());
        }
        return Optional.empty();
    }
}
