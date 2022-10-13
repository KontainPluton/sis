open module org.apache.sis.storage.xml {
    exports org.apache.sis.storage.gps;

    requires transitive org.apache.sis.storage;
    requires org.opengis.geoapi.pending;
    requires java.logging;

    requires junit;
}