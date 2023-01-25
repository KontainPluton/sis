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

import java.util.Spliterator;
import java.util.stream.Stream;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.Comparator;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.apache.sis.filter.Optimization;
import org.apache.sis.internal.metadata.sql.SQLBuilder;
import org.apache.sis.internal.stream.DeferredStream;
import org.apache.sis.internal.stream.PaginedStream;
import org.apache.sis.internal.filter.SortByComparator;
import org.apache.sis.internal.util.Strings;
import org.apache.sis.storage.DataStoreException;
import org.apache.sis.util.collection.BackingStoreException;
import org.apache.sis.util.ArgumentChecks;

// Branch-dependent imports
import org.opengis.feature.Feature;
import org.opengis.filter.Filter;
import org.opengis.filter.SortBy;


/**
 * A stream of {@code Feature} instances from a table. This implementation intercepts some {@link Stream}
 * method calls such as {@link #count()}, {@link #distinct()}, {@link #skip(long)} and {@link #limit(long)}
 * in order to delegate the operation to the underlying SQL database.
 *
 * <p>Optimization strategies are also propagated to streams obtained using {@link #map(Function)} and
 * {@link #mapToDouble(ToDoubleFunction)}. However, for result consistency, no optimization is stacked
 * anymore after either {@link #filter(Predicate)} or {@link #flatMap(Function)} operations are called,
 * because they modify volumetry (the count of stream elements is not bound 1 to 1 to query result rows).</p>
 *
 * @author  Alexis Manin (Geomatys)
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.1
 * @since   1.1
 * @module
 */
final class FeatureStream extends DeferredStream<Feature> {
    /**
     * The table which is the source of features.
     */
    private final Table table;

    /**
     * The visitor to use for converting filters/expressions to SQL statements.
     * This is used for writing the content of the {@link SelectionClause}.
     * It is usually a singleton instance shared by all databases.
     * It is fetched when first needed.
     */
    private SelectionClauseWriter filterToSQL;

    /**
     * The SQL fragment on the right side of the {@code WHERE} keyword.
     * This buffer does not including the {@code WHERE} keyword.
     * It is created when first needed and discarded after the iterator is created.
     */
    private SelectionClause selection;

    /**
     * {@code true} if at least one predicate given to {@link #filter(Predicate)}
     * is implemented using Java code instead of using SQL statements.
     */
    private boolean hasPredicates;

    /**
     * {@code true} if at least one comparator given to {@link #sorted(Comparator)}
     * is implemented using Java code instead of using SQL statements.
     */
    private boolean hasComparator;

    /**
     * Whether all returned feature instances should be unique.
     */
    private boolean distinct;

    /**
     * The {@code ORDER BY} clauses, or {@code null} if none.
     */
    private SortBy<? super Feature> sort;

    /**
     * Number of rows to skip in underlying SQL query, or 0 for none.
     *
     * @see #skip(long)
     */
    private long offset;

    /**
     * Maximum number of rows to return, or 0 for no limit. Note that 0 is a valid value for the limit,
     * but when this value is reached the {@link #empty()} stream should be immediately returned.
     *
     * @see #limit(long)
     */
    private long count;

    /**
     * Creates a new stream of features.
     *
     * @param table     the source table.
     * @param parallel  whether the stream should be initially parallel.
     */
    FeatureStream(final Table table, final boolean parallel) {
        super(FeatureIterator.CHARACTERISTICS, parallel);
        this.table = table;
    }

    /**
     * Marks this stream as inactive and returns an empty stream.
     * This method is invoked when an operation resulted in an empty stream.
     */
    private Stream<Feature> empty() {
        count = 0;
        delegate();                 // Mark this stream as inactive.
        return Stream.empty();
    }

    /**
     * Returns {@code true} if either {@link #count} or {@link #offset} is set.
     * In such case, we can not continue to build the SQL statement because the
     * {@code OFFSET ... FETCH NEXT} clauses in SQL are executed last.
     * Consequently in order to have consistent results, the {@link #offset(long)} and
     * {@link #limit(long)} methods need to be the last methods invoked on this stream.
     */
    private boolean isPagined() {
        return (offset | count) != 0;
    }

    /**
     * Returns a stream with features of this stream that match the given predicate.
     * If the given predicate is an instance of {@link Filter}, then this method tries
     * to express the filter using SQL statements.
     */
    @Override
    public Stream<Feature> filter(final Predicate<? super Feature> predicate) {
        ArgumentChecks.ensureNonNull("predicate", predicate);
        if (predicate == Filter.include()) return this;
        if (predicate == Filter.exclude()) return empty();
        if (isPagined()) {
            /*
             * Offset/limit executed before the filter. Can not continue to build an SQL statement
             * because the SQL `OFFSET ... FETCH NEXT` clause would be executed after the filter.
             */
            return delegate().filter(predicate);
        }
        if (!(predicate instanceof Filter<?>)) {
            hasPredicates = true;
            return super.filter(predicate);
        }
        if (selection == null) {
            selection = new SelectionClause(table);
            filterToSQL = table.database.getFilterToSupportedSQL();
        }
        /*
         * Simplify/optimize the filter (it may cause `include` or `exclude` filters to emerge) and try
         * to convert the filter to SQL statements. This is not necessarily an all or nothing operation:
         * if we have a "F₀ AND F₁ AND F₂" chain, it is possible to have some Fₙ as SQL statements and
         * other Fₙ executed in Java code.
         */
        final Optimization optimization = new Optimization();
        optimization.setFeatureType(table.featureType);
        Stream<Feature> stream = this;
        for (final Filter<? super Feature> filter : optimization.applyAndDecompose((Filter<? super Feature>) predicate)) {
            if (filter == Filter.include()) continue;
            if (filter == Filter.exclude()) return empty();
            if (!selection.tryAppend(filterToSQL, filter)) {
                // Delegate to Java code all filters that we can not translate to SQL statement.
                stream = super.filter(filter);
                hasPredicates = true;
            }
        }
        return stream;
    }

    /**
     * Requests this stream to return distinct feature instances.
     * This operation will be done with a SQL {@code DISTINCT} clause if possible.
     */
    @Override
    public Stream<Feature> distinct() {
        if (isPagined()) {
            return delegate().distinct();
        } else {
            distinct = true;
            return this;
        }
    }

    /**
     * Returns an equivalent stream that is unordered.
     */
    @Override
    public Stream<Feature> unordered() {
        if (isPagined()) {
            return delegate().unordered();
        } else {
            sort = null;
            return super.unordered();
        }
    }

    /**
     * Returns an equivalent stream that is sorted by feature natural order.
     * This is defined as a matter of principle, but will cause a {@link ClassCastException} to be thrown
     * when a terminal operation will be executed because {@link Feature} instances are not comparable.
     */
    @Override
    public Stream<Feature> sorted() {
        if (isPagined()) {
            return delegate().sorted();
        } else {
            return super.sorted();
        }
    }

    /**
     * Returns a stream with features of this stream sorted using the given comparator.
     */
    @Override
    public Stream<Feature> sorted(final Comparator<? super Feature> comparator) {
        if (isPagined() || hasComparator) {
            return delegate().sorted(comparator);
        }
        final SortBy<? super Feature> c = SortByComparator.concatenate(sort, comparator);
        if (c != null) {
            sort = c;
            return this;
        }
        hasComparator = true;
        return super.sorted(comparator);
    }

    /**
     * Discards the specified number of elements.
     * This operation will be done with a SQL {@code OFFSET} clause.
     */
    @Override
    public Stream<Feature> skip(final long n) {
        // Do not require this stream to be active because this method may be invoked by `PaginedStream`.
        ArgumentChecks.ensurePositive("n", n);
        offset = Math.addExact(offset, n);
        if (count != 0) {
            if (n >= count) {
                return empty();
            }
            count -= n;
        }
        return this;
    }

    /**
     * Truncates this stream to the given number of elements.
     * This operation will be done with a SQL {@code FETCH NEXT} clause.
     */
    @Override
    public Stream<Feature> limit(final long maxSize) {
        // Do not require this stream to be active because this method may be invoked by `PaginedStream`.
        ArgumentChecks.ensurePositive("maxSize", maxSize);
        if (maxSize == 0) {
            return empty();
        }
        count = (count != 0) ? Math.min(count, maxSize) : maxSize;
        return this;
    }

    /**
     * Returns a stream with results of applying the given function to the elements of this stream.
     * The {@code skip} and {@code limit} operations applied on the returned stream may continue to
     * be optimized.
     */
    @Override
    public <R> Stream<R> map(final Function<? super Feature, ? extends R> mapper) {
        return new PaginedStream<>(super.map(mapper), this);
    }

    /**
     * Counts the number of elements in the table. This method uses a simpler SQL statement than the one
     * associated to the table. For example if a property is an association to another feature, the SQL
     * statement will contain only the foreigner key values, not an inner join to the other feature table.
     */
    @Override
    public long count() {
        /*
         * If at least one filter is implemented by Java code (i.e. has not been translated to SQL statement),
         * then we can not count using SQL only. We have to rely on the more costly default implementation.
         */
        if (hasPredicates || count != 0) {
            return super.count();
        }
        /*
         * Build the full SQL statement here, without using `FeatureAdapter.sql`,
         * because we do not need to follow foreigner keys.
         */
        final SQLBuilder sql = new SQLBuilder(table.database).append(SQLBuilder.SELECT).append("COUNT(");
        if (distinct) {
            String separator = "DISTINCT ";
            for (final Column attribute : table.attributes) {
                sql.append(separator).appendIdentifier(attribute.label);
                separator = ", ";
            }
        } else {
            // If we want a count and no distinct clause is specified, a single column is sufficient.
            sql.appendIdentifier(table.attributes[0].label);
        }
        table.appendFromClause(sql.append(')'));
        if (selection != null && !selection.isEmpty()) {
            sql.append(" WHERE ").append(selection.toString());
        }
        try (Connection connection = getConnection()) {
            makeReadOnly(connection);
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery(sql.toString()))
            {
                while (rs.next()) {
                    final long n = rs.getLong(1);
                    if (!rs.wasNull()) return n;
                }
            }
        } catch (SQLException e) {
            throw new BackingStoreException(e);
        }
        return Math.max(super.count() - offset, 0);
    }

    /**
     * Acquires a connection to the database. The {@link #makeReadOnly(Connection)} method should be invoked
     * after this method. Those two methods are separated for allowing the immediate use of the connection
     * in a {@code try ... finally} block.
     *
     * @return a new connection to the database.
     * @throws SQLException if we cannot create a new connection. See {@link DataSource#getConnection()} for details.
     */
    private Connection getConnection() throws SQLException {
        return table.database.source.getConnection();
    }

    /**
     * Makes the given connection read-only and apply some configuration for better performances.
     * Current configurations are:
     *
     * <ul>
     *   <li>{@linkplain Connection#setReadOnly(boolean) querying read-only}.</li>
     * </ul>
     *
     * @param  connection  the connection to configure.
     */
    private void makeReadOnly(final Connection connection) throws SQLException {
        connection.setReadOnly(true);
        /*
         * Do not invoke `setAutoCommit(false)` because it causes the database to hold read locks,
         * even if we are doing only SELECT statements. On Derby database it causes the following
         * exception to be thrown when closing the connection because we do not invoke `commit()`:
         *
         *     ERROR 25001: Cannot close a connection while a transaction is still active.
         */
    }

    /**
     * Creates the iterator which will provide the actual feature instances.
     * The {@linkplain Spliterator#characteristics() characteristics} of the returned iterator
     * must be the characteristics declared in the {@code FeatureStream} constructor.
     *
     * <p>This method is invoked at most once, generally when a stream terminal operation is invoked.
     * After this method is invoked, this stream will not be active anymore.</p>
     *
     * @return an iterator over the feature elements.
     * @throws DataStoreException if a data model dependent error occurs.
     * @throws SQLException if an error occurs while executing the SQL statement.
     */
    @Override
    protected Spliterator<Feature> createSourceIterator() throws Exception {
        final String filter = (selection != null && !selection.isEmpty()) ? selection.toString() : null;
        selection = null;             // Let the garbage collector do its work.

        final Connection connection = getConnection();
        setCloseHandler(connection);  // Executed only if `FeatureIterator` creation fails, discarded later otherwise.
        makeReadOnly(connection);
        final FeatureIterator features = new FeatureIterator(table, connection, distinct, filter, sort, offset, count);
        setCloseHandler(features);
        return features;
    }

    /**
     * Returns a string representation of this stream for debugging purposes.
     * The returned string tells whether filtering and sorting are done using
     * SQL statement, Java code, or a mix of both.
     */
    @Override
    public String toString() {
        return Strings.toString(getClass(), "table", table.name.table,
                "predicates", hasPredicates ? (filterToSQL != null ? "mixed" : "Java") : (filterToSQL != null ? "SQL" : null),
                "comparator", hasComparator ? (sort != null ? "mixed" : "Java") : (sort != null ? "SQL" : null),
                "distinct",   distinct ? Boolean.TRUE : null,
                "offset",     offset != 0 ? offset : null,
                "count",      count  != 0 ? count  : null);
    }
}
