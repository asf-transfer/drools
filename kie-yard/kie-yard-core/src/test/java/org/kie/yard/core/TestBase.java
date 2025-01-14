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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.drools.util.IoUtils;

public class TestBase {

    private JsonMapper jsonMapper = JsonMapper.builder().build();

    protected Map<String, Object> evaluate(String jsonInputCxt, String file) throws Exception {
        final String yamlDecision = read(file);
        final String OUTPUT_JSON = new YaRDRunner(yamlDecision).evaluate(jsonInputCxt);
        return readJSON(OUTPUT_JSON);
    }

    private String read(String file) throws IOException {
        return new String(IoUtils.readBytesFromInputStream(this.getClass().getResourceAsStream(file), true));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJSON(final String CONTEXT) throws JsonProcessingException {
        return jsonMapper.readValue(CONTEXT, Map.class);
    }
}
