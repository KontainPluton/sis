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
package org.apache.sis.image;

import org.apache.sis.coverage.Category;
import org.apache.sis.coverage.SampleDimension;
import org.apache.sis.internal.coverage.CompoundTransform;
import org.apache.sis.internal.coverage.SampleDimensions;
import org.apache.sis.internal.coverage.j2d.Colorizer;
import org.apache.sis.internal.coverage.j2d.ImageLayout;
import org.apache.sis.internal.coverage.j2d.ImageUtilities;
import org.apache.sis.internal.feature.Resources;
import org.apache.sis.math.Statistics;
import org.apache.sis.measure.NumberRange;
import org.apache.sis.referencing.operation.transform.MathTransforms;
import org.apache.sis.util.collection.BackingStoreException;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.MathTransform1D;
import org.opengis.referencing.operation.NoninvertibleTransformException;
import org.opengis.referencing.operation.TransformException;

import javax.measure.Quantity;
import java.awt.*;
import java.awt.image.*;
import java.nio.DoubleBuffer;
import java.util.List;
import java.util.*;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;


/**
 * Image generated for visualization purposes only (not to be used for computation purposes).
 * This class merges {@link ResampledImage}, {@link BandedSampleConverter} and {@link RecoloredImage} operations
 * in a single operation for efficiency. This merge avoids creating intermediate tiles of {@code float} values.
 * By writing directly {@code byte} values, we save memory and CPU because
 * {@link WritableRaster#setPixel(int, int, int[])} has more efficient implementations for integers.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.3
 * @since   1.1
 * @module
 */
final class Visualization extends ResampledImage {
    /**
     * Builds an image where all sample values are indices of colors in an {@link IndexColorModel}.
     * If the given image stores sample values as unsigned bytes or short integers, then those values
     * are used as-is (they are not copied or converted). Otherwise {@link Visualization} will convert
     * sample values to unsigned bytes in order to enable the use of {@link IndexColorModel}.
     *
     * <p>This builder accepts two kinds of input:</p>
     * <ul>
     *   <li>Non-null {@code sourceBands} and {@link ImageProcessor#getCategoryColors()}.</li>
     *   <li>Non-null {@code rangesAndColors}.</li>
     * </ul>
     *
     * The resulting image is suitable for visualization purposes but should not be used for computation purposes.
     * There is no guarantee about the number of bands in returned image and the formulas used for converting
     * floating point values to integer values.
     *
     * <h2>Resampling</h2>
     * {@link Visualization} can optionally be combined with a {@link ResampledImage} operation.
     * This can be done by providing a non-null value to the {@code toSource} argument.
     *
     * @see ImageProcessor#visualize(RenderedImage, Map)
     */
    static final class Builder {
        /** Number of bands of the image to create. */
        private static final int NUM_BANDS = 1;

        /** Band to make visible. */
        private static final int VISIBLE_BAND = 0;

        ////  ┌─────────────────────────────────────┐
        ////  │ Arguments given by user             │
        ////  └─────────────────────────────────────┘

        /** Pixel coordinates of the visualization image, or {@code null} if same as {@link #source} image. */
        private Rectangle bounds;

        /** Image to be resampled and converted. */
        private RenderedImage source;

        /** Conversion from pixel coordinates of visualization image to pixel coordinates of {@link #source} image. */
        private MathTransform toSource;

        /** Description of {@link #source} bands, or {@code null} if none. */
        private List<SampleDimension> sourceBands;

        /** Colors to apply for range of sample values in source image, or {@code null} if none. */
        private Collection<Map.Entry<NumberRange<?>,Color[]>> rangesAndColors;

        ////  ┌─────────────────────────────────────┐
        ////  │ Given by ImageProcesor.configure(…) │
        ////  └─────────────────────────────────────┘

        /** Computer of tile size. */
        ImageLayout layout;

        /** Object to use for performing interpolations. */
        Interpolation interpolation;

        /** The colors to use for given categories of sample values, or {@code null} is unspecified. */
        Function<Category,Color[]> categoryColors;

        /** Values to use for pixels in this image that can not be mapped to pixels in source image. */
        Number[] fillValues;

        /** Values of {@value #POSITIONAL_ACCURACY_KEY} property, or {@code null} if none. */
        Quantity<?>[] positionalAccuracyHints;

        ////  ┌─────────────────────────────────────┐
        ////  │ Computed by `create(…)`             │
        ////  └─────────────────────────────────────┘

        /** Transfer functions to apply on each band of the source image. */
        private MathTransform1D[] converters;

        /** Sample model of {@link Visualization} image. */
        private SampleModel sampleModel;

        /** Color model of {@link Visualization} image. */
        private ColorModel colorModel;

        /**
         * Creates a builder for a visualization image with colors inferred from sample dimensions.
         *
         * @param bounds       desired domain of pixel coordinates, or {@code null} if same as {@code source} image.
         * @param source       the image for which to replace the color model.
         * @param toSource     pixel coordinates conversion to {@code source} image, or {@code null} if none.
         * @param sourceBands  description of {@code source} bands.
         */
        Builder(final Rectangle bounds, final RenderedImage source, final MathTransform toSource,
                final List<SampleDimension> sourceBands)
        {
            this.bounds      = bounds;
            this.source      = source;
            this.toSource    = toSource;
            this.sourceBands = sourceBands;
        }

        /**
         * Creates a builder for a visualization image with colors specified for range of values.
         * Current version assumes that target image bounds are the same than source image bounds
         * and that there is no change of pixel coordinates, but this is not a real restriction.
         * The {@code bounds} and {@code toSource} arguments could be added back in the future if useful.
         *
         * @param source           the image for which to replace the color model.
         * @param rangesAndColors  range of sample values in source image associated to colors to apply.
         */
        Builder(final RenderedImage source, final Collection<Map.Entry<NumberRange<?>,Color[]>> rangesAndColors) {
            this.source          = source;
            this.rangesAndColors = rangesAndColors;
        }

        /**
         * Returns an image where all sample values are indices of colors in an {@link IndexColorModel}.
         * If the source image stores sample values as unsigned bytes or short integers, then those values
         * are used as-is (they are not copied or converted). Otherwise this operation will convert sample
         * values to unsigned bytes in order to enable the use of {@link IndexColorModel}.
         *
         * <p>The resulting image is suitable for visualization but should not be used for computational purposes.
         * There is no guarantee about the number of bands in returned image and the formulas used for converting
         * floating point values to integer values.</p>
         *
         * <h4>Resampling</h4>
         * This operation can optionally be combined with a {@link ResampledImage} operation.
         * This can be done by providing a non-null value to the {@link #toSource} field.
         *
         * @param  processor  the processor invoking this constructor.
         * @return resampled and recolored image for visualization purposes only.
         * @throws NoninvertibleTransformException if sample values in source image
         *         can not be converted to sample values in the recolored image.
         */
        RenderedImage create(final ImageProcessor processor) throws NoninvertibleTransformException {
            final int visibleBand = ImageUtilities.getVisibleBand(source);
            if (visibleBand < 0) {
                // This restriction may be relaxed in a future version if we implement conversion to RGB images.
                throw new IllegalArgumentException(Resources.format(Resources.Keys.OperationRequiresSingleBand));
            }
            /*
             * Get a `Colorizer` which will compute the `ColorModel` of destination image.
             * There is different ways to create colorizer, depending on which arguments
             * were supplied by user. In precedence order:
             *
             *    - rangesAndColor  : Collection<Map.Entry<NumberRange<?>,Color[]>>
             *    - sourceBands     : List<SampleDimension>
             *    - statistics
             */
            boolean initialized;
            final Colorizer colorizer;
            if (rangesAndColors != null) {
                colorizer = new Colorizer(rangesAndColors);
                initialized = true;
            } else {
                /*
                 * Ranges of sample values were not specified explicitly. Instead we will try to infer them
                 * in various ways: sample dimensions, scaled color model, statistics in last resort.
                 */
                colorizer = new Colorizer(categoryColors);
                initialized = (sourceBands != null) && colorizer.initialize(source.getSampleModel(), sourceBands.get(visibleBand));
                if (initialized) {
                    /*
                     * If we have been able to configure Colorizer using SampleDimension, apply an adjustment based
                     * on the ScaledColorModel if it exists.  Use case: an image is created with an IndexColorModel
                     * determined by the SampleModel, then user enhanced contrast by a call to `stretchColorRamp(…)`
                     * above. We want to preserve that contrast enhancement.
                     */
                    colorizer.rescaleMainRange(source.getColorModel());
                } else {
                    /*
                     * If we have not been able to use the SampleDimension, try to use the ColorModel or SampleModel.
                     * There is no call to `rescaleMainRange(…)` because the following code already uses the range
                     * specified by the ColorModel, if available.
                     */
                    initialized = colorizer.initialize(source.getColorModel());
                    if (!initialized) {
                        if (source instanceof RecoloredImage) {
                            final RecoloredImage colored = (RecoloredImage) source;
                            colorizer.initialize(colored.minimum, colored.maximum);
                            initialized = true;
                        } else {
                            initialized = colorizer.initialize(source.getSampleModel(), visibleBand);
                        }
                    }
                }
            }
            source = BandSelectImage.create(source, new int[] {visibleBand});               // Make single-banded.
            if (!initialized) {
                /*
                 * If none of above Colorizer configurations worked, use statistics in last resort. We do that
                 * after we reduced the image to a single band, in order to reduce the amount of calculations.
                 */
                final DoubleUnaryOperator[] sampleFilters = SampleDimensions.toSampleFilters(processor, sourceBands);
                final Statistics statistics = processor.valueOfStatistics(source, null, sampleFilters)[VISIBLE_BAND];
                colorizer.initialize(statistics.minimum(), statistics.maximum());
            }
            /*
             * If we reach this point, sample values need to be converted to integers in [0 … 255] range.
             * Skip any previous `RecoloredImage` since we are replacing the `ColorModel` by a new one.
             */
            while (source instanceof RecoloredImage) {
                source = ((RecoloredImage) source).source;
            }
            colorModel = colorizer.compactColorModel(NUM_BANDS, VISIBLE_BAND);
            converters = new MathTransform1D[] {
                colorizer.getSampleToIndexValues()          // Must be after `compactColorModel(…)`.
            };
            /*
             * If there is no conversion of pixel coordinates, there is no need for interpolations.
             * In such case the `Visualization.computeTile(…)` implementation takes a shortcut which
             * requires the tile layout of destination image to be the same as source image.
             */
            if (toSource == null) {
                toSource = MathTransforms.identity(BIDIMENSIONAL);
            }
            if (toSource.isIdentity() && (bounds == null || ImageUtilities.getBounds(source).contains(bounds))) {
                layout        = ImageLayout.fixedSize(source);
                interpolation = Interpolation.NEAREST;
            } else {
                interpolation = combine(interpolation.toCompatible(source), converters);
                converters    = null;
            }
            /*
             * Final image creation after the tile layout has been chosen.
             */
            sampleModel = layout.createBandedSampleModel(Colorizer.TYPE_COMPACT, NUM_BANDS, source, bounds);
            if (bounds == null) {
                bounds = ImageUtilities.getBounds(source);
            }
            return ImageProcessor.unique(new Visualization(this));
        }
    }

    /**
     * Combines the given interpolation method with the given sample conversion.
     */
    private static Interpolation combine(final Interpolation interpolation, final MathTransform1D[] converters) {
        final MathTransform converter = CompoundTransform.create(converters);
        if (converter.isIdentity()) {
            return interpolation;
        } else if (converter instanceof MathTransform1D) {
            return new InterpConvertOneBand(interpolation, (MathTransform1D) converter);
        } else {
            return new InterpConvert(interpolation, converter);
        }
    }

    /**
     * Interpolation followed by conversion from floating point values to the values to store as integers in the
     * destination image. This class is used for combining {@link ResampledImage} and {@link BandedSampleConverter}
     * in a single operation.
     */
    static class InterpConvert extends Interpolation {
        /**
         * The object to use for performing interpolations.
         *
         * @see ResampledImage#interpolation
         */
        final Interpolation interpolation;

        /**
         * Conversion from floating point values resulting from interpolations to values to store as integers
         * in the destination image. This transform shall operate on all bands in one {@code transform(…)} call.
         */
        final MathTransform converter;

        /**
         * Creates a new object combining the given interpolation with the given conversion of sample values.
         */
        InterpConvert(final Interpolation interpolation, final MathTransform converter) {
            this.interpolation = interpolation;
            this.converter = converter;
        }

        /**
         * Delegates to {@link Interpolation#getSupportSize()}.
         */
        @Override
        public final Dimension getSupportSize() {
            return interpolation.getSupportSize();
        }

        /**
         * Delegates to {@link #interpolation}, then convert sample values in all bands.
         *
         * @throws BackingStoreException if an error occurred while converting sample values.
         *         This exception should be unwrapped by {@link #computeTile(int, int, WritableRaster)}.
         */
        @Override
        public void interpolate(final DoubleBuffer source, final int numBands,
                                final double xfrac, final double yfrac,
                                final double[] writeTo, final int writeToOffset)
        {
            interpolation.interpolate(source, numBands, xfrac, yfrac, writeTo, writeToOffset);
            try {
                converter.transform(writeTo, writeToOffset, writeTo, writeToOffset, 1);
            } catch (TransformException e) {
                throw new BackingStoreException(e);     // Will be unwrapped by computeTile(…).
            }
        }

        /** This interpolation never need to be disabled. */
        @Override Interpolation toCompatible(final RenderedImage source) {
            return this;
        }
    }

    /**
     * Same as {@link InterpConvert} optimized for the single-band case.
     * This class uses the more efficient {@link MathTransform1D#transform(double)} method.
     */
    private static final class InterpConvertOneBand extends InterpConvert {
        /** Conversion from floating point values to values to store as integers in the destination image. */
        private final MathTransform1D singleConverter;

        /** Creates a new object combining the given interpolation with the given conversion of sample values. */
        InterpConvertOneBand(final Interpolation interpolation, final MathTransform1D converter) {
            super(interpolation, converter);
            singleConverter = converter;
        }

        /** Delegates to {@link #interpolation}, then convert sample values in all bands. */
        @Override public void interpolate(final DoubleBuffer source, final int numBands,
                                          final double xfrac, final double yfrac,
                                          final double[] writeTo, final int writeToOffset)
        {
            interpolation.interpolate(source, numBands, xfrac, yfrac, writeTo, writeToOffset);
            try {
                writeTo[writeToOffset] = singleConverter.transform(writeTo[writeToOffset]);
            } catch (TransformException e) {
                throw new BackingStoreException(e);     // Will be unwrapped by computeTile(…).
            }
        }
    }

    /**
     * Transfer functions to apply on each band of the source image, or {@code null} if those conversions are done
     * by {@link InterpConvert}. Non-null array is used for allowing {@link #computeTile(int, int, WritableRaster)}
     * to use a shortcut avoiding {@link ResampledImage} cost. Outputs should be values in the [0 … 255] range;
     * values outside that ranges will be clamped.
     */
    private final MathTransform1D[] converters;

    /**
     * The color model for the expected range of values. Typically an {@link IndexColorModel} for byte values.
     * May be {@code null} if the color model is unknown.
     */
    private final ColorModel colorModel;

    /**
     * Creates a new image which will resample and convert values of the given image.
     * See parent class for more details about arguments.
     */
    private Visualization(final Builder builder) {
        super(builder.source,
              builder.sampleModel,
              builder.layout.getMinTile(),
              builder.bounds,
              builder.toSource,
              builder.interpolation,
              builder.fillValues,
              builder.positionalAccuracyHints);

        this.colorModel = builder.colorModel;
        this.converters = builder.converters;
    }

    /**
     * Returns {@code true} if this image can not have mask.
     */
    @Override
    final boolean hasNoMask() {
        return !(interpolation instanceof InterpConvert) && super.hasNoMask();
    }

    /**
     * Returns the color model associated with all rasters of this image.
     */
    @Override
    public ColorModel getColorModel() {
        return colorModel;
    }

    /**
     * Invoked when a tile need to be computed or updated.
     *
     * @throws TransformException if an error occurred while computing pixel coordinates or converting sample values.
     */
    @Override
    protected Raster computeTile(final int tileX, final int tileY, WritableRaster tile) throws TransformException {
        if (converters == null) try {
            // Most expansive operation (resampling + conversion).
            return super.computeTile(tileX, tileY, tile);
        } catch (BackingStoreException e) {
            throw e.unwrapOrRethrow(TransformException.class);
        }
        if (tile == null) {
            tile = createTile(tileX, tileY);
        }
        // Conversion only, when no resampling is needed.
        Transferer.create(getSource(), tile).compute(converters);
        return tile;
    }

    /**
     * Compares the given object with this image for equality.
     */
    @Override
    public boolean equals(final Object object) {
        if (super.equals(object)) {
            final Visualization other = (Visualization) object;
            return Arrays .equals(converters, other.converters) &&
                   Objects.equals(colorModel, other.colorModel);
        }
        return false;
    }

    /**
     * Returns a hash code value for this image.
     */
    @Override
    public int hashCode() {
        return super.hashCode() + 67 *  Arrays.hashCode(converters)
                                + 97 * Objects.hashCode(colorModel);
    }
}
