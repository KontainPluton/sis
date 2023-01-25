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
package org.apache.sis.storage.landsat;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.opengis.util.LocalName;
import org.opengis.util.GenericName;
import org.opengis.metadata.Metadata;
import org.opengis.metadata.identification.Identification;
import org.opengis.metadata.content.CoverageContentType;
import org.apache.sis.storage.GridCoverageResource;
import org.apache.sis.storage.StorageConnector;
import org.apache.sis.storage.DataStoreException;
import org.apache.sis.storage.geotiff.GeoTiffStore;
import org.apache.sis.internal.geotiff.SchemaModifier;
import org.apache.sis.internal.storage.GridResourceWrapper;
import org.apache.sis.metadata.iso.DefaultMetadata;
import org.apache.sis.metadata.iso.citation.DefaultCitation;
import org.apache.sis.metadata.iso.content.DefaultImageDescription;
import org.apache.sis.metadata.iso.content.DefaultAttributeGroup;
import org.apache.sis.metadata.iso.content.DefaultSampleDimension;
import org.apache.sis.metadata.iso.content.DefaultBand;
import org.apache.sis.coverage.SampleDimension;
import org.apache.sis.measure.NumberRange;
import org.apache.sis.measure.Units;
import org.apache.sis.util.resources.Vocabulary;

import static org.apache.sis.internal.util.CollectionsExt.first;


/**
 * A band in a Landsat data set. Each band is represented by a separated GeoTIFF file.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.2
 * @since   1.1
 * @module
 */
final class Band extends GridResourceWrapper implements SchemaModifier {
    /**
     * The data store that contains this band.
     * Also the object on which to perform synchronization locks.
     */
    private final LandsatStore parent;

    /**
     * The band for which this instance provides data.
     */
    final BandName band;

    /**
     * Identifier of the band for which this instance provides data.
     * Should not be modified after the end of metadata parsing.
     *
     * @see #getIdentifier()
     */
    LocalName identifier;

    /**
     * Filename of the file to read for band data.
     * This is relative to {@link LandsatStore#directory}.
     * Should not be modified after the end of metadata parsing.
     */
    String filename;

    /**
     * Metadata about the band.
     * Should not be modified after the end of metadata parsing.
     */
    final DefaultSampleDimension sampleDimension;

    /**
     * Creates a new resource for the specified band.
     */
    Band(final LandsatStore parent, final BandName band) {
        this.parent = parent;
        this.band   = band;
        if (band.wavelength != 0) {
            final DefaultBand b = new DefaultBand();
            b.setPeakResponse((double) band.wavelength);
            b.setBoundUnits(Units.NANOMETRE);
            sampleDimension = b;
        } else {
            sampleDimension = new DefaultSampleDimension();
        }
        sampleDimension.setDescription(band.title);
        if (band.group.reflectance) {
            sampleDimension.setUnits(Units.UNITY);
        } else {
            // W/(m² sr um)/DN
        }
    }

    /**
     * Returns the object on which to perform all synchronizations for thread-safety.
     */
    @Override
    protected final Object getSynchronizationLock() {
        return parent;
    }

    /**
     * Creates the GeoTIFF reader and get the first image from it.
     */
    @Override
    protected GridCoverageResource createSource() throws DataStoreException {
        final Path file;
        if (parent.directory != null) {
            file = parent.directory.resolve(filename);
        } else {
            file = Paths.get(filename);
        }
        final StorageConnector connector = new StorageConnector(file);
        connector.setOption(SchemaModifier.OPTION, this);
        return new GeoTiffStore(parent, parent.getProvider(), connector, true).components().get(0);
    }

    /**
     * Returns the resource persistent identifier. The name is the {@link BandName#name()}
     * and the scope (namespace) is the name of the directory that contains this band.
     */
    @Override
    public Optional<GenericName> getIdentifier() throws DataStoreException {
        return Optional.of(identifier);
    }

    /**
     * Invoked when the GeoTIFF reader creates the resource identifier.
     * We use the identifier of the enclosing {@link Band}.
     */
    @Override
    public GenericName customize(final int image, final GenericName fallback) {
        return (image == 0) ? identifier : fallback;
    }

    /**
     * Invoked when the GeoTIFF reader creates a metadata.
     * This method modifies or completes some information inferred by the GeoTIFF reader.
     */
    @Override
    public Metadata customize(final int image, final DefaultMetadata metadata) {
        if (image == 0) {
            for (final Identification id : metadata.getIdentificationInfo()) {
                final DefaultCitation c = (DefaultCitation) id.getCitation();
                if (c != null) {
                    c.setTitle(band.title);
                    break;
                }
            }
            /*
             * All collections below should be singleton and all casts should be safe because we use
             * one specific implementation (`GeoTiffStore`) which is known to build metadata that way.
             * A ClassCastException would be a bug in the handling of `isElectromagneticMeasurement(…)`.
             */
            final DefaultImageDescription content = (DefaultImageDescription) first(metadata.getContentInfo());
            final DefaultAttributeGroup   group   = (DefaultAttributeGroup)   first(content.getAttributeGroups());
            final DefaultSampleDimension  sd      = (DefaultSampleDimension)  first(group.getAttributes());
            group.getContentTypes().add(CoverageContentType.PHYSICAL_MEASUREMENT);
            sd.setDescription(sampleDimension.getDescription());
            sd.setMinValue   (sampleDimension.getMinValue());
            sd.setMaxValue   (sampleDimension.getMaxValue());
            sd.setScaleFactor(sampleDimension.getScaleFactor());
            sd.setOffset     (sampleDimension.getOffset());
            sd.setUnits      (sampleDimension.getUnits());
            if (sampleDimension instanceof DefaultBand) {
                final DefaultBand s = (DefaultBand) sampleDimension;
                final DefaultBand t = (DefaultBand) sd;
                t.setPeakResponse(s.getPeakResponse());
                t.setBoundUnits(s.getBoundUnits());
            }
        }
        return metadata;
    }

    /**
     * Invoked when a sample dimension is created for a band in an image.
     */
    @Override
    public SampleDimension customize(final int image, final int band, final NumberRange<?> sampleRange,
                                     final SampleDimension.Builder dimension)
    {
        if ((image | band) == 0) {
            dimension.setName(identifier);
            if (sampleRange != null) {
                final Number min    = sampleRange.getMinValue();
                final Number max    = sampleRange.getMaxValue();
                final Double scale  = sampleDimension.getScaleFactor();
                final Double offset = sampleDimension.getOffset();
                if (min != null && max != null && scale != null && offset != null) {
                    int lower = min.intValue();
                    if (lower >= 0) {           // Should always be zero but we are paranoiac.
                        dimension.addQualitative(Vocabulary.formatInternational(Vocabulary.Keys.Nodata), 0);
                        if (lower == 0) lower = 1;
                    }
                    dimension.addQuantitative(this.band.group.measurement, lower, max.intValue(),
                                              scale, offset, sampleDimension.getUnits());
                }
            }
        }
        return dimension.build();
    }

    /**
     * Returns {@code true} if the converted values are measurement in the electromagnetic spectrum.
     */
    @Override
    public boolean isElectromagneticMeasurement(final int image) {
        return (image == 0) && band.wavelength != 0;
    }
}
