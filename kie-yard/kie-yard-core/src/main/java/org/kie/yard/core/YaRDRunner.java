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

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.databind.json.JsonMapper;

public class YaRDRunner {

    private final YaRDDefinitions definitions;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final String name;

    public YaRDRunner(final String yaml) throws IOException {
        final YaRDParser parser = new YaRDParser(yaml);
        name = parser.getModel().getName();
        definitions = parser.getDefinitions();
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> evaluate(final Map<String, Object> map) {
        return definitions.evaluate(map);
    }

    public String evaluate(String jsonInputCxt) throws Exception {
        final Map<String, Object> inputContext = readJSON(jsonInputCxt);
        return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(evaluate(inputContext));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJSON(final String json) {
        try {
            return jsonMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read JSON " + json, e);
        }
    }
}
