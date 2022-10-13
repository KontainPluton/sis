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
            org.apache.sis.metadata, org.apache.sis.feature, org.apache.sis.referencing, org.apache.sis.referencing.gazetteer, org.apache.sis.storage, org.apache.sis.portrayal;
    exports org.apache.sis.internal.util to
            org.apache.sis.metadata, org.apache.sis.feature, org.apache.sis.referencing, org.apache.sis.referencing.gazetteer, org.apache.sis.storage, org.apache.sis.portrayal;
    exports org.apache.sis.internal.converter to
            org.apache.sis.metadata, org.apache.sis.feature, org.apache.sis.referencing, org.apache.sis.storage;
    exports org.apache.sis.internal.jdk9 to
            org.apache.sis.feature, org.apache.sis.referencing, org.apache.sis.referencing.gazetteer, org.apache.sis.storage;

    requires static org.osgi.core;
    requires static javaee.api;
    requires java.sql;
    requires java.management;

    requires junit;
    requires java.desktop;
}