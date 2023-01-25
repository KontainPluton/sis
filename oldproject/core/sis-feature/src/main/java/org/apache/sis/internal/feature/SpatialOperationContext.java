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
package org.apache.sis.internal.feature;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import javax.measure.Unit;
import javax.measure.IncommensurableException;
import org.apache.sis.internal.referencing.ReferencingFactoryContainer;
import org.opengis.util.FactoryException;
import org.opengis.geometry.DirectPosition;
import org.opengis.metadata.extent.GeographicBoundingBox;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.referencing.cs.CartesianCS;
import org.opengis.referencing.cs.AxisDirection;
import org.opengis.referencing.cs.CoordinateSystem;
import org.opengis.referencing.cs.CoordinateSystemAxis;
import org.opengis.referencing.crs.ProjectedCRS;
import org.opengis.referencing.crs.GeographicCRS;
import org.opengis.referencing.crs.GeneralDerivedCRS;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.OperationMethod;
import org.opengis.referencing.operation.TransformException;
import org.apache.sis.util.collection.BackingStoreException;
import org.apache.sis.internal.referencing.ReferencingUtilities;
import org.apache.sis.referencing.operation.DefaultConversion;
import org.apache.sis.referencing.crs.DefaultProjectedCRS;
import org.apache.sis.referencing.ImmutableIdentifier;
import org.apache.sis.referencing.CRS;
import org.apache.sis.measure.Units;
import org.apache.sis.util.Utilities;
import org.apache.sis.util.resources.Errors;
import org.apache.sis.internal.util.Constants;
import org.apache.sis.metadata.iso.citation.Citations;

// Branch-dependent imports
import org.opengis.filter.SpatialOperatorName;
import org.opengis.filter.DistanceOperatorName;


/**
 * Context (such as desired CRS) in which a spatial operator will be executed.
 *
 * <p>Instances of this class are immutable and thread-safe.</p>
 *
 * <p>The serialization form is not a committed API and may change in any future version.</p>
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.1
 * @since   1.1
 * @module
 */
public final class SpatialOperationContext implements Serializable {
    /**
     * For cross-version compatibility.
     */
    private static final long serialVersionUID = -6197547343970700471L;

    /**
     * The {@value} value, for identifying code that assume two-dimensional objects.
     */
    private static final int BIDIMENSIONAL = 2;

    /**
     * Approximate geographic area of geometries, or {@code null} if unspecified.
     */
    private final GeographicBoundingBox areaOfInterest;

    /**
     * The target CRS in which to transform geometries, or {@code null} for inferring automatically.
     */
    private final CoordinateReferenceSystem computationCRS;

    /**
     * If the CRS needs to be in some units of measurement, the {@link Unit#getSystemUnit()} value.
     * For example is units need to be linear, then {@code systemUnit} shall be {@link Units#METRE}.
     * Note that it does not mean that the units of measurement must be meters; only that they must
     * be compatible with meters.
     */
    private final Unit<?> systemUnit;

    /**
     * Index of the geometry associated to the common CRS, or -1 if none.
     * This is used for avoiding unnecessary check of its CRS.
     */
    private final int skipIndex;

    /**
     * The common CRS found by {@link #transform(GeometryWrapper[])}. May be null.
     */
    CoordinateReferenceSystem commonCRS;

    /**
     * Creates a new context.
     *
     * @param  areaOfInterest  approximate geographic area of geometries, or {@code null} if unspecified.
     * @param  literal         if a geometry operand is a literal, that literal. Otherwise {@code null}.
     * @param  systemUnit      if the CRS needs to be in some units of measurement, the {@link Unit#getSystemUnit()} value.
     * @param  skipIndex       index of the geometry associated to {@code commonCRS}, or -1 if none.
     * @throws FactoryException if an error occurred while fetching {@code literal} CRS.
     * @throws TransformException if a coordinate conversion was required but failed.
     * @throws IncommensurableException if a coordinate system does not use the expected units.
     */
    public SpatialOperationContext(final GeographicBoundingBox areaOfInterest, final GeometryWrapper<?> literal,
                                   final Unit<?> systemUnit, final int skipIndex)
            throws FactoryException, TransformException, IncommensurableException
    {
        this.areaOfInterest = areaOfInterest;
        this.systemUnit     = systemUnit;
        this.skipIndex      = skipIndex;
        if (literal == null) {
            computationCRS = null;
        } else try {
            CoordinateReferenceSystem crs = to2D(literal.getCoordinateReferenceSystem());
            if (systemUnit != null && crs != null) {
                crs = usingSystemUnit(literal, crs, crs, systemUnit);
            }
            computationCRS = crs;
        } catch (BackingStoreException e) {
            throw e.unwrapOrRethrow(FactoryException.class);
        }
    }

    /**
     * Returns the two first dimensions of the given CRS. This is usually the {@code crs} argument unchanged
     * (which may be {@code null}), unless a three- or four-dimensional CRS has been specified.
     * We work with two dimensional CRS because the wrapped geometries are 2D and for avoiding
     * that {@link ReferencingUtilities#getUnit(CoordinateSystem)} returns {@code null}.
     */
    private static CoordinateReferenceSystem to2D(CoordinateReferenceSystem crs) {
        if (ReferencingUtilities.getDimension(crs) > BIDIMENSIONAL) {
            crs = CRS.getComponentAt(crs, 0, BIDIMENSIONAL);
        }
        return crs;
    }

    /**
     * Transforms the specified geometry to the computation CRS.
     * Geometries with unspecified CRS will be used as-is, without transformation.
     * The common CRS is stored in the {@link #commonCRS} field.
     *
     * @param  <G>       geometry implementation base class.
     * @param  geometry  the geometry to transform.
     * @return the transformed geometry, or the same instance if no transformation was applied.
     * @throws FactoryException if an error occurred while fetching a CRS.
     * @throws TransformException if a coordinate conversion was required but failed.
     */
    public <G> GeometryWrapper<G> transform(GeometryWrapper<G> geometry) throws FactoryException, TransformException {
        if (computationCRS != null) {
            final CoordinateReferenceSystem sourceCRS = to2D(geometry.getCoordinateReferenceSystem());
            if (sourceCRS != null && !Utilities.equalsIgnoreMetadata(computationCRS, sourceCRS)) {
                geometry = geometry.transform(CRS.findOperation(sourceCRS, computationCRS, areaOfInterest), false);
            }
        }
        return geometry;
    }

    /**
     * Transforms the specified geometries to the common CRS.
     * If {@link #computationCRS} is {@code null}, then this method tries to infer one automatically.
     * Geometries with unspecified CRS will be used as-is, without transformation.
     * The common CRS is stored in the {@link #commonCRS} field.
     *
     * @param  <G>         geometry implementation base class.
     * @param  geometries  the geometries to transform. Results will be stored in-place.
     * @return whether this method has been able to find a common CRS.
     * @throws FactoryException if an error occurred while fetching a CRS.
     * @throws TransformException if a coordinate conversion was required but failed.
     * @throws IncommensurableException if a geographic CRS does not use angular units (should not happen).
     */
    final <G> boolean transform(final GeometryWrapper<G>[] geometries)
            throws FactoryException, TransformException, IncommensurableException
    {
        final CoordinateReferenceSystem[] allCRS = new CoordinateReferenceSystem[geometries.length];
        try {
            for (int i = allCRS.length; --i >= 0;) {
                allCRS[i] = (i == skipIndex) ? computationCRS : to2D(geometries[i].getCoordinateReferenceSystem());
            }
        } catch (BackingStoreException e) {
            throw e.unwrapOrRethrow(FactoryException.class);
        }
        /*
         * Search for an arbitrary non-null CRS. Then check in the next loop if all other CRS are equal,
         * ignoring metadata and ignoring null CRS. If this is the case, then we do not transform anything.
         */
        {
            int i = allCRS.length;
            do if (--i < 0) return true;
            while ((commonCRS = allCRS[i]) == null);
            /*
             * Found a CRS potentially common to all geometries (this will be checked in next loop).
             * But if units of measurement are restricted to some kind, we will accept that common CRS
             * only if its unit is of the requested type.
             */
            boolean reject = false;
            if (systemUnit != null && commonCRS != computationCRS) {
                reject = isCompatibleUnit(commonCRS, systemUnit);
            }
            if (!reject) {
                // Potential common CRS accepted, verify if all other geometries use the same CRS.
                CoordinateReferenceSystem crs;
                do {
                    if (--i < 0) return true;       // All geometries are in common CRS.
                    crs = allCRS[i];
                } while (crs == null || Utilities.equalsIgnoreMetadata(commonCRS, crs));
            }
        }
        /*
         * At least one geometry needs to be transformed. Get a CRS which can be a common target
         * for the specified geometries. The `if` block should be executed only if both operands
         * are dynamic (non-literal) values. The rules applied inside the block are arbitrary
         * and may change in any future version.
         */
        commonCRS = computationCRS;
select: if (commonCRS == null) {
            /*
             * If there is a restriction on the unit of measurement, check if an existing CRS
             * met that criterion. We do this check before to invoke `suggestCommonTarget(…)`
             * because that method may replace `ProjectedCRS` by `GeographicCRS` in order to
             * cover a larger area, and we usually want the `ProjectedCRS`.
             */
            if (systemUnit != null) {
                for (int i=allCRS.length; --i >= 0;) {
                    commonCRS = allCRS[i];
                    final Unit<?> unit = ReferencingUtilities.getUnit(commonCRS);
                    if (unit != null && systemUnit.equals(unit.getSystemUnit())) {
                        break select;       // Use the `commonCRS` we just found.
                    }
                }
            }
            /*
             * If there are no restrictions on units of measurement, or if no geometry CRS met that restriction,
             * request a CRS which may be different than the CRS of all geometries. The search takes in account
             * the CRS domains of validity. The CRS found may be derived in order to be made compatible with the
             * desired units of measurement.
             */
            commonCRS = CRS.suggestCommonTarget(areaOfInterest, allCRS);
            if (commonCRS == null) {
                return false;                       // Geometries are in incompatible CRS.
            }
            if (systemUnit != null) {
                for (int i=allCRS.length; --i >= 0;) {
                    final CoordinateReferenceSystem crs = allCRS[i];
                    if (crs != null) {
                        commonCRS = usingSystemUnit(geometries[i], crs, commonCRS, systemUnit);
                        break select;
                    }
                }
                return false;               // Unable to use the desired units of measurement.
            }
        }
        /*
         * At this point, `commonCRS` is the CRS to use for converting all geometries.
         * Perform the conversions in-place.
         */
        for (int i=0; i<allCRS.length; i++) {
            if (i != skipIndex) {
                final CoordinateReferenceSystem sourceCRS = allCRS[i];
                if (!Utilities.equalsIgnoreMetadata(commonCRS, sourceCRS)) {
                    geometries[i] = geometries[i].transform(CRS.findOperation(sourceCRS, commonCRS, areaOfInterest), false);
                }
            }
        }
        return true;
    }

    /**
     * Returns {@code true} if the units of measurement of the given CRS are compatible with the given units.
     * All CRS axes should have the same units, otherwise this method returns {@code false}.
     *
     * @param  crs         the CRS for which to test the units of measurement.
     * @param  systemUnit  the {@link Unit#getSystemUnit()} value of the desired unit.
     * @return whether the CRS units of measurement are compatible.
     */
    private static boolean isCompatibleUnit(final CoordinateReferenceSystem crs, final Unit<?> systemUnit) {
        final Unit<?> unit = ReferencingUtilities.getUnit(crs);
        return (unit != null) && systemUnit.equals(unit.getSystemUnit());
    }

    /**
     * Returns a coordinate reference system using the unit of measurement compatible with given system unit.
     * This is usually for creating a {@link ProjectedCRS} when the user requested linear units, but it can
     * also return {@link GeographicCRS} if the user requested angular units.
     *
     * @param  geometry     one of the geometry used in the operation. Will determine projection center.
     * @param  geometryCRS  the CRS of {@code geometry}.
     * @param  targetCRS    initial proposal of CRS to use for coordinate operation.
     * @param  systemUnit   {@link Unit#getSystemUnit()} value on the unit requested by user.
     * @return a CRS derived from {@code targetCRS} with units compatible with the specified units.
     * @throws FactoryException if an error occurred while fetching a CRS.
     * @throws TransformException if a coordinate conversion was required but failed.
     * @throws IncommensurableException if a coordinate system does not use the expected units.
     */
    private static CoordinateReferenceSystem usingSystemUnit(final GeometryWrapper<?>        geometry,
                                                             final CoordinateReferenceSystem geometryCRS,
                                                                   CoordinateReferenceSystem targetCRS,
                                                             final Unit<?>                   systemUnit)
            throws FactoryException, TransformException, IncommensurableException
    {
        while (!isCompatibleUnit(targetCRS, systemUnit)) {
            /*
             * If the target CRS uses (latitude, longitude) coordinates and the requested units
             * are metres (or compatible linear units), apply a map projection. We will use the
             * same datum than `targetCRS` for avoiding datum shift.
             */
            if (Units.isLinear(systemUnit) && targetCRS instanceof GeographicCRS) {
                return Projector.instance().create((GeographicCRS) targetCRS, geometry.getCentroid(), geometryCRS);
            }
            if (targetCRS instanceof GeneralDerivedCRS) {
                targetCRS = ((GeneralDerivedCRS) targetCRS).getBaseCRS();
            } else {
                throw new IncommensurableException(Errors.format(Errors.Keys.InconsistentUnitsForCS_1, systemUnit));
            }
        }
        return targetCRS;
    }

    /**
     * Creates projections centered on a given geometry.
     * This is defined in a separated class for lazy static field initialization.
     */
    private static final class Projector {
        /** A singleton map containing the name to assign to the CRS. */
        private final Map<String,?> name;

        /** The operation method for the map projection to use. */
        private final OperationMethod method;

        /** The coordinate system for projected CRS. */
        private final CartesianCS cartCS;

        /** Creates the {@link #INSTANCE} singleton. */
        private Projector() throws FactoryException {
            final ReferencingFactoryContainer f = new ReferencingFactoryContainer();
            method = f.getCoordinateOperationFactory().getOperationMethod("Mercator_2SP");
            cartCS = f.getStandardProjectedCS();
            name   = Collections.singletonMap(DefaultConversion.NAME_KEY,
                        new ImmutableIdentifier(Citations.SIS, "SIS", "Mercator for geometry"));
        }

        /**
         * Creates a projected CRS derived from the given geographic CRS.
         *
         * @param  baseCRS      the geographic CRS for which to derive a projected CRS.
         * @param  centroid     coordinate a the center of the geometry.
         * @param  geometryCRS  CRS of {@code centroid}.
         * @return CRS using Cartesian coordinate system.
         * @throws TransformException if a coordinate conversion was required but failed.
         * @throws IncommensurableException if a coordinate system does not use the expected units.
         */
        ProjectedCRS create(final GeographicCRS baseCRS, DirectPosition centroid, CoordinateReferenceSystem geometryCRS)
                throws FactoryException, TransformException, IncommensurableException
        {
            /*
             * We will need the (latitude, longitude) coordinates of projection center. If the CRS is derived
             * (including projected CRS case), convert the position to the base CRS, which should be geographic.
             * Note that a CRS can be both derived and geographic, so we need to do this check first in order to
             * avoid derived geographic CRS such as the ones having rotated poles.
             */
            while (geometryCRS instanceof GeneralDerivedCRS) {
                final GeneralDerivedCRS g = (GeneralDerivedCRS) geometryCRS;
                centroid = g.getConversionFromBase().getMathTransform().inverse().transform(centroid, centroid);
                geometryCRS = g.getBaseCRS();
            }
            if (!(geometryCRS instanceof GeographicCRS)) {
                throw new FactoryException(Errors.format(Errors.Keys.IllegalCRSType_1,
                        ReferencingUtilities.getInterface(CoordinateReferenceSystem.class, geometryCRS)));
            }
            /*
             * Get the latitude and longitude values in degrees, applying unit conversions if needed.
             * This code is much lighter than a call to `CRS.findOperation(…)` and also intentionally
             * avoids datum shifts.
             */
            final CoordinateSystem cs = geometryCRS.getCoordinateSystem();
            double latitude = Double.NaN, longitude = Double.NaN;
            for (int i=0; i<BIDIMENSIONAL; i++) {
                final CoordinateSystemAxis axis = cs.getAxis(i);
                double coordinate = centroid.getOrdinate(i);
                coordinate = axis.getUnit().getConverterToAny(Units.DEGREE).convert(coordinate);
                final AxisDirection direction = axis.getDirection();
                     if (direction == AxisDirection.NORTH) latitude  =  coordinate;
                else if (direction == AxisDirection.EAST)  longitude =  coordinate;
                else if (direction == AxisDirection.WEST)  longitude = -coordinate;
                else if (direction == AxisDirection.SOUTH) latitude  = -coordinate;
                else throw new FactoryException(Errors.format(Errors.Keys.UnsupportedAxisDirection_1, direction));
            }
            /*
             * Create a projected coordinate reference system for the geometry center.
             */
            final ParameterValueGroup p = method.getParameters().createValue();
            p.parameter(Constants.STANDARD_PARALLEL_1).setValue(latitude);
            p.parameter(Constants.CENTRAL_MERIDIAN).setValue(longitude);
            final DefaultConversion conversion = new DefaultConversion(name, method, null, p);
            return new DefaultProjectedCRS(name, baseCRS, conversion, cartCS);
        }

        /**
         * Returns an instance. Should be a singleton instance, unless its creating failed
         * at class initialization time in which case a new attempt will be made now.
         */
        static Projector instance() throws FactoryException {
            return (INSTANCE != null) ? INSTANCE : new Projector();
        }

        /** The singleton instance, or {@code null} if its creation failed. */
        private static final Projector INSTANCE;
        static {
            Projector b;
            try {
                b = new Projector();
            } catch (FactoryException e) {
                b = null;
            }
            INSTANCE = b;
        }
    }

    /**
     * The value to return when a test can not be applied. This method is defined for
     * having a single place to update if more operator types need to be recognized.
     *
     * @param   type  the test that could not be applied.
     * @return  the operation result to assume.
     */
    public static boolean negativeResult(final SpatialOperatorName type) {
        return type == SpatialOperatorName.DISJOINT;
    }

    /**
     * The value to return when a test can not be applied. This method is defined for
     * having a single place to update if more operator types need to be recognized.
     *
     * @param   type  the test that could not be applied.
     * @return  the operation result to assume.
     */
    public static boolean negativeResult(final DistanceOperatorName type) {
        return type == DistanceOperatorName.BEYOND;
    }
}
