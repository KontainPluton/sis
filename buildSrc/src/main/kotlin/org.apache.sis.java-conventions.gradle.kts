/*
 * This file was generated by the Gradle 'init' task.
 */

plugins {
    `java-library`
    `java`
    `maven-publish`
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://maven.geotoolkit.org")
    }

    maven {
        url = uri("https://repository.apache.org/snapshots")
    }

    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }

    maven {
        url = uri("https://artifacts.unidata.ucar.edu/repository/unidata-releases")
    }
}

dependencies {
    // TODO: find a way to unify geoapi version with version catalog, or move dependency declaration elsewhere (in core ?)
    api(platform("org.opengis:geoapi-parent:4.0-SNAPSHOT"))
    api("org.opengis:geoapi-pending:4.0-SNAPSHOT")
    api("javax.measure:unit-api")
    testImplementation("junit:junit")
    testImplementation("org.opengis:geoapi-conformance:4.0-SNAPSHOT")
}

group = "org.apache.sis"

// TODO: maybe move version in project root build script instead
version = "2.0-SNAPSHOT"

java.sourceCompatibility = JavaVersion.VERSION_17
java.targetCompatibility = JavaVersion.VERSION_17

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}

tasks.withType<Test>() {
    useJUnit()
}
