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

import java.util.Iterator;
import java.util.Collection;
import java.util.Locale;
import java.util.TimeZone;
import java.text.Format;

import org.opengis.util.FactoryException;
import org.opengis.util.InternationalString;
import org.opengis.parameter.ParameterDescriptor;
import org.opengis.referencing.IdentifiedObject;
import org.opengis.referencing.crs.SingleCRS;
import org.opengis.referencing.crs.VerticalCRS;
import org.opengis.referencing.crs.GeographicCRS;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.cs.CoordinateSystem;
import org.opengis.referencing.operation.TransformException;
import org.opengis.referencing.operation.CoordinateOperation;
import org.opengis.referencing.operation.CoordinateOperationFactory;
import org.opengis.metadata.Identifier;
import org.opengis.metadata.citation.Citation;
import org.opengis.metadata.citation.OnLineFunction;
import org.opengis.metadata.citation.OnlineResource;
import org.opengis.metadata.extent.GeographicBoundingBox;
import org.opengis.metadata.extent.GeographicExtent;
import org.opengis.metadata.extent.VerticalExtent;
import org.opengis.geometry.DirectPosition;
import org.opengis.geometry.Envelope;

import org.apache.sis.geometry.Envelopes;
import org.apache.sis.geometry.AbstractEnvelope;
import org.apache.sis.geometry.DirectPosition2D;
import org.apache.sis.geometry.CoordinateFormat;
import org.apache.sis.referencing.CRS;
import org.apache.sis.referencing.CommonCRS;
import org.apache.sis.referencing.IdentifiedObjects;
import org.apache.sis.parameter.DefaultParameterDescriptor;
import org.apache.sis.metadata.iso.extent.DefaultExtent;
import org.apache.sis.metadata.iso.extent.DefaultVerticalExtent;
import org.apache.sis.metadata.iso.extent.DefaultTemporalExtent;
import org.apache.sis.metadata.iso.extent.DefaultSpatialTemporalExtent;
import org.apache.sis.metadata.iso.extent.DefaultGeographicBoundingBox;
import org.apache.sis.measure.Latitude;
import org.apache.sis.measure.Longitude;
import org.apache.sis.internal.metadata.ReferencingServices;
import org.apache.sis.internal.system.Modules;
import org.apache.sis.internal.util.Constants;
import org.apache.sis.util.resources.Vocabulary;
import org.apache.sis.util.resources.Errors;
import org.apache.sis.util.logging.Logging;
import org.apache.sis.util.Exceptions;
import org.apache.sis.util.Utilities;

import static java.util.logging.Logger.getLogger;


/**
 * Implements the referencing services needed by the {@code "sis-metadata"} module.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.1
 * @since   0.5
 * @module
 */
public final class ServicesForMetadata extends ReferencingServices {
    /**
     * Name of an {@link OnLineFunction} code list value, used for transferring information about the EPSG database.
     */
    public static final String CONNECTION = "CONNECTION";

    /**
     * Creates a new instance. This constructor is invoked by reflection only.
     */
    public ServicesForMetadata() {
    }




    ///////////////////////////////////////////////////////////////////////////////////////
    ////                                                                               ////
    ////                        SERVICES FOR ISO 19115 METADATA                        ////
    ////                                                                               ////
    ///////////////////////////////////////////////////////////////////////////////////////

    /**
     * Creates an exception message for a spatial, vertical or temporal dimension not found.
     * The given key must be one of {@code Resources.Keys} constants.
     */
    private static String dimensionNotFound(final short resourceKey, final CoordinateReferenceSystem crs) {
        if (crs == null) {
            return Errors.format(Errors.Keys.UnspecifiedCRS);
        } else {
            return Resources.format(resourceKey, crs.getName());
        }
    }

    /**
     * Implementation of the public {@code setBounds(…, DefaultGeographicBoundingBox, …)} methods for
     * the horizontal extent. If the {@code crs} argument is null, then it is caller's responsibility
     * to ensure that the given envelope is two-dimensional.
     *
     * <p>If {@code findOpCaller} is non-null, then this method will be executed in optional mode:
     * some failures will cause this method to return {@code null} instead of throwing an exception.
     * Note that {@link TransformException} may still be thrown but not directly by this method.
     * Warning may be logged, but in such case this method presumes that public caller is the named
     * method from {@link Envelopes} — typically {@link Envelopes#findOperation(Envelope, Envelope)}.</p>
     *
     * @param  envelope       the source envelope.
     * @param  target         the target bounding box, or {@code null} for creating it automatically.
     * @param  crs            the envelope CRS, or {@code null} if unknown.
     * @param  normalizedCRS  the horizontal component of the given CRS, or null if the {@code crs} argument is null.
     * @param  findOpCaller   non-null for replacing some (not all) exceptions by {@code null} return value.
     * @return the bounding box or {@code null} on failure. Never {@code null} if {@code findOpCaller} argument is {@code null}.
     * @throws TransformException if the given envelope can not be transformed.
     */
    private static DefaultGeographicBoundingBox setGeographicExtent(Envelope envelope, DefaultGeographicBoundingBox target,
            final CoordinateReferenceSystem crs, final GeographicCRS normalizedCRS, final String findOpCaller) throws TransformException
    {
        if (normalizedCRS != null) {
            // No need to check for dimension, since GeodeticCRS can not have less than 2.
            final CoordinateSystem cs1 = crs.getCoordinateSystem();
            final CoordinateSystem cs2 = normalizedCRS.getCoordinateSystem();
            if (!Utilities.equalsIgnoreMetadata(cs2.getAxis(0), cs1.getAxis(0)) ||
                !Utilities.equalsIgnoreMetadata(cs2.getAxis(1), cs1.getAxis(1)))
            {
                final CoordinateOperation operation;
                final CoordinateOperationFactory factory = CoordinateOperations.factory();
                try {
                    operation = factory.createOperation(crs, normalizedCRS);
                } catch (FactoryException e) {
                    if (findOpCaller != null) {
                        // See javadoc for the assumption that optional mode is used by Envelopes.findOperation(…).
                        Logging.recoverableException(getLogger(Modules.REFERENCING), Envelopes.class, findOpCaller, e);
                        return null;
                    }
                    throw new TransformException(Resources.format(Resources.Keys.CanNotTransformEnvelopeToGeodetic), e);
                }
                envelope = Envelopes.transform(operation, envelope);
            }
        }
        /*
         * At this point, the envelope should use (longitude, latitude) coordinates in degrees.
         * The envelope may cross the anti-meridian if the envelope implementation is an Apache SIS one.
         * For other implementations, the longitude range may be conservatively expanded to [-180 … 180]°.
         */
        double westBoundLongitude, eastBoundLongitude;
        double southBoundLatitude, northBoundLatitude;
        if (envelope instanceof AbstractEnvelope) {
            final AbstractEnvelope ae = (AbstractEnvelope) envelope;
            westBoundLongitude = ae.getLower(0);
            eastBoundLongitude = ae.getUpper(0);            // Cross anti-meridian if eastBoundLongitude < westBoundLongitude.
            southBoundLatitude = ae.getLower(1);
            northBoundLatitude = ae.getUpper(1);
        } else {
            westBoundLongitude = envelope.getMinimum(0);
            eastBoundLongitude = envelope.getMaximum(0);    // Expanded to [-180 … 180]° if it was crossing the anti-meridian.
            southBoundLatitude = envelope.getMinimum(1);
            northBoundLatitude = envelope.getMaximum(1);
        }
        /*
         * The envelope transformation at the beginning of this method intentionally avoided to apply datum shift.
         * This implies that the prime meridian has not been changed and may be something else than Greenwich.
         * We need to take it in account manually.
         *
         * Note that there is no need to normalize the longitudes back to the [-180 … +180]° range after the rotation, or
         * to verify if the longitude span is 360°. Those verifications will be done automatically by target.setBounds(…).
         */
        if (normalizedCRS != null) {
            final double rotation = CRS.getGreenwichLongitude(normalizedCRS);
            westBoundLongitude += rotation;
            eastBoundLongitude += rotation;
        }
        /*
         * In the particular case where this method is invoked (indirectly) for Envelopes.findOperation(…) purposes,
         * replace NaN values by the whole world.  We do that only for Envelopes.findOperation(…) since we know that
         * the geographic bounding box will be used for choosing a CRS, and a conservative approach is to select the
         * CRS valid in the widest area. If this method is invoked for other usages, then we keep NaN values because
         * we don't know the context (union, intersection, something else?).
         */
        if (findOpCaller != null) {
            if (Double.isNaN(southBoundLatitude)) southBoundLatitude = Latitude.MIN_VALUE;
            if (Double.isNaN(northBoundLatitude)) northBoundLatitude = Latitude.MAX_VALUE;
            if (Double.isNaN(eastBoundLongitude) || Double.isNaN(westBoundLongitude)) {
                // Conservatively set the two bounds because may be crossing the anti-meridian.
                eastBoundLongitude = Longitude.MIN_VALUE;
                westBoundLongitude = Longitude.MAX_VALUE;
            }
        }
        if (target == null) {
            target = new DefaultGeographicBoundingBox();
        }
        target.setBounds(westBoundLongitude, eastBoundLongitude, southBoundLatitude, northBoundLatitude);
        target.setInclusion(Boolean.TRUE);
        return target;
    }

    /**
     * Implementation of the public {@code setBounds} methods for the vertical extent.
     * If the {@code crs} argument is null, then it is caller's responsibility to ensure
     * that the given envelope is one-dimensional.
     *
     * @param  envelope     the source envelope.
     * @param  target       the target vertical extent.
     * @param  crs          the envelope CRS, or {@code null} if unknown.
     * @param  verticalCRS  the vertical component of the given CRS, or null if the {@code crs} argument is null.
     */
    private static void setVerticalExtent(final Envelope envelope, final DefaultVerticalExtent target,
            final CoordinateReferenceSystem crs, final VerticalCRS verticalCRS)
    {
        final int dim;
        if (verticalCRS == null) {
            dim = 0;
        } else {
            dim = AxisDirections.indexOfColinear(crs.getCoordinateSystem(), verticalCRS.getCoordinateSystem());
            assert dim >= 0 : crs;      // Should not fail since 'verticalCRS' has been extracted from 'crs' by the caller.
        }
        target.setMinimumValue(envelope.getMinimum(dim));
        target.setMaximumValue(envelope.getMaximum(dim));
        target.setVerticalCRS(verticalCRS);
    }

    /**
     * Sets a geographic bounding box from the specified envelope.
     * If the envelope has no CRS, then (<var>longitude</var>, <var>latitude</var>) axis order is assumed.
     * If the envelope CRS is not geographic, then the envelope will be transformed to a geographic CRS.
     * If {@code findOpCaller} is {@code true}, then some failures will cause this method to return {@code null}
     * instead of throwing an exception, and warning may be logged with assumption that caller is the named
     * method from {@link Envelopes} — typically {@link Envelopes#findOperation(Envelope, Envelope)}.
     *
     * @param  envelope      the source envelope.
     * @param  target        the target bounding box, or {@code null} for creating it automatically.
     * @param  findOpCaller  non-null for replacing some (not all) exceptions by {@code null} return value.
     * @return the bounding box or {@code null} on failure. Never {@code null} if {@code findOpCaller} argument is {@code null}.
     * @throws TransformException if the given envelope can not be transformed.
     */
    @Override
    public DefaultGeographicBoundingBox setBounds(final Envelope envelope, final DefaultGeographicBoundingBox target,
            final String findOpCaller) throws TransformException
    {
        final CoordinateReferenceSystem crs = envelope.getCoordinateReferenceSystem();
        GeographicCRS normalizedCRS = ReferencingUtilities.toNormalizedGeographicCRS(crs, false, false);
        if (normalizedCRS == null) {
            if (crs != null) {
                normalizedCRS = CommonCRS.defaultGeographic();
            } else if (envelope.getDimension() != 2) {
                if (findOpCaller != null) return null;
                throw new TransformException(dimensionNotFound(Resources.Keys.MissingHorizontalDimension_1, crs));
            }
        }
        return setGeographicExtent(envelope, target, crs, normalizedCRS, findOpCaller);
    }

    /**
     * Sets a vertical extent with the value inferred from the given envelope.
     * Only the vertical coordinates are extracted; all other coordinates are ignored.
     *
     * @param  envelope  the source envelope.
     * @param  target    the target vertical extent where to store envelope information.
     * @throws TransformException if no vertical component can be extracted from the given envelope.
     */
    @Override
    public void setBounds(final Envelope envelope, final DefaultVerticalExtent target) throws TransformException {
        final CoordinateReferenceSystem crs = envelope.getCoordinateReferenceSystem();
        final VerticalCRS verticalCRS = CRS.getVerticalComponent(crs, true);
        if (verticalCRS == null && envelope.getDimension() != 1) {
            throw new TransformException(dimensionNotFound(Resources.Keys.MissingVerticalDimension_1, crs));
        }
        setVerticalExtent(envelope, target, crs, verticalCRS);
    }

    /**
     * Sets a temporal extent with the value inferred from the given envelope.
     * Only the vertical coordinates are extracted; all other coordinates are ignored.
     *
     * @param  envelope  the source envelope.
     * @param  target    the target temporal extent where to store envelope information.
     * @throws TransformException if no temporal component can be extracted from the given envelope.
     */
    @Override
    public void setBounds(final Envelope envelope, final DefaultTemporalExtent target) throws TransformException {
        final CoordinateReferenceSystem crs = envelope.getCoordinateReferenceSystem();
        final TemporalAccessor accessor = TemporalAccessor.of(crs, 0);
        if (accessor == null) {                     // Mandatory for the conversion from numbers to dates.
            throw new TransformException(dimensionNotFound(Resources.Keys.MissingTemporalDimension_1, crs));
        }
        accessor.setTemporalExtent(envelope, target);
    }

    /**
     * Sets the geographic, vertical and temporal extents with the values inferred from the given envelope.
     * If the given {@code target} has more geographic or vertical extents than needed (0 or 1), then the
     * extraneous extents are removed.
     *
     * @param  envelope  the source envelope.
     * @param  target    the target spatiotemporal extent where to store envelope information.
     * @throws TransformException if no temporal component can be extracted from the given envelope.
     */
    @Override
    public void setBounds(final Envelope envelope, final DefaultSpatialTemporalExtent target) throws TransformException {
        final CoordinateReferenceSystem crs = envelope.getCoordinateReferenceSystem();
        final SingleCRS horizontalCRS = CRS.getHorizontalComponent(crs);
        final VerticalCRS verticalCRS = CRS.getVerticalComponent(crs, true);
        final TemporalAccessor accessor = TemporalAccessor.of(crs, 0);
        if (horizontalCRS == null && verticalCRS == null && accessor == null) {
            throw new TransformException(dimensionNotFound(Resources.Keys.MissingSpatioTemporalDimension_1, crs));
        }
        /*
         * Try to set the geographic bounding box first, because this operation may fail with a
         * TransformException while the other operations (vertical and temporal) should not fail.
         * So doing the geographic part first help us to get a "all or nothing" behavior.
         */
        DefaultGeographicBoundingBox box = null;
        boolean useExistingBox = (horizontalCRS != null);
        final Collection<GeographicExtent> spatialExtents = target.getSpatialExtent();
        final Iterator<GeographicExtent> it = spatialExtents.iterator();
        while (it.hasNext()) {
            final GeographicExtent extent = it.next();
            if (extent instanceof GeographicBoundingBox) {
                if (useExistingBox && (extent instanceof DefaultGeographicBoundingBox)) {
                    box = (DefaultGeographicBoundingBox) extent;
                    useExistingBox = false;
                } else {
                    it.remove();
                }
            }
        }
        if (horizontalCRS != null) {
            if (box == null) {
                box = new DefaultGeographicBoundingBox();
                spatialExtents.add(box);
            }
            GeographicCRS normalizedCRS = ReferencingUtilities.toNormalizedGeographicCRS(crs, false, false);
            if (normalizedCRS == null) {
                normalizedCRS = CommonCRS.defaultGeographic();
            }
            setGeographicExtent(envelope, box, crs, normalizedCRS, null);
        }
        /*
         * Other dimensions (vertical and temporal).
         */
        if (verticalCRS != null) {
            VerticalExtent e = target.getVerticalExtent();
            if (!(e instanceof DefaultVerticalExtent)) {
                e = new DefaultVerticalExtent();
                target.setVerticalExtent(e);
            }
            setVerticalExtent(envelope, (DefaultVerticalExtent) e, crs, verticalCRS);
        } else {
            target.setVerticalExtent(null);
        }
        if (accessor != null) {
            accessor.setTemporalExtent(envelope, target);
        } else {
            target.setExtent(null);
        }
    }

    /**
     * Initializes a horizontal, vertical and temporal extent with the values inferred from the given envelope.
     *
     * @param  envelope  the source envelope.
     * @param  target    the target extent where to store envelope information.
     * @throws TransformException if a coordinate transformation was required and failed.
     * @throws UnsupportedOperationException if this method requires an Apache SIS module
     *         which has been found on the classpath.
     */
    @Override
    public void addElements(final Envelope envelope, final DefaultExtent target) throws TransformException {
        final CoordinateReferenceSystem crs = envelope.getCoordinateReferenceSystem();
        final SingleCRS horizontalCRS = CRS.getHorizontalComponent(crs);
        final VerticalCRS verticalCRS = CRS.getVerticalComponent(crs, true);
        final TemporalAccessor accessor = TemporalAccessor.of(crs, 0);
        if (horizontalCRS == null && verticalCRS == null && accessor == null) {
            throw new TransformException(dimensionNotFound(Resources.Keys.MissingSpatioTemporalDimension_1, crs));
        }
        if (horizontalCRS != null) {
            target.getGeographicElements().add(setBounds(envelope, null, null));
        }
        if (verticalCRS != null) {
            final DefaultVerticalExtent extent = new DefaultVerticalExtent();
            setVerticalExtent(envelope, extent, crs, verticalCRS);
            target.getVerticalElements().add(extent);
        }
        if (accessor != null) {
            final DefaultTemporalExtent extent = new DefaultTemporalExtent();
            accessor.setTemporalExtent(envelope, extent);
            target.getTemporalElements().add(extent);
        }
    }

    /**
     * Creates a two-dimensional geographic position associated to the default geographic CRS.
     * Axis order is (longitude, latitude).
     *
     * @param  λ  the longitude value.
     * @param  φ  the latitude value.
     * @return the direct position for the given geographic coordinate.
     *
     * @since 0.8
     */
    @Override
    public DirectPosition geographic(final double λ, final double φ) {
        return new DirectPosition2D(CommonCRS.defaultGeographic(), λ, φ);
    }

    /**
     * Returns an identifier for the given object, giving precedence to EPSG identifier if available.
     * The returned string should be of the form {@code "AUTHORITY:CODE"} if possible (no guarantees).
     *
     * @param  object  the object for which to get an identifier.
     * @return an identifier for the given object, with preference given to EPSG codes.
     * @throws FactoryException if an error occurred while searching for the EPSG code.
     *
     * @since 1.0
     */
    @Override
    public String getPreferredIdentifier(final IdentifiedObject object) throws FactoryException {
        final Integer code = IdentifiedObjects.lookupEPSG(object);
        if (code != null) {
            return Constants.EPSG + Constants.DEFAULT_SEPARATOR + code;
        }
        /*
         * If above code did not found an EPSG code, discard EPSG codes that
         * we may find in the loop below because they are probably invalid.
         */
        for (final Identifier id : object.getIdentifiers()) {
            if (!Constants.EPSG.equalsIgnoreCase(id.getCodeSpace())) {
                return IdentifiedObjects.toString(id);
            }
        }
        return IdentifiedObjects.getSimpleNameOrIdentifier(object);
    }




    ///////////////////////////////////////////////////////////////////////////////////////
    ////                                                                               ////
    ////                          OTHER REFERENCING SERVICES                           ////
    ////                                                                               ////
    ///////////////////////////////////////////////////////////////////////////////////////

    /**
     * Returns a fully implemented parameter descriptor.
     *
     * @param  parameter  a partially implemented parameter descriptor, or {@code null}.
     * @return a fully implemented parameter descriptor, or {@code null} if the given argument was null.
     */
    @Override
    public ParameterDescriptor<?> toImplementation(final ParameterDescriptor<?> parameter) {
        return DefaultParameterDescriptor.castOrCopy(parameter);
    }

    /**
     * Creates a format for {@link DirectPosition} instances.
     *
     * @param  locale    the locale for the new {@code Format}, or {@code null} for {@code Locale.ROOT}.
     * @param  timezone  the timezone, or {@code null} for UTC.
     * @return a {@link org.apache.sis.geometry.CoordinateFormat}.
     *
     * @since 0.8
     */
    @Override
    public Format createCoordinateFormat(final Locale locale, final TimeZone timezone) {
        return new CoordinateFormat(locale, timezone);
    }

    /**
     * Returns the default coordinate operation factory.
     *
     * @return the coordinate operation factory to use.
     */
    @Override
    public CoordinateOperationFactory getCoordinateOperationFactory() {
        return CoordinateOperations.factory();
    }

    /**
     * Returns information about the Apache SIS configuration.
     * See super-class for a list of keys.
     *
     * @param  key     a key identifying the information to return.
     * @param  locale  language to use if possible.
     * @return the information, or {@code null} if none.
     */
    @Override
    public String getInformation(final String key, final Locale locale) {
        switch (key) {
            /*
             * Get the version of the EPSG database and the version of the database software.
             * This operation can be relatively costly as it may open a JDBC connection.
             */
            case Constants.EPSG: {
                final Citation authority;
                try {
                    authority = CRS.getAuthorityFactory(Constants.EPSG).getAuthority();
                } catch (FactoryException e) {
                    final String msg = Exceptions.getLocalizedMessage(e, locale);
                    return (msg != null) ? msg : e.toString();
                }
                if (authority != null) {
                    final OnLineFunction f = OnLineFunction.valueOf(CONNECTION);
                    for (final OnlineResource res : authority.getOnlineResources()) {
                        if (f.equals(res.getFunction())) {
                            final InternationalString i18n = res.getDescription();
                            if (i18n != null) return i18n.toString(locale);
                        }
                    }
                    final InternationalString i18n = authority.getTitle();
                    if (i18n != null) return i18n.toString(locale);
                }
                return Vocabulary.getResources(locale).getString(Vocabulary.Keys.Untitled);
            }
            // More cases may be added in future SIS versions.
        }
        return super.getInformation(key, locale);
    }
}
