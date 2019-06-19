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

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import org.opengis.util.GenericName;
import org.opengis.geometry.Envelope;
import org.opengis.metadata.maintenance.ScopeCode;
import org.opengis.referencing.operation.TransformException;
import org.apache.sis.geometry.ImmutableEnvelope;
import org.apache.sis.geometry.Envelopes;
import org.apache.sis.storage.FeatureSet;
import org.apache.sis.storage.DataStore;
import org.apache.sis.storage.DataStoreException;
import org.apache.sis.util.logging.WarningListeners;

// Branch-dependent imports
import org.opengis.feature.FeatureType;


/**
 * A feature set made from the aggregation of other feature sets. The features may be aggregated in different ways,
 * depending on the subclass. The aggregation may be all features from one set followed by all features from another set,
 * or it may be features of the two sets merged together in a way similar to SQL JOIN statement.
 *
 * <p>This class provides default implementations of {@link #getEnvelope()} and {@link #getMetadata()}.
 * Subclasses need to implement {@link #dependencies()}.</p>
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.0
 * @since   1.0
 * @module
 */
abstract class AggregatedFeatureSet extends AbstractFeatureSet {
    /**
     * The envelope, computed when first needed and cached for reuse.
     *
     * @see #getEnvelope()
     */
    private ImmutableEnvelope envelope;

    /**
     * Whether {@link #envelope} has been computed. The result may still be null.
     */
    private boolean isEnvelopeComputed;

    /**
     * Creates a new aggregated feature set.
     *
     * @param  listeners  the set of registered warning listeners for the data store, or {@code null} if none.
     */
    protected AggregatedFeatureSet(final WarningListeners<DataStore> listeners) {
        super(listeners);
        /*
         * TODO: we should add listeners on source feature sets. By doing this,
         *       we could be notified of changes and invoke clearCache().
         */
    }

    /**
     * Creates a new feature set with the same warning listeners than the given resource,
     * or with {@code null} listeners if they are unknown.
     *
     * @param resource  the resources from which to get the listeners, or {@code null} if none.
     */
    protected AggregatedFeatureSet(final FeatureSet resource) {
        super(resource);
    }

    /**
     * Returns all feature set used by this aggregation. This method is invoked for implementation of
     * {@link #getEnvelope()} and {@link #createMetadata(MetadataBuilder)}.
     *
     * @return all feature sets in this aggregation.
     */
    abstract Collection<FeatureSet> dependencies();

    /**
     * Returns {@code null} since this resource is a computation result.
     */
    @Override
    public GenericName getIdentifier() {
        return null;
    }

    /**
     * Adds the envelopes of the aggregated feature sets in the given list. If some of the feature sets
     * are themselves aggregated feature sets, then this method traverses them recursively. We compute
     * the union of all envelopes at once after we got all envelopes.
     *
     * <p>If any source returns {@code null}, then this method stops the collect immediately and returns {@code false}.
     * The rational is that if at least one source has unknown location, providing a location based on other sources
     * may be misleading since they may be very far from the missing resource location.</p>
     *
     * @return {@code false} if the collect has been interrupted because a source returned a {@code null} envelope.
     */
    private boolean getEnvelopes(final List<Envelope> addTo) throws DataStoreException {
        for (final FeatureSet fs : dependencies()) {
            if (fs instanceof AggregatedFeatureSet) {
                if (!((AggregatedFeatureSet) fs).getEnvelopes(addTo)) {
                    return false;
                }
            } else {
                final Envelope e = fs.getEnvelope();
                if (e == null) return false;
                addTo.add(e);
            }
        }
        return true;
    }

    /**
     * Returns the union of the envelope in all aggregated feature set, or {@code null} if none.
     * This method tries to find a CRS common to all feature sets. If no common CRS can be found,
     * then this method returns {@code null}.
     *
     * <div class="note"><b>Implementation note:</b>
     * the envelope is recomputed every time this method is invoked. The result is not cached because
     * the envelope of {@code FeatureSet} sources may change between invocations of this method.
     * The cost should not be excessive if the sources cache themselves their envelopes.</div>
     *
     * @return union of envelopes of both sides, or {@code null}.
     * @throws DataStoreException if an error occurred while computing the envelope.
     */
    @Override
    public synchronized Envelope getEnvelope() throws DataStoreException {
        if (!isEnvelopeComputed) {
            final List<Envelope> envelopes = new ArrayList<>();
            if (getEnvelopes(envelopes)) try {
                envelope = ImmutableEnvelope.castOrCopy(Envelopes.union(envelopes.toArray(new Envelope[envelopes.size()])));
            } catch (TransformException e) {
                warning(e);
            }
            isEnvelopeComputed = true;
        }
        return envelope;
    }

    /**
     * Invoked the first time that {@link #getMetadata()} is invoked. The default implementation adds
     * the information documented in {@link AbstractFeatureSet#createMetadata(MetadataBuilder)}, then
     * adds the dependencies as lineages.
     *
     * @param  metadata  the builder where to set metadata properties.
     * @throws DataStoreException if an error occurred while reading metadata from the data stores.
     */
    @Override
    protected void createMetadata(final MetadataBuilder metadata) throws DataStoreException {
        super.createMetadata(metadata);
        for (final FeatureSet fs : dependencies()) {
            final FeatureType type = fs.getType();
            metadata.addSource(fs.getMetadata(), ScopeCode.FEATURE_TYPE,
                    (type == null) ? null : new CharSequence[] {type.getName().toInternationalString()});
        }
    }

    /**
     * Clears any cache in this resource, forcing the data to be recomputed when needed again.
     * This method should be invoked if the data in underlying data store changed.
     */
    @Override
    protected synchronized void clearCache() {
        isEnvelopeComputed = false;
        envelope = null;
        super.clearCache();
    }
}