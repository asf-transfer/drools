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
import java.util.Collection;
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
import org.drools.testcoverage.common.model.Address;
import org.drools.testcoverage.common.model.Person;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.TestParametersUtil2;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.internal.builder.conf.EvaluatorOption;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomOperatorTest {

    public static Stream<KieBaseTestConfiguration> parameters() {
        return TestParametersUtil2.getKieBaseCloudConfigurations(true).stream();
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testCustomOperatorUsingCollections(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String constraints =
                "    $alice : Person(name == \"Alice\")\n" +
                "    $bob : Person(name == \"Bob\", addresses ##supersetOf $alice.addresses)\n";
        customOperatorUsingCollections(kieBaseTestConfiguration, constraints);
    }

    @ParameterizedTest(name = "KieBase type={0}")
    @MethodSource("parameters")
    public void testCustomOperatorUsingCollectionsWithNot(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String constraints =
                "    $alice : Person(name == \"Alice\")\n" +
                        "    $bob : Person(name == \"Bob\", $alice.addresses not ##supersetOf this.addresses)\n";
        customOperatorUsingCollections(kieBaseTestConfiguration, constraints);
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testNoOperatorInstancesCreatedAtRuntime(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String constraints =
                "    $alice : Person(name == \"Alice\")\n" +
                "    $bob : Person(name == \"Bob\", addresses ##supersetOf $alice.addresses)\n" +
                "    Person(name == \"Bob\", addresses ##supersetOf $alice.addresses)\n";

        customOperatorUsingCollections(kieBaseTestConfiguration, constraints);

        assertThat(SupersetOfEvaluatorDefinition.INSTANCES_COUNTER).isEqualTo(0);
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testCustomOperatorUsingCollectionsInverted(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // DROOLS-6983
        String constraints =
                "    $bob : Person(name == \"Bob\")\n" +
                "    $alice : Person(name == \"Alice\", $bob.addresses ##supersetOf this.addresses)\n";
        customOperatorUsingCollections(kieBaseTestConfiguration, constraints);
    }

    private void customOperatorUsingCollections(KieBaseTestConfiguration kieBaseTestConfiguration, String constraints) {
        final String drl =
                "import " + Address.class.getCanonicalName() + ";\n" +
                        "import " + Person.class.getCanonicalName() + ";\n" +
                        "rule R when\n" +
                        constraints +
                        "then\n" +
                        "end\n";

        System.setProperty(EvaluatorOption.PROPERTY_NAME + "supersetOf", SupersetOfEvaluatorDefinition.class.getName());
        try {
            final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("custom-operator-test", kieBaseTestConfiguration, drl);

            SupersetOfEvaluatorDefinition.INSTANCES_COUNTER = 0;

            final KieSession ksession = kbase.newKieSession();
            try {
                final Person alice = new Person("Alice", 30);
                alice.addAddress(new Address("Large Street", "BigTown", "12345"));
                final Person bob = new Person("Bob", 30);
                bob.addAddress(new Address("Large Street", "BigTown", "12345"));
                bob.addAddress(new Address("Long Street", "SmallTown", "54321"));

                ksession.insert(alice);
                ksession.insert(bob);

                assertThat(ksession.fireAllRules()).isEqualTo(1);
            } finally {
                ksession.dispose();
            }
        } finally {
            System.clearProperty(EvaluatorOption.PROPERTY_NAME + "supersetOf");
        }
    }

    public static class SupersetOfEvaluatorDefinition implements EvaluatorDefinition {

        public static final Operator SUPERSET_OF = Operator.addOperatorToRegistry("supersetOf", false);
        public static final Operator NOT_SUPERSET_OF = Operator.addOperatorToRegistry("supersetOf", true);
        private static final String[] SUPPORTED_IDS = {SUPERSET_OF.getOperatorString()};

        private Evaluator[] evaluator;

        static int INSTANCES_COUNTER = 0;

        public SupersetOfEvaluatorDefinition() {
            INSTANCES_COUNTER++;
        }

        public String[] getEvaluatorIds() {
            return SupersetOfEvaluatorDefinition.SUPPORTED_IDS;
        }

        public boolean isNegatable() {
            return true;
        }

        public Evaluator getEvaluator(final ValueType type, final String operatorId, final boolean isNegated, final String parameterText, final Target leftTarget, final Target rightTarget) {
            return new SupersetOfEvaluator(type, isNegated);
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

    public static class SupersetOfEvaluator extends BaseEvaluator {

        public SupersetOfEvaluator(final ValueType type, final boolean isNegated) {
            super(type, isNegated ? SupersetOfEvaluatorDefinition.NOT_SUPERSET_OF : SupersetOfEvaluatorDefinition.SUPERSET_OF);
        }

        public boolean evaluate(final ValueResolver valueResolver, final ReadAccessor extractor, final FactHandle factHandle, final FieldValue value) {
            final Object objectValue = extractor.getValue(valueResolver, factHandle);
            return evaluateAll((Collection) value.getValue(), (Collection) objectValue);
        }

        public boolean evaluate(final ValueResolver valueResolver, final ReadAccessor ira, final FactHandle left, final ReadAccessor ira1, final FactHandle right) {
            return evaluateAll((Collection) left.getObject(), (Collection) right.getObject());
        }

        public boolean evaluateCachedLeft(final ValueResolver valueResolver, final VariableRestriction.VariableContextEntry context, final FactHandle right) {
            final Object valRight = context.extractor.getValue(valueResolver, right.getObject());
            return evaluateAll((Collection) ((VariableRestriction.ObjectVariableContextEntry) context).left, (Collection) valRight);
        }

        public boolean evaluateCachedRight(final ValueResolver reteEvaluator, final VariableRestriction.VariableContextEntry context, final FactHandle left) {
            final Object varLeft = context.declaration.getExtractor().getValue(reteEvaluator, left);
            return evaluateAll((Collection) varLeft, (Collection) ((VariableRestriction.ObjectVariableContextEntry) context).right);
        }

        public boolean evaluateAll(final Collection leftCollection, final Collection rightCollection) {
            return getOperator().isNegated() ^ rightCollection.containsAll(leftCollection);
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testCustomOperatorOnKieModule(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String drl = "import " + Address.class.getCanonicalName() + ";\n" +
                "import " + Person.class.getCanonicalName() + ";\n" +
                "rule R when\n" +
                "    $alice : Person(name == \"Alice\")\n" +
                "    $bob : Person(name == \"Bob\", addresses ##supersetOf $alice.addresses)\n" +
                "then\n" +
                "end\n";

        System.setProperty(EvaluatorOption.PROPERTY_NAME + "supersetOf", SupersetOfEvaluatorDefinition.class.getName());
        try {
            final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("custom-operator-test", kieBaseTestConfiguration, drl);
            final KieSession ksession = kbase.newKieSession();
            try {
                final Person alice = new Person("Alice", 30);
                alice.addAddress(new Address("Large Street", "BigTown", "12345"));
                final Person bob = new Person("Bob", 30);
                bob.addAddress(new Address("Large Street", "BigTown", "12345"));
                bob.addAddress(new Address("Long Street", "SmallTown", "54321"));

                ksession.insert(alice);
                ksession.insert(bob);

                assertThat(ksession.fireAllRules()).isEqualTo(1);
            } finally {
                ksession.dispose();
            }
        } finally {
            System.clearProperty(EvaluatorOption.PROPERTY_NAME + "supersetOf");
        }
    }
}
