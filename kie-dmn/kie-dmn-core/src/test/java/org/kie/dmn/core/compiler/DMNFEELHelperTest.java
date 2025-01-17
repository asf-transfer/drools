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
package org.kie.dmn.core.compiler;

import java.util.Collections;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kie.dmn.feel.FEEL;
import org.kie.dmn.feel.lang.FEELDialect;
import org.kie.dmn.feel.lang.impl.FEELImpl;

import static org.assertj.core.api.Assertions.assertThat;

class DMNFEELHelperTest {

    private static final FEELDialect DEFAULT_FEEL_DIALECT = FEELDialect.FEEL;
    private static DMNFEELHelper DMN_FEEL_HELPER;

    @BeforeAll
    static void setUp() {
        DMN_FEEL_HELPER = new DMNFEELHelper(Collections.emptyList(), DEFAULT_FEEL_DIALECT);
    }

    @Test
    void newFEELInstanceDefaultFEELDialect() {
        FEEL retrieved = DMN_FEEL_HELPER.newFEELInstance();
        assertThat(retrieved).isNotNull().isInstanceOf(FEELImpl.class);
        assertThat(((FEELImpl)retrieved).getFeelDialect()).isEqualTo(DEFAULT_FEEL_DIALECT);
    }

    @Test
    void newFEELInstanceOverrideFEELDialect() {
        FEELDialect overridingFEELDialect = FEELDialect.FEEL;
        FEEL retrieved = DMN_FEEL_HELPER.newFEELInstance(overridingFEELDialect);
        assertThat(retrieved).isNotNull().isInstanceOf(FEELImpl.class);
        assertThat(((FEELImpl)retrieved).getFeelDialect()).isEqualTo(overridingFEELDialect);
    }

}