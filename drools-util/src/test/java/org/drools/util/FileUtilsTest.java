/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.Assert.assertThrows;

public class FileUtilsTest {

    private static final String TEST_FILE = "TestFile.txt";
    private static final String NOT_EXISTING_FILE = "NotExisting.txt";

    @Test
    public void getFileExisting() {
        final File retrieved = FileUtils.getFile(TEST_FILE);
        assertThat(retrieved).exists();
        assertThat(retrieved.getName()).isEqualTo(TEST_FILE);
    }

    @Test
    public void getFileNotExisting() {
        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> FileUtils.getFile(NOT_EXISTING_FILE));
    }

    @Test
    public void getFileInputStreamExisting() throws IOException {
        final FileInputStream retrieved = FileUtils.getFileInputStream(TEST_FILE);
        assertThat(retrieved).isNotNull();
        retrieved.close();
    }

    @Test
    public void getFileInputStreamNotExisting() {
        assertThrows(RuntimeException.class, () -> FileUtils.getFileInputStream(NOT_EXISTING_FILE));
    }


    @Test
    public void getInputStreamFromFileNameExisting() {
        InputStream retrieved = FileUtils.getInputStreamFromFileName(TEST_FILE);
        assertThat(retrieved).isNotNull();
    }

    @Test
    public void getInputStreamFromFileNameNotExisting() {
        assertThrows(RuntimeException.class, () -> FileUtils.getInputStreamFromFileName(NOT_EXISTING_FILE));
    }
}