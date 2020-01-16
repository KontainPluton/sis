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
package org.apache.sis.internal.filter.sqlmm;

import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import java.text.ParseException;
import org.apache.sis.feature.builder.FeatureTypeBuilder;
import org.apache.sis.feature.builder.PropertyTypeBuilder;
import org.opengis.feature.FeatureType;
import org.opengis.filter.expression.Expression;

/**
 * SQL/MM, ISO/IEC 13249-3:2011, ST_InteriorRingN. <br>
 * Return the specified element in the ST_PrivateInteriorRings attribute of an ST_CurvePolygon value.
 *
 * @author Johann Sorel (Geomatys)
 * @version 2.0
 * @since   2.0
 * @module
 */
final class ST_InteriorRingN extends AbstractAccessorSpatialFunction<Polygon> {
    /**
     * For cross-version compatibility.
     */
    private static final long serialVersionUID = -8020999740090022880L;

    public static final String NAME = "ST_InteriorRingN";

    public ST_InteriorRingN(Expression... parameters) {
        super(parameters);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    protected Class<Polygon> getExpectedClass() {
        return Polygon.class;
    }

    @Override
    public Object execute(Polygon geom, Object... params) throws ParseException {
        final int index = Integer.parseInt(params[1].toString());
        final LineString ring = geom.getInteriorRingN(index-1);
        copyCrs(geom, ring);
        return ring;
    }

    @Override
    public PropertyTypeBuilder expectedType(FeatureType valueType, FeatureTypeBuilder addTo) {
        return addTo.addAttribute(LineString.class)
                .setCRS(expectedCrs(valueType, parameters.get(0)))
                .setName(NAME);
    }
}