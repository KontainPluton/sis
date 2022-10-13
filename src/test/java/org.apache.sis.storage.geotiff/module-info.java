open module org.apache.sis.storage.geotiff {
    exports org.apache.sis.storage.geotiff;

    requires transitive org.apache.sis.storage;
    requires org.opengis.geoapi.pending;
    requires java.logging;
    requires java.desktop;

    requires junit;
}