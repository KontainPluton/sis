package org.apache.sis;

import org.apache.sis.util.internal.gradle.Assembler;
import org.apache.sis.util.internal.gradle.JarCollector;
import org.apache.sis.util.internal.unopkg.JavaMaker;
import org.apache.sis.util.internal.unopkg.UnoPkg;
import org.apache.sis.util.resources.ResourceCompilerTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class SisBuildHelper implements Plugin<Project> {
    public void apply(Project project) {
        // NOTE : if you have other tasks in the plugin, please declare them here
        project.getTasks().register("resourceCompiler", ResourceCompilerTask.class);
        project.getTasks().getByPath("resourceCompiler").dependsOn("processResources");

        project.getTasks().register("javaMaker", JavaMaker.class);
        project.getTasks().getByPath("resourceCompiler").dependsOn("classes");

        project.getTasks().register("unopkg",UnoPkg.class);
        project.getTasks().getByPath("unopkg").dependsOn("jar");

        // With modularisation, this task is probably useless because the jars are already in the same place (build/libs)
        project.getTasks().register("collect-jars", JarCollector.class);
        project.getTasks().getByPath("collect-jars").dependsOn("jar");

        //project.getTasks().register("dist", Assembler.class);
        //project.getTasks().getByPath("dist").dependsOn("publishToMavenLocal");
    }
}