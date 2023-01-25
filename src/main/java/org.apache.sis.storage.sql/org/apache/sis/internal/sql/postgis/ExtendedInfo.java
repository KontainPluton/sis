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
package org.apache.sis.internal.sql.postgis;

import java.util.Map;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.apache.sis.internal.sql.feature.Column;
import org.apache.sis.internal.sql.feature.Database;
import org.apache.sis.internal.sql.feature.TableReference;
import org.apache.sis.internal.sql.feature.InfoStatements;


/**
 * A specialization for PostGIS database of prepared statements about spatial information.
 *
 * @author  Alexis Manin (Geomatys)
 * @author  Martin Desruisseaux (Geomatys)
 * @since   1.2
 * @version 1.1
 * @module
 */
final class ExtendedInfo extends InfoStatements {
    /**
     * A statement for fetching geometric information for a specific column.
     * This statement is used for objects of type "Geography", which is a data type specific to PostGIS.
     */
    private PreparedStatement geographyColumns;

    /**
     * A statement for fetching raster information for a specific column.
     */
    protected PreparedStatement rasterColumns;

    /**
     * The object for reading a raster, or {@code null} if not yet created.
     */
    private RasterReader rasterReader;

    /**
     * Creates an initially empty {@code PostgisStatements} which will use
     * the given connection for creating {@link PreparedStatement}s.
     */
    ExtendedInfo(final Database<?> session, final Connection connection) {
        super(session, connection);
    }

    /**
     * Gets all geometry columns for the given table and sets the geometry information on the corresponding columns.
     *
     * @param  source   the table for which to get all geometry columns.
     * @param  columns  all columns for the specified table. Keys are column names.
     */
    @Override
    public void completeIntrospection(final TableReference source, final Map<String,Column> columns) throws Exception {
        if (geometryColumns == null) {
            geometryColumns = prepareIntrospectionStatement("geometry_columns", 'f', "geometry_column", "type");
        }
        if (geographyColumns == null) {
            geographyColumns = prepareIntrospectionStatement("geography_columns", 'f', "geography_column", "type");
        }
        if (rasterColumns == null) {
            rasterColumns = prepareIntrospectionStatement("raster_columns", 'r', "raster_column", null);
        }
        configureSpatialColumns(geometryColumns,  source, columns, GeometryTypeEncoding.TEXTUAL);
        configureSpatialColumns(geographyColumns, source, columns, GeometryTypeEncoding.TEXTUAL);
        configureSpatialColumns(rasterColumns,    source, columns, null);
    }

    /**
     * Returns a reader for decoding PostGIS Raster binary format to grid coverage instances.
     */
    final RasterReader getRasterReader() {
        if (rasterReader == null) {
            rasterReader = new RasterReader(this);
        }
        return rasterReader;
    }

    /**
     * Closes all prepared statements. This method does <strong>not</strong> close the connection.
     */
    @Override
    public void close() throws SQLException {
        if (geographyColumns != null) {
            geographyColumns.close();
            geographyColumns = null;
        }
        super.close();
    }
}
