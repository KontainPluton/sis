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
package org.apache.sis.test.storage;

import java.util.Arrays;
import java.util.Random;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.RenderedImage;
import org.apache.sis.coverage.grid.GridCoverage;
import org.apache.sis.coverage.grid.GridDerivation;
import org.apache.sis.coverage.grid.GridGeometry;
import org.apache.sis.coverage.grid.GridExtent;
import org.apache.sis.storage.DataStoreException;
import org.apache.sis.storage.GridCoverageResource;
import org.apache.sis.image.PixelIterator;
import org.apache.sis.test.DependsOnMethod;
import org.apache.sis.test.TestUtilities;
import org.apache.sis.test.TestCase;
import org.junit.Test;

import static org.junit.Assert.*;


/**
 * Base class for testing the consistency of grid coverage read operations.
 * The test reads the grid coverage in full, then reads random sub-regions.
 * The sub-regions pixels are compared with the original image.
 *
 * <h2>Assumptions</h2>
 * Assuming that the code reading the full extent is correct, this class can detect some bugs
 * in the code reading sub-regions or applying sub-sampling. This assumption is reasonable if
 * we consider that the code reading the full extent is usually simpler than the code reading
 * a subset of data.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.1
 * @since   1.1
 * @module
 */
public strictfp class CoverageReadConsistency extends TestCase {
    /**
     * A constant for identifying the codes working on two dimensional slices.
     */
    private static final int BIDIMENSIONAL = 2;

    /**
     * The resource to test.
     */
    private final GridCoverageResource resource;

    /**
     * The coverage at full extent, full resolution and with all bands.
     * This coverage will be used as a reference for verifying values read in sub-domains.
     */
    private final GridCoverage full;

    /**
     * The random number generator to use.
     */
    private final Random random;

    /**
     * Creates a new tester. This constructor reads immediately the coverage at full extent and full resolution.
     * That full coverage will be used as a reference for verifying values read in sub-domains.
     *
     * @param  resource  the resource to test.
     * @throws DataStoreException if the full coverage can not be read.
     */
    public CoverageReadConsistency(final GridCoverageResource resource) throws DataStoreException {
        this.resource = resource;
        full = resource.read(null, null);
        random = TestUtilities.createRandomNumberGenerator();
    }

    /**
     * Tests reading in random sub-regions starting at coordinates (0,0).
     * Data are read at full resolution (no-subsampling) and all bands are read. This is the simplest test.
     *
     * @throws DataStoreException if an error occurred while using the resource.
     */
    @Test
    public void testSubRegionAtOrigin() throws DataStoreException {
        readAndCompareRandomRegions(10, false, false);
    }

    /**
     * Tests reading in random sub-regions starting at random offsets.
     * Data are read at full resolution (no-subsampling) and all bands are read.
     *
     * @throws DataStoreException if an error occurred while using the resource.
     */
    @Test
    @DependsOnMethod("testSubRegionAtOrigin")
    public void testSubRegionsAnywhere() throws DataStoreException {
        readAndCompareRandomRegions(10, true, false);
    }

    /**
     * Tests reading in random sub-regions starting at coordinates (0,0) with subsampling applied.
     * All bands are read.
     *
     * @throws DataStoreException if an error occurred while using the resource.
     */
    @Test
    @DependsOnMethod("testSubRegionAtOrigin")
    public void testSubsamplingAtOrigin() throws DataStoreException {
        readAndCompareRandomRegions(10, false, true);
    }

    /**
     * Tests reading in random sub-regions starting at random offsets with subsampling applied.
     * All bands are read.
     *
     * @throws DataStoreException if an error occurred while using the resource.
     */
    @Test
    @DependsOnMethod({"testSubsamplingAtOrigin", "testSubRegionsAnywhere"})
    public void testSubsamplingAnywhere() throws DataStoreException {
        readAndCompareRandomRegions(100, true, true);
    }

    /**
     * Implementation of methods testing reading in random sub-regions with random sub-samplings.
     *
     * @param  numIterations      number of random sub-regions to test.
     * @param  allowOffsets       whether to allow sub-regions to start elsewhere than (0,0).
     * @param  allowSubsamplings  whether to use random subsamplings.
     * @throws DataStoreException if an error occurred while using the resource.
     */
    private void readAndCompareRandomRegions(int numIterations, final boolean allowOffsets, final boolean allowSubsamplings)
            throws DataStoreException
    {
        final GridGeometry gg = resource.getGridGeometry();
        final GridExtent fullExtent = gg.getExtent();
        final int    dimension   = fullExtent.getDimension();
        final long[] low         = new long[dimension];
        final long[] high        = new long[dimension];
        final int [] subsampling = new int [dimension];
        final int [] subOffsets  = new int [dimension];
        while (--numIterations >= 0) {
            /*
             * Create a random domain to be used as a query on the `GridCoverageResource`.
             */
            for (int d=0; d<dimension; d++) {
                final int span = StrictMath.toIntExact(fullExtent.getSize(d));
                final int rs = random.nextInt(span);                    // Span of the sub-region - 1.
                if (allowOffsets) {
                    low[d] = random.nextInt(span - rs);                 // Note: (span - rs) > 0.
                }
                high[d] = low[d] + rs;
                subsampling[d] = 1;
                if (allowSubsamplings) {
                    subsampling[d] += random.nextInt(StrictMath.max(rs / 16, 1));
                }
            }
            final GridGeometry domain = gg.derive()
                    .subgrid(new GridExtent(null, low, high, true), subsampling).build();
            /*
             * Read a coverage containing the requested sub-domain. Note that the reader is free to read
             * more data than requested. The extent actually read is `actualReadExtent`. It shall contain
             * fully the requested `domain`.
             */
            final GridCoverage subset = resource.read(domain, null);
            final GridExtent actualReadExtent = subset.getGridGeometry().getExtent();
            assertEquals("Unexpected number of dimensions.", dimension, actualReadExtent.getDimension());
            for (int d=0; d<dimension; d++) {
                if (subsampling[d] == 1) {
                    assertTrue("Actual extent is too small.", actualReadExtent.getLow (d) <= low [d]);
                    assertTrue("Actual extent is too small.", actualReadExtent.getHigh(d) >= high[d]);
                }
            }
            /*
             * If subsampling was enabled, the factors selected by the reader may be different than
             * the subsampling factors that we specified. The following block updates those values.
             */
            if (allowSubsamplings) {
                final GridDerivation change = full.getGridGeometry().derive().subgrid(subset.getGridGeometry());
                System.arraycopy(change.getSubsampling(),        0, subsampling, 0, dimension);
                System.arraycopy(change.getSubsamplingOffsets(), 0, subOffsets,  0, dimension);
            }
            /*
             * Iterate over all dimensions greater than 2. In the common case where we are reading a
             * two-dimensional image, the following loop will be executed only once. If reading a 3D
             * or 4D image, the loop is executed for all possible two-dimensional slices in the cube.
             */
            final long[] sliceMin = actualReadExtent.getLow() .getCoordinateValues();
            final long[] sliceMax = actualReadExtent.getHigh().getCoordinateValues();
nextSlice:  for (;;) {
                System.arraycopy(sliceMin, BIDIMENSIONAL, sliceMax, BIDIMENSIONAL, dimension - BIDIMENSIONAL);
                final PixelIterator itr = iterator(full,   sliceMin, sliceMax, subsampling, subOffsets, allowSubsamplings);
                final PixelIterator itc = iterator(subset, sliceMin, sliceMax, subsampling, subOffsets, false);
                if (itr != null) {
                    assertEquals(itr.getDomain().getSize(), itc.getDomain().getSize());
                    double[] expected = null, actual = null;
                    while (itr.next()) {
                        assertTrue(itc.next());
                        expected = itr.getPixel(expected);
                        actual   = itc.getPixel(actual);
                        if (!Arrays.equals(expected, actual)) {
                            final Point pr = itr.getPosition();
                            final Point pc = itc.getPosition();
                            assertArrayEquals("Mismatch at position (" + pr.x + ", " + pr.y + ") in full image " +
                                              "and (" + pc.x + ", " + pc.y + ") in tested sub-image",
                                              expected, actual, STRICT);
                        }
                    }
                    assertFalse(itc.next());
                } else {
                    // Unable to create a reference image. Just check that no exception is thrown.
                    double[] actual = null;
                    while (itc.next()) {
                        actual = itc.getPixel(actual);
                    }
                }
                /*
                 * Move to the next two-dimensional slice and read again.
                 * We stop the loop after we have read all 2D slices.
                 */
                for (int d=dimension; --d >= BIDIMENSIONAL;) {
                    if (sliceMin[d]++ <= actualReadExtent.getHigh(d)) continue nextSlice;
                    sliceMin[d] = actualReadExtent.getLow(d);
                }
                break;
            }
        }
    }

    /**
     * Creates a pixel iterator for a sub-region in a slice of the specified coverage.
     * All coordinates given to this method are in the coordinate space of subsampled coverage subset.
     * This method returns {@code null} if the arguments are valid but the image can not be created
     * because of a restriction in {@code PixelInterleavedSampleModel} constructor.
     *
     * @param  coverage     the coverage from which to get the iterator.
     * @param  sliceMin     lower bounds of the <var>n</var>-dimensional region of the coverage for which to get an iterator.
     * @param  sliceMax     upper bounds of the <var>n</var>-dimensional region of the coverage for which to get an iterator.
     * @param  subsampling  subsampling factors to apply on the image.
     * @param  subOffsets   offsets to add after multiplication by subsampling factors.
     * @return pixel iterator over requested area, or {@code null} if unavailable.
     */
    private static PixelIterator iterator(final GridCoverage coverage, long[] sliceMin, long[] sliceMax,
            final int[] subsampling, final int[] subOffsets, final boolean allowSubsamplings)
    {
        /*
         * Same extent than `areaOfInterest` but in two dimensions and with (0,0) origin.
         * We use that for clipping iteration to the area that we requested even if the
         * coverage gave us a larger area.
         */
        final Rectangle sliceAOI = new Rectangle(StrictMath.toIntExact(sliceMax[0] - sliceMin[0] + 1),
                                                 StrictMath.toIntExact(sliceMax[1] - sliceMin[1] + 1));
        /*
         * If the given coordinates were in a subsampled space while the coverage is at full resolution,
         * convert the coordinates to full resolution.
         */
        if (allowSubsamplings) {
            sliceMin = sliceMin.clone();
            sliceMax = sliceMax.clone();
            for (int i=0; i<sliceMin.length; i++) {
                sliceMin[i] = sliceMin[i] * subsampling[i] + subOffsets[i];
                sliceMax[i] = sliceMax[i] * subsampling[i] + subOffsets[i];
            }
        }
        RenderedImage image = coverage.render(new GridExtent(null, sliceMin, sliceMax, true));
        /*
         * The subsampling offsets were included in the extent given to above `render` method call, so in principle
         * they should not be given again to `SubsampledImage` constructor.  However the `render` method is free to
         * return an image with a larger extent, which may result in different offsets. The result can be "too much"
         * offset. We want to compensate by subtracting the surplus. But because we can not have negative offsets,
         * we shift the whole `sliceAOI` (which is equivalent to subtracting `subX|Y` in full resolution coordinates)
         * and set the offset to the complement.
         */
        if (allowSubsamplings) {
            final int subX = subsampling[0];
            final int subY = subsampling[1];
            int offX = StrictMath.floorMod(image.getMinX(), subX);
            int offY = StrictMath.floorMod(image.getMinY(), subY);
            if (offX != 0) {sliceAOI.x--; offX = subX - offX;}
            if (offY != 0) {sliceAOI.y--; offY = subY - offY;}
            image = SubsampledImage.create(image, subX, subY, offX, offY);
            if (image == null) {
                return null;
            }
        }
        return new PixelIterator.Builder().setRegionOfInterest(sliceAOI).create(image);
    }
}