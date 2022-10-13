module org.apache.sis.referencing {
    exports org.apache.sis.geometry;
    exports org.apache.sis.io.wkt;
    exports org.apache.sis.parameter;
    exports org.apache.sis.referencing;
    exports org.apache.sis.referencing.crs;
    exports org.apache.sis.referencing.cs;
    exports org.apache.sis.referencing.datum;
    exports org.apache.sis.referencing.factory;
    exports org.apache.sis.referencing.factory.sql;
    exports org.apache.sis.referencing.operation;
    exports org.apache.sis.referencing.operation.builder;
    exports org.apache.sis.referencing.operation.matrix;
    exports org.apache.sis.referencing.operation.projection;
    exports org.apache.sis.referencing.operation.transform;

    exports org.apache.sis.internal.referencing to org.apache.sis.feature, org.apache.sis.referencing.gazetteer, org.apache.sis.storage,
            org.apache.sis.portrayal, org.apache.sis.storage.sql, org.apache.sis.storage.netcdf, org.apache.sis.storage.geotiff,
            org.apache.sis.storage.earthobservation, org.apache.sis.console, org.apache.sis.openoffice;
    exports org.apache.sis.internal.referencing.j2d to org.apache.sis.feature, org.apache.sis.referencing.gazetteer, org.apache.sis.storage,
            org.apache.sis.portrayal, org.apache.sis.storage.sql, org.apache.sis.storage.netcdf;
    exports org.apache.sis.internal.referencing.provider to org.apache.sis.referencing.gazetteer, org.apache.sis.storage.netcdf,
            org.apache.sis.storage.earthobservation, org.apache.sis.profile.japan;

    requires transitive org.apache.sis.metadata;
    requires org.opengis.geoapi.pending;
    requires java.desktop;
    requires java.logging;
    requires java.sql;
}