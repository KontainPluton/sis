module org.apache.sis.referencing.gazetteer {
    exports org.apache.sis.referencing.gazetteer;

    requires transitive org.apache.sis.referencing;
    requires org.opengis.geoapi.pending;
    requires java.desktop;
    requires java.logging;
}