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

public class ExtraCostsTest
        extends TestBase {

    private static final String FILE_NAME = "/extra-costs.yml";

    @Test
    public void testMPackage() throws Exception {
        final String CTX = """
                {
                    "Fragile":true,
                    "Package Tracking":true,
                    "Insurance":true,
                    "Package Type":"M"
                }
                """;
        Map<String, Object> outputJSONasMap = evaluate(CTX, FILE_NAME);
        assertThat(outputJSONasMap).hasFieldOrPropertyWithValue("Total cost of premiums", 40);
    }
}
