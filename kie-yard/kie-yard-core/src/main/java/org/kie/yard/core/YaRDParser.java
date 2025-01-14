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
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import org.drools.ruleunits.api.DataSource;
import org.kie.yard.api.model.DecisionLogic;
import org.kie.yard.api.model.Element;
import org.kie.yard.api.model.Input;
import org.kie.yard.api.model.YaRD;
import org.kie.yard.api.model.YaRD_YamlMapperImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class YaRDParser {

    private static final Logger LOG = LoggerFactory.getLogger(YaRDParser.class);
    private final YaRDDefinitions definitions = new YaRDDefinitions(new HashMap<>(), new ArrayList<>(), new HashMap<>());
    private final YaRD model;
    private final String yaml;

    public YaRDParser(Reader reader) throws Exception {
        yaml = read(reader);
        model = getModel(yaml);
        parse(yaml);
    }

    public YaRDParser(String yaml) throws IOException {
        this.yaml = yaml;
        model = getModel(yaml);
        parse(yaml);
    }

    public YaRD getModel() {
        return model;
    }

    public String getYaml() {
        return yaml;
    }

    public YaRDDefinitions getDefinitions() {
        return definitions;
    }

    private String read(Reader reader) throws Exception {
        final StringBuilder fileData = new StringBuilder(1000);
        char[] buf = new char[1024];
        int numRead;
        while ((numRead = reader.read(buf)) != -1) {
            String readData = String.valueOf(buf,
                    0,
                    numRead);
            fileData.append(readData);
            buf = new char[1024];
        }
        reader.close();
        return fileData.toString();
    }

    private YaRD getModel(String yaml) throws IOException {
        return new YaRD_YamlMapperImpl().read(yaml);
    }

    private YaRDDefinitions parse(String yaml) throws IOException {
        final YaRD sd = new YaRD_YamlMapperImpl().read(yaml);
        appendInputs(sd.getInputs());
        appendUnits(sd.getElements());
        return definitions;
    }

    private void appendUnits(List<Element> list) {
        final List<String> existingNames = new ArrayList<>();
        for (Element hi : list) {
            final String nameString = hi.getName();
            if (existingNames.contains(nameString)) {
                throw new IllegalArgumentException("Two element definitions with the same name are not allowed.");
            } else {
                existingNames.add(nameString);
            }
            LOG.debug("parsing {}", nameString);
            final Firable decisionLogic = createDecisionLogic(nameString, hi.getLogic());
            definitions.units().add(decisionLogic);
        }
    }

    private Firable createDecisionLogic(String nameString, DecisionLogic decisionLogic) {
        if (decisionLogic instanceof org.kie.yard.api.model.DecisionTable decisionTable) {
            return new SyntheticRuleUnitWrapper(new DTableUnitBuilder(definitions, nameString, decisionTable).build());
        } else if (decisionLogic instanceof org.kie.yard.api.model.LiteralExpression literalExpression) {
            return new LiteralExpressionBuilder(model.getExpressionLang(), definitions, nameString, literalExpression).build();
        } else {
            throw new UnsupportedOperationException("Not implemented.");
        }
    }

    private void appendInputs(List<Input> list) {
        for (Input hi : list) {
            String nameString = hi.getName();
            @SuppressWarnings("unused")
            Class<?> typeRef = processType(hi.getType());
            definitions.inputs().put(nameString, DataSource.createSingleton());
        }
    }

    private Class<?> processType(String string) {
        switch (string) {
            case "string":
            case "number":
            case "boolean":
            default:
                return Object.class;
        }
    }
}
