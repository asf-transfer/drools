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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "runTurtleTests", matches = "true")
public class AddRemoveRulesAddDeleteFactsTest {

    public static Stream<StringPermutation> parameters() {
        final Collection<StringPermutation> rulesPermutations = new HashSet<>();

        final Set<StringPermutation> parametersPermutations = new HashSet<>();
        getStringPermutations(
                new String[]{TestUtil.RULE1_NAME, TestUtil.RULE2_NAME, TestUtil.RULE3_NAME},
                new String[]{},
                parametersPermutations);

        for (final StringPermutation permutation : parametersPermutations) {
            rulesPermutations.add(permutation);
        }

        return rulesPermutations.stream();
    }

    @ParameterizedTest
	@MethodSource("parameters")
    public void testAddRemoveRulesAddRemoveFacts(StringPermutation rulesPermutation) {
        final KieSession kieSession = TestUtil.buildSessionInSteps(getRules());
        try {
            final List<String> resultsList = new ArrayList<>();
            kieSession.setGlobal("list", resultsList);
            final List<FactHandle> insertedFacts = TestUtil.insertFacts(kieSession, getFacts());
            kieSession.fireAllRules();
            assertThat(resultsList).containsOnly(TestUtil.RULE1_NAME, TestUtil.RULE2_NAME, TestUtil.RULE3_NAME);
            resultsList.clear();
            TestUtil.removeRules(kieSession, TestUtil.RULES_PACKAGE_NAME, rulesPermutation.getPermutation());
            kieSession.fireAllRules();
            assertThat(resultsList).isEmpty();
            TestUtil.removeFacts(kieSession, insertedFacts);
            kieSession.fireAllRules();
            assertThat(resultsList).isEmpty();
        } finally {
            kieSession.dispose();
        }
    }

    private Object[] getFacts() {
        return new Object[]{1, "1"};
    }

    private static void getStringPermutations(final String[] rules, final String[] partialPermutation,
            final Collection<StringPermutation> foundPermutations) {
        if (rules.length == 1) {
            if (partialPermutation != null && partialPermutation.length > 0) {
                foundPermutations.add(new StringPermutation(concatenateStringArrays(partialPermutation, rules)));
            } else {
                foundPermutations.add(new StringPermutation(rules));
            }
        } else {
            for (int i = 0; i < rules.length; i++) {
                if (i == 0) {
                    getStringPermutations(
                            Arrays.copyOfRange(rules, 1, rules.length),
                            concatenateStringArrays(partialPermutation, new String[]{rules[i]}),
                            foundPermutations);
                } else if (i == rules.length - 1) {
                    getStringPermutations(
                            Arrays.copyOfRange(rules, 0, rules.length - 1),
                            concatenateStringArrays(partialPermutation, new String[]{rules[i]}),
                            foundPermutations);
                } else {
                    final String[] remainingRules = concatenateStringArrays(
                            Arrays.copyOfRange(rules, 0, i),
                            Arrays.copyOfRange(rules, i + 1, rules.length));

                    getStringPermutations(
                            remainingRules,
                            concatenateStringArrays(partialPermutation, new String[]{rules[i]}),
                            foundPermutations);
                }
            }
        }
    }

    private static String[] concatenateStringArrays(final String[] array1, final String[] array2) {
        final String[] concatenatedArray = new String[array1.length + array2.length];
        System.arraycopy(array1, 0, concatenatedArray, 0, array1.length);
        System.arraycopy(array2, 0, concatenatedArray, array1.length, array2.length);
        return concatenatedArray;
    }

    private static String[] getRules() {
        final String[] rules = new String[3];

        rules[0] = " package " + TestUtil.RULES_PACKAGE_NAME + ";\n" +
                " global java.util.List list\n" +
                " rule " + TestUtil.RULE1_NAME + " \n" +
                " when \n" +
                "   Integer() \n" +
                "   not(not(Integer() and Integer())) \n" +
                " then\n" +
                "   list.add('" + TestUtil.RULE1_NAME + "'); \n" +
                " end";

        rules[1] = " package " + TestUtil.RULES_PACKAGE_NAME + ";\n" +
                " global java.util.List list\n" +
                " rule " + TestUtil.RULE2_NAME + " \n" +
                " when \n" +
                "   Integer() \n" +
                "   exists(Integer() and Integer()) \n" +
                " then\n" +
                "   list.add('" + TestUtil.RULE2_NAME + "'); \n" +
                " end";

        rules[2] = " package " + TestUtil.RULES_PACKAGE_NAME + ";\n" +
                " global java.util.List list\n" +
                " rule " + TestUtil.RULE3_NAME + " \n" +
                " when \n" +
                "   Integer() \n" +
                "   exists(Integer() and Integer()) \n" +
                "   String() \n" +
                " then\n" +
                "   list.add('" + TestUtil.RULE3_NAME + "'); \n" +
                " end";

        return rules;
    }
}