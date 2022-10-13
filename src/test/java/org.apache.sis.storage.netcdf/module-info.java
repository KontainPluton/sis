open module org.apache.sis.storage.netcdf {
    exports org.apache.sis.storage.netcdf;

    requires transitive org.apache.sis.storage;
    requires static cdm.core;
    requires java.logging;
    requires java.desktop;

    requires junit;
    requires org.slf4j;
}