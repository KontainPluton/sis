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
package org.apache.sis.storage;

import org.apache.sis.internal.storage.MetadataBuilder;
import org.apache.sis.storage.event.StoreListeners;
import org.opengis.feature.FeatureType;
import org.opengis.metadata.Metadata;
import org.opengis.util.GenericName;

import java.util.Optional;
import java.util.OptionalLong;


/**
 * Default implementations of several methods for classes that want to implement the {@link FeatureSet} interface.
 * Subclasses should override the following methods:
 *
 * <ul>
 *   <li>{@link #getType()} (mandatory)</li>
 *   <li>{@link #features(boolean parallel)} (mandatory)</li>
 *   <li>{@link #getFeatureCount()} (recommended)</li>
 *   <li>{@link #getEnvelope()} (recommended)</li>
 *   <li>{@link #createMetadata()} (optional)</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * Default methods of this abstract class are thread-safe.
 * Synchronization, when needed, uses {@link #getSynchronizationLock()}.
 *
 * @author  Johann Sorel (Geomatys)
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.2
 * @since   1.2
 * @module
 */
public abstract class AbstractFeatureSet extends AbstractResource implements FeatureSet {
    /**
     * Creates a new resource which can send notifications to the given set of listeners.
     * If {@code hidden} is {@code false} (the recommended value), then this resource will have its own set of
     * listeners with this resource declared as the {@linkplain StoreListeners#getSource() source of events}.
     * It will be possible to add and remove listeners independently from the set of parent listeners.
     * Conversely if {@code hidden} is {@code true}, then the given listeners will be used directly
     * and this resource will not appear as the source of any event.
     *
     * <p>In any cases, the listeners of all parents (ultimately the data store that created this resource)
     * will always be notified, either directly if {@code hidden} is {@code true}
     * or indirectly if {@code hidden} is {@code false}.</p>
     *
     * @param  parentListeners  listeners of the parent resource, or {@code null} if none.
     *         This is usually the listeners of the {@link DataStore} that created this resource.
     * @param  hidden  {@code false} if this resource shall use its own {@link StoreListeners}
     *         with the specified parent, or {@code true} for using {@code parentListeners} directly.
     */
    protected AbstractFeatureSet(final StoreListeners parentListeners, final boolean hidden) {
        super(parentListeners, hidden);
    }

    /**
     * Returns the feature type name as the identifier for this resource.
     * Subclasses should override if they can provide a more specific identifier.
     *
     * @return the resource identifier inferred from feature type.
     * @throws DataStoreException if an error occurred while fetching the identifier.
     *
     * @see DataStore#getIdentifier()
     */
    @Override
    public Optional<GenericName> getIdentifier() throws DataStoreException {
        final FeatureType type = getType();
        return (type != null) ? Optional.of(type.getName()) : Optional.empty();
    }

    /**
     * Returns an estimation of the number of features in this set, or empty if unknown.
     * The default implementation returns an empty value.
     *
     * @return estimation of the number of features.
     */
    public OptionalLong getFeatureCount() {
        return OptionalLong.empty();
    }

    /**
     * Invoked in a synchronized block the first time that {@code getMetadata()} is invoked.
     * The default implementation populates metadata based on information provided by
     * {@link #getIdentifier()   getIdentifier()},
     * {@link #getEnvelope()     getEnvelope()},
     * {@link #getType()         getType()} and
     * {@link #getFeatureCount() getFeatureCount()}.
     * Subclasses should override if they can provide more information.
     * The default value can be completed by casting to {@link org.apache.sis.metadata.iso.DefaultMetadata}.
     *
     * @return the newly created metadata, or {@code null} if unknown.
     * @throws DataStoreException if an error occurred while reading metadata from this resource.
     */
    @Override
    protected Metadata createMetadata() throws DataStoreException {
        final MetadataBuilder builder = new MetadataBuilder();
        builder.addDefaultMetadata(this, listeners);
        return builder.build();
    }
}
