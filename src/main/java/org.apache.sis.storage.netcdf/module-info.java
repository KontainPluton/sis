module org.apache.sis.storage.netcdf {
    exports org.apache.sis.storage.netcdf;

    exports org.apache.sis.internal.netcdf to org.apache.sis.profile.japan;

    requires transitive org.apache.sis.storage;
    requires static cdm.core;
    requires java.logging;
    requires java.desktop;
}