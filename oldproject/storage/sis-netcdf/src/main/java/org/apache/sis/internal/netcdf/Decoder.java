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
package org.apache.sis.internal.netcdf;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.Objects;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import org.opengis.util.NameSpace;
import org.opengis.util.NameFactory;
import org.opengis.referencing.datum.Datum;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.apache.sis.setup.GeometryLibrary;
import org.apache.sis.storage.DataStore;
import org.apache.sis.storage.DataStoreException;
import org.apache.sis.storage.event.StoreListeners;
import org.apache.sis.util.Utilities;
import org.apache.sis.util.ComparisonMode;
import org.apache.sis.util.logging.PerformanceLevel;
import org.apache.sis.util.collection.TreeTable;
import org.apache.sis.internal.util.StandardDateFormat;
import org.apache.sis.internal.system.DefaultFactories;
import org.apache.sis.internal.system.Modules;
import org.apache.sis.internal.referencing.ReferencingFactoryContainer;
import ucar.nc2.constants.CF;


/**
 * The API used internally by Apache SIS for fetching variables and attribute values from a netCDF file.
 *
 * <p>This {@code Decoder} class and subclasses are <strong>not</strong> thread-safe.
 * Synchronizations are caller's responsibility.</p>
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.3
 * @since   0.3
 * @module
 */
public abstract class Decoder extends ReferencingFactoryContainer implements Closeable {
    /**
     * The logger to use for messages other than warnings specific to the file being read.
     */
    static final Logger LOGGER = Logger.getLogger(Modules.NETCDF);

    /**
     * The format name to use in error message. We use lower-case "n" because it seems to be what the netCDF community uses.
     * By contrast, {@code NetcdfStoreProvider} uses upper-case "N" because it is considered at the beginning of sentences.
     */
    public static final String FORMAT_NAME = "netCDF";

    /**
     * The path to the netCDF file, or {@code null} if unknown.
     * This is set by netCDF store constructor and shall not be modified afterward.
     * This is used for information purpose only, not for actual reading operation.
     */
    public Path location;

    /**
     * Conventions to apply in addition of netCDF conventions.
     * Shall never be {@code null} after {@link #initialize()}.
     *
     * @see #convention()
     */
    private Convention convention;

    /**
     * The data store identifier created from the global attributes, or {@code null} if none.
     * Defined as a namespace for use as the scope of children resources (the variables).
     * This is set by netCDF store constructor and shall not be modified afterward.
     */
    public NameSpace namespace;

    /**
     * The factory to use for creating variable identifiers.
     */
    public final NameFactory nameFactory;

    /**
     * The library for geometric objects, or {@code null} for the default.
     * This will be used only if there is geometric objects to create.
     * If the netCDF file contains only raster data, this value is ignored.
     */
    public final GeometryLibrary geomlib;

    /**
     * The geodetic datum, created when first needed. The datum are generally not specified in netCDF files.
     * To make that clearer, we will build datum with names like "Unknown datum presumably based on GRS 1980".
     * Index in the cache are one of the {@code CACHE_INDEX} constants declared in {@link CRSBuilder}.
     *
     * @see CRSBuilder#build(Decoder, boolean)
     */
    final Datum[] datumCache;

    /**
     * The CRS and <cite>grid to CRS</cite> transform defined by attributes in a variable. For example GDAL uses
     * {@code "spatial_ref_sys"} and {@code "GeoTransform"} attributes associated to a variable having the name
     * specified by the {@code "grid_mapping"} attribute.
     *
     * <p>Keys are either {@link Variable} instance for which we found a grid mapping, or {@link String} instances
     * if we found some variables with {@code "grid_mapping"} attribute values.</p>
     *
     * @see GridMapping#forVariable(Variable)
     */
    final Map<Object,GridMapping> gridMapping;

    /**
     * Cache of localization grids created for a given pair of (<var>x</var>,<var>y</var>) axes.
     * Localization grids are expensive to compute and consume a significant amount of memory.
     * The {@link Grid} instances returned by {@link #getGridCandidates()} share localization
     * grids only between variables using the exact same list of dimensions.
     * This {@code localizationGrids} cache allows to cover other cases.
     *
     * <div class="note"><b>Example:</b>
     * a netCDF file may have a variable with (<var>longitude</var>, <var>latitude</var>) dimensions and another
     * variable with (<var>longitude</var>, <var>latitude</var>, <var>depth</var>) dimensions, with both variables
     * using the same localization grid for the (<var>longitude</var>, <var>latitude</var>) part.</div>
     *
     * @see GridCacheKey#cached(Decoder)
     */
    final Map<GridCacheKey,GridCacheValue> localizationGrids;

    /**
     * Where to send the warnings.
     */
    public final StoreListeners listeners;

    /**
     * Sets to {@code true} for canceling a reading process.
     * This flag is honored on a <cite>best effort</cite> basis only.
     */
    public volatile boolean canceled;

    /**
     * Creates a new decoder.
     *
     * @param  geomlib    the library for geometric objects, or {@code null} for the default.
     * @param  listeners  where to send the warnings.
     */
    protected Decoder(final GeometryLibrary geomlib, final StoreListeners listeners) {
        Objects.requireNonNull(listeners);
        this.geomlib      = geomlib;
        this.listeners    = listeners;
        this.nameFactory  = DefaultFactories.forBuildin(NameFactory.class);
        this.datumCache   = new Datum[CRSBuilder.DATUM_CACHE_SIZE];
        this.gridMapping  = new HashMap<>();
        localizationGrids = new HashMap<>();
    }

    /**
     * Shall be invoked by subclass constructors after the finished their construction, for completing initialization.
     * This method checks if an extension to CF-convention applies to the current file.
     */
    protected final void initialize() {
        convention = Convention.find(this);
    }

    /**
     * Checks and potentially modifies the content of this dataset for conventions other than CF-conventions.
     * This method should be invoked after construction for handling the particularities of some datasets
     * (HYCOM, …).
     *
     * @throws IOException if an error occurred while reading the channel.
     * @throws DataStoreException if an error occurred while interpreting the netCDF file content.
     */
    public final void applyOtherConventions() throws IOException, DataStoreException {
        HYCOM.convert(this, getVariables());
    }

    /**
     * Returns information about modifications to apply to netCDF conventions in order to handle this netCDF file.
     * Customized conventions are necessary when the variables and attributes in a netCDF file do not follow CF-conventions.
     *
     * @return conventions to apply.
     */
    public final Convention convention() {
        // Convention are still null if this method is invoked from Convention.isApplicableTo(Decoder).
        return (convention != null) ? convention : Convention.DEFAULT;
    }

    /**
     * Adds netCDF attributes to the given node, including variables and sub-groups attributes.
     * Groups are shown first, then variables attributes, and finally global attributes.
     * Showing global attributes last is consistent with ncML ("netCDF dump") output.
     *
     * @param  root  the node where to add netCDF attributes.
     */
    public abstract void addAttributesTo(TreeTable.Node root);

    /**
     * Returns a filename for formatting error message and for information purpose.
     * The filename should not contain path, but may contain file extension.
     *
     * @return a filename to include in warnings or error messages.
     */
    public abstract String getFilename();

    /**
     * Returns an identification of the file format. This method should returns an array of length 1, 2 or 3 as below:
     *
     * <ul>
     *   <li>One of the following identifier in the first element: {@code "NetCDF"}, {@code "NetCDF-4"} or other values
     *       defined by the UCAR library. If known, it will be used as an identifier for a more complete description to
     *       be provided by {@link org.apache.sis.metadata.sql.MetadataSource#lookup(Class, String)}.</li>
     *   <li>Optionally a human-readable description in the second array element.</li>
     *   <li>Optionally a version in the third array element.</li>
     * </ul>
     *
     * @return identification of the file format, human-readable description and version number.
     */
    public abstract String[] getFormatDescription();

    /**
     * Defines the groups where to search for named attributes, in preference order.
     * The {@code null} group name stands for attributes in the root group.
     *
     * @param  groupNames  the name of the group where to search, in preference order.
     *
     * @see Convention#getSearchPath()
     */
    public abstract void setSearchPath(String... groupNames);

    /**
     * Returns the path which is currently set. The array returned by this method may be only
     * a subset of the array given to {@link #setSearchPath(String[])} since only the name of
     * groups which have been found in the netCDF file are returned by this method.
     *
     * @return the current search path.
     */
    public abstract String[] getSearchPath();

    /**
     * Returns the names of all global attributes found in the file.
     *
     * @return names of all global attributes in the file.
     */
    public abstract Collection<String> getAttributeNames();

    /**
     * Returns the value for the attribute of the given name, or {@code null} if none.
     * This method searches in the groups specified by the last call to {@link #setSearchPath(String[])}.
     * Null values and empty strings are ignored.
     *
     * @param  name  the name of the attribute to search, or {@code null}.
     * @return the attribute value, or {@code null} if none or empty or if the given name was null.
     */
    public abstract String stringValue(String name);

    /**
     * Returns the value of the attribute of the given name as a number, or {@code null} if none.
     *
     * @param  name  the name of the attribute to search, or {@code null}.
     * @return the attribute value, or {@code null} if none or unparsable or if the given name was null.
     */
    public abstract Number numericValue(String name);

    /**
     * Convenience method for {@link #numericValue(String)} implementation.
     *
     * @param  name   the attribute name, used only in case of error.
     * @param  value  the attribute value to parse.
     * @return the parsed attribute value, or {@code null} if the given value can not be parsed.
     */
    protected final Number parseNumber(final String name, String value) {
        final int s = value.indexOf(' ');
        if (s >= 0) {
            /*
             * Sometime, numeric values as string are followed by
             * a unit of measurement. We ignore that unit for now.
             */
            value = value.substring(0, s);
        }
        Number n;
        try {
            if (value.indexOf('.') >= 0) {
                n = Double.valueOf(value);
            } else {
                n = Long.valueOf(value);
            }
        } catch (NumberFormatException e) {
            illegalAttributeValue(name, value, e);
            n = null;
        }
        return n;
    }

    /**
     * Logs a warning for an illegal attribute value. This may be due to a failure to parse a string as a number.
     * This method should be invoked from methods that are invoked only once per attribute because we do not keep
     * track of which warnings have already been emitted.
     *
     * @param  name   the attribute name.
     * @param  value  the illegal value.
     * @param  e      the exception, or {@code null} if none.
     */
    final void illegalAttributeValue(final String name, final String value, final NumberFormatException e) {
        listeners.warning(resources().getString(Resources.Keys.IllegalAttributeValue_3, getFilename(), name, value), e);
    }

    /**
     * Returns the value of the attribute of the given name as a date, or {@code null} if none.
     *
     * @param  name  the name of the attribute to search, or {@code null}.
     * @return the attribute value, or {@code null} if none or unparsable or if the given name was null.
     */
    public abstract Date dateValue(String name);

    /**
     * Converts the given numerical values to date, using the information provided in the given unit symbol.
     * The unit symbol is typically a string like <cite>"days since 1970-01-01T00:00:00Z"</cite>.
     *
     * @param  symbol  the temporal unit name or symbol, followed by the epoch.
     * @param  values  the values to convert. May contains {@code null} elements.
     * @return the converted values. May contains {@code null} elements.
     */
    public abstract Date[] numberToDate(String symbol, Number... values);

    /**
     * Returns the timezone for decoding dates. Currently fixed to UTC.
     *
     * @return the timezone for dates.
     */
    public TimeZone getTimeZone() {
        return TimeZone.getTimeZone(StandardDateFormat.UTC);
    }

    /**
     * Returns the value of the {@code "_Id"} global attribute. The UCAR library defines a
     * {@link ucar.nc2.NetcdfFile#getId()} method for that purpose, which we will use when
     * possible in case that {@code getId()} method is defined in an other way.
     *
     * <p>This method is used by {@link org.apache.sis.storage.netcdf.NetcdfStore#getMetadata()} in last resort
     * when no value were found for the attributes defined by the CF standard or by THREDDS.</p>
     *
     * @return the global dataset identifier, or {@code null} if none.
     */
    public String getId() {
        return stringValue("_Id");
    }

    /**
     * Returns the value of the {@code "_Title"} global attribute. The UCAR library defines a
     * {@link ucar.nc2.NetcdfFile#getTitle()} method for that purpose, which we will use when
     * possible in case that {@code getTitle()} method is defined in an other way.
     *
     * <p>This method is used by {@link org.apache.sis.storage.netcdf.NetcdfStore#getMetadata()} in last resort
     * when no value were found for the attributes defined by the CF standard or by THREDDS.</p>
     *
     * @return the dataset title, or {@code null} if none.
     */
    public String getTitle() {
        return stringValue("_Title");
    }

    /**
     * Returns all variables found in the netCDF file.
     * This method may return a direct reference to an internal array - do not modify.
     *
     * @return all variables, or an empty array if none.
     */
    public abstract Variable[] getVariables();

    /**
     * If the file contains features encoded as discrete sampling (for example profiles or trajectories),
     * returns objects for handling them. This method does not need to cache the returned array, because
     * it will be invoked only once by {@link org.apache.sis.storage.netcdf.NetcdfStore#components()}.
     *
     * @param  lock  the lock to use in {@code synchronized(lock)} statements.
     * @return a handler for the features, or an empty array if none.
     * @throws IOException if an I/O operation was necessary but failed.
     * @throws DataStoreException if a logical error occurred.
     */
    public DiscreteSampling[] getDiscreteSampling(final DataStore lock) throws IOException, DataStoreException {
        final String type = stringValue(CF.FEATURE_TYPE);
        if (type == null || type.equalsIgnoreCase(FeatureSet.TRAJECTORY)) try {
            return FeatureSet.create(this, lock);
        } catch (IllegalArgumentException | ArithmeticException e) {
            // Illegal argument is not a problem with content, but rather with configuration.
            throw new DataStoreException(e.getLocalizedMessage(), e);
        }
        return new FeatureSet[0];
    }

    /**
     * Returns all grid geometries (related to coordinate systems) found in the netCDF file.
     * This method may return a direct reference to an internal array - do not modify.
     *
     * <p>The number of grid geometries returned by this method may be greater that the actual number of
     * grids in the file. A more extensive analysis is done by {@link Variable#findGrid(GridAdjustment)},
     * which may result in some grid candidates being filtered out.</p>
     *
     * @return all grid geometries, or an empty array if none.
     * @throws IOException if an I/O operation was necessary but failed.
     * @throws DataStoreException if a logical error occurred.
     */
    public abstract Grid[] getGridCandidates() throws IOException, DataStoreException;

    /**
     * Returns for information purpose only the Coordinate Reference Systems present in this file.
     * The CRS returned by this method may not be exactly the same than the ones used by variables.
     * For example, axis order is not guaranteed. This method is provided for metadata purposes.
     *
     * @return coordinate reference systems present in this file.
     * @throws IOException if an I/O operation was necessary but failed.
     * @throws DataStoreException if a logical error occurred.
     */
    public final List<CoordinateReferenceSystem> getReferenceSystemInfo() throws IOException, DataStoreException {
        final List<CoordinateReferenceSystem> list = new ArrayList<>();
        for (final Variable variable : getVariables()) {
            final GridMapping m = GridMapping.forVariable(variable);
            if (m != null) {
                addIfNotPresent(list, m.crs);
            }
        }
        /*
         * Add the CRS computed by grids only if we did not found any grid mapping information.
         * This is because grid mapping information override the CRS inferred by Grid from axes.
         * Consequently if such information is present, grid CRS may be inaccurate.
         */
        if (list.isEmpty()) {
            final List<Exception> warnings = new ArrayList<>();     // For internal usage by Grid.
            for (final Grid grid : getGridCandidates()) {
                addIfNotPresent(list, grid.getCoordinateReferenceSystem(this, warnings, null, null));
            }
        }
        return list;
    }

    /**
     * Adds the given coordinate reference system to the given list, provided that an equivalent CRS
     * (ignoring axes) is not already present. We ignore axes because the same CRS may be repeated
     * with different axis order if values in the localization grid do not vary at the same speed in
     * the same directions.
     */
    private static void addIfNotPresent(final List<CoordinateReferenceSystem> list, final CoordinateReferenceSystem crs) {
        if (crs != null) {
            for (int i=list.size(); --i >= 0;) {
                if (Utilities.deepEquals(crs, list.get(i), ComparisonMode.ALLOW_VARIANT)) {
                    return;
                }
            }
            list.add(crs);
        }
    }

    /**
     * Returns the dimension of the given name (eventually ignoring case), or {@code null} if none.
     * This method searches in all dimensions found in the netCDF file, regardless of variables.
     *
     * @param  dimName  the name of the dimension to search.
     * @return dimension of the given name, or {@code null} if none.
     */
    protected abstract Dimension findDimension(String dimName);

    /**
     * Returns the netCDF variable of the given name, or {@code null} if none.
     *
     * @param  name  the name of the variable to search, or {@code null}.
     * @return the variable of the given name, or {@code null} if none.
     *
     * @see #getVariables()
     */
    protected abstract Variable findVariable(String name);

    /**
     * Returns the variable or group of the given name. Groups exist in netCDF 4 but not in netCDF 3.
     *
     * @param  name  name of the variable or group to search.
     * @return the variable or group of the given name, or {@code null} if none.
     */
    protected abstract Node findNode(String name);

    /**
     * Logs a message about a potentially slow operation. This method does use the listeners registered to the netCDF reader
     * because this is not a warning.
     *
     * @param  caller       the class to report as the source.
     * @param  method       the method to report as the source.
     * @param  resourceKey  a {@link Resources} key expecting filename as first argument and elapsed time as second argument.
     * @param  time         value of {@link System#nanoTime()} when the operation started.
     */
    final void performance(final Class<?> caller, final String method, final short resourceKey, long time) {
        time = System.nanoTime() - time;
        final Level level = PerformanceLevel.forDuration(time, TimeUnit.NANOSECONDS);
        if (LOGGER.isLoggable(level)) {
            final LogRecord record = resources().getLogRecord(level, resourceKey,
                    getFilename(), time / (double) StandardDateFormat.NANOS_PER_SECOND);
            record.setLoggerName(Modules.NETCDF);
            record.setSourceClassName(caller.getCanonicalName());
            record.setSourceMethodName(method);
            LOGGER.log(record);
        }
    }

    /**
     * Returns the netCDF-specific resource bundle for the locale given by {@link StoreListeners#getLocale()}.
     *
     * @return the localized error resource bundle.
     */
    final Resources resources() {
        return Resources.forLocale(listeners.getLocale());
    }
}
