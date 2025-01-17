/*
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
package org.kie.dmn.feel.lang;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kie.dmn.feel.lang.FEELDialect.STANDARD_FEEL_URIS;

class FEELDialectTest {

    @Test
    void fromNamespaceValid() {
        String bFeelNamespace = "https://www.omg.org/spec/DMN/20240513/B-FEEL/";
        assertThat(FEELDialect.fromNamespace(bFeelNamespace)).isEqualTo(FEELDialect.BFEEL);

        String emptyNamespace = "";
        assertThat(FEELDialect.fromNamespace(emptyNamespace)).isEqualTo(FEELDialect.FEEL);

        STANDARD_FEEL_URIS.forEach(namespace -> assertThat(FEELDialect.fromNamespace(namespace)).isEqualTo(FEELDialect.FEEL));
    }

    @Test
    void fromNamespaceInvalid() {
        String unknownNameSpace = "whatever-get";
        assertThatThrownBy(() -> FEELDialect.fromNamespace(unknownNameSpace))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown FEEL dialect '" + unknownNameSpace + "'");
        String nullNameSpace = null;
        assertThatThrownBy(() -> FEELDialect.fromNamespace(nullNameSpace))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown FEEL dialect '" + nullNameSpace + "'");
    }

    @Test
    void getStandardFeelDialectValid() {
        String bFeelNamespace = "https://www.omg.org/spec/DMN/20240513/B-FEEL/";
        assertThat(FEELDialect.fromNamespace(bFeelNamespace)).isEqualTo(FEELDialect.BFEEL);

        String emptyNamespace = "";
        assertThat(FEELDialect.fromNamespace(emptyNamespace)).isEqualTo(FEELDialect.FEEL);

        STANDARD_FEEL_URIS.forEach(namespace -> assertThat(FEELDialect.getStandardFeelDialect(namespace))
                .isNotEmpty()
                .contains(FEELDialect.FEEL));
    }

    @Test
    void getStandardFeelDialectInvalid() {
        String bFeelNamespace = "https://www.omg.org/spec/DMN/20240513/B-FEEL/";
        assertThat(FEELDialect.getStandardFeelDialect(bFeelNamespace))
                .isEmpty();
        String emptyNamespace = "";
        assertThat(FEELDialect.getStandardFeelDialect(emptyNamespace))
                .isEmpty();
        String unknownNameSpace = "whatever-get";
        assertThat(FEELDialect.getStandardFeelDialect(unknownNameSpace))
                .isEmpty();
        String nullNameSpace = null;
        assertThat(FEELDialect.getStandardFeelDialect(nullNameSpace))
                .isEmpty();
    }
}