/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.efesto.common.api.identifiers;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kie.efesto.common.api.identifiers.componentroots.ComponentRootA;
import org.kie.efesto.common.api.identifiers.componentroots.LocalComponentIdA;

import static org.assertj.core.api.Assertions.assertThat;

class ReflectiveAppRootTest {

    private static AppRoot appRoot;

    @BeforeAll
    public static void setup() {
        appRoot = new ReflectiveAppRoot("testing");
    }

    @Test
    void get() {
        String fileName = "fileName";
        String name = "name";
        LocalUri retrieved = appRoot.get(ComponentRootA.class)
                .get(fileName, name)
                .toLocalId()
                .asLocalUri();

        appRoot.get(ComponentRootA.class)
                .get(fileName, name)
                .toLocalId();

        assertThat(retrieved).isNotNull();
        String expected = String.format("/%1$s/%2$s/%3$s", LocalComponentIdA.PREFIX, fileName, name);
        assertThat(retrieved.path()).isEqualTo(expected);
        expected = String.format("/%1$s/%2$s", fileName, name);
//        assertThat(retrieved.basePath()).isEqualTo(expected);
//        assertThat(retrieved.model()).isEqualTo(LocalComponentIdA.PREFIX);
    }
}