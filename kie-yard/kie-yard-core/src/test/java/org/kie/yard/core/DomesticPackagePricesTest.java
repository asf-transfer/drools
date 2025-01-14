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

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DomesticPackagePricesTest
        extends TestBase {

    private static final String FILE_NAME = "/domestic-package-prices.yml";

    @Test
    public void testMPackage() throws Exception {
        final String CTX = """
                {
                    "Height":10,
                    "Width":10,
                    "Length": 10,
                    "Weight":10
                }
                """;
        Map<String, Object> outputJSONasMap = evaluate(CTX, FILE_NAME);
        assertEquals(6.9, ((Map) outputJSONasMap.get("Package")).get("Cost"));
        assertEquals("M", ((Map) outputJSONasMap.get("Package")).get("Size"));
    }

    @Test
    public void testLPackage() throws Exception {
        final String CTX = """
                {
                    "Height":12,
                    "Width":10,
                    "Length": 10,
                    "Weight":10
                }
                """;
        Map<String, Object> outputJSONasMap = evaluate(CTX, FILE_NAME);
        assertEquals(8.9, ((Map) outputJSONasMap.get("Package")).get("Cost"));
        assertEquals("L", ((Map) outputJSONasMap.get("Package")).get("Size"));
    }
}
