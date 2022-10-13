open module application.sis.javafx {
    exports org;

    requires core.sis.portrayal;
    requires core.sis.referencingbyidentifiers;
    requires storage.sis.xmlstore;

    requires junit;
    requires core.sis.feature: //Configuration "testArtifact"
}