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
package org.apache.sis.internal.referencing;

import java.lang.reflect.Array;
import java.util.function.Function;
import javax.measure.Unit;
import javax.measure.Quantity;
import javax.measure.quantity.Angle;
import org.opengis.metadata.Identifier;
import org.opengis.parameter.ParameterValue;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.parameter.GeneralParameterValue;
import org.opengis.parameter.GeneralParameterDescriptor;
import org.opengis.referencing.IdentifiedObject;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.cs.CoordinateSystem;
import org.opengis.referencing.cs.CoordinateSystemAxis;
import org.opengis.referencing.datum.Datum;
import org.opengis.referencing.datum.GeodeticDatum;
import org.opengis.referencing.datum.PrimeMeridian;
import org.opengis.referencing.datum.Ellipsoid;
import org.opengis.referencing.operation.Matrix;
import org.opengis.referencing.operation.MathTransform;
import org.apache.sis.referencing.IdentifiedObjects;
import org.apache.sis.referencing.crs.AbstractCRS;
import org.apache.sis.referencing.cs.AbstractCS;
import org.apache.sis.referencing.cs.DefaultCoordinateSystemAxis;
import org.apache.sis.referencing.datum.AbstractDatum;
import org.apache.sis.referencing.datum.DefaultGeodeticDatum;
import org.apache.sis.referencing.datum.DefaultPrimeMeridian;
import org.apache.sis.referencing.datum.DefaultEllipsoid;
import org.apache.sis.referencing.operation.transform.MathTransforms;
import org.apache.sis.internal.referencing.provider.Affine;
import org.apache.sis.parameter.DefaultParameterValue;
import org.apache.sis.parameter.Parameterized;
import org.apache.sis.io.wkt.ElementKind;
import org.apache.sis.io.wkt.FormattableObject;
import org.apache.sis.io.wkt.Formatter;
import org.apache.sis.measure.Units;
import org.apache.sis.util.Static;
import org.apache.sis.util.CharSequences;
import org.apache.sis.util.resources.Vocabulary;
import org.apache.sis.internal.util.Constants;
import org.apache.sis.internal.util.Numerics;
import org.apache.sis.math.DecimalFunctions;
import org.apache.sis.math.Statistics;
import org.apache.sis.math.Vector;


/**
 * Utility methods for referencing WKT formatting.
 *
 * This class provides a set of {@code toFormattable(…)} for various {@link IdentifiedObject} subtypes.
 * It is important to <strong>not</strong> provide a generic {@code toFormattable(IdentifiedObject)}
 * method, because the user may choose to implement more than one GeoAPI interface for the same object.
 * We need to be specific in order to select the right "aspect" of the given object.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.1
 * @since   0.4
 * @module
 */
public final class WKTUtilities extends Static {
    /**
     * Do not allow instantiation of this class.
     */
    private WKTUtilities() {
    }

    /**
     * Returns the WKT type of the given interface.
     *
     * For {@link CoordinateSystem} base type, the returned value shall be one of
     * {@code affine}, {@code Cartesian}, {@code cylindrical}, {@code ellipsoidal}, {@code linear},
     * {@code parametric}, {@code polar}, {@code spherical}, {@code temporal} or {@code vertical}.
     *
     * @param  base  the abstract base interface.
     * @param  type  the interface or classes for which to get the WKT type.
     * @return the WKT type for the given class or interface, or {@code null} if none.
     *
     * @see ReferencingUtilities#toPropertyName(Class, Class)
     */
    public static String toType(final Class<?> base, final Class<?> type) {
        if (type != base) {
            final StringBuilder name = ReferencingUtilities.toPropertyName(base, type);
            if (name != null) {
                int end = name.length() - 2;
                if (CharSequences.regionMatches(name, end, "CS")) {
                    name.setLength(end);
                    if ("time".contentEquals(name)) {
                        return "temporal";
                    }
                    if (CharSequences.regionMatches(name, 0, "cartesian")) {
                        name.setCharAt(0, 'C');     // "Cartesian"
                    }
                    return name.toString();
                }
            }
        }
        return null;
    }

    /**
     * Returns the given coordinate reference system as a formattable object.
     *
     * @param  object  the coordinate reference system, or {@code null}.
     * @return the given coordinate reference system as a formattable object, or {@code null}.
     */
    public static FormattableObject toFormattable(final CoordinateReferenceSystem object) {
        if (object instanceof FormattableObject) {
            return (FormattableObject) object;
        } else {
            return AbstractCRS.castOrCopy(object);
        }
    }

    /**
     * Returns the given coordinate system as a formattable object.
     *
     * @param  object  the coordinate system, or {@code null}.
     * @return the given coordinate system as a formattable object, or {@code null}.
     */
    public static FormattableObject toFormattable(final CoordinateSystem object) {
        if (object instanceof FormattableObject) {
            return (FormattableObject) object;
        } else {
            return AbstractCS.castOrCopy(object);
        }
    }

    /**
     * Returns the given coordinate system axis as a formattable object.
     *
     * @param  object  the coordinate system axis, or {@code null}.
     * @return the given coordinate system axis as a formattable object, or {@code null}.
     */
    public static FormattableObject toFormattable(final CoordinateSystemAxis object) {
        if (object instanceof FormattableObject) {
            return (FormattableObject) object;
        } else {
            return DefaultCoordinateSystemAxis.castOrCopy(object);
        }
    }

    /**
     * Returns the given datum as a formattable object.
     *
     * @param  object  the datum, or {@code null}.
     * @return the given datum as a formattable object, or {@code null}.
     */
    public static FormattableObject toFormattable(final Datum object) {
        if (object instanceof FormattableObject) {
            return (FormattableObject) object;
        } else {
            return AbstractDatum.castOrCopy(object);
        }
    }

    /**
     * Returns the given geodetic datum as a formattable object.
     *
     * @param  object  the datum, or {@code null}.
     * @return the given datum as a formattable object, or {@code null}.
     */
    public static FormattableObject toFormattable(final GeodeticDatum object) {
        if (object instanceof FormattableObject) {
            return (FormattableObject) object;
        } else {
            return DefaultGeodeticDatum.castOrCopy(object);
        }
    }

    /**
     * Returns the ellipsoid as a formattable object.
     *
     * @param  object  the ellipsoid, or {@code null}.
     * @return the given ellipsoid as a formattable object, or {@code null}.
     */
    public static FormattableObject toFormattable(final Ellipsoid object) {
        if (object instanceof FormattableObject) {
            return (FormattableObject) object;
        } else {
            return DefaultEllipsoid.castOrCopy(object);
        }
    }

    /**
     * Returns the given prime meridian as a formattable object.
     *
     * @param  object  the prime meridian, or {@code null}.
     * @return the given prime meridian as a formattable object, or {@code null}.
     */
    public static FormattableObject toFormattable(final PrimeMeridian object) {
        if (object instanceof FormattableObject) {
            return (FormattableObject) object;
        } else {
            return DefaultPrimeMeridian.castOrCopy(object);
        }
    }

    /**
     * Converts the given object in a {@code FormattableObject} instance. Callers should verify that the
     * given object is not already an instance of {@code FormattableObject} before to invoke this method.
     * This method returns {@code null} if it can not convert the object.
     *
     * @param  object    the object to wrap.
     * @param  internal  {@code true} if the formatting convention is {@code Convention.INTERNAL}.
     * @return the given object converted to a {@code FormattableObject} instance, or {@code null}.
     */
    public static FormattableObject toFormattable(final MathTransform object, boolean internal) {
        Matrix matrix;
        final ParameterValueGroup parameters;
        if (internal && (matrix = MathTransforms.getMatrix(object)) != null) {
            parameters = Affine.parameters(matrix);
        } else if (object instanceof Parameterized) {
            parameters = ((Parameterized) object).getParameterValues();
        } else {
            matrix = MathTransforms.getMatrix(object);
            if (matrix == null) {
                return null;
            }
            parameters = Affine.parameters(matrix);
        }
        return new FormattableObject() {
            @Override protected String formatTo(final Formatter formatter) {
                WKTUtilities.appendParamMT(parameters, formatter);
                return WKTKeywords.Param_MT;
            }
        };
    }

    /**
     * If the given unit is one of the unit that can not be formatted without ambiguity in WKT format,
     * return a proposed replacement. Otherwise returns {@code unit} unchanged.
     *
     * @param  <Q>   the unit dimension.
     * @param  unit  the unit to test.
     * @return the replacement to format, or {@code unit} if not needed.
     *
     * @since 0.8
     */
    @SuppressWarnings("unchecked")
    public static <Q extends Quantity<Q>> Unit<Q> toFormattable(Unit<Q> unit) {
        if (Units.isAngular(unit)) {
            if (!((Unit<Angle>) unit).getConverterTo(Units.RADIAN).isLinear()) {
                unit = (Unit<Q>) Units.DEGREE;
            }
        }
        return unit;
    }

    /**
     * Appends the name of the given object to the formatter.
     *
     * @param  object     the object from which to get the name.
     * @param  formatter  the formatter where to append the name.
     * @param  type       the key of colors to apply if syntax colors are enabled.
     */
    public static void appendName(final IdentifiedObject object, final Formatter formatter, final ElementKind type) {
        String name = IdentifiedObjects.getName(object, formatter.getNameAuthority());
        if (name == null) {
            name = IdentifiedObjects.getName(object, null);
            if (name == null) {
                name = Vocabulary.getResources(formatter.getLocale()).getString(Vocabulary.Keys.Unnamed);
            }
        }
        formatter.append(name, (type != null) ? type : ElementKind.NAME);
    }

    /**
     * Appends a {@linkplain ParameterValueGroup group of parameters} in a {@code Param_MT[…]} element.
     *
     * @param  parameters  the parameter to append to the WKT, or {@code null} if none.
     * @param  formatter   the formatter where to append the parameter.
     */
    public static void appendParamMT(final ParameterValueGroup parameters, final Formatter formatter) {
        if (parameters != null) {
            appendName(parameters.getDescriptor(), formatter, ElementKind.PARAMETER);
            append(parameters, formatter);
        }
    }

    /**
     * Appends a {@linkplain ParameterValue parameter} in a {@code PARAMETER[…]} element.
     * If the supplied parameter is actually a {@linkplain ParameterValueGroup parameter group},
     * all contained parameters will be flattened in a single list.
     *
     * @param  parameter  the parameter to append to the WKT, or {@code null} if none.
     * @param  formatter  the formatter where to append the parameter.
     */
    public static void append(GeneralParameterValue parameter, final Formatter formatter) {
        if (parameter instanceof ParameterValueGroup) {
            boolean first = true;
            for (final GeneralParameterValue param : ((ParameterValueGroup) parameter).values()) {
                if (first) {
                    formatter.newLine();
                    first = false;
                }
                append(param, formatter);
            }
        }
        if (parameter instanceof ParameterValue<?>) {
            if (!(parameter instanceof FormattableObject)) {
                parameter = new DefaultParameterValue<>((ParameterValue<?>) parameter);
            }
            formatter.append((FormattableObject) parameter);
            formatter.newLine();
        }
    }

    /**
     * Returns {@code true} if the given parameter is defined in the EPSG code space. We handle EPSG
     * parameters in a special way because Apache SIS uses the EPSG geodetic dataset as the primary
     * source of coordinate operation definitions.
     *
     * <p>We intentionally don't define {@code isEPSG(OperationMethod)} method because the operation
     * method may be the inverse of an EPSG method (for example "Inverse of Mercator (variant A)")
     * which would not be recognized. Instead, {@code isEPSG(method.getParameters())} should work.</p>
     *
     * @param  descriptor   the parameter or group of parameters to inspect.
     * @param  ifUndefined  the value to return if the code space is undefined.
     * @return whether the given parameter is an EPSG parameter.
     */
    public static boolean isEPSG(final GeneralParameterDescriptor descriptor, final boolean ifUndefined) {
        if (descriptor != null) {
            final Identifier id = descriptor.getName();
            if (id != null) {
                final String cs = id.getCodeSpace();
                if (cs != null) {
                    return Constants.EPSG.equalsIgnoreCase(cs);
                }
            }
        }
        return ifUndefined;
    }

    /**
     * Suggests an amount of fraction digits to use for formatting numbers in each column of the given matrix.
     * The number of fraction digits may be negative if we could round the numbers to 10, 100, <i>etc</i>.
     *
     * @param  rows  the matrix rows. It is not required that each row has the same length.
     * @return suggested amount of fraction digits as an array as long as the longest row.
     *
     * @see org.apache.sis.referencing.operation.matrix.Matrices#toString(Matrix)
     */
    public static int[] suggestFractionDigits(final Vector[] rows) {
        int length = 0;
        final int n = rows.length - 1;
        for (int j=0; j <= n; j++) {
            final int rl = rows[j].size();
            if (rl > length) length = rl;
        }
        final int[] fractionDigits = new int[length];
        final Statistics stats = new Statistics(null);
        for (int i=0; i<length; i++) {
            boolean isInteger = true;
            for (final Vector row : rows) {
                if (row.size() > i) {
                    final double value = row.doubleValue(i);
                    stats.accept(value);
                    if (isInteger && Math.floor(value) != value && !Double.isNaN(value)) {
                        isInteger = false;
                    }
                }
            }
            if (!isInteger) {
                fractionDigits[i] = Numerics.suggestFractionDigits(stats);
            }
            stats.reset();
        }
        return fractionDigits;
    }

    /**
     * Suggests an amount of fraction digits to use for formatting numbers in each column of the given sequence
     * of points. The number of fraction digits may be negative if we could round the numbers to 10, <i>etc</i>.
     *
     * @param  crs     the coordinate reference system for each points, or {@code null} if unknown.
     * @param  points  the sequence of points. It is not required that each point has the same dimension.
     * @return suggested amount of fraction digits as an array as long as the longest row.
     */
    public static int[] suggestFractionDigits(final CoordinateReferenceSystem crs, final Vector[] points) {
        final int[] fractionDigits = suggestFractionDigits(points);
        final Ellipsoid ellipsoid = ReferencingUtilities.getEllipsoid(crs);
        if (ellipsoid != null) {
            /*
             * Use heuristic precisions for geodetic or projected CRS. We do not apply those heuristics
             * for other kind of CRS (e.g. engineering) because we do not know what could be the size
             * of the object attached to the CRS.
             */
            final CoordinateSystem cs = crs.getCoordinateSystem();
            final int dimension = Math.min(cs.getDimension(), fractionDigits.length);
            final double scale = Formulas.scaleComparedToEarth(ellipsoid);
            for (int i=0; i<dimension; i++) {
                final Unit<?> unit = cs.getAxis(i).getUnit();
                double precision;
                if (Units.isLinear(unit)) {
                    precision = Formulas.LINEAR_TOLERANCE * scale;                          // In metres
                } else if (Units.isAngular(unit)) {
                    precision = Formulas.ANGULAR_TOLERANCE * (Math.PI / 180) * scale;       // In radians
                } else if (Units.isTemporal(unit)) {
                    precision = Formulas.TEMPORAL_TOLERANCE;                                // In seconds
                } else {
                    continue;
                }
                precision /= Units.toStandardUnit(unit);                // In units used by the coordinates.
                final int f = DecimalFunctions.fractionDigitsForDelta(precision, false);
                if (f > fractionDigits[i]) {
                    fractionDigits[i] = f;                              // Use at least the heuristic precision.
                }
            }
        }
        return fractionDigits;
    }

    /**
     * Returns the values in the corners and in the center of the given tensor. The values are returned in a
     * <var>n</var>-dimensional array of {@link Number} where <var>n</var> is the length of {@code size}.
     * If some values have been skipped, {@code null} values are inserted in the rows or columns where the
     * skipping occurs. Caller may replace null values by {@code "…"} string at formatting time for example.
     *
     * <p>Indices of elements in the returned array are in reverse order than in the {@code size} argument.
     * For example if {@code size} contains the values for dimensions (x,y,z) in that order, then elements
     * in the returned array are accessed with {@code cornersAndCenter[z][y][x]} indices in that order.
     * It is done that way because in the common case where there is only two dimensions,
     * {@code cornersAndCenter[y]} is a row. This is what WKT formatter expects among others.</p>
     *
     * @param  tensor      function providing values of the tensor. Inputs are indices of the desired value with
     *                     index in each dimension ranging from 0 inclusive to {@code size[dimension]} exclusive.
     * @param  size        size of the tensor. The length of this array is the tensor dimension.
     * @param  cornerSize  number of values to keep in each corner.
     * @return <var>n</var>-dimensional array of {@link Number} containing corners and center of the given tensor.
     *
     * @since 1.0
     */
    public static Object[] cornersAndCenter(final Function<int[],Number> tensor, final int[] size, final int cornerSize) {
        /*
         * The `source` array will contain indices of values to fetch in the tensor, and the `target` array will contain
         * indices where to store those values in the returned data structure. Other arrays contain threshold indices of
         * points of interest in the target data structure.
         */
        final int sizeLimit = cornerSize*2 + 1;
        final int[] shown = size.clone();
        final int[] empty = size.clone();           // Target index of row/column to leave empty, or an unreachable value if none.
        for (int d=0; d<shown.length; d++) {
            if (shown[d] > sizeLimit) {
                shown[d] = sizeLimit;
                empty[d] = cornerSize;
            }
        }
        final int[] source = new int[shown.length];
        final int[] target = new int[shown.length];
        final Object[] numbers;
        {
            final int[] reversed = new int[shown.length];
            for (int i=0; i<reversed.length;) {
                reversed[i] = shown[shown.length - ++i];
            }
            numbers = (Object[]) Array.newInstance(Number.class, reversed);
        }
        /*
         * The loops below are used for simulating GOTO statements. This is usually a deprecated practice,
         * but in this case we can hardly use normal loops because the number of nested loops is dynamic.
         * We want something equivalent to the code below where `n` - the number of nested loops - is not
         * known at compile-time:
         *
         * for (int i0=0; i0<size[0]; i0++) {
         *     for (int i1=0; i1<size[1]; i1++) {
         *         for (int i2=0; i2<size[2]; i2++) {
         *             // ... etc ...
         *             for (int in=0; in<size[n]; in++) {
         *             }
         *         }
         *     }
         * }
         *
         * Since we can not have a varying number of nested loops in the code, we achieve the same effect with
         * GOTO-like statements. It would be possible to achieve the same effect with recursive method calls,
         * but the GOTO-like approach is a little bit more compact.
         */
        Number[] row = null;
fill:   for (;;) {
            if (row == null) {
                Object[] walk = numbers;
                for (int d=shown.length; --d >= 1;) {
                    walk = (Object[]) walk[target[d]];
                }
                row = (Number[]) walk;
            }
            row[target[0]] = tensor.apply(source);
            for (int d=0;;) {
                source[d]++;
                final int p = ++target[d];
                if (p == shown[d]) {            // End of row (or higher dimension). This check must be first.
                    row = null;
                    source[d] = 0;
                    target[d] = 0;
                    if (++d >= shown.length) {
                        break fill;
                    }
                    // Continue loop for incrementing the higher dimension.
                } else {
                    switch (p - empty[d]) {
                        case 0:  continue;          // Column/row to leave null. Continue the loop for moving to next column/row.
                        case 1:  source[d] = size[d] - cornerSize;            // Skip source columns/rows (or higher dimensions).
                    }
                    continue fill;                  // Stop incrementing indices and fetch the value at current location.
                }
            }
        }
        /*
         * Add the center value in the empty location (in the middle).
         */
        Object walk = numbers;
        Object[] previous = null;
        for (int d=size.length; --d >= 0;) {
            final int p = empty[d];
            previous = (Object[]) walk;
            if (p >= previous.length) {
                return numbers;
            }
            walk = previous[p];
            source[d] = size[d] / 2;
        }
        assert walk == previous[empty[0]];
        if (walk == null) {
            previous[empty[0]] = tensor.apply(source);
        }
        return numbers;
    }
}
