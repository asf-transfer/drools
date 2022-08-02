/*
 * Copyright (c) 2021. Red Hat, Inc. and/or its affiliates.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.util;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class KiePathTest {

    protected final boolean isWindowsSeparator;
    protected final String fileSeparator;

    public KiePathTest( boolean isWindowsSeparator ) {
        this.isWindowsSeparator = isWindowsSeparator;
        this.fileSeparator = isWindowsSeparator ? "\\" : "/";
    }

    @Parameterized.Parameters(name = "{0}")
    public static Object[] params() {
        return new Object[] { true, false };
    }

    @Test
    public void testAsString() {
        assertThat(PortablePath.of("src", isWindowsSeparator).asString()).isEqualTo("src");
        assertThat(PortablePath.of("src" + fileSeparator + "test", isWindowsSeparator).asString()).isEqualTo("src/test");
        assertThat(PortablePath.of("src" + fileSeparator + "test", isWindowsSeparator).asString()).isEqualTo("src/test");
        assertThat(PortablePath.of("src" + fileSeparator + "test" + fileSeparator + "folder", isWindowsSeparator).asString()).isEqualTo("src/test/folder");
    }

    @Test
    public void testParent() {
        assertThat(PortablePath.of("src" + fileSeparator + "test" + fileSeparator + "folder", isWindowsSeparator).getParent().asString()).isEqualTo("src/test");
        assertThat(PortablePath.of("src" + fileSeparator + "test" + fileSeparator + "folder", isWindowsSeparator).getParent().getParent().asString()).isEqualTo("src");
        assertThat(PortablePath.of("src" + fileSeparator + "test" + fileSeparator + "folder", isWindowsSeparator).getParent().getParent().getParent().asString()).isEqualTo("");
    }

    @Test
    public void testResolve() {
        assertThat(PortablePath.of("src" + fileSeparator + "test", isWindowsSeparator).resolve("folder").asString()).isEqualTo("src/test/folder");
        assertThat(PortablePath.of("src" + fileSeparator + "test", isWindowsSeparator).resolve(PortablePath.of("folder" + fileSeparator + "subfolder", isWindowsSeparator)).asString()).isEqualTo("src/test/folder/subfolder");
    }

    @Test
    public void testFileName() {
        assertThat(PortablePath.of("src" + fileSeparator + "test" + fileSeparator + "folder", isWindowsSeparator).getFileName()).isEqualTo("folder");
    }

    @Test
    public void testSuffix() {
        assertThat(PortablePath.of("test.suffix").getSuffix()).isEqualTo("suffix");
        assertThat(PortablePath.of("test.").getSuffix()).isEqualTo("");
        assertThat(PortablePath.of("test").getSuffix()).isEqualTo("");
    }
}