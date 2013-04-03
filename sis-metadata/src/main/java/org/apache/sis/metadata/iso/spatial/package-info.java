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
 * {@linkplain org.apache.sis.metadata.iso.spatial.AbstractSpatialRepresentation Spatial representation} implementation.
 * An explanation for this package is provided in the {@linkplain org.opengis.metadata.spatial OpenGIS® javadoc}.
 * The remaining discussion on this page is specific to the SIS implementation.
 *
 * {@section Overview}
 * For a global overview of metadata in SIS, see the
 * <a href="{@docRoot}/../sis-metadata/index.html">Metadata page on the project web site</a>.
 *
 * <table class="sis"><tr>
 *   <th>Class hierarchy</th>
 *   <th class="sep">Aggregation hierarchy</th>
 * </tr><tr><td width="50%" nowrap>
 * {@linkplain org.apache.sis.metadata.iso.ISOMetadata ISO-19115 metadata}<br>
 * {@code  ├─}         {@linkplain org.apache.sis.metadata.iso.spatial.AbstractSpatialRepresentation      Spatial representation} «abstract»<br>
 * {@code  │   ├─}     {@linkplain org.apache.sis.metadata.iso.spatial.DefaultVectorSpatialRepresentation Vector spatial representation}<br>
 * {@code  │   └─}     {@linkplain org.apache.sis.metadata.iso.spatial.DefaultGridSpatialRepresentation   Grid spatial representation}<br>
 * {@code  │       ├─} {@linkplain org.apache.sis.metadata.iso.spatial.DefaultGeoreferenceable            Georeferenceable}<br>
 * {@code  │       └─} {@linkplain org.apache.sis.metadata.iso.spatial.DefaultGeorectified                Georectified}<br>
 * {@code  ├─}         {@linkplain org.apache.sis.metadata.iso.spatial.AbstractGeolocationInformation     Geolocation information} «abstract»<br>
 * {@code  │   └─}     {@linkplain org.apache.sis.metadata.iso.spatial.DefaultGCPCollection               GCP collection}<br>
 * {@code  ├─}         {@linkplain org.apache.sis.metadata.iso.spatial.DefaultGCP                         GCP}<br>
 * {@code  ├─}         {@linkplain org.apache.sis.metadata.iso.spatial.DefaultDimension                   Dimension}<br>
 * {@code  └─}         {@linkplain org.apache.sis.metadata.iso.spatial.DefaultGeometricObjects            Geometric objects}<br>
 * {@linkplain org.opengis.util.CodeList Code list}<br>
 * {@code  ├─} {@linkplain org.opengis.metadata.spatial.TopologyLevel             Topology level}<br>
 * {@code  ├─} {@linkplain org.opengis.metadata.spatial.GeometricObjectType       Geometric object type}<br>
 * {@code  ├─} {@linkplain org.opengis.metadata.spatial.CellGeometry              Cell geometry}<br>
 * {@code  ├─} {@linkplain org.opengis.metadata.spatial.PixelOrientation          Pixel orientation}<br>
 * {@code  ├─} {@linkplain org.opengis.metadata.spatial.DimensionNameType         Dimension name type}<br>
 * {@code  └─} {@linkplain org.opengis.metadata.spatial.SpatialRepresentationType Spatial representation type}<br>
 * </td><td class="sep" width="50%" nowrap>
 *                 {@linkplain org.apache.sis.metadata.iso.spatial.AbstractSpatialRepresentation      Spatial representation} «abstract»<br>
 *                 {@linkplain org.apache.sis.metadata.iso.spatial.DefaultVectorSpatialRepresentation Vector spatial representation}<br>
 * {@code  ├─}     {@linkplain org.opengis.metadata.spatial.TopologyLevel                             Topology level} «code list»<br>
 * {@code  └─}     {@linkplain org.apache.sis.metadata.iso.spatial.DefaultGeometricObjects            Geometric objects}<br>
 * {@code      └─} {@linkplain org.opengis.metadata.spatial.GeometricObjectType                       Geometric object type} «code list»<br>
 *                 {@linkplain org.apache.sis.metadata.iso.spatial.DefaultGridSpatialRepresentation   Grid spatial representation}<br>
 * {@code  ├─}     {@linkplain org.apache.sis.metadata.iso.spatial.DefaultDimension                   Dimension}<br>
 * {@code  │   └─} {@linkplain org.opengis.metadata.spatial.DimensionNameType                         Dimension name type} «code list»<br>
 * {@code  └─}     {@linkplain org.opengis.metadata.spatial.CellGeometry                              Cell geometry} «code list»<br>
 *                 {@linkplain org.apache.sis.metadata.iso.spatial.DefaultGeoreferenceable            Georeferenceable}<br>
 * {@code  └─}     {@linkplain org.apache.sis.metadata.iso.spatial.AbstractGeolocationInformation     Geolocation information} «abstract»<br>
 *                 {@linkplain org.apache.sis.metadata.iso.spatial.DefaultGeorectified                Georectified}<br>
 * {@code  ├─}     {@linkplain org.opengis.metadata.spatial.PixelOrientation                          Pixel orientation} «code list»<br>
 * {@code  └─}     {@linkplain org.apache.sis.metadata.iso.spatial.DefaultGCP                         GCP}<br>
 *                 {@linkplain org.apache.sis.metadata.iso.spatial.DefaultGCPCollection               GCP collection}<br>
 *                 {@linkplain org.opengis.metadata.spatial.SpatialRepresentationType                 Spatial representation type} «code list»<br>
 * </td></tr></table>
 *
 * {@section Collections and null values}
 * All constructors (except the <cite>copy constructor</cite>) and setter methods accept {@code null} arguments.
 * A null argument value means that the metadata element can not be provided, and the reason for that is unspecified.
 * Alternatively, users can specify why a metadata element is missing by providing a value created by
 * {@link org.apache.sis.xml.NilReason#createNilObject NilReason.createNilObject(Class)}.
 *
 * <p>Unless otherwise noted in the Javadoc, all getter methods may return an empty collection,
 * an empty array or {@code null} if there is no value. More specifically:</p>
 * <ul>
 *   <li>If the return type is a collection, the method may return an empty collection (never {@code null}).</li>
 *   <li>If the return type is an array, the method may return an empty array (never {@code null}).</li>
 *   <li>Otherwise the method may return {@code null}.</li>
 * </ul>
 *
 * Unless the metadata object has been marked as unmodifiable and unless otherwise noted in the Javadoc,
 * all collections returned by getter methods are <cite>live</cite>: adding new elements in the collection
 * modify directly the underlying metadata object.
 *
 * @author  Martin Desruisseaux (IRD, Geomatys)
 * @author  Touraïvane (IRD)
 * @author  Cédric Briançon (Geomatys)
 * @since   0.3 (derived from geotk-2.1)
 * @version 0.3
 * @module
 */
@XmlSchema(elementFormDefault = XmlNsForm.QUALIFIED, namespace = Namespaces.GMD, xmlns = {
    @XmlNs(prefix = "gmi", namespaceURI = Namespaces.GMI),
    @XmlNs(prefix = "gmd", namespaceURI = Namespaces.GMD),
    @XmlNs(prefix = "gco", namespaceURI = Namespaces.GCO),
    @XmlNs(prefix = "xsi", namespaceURI = Namespaces.XSI)
})
@XmlAccessorType(XmlAccessType.NONE)
@XmlJavaTypeAdapters({
    @XmlJavaTypeAdapter(CI_Citation.class),
    @XmlJavaTypeAdapter(DQ_DataQuality.class),
    @XmlJavaTypeAdapter(DQ_Element.class),
    @XmlJavaTypeAdapter(GM_Object.class),
    @XmlJavaTypeAdapter(MD_CellGeometryCode.class),
    @XmlJavaTypeAdapter(MD_Dimension.class),
    @XmlJavaTypeAdapter(MD_DimensionNameTypeCode.class),
    @XmlJavaTypeAdapter(MD_GeometricObjects.class),
    @XmlJavaTypeAdapter(MD_GeometricObjectTypeCode.class),
    @XmlJavaTypeAdapter(MD_PixelOrientationCode.class),
    @XmlJavaTypeAdapter(MD_TopologyLevelCode.class),
    @XmlJavaTypeAdapter(MI_GCP.class),
    @XmlJavaTypeAdapter(MI_GeolocationInformation.class),
    @XmlJavaTypeAdapter(RS_ReferenceSystem.class),

    // Java types, primitive types and basic OGC types handling
    @XmlJavaTypeAdapter(InternationalStringAdapter.class),
    @XmlJavaTypeAdapter(GO_Decimal.class), @XmlJavaTypeAdapter(type=double.class,  value=GO_Decimal.class),
    @XmlJavaTypeAdapter(GO_Integer.class), @XmlJavaTypeAdapter(type=int.class,     value=GO_Integer.class),
    @XmlJavaTypeAdapter(GO_Boolean.class), @XmlJavaTypeAdapter(type=boolean.class, value=GO_Boolean.class)
})
package org.apache.sis.metadata.iso.spatial;

import javax.xml.bind.annotation.XmlNs;
import javax.xml.bind.annotation.XmlNsForm;
import javax.xml.bind.annotation.XmlSchema;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapters;
import org.apache.sis.xml.Namespaces;
import org.apache.sis.internal.jaxb.gco.*;
import org.apache.sis.internal.jaxb.code.*;
import org.apache.sis.internal.jaxb.metadata.*;
import org.apache.sis.internal.jaxb.geometry.GM_Object;
