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
package org.apache.sis.xml;

import java.util.Locale;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.JAXBException;
import org.apache.sis.util.Static;
import org.apache.sis.internal.util.SystemListener;
import org.apache.sis.internal.jaxb.TypeRegistration;


/**
 * Provides convenience methods for marshalling and unmarshalling SIS objects.
 * This class defines also some property keys that can be given to the {@link Marshaller}
 * and {@link Unmarshaller} instances created by {@link PooledMarshaller}:
 *
 * <ul>
 *   <li>{@link #LOCALE} for specifying the locale to use for international strings and code lists.</li>
 *   <li>{@link #TIMEZONE} for specifying the timezone to use for dates and times.</li>
 *   <li>{@link #SCHEMAS} for specifying the root URL of metadata schemas to use.</li>
 *   <li>{@link #RESOLVER} for replacing {@code xlink} or {@code uuidref} attributes by the actual object to use.</li>
 *   <li>{@link #CONVERTER} for controlling the conversion of URL, UUID, Units or similar objects.</li>
 *   <li>{@link #STRING_SUBSTITUTES} for specifying which code lists to replace by simpler {@code <gco:CharacterString>} elements.</li>
 * </ul>
 *
 * @author  Cédric Briançon (Geomatys)
 * @author  Martin Desruisseaux (Geomatys)
 * @since   0.3 (derived from geotk-3.00)
 * @version 0.3
 * @module
 */
public final class XML extends Static {
    /**
     * Allows client code to specify the locale to use for marshalling
     * {@link org.opengis.util.InternationalString} and {@link org.opengis.util.CodeList}
     * instances. The value for this property shall be an instance of {@link Locale}.
     *
     * <p>This property is mostly for marshallers. However this property can also be used at
     * unmarshalling time, for example if a {@code <gmd:PT_FreeText>} element containing
     * many localized strings need to be represented in a Java {@link String} object. In
     * such case, the unmarshaller will try to pickup a string in the language specified
     * by this property.</p>
     *
     * {@section Default behavior}
     * If this property is never set, then (un)marshalling will try to use "unlocalized" strings -
     * typically some programmatic strings like {@linkplain org.opengis.annotation.UML#identifier()
     * UML identifiers}. While such identifiers often look like English words, they are not
     * considered as the {@linkplain Locale#ENGLISH English} localization.
     * The algorithm attempting to find a "unlocalized" string is defined in the
     * {@link org.apache.sis.util.iso.DefaultInternationalString#toString(Locale)} javadoc.
     *
     * {@section Special case}
     * If the object to be marshalled is an instance of
     * {@link org.apache.sis.metadata.iso.DefaultMetadata}, then the value given to its
     * {@link org.apache.sis.metadata.iso.DefaultMetadata#setLanguage(Locale) setLanguage(Locale)}
     * method will have precedence over this property. This behavior is compliant with INSPIRE rules.
     *
     * @see Marshaller#setProperty(String, Object)
     * @see org.apache.sis.metadata.iso.DefaultMetadata#setLanguage(Locale)
     */
    public static final String LOCALE = "org.apache.sis.xml.locale";

    /**
     * The timezone to use for marshalling dates and times.
     *
     * {@section Default behavior}
     * If this property is never set, then (un)marshalling will use the
     * {@linkplain java.util.TimeZone#getDefault() default timezone}.
     */
    public static final String TIMEZONE = "org.apache.sis.xml.timezone";

    /**
     * Allows client code to specify the root URL of schemas. The value for this property shall
     * be an instance of {@link java.util.Map Map&lt;String,String&gt;}. This property controls
     * the URL to be used when marshalling the following elements:
     *
     * <ul>
     *   <li>The value of the {@code codeList} attribute when marshalling subclasses of
     *       {@link org.opengis.util.CodeList} in ISO 19139 compliant XML document.</li>
     *   <li>The value of the {@code uom} attribute when marshalling measures (for example
     *       {@code <gco:Distance>}) in ISO 19139 compliant XML document.</li>
     * </ul>
     *
     * As of SIS 0.3, only one {@code Map} key is recognized: {@code "gmd"}, which stands
     * for the ISO 19139 schemas. Additional keys, if any, are ignored. Future SIS versions
     * may recognize more keys.
     *
     * {@section Valid values}
     * <table class="sis">
     *   <tr><th>Map key</th> <th>Typical values (choose only one)</th></tr>
     *   <tr><td><b>gmd</b></td><td>
     *     http://schemas.opengis.net/iso/19139/20070417/<br>
     *     http://standards.iso.org/ittf/PubliclyAvailableStandards/ISO_19139_Schemas/<br>
     *     http://eden.ign.fr/xsd/fra/20060922/
     *   </td></tr>
     * </table>
     */
    public static final String SCHEMAS = "org.apache.sis.xml.schemas";
    // If more keys are documented, update the Pooled.SCHEMAS_KEY array.

    /**
     * Specifies the GML version to be marshalled or unmarshalled. The GML version may affect the
     * set of XML elements to be marshalled. Newer versions typically have more elements, but not
     * always. For example in {@code gml:VerticalDatum}, the {@code gml:verticalDatumType} property
     * presents in GML 3.0 and 3.1 has been removed in GML 3.2.
     *
     * <p>The value can be {@link String} or {@link org.apache.sis.util.Version} objects.
     * If no version is specified, then the most recent GML version is assumed.</p>
     */
    public static final String GML_VERSION = "org.apache.sis.gml.version";

    /**
     * Allows client code to replace {@code xlink} or {@code uuidref} attributes by the actual
     * object to use. The value for this property shall be an instance of {@link ReferenceResolver}.
     *
     * <p>If a property in a XML document is defined only by {@code xlink} or {@code uuidref} attributes,
     * without any concrete definition, then the default behavior is to create an empty element which
     * contain only the values of the above-cited attributes. This is usually not the right behavior,
     * since we should use the reference ({@code href} or {@code uuidref} attributes) for fetching
     * the appropriate object. However doing so require some application knowledge, for example a
     * catalog where to perform the search, which is left to users. Users can define their search
     * algorithm by subclassing {@link ReferenceResolver} and configure a unmarshaller as below:</p>
     *
     * {@preformat java
     *     ReferenceResolver myResolver = ...;
     *     Unmarshaller um = marshallerPool.acquireUnmarshaller();
     *     um.setProperty(XML.RESOLVER, myResolver);
     *     Object obj = um.unmarshal(xml);
     *     marshallerPool.recycle(um);
     * }
     *
     * @see Unmarshaller#setProperty(String, Object)
     * @see ReferenceResolver
     */
    public static final String RESOLVER = "org.apache.sis.xml.resolver";

    /**
     * Allows client code to control the behavior of the (un)marshalling process when an element
     * can not be processed, or alter the element values. The value for this property shall be an
     * instance of {@link ValueConverter}.
     *
     * <p>If an element in a XML document can not be parsed (for example if a {@linkplain java.net.URL}
     * string is not valid), the default behavior is to throw an exception which cause the
     * (un)marshalling of the entire document to fail. This default behavior can be customized by
     * invoking {@link Marshaller#setProperty(String, Object)} with this {@code CONVERTER} property
     * key and a custom {@link ValueConverter} instance. {@code ValueConverter} can also be used
     * for replacing an erroneous URL by a fixed URL. See the {@link ValueConverter} javadoc for
     * more details.</p>
     *
     * {@section Example}
     * The following example collects the failures in a list without stopping the (un)marshalling
     * process.
     *
     * {@preformat java
     *     class WarningCollector extends ValueConverter {
     *         // The warnings collected during (un)marshalling.
     *         List<String> messages = new ArrayList<String>();
     *
     *         // Override the default implementation in order to
     *         // collect the warnings and allow the process to continue.
     *         &#64;Override
     *         protected <T> boolean exceptionOccured(MarshalContext context,
     *                 T value, Class<T> sourceType, Class<T> targetType, Exception e)
     *         {
     *             mesages.add(e.getLocalizedMessage());
     *             return true;
     *         }
     *     }
     *
     *     // Unmarshal a XML string, trapping some kind of errors.
     *     // Not all errors are trapped - see the ValueConverter
     *     // javadoc for more details.
     *     WarningCollector myWarningList = new WarningCollector();
     *     Unmarshaller um = marshallerPool.acquireUnmarshaller();
     *     um.setProperty(XML.CONVERTER, myWarningList);
     *     Object obj = um.unmarshal(xml);
     *     marshallerPool.recycle(um);
     *     if (!myWarningList.isEmpty()) {
     *         // Report here the warnings to the user.
     *     }
     * }
     *
     * @see Unmarshaller#setProperty(String, Object)
     * @see ValueConverter
     */
    public static final String CONVERTER = "org.apache.sis.xml.converter";

    /**
     * Allows marshallers to substitute some code lists by the simpler {@code <gco:CharacterString>}
     * element. The value for this property shall be a coma-separated list of any of the following
     * values: "{@code language}", "{@code country}".
     *
     * {@section Example}
     * INSPIRE compliant language code shall be formatted like below (formatting may vary):
     *
     * {@preformat xml
     *   <gmd:language>
     *     <gmd:LanguageCode
     *         codeList="http://schemas.opengis.net/iso/19139/20070417/resources/Codelist/ML_gmxCodelists.xml#LanguageCode"
     *         codeListValue="fra">French</gmd:LanguageCode>
     *   </gmd:language>
     * }
     *
     * However if this property contains the "{@code language}" value, then the marshaller will
     * format the language code like below (which is legal according OGC schemas, but is not
     * INSPIRE compliant):
     *
     * {@preformat xml
     *   <gmd:language>
     *     <gco:CharacterString>fra</gco:CharacterString>
     *   </gmd:language>
     * }
     */
    public static final String STRING_SUBSTITUTES = "org.apache.sis.xml.stringSubstitutes";

    /**
     * The pool of marshallers and unmarshallers used by this class.
     * The field name uses the uppercase convention because this field is almost constant:
     * this field is initially null, then created by {@link #getPool()} when first needed.
     * Once created the field value usually doesn't change. However the field may be reset
     * to {@code null} in an OSGi context when modules are loaded or unloaded, because the
     * set of classes returned by {@link TypeRegistration#defaultClassesToBeBound()} may
     * have changed.
     *
     * @see #getPool()
     */
    private static volatile MarshallerPool POOL;

    /**
     * Registers a listener for classpath changes. In such case, a new pool will need to
     * be created because the {@code JAXBContext} may be different.
     */
    static {
        SystemListener.add(new SystemListener() {
            @Override protected void classpathChanged() {
                POOL = null;
            }
        });
    }

    /**
     * Do not allow instantiation on this class.
     */
    private XML() {
    }

    /**
     * Returns the default (un)marshaller pool used by all methods in this class.
     *
     * {@note Current implementation uses the double-check idiom. This is usually a deprecated
     * practice (the recommended alterative is to use static class initialization), but in this
     * particular case the field may be reset to <code>null</code> if OSGi modules are loaded
     * or unloaded, so static class initialization would be a little bit too rigid.}
     */
    private static MarshallerPool getPool() throws JAXBException {
        MarshallerPool pool = POOL;
        if (pool == null) {
            synchronized (XML.class) {
                pool = POOL; // Double-check idiom: see javadoc.
                if (pool == null) {
                    POOL = pool = new MarshallerPool(TypeRegistration.defaultClassesToBeBound());
                }
            }
        }
        return pool;
    }

    /**
     * Marshall the given object into a string.
     *
     * @param  object The root of content tree to be marshalled.
     * @return The XML representation of the given object.
     * @throws JAXBException If an error occurred during the marshalling.
     */
    public static String marshal(final Object object) throws JAXBException {
        final StringWriter output = new StringWriter();
        final MarshallerPool pool = getPool();
        final Marshaller marshaller = pool.acquireMarshaller();
        marshaller.marshal(object, output);
        pool.recycle(marshaller);
        return output.toString();
    }

    /**
     * Marshall the given object into a stream.
     *
     * @param  object The root of content tree to be marshalled.
     * @param  output The stream where to write.
     * @throws JAXBException If an error occurred during the marshalling.
     */
    public static void marshal(final Object object, final OutputStream output) throws JAXBException {
        final MarshallerPool pool = getPool();
        final Marshaller marshaller = pool.acquireMarshaller();
        marshaller.marshal(object, output);
        pool.recycle(marshaller);
    }

    /**
     * Marshall the given object into a file.
     *
     * @param  object The root of content tree to be marshalled.
     * @param  output The file to be written.
     * @throws JAXBException If an error occurred during the marshalling.
     */
    public static void marshal(final Object object, final File output) throws JAXBException {
        final MarshallerPool pool = getPool();
        final Marshaller marshaller = pool.acquireMarshaller();
        marshaller.marshal(object, output);
        pool.recycle(marshaller);
    }

    /**
     * Unmarshall an object from the given string.
     *
     * @param  input The XML representation of an object.
     * @return The object unmarshalled from the given input.
     * @throws JAXBException If an error occurred during the unmarshalling.
     */
    public static Object unmarshal(final String input) throws JAXBException {
        final StringReader in = new StringReader(input);
        final MarshallerPool pool = getPool();
        final Unmarshaller unmarshaller = pool.acquireUnmarshaller();
        final Object object = unmarshaller.unmarshal(in);
        pool.recycle(unmarshaller);
        return object;
    }

    /**
     * Unmarshall an object from the given stream.
     *
     * @param  input The stream from which to read a XML representation.
     * @return The object unmarshalled from the given input.
     * @throws JAXBException If an error occurred during the unmarshalling.
     */
    public static Object unmarshal(final InputStream input) throws JAXBException {
        final MarshallerPool pool = getPool();
        final Unmarshaller unmarshaller = pool.acquireUnmarshaller();
        final Object object = unmarshaller.unmarshal(input);
        pool.recycle(unmarshaller);
        return object;
    }

    /**
     * Unmarshall an object from the given file.
     *
     * @param  input The file from which to read a XML representation.
     * @return The object unmarshalled from the given input.
     * @throws JAXBException If an error occurred during the unmarshalling.
     */
    public static Object unmarshal(final File input) throws JAXBException {
        final MarshallerPool pool = getPool();
        final Unmarshaller unmarshaller = pool.acquireUnmarshaller();
        final Object object = unmarshaller.unmarshal(input);
        pool.recycle(unmarshaller);
        return object;
    }
}
