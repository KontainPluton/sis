open module org.apache.sis.portrayal {
    exports org.apache.sis.portrayal;

    requires transitive org.apache.sis.storage;
    requires static org.locationtech.jts;
    requires java.desktop;
    requires java.logging;

    requires junit;
}