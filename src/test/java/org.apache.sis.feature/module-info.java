open module org.apache.sis.feature {
    exports org.apache.sis.coverage;
    exports org.apache.sis.coverage.grid;
    exports org.apache.sis.feature;
    exports org.apache.sis.feature.builder;
    exports org.apache.sis.filter;
    exports org.apache.sis.image;
    exports org.apache.sis.index.tree;

    exports org.apache.sis.internal.filter to org.apache.sis.storage, org.apache.sis.storage.sql;
    exports org.apache.sis.internal.feature to org.apache.sis.storage, org.apache.sis.portrayal, org.apache.sis.storage.xml, org.apache.sis.storage.sql,
            org.apache.sis.storage.netcdf;
    exports org.apache.sis.internal.coverage.j2d to org.apache.sis.storage, org.apache.sis.portrayal, org.apache.sis.storage.sql, org.apache.sis.storage.netcdf,
            org.apache.sis.storage.geotiff;

    requires transitive org.apache.sis.referencing;
    requires static esri.geometry.api;
    requires static org.locationtech.jts;
    requires java.desktop;
    requires java.logging;
    requires java.sql;

    exports org.apache.sis.internal.feature.jts;

    requires junit;
    requires org.apache.sis.util;
}