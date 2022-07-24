rootProject.name = "Apache SIS"

/**
 * Check if JavaFx is available. Note that:
 * - If user specify a `javafx` project property, we do not check classpath, and directly return true.
 * - Otherwise, we try to load arbitrarily the `javafx.application.Application` class, to verify if JavaFx is available.
 * @return True if JavaFx is available on the classpath, or if user has forced `-Pjavafx` argument. False otherwise.
 */
fun javaFxFound() : Boolean {
    if (gradle.startParameter.projectProperties.containsKey("javafx")) return true
    return runCatching {
        ClassLoader.getSystemClassLoader().loadClass("javafx.application.Application")
        true
    }
    .getOrDefault(false)
}

include(
    ":application",
    ":application:sis-console",
    if (javaFxFound()) ":application:sis-javafx" else "",
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