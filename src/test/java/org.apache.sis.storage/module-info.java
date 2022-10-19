open module org.apache.sis.storage {
    exports org.apache.sis.storage;
    exports org.apache.sis.storage.event;

    exports org.apache.sis.internal.storage to org.apache.sis.storage.xml, org.apache.sis.storage.sql, org.apache.sis.storage.netcdf,
            org.apache.sis.storage.geotiff, org.apache.sis.storage.earthobservation, org.apache.sis.console, org.apache.sis.openoffice,
            org.apache.sis.portrayal;
    exports org.apache.sis.internal.storage.io to org.apache.sis.storage.xml, org.apache.sis.storage.sql, org.apache.sis.storage.netcdf,
            org.apache.sis.storage.geotiff;
    exports org.apache.sis.internal.storage.xml to org.apache.sis.storage.xml;
    exports org.apache.sis.internal.storage.wkt to org.apache.sis.storage.netcdf, org.apache.sis.storage.earthobservation;

    requires transitive org.apache.sis.feature;
    requires org.opengis.geoapi.pending;
    requires java.logging;
    requires java.desktop;
    requires java.sql;

    exports org.apache.sis.teststorage.storage;

    requires junit;
}