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
    id("org.apache.sis.java-conventions")
}

dependencies {
    implementation("org.sonatype.plexus:plexus-build-api:0.0.7")
    implementation("org.apache.commons:commons-compress:1.21")
    compileOnly("org.apache.maven:maven-core:3.8.5")
    compileOnly("org.apache.maven:maven-plugin-api:3.8.5")
    compileOnly("org.apache.maven.plugin-tools:maven-plugin-annotations:3.6.4")
}

group = "org.apache.sis.core"
description = "Apache SIS build helper"

val testsJar by tasks.registering(Jar::class) {
    archiveClassifier.set("tests")
    from(sourceSets["test"].output)
}

(publishing.publications["maven"] as MavenPublication).artifact(testsJar)
