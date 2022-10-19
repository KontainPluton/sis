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

import org.apache.sis.internal.shapefile.jdbc.SQLConnectionClosedException;
import org.apache.sis.internal.shapefile.jdbc.connection.DBFConnection;
import org.apache.sis.internal.shapefile.jdbc.resultset.*;
import org.apache.sis.internal.shapefile.jdbc.statement.DBFStatement;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Objects;

/**
 * Database Metadata.
 * @author Marc LE BIHAN
 */
public class DBFDatabaseMetaData extends AbstractDatabaseMetaData {
    /** Connection. */
    private DBFConnection connection;

    /**
     * Construct a database Metadata.
     * @param cnt Connection.
     */
    public DBFDatabaseMetaData(DBFConnection cnt) {
        Objects.requireNonNull(cnt, "The database connection used to create Database metadata cannot be null.");
        this.connection = cnt;
    }

    /**
     * @see org.apache.sis.internal.shapefile.jdbc.AbstractJDBC#isWrapperFor(Class)
     */
    @Override public boolean isWrapperFor(Class<?> iface) {
        logStep("isWrapperFor", iface);
        return getInterface().isAssignableFrom(iface);
    }

    /**
     * @see DatabaseMetaData#allProceduresAreCallable()
     */
    @Override public boolean allProceduresAreCallable() {
        logStep("allProceduresAreCallable");
        return false;
    }

    /**
     * @see DatabaseMetaData#allTablesAreSelectable()
     */
    @Override public boolean allTablesAreSelectable() {
        logStep("allTablesAreSelectable");
        return true;
    }

    /**
     * @see DatabaseMetaData#getColumns(String, String, String, String)
     * @throws SQLConnectionClosedException if the connection is closed.
     */
    @Override
    public ResultSet getColumns(@SuppressWarnings("unused") String catalog, @SuppressWarnings("unused") String schemaPattern, @SuppressWarnings("unused") String tableNamePattern, @SuppressWarnings("unused") String columnNamePattern) throws SQLConnectionClosedException {
        try(DBFStatement stmt = (DBFStatement)this.connection.createStatement()) {
            return new DBFBuiltInMemoryResultSetForColumnsListing(stmt, this.connection.getFieldsDescriptors());
        }
    }

    /**
     * Returns the Database File.
     * @return Database File.
     */
    @Override
    public File getFile() {
        return this.connection.getFile();
    }

    /**
     * @see DatabaseMetaData#getURL()
     */
    @Override public String getURL() {
        logStep("getURL");
        return getFile().getAbsolutePath();
    }

    /**
     * @see DatabaseMetaData#getUserName()
     */
    @Override public String getUserName() {
        logStep("getUserName");
        return null;
    }

    /**
     * @see DatabaseMetaData#isReadOnly()
     */
    @Override public boolean isReadOnly() {
        logStep("isReadOnly");
        return false;
    }

    /**
     * @see DatabaseMetaData#nullsAreSortedHigh()
     */
    @Override public boolean nullsAreSortedHigh() {
        logStep("nullsAreSortedHigh");
        return false; // TODO : Check in documentation about this.
    }

    /**
     * @see DatabaseMetaData#nullsAreSortedLow()
     */
    @Override public boolean nullsAreSortedLow() {
        logStep("nullsAreSortedLow");
        return false; // TODO : Check in documentation about this.
    }

    /**
     * @see DatabaseMetaData#nullsAreSortedAtStart()
     */
    @Override public boolean nullsAreSortedAtStart() {
        logStep("nullsAreSortedAtStart");
        return false; // TODO : Check in documentation about this.
    }

    /**
     * @see DatabaseMetaData#nullsAreSortedAtEnd()
     */
    @Override public boolean nullsAreSortedAtEnd() {
        logStep("nullsAreSortedAtEnd");
        return false; // TODO : Check in documentation about this.
    }

    /**
     * @see DatabaseMetaData#getDatabaseProductName()
     */
    @Override public String getDatabaseProductName() {
        logStep("getDatabaseProductName");
        return "DBase 3";
    }

    /**
     * @see DatabaseMetaData#getDatabaseProductVersion()
     */
    @Override public String getDatabaseProductVersion() {
        logStep("getDatabaseProductVersion");
        return "3";
    }

    /**
     * @see DatabaseMetaData#getDriverName()
     */
    @Override public String getDriverName() {
        logStep("getDriverName");
        return "Apache SIS DBase 3 JDBC driver";
    }

    /**
     * @see DatabaseMetaData#getDriverVersion()
     */
    @Override public String getDriverVersion() {
        logStep("getDriverVersion");
        return "1.0";
    }

    /**
     * @see DatabaseMetaData#getDriverMajorVersion()
     */
    @Override public int getDriverMajorVersion() {
        logStep("getDriverMajorVersion");
        return 1;
    }

    /**
     * @see DatabaseMetaData#getDriverMinorVersion()
     */
    @Override public int getDriverMinorVersion() {
        logStep("getDriverMinorVersion");
        return 0;
    }

    /**
     * @see DatabaseMetaData#usesLocalFiles()
     */
    @Override public boolean usesLocalFiles() {
        logStep("usesLocalFiles");
        return true;
    }

    /**
     * @see DatabaseMetaData#usesLocalFilePerTable()
     */
    @Override public boolean usesLocalFilePerTable() {
        logStep("usesLocalFilePerTable");
        return true;
    }

    /**
     * @see DatabaseMetaData#supportsMixedCaseIdentifiers()
     */
    @Override public boolean supportsMixedCaseIdentifiers() {
        logStep("supportsMixedCaseIdentifiers");
        return true;
    }

    /**
     * @see DatabaseMetaData#storesUpperCaseIdentifiers()
     */
    @Override public boolean storesUpperCaseIdentifiers() {
        logStep("storesUpperCaseIdentifiers");
        return true;
    }

    /**
     * @see DatabaseMetaData#storesLowerCaseIdentifiers()
     */
    @Override public boolean storesLowerCaseIdentifiers() {
        logStep("storesLowerCaseIdentifiers");
        return true;
    }

    /**
     * @see DatabaseMetaData#storesMixedCaseIdentifiers()
     */
    @Override public boolean storesMixedCaseIdentifiers() {
        logStep("storesMixedCaseIdentifiers");
        return true;
    }

    /**
     * @see DatabaseMetaData#supportsMixedCaseQuotedIdentifiers()
     */
    @Override public boolean supportsMixedCaseQuotedIdentifiers() {
        logStep("supportsMixedCaseQuotedIdentifiers");
        return true;
    }

    /**
     * @see DatabaseMetaData#storesUpperCaseQuotedIdentifiers()
     */
    @Override public boolean storesUpperCaseQuotedIdentifiers() {
        logStep("storesUpperCaseQuotedIdentifiers");
        return true;
    }

    /**
     * @see DatabaseMetaData#storesLowerCaseQuotedIdentifiers()
     */
    @Override public boolean storesLowerCaseQuotedIdentifiers() {
        logStep("storesLowerCaseQuotedIdentifiers");
        return true;
    }

    /**
     * @see DatabaseMetaData#storesMixedCaseQuotedIdentifiers()
     */
    @Override public boolean storesMixedCaseQuotedIdentifiers() {
        logStep("storesMixedCaseQuotedIdentifiers");
        return true;
    }

    /**
     * @see DatabaseMetaData#getIdentifierQuoteString()
     */
    @Override public String getIdentifierQuoteString() {
        logStep("getIdentifierQuoteString");
        return " ";
    }

    /**
     * @see DatabaseMetaData#getSQLKeywords()
     */
    @Override public String getSQLKeywords() {
        logStep("getSQLKeywords");
        return ""; // We don't have special Keywords yet.
    }

    /**
     * @see DatabaseMetaData#getNumericFunctions()
     */
    @Override public String getNumericFunctions() {
        logStep("getNumericFunctions");
        return "";
    }

    /**
     * @see DatabaseMetaData#getStringFunctions()
     */
    @Override public String getStringFunctions() {
        logStep("getStringFunctions");
        return "";
    }

    /**
     * @see DatabaseMetaData#getSystemFunctions()
     */
    @Override public String getSystemFunctions() {
        logStep("getSystemFunctions");
        return "";
    }

    /**
     * @see DatabaseMetaData#getTimeDateFunctions()
     */
    @Override public String getTimeDateFunctions() {
        logStep("getTimeDateFunctions");
        return "";
    }

    /**
     * @see DatabaseMetaData#getExtraNameCharacters()
     */
    @Override public String getExtraNameCharacters() {
        logStep("getExtraNameCharacters");
        return "";
    }

    /**
     * @see DatabaseMetaData#supportsAlterTableWithAddColumn()
     */
    @Override public boolean supportsAlterTableWithAddColumn() {
        logStep("supportsAlterTableWithAddColumn");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsAlterTableWithDropColumn()
     */
    @Override public boolean supportsAlterTableWithDropColumn() {
        logStep("supportsAlterTableWithDropColumn");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsColumnAliasing()
     */
    @Override public boolean supportsColumnAliasing() {
        logStep("supportsColumnAliasing");
        return false;
    }

    /**
     * @see DatabaseMetaData#nullPlusNonNullIsNull()
     */
    @Override public boolean nullPlusNonNullIsNull() {
        logStep("nullPlusNonNullIsNull");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsConvert()
     */
    @Override public boolean supportsConvert() {
        logStep("supportsConvert");
        return false; // We can promote internally types, but not offer the keyword.
    }

    /**
     * @see DatabaseMetaData#supportsConvert(int, int)
     */
    @Override public boolean supportsConvert(int fromType, int toType) {
        logStep("supportsConvert", fromType, toType);
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsTableCorrelationNames()
     */
    @Override public boolean supportsTableCorrelationNames() {
        logStep("supportsTableCorrelationNames");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsDifferentTableCorrelationNames()
     */
    @Override public boolean supportsDifferentTableCorrelationNames() {
        logStep("supportsDifferentTableCorrelationNames");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsExpressionsInOrderBy()
     */
    @Override public boolean supportsExpressionsInOrderBy() {
        logStep("supportsExpressionsInOrderBy");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsOrderByUnrelated()
     */
    @Override public boolean supportsOrderByUnrelated() {
        logStep("supportsOrderByUnrelated");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsGroupBy()
     */
    @Override public boolean supportsGroupBy() {
        logStep("supportsGroupBy");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsGroupByUnrelated()
     */
    @Override public boolean supportsGroupByUnrelated() {
        logStep("supportsGroupByUnrelated");
        return false;
    }
    /**
     * @see DatabaseMetaData#supportsGroupByBeyondSelect()
     */
    @Override public boolean supportsGroupByBeyondSelect() {
        logStep("supportsGroupByBeyondSelect");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsLikeEscapeClause()
     */
    @Override public boolean supportsLikeEscapeClause() {
        logStep("supportsLikeEscapeClause");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsMultipleResultSets()
     */
    @Override public boolean supportsMultipleResultSets() {
        logStep("supportsMultipleResultSets");
        return false; // Even if the code allow creating multiple ResultSet from a statement.
    }

    /**
     * @see DatabaseMetaData#supportsMultipleTransactions()
     */
    @Override public boolean supportsMultipleTransactions() {
        logStep("supportsMultipleTransactions");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsNonNullableColumns()
     */
    @Override public boolean supportsNonNullableColumns() {
        logStep("supportsNonNullableColumns");
        return false; // TODO Check in documentation.
    }

    /**
     * @see DatabaseMetaData#supportsMinimumSQLGrammar()
     */
    @Override public boolean supportsMinimumSQLGrammar() {
        logStep("supportsMinimumSQLGrammar");
        return false; // Check what is the ODBC SQL minimum grammar.
    }

    /**
     * @see DatabaseMetaData#supportsCoreSQLGrammar()
     */
    @Override public boolean supportsCoreSQLGrammar() {
        logStep("supportsCoreSQLGrammar");
        return false; // Check what is the core SQL grammar.
    }

    /**
     * @see DatabaseMetaData#supportsExtendedSQLGrammar()
     */
    @Override public boolean supportsExtendedSQLGrammar() {
        logStep("supportsExtendedSQLGrammar");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsANSI92EntryLevelSQL()
     */
    @Override public boolean supportsANSI92EntryLevelSQL() {
        logStep("supportsANSI92EntryLevelSQL");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsANSI92IntermediateSQL()
     */
    @Override public boolean supportsANSI92IntermediateSQL() {
        logStep("supportsANSI92IntermediateSQL");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsANSI92FullSQL()
     */
    @Override public boolean supportsANSI92FullSQL() {
        logStep("supportsANSI92FullSQL");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsIntegrityEnhancementFacility()
     */
    @Override public boolean supportsIntegrityEnhancementFacility() {
        logStep("supportsIntegrityEnhancementFacility");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsOuterJoins()
     */
    @Override public boolean supportsOuterJoins() {
        logStep("supportsOuterJoins");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsFullOuterJoins()
     */
    @Override public boolean supportsFullOuterJoins() {
        logStep("supportsFullOuterJoins");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsLimitedOuterJoins()
     */
    @Override public boolean supportsLimitedOuterJoins() {
        logStep("supportsLimitedOuterJoins");
        return false;
    }

    /**
     * @see DatabaseMetaData#getSchemaTerm()
     */
    @Override public String getSchemaTerm() {
        logStep("getSchemaTerm");
        return "";
    }

    /**
     * @see DatabaseMetaData#getProcedureTerm()
     */
    @Override public String getProcedureTerm() {
        logStep("getProcedureTerm");
        return "";
    }

    /**
     * @see DatabaseMetaData#getCatalogTerm()
     */
    @Override public String getCatalogTerm() {
        logStep("getCatalogTerm");
        return "";
    }

    /**
     * @see DatabaseMetaData#isCatalogAtStart()
     */
    @Override public boolean isCatalogAtStart() {
        logStep("isCatalogAtStart");
        return false;
    }

    /**
     * @see DatabaseMetaData#getCatalogSeparator()
     */
    @Override public String getCatalogSeparator() {
        logStep("getCatalogSeparator");
        return "";
    }

    /**
     * @see DatabaseMetaData#supportsSchemasInDataManipulation()
     */
    @Override public boolean supportsSchemasInDataManipulation() {
        logStep("supportsSchemasInDataManipulation");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsSchemasInProcedureCalls()
     */
    @Override public boolean supportsSchemasInProcedureCalls() {
        logStep("supportsSchemasInProcedureCalls");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsSchemasInTableDefinitions()
     */
    @Override public boolean supportsSchemasInTableDefinitions() {
        logStep("supportsSchemasInTableDefinitions");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsSchemasInIndexDefinitions()
     */
    @Override public boolean supportsSchemasInIndexDefinitions() {
        logStep("supportsSchemasInIndexDefinitions");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsSchemasInPrivilegeDefinitions()
     */
    @Override public boolean supportsSchemasInPrivilegeDefinitions() {
        logStep("supportsSchemasInPrivilegeDefinitions");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsCatalogsInDataManipulation()
     */
    @Override public boolean supportsCatalogsInDataManipulation() {
        logStep("supportsCatalogsInDataManipulation");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsCatalogsInProcedureCalls()
     */
    @Override public boolean supportsCatalogsInProcedureCalls() {
        logStep("supportsCatalogsInProcedureCalls");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsCatalogsInTableDefinitions()
     */
    @Override public boolean supportsCatalogsInTableDefinitions() {
        logStep("supportsCatalogsInTableDefinitions");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsCatalogsInIndexDefinitions()
     */
    @Override public boolean supportsCatalogsInIndexDefinitions() {
        logStep("supportsCatalogsInIndexDefinitions");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsCatalogsInPrivilegeDefinitions()
     */
    @Override public boolean supportsCatalogsInPrivilegeDefinitions() {
        logStep("supportsCatalogsInPrivilegeDefinitions");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsPositionedDelete()
     */
    @Override public boolean supportsPositionedDelete() {
        logStep("supportsPositionedDelete");
        return false; // TODO not yet, but might later.
    }

    /**
     * @see DatabaseMetaData#supportsPositionedUpdate()
     */
    @Override public boolean supportsPositionedUpdate() {
        logStep("supportsPositionedUpdate");
        return false; // TODO not yet, but might later.
    }

    /**
     * @see DatabaseMetaData#supportsSelectForUpdate()
     */
    @Override public boolean supportsSelectForUpdate() {
        logStep("supportsSelectForUpdate");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsStoredProcedures()
     */
    @Override public boolean supportsStoredProcedures() {
        logStep("supportsStoredProcedures");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsSubqueriesInComparisons()
     */
    @Override public boolean supportsSubqueriesInComparisons() {
        logStep("supportsStoredProcedures");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsSubqueriesInExists()
     */
    @Override public boolean supportsSubqueriesInExists() {
        logStep("supportsSubqueriesInExists");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsSubqueriesInIns()
     */
    @Override public boolean supportsSubqueriesInIns() {
        logStep("supportsSubqueriesInIns");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsSubqueriesInQuantifieds()
     */
    @Override public boolean supportsSubqueriesInQuantifieds() {
        logStep("supportsSubqueriesInQuantifieds");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsCorrelatedSubqueries()
     */
    @Override public boolean supportsCorrelatedSubqueries() {
        logStep("supportsCorrelatedSubqueries");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsUnion()
     */
    @Override public boolean supportsUnion() {
        logStep("supportsUnion");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsUnionAll()
     */
    @Override public boolean supportsUnionAll() {
        logStep("supportsUnionAll");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsOpenCursorsAcrossCommit()
     */
    @Override public boolean supportsOpenCursorsAcrossCommit() {
        logStep("supportsOpenCursorsAcrossCommit");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsOpenCursorsAcrossRollback()
     */
    @Override public boolean supportsOpenCursorsAcrossRollback() {
        logStep("supportsOpenCursorsAcrossRollback");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsOpenStatementsAcrossCommit()
     */
    @Override public boolean supportsOpenStatementsAcrossCommit() {
        logStep("supportsOpenStatementsAcrossCommit");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsOpenStatementsAcrossRollback()
     */
    @Override public boolean supportsOpenStatementsAcrossRollback() {
        logStep("supportsOpenStatementsAcrossRollback");
        return false;
    }

    /**
     * @see DatabaseMetaData#getMaxBinaryLiteralLength()
     */
    @Override public int getMaxBinaryLiteralLength() {
        logStep("getMaxBinaryLiteralLength");
        return 0;
    }

    /**
     * @see DatabaseMetaData#getMaxCharLiteralLength()
     */
    @Override public int getMaxCharLiteralLength() {
        logStep("getMaxCharLiteralLength");
        return 254;
    }

    /**
     * @see DatabaseMetaData#getMaxColumnNameLength()
     */
    @Override public int getMaxColumnNameLength() {
        logStep("getMaxColumnNameLength");
        return 10;
    }

    /**
     * @see DatabaseMetaData#getMaxColumnsInGroupBy()
     */
    @Override public int getMaxColumnsInGroupBy() {
        logStep("getMaxColumnsInGroupBy");
        return 0;
    }

    /**
     * @see DatabaseMetaData#getMaxColumnsInIndex()
     */
    @Override public int getMaxColumnsInIndex() {
        logStep("getMaxColumnsInIndex");
        return 0;
    }

    /**
     * @see DatabaseMetaData#getMaxColumnsInOrderBy()
     */
    @Override public int getMaxColumnsInOrderBy() {
        logStep("getMaxColumnsInOrderBy");
        return 0;
    }

    /**
     * @see DatabaseMetaData#getMaxColumnsInSelect()
     */
    @Override public int getMaxColumnsInSelect() {
        logStep("getMaxColumnsInSelect");
        return 0;
    }

    /**
     * @see DatabaseMetaData#getMaxColumnsInTable()
     */
    @Override public int getMaxColumnsInTable() {
        logStep("getMaxColumnsInTable");
        return 0;
    }

    /**
     * @see DatabaseMetaData#getMaxConnections()
     */
    @Override public int getMaxConnections() {
        logStep("getMaxConnections");
        return 0;
    }

    /**
     * @see DatabaseMetaData#getMaxCursorNameLength()
     */
    @Override public int getMaxCursorNameLength() {
        logStep("getMaxCursorNameLength");
        return 0;
    }

    /**
     * @see DatabaseMetaData#getMaxIndexLength()
     */
    @Override public int getMaxIndexLength() {
        logStep("getMaxIndexLength");
        return 0;
    }

    /**
     * @see DatabaseMetaData#getMaxSchemaNameLength()
     */
    @Override public int getMaxSchemaNameLength() {
        logStep("getMaxSchemaNameLength");
        return 0;
    }

    /**
     * @see DatabaseMetaData#getMaxProcedureNameLength()
     */
    @Override public int getMaxProcedureNameLength() {
        logStep("getMaxProcedureNameLength");
        return 0;
    }

    /**
     * @see DatabaseMetaData#getMaxCatalogNameLength()
     */
    @Override public int getMaxCatalogNameLength() {
        logStep("getMaxCatalogNameLength");
        return 0;
    }

    /**
     * @see DatabaseMetaData#getMaxRowSize()
     */
    @Override public int getMaxRowSize() {
        logStep("getMaxRowSize");
        return 0;
    }

    /**
     * @see DatabaseMetaData#doesMaxRowSizeIncludeBlobs()
     */
    @Override public boolean doesMaxRowSizeIncludeBlobs() {
        logStep("doesMaxRowSizeIncludeBlobs");
        return false;
    }

    /**
     * @see DatabaseMetaData#getMaxStatementLength()
     */
    @Override public int getMaxStatementLength() {
        logStep("getMaxStatementLength");
        return 0;
    }

    /**
     * @see DatabaseMetaData#getMaxStatements()
     */
    @Override public int getMaxStatements() {
        logStep("getMaxStatements");
        return 0;
    }

    /**
     * @see DatabaseMetaData#getMaxTableNameLength()
     */
    @Override public int getMaxTableNameLength() {
        logStep("getMaxTableNameLength");
        return 0;
    }

    /**
     * @see DatabaseMetaData#getMaxTablesInSelect()
     */
    @Override public int getMaxTablesInSelect() {
        logStep("getMaxTablesInSelect");
        return 1; // We only handle one table at this time.
    }

    /**
     * @see DatabaseMetaData#getMaxUserNameLength()
     */
    @Override public int getMaxUserNameLength() {
        logStep("getMaxUserNameLength");
        return 0;
    }

    /**
     * @see DatabaseMetaData#getDefaultTransactionIsolation()
     */
    @Override public int getDefaultTransactionIsolation() {
        logStep("getDefaultTransactionIsolation");
        return 0; // No guaranties of anything.
    }

    /**
     * @see DatabaseMetaData#supportsTransactions()
     */
    @Override public boolean supportsTransactions() {
        logStep("supportsTransactions");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsTransactionIsolationLevel(int)
     */
    @Override public boolean supportsTransactionIsolationLevel(int level) {
        logStep("supportsTransactionIsolationLevel", level);
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsDataDefinitionAndDataManipulationTransactions()
     */
    @Override public boolean supportsDataDefinitionAndDataManipulationTransactions() {
        logStep("supportsDataDefinitionAndDataManipulationTransactions");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsDataManipulationTransactionsOnly()
     */
    @Override public boolean supportsDataManipulationTransactionsOnly() {
        logStep("supportsDataManipulationTransactionsOnly");
        return false;
    }

    /**
     * @see DatabaseMetaData#dataDefinitionCausesTransactionCommit()
     */
    @Override public boolean dataDefinitionCausesTransactionCommit() {
        logStep("dataDefinitionCausesTransactionCommit");
        return false;
    }

    /**
     * @see DatabaseMetaData#dataDefinitionIgnoredInTransactions()
     */
    @Override public boolean dataDefinitionIgnoredInTransactions() {
        logStep("dataDefinitionIgnoredInTransactions");
        return false;
    }

    /**
     * @see DatabaseMetaData#getTables(String, String, String, String[])
     */
    @SuppressWarnings("resource") // The statement will be closed by the caller.
    @Override public ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] types) {
        logStep("getTables", catalog, schemaPattern, tableNamePattern, types != null ? Arrays.asList(types) : null);

        DBFStatement stmt = new DBFStatement(this.connection);
        DBFBuiltInMemoryResultSetForTablesListing tables = new DBFBuiltInMemoryResultSetForTablesListing(stmt);
        stmt.registerResultSet(tables);
        return tables;
    }

    /**
     * @see DatabaseMetaData#getSchemas()
     */
    @SuppressWarnings("resource") // The statement will be closed by the caller.
    @Override public ResultSet getSchemas() {
        logStep("getSchemas");

        DBFStatement stmt = new DBFStatement(this.connection);
        DBFBuiltInMemoryResultSetForSchemaListing schemas = new DBFBuiltInMemoryResultSetForSchemaListing(stmt);
        stmt.registerResultSet(schemas);
        return schemas;
    }

    /**
     * @see DatabaseMetaData#getCatalogs()
     */
    @SuppressWarnings("resource") // The statement will be closed by the caller.
    @Override public ResultSet getCatalogs() {
        logStep("getCatalogs");

        DBFStatement stmt = new DBFStatement(this.connection);
        DBFBuiltInMemoryResultSetForCatalogNamesListing catalogNames = new DBFBuiltInMemoryResultSetForCatalogNamesListing(stmt);
        stmt.registerResultSet(catalogNames);
        return catalogNames;
    }

    /**
     * @see DatabaseMetaData#getTableTypes()
     */
    @SuppressWarnings("resource") // The statement will be closed by the caller.
    @Override public ResultSet getTableTypes() {
        logStep("getTableTypes");

        DBFStatement stmt = new DBFStatement(this.connection);
        DBFBuiltInMemoryResultSetForTablesTypesListing tablesTypes = new DBFBuiltInMemoryResultSetForTablesTypesListing(stmt);
        stmt.registerResultSet(tablesTypes);
        return tablesTypes;
    }

    /**
     * @see DatabaseMetaData#supportsResultSetType(int)
     */
    @Override public boolean supportsResultSetType(int type) {
        logStep("supportsResultSetType", type);

        switch(type) {
            case ResultSet.FETCH_FORWARD:
            case ResultSet.FETCH_UNKNOWN:
            case ResultSet.TYPE_FORWARD_ONLY:
            return true;

            default :
                return false;
        }
    }

    /**
     * @see DatabaseMetaData#supportsResultSetConcurrency(int, int)
     */
    @Override public boolean supportsResultSetConcurrency(int type, int concurrency) {
        logStep("supportsResultSetConcurrency", type, concurrency);
        return false;
    }

    /**
     * @see DatabaseMetaData#ownUpdatesAreVisible(int)
     */
    @Override public boolean ownUpdatesAreVisible(int type) {
        logStep("ownUpdatesAreVisible", type);
        return false;
    }

    /**
     * @see DatabaseMetaData#ownDeletesAreVisible(int)
     */
    @Override public boolean ownDeletesAreVisible(int type) {
        logStep("ownDeletesAreVisible", type);
        return false;
    }

    /**
     * @see DatabaseMetaData#ownInsertsAreVisible(int)
     */
    @Override public boolean ownInsertsAreVisible(int type) {
        logStep("ownInsertsAreVisible", type);
        return false;
    }

    /**
     * @see DatabaseMetaData#othersUpdatesAreVisible(int)
     */
    @Override public boolean othersUpdatesAreVisible(int type) {
        logStep("othersUpdatesAreVisible", type);
        return false;
    }

    /**
     * @see DatabaseMetaData#othersDeletesAreVisible(int)
     */
    @Override public boolean othersDeletesAreVisible(int type) {
        logStep("othersDeletesAreVisible", type);
        return false;
    }

    /**
     * @see DatabaseMetaData#othersInsertsAreVisible(int)
     */
    @Override public boolean othersInsertsAreVisible(int type) {
        logStep("othersInsertsAreVisible", type);
        return false;
    }

    /**
     * @see DatabaseMetaData#updatesAreDetected(int)
     */
    @Override public boolean updatesAreDetected(int type) {
        logStep("updatesAreDetected", type);
        return false;
    }

    /**
     * @see DatabaseMetaData#deletesAreDetected(int)
     */
    @Override public boolean deletesAreDetected(int type) {
        logStep("deletesAreDetected", type);
        return false;
    }

    /**
     * @see DatabaseMetaData#insertsAreDetected(int)
     */
    @Override public boolean insertsAreDetected(int type) {
        logStep("insertsAreDetected", type);
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsBatchUpdates()
     */
    @Override public boolean supportsBatchUpdates() {
        logStep("supportsBatchUpdates");
        return false;
    }

    /**
     * @see DatabaseMetaData#getConnection()
     */
    @Override public Connection getConnection() {
        logStep("getConnection");
        return this.connection;
    }

    /**
     * @see DatabaseMetaData#supportsSavepoints()
     */
    @Override public boolean supportsSavepoints() {
        logStep("supportsSavepoints");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsNamedParameters()
     */
    @Override public boolean supportsNamedParameters() {
        logStep("supportsNamedParameters");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsMultipleOpenResults()
     */
    @Override public boolean supportsMultipleOpenResults() {
        logStep("supportsMultipleOpenResults");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsGetGeneratedKeys()
     */
    @Override public boolean supportsGetGeneratedKeys() {
        logStep("supportsGetGeneratedKeys");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsResultSetHoldability(int)
     */
    @Override public boolean supportsResultSetHoldability(int holdability) {
        logStep("supportsResultSetHoldability", holdability);
        return false;
    }

    /**
     * @see DatabaseMetaData#getResultSetHoldability()
     */
    @Override public int getResultSetHoldability() {
        logStep("getResultSetHoldability");
        return ResultSet.HOLD_CURSORS_OVER_COMMIT; // TODO : No matters, as we don't handle transactions.
    }

    /**
     * @see DatabaseMetaData#getDatabaseMajorVersion()
     */
    @Override public int getDatabaseMajorVersion() {
        logStep("getDatabaseMajorVersion");
        return 3;
    }

    /**
     * @see DatabaseMetaData#getDatabaseMinorVersion()
     */
    @Override public int getDatabaseMinorVersion() {
        logStep("getDatabaseMinorVersion");
        return 0;
    }

    /**
     * @see DatabaseMetaData#getJDBCMajorVersion()
     */
    @Override public int getJDBCMajorVersion() {
        logStep("getJDBCMajorVersion");
        return 1;
    }

    /**
     * @see DatabaseMetaData#getJDBCMinorVersion()
     */
    @Override public int getJDBCMinorVersion() {
        logStep("getJDBCMinorVersion");
        return 0;
    }

    /**
     * @see DatabaseMetaData#locatorsUpdateCopy()
     */
    @Override public boolean locatorsUpdateCopy() {
        logStep("locatorsUpdateCopy");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsStatementPooling()
     */
    @Override public boolean supportsStatementPooling() {
        logStep("supportsStatementPooling");
        return false;
    }

    /**
     * @see DatabaseMetaData#supportsStoredFunctionsUsingCallSyntax()
     */
    @Override public boolean supportsStoredFunctionsUsingCallSyntax() {
        logStep("supportsStoredFunctionsUsingCallSyntax");
        return false;
    }

    /**
     * @see DatabaseMetaData#autoCommitFailureClosesAllResultSets()
     */
    @Override public boolean autoCommitFailureClosesAllResultSets() {
        logStep("autoCommitFailureClosesAllResultSets");
        return false;
    }

    /**
     * @see DatabaseMetaData#generatedKeyAlwaysReturned()
     */
    @Override public boolean generatedKeyAlwaysReturned() {
        logStep("generatedKeyAlwaysReturned");
        return false;
    }

    /**
     * @see org.apache.sis.internal.shapefile.jdbc.AbstractJDBC#getInterface()
     */
    @Override protected Class<?> getInterface() {
        return DatabaseMetaData.class;
    }
}
