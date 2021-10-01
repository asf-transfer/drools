/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.compiler.integrationtests.operators;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.NodeMemories;
import org.drools.core.impl.StatefulKnowledgeSessionImpl;
import org.drools.core.reteoo.AlphaNode;
import org.drools.core.reteoo.BetaMemory;
import org.drools.core.reteoo.NotNode;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.RightTupleImpl;
import org.drools.core.spi.Tuple;
import org.drools.core.util.index.TupleIndexHashTable;
import org.drools.testcoverage.common.model.AFact;
import org.drools.testcoverage.common.model.Cheese;
import org.drools.testcoverage.common.model.Person;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.ReteUtil;
import org.drools.testcoverage.common.util.TestParametersUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

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

            assertEquals(0, list.size());
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
            assertEquals(1, ksession.fireAllRules());
        } finally {
            ksession.dispose();
        }
    }

    @Test
    public void testMissingRootBlockerEquality() {

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
            assertEquals(0, ksession.fireAllRules());

            InternalFactHandle blockerHandle = getBlockerFactHandle(ksession);
            Person blockerFact = (Person) blockerHandle.getObject(); // for example, it returns p3 "George"

            blockerFact.setAge(40); // modify unrelated property
            ksession.update(blockerHandle, blockerFact, "age");
            assertEquals(0, ksession.fireAllRules());

            blockerFact.setSalary(new BigDecimal(1000)); // now this fact should match but remaining 2 facts shouldn't
            ksession.update(blockerHandle, blockerFact, "salary");
            assertEquals(0, ksession.fireAllRules());

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
            assertEquals(1, ksession.fireAllRules());

        } finally {
            ksession.dispose();
        }
    }

    private InternalFactHandle getBlockerFactHandle(KieSession ksession) {
        ObjectTypeNode otn = ReteUtil.getObjectTypeNode(ksession.getKieBase(), Person.class);
        NotNode notNode = (NotNode) ((AlphaNode) otn.getSinks()[0]).getSinks()[0];

        StatefulKnowledgeSessionImpl ksessionImpl = (StatefulKnowledgeSessionImpl) ksession;
        NodeMemories nodeMemories = ksessionImpl.getNodeMemories();
        BetaMemory betaMemory = (BetaMemory) nodeMemories.getNodeMemory(notNode, ksessionImpl);
        TupleIndexHashTable rightTupleMemory = (TupleIndexHashTable) betaMemory.getRightTupleMemory();
        Tuple[] tuples = rightTupleMemory.toArray();
        for (int i = 0; i < tuples.length; i++) {
            RightTupleImpl tuple = (RightTupleImpl) tuples[i];
            if (tuple.getBlocked() != null) {
                return tuple.getFactHandle();
            }
        }

        fail("Cannot find blocker in BetaMemory");
        return null;
    }

}
