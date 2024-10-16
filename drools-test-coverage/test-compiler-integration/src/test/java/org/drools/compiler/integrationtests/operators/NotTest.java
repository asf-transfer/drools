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
package org.drools.compiler.integrationtests.operators;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.drools.base.base.ClassObjectType;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.NodeMemories;
import org.drools.core.impl.InternalRuleBase;
import org.drools.core.reteoo.BetaMemory;
import org.drools.core.reteoo.BetaNode;
import org.drools.core.reteoo.NotRight;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.RightTuple;
import org.drools.core.reteoo.TupleImpl;
import org.drools.core.reteoo.TupleMemory;
import org.drools.core.util.FastIterator;
import org.drools.kiesession.session.StatefulKnowledgeSessionImpl;
import org.drools.testcoverage.common.model.AFact;
import org.drools.testcoverage.common.model.Cheese;
import org.drools.testcoverage.common.model.Person;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.TestParametersUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@RunWith(Parameterized.class)
public class NotTest {

    private final KieBaseTestConfiguration kieBaseTestConfiguration;

    public NotTest(final KieBaseTestConfiguration kieBaseTestConfiguration) {
        this.kieBaseTestConfiguration = kieBaseTestConfiguration;
    }

    @Parameterized.Parameters(name = "KieBase type={0}")
    public static Collection<Object[]> getParameters() {
        return TestParametersUtil.getKieBaseCloudConfigurations(true);
    }

    @Test
    public void testLastMemoryEntryNotBug() {
        // JBRULES-2809
        // This occurs when a blocker is the last in the node's memory, or if there is only one fact in the node
        // And it gets no opportunity to rematch with itself

        final String drl =
            "package org.drools.compiler.integrationtests.operators \n" +
            "import " + AFact.class.getCanonicalName() + "\n" +
            "global java.util.List list \n" +
            "rule x1 \n" +
            "when \n" +
            "    $s : String( this == 'x1' ) \n" +
            "    not AFact( this != null ) \n" +
            "then \n" +
            "  list.add(\"fired x1\"); \n" +
            "end  \n" +
            "rule x2 \n" +
            "when \n" +
            "    $s : String( this == 'x2' ) \n" +
            "    not AFact( field1 == $s, this != null ) \n" + // this ensures an index bucket
            "then \n" +
            "  list.add(\"fired x2\"); \n" +
            "end  \n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("not-test",
                                                                         kieBaseTestConfiguration,
                                                                         drl);
        final KieSession ksession = kbase.newKieSession();
        try {
            final List list = new ArrayList();
            ksession.setGlobal("list", list);

            ksession.insert("x1");
            ksession.insert("x2");
            final AFact a1 = new AFact("x1", null);
            final AFact a2 = new AFact("x2", null);

            final FactHandle fa1 = ksession.insert(a1);
            final FactHandle fa2 = ksession.insert(a2);

            // make sure the 'exists' is obeyed when fact is cycled causing add/remove node memory
            ksession.update(fa1, a1);
            ksession.update(fa2, a2);
            ksession.fireAllRules();

            assertThat(list.size()).isEqualTo(0);
        } finally {
            ksession.dispose();
        }
    }

    @Test
    public void testNegatedConstaintInNot() {

        final String drl =
                "package org.drools.compiler.integrationtests.operators;\n" +
                "import " + Person.class.getCanonicalName() + ";\n" +
                "\n" +
                "rule R1 when\n" +
                "    not( Person( !(age > 18) ) )\n" +
                "then\n" +
                "end";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("negated-not-test", kieBaseTestConfiguration, drl);

        final KieSession ksession = kbase.newKieSession();
        try {
            ksession.insert(new Person("Mario", 45));
            assertThat(ksession.fireAllRules()).isEqualTo(1);
        } finally {
            ksession.dispose();
        }
    }

    @Test
    public void testMissingRootBlockerEquality() {
        // DROOLS-6636
        final String drl =
                "package org.drools.compiler.integrationtests.operators;\n" +
                        "import " + Person.class.getCanonicalName() + ";\n" +
                        "import " + Cheese.class.getCanonicalName() + ";\n" +
                        "\n" +
                        "rule R1 when\n" +
                        "    Cheese($type : type)\n" +
                        "    not( Person( likes == $type, salary == null ) )\n" +
                        "then\n" +
                        "end";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("not-test", kieBaseTestConfiguration, drl);

        final KieSession ksession = kbase.newKieSession();
        try {
            Cheese cheese = new Cheese("cheddar");
            Person p1 = new Person("John");
            p1.setLikes("cheddar");
            p1.setSalary(null);
            Person p2 = new Person("Paul");
            p2.setLikes("cheddar");
            p2.setSalary(null);
            Person p3 = new Person("George");
            p3.setLikes("cheddar");
            p3.setSalary(null);

            ksession.insert(cheese);
            InternalFactHandle handle1 = (InternalFactHandle) ksession.insert(p1);
            InternalFactHandle handle2 = (InternalFactHandle) ksession.insert(p2);
            InternalFactHandle handle3 = (InternalFactHandle) ksession.insert(p3);
            assertThat(ksession.fireAllRules()).isEqualTo(0);

            InternalFactHandle blockerHandle = getBlockerFactHandle(ksession);
            Person blockerFact = (Person) blockerHandle.getObject(); // for example, it returns p3 "George"

            blockerFact.setAge(40); // modify unrelated property
            ksession.update(blockerHandle, blockerFact, "age");
            assertThat(ksession.fireAllRules()).isEqualTo(0);

            blockerFact.setSalary(new BigDecimal(1000)); // now this fact should match but remaining 2 facts shouldn't
            ksession.update(blockerHandle, blockerFact, "salary");
            assertThat(ksession.fireAllRules()).isEqualTo(0);

            // Then, modify remaining facts
            List<InternalFactHandle> handleList = new ArrayList<>();
            handleList.add(handle1);
            handleList.add(handle2);
            handleList.add(handle3);
            handleList.remove(blockerHandle);

            for (InternalFactHandle handle : handleList) {
                Person p = (Person) handle.getObject();
                p.setSalary(new BigDecimal(1000));
                ksession.update(handle, p, "salary");
            }
            assertThat(ksession.fireAllRules()).isEqualTo(1);

        } finally {
            ksession.dispose();
        }
    }

    private InternalFactHandle getBlockerFactHandle(KieSession ksession) {
        ObjectTypeNode otn = getObjectTypeNode(ksession.getKieBase(), Person.class);
        BetaNode notNode = ((NotRight)otn.getSinks()[0].getSinks()[0]).getBetaNode();

        StatefulKnowledgeSessionImpl ksessionImpl = (StatefulKnowledgeSessionImpl) ksession;
        NodeMemories                 nodeMemories = ksessionImpl.getNodeMemories();
        BetaMemory                   betaMemory = (BetaMemory) nodeMemories.getNodeMemory(notNode, ksessionImpl);
        TupleMemory                  rightTupleMemory = betaMemory.getRightTupleMemory();

        FastIterator<TupleImpl> it = rightTupleMemory.fullFastIterator();
        TupleImpl          rt = BetaNode.getFirstTuple(rightTupleMemory, it);

        for (; rt != null; rt = it.next(rt)) {
            if (((RightTuple)rt).getBlocked() != null) {
                return rt.getFactHandle();
            }
        }

        fail("Cannot find blocker in BetaMemory");
        return null;
    }

    public static ObjectTypeNode getObjectTypeNode(KieBase kbase, Class<?> nodeClass) {
        List<ObjectTypeNode> nodes = ((InternalRuleBase) kbase).getRete().getObjectTypeNodes();
        for (ObjectTypeNode n : nodes) {
            if (((ClassObjectType) n.getObjectType()).getClassType() == nodeClass) {
                return n;
            }
        }
        return null;
    }

    public static class Visit {
        private final String location;
        private String previous;

        private Visit(String location) {
            this(location, null);
        }

        private Visit(String location, String previous) {
            this.location = location;
            this.previous = previous;
        }

        public String getLocation() {
            return location;
        }

        public String getPrevious() {
            return previous;
        }

        public void setPrevious(String previous) {
            this.previous = previous;
        }

        @Override
        public String toString() {
            return "Visit{" +
                    "location='" + location + '\'' +
                    '}';
        }
    }

    @Test
    public void testNotWithInnerJoin() {
        // DROOLS-6652
        final String drl =
                "package org.drools.compiler.integrationtests.operators;\n" +
                "global java.util.List results;\n" +
                "import " + Visit.class.getCanonicalName() + ";\n" +
                "\n" +
                "rule R1 when\n" +
                "    $visit : Visit( previous != null )\n" +
                "    not( Visit( previous != null, previous == $visit.location ) )\n" +
                "then" +
                "    results.add($visit.getLocation());\n" +
                "end";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("not-test", kieBaseTestConfiguration, drl);
        final KieSession ksession = kbase.newKieSession();

        List<String> results = new ArrayList();
        ksession.setGlobal("results", results);

        Visit london = new Visit("London", "Brussels");
        Visit dublin = new Visit("Dublin", "London");
        Visit paris = new Visit("Paris", "Dublin");

        FactHandle dublinFH = ksession.insert(dublin);
        FactHandle londonFH = ksession.insert(london);
        FactHandle parisFH = ksession.insert(paris);

        ksession.fireAllRules();
        results.clear();

        paris.setPrevious("London");
        ksession.update(parisFH, paris);
        dublin.setPrevious(null);
        ksession.update(dublinFH, dublin);

        ksession.fireAllRules();
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0)).isEqualTo("Paris");
    }

    @Test
    public void testNotWithUnification() {
        // DROOLS-7629
        final String drl =
                "package org.drools.compiler.integrationtests.operators;\n" +
                "rule R1 when\n" +
                "      ( not ( $rao_v2 := String() and (\n" +
                "          $rao_v2 := String()\n" +
                "      ) ) and (\n" +
                "         $rao_v2 := String()\n" +
                "      )\n" +
                "   )\n" +
                "then\n" +
                "end";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("not-test", kieBaseTestConfiguration, drl);
        final KieSession ksession = kbase.newKieSession();

        ksession.insert("test");
        assertThat(ksession.fireAllRules()).isEqualTo(0);
    }
}
