open module org.apache.sis.storage.xml {
    exports org.apache.sis.internal.storage.gpx to org.apache.sis.console;

    requires transitive org.apache.sis.storage;
    requires org.opengis.geoapi.pending;
    requires java.logging;

    requires junit;
}