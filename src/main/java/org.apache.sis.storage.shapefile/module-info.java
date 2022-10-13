module org.apache.sis.storage.shapefile {
    exports org.apache.sis.storage.shapefile;
    exports org.apache.sis.storage.shapefile.cpg;

    requires transitive org.apache.sis.storage;
    requires static esri.geometry.api;
    requires java.sql;
}