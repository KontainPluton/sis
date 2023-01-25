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
 * Partial implementation of SQLMM operations as filter expressions.
 * This package supports only for the simplest types (point, line string, polygon).
 * Other types (curve, circular string, compound curve, curve polygon, triangle,
 * polyhedral surface, TIN, multi curve, multi surface) are not supported.
 *
 * <p>The main public class in this package is {@link Registry},
 * which is the single entry point for all functions.</p>
 *
 * <h2>Coordinate Reference System</h2>
 * If the geometry operands use different CRS, ISO 13249 mandates that the geometric calculations
 * are done in the spatial reference system of the first geometry value in the parameter list.
 * Returns value are in the CRS or in the units of measurement of the first geometry.
 *
 * @author  Johann Sorel (Geomatys)
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.3
 * @since   1.1
 * @module
 */
package org.apache.sis.internal.filter.sqlmm;
