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
package org.apache.sis.metadata.iso.identification;

import java.net.URI;
import java.util.Collection;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import org.opengis.util.InternationalString;
import org.opengis.metadata.citation.OnlineResource;
import org.opengis.metadata.constraint.Constraints;
import org.opengis.metadata.identification.BrowseGraphic;
import org.apache.sis.metadata.iso.ISOMetadata;
import org.apache.sis.internal.jaxb.gcx.MimeFileTypeAdapter;
import org.apache.sis.internal.jaxb.FilterByVersion;
import org.apache.sis.xml.Namespaces;


/**
 * Graphic that provides an illustration of the dataset (should include a legend for the graphic).
 * The following property is mandatory in a well-formed metadata according ISO 19115:
 *
 * <div class="preformat">{@code MD_BrowseGraphic}
 * {@code   └─fileName…………} Name of the file that contains a graphic that provides an illustration of the dataset.</div>
 *
 * <h2>Limitations</h2>
 * <ul>
 *   <li>Instances of this class are not synchronized for multi-threading.
 *       Synchronization, if needed, is caller's responsibility.</li>
 *   <li>Serialized objects of this class are not guaranteed to be compatible with future Apache SIS releases.
 *       Serialization support is appropriate for short term storage or RMI between applications running the
 *       same version of Apache SIS. For long term storage, use {@link org.apache.sis.xml.XML} instead.</li>
 * </ul>
 *
 * @author  Martin Desruisseaux (IRD, Geomatys)
 * @author  Touraïvane (IRD)
 * @author  Cédric Briançon (Geomatys)
 * @author  Rémi Maréchal (Geomatys)
 * @author  Cullen Rombach (Image Matters)
 * @version 1.0
 * @since   0.3
 * @module
 */
@XmlType(name = "MD_BrowseGraphic_Type", namespace = Namespaces.MCC, propOrder = {
    "fileName",
    "fileDescription",
    "fileType",
    "linkage",                  // New in ISO 19115:2014
    "imageConstraint"           // Ibid.
})
@XmlRootElement(name = "MD_BrowseGraphic", namespace = Namespaces.MCC)
public class DefaultBrowseGraphic extends ISOMetadata implements BrowseGraphic {
    /**
     * Serial number for compatibility with different versions.
     */
    private static final long serialVersionUID = 1769063690091153678L;

    /**
     * Name of the file that contains a graphic that provides an illustration of the dataset.
     */
    private URI fileName;

    /**
     * Text description of the illustration.
     */
    private InternationalString fileDescription;

    /**
     * Format in which the illustration is encoded.
     * Examples: CGM, EPS, GIF, JPEG, PBM, PS, TIFF, XWD.
     */
    private String fileType;

    /**
     * Restrictions on access and/or of browse graphic.
     */
    private Collection<Constraints> imageConstraints;

    /**
     * Links to browse graphic.
     */
    private Collection<OnlineResource> linkages;

    /**
     * Constructs an initially empty browse graphic.
     */
    public DefaultBrowseGraphic() {
    }

    /**
     * Creates a browse graphics initialized to the specified URI.
     *
     * @param fileName  the name of the file that contains a graphic.
     */
    public DefaultBrowseGraphic(final URI fileName) {
        this.fileName = fileName;
    }

    /**
     * Constructs a new instance initialized with the values from the specified metadata object.
     * This is a <cite>shallow</cite> copy constructor, since the other metadata contained in the
     * given object are not recursively copied.
     *
     * @param  object  the metadata to copy values from, or {@code null} if none.
     *
     * @see #castOrCopy(BrowseGraphic)
     */
    public DefaultBrowseGraphic(final BrowseGraphic object) {
        super(object);
        if (object != null) {
            fileName         = object.getFileName();
            fileDescription  = object.getFileDescription();
            fileType         = object.getFileType();
            imageConstraints = copyCollection(object.getImageConstraints(), Constraints.class);
            linkages         = copyCollection(object.getLinkages(), OnlineResource.class);
        }
    }

    /**
     * Returns a SIS metadata implementation with the values of the given arbitrary implementation.
     * This method performs the first applicable action in the following choices:
     *
     * <ul>
     *   <li>If the given object is {@code null}, then this method returns {@code null}.</li>
     *   <li>Otherwise if the given object is already an instance of
     *       {@code DefaultBrowseGraphic}, then it is returned unchanged.</li>
     *   <li>Otherwise a new {@code DefaultBrowseGraphic} instance is created using the
     *       {@linkplain #DefaultBrowseGraphic(BrowseGraphic) copy constructor}
     *       and returned. Note that this is a <cite>shallow</cite> copy operation, since the other
     *       metadata contained in the given object are not recursively copied.</li>
     * </ul>
     *
     * @param  object  the object to get as a SIS implementation, or {@code null} if none.
     * @return a SIS implementation containing the values of the given object (may be the
     *         given object itself), or {@code null} if the argument was null.
     */
    public static DefaultBrowseGraphic castOrCopy(final BrowseGraphic object) {
        if (object == null || object instanceof DefaultBrowseGraphic) {
            return (DefaultBrowseGraphic) object;
        }
        return new DefaultBrowseGraphic(object);
    }

    /**
     * Returns the name of the file that contains a graphic that provides an illustration of the dataset.
     *
     * @return file that contains a graphic that provides an illustration of the dataset, or {@code null}.
     */
    @Override
    @XmlElement(name = "fileName", required = true)
    public URI getFileName() {
        return fileName;
    }

    /**
     * Sets the name of the file that contains a graphic that provides an illustration of the dataset.
     *
     * @param  newValue  the new filename.
     */
    public void setFileName(final URI newValue) {
        checkWritePermission(fileName);
        fileName = newValue;
    }

    /**
     * Returns the text description of the illustration.
     *
     * @return text description of the illustration, or {@code null}.
     */
    @Override
    @XmlElement(name = "fileDescription")
    public InternationalString getFileDescription() {
        return fileDescription;
    }

    /**
     * Sets the text description of the illustration.
     *
     * @param  newValue  the new file description.
     */
    public void setFileDescription(final InternationalString newValue)  {
        checkWritePermission(fileDescription);
        fileDescription = newValue;
    }

    /**
     * Format in which the illustration is encoded.
     *
     * <div class="note"><b>Example:</b>
     * CGM, EPS, GIF, JPEG, PBM, PS, TIFF, XWD.
     * </div>
     *
     * @return format in which the illustration is encoded, or {@code null}.
     */
    @Override
    @XmlElement(name = "fileType")
    @XmlJavaTypeAdapter(MimeFileTypeAdapter.class)
    public String getFileType() {
        return fileType;
    }

    /**
     * Sets the format in which the illustration is encoded.
     * Raster formats are encouraged to use one of the names returned by
     * {@link javax.imageio.ImageIO#getReaderFormatNames()}.
     *
     * @param  newValue  the new file type.
     */
    public void setFileType(final String newValue)  {
        checkWritePermission(fileType);
        fileType = newValue;
    }

    /**
     * Returns the restrictions on access and / or use of browse graphic.
     *
     * @return restrictions on access and / or use of browse graphic.
     *
     * @since 0.5
     */
    @Override
    // @XmlElement at the end of this class.
    public Collection<Constraints> getImageConstraints() {
        return imageConstraints = nonNullCollection(imageConstraints, Constraints.class);
    }

    /**
     * Sets the restrictions on access and / or use of browse graphic.
     *
     * @param  newValues  the new restrictions on access and / or use of browse graphic.
     *
     * @since 0.5
     */
    public void setImageConstraints(final Collection<? extends Constraints> newValues) {
        imageConstraints = writeCollection(newValues, imageConstraints, Constraints.class);
    }

    /**
     * Return the links to browse graphic.
     *
     * @return the links to browse graphic.
     *
     * @since 0.5
     */
    @Override
    // @XmlElement at the end of this class.
    public Collection<OnlineResource> getLinkages() {
        return linkages = nonNullCollection(linkages, OnlineResource.class);
    }

    /**
     * Sets the links to browse graphic.
     *
     * @param  newValues  the new links to browse graphic.
     *
     * @since 0.5
     */
    public void setLinkages(final Collection<? extends OnlineResource> newValues) {
        linkages = writeCollection(newValues, linkages, OnlineResource.class);
    }




    //////////////////////////////////////////////////////////////////////////////////////////////////
    ////////                                                                                  ////////
    ////////                               XML support with JAXB                              ////////
    ////////                                                                                  ////////
    ////////        The following methods are invoked by JAXB using reflection (even if       ////////
    ////////        they are private) or are helpers for other methods invoked by JAXB.       ////////
    ////////        Those methods can be safely removed if Geographic Markup Language         ////////
    ////////        (GML) support is not needed.                                              ////////
    ////////                                                                                  ////////
    //////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Invoked by JAXB at both marshalling and unmarshalling time.
     * This attribute has been added by ISO 19115:2014 standard.
     * If (and only if) marshalling an older standard version, we omit this attribute.
     */
    @XmlElement(name = "imageConstraints")
    private Collection<Constraints> getImageConstraint() {
        return FilterByVersion.CURRENT_METADATA.accept() ? getImageConstraints() : null;
    }

    @XmlElement(name = "linkage")
    private Collection<OnlineResource> getLinkage() {
        return FilterByVersion.CURRENT_METADATA.accept() ? getLinkages() : null;
    }
}