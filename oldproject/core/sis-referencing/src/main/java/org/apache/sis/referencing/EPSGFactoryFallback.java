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
package org.apache.sis.referencing;

import java.util.Collections;
import java.util.Set;
import java.util.LinkedHashSet;
import javax.measure.Unit;
import org.opengis.referencing.IdentifiedObject;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.datum.DatumAuthorityFactory;
import org.opengis.referencing.datum.PrimeMeridian;
import org.opengis.referencing.datum.Ellipsoid;
import org.opengis.referencing.datum.Datum;
import org.opengis.referencing.datum.GeodeticDatum;
import org.opengis.referencing.datum.VerticalDatum;
import org.opengis.referencing.crs.GeocentricCRS;
import org.opengis.referencing.crs.GeographicCRS;
import org.opengis.referencing.crs.ProjectedCRS;
import org.opengis.referencing.crs.VerticalCRS;
import org.opengis.referencing.crs.CRSAuthorityFactory;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.cs.CSAuthorityFactory;
import org.opengis.referencing.cs.CoordinateSystem;
import org.opengis.referencing.cs.CoordinateSystemAxis;
import org.opengis.referencing.cs.EllipsoidalCS;
import org.opengis.referencing.cs.SphericalCS;
import org.opengis.referencing.cs.CartesianCS;
import org.opengis.metadata.citation.Citation;
import org.apache.sis.metadata.iso.citation.Citations;
import org.apache.sis.metadata.iso.citation.DefaultCitation;
import org.apache.sis.referencing.factory.GeodeticAuthorityFactory;
import org.apache.sis.internal.referencing.provider.TransverseMercator;
import org.apache.sis.internal.referencing.Resources;
import org.apache.sis.internal.system.DefaultFactories;
import org.apache.sis.internal.system.Fallback;
import org.apache.sis.internal.util.MetadataServices;
import org.apache.sis.internal.util.Constants;
import org.apache.sis.internal.util.URLs;
import org.apache.sis.setup.InstallationResources;
import org.apache.sis.util.resources.Vocabulary;
import org.apache.sis.util.CharSequences;
import org.apache.sis.util.Debug;
import org.apache.sis.measure.Latitude;
import org.apache.sis.measure.Units;


/**
 * The authority factory to use as a fallback when the real EPSG factory is not available.
 * We use this factory in order to guarantee that the minimal set of CRS codes documented
 * in the {@link CRS#forCode(String)} method javadoc is always available.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.2
 * @since   0.7
 * @module
 */
@Fallback
final class EPSGFactoryFallback extends GeodeticAuthorityFactory
        implements CRSAuthorityFactory, CSAuthorityFactory, DatumAuthorityFactory
{
    /**
     * Whether to disallow {@code CommonCRS} to use {@link org.apache.sis.referencing.factory.sql.EPSGFactory}
     * (in which case {@code CommonCRS} will fallback on hard-coded values).
     * This field should always be {@code false}, except for debugging purposes.
     */
    @Debug
    static final boolean FORCE_HARDCODED = false;

    /**
     * The singleton instance.
     */
    static final EPSGFactoryFallback INSTANCE = new EPSGFactoryFallback();

    /**
     * The authority to report in exceptions. Not necessarily the same than the {@link #authority} title.
     */
    private static final String AUTHORITY = Constants.EPSG + "-subset";

    /**
     * The authority, created when first needed.
     */
    private Citation authority;

    /**
     * URL  where users can get more information about the installation process.
     * Fetched when first needed.
     *
     * @see #getInstallationURL()
     * @see <a href="https://issues.apache.org/jira/browse/SIS-336">SIS-336</a>
     */
    private String installationURL;

    /**
     * Constructor for the singleton instance.
     */
    private EPSGFactoryFallback() {
    }

    /**
     * Returns the EPSG authority with only a modification in the title
     * for emphasing that this is a subset of EPSG dataset.
     */
    @Override
    public synchronized Citation getAuthority() {
        if (authority == null) {
            final DefaultCitation c = new DefaultCitation(Citations.EPSG);
            c.setTitle(Vocabulary.formatInternational(Vocabulary.Keys.SubsetOf_1, c.getTitle()));
            authority = c;
        }
        return authority;
    }

    /**
     * Returns the namespace of EPSG codes.
     *
     * @return the {@code "EPSG"} string in a singleton map.
     */
    @Override
    public Set<String> getCodeSpaces() {
        return Collections.singleton(Constants.EPSG);
    }

    /**
     * Returns the list of EPSG codes available.
     */
    @Override
    public Set<String> getAuthorityCodes(Class<? extends IdentifiedObject> type) {
        final boolean pm         = type.isAssignableFrom(PrimeMeridian.class);
        final boolean ellipsoid  = type.isAssignableFrom(Ellipsoid    .class);
        final boolean datum      = type.isAssignableFrom(GeodeticDatum.class);
        final boolean geographic = type.isAssignableFrom(GeographicCRS.class);
        final boolean geocentric = type.isAssignableFrom(GeocentricCRS.class);
        final boolean projected  = type.isAssignableFrom(ProjectedCRS .class);
        final Set<String> codes = new LinkedHashSet<>();
        if (pm) codes.add(StandardDefinitions.GREENWICH);
        for (final CommonCRS crs : CommonCRS.values()) {
            if (ellipsoid)  add(codes, crs.ellipsoid);
            if (datum)      add(codes, crs.datum);
            if (geocentric) add(codes, crs.geocentric);
            if (geographic) {
                add(codes, crs.geographic);
                add(codes, crs.geo3D);
            }
            if (projected) {
                add(codes, crs.northUPS);
                add(codes, crs.southUPS);
                if (crs.northUTM != 0 || crs.southUTM != 0) {
                    for (int zone = crs.firstZone; zone <= crs.lastZone; zone++) {
                        if (crs.northUTM != 0) codes.add(Integer.toString(crs.northUTM + zone));
                        if (crs.southUTM != 0) codes.add(Integer.toString(crs.southUTM + zone));
                    }
                }
            }
        }
        final boolean vertical = type.isAssignableFrom(VerticalCRS  .class);
        final boolean vdatum   = type.isAssignableFrom(VerticalDatum.class);
        if (vertical || vdatum) {
            for (final CommonCRS.Vertical candidate : CommonCRS.Vertical.values()) {
                if (candidate.isEPSG) {
                    if (vertical) add(codes, candidate.crs);
                    if (vdatum)   add(codes, candidate.datum);
                }
            }
        }
        if (type.isAssignableFrom(EllipsoidalCS.class)) {
            add(codes, StandardDefinitions.ELLIPSOIDAL_2D);
            add(codes, StandardDefinitions.ELLIPSOIDAL_3D);
        }
        if (type.isAssignableFrom(SphericalCS.class)) {
            add(codes, StandardDefinitions.SPHERICAL);
        }
        if (type.isAssignableFrom(CartesianCS.class)) {
            add(codes, StandardDefinitions.EARTH_CENTRED);
            add(codes, StandardDefinitions.CARTESIAN_2D);
            add(codes, StandardDefinitions.UPS_NORTH);
            add(codes, StandardDefinitions.UPS_SOUTH);
        }
        if (type.isAssignableFrom(Unit.class)) {
            add(codes, Constants.EPSG_METRE);
            add(codes, Constants.EPSG_AXIS_DEGREES);
        }
        return codes;
    }

    /**
     * Adds the given value to the given set, provided that the value is different than zero.
     * Zero is used as a sentinel value in {@link CommonCRS} meaning "no EPSG code".
     */
    private static void add(final Set<String> codes, final short value) {
        if (value != 0) codes.add(Short.toString(value));
    }

    /**
     * Kinds of object created by this factory, as bitmask. Note that objects
     * created for {@link #CS} and {@link #AXIS} kinds are currently not cached.
     */
    private static final int CRS=0x1, DATUM=0x2, ELLIPSOID=0x4, PRIME_MERIDIAN=0x8, UNIT=0x10, AXIS=0x20, CS=0x40;

    /**
     * Returns a prime meridian for the given EPSG code.
     */
    @Override
    public PrimeMeridian createPrimeMeridian(final String code) throws NoSuchAuthorityCodeException {
        return (PrimeMeridian) predefined(code, PRIME_MERIDIAN);
    }

    /**
     * Returns an ellipsoid for the given EPSG code.
     */
    @Override
    public Ellipsoid createEllipsoid(final String code) throws NoSuchAuthorityCodeException {
        return (Ellipsoid) predefined(code, ELLIPSOID);
    }

    /**
     * Returns a datum for the given EPSG code.
     */
    @Override
    public Datum createDatum(final String code) throws NoSuchAuthorityCodeException {
        return (Datum) predefined(code, DATUM);
    }

    /**
     * Returns a coordinate reference system for the given EPSG code. This method is invoked
     * as a fallback when {@link CRS#forCode(String)} can not create a CRS for a given code.
     */
    @Override
    public CoordinateReferenceSystem createCoordinateReferenceSystem(final String code) throws NoSuchAuthorityCodeException {
        return (CoordinateReferenceSystem) predefined(code, CRS);
    }

    /**
     * Returns a coordinate system for the given EPSG code. Contrarily to other kinds of objects,
     * coordinate systems are not cached because we can not use {@link CommonCRS} as a store for
     * them (because all enumerated values use the same coordinate systems). The lack of caching
     * should not be an issue since standalone CS objects (without CRS) are rarely be needed.
     */
    @Override
    public CoordinateSystem createCoordinateSystem(final String code) throws NoSuchAuthorityCodeException {
        return (CoordinateSystem) predefined(code, CS);
    }

    /**
     * Returns a coordinate system axis for the given EPSG code. Axes are not cached for the same
     * reasons than {@link #createCoordinateSystem(String)}.
     */
    @Override
    public CoordinateSystemAxis createCoordinateSystemAxis(final String code) throws NoSuchAuthorityCodeException {
        return (CoordinateSystemAxis) predefined(code, AXIS);
    }

    /**
     * Returns a unit of measurement for the given code.
     */
    @Override
    public Unit<?> createUnit(final String code) throws NoSuchAuthorityCodeException {
        return (Unit) predefined(code, UNIT);
    }

    /**
     * Returns a coordinate reference system, datum or ellipsoid for the given EPSG code.
     */
    @Override
    public IdentifiedObject createObject(final String code) throws NoSuchAuthorityCodeException {
        return (IdentifiedObject) predefined(code, -1 & ~UNIT);
    }

    /**
     * Implementation of all {@code createFoo(String)} methods in this fallback class.
     *
     * @param  code  the EPSG code.
     * @param  kind  any combination of {@code *_MASK} bits.
     * @return the requested object.
     * @throws NoSuchAuthorityCodeException if no matching object has been found.
     */
    private Object predefined(String code, final int kind) throws NoSuchAuthorityCodeException {
        try {
            /*
             * Parse the value after the last ':'. We do not bother to verify if the part before ':' is legal
             * (e.g. "EPSG:4326", "EPSG::4326", "urn:ogc:def:crs:epsg::4326", etc.) because this analysis has
             * already be done by MultiAuthoritiesFactory. We nevertheless skip the prefix in case this factory
             * is used directly (not through MultiAuthoritiesFactory), which should be rare. The main case is
             * when using the factory returned by AuthorityFactories.fallback(…).
             */
            code = CharSequences.trimWhitespaces(code, code.lastIndexOf(Constants.DEFAULT_SEPARATOR) + 1, code.length()).toString();
            final short n = Short.parseShort(code);
            if ((kind & (ELLIPSOID | DATUM | CRS)) != 0) {
                for (final CommonCRS crs : CommonCRS.values()) {
                    /*
                     * In a complete EPSG dataset we could have an ambiguity below because the same code can be used
                     * for datum, ellipsoid and CRS objects. However in the particular case of this EPSG-subset, we
                     * ensured that there is no such collision - see CommonCRSTest.ensureNoCodeCollision().
                     */
                    if ((kind & ELLIPSOID) != 0  &&  n == crs.ellipsoid) return crs.ellipsoid();
                    if ((kind & DATUM)     != 0  &&  n == crs.datum)     return crs.datum();
                    if ((kind & CRS) != 0) {
                        if (n == crs.geographic) return crs.geographic();
                        if (n == crs.geocentric) return crs.geocentric();
                        if (n == crs.geo3D)      return crs.geographic3D();
                        final double latitude;
                        int zone;
                        if (crs.northUTM != 0 && (zone = n - crs.northUTM) >= crs.firstZone && zone <= crs.lastZone) {
                            latitude = +1;          // Any north latitude below 56°N (because of Norway exception) is okay
                        } else if (crs.southUTM != 0 && (zone = n - crs.southUTM) >= crs.firstZone && zone <= crs.lastZone) {
                            latitude = -1;          // Any south latitude above 80°S (because of UPS south case) is okay.
                        } else if (n == crs.northUPS) {
                            latitude = Latitude.MAX_VALUE;
                            zone     = 30;                  // Any random UTM zone is okay.
                        } else if (n == crs.southUPS) {
                            latitude = Latitude.MIN_VALUE;
                            zone     = 30;                  // Any random UTM zone is okay.
                        } else {
                            continue;
                        }
                        return crs.universal(latitude, TransverseMercator.Zoner.UTM.centralMeridian(zone));
                    }
                }
                if ((kind & (DATUM | CRS)) != 0) {
                    for (final CommonCRS.Vertical candidate : CommonCRS.Vertical.values()) {
                        if (candidate.isEPSG) {
                            if ((kind & DATUM) != 0  &&  candidate.datum == n) return candidate.datum();
                            if ((kind & CRS)   != 0  &&  candidate.crs   == n) return candidate.crs();
                        }
                    }
                }
            }
            /*
             * Other kinds of objects (prime meridian, units of measurement, etc). We check those candidates only after
             * above loop (CRS, datum, etc.) in order to give precedence to CRS if the same code is used for both kinds
             * of objects. We do not bother to cache coordinate system and axis instances.
             */
            if ((kind & PRIME_MERIDIAN) != 0  &&  n == Constants.EPSG_GREENWICH) {
                return CommonCRS.WGS84.primeMeridian();
            }
            if ((kind & CS) != 0) {
                final CoordinateSystem cs = StandardDefinitions.createCoordinateSystem(n, false);
                if (cs != null) return cs;
            }
            if ((kind & AXIS) != 0) {
                final CoordinateSystemAxis axis = StandardDefinitions.createAxis(n, false);
                if (axis != null) return axis;
            }
            if ((kind & UNIT) != 0) {
                final Unit<?> unit = Units.valueOfEPSG(n);
                if (unit != null) return unit;
            }
        } catch (NumberFormatException cause) {
            final NoSuchAuthorityCodeException e = new NoSuchAuthorityCodeException(Resources.format(
                    Resources.Keys.NoSuchAuthorityCode_3, Constants.EPSG, toClass(kind), code), AUTHORITY, code);
            e.initCause(cause);
            throw e;
        }
        throw new NoSuchAuthorityCodeException(Resources.format(Resources.Keys.NoSuchAuthorityCodeInSubset_4,
                Constants.EPSG, toClass(kind), code, getInstallationURL()), AUTHORITY, code);
    }

    /**
     * Returns a URL where users can get more information about the installation process.
     */
    private synchronized String getInstallationURL() {
        if (installationURL == null) {
            installationURL = URLs.EPSG_INSTALL;            // To be used as fallback.
            final Iterable<InstallationResources> services =
                    DefaultFactories.createServiceLoader(InstallationResources.class);
            /*
             * Following loop will be executed one or two times. First, we check for resources that are
             * specifically for EPSG geodetic dataset. If none are found, fallback on embedded database.
             */
            boolean embedded = false;
            do {
                final String authority = embedded ? MetadataServices.EMBEDDED : Constants.EPSG;
                for (InstallationResources res : services) {
                    if (res.getAuthorities().contains(authority)) {
                        final String url = res.getInstructionURL();
                        if (url != null) {
                            installationURL = url;
                            return url;
                        }
                    }
                }
            } while ((embedded = !embedded) == true);
        }
        return installationURL;
    }

    /**
     * Returns the interface for the given {@code *_MASK} constant.
     * This is used for formatting error message only.
     */
    private static Class<?> toClass(final int kind) {
        switch (kind) {
            case CRS:            return CoordinateReferenceSystem.class;
            case DATUM:          return Datum.class;
            case ELLIPSOID:      return Ellipsoid.class;
            case PRIME_MERIDIAN: return PrimeMeridian.class;
            case UNIT:           return Unit.class;
            case AXIS:           return CoordinateSystemAxis.class;
            case CS:             return CoordinateSystem.class;
            default:             return IdentifiedObject.class;
        }
    }
}
