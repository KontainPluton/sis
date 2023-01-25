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
package org.apache.sis.internal.util;

import org.apache.sis.util.Static;


/**
 * Hard-coded URLs other than XML namespaces. Those URLs are mostly for documentation.
 * Note: other URLs are listed in the following classes:
 *
 * <ul>
 *   <li>{@link org.apache.sis.xml.Namespaces} for XML namespaces.</li>
 *   <li>{@link org.apache.sis.setup.OptionalInstallations} for location of optional data to download.</li>
 * </ul>
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.2
 * @since   1.2
 * @module
 */
public final class URLs extends Static {
    /**
     * EPSG home page.
     */
    public static final String EPSG = "https://epsg.org/";

    /**
     * EPSG terms of use.
     */
    public static final String EPSG_LICENSE = "https://epsg.org/terms-of-use.html";

    /**
     * Installation instructions for EPSG database.
     */
    public static final String EPSG_INSTALL = "https://sis.apache.org/epsg.html";

    /**
     * The Well-Known Text (WKT) 2 specification implemented by Apache SIS.
     */
    public static final String WKT_SPECIFICATION = "http://docs.opengeospatial.org/is/12-063r5/12-063r5.html";

    /**
     * Do not allow instantiation of this class.
     */
    private URLs() {
    }
}
