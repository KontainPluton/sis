module org.apache.sis.metadata {
    exports org.apache.sis.metadata;
    exports org.apache.sis.metadata.sql;
    exports org.apache.sis.metadata.iso;
    exports org.apache.sis.metadata.iso.acquisition;
    exports org.apache.sis.metadata.iso.citation;
    exports org.apache.sis.metadata.iso.constraint;
    exports org.apache.sis.metadata.iso.content;
    exports org.apache.sis.metadata.iso.distribution;
    exports org.apache.sis.metadata.iso.extent;
    exports org.apache.sis.metadata.iso.identification;
    exports org.apache.sis.metadata.iso.lineage;
    exports org.apache.sis.metadata.iso.maintenance;
    exports org.apache.sis.metadata.iso.quality;
    exports org.apache.sis.metadata.iso.spatial;
    exports org.apache.sis.util.iso;
    exports org.apache.sis.xml;

    exports org.apache.sis.internal.jaxb to org.apache.sis.referencing, org.apache.sis.storage.xml, org.apache.sis.profile.france;
    exports org.apache.sis.internal.jaxb.gco to org.apache.sis.referencing;
    exports org.apache.sis.internal.jaxb.gml to org.apache.sis.referencing, org.apache.sis.storage.xml;
    exports org.apache.sis.internal.jaxb.metadata to org.apache.sis.referencing, org.apache.sis.storage, org.apache.sis.profile.france;
    exports org.apache.sis.internal.jaxb.metadata.replace to org.apache.sis.referencing, org.apache.sis.profile.france;
    exports org.apache.sis.internal.metadata to org.apache.sis.referencing, org.apache.sis.feature, org.apache.sis.storage;
    exports org.apache.sis.internal.metadata.sql to org.apache.sis.referencing, org.apache.sis.storage.sql;
    exports org.apache.sis.internal.simple to org.apache.sis.referencing, org.apache.sis.storage, org.apache.sis.storage.xml;
    exports org.apache.sis.internal.xml to org.apache.sis.referencing, org.apache.sis.storage, org.apache.sis.storage.xml;

    requires transitive org.apache.sis.util;
    requires transitive java.xml.bind;
    requires org.opengis.geoapi.pending;
    requires java.logging;
    requires java.xml;
    requires java.sql;
    requires java.naming;
}