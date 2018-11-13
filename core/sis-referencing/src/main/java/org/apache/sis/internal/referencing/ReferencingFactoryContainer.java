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
package org.apache.sis.internal.referencing;

import org.opengis.referencing.cs.CSFactory;
import org.opengis.referencing.crs.CRSFactory;
import org.opengis.referencing.datum.DatumFactory;
import org.opengis.referencing.operation.MathTransformFactory;
import org.opengis.referencing.operation.CoordinateOperationFactory;
import org.apache.sis.internal.system.DefaultFactories;


/**
 * A container of factories frequently used together.
 * This class may be temporary until we choose a dependency injection framework
 * See <a href="https://issues.apache.org/jira/browse/SIS-102">SIS-102</a>.
 *
 * <p>This class is not thread safe. Synchronization, if needed, is caller's responsibility.</p>
 *
 * @author  Martin Desruisseaux (IRD, Geomatys)
 * @version 1.0
 * @since   1.0
 * @module
 */
public class ReferencingFactoryContainer {
    /**
     * The {@linkplain org.opengis.referencing.datum.Datum datum} factory.
     * If null, then a default factory will be created only when first needed.
     */
    private DatumFactory datumFactory;

    /**
     * The {@linkplain org.opengis.referencing.cs.CoordinateSystem coordinate system} factory.
     * If null, then a default factory will be created only when first needed.
     */
    private CSFactory csFactory;

    /**
     * The {@linkplain org.opengis.referencing.crs.CoordinateReferenceSystem coordinate reference system} factory.
     * If null, then a default factory will be created only when first needed.
     */
    private CRSFactory crsFactory;

    /**
     * Factory for fetching operation methods and creating defining conversions.
     * This is needed only for user-defined projected coordinate reference system.
     */
    private CoordinateOperationFactory operationFactory;

    /**
     * The {@linkplain org.opengis.referencing.operation.MathTransform math transform} factory.
     * If null, then a default factory will be created only when first needed.
     */
    private MathTransformFactory mtFactory;

    /**
     * Creates a new instance for the default factories.
     */
    public ReferencingFactoryContainer() {
    }

    /**
     * Returns the factory for creating datum, prime meridians and ellipsoids.
     *
     * @return the Datum factory (never {@code null}).
     */
    public final DatumFactory getDatumFactory() {
        if (datumFactory == null) {
            datumFactory = DefaultFactories.forBuildin(DatumFactory.class);
        }
        return datumFactory;
    }

    /**
     * Returns the factory for creating coordinate systems and their axes.
     *
     * @return the Coordinate System factory (never {@code null}).
     */
    public final CSFactory getCSFactory() {
        if (csFactory == null) {
            csFactory = DefaultFactories.forBuildin(CSFactory.class);
        }
        return csFactory;
    }

    /**
     * Returns the factory for creating coordinate reference systems.
     *
     * @return the Coordinate Reference System factory (never {@code null}).
     */
    public final CRSFactory getCRSFactory() {
        if (crsFactory == null) {
            crsFactory = DefaultFactories.forBuildin(CRSFactory.class);
        }
        return crsFactory;
    }

    /**
     * Returns the factory for fetching operation methods and creating defining conversions.
     * This is needed only for user-defined projected coordinate reference system.
     * The factory is fetched when first needed.
     *
     * @return the Coordinate Operation factory (never {@code null}).
     */
    public final CoordinateOperationFactory getCoordinateOperationFactory() {
        if (operationFactory == null) {
            operationFactory = CoordinateOperations.factory();
        }
        return operationFactory;
    }

    /**
     * Returns the factory for creating parameterized transforms.
     *
     * @return the Math Transform factory (never {@code null}).
     */
    public final MathTransformFactory getMathTransformFactory() {
        if (mtFactory == null) {
            mtFactory = DefaultFactories.forBuildin(MathTransformFactory.class);
        }
        return mtFactory;
    }
}
