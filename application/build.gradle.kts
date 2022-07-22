plugins {
    org.apache.sis.`java-conventions`
}

subprojects {
    apply(plugin = "org.apache.sis.java-conventions")

    dependencies {
        implementation(project(":storage:sis-storage"))
        implementation(project(":core:sis-utility"))
        testImplementation(project(":core:sis-utility"))
    }
}