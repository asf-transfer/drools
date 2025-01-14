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
package org.kie.yard.core;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class InsuranceBasePriceTest
        extends TestBase {

    private static final String FILE_NAME = "/insurance-base-price.yml";

    @Test
    public void testScenario1() throws Exception {
        final String CTX = """
                {
                  "Age": 47,
                  "Previous incidents?": false
                }
                """;
        Map<String, Object> outputJSONasMap = evaluate(CTX, FILE_NAME);
        assertThat(outputJSONasMap).hasFieldOrPropertyWithValue("Base price", 500);
        assertThat(outputJSONasMap).hasFieldOrPropertyWithValue("Downpayment", 50);
    }

    @Test
    public void testScenario2() throws Exception {
        final String CTX = """
                {
                  "Age": 19,
                  "Previous incidents?": true
                }
                """;
        Map<String, Object> outputJSONasMap = evaluate(CTX, FILE_NAME);
        assertThat(outputJSONasMap).hasFieldOrPropertyWithValue("Base price", 1000);
        assertThat(outputJSONasMap).hasFieldOrPropertyWithValue("Downpayment", 70);
    }
}
