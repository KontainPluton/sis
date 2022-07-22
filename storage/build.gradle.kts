plugins {
    id("org.apache.sis.java-conventions")
}

subprojects {
    apply(plugin = "org.apache.sis.java-conventions")

    dependencies {
        api(project(":core:sis-feature"))

        testRuntimeOnly(rootProject.drivers.derby)
    }
}