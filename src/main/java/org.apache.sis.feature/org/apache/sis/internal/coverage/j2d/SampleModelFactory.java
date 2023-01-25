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
package org.apache.sis.internal.coverage.j2d;

import java.util.Arrays;
import java.awt.image.DataBuffer;
import java.awt.image.SampleModel;
import java.awt.image.BandedSampleModel;
import java.awt.image.ComponentSampleModel;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.MultiPixelPackedSampleModel;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.RasterFormatException;
import org.apache.sis.internal.util.Numerics;
import org.apache.sis.image.DataType;
import org.apache.sis.util.ArraysExt;
import org.apache.sis.util.ArgumentChecks;
import org.apache.sis.util.resources.Errors;


/**
 * A factory for {@link SampleModel} instances. This class provides a convenient way to get the properties
 * of an existing sample model, modify them, then create a new sample model with the modified properties.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.1
 *
 * @see SampleModel#createCompatibleSampleModel(int, int)
 * @see SampleModel#createSubsetSampleModel(int[])
 *
 * @since 1.1
 * @module
 */
public final class SampleModelFactory {
    /**
     * Type of sample values as one of the {@link DataBuffer} constants.
     */
    private int dataType;

    /**
     * Width and height in pixels of tiles using the sample model.
     *
     * <p><b>Warning:</b> changing sample model size has non-obvious implications in {@link BandedSampleModel}
     * case when all data are in a single bank. The {@link SampleModel#createCompatibleSampleModel(int, int)}
     * method should be preferred instead.</p>
     *
     * @see SampleModel#createCompatibleSampleModel(int, int)
     */
    private final int width, height;

    /**
     * Number of bands of the image data.
     * Note: do not confuse <em>bands</em> and <em>banks</em>.
     */
    private int numBands;

    /**
     * Index for each bank storing a band of image data, or {@code null} if not applicable.
     */
    private int[] bankIndices;

    /**
     * Offsets for all bands in data array elements, or {@code null} if not applicable.
     */
    private int[] bandOffsets;

    /**
     * The bit masks for all bands, or {@code null} if not applicable.
     */
    private int[] bitMasks;

    /**
     * The bit offset into the data array where the first pixel begins.
     */
    private int dataBitOffset;

    /**
     * Number of bits per pixel, or 0 if not applicable.
     * Also known as "pixel bit stride".
     */
    private int numberOfBits;

    /**
     * Number of sample values to advance for moving to next pixel.
     * This is 0 when {@link #numberOfBits} should be used instead.
     */
    private int pixelStride;

    /**
     * Number of sample values to advance for moving to next line.
     */
    private int scanlineStride;

    /**
     * Creates a factory initialized to the given values.
     *
     * @param  type           type of sample values.
     * @param  width          tile width in pixels.
     * @param  height         tile height in pixels.
     * @param  numBands       number of bands.
     * @param  bitsPerSample  number of bits per sample values.
     * @param  isBanded       {@code true} if each band is stored in a separated bank.
     * @throws RasterFormatException if the arguments imply a sample model of unsupported type.
     */
    public SampleModelFactory(final DataType type, final int width, final int height,
            final int numBands, final int bitsPerSample, final boolean isBanded)
    {
        this.dataType  = type.toDataBufferType();
        this.width     = width;
        this.height    = height;
        this.numBands  = numBands;
        scanlineStride = width;
        pixelStride    = 1;
        if (bitsPerSample != type.size()) {
            if (numBands == 1) {
                // MultiPixelPackedSampleModel
                pixelStride    = 0;
                numberOfBits   = bitsPerSample;
                scanlineStride = Numerics.ceilDiv(Math.multiplyExact(width, numberOfBits), type.size());
            } else if (!isBanded) {
                // SinglePixelPackedSampleModel
                bitMasks = new int[numBands];
                bitMasks[0] = (1 << bitsPerSample) - 1;
                for (int i=1; i < bitMasks.length; i++) {
                    bitMasks[i] = bitMasks[i-1] << bitsPerSample;
                }
            } else {
                // TODO: we can support that with a little bit more work.
                throw new RasterFormatException(Errors.format(Errors.Keys.UnsupportedType_1, "bitsPerSample=" + type.size()));
            }
        } else if (isBanded) {
            // BandedSampleModel
            bankIndices = ArraysExt.range(0, numBands);
            bandOffsets = new int[numBands];
        } else {
            // PixelInterleavedSampleModel
            bandOffsets    = ArraysExt.range(0, numBands);
            scanlineStride = Math.multiplyExact(numBands, width);
            pixelStride    = numBands;
        }
    }

    /**
     * Creates a new factory initialized to the value of given sample model.
     *
     * @param  model  the sample model from which to copy values.
     * @throws RasterFormatException if the type of the given sample model is not supported.
     */
    public SampleModelFactory(final SampleModel model) {
        width    = model.getWidth();
        height   = model.getHeight();
        numBands = model.getNumBands();
        dataType = model.getDataType();
        if (model instanceof ComponentSampleModel) {
            final ComponentSampleModel cm = (ComponentSampleModel) model;
            bankIndices    = cm.getBankIndices();
            bandOffsets    = cm.getBandOffsets();
            scanlineStride = cm.getScanlineStride();
            pixelStride    = cm.getPixelStride();
            for (int i=0; i<bankIndices.length; i++) {
                if (bankIndices[i] != 0) return;
            }
            bankIndices = null;     // PixelInterleavedSampleModel
        } else if (model instanceof SinglePixelPackedSampleModel) {
            final SinglePixelPackedSampleModel cm = (SinglePixelPackedSampleModel) model;
            bitMasks       = cm.getBitMasks();
            scanlineStride = cm.getScanlineStride();
            pixelStride    = 1;
        } else if (model instanceof MultiPixelPackedSampleModel) {
            final MultiPixelPackedSampleModel cm = (MultiPixelPackedSampleModel) model;
            numberOfBits   = cm.getPixelBitStride();
            dataBitOffset  = cm.getDataBitOffset();
            scanlineStride = cm.getScanlineStride();
        } else {
            throw new RasterFormatException(Errors.format(Errors.Keys.UnsupportedType_1, model.getClass()));
        }
    }

    /**
     * Requests a sample model with only a subset of the bands of the original sample model.
     * Special cases:
     *
     * <ul>
     *   <li>For {@link SinglePixelPackedSampleModel}, this method may change the data type
     *       if decreasing the number of bands make possible to store pixels in smaller integers.</li>
     * </ul>
     *
     * <h4>Comparison with standard method</h4>
     * The standard {@link SampleModel#createSubsetSampleModel(int[])} method also selects a subset of the bands.
     * However that standard method creates a sample model accepting the same {@link DataBuffer}
     * than the original {@link SampleModel}, which is useful for creating a <em>view</em> of image data that are
     * already in memory. By contrast, this {@code BandSelector} <em>compresses</em> bank indices or pixel masks
     * for leaving no empty space between them. This is useful when done <em>before</em> loading data from a file
     * in order to avoid consuming space for bands that were not requested.
     *
     * @param  bands  bands to keep.
     *
     * @see SampleModel#createSubsetSampleModel(int[])
     */
    public void subsetAndCompress(final int[] bands) {
        ArgumentChecks.ensureSizeBetween("bands", 1, numBands, bands.length);
        if (bankIndices != null) bankIndices = subset(bankIndices, bands, true);
        if (bandOffsets != null) bandOffsets = subset(bandOffsets, bands, bankIndices == null);
        if (bitMasks    != null) {
            final int[] shifts = new int[bitMasks.length];      // Number of bits to "remove".
            for (int i=0; i<shifts.length; i++) shifts[i] = bitCount(bitMasks[i]);
            for (int i=0; i< bands.length; i++) shifts[bands[i]] = 0;
            for (int i=1; i<shifts.length; i++) shifts[i] += shifts[i-1];
            final int[] masks = new int[bands.length];
            int allMasks = 0;
            for (int i=0; i<bands.length; i++) {
                final int b = bands[i];
                allMasks |= (masks[i] = bitMasks[b] >>> shifts[b]);
            }
            bitMasks = masks;
            if (dataType == DataBuffer.TYPE_INT    && (allMasks & ~0xFFFF) == 0) dataType = DataBuffer.TYPE_USHORT;
            if (dataType == DataBuffer.TYPE_USHORT && (allMasks & ~0x00FF) == 0) dataType = DataBuffer.TYPE_BYTE;
        }
        if (pixelStride > 1) {
            final int s    = scanlineStride / pixelStride;
            final int r    = scanlineStride % pixelStride;
            pixelStride   -= (numBands - bands.length);
            scanlineStride = pixelStride * s + r;
        }
        numBands = bands.length;
    }

    /**
     * Returns the number of bits in the given mask. All bits between the first and last bits
     * are considered set to 1. This is intentional, because masks shall not overlap.
     *
     * @see Integer#bitCount(int)
     */
    private static int bitCount(final int mask) {
        return Integer.SIZE - Integer.numberOfLeadingZeros(mask) - Integer.numberOfTrailingZeros(mask);
    }

    /**
     * Creates a subset of the {@code data} arrays for the specified bands.
     * If {@code compress} is {@code true}, then sequences such as {1, 3, 4, 6, …}
     * are replaced by {0, 1, 2, 3, …} (not necessarily in increasing order).
     */
    private static int[] subset(final int[] data, final int[] bands, final boolean compress) {
        final int[] subset = new int[bands.length];
        for (int i=0; i<bands.length; i++) {
            subset[i] = data[bands[i]];
        }
        if (!compress) {
            return subset;
        }
        Arrays.sort(subset);
        final int[] indices = new int[subset.length];
        for (int i=0; i<bands.length; i++) {
            indices[i] = Arrays.binarySearch(subset, data[bands[i]]);
            assert indices[i] >= 0;
        }
        return indices;
    }

    /**
     * Builds a sample model based on current factory configuration.
     * The factory is still valid after this method call.
     *
     * @return the sample model built from current factory configuration.
     * @throws IllegalArgumentException if an error occurred while building a sample model.
     */
    public SampleModel build() {
        if (pixelStride == 1) {
            if (bankIndices != null) {
                return new BandedSampleModel(dataType, width, height, scanlineStride, bankIndices, bandOffsets);
            } else if (bitMasks != null) {
                return new SinglePixelPackedSampleModel(dataType, width, height, scanlineStride, bitMasks);
            }
        }
        if (numberOfBits != 0) {
            return new MultiPixelPackedSampleModel(dataType, width, height, numberOfBits, scanlineStride, dataBitOffset);
        } else {
            return new PixelInterleavedSampleModel(dataType, width, height, pixelStride, scanlineStride, bandOffsets);
        }
    }
}
