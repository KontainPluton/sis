plugins {
    `java-gradle-plugin`
    `java-library`
    `java`
}

repositories {
    mavenLocal()

    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}

dependencies {
    testImplementation("junit:junit:4.13")
}

tasks.test {
    useJUnit()
    testLogging {
        events("PASSED", "FAILED", "SKIPPED", "STANDARD_OUT")
    }
    maxHeapSize = "1G"
}

gradlePlugin {
    plugins {
        create("simplePlugin") {
            id = "org.apache.sis.sis-build-helper"
            implementationClass = "org.apache.sis.SisBuildHelper"
        }
    }
}