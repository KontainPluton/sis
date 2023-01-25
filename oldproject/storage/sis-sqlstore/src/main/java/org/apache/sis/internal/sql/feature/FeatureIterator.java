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
package org.apache.sis.internal.sql.feature;

import java.util.List;
import java.util.ArrayList;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.sis.internal.metadata.sql.SQLBuilder;
import org.apache.sis.storage.InternalDataStoreException;
import org.apache.sis.util.collection.BackingStoreException;
import org.apache.sis.util.collection.WeakValueHashMap;

// Branch-dependent imports
import org.opengis.feature.Feature;
import org.opengis.filter.SortOrder;
import org.opengis.filter.SortProperty;
import org.opengis.filter.SortBy;


/**
 * Iterator over feature instances.
 * This iterator converters {@link ResultSet} rows to {@link Feature} instances.
 * Each {@code FeatureIterator} iterator is created for one specific SQL query
 * and can be used for only one iteration.
 *
 * <h2>Parallelism</h2>
 * Current implementation of {@code FeatureIterator} does not support parallelism.
 * This iterator is not thread-safe and the {@link #trySplit()} method always returns {@code null}.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @author  Alexis Manin (Geomatys)
 * @version 1.2
 * @since   1.0
 * @module
 */
final class FeatureIterator implements Spliterator<Feature>, AutoCloseable {
    /**
     * Characteristics of the iterator. The value returned by {@link #characteristics()}
     * must be consistent with the value given to {@code DeferredStream} constructor.
     *
     * @see #characteristics()
     */
    static final int CHARACTERISTICS = NONNULL;

    /**
     * The converter from a {@link ResultSet} row to a {@link Feature} instance.
     */
    private final FeatureAdapter adapter;

    /**
     * If this iterator returns only the features matching some condition (typically a primary key value),
     * the statement for performing that filtering. Otherwise if this iterator returns all features, then
     * this field is {@code null}.
     */
    private final PreparedStatement statement;

    /**
     * The result of executing the SQL query for a {@link Table}. If {@link #statement} is null, then
     * a single {@code ResultSet} is used for all the lifetime of this {@code FeatureIterator} instance.
     * Otherwise an arbitrary amount of {@code ResultSet}s may be created from the statement.
     */
    private ResultSet result;

    /**
     * Estimated number of remaining rows, or ≤ 0 if unknown.
     */
    private final long estimatedSize;

    /**
     * A cache of statements for fetching spatial information such as geometry columns or SRID.
     * This is non-null only if the {@linkplain Database#isSpatial() database is spatial}.
     * The same instance is shared by all dependencies of this {@code FeatureIterator}.
     */
    private final InfoStatements spatialInformation;

    /**
     * The feature sets referenced through foreigner keys, or an empty array if none.
     * This includes the associations inferred from both the imported and exported keys.
     * The first {@link FeatureAdapter#importCount} iterators are for imported keys,
     * and the remaining iterators are for the exported keys.
     *
     * <p>All elements in this array are initially null. Iterators are created when first needed.
     * They may be never created because those features may be in the cache.</p>
     */
    private final FeatureIterator[] dependencies;

    /**
     * Creates a new iterator over features.
     *
     * @param table       the source table.
     * @param connection  connection to the database, used for creating the statement.
     * @param distinct    whether the set should contain distinct feature instances.
     * @param filter      condition to append, not including the {@code WHERE} keyword.
     * @param sort        the {@code ORDER BY} clauses, or {@code null} if none.
     * @param offset      number of rows to skip in underlying SQL query, or ≤ 0 for none.
     * @param count       maximum number of rows to return, or ≤ 0 for no limit.
     */
    FeatureIterator(final Table table, final Connection connection,
             final boolean distinct, final String filter, final SortBy<? super Feature> sort,
             final long offset, final long count)
            throws SQLException, InternalDataStoreException
    {
        adapter = table.adapter(connection);
        spatialInformation = table.database.isSpatial() ? table.database.createInfoStatements(connection) : null;
        String sql = adapter.sql;
        if (distinct || filter != null || sort != null || offset > 0 || count > 0) {
            final SQLBuilder builder = new SQLBuilder(table.database).append(sql);
            if (distinct) {
                builder.insertDistinctAfterSelect();
            }
            if (filter != null) {
                builder.append(" WHERE ").append(filter);
            }
            if (sort != null) {
                String separator = " ORDER BY ";
                for (final SortProperty<? super Feature> s : sort.getSortProperties()) {
                    builder.append(separator).appendIdentifier(s.getValueReference().getXPath());
                    final SortOrder order = s.getSortOrder();
                    if (order != null) {
                        builder.append(' ').append(order.toSQL());
                    }
                    separator = ", ";
                }
            }
            sql = builder.appendFetchPage(offset, count).toString();
        }
        result = connection.createStatement().executeQuery(sql);
        dependencies = new FeatureIterator[adapter.dependencies.length];
        statement = null;
        if (filter == null) {
            estimatedSize = Math.min(table.countRows(connection.getMetaData(), distinct, true), offset + count) - offset;
        } else {
            estimatedSize = 0;              // Can not estimate the size if there is filtering conditions.
        }
    }

    /**
     * Creates a new iterator over the dependencies of a feature.
     *
     * @param table       the source table, or {@code null} if we are creating an iterator for a dependency.
     * @param adapter     converter from a {@link ResultSet} row to a {@link Feature} instance.
     * @param connection  connection to the database, used for creating statement.
     * @param filter      condition to append, not including the {@code WHERE} keyword.
     * @param distinct    whether the set should contain distinct feature instances.
     * @param offset      number of rows to skip in underlying SQL query, or ≤ 0 for none.
     * @param count       maximum number of rows to return, or ≤ 0 for no limit.
     */
    private FeatureIterator(final FeatureAdapter adapter, final Connection connection,
                            final InfoStatements spatialInformation) throws SQLException
    {
        this.spatialInformation = spatialInformation;
        this.adapter  = adapter;
        statement     = connection.prepareStatement(adapter.sql);
        dependencies  = new FeatureIterator[adapter.dependencies.length];
        estimatedSize = 0;
    }

    /**
     * Returns the dependency at the given index, creating it when first needed.
     */
    private FeatureIterator dependency(final int i) throws SQLException {
        FeatureIterator dependency = dependencies[i];
        if (dependency == null) {
            dependency = new FeatureIterator(adapter.dependencies[i], result.getStatement().getConnection(), spatialInformation);
            dependencies[i] = dependency;
        }
        return dependency;
    }

    /**
     * Declares that this iterator never returns {@code null} elements.
     */
    @Override
    public int characteristics() {
        return CHARACTERISTICS;
    }

    /**
     * Returns the estimated number of remaining features, or {@link Long#MAX_VALUE} if unknown.
     */
    @Override
    public long estimateSize() {
        return (estimatedSize > 0) ? estimatedSize : Long.MAX_VALUE;
    }

    /**
     * Current version does not support split.
     *
     * @return always {@code null}.
     */
    @Override
    public Spliterator<Feature> trySplit() {
        return null;
    }

    /**
     * Gives the next feature to the given consumer.
     */
    @Override
    public boolean tryAdvance(final Consumer<? super Feature> action) {
        try {
            return fetch(action, false);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new BackingStoreException(e);
        }
    }

    /**
     * Gives all remaining features to the given consumer.
     */
    @Override
    public void forEachRemaining(final Consumer<? super Feature> action) {
        try {
            fetch(action, true);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new BackingStoreException(e);
        }
    }

    /**
     * Gives at least the next feature to the given consumer.
     * Gives all remaining features if {@code all} is {@code true}.
     *
     * @param  action  the action to execute for each {@link Feature} instances fetched by this method.
     * @param  all     {@code true} for reading all remaining feature instances, or {@code false} for only the next one.
     * @return {@code true} if we have read an instance and {@code all} is {@code false} (so there is maybe other instances).
     */
    private boolean fetch(final Consumer<? super Feature> action, final boolean all) throws Exception {
        while (result.next()) {
            final Feature feature = adapter.createFeature(spatialInformation, result);
            for (int i=0; i < dependencies.length; i++) {
                WeakValueHashMap<?,Object> instances = null;
                Object key = null, value = null;
                if (i < adapter.importCount) {
                    /*
                     * Check in the cache only for `Relation.Direction.IMPORT`
                     * (when this table references another table).
                     *
                     * We do not cache dependencies for `Relation.Direction.EXPORT`
                     * (when another table references this table) because that direction can return
                     * a lot of instances, contrarily to `IMPORT` which returns only one instance.
                     * Furthermore instances fetched from `Direction.EXPORT` can not be
                     * shared by feature instances, so caching would be useless here.
                     */
                    key = adapter.getCacheKey(result, i);
                    if (key == null) {
                        continue;
                    }
                    instances = adapter.dependencies[i].instances;
                    value = instances.get(key);
                }
                if (value == null) {
                    final FeatureIterator dependency = dependency(i);
                    adapter.setForeignerKeys(result, dependency.statement, i);
                    value = dependency.fetchReferenced(feature);
                }
                if (instances != null) {
                    @SuppressWarnings("unchecked")         // Check is performed by putIfAbsent(…).
                    final Object previous = ((WeakValueHashMap) instances).putIfAbsent(key, value);
                    if (previous != null) value = previous;
                }
                feature.setPropertyValue(adapter.associationNames[i], value);
            }
            action.accept(feature);
            if (!all) return true;
        }
        return false;
    }

    /**
     * Executes the current {@link #statement} and stores all features in a list.
     * Returns {@code null} if there are no features, or returns the feature instance
     * if there is only one such instance, or returns a list of features otherwise.
     *
     * @param  owner  if the features to fetch are components of another feature, that container feature instance.
     * @return the feature as a singleton {@code Feature} or as a {@code Collection<Feature>}.
     */
    private Object fetchReferenced(final Feature owner) throws Exception {
        final List<Feature> features = new ArrayList<>();
        try (ResultSet r = statement.executeQuery()) {
            result = r;
            fetch(features::add, true);
        } finally {
            result = null;
        }
        if (owner != null && adapter.deferredAssociation != null) {
            for (final Feature feature : features) {
                feature.setPropertyValue(adapter.deferredAssociation, owner);
            }
        }
        Object feature;
        switch (features.size()) {
            case 0:  feature = null; break;
            case 1:  feature = features.get(0); break;
            default: feature = features; break;
        }
        return feature;
    }

    /**
     * Closes the (pooled) connection, including the statements of all dependencies.
     */
    @Override
    public void close() throws SQLException {
        if (spatialInformation != null) {
            spatialInformation.close();
        }
        /*
         * Only one of `statement` and `result` should be non-null. The connection should be closed by
         * the `FeatureIterator` instance having a non-null `result` because it is the main one created
         * by `Table.features(boolean)` method. The other `FeatureIterator` instances are dependencies.
         */
        if (statement != null) {
            statement.close();
        }
        final ResultSet r = result;
        if (r != null) {
            result = null;
            final Statement s = r.getStatement();
            try (Connection c = s.getConnection()) {
                r.close();      // Implied by s.close() according JDBC javadoc, but we are paranoiac.
                s.close();
                for (final FeatureIterator dependency : dependencies) {
                    if (dependency != null) {
                        dependency.close();
                    }
                }
            }
        }
    }
}
