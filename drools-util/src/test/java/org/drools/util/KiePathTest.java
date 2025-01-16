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

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class KiePathTest {

    public static Stream<Arguments> parameters() {
    	return Stream.of(arguments(true, "\\"),
    			arguments(false, "/"));
    }

    @ParameterizedTest(name="separator = {1}")
    @MethodSource("parameters")
    public void testAsString(boolean isWindowsSeparator, String fileSeparator) throws Exception {
        assertThat(PortablePath.of("src", isWindowsSeparator).asString()).isEqualTo("src");
        assertThat(PortablePath.of("src" + fileSeparator + "test", isWindowsSeparator).asString()).isEqualTo("src/test");
        assertThat(PortablePath.of("src" + fileSeparator + "test", isWindowsSeparator).asString()).isEqualTo("src/test");
        assertThat(PortablePath.of("src" + fileSeparator + "test" + fileSeparator + "folder", isWindowsSeparator).asString()).isEqualTo("src/test/folder");
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testParent(boolean isWindowsSeparator, String fileSeparator) throws Exception {
        assertThat(PortablePath.of("src" + fileSeparator + "test" + fileSeparator + "folder", isWindowsSeparator).getParent().asString()).isEqualTo("src/test");
        assertThat(PortablePath.of("src" + fileSeparator + "test" + fileSeparator + "folder", isWindowsSeparator).getParent().getParent().asString()).isEqualTo("src");
        assertThat(PortablePath.of("src" + fileSeparator + "test" + fileSeparator + "folder", isWindowsSeparator).getParent().getParent().getParent().asString()).isEqualTo("");
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testResolve(boolean isWindowsSeparator, String fileSeparator) throws Exception {
        assertThat(PortablePath.of("src" + fileSeparator + "test", isWindowsSeparator).resolve("folder").asString()).isEqualTo("src/test/folder");
        assertThat(PortablePath.of("src" + fileSeparator + "test", isWindowsSeparator).resolve(PortablePath.of("folder" + fileSeparator + "subfolder", isWindowsSeparator)).asString()).isEqualTo("src/test/folder/subfolder");
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testFileName(boolean isWindowsSeparator, String fileSeparator) throws Exception {
        assertThat(PortablePath.of("src" + fileSeparator + "test" + fileSeparator + "folder", isWindowsSeparator).getFileName()).isEqualTo("folder");
    }
}