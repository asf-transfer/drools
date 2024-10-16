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
package org.drools.model.codegen.execmodel.operators;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.drools.model.codegen.execmodel.BaseModelTest2.RUN_TYPE;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.runtime.KieSession;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class EqualityComparisonWith2PropertiesTest extends BaseOperatorsTest {

	

    public static Stream<Arguments> ruleParams() {
        List<Arguments> parameterData = new ArrayList<Arguments>();
        for (RUN_TYPE runType : RUN_TYPES) {
            for (Class type : TYPES) {
                for (String operator : EQUALITY_COMPARISON_OPERATORS) {
                    for (boolean nullPropertyOnLeft : NULL_PROPERTY_ON_LEFT)
                        parameterData.add(arguments(runType, type, operator, nullPropertyOnLeft));
                }
            }
        }
        
        return Stream.of(parameterData.toArray(new Arguments[0]));
    }
	

    @BeforeEach
    public void setUp() {
        org.drools.compiler.rule.builder.util.ConstraintTestUtil.disableNormalizeConstraint();
        org.drools.model.codegen.execmodel.generator.ConstraintTestUtil.disableNormalizeConstraint();
    }

    @AfterEach
    public void tearDown() {
        org.drools.compiler.rule.builder.util.ConstraintTestUtil.enableNormalizeConstraint();
        org.drools.model.codegen.execmodel.generator.ConstraintTestUtil.enableNormalizeConstraint();
    }

    @ParameterizedTest(name = "{0} {1} {2} {3}")
	@MethodSource("ruleParams")
    public void compareWithNullProperty(RUN_TYPE testRunType, Class type, String operator, boolean nullPropertyOnLeft) throws Exception {
        String firstPropertyName = BaseOperatorsTest.getPropertyNameWithPrefix(type, "first");
        String secondPropertyName = BaseOperatorsTest.getPropertyNameWithPrefix(type, "second");
        String drl = "import " + type.getCanonicalName() + ";\n" +
                     "import " + ValueHolderWith2Properties.class.getCanonicalName() + ";\n" +
                     "rule R\n" +
                     "when\n";
        if (nullPropertyOnLeft) {
            drl += "  ValueHolderWith2Properties(" + firstPropertyName + " " + operator + " " + secondPropertyName + ")\n";
        } else {
            drl += "  ValueHolderWith2Properties(" + secondPropertyName + " " + operator + " " + firstPropertyName + ")\n";
        }
        drl += "then\n" +
               "end\n";

        KieSession ksession = getKieSession(drl, testRunType);
        ksession.insert(new ValueHolderWith2Properties());
        try {
            int fired = ksession.fireAllRules();
            if (operator.equals("==") && fired == 0 || operator.equals("!=") && fired == 1) {
                assertTrue(true);
            } else {
                fail("Wrong result");
            }
        } catch (org.drools.mvel.ConstraintEvaluationException | org.drools.modelcompiler.constraints.ConstraintEvaluationException e) {
            throw new RuntimeException("Unexpected Exception", e);
        }
    }
}
