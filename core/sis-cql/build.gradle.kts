/*
 * This file was generated by the Gradle 'init' task.
 */

plugins {
    id("org.apache.sis.java-conventions")
    antlr
}

val antlrVersion = "4.10.1"

// TODO: this entire source set configuration can be removed if 'antlr4' source directory is simply renamed to 'antlr'
sourceSets {
    main {
        antlr.setSrcDirs(listOf("src/main/antlr4/"))
    }
}

dependencies {
    antlr("org.antlr:antlr4:$antlrVersion")

    api(project(":core:sis-feature"))
    compileOnly(libs.geometry.jts)
    implementation("org.antlr:antlr4-runtime:$antlrVersion")
    testImplementation(project(":core:sis-utility"))
}

group = "org.apache.sis.core"
description = "Apache SIS CQL"

val testsJar by tasks.registering(Jar::class) {
    archiveClassifier.set("tests")
    from(sourceSets["test"].output)
}

(publishing.publications["maven"] as MavenPublication).artifact(testsJar)

// Force classes generated by Antlr to be assigned the given package.
tasks.generateGrammarSource {
    arguments.addAll(listOf("-package", "org.apache.sis.internal.cql"))
}
