module org.apache.sis.openoffice {
    exports org.apache.sis.openoffice;

    requires org.apache.sis.storage;
    requires org.apache.sis.util;
    requires org.apache.sis.referencing;
    requires org.apache.sis.metadata;
    requires org.opengis.geoapi.pending;
    requires static org.libreoffice.uno;
    requires java.logging;
}