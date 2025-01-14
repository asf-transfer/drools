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
public class YaRD {

    private String specVersion = "alpha";
    private String kind = "YaRD";
    private String name;
    private String expressionLang;
    private List<Input> inputs;
    private List<Element> elements;

    public void setInputs(List<Input> inputs) {
        this.inputs = inputs;
    }

    public void setElements(List<Element> elements) {
        this.elements = elements;
    }

    public String getName() {
        return name;
    }

    public String getExpressionLang() {
        return expressionLang;
    }

    public void setExpressionLang(String expressionLang) {
        this.expressionLang = expressionLang;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getSpecVersion() {
        return specVersion;
    }

    public void setSpecVersion(String specVersion) {
        this.specVersion = specVersion;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Input> getInputs() {
        return inputs;
    }

    public List<Element> getElements() {
        return elements;
    }
}
