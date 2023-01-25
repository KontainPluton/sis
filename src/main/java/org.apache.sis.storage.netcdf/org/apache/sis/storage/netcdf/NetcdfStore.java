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
package org.apache.sis.storage.netcdf;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.net.URI;
import java.util.List;
import java.util.Collection;
import java.util.Optional;

import org.opengis.util.NameSpace;
import org.opengis.util.NameFactory;
import org.opengis.util.GenericName;
import org.opengis.metadata.Metadata;
import org.opengis.parameter.ParameterValueGroup;
import org.apache.sis.storage.DataStore;
import org.apache.sis.storage.DataStoreException;
import org.apache.sis.storage.UnsupportedStorageException;
import org.apache.sis.storage.StorageConnector;
import org.apache.sis.storage.Aggregate;
import org.apache.sis.internal.netcdf.Decoder;
import org.apache.sis.internal.netcdf.RasterResource;
import org.apache.sis.internal.storage.URIDataStore;
import org.apache.sis.internal.util.UnmodifiableArrayList;
import org.apache.sis.internal.util.Strings;
import org.apache.sis.setup.OptionKey;
import org.apache.sis.storage.DataStoreClosedException;
import org.apache.sis.storage.Resource;
import org.apache.sis.storage.event.StoreEvent;
import org.apache.sis.storage.event.StoreListener;
import org.apache.sis.storage.event.WarningEvent;
import org.apache.sis.util.collection.DefaultTreeTable;
import org.apache.sis.util.collection.TableColumn;
import org.apache.sis.util.collection.TreeTable;
import org.apache.sis.util.CharSequences;
import org.apache.sis.util.Version;
import ucar.nc2.constants.ACDD;
import ucar.nc2.constants.CDM;


/**
 * A data store backed by netCDF files.
 * Instances of this data store are created by {@link NetcdfStoreProvider#open(StorageConnector)}.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.3
 *
 * @see NetcdfStoreProvider
 *
 * @since 0.3
 * @module
 */
public class NetcdfStore extends DataStore implements Aggregate {
    /**
     * The object to use for decoding the netCDF file content. There is two different implementations,
     * depending on whether we are using the embedded SIS decoder or a wrapper around the UCAR library.
     * This is set to {@code null} when the data store is closed.
     *
     * @see #decoder()
     */
    private Decoder decoder;

    /**
     * The {@link NetcdfStoreProvider#LOCATION} parameter value, or {@code null} if none.
     * This is used for information purpose only, not for actual reading operations.
     *
     * @see #getOpenParameters()
     */
    private final URI location;

    /**
     * The object returned by {@link #getMetadata()}, created when first needed and cached.
     */
    private Metadata metadata;

    /**
     * The data (raster or features) found in the netCDF file. This list is created when first needed.
     *
     * @see #components()
     */
    private List<Resource> components;

    /**
     * Creates a new netCDF store from the given file, URL, stream or {@link ucar.nc2.NetcdfFile} object.
     * This constructor invokes {@link StorageConnector#closeAllExcept(Object)}, keeping open only the
     * needed resource.
     *
     * @param  provider   the factory that created this {@code DataStore} instance, or {@code null} if unspecified.
     * @param  connector  information about the storage (URL, stream, {@link ucar.nc2.NetcdfFile} instance, <i>etc</i>).
     * @throws DataStoreException if an error occurred while opening the netCDF file.
     *
     * @since 0.8
     */
    public NetcdfStore(final NetcdfStoreProvider provider, final StorageConnector connector) throws DataStoreException {
        super(provider, connector);
        location = connector.getStorageAs(URI.class);
        final Path path = connector.getStorageAs(Path.class);
        try {
            decoder = NetcdfStoreProvider.decoder(listeners, connector);
        } catch (IOException | ArithmeticException e) {
            throw new DataStoreException(e);
        }
        if (decoder == null) {
            throw new UnsupportedStorageException(super.getLocale(), NetcdfStoreProvider.NAME,
                    connector.getStorage(), connector.getOption(OptionKey.OPEN_OPTIONS));
        }
        decoder.location = path;
        String id = Strings.trimOrNull(decoder.stringValue(ACDD.id));
        if (id == null) {
            id = decoder.getFilename();
        }
        if (id != null) {
            final NameFactory f = decoder.nameFactory;
            decoder.namespace = f.createNameSpace(f.createLocalName(null, id), null);
        }
        if (getClass() == NetcdfStore.class) {
            listeners.useReadOnlyEvents();
        }
    }

    /**
     * Returns the parameters used to open this netCDF data store.
     * If non-null, the parameters are described by {@link NetcdfStoreProvider#getOpenParameters()} and contains at
     * least a parameter named {@value org.apache.sis.storage.DataStoreProvider#LOCATION} with a {@link URI} value.
     * This method may return {@code null} if the storage input can not be described by a URI
     * (for example a netCDF file reading directly from a {@link java.nio.channels.ReadableByteChannel}).
     *
     * @return parameters used for opening this data store.
     *
     * @since 0.8
     */
    @Override
    public Optional<ParameterValueGroup> getOpenParameters() {
        return Optional.ofNullable(URIDataStore.parameters(provider, location));
    }

    /**
     * Returns the version number of the Climate and Forecast (CF) conventions used in the netCDF file.
     * The use of CF convention is mandated by the OGC 11-165r2 standard
     * (<cite>CF-netCDF3 Data Model Extension standard</cite>).
     *
     * @return CF-convention version, or {@code null} if no information about CF convention has been found.
     * @throws DataStoreException if an error occurred while reading the data.
     *
     * @since 0.8
     */
    public synchronized Version getConventionVersion() throws DataStoreException {
        for (final CharSequence value : CharSequences.split(decoder().stringValue(CDM.CONVENTIONS), ',')) {
            if (CharSequences.regionMatches(value, 0, "CF-", true)) {
                return new Version(value.subSequence(3, value.length()).toString());
            }
        }
        return null;
    }

    /**
     * Returns an identifier constructed from global attributes or the filename of the netCDF file.
     *
     * @return the identifier fetched from global attributes or the filename. May be absent.
     * @throws DataStoreException if an error occurred while fetching the identifier.
     *
     * @since 1.0
     */
    @Override
    public Optional<GenericName> getIdentifier() throws DataStoreException {
        final NameSpace namespace = decoder().namespace;
        return (namespace != null) ? Optional.of(namespace.name()) : Optional.empty();
    }

    /**
     * Returns information about the dataset as a whole. The returned metadata object can contain information
     * such as the spatiotemporal extent of the dataset, contact information about the creator or distributor,
     * data quality, usage constraints and more.
     *
     * @return information about the dataset.
     * @throws DataStoreException if an error occurred while reading the data.
     */
    @Override
    public synchronized Metadata getMetadata() throws DataStoreException {
        if (metadata == null) try {
            final MetadataReader reader = new MetadataReader(decoder());
            metadata = reader.read();
        } catch (IOException | ArithmeticException e) {
            throw new DataStoreException(e);
        }
        return metadata;
    }

    /**
     * Returns netCDF attributes. The meaning of those attributes may vary depending on data provider.
     * The {@linkplain #getMetadata() standard metadata} should be preferred since they allow abstraction of
     * data format details, but those native metadata are sometime useful when an information is not provided
     * by the standard metadata.
     *
     * @return resources information structured in an implementation-specific way.
     * @throws DataStoreException if an error occurred while reading the metadata.
     *
     * @since 1.1
     */
    @Override
    public Optional<TreeTable> getNativeMetadata() throws DataStoreException {
        final DefaultTreeTable table = new DefaultTreeTable(TableColumn.NAME, TableColumn.VALUE);
        final TreeTable.Node root = table.getRoot();
        root.setValue(TableColumn.NAME, NetcdfStoreProvider.NAME);
        decoder().addAttributesTo(root);
        return Optional.of(table);
    }

    /**
     * Returns the resources (features or coverages) in this netCDF store.
     *
     * @return children resources that are components of this netCDF store.
     * @throws DataStoreException if an error occurred while fetching the components.
     *
     * @since 0.8
     */
    @Override
    @SuppressWarnings("ReturnOfCollectionOrArrayField")
    public synchronized Collection<Resource> components() throws DataStoreException {
        if (components == null) try {
            final Decoder decoder = decoder();
            Resource[] resources = decoder.getDiscreteSampling(this);
            final List<Resource> grids = RasterResource.create(decoder, this);
            if (!grids.isEmpty()) {
                grids.addAll(UnmodifiableArrayList.wrap(resources));
                resources = grids.toArray(new Resource[grids.size()]);
            }
            components = UnmodifiableArrayList.wrap(resources);
        } catch (IOException e) {
            throw new DataStoreException(e);
        }
        return components;
    }

    /**
     * Registers a listener to notify when the specified kind of event occurs in this data store.
     * The current implementation of this data store can emit only {@link WarningEvent}s;
     * any listener specified for another kind of events will be ignored.
     */
    @Override
    public <T extends StoreEvent> void addListener(Class<T> eventType, StoreListener<? super T> listener) {
        // If an argument is null, we let the parent class throws (indirectly) NullArgumentException.
        if (listener == null || eventType == null || eventType.isAssignableFrom(WarningEvent.class)) {
            super.addListener(eventType, listener);
        }
    }

    /**
     * Closes this netCDF store and releases any underlying resources.
     *
     * @throws DataStoreException if an error occurred while closing the netCDF file.
     */
    @Override
    public synchronized void close() throws DataStoreException {
        listeners.close();                  // Should never fail.
        final Decoder reader = decoder;
        decoder    = null;
        metadata   = null;
        components = null;
        if (reader != null) try {
            reader.close();
        } catch (IOException e) {
            throw new DataStoreException(e);
        }
    }

    /**
     * Returns the decoder if it has not been closed.
     *
     * @see #close()
     */
    private Decoder decoder() throws DataStoreClosedException {
        final Decoder reader = decoder;
        if (reader == null) {
            throw new DataStoreClosedException(getLocale(), NetcdfStoreProvider.NAME, StandardOpenOption.READ);
        }
        return reader;
    }

    /**
     * Returns a string representation of this netCDF store for debugging purpose.
     * The content of the string returned by this method may change in any future SIS version.
     *
     * @return a string representation of this data store for debugging purpose.
     */
    @Override
    public String toString() {
        return Strings.bracket(getClass(), decoder);
    }
}
