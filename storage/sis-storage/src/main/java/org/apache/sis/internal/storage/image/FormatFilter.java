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

import java.util.Map;
import java.util.Set;
import java.util.Iterator;
import java.util.function.Function;
import java.net.URI;
import java.net.URL;
import java.io.File;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.awt.image.RenderedImage;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.spi.ImageReaderWriterSpi;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.apache.sis.storage.StorageConnector;
import org.apache.sis.storage.DataStoreException;
import org.apache.sis.util.ArraysExt;
import org.apache.sis.util.Classes;


/**
 * Specify the property to use as a filtering criterion for choosing an image reader or writer.
 * This is used for providing utility methods about image formats.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.2
 * @since   1.2
 * @module
 */
enum FormatFilter {
    /**
     * Filter the providers by format name.
     */
    NAME(ImageReaderWriterSpi::getFormatNames),

    /**
     * Filter the providers by file extension.
     */
    SUFFIX(ImageReaderWriterSpi::getFileSuffixes),

    /**
     * Filter the providers by MIME type.
     */
    MIME(ImageReaderWriterSpi::getMIMETypes);

    /**
     * The method to invoke for getting the property values
     * (name, suffix or MIME type) to use for filtering.
     */
    private final Function<ImageReaderWriterSpi, String[]> property;

    /**
     * Valid types of inputs accepted by this class.
     */
    private static final Class<?>[] VALID_INPUTS = {
        // ImageInputStream case included by DataInput.
        DataInput.class, InputStream.class, File.class, Path.class, URL.class, URI.class
    };

    /**
     * Valid types of outputs accepted by this class.
     */
    private static final Class<?>[] VALID_OUTPUTS = {
        // ImageOutputStream case included by DataOutput.
        DataOutput.class, OutputStream.class, File.class, Path.class, URL.class, URI.class
    };

    /**
     * Creates a new enumeration value.
     */
    private FormatFilter(final Function<ImageReaderWriterSpi, String[]> property) {
        this.property = property;
    }

    /**
     * Returns an iterator over all providers of the given category having the given name,
     * suffix or MIME type.
     *
     * @param  <T>         the compile-time type of the {@code category} argument.
     * @param  category    either {@link ImageReaderSpi} or {@link ImageWriterSpi}.
     * @param  identifier  the property value to use as a filtering criterion, or {@code null} if none.
     * @return an iterator over the requested providers.
     */
    private <T extends ImageReaderWriterSpi> Iterator<T> getServiceProviders(final Class<T> category, final String identifier) {
        final IIORegistry registry = IIORegistry.getDefaultInstance();
        if (identifier != null) {
            final IIORegistry.Filter filter = (provider) -> {
                final String[] identifiers = property.apply((ImageReaderWriterSpi) provider);
                return ArraysExt.contains(identifiers, identifier);
            };
            return registry.getServiceProviders(category, filter, true);
        } else {
            return registry.getServiceProviders(category, true);
        }
    }

    /**
     * Finds a provider for the given input, or returns {@code null} if none.
     *
     * @param  identifier  the property value to use as a filtering criterion, or {@code null} if none.
     * @param  connector   provider of the input to be given to the {@code canDecodeInput(…)} method.
     * @param  done        initially empty set to be populated with providers tested by this method.
     * @return the provider for the given input, or {@code null} if none was found.
     * @throws DataStoreException if an error occurred while opening a stream from the storage connector.
     * @throws IOException if an error occurred while creating the image reader instance.
     */
    final ImageReaderSpi findProvider(final String identifier, final StorageConnector connector, final Set<ImageReaderSpi> done)
            throws IOException, DataStoreException
    {
        final Iterator<ImageReaderSpi> it = FormatFilter.SUFFIX.getServiceProviders(ImageReaderSpi.class, identifier);
        while (it.hasNext()) {
            final ImageReaderSpi provider = it.next();
            if (done.add(provider)) {
                for (final Class<?> type : provider.getInputTypes()) {
                    if (Classes.isAssignableToAny(type, VALID_INPUTS)) {
                        final Object input = connector.getStorageAs(type);
                        if (input != null) {
                            /*
                             * We do not try to mark/reset the input because it should be done
                             * by `canDecodeInput(…)` as per Image I/O contract. Doing our own
                             * mark/reset may interfere with the `canDecodeInput(…)` marks.
                             */
                            if (provider.canDecodeInput(input)) {
                                return provider;
                            }
                            break;          // Skip other input types, try the next provider.
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Creates a new reader for the given input. Caller needs to invoke this method with an initially empty
     * {@code deferred} map, which will be populated by this method. Providers associated to {@code TRUE}
     * should be tested again by the caller with an {@link ImageInputStream} created by the caller.
     * This is intentionally not done automatically by {@link StorageConnector}.
     *
     * @param  identifier  the property value to use as a filtering criterion, or {@code null} if none.
     * @param  connector   provider of the input to be given to the new reader instance.
     * @param  deferred    initially empty map to be populated with providers tested by this method.
     * @return the new image reader instance with its input initialized, or {@code null} if none was found.
     * @throws DataStoreException if an error occurred while opening a stream from the storage connector.
     * @throws IOException if an error occurred while creating the image reader instance.
     */
    final ImageReader createReader(final String identifier, final StorageConnector connector,
                                   final Map<ImageReaderSpi,Boolean> deferred) throws IOException, DataStoreException
    {
        final Iterator<ImageReaderSpi> it = getServiceProviders(ImageReaderSpi.class, identifier);
        while (it.hasNext()) {
            final ImageReaderSpi provider = it.next();
            if (deferred.putIfAbsent(provider, Boolean.FALSE) == null) {
                for (final Class<?> type : provider.getInputTypes()) {
                    if (Classes.isAssignableToAny(type, VALID_INPUTS)) {
                        final Object input = connector.getStorageAs(type);
                        if (input != null) {
                            if (provider.canDecodeInput(input)) {
                                connector.closeAllExcept(input);
                                final ImageReader reader = provider.createReaderInstance();
                                reader.setInput(input, false, true);
                                return reader;
                            }
                            break;        // Skip other input types, try the next provider.
                        } else if (type == ImageInputStream.class) {
                            deferred.put(provider, Boolean.TRUE);
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Creates a new writer for the given output. Caller needs to invoke this method with an initially empty
     * {@code deferred} map, which will be populated by this method. Providers associated to {@code TRUE}
     * should be tested again by the caller with an {@link ImageOutputStream} created by the caller.
     * This is intentionally not done automatically by {@link StorageConnector}.
     *
     * @param  identifier  the property value to use as a filtering criterion, or {@code null} if none.
     * @param  output      the output to be given to the new reader instance.
     * @param  image       the image to write.
     * @param  deferred    initially empty map to be populated with providers tested by this method.
     * @return the new image writer instance with its output initialized, or {@code null} if none was found.
     * @throws DataStoreException if an error occurred while opening a stream from the storage connector.
     * @throws IOException if an error occurred while creating the image writer instance.
     */
    final ImageWriter createWriter(final String identifier, final StorageConnector connector, final RenderedImage image,
                                   final Map<ImageWriterSpi,Boolean> deferred) throws IOException, DataStoreException
    {
        final Iterator<ImageWriterSpi> it = getServiceProviders(ImageWriterSpi.class, identifier);
        while (it.hasNext()) {
            final ImageWriterSpi provider = it.next();
            if (deferred.putIfAbsent(provider, Boolean.FALSE) == null) {
                if (provider.canEncodeImage(image)) {
                    for (final Class<?> type : provider.getOutputTypes()) {
                        if (Classes.isAssignableToAny(type, VALID_OUTPUTS)) {
                            final Object output = connector.getStorageAs(type);
                            if (output != null) {
                                connector.closeAllExcept(output);
                                final ImageWriter writer = provider.createWriterInstance();
                                writer.setOutput(output);
                                return writer;
                            } else if (type == ImageOutputStream.class) {
                                deferred.put(provider, Boolean.TRUE);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
}
