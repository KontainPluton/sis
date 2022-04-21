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

/**
 * Simple data store implementations for some ESRI grid formats (ASCII and binary).
 * The data formats supported by this package are relatively simple.
 * Values are stored either in plain ASCII text or in RAW binary encoding (without compression).
 *
 * <h2>Extensions</h2>
 * The implementation in this package adds the following extensions
 * (some of them are taken from GDAL):
 *
 * <ul class="verbose">
 *   <li>Coordinate reference system specified by auxiliary {@code *.prj} file.
 *       If the format is WKT 1, the GDAL variant is used (that variant differs from
 *       the OGC 01-009 standard in their interpretation of units of measurement).</li>
 *   <li>ASCII Grid reader accepts also some metadata defined for the binary formats
 *       ({@code XDIM}, {@code YDIM}, color file, statistics file, <i>etc.</i>).</li>
 * </ul>
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.2
 * @since   1.2
 * @module
 */
package org.apache.sis.internal.storage.esri;
