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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MVELJSONTest
        extends TestBase {

    private static final String FILE_NAME = "/mvel-json-dot-access.yml";

    @Test
    public void testMVELManagesJSONMaps() throws Exception {
        final String CTX = """
                {
                    "Work Address":true
                }
                """;
        final Map<String, Object> output = evaluate(CTX, FILE_NAME);

        final Map mailingAddress = (Map) output.get("Mailing Address");
        assertEquals("Work Street", mailingAddress.get("Street"));
        assertEquals(23, mailingAddress.get("Number"));

        final List deliveryItemNames = (List) output.get("Delivery Item Names");
        assertEquals(3, deliveryItemNames.size());
        assertTrue(deliveryItemNames.contains("Work Shoes"));
        assertTrue(deliveryItemNames.contains("Work Hat"));
        assertTrue(deliveryItemNames.contains("Work Shirt"));

        final Map JSONTest= (Map) output.get("JSON Test");
        assertEquals("Best Company LTD", JSONTest.get("Company"));

        final Map mapTest = (Map) output.get("Map Test");
        assertEquals("Hello", mapTest.get("Map"));
    }
}
