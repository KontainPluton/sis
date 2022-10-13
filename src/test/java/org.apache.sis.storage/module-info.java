open module org.apache.sis.storage {
    exports org.apache.sis.storage;
    exports org.apache.sis.storage.event;
    exports org.apache.sis.storage.tiling;

    requires transitive org.apache.sis.feature;
    requires org.opengis.geoapi.pending;
    requires java.logging;
    requires java.desktop;
    requires java.sql;

    requires junit;
}