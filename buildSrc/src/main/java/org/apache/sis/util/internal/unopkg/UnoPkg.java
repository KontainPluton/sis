/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sis.util.internal.unopkg;

import java.io.*;
import java.util.Enumeration;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.dsl.ArtifactHandler;
import org.gradle.api.component.Artifact;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.TaskAction;

/**
 * Creates an {@code .oxt} package for <a href="http://www.openoffice.org">OpenOffice.org</a> addins.
 *
 * @author  Martin Desruisseaux (IRD, Geomatys)
 * @version 1.0
 * @since   0.8
 * @module
 */
public class UnoPkg extends DefaultTask implements FilenameFilter {
    /**
     * The subdirectory (relative to {@link #baseDirectory}) where the UNO files are expected.
     */
    static final String SOURCE_DIRECTORY = "src/main/unopkg";

    /**
     * Module to decompress. We inflate the {@value} module because Derby is much slower
     * when using an embedded database in a compressed ZIP file compared to flat storage.
     * Since the JAR files are distributed in a ZIP file anyway, inflating that file has
     * little impact on the final ZIP file size.
     */
    private static final String TO_INFLATE = "sis-embedded-data";

    /**
     * The encoding for text files to read and write.
     */
    private static final String ENCODING = "UTF-8";

    /**
     * The string to replace by the final name.
     */
    private static final String FILTERED_NAME = "${project.build.finalName}";

    /**
     * The string to replace by the version number.
     */
    private static final String FILTERED_VERSION = "${project.version}";

    /**
     * The Maven project running this plugin.
     */
    private final Project project = getProject();

    /**
     * Base directory of the module to compile.
     * The UNO files are expected in the {@code "src/main/unopkg"} subdirectory.
     * The plugin will look for the {@code META-INF/manifest.xml} and {@code *.rdb} files in that directory.
     */
    private String baseDirectory;

    @Input
    public String getBaseDirectory() {
        return baseDirectory;
    }
    public void setBaseDirectory(String basedir) { baseDirectory = basedir; }

    /**
     * Directory where the output {@code .oxt} file will be located.
     */
    private File outputDirectory = new File(project.getBuildDir().getPath()+"/classes/java/main");;

    @InputDirectory
    public File getOutputDirectory() {
        return outputDirectory;
    }

    /**
     * In {@code META-INF/manifest.xml}, all occurrences of {@code ${project.build.finalName}}
     * will be replaced by this value.
     */
    private String finalName = project.getName();

    /**
     * In {@code description.xml}, all occurrences of {@code ${project.version}}
     * will be replaced by this value.
     */
    private String version = project.getVersion().toString();

    /**
     * The name for the {@code .oxt} file to create, without the {@code ".oxt"} filename extension.
     */
    private String oxtName = project.getName();

    /**
     * Invoked by reflection for creating the Task.
     */
    public UnoPkg() {}

    /**
     * Tests if a specified file should be included in a file list.
     *
     * @param  directory  the directory in which the file was found.
     * @param  name       the name of the file.
     */
    @Override
    public boolean accept(final File directory, final String name) {
        if (name.endsWith("-sources.jar") || name.endsWith("-tests.jar") || name.endsWith("-javadoc.jar")) {
            return false;
        }
        return name.endsWith(".jar") || name.endsWith(".JAR") ||
               name.endsWith(".rdb") || name.endsWith(".RDB") ||
               name.endsWith(".xml") || name.endsWith(".XML") ||
               name.endsWith(".xcu") || name.endsWith(".XCU") ||
               name.endsWith(".txt") || name.endsWith(".TXT") ||
               name.endsWith(".png") || name.endsWith(".PNG");
    }

    /**
     * Generates the {@code .oxt} file from all {@code .jar} files found in the target directory.
     *
     * @throws GradleException if the plugin execution failed.
     */
    @TaskAction
    public void execute() throws GradleException {
        final String  manifestName = "META-INF/manifest.xml";
        final File sourceDirectory = new File(baseDirectory, SOURCE_DIRECTORY);
        final File         zipFile = new File(outputDirectory, oxtName + ".oxt");
        final File    manifestFile = new File(sourceDirectory, manifestName);
        final File[]          jars = outputDirectory.listFiles(this);
        final File[]          rdbs = sourceDirectory.listFiles(this);
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile))) {
            out.setLevel(9);
            if (manifestFile.isFile()) {
                copyFiltered(manifestFile, out, manifestName);
            }
            /*
             * Copies files from "unopkg" source directory. This include binary files like RDB,
             * but also XML file. We apply filtering on the "description.xml" file in order to
             * set the version number automatically.
             */
            for (final File rdb : rdbs) {
                final String name = rdb.getName();
                if (name.endsWith(".xml")) {
                    copyFiltered(rdb, out, name);
                } else {
                    copy(rdb, out);
                }
            }
            /*
             * Copies the JAR for this module and any additional JARs provided in the output directory.
             */
            for (final File jar : jars) {
                copy(jar, out);
            }
            /*
             * Copies the dependencies.
             */

            ConfigurationContainer configurations = project.getConfigurations();

            String[] includeDependenciesConfigurations = {"implementation","runtimeOnly"};

            for (String s : includeDependenciesConfigurations) {

                Configuration configuration = configurations.getByName(s);

                Configuration copiedConfiguration = configuration.copyRecursive();
                copiedConfiguration.setCanBeResolved(true); // THIS IS IMPORTANT; AS IT IS A COPIED CONFIG I GUESS IT IS OK TO DO.

                ResolvedConfiguration resolvedConfiguration = copiedConfiguration.getResolvedConfiguration();

                Set<ResolvedArtifact> artifacts = resolvedConfiguration.getResolvedArtifacts();
                for (ResolvedArtifact artifact : artifacts) {

                    final File file = artifact.getFile();
                    final String name = file.getName();
                    if (name.startsWith(TO_INFLATE)) {
                        copyInflated(file, out);
                    } else {
                        copy(file, out);
                    }

                }
            }

        } catch (IOException e) {
            throw new GradleException("Error creating the oxt file.", e);
        }
    }

    /**
     * Copies the content of the specified binary file to the specified output stream.
     *
     * @param  file    the regular file to copy inside the ZIP file.
     * @param  bundle  the ZIP file where to copy the given regular file.
     */
    private static void copy(final File file, final ZipOutputStream bundle) throws IOException {
        final String name = file.getName();
        final ZipEntry entry = new ZipEntry(name);
        if (name.endsWith(".png")) {
            final long size = file.length();
            entry.setMethod(ZipOutputStream.STORED);
            entry.setSize(size);
            entry.setCompressedSize(size);
            entry.setCrc(getCRC32(file));
        }
        bundle.putNextEntry(entry);
        try (InputStream in = new FileInputStream(file)) {
            in.transferTo(bundle);
        }
        bundle.closeEntry();
    }

    /**
     * Copies the content of the specified ASCII file to the specified output stream.
     *
     * @param  file    the regular file to copy inside the ZIP file.
     * @param  bundle  the ZIP file where to copy the given regular file.
     * @param  name    the ZIP entry name.
     */
    private void copyFiltered(final File file, final ZipOutputStream bundle, final String name) throws IOException {
        final ZipEntry entry = new ZipEntry(name);
        bundle.putNextEntry(entry);
        final Writer writer = new OutputStreamWriter(bundle, ENCODING);
        try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), ENCODING))) {
            String line;
            while ((line = in.readLine()) != null) {
                line = line.replace(FILTERED_NAME, finalName);
                line = line.replace(FILTERED_VERSION, version);
                writer.write(line);
                writer.write('\n');
            }
        }
        writer.flush();
        bundle.closeEntry();
    }

    /**
     * Copies a JAR file in the given ZIP file, but without compression for the files
     * in {@code SIS_DATA} directory.
     *
     * @param  file    the JAR file to copy.
     * @param  bundle  destination where to copy the JAR file.
     */
    private static void copyInflated(final File file, final ZipOutputStream bundle) throws IOException {
        final ZipEntry entry = new ZipEntry(file.getName());
        bundle.putNextEntry(entry);
        final ZipOutputStream out = new ZipOutputStream(bundle);
        out.setLevel(9);
        try (ZipFile in = new ZipFile(file)) {
            final Enumeration<? extends ZipEntry> entries = in.entries();
            while (entries.hasMoreElements()) {
                final ZipEntry   inEntry  = entries.nextElement();
                final String     name     = inEntry.getName();
                final ZipEntry   outEntry = new ZipEntry(name);
                if (name.startsWith("SIS_DATA")) {
                    final long size = inEntry.getSize();
                    outEntry.setMethod(ZipOutputStream.STORED);
                    outEntry.setSize(size);
                    outEntry.setCompressedSize(size);
                    outEntry.setCrc(inEntry.getCrc());
                }
                try (InputStream inStream = in.getInputStream(inEntry)) {
                    out.putNextEntry(outEntry);
                    inStream.transferTo(out);
                    out.closeEntry();
                }
            }
        }
        out.finish();
        bundle.closeEntry();
    }

    /**
     * Computes CRC32 for the given file.
     */
    private static long getCRC32(final File file) throws IOException {
        final CRC32 crc = new CRC32();
        try (InputStream in = new FileInputStream(file)) {
            final byte[] buffer = new byte[4*1024];
            int length;
            while ((length = in.read(buffer)) >= 0) {
                crc.update(buffer, 0, length);
            }
        }
        return crc.getValue();
    }
}
