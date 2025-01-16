/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.drools.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class FileUtilsTest {

    public static final String TEST_FILE = "TestFile.txt";
    private static final String NOT_EXISTING_FILE = "NotExisting.txt";

    private static final String EXISTING_DIRECTORY =  "subdir";

    private static final String NOT_EXISTING_DIRECTORY = String.format(".%snotexisting", File.separator);

    @Test
    public void getFileExisting() {
        final File retrieved = FileUtils.getFile(TEST_FILE);
        assertThat(retrieved).exists().hasName(TEST_FILE);
    }

    @Test
    public void getFileExistingFromDirectory() {
        final File retrieved = FileUtils.getFile(TEST_FILE, getSubdir());
        assertThat(retrieved).exists().hasName(TEST_FILE);
        assertThat(retrieved.getParentFile()).exists().isDirectory().hasName(EXISTING_DIRECTORY);
    }

    @Test
    public void getFileNotExisting() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> FileUtils.getFile(NOT_EXISTING_FILE));
    }

    @Test
    public void getFileNotExistingDirectory() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> FileUtils.getFile(TEST_FILE, NOT_EXISTING_DIRECTORY));
    }

    @Test
    public void getFileInputStreamExisting() throws IOException {
        final FileInputStream retrieved = FileUtils.getFileInputStream(TEST_FILE);
        assertThat(retrieved).isNotNull();
        retrieved.close();
    }

    @Test
    public void getFileInputStreamNotExisting() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> FileUtils.getFileInputStream(NOT_EXISTING_FILE));
    }

    @Test
    public void getInputStreamFromFileNameExisting() {
        Optional<InputStream> retrieved = FileUtils.getInputStreamFromFileNameAndClassLoader(TEST_FILE, FileUtilsTest.class.getClassLoader());
        assertThat(retrieved).isPresent();
    }

    @Test
    public void getInputStreamFromFileNameNotExisting() {
        Optional<InputStream> retrieved = FileUtils.getInputStreamFromFileNameAndClassLoader(NOT_EXISTING_FILE, FileUtilsTest.class.getClassLoader());
        assertThat(retrieved).isNotPresent();
    }

    @Test
    public void deleteDirectory() throws IOException {
        final Path tempDirectory = Files.createTempDirectory("temp");
        final Path tempFile = Files.createTempFile(tempDirectory, "temp", "temp");
        FileUtils.deleteDirectory(tempDirectory);
        assertThat(Files.exists(tempDirectory)).isFalse();
        assertThat(Files.exists(tempFile)).isFalse();
    }

    private static String getSubdir() {
        URL subdirResource =  FileUtilsTest.class.getClassLoader().getResource(EXISTING_DIRECTORY);
        if (subdirResource == null) {
            throw new RuntimeException("Failed to find subdir folder");
        } else {
            return subdirResource.getFile();
        }
    }
}