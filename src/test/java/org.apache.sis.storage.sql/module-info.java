open module org.apache.sis.storage.sql {
    exports org.apache.sis.storage.sql;

    requires transitive org.apache.sis.storage;
    requires static org.postgresql.jdbc;
    requires java.sql;
    requires java.desktop;

    requires junit;
    requires esri.geometry.api;
    requires org.locationtech.jts;
}