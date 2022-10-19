open module org.apache.sis.util {
    exports org.apache.sis.io;
    exports org.apache.sis.math;
    exports org.apache.sis.measure;
    exports org.apache.sis.setup;
    exports org.apache.sis.util;
    exports org.apache.sis.util.collection;
    exports org.apache.sis.util.logging;
    exports org.apache.sis.util.resources;

    exports org.apache.sis.internal.system to
            org.apache.sis.metadata, org.apache.sis.feature, org.apache.sis.referencing, org.apache.sis.referencing.gazetteer,
            org.apache.sis.storage, org.apache.sis.portrayal, org.apache.sis.storage.xml, org.apache.sis.storage.sql, org.apache.sis.storage.shapefile,
            org.apache.sis.storage.netcdf, org.apache.sis.storage.geotiff, org.apache.sis.storage.earthobservation, org.apache.sis.console;
    exports org.apache.sis.internal.util to
            org.apache.sis.metadata, org.apache.sis.feature, org.apache.sis.referencing, org.apache.sis.referencing.gazetteer,
            org.apache.sis.storage, org.apache.sis.portrayal, org.apache.sis.storage.xml, org.apache.sis.storage.sql, org.apache.sis.storage.netcdf,
            org.apache.sis.storage.geotiff, org.apache.sis.storage.earthobservation, org.apache.sis.cloud.aws, org.apache.sis.console;
    exports org.apache.sis.internal.converter to
            org.apache.sis.metadata, org.apache.sis.feature, org.apache.sis.referencing, org.apache.sis.storage;

    requires static org.osgi.core;
    requires static javaee.api;
    requires java.sql;
    requires java.management;

    exports org.apache.sis.testutilities;

    requires junit;
    requires java.desktop;
}