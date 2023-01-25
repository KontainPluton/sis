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
package org.apache.sis.internal.gui;

import javafx.scene.input.DataFormat;
import org.apache.sis.internal.storage.xml.AbstractProvider;


/**
 * A central place where to declare data formats used by SIS application.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.1
 * @since   1.1
 * @module
 */
public final class DataFormats {
    /**
     * The data format for generic XML.
     */
    public static final DataFormat XML = new DataFormat(AbstractProvider.MIME_TYPE);

    /**
     * The data format for legacy ISO 19139:2007.
     */
    public static final DataFormat ISO_19139 = new DataFormat("application/vnd.iso.19139+xml");

    /**
     * Do not allow instantiation of this class.
     */
    private DataFormats() {
    }
}
