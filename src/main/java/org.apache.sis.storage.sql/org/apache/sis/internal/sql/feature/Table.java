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

import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Stream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.opengis.util.GenericName;
import org.opengis.geometry.Envelope;
import org.apache.sis.storage.AbstractFeatureSet;
import org.apache.sis.storage.DataStoreException;
import org.apache.sis.storage.InternalDataStoreException;
import org.apache.sis.internal.metadata.sql.Reflection;
import org.apache.sis.internal.metadata.sql.SQLBuilder;
import org.apache.sis.util.collection.WeakValueHashMap;
import org.apache.sis.util.collection.Containers;
import org.apache.sis.util.collection.TreeTable;
import org.apache.sis.util.iso.DefaultNameSpace;
import org.apache.sis.util.Debug;

// Branch-dependent imports
import org.opengis.feature.Feature;
import org.opengis.feature.FeatureType;
import org.opengis.feature.AttributeType;
import org.opengis.feature.FeatureAssociationRole;


/**
 * Description of a table in the database, including columns, primary keys and foreigner keys.
 * This class contains a {@link FeatureType} inferred from the table structure.
 * The {@link FeatureType} contains an {@link AttributeType} for each table column,
 * except foreigner keys which are represented by {@link FeatureAssociationRole}s.
 *
 * <h2>Multi-threading</h2>
 * This class is immutable (except for the cache) and safe for concurrent use by many threads.
 * The content of arrays in this class shall not be modified in order to preserve immutability.
 *
 * @author  Johann Sorel (Geomatys)
 * @author  Martin Desruisseaux (Geomatys)
 * @author  Alexis Manin (Geomatys)
 * @version 1.2
 * @since   1.0
 * @module
 */
final class Table extends AbstractFeatureSet {
    /**
     * Information about the database (syntax for building SQL statements, …) together with a cache of CRS.
     * Contains the provider of (pooled) connections to the database.
     */
    final Database<?> database;

    /**
     * The structure of this table represented as a feature. Each feature attribute is a table column,
     * except synthetic attributes like "sis:identifier". The feature may also contain associations
     * inferred from foreigner keys that are not immediately apparent in the table.
     *
     * @see #getType()
     */
    final FeatureType featureType;

    /**
     * The SQL query to execute for fetching data, or {@code null} for querying the table identified by {@link #name}.
     * This is non-null only if the user explicitly specified a SQL query to execute.
     *
     * @see #appendFromClause(SQLBuilder)
     */
    private final String query;

    /**
     * The name in the database of this {@code Table} object, together with its schema and catalog.
     * The catalog and schema parts are optional and can be null, but the table name is mandatory.
     *
     * @see #appendFromClause(SQLBuilder)
     */
    final TableReference name;

    /**
     * Attributes in feature instances, excluding operations and associations to other tables.
     * Elements are in the order of columns declared in the {@code SELECT <columns>} statement.
     * This array shall not be modified after construction.
     *
     * <p>Columns may have alias if it was necessary to avoid name collisions.
     * The alias is given by {@link Column#propertyName} and will be the name used in {@link FeatureType}.</p>
     */
    final Column[] attributes;

    /**
     * The columns that constitute the primary key, or {@code null} if there is no primary key.
     */
    final PrimaryKey primaryKey;

    /**
     * The primary keys of other tables that are referenced by this table foreign key columns.
     * They are 0:1 relations. May be empty if there are no imported keys but never null.
     */
    final Relation[] importedKeys;

    /**
     * The foreign keys of other tables that reference this table primary key columns.
     * They are 0:N relations. May be empty if there are no exported keys but never null.
     */
    final Relation[] exportedKeys;

    /**
     * {@code true} if this table contains at least one geometry column.
     */
    final boolean hasGeometry;

    /**
     * {@code true} if this table contains at least one raster column.
     */
    final boolean hasRaster;

    /**
     * Map from attribute name to columns. This is built from {@link #columns} array when first needed.
     *
     * @see #getColumn(String)
     */
    private Map<String,Column> attributeToColumns;

    /**
     * The converter of {@link ResultSet} rows to {@link Feature} instances.
     * Created when first needed.
     *
     * @see #adapter(Connection)
     */
    private FeatureAdapter adapter;

    /**
     * Feature instances already created for given primary keys. This map is used only when requesting feature
     * instances by identifiers (not for iterating over all features) and those identifiers are primary keys.
     * We create this map only for tables referenced by foreigner keys of other tables as enumerated by the
     * {@link Relation.Direction#IMPORT} and {@link Relation.Direction#EXPORT} cases; not for arbitrary
     * cross-reference cases. Values are usually {@code Feature} instances, but may also be {@code Collection<Feature>}.
     *
     * @see #instanceForPrimaryKeys()
     */
    private WeakValueHashMap<?,Object> instanceForPrimaryKeys;

    /**
     * {@code true} if {@link #getEnvelope()} has been invoked at least once on this table.
     * This is used for performing only once operations such as PosthreSQL {@code ANALYZE}.
     *
     * @see #getEnvelope()
     */
    private boolean isEnvelopeAnalyzed;

    /**
     * Creates a description of the table analyzed by the given object.
     *
     * @param  database  information about the database (syntax for building SQL statements, …).
     * @param  analyzer  helper functions, e.g. for converting SQL types to Java types.
     * @param  query     the SQL query to use for fetching data, or {@code null} for querying
     *                   the table identified by {@link #name}.
     */
    Table(final Database<?> database, final FeatureAnalyzer analyzer, final String query) throws Exception {
        super(database.listeners, false);
        this.database = database;
        this.query    = query;
        name          = analyzer.id;
        importedKeys  = analyzer.getForeignerKeys(Relation.Direction.IMPORT);
        exportedKeys  = analyzer.getForeignerKeys(Relation.Direction.EXPORT);
        attributes    = analyzer.createAttributes();                 // Must be after `spec.getForeignerKeys(IMPORT)`.
        primaryKey    = analyzer.createAssociations(exportedKeys);   // Must be after `spec.createAttributes(…)`.
        featureType   = analyzer.buildFeatureType();
        hasGeometry   = analyzer.hasGeometry;
        hasRaster     = analyzer.hasRaster;
    }

    /**
     * Creates a new table as a projection (subset of columns) of the given table.
     *
     * @todo This constructor is not yet used because it is an unfinished work.
     *       We need to invent some mechanism for using a subset of the columns.
     *       A starting point is {@link org.apache.sis.storage.FeatureQuery#expectedType(FeatureType)}.
     */
    Table(final Table parent) {
        super(parent.listeners, false);
        database = parent.database;
        query    = parent.query;
        name     = parent.name;

        // TODO: filter the columns.
        primaryKey   = parent.primaryKey;
        attributes   = parent.attributes;
        importedKeys = parent.importedKeys;
        exportedKeys = parent.exportedKeys;
        featureType  = parent.featureType;
        hasGeometry  = parent.hasGeometry;
        hasRaster    = parent.hasRaster;
    }

    /**
     * Sets the search tables on all {@link Relation} instances for which this operation has been deferred.
     * This happen when a table could not be obtained because of circular dependency.
     * This method is invoked after all tables have been created in order to fill such holes.
     *
     * @param  tables  all tables created.
     */
    final void setDeferredSearchTables(final Analyzer analyzer, final Map<GenericName,Table> tables) throws DataStoreException {
        for (final Relation.Direction direction : Relation.Direction.values()) {
            final Relation[] relations;
            switch (direction) {
                case IMPORT: relations = importedKeys; break;
                case EXPORT: relations = exportedKeys; break;
                default: continue;
            }
            for (final Relation relation : relations) {
                if (relation.isSearchTableDeferred()) {
                    /*
                     * `ClassCastException` should never occur below because `relation.propertyName` fields
                     * have been set to association names. If `ClassCastException` occurs here, it is a bug
                     * in our object constructions.
                     */
                    final FeatureAssociationRole association =
                            (FeatureAssociationRole) featureType.getProperty(relation.propertyName);

                    final Table table = tables.get(association.getValueType().getName());
                    if (table == null) {
                        throw new InternalDataStoreException(association.toString());
                    }
                    final PrimaryKey referenced;
                    switch (direction) {
                        case IMPORT: referenced = table.primaryKey; break;
                        case EXPORT: referenced =  this.primaryKey; break;
                        default: throw new AssertionError(direction);
                    }
                    if (referenced != null) {
                        relation.setSearchTable(analyzer, table, referenced, direction);
                    }
                }
            }
        }
    }


    // ────────────────────────────────────────────────────────────────────────────────────────
    //     End of table construction. Next methods are for visualizing the table structure.
    // ────────────────────────────────────────────────────────────────────────────────────────


    /**
     * Appends all children to the given parent. The children are added under the given node.
     * If the children array is empty, then this method does nothing.
     *
     * @param  parent    the node where to add children.
     * @param  children  the children to add, or an empty array if none.
     * @param  arrow     the symbol to use for relating the columns of two tables in a foreigner key.
     */
    @Debug
    private static void appendAll(final TreeTable.Node parent, final Relation[] children, final String arrow) {
        for (final Relation child : children) {
            child.appendTo(parent, arrow);
        }
    }

    /**
     * Creates a tree representation of this table for debugging purpose.
     *
     * @param  parent  the parent node where to add the tree representation.
     */
    @Debug
    final void appendTo(TreeTable.Node parent) {
        parent = Relation.newChild(parent, featureType.getName().toString());
        for (final Column attribute : attributes) {
            TableReference.newChild(parent, attribute.propertyName);
        }
        appendAll(parent, importedKeys, " → ");
        appendAll(parent, exportedKeys, " ← ");
    }

    /**
     * Formats a graphical representation of this table for debugging purpose. This representation
     * can be printed to the {@linkplain System#out standard output stream} (for example) if the
     * output device uses a monospaced font and supports Unicode.
     */
    @Override
    public String toString() {
        return TableReference.toString(this, (n) -> appendTo(n));
    }


    // ────────────────────────────────────────────────────────────────────────────────────────
    //     End of table structure visualization. Next methods are for fetching features.
    // ────────────────────────────────────────────────────────────────────────────────────────


    /**
     * Returns the table identifier composed of catalog, schema and table name.
     */
    @Override
    public final Optional<GenericName> getIdentifier() {
        return Optional.of(featureType.getName().toFullyQualifiedName());
    }

    /**
     * Returns the feature type inferred from the database structure analysis.
     */
    @Override
    public final FeatureType getType() {
        return featureType;
    }

    /**
     * Returns an estimation of the envelope of all geometry columns in this table.
     * The returned envelope shall contain at least the two-dimensional spatial components.
     * Whether other dimensions (vertical and temporal) and present or not depends on the implementation.
     *
     * <h2>Departure from interface contract</h2>
     * {@link org.apache.sis.storage.DataSet#getEnvelope()} contract allows estimated envelope to be larger than
     * actual envelope (similar to Java2D {@link java.awt.Shape#getBounds()} contract), but smaller envelope are
     * discouraged. Despite that, this method may return smaller envelopes because the computation is done using
     * a subset of all data.
     *
     * <h2>Limitations</h2>
     * The exact behavior is database-dependent.
     * For example PostGIS implementation assumes that all geometries in the same column are in the same CRS.
     * If geometries in different <em>rows</em> use different CRS, coordinate transformations are <em>not</em>
     * applied and the result is likely to be invalid. However if different <em>column</em> use different CRS,
     * coordinate transformations between columns is applied and the result is in the CRS of the first column
     * having at least one geometry.
     *
     * @return an estimation of the spatiotemporal resource extent.
     * @throws DataStoreException if an error occurred while reading or computing the envelope.
     */
    @Override
    public Optional<Envelope> getEnvelope() throws DataStoreException {
        if (hasGeometry) try {
            final boolean recall = isEnvelopeAnalyzed;
            isEnvelopeAnalyzed = true;
            return Optional.ofNullable(database.getEstimatedExtent(name, attributes, recall));
        } catch (SQLException e) {
            throw new DataStoreException(e);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Returns the column from an attribute name specified as XPath.
     * Current implementation interprets the {@code xpath} value only as the attribute name,
     * but a future implementation may parse something like a {@code "table/column"} syntax.
     * It may be necessary with {@code Table} that are actually views generated by queries.
     *
     * @param  xpath  the XPath (currently only attribute name).
     * @return column for the given XPath, or {@code null} if the specified attribute is not found.
     */
    final Column getColumn(final String xpath) {
        Map<String,Column> m;
        synchronized (this) {
            m = attributeToColumns;
            if (m == null) {
                m = new HashMap<>(Containers.hashMapCapacity(attributes.length));
                for (final Column c : attributes) {
                    String label = c.propertyName;
                    m.put(label, c);
                    final int s = label.lastIndexOf(DefaultNameSpace.DEFAULT_SEPARATOR);
                    if (s >= 0) {
                        m.putIfAbsent(label.substring(s+1), c);
                    }
                }
                attributeToColumns = m;
            }
        }
        return m.get(xpath);
    }

    /**
     * If this table imports the inverse of the given relation, returns the imported relation.
     * Otherwise returns {@code null}. This method is used for preventing infinite recursivity.
     *
     * @param  exported       the relation exported by another table.
     * @param  exportedOwner  {@code exported.owner.name}: table that contains the {@code exported} relation.
     * @return the inverse of the given relation, or {@code null} if none.
     */
    final Relation getInverseOf(final Relation exported, final TableReference exportedOwner) {
        if (name.equals(exported)) {
            for (final Relation relation : importedKeys) {
                if (relation.equals(exportedOwner) && relation.isInverseOf(exported)) {
                    return relation;
                }
            }
        }
        return null;
    }

    /**
     * Returns a cache for fetching feature instances by identifier. The map is created when this method is
     * first invoked. Keys are primary key values, typically as {@code String} or {@code Integer} instances
     * or arrays of those if the keys use more than one column. Values are usually {@code Feature} instances,
     * but may also be {@code Collection<Feature>}.
     */
    @SuppressWarnings("ReturnOfCollectionOrArrayField")
    final synchronized WeakValueHashMap<?,Object> instanceForPrimaryKeys() {
        if (instanceForPrimaryKeys == null) {
            instanceForPrimaryKeys = new WeakValueHashMap<>(primaryKey.valueClass);
        }
        return instanceForPrimaryKeys;
    }

    /**
     * Appends the catalog, schema and table name to the given builder after the {@code "FROM"} keyword.
     */
    final void appendFromClause(final SQLBuilder sql) {
        sql.append(" FROM ");
        if (query != null) {
            sql.append('(').append(query).append(") AS USER_QUERY");
        } else {
            sql.appendIdentifier(name.catalog, name.schema, name.table);
        }
    }

    /**
     * Returns the number of rows, or -1 if unknown. Note that some database drivers returns 0,
     * so it is better to consider 0 as "unknown" too (see {@link FeatureIterator#estimatedSize}).
     * We do not cache this count because it may change at any time.
     *
     * @param  metadata     information about the database.
     * @param  distinct     whether to count distinct values instead of all values.
     * @param  approximate  whether approximate or outdated values are acceptable.
     * @return number of rows (may be approximate), or -1 if unknown.
     */
    final long countRows(final DatabaseMetaData metadata, final boolean distinct, final boolean approximate) throws SQLException {
        long count = -1;
        final String[] names = TableReference.splitName(featureType.getName());
        try (ResultSet reflect = metadata.getIndexInfo(names[2], names[1], names[0], distinct, approximate)) {
            while (reflect.next()) {
                final long n = reflect.getLong(Reflection.CARDINALITY);
                if (!reflect.wasNull()) {
                    if (reflect.getShort(Reflection.TYPE) == DatabaseMetaData.tableIndexStatistic) {
                        return n;       // "Index statistic" type provides the number of rows in the table.
                    }
                    if (n > count) {    // Other index types may be inaccurate.
                        count = n;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Returns the converter of {@link ResultSet} rows to {@link Feature} instances.
     * The converter is created the first time that this method is invoked, then cached.
     *
     * @param  connection  source of database metadata to use if the adapter needs to be created.
     */
    final synchronized FeatureAdapter adapter(final Connection connection) throws SQLException, InternalDataStoreException {
        if (adapter == null) {
            adapter = new FeatureAdapter(this, connection.getMetaData());
        }
        return adapter;
    }

    /**
     * Returns a stream of all features contained in this dataset.
     *
     * @param  parallel  {@code true} for a parallel stream (if supported), or {@code false} for a sequential stream.
     * @return all features contained in this dataset.
     * @throws DataStoreException if an error occurred while creating the stream.
     */
    @Override
    public Stream<Feature> features(final boolean parallel) throws DataStoreException {
        return new FeatureStream(this, parallel);
    }
}
