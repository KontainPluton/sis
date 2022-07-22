group = "org.apache.sis.application"
description = "Apache SIS console"

plugins {
    id("org.apache.sis.java-conventions")
}

dependencies {
    // Should be runtime after the hack in FormattedOutputCommand has been fixed.
    implementation(project(":core:sis-portrayal"))
    implementation(project(":core:sis-referencing-by-identifiers"))
    implementation(project(":storage:sis-xmlstore"))

    runtimeOnly(project(":storage:sis-netcdf"))
    runtimeOnly(project(":storage:sis-geotiff"))
    runtimeOnly(project(":storage:sis-earth-observation"))
    runtimeOnly(project(":profiles:sis-japan-profile"))
    runtimeOnly(libs.jaxb.runtime)
    runtimeOnly(drivers.derby)

    testImplementation(project(":core:sis-feature", configuration = "testArtifact"))
    testRuntimeOnly(libs.jaxb.runtime)
}
