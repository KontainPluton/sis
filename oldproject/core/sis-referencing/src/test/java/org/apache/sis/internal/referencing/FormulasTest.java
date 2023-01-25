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

import java.util.Collections;
import org.opengis.referencing.datum.Ellipsoid;
import org.apache.sis.internal.metadata.ReferencingServices;
import org.apache.sis.measure.Longitude;
import org.apache.sis.measure.Units;
import org.apache.sis.referencing.datum.DefaultEllipsoid;
import org.apache.sis.referencing.datum.HardCodedDatum;
import org.apache.sis.test.TestCase;
import org.junit.Test;

import static org.junit.Assert.*;


/**
 * Tests {@link Formulas}.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.3
 * @since   0.4
 * @module
 */
public final strictfp class FormulasTest extends TestCase {
    /**
     * Verifies the {@link Formulas#LONGITUDE_MAX} constant.
     */
    @Test
    public void verifyLongitudeMax() {
        assertTrue(Formulas.LONGITUDE_MAX > Longitude.MAX_VALUE);
        assertTrue(StrictMath.ulp(Formulas.LONGITUDE_MAX) <= Formulas.ANGULAR_TOLERANCE);
    }

    /**
     * Verifies the {@link Formulas#JULIAN_YEAR_LENGTH} constant.
     */
    @Test
    public void verifyJulianYearLength() {
        assertEquals(StrictMath.round(365.25 * 24 * 60 * 60 * 1000), Formulas.JULIAN_YEAR_LENGTH);
    }

    /**
     * Tests {@link Formulas#isPoleToPole(double, double)}.
     */
    @Test
    public void testIsPoleToPole() {
        assertTrue (Formulas.isPoleToPole(-90, 90));
        assertFalse(Formulas.isPoleToPole(-89, 90));
        assertFalse(Formulas.isPoleToPole(-90, 89));
    }

    /**
     * Tests {@link Formulas#getAuthalicRadius(double, double)} using the parameters of <cite>GRS 1980</cite>
     * ellipsoid (EPSG:7019).
     *
     * <ul>
     *   <li>Semi-major axis length: 6378137 metres</li>
     *   <li>Inverse flattening: 298.257222101</li>
     * </ul>
     *
     * Expected result is the radius of <cite>GRS 1980 Authalic Sphere</cite> (EPSG:7048),
     * which is 6371007 metres.
     */
    @Test
    public void testGetAuthalicRadius() {
        assertEquals(ReferencingServices.AUTHALIC_RADIUS, Formulas.getAuthalicRadius(6378137, 6356752), 0.5);
    }

    /**
     * Tests {@link Formulas#scaleComparedToEarth(Ellipsoid)}.
     */
    @Test
    public void testScaleComparedToEarth() {
        assertEquals(1, Formulas.scaleComparedToEarth(HardCodedDatum.WGS84.getEllipsoid()), 1E-14);
    }

    /**
     * Tests {@link Formulas#getSemiMinor(double, double)}.
     */
    @Test
    public void testGetSemiMinor() {
        assertEquals("WGS 84",             6356752.314245179,  Formulas.getSemiMinor(6378137, 298.257223563), 1E-9);
        assertEquals("International 1924", 6356911.9461279465, Formulas.getSemiMinor(6378388, 297), 1E-9);
        assertEquals("Clarke 1858",        20855233, // Unit in feet. Is the definitive parameter for this ellipsoid.
                Formulas.getSemiMinor(20926348, 294.26067636926103), 1E-8);
    }

    /**
     * Tests {@link Formulas#getInverseFlattening(double, double)}.
     */
    @Test
    public void testGetInverseFlattening() {
        assertEquals("WGS 84", 298.2572235629972, Formulas.getInverseFlattening(6378137, 6356752.314245179), 1E-11);
        assertEquals("International 1924", 297, Formulas.getInverseFlattening(6378388, 6356911.9461279465), 1E-11);
        assertEquals("Clarke 1858", 294.26067636926103, Formulas.getInverseFlattening(20926348, 20855233), 1E-11);
    }

    /**
     * Tests {@link Formulas#radiusOfConformalSphere(Ellipsoid, double)}.
     * This test computes the Radius of Conformal Sphere using the values given by the
     * IOGP Report 373-07-02 – <cite>Coordinate conversions and transformation including formulas</cite>
     * for the <cite>Amersfoort / RD New</cite> projection (a Stereographic one).
     */
    @Test
    public void testRadiusOfConformalSphere() {
        final DefaultEllipsoid ellipsoid = DefaultEllipsoid.createFlattenedSphere(
                Collections.singletonMap(DefaultEllipsoid.NAME_KEY, "Bessel 1841"), 6377397.155, 299.1528128, Units.METRE);
        assertEquals(6382644.571, Formulas.radiusOfConformalSphere(ellipsoid, StrictMath.toRadians(52.156160556)), 0.001);
    }

    /**
     * Tests {@link Formulas#getRadius(Ellipsoid, double)}.
     */
    @Test
    public void testGetRadius() {
        final Ellipsoid e = HardCodedDatum.WGS84.getEllipsoid();
        assertEquals(e.getSemiMajorAxis(), Formulas.getRadius(e,  0),         0.01);
        assertEquals(e.getSemiMinorAxis(), Formulas.getRadius(e, +Math.PI/2), 0.01);
        assertEquals(e.getSemiMinorAxis(), Formulas.getRadius(e, -Math.PI/2), 0.01);
        assertEquals(6372824, Formulas.getRadius(e, Math.toRadians(30)), 0.5);
    }
}
