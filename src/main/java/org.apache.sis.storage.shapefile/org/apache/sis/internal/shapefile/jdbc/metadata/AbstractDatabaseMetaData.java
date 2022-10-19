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
package org.apache.sis.internal.shapefile.jdbc.metadata;

import org.apache.sis.internal.shapefile.jdbc.AbstractJDBC;

import java.sql.*;

/**
 * Unimplemented methods of DatabaseMetaData.
 * @author Marc LE BIHAN
 */
public abstract class AbstractDatabaseMetaData extends AbstractJDBC implements DatabaseMetaData {
    /**
     * @see DatabaseMetaData#getAttributes(String, String, String, String)
     */
    @Override public ResultSet getAttributes(String catalog, String schemaPattern, String typeNamePattern, String attributeNamePattern) throws SQLException {
        throw unsupportedOperation("getAttributes", catalog, schemaPattern, typeNamePattern, attributeNamePattern);
    }

    /**
     * @see DatabaseMetaData#getBestRowIdentifier(String, String, String, int, boolean)
     */
    @Override public ResultSet getBestRowIdentifier(String catalog, String schema, String table, int scope, boolean nullable) throws SQLException {
        throw unsupportedOperation("getBestRowIdentifier", catalog, schema, table, scope, nullable);
    }

    /**
     * @see DatabaseMetaData#getClientInfoProperties()
     */
    @Override public ResultSet getClientInfoProperties() throws SQLException {
        throw unsupportedOperation("getClientInfoProperties");
    }

    /**
     * @see DatabaseMetaData#getColumnPrivileges(String, String, String, String)
     */
    @Override public ResultSet getColumnPrivileges(String catalog, String schema, String table, String columnNamePattern) throws SQLException {
        throw unsupportedOperation("getColumnPrivileges", catalog, schema, table, columnNamePattern);
    }

    /**
     * @see DatabaseMetaData#getCrossReference(String, String, String, String, String, String)
     */
    @Override public ResultSet getCrossReference(String parentCatalog, String parentSchema, String parentTable, String foreignCatalog, String foreignSchema, String foreignTable) throws SQLException {
        throw unsupportedOperation("getCrossReference", parentCatalog, parentSchema, parentTable, foreignCatalog, foreignSchema, foreignTable);
    }

    /**
     * @see DatabaseMetaData#getExportedKeys(String, String, String)
     */
    @Override public ResultSet getExportedKeys(String catalog, String schema, String table) throws SQLException {
        throw unsupportedOperation("getExportedKeys", catalog, schema, table);
    }

    /**
     * @see DatabaseMetaData#getFunctions(String, String, String)
     */
    @Override public ResultSet getFunctions(String catalog, String schemaPattern, String functionNamePattern) throws SQLException {
        throw unsupportedOperation("getFunctions", catalog, schemaPattern, functionNamePattern);
    }

    /**
     * @see DatabaseMetaData#getFunctionColumns(String, String, String, String)
     */
    @Override public ResultSet getFunctionColumns(String catalog, String schemaPattern, String functionNamePattern, String columnNamePattern) throws SQLException {
        throw unsupportedOperation("getFunctionColumns", catalog, schemaPattern, functionNamePattern, columnNamePattern);
    }

    /**
     * @see DatabaseMetaData#getIndexInfo(String, String, String, boolean, boolean)
     */
    @Override public ResultSet getIndexInfo(String catalog, String schema, String table, boolean unique, boolean approximate) throws SQLException {
        throw unsupportedOperation("getIndexInfo", catalog, schema, table, unique, approximate);
    }

    /**
     * @see DatabaseMetaData#getImportedKeys(String, String, String)
     */
    @Override public ResultSet getImportedKeys(String catalog, String schema, String table) throws SQLException {
        throw unsupportedOperation("getImportedKeys", catalog, schema, table);
    }

    /**
     * @see DatabaseMetaData#getPrimaryKeys(String, String, String)
     */
    @Override public ResultSet getPrimaryKeys(String catalog, String schema, String table) throws SQLException {
        throw unsupportedOperation("getPrimaryKeys", catalog, schema, table);
    }

    /**
     * @see DatabaseMetaData#getProcedures(String, String, String)
     */
    @Override public ResultSet getProcedures(String catalog, String schemaPattern, String procedureNamePattern) throws SQLException {
        throw unsupportedOperation("getProcedures", catalog, schemaPattern, procedureNamePattern);
    }

    /**
     * @see DatabaseMetaData#getProcedureColumns(String, String, String, String)
     */
    @Override public ResultSet getProcedureColumns(String catalog, String schemaPattern, String procedureNamePattern, String columnNamePattern) throws SQLException{
        throw unsupportedOperation("getProcedureColumns", catalog, schemaPattern, procedureNamePattern, columnNamePattern);
    }

    /**
     * @see DatabaseMetaData#getPseudoColumns(String, String, String, String)
     */
    @Override public ResultSet getPseudoColumns(String catalog, String schemaPattern, String tableNamePattern, String columnNamePattern) throws SQLException {
        throw unsupportedOperation("getPseudoColumns", catalog, schemaPattern, tableNamePattern, columnNamePattern);
    }

    /**
     * @see DatabaseMetaData#getRowIdLifetime()
     */
    @Override public RowIdLifetime getRowIdLifetime() throws SQLException {
        throw unsupportedOperation("getRowIdLifetime");
    }

    /**
     * @see DatabaseMetaData#getSchemas(String, String)
     */
    @Override public ResultSet getSchemas(String catalog, String schemaPattern) throws SQLException {
        throw unsupportedOperation("getSchemas", catalog, schemaPattern);
    }

    /**
     * @see DatabaseMetaData#getSearchStringEscape()
     */
    @Override public String getSearchStringEscape() throws SQLException {
        throw unsupportedOperation("getSearchStringEscape");
    }

    /**
     * @see DatabaseMetaData#getSQLStateType()
     */
    @Override public int getSQLStateType() throws SQLException {
        throw unsupportedOperation("getSQLStateType");
    }

    /**
     * @see DatabaseMetaData#getSuperTypes(String, String, String)
     */
    @Override public ResultSet getSuperTypes(String catalog, String schemaPattern, String typeNamePattern) throws SQLException {
        throw unsupportedOperation("getSuperTypes", catalog, schemaPattern, typeNamePattern);
    }

    /**
     * @see DatabaseMetaData#getSuperTables(String, String, String)
     */
    @Override public ResultSet getSuperTables(String catalog, String schemaPattern, String tableNamePattern) throws SQLException {
        throw unsupportedOperation("getSuperTables", catalog, schemaPattern, tableNamePattern);
    }

    /**
     * @see DatabaseMetaData#getTablePrivileges(String, String, String)
     */
    @Override public ResultSet getTablePrivileges(String catalog, String schemaPattern, String tableNamePattern) throws SQLException {
        throw unsupportedOperation("getTablePrivileges", catalog, schemaPattern, tableNamePattern);
    }

    /**
     * @see DatabaseMetaData#getTypeInfo()
     */
    @Override public ResultSet getTypeInfo() throws SQLException {
        throw unsupportedOperation("getTypeInfo");
    }

    /**
     * @see DatabaseMetaData#getUDTs(String, String, String, int[])
     */
    @Override public ResultSet getUDTs(String catalog, String schemaPattern, String typeNamePattern, int[] types) throws SQLException {
        throw unsupportedOperation("getUDTs", catalog, schemaPattern, typeNamePattern, types);
    }

    /**
     * @see DatabaseMetaData#getVersionColumns(String, String, String)
     */
    @Override public ResultSet getVersionColumns(String catalog, String schema, String table) throws SQLException {
        throw unsupportedOperation("getVersionColumns", catalog, schema, table);
    }

    /**
     * @see AbstractJDBC#unwrap(Class)
     */
    @Override public <T> T unwrap(Class<T> iface) throws SQLFeatureNotSupportedException {
        throw unsupportedOperation("unwrap", iface);
    }
}
