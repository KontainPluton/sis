module org.apache.sis.storage.geotiff {
    exports org.apache.sis.storage.geotiff;

    exports org.apache.sis.internal.geotiff to org.apache.sis.storage.earthobservation;

    requires transitive org.apache.sis.storage;
    requires org.opengis.geoapi.pending;
    requires java.logging;
    requires java.desktop;
}