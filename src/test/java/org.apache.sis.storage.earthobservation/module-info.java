open module org.apache.sis.storage.earthobservation {
    exports org.apache.sis.storage.landsat;

    requires transitive org.apache.sis.storage.geotiff;
    requires org.opengis.geoapi.pending;
    requires java.desktop;

    requires junit;
}