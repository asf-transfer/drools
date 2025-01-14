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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.drools.model.Index;
import org.drools.ruleunits.api.SingletonStore;
import org.drools.ruleunits.dsl.SyntheticRuleUnit;
import org.drools.ruleunits.dsl.SyntheticRuleUnitBuilder;
import org.kie.yard.api.model.InlineRule;
import org.kie.yard.api.model.Rule;
import org.kie.yard.api.model.WhenThenRule;

public class DTableUnitBuilder {

    private final YaRDDefinitions definitions;
    private final String name;
    private final OnExecute executionAction;
    private final List<Rule> rules;
    private final List<String> inputs;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public DTableUnitBuilder(
            final YaRDDefinitions definitions,
            final String name,
            final org.kie.yard.api.model.DecisionTable dtableDefinition) {
        this.inputs = dtableDefinition.getInputs();
        if (inputs.isEmpty()) {
            throw new IllegalStateException("Empty decision table?");
        }
        this.definitions = definitions;
        this.name = name;
        this.rules = dtableDefinition.getRules();
        this.executionAction = getExecutionAction(dtableDefinition.getHitPolicy());
    }

    public SyntheticRuleUnit build() {

        final SyntheticRuleUnitBuilder unit = SyntheticRuleUnitBuilder.build(name);
        for (Map.Entry<String, SingletonStore<Object>> e : definitions.inputs().entrySet()) {
            unit.registerDataSource(e.getKey(), e.getValue(), Object.class);
        }
        final StoreHandle<Object> result = StoreHandle.empty(Object.class);
        unit.registerGlobal(name, result);
        definitions.outputs().put(name, result);
        return unit.defineRules(rulesFactory -> {
            for (Rule ruleDefinition : rules) {
                var rule = rulesFactory.rule();
                for (int idx = 0; idx < inputs.size(); idx++) {
                    final RuleCell ruleCell = parseGenericRuleCell(ruleDefinition, idx);
                    if (ruleCell.value != null) {
                        final SingletonStore<Object> dataSource = definitions.inputs().get(inputs.get(idx));

                        rule.on(dataSource).filter(ruleCell.idxtype, ruleCell.value);
                    }
                }
                rule.execute(result, storeHandle -> executionAction.onExecute(ruleDefinition, storeHandle));
            }
        });
    }

    private OnExecute getExecutionAction(String hitPolicy) {
        if (hitPolicy == null || Objects.equals("ANY", hitPolicy)) {
            return (ruleDefinition, storeHandle) -> {
                final RuleCell rc = parseGenericRuleThen(ruleDefinition);
                Object value = resolveValue(rc.value);
                storeHandle.set(value);
            };
        } else if (Objects.equals("FIRST", hitPolicy)) {
            return (ruleDefinition, storeHandle) -> {
                if (!storeHandle.isValuePresent()) {
                    final RuleCell rc = parseGenericRuleThen(ruleDefinition);
                    storeHandle.set(resolveValue(rc.value));
                }
            };
        } else if (Objects.equals("COLLECT", hitPolicy)) {
            return (ruleDefinition, storeHandle) -> {
                if (!storeHandle.isValuePresent()) {
                    storeHandle.set(new ArrayList<>());
                }
                final RuleCell rc = parseGenericRuleThen(ruleDefinition);

                if (storeHandle.get() instanceof List list) {
                    list.add(resolveValue(rc.value));
                }
                String remove = "";
            };
        } else {
            throw new UnsupportedOperationException("Not implemented ");
        }
    }

    private Object resolveValue(Object value) {
        try {
            if (value instanceof String text) {
                return jsonMapper.readValue(text, Map.class);
            }
        } catch (JsonProcessingException ignored) {
        }
        return value;
    }

    private RuleCell parseGenericRuleThen(Rule rule) {
        if (rule instanceof InlineRule inlineRule) {
            return parseRuleCell(inlineRule.getDef().get(inlineRule.getDef().size() - 1));
        } else if (rule instanceof WhenThenRule whenThenRule) {
            return parseRuleCell((whenThenRule).getThen());
        } else {
            throw new IllegalStateException("Unknown or unmanaged rule instance type?");
        }
    }

    private RuleCell parseGenericRuleCell(Rule rule, int i) {
        if (rule instanceof InlineRule inlineRule) {
            return parseRuleCell((inlineRule).getDef().get(i));
        } else if (rule instanceof WhenThenRule whenThenRule) {
            return parseRuleCell((whenThenRule).getWhen().get(i));
        } else {
            throw new IllegalStateException("Unknown or unmanaged rule instance type?");
        }
    }

    private RuleCell parseRuleCell(Object object) {
        if (object instanceof Boolean) {
            return new RuleCell(Index.ConstraintType.EQUAL, object);
        } else if (object instanceof Number) {
            return new RuleCell(Index.ConstraintType.EQUAL, object);
        } else if (object instanceof String valueString) {
            if (valueString.startsWith("<=")) { // pay attention to ordering when not using a parser like in this case.
                return new RuleCell(Index.ConstraintType.LESS_OR_EQUAL, parseConstrainedCellString(valueString.substring(2)));
            } else if (valueString.startsWith(">=")) {
                return new RuleCell(Index.ConstraintType.GREATER_OR_EQUAL, parseConstrainedCellString(valueString.substring(2)));
            } else if (valueString.startsWith("<")) {
                return new RuleCell(Index.ConstraintType.LESS_THAN, parseConstrainedCellString(valueString.substring(1)));
            } else if (valueString.startsWith(">")) {
                return new RuleCell(Index.ConstraintType.GREATER_THAN, parseConstrainedCellString(valueString.substring(1)));
            } else {
                return new RuleCell(Index.ConstraintType.EQUAL, parseConstrainedCellString(valueString));
            }
        } else {
            throw new IllegalStateException("Unmanaged case, please report!");
        }
    }

    private Object parseConstrainedCellString(String substring) {
        if (Objects.equals("true", substring.trim().toLowerCase())) {
            return true;
        }
        if (Objects.equals("false", substring.trim().toLowerCase())) {
            return false;
        }
        try {
            return Integer.parseInt(substring.trim());
        } catch (Exception e) {

        }
        try {
            return Long.parseLong(substring.trim());
        } catch (Exception e) {

        }
        if (Objects.equals("-", substring.trim())) {
            return null;
        }
        return substring;
    }

    private interface OnExecute {

        void onExecute(Rule ruleDefinition, StoreHandle<Object> storeHandle);
    }

    public static record RuleCell(Index.ConstraintType idxtype, Object value) {

    }
}
