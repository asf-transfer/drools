/**
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
package org.drools.compiler.integrationtests.incrementalcompilation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public abstract class AbstractAddRemoveGenerated2RulesTest {

    private String rule1;
    private String rule2;

    private void generateRules(final ConstraintsPair constraintsPair) {
        final String rule1 = "package " + TestUtil.RULES_PACKAGE_NAME + ";" +
                "global java.util.List list\n" +
                "rule " + TestUtil.RULE1_NAME + " \n" +
                " when \n ${constraints} " +
                "then\n" +
                " list.add('" + TestUtil.RULE1_NAME + "'); \n" +
                "end\n";

        final String rule2 = "package " + TestUtil.RULES_PACKAGE_NAME + ";" +
                "global java.util.List list\n" +
                "rule " + TestUtil.RULE2_NAME + " \n" +
                " when \n ${constraints} " +
                "then\n" +
                " list.add('" + TestUtil.RULE2_NAME + "'); \n" +
                "end\n";

        this.rule1 = rule1.replace("${constraints}", constraintsPair.getConstraints1());
        this.rule2 = rule2.replace("${constraints}", constraintsPair.getConstraints2());
    }

    // This takes only three different constraints - this is intentional, because it is needed to
    // keep the number of combinations at reasonable number.
    public static Collection<ConstraintsPair> generateRulesConstraintsCombinations(final String constraint1,
                                                                                     final String constraint2,
                                                                                     final String constraint3) {
        final Set<ConstraintsPair> constraintsPairs = new HashSet<>();
        final List<ConstraintsPair> result = new ArrayList<>();

        final List<String> constraintsList = new ArrayList<>();
        constraintsList.add(constraint1);
        constraintsList.add(constraint2);
        constraintsList.add(constraint3);
        final List<String> constraintsCombinations = getConstraintsCombinations(constraintsList);

        for (final String constraintsRule1 : constraintsCombinations) {
            for (final String constraintsRule2 : constraintsCombinations) {
                final ConstraintsPair constraintsPair = new ConstraintsPair(constraintsRule1, constraintsRule2);
                if (constraintsPairs.add(constraintsPair)) {
                    result.add(constraintsPair);
                }
            }
        }
        return result;
    }

    private static List<String> getConstraintsCombinations(final List<String> constraintsList) {
        final List<String> ruleConstraintsCombinations = new ArrayList<>();
        for (final String constraint : constraintsList) {
            for (final String constraint2 : constraintsList) {
                for (final String constraint3 : constraintsList) {
                    ruleConstraintsCombinations.add(constraint.replace("${variableNamePlaceholder}", "$i")
                            + constraint2.replace("${variableNamePlaceholder}", "$j")
                            + constraint3.replace("${variableNamePlaceholder}", "$k"));
                }
            }
        }
        return ruleConstraintsCombinations;
    }

    /////////////////////////// TESTS //////////////////////////////////

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    @Timeout(40000)
    public void testInsertFactsFireRulesRemoveRules(ConstraintsPair constraintsPair) {
    	generateRules(constraintsPair);
        AddRemoveTestCases.insertFactsFireRulesRemoveRules1(rule1, rule2, TestUtil.RULE1_NAME, TestUtil.RULE2_NAME, null, getFacts());
        AddRemoveTestCases.insertFactsFireRulesRemoveRules2(rule1, rule2, TestUtil.RULE1_NAME, TestUtil.RULE2_NAME, null, getFacts());
        AddRemoveTestCases.insertFactsFireRulesRemoveRules3(rule1, rule2, TestUtil.RULE1_NAME, TestUtil.RULE2_NAME, null, getFacts());
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    @Timeout(40000)
    public void testInsertFactsFireRulesRemoveRulesRevertedRules(ConstraintsPair constraintsPair) {
    	generateRules(constraintsPair);
        AddRemoveTestCases.insertFactsFireRulesRemoveRules1(rule2, rule1, TestUtil.RULE2_NAME, TestUtil.RULE1_NAME, null, getFacts());
        AddRemoveTestCases.insertFactsFireRulesRemoveRules2(rule2, rule1, TestUtil.RULE2_NAME, TestUtil.RULE1_NAME, null, getFacts());
        AddRemoveTestCases.insertFactsFireRulesRemoveRules3(rule2, rule1, TestUtil.RULE2_NAME, TestUtil.RULE1_NAME, null, getFacts());
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    @Timeout(40000)
    public void testFireRulesInsertFactsFireRulesRemoveRules(ConstraintsPair constraintsPair) {
    	generateRules(constraintsPair);
        AddRemoveTestCases.fireRulesInsertFactsFireRulesRemoveRules1(rule1, rule2, TestUtil.RULE1_NAME, TestUtil.RULE2_NAME, null, getFacts());
        AddRemoveTestCases.fireRulesInsertFactsFireRulesRemoveRules2(rule1, rule2, TestUtil.RULE1_NAME, TestUtil.RULE2_NAME, null, getFacts());
        AddRemoveTestCases.fireRulesInsertFactsFireRulesRemoveRules3(rule1, rule2, TestUtil.RULE1_NAME, TestUtil.RULE2_NAME, null, getFacts());
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    @Timeout(40000)
    public void testFireRulesInsertFactsFireRulesRemoveRulesRevertedRules(ConstraintsPair constraintsPair) {
    	generateRules(constraintsPair);
        AddRemoveTestCases.fireRulesInsertFactsFireRulesRemoveRules1(rule2, rule1, TestUtil.RULE2_NAME, TestUtil.RULE1_NAME, null, getFacts());
        AddRemoveTestCases.fireRulesInsertFactsFireRulesRemoveRules2(rule2, rule1, TestUtil.RULE2_NAME, TestUtil.RULE1_NAME, null, getFacts());
        AddRemoveTestCases.fireRulesInsertFactsFireRulesRemoveRules3(rule2, rule1, TestUtil.RULE2_NAME, TestUtil.RULE1_NAME, null, getFacts());
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    @Timeout(40000)
    public void testInsertFactsRemoveRulesFireRulesRemoveRules(ConstraintsPair constraintsPair) {
    	generateRules(constraintsPair);
        AddRemoveTestCases.insertFactsRemoveRulesFireRulesRemoveRules1(rule1, rule2, TestUtil.RULE1_NAME, TestUtil.RULE2_NAME, null, getFacts());
        AddRemoveTestCases.insertFactsRemoveRulesFireRulesRemoveRules2(rule1, rule2, TestUtil.RULE1_NAME, TestUtil.RULE2_NAME, null, getFacts());
        AddRemoveTestCases.insertFactsRemoveRulesFireRulesRemoveRules3(rule1, rule2, TestUtil.RULE1_NAME, TestUtil.RULE2_NAME, null, getFacts());
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    @Timeout(40000)
    public void testInsertFactsRemoveRulesFireRulesRemoveRulesRevertedRules(ConstraintsPair constraintsPair) {
    	generateRules(constraintsPair);
        AddRemoveTestCases.insertFactsRemoveRulesFireRulesRemoveRules1(rule2, rule1, TestUtil.RULE2_NAME, TestUtil.RULE1_NAME, null, getFacts());
        AddRemoveTestCases.insertFactsRemoveRulesFireRulesRemoveRules2(rule2, rule1, TestUtil.RULE2_NAME, TestUtil.RULE1_NAME, null, getFacts());
        AddRemoveTestCases.insertFactsRemoveRulesFireRulesRemoveRules3(rule2, rule1, TestUtil.RULE2_NAME, TestUtil.RULE1_NAME, null, getFacts());
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    @Timeout(40000)
    public void testInsertFactsFireRulesRemoveRulesReinsertRules(ConstraintsPair constraintsPair) {
    	generateRules(constraintsPair);
        AddRemoveTestCases.insertFactsFireRulesRemoveRulesReinsertRules1(rule1, rule2, TestUtil.RULE1_NAME, TestUtil.RULE2_NAME, null, getFacts());
        AddRemoveTestCases.insertFactsFireRulesRemoveRulesReinsertRules2(rule1, rule2, TestUtil.RULE1_NAME, TestUtil.RULE2_NAME, null, getFacts());
        AddRemoveTestCases.insertFactsFireRulesRemoveRulesReinsertRules3(rule1, rule2, TestUtil.RULE1_NAME, TestUtil.RULE2_NAME, null, getFacts());
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    @Timeout(40000)
    public void testInsertFactsFireRulesRemoveRulesReinsertRulesRevertedRules(ConstraintsPair constraintsPair) {
    	generateRules(constraintsPair);
        AddRemoveTestCases.insertFactsFireRulesRemoveRulesReinsertRules1(rule2, rule1, TestUtil.RULE2_NAME, TestUtil.RULE1_NAME, null, getFacts());
        AddRemoveTestCases.insertFactsFireRulesRemoveRulesReinsertRules2(rule2, rule1, TestUtil.RULE2_NAME, TestUtil.RULE1_NAME, null, getFacts());
        AddRemoveTestCases.insertFactsFireRulesRemoveRulesReinsertRules3(rule2, rule1, TestUtil.RULE2_NAME, TestUtil.RULE1_NAME, null, getFacts());
    }

    private Object[] getFacts() {
        final Map<Object, String> mapFact = new HashMap<>(1);
        mapFact.put(new Object(), "1");
        return new Object[] {1, 2, 3, "1", mapFact};
    }
}
