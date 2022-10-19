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
