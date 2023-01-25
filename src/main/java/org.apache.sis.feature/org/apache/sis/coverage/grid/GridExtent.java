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
package org.apache.sis.coverage.grid;

import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.SortedMap;
import java.util.Arrays;
import java.util.Optional;
import java.util.Locale;
import java.io.Serializable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.awt.Rectangle;
import org.opengis.util.FactoryException;
import org.opengis.util.InternationalString;
import org.opengis.geometry.Envelope;
import org.opengis.geometry.DirectPosition;
import org.opengis.metadata.spatial.DimensionNameType;
import org.opengis.referencing.cs.AxisDirection;
import org.opengis.referencing.cs.CoordinateSystem;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.datum.PixelInCell;
import org.opengis.referencing.operation.Matrix;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.apache.sis.util.ArgumentChecks;
import org.apache.sis.util.resources.Errors;
import org.apache.sis.util.resources.Vocabulary;
import org.apache.sis.util.collection.WeakValueHashMap;
import org.apache.sis.internal.referencing.AxisDirections;
import org.apache.sis.internal.referencing.ExtendedPrecisionMatrix;
import org.apache.sis.internal.feature.Resources;
import org.apache.sis.internal.util.Numerics;
import org.apache.sis.internal.util.Strings;
import org.apache.sis.internal.util.DoubleDouble;
import org.apache.sis.geometry.AbstractEnvelope;
import org.apache.sis.geometry.GeneralEnvelope;
import org.apache.sis.geometry.Envelopes;
import org.apache.sis.coverage.SubspaceNotSpecifiedException;
import org.apache.sis.referencing.operation.matrix.Matrices;
import org.apache.sis.referencing.operation.matrix.MatrixSIS;
import org.apache.sis.referencing.operation.transform.MathTransforms;
import org.apache.sis.referencing.operation.transform.TransformSeparator;
import org.apache.sis.math.MathFunctions;
import org.apache.sis.io.TableAppender;
import org.apache.sis.util.ArraysExt;
import org.apache.sis.util.ComparisonMode;
import org.apache.sis.util.LenientComparable;
import org.apache.sis.util.iso.Types;
import org.apache.sis.util.logging.Logging;
import org.apache.sis.internal.system.Modules;

import static java.util.logging.Logger.getLogger;

// Branch-dependent imports
import org.opengis.coverage.grid.GridEnvelope;
import org.opengis.coverage.grid.GridCoordinates;
import org.opengis.coverage.CannotEvaluateException;
import org.opengis.coverage.PointOutsideCoverageException;


/**
 * A range of grid coverage coordinates, also known as "grid envelope".
 * {@code GridExtent} are defined by {@linkplain #getLow() low} coordinates (often all zeros)
 * and {@linkplain #getHigh() high} coordinates, <strong>inclusive</strong>.
 * For example a grid with a width of 512 cells can have a low coordinate of 0 and high coordinate of 511.
 *
 * <div class="note"><b>Note:</b>
 * The inclusiveness of {@linkplain #getHigh() high} coordinates come from ISO 19123.
 * We follow this specification for all getters methods, but developers should keep in mind
 * that this is the opposite of Java2D usage where {@link Rectangle} maximal values are exclusive.</div>
 *
 * <p>{@code GridExtent} instances are immutable and thread-safe.
 * The same instance can be shared by different {@link GridGeometry} instances.</p>
 *
 * @author  Martin Desruisseaux (IRD, Geomatys)
 * @author  Alexis Manin (Geomatys)
 * @version 1.3
 * @since   1.0
 * @module
 */
public class GridExtent implements GridEnvelope, LenientComparable, Serializable {
    /**
     * Serial number for inter-operability with different versions.
     */
    private static final long serialVersionUID = -4717353677844056017L;

    /**
     * The dimension name types for given coordinate system axis directions.
     * This map contains only the "positive" axis directions.
     *
     * @todo Verify if there is more directions to add as of ISO 19111:2018.
     *
     * @see #typeFromAxes(CoordinateReferenceSystem, int)
     */
    private static final Map<AxisDirection,DimensionNameType> AXIS_DIRECTIONS;
    static {
        final Map<AxisDirection,DimensionNameType> dir = new HashMap<>(6);
        dir.put(AxisDirection.COLUMN_POSITIVE, DimensionNameType.COLUMN);
        dir.put(AxisDirection.ROW_POSITIVE,    DimensionNameType.ROW);
        dir.put(AxisDirection.UP,              DimensionNameType.VERTICAL);
        dir.put(AxisDirection.FUTURE,          DimensionNameType.TIME);
        AXIS_DIRECTIONS = dir;
    }

    /**
     * Default axis types for the two-dimensional cases.
     */
    private static final DimensionNameType[] DEFAULT_TYPES = new DimensionNameType[] {
        DimensionNameType.COLUMN,
        DimensionNameType.ROW
    };

    /**
     * A pool of shared {@link DimensionNameType} arrays. We use a pool
     * because a small amount of arrays is shared by most grid extents.
     */
    private static final WeakValueHashMap<DimensionNameType[],DimensionNameType[]> POOL = new WeakValueHashMap<>(DimensionNameType[].class);

    /**
     * Type of each axis (vertical, temporal, …) or {@code null} if unspecified.
     * If non-null, the array length shall be equal to {@link #getDimension()}.
     * Any array element may be null if unspecified for that particular axis.
     * The same array may be shared by many {@code GridExtent} instances.
     *
     * @see #getAxisType(int)
     */
    private final DimensionNameType[] types;

    /**
     * Minimum and maximum grid coordinates. The first half contains minimum coordinates (inclusive),
     * while the last half contains maximum coordinates (<strong>inclusive</strong>). Note that the
     * later inclusiveness is the opposite of Java2D usage but conforms to ISO specification.
     */
    private final long[] coordinates;

    /**
     * Creates a new array of coordinates with the given number of dimensions.
     *
     * @throws IllegalArgumentException if the given number of dimensions is excessive.
     */
    static long[] allocate(final int dimension) throws IllegalArgumentException {
        if (dimension >= Numerics.MAXIMUM_MATRIX_SIZE) {
            // Actually the real limit is Integer.MAX_VALUE / 2, but a value too high is likely to be an error.
            throw new IllegalArgumentException(Errors.format(Errors.Keys.ExcessiveNumberOfDimensions_1, dimension));
        }
        return new long[dimension << 1];
    }

    /**
     * Checks if coordinate values in the low part are less than or
     * equal to the corresponding coordinate value in the high part.
     *
     * @throws IllegalArgumentException if a coordinate value in the low part is
     *         greater than the corresponding coordinate value in the high part.
     */
    private void validateCoordinates() throws IllegalArgumentException {
        final int dimension = getDimension();
        for (int i=0; i<dimension; i++) {
            final long lower = coordinates[i];
            final long upper = coordinates[i + dimension];
            if (lower > upper) {
                throw new IllegalArgumentException(Resources.format(
                        Resources.Keys.IllegalGridEnvelope_3, getAxisIdentification(i,i), lower, upper));
            }
        }
    }

    /**
     * Verifies that the given array (if non-null) contains no duplicated values, then returns a copy of that array.
     * The returned copy may be shared by many {@code GridExtent} instances. Consequently it shall not be modified.
     *
     * @throws IllegalArgumentException if the given array contains duplicated elements.
     */
    @SuppressWarnings("ReturnOfCollectionOrArrayField")
    private static DimensionNameType[] validateAxisTypes(DimensionNameType[] types) throws IllegalArgumentException {
        if (types == null) {
            return null;
        }
        if (Arrays.equals(DEFAULT_TYPES, types)) {          // Common case verified before POOL synchronized lock.
            return DEFAULT_TYPES;
        }
        DimensionNameType[] shared = POOL.get(types);
        if (shared == null) {
            /*
             * Verify the array only if it was not found in the pool. Arrays in the pool were already validated,
             * so do not need to be verified again. The check performed here is inefficient (nested loop), but it
             * should be okay since the arrays are usually small (less than 5 elements) and the checks should not
             * be done often (because of the pool).
             */
            types = types.clone();
            for (int i=1; i<types.length; i++) {
                final DimensionNameType t = types[i];
                if (t != null) {
                    for (int j=i; --j >= 0;) {
                        if (t.equals(types[j])) {
                            throw new IllegalArgumentException(Errors.format(Errors.Keys.DuplicatedElement_1, t));
                        }
                    }
                }
            }
            shared = POOL.putIfAbsent(types, types);
            if (shared == null) {
                return types;
            }
        }
        return shared;
    }

    /**
     * Creates an initially empty grid extent with the given number of dimensions.
     * All grid coordinate values are initialized to zero. This constructor is private
     * because {@code GridExtent} coordinate values can not be modified by public API.
     *
     * @param dimension  number of dimensions.
     * @param axisTypes  the axis types, or {@code null} if unspecified.
     *
     * @see #GridExtent(GridExtent)
     */
    private GridExtent(final int dimension, final DimensionNameType[] axisTypes) {
        coordinates = allocate(dimension);
        types = validateAxisTypes(axisTypes);
    }

    /**
     * Creates a new grid extent for an image or matrix of the given bounds.
     * The axis types are {@link DimensionNameType#COLUMN} and {@link DimensionNameType#ROW ROW} in that order.
     *
     * @param  bounds  the bounds to copy in the new grid extent.
     *
     * @since 1.1
     */
    public GridExtent(final Rectangle bounds) {
        this(bounds.width, bounds.height);
        translate2D(bounds.x, bounds.y);
    }

    /**
     * Creates a new grid extent for an image or matrix of the given size.
     * The {@linkplain #getLow() low} grid coordinates are zeros and the axis types are
     * {@link DimensionNameType#COLUMN} and {@link DimensionNameType#ROW ROW} in that order.
     *
     * @param  width   number of pixels in each row.
     * @param  height  number of pixels in each column.
     */
    public GridExtent(final long width, final long height) {
        ArgumentChecks.ensureStrictlyPositive("width",  width);
        ArgumentChecks.ensureStrictlyPositive("height", height);
        coordinates = new long[4];
        coordinates[2] = width  - 1;
        coordinates[3] = height - 1;
        types = DEFAULT_TYPES;
    }

    /**
     * Creates a new grid extent for an image of the given size and location. This constructor
     * is for internal usage: argument meanings differ from conventions in public constructors.
     *
     * @param  xmin    column index of the first cell.
     * @param  ymin    row index of the first cell.
     * @param  width   number of pixels in each row.
     * @param  height  number of pixels in each column.
     */
    GridExtent(final int xmin, final int ymin, final int width, final int height) {
        this(width, height);
        translate2D(xmin, ymin);
    }

    /**
     * Completes a {@link GridExtent} construction with a final translation.
     * Shall be invoked for two-dimensional extents only.
     */
    private void translate2D(final long xmin, final long ymin) {
        for (int i=coordinates.length; --i >= 0;) {
            coordinates[i] += ((i & 1) == 0) ? xmin : ymin;
        }
    }

    /**
     * Constructs a new grid extent set to the specified coordinates.
     * The given arrays contain a minimum (inclusive) and maximum value for each dimension of the grid coverage.
     * The lowest valid grid coordinates are often zero, but this is not mandatory.
     * As a convenience for this common case, a null {@code low} array means that all low coordinates are zero.
     *
     * <p>An optional (nullable) {@code axisTypes} argument can be used for attaching a label to each grid axis.
     * For example if this {@code GridExtent} is four-dimensional, then the axis types may be
     * {{@linkplain DimensionNameType#COLUMN   column}   (<var>x</var>),
     *  {@linkplain DimensionNameType#ROW      row}      (<var>y</var>),
     *  {@linkplain DimensionNameType#VERTICAL vertical} (<var>z</var>),
     *  {@linkplain DimensionNameType#TIME     time}     (<var>t</var>)},
     * which means that the last axis is for the temporal dimension, the third axis is for the vertical dimension, <i>etc.</i>
     * This information is related to the "real world" coordinate reference system axes, but not necessarily in the same order;
     * it is caller responsibility to ensure that the grid axes are consistent with the CRS axes.
     * The {@code axisTypes} array shall not contain duplicated elements,
     * but may contain {@code null} elements if the type of some axes are unknown.</p>
     *
     * @param  axisTypes       the type of each grid axis, or {@code null} if unspecified.
     * @param  low             the valid minimum grid coordinates (always inclusive), or {@code null} for all zeros.
     * @param  high            the valid maximum grid coordinates, inclusive or exclusive depending on the next argument.
     * @param  isHighIncluded  {@code true} if the {@code high} values are inclusive (as in ISO 19123 specification),
     *                         or {@code false} if they are exclusive (as in Java2D usage).
     *                         This argument does not apply to {@code low} values, which are always inclusive.
     * @throws IllegalArgumentException if a coordinate value in the low part is
     *         greater than the corresponding coordinate value in the high part.
     *
     * @see #getLow()
     * @see #getHigh()
     * @see #insertDimension(int, DimensionNameType, long, long, boolean)
     */
    public GridExtent(final DimensionNameType[] axisTypes, final long[] low, final long[] high, final boolean isHighIncluded) {
        ArgumentChecks.ensureNonNull("high", high);
        final int dimension = high.length;
        if (low != null && low.length != dimension) {
            throw new IllegalArgumentException(Errors.format(Errors.Keys.MismatchedDimension_2, low.length, dimension));
        }
        if (axisTypes != null && axisTypes.length != dimension) {
            throw new IllegalArgumentException(Errors.format(Errors.Keys.MismatchedArrayLengths));
        }
        coordinates = allocate(dimension);
        if (low != null) {
            System.arraycopy(low, 0, coordinates, 0, dimension);
        }
        System.arraycopy(high, 0, coordinates, dimension, dimension);
        if (!isHighIncluded) {
            for (int i=dimension; i < coordinates.length; i++) {
                coordinates[i] = Math.decrementExact(coordinates[i]);
            }
        }
        types = validateAxisTypes(axisTypes);
        validateCoordinates();
    }

    /**
     * Infers the axis types from the given coordinate reference system.
     * This method is the converse of {@link GridExtentCRS}.
     *
     * @param  crs        the coordinate reference system, or {@code null}.
     * @param  dimension  number of name type to infer. Shall not be greater than the CRS dimension.
     * @return axis types, or {@code null} if no axis were recognized.
     */
    static DimensionNameType[] typeFromAxes(final CoordinateReferenceSystem crs, final int dimension) {
        DimensionNameType[] axisTypes = null;
        if (crs != null) {
            final CoordinateSystem cs = crs.getCoordinateSystem();
            for (int i=0; i<dimension; i++) {
                final DimensionNameType type = AXIS_DIRECTIONS.get(AxisDirections.absolute(cs.getAxis(i).getDirection()));
                if (type != null) {
                    if (axisTypes == null) {
                        axisTypes = new DimensionNameType[dimension];
                    }
                    axisTypes[i] = type;
                }
            }
        }
        return axisTypes;
    }

    /**
     * Creates a new grid extent by rounding the given envelope to (usually) nearest integers.
     * The envelope coordinates shall be cell indices with lower values inclusive and upper values exclusive.
     * {@link Double#NaN} envelope coordinates will be set to the corresponding {@code enclosing} coordinates
     * (an exception will be thrown if {@code enclosing} is null in that situation).
     *
     * Envelopes crossing the anti-meridian shall be {@linkplain GeneralEnvelope#simplify() simplified}.
     * The envelope CRS is ignored, except for identifying dimension names for information purpose.
     * The way floating point values are rounded to integers may be adjusted in any future version.
     *
     * <p><b>API note:</b> this constructor is not public because its contract is a bit approximate.</p>
     *
     * @param  envelope            the envelope containing cell indices to store in a {@code GridExtent}.
     * @param  rounding            controls behavior of rounding from floating point values to integers.
     * @param  clipping            how to clip this extent to the enclosing extent. Ignored if {@code enclosing} is null.
     * @param  margin              if non-null, expands the extent by that amount of cells on each envelope dimension.
     * @param  chunkSize           if non-null, make the grid extent spanning an integer amount of chunks (tiles).
     * @param  enclosing           if the new grid is a sub-grid of a larger grid, that larger grid. Otherwise {@code null}.
     * @param  modifiedDimensions  if {@code enclosing} is non-null, the grid dimensions to set from the envelope.
     *                             The length of this array shall be equal to the {@code envelope} dimension.
     *                             This argument is ignored if {@code enclosing} is null.
     * @throws DisjointExtentException if the given envelope does not intersect the enclosing grid extent.
     *
     * @see #toEnvelope(MathTransform, MathTransform, Envelope)
     * @see #slice(DirectPosition, int[])
     */
    GridExtent(final AbstractEnvelope envelope, final GridRoundingMode rounding, final GridClippingMode clipping,
               final int[] margin, final int[] chunkSize, final GridExtent enclosing, final int[] modifiedDimensions)
    {
        final int dimension = envelope.getDimension();
        coordinates = (enclosing != null) ? enclosing.coordinates.clone() : allocate(dimension);
        /*
         * Assign the `types` field before we try to compute the grid extent coordinates
         * because if the coordinate computation fail, `getAxisIdentification(…)` uses
         * that information for producing a more informative error message if possible.
         */
        if (enclosing != null && enclosing.types != null) {
            types = enclosing.types;
        } else {
            types = validateAxisTypes(typeFromAxes(envelope.getCoordinateReferenceSystem(), dimension));
        }
        /*
         * Now computes the grid extent coordinates.
         */
        for (int i=0; i<dimension; i++) {
            double min = envelope.getLower(i);                      // Inclusive
            double max = envelope.getUpper(i);                      // Exclusive
            final boolean isMinValid = (min >= Long.MIN_VALUE);
            final boolean isMaxValid = (max <= Long.MAX_VALUE);
            if (min > max || (enclosing == null && !(isMinValid & isMaxValid))) {
                /*
                 * We do not throw an exception for NaN envelope bounds if `enclosing` is non-null
                 * because this case occurs when the `gridToCRS` transform has a NaN scale factor.
                 * Such scale factor may result from ranges like [0 … 0]. We tolerate them because
                 * with a non-null `enclosing` extent, we can still have grid coordinates: they are
                 * inherited from `enclosing`. Note that we require the two bounds to be NaN, because
                 * otherwise the reason for those NaN envelope bounds is not a NaN scale factor.
                 */
                throw new IllegalArgumentException(Resources.format(
                        Resources.Keys.IllegalGridEnvelope_3, getAxisIdentification(i,i), min, max));
            }
            if (!isMinValid) min = Long.MIN_VALUE;
            if (!isMaxValid) max = Long.MAX_VALUE;
            long lower, upper;                                                  // Both inclusive (upper as well).
            switch (rounding) {
                default: {
                    throw new AssertionError(rounding);
                }
                case ENCLOSING: {
                    lower = (long) Math.floor(min);
                    upper = (long) Math.ceil (max);
                    if (lower != upper) upper--;                                // For making the coordinate inclusive.
                    break;
                }
                case CONTAINED: {
                    final double lo = Math.ceil (min);
                    final double hi = Math.floor(max);
                    if (lo > hi) {
                        lower = (long) ((lo - min > max - hi) ? hi : lo);       // Take the value closest to integer.
                        upper = lower;
                    } else {
                        lower = (long) lo;
                        upper = (long) hi;
                        if (lower != upper) upper--;                            // For making the coordinate inclusive.
                    }
                    break;
                }
                case NEAREST: {
                    lower = Math.round(min);
                    upper = Math.round(max);
                    if (lower == upper) {                                       // Equality implies (max - min) < 1.
                        if (min - Math.floor(min) > Math.ceil(max) - max) {
                            upper = --lower;
                        }
                    } else {
                        upper--;                                                // For making the coordinate inclusive.
                        /*
                         * The [lower … upper] range may be slightly larger than desired in some rounding error situations.
                         * For example if `min` was 1.49999 and `max` was 2.50001,  the rounding will create a [1…3] range
                         * while there is actually only 2 pixels. We detect those rounding problems by comparing the spans
                         * before and after rounding.  We attempt an adjustment only if the span mismatch is ±1, otherwise
                         * the difference is assumed to be caused by overflow. On the three values that can be affected by
                         * the adjustment (min, max and span), we change only the number which is farthest from an integer
                         * value.
                         */
                        long delta = (upper - lower) + 1;                       // Negative number if overflow.
                        if (delta >= 0) {
                            final double span = envelope.getSpan(i);
                            final long extent = Math.round(span);
                            if (extent != 0 && Math.abs(delta -= extent) == 1) {
                                final double dmin = Math.abs(min - Math.rint(min));
                                final double dmax = Math.abs(max - Math.rint(max));
                                final boolean adjustMax = (dmax >= dmin);
                                if (Math.abs(span - extent) < (adjustMax ? dmax : dmin)) {
                                    if (adjustMax) upper = Math.subtractExact(upper, delta);
                                    else lower = Math.addExact(lower, delta);
                                }
                            }
                        }
                    }
                }
            }
            /*
             * If caller requested to clip only the user Area Of Interest (AOI) without constraining the
             * margin or chunk size, then we need to do clipping now instead of at the end of this loop.
             */
            if (enclosing != null && clipping == GridClippingMode.BORDER_EXPANSION) {
                final int  lo = (modifiedDimensions != null) ? modifiedDimensions[i] : i;
                final int  hi = lo + getDimension();
                final long lv = Math.max(lower, coordinates[lo]);
                final long hv = Math.min(upper, coordinates[hi]);
                if (lv > hv) {
                    throw new DisjointExtentException(getAxisIdentification(lo, i),
                                        coordinates[lo], coordinates[hi], lv, hv);
                }
                lower = lv;
                upper = hv;
            }
            /*
             * If the user specified a margin, add it now. The margin dimension indices follow the envelope
             * dimension indices.  Note that the resulting extent will be intersected with enclosing extent
             * at a next step, which may cancel the margin effect.
             *
             * Note about overflow checks: if m>0, then x < x+m unless the result overflows the `long` capacity.
             */
            if (margin != null && i < margin.length) {
                final int m = margin[i];
                if (enclosing != null && m > 0) {
                    if (lower < (lower -= m)) lower = Long.MIN_VALUE;       // Clamp to MIN/MAX if overflow.
                    if (upper > (upper += m)) upper = Long.MAX_VALUE;
                } else {
                    lower = Math.subtractExact(lower, m);
                    upper = Math.addExact(upper, m);
                }
            }
            if (lower > upper) {
                upper += (lower - upper) >>> 1;         // (lower - upper)/2 as unsigned integer: overflow-safe.
                lower = upper;
            }
            /*
             * If chunk size has been specified, snap the coordinates to a multiple of that size.
             * The new extent will be clipped with `enclosing` (if non-null) in next step.
             * Note: formulas used here are the same than in `forChunkSize(…)` method.
             */
            if (chunkSize != null && i < chunkSize.length) {
                final int s = chunkSize[i];
                lower = Math.subtractExact(lower, Math.floorMod(lower, s));
                upper = Math.addExact(upper, (s-1) - Math.floorMod(upper, s));
            }
            /*
             * At this point the grid range has been computed (lower to upper). Compute intersection,
             * then update the coordinates accordingly. Note that if envelope coordinates were NaN,
             * they will have been replaced by `Long.MIN/MAX_VALUE`, which will usually cause the
             * assignation to be skipt below (so we keep the values inherited from `enclosing`).
             */
            if (enclosing != null && clipping == GridClippingMode.STRICT) {
                final int lo = (modifiedDimensions != null) ? modifiedDimensions[i] : i;
                final int hi = lo + getDimension();
                final long validMin = coordinates[lo];
                final long validMax = coordinates[hi];
                if (lower > validMin) coordinates[lo] = lower;
                if (upper < validMax) coordinates[hi] = upper;
                if (lower > validMax || upper < validMin) {
                    throw new DisjointExtentException(getAxisIdentification(lo, i), validMin, validMax, lower, upper);
                }
            } else {
                coordinates[i] = lower;
                coordinates[i + getDimension()] = upper;
            }
        }
    }

    /**
     * Creates a new grid extent with the same axes than the given extent, but different coordinates.
     * This constructor does not invoke {@link #validateCoordinates()}; we presume that the caller's
     * computation is correct.
     *
     * @param enclosing    the extent from which to copy axes, or {@code null} if none.
     * @param coordinates  the coordinates. This array is not cloned.
     */
    GridExtent(final GridExtent enclosing, final long[] coordinates) {
        this.coordinates = coordinates;
        types = (enclosing != null) ? enclosing.types : null;
        assert (types == null) || types.length == getDimension();
    }

    /**
     * Creates a copy of the given grid extent. The {@link #coordinates} array is cloned
     * while the {@link #types} array is shared between the two instances. This constructor
     * is reserved to methods that modify the coordinates after construction. It must be
     * private because we do not allow coordinates modifications by public API.
     *
     * @see #GridExtent(int, DimensionNameType[])
     */
    private GridExtent(final GridExtent extent) {
        types = extent.types;
        coordinates = extent.coordinates.clone();
    }

    /**
     * Creates a new grid extent as a copy of the given one.
     *
     * @param  extent  the grid extent to copy.
     * @throws IllegalArgumentException if a coordinate value in the low part is
     *         greater than the corresponding coordinate value in the high part.
     *
     * @see #castOrCopy(GridEnvelope)
     */
    protected GridExtent(final GridEnvelope extent) {
        ArgumentChecks.ensureNonNull("extent", extent);
        final int dimension = extent.getDimension();
        coordinates = allocate(dimension);
        for (int i=0; i<dimension; i++) {
            coordinates[i] = extent.getLow(i);
            coordinates[i + dimension] = extent.getHigh(i);
        }
        types = (extent instanceof GridExtent) ? ((GridExtent) extent).types : null;
        validateCoordinates();
    }

    /**
     * Returns the given grid extent as a {@code GridExtent} implementation.
     * If the given extent is already a {@code GridExtent} instance or is null, then it is returned as-is.
     * Otherwise a new extent is created using the {@linkplain #GridExtent(GridEnvelope) copy constructor}.
     *
     * @param  extent  the grid extent to cast or copy, or {@code null}.
     * @return the grid extent as a {@code GridExtent}, or {@code null} if the given extent was null.
     */
    public static GridExtent castOrCopy(final GridEnvelope extent) {
        if (extent == null || extent instanceof GridExtent) {
            return (GridExtent) extent;
        } else {
            return new GridExtent(extent);
        }
    }

    /**
     * Returns the number of dimensions.
     *
     * @return the number of dimensions.
     *
     * @see #reduceDimension(int[])
     */
    @Override
    public final int getDimension() {
        return coordinates.length >>> 1;
    }

    /**
     * Returns the number of dimensions where this grid extent has a size greater than 1.
     * This is a value between 0 and {@link #getDimension()} inclusive.
     *
     * @return the number of dimensions where this grid extent has a size greater than 1.
     *
     * @see #getSubspaceDimensions(int)
     */
    final int getSubDimension() {
        int n = 0;
        final int dimension = getDimension();
        for (int i=0; i<dimension; i++) {
            if (coordinates[i] != coordinates[i + dimension]) n++;
        }
        return n;
    }

    /**
     * Returns {@code true} if all low coordinates are zero.
     * This is a very common case since many grids start their cell numbering at zero.
     *
     * @return whether all low coordinates are zero.
     *
     * @see #translate(long...)
     */
    public boolean startsAtZero() {
        return isZero(coordinates, getDimension());
    }

    /**
     * Returns {@code true} if all values in the given vector are zero.
     *
     * @param  vector  the vector to verify.
     * @param  n       number of elements to verify. All remaining elements are ignored.
     * @return whether the <var>n</var> first elements in the given array are all zero.
     */
    private static boolean isZero(final long[] vector, int n) {
        while (--n >= 0) {
            if (vector[n] != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the valid minimum grid coordinates, inclusive.
     * The sequence contains a minimum value for each dimension of the grid coverage.
     *
     * @return the valid minimum grid coordinates, inclusive.
     *
     * @see #getLow(int)
     */
    @Override
    public GridCoordinates getLow() {
        return new GridCoordinatesView(coordinates, 0);
    }

    /**
     * Returns the valid maximum grid coordinates, <strong>inclusive</strong>.
     * The sequence contains a maximum value for each dimension of the grid coverage.
     *
     * @return the valid maximum grid coordinates, <strong>inclusive</strong>.
     *
     * @see #getHigh(int)
     */
    @Override
    public GridCoordinates getHigh() {
        return new GridCoordinatesView(coordinates, getDimension());
    }

    /**
     * Returns the valid minimum inclusive grid coordinate along the specified dimension.
     *
     * @param  index  the dimension for which to obtain the coordinate value.
     * @return the low coordinate value at the given dimension, inclusive.
     * @throws IndexOutOfBoundsException if the given index is negative or is equal or greater
     *         than the {@linkplain #getDimension() grid dimension}.
     *
     * @see #getLow()
     * @see #getHigh(int)
     * @see #withRange(int, long, long)
     */
    @Override
    public long getLow(final int index) {
        ArgumentChecks.ensureValidIndex(getDimension(), index);
        return coordinates[index];
    }

    /**
     * Returns the valid maximum <strong>inclusive</strong> grid coordinate along the specified dimension.
     *
     * @param  index  the dimension for which to obtain the coordinate value.
     * @return the high coordinate value at the given dimension, <strong>inclusive</strong>.
     * @throws IndexOutOfBoundsException if the given index is negative or is equal or greater
     *         than the {@linkplain #getDimension() grid dimension}.
     *
     * @see #getHigh()
     * @see #getLow(int)
     * @see #withRange(int, long, long)
     */
    @Override
    public long getHigh(final int index) {
        final int dimension = getDimension();
        ArgumentChecks.ensureValidIndex(dimension, index);
        return coordinates[index + dimension];
    }

    /**
     * Returns the number of integer grid coordinates along the specified dimension.
     * This is equal to {@code getHigh(dimension) - getLow(dimension) + 1}.
     *
     * @param  index  the dimension for which to obtain the size.
     * @return the number of integer grid coordinates along the given dimension.
     * @throws IndexOutOfBoundsException if the given index is negative or is equal or greater
     *         than the {@linkplain #getDimension() grid dimension}.
     * @throws ArithmeticException if the size is too large for the {@code long} primitive type.
     *
     * @see #getLow(int)
     * @see #getHigh(int)
     * @see #resize(long...)
     */
    @Override
    public long getSize(final int index) {
        final int dimension = getDimension();
        ArgumentChecks.ensureValidIndex(dimension, index);
        return Math.incrementExact(Math.subtractExact(coordinates[dimension + index], coordinates[index]));
    }

    /**
     * Returns the number of grid coordinates as a double precision floating point value.
     * Invoking this method is equivalent to invoking {@link #getSize(int)} and converting
     * the result from {@code long} to the {@code double} primitive type, except that this
     * method does not overflow (i.e. does not throw {@link ArithmeticException}).
     *
     * @param  index     the dimension for which to obtain the size.
     * @param  minusOne  {@code true} for returning <var>size</var>−1 instead of <var>size</var>.
     * @return the number of integer grid coordinates along the given dimension.
     */
    public double getSize(final int index, final boolean minusOne) {
        final int dimension = getDimension();
        ArgumentChecks.ensureValidIndex(dimension, index);
        long size = coordinates[dimension + index] - coordinates[index];        // Unsigned long.
        if (!minusOne && ++size == 0) {
            return 0x1P64;                          // Unsigned integer overflow. Result is 2^64.
        }
        return Numerics.toUnsignedDouble(size);
    }

    /**
     * @deprecated Replaced by {@link #getPointOfInterest(PixelInCell)}.
     *
     * @return the grid coordinates of a representative point.
     */
    @Deprecated
    public double[] getPointOfInterest() {
        return getPointOfInterest(PixelInCell.CELL_CORNER);
    }

    /**
     * Returns the grid coordinates of a representative point.
     * This point may be used for estimating a {@linkplain GridGeometry#getResolution(boolean) grid resolution}.
     * The default implementation returns the median (or center) coordinates of this grid extent,
     * but subclasses can override this method if another point is considered more representative.
     *
     * <p>The {@code anchpr} argument tells {@linkplain GridGeometry#getGridToCRS(PixelInCell) which transform}
     * the caller intend to use for converting the grid coordinates to "real world" coordinates.
     * With the default implementation, the coordinate values returned with {@code CELL_CORNER}
     * are 0.5 cell units higher than the coordinate values returned with {@code CELL_CENTER}.
     * Subclasses are free to ignore this argument.</p>
     *
     * @param  anchor  the convention to be used for conversion to "real world" coordinates.
     * @return the grid coordinates of a representative point.
     *
     * @since 1.3
     */
    public double[] getPointOfInterest(final PixelInCell anchor) {
        final int dimension = getDimension();
        final double[] center = new double[dimension];
        final boolean isCorner = PixelInCell.CELL_CORNER.equals(anchor);
        for (int i=0; i<dimension; i++) {
            /*
             * We want the average of (low + hi+1). However for the purpose of computing an average, it does
             * not matter if we add 1 to `low` or `hi`. So we add 1 to `low` because it should not overflow.
             */
            long low = coordinates[i];
            if (isCorner) {
                low = Math.incrementExact(coordinates[i]);
            }
            center[i] = MathFunctions.average(low, coordinates[i + dimension]);
        }
        return center;
    }

    /**
     * Returns the grid coordinates for all dimensions where the grid has a size of 1.
     * Keys are dimensions as values from 0 inclusive to {@link #getDimension()} exclusive.
     * Values are the {@linkplain #getLow(int) low} and {@linkplain #getHigh(int) high} coordinates
     * (which are equal) in the associated dimension.
     *
     * @return grid coordinates for all dimensions where the grid has a size of 1.
     *
     * @see GridEvaluator#setDefaultSlice(Map)
     *
     * @since 1.3
     */
    public SortedMap<Integer,Long> getSliceCoordinates() {
        final TreeMap<Integer,Long> slice = new TreeMap<>();
        final int dimension = getDimension();
        for (int i=0; i<dimension; i++) {
            final long value = coordinates[i];
            if (value == coordinates[i + dimension]) {
                slice.put(i, value);
            }
        }
        return slice;
    }

    /**
     * Returns indices of all dimensions where this grid extent has a size greater than 1.
     * This method can be used for getting the grid extent of a <var>s</var>-dimensional slice
     * in a <var>n</var>-dimensional cube where <var>s</var> ≤ <var>n</var>.
     *
     * <div class="note"><b>Example:</b>
     * suppose that we want to get a two-dimensional slice <var>(y,z)</var> in a four-dimensional data cube <var>(x,y,z,t)</var>.
     * The first step is to specify the <var>x</var> and <var>t</var> coordinates of the slice.
     * In this example we set <var>x</var> to 5 and <var>t</var> to 8.
     *
     * {@preformat java
     *     GridGeometry grid = ...;             // Geometry of the (x,y,z,t) grid.
     *     GridGeometry slice4D = grid.slice(new GeneralDirectPosition(5, NaN, NaN, 8));
     * }
     *
     * Above code created a slice at the requested position, but that slice still have 4 dimensions.
     * It is a "slice" because the <var>x</var> and <var>t</var> dimensions of {@code slice4D} have only one cell.
     * If a two-dimensional slice is desired, then above operations can be completed as below.
     * In this example, the result of {@code getSubspaceDimensions(2)} call will be {1,2}.
     *
     * {@preformat java
     *     int[]  subDimensions = slice4D.getExtent().getSubspaceDimensions(2);
     *     GridGeometry slice2D = slice4D.reduce(subDimensions);
     * }
     *
     * Note that in this particular example, it would have been more efficient to execute {@code grid.reduce(1,2)} directly.
     * This {@code getSubspaceDimensions(int)} method is more useful for inferring a {@code slice2D} from a {@code slice4D}
     * which has been created elsewhere, or when we do not really want the {@code slice2D} but only its dimension indices.
     * </div>
     *
     * This method returns exactly <var>s</var> indices. If there is more than <var>s</var> dimensions having a
     * {@linkplain #getSize(int) size} greater than 1, then a {@link SubspaceNotSpecifiedException} is thrown.
     * If there is less than <var>s</var> dimensions having a size greater than 1, then the returned list of
     * dimensions is completed with some dimensions of size 1, starting with the first dimensions in this grid
     * extent, until there is exactly <var>s</var> dimensions. If this grid extent does not have at least
     * <var>s</var> dimensions, then a {@link CannotEvaluateException} is thrown.
     *
     * @param  s  number of dimensions of the sub-space.
     * @return indices of sub-space dimensions, in increasing order in an array of length <var>s</var>.
     * @throws SubspaceNotSpecifiedException if there is more than <var>s</var> dimensions having a size greater than 1.
     * @throws CannotEvaluateException if this grid extent does not have at least <var>s</var> dimensions.
     */
    public int[] getSubspaceDimensions(final int s) {
        ArgumentChecks.ensurePositive("s", s);
        final int m = getDimension();
        if (s > m) {
            throw new CannotEvaluateException(Resources.format(Resources.Keys.GridEnvelopeMustBeNDimensional_1, s));
        }
        final int[] selected = new int[s];
        int count = 0;
        for (int i=0; i<m; i++) {
            final long low  = coordinates[i];
            final long high = coordinates[i+m];
            if (low != high) {
                if (count < s) {
                    selected[count++] = i;
                } else {
                    long size = high - low;
                    if (size != -1) size++;     // When interpreted as unsigned long, -1 is the maximal value.
                    throw new SubspaceNotSpecifiedException(Resources.format(Resources.Keys.NoNDimensionalSlice_3,
                                s, getAxisIdentification(i,i), Numerics.toUnsignedDouble(size)));
                }
            }
        }
        if (s != count) {
            for (int i=0; ; i++) {
                // An IndexOutOfBoundsException would be a bug in our algorithm.
                if (coordinates[i] == coordinates[i+m]) {
                    selected[count++] = i;
                    if (count == s) break;
                }
            }
            Arrays.sort(selected);
        }
        return selected;
    }

    /**
     * Returns the type (vertical, temporal, …) of grid axis at given dimension.
     * This information is provided because the grid axis type can not always be inferred from the context.
     * Some examples are:
     *
     * <ul>
     *   <li>{@code getAxisType(0)} may return {@link DimensionNameType#COLUMN},
     *       {@link DimensionNameType#TRACK TRACK} or {@link DimensionNameType#LINE LINE}.</li>
     *   <li>{@code getAxisType(1)} may return {@link DimensionNameType#ROW},
     *       {@link DimensionNameType#CROSS_TRACK CROSS_TRACK} or {@link DimensionNameType#SAMPLE SAMPLE}.</li>
     *   <li>{@code getAxisType(2)} may return {@link DimensionNameType#VERTICAL}.</li>
     *   <li>{@code getAxisType(3)} may return {@link DimensionNameType#TIME}.</li>
     * </ul>
     *
     * Above are only examples; there are no constraints on axis order. In particular grid axes do not need to be in the same
     * order than the corresponding {@linkplain GridGeometry#getCoordinateReferenceSystem() coordinate reference system} axes.
     *
     * @param  index  the dimension for which to obtain the axis type.
     * @return the axis type at the given dimension. May be absent if the type is unknown.
     * @throws IndexOutOfBoundsException if the given index is negative or is equal or greater
     *         than the {@linkplain #getDimension() grid dimension}.
     */
    public Optional<DimensionNameType> getAxisType(final int index) {
        ArgumentChecks.ensureValidIndex(getDimension(), index);
        return Optional.ofNullable((types != null) ? types[index] : null);
    }

    /**
     * Returns the {@link #types} array or a default array of arbitrary length if {@link #types} is null.
     * This method returns directly the arrays without cloning; do not modify.
     */
    final DimensionNameType[] getAxisTypes() {
        return (types != null) ? types : DEFAULT_TYPES;
    }

    /**
     * Returns the axis number followed by the localized axis type if available.
     * This is used for error messages only.
     *
     * @param  index       index of the dimension as stored in this grid extent.
     * @param  indexShown  index to write in the message. Often the same as {@code index}.
     */
    final Object getAxisIdentification(final int index, final int indexShown) {
        if (types != null) {
            final DimensionNameType type = types[index];
            if (type != null) {
                return indexShown + " (" + Types.getCodeTitle(type) + ')';
            }
        }
        return indexShown;
    }

    /**
     * Returns a grid extent identical to this grid extent except for the coordinate values in the specified dimension.
     * This grid extent is not modified.
     *
     * @param  index  the dimension for which to set the coordinate values.
     * @param  low    the low coordinate value at the given dimension, inclusive.
     * @param  high   the high coordinate value at the given dimension, <strong>inclusive</strong>.
     * @return a grid extent with the specified coordinate values, or {@code this} if values are unchanged.
     * @throws IllegalArgumentException if the low coordinate value is greater than the high coordinate value.
     *
     * @see #getLow(int)
     * @see #getHigh(int)
     *
     * @since 1.3
     */
    public GridExtent withRange(final int index, final long low, final long high) {
        int ih = getDimension();
        ArgumentChecks.ensureValidIndex(ih, index);
        ih += index;
        if (coordinates[index] == low && coordinates[ih] == high) {
            return this;
        }
        if (low > high) {
            throw new IllegalArgumentException(Resources.format(
                    Resources.Keys.IllegalGridEnvelope_3, getAxisIdentification(index, index), low, high));
        }
        final GridExtent copy = new GridExtent(this);
        copy.coordinates[index] = low;
        copy.coordinates[ih] = high;
        return copy;
    }

    /**
     * Transforms this grid extent to a "real world" envelope using the given transform.
     * The transform shall map <em>cell corner</em> to real world coordinates.
     *
     * @param  cornerToCRS  a transform from <em>cell corners</em> to real world coordinates.
     * @return this grid extent in real world coordinates.
     * @throws TransformException if the envelope can not be computed with the given transform.
     *
     * @see GridGeometry#getEnvelope()
     * @see PixelInCell#CELL_CORNER
     *
     * @since 1.1
     */
    public GeneralEnvelope toEnvelope(final MathTransform cornerToCRS) throws TransformException {
        ArgumentChecks.ensureNonNull("cornerToCRS", cornerToCRS);
        final GeneralEnvelope envelope = toEnvelope(cornerToCRS, cornerToCRS, null);
        final Matrix gridToCRS = MathTransforms.getMatrix(cornerToCRS);
        if (gridToCRS != null && Matrices.isAffine(gridToCRS)) try {
            envelope.setCoordinateReferenceSystem(GridExtentCRS.forExtentAlone(gridToCRS, getAxisTypes()));
        } catch (FactoryException e) {
            throw new TransformException(e);
        }
        return envelope;
    }

    /**
     * Transforms this grid extent to a "real world" envelope using the given transform.
     * The transform shall map <em>cell corner</em> to real world coordinates.
     * This method does not set the envelope coordinate reference system.
     *
     * @param  cornerToCRS  a transform from <em>cell corners</em> to real world coordinates.
     * @param  gridToCRS    the transform specified by the user. May be the same as {@code cornerToCRS}.
     *                      If different, then this is assumed to map cell centers instead of cell corners.
     * @param  fallback     bounds to use if some values are still NaN after conversion, or {@code null} if none.
     * @return this grid extent in real world coordinates.
     * @throws TransformException if the envelope can not be computed with the given transform.
     *
     * @see #GridExtent(AbstractEnvelope, GridRoundingMode, int[], GridExtent, int[])
     *
     * @see GridGeometry#getEnvelope(CoordinateReferenceSystem)
     */
    final GeneralEnvelope toEnvelope(final MathTransform cornerToCRS, final MathTransform gridToCRS, final Envelope fallback)
            throws TransformException
    {
        final GeneralEnvelope envelope = Envelopes.transform(cornerToCRS, toEnvelope());
        complete(envelope, gridToCRS, gridToCRS != cornerToCRS, fallback);
        return envelope;
    }

    /**
     * Returns the coordinates of this grid extent in an envelope.
     * The returned envelope has no CRS.
     */
    final GeneralEnvelope toEnvelope() {
        final int dimension = getDimension();
        final GeneralEnvelope envelope = new GeneralEnvelope(dimension);
        for (int i=0; i<dimension; i++) {
            long high = coordinates[i + dimension];
            if (high != Long.MAX_VALUE) high++;             // Make the coordinate exclusive before cast.
            envelope.setRange(i, coordinates[i], high);     // Possible loss of precision in cast to `double` type.
        }
        return envelope;
    }

    /**
     * Transforms this grid extent to "real world" envelopes using the given transform.
     * This method usually returns exactly one envelope, but may return more envelopes if the given transform
     * contains at least one {@link org.apache.sis.referencing.operation.transform.WraparoundTransform} step.
     *
     * @param  cornerToCRS  a transform from <em>cell corners</em> to real world coordinates.
     * @param  gridToCRS    the transform specified by the user. May be the same as {@code cornerToCRS}.
     *                      If different, then this is assumed to map cell centers instead of cell corners.
     * @param  fallback     bounds to use if some values are still NaN after conversion, or {@code null} if none.
     * @return this grid extent in real world coordinates.
     * @throws TransformException if the envelope can not be computed with the given transform.
     *
     * @see #GridExtent(AbstractEnvelope, GridRoundingMode, int[], GridExtent, int[])
     *
     * @see GridGeometry#getEnvelope(CoordinateReferenceSystem)
     */
    final GeneralEnvelope[] toEnvelopes(final MathTransform cornerToCRS, final MathTransform gridToCRS, final Envelope fallback)
            throws TransformException
    {
        final GeneralEnvelope[] envelopes = Envelopes.wraparound(cornerToCRS, toEnvelope());
        for (final GeneralEnvelope envelope : envelopes) {
            complete(envelope, gridToCRS, gridToCRS != cornerToCRS, fallback);
        }
        return envelopes;
    }

    /**
     * If the envelope contains some NaN values, tries to replace them by constant values inferred from the math transform.
     * We must use the {@link MathTransform} specified by the user ({@code gridToCRS}), not necessarily {@code cornerToCRS},
     * because inferring a {@code cornerToCRS} by translating a {@code centerToCRS} by 0.5 cell increase the amount of NaN
     * values in the matrix. For giving a chance to {@link TransformSeparator} to perform its work,
     * we need the minimal amount of NaN values.
     *
     * @param  envelope   the envelope to complete if empty.
     * @param  gridToCRS  the transform specified by user.
     * @param  isCenter   whether the "grid to CRS" transform maps cell center instead of cell corners.
     * @param  fallback   bounds to use if some values are still NaN after conversion, or {@code null} if none.
     */
    private void complete(final GeneralEnvelope envelope, final MathTransform gridToCRS, final boolean isCenter, final Envelope fallback) {
        if (envelope.isEmpty()) try {
            final int dimension = getDimension();
            TransformSeparator separator = null;
            for (int srcDim=0; srcDim < dimension; srcDim++) {
                if (coordinates[srcDim + dimension] == 0 && coordinates[srcDim] == 0) {
                    /*
                     * At this point we found a grid dimension with [0 … 0] range. Only this specific range is processed because
                     * it is assumed associated to NaN scale factors in the `gridToCRS` matrix, since the resolution is computed
                     * by 0/0.  We require the range to be [0 … 0] instead of [n … n] because if grid indices are not zero, then
                     * we would need to know the scale factors for computing the offset.
                     */
                    if (separator == null) {
                        separator = new TransformSeparator(gridToCRS);
                    }
                    separator.addSourceDimensionRange(srcDim, srcDim + 1);
                    final Matrix component = MathTransforms.getMatrix(separator.separate());
                    if (component != null) {
                        final int[] targets = separator.getTargetDimensions();
                        for (int j=0; j<targets.length; j++) {
                            final int tgtDim = targets[j];
                            double lower = envelope.getLower(tgtDim);
                            double upper = envelope.getUpper(tgtDim);
                            final double value = component.getElement(j, component.getNumCol() - 1);
                            /*
                             * Replace only the envelope NaN values by the translation term (non-NaN values are left unchanged).
                             * If the gridToCRS map cell corners, then we update only the lower bound since the transform maps
                             * lower-left corner; the upper bound is unknown. If the gridToCRS maps cell center, then we update
                             * both lower and upper bounds to a value assumed to be in the center; the span is set to zero.
                             */
                            if (isCenter) {
                                double span = upper - value;
                                if (Double.isNaN(span)) {
                                    span = value - lower;
                                    if (Double.isNaN(span)) {
                                        span = 0;
                                    }
                                }
                                if (Double.isNaN(lower)) lower = value - span;
                                if (Double.isNaN(upper)) upper = value + span;
                            } else if (Double.isNaN(lower)) {
                                lower = value;
                            }
                            envelope.setRange(tgtDim, lower, upper);
                        }
                    }
                    separator.clear();
                }
            }
            /*
             * If above block has been unable to fix all NaN values, fix the remaining NaNs by copying the corresponding
             * coordinates from the fallback envelope. It should happen only for dimensions with a thickness of 1, i.e.
             * when `low == high` but not necessarily `low == 0` and `high == 0` (contrarily to above block). We use this
             * fallback is last resort because the envelope may be less reliable than values computed from `gridToCRS`.
             */
            if (fallback != null) {
                for (int tgtDim = envelope.getDimension(); --tgtDim >= 0;) {
                    boolean modified = false;
                    double lower = envelope.getLower(tgtDim);
                    double upper = envelope.getUpper(tgtDim);
                    if (Double.isNaN(lower)) {lower = fallback.getMinimum(tgtDim); modified = true;}
                    if (Double.isNaN(upper)) {upper = fallback.getMaximum(tgtDim); modified = true;}
                    if (modified && !(lower > upper)) {                // Use `!` for accepting NaN.
                        envelope.setRange(tgtDim, lower, upper);
                    }
                }
            }
        } catch (FactoryException e) {
            // "toEnvelope" is the closest public method that may invoke this method.
            Logging.recoverableException(getLogger(Modules.RASTER), GridExtent.class, "toEnvelope", e);
        }
    }

    /**
     * Returns a new grid extent with the specified dimension inserted at the given index in this grid extent.
     * To append a new dimension after all existing dimensions, set {@code offset} to {@link #getDimension()}.
     *
     * @param  offset          where to insert the new dimension, from 0 to {@link #getDimension()} inclusive.
     * @param  axisType        the type of the grid axis to add, or {@code null} if unspecified.
     * @param  low             the valid minimum grid coordinate (always inclusive).
     * @param  high            the valid maximum grid coordinate, inclusive or exclusive depending on the next argument.
     * @param  isHighIncluded  {@code true} if the {@code high} value is inclusive (as in ISO 19123 specification),
     *                         or {@code false} if it is exclusive (as in Java2D usage).
     *                         This argument does not apply to {@code low} value, which is always inclusive.
     * @return a new grid extent with the specified dimension added.
     * @throws IllegalArgumentException if the low coordinate value is greater than the high coordinate value.
     *
     * @since 1.1
     */
    public GridExtent insertDimension(final int offset, final DimensionNameType axisType, final long low, long high, final boolean isHighIncluded) {
        final int dimension = getDimension();
        ArgumentChecks.ensureBetween("offset", 0, dimension, offset);
        if (!isHighIncluded) {
            high = Math.decrementExact(high);
        }
        final int newDim = dimension + 1;
        DimensionNameType[] axisTypes = null;
        if (types != null || axisType != null) {
            if (types != null) {
                axisTypes = ArraysExt.insert(types, offset, 1);
            } else {
                axisTypes = new DimensionNameType[newDim];
            }
            axisTypes[offset] = axisType;
        }
        final GridExtent ex = new GridExtent(newDim, axisTypes);
        System.arraycopy(coordinates, 0,                  ex.coordinates, 0,                   offset);
        System.arraycopy(coordinates, offset,             ex.coordinates, offset + 1,          dimension - offset);
        System.arraycopy(coordinates, dimension,          ex.coordinates, newDim,              offset);
        System.arraycopy(coordinates, dimension + offset, ex.coordinates, newDim + offset + 1, dimension - offset);
        ex.coordinates[offset]          = low;
        ex.coordinates[offset + newDim] = high;
        ex.validateCoordinates();
        return ex;
    }

    /**
     * Returns a grid extent that encompass only some dimensions of this grid extent.
     * This method copies the specified dimensions of this grid extent into a new grid extent.
     * The given dimensions must be in strictly ascending order without duplicated values.
     * The number of dimensions of the sub grid extent will be {@code dimensions.length}.
     *
     * <p>This method performs a <cite>dimensionality reduction</cite> and can be used as the
     * converse of {@link #insertDimension(int, DimensionNameType, long, long, boolean)}.
     * This method can not be used for changing dimension order.</p>
     *
     * @param  dimensions  the dimensions to select, in strictly increasing order.
     * @return the sub-envelope, or {@code this} if the given array contains all dimensions of this grid extent.
     * @throws IndexOutOfBoundsException if an index is out of bounds.
     *
     * @see #getSubspaceDimensions(int)
     * @see GridGeometry#reduce(int...)
     *
     * @since 1.1
     */
    public GridExtent reduceDimension(int... dimensions) {
        dimensions = verifyDimensions(dimensions, getDimension());
        return (dimensions != null) ? reorder(dimensions) : this;
    }

    /**
     * Verifies the validity of a given {@code dimensions} argument.
     *
     * @param  dimensions  the user-supplied argument to validate.
     * @param  limit       maximal number of dimensions, exclusive.
     * @return a clone of the given array, or {@code null} if the caller can return {@code this}.
     */
    static int[] verifyDimensions(int[] dimensions, final int limit) {
        ArgumentChecks.ensureNonNull("dimensions", dimensions);
        final int n = dimensions.length;
        ArgumentChecks.ensureSizeBetween("dimensions", 1, limit, n);
        dimensions = dimensions.clone();
        if (!ArraysExt.isSorted(dimensions, true)) {
            throw new IllegalArgumentException(Resources.format(Resources.Keys.NotStrictlyOrderedDimensions));
        }
        int d = dimensions[0];
        if (d >= 0) {
            d = dimensions[n - 1];
            if (d < limit) {
                return (n != limit) ? dimensions : null;
            }
        }
        throw new IndexOutOfBoundsException(Errors.format(Errors.Keys.IndexOutOfBounds_1, d));
    }

    /**
     * Changes axis order or reduces the number of dimensions.
     * It is caller responsibility to ensure that the given dimensions are valid.
     */
    final GridExtent reorder(final int[] dimensions) {
        final int sd = getDimension();
        final int td = dimensions.length;
        DimensionNameType[] tt = null;
        if (types != null) {
            tt = new DimensionNameType[td];
            for (int i=0; i<td; i++) {
                tt[i] = types[dimensions[i]];
            }
        }
        final GridExtent sub = new GridExtent(td, tt);
        for (int i=0; i<td; i++) {
            final int j = dimensions[i];
            sub.coordinates[i]    = coordinates[j];
            sub.coordinates[i+td] = coordinates[j+sd];
        }
        return sub;
    }

    /**
     * Returns a grid extent expanded by the given amount of cells on both sides along each dimension.
     * This method adds the given margins to the {@linkplain #getHigh(int) high coordinates}
     * and subtracts the same margins from the {@linkplain #getLow(int) low coordinates}.
     * If a negative margin is supplied, the extent size decreases accordingly.
     *
     * <h4>Number of arguments</h4>
     * The {@code margins} array length should be equal to the {@linkplain #getDimension() number of dimensions}.
     * If the array is shorter, missing values default to 0 (i.e. sizes in unspecified dimensions are unchanged).
     * If the array is longer, extraneous values are ignored.
     *
     * @param  margins  amount of cells to add or subtract on both sides for each dimension.
     * @return a grid extent expanded by the given amount, or {@code this} if there is no change.
     * @throws ArithmeticException if expanding this extent by the given margins overflows {@code long} capacity.
     *
     * @see GridDerivation#margin(int...)
     */
    public GridExtent expand(final long... margins) {
        ArgumentChecks.ensureNonNull("margins", margins);
        final int m = getDimension();
        final int length = Math.min(m, margins.length);
        if (isZero(margins, length)) {
            return this;
        }
        final GridExtent resized = new GridExtent(this);
        final long[] c = resized.coordinates;
        for (int i=0; i<length; i++) {
            final long p = margins[i];
            c[i] = Math.subtractExact(c[i], p);
            c[i+m] = Math.addExact(c[i+m], p);
        }
        return resized;
    }

    /**
     * Returns a grid extent expanded by the minimal amount of cells needed for covering an integer amount of chunks.
     * The grid coordinates (0, 0, …) locate the corner of a chunk.
     *
     * <h4>Number of arguments</h4>
     * The {@code sizes} array length should be equal to the {@linkplain #getDimension() number of dimensions}.
     * If the array is shorter, missing values default to 1. If the array is longer, extraneous values are ignored.
     *
     * @param  sizes  number of cells in all tiles or chunks.
     * @return a grid extent expanded for the given chunk size.
     *
     * @see GridDerivation#chunkSize(int...)
     */
    final GridExtent forChunkSize(final int... sizes) {
        /*
         * Current implementation does not validate argument because this method is not public.
         * If we make this method public in the future, argument validation should be added.
         */
        final int m = getDimension();
        final int length = Math.min(m, sizes.length);
        final GridExtent resized = new GridExtent(this);
        final long[] c = resized.coordinates;
        for (int i=0; i<length; i++) {
            final int s = sizes[i];
            final int j = i + m;
            c[i] = Math.subtractExact(c[i], Math.floorMod(c[i], s));
            c[j] = Math.addExact(c[j], (s-1) - Math.floorMod(c[j], s));
        }
        return resized;
    }

    /**
     * Sets the size of grid extent to the given values by moving low and high coordinates.
     * This method modifies grid coordinates as if they were multiplied by
     *
     *     <var>(given size)</var> / <var>({@linkplain #getSize(int) current size})</var>,
     *
     * rounded toward zero and with the value farthest from zero adjusted by ±1 for having a size
     * exactly equals to the specified value.
     *
     * In the common case where the {@linkplain #getLow(int) low value} is zero,
     * this is equivalent to setting the {@linkplain #getHigh(int) high value} to {@code size} - 1.
     *
     * <h4>Number of arguments</h4>
     * The {@code sizes} array length should be equal to the {@linkplain #getDimension() number of dimensions}.
     * If the array is shorter, sizes in unspecified dimensions are unchanged.
     * If the array is longer, extraneous values are ignored.
     *
     * @param  sizes  the new grid sizes for each dimension.
     * @return a grid extent having the given sizes, or {@code this} if there is no change.
     * @throws ArithmeticException if resizing this extent to the given size overflows {@code long} capacity.
     *
     * @see #getSize(int)
     * @see GridDerivation#subgrid(GridExtent, int...)
     */
    public GridExtent resize(final long... sizes) {
        ArgumentChecks.ensureNonNull("sizes", sizes);
        final int m = getDimension();
        final int length = Math.min(m, sizes.length);
        final GridExtent resize = new GridExtent(this);
        final long[] c = resize.coordinates;
        for (int i=0; i<length; i++) {
            final long size = sizes[i];
            if (size <= 0) {
                throw new IllegalArgumentException(Errors.format(
                        Errors.Keys.ValueNotGreaterThanZero_2, Strings.toIndexed("sizes", i), size));
            }
            long lower = c[i];
            long upper = c[i+m];
            final long current = Math.incrementExact(Math.subtractExact(upper, lower));
            if (Math.abs(lower) <= Math.abs(upper)) {
                lower = Numerics.multiplyDivide(lower, size, current);
                upper = Math.addExact(lower, size - 1);
            } else {
                upper = Numerics.multiplyDivide(upper, size, current);
                lower = Math.subtractExact(upper, size - 1);
            }
            c[i  ] = lower;
            c[i+m] = upper;
        }
        return Arrays.equals(c, coordinates) ? this : resize;
    }

    /**
     * Creates a new grid extent subsampled by the given amount of cells along each grid dimensions.
     * This method divides {@linkplain #getLow(int) low coordinates} and {@linkplain #getSize(int) grid sizes}
     * by the given periods, rounding toward zero. The {@linkplain #getHigh(int) high coordinates} are adjusted
     * accordingly (this is often equivalent to dividing high coordinates by the periods too, but a difference
     * of one cell may exist).
     *
     * <div class="note"><b>Note:</b>
     * The envelope computed from a grid extent may become <em>larger</em> after subsampling, not smaller.
     * This effect can be understood intuitively if we consider that cells become larger after subsampling,
     * which implies that accurate representation of the same envelope may require fractional cells on some
     * grid borders.</div>
     *
     * This method does not reduce the number of dimensions of the grid extent.
     * For dimensionality reduction, see {@link #reduceDimension(int[])}.
     *
     * <h4>Number of arguments</h4>
     * The {@code periods} array length should be equal to the {@linkplain #getDimension() number of dimensions}.
     * If the array is shorter, missing values default to 1 (i.e. samplings in unspecified dimensions are unchanged).
     * If the array is longer, extraneous values are ignored.
     *
     * @param  periods  the subsampling. Length shall be equal to the number of dimension and all values shall be greater than zero.
     * @return the subsampled extent, or {@code this} is subsampling results in the same extent.
     * @throws IllegalArgumentException if a period is not greater than zero.
     *
     * @see GridDerivation#subgrid(GridExtent, int...)
     */
    public GridExtent subsample(final int... periods) {
        ArgumentChecks.ensureNonNull("periods", periods);
        final int m = getDimension();
        final int length = Math.min(m, periods.length);
        final GridExtent sub = new GridExtent(this);
        for (int i=0; i<length; i++) {
            final int s = periods[i];
            if (s > 1) {
                final int j = i + m;
                long low  = coordinates[i];
                long size = coordinates[j] - low + 1;                      // Result is an unsigned number.
                if (size == 0) {
                    throw new ArithmeticException(Errors.format(Errors.Keys.IntegerOverflow_1, Long.SIZE));
                }
                long r = Long.divideUnsigned(size, s);
                if (r*s == size) r--;                           // Make inclusive if the division did not already rounded toward 0.
                sub.coordinates[i] = low /= s;
                sub.coordinates[j] = low + r;
            } else if (s <= 0) {
                throw new IllegalArgumentException(Errors.format(Errors.Keys.ValueNotGreaterThanZero_2, Strings.toIndexed("periods", i), s));
            }
        }
        return Arrays.equals(coordinates, sub.coordinates) ? this : sub;
    }

    /**
     * Returns a slice of this grid extent computed by a ratio between 0 and 1 inclusive.
     * This is a helper method for {@link GridDerivation#sliceByRatio(double, int...)} implementation.
     *
     * @param  slicePoint        a pre-allocated direct position to be overwritten by this method.
     * @param  sliceRatio        the ratio to apply on all grid dimensions except the ones to keep.
     * @param  dimensionsToKeep  the grid dimension to keep unchanged.
     */
    final GridExtent sliceByRatio(final DirectPosition slicePoint, final double sliceRatio, final int[] dimensionsToKeep) {
        for (int i=slicePoint.getDimension(); --i >= 0;) {
            slicePoint.setOrdinate(i, sliceRatio * getSize(i, true) + getLow(i));       // TODO: use Math.fma
        }
        for (int i=0; i<dimensionsToKeep.length; i++) {
            slicePoint.setOrdinate(dimensionsToKeep[i], Double.NaN);
        }
        return slice(slicePoint, null);
    }

    /**
     * Creates a new grid extent which represent a slice of this grid at the given point.
     * The given point may have less dimensions than this grid extent, in which case the
     * dimensions must be specified in the {@code modifiedDimensions} array. Coordinates
     * in the given point will be rounded to nearest integer.
     *
     * <p>This method does not reduce the number of dimensions of the grid extent.
     * For dimensionality reduction, see {@link #reduceDimension(int[])}.</p>
     *
     * @param  slicePoint           where to take a slice. NaN values are handled as if their dimensions were absent.
     * @param  modifiedDimensions   mapping from {@code slicePoint} dimensions to this {@code GridExtent} dimensions,
     *                              or {@code null} if {@code slicePoint} contains all grid dimensions in same order.
     * @return a grid extent for the specified slice.
     * @throws PointOutsideCoverageException if the given point is outside the grid extent.
     */
    final GridExtent slice(final DirectPosition slicePoint, final int[] modifiedDimensions) {
        final GridExtent slice = new GridExtent(this);
        final int n = slicePoint.getDimension();
        final int m = getDimension();
        for (int k=0; k<n; k++) {
            double p = slicePoint.getOrdinate(k);
            if (!Double.isNaN(p)) {
                final long c = Math.round(p);
                final int i = (modifiedDimensions != null) ? modifiedDimensions[k] : k;
                final long low  = coordinates[i];
                final long high = coordinates[i + m];
                if (c >= low && c <= high) {
                    slice.coordinates[i + m] = slice.coordinates[i] = c;
                } else {
                    final StringBuilder b = new StringBuilder();
                    for (int j=0; j<n; j++) {
                        if (j != 0) b.append(", ");
                        p = slicePoint.getOrdinate(j);
                        if (Double.isNaN(p)) b.append("NaN");
                        else b.append(Math.round(p));
                    }
                    throw new PointOutsideCoverageException(Resources.format(
                            Resources.Keys.GridCoordinateOutsideCoverage_4,
                            getAxisIdentification(i,k), low, high, b.toString()));
                }
            }
        }
        return Arrays.equals(coordinates, slice.coordinates) ? this : slice;
    }

    /**
     * Creates an affine transform from the coordinates of this grid to the coordinates of the given envelope.
     * This method assumes that all axes are in the same order (no axis swapping) and there is no flipping of
     * axis direction except for those specified in the {@code flips} bitmask. The transform maps cell corners.
     *
     * @param  env               the target envelope. Despite this method name, the envelope CRS is ignored.
     * @param  flippedAxes       bitmask of target axes to flip (0 if none).
     * @param  sourceDimensions  source dimension for each target dimension, or {@code null} if dimensions are the same.
     * @return an affine transform from this grid extent to the given envelope, expressed as a matrix.
     */
    final MatrixSIS cornerToCRS(final Envelope env, final long flippedAxes, final int[] sourceDimensions) {
        final int          srcDim = getDimension();
        final int          tgtDim = env.getDimension();
        final MatrixSIS    affine = Matrices.create(tgtDim + 1, srcDim + 1, ExtendedPrecisionMatrix.ZERO);
        final DoubleDouble scale  = new DoubleDouble();
        final DoubleDouble offset = new DoubleDouble();
        for (int j=0; j<tgtDim; j++) {
            final int i = (sourceDimensions != null) ? sourceDimensions[j] : j;
            if (i < srcDim) {
                final boolean flip = (flippedAxes & Numerics.bitmask(j)) != 0;
                offset.set(coordinates[i]);
                scale.set(coordinates[i + srcDim]);
                scale.subtract(offset);
                scale.add(1);                                   // == getSize(j) but without overflow.
                scale.inverseDivideGuessError(env.getSpan(j));  // == (envelope span) / (grid size).
                if (flip) scale.negate();
                if (!offset.isZero()) {                         // Use `if` for keeping the value if scale is NaN.
                    offset.multiply(scale);
                    offset.negate();
                }
                offset.addGuessError(flip ? env.getMaximum(j) : env.getMinimum(j));
                affine.setNumber(j, srcDim, offset);
            } else {
                scale.value = Double.NaN;
                scale.error = Double.NaN;
            }
            affine.setNumber(j, i, scale);
        }
        affine.setElement(tgtDim, srcDim, 1);
        return affine;
    }

    /**
     * Returns an extent translated by the given amount of cells compared to this extent.
     * The returned extent has the same {@linkplain #getSize(int) size} than this extent,
     * i.e. both low and high grid coordinates are displaced by the same amount of cells.
     *
     * <div class="note"><b>Example:</b>
     * for an extent (x: [0…10], y: [2…4], z: [0…1]) and a translation {-2, 2},
     * the resulting extent would be (x: [-2…8], y: [4…6], z: [0…1]).</div>
     *
     * <h4>Number of arguments</h4>
     * The {@code translation} array length should be equal to the {@linkplain #getDimension() number of dimensions}.
     * If the array is shorter, missing values default to 0 (i.e. no translation in unspecified dimensions).
     * If the array is longer, extraneous values are ignored.
     *
     * @param  translation  translation to apply on each axis in order.
     * @return a grid extent whose coordinates (both low and high ones) have been translated by given amounts.
     *         If the given translation is a no-op (no value or only 0 ones), then this extent is returned as is.
     * @throws ArithmeticException if the translation results in coordinates that overflow 64-bits integer.
     *
     * @see #startsAtZero()
     * @see GridGeometry#translate(long...)
     *
     * @since 1.1
     */
    public GridExtent translate(final long... translation) {
        ArgumentChecks.ensureNonNull("translation", translation);
        final int m = getDimension();
        final int length = Math.min(m, translation.length);
        if (!isZero(translation, length)) {
            final GridExtent translated = new GridExtent(this);
            final long[] c = translated.coordinates;
            for (int i=0; i < length; i++) {
                final int  j = i + m;
                final long t = translation[i];
                c[i] = Math.addExact(c[i], t);
                c[j] = Math.addExact(c[j], t);
            }
            return translated;
        }
        return this;
    }

    /**
     * Returns {@code true} if this extent contains the given cell indices.
     * An index is considered inside the grid extent if its value is between
     * {@link #getLow(int) low} and {@link #getHigh(int) high} bounds, inclusive.
     *
     * <h4>Number of arguments</h4>
     * The {@code indices} array length should be equal to the {@linkplain #getDimension() number of dimensions}.
     * If the array is shorter, missing index values are considered inside the extent.
     * If the array is longer, extraneous values are ignored.
     *
     * @param  indices  indices of the grid cell to check.
     * @return whether the given indices are inside this extent.
     *
     * @since 1.2
     */
    public boolean contains(final long... indices) {
        ArgumentChecks.ensureNonNull("indices", indices);
        final int m = getDimension();
        final int length = Math.min(m, indices.length);
        for (int i=0; i<length; i++) {
            final long c = indices[i];
            if (c < coordinates[i] || c > coordinates[i + m]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the intersection of this grid extent with to the given grid extent.
     * The given extent shall have the same number of dimensions.
     *
     * <p>This method is not public because we do not yet have a policy
     * about whether we should verify if axis {@link #types} match.</p>
     *
     * @param  other  the grid to intersect with.
     * @return the intersection result. May be one of the existing instances.
     */
    final GridExtent intersect(final GridExtent other) {
        return combine(other, false);
    }

    /**
     * Returns the union of this grid extent with to the given grid extent.
     * The given extent shall have the same number of dimensions.
     *
     * <p>This method is not public because we do not yet have a policy
     * about whether we should verify if axis {@link #types} match.</p>
     *
     * @param  other  the grid to combine with.
     * @return the union result. May be one of the existing instances.
     */
    final GridExtent union(final GridExtent other) {
        return combine(other, true);
    }

    /**
     * Implementation of {@link #union(GridExtent)} and {@link #intersect(GridExtent)}
     */
    private GridExtent combine(final GridExtent other, final boolean union) {
        final int n = coordinates.length;
        final int m = n >>> 1;
        final long[] clipped = new long[n];
        int i = 0;
        while (i < m) {clipped[i] = extremum(coordinates[i], other.coordinates[i], !union); i++;}
        while (i < n) {clipped[i] = extremum(coordinates[i], other.coordinates[i],  union); i++;}
        if (Arrays.equals(clipped,  this.coordinates)) return this;
        if (Arrays.equals(clipped, other.coordinates)) return other;
        return new GridExtent(this, clipped);
    }

    /**
     * Returns the minimum or maximum value between the given pair of values.
     */
    private static long extremum(final long a, final long b, final boolean max) {
        return max ? Math.max(a, b) : Math.min(a, b);
    }

    /**
     * Returns a hash value for this grid extent. This value needs not to remain
     * consistent between different implementations of the same class.
     *
     * @return a hash value for this grid extent.
     */
    @Override
    public int hashCode() {
        return Arrays.hashCode(coordinates) + Arrays.hashCode(types) ^ (int) serialVersionUID;
    }

    /**
     * Compares the specified object with this grid extent for equality.
     * This method delegates to {@code equals(object, ComparisonMode.STRICT)}.
     *
     * @param  object  the object to compare with this grid extent for equality.
     * @return {@code true} if the given object is equal to this grid extent.
     */
    @Override
    public final boolean equals(final Object object) {
        return equals(object, ComparisonMode.STRICT);
    }

    /**
     * Compares the specified object with this grid extent for equality.
     * If the mode is {@link ComparisonMode#IGNORE_METADATA} or more flexible,
     * then the {@linkplain #getAxisType(int) axis types} are ignored.
     *
     * @param  object  the object to compare with this grid extent for equality.
     * @param  mode    the strictness level of the comparison.
     * @return {@code true} if the given object is equal to this grid extent.
     *
     * @since 1.1
     */
    @Override
    @SuppressWarnings("fallthrough")
    public boolean equals(final Object object, final ComparisonMode mode) {
        if (object == this) {
            return true;
        }
        if (object instanceof GridExtent) {
            final GridExtent other = (GridExtent) object;
            if (Arrays.equals(coordinates, other.coordinates)) {
                switch (mode) {
                    case STRICT:      if (!getClass().equals(object.getClass())) return false;  // else fallthrough
                    case BY_CONTRACT: if (!Arrays.equals(types, other.types))    return false;  // else fallthrough
                    default:          return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns a string representation of this grid extent. The returned string
     * is implementation dependent and is provided for debugging purposes only.
     */
    @Override
    public String toString() {
        final StringBuilder out = new StringBuilder(256);
        try {
            appendTo(out, Vocabulary.getResources((Locale) null));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString();
    }

    /**
     * Writes a string representation of this grid extent in the given buffer.
     * This method is provided for allowing caller to recycle the same buffer.
     *
     * @param out         where to write the string representation.
     * @param vocabulary  resources for some words.
     */
    final void appendTo(final Appendable out, final Vocabulary vocabulary) throws IOException {
        final TableAppender table = new TableAppender(out, "");
        final int dimension = getDimension();
        for (int i=0; i<dimension; i++) {
            String name = null;
            if (types != null) {
                final InternationalString title = Types.getCodeTitle(types[i]);
                if (title != null) {
                    name = title.toString(vocabulary.getLocale());
                }
            }
            if (name == null) {
                name = vocabulary.getString(Vocabulary.Keys.Dimension_1, i);
            }
            final long lower = coordinates[i];
            final long upper = coordinates[i + dimension];
            table.setCellAlignment(TableAppender.ALIGN_LEFT);
            table.append(name).append(": ").nextColumn();
            table.append('[').nextColumn();
            table.setCellAlignment(TableAppender.ALIGN_RIGHT);
            table.append(Long.toString(lower)).append(" … ").nextColumn();
            table.append(Long.toString(upper)).append("] ") .nextColumn();
            table.append('(').append(vocabulary.getString(Vocabulary.Keys.CellCount_1,
                    toSizeString(upper - lower + 1))).append(')').nextLine();
        }
        table.flush();
    }

    /**
     * Returns a string representation of the given size, assumed computed by {@code high - low + 1}.
     * A value of 0 means that there is an overflow and that the true value os 2<sup>64</sup>.
     */
    static String toSizeString(final long size) {
        return (size != 0) ? Long.toUnsignedString(size) : "2⁶⁴";
    }
}
