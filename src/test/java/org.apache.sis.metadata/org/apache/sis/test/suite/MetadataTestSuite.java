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
package org.apache.sis.test.suite;

import org.apache.sis.internal.jaxb.IdentifierMapAdapterTest;
import org.apache.sis.internal.jaxb.ModifiableIdentifierMapTest;
import org.apache.sis.internal.jaxb.cat.CodeListMarshallingTest;
import org.apache.sis.internal.jaxb.cat.EnumAdapterTest;
import org.apache.sis.internal.jaxb.cat.EnumMarshallingTest;
import org.apache.sis.internal.jaxb.gco.MultiplicityTest;
import org.apache.sis.internal.jaxb.gco.PropertyTypeTest;
import org.apache.sis.internal.jaxb.gco.StringAdapterTest;
import org.apache.sis.internal.jaxb.gml.MeasureTest;
import org.apache.sis.internal.jaxb.gml.TimePeriodTest;
import org.apache.sis.internal.jaxb.lan.FreeTextMarshallingTest;
import org.apache.sis.internal.jaxb.lan.LanguageCodeTest;
import org.apache.sis.internal.jaxb.lan.OtherLocalesTest;
import org.apache.sis.internal.jaxb.lan.PT_LocaleTest;
import org.apache.sis.internal.jaxb.metadata.replace.ServiceParameterTest;
import org.apache.sis.internal.metadata.*;
import org.apache.sis.internal.metadata.sql.SQLUtilitiesTest;
import org.apache.sis.internal.metadata.sql.ScriptRunnerTest;
import org.apache.sis.internal.metadata.sql.TypeMapperTest;
import org.apache.sis.internal.simple.SimpleIdentifierTest;
import org.apache.sis.internal.test.DocumentComparatorTest;
import org.apache.sis.internal.xml.XmlUtilitiesTest;
import org.apache.sis.metadata.*;
import org.apache.sis.metadata.iso.*;
import org.apache.sis.metadata.iso.citation.*;
import org.apache.sis.metadata.iso.constraint.DefaultLegalConstraintsTest;
import org.apache.sis.metadata.iso.content.DefaultBandTest;
import org.apache.sis.metadata.iso.extent.DefaultExtentTest;
import org.apache.sis.metadata.iso.extent.DefaultGeographicBoundingBoxTest;
import org.apache.sis.metadata.iso.extent.ExtentsTest;
import org.apache.sis.metadata.iso.identification.*;
import org.apache.sis.metadata.iso.lineage.DefaultLineageTest;
import org.apache.sis.metadata.iso.lineage.DefaultProcessStepTest;
import org.apache.sis.metadata.iso.maintenance.DefaultScopeDescriptionTest;
import org.apache.sis.metadata.iso.quality.AbstractElementTest;
import org.apache.sis.metadata.iso.quality.AbstractPositionalAccuracyTest;
import org.apache.sis.metadata.iso.quality.ScopeCodeTest;
import org.apache.sis.metadata.iso.spatial.DefaultGeorectifiedTest;
import org.apache.sis.metadata.sql.IdentifierGeneratorTest;
import org.apache.sis.metadata.sql.MetadataSourceTest;
import org.apache.sis.metadata.sql.MetadataWriterTest;
import org.apache.sis.metadata.xml.SchemaComplianceTest;
import org.apache.sis.util.iso.*;
import org.apache.sis.xml.*;
import org.apache.sis.test.TestSuite;
import org.junit.runners.Suite;
import org.junit.BeforeClass;


/**
 * All tests from the {@code sis-metadata} module, in rough dependency order.
 *
 * @author  Martin Desruisseaux (Geomatys)
 * @version 1.3
 * @since   0.3
 * @module
 */
@Suite.SuiteClasses({
    IdentifiersTest.class,
    AxisNamesTest.class,
    NameMeaningTest.class,
    MetadataUtilitiesTest.class,

    // Classes using Java reflection.
    PropertyInformationTest.class,
    PropertyAccessorTest.class,
    SpecialCasesTest.class,
    NameMapTest.class,
    TypeMapTest.class,
    InformationMapTest.class,
    ValueMapTest.class,
    TreeNodeChildrenTest.class,
    TreeNodeTest.class,
    TreeTableViewTest.class,
    TreeTableFormatTest.class,
    MetadataStandardTest.class,
    HashCodeTest.class,
    PrunerTest.class,
    AbstractMetadataTest.class,
    ModifiableMetadataTest.class,
    MetadataCopierTest.class,
    MergerTest.class,

    // XML marshalling.
    DocumentComparatorTest.class,
    NamespacesTest.class,
    XLinkTest.class,
    XPointerTest.class,
    NilReasonTest.class,
    LegacyCodesTest.class,
    ValueConverterTest.class,
    MarshallerPoolTest.class,
    TransformingNamespacesTest.class,
    TransformerTest.class,
    XmlUtilitiesTest.class,
    IdentifierMapAdapterTest.class,
    ModifiableIdentifierMapTest.class,
    StringAdapterTest.class,
    PropertyTypeTest.class,
    MultiplicityTest.class,
    PT_LocaleTest.class,
    OtherLocalesTest.class,
    LanguageCodeTest.class,
    FreeTextMarshallingTest.class,
    EnumAdapterTest.class,
    EnumMarshallingTest.class,
    CodeListMarshallingTest.class,
    TimePeriodTest.class,
    MeasureTest.class,
    NilReasonMarshallingTest.class,
    CharSequenceSubstitutionTest.class,
    UUIDMarshallingTest.class,
    XLinkMarshallingTest.class,

    // GeoAPI most basic types.
    SimpleIdentifierTest.class,
    TypesTest.class,
    DefaultLocalNameTest.class,
    DefaultScopedNameTest.class,
    DefaultNameFactoryTest.class,
    NamesTest.class,
    TypeNamesTest.class,
    DefaultRecordTypeTest.class,
    DefaultRecordSchemaTest.class,
    DefaultRecordTest.class,
    NameMarshallingTest.class,

    // ISO implementations.
    DefaultContactTest.class,
    DefaultResponsibilityTest.class,
    DefaultCitationDateTest.class,
    DefaultCitationTest.class,
    DefaultScopeDescriptionTest.class,
    DefaultGeographicBoundingBoxTest.class,
    DefaultExtentTest.class,
    ExtentsTest.class,
    DefaultBandTest.class,
    DefaultGeorectifiedTest.class,
    DefaultKeywordsTest.class,
    DefaultRepresentativeFractionTest.class,
    DefaultResolutionTest.class,
    DefaultBrowseGraphicTest.class,
    DefaultDataIdentificationTest.class,
    ServiceParameterTest.class,
    DefaultCoupledResourceTest.class,
    DefaultServiceIdentificationTest.class,
    AbstractElementTest.class,
    AbstractPositionalAccuracyTest.class,
    ScopeCodeTest.class,
    DefaultLineageTest.class,
    DefaultProcessStepTest.class,
    DefaultLegalConstraintsTest.class,
    DefaultIdentifierTest.class,
    DefaultMetadataTest.class,
    CustomMetadataTest.class,
    AllMetadataTest.class,
    MarshallingTest.class,
    APIVerifier.class,

    SQLUtilitiesTest.class,
    TypeMapperTest.class,
    ScriptRunnerTest.class,
    IdentifierGeneratorTest.class,
    MetadataSourceTest.class,
    MetadataWriterTest.class,
    CitationsTest.class,
    SchemaComplianceTest.class
})
public final strictfp class MetadataTestSuite extends TestSuite {
    /**
     * Verifies the list of tests before to run the suite.
     * See {@link #verifyTestList(Class, Class[])} for more information.
     */
    @BeforeClass
    public static void verifyTestList() {
        assertNoMissingTest(MetadataTestSuite.class);
        verifyTestList(MetadataTestSuite.class);
    }
}
