import org.apache.sis.SisBuildHelper

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

//=== PLUGINS ====================================================================//

plugins {
    `java-library`
    `java`
    `maven-publish`
    //`checkstyle`
    war
}

apply<SisBuildHelper>()
tasks.get("javaMaker").setProperty("baseDirectory","${project.rootDir.absolutePath}/src/main/java/org.apache.sis.openoffice")
tasks.get("unopkg").setProperty("baseDirectory","${project.rootDir.absolutePath}/src/main/java/org.apache.sis.openoffice")
//tasks.get("dist").setProperty("baseDirectory","${project.rootDir.absolutePath}/src/main/java")

//=== REPOSITORIES ====================================================================//

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

//=== INFORMATION ====================================================================//

group = "org.apache.sis"

// TODO: maybe move version in project root build script instead
version = "2.0-SNAPSHOT"

java.sourceCompatibility = JavaVersion.VERSION_17
java.targetCompatibility = JavaVersion.VERSION_17

//=== DEPENDENCIES ====================================================================//

dependencies {
    // TODO: find a way to unify geoapi version with version catalog, or move dependency declaration elsewhere (in core ?)
    api(platform("org.opengis:geoapi-parent:4.0-SNAPSHOT"))
    api("org.opengis:geoapi-pending:4.0-SNAPSHOT")
    api("javax.measure:unit-api")
    testImplementation("junit:junit")
    testImplementation("org.opengis:geoapi-conformance:4.0-SNAPSHOT")

    // CORE UTILITY
    compileOnly("org.osgi:org.osgi.core:6.0.0")
    compileOnly("javax:javaee-api:8.0.1") {
        exclude("com.sun.mail","javax.mail")
    }
    testCompileOnly("org.osgi:org.osgi.core:6.0.0")
    testCompileOnly("javax:javaee-api:8.0.1") {
        exclude("com.sun.mail","javax.mail")
    }

    // CORE METADATA
    api(libs.jaxb.api)
    //testImplementation(project(":core:sis-utility", "testArtifact"))
    testRuntimeOnly(libs.jaxb.runtime)

    testImplementation(drivers.h2)
    testImplementation(drivers.hsqldb)
    testImplementation(drivers.postgres)
    testImplementation(drivers.derby)
    testImplementation(drivers.derbytools)
    testImplementation(drivers.derbyshared)

    testRuntimeOnly(drivers.h2)
    testRuntimeOnly(drivers.hsqldb)
    testRuntimeOnly(drivers.postgres)

    // CORE REFERENCING
    testImplementation(libs.geographiclib)
    testImplementation(libs.jama)
    testRuntimeOnly(libs.jaxb.runtime)
    testRuntimeOnly(drivers.h2)
    testRuntimeOnly(drivers.hsqldb)
    testRuntimeOnly(drivers.postgres)

    // CORE FEATURE
    compileOnly(libs.geometry.esri)
    compileOnly(libs.geometry.jts)
    testCompileOnly(libs.geometry.esri)
    testCompileOnly(libs.geometry.jts)

    // CORE REFERENCING GAZETTEER
    testRuntimeOnly(drivers.derby)

    // CORE PORTRAYAL
    compileOnly(libs.geometry.jts)
    testCompileOnly(libs.geometry.jts)

    // STORAGE STORAGE
    testRuntimeOnly(libs.geometry.esri)
    testRuntimeOnly(libs.jaxb.runtime)

    // STORAGE XML
    testRuntimeOnly(libs.geometry.esri)
    testRuntimeOnly(libs.jaxb.runtime)

    // STORAGE SQL
    compileOnly(drivers.postgres)
    testCompileOnly(drivers.postgres)
    testImplementation(libs.geometry.esri)
    testImplementation(libs.geometry.jts)
    testRuntimeOnly(drivers.h2)
    testRuntimeOnly(drivers.hsqldb)
    testRuntimeOnly(drivers.postgres)

    // STORAGE SHAPEFILE
    compileOnly(libs.geometry.esri)
    testCompileOnly(libs.geometry.esri)

    // STORAGE NETCDF
    compileOnly("edu.ucar:cdm-core:5.5.3") {
        exclude("com.google.code.findbugs","jsr305")
    }
    testCompileOnly("edu.ucar:cdm-core:5.5.3") {
        exclude("com.google.code.findbugs","jsr305")
    }
    testImplementation("org.slf4j:slf4j-jdk14:1.7.28")

    // STORAGE GEOTIFF
    testRuntimeOnly(libs.jaxb.runtime)

    // PROFILE FRANCE
    testRuntimeOnly(libs.jaxb.runtime)

    // PROFILE JAPAN
    compileOnly("edu.ucar:cdm-core:5.5.3") {
        exclude("com.google.code.findbugs","jsr305")
    }
    testImplementation("edu.ucar:cdm-core:5.5.3") {
        exclude("com.google.code.findbugs","jsr305")
    }
    runtimeOnly("org.slf4j:slf4j-jdk14:1.7.28")

    // CLOUD AWS
    implementation("software.amazon.awssdk:s3:2.17.185")
    testImplementation("software.amazon.awssdk:s3:2.17.185")

    // APPLICATION CONSOLE
    //runtimeOnly(project("storage.sis.netcdf"))
    //runtimeOnly(project("storage.sis.geotiff"))
    //runtimeOnly(project("storage.sis.earthobservation"))
    runtimeOnly(drivers.derby)
    testRuntimeOnly(libs.jaxb.runtime)

    // APPLICATION OPENOFFICE
    implementation("org.opengis:geoapi-pending:4.0-SNAPSHOT")
    compileOnly("org.libreoffice:libreoffice:7.3.3")
    testImplementation("org.opengis:geoapi-pending:4.0-SNAPSHOT")
    testCompileOnly("org.libreoffice:libreoffice:7.3.3")

    // APPLICATION WEBAPP
    providedCompile("javax:javaee-api:8.0.1") {
        exclude("com.sun.mail","javax.mail")
    }

    // SIS-JAVAFX
    /*runtimeOnly(project(":storage:sis-netcdf"))
    runtimeOnly(project(":storage:sis-geotiff"))
    runtimeOnly(project(":storage:sis-earth-observation"))
    runtimeOnly(project(":profiles:sis-japan-profile"))
    runtimeOnly(libs.jaxb.runtime)
    runtimeOnly(drivers.derby)
    */
}

//=== JAVA COMPILATION ====================================================================//

tasks.compileJava {
    options.encoding = "UTF-8"
    options.compilerArgs.add("--module-source-path")
    options.compilerArgs.add(files("src/main/java").asPath)
    options.compilerArgs.add("--module-path=${classpath.asPath}")
}

//=== TEST COMPILATION ====================================================================//

tasks.compileTestJava {
    options.compilerArgs.add("--module-source-path")
    options.compilerArgs.add(files("src/test/java").asPath)

    File("src/test/java/").list().forEach {
        options.compilerArgs.add("--patch-module")
        options.compilerArgs.add("${it}=${tasks.compileJava.get().destinationDirectory.asFile.get().path}/${it}")
    }

    // Exports from main module, only for testing purpose
    options.compilerArgs.add("--add-exports")
    options.compilerArgs.add("org.apache.sis.util/org.apache.sis.internal.temporal=org.apache.sis.metadata")

    options.compilerArgs.add("--add-exports")
    options.compilerArgs.add("org.apache.sis.util/org.apache.sis.internal.jdk9=org.apache.sis.feature")
    options.compilerArgs.add("--add-exports")
    options.compilerArgs.add("org.apache.sis.util/org.apache.sis.internal.jdk9=org.apache.sis.referencing")

    options.compilerArgs.add("--add-exports")
    options.compilerArgs.add("org.apache.sis.metadata/org.apache.sis.metadata.iso.distribution=org.apache.sis.referencing")

    options.compilerArgs.add("--add-exports")
    options.compilerArgs.add("org.apache.sis.metadata/org.apache.sis.internal.jaxb.gcx=org.apache.sis.referencing")

    options.compilerArgs.add("--module-path=${classpath.asPath}")
}

//=== TEST EXECUTION ====================================================================//

tasks.test {
    useJUnit()
    testLogging {
        events("PASSED", "FAILED", "SKIPPED", "STANDARD_OUT")
    }
    maxHeapSize = "1G"

    val args = mutableListOf("--module-path=${classpath.asPath}")

    File("src/test/java/").list().forEach {
        args.add("--patch-module")
        args.add("${it}=${tasks.compileJava.get().destinationDirectory.asFile.get().path}/${it}")
        args.add("--add-modules")
        args.add(it)
    }

    jvmArgs(args)
}

//=== JAR CREATION (EACH MODULE) ====================================================================//

file("src/main/java").listFiles() { pathname -> pathname.isDirectory }.forEach {
    val tabName = it.name.split("/")
    val name = tabName[tabName.size-1]

//    tasks.register<Jar>("${name}Jar") {
//        // Create a jar archive BUT the inner structure is not correct !
//        archiveBaseName.set("${name}_notworking")
//        dependsOn("classes")
//        val sourcesMain = sourceSets.main.get()
//
//        from(sourcesMain.output)
//        {
//            include("${name}/**")
//        }
//    }

    tasks.register("${name}Jar") {
        dependsOn("classes")
        val sourcesMain = sourceSets.main.get()

        // TODO : Find another way to build the jar (without using the "jar command")
        doFirst{
            mkdir("${project.buildDir.absolutePath}/libs")
        }
        doLast{
            project.exec{
                workingDir(".")
                executable("jar")
                args("--create")
                args("--file","${project.buildDir.absolutePath}/libs/${name}.jar")
                args("-C","${sourcesMain.output.classesDirs.elementAt(0).absolutePath}/${name}")
                args(".")
            }
        }
    }
}

//=== JAR CREATION (GLOBAL TASK) ====================================================================//

tasks.jar.configure() {
    actions.clear()
    file("src/main/java").listFiles() { pathname -> pathname.isDirectory }.forEach {
        val tabName = it.name.split("/")
        val name = tabName[tabName.size-1]
        dependsOn ("${name}Jar")
    }

    doFirst {
        println("Creation of a jar per module...")
    }
}

/*tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "application.application.Main"
    }
}*/

//=== MAVEN PUBLICATION ====================================================================//

/*publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}*/