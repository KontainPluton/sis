open module org.apache.sis.console {
    exports org.apache.sis.console;

    requires org.apache.sis.storage;
    requires org.apache.sis.util;
    requires org.apache.sis.storage.xml;
    requires org.opengis.geoapi.pending;
    requires java.logging;
    requires java.sql;
    requires java.rmi;
    requires java.management;

    requires junit;
    requires org.apache.sis.metadata;
}