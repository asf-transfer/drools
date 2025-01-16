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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.drools.util.FileUtilsTest.TEST_FILE;
import static org.drools.util.ResourceHelper.getFileResourcesByExtension;
import static org.drools.util.ResourceHelper.getFileResourcesFromDirectory;
import static org.drools.util.ResourceHelper.getResourcesByExtension;
import static org.drools.util.ResourceHelper.getResourcesFromDirectory;
import static org.drools.util.ResourceHelper.internalGetResources;

public class ResourceHelperTest {


    @Test
    public void getResourcesByExtensionTest() {
        Collection<String> resources = getResourcesByExtension("txt");
        assertThat(resources)
                .hasSize(2)
                .allMatch(elem -> elem.endsWith(TEST_FILE));
    }

    @Test
    public void getResourcesByExtensionExisting() {
        final Collection<File> retrieved = getFileResourcesByExtension("txt");
        commonVerifyCollectionWithExpectedFile(retrieved, TEST_FILE);
    }

    @Test
    public void getResourcesByExtensionNotExisting() {
        final Collection<File> retrieved = getFileResourcesByExtension("arg");
        commonVerifyCollectionWithoutExpectedFile(retrieved);
    }

    @Test
    public void getResourcesFromDirectoryTest() {
        List<String> classPathElements = Arrays.asList(ResourceHelper.getClassPathElements());
        Optional<String> testFolder =
                classPathElements.stream().filter(elem -> elem.contains("test-classes")).findFirst();
        assertThat(testFolder).isPresent();
        File dir = new File(testFolder.get());
        String regex = ".*" + TEST_FILE;
        Collection<String> filesFound = getResourcesFromDirectory(dir, Pattern.compile(regex));
        assertThat(filesFound).hasSize(2);

        assertThat(getResourcesFromDirectory(null, null)).isEmpty();
        assertThat(getResourcesFromDirectory(dir, Pattern.compile("noMatch"))).isEmpty();
    }

    @Test
    public void getResourcesFromDirectoryExisting() {
        File directory = new File("." + File.separator + "target" + File.separator + "test-classes");
        Pattern pattern = Pattern.compile(".*txt");
        final Collection<File> retrieved = getFileResourcesFromDirectory(directory, pattern);
        commonVerifyCollectionWithExpectedFile(retrieved, TEST_FILE);
    }

    @Test
    public void getResourcesFromDirectoryNotExisting() {
        File directory = new File("." + File.separator + "target" + File.separator + "test-classes");
        Pattern pattern = Pattern.compile(".*arg");
        final Collection<File> retrieved = getFileResourcesFromDirectory(directory, pattern);
        commonVerifyCollectionWithoutExpectedFile(retrieved);
    }

    @Test
    public void getClassPathElements() {
        String[] retrieved = ResourceHelper.getClassPathElements();
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.length == 0).isFalse();
    }

    @Test
    public void getClassPathElementsTest() {
        List<String> classPathElements = Arrays.asList(ResourceHelper.getClassPathElements());
        List<String> notJar = classPathElements.stream().filter(elem -> !elem.contains(".jar")).collect(Collectors.toList());
        assertThat(notJar.stream().anyMatch(elem -> elem.contains("test-classes"))).isTrue();
    }

    @Test
    public void internalGetResourcesTest() {
        List<String> classPathElements = Arrays.asList(ResourceHelper.getClassPathElements());
        Optional<String> testFolder = classPathElements.stream().filter(elem -> elem.contains("test-classes")).findFirst();
        assertThat(testFolder).isPresent();
        Collection<String> filesFound = internalGetResources(testFolder.get(), Pattern.compile(".*\\.txt$"));
        assertThat(filesFound).hasSize(2);

        assertThat(internalGetResources(filesFound.iterator().next(), Pattern.compile(".*\\.txt$"))).isEmpty();
    }

    @Test
    public void internalGetResourcesExisting() {
        String path = "." + File.separator + "target" + File.separator + "test-classes";
        Pattern pattern = Pattern.compile(".*txt");
        final Collection<File> retrieved = ResourceHelper.internalGetFileResources(path, pattern);
        commonVerifyCollectionWithExpectedFile(retrieved, TEST_FILE);
    }

    @Test
    public void internalGetResourcesNotExisting() {
        String path = "." + File.separator + "target" + File.separator + "test-classes";
        Pattern pattern = Pattern.compile(".*arg");
        final Collection<File> retrieved = ResourceHelper.internalGetFileResources(path, pattern);
        commonVerifyCollectionWithoutExpectedFile(retrieved);
    }

    private void commonVerifyCollectionWithExpectedFile(final Collection<File> toVerify, String expectedFile) {
        assertThat(toVerify).isNotNull();
        assertThat(toVerify).hasSize(2)
                .allMatch(file -> file.exists() && file.getName().equals(expectedFile));
    }

    private void commonVerifyCollectionWithoutExpectedFile(final Collection<File> toVerify) {
        assertThat(toVerify).isNotNull();
        assertThat(toVerify).isEmpty();
    }
}