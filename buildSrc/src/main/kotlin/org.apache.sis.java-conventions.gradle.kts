//=======================================================================
//        Gradle Project Build File
//
//  Licensed to the Apache Software Foundation (ASF) under one
//  or more contributor license agreements.  See the NOTICE file
//  distributed with this work for additional information
//  regarding copyright ownership.  The ASF licenses this file
//  to you under the Apache License, Version 2.0 (the
//  "License"); you may not use this file except in compliance
//  with the License.  You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing,
//  software distributed under the License is distributed on an
//  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
//  KIND, either express or implied.  See the License for the
//  specific language governing permissions and limitations
//  under the License.
//=======================================================================

plugins {
    `java-library`
    `java`
    `maven-publish`
    `checkstyle`
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
