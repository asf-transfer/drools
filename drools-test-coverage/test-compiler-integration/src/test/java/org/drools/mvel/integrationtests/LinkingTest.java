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
package org.drools.mvel.integrationtests;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.drools.base.base.ClassObjectType;
import org.drools.core.common.InternalAgenda;
import org.drools.core.common.InternalAgendaGroup;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.MemoryFactory;
import org.drools.core.impl.InternalRuleBase;
import org.drools.core.reteoo.BetaMemory;
import org.drools.core.reteoo.RightInputAdapterNode;
import org.drools.core.reteoo.TupleToObjectNode.SubnetworkPathMemory;
import org.drools.kiesession.session.StatefulKnowledgeSessionImpl;
import org.drools.core.phreak.RuleAgendaItem;
import org.drools.core.phreak.RuleExecutor;
import org.drools.core.phreak.RuntimeSegmentUtilities;
import org.drools.core.reteoo.EvalConditionNode;
import org.drools.core.reteoo.ExistsNode;
import org.drools.core.reteoo.JoinNode;
import org.drools.core.reteoo.LeftInputAdapterNode;
import org.drools.core.reteoo.LeftInputAdapterNode.LiaNodeMemory;
import org.drools.core.reteoo.NotNode;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.PathMemory;
import org.drools.core.reteoo.TupleToObjectNode;
import org.drools.core.reteoo.RuleTerminalNode;
import org.drools.core.reteoo.SegmentMemory;
import org.drools.core.reteoo.Tuple;
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

@RunWith(Parameterized.class)
public class LinkingTest {

    // Note: Replaced class D with X : See DROOLS-6032

    private final KieBaseTestConfiguration kieBaseTestConfiguration;

    public LinkingTest(final KieBaseTestConfiguration kieBaseTestConfiguration) {
        this.kieBaseTestConfiguration = kieBaseTestConfiguration;
    }

    @Parameterized.Parameters(name = "KieBase type={0}")
    public static Collection<Object[]> getParameters() {
        return TestParametersUtil.getKieBaseCloudConfigurations(true);
    }

    public static class A {
        private int value;

        public A() {

        }

        public A(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }

    public static class B {
        private int value;

        public B() {

        }

        public B(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }

    public static class C {
        private int value;

        public C() {

        }

        public C(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }

    public static class X {
        private int value;

        public X() {

        }

        public X(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }

    public static class E {
        private int value;

        public E() {

        }

        public E(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }

    public static class F {
        private int value;

        public F() {

        }

        public F(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }

    public static class G {
        private int value;

        public G() {

        }

        public G(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }
    }

    @Test
    public void testSubNetworkSharing() throws Exception {
        // Checks the network is correctly formed, with sharing
        String str = "";
        str += "package org.kie \n";
        str += "import " + A.class.getCanonicalName() + "\n" ;
        str += "import " + B.class.getCanonicalName() + "\n" ;
        str += "import " + C.class.getCanonicalName() + "\n" ;
        str += "import " + X.class.getCanonicalName() + "\n" ;
        str += "import " + E.class.getCanonicalName() + "\n" ;
        str += "global java.util.List list \n";

        str += "rule rule1 when \n";
        str += "   A() \n";
        str += "   B() \n";
        str += "   C() \n";
        str += "   X() \n";
        str += "then \n";
        str += "end \n";

        str += "rule rule2 when \n";
        str += "   A() \n";
        str += "   exists( B() and C() ) \n";
        str += "   X() \n";
        str += "then \n";
        str += "end \n";

        str += "rule rule3 when \n";
        str += "   A() \n";
        str += "   exists( B() and C() and X() ) \n";
        str += "   E() \n";
        str += "then \n";
        str += "end \n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);

        ObjectTypeNode node = getObjectTypeNode(kbase, A.class );
        InternalWorkingMemory wm = ((StatefulKnowledgeSessionImpl)kbase.newKieSession());

        LeftInputAdapterNode liaNode = (LeftInputAdapterNode) node.getObjectSinkPropagator().getSinks()[0];
        assertThat(liaNode.getSinkPropagator().size()).isEqualTo(3);

        ExistsNode existsNode2 = ( ExistsNode) liaNode.getSinkPropagator().getSinks()[1];

        ExistsNode existsNode3 = ( ExistsNode) liaNode.getSinkPropagator().getSinks()[2];

        JoinNode joinNodeB = ( JoinNode) liaNode.getSinkPropagator().getSinks()[0];
        assertThat(getObjectTypeNode(kbase, B.class)).isSameAs(joinNodeB.getRightInput().getParent());

        JoinNode joinNodeC = ( JoinNode) joinNodeB.getSinkPropagator().getSinks()[0];
        assertThat(getObjectTypeNode(kbase, C.class)).isSameAs(joinNodeC.getRightInput().getParent());
        assertThat(joinNodeC.getSinkPropagator().size()).isEqualTo(2);

        JoinNode joinNodeD = ( JoinNode) joinNodeC.getSinkPropagator().getSinks()[0];
        assertThat(getObjectTypeNode(kbase, X.class)).isSameAs(joinNodeD.getRightInput().getParent());
        assertThat(joinNodeD.getSinkPropagator().size()).isEqualTo(2);


        assertThat(((RightInputAdapterNode)((TupleToObjectNode) joinNodeC.getSinkPropagator().getSinks()[1]).getObjectSinkPropagator().getSinks()[0]).getBetaNode()).isSameAs(existsNode2);

        assertThat(((RightInputAdapterNode)((TupleToObjectNode) joinNodeD.getSinkPropagator().getSinks()[1]).getObjectSinkPropagator().getSinks()[0]).getBetaNode()).isSameAs(existsNode3);
    }

    @Test
    public void testSubNetworkSharingMemories() throws Exception {
        // checks the memory sharing works, and linking, uses the already checked network from testSubNetworkSharing
        String str = "";
        str += "package org.kie \n";
        str += "import " + A.class.getCanonicalName() + "\n" ;
        str += "import " + B.class.getCanonicalName() + "\n" ;
        str += "import " + C.class.getCanonicalName() + "\n" ;
        str += "import " + X.class.getCanonicalName() + "\n" ;
        str += "import " + E.class.getCanonicalName() + "\n" ;
        str += "global java.util.List list \n";

        str += "rule rule1 when \n";
        str += "   A() \n";
        str += "   B() \n";
        str += "   C() \n";
        str += "   X() \n";
        str += "then \n";
        str += "end \n";

        str += "rule rule2 when \n";
        str += "   A() \n";
        str += "   exists( B() and C() ) \n";
        str += "   X() \n";
        str += "then \n";
        str += "end \n";

        str += "rule rule3 when \n";
        str += "   A() \n";
        str += "   exists( B() and C() and X() ) \n";
        str += "   E() \n";
        str += "then \n";
        str += "end \n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);

        ObjectTypeNode node = getObjectTypeNode(kbase, A.class );
        InternalWorkingMemory wm = ((StatefulKnowledgeSessionImpl)kbase.newKieSession());

        LeftInputAdapterNode liaNodeA = (LeftInputAdapterNode) node.getObjectSinkPropagator().getSinks()[0];
        ExistsNode existsNode2 = ( ExistsNode) liaNodeA.getSinkPropagator().getSinks()[1];
        ExistsNode existsNode3 = ( ExistsNode) liaNodeA.getSinkPropagator().getSinks()[2];
        JoinNode joinNodeB = ( JoinNode) liaNodeA.getSinkPropagator().getSinks()[0];
        JoinNode joinNodeC = ( JoinNode) joinNodeB.getSinkPropagator().getSinks()[0];

        JoinNode joinNodeD1 = ( JoinNode) joinNodeC.getSinkPropagator().getSinks()[0];
        JoinNode joinNodeD2 = ( JoinNode) existsNode2.getSinkPropagator().getSinks()[0];

        JoinNode joinNodeE = ( JoinNode) existsNode3.getSinkPropagator().getSinks()[0];

        RuleTerminalNode rtn1 = ( RuleTerminalNode ) joinNodeD1.getSinkPropagator().getSinks()[0];
        RuleTerminalNode rtn2 = ( RuleTerminalNode ) joinNodeD2.getSinkPropagator().getSinks()[0];
        RuleTerminalNode rtn3 = ( RuleTerminalNode ) joinNodeE.getSinkPropagator().getSinks()[0];

        FactHandle fha = wm.insert( new A() );
        wm.insert( new B() );
        wm.insert( new C() );
        wm.insert( new X() );
        wm.flushPropagations();

        BetaMemory bm = null;

        LiaNodeMemory liam = wm.getNodeMemory(liaNodeA);

        BetaMemory bm1 = (BetaMemory) wm.getNodeMemory(joinNodeB);
        BetaMemory bm2 = (BetaMemory) wm.getNodeMemory(joinNodeC);
        BetaMemory bm3 = (BetaMemory) wm.getNodeMemory(joinNodeD1);
        assertThat(liam.getNodePosMaskBit()).isEqualTo(1);
        assertThat(bm1.getNodePosMaskBit()).isEqualTo(1);
        assertThat(bm2.getNodePosMaskBit()).isEqualTo(2);
        assertThat(bm3.getNodePosMaskBit()).isEqualTo(1);

        assertThat(bm1.getSegmentMemory()).isNotSameAs(liam.getSegmentMemory());
        assertThat(bm2.getSegmentMemory()).isSameAs(bm1.getSegmentMemory());
        assertThat(bm3.getSegmentMemory()).isNotSameAs(bm2.getSegmentMemory());

        BetaMemory bm4 = (BetaMemory) wm.getNodeMemory(existsNode2);
        BetaMemory bm5 = (BetaMemory) wm.getNodeMemory(joinNodeD2);
        assertThat(bm4.getNodePosMaskBit()).isEqualTo(1);
        assertThat(bm5.getNodePosMaskBit()).isEqualTo(2);
        assertThat(bm5.getSegmentMemory()).isSameAs(bm4.getSegmentMemory());

        PathMemory rs1 = wm.getNodeMemory(rtn1);
        PathMemory rs2 = wm.getNodeMemory(rtn2);
        PathMemory rs3 = wm.getNodeMemory(rtn3);

        assertThat(rs1.isRuleLinked()).isTrue();
        assertThat(rs2.isRuleLinked()).isTrue();
        assertThat(rs3.isRuleLinked()).isFalse(); // no E yet

        wm.insert( new E() );
        wm.flushPropagations();

        BetaMemory bm6 = (BetaMemory) wm.getNodeMemory(existsNode3);
        BetaMemory bm7 = (BetaMemory) wm.getNodeMemory(joinNodeE);
        assertThat(bm6.getNodePosMaskBit()).isEqualTo(1);
        assertThat(bm7.getNodePosMaskBit()).isEqualTo(2);
        assertThat(bm7.getSegmentMemory()).isSameAs(bm6.getSegmentMemory());

        assertThat(rs1.isRuleLinked()).isTrue();
        assertThat(rs2.isRuleLinked()).isTrue();
        assertThat(rs3.isRuleLinked()).isTrue();

        wm.retract( fha );
        wm.fireAllRules(); // need to have rules evalulated, for unlinking to happen
        assertThat(rs1.isRuleLinked()).isFalse();
        assertThat(rs2.isRuleLinked()).isFalse();
        assertThat(rs3.isRuleLinked()).isFalse();
    }

    @Test
    public void testSubNetworkRiaLinking() throws Exception {
        String str = "";
        str += "package org.kie \n";
        str += "import " + A.class.getCanonicalName() + "\n" ;
        str += "import " + B.class.getCanonicalName() + "\n" ;
        str += "import " + C.class.getCanonicalName() + "\n" ;
        str += "import " + X.class.getCanonicalName() + "\n" ;
        str += "import " + E.class.getCanonicalName() + "\n" ;
        str += "global java.util.List list \n";

        str += "rule rule1 when \n";
        str += "   A() \n";
        str += "   B() \n";
        str += "   exists( C() and X() ) \n";
        str += "   E() \n";
        str += "then \n";
        str += "end \n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);

        ObjectTypeNode node = getObjectTypeNode(kbase, A.class );
        InternalWorkingMemory wm = ((StatefulKnowledgeSessionImpl)kbase.newKieSession());

        LeftInputAdapterNode liaNode = (LeftInputAdapterNode) node.getObjectSinkPropagator().getSinks()[0];
        assertThat(liaNode.getSinkPropagator().size()).isEqualTo(1);

        JoinNode bNode = ( JoinNode) liaNode.getSinkPropagator().getSinks()[0];
        assertThat(bNode.getSinkPropagator().size()).isEqualTo(2);

        ExistsNode exists1n = ( ExistsNode) bNode.getSinkPropagator().getSinks()[1];

        JoinNode cNode = ( JoinNode) bNode.getSinkPropagator().getSinks()[0];
        JoinNode dNode = ( JoinNode) cNode.getSinkPropagator().getSinks()[0];
        assertThat(dNode.getSinkPropagator().size()).isEqualTo(1);

        TupleToObjectNode riaNode1 =  (TupleToObjectNode) dNode.getSinkPropagator().getSinks()[0];


        JoinNode eNode = ( JoinNode ) exists1n.getSinkPropagator().getSinks()[0];
        RuleTerminalNode rtn = ( RuleTerminalNode ) eNode.getSinkPropagator().getSinks()[0];

        RuntimeSegmentUtilities.getOrCreateSegmentMemory(exists1n, wm);
        BetaMemory existsBm = (BetaMemory) wm.getNodeMemory(exists1n);

        assertThat(existsBm.getSegmentMemory().getLinkedNodeMask()).isEqualTo(0);

        FactHandle fhc = wm.insert(  new C() );
        FactHandle fhd = wm.insert(  new X() );
        wm.flushPropagations();

        assertThat(existsBm.getSegmentMemory().getLinkedNodeMask()).isEqualTo(1);  // exists is start of new segment

        wm.retract( fhd );
        wm.flushPropagations();
        assertThat(existsBm.getSegmentMemory().getLinkedNodeMask()).isEqualTo(0);

        PathMemory rs = wm.getNodeMemory(rtn);
        assertThat(rs.isRuleLinked()).isFalse();

        wm.insert(  new A() );
        wm.flushPropagations();
        assertThat(rs.isRuleLinked()).isFalse();
        wm.insert(new B());
        wm.flushPropagations();
        assertThat(rs.isRuleLinked()).isFalse();
        wm.insert(new E());
        wm.flushPropagations();
        assertThat(rs.isRuleLinked()).isFalse();

        wm.insert(  new X() );
        wm.flushPropagations();
        assertThat(rs.isRuleLinked()).isTrue();

        wm.retract(  fhc );
        wm.flushPropagations();
        assertThat(rs.isRuleLinked()).isFalse();

        wm.insert(  new C() );
        wm.flushPropagations();
        assertThat(rs.isRuleLinked()).isTrue();
    }

    @Test
    public void testNonReactiveSubNetworkInShareMasks() throws Exception {
        String str = "";
        str += "package org.kie \n";
        str += "import " + A.class.getCanonicalName() + "\n" ;
        str += "import " + B.class.getCanonicalName() + "\n" ;
        str += "import " + C.class.getCanonicalName() + "\n" ;
        str += "import " + X.class.getCanonicalName() + "\n" ;
        str += "import " + E.class.getCanonicalName() + "\n" ;
        str += "import " + F.class.getCanonicalName() + "\n" ;
        str += "import " + G.class.getCanonicalName() + "\n" ;
        str += "global java.util.List list \n";

        str += "rule rule1 when \n";
        str += "   A() \n";
        str += "   exists( B() and C() ) \n";
        str += "   exists( eval(1==1) ) \n";
        str += "   X() \n";
        str += "then \n";
        str += "end \n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);

        ObjectTypeNode node = getObjectTypeNode(kbase, A.class );
        InternalWorkingMemory wm = ((StatefulKnowledgeSessionImpl)kbase.newKieSession());

        LeftInputAdapterNode liaNode = (LeftInputAdapterNode) node.getObjectSinkPropagator().getSinks()[0];
        assertThat(liaNode.getSinkPropagator().size()).isEqualTo(2);

        JoinNode bNode = ( JoinNode) liaNode.getSinkPropagator().getSinks()[0];
        JoinNode cNode = ( JoinNode) bNode.getSinkPropagator().getSinks()[0];
        ExistsNode exists1n = ( ExistsNode) liaNode.getSinkPropagator().getSinks()[1];
        EvalConditionNode evalNode = ( EvalConditionNode) exists1n.getSinkPropagator().getSinks()[0];
        ExistsNode exists2n = ( ExistsNode) exists1n.getSinkPropagator().getSinks()[1];
        JoinNode dNode = ( JoinNode) exists2n.getSinkPropagator().getSinks()[0];
        RuleTerminalNode rtn = ( RuleTerminalNode) dNode.getSinkPropagator().getSinks()[0];

        wm.insert(  new A() );

        PathMemory pmem = wm.getNodeMemory(rtn);
        assertThat(pmem.getSegmentMemories().length).isEqualTo(3);
        assertThat(pmem.getAllLinkedMaskTest()).isEqualTo(7); // D is in the exists segment


        BetaMemory bm =  (BetaMemory) wm.getNodeMemory(dNode);
        assertThat(bm.getSegmentMemory()).isNull(); // check lazy initialization
        wm.insert(new X());
        wm.flushPropagations();
        assertThat(bm.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(2); // only X can be linked in
    }

    @Test
    public void testNonReactiveSubNetworkOwnSegmentMasks() throws Exception {
        String str = "";
        str += "package org.kie \n";
        str += "import " + A.class.getCanonicalName() + "\n" ;
        str += "import " + B.class.getCanonicalName() + "\n" ;
        str += "import " + C.class.getCanonicalName() + "\n" ;
        str += "import " + X.class.getCanonicalName() + "\n" ;
        str += "import " + E.class.getCanonicalName() + "\n" ;
        str += "import " + F.class.getCanonicalName() + "\n" ;
        str += "import " + G.class.getCanonicalName() + "\n" ;
        str += "global java.util.List list \n";

        str += "rule rule1 when \n";
        str += "   A() \n";
        str += "   exists( B() and C() ) \n";
        str += "   exists( eval(1==1) ) \n";
        str += "   X() \n";
        str += "then \n";
        str += "end \n";
        str += "rule rule2 when \n";
        str += "   A() \n";
        str += "   exists( B() and C() ) \n";
        str += "   exists( eval(1==1) ) \n";
        str += "   E() \n";
        str += "then \n";
        str += "end \n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);

        ObjectTypeNode node = getObjectTypeNode(kbase, A.class );
        InternalWorkingMemory wm = ((StatefulKnowledgeSessionImpl)kbase.newKieSession());

        LeftInputAdapterNode liaNode = (LeftInputAdapterNode) node.getObjectSinkPropagator().getSinks()[0];
        assertThat(liaNode.getSinkPropagator().size()).isEqualTo(2);

        JoinNode bNode = ( JoinNode) liaNode.getSinkPropagator().getSinks()[0];
        JoinNode cNode = ( JoinNode) bNode.getSinkPropagator().getSinks()[0];
        ExistsNode exists1n = ( ExistsNode) liaNode.getSinkPropagator().getSinks()[1];
        EvalConditionNode evalNode = ( EvalConditionNode) exists1n.getSinkPropagator().getSinks()[0];
        ExistsNode exists2n = ( ExistsNode) exists1n.getSinkPropagator().getSinks()[1];
        JoinNode dNode = ( JoinNode) exists2n.getSinkPropagator().getSinks()[0];
        RuleTerminalNode rtn = ( RuleTerminalNode) dNode.getSinkPropagator().getSinks()[0];

        wm.insert(  new A() );

        PathMemory pmem = wm.getNodeMemory(rtn);
        assertThat(pmem.getSegmentMemories().length).isEqualTo(4);
        assertThat(pmem.getAllLinkedMaskTest()).isEqualTo(11); // the exists eval segment does not need to be linked in

        SubnetworkPathMemory riaMem =  (SubnetworkPathMemory) wm.getNodeMemory((MemoryFactory) exists1n.getRightInput().getParent());
        assertThat(riaMem.getAllLinkedMaskTest()).isEqualTo(2); // second segment must be linked in

        wm.insert(  new B() );
        wm.insert(  new C() );
        assertThat(riaMem.getSegmentMemories().length).isEqualTo(2);

        riaMem =  (SubnetworkPathMemory) wm.getNodeMemory((MemoryFactory) exists2n.getRightInput().getParent());
        assertThat(riaMem.getAllLinkedMaskTest()).isEqualTo(0); // no segments to be linked in
    }

    @Test
    public void testNestedSubNetwork() throws Exception {
        String str = "";
        str += "package org.kie \n";
        str += "import " + A.class.getCanonicalName() + "\n" ;
        str += "import " + B.class.getCanonicalName() + "\n" ;
        str += "import " + C.class.getCanonicalName() + "\n" ;
        str += "import " + X.class.getCanonicalName() + "\n" ;
        str += "import " + E.class.getCanonicalName() + "\n" ;
        str += "import " + F.class.getCanonicalName() + "\n" ;
        str += "import " + G.class.getCanonicalName() + "\n" ;
        str += "global java.util.List list \n";

        str += "rule rule1 when \n";
        str += "   A() \n";
        str += "   B() \n";
        str += "   exists( C() and X() and exists( E() and F() ) ) \n";
        str += "   G() \n";
        str += "then \n";
        str += "end \n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);

        ObjectTypeNode node = getObjectTypeNode(kbase, A.class );
        InternalWorkingMemory wm = ((StatefulKnowledgeSessionImpl)kbase.newKieSession());

        LeftInputAdapterNode liaNode = (LeftInputAdapterNode) node.getObjectSinkPropagator().getSinks()[0];
        assertThat(liaNode.getSinkPropagator().size()).isEqualTo(1);

        JoinNode bNode = ( JoinNode) liaNode.getSinkPropagator().getSinks()[0];
        assertThat(bNode.getSinkPropagator().size()).isEqualTo(2);

        ExistsNode exists1n = ( ExistsNode) bNode.getSinkPropagator().getSinks()[1];

        JoinNode cNode = ( JoinNode) bNode.getSinkPropagator().getSinks()[0];
        JoinNode dNode = ( JoinNode) cNode.getSinkPropagator().getSinks()[0];
        assertThat(dNode.getSinkPropagator().size()).isEqualTo(2);

        ExistsNode exists2n = ( ExistsNode) dNode.getSinkPropagator().getSinks()[1];

        JoinNode eNode = ( JoinNode) dNode.getSinkPropagator().getSinks()[0];
        JoinNode fNode = ( JoinNode) eNode.getSinkPropagator().getSinks()[0];

        TupleToObjectNode riaNode2 =  (TupleToObjectNode) fNode.getSinkPropagator().getSinks()[0];
        assertThat(((RightInputAdapterNode)riaNode2.getObjectSinkPropagator().getSinks()[0]).getBetaNode()).isEqualTo(exists2n);

        TupleToObjectNode riaNode1 =  (TupleToObjectNode) exists2n.getSinkPropagator().getSinks()[0];
        assertThat(((RightInputAdapterNode)riaNode1.getObjectSinkPropagator().getSinks()[0]).getBetaNode()).isEqualTo(exists1n);

        JoinNode gNode = ( JoinNode) exists1n.getSinkPropagator().getSinks()[0];
        RuleTerminalNode rtn = ( RuleTerminalNode) gNode.getSinkPropagator().getSinks()[0];

        wm.insert(  new A() );
        wm.insert(  new B() );
        wm.insert(  new C() );
        wm.insert(  new X() );
        wm.insert(  new F() );
        wm.insert(  new G() );

        PathMemory rs = wm.getNodeMemory(rtn);
        assertThat(rs.isRuleLinked()).isFalse();

        FactHandle fhE1 = wm.insert(  new E() );
        FactHandle fhE2 = wm.insert(  new E() );
        wm.flushPropagations();
        assertThat(rs.isRuleLinked()).isTrue();

        wm.retract( fhE1 );
        wm.flushPropagations();
        assertThat(rs.isRuleLinked()).isTrue();

        wm.retract( fhE2 );
        wm.flushPropagations();
        assertThat(rs.isRuleLinked()).isFalse();
    }

    @Test
    public void testNestedSubNetworkMasks() throws Exception {
        String str = "";
        str += "package org.kie \n";
        str += "import " + A.class.getCanonicalName() + "\n" ;
        str += "import " + B.class.getCanonicalName() + "\n" ;
        str += "import " + C.class.getCanonicalName() + "\n" ;
        str += "import " + X.class.getCanonicalName() + "\n" ;
        str += "import " + E.class.getCanonicalName() + "\n" ;
        str += "import " + F.class.getCanonicalName() + "\n" ;
        str += "import " + G.class.getCanonicalName() + "\n" ;
        str += "global java.util.List list \n";

        str += "rule rule1 when \n";
        str += "   A() \n";
        str += "   B() \n";
        str += "   exists( C() and X() and exists( E() and F() ) ) \n";
        str += "   G() \n";
        str += "then \n";
        str += "end \n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);

        ObjectTypeNode node = getObjectTypeNode(kbase, A.class );
        InternalWorkingMemory wm = ((StatefulKnowledgeSessionImpl)kbase.newKieSession());

        LeftInputAdapterNode liaNode = (LeftInputAdapterNode) node.getObjectSinkPropagator().getSinks()[0];
        JoinNode bNode = ( JoinNode) liaNode.getSinkPropagator().getSinks()[0];

        ExistsNode exists1n = ( ExistsNode) bNode.getSinkPropagator().getSinks()[1];

        JoinNode cNode = ( JoinNode) bNode.getSinkPropagator().getSinks()[0];
        JoinNode dNode = ( JoinNode) cNode.getSinkPropagator().getSinks()[0];

        ExistsNode exists2n = ( ExistsNode) dNode.getSinkPropagator().getSinks()[1];

        JoinNode eNode = ( JoinNode) dNode.getSinkPropagator().getSinks()[0];
        JoinNode fNode = ( JoinNode) eNode.getSinkPropagator().getSinks()[0];

        TupleToObjectNode riaNode2 =  (TupleToObjectNode) fNode.getSinkPropagator().getSinks()[0];
        TupleToObjectNode riaNode1 =  (TupleToObjectNode) exists2n.getSinkPropagator().getSinks()[0];

        JoinNode gNode = ( JoinNode) exists1n.getSinkPropagator().getSinks()[0];
        RuleTerminalNode rtn = ( RuleTerminalNode) gNode.getSinkPropagator().getSinks()[0];

        wm.insert(  new A() );
        wm.insert(  new B() );
        wm.insert(  new C() );
        wm.insert(  new X() );
        wm.insert(  new G() );
        wm.flushPropagations();

        LiaNodeMemory  liaMem     = wm.getNodeMemory(liaNode);
        BetaMemory bMem       = (BetaMemory)   wm.getNodeMemory(bNode);
        BetaMemory exists1Mem = (BetaMemory) wm.getNodeMemory(exists1n);
        BetaMemory cMem       = (BetaMemory)   wm.getNodeMemory(cNode);
        BetaMemory dMem       = (BetaMemory)   wm.getNodeMemory(dNode);
        BetaMemory exists2Mem = (BetaMemory) wm.getNodeMemory(exists2n);
        BetaMemory eMem       = (BetaMemory)   wm.getNodeMemory(eNode);
        BetaMemory fMem       = (BetaMemory)   wm.getNodeMemory(fNode);
        BetaMemory gMem       = (BetaMemory)   wm.getNodeMemory(gNode);

        SubnetworkPathMemory riaMem1 = (SubnetworkPathMemory) wm.getNodeMemory(riaNode1);
        SubnetworkPathMemory riaMem2 = (SubnetworkPathMemory) wm.getNodeMemory(riaNode2);

        PathMemory rs = wm.getNodeMemory(rtn);

        assertThat(rs.isRuleLinked()).isFalse(); //E and F are not inserted yet, so rule is unlinked

        //---
        // assert a and b in same segment
        assertThat(bMem.getSegmentMemory()).isSameAs(liaMem.getSegmentMemory());

        // exists1 and b not in same segment
        assertThat(exists1Mem.getSegmentMemory()).isNotSameAs(bMem.getSegmentMemory());

        // exists1 and b are in same segment
        assertThat(gMem.getSegmentMemory()).isSameAs(exists1Mem.getSegmentMemory());

        // check segment masks
        assertThat(rs.getSegmentMemories().length).isEqualTo(2);
        assertThat(rs.getAllLinkedMaskTest()).isEqualTo(3);
        assertThat(rs.getLinkedSegmentMask()).isEqualTo(1);

        assertThat(liaMem.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(3);
        assertThat(liaMem.getNodePosMaskBit()).isEqualTo(1);
        assertThat(bMem.getNodePosMaskBit()).isEqualTo(2);

        assertThat(exists1Mem.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(3);
        assertThat(exists1Mem.getNodePosMaskBit()).isEqualTo(1);
        assertThat(gMem.getNodePosMaskBit()).isEqualTo(2);


        // assert c, d are in the same segment, and that this is the only segment in ria1 memory
        assertThat(cMem.getSegmentMemory()).isSameAs(dMem.getSegmentMemory());

        // assert d and exists are not in the same segment
        assertThat(dMem.getSegmentMemory()).isNotSameAs(exists2Mem.getSegmentMemory());
        assertThat(riaMem1.getSegmentMemories().length).isEqualTo(3);
        assertThat(riaMem1.getSegmentMemories()[0]).isEqualTo(null); // only needs to know about segments in the subnetwork
        assertThat(riaMem1.getSegmentMemories()[1]).isEqualTo(dMem.getSegmentMemory());
        assertThat(dMem.getSegmentMemory().getPathMemories().size()).isEqualTo(1);
        assertThat(cMem.getSegmentMemory().getPathMemories().get(0)).isSameAs(riaMem1);

        assertThat(cMem.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(3);
        assertThat(cMem.getSegmentMemory().getLinkedNodeMask()).isEqualTo(3); // E and F is not yet inserted, so bit is not set
        assertThat(cMem.getNodePosMaskBit()).isEqualTo(1);
        assertThat(dMem.getNodePosMaskBit()).isEqualTo(2);

        assertThat(exists2Mem.getNodePosMaskBit()).isEqualTo(0);
        FactHandle fhE1 = wm.insert(  new E() ); // insert to lazy initialize exists2Mem segment
        FactHandle fhF1 = wm.insert(  new F() );
        wm.flushPropagations();

        assertThat(exists2Mem.getNodePosMaskBit()).isEqualTo(1);
        assertThat(riaMem1.getAllLinkedMaskTest()).isEqualTo(6); // only cares that the segment for c, E and exists1 are set, ignores the outer first segment
        assertThat(riaMem1.getLinkedSegmentMask()).isEqualTo(6); // E and F are inerted, so 6
        wm.delete(fhE1);
        wm.delete(fhF1);
        wm.flushPropagations();
        assertThat(riaMem1.getLinkedSegmentMask()).isEqualTo(2); // E deleted

        // assert e, f are in the same segment, and that this is the only segment in ria2 memory
        assertThat(eMem.getSegmentMemory()).isNotNull(); //subnetworks are recursively created, so segment already exists
        assertThat(eMem.getSegmentMemory()).isSameAs(fMem.getSegmentMemory());

        assertThat(riaMem2.getSegmentMemories().length).isEqualTo(3);
        assertThat(riaMem2.getSegmentMemories()[0]).isEqualTo(null); // only needs to know about segments in the subnetwork
        assertThat(riaMem2.getSegmentMemories()[1]).isEqualTo(null); // only needs to know about segments in the subnetwork
        assertThat(riaMem2.getSegmentMemories()[2]).isEqualTo(fMem.getSegmentMemory());
        assertThat(eMem.getSegmentMemory().getPathMemories().get(0)).isSameAs(riaMem2);
        assertThat(eMem.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(3);
        assertThat(eMem.getSegmentMemory().getLinkedNodeMask()).isEqualTo(0);
        assertThat(riaMem2.getAllLinkedMaskTest()).isEqualTo(4); // only cares that the segment for e and f set, ignores the outer two segment
        assertThat(riaMem2.getLinkedSegmentMask()).isEqualTo(0); // E and F is not yet inserted, so bit is not set

        fhE1 = wm.insert(  new E() );
        wm.insert(  new F() );
        wm.flushPropagations();

        assertThat(rs.isRuleLinked()).isTrue(); //E and F are now inserted yet, so rule is linked
        assertThat(rs.getAllLinkedMaskTest()).isEqualTo(3);
        assertThat(rs.getLinkedSegmentMask()).isEqualTo(3);

        // retest bits
        assertThat(cMem.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(3);
        assertThat(cMem.getSegmentMemory().getLinkedNodeMask()).isEqualTo(3);
        assertThat(riaMem1.getAllLinkedMaskTest()).isEqualTo(6);
        assertThat(riaMem1.getLinkedSegmentMask()).isEqualTo(6);

        assertThat(eMem.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(3);
        assertThat(eMem.getSegmentMemory().getLinkedNodeMask()).isEqualTo(3);
        assertThat(riaMem2.getAllLinkedMaskTest()).isEqualTo(4);
        assertThat(riaMem2.getLinkedSegmentMask()).isEqualTo(4);

        wm.delete( fhE1);
        wm.flushPropagations();

        // retest bits
        assertThat(rs.isRuleLinked()).isFalse();

        assertThat(cMem.getSegmentMemory().getLinkedNodeMask()).isEqualTo(3);
        assertThat(riaMem1.getLinkedSegmentMask()).isEqualTo(2);

        assertThat(eMem.getSegmentMemory().getLinkedNodeMask()).isEqualTo(2);
        assertThat(riaMem2.getLinkedSegmentMask()).isEqualTo(0);
    }
    @Test
    public void testJoinNodes() throws Exception {
        String str = "";
        str += "package org.kie \n";
        str += "import " + A.class.getCanonicalName() + "\n" ;
        str += "import " + B.class.getCanonicalName() + "\n" ;
        str += "import " + C.class.getCanonicalName() + "\n" ;
        str += "import " + X.class.getCanonicalName() + "\n" ;
        str += "import " + E.class.getCanonicalName() + "\n" ;
        str += "import " + F.class.getCanonicalName() + "\n" ;
        str += "import " + G.class.getCanonicalName() + "\n" ;
        str += "global java.util.List list \n";

        str += "rule rule1 when \n";
        str += "   $a : A() \n";
        str += "   $b : B() \n";
        str += "   $c : C() \n";
        str += "then \n";
        str += "  list.add( $a.getValue() + \":\"+ $b.getValue() + \":\" + $c.getValue() ); \n";
        str += "end \n";                  

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        
        ObjectTypeNode aotn = getObjectTypeNode(kbase, A.class );
        ObjectTypeNode botn = getObjectTypeNode(kbase, B.class );
        ObjectTypeNode cotn = getObjectTypeNode(kbase, C.class );

        InternalWorkingMemory wm = (InternalWorkingMemory)kbase.newKieSession();
        List list = new ArrayList();
        wm.setGlobal( "list", list );
        
        for ( int i = 0; i < 3; i++ ) {
            wm.insert(  new A(i) );
        }
        
        for ( int i = 0; i < 3; i++ ) {
            wm.insert(  new B(i) );
        }
        
        for ( int i = 0; i < 29; i++ ) {
            wm.insert(  new C(i) );
        }        
        wm.flushPropagations();
        
        LeftInputAdapterNode aNode = (LeftInputAdapterNode) aotn.getObjectSinkPropagator().getSinks()[0];
        JoinNode bNode = ( JoinNode) aNode.getSinkPropagator().getSinks()[0];        
        JoinNode cNode = ( JoinNode) bNode.getSinkPropagator().getSinks()[0];                
        
        LiaNodeMemory  amem = wm.getNodeMemory(aNode);
        BetaMemory bmem = (BetaMemory) wm.getNodeMemory(bNode);
        BetaMemory cmem = (BetaMemory) wm.getNodeMemory(cNode);
        
        // amem.getSegmentMemory().getStagedLeftTuples().insertSize() == 3
        assertThat(amem.getSegmentMemory().getStagedLeftTuples().getInsertFirst()).isNotNull();
        assertThat((Tuple) amem.getSegmentMemory().getStagedLeftTuples().getInsertFirst().getStagedNext()).isNotNull();
        assertThat((Tuple) amem.getSegmentMemory().getStagedLeftTuples().getInsertFirst().getStagedNext().getStagedNext()).isNotNull();
        assertThat((Tuple) amem.getSegmentMemory().getStagedLeftTuples().getInsertFirst().getStagedNext().getStagedNext().getStagedNext()).isNull();

        //assertEquals( 3, bmem.getStagedRightTuples().insertSize() );
        assertThat(bmem.getStagedRightTuples().getInsertFirst()).isNotNull();
        assertThat((Tuple) bmem.getStagedRightTuples().getInsertFirst().getStagedNext()).isNotNull();
        assertThat((Tuple) bmem.getStagedRightTuples().getInsertFirst().getStagedNext().getStagedNext()).isNotNull();
        assertThat((Tuple) bmem.getStagedRightTuples().getInsertFirst().getStagedNext().getStagedNext().getStagedNext()).isNull();

        wm.fireAllRules();

        assertThat(amem.getSegmentMemory().getStagedLeftTuples().getInsertFirst()).isNull();
        assertThat(bmem.getStagedRightTuples().getInsertFirst()).isNull();
        assertThat(cmem.getStagedRightTuples().getInsertFirst()).isNull();

        assertThat(list.size()).isEqualTo(261);

        assertThat(list.contains("2:2:14")).isTrue();
        assertThat(list.contains("1:0:6")).isTrue();
        assertThat(list.contains("0:1:1")).isTrue();
        assertThat(list.contains("2:2:14")).isTrue();
        assertThat(list.contains("0:0:25")).isTrue();    
    }    
    
    @Test
    public void testExistsNodes1() throws Exception {
        String str = "";
        str += "package org.kie \n";
        str += "import " + A.class.getCanonicalName() + "\n" ;
        str += "import " + B.class.getCanonicalName() + "\n" ;
        str += "import " + C.class.getCanonicalName() + "\n" ;
        str += "import " + X.class.getCanonicalName() + "\n" ;
        str += "import " + E.class.getCanonicalName() + "\n" ;
        str += "import " + F.class.getCanonicalName() + "\n" ;
        str += "import " + G.class.getCanonicalName() + "\n" ;
        str += "global java.util.List list \n";

        str += "rule rule1 when \n";
        str += "   exists A() \n";
        str += "then \n";
        str += "  list.add( 'x' ); \n";
        str += "end \n";                  

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);

        KieSession wm = kbase.newKieSession();
        List list = new ArrayList();
        wm.setGlobal( "list", list );
        
        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(0);
        
        wm = kbase.newKieSession();
        list = new ArrayList();
        wm.setGlobal( "list", list );
        
        FactHandle fh = wm.insert( new A(1) );
        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(1);  
        
        wm.retract( fh );
        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(1);        
    }      
    
    @Test
    public void testExistsNodes2() throws Exception {
        String str = "";
        str += "package org.kie \n";
        str += "import " + A.class.getCanonicalName() + "\n" ;
        str += "import " + B.class.getCanonicalName() + "\n" ;
        str += "import " + C.class.getCanonicalName() + "\n" ;
        str += "import " + X.class.getCanonicalName() + "\n" ;
        str += "import " + E.class.getCanonicalName() + "\n" ;
        str += "import " + F.class.getCanonicalName() + "\n" ;
        str += "import " + G.class.getCanonicalName() + "\n" ;
        str += "global java.util.List list \n";

        str += "rule rule1 when \n";
        str += "   $a : A() \n";
        str += "   exists B() \n";
        str += "   $c : C() \n";        
        str += "then \n";
        str += "  list.add( 'x' ); \n";
        str += "end \n";                  

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);


        KieSession wm = kbase.newKieSession();        List list = new ArrayList();
        wm.setGlobal( "list", list );
        
        for ( int i = 0; i < 3; i++ ) {
            wm.insert(  new A(i) );
        }        
        
        for ( int i = 0; i < 3; i++ ) {
            wm.insert(  new C(i) );
        }         
        
        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(0);
        
        wm = kbase.newKieSession();
        list = new ArrayList();
        wm.setGlobal( "list", list );
        
        for ( int i = 0; i < 3; i++ ) {
            wm.insert(  new A(i) );
        }       
        
        FactHandle fh = wm.insert(  new B(1) );
        
        for ( int i = 0; i < 3; i++ ) {
            wm.insert(  new C(i) );
        }      
        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(9);        
        
        wm.retract( fh );
        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(9);            
    }   
    
    @Test
    public void testNotNodeUnlinksWithNoConstriants() {
        String str = "";
        str += "package org.kie \n";
        str += "import " + A.class.getCanonicalName() + "\n" ;
        str += "import " + B.class.getCanonicalName() + "\n" ;
        str += "import " + C.class.getCanonicalName() + "\n" ;
        str += "import " + X.class.getCanonicalName() + "\n" ;
        str += "import " + E.class.getCanonicalName() + "\n" ;
        str += "import " + F.class.getCanonicalName() + "\n" ;
        str += "import " + G.class.getCanonicalName() + "\n" ;
        str += "global java.util.List list \n";

        str += "rule rule1 when \n";
        str += "   $a : A() \n";
        str += "   not B() \n";
        str += "   $c : C() \n";        
        str += "then \n";
        str += "  list.add( 'x' ); \n";
        str += "end \n";                  

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        
        ObjectTypeNode aotn = getObjectTypeNode(kbase, A.class );

        InternalWorkingMemory wm = ((StatefulKnowledgeSessionImpl)kbase.newKieSession());
        List list = new ArrayList();
        wm.setGlobal( "list", list );
        
        LeftInputAdapterNode aNode = (LeftInputAdapterNode) aotn.getObjectSinkPropagator().getSinks()[0];
        NotNode bNode = ( NotNode) aNode.getSinkPropagator().getSinks()[0];        
        JoinNode cNode = ( JoinNode) bNode.getSinkPropagator().getSinks()[0];                
        
        RuntimeSegmentUtilities.getOrCreateSegmentMemory(cNode, wm);
        LiaNodeMemory amem = wm.getNodeMemory(aNode);

        // Only NotNode is linked in
        assertThat(amem.getSegmentMemory().getLinkedNodeMask()).isEqualTo(2);
        
        FactHandle fha = wm.insert(  new A() );
        FactHandle fhb = wm.insert(  new B() );
        FactHandle fhc = wm.insert(  new C() );        
        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(0);

        // NotNode unlinks, which is allowed because it has no variable constraints
        assertThat(amem.getSegmentMemory().getLinkedNodeMask()).isEqualTo(5);        
        
        // NotNode links back in again, which is allowed because it has no variable constraints
        wm.retract( fhb);
        wm.flushPropagations();
        assertThat(amem.getSegmentMemory().getLinkedNodeMask()).isEqualTo(7);
        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(1); 
        
        // Now try with lots of facthandles on NotNode
        
        list.clear();
        List<FactHandle> handles = new ArrayList<FactHandle>();
        for ( int i = 0; i < 5; i++ ) {
            handles.add(  wm.insert(  new B() ) );
        }
        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(0);

        assertThat(amem.getSegmentMemory().getLinkedNodeMask()).isEqualTo(5);
        for ( FactHandle fh : handles ) {
            wm.retract( fh );
        }
        wm.flushPropagations();

        assertThat(amem.getSegmentMemory().getLinkedNodeMask()).isEqualTo(7);
        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(1);        
    }
    
    @Test
    public void testNotNodeDoesNotUnlinksWithConstriants() {
        String str = "";
        str += "package org.kie \n";
        str += "import " + A.class.getCanonicalName() + "\n" ;
        str += "import " + B.class.getCanonicalName() + "\n" ;
        str += "import " + C.class.getCanonicalName() + "\n" ;
        str += "import " + X.class.getCanonicalName() + "\n" ;
        str += "import " + E.class.getCanonicalName() + "\n" ;
        str += "import " + F.class.getCanonicalName() + "\n" ;
        str += "import " + G.class.getCanonicalName() + "\n" ;
        str += "global java.util.List list \n";

        str += "rule rule1 when \n";
        str += "   $a : A() \n";
        str += "   not B( value == $a.value ) \n";
        str += "   $c : C() \n";        
        str += "then \n";
        str += "  list.add( 'x' ); \n";
        str += "end \n";                  

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        
        ObjectTypeNode aotn = getObjectTypeNode(kbase, A.class );

        InternalWorkingMemory wm = ((StatefulKnowledgeSessionImpl)kbase.newKieSession());
        List list = new ArrayList();
        wm.setGlobal( "list", list );
        
        LeftInputAdapterNode aNode = (LeftInputAdapterNode) aotn.getObjectSinkPropagator().getSinks()[0];
        NotNode bNode = ( NotNode) aNode.getSinkPropagator().getSinks()[0];        
        JoinNode cNode = ( JoinNode) bNode.getSinkPropagator().getSinks()[0];                
        
        RuntimeSegmentUtilities.getOrCreateSegmentMemory(cNode, wm);
        LiaNodeMemory amem = wm.getNodeMemory(aNode);

        // Only NotNode is linked in
        assertThat(amem.getSegmentMemory().getLinkedNodeMask()).isEqualTo(2);
        
        FactHandle fha = wm.insert(  new A() );
        FactHandle fhb = wm.insert(  new B(1) );
        FactHandle fhc = wm.insert(  new C() );
        wm.flushPropagations();

        // All nodes are linked in
        assertThat(amem.getSegmentMemory().getLinkedNodeMask()).isEqualTo(7);
        
        // NotNode does not unlink, due to variable constraint
        wm.retract( fhb);
        wm.flushPropagations();
        assertThat(amem.getSegmentMemory().getLinkedNodeMask()).isEqualTo(7);
    }    
    
    @Test
    public void testNotNodes1() throws Exception {
        String str = "";
        str += "package org.kie \n";
        str += "import " + A.class.getCanonicalName() + "\n" ;
        str += "import " + B.class.getCanonicalName() + "\n" ;
        str += "import " + C.class.getCanonicalName() + "\n" ;
        str += "import " + X.class.getCanonicalName() + "\n" ;
        str += "import " + E.class.getCanonicalName() + "\n" ;
        str += "import " + F.class.getCanonicalName() + "\n" ;
        str += "import " + G.class.getCanonicalName() + "\n" ;
        str += "global java.util.List list \n";

        str += "rule rule1 when \n";
        str += "   not A() \n";
        str += "then \n";
        str += "  list.add( 'x' ); \n";
        str += "end \n";                  

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);

        InternalWorkingMemory wm = ((StatefulKnowledgeSessionImpl)kbase.newKieSession());
        List list = new ArrayList();
        wm.setGlobal( "list", list );
        
        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(1);
        
        wm = ((StatefulKnowledgeSessionImpl)kbase.newKieSession());
        list = new ArrayList();
        wm.setGlobal( "list", list );
        
        FactHandle fh = wm.insert( new A(1) );
        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(0);  
        
        wm.retract( fh );
        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(1);        
    }  
    
    
    @Test
    public void testNotNodes2() throws Exception {
        String str = "";
        str += "package org.kie \n";
        str += "import " + A.class.getCanonicalName() + "\n" ;
        str += "import " + B.class.getCanonicalName() + "\n" ;
        str += "import " + C.class.getCanonicalName() + "\n" ;
        str += "import " + X.class.getCanonicalName() + "\n" ;
        str += "import " + E.class.getCanonicalName() + "\n" ;
        str += "import " + F.class.getCanonicalName() + "\n" ;
        str += "import " + G.class.getCanonicalName() + "\n" ;
        str += "global java.util.List list \n";

        str += "rule rule1 when \n";
        str += "   $a : A() \n";
        str += "   not B() \n";
        str += "   $c : C() \n";        
        str += "then \n";
        str += "  list.add( 'x' ); \n";
        str += "end \n";                  

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        
        ObjectTypeNode aotn = getObjectTypeNode(kbase, A.class );
        ObjectTypeNode botn = getObjectTypeNode(kbase, A.class );
        ObjectTypeNode cotn = getObjectTypeNode(kbase, A.class );

        InternalWorkingMemory wm = ((StatefulKnowledgeSessionImpl)kbase.newKieSession());
        List list = new ArrayList();
        wm.setGlobal( "list", list );
        
        for ( int i = 0; i < 3; i++ ) {
            wm.insert(  new A(i) );
        }        
        
        for ( int i = 0; i < 3; i++ ) {
            wm.insert(  new C(i) );
        }         
        
        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(9);
        
        wm = ((StatefulKnowledgeSessionImpl)kbase.newKieSession());
        list = new ArrayList();
        wm.setGlobal( "list", list );
        
        for ( int i = 0; i < 3; i++ ) {
            wm.insert(  new A(i) );
        }       
        
        FactHandle fh = wm.insert(  new B(1) );
        
        for ( int i = 0; i < 3; i++ ) {
            wm.insert(  new C(i) );
        }      
        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(0);        
        
        wm.retract( fh );
        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(9);            
    }

    @Test
    public void testNotNodeMasksWithConstraints() throws Exception {
        String str = "";
        str += "package org.kie \n";
        str += "import " + A.class.getCanonicalName() + "\n" ;
        str += "import " + B.class.getCanonicalName() + "\n" ;
        str += "import " + C.class.getCanonicalName() + "\n" ;
        str += "import " + X.class.getCanonicalName() + "\n" ;
        str += "import " + E.class.getCanonicalName() + "\n" ;
        str += "import " + F.class.getCanonicalName() + "\n" ;
        str += "import " + G.class.getCanonicalName() + "\n" ;
        str += "global java.util.List list \n";

        str += "rule rule1 when \n";
        str += "   $a : A() \n";
        str += "   not( B( value == $a.value ) ) \n";
        str += "   C() \n";
        str += "then \n";
        str += "end \n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);

        ObjectTypeNode node = getObjectTypeNode(kbase, A.class );
        InternalWorkingMemory wm = ((StatefulKnowledgeSessionImpl)kbase.newKieSession());

        LeftInputAdapterNode liaNode = (LeftInputAdapterNode) node.getObjectSinkPropagator().getSinks()[0];
        assertThat(liaNode.getSinkPropagator().size()).isEqualTo(1);

        wm.insert( new A() );
        wm.flushPropagations();

        NotNode notNode = ( NotNode) liaNode.getSinkPropagator().getSinks()[0];
        JoinNode cNode = ( JoinNode) notNode.getSinkPropagator().getSinks()[0];
        RuleTerminalNode rtn = ( RuleTerminalNode ) cNode.getSinkPropagator().getSinks()[0];

        PathMemory pmem = wm.getNodeMemory(rtn);
        assertThat(pmem.getSegmentMemories().length).isEqualTo(1);
        assertThat(pmem.getAllLinkedMaskTest()).isEqualTo(1);

        SegmentMemory sm = pmem.getSegmentMemories()[0];
        assertThat(sm.getAllLinkedMaskTest()).isEqualTo(5);

        assertThat(sm.getLinkedNodeMask()).isEqualTo(3);
        assertThat(sm.isSegmentLinked()).isFalse();
        assertThat(pmem.isRuleLinked()).isFalse();

        wm.insert( new C());
        wm.flushPropagations();
        assertThat(sm.getLinkedNodeMask()).isEqualTo(7);  // only 5 is needed to link, the 'not' turns on but it has no unfleunce either way
        assertThat(sm.isSegmentLinked()).isTrue();
        assertThat(pmem.isRuleLinked()).isTrue();
    }

    @Test
    public void testNotNodeMasksWithoutConstraints() throws Exception {
        String str = "";
        str += "package org.kie \n";
        str += "import " + A.class.getCanonicalName() + "\n" ;
        str += "import " + B.class.getCanonicalName() + "\n" ;
        str += "import " + C.class.getCanonicalName() + "\n" ;
        str += "import " + X.class.getCanonicalName() + "\n" ;
        str += "import " + E.class.getCanonicalName() + "\n" ;
        str += "import " + F.class.getCanonicalName() + "\n" ;
        str += "import " + G.class.getCanonicalName() + "\n" ;
        str += "global java.util.List list \n";

        str += "rule rule1 when \n";
        str += "   A() \n";
        str += "   not( B( ) ) \n";
        str += "   C() \n";
        str += "then \n";
        str += "end \n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);

        ObjectTypeNode node = getObjectTypeNode(kbase, A.class );
        InternalWorkingMemory wm = ((StatefulKnowledgeSessionImpl)kbase.newKieSession());

        LeftInputAdapterNode liaNode = (LeftInputAdapterNode) node.getObjectSinkPropagator().getSinks()[0];
        assertThat(liaNode.getSinkPropagator().size()).isEqualTo(1);

        wm.insert( new A() );
        wm.flushPropagations();

        NotNode notNode = ( NotNode) liaNode.getSinkPropagator().getSinks()[0];
        JoinNode cNode = ( JoinNode) notNode.getSinkPropagator().getSinks()[0];
        RuleTerminalNode rtn = ( RuleTerminalNode ) cNode.getSinkPropagator().getSinks()[0];

        PathMemory pmem = wm.getNodeMemory(rtn);
        assertThat(pmem.getSegmentMemories().length).isEqualTo(1);
        assertThat(pmem.getAllLinkedMaskTest()).isEqualTo(1);

        SegmentMemory sm = pmem.getSegmentMemories()[0];
        assertThat(sm.getAllLinkedMaskTest()).isEqualTo(7);

        assertThat(sm.getLinkedNodeMask()).isEqualTo(3);
        assertThat(sm.isSegmentLinked()).isFalse();
        assertThat(pmem.isRuleLinked()).isFalse();

        wm.insert( new C() );
        wm.flushPropagations();
        assertThat(sm.getLinkedNodeMask()).isEqualTo(7);
        assertThat(sm.isSegmentLinked()).isTrue();
        assertThat(pmem.isRuleLinked()).isTrue();
    }
    
    @Test
    public void testForallNodes() throws Exception {
        String str = "";
        str += "package org.kie \n";
        str += "import " + A.class.getCanonicalName() + "\n" ;
        str += "import " + B.class.getCanonicalName() + "\n" ;
        str += "import " + C.class.getCanonicalName() + "\n" ;
        str += "import " + X.class.getCanonicalName() + "\n" ;
        str += "import " + E.class.getCanonicalName() + "\n" ;
        str += "import " + F.class.getCanonicalName() + "\n" ;
        str += "import " + G.class.getCanonicalName() + "\n" ;
        str += "global java.util.List list \n";

        str += "rule rule1 when \n";
        str += "   $a : A() \n";
        str += "   forall( B() )\n";
        str += "   $c : C() \n";        
        str += "then \n";
        str += "  list.add( 'x' ); \n";
        str += "end \n";

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        
        ObjectTypeNode aotn = getObjectTypeNode(kbase, A.class );
        ObjectTypeNode botn = getObjectTypeNode(kbase, A.class );
        ObjectTypeNode cotn = getObjectTypeNode(kbase, A.class );

        KieSession wm = kbase.newKieSession();
        List list = new ArrayList();
//        wm.setGlobal( "list", list );
//        
//        for ( int i = 0; i < 3; i++ ) {
//            wm.insert(  new A(i) );
//        }        
//        
//        wm.insert(  new B(2) );
//        
//        for ( int i = 0; i < 3; i++ ) {
//            wm.insert(  new C(i) );
//        }         
//        
//        wm.fireAllRules();        
//        assertEquals( 0, list.size() );
        
        wm = kbase.newKieSession();
        list = new ArrayList();
        wm.setGlobal( "list", list );
        
        for ( int i = 0; i < 2; i++ ) {
            wm.insert(  new A(i) );
        }       
                
        for ( int i = 0; i < 27; i++ ) {
            wm.insert(  new B(1) );
        }                
        
        for ( int i = 0; i < 2; i++ ) {
            wm.insert(  new C(i) );
        }      
        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(4);        
        
//        wm.retract( fh );
//        wm.fireAllRules();        
//        assertEquals( 9, list.size() );            
    }      
    
    @Test
    public void testAccumulateNodes1() throws Exception {
        String str = "";
        str += "package org.kie \n";
        str += "import " + A.class.getCanonicalName() + "\n" ;
        str += "import " + B.class.getCanonicalName() + "\n" ;
        str += "import " + C.class.getCanonicalName() + "\n" ;
        str += "import " + X.class.getCanonicalName() + "\n" ;
        str += "import " + E.class.getCanonicalName() + "\n" ;
        str += "import " + F.class.getCanonicalName() + "\n" ;
        str += "import " + G.class.getCanonicalName() + "\n" ;
        str += "global java.util.List list \n";

        str += "rule rule1 when \n";
        str += "   accumulate( $a : A(); $l : collectList( $a ) ) \n";
        str += "then \n";
        str += "  list.add( $l.size() ); \n";
        str += "end \n";                  

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);

        KieSession wm = kbase.newKieSession();
        List list = new ArrayList();
        wm.setGlobal( "list", list );
        
        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(1);
        
        wm = kbase.newKieSession();
        list = new ArrayList();
        wm.setGlobal( "list", list );
        
        FactHandle fh1 = wm.insert( new A(1) );
        FactHandle fh2 = wm.insert( new A(2) );
        FactHandle fh3 = wm.insert( new A(3) );
        FactHandle fh4 = wm.insert( new A(4) );
        wm.fireAllRules();
        assertThat(list.get(0)).isEqualTo(4);        
    }       
    
    @Test
    public void testAccumulateNodes2() throws Exception {
        String str = "";
        str += "package org.kie \n";
        str += "import " + A.class.getCanonicalName() + "\n" ;
        str += "import " + B.class.getCanonicalName() + "\n" ;
        str += "import " + C.class.getCanonicalName() + "\n" ;
        str += "import " + X.class.getCanonicalName() + "\n" ;
        str += "import " + E.class.getCanonicalName() + "\n" ;
        str += "import " + F.class.getCanonicalName() + "\n" ;
        str += "import " + G.class.getCanonicalName() + "\n" ;
        str += "global java.util.List list \n";

        str += "rule rule1 when \n";
        str += "   A() \n";
        str += "   accumulate( $a : B(); $l : collectList( $a ) ) \n";
        str += "   C() \n";        
        str += "then \n";
        str += "  list.add( $l.size() ); \n";
        str += "end \n";                  

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);

        KieSession wm = kbase.newKieSession();
        List list = new ArrayList();
        wm.setGlobal( "list", list );
        
        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(0);
        
        wm = kbase.newKieSession();
        list = new ArrayList();
        wm.setGlobal( "list", list );
        
        FactHandle fh1 = wm.insert( new B(1) );
        FactHandle fh2 = wm.insert( new B(2) );
        FactHandle fh3 = wm.insert( new B(3) );
        FactHandle fh4 = wm.insert( new B(4) );
        
        FactHandle fha = wm.insert( new A(1) );
        FactHandle fhc = wm.insert( new C(1) );
        wm.fireAllRules();
        assertThat(list.get(0)).isEqualTo(4);        
    } 
    
    
    @Test
    public void testSubnetwork() throws Exception {
        String str = "";
        str += "package org.kie \n";
        str += "import " + A.class.getCanonicalName() + "\n" ;
        str += "import " + B.class.getCanonicalName() + "\n" ;
        str += "import " + C.class.getCanonicalName() + "\n" ;
        str += "import " + X.class.getCanonicalName() + "\n" ;
        str += "import " + E.class.getCanonicalName() + "\n" ;
        str += "import " + F.class.getCanonicalName() + "\n" ;
        str += "import " + G.class.getCanonicalName() + "\n" ;
        str += "global java.util.List list \n";

        str += "rule rule1 when \n";
        str += "   $a : A() \n";
        str += "   exists ( B() and C() ) \n";
        str += "   $e : X() \n";        
        str += "then \n";
        str += "  list.add( 'x' ); \n";
        str += "end \n";                  

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        
        ObjectTypeNode aotn = getObjectTypeNode(kbase, A.class );
        ObjectTypeNode botn = getObjectTypeNode(kbase, A.class );
        ObjectTypeNode cotn = getObjectTypeNode(kbase, A.class );

        InternalWorkingMemory wm = ((StatefulKnowledgeSessionImpl)kbase.newKieSession());
        List list = new ArrayList();
        wm.setGlobal( "list", list );
        
        wm.insert( new A() );
        wm.insert( new B() );
        for ( int i = 0; i < 28; i++ ) {
            wm.insert( new C() );
        }
        wm.insert( new X() );
        wm.flushPropagations();

        InternalAgenda agenda = wm.getAgenda();
        InternalAgendaGroup group = agenda.getAgendaGroupsManager().getNextFocus();
        RuleAgendaItem item = group.remove();
        RuleExecutor ruleExecutor = ((RuleAgendaItem)item).getRuleExecutor();
        int count = ruleExecutor.evaluateNetworkAndFire(wm, null, 0, -1);
        //assertEquals(3, count );
        
        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(1);             
        
        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(1); // check it doesn't double fire        
    }
    
    @Test
    public void testNestedSubnetwork() throws Exception {
        String str = "";
        str += "package org.kie \n";
        str += "import " + A.class.getCanonicalName() + "\n" ;
        str += "import " + B.class.getCanonicalName() + "\n" ;
        str += "import " + C.class.getCanonicalName() + "\n" ;
        str += "import " + X.class.getCanonicalName() + "\n" ;
        str += "import " + E.class.getCanonicalName() + "\n" ;
        str += "import " + F.class.getCanonicalName() + "\n" ;
        str += "import " + G.class.getCanonicalName() + "\n" ;
        str += "global java.util.List list \n";

        str += "rule rule1 when \n";
        str += "   $a : A() \n";
        str += "   exists ( B() and exists( C() and X() ) and E() ) \n";
        str += "   $f : F() \n";        
        str += "then \n";
        str += "  list.add( 'x' ); \n";
        str += "end \n";                  

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        
        ObjectTypeNode aotn = getObjectTypeNode(kbase, A.class );
        ObjectTypeNode botn = getObjectTypeNode(kbase, A.class );
        ObjectTypeNode cotn = getObjectTypeNode(kbase, A.class );

        InternalWorkingMemory wm = ((StatefulKnowledgeSessionImpl)kbase.newKieSession());
        List list = new ArrayList();
        wm.setGlobal( "list", list );
        
        wm.insert( new A() );
        wm.insert( new B() );
        for ( int i = 0; i < 28; i++ ) {
            wm.insert( new C() );
        }
        for ( int i = 0; i < 29; i++ ) {
            wm.insert( new X() );
        }
        wm.insert( new E() );
        wm.insert( new F() );
        
//        DefaultAgenda agenda = ( DefaultAgenda ) wm.getAgenda();
//        InternalAgendaGroup group = (InternalAgendaGroup) agenda.getNextFocus();
//        InternalMatch item = (InternalMatch) group.remove();
//        int count = ((RuleAgendaItem)item).evaluateNetworkAndFire( wm );
//        //assertEquals(7, count ); // proves we correctly track nested sub network staged propagations
//                
//        agenda.addActivation( item, true );
//        agenda = ( DefaultAgenda ) wm.getAgenda();
//        group = (InternalAgendaGroup) agenda.getNextFocus();
//        item = (InternalMatch) group.remove();
//        
//        agenda.fireActivation( item );
//        assertEquals( 1, list.size() );        
//        
//        agenda = ( DefaultAgenda ) wm.getAgenda();
//        group = (InternalAgendaGroup) agenda.getNextFocus();
//        item = (InternalMatch) group.remove();
//        count = ((RuleAgendaItem)item).evaluateNetworkAndFire( wm );
//        //assertEquals(0, count );        
        
        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(1);
        
        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(1);         
    }      
    
    public static ObjectTypeNode getObjectTypeNode(KieBase kbase, Class<?> nodeClass) {
        List<ObjectTypeNode> nodes = ((InternalRuleBase)kbase).getRete().getObjectTypeNodes();
        for ( ObjectTypeNode n : nodes ) {
            if ( ((ClassObjectType)n.getObjectType()).getClassType() == nodeClass ) {
                return n;
            }
        }
        return null;
    }
}
