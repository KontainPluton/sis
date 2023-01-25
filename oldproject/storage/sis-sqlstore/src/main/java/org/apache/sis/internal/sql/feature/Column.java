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

import java.sql.Types;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.SQLDataException;
import java.util.Optional;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.apache.sis.internal.metadata.sql.Reflection;
import org.apache.sis.internal.metadata.sql.SQLUtilities;
import org.apache.sis.internal.feature.GeometryType;
import org.apache.sis.internal.util.Strings;
import org.apache.sis.storage.DataStoreContentException;
import org.apache.sis.feature.builder.AttributeTypeBuilder;
import org.apache.sis.feature.builder.FeatureTypeBuilder;
import org.apache.sis.util.resources.Errors;
import org.apache.sis.util.Localized;


/**
 * Information (name, data type…) about a table column. It contains information extracted from
 * {@linkplain DatabaseMetaData#getColumns(String, String, String, String) database metadata},
 * possibly completed with information about a geometry column.
 * The aim is to describe all information about a column that is needed for mapping to feature model.
 *
 * <h2>Multi-threading</h2>
 * {@code Column} instances shall be kept unmodified after all fields have been initialized.
 * The same instances may be read concurrently by many threads.
 *
 * @author  Alexis Manin (Geomatys)
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.2
 *
 * @see ResultSet#getMetaData()
 * @see DatabaseMetaData#getColumns(String, String, String, String)
 *
 * @since 1.1
 * @module
 */
public final class Column {
    /**
     * Name of the column.
     *
     * @see Reflection#COLUMN_NAME
     */
    public final String name;

    /**
     * Title to use for displays. This is the name specified by the {@code AS} keyword in a {@code SELECT} clause.
     * This is never null but may be identical to {@link #name} if no label was specified.
     */
    public final String label;

    /**
     * Name to use for feature property. This is the same as {@link #label} unless there is a name collision.
     * In that case the property name is modified for avoiding the collision.
     */
    String propertyName;

    /**
     * Type of values as one of the constants enumerated in {@link Types} class.
     *
     * @see Reflection#DATA_TYPE
     */
    public final int type;

    /**
     * A name for the value type, free-text from the database engine. For more information about this, see
     * {@link DatabaseMetaData#getColumns(String, String, String, String)} and {@link Reflection#TYPE_NAME}.
     * This value shall not be null.
     *
     * @see Reflection#TYPE_NAME
     */
    public final String typeName;

    /**
     * The column size, or 0 if not applicable. For texts, this is the maximum number of characters allowed.
     * For numbers, this is the maximum number of digits. For blobs, this is a limit in number of bytes.
     *
     * @see Reflection#COLUMN_SIZE
     * @see ResultSetMetaData#getPrecision(int)
     */
    private final int precision;

    /**
     * Whether the column can have null values.
     *
     * @see Reflection#IS_NULLABLE
     */
    public final boolean isNullable;

    /**
     * If this column is a geometry column, the type of the geometry objects. Otherwise {@code null}.
     *
     * @see #getGeometryType()
     */
    private GeometryType geometryType;

    /**
     * If this column is a geometry or raster column, the Coordinate Reference System (CRS). Otherwise {@code null}.
     * This is determined from the geometry Spatial Reference Identifier (SRID).
     *
     * @see #getDefaultCRS()
     */
    private CoordinateReferenceSystem defaultCRS;

    /**
     * Converter from {@link ResultSet} column value to value stored in the feature instance.
     * It will typically delegate to the {@link ResultSet} getter method for the column type,
     * but may also perform some conversions such as parsing geometry Well-Known Binary (WKB).
     */
    ValueGetter<?> valueGetter;

    /**
     * Creates a synthetic column (a column not inferred from database analysis)
     * for describing the type of elements in an array.
     *
     * @param  type      SQL type of the column.
     * @param  typeName  SQL name of the type.
     */
    Column(final int type, final String typeName) {
        this.name = label = propertyName = "element";
        this.type       = type;
        this.typeName   = typeName;
        this.precision  = 0;
        this.isNullable = false;
    }

    /**
     * Creates a new column from database metadata.
     * Information are fetched from current {@code ResultSet} row.
     * This method does not change cursor position.
     *
     * @param  analyzer  the analyzer which is creating this column.
     * @param  metadata  the result of {@code DatabaseMetaData.getColumns(…)}.
     * @param  quote     value of {@code DatabaseMetaData.getIdentifierQuoteString()}.
     * @throws SQLException if an error occurred while fetching metadata.
     *
     * @see DatabaseMetaData#getColumns(String, String, String, String)
     */
    Column(final Analyzer analyzer, final ResultSet metadata, final String quote) throws SQLException {
        label = name = analyzer.getUniqueString(metadata, Reflection.COLUMN_NAME);
        type         = metadata.getInt(Reflection.DATA_TYPE);
        typeName     = localPart(metadata.getString(Reflection.TYPE_NAME), quote);
        precision    = metadata.getInt(Reflection.COLUMN_SIZE);
        isNullable   = Boolean.TRUE.equals(SQLUtilities.parseBoolean(metadata.getString(Reflection.IS_NULLABLE)));
        propertyName = label;
    }

    /**
     * Creates a new column from the result of a query.
     *
     * @param  metadata  value of {@link ResultSet#getMetaData()}.
     * @param  column    index of the column for which to get metadata.
     * @param  quote     value of {@code DatabaseMetaData.getIdentifierQuoteString()}.
     * @throws SQLException if an error occurred while fetching metadata.
     *
     * @see ResultSet#getMetaData()
     */
    Column(final ResultSetMetaData metadata, final int column, final String quote) throws SQLException {
        name         = metadata.getColumnName(column);
        label        = metadata.getColumnLabel(column);
        type         = metadata.getColumnType(column);
        typeName     = localPart(metadata.getColumnTypeName(column), quote);
        precision    = metadata.getPrecision(column);
        isNullable   = metadata.isNullable(column) == ResultSetMetaData.columnNullable;
        propertyName = label;
    }

    /**
     * PostgreSQL JDBC drivers sometime gives the fully qualified type name.
     * For example we sometime get {@code "public"."geometry"} (including the quotes)
     * instead of a plain {@code geometry}. If this is the case, keep only the local part.
     *
     * @param  type   value found in the {@value Reflection.TYPE_NAME} column.
     * @param  quote  value of {@code DatabaseMetaData.getIdentifierQuoteString()}.
     * @return local part of the type name.
     */
    private static String localPart(String type, final String quote) throws SQLDataException {
        if (type == null) {
            throw new SQLDataException(Errors.format(Errors.Keys.MissingValueInColumn_1, Reflection.TYPE_NAME));
        }
        if (quote != null) {
            int end = type.lastIndexOf(quote);
            if (end >= 0) {
                int start = type.lastIndexOf(quote, end - 1);
                if (start >= 0 && end > (start += quote.length())) {
                    type = type.substring(start, end);
                }
            }
        }
        return type;
    }

    /**
     * Modifies this column for declaring it as a geometry or raster column.
     * This method is invoked during inspection of the {@code "GEOMETRY_COLUMNS"} table of a spatial database.
     * It can also be invoked during the inspection of {@code "GEOGRAPHY_COLUMNS"} or {@code "RASTER_COLUMNS"}
     * tables, which are PostGIS extensions. In the raster case, the geometry {@code type} argument shall be null.
     *
     * @param  caller  provider of the locale for error message, if any.
     * @param  type    the type of values in the column, or {@code null} if not geometric.
     * @param  crs     the Coordinate Reference System (CRS), or {@code null} if unknown.
     */
    final void makeSpatial(final Localized caller, final GeometryType type, final CoordinateReferenceSystem crs)
            throws DataStoreContentException
    {
        final String property;
        if (geometryType != null && !geometryType.equals(type)) {
            property = "geometryType";
        } else if (defaultCRS != null && !defaultCRS.equals(crs)) {
            property = "defaultCRS";
        } else {
            geometryType = type;
            defaultCRS = crs;
            return;
        }
        throw new DataStoreContentException(Errors.getResources(caller.getLocale())
                        .getString(Errors.Keys.ValueAlreadyDefined_1, property));
    }

    /**
     * If this column is a geometry column, returns the type of the geometry objects.
     * Otherwise returns empty (including the case where this is a raster column).
     * Note that if this column is a geometry column but the geometry type was not defined,
     * then {@link GeometryType#GEOMETRY} is returned as a fallback.
     *
     * @return type of geometry objects, or empty if this column is not a geometry column.
     */
    public final Optional<GeometryType> getGeometryType() {
        return Optional.ofNullable(geometryType);
    }

    /**
     * If this column is a geometry or raster column, returns the default coordinate reference system.
     * Otherwise returns empty. The CRS may also be empty even for a geometry column if it is unspecified.
     *
     * @return CRS of geometries or rasters in this column, or empty if unknown or not applicable.
     */
    public final Optional<CoordinateReferenceSystem> getDefaultCRS() {
        return Optional.ofNullable(defaultCRS);
    }

    /**
     * Creates a feature attribute for this column. The attribute is appended to the given feature builder.
     * The attribute builder is returned for allowing additional configuration.
     *
     * @param  feature  the feature where to append an attribute for this column.
     * @return builder for the added feature attribute.
     */
    final AttributeTypeBuilder<?> createAttribute(final FeatureTypeBuilder feature) {
        Class<?> valueType = valueGetter.valueType;
        final boolean isArray = (valueGetter instanceof ValueGetter.AsArray);
        if (isArray) {
            valueType = ((ValueGetter.AsArray) valueGetter).cmget.valueType;
        }
        final AttributeTypeBuilder<?> attribute = feature.addAttribute(valueType).setName(propertyName);
        if (precision > 0 && precision != Integer.MAX_VALUE && CharSequence.class.isAssignableFrom(valueType)) {
            attribute.setMaximalLength(precision);
        }
        if (isArray) {
            /*
             * We have no standard API yet for determining the minimal and maximal array length.
             * The PostgreSQL driver seems to use the `precision` field, but it may be specific
             * to that driver and seems to be always `MAX_VALUE` anyway.
             */
            attribute.setMinimumOccurs(0);
            attribute.setMaximumOccurs(Integer.MAX_VALUE);
        } else if (isNullable) {
            attribute.setMinimumOccurs(0);
        }
        if (geometryType != null || defaultCRS != null) {
            attribute.setCRS(defaultCRS);
        }
        return attribute;
    }

    /**
     * Returns a string representation for debugging purposes.
     *
     * @return a string representation of this column.
     */
    @Override
    public String toString() {
        return Strings.toString(getClass(),
                "name", name, "propertyName", propertyName, "type", type, "typeName", typeName,
                "geometryType", geometryType, "precision", precision, "isNullable", isNullable);
    }
}
