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

import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import org.opengis.util.GenericName;
import org.apache.sis.storage.Resource;
import org.apache.sis.storage.Aggregate;
import org.apache.sis.storage.DataStoreException;
import org.apache.sis.storage.event.StoreListeners;
import org.apache.sis.internal.storage.AbstractResource;
import org.apache.sis.internal.storage.MetadataBuilder;
import org.apache.sis.internal.util.UnmodifiableArrayList;
import org.apache.sis.util.ArraysExt;


/**
 * An aggregate of {@link Band}.
 * Each aggregate is for one {@link BandGroupName}.
 *
 * @todo Future implementation should implement {@code GridCoverageResource}
 *       and provides an aggregated coverage view where each Landsat band is
 *       a sample dimension.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.1
 * @since   1.1
 * @module
 */
final class BandGroup extends AbstractResource implements Aggregate {
    /**
     * The group of bands that this aggregate represents.
     */
    final BandGroupName group;

    /**
     * Name of the band group.
     * This is set by {@link LandsatStore} and should not be modified after that point.
     */
    GenericName identifier;

    /**
     * The array of images for each Landsat band.
     */
    private final Band[] components;

    /**
     * Creates a new aggregate for the specified group.
     * This constructor will copy only the resources for that group from the given array.
     */
    private BandGroup(final StoreListeners parent, final BandGroupName group,
                             final Band[] resources, final int count)
    {
        super(parent);
        this.group = group;
        int n = 0;
        Band[] components = new Band[resources.length];
        for (int i=0; i<count; i++) {
            final Band r = resources[i];
            if (r.band.group == group) {
                components[n++] = r;
            }
        }
        this.components = ArraysExt.resize(components, n);
    }

    /**
     * Creates aggregates for the given bands.
     */
    static BandGroup[] group(final StoreListeners parent, final Band[] resources, final int count) {
        final BandGroupName[] groups = BandGroupName.values();
        final BandGroup[] aggregates = new BandGroup[groups.length];
        int n = 0;
        for (final BandGroupName group : groups) {
            final BandGroup c = new BandGroup(parent, group, resources, count);
            if (c.components.length != 0) {
                aggregates[n++] = c;
            }
        }
        return ArraysExt.resize(aggregates, n);
    }

    /**
     * Returns the resource persistent identifier if available.
     */
    @Override
    public Optional<GenericName> getIdentifier() {
        return Optional.of(identifier);
    }

    /**
     * Invoked in a synchronized block the first time that {@link #getMetadata()} is invoked.
     */
    @Override
    protected void createMetadata(final MetadataBuilder metadata) throws DataStoreException {
        metadata.addTitle(group.title);     // Must be before `super.createMetadata(…)`.
        super.createMetadata(metadata);
    }

    /**
     * Returns the resources for each Landsat band of this group.
     */
    @Override
    public Collection<Resource> components() {
        return UnmodifiableArrayList.wrap(components);
    }

    /**
     * Returns all bands in the given array of aggregates.
     */
    static final List<Band> bands(final BandGroup[] components) {
        final List<Band> bands = new ArrayList<>();
        if (components != null) {
            for (final BandGroup c : components) {
                bands.addAll(Arrays.asList(c.components));
            }
        }
        return bands;
    }
}