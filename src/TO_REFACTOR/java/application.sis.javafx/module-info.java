module application.sis.javafx {
    exports org.apache.sis.gui;
    exports org.apache.sis.gui.coverage;
    exports org.apache.sis.gui.dataset;
    exports org.apache.sis.gui.map;
    exports org.apache.sis.gui.metadata;
    exports org.apache.sis.gui.referencing;

    requires core.sis.portrayal;
    requires core.sis.referencingbyidentifiers;
    requires storage.sis.xmlstore;
}