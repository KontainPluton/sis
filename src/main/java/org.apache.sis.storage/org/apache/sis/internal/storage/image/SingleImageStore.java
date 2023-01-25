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
package org.apache.sis.internal.storage.image;

import org.apache.sis.coverage.SampleDimension;
import org.apache.sis.coverage.grid.GridCoverage;
import org.apache.sis.coverage.grid.GridGeometry;
import org.apache.sis.storage.*;
import org.opengis.geometry.Envelope;

import java.io.IOException;
import java.util.List;
import java.util.Optional;


/**
 * A world file store which is expected to contain exactly one image.
 * This class is used for image formats that are restricted to one image per file.
 * Examples: PNG and BMP image formats.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.2
 * @since   1.2
 * @module
 */
class SingleImageStore extends WorldFileStore implements GridCoverageResource {
    /**
     * The singleton resource in this aggregate. Fetched when first needed.
     */
    private volatile WorldFileResource delegate;

    /**
     * Creates a new store from the given file, URL or stream.
     *
     * @param  format  information about the storage (URL, stream, <i>etc</i>) and the reader/writer to use.
     * @throws DataStoreException if an error occurred while opening the stream.
     * @throws IOException if an error occurred while creating the image reader instance.
     */
    SingleImageStore(final FormatFinder format) throws DataStoreException, IOException {
        super(format, false);
    }

    /**
     * Returns {@code true} for meaning that the singleton component will be used only for internal purposes.
     */
    @Override
    final boolean isComponentHidden() {
        return true;
    }

    /**
     * Returns the singleton resource in this aggregate. The delegate is used for all
     * {@link GridCoverageResource} operations but <strong>not</strong> for the following operations:
     *
     * <ul>
     *   <li>{@link #getIdentifier()} because we want the filename without ":1" suffix (the image index).</li>
     *   <li>{@link #getMetadata()} because it is richer than {@link WorldFileResource#getMetadata()}.</li>
     * </ul>
     */
    final WorldFileResource delegate() throws DataStoreException {
        WorldFileResource r = delegate;
        if (r == null) {
            delegate = r = ((Components) components()).get(MAIN_IMAGE);
        }
        return r;
    }

    /**
     * Returns the valid extent of grid coordinates together with the conversion from those grid coordinates
     * to real world coordinates. The CRS and "pixels to CRS" conversion may be unknown if the {@code *.prj}
     * and/or world auxiliary file has not been found.
     */
    @Override
    public final GridGeometry getGridGeometry() throws DataStoreException {
        return delegate().getGridGeometry();
    }

    /**
     * Returns the envelope of the grid geometry if known.
     * The envelope is absent if the grid geometry does not provide this information.
     */
    @Override
    public final Optional<Envelope> getEnvelope() throws DataStoreException {
        return delegate().getEnvelope();
    }

    /**
     * Returns the preferred resolutions (in units of CRS axes) for read operations in this data store.
     */
    @Override
    public final List<double[]> getResolutions() throws DataStoreException {
        return delegate().getResolutions();
    }

    /**
     * Returns the ranges of sample values in each band. Those sample dimensions describe colors
     * because the World File format does not provide more information.
     */
    @Override
    public final List<SampleDimension> getSampleDimensions() throws DataStoreException {
        return delegate().getSampleDimensions();
    }

    /**
     * Requests a subset of the coverage.
     */
    @Override
    public final GridCoverageResource subset(Query query) throws UnsupportedQueryException, DataStoreException {
        return delegate().subset(query);
    }

    /**
     * Loads a subset of the image wrapped by this resource.
     *
     * @param  domain  desired grid extent and resolution, or {@code null} for reading the whole domain.
     * @param  range   0-based indices of sample dimensions to read, or {@code null} or an empty sequence for reading them all.
     * @return the grid coverage for the specified domain and range.
     */
    @Override
    public final GridCoverage read(GridGeometry domain, int... range) throws DataStoreException {
        return delegate().read(domain, range);
    }

    /**
     * Returns an indication about when the "physical" loading of raster data will happen.
     */
    @Override
    public final RasterLoadingStrategy getLoadingStrategy() throws DataStoreException {
        return delegate().getLoadingStrategy();
    }

    /**
     * Sets the preferred strategy about when to do the "physical" loading of raster data.
     * Implementations are free to ignore this parameter or to replace the given strategy
     * by the closest alternative that this resource can support.
     *
     * @param  strategy  the desired strategy for loading raster data.
     * @return {@code true} if the given strategy has been accepted, or {@code false}
     *         if this implementation replaced the given strategy by an alternative.
     */
    @Override
    public final boolean setLoadingStrategy(RasterLoadingStrategy strategy) throws DataStoreException {
        return delegate().setLoadingStrategy(strategy);
    }

    /**
     * The writable variant of {@link MultiImageStore}.
     */
    static final class Writable extends SingleImageStore implements WritableGridCoverageResource {
        /**
         * Creates a new store from the given file, URL or stream.
         *
         * @param  format  information about the storage (URL, stream, <i>etc</i>) and the reader/writer to use.
         * @throws DataStoreException if an error occurred while opening the stream.
         * @throws IOException if an error occurred while creating the image reader instance.
         */
        Writable(final FormatFinder format) throws DataStoreException, IOException {
            super(format);
        }

        /**
         * Writes a new coverage in the data store for this resource. If a coverage already exists for this resource,
         * then it will be overwritten only if the {@code TRUNCATE} or {@code UPDATE} option is specified.
         *
         * @param  coverage  new data to write in the data store for this resource.
         * @param  options   configuration of the write operation.
         */
        @Override
        public void write(GridCoverage coverage, Option... options) throws DataStoreException {
            ((WritableResource) delegate()).write(coverage, options);
        }
    }
}
