rootProject.name = "Apache SIS"

include(
    ":application",
    ":application:sis-console",
    ":application:sis-javafx",
    ":application:sis-openoffice",
    ":application:sis-webapp",
)

include(
    ":core",
    ":core:sis-build-helper",
    ":core:sis-cql",
    ":core:sis-feature",
    ":core:sis-metadata",
    ":core:sis-portrayal",
    ":core:sis-referencing",
    ":core:sis-referencing-by-identifiers",
    ":core:sis-utility"
)

include(
    ":cloud",
    ":cloud:sis-cloud-aws"
)

include(
    ":profiles",
    ":profiles:sis-french-profile",
    ":profiles:sis-japan-profile",
)

include(
    ":storage",
    ":storage:sis-earth-observation",
    ":storage:sis-geotiff",
    ":storage:sis-netcdf",
    ":storage:sis-shapefile",
    ":storage:sis-sqlstore",
    ":storage:sis-storage",
    ":storage:sis-xmlstore",
)

val geoapiVersion = "4.0-SNAPSHOT"

dependencyResolutionManagement {
    versionCatalogs {

        create("drivers") {
            library("derby", "org.apache.derby:derby:10.14.2.0")
            library("hsqldb", "org.hsqldb:hsqldb:2.6.1")
            library("h2", "com.h2database:h2:2.1.212")
            library("postgres", "org.postgresql:postgresql:42.3.5")
        }

        create("libs") {
            library("commons.compress", "org.apache.commons:commons-compress:1.21")

            library("geometry.esri", "com.esri.geometry:esri-geometry-api:2.2.4")
            library("geometry.jts", "org.locationtech.jts:jts-core:1.18.2")

            library("jama", "gov.nist.math:jama:1.0.3")

            library("geographiclib", "net.sf.geographiclib:GeographicLib-Java:2.0")

            library("jaxb.api", "jakarta.xml.bind:jakarta.xml.bind-api:2.3.3")
            library("jaxb.runtime", "org.glassfish.jaxb:jaxb-runtime:2.3.6")

            library("ucar.cdm.core", "edu.ucar:cdm-core:5.5.3")
        }
    }
}