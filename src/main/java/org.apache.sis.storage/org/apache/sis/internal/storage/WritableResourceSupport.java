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
package org.apache.sis.internal.storage;

import org.apache.sis.coverage.grid.GridCoverage;
import org.apache.sis.coverage.grid.GridExtent;
import org.apache.sis.internal.coverage.CoverageCombiner;
import org.apache.sis.internal.storage.io.ChannelDataInput;
import org.apache.sis.internal.storage.io.ChannelDataOutput;
import org.apache.sis.referencing.operation.matrix.AffineTransforms2D;
import org.apache.sis.referencing.operation.transform.TransformSeparator;
import org.apache.sis.storage.*;
import org.apache.sis.util.ArgumentChecks;
import org.apache.sis.util.Classes;
import org.apache.sis.util.Localized;
import org.apache.sis.util.resources.Errors;
import org.opengis.coverage.CannotEvaluateException;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.opengis.util.FactoryException;

import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.util.Locale;


/**
 * Helper classes for the management of {@link WritableGridCoverageResource.CommonOption}.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.2
 * @since   1.2
 * @module
 */
public final class WritableResourceSupport implements Localized {
    /**
     * The resource where to write.
     */
    private final GridCoverageResource resource;

    /**
     * {@code true} if the {@link WritableGridCoverageResource.CommonOption.REPLACE} option has been specified.
     * At most one of {@code replace} and {@link #update} can be {@code true}.
     */
    private boolean replace;

    /**
     * {@code true} if the {@link WritableGridCoverageResource.CommonOption.UPDATE} option has been specified.
     * At most one of {@link #replace} and {@code update} can be {@code true}.
     */
    private boolean update;

    /**
     * Creates a new helper class for the given options.
     *
     * @param  resource  the resource where to write.
     * @param  options   configuration of the write operation.
     */
    public WritableResourceSupport(final GridCoverageResource resource, final WritableGridCoverageResource.Option[] options) {
        this.resource = resource;
        ArgumentChecks.ensureNonNull("options", options);
        for (final WritableGridCoverageResource.Option option : options) {
            replace |= WritableGridCoverageResource.CommonOption.REPLACE.equals(option);
            update  |= WritableGridCoverageResource.CommonOption.UPDATE .equals(option);
        }
        if (replace & update) {
            throw new IllegalArgumentException(Errors.format(Errors.Keys.MutuallyExclusiveOptions_2,
                    WritableGridCoverageResource.CommonOption.REPLACE,
                    WritableGridCoverageResource.CommonOption.UPDATE));
        }
    }

    /**
     * Returns the locale used by the resource for error messages, or {@code null} if unknown.
     *
     * @return the locale used by the resource for error messages, or {@code null} if unknown.
     */
    @Override
    public final Locale getLocale() {
        return (resource instanceof Localized) ? ((Localized) resource).getLocale() : null;
    }

    /**
     * Returns the writable channel positioned at the beginning of the stream.
     * The returned channel should <em>not</em> be closed
     * because it is the same channel than the one used by {@code input}.
     * Caller should invoke {@link ChannelDataOutput#flush()} after usage.
     *
     * @param  input  the input from which to get the writable channel.
     * @return the writable channel.
     * @throws IOException if the stream position can not be reset.
     * @throws DataStoreException if the channel is read-only.
     */
    public final ChannelDataOutput channel(final ChannelDataInput input) throws IOException, DataStoreException {
        if (input.channel instanceof WritableByteChannel && input.rewind()) {
            return new ChannelDataOutput(input.filename, (WritableByteChannel) input.channel, input.buffer);
        } else {
            throw new ReadOnlyStorageException(canNotWrite());
        }
    }

    /**
     * Returns {@code true} if the caller should add or replace the resource
     * or {@code false} if it needs to update an existing resource.
     * Current heuristic:
     *
     * <ul>
     *   <li>If the given channel is empty, then this method always returns {@code true}.</li>
     *   <li>Otherwise this method returns {@code true} if the {@code REPLACE} option was specified,
     *       or returns {@code false} if the {@code UPDATE} option was specified,
     *       or thrown a {@link ResourceAlreadyExistsException} otherwise.</li>
     * </ul>
     *
     * @param  input  the channel to test for emptiness, or {@code null} if unknown.
     * @return whether the caller should replace ({@code true}) or update ({@code false}) the resource.
     * @throws IOException if an error occurred while checking the channel length.
     * @throws ResourceAlreadyExistsException if the resource exists and the writer
     *         should neither updating or replacing it.
     * @throws DataStoreException if another kind of error occurred with the resource.
     */
    public final boolean replace(final ChannelDataInput input) throws IOException, DataStoreException {
        if (update) {
            return isEmpty(input);
        } else if (replace || isEmpty(input)) {
            return true;
        } else {
            Object identifier = resource.getIdentifier().orElse(null);
            if (identifier == null && input != null) identifier = input.filename;
            throw new ResourceAlreadyExistsException(Resources.forLocale(getLocale())
                    .getString(Resources.Keys.ResourceAlreadyExists_1, identifier));
        }
    }

    /**
     * Returns {@code true} if the given channel is empty.
     * In case of doubt, this method conservatively returns {@code false}.
     *
     * @param  input  the channel to test for emptiness, or {@code null} if unknown.
     * @return {@code true} if the channel is empty, or {@code false} if not or if unknown.
     */
    private static boolean isEmpty(final ChannelDataInput input) throws IOException {
        return (input != null) && input.length() == 0;
    }

    /**
     * Reads the current coverage in the resource and updates its content with cell values from the given coverage.
     * This method can be used as a simple implementation of {@link WritableGridCoverageResource.CommonOption#UPDATE}.
     * This method returns the updated coverage; it is caller responsibility to write it.
     *
     * <p>This method can be used when updating the coverage requires to read it fully, then write if fully.
     * Advanced writers should try to update only the modified parts (typically some tiles) instead.</p>
     *
     * @param  coverage  the coverage to use for updating the currently existing coverage.
     * @return the updated coverage that the caller should write.
     * @throws DataStoreException if an error occurred while reading or updating the coverage.
     */
    public final GridCoverage update(final GridCoverage coverage) throws DataStoreException {
        final GridCoverage existing = resource.read(null, null);
        final CoverageCombiner combiner = new CoverageCombiner(existing, 0, 1);
        try {
            if (!combiner.apply(coverage)) {
                throw new ReadOnlyStorageException(canNotWrite());
            }
        } catch (TransformException e) {
            throw new DataStoreReferencingException(canNotWrite(), e);
        }
        return existing;
    }

    /**
     * Returns the "grid to CRS" transform as a two-dimensional affine transform.
     * This is a convenience method for writers that support only this kind of transform.
     *
     * @param  extent     the extent of the grid coverage to write.
     * @param  gridToCRS  the "grid to CRS" transform of the coverage to write.
     * @return the given "grid to CRS" as a two-dimensional affine transform.
     * @throws DataStoreException if the affine transform can not be extracted from the given "grid to CRS" transform.
     */
    public final AffineTransform getAffineTransform2D(final GridExtent extent, final MathTransform gridToCRS)
            throws DataStoreException
    {
        final TransformSeparator s = new TransformSeparator(gridToCRS);
        try {
            s.addSourceDimensions(extent.getSubspaceDimensions(2));
            return AffineTransforms2D.castOrCopy(s.separate());
        } catch (FactoryException | CannotEvaluateException e) {
            throw new DataStoreReferencingException(canNotWrite(), e);
        } catch (IllegalArgumentException e) {
            throw new IncompatibleResourceException(canNotWrite(), e);
        }
    }

    /**
     * Returns the message for an exception saying that we can not write the resource.
     *
     * @return a localized "Can not write resource" message.
     * @throws DataStoreException if an error occurred while preparing the error message.
     */
    public final String canNotWrite() throws DataStoreException {
        Object identifier = resource.getIdentifier().orElse(null);
        if (identifier == null) identifier = Classes.getShortClassName(resource);
        return Resources.forLocale(getLocale()).getString(Resources.Keys.CanNotWriteResource_1, identifier);
    }

    /**
     * Returns the message for an exception saying that rotations are not supported.
     *
     * @param  format  name of the format that does not support rotations.
     * @return a localized "rotation not supported" message.
     */
    public final String rotationNotSupported(final String format) {
        return Resources.forLocale(getLocale()).getString(Resources.Keys.RotationNotSupported_1, format);
    }
}
