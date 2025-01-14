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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.drools.ruleunits.api.SingletonStore;

public record YaRDDefinitions(
        Map<String, SingletonStore<Object>> inputs,
        List<Firable> units,
        Map<String, StoreHandle<Object>> outputs) {

    public Map<String, Object> evaluate(Map<String, Object> context) {
        Map<String, Object> results = new LinkedHashMap<>(context);
        for (String inputKey : inputs.keySet()) {
            if (!context.containsKey(inputKey)) {
                throw new IllegalArgumentException("Missing input key in context: " + inputKey);
            }
            Object inputValue = context.get(inputKey);
            inputs.get(inputKey).set(inputValue);
        }
        for (Firable unit : units) {
            unit.fire(context, this);
        }
        for (Entry<String, StoreHandle<Object>> outputSets : outputs.entrySet()) {
            results.put(outputSets.getKey(), outputSets.getValue().get());
        }
        reset();
        return results;
    }

    private void reset() {
        inputs.forEach((k, v) -> v.clear());
        outputs.forEach((k, v) -> v.clear());
    }
}
