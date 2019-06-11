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

import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.PathIterator;
import java.util.Arrays;
import java.util.Random;
import java.io.IOException;
import java.io.LineNumberReader;
import org.opengis.geometry.DirectPosition;
import org.opengis.referencing.cs.AxisDirection;
import org.opengis.referencing.operation.TransformException;
import org.apache.sis.referencing.operation.GeodesicException;
import org.apache.sis.internal.referencing.j2d.ShapeUtilitiesExt;
import org.apache.sis.internal.referencing.Formulas;
import org.apache.sis.referencing.crs.HardCodedCRS;
import org.apache.sis.geometry.DirectPosition2D;
import org.apache.sis.util.CharSequences;
import org.apache.sis.math.StatisticsFormat;
import org.apache.sis.math.Statistics;
import org.apache.sis.measure.Units;
import org.apache.sis.test.widget.VisualCheck;
import org.apache.sis.test.OptionalTestData;
import org.apache.sis.test.DependsOnMethod;
import org.apache.sis.test.TestUtilities;
import org.apache.sis.test.TestCase;
import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.GeodesicData;
import org.junit.Test;

import static java.lang.StrictMath.*;
import static org.opengis.test.Assert.*;


/**
 * Tests {@link GeodeticCalculator}. Test values come from the following sources:
 *
 * <ul>
 *   <li><a href="https://en.wikipedia.org/wiki/Great-circle_navigation#Example">Great-circle navigation on Wikipedia.</a></li>
 *   <li><a href="http://doi.org/10.5281/zenodo.32156">Karney, C. F. F. (2010). Test set for geodesics [Data set]. Zenodo.</a></li>
 *   <li>Charles Karney's <a href="https://geographiclib.sourceforge.io/">GeographicLib</a> implementation.</li>
 * </ul>
 *
 * This base class tests calculator using spherical formulas.
 * Subclass executes the same test but using ellipsoidal formulas.
 *
 * @version 1.0
 * @since   1.0
 * @module
 */
public strictfp class GeodeticCalculatorTest extends TestCase {
    /**
     * Creates a new test case.
     */
    public GeodeticCalculatorTest() {
    }

    /**
     * Verifies that the given point is equals to the given latitude and longitude.
     *
     * @param φ  the expected latitude value, in degrees.
     * @param λ  the expected longitude value, in degrees.
     * @param p  the actual position to verify.
     * @param ε  the tolerance threshold.
     */
    static void assertPositionEquals(final double φ, final double λ, final DirectPosition p, final double ε) {
        assertEquals("φ", φ, p.getOrdinate(0), ε);
        assertEquals("λ", λ, p.getOrdinate(1), ε);
    }

    /**
     * Asserts that a Java2D point is equal to the expected value. Used for verifying geodesic paths.
     *
     * @param x  the expected <var>x</var> coordinates.
     * @param y  the expected <var>y</var> coordinates.
     * @param p  the actual position to verify.
     * @param ε  the tolerance threshold.
     */
    private static void assertPointEquals(final double x, final double y, final Point2D p, final double ε) {
        assertEquals("x", x, p.getX(), ε);
        assertEquals("y", y, p.getY(), ε);
    }

    /**
     * Returns the calculator to use for testing purpose. Default implementation uses a calculator
     * for a sphere. Subclasses should override for creating instances of the class to be tested.
     *
     * @param  normalized  whether to force (longitude, latitude) axis order.
     */
    GeodeticCalculator create(final boolean normalized) {
        final GeodeticCalculator c = GeodeticCalculator.create(normalized ? HardCodedCRS.SPHERE : HardCodedCRS.SPHERE_φλ);
        assertEquals(GeodeticCalculator.class, c.getClass());       // Expect the implementation with spherical formulas.
        return c;
    }

    /**
     * Returns a reference implementation for the given geodetic calculator.
     */
    private static Geodesic createReferenceImplementation(final GeodeticCalculator c) {
        return new Geodesic(c.ellipsoid.getSemiMajorAxis(), 1/c.ellipsoid.getInverseFlattening());
    }

    /**
     * Tests some simple azimuth directions. The expected directions are approximately North, East,
     * South and West, but not exactly because of Earth curvature. The test verify merely that the
     * azimuths are approximately correct.
     *
     * @throws GeodesicException if a geodesic can not be computed.
     */
    @Test
    public void testCardinalAzimuths() throws GeodesicException {
        final GeodeticCalculator c = create(false);
        final double tolerance = 0.2;
        c.setStartGeographicPoint(20, 12);
        c.setEndGeographicPoint(20, 13);  assertEquals("East",   90, c.getStartingAzimuth(), tolerance);
        c.setEndGeographicPoint(21, 12);  assertEquals("North",   0, c.getStartingAzimuth(), tolerance);
        c.setEndGeographicPoint(20, 11);  assertEquals("West",  -90, c.getStartingAzimuth(), tolerance);
        c.setEndGeographicPoint(19, 12);  assertEquals("South", 180, c.getStartingAzimuth(), tolerance);
    }

    /**
     * Tests azimuths at poles.
     *
     * @throws GeodesicException if a geodesic can not be computed.
     */
    @Test
    @DependsOnMethod("testCardinalAzimuths")
    public void testAzimuthAtPoles() throws GeodesicException {
        final GeodeticCalculator c = create(false);
        final double tolerance = 0.2;
        c.setStartGeographicPoint( 90,  30);
        c.setEndGeographicPoint  ( 20,  20);  assertEquals(-170, c.getStartingAzimuth(), tolerance);
        c.setEndGeographicPoint  ( 20,  40);  assertEquals( 170, c.getStartingAzimuth(), tolerance);
        c.setEndGeographicPoint  ( 20,  30);  assertEquals( 180, c.getStartingAzimuth(), tolerance);
        c.setEndGeographicPoint  (-20,  30);  assertEquals( 180, c.getStartingAzimuth(), tolerance);
        c.setEndGeographicPoint  (-90,  30);  assertEquals( 180, c.getStartingAzimuth(), tolerance);

        c.setStartGeographicPoint( 90,   0);
        c.setEndGeographicPoint  ( 20,  20);  assertEquals( 160, c.getStartingAzimuth(), tolerance);
        c.setEndGeographicPoint  ( 20, -20);  assertEquals(-160, c.getStartingAzimuth(), tolerance);
        c.setEndGeographicPoint  ( 20,   0);  assertEquals( 180, c.getStartingAzimuth(), tolerance);
        c.setEndGeographicPoint  (-90,   0);  assertEquals( 180, c.getStartingAzimuth(), tolerance);
    }

    /**
     * Tests geodesic distances and rhumb line length on the equator.
     *
     * @throws GeodesicException if a geodesic can not be computed.
     */
    @Test
    public void testDistanceAtEquator() throws GeodesicException {
        final Random random = TestUtilities.createRandomNumberGenerator(86909845084528L);
        final GeodeticCalculator c = create(false);
        final double a = c.ellipsoid.getSemiMajorAxis();
        final double b = c.ellipsoid.getSemiMinorAxis();
        final double r = a * (PI / 180);
        final double λmax = nextDown((b/a) * 180);
        c.setStartGeographicPoint(0, 0);
        for (int i=0; i<100; i++) {
            final double x = (2*λmax) * random.nextDouble() - λmax;
            c.setEndGeographicPoint(0, x);
            final double expected = abs(x) * r;
            assertEquals("Geodesic",   expected, c.getGeodesicDistance(), Formulas.LINEAR_TOLERANCE);
            assertEquals("Rhumb line", expected, c.getRhumblineLength(),  Formulas.LINEAR_TOLERANCE);
        }
    }

    /**
     * Tests {@link GeodeticCalculator#getGeodesicDistance()} and azimuths with the example given in Wikipedia.
     * This computes the great circle route from Valparaíso (33°N 71.6W) to Shanghai (31.4°N 121.8°E).
     *
     * @throws TransformException if an error occurred while transforming coordinates.
     *
     * @see <a href="https://en.wikipedia.org/wiki/Great-circle_navigation#Example">Great-circle navigation on Wikipedia</a>
     */
    @Test
    @DependsOnMethod({"testCardinalAzimuths", "testDistanceAtEquator"})
    public void testGeodesicDistanceAndAzimuths() throws TransformException {
        final GeodeticCalculator c = create(false);
        c.setStartGeographicPoint(-33.0, -71.6);            // Valparaíso
        c.setEndGeographicPoint  ( 31.4, 121.8);            // Shanghai
        /*
         * Wikipedia example gives:
         *
         *     Δλ = −166.6°
         *     α₁ = −94.41°
         *     α₂ = −78.42°
         *     Δσ = 168.56°   →    taking R = 6371 km, the distance is 18743 km.
         */
        assertEquals(Units.METRE,         c.getDistanceUnit());
        assertEquals("α₁",        -94.41, c.getStartingAzimuth(), 0.005);
        assertEquals("α₂",        -78.42, c.getEndingAzimuth(),   0.005);
        assertEquals("distance",   18743, c.getGeodesicDistance() / 1000, 0.5);
        assertPositionEquals(31.4, 121.8, c.getEndPoint(), 1E-12);                  // Should be the specified value.
        /*
         * Keep start point unchanged, but set above azimuth and distance.
         * Verify that we get the Shanghai coordinates.
         */
        c.setStartingAzimuth(-94.41);
        c.setGeodesicDistance(18743000);
        assertEquals("α₁",        -94.41, c.getStartingAzimuth(), 1E-12);           // Should be the specified value.
        assertEquals("α₂",        -78.42, c.getEndingAzimuth(),   0.01);
        assertEquals("distance",   18743, c.getGeodesicDistance() / 1000, STRICT);  // Should be the specified value.
        assertPositionEquals(31.4, 121.8, c.getEndPoint(), 0.01);
    }

    /**
     * Tests geodetic calculator involving a coordinate operation.
     * This test uses a simple CRS with only the axis order interchanged.
     * The coordinates are the same than {@link #testGeodesicDistanceAndAzimuths()}.
     *
     * @throws TransformException if an error occurred while transforming coordinates.
     */
    @Test
    @DependsOnMethod("testGeodesicDistanceAndAzimuths")
    public void testUsingTransform() throws TransformException {
        final GeodeticCalculator c = create(true);
        assertAxisDirectionsEqual("GeographicCRS", c.getGeographicCRS().getCoordinateSystem(), AxisDirection.NORTH, AxisDirection.EAST);
        assertAxisDirectionsEqual("PositionCRS",     c.getPositionCRS().getCoordinateSystem(), AxisDirection.EAST, AxisDirection.NORTH);
        final double φ = -33.0;
        final double λ = -71.6;
        c.setStartPoint(new DirectPosition2D(λ, φ));
        assertPositionEquals(λ, φ, c.getStartPoint(), Formulas.ANGULAR_TOLERANCE);
        /*
         * Test the "Valparaíso to Shanghai" geodesic given by Wikipedia, but with a larger tolerance threshold for allowing
         * the test to pass with both spherical and ellipsoidal formula. It is not the purpose of this test to check accuracy;
         * that check is done by `testGeodesicDistanceAndAzimuths()`.
         */
        c.setStartingAzimuth(-94.41);
        c.setGeodesicDistance(18743000);
        assertPositionEquals(121.8, 31.4, c.getEndPoint(), 0.2);
    }

    /**
     * Tests {@link GeodeticCalculator#moveToEndPoint()}.
     *
     * @throws TransformException if an error occurred while transforming coordinates.
     */
    @Test
    public void testMoveToEndPoint() throws TransformException {
        // Following relationship is required by GeodeticCalculator.moveToEndPoint() implementation.
        assertEquals(GeodeticCalculator.ENDING_AZIMUTH >>> 1, GeodeticCalculator.STARTING_AZIMUTH);
        final GeodeticCalculator c = create(false);
        c.setStartGeographicPoint(-33.0, -71.6);            // Valparaíso
        c.setEndGeographicPoint  ( 31.4, 121.8);            // Shanghai
        c.moveToEndPoint();
        assertPositionEquals(31.4, 121.8, c.getStartPoint(), 1E-12);
    }

    /**
     * Tests {@link GeodeticCalculator#createCircularRegion2D(double)}.
     *
     * @throws TransformException if an error occurred while transforming coordinates.
     */
    @Test
    @DependsOnMethod("testUsingTransform")
    public void testCircularRegion2D() throws TransformException {
        final GeodeticCalculator c = create(true);
        c.setStartGeographicPoint(-33.0, -71.6);                // Valparaíso
        c.setGeodesicDistance(100000);                          // 100 km
        Shape region = c.createCircularRegion2D(10000);
        if (VisualCheck.SHOW_WIDGET) {
            VisualCheck.show(region);
        }
        final Rectangle2D bounds = region.getBounds2D();
        assertEquals("x",    -71.6, bounds.getCenterX(), 1E-3);
        assertEquals("y",    -33.0, bounds.getCenterY(), 1E-3);
        assertEquals("width",  2.1, bounds.getWidth(),   0.1);
        assertEquals("height", 1.8, bounds.getHeight(),  0.1);
    }

    /**
     * Tests {@link GeodeticCalculator#createGeodesicPath2D(double)}. This method uses a CRS that swap axis order
     * as a way to verify that user-specified CRS is taken in account. The start point and end point are the same
     * than in {@link #testGeodesicDistanceAndAzimuths()}. Note that this path crosses the anti-meridian,
     * so the end point needs to be shifted by 360°.
     *
     * @throws TransformException if an error occurred while transforming coordinates.
     */
    @Test
    @DependsOnMethod("testUsingTransform")
    public void testGeodesicPath2D() throws TransformException {
        final GeodeticCalculator c = create(true);
        c.setStartGeographicPoint(-33.0, -71.6);                                        // Valparaíso
        c.setEndGeographicPoint  ( 31.4, 121.8);                                        // Shanghai
        final Shape singleCurve = c.createGeodesicPath2D(Double.POSITIVE_INFINITY);
        final Shape multiCurves = c.createGeodesicPath2D(10000);                        // 10 km tolerance.
        /*
         * The approximation done by a single curve is not very good, but is easier to test.
         * The coordinate at t=0.5 has a larger tolerance threshold because it varies slightly
         * depending on whether we are testing with spherical or ellipsoidal formulas.
         */
        assertPointEquals( -71.6, -33.0, ShapeUtilitiesExt.pointOnBezier(singleCurve, 0),   0.05);
        assertPointEquals(-238.2,  31.4, ShapeUtilitiesExt.pointOnBezier(singleCurve, 1),   0.05);      // λ₂ = 121.8° - 360°
        assertPointEquals(-159.2,  -6.9, ShapeUtilitiesExt.pointOnBezier(singleCurve, 0.5), 0.25);
        /*
         * The more accurate curve can not be simplified to a Java2D primitive.
         */
        assertInstanceOf("Multicurves", Path2D.class, multiCurves);
        if (VisualCheck.SHOW_WIDGET) {
            VisualCheck.show(singleCurve, multiCurves);
        }
    }

    /**
     * Verifies that all <var>y</var> coordinates are zero for a geodesic path on equator.
     *
     * @throws TransformException if an error occurred while transforming coordinates.
     */
    @Test
    public void testGeodesicPathOnEquator() throws TransformException {
        final GeodeticCalculator c = create(false);
        final double tolerance = 1E-12;
        c.setStartGeographicPoint(0, 20);
        c.setEndGeographicPoint  (0, 12);
        assertEquals(-90, c.getStartingAzimuth(), tolerance);
        assertEquals(-90, c.getEndingAzimuth(),   tolerance);
        final Shape geodeticCurve = c.createGeodesicPath2D(1);
        final double[] coords = new double[2];
        for (final PathIterator it = geodeticCurve.getPathIterator(null, 1); !it.isDone(); it.next()) {
            it.currentSegment(coords);
            assertEquals ("φ",  0, coords[0], tolerance);
            assertBetween("λ", 12, 20, coords[1]);
        }
    }

    /**
     * Tests geodesic path between random points. The coordinates are compared with values computed by
     * <a href="https://geographiclib.sourceforge.io/">GeographicLib</a>, taken as reference implementation.
     *
     * @throws TransformException if an error occurred while transforming coordinates.
     */
    @Test
    public void testBetweenRandomPoints() throws TransformException {
        final Random random = TestUtilities.createRandomNumberGenerator();
        final GeodeticCalculator c = create(false);
        final Geodesic reference = createReferenceImplementation(c);
        Statistics[] errors = null;
        for (int i=0; i<100; i++) {
            final double φ1 = random.nextDouble() * 180 -  90;
            final double λ1 = random.nextDouble() * 360 - 180;
            final double φ2 = random.nextDouble() * 180 -  90;
            final double Δλ = random.nextDouble() * 360 - 180;
            final double λ2 = IEEEremainder(λ1 + Δλ, 360);
            c.setStartGeographicPoint(φ1, λ1);
            c.setEndGeographicPoint  (φ2, λ2);
            final double geodesic  = c.getGeodesicDistance();
            final double rhumbLine = c.getRhumblineLength();
            final GeodesicData expected = reference.Inverse(φ1, λ1, φ2, λ2);
            assertEquals("Geodesic distance", expected.s12,  geodesic,               Formulas.LINEAR_TOLERANCE);
            assertEquals("Starting azimuth",  expected.azi1, c.getStartingAzimuth(), Formulas.ANGULAR_TOLERANCE);
            assertEquals("Ending azimuth",    expected.azi2, c.getEndingAzimuth(),   Formulas.ANGULAR_TOLERANCE);
            assertTrue  ("Rhumb ≧ geodesic",  rhumbLine >= geodesic);
            if (VERBOSE) {
                // Checks the geodesic path on only 10% of test data, because this computation is expensive.
                if ((i % 10) == 0) {
                    final Statistics[] stats = geodesicPathFitness(c, 1000);
                    if (errors == null) {
                        errors = stats;
                    } else for (int j=0; j<errors.length; j++) {
                        errors[j].combine(stats[j]);
                    }
                }
            }
        }
        if (errors != null) {
            out.println("Distance between points on Bézier curve and points on geodesic.");
            out.println("∆x/r and ∆y/r should be smaller than 1:");
            out.println(StatisticsFormat.getInstance().format(errors));
        }
    }

    /**
     * Estimates the differences between the points on the Bézier curves and the points computed by geodetic calculator.
     * This method approximates the Bézier curve by line segments. Then for each point of the approximated Bézier curve,
     * this method computes the location of a close point on the geodesic (more specifically a point at the same geodesic
     * distance from the start point). The distance in metres between the two points is measured and accumulated as a
     * fraction of the expected resolution <var>r</var>. Consequently the values in ∆x/r and ∆y/r columns should be less
     * than 1.
     *
     * <div class="note"><b>Note:</b> the state of the given calculator is modified by this method.</div>
     *
     * @param  resolution  tolerance threshold for the curve approximation, in metres.
     * @return statistics about errors relative to the resolution.
     */
    private static Statistics[] geodesicPathFitness(final GeodeticCalculator c, final double resolution) throws TransformException {
        final PathIterator iterator = c.createGeodesicPath2D(resolution).getPathIterator(null, Formulas.ANGULAR_TOLERANCE);
        final Statistics   xError   = new Statistics("∆x/r");
        final Statistics   yError   = new Statistics("∆y/r");
        final Statistics   aErrors  = new Statistics("∆α (°)");
        final double       azimuth  = c.getStartingAzimuth();
        final double       toMetres = (PI/180) * c.authalicRadius;
        final double[]     buffer   = new double[2];
        while (!iterator.isDone()) {
            switch (iterator.currentSegment(buffer)) {
                default: fail("Unexpected segment"); break;
                case PathIterator.SEG_MOVETO: break;
                case PathIterator.SEG_LINETO: {
                    c.setEndGeographicPoint(buffer[0], buffer[1]);
                    aErrors.accept(abs(c.getStartingAzimuth() - azimuth));
                    c.setStartingAzimuth(azimuth);
                    DirectPosition endPoint = c.getEndPoint();
                    final double φ = endPoint.getOrdinate(0);
                    final double λ = endPoint.getOrdinate(1);
                    double dy =              (buffer[0] - φ)      * toMetres;
                    double dx = IEEEremainder(buffer[1] - λ, 360) * toMetres * cos(toRadians(φ));
                    yError.accept(abs(dy) / resolution);
                    xError.accept(abs(dx) / resolution);
                }
            }
            iterator.next();
        }
        return new Statistics[] {xError, yError, aErrors};
    }

    /**
     * Column index for stating/ending latitude/longitude and geodesic distance.
     * Used by {@link #compareAgainstDataset()} only.
     */
    static final int COLUMN_φ1 = 0, COLUMN_λ1 = 1, COLUMN_α1 = 2,
                     COLUMN_φ2 = 3, COLUMN_λ2 = 4, COLUMN_α2 = 5,
                     COLUMN_Δs = 6;

    /**
     * Compares computations against values provided in <cite>Karney (2010) Test set for geodesics</cite>.
     * This is an optional test executed only if the {@code $SIS_DATA/Tests/GeodTest.dat} file is found.
     *
     * @throws IOException if an error occurred while reading the test file.
     * @throws TransformException if an error occurred while transforming coordinates.
     */
    @Test
    public void compareAgainstDataset() throws IOException, TransformException {
        int noConvergenceCount = 0;
        try (LineNumberReader reader = OptionalTestData.GEODESIC.reader()) {
            final Random random = TestUtilities.createRandomNumberGenerator();
            final GeodeticCalculator c = create(false);
            final boolean isSphere = c.ellipsoid.isSphere();
            final double[] expected = new double[7];
            String line;
            while ((line = reader.readLine()) != null) {
                Arrays.fill(expected, Double.NaN);
                final CharSequence[] split = CharSequences.split(line, ' ');
                for (int i=min(split.length, expected.length); --i >= 0;) {
                    expected[i] = Double.parseDouble(split[i].toString());
                }
                /*
                 * Choose randomly whether we will test the direct or inverse geodesic problem.
                 * We execute only one test for each row instead than executing both tests,
                 * for making sure that `GeodeticCalculator` never see the expected values.
                 */
                final boolean isTestingInverse = random.nextBoolean();
                final double cosφ1 = abs(cos(toRadians(expected[COLUMN_φ1])));          // For adjusting longitude tolerance.
                final double cosφ2 = abs(cos(toRadians(expected[COLUMN_φ2])));
                double linearTolerance, latitudeTolerance, longitudeTolerance, azimuthTolerance;
                if (isSphere) {
                    /*
                     * When spherical formulas are used instead than ellipsoidal formulas, an error up to 1% is expected
                     * in distance calculations (source: Wikipedia). A yet larger error is observed for azimuth values,
                     * especially near poles and between antipodal points. Following are empirical thresholds.
                     */
                    linearTolerance    = expected[COLUMN_Δs] * 0.01;
                    latitudeTolerance  = toDegrees(linearTolerance / c.authalicRadius);
                    longitudeTolerance = expected[COLUMN_φ2] > 89.5 ? 180 : latitudeTolerance / cosφ2;
                    azimuthTolerance   = 0.5;                                   // About 8.8 metres at distance of 1 km.
                    if (isTestingInverse) {
                        final double Δλ = abs(expected[COLUMN_λ2] - expected[COLUMN_λ1]);
                             if (Δλ > 179) azimuthTolerance = 100;
                        else if (Δλ > 178) azimuthTolerance = 20;
                        else if (Δλ > 175) azimuthTolerance = 10;
                        else if (Δλ > 168) azimuthTolerance = 3;
                        else if (Δλ > 158) azimuthTolerance = 1;
                    }
                } else {
                    /*
                     * When ellipsoidal formulas are used, we aim for an 1 cm accuracy in coordinate values.
                     * We also aim for azimuthd such as the error is less than 1 cm after the first 10 km.
                     * If points are nearly antipodal, we relax the azimuth tolerance threshold to 1 meter.
                     */
                    linearTolerance    = Formulas.LINEAR_TOLERANCE;
                    latitudeTolerance  = Formulas.ANGULAR_TOLERANCE;
                    longitudeTolerance = Formulas.ANGULAR_TOLERANCE / cosφ2;
                    azimuthTolerance   = Formulas.LINEAR_TOLERANCE * (180/PI) / 10000;
                    if (isTestingInverse) {
                        final double Δ = max(abs(180 - abs(expected[COLUMN_λ2] - expected[COLUMN_λ1])),
                                                       abs(expected[COLUMN_φ1] + expected[COLUMN_φ2]));
                        if (Δ < 1) {
                            azimuthTolerance = 1 * (180/PI) / 10000;                // 1 meter for 10 km.
                        }
                    }
                }
                /*
                 * Set input values, compute then verify results. The azimuth tolerance is divided by cos(φ).
                 * This is consistent with the ∂y/∂φ = 1/cos(φ) term in the Jacobian of Mercator projection.
                 * An error of ε in the calculation of φ causes an error of ε/cos(φ) in calculation of tan(α).
                 * Given that tan(α)+ε ≈ tan(α+ε) for small ε values, we assume that the error on α is also
                 * proportional to 1/cos(φ).
                 */
                c.setStartGeographicPoint(expected[COLUMN_φ1], expected[COLUMN_λ1]);
                if (isTestingInverse) {
                    c.setEndGeographicPoint(expected[COLUMN_φ2], expected[COLUMN_λ2]);
                } else {
                    c.setStartingAzimuth (expected[COLUMN_α1]);
                    c.setGeodesicDistance(expected[COLUMN_Δs]);
                }
                final DirectPosition start = c.getStartPoint();
                final DirectPosition end   = c.getEndPoint();
                try {
                    assertEquals("φ₁", expected[COLUMN_φ1], start.getOrdinate(0),    Formulas.ANGULAR_TOLERANCE);
                    assertEquals("λ₁", expected[COLUMN_λ1], start.getOrdinate(1),    Formulas.ANGULAR_TOLERANCE);
                    assertEquals("α₁", expected[COLUMN_α1], c.getStartingAzimuth(),  azimuthTolerance / cosφ1);
                    assertEquals("φ₂", expected[COLUMN_φ2], end.getOrdinate(0),      latitudeTolerance);
                    assertEquals("λ₂", expected[COLUMN_λ2], end.getOrdinate(1),      longitudeTolerance);
                    assertEquals("α₂", expected[COLUMN_α2], c.getEndingAzimuth(),    azimuthTolerance / cosφ2);
                    assertEquals("∆s", expected[COLUMN_Δs], c.getGeodesicDistance(), linearTolerance);
                } catch (GeodesicException | AssertionError e) {
                    if (!isTestingInverse || e instanceof AssertionError || isFailure(expected)) {
                        out.printf("Test failure at line %d: %s%n"
                                + "The values provided in the test file are:%n"
                                + "(φ₁,λ₁) = %16.12f %16.12f%n"
                                + "(φ₂,λ₂) = %16.12f %16.12f%n"
                                + "The values computed by the geodesic calculator are:%n",
                                reader.getLineNumber(), e.getLocalizedMessage(),
                                expected[0], expected[1], expected[3], expected[4]);
                        out.println(c);
                        throw e;
                    }
                    noConvergenceCount++;
                }
            }
        }
        assertTrue("Unexpected amount of no convergence errors.", noConvergenceCount <= 8);
    }

    /**
     * Tells whether failure to compute geodesic for the given data should cause the test case to fail.
     * The default implementation always return {@code true}. Subclass can override if some points are
     * known to fail.
     *
     * @param  expected  a row from the {@code $SIS_DATA/Tests/GeodTest.dat} file.
     *         Use {@code COLUMN_*} constant for accessing values by column indices.
     * @return whether the JUnit test should fail.
     */
    boolean isFailure(final double[] expected) {
        return true;
    }
}
