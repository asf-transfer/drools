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
package org.drools.compiler.integrationtests;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.stream.Stream;

import org.drools.base.base.ValueResolver;
import org.drools.base.base.ValueType;
import org.drools.compiler.rule.builder.EvaluatorDefinition;
import org.drools.drl.parser.impl.Operator;
import org.drools.base.rule.accessor.Evaluator;
import org.drools.base.rule.accessor.FieldValue;
import org.drools.base.rule.accessor.ReadAccessor;
import org.drools.mvel.evaluators.BaseEvaluator;
import org.drools.mvel.evaluators.VariableRestriction;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.TestParametersUtil2;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.internal.builder.conf.EvaluatorOption;

public class CustomOperatorOnlyDrlTest {

    public static Stream<KieBaseTestConfiguration> parameters() {
        // TODO EM DROOLS-6302
        return TestParametersUtil2.getKieBaseCloudConfigurations(false).stream();
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testCustomOperatorCombiningConstraints(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // JBRULES-3517
        final String drl =
                "declare GN\n" +
                        "   gNo : Double\n" +
                        "end\n" +
                        "\n" +
                        "declare t547147\n" +
                        "   c547148 : String\n" +
                        "   c547149 : String\n" +
                        "end\n" +
                        "\n" +
                        "declare Tra48\n" +
                        "   gNo : Double\n" +
                        "   postCode : String\n" +
                        "   name : String\n" +
                        "   cnt : String\n" +
                        "end\n" +
                        "\n" +
                        "rule \"r548695.1\"\n" +
                        "no-loop true\n" +
                        "dialect \"mvel\"\n" +
                        "when\n" +
                        "   gnId : GN()\n" +
                        "   la : t547147( )\n" +
                        "   v1717 : Tra48( gnId.gNo == gNo, name ##F_str[startsWith] la.c547148 || postCode ##F_str[contains] la.c547149 )\n" +
                        "then\n" +
                        "end\n";

        System.setProperty(EvaluatorOption.PROPERTY_NAME + "str", F_StrEvaluatorDefinition.class.getName());
        try {
            KieBaseUtil.getKieBaseFromKieModuleFromDrl("custom-operator-test", kieBaseTestConfiguration, drl);
        } finally {
            System.clearProperty(EvaluatorOption.PROPERTY_NAME + "str");
        }
    }

    public static class F_StrEvaluatorDefinition implements EvaluatorDefinition {

        public static final Operator STR_COMPARE = Operator.addOperatorToRegistry("F_str", false);
        public static final Operator NOT_STR_COMPARE = Operator.addOperatorToRegistry("F_str", true);
        private static final String[] SUPPORTED_IDS = {STR_COMPARE.getOperatorString()};

        public enum Operations {

            startsWith,
            endsWith,
            length,
            contains,
            bidicontains;
        }

        private Evaluator[] evaluator;

        public String[] getEvaluatorIds() {
            return F_StrEvaluatorDefinition.SUPPORTED_IDS;
        }

        public boolean isNegatable() {
            return true;
        }

        public Evaluator getEvaluator(final ValueType type, final String operatorId, final boolean isNegated, final String parameterText, final Target leftTarget, final Target rightTarget) {
            final F_StrEvaluator evaluatorLocal = new F_StrEvaluator(type, isNegated);
            evaluatorLocal.setParameterText(parameterText);
            return evaluatorLocal;
        }

        public Evaluator getEvaluator(final ValueType type, final String operatorId, final boolean isNegated, final String parameterText) {
            return getEvaluator(type, operatorId, isNegated, parameterText, Target.FACT, Target.FACT);
        }

        public Evaluator getEvaluator(final ValueType type, final Operator operator, final String parameterText) {
            return this.getEvaluator(type, operator.getOperatorString(), operator.isNegated(), parameterText);
        }

        public Evaluator getEvaluator(final ValueType type, final Operator operator) {
            return this.getEvaluator(type, operator.getOperatorString(), operator.isNegated(), null);
        }

        public boolean supportsType(final ValueType vt) {
            return true;
        }

        public Target getTarget() {
            return Target.FACT;
        }

        public void writeExternal(final ObjectOutput out) throws IOException {
            out.writeObject(evaluator);
        }

        public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
            evaluator = (Evaluator[]) in.readObject();
        }
    }

    public static class F_StrEvaluator extends BaseEvaluator {

        private F_StrEvaluatorDefinition.Operations parameter;

        public void setParameterText(final String parameterText) {
            this.parameter = F_StrEvaluatorDefinition.Operations.valueOf(parameterText);
        }

        public F_StrEvaluatorDefinition.Operations getParameter() {
            return parameter;
        }

        public F_StrEvaluator(final ValueType type, final boolean isNegated) {
            super(type, isNegated ? F_StrEvaluatorDefinition.NOT_STR_COMPARE : F_StrEvaluatorDefinition.STR_COMPARE);
        }

        public boolean evaluate(final ValueResolver valueResolver, final ReadAccessor extractor, final FactHandle factHandle, final FieldValue value) {
            final Object objectValue = extractor.getValue(valueResolver, factHandle);
            final String objectValueString = (String) objectValue;
            return evaluateAll((String) value.getValue(), objectValueString);
        }

        public boolean evaluate(final ValueResolver valueResolver, final ReadAccessor ira, final FactHandle left, final ReadAccessor ira1, final FactHandle right) {
            return evaluateAll((String) left.getObject(), (String) right.getObject());
        }

        public boolean evaluateCachedLeft(final ValueResolver valueResolver, final VariableRestriction.VariableContextEntry context, final FactHandle right) {
            final Object valRight = context.extractor.getValue(valueResolver, right);
            return evaluateAll((String) ((VariableRestriction.ObjectVariableContextEntry) context).left, (String) valRight);
        }

        public boolean evaluateCachedRight(final ValueResolver valueResolver, final VariableRestriction.VariableContextEntry context, final FactHandle left) {
            final Object varLeft = context.declaration.getExtractor().getValue(valueResolver, left);
            return evaluateAll((String) varLeft, (String) ((VariableRestriction.ObjectVariableContextEntry) context).right);
        }

        public boolean evaluateAll(final String leftString, final String rightString) {
            boolean result = ((leftString != null) && (rightString != null));

            if (result) {
                switch (parameter) {
                    case startsWith:
                        result = this.getOperator().isNegated() ^ (leftString.startsWith(rightString));
                        return result;
                    case endsWith:
                        result = this.getOperator().isNegated() ^ (leftString.endsWith(rightString));
                        return result;
                }
            }
            return result;
        }
    }
}
