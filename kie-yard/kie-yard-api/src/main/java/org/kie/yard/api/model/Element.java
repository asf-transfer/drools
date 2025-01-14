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
package org.kie.yard.api.model;

import java.util.List;

import org.kie.j2cl.tools.yaml.mapper.api.annotation.YAMLMapper;

@YAMLMapper
public class Element {

    private String name;
    private String type;
    private List<String> requirements;
    private DecisionLogic logic;

    public void setName(String name) {
        this.name = name;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setRequirements(List<String> requirements) {
        this.requirements = requirements;
    }

    public void setLogic(DecisionLogic logic) {
        this.logic = logic;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public List<String> getRequirements() {
        return requirements;
    }

    public DecisionLogic getLogic() {
        return logic;
    }
}
