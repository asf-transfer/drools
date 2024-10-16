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
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.impl.InternalRuleBase;
import org.drools.core.phreak.PhreakBuilder;
import org.drools.core.reteoo.BetaMemory;
import org.drools.core.reteoo.ConditionalBranchNode;
import org.drools.base.reteoo.InitialFactImpl;
import org.drools.core.reteoo.JoinNode;
import org.drools.core.reteoo.JoinRightAdapterNode;
import org.drools.core.reteoo.LeftInputAdapterNode;
import org.drools.core.reteoo.LeftInputAdapterNode.LiaNodeMemory;
import org.drools.core.reteoo.LeftTupleSource;
import org.drools.core.reteoo.NotNode;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.PathMemory;
import org.drools.core.reteoo.TupleToObjectNode;
import org.drools.core.reteoo.RuleTerminalNode;
import org.drools.core.reteoo.SegmentMemory;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.TestParametersUtil;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.kie.api.KieBase;
import org.kie.api.runtime.rule.FactHandle;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class SegmentCreationTest {

    private final KieBaseTestConfiguration kieBaseTestConfiguration;

    public SegmentCreationTest(final KieBaseTestConfiguration kieBaseTestConfiguration) {
        this.kieBaseTestConfiguration = kieBaseTestConfiguration;
    }

    @Parameterized.Parameters(name = "KieBase type={0}")
    public static Collection<Object[]> getParameters() {
        return TestParametersUtil.getKieBaseCloudConfigurations(true);
    }
    
    @Test
    public void testSingleEmptyLhs() throws Exception {
        KieBase kbase = buildKnowledgeBase(" ");

        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());
        
        ObjectTypeNode aotn = getObjectTypeNode(kbase, InitialFactImpl.class );

        LeftInputAdapterNode liaNode = (LeftInputAdapterNode) aotn.getObjectSinkPropagator().getSinks()[0];
        RuleTerminalNode rtn = ( RuleTerminalNode) liaNode.getSinkPropagator().getSinks()[0];  
        

        wm.insert( new LinkingTest.A() );
        wm.flushPropagations();

        // LiaNode and Rule are in same segment
        LiaNodeMemory liaMem = wm.getNodeMemory(liaNode);
        SegmentMemory smem = liaMem.getSegmentMemory();
        assertThat(smem.getRootNode()).isEqualTo(liaNode);
        assertThat(smem.getTipNode()).isEqualTo(rtn);
        assertThat(smem.getNext()).isNull();
        assertThat(smem.getFirst()).isNull();
    }
  
    @Test
    public void testSingleSharedEmptyLhs() throws Exception {
        KieBase kbase = buildKnowledgeBase( " ", " ");

        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());
        
        ObjectTypeNode aotn = getObjectTypeNode(kbase, InitialFactImpl.class );

        LeftInputAdapterNode liaNode = (LeftInputAdapterNode) aotn.getObjectSinkPropagator().getSinks()[0];
        RuleTerminalNode rtn1 = ( RuleTerminalNode) liaNode.getSinkPropagator().getSinks()[0];
        RuleTerminalNode rtn2 = ( RuleTerminalNode) liaNode.getSinkPropagator().getSinks()[1];
        
        wm.insert( new LinkingTest.A() );
        wm.flushPropagations();

        // LiaNode  is in it's own segment
        LiaNodeMemory liaMem = wm.getNodeMemory(liaNode);
        SegmentMemory smem = liaMem.getSegmentMemory();
        assertThat(smem.getRootNode()).isEqualTo(liaNode);
        assertThat(smem.getTipNode()).isEqualTo(liaNode);
        
        // each RTN is in it's own segment
        SegmentMemory rtnSmem1 = smem.getFirst();
        assertThat(rtnSmem1.getRootNode()).isEqualTo(rtn1);
        assertThat(rtnSmem1.getTipNode()).isEqualTo(rtn1);
        
        SegmentMemory rtnSmem2 = rtnSmem1.getNext();
        assertThat(rtnSmem2.getRootNode()).isEqualTo(rtn2);
        assertThat(rtnSmem2.getTipNode()).isEqualTo(rtn2);
    }    
    
    @Test
    public void testSinglePattern() throws Exception {
        KieBase kbase = buildKnowledgeBase("   A() \n");

        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());
        
        ObjectTypeNode aotn = getObjectTypeNode(kbase, LinkingTest.A.class );

        LeftInputAdapterNode liaNode = (LeftInputAdapterNode) aotn.getObjectSinkPropagator().getSinks()[0];
        RuleTerminalNode rtn = ( RuleTerminalNode) liaNode.getSinkPropagator().getSinks()[0];  
        

        wm.insert(new LinkingTest.A());
        wm.flushPropagations();

        // LiaNode and Rule are in same segment
        LiaNodeMemory liaMem = wm.getNodeMemory(liaNode);
        SegmentMemory smem = liaMem.getSegmentMemory();
        assertThat(smem).isNull();
    }
    
    @Test
    public void testSingleSharedPattern() throws Exception {
        KieBase kbase = buildKnowledgeBase( "   A() B()\n",
                                                  "   A() B()\n");

        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());
        
        ObjectTypeNode aotn = getObjectTypeNode(kbase, LinkingTest.A.class );

        LeftInputAdapterNode liaNode = (LeftInputAdapterNode) aotn.getObjectSinkPropagator().getSinks()[0];
        JoinNode beta = ( JoinNode) liaNode.getSinkPropagator().getSinks()[0];
        RuleTerminalNode rtn1 = ( RuleTerminalNode) beta.getSinkPropagator().getSinks()[0];
        RuleTerminalNode rtn2 = ( RuleTerminalNode) beta.getSinkPropagator().getSinks()[1];
        
        wm.insert(new LinkingTest.A());
        wm.insert(new LinkingTest.B());
        wm.fireAllRules();

        // LiaNode  is in it's own segment
        LiaNodeMemory liaMem = wm.getNodeMemory(liaNode);
        SegmentMemory smem = liaMem.getSegmentMemory();
        assertThat(smem.getRootNode()).isEqualTo(liaNode);
        assertThat(smem.getTipNode()).isEqualTo(beta);
        
        // each RTN is in it's own segment
        SegmentMemory rtnSmem1 = smem.getFirst();
        assertThat(rtnSmem1.getRootNode()).isEqualTo(rtn1);
        assertThat(rtnSmem1.getTipNode()).isEqualTo(rtn1);
        
        SegmentMemory rtnSmem2 = rtnSmem1.getNext();
        assertThat(rtnSmem2.getRootNode()).isEqualTo(rtn2);
        assertThat(rtnSmem2.getTipNode()).isEqualTo(rtn2);        
    }     
    
    @Test
    public void testMultiSharedPattern() throws Exception {
        KieBase kbase = buildKnowledgeBase( " X() A() \n",
                                                  "  X()  A() B() \n",
                                                  "  X() A() B() C() \n");

        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());
        
        ObjectTypeNode dotn = getObjectTypeNode(kbase, LinkingTest.X.class );
        ObjectTypeNode aotn = getObjectTypeNode(kbase, LinkingTest.A.class );

        LeftInputAdapterNode lian = (LeftInputAdapterNode) dotn.getObjectSinkPropagator().getSinks()[0];

        JoinNode beta = ((JoinRightAdapterNode) aotn.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        RuleTerminalNode rtn1 = ( RuleTerminalNode) beta.getSinkPropagator().getSinks()[0];
        JoinNode bNode = ( JoinNode ) beta.getSinkPropagator().getSinks()[1];
        RuleTerminalNode rtn2 = ( RuleTerminalNode) bNode.getSinkPropagator().getSinks()[0];
        
        JoinNode cNode = ( JoinNode ) bNode.getSinkPropagator().getSinks()[1];
        RuleTerminalNode rtn3 = ( RuleTerminalNode) cNode.getSinkPropagator().getSinks()[0];        
                
        wm.insert( new LinkingTest.X() );
        wm.insert( new LinkingTest.A() );
        wm.insert( new LinkingTest.B() );
        wm.insert(new LinkingTest.C());
        wm.flushPropagations();

        // LiaNode  is in it's own segment
        BetaMemory betaMem = (BetaMemory) wm.getNodeMemory(beta);
        SegmentMemory  smem    = betaMem.getSegmentMemory();
        assertThat(smem.getRootNode()).isEqualTo(lian);
        assertThat(smem.getTipNode()).isEqualTo(beta);

        // child segment is not yet initialised, so null
        assertThat(smem.getFirst()).isNull();

        // there is no next
        assertThat(smem.getNext()).isNull();
        
        wm.fireAllRules(); // child segments should now be initialised
        wm.flushPropagations();

        SegmentMemory rtnSmem1 = smem.getFirst();
        assertThat(rtnSmem1.getRootNode()).isEqualTo(rtn1);
        assertThat(rtnSmem1.getTipNode()).isEqualTo(rtn1);

        SegmentMemory bSmem = rtnSmem1.getNext();
        assertThat(bSmem.getRootNode()).isEqualTo(bNode);
        assertThat(bSmem.getTipNode()).isEqualTo(bNode);

        SegmentMemory rtnSmem2 = bSmem.getFirst();
        assertThat(rtnSmem2.getRootNode()).isEqualTo(rtn2);
        assertThat(rtnSmem2.getTipNode()).isEqualTo(rtn2); 
        
        SegmentMemory cSmem = rtnSmem2.getNext();
        assertThat(cSmem.getRootNode()).isEqualTo(cNode);
        assertThat(cSmem.getTipNode()).isEqualTo(rtn3); // note rtn3 is in the same segment as C
    }       
  
    @Test
    public void testSubnetworkNoSharing() throws Exception {
        KieBase kbase = buildKnowledgeBase( " A()  not ( B() and C() ) \n" );

        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());
        
        ObjectTypeNode aotn = getObjectTypeNode(kbase, LinkingTest.A.class );

        LeftInputAdapterNode liaNode = (LeftInputAdapterNode) aotn.getObjectSinkPropagator().getSinks()[0];
        
        JoinNode bNode = ( JoinNode ) liaNode.getSinkPropagator().getSinks()[0];
        JoinNode          cNode   = ( JoinNode ) bNode.getSinkPropagator().getSinks()[0];
        TupleToObjectNode riaNode = (TupleToObjectNode) cNode.getSinkPropagator().getSinks()[0];
        
        NotNode notNode = ( NotNode ) liaNode.getSinkPropagator().getSinks()[1];
        RuleTerminalNode rtn1 = ( RuleTerminalNode) notNode.getSinkPropagator().getSinks()[0];
            
        wm.insert( new LinkingTest.A() );
        wm.insert( new LinkingTest.B() );
        wm.insert( new LinkingTest.C() );
        wm.flushPropagations();

        // LiaNode is in it's own segment
        LiaNodeMemory liaMem = wm.getNodeMemory(liaNode);
        SegmentMemory smem = liaMem.getSegmentMemory();
        assertThat(smem.getRootNode()).isEqualTo(liaNode);
        assertThat(smem.getTipNode()).isEqualTo(liaNode);
        assertThat(smem.getNext()).isNull();
        smem =  smem.getFirst();
        
        SegmentMemory bSmem = wm.getNodeMemory( bNode ).getSegmentMemory(); // it's nested inside of smem, so lookup from wm
        assertThat(bSmem).isEqualTo(smem);
        assertThat(bSmem.getRootNode()).isEqualTo(bNode);
        assertThat(bSmem.getTipNode()).isEqualTo(riaNode); 
        
        BetaMemory bm = (BetaMemory) wm.getNodeMemory(notNode);
        assertThat(smem.getNext()).isEqualTo(bm.getSegmentMemory());
        assertThat(bm.getSubnetworkPathMemory().getSegmentMemory()).isEqualTo(bSmem); // check subnetwork ref was made
    }        

    
    @Test
    public void tesSubnetworkAfterShare() throws Exception {
        KieBase kbase = buildKnowledgeBase( "  X() A() \n",
                                                  "   X() A()  not ( B() and C() ) \n" );

        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());
        
        ObjectTypeNode aotn = getObjectTypeNode(kbase, LinkingTest.A.class );
        ObjectTypeNode dotn = getObjectTypeNode(kbase, LinkingTest.X.class );

        LeftInputAdapterNode lian = (LeftInputAdapterNode) dotn.getObjectSinkPropagator().getSinks()[0];

        JoinNode joinNode = ((JoinRightAdapterNode) aotn.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        RuleTerminalNode rtn1 = ( RuleTerminalNode) joinNode.getSinkPropagator().getSinks()[0];
        
        JoinNode bNode = ( JoinNode ) joinNode.getSinkPropagator().getSinks()[1];
        JoinNode          cNode   = ( JoinNode ) bNode.getSinkPropagator().getSinks()[0];
        TupleToObjectNode riaNode = (TupleToObjectNode) cNode.getSinkPropagator().getSinks()[0];
        
        NotNode notNode = ( NotNode ) joinNode.getSinkPropagator().getSinks()[2];
        RuleTerminalNode rtn2 = ( RuleTerminalNode) notNode.getSinkPropagator().getSinks()[0];
               
        wm.insert( new LinkingTest.X() );
        wm.insert( new LinkingTest.A() );
        wm.insert( new LinkingTest.B() );
        wm.insert( new LinkingTest.C() );
        wm.fireAllRules();

        BetaMemory liaMem = (BetaMemory) wm.getNodeMemory(joinNode);
        SegmentMemory  smem   = liaMem.getSegmentMemory();
        assertThat(smem.getRootNode()).isEqualTo(lian);
        assertThat(smem.getTipNode()).isEqualTo(joinNode);
        
        SegmentMemory rtnSmem1 = smem.getFirst();
        assertThat(rtnSmem1.getRootNode()).isEqualTo(rtn1);
        assertThat(rtnSmem1.getTipNode()).isEqualTo(rtn1);
        
        SegmentMemory bSmem = rtnSmem1.getNext();
        assertThat(bSmem.getRootNode()).isEqualTo(bNode);
        assertThat(bSmem.getTipNode()).isEqualTo(riaNode);
        
        SegmentMemory notSmem = bSmem.getNext();
        assertThat(notSmem.getRootNode()).isEqualTo(notNode);
        assertThat(notSmem.getTipNode()).isEqualTo(rtn2);

        // child segment is not yet initialised, so null
        assertThat(bSmem.getFirst()).isNull();
    }    
    
    @Test
    public void tesShareInSubnetwork() throws Exception {
        KieBase kbase = buildKnowledgeBase( "  X() A() \n",
                                                  "   X() A() B() C() \n",
                                                  "   X() A()  not ( B() and C() ) \n" );

        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());
        
        ObjectTypeNode aotn = getObjectTypeNode(kbase, LinkingTest.A.class );
        ObjectTypeNode dotn = getObjectTypeNode(kbase, LinkingTest.X.class );

        LeftInputAdapterNode lian = (LeftInputAdapterNode) dotn.getObjectSinkPropagator().getSinks()[0];

        JoinNode beta = ((JoinRightAdapterNode) aotn.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        RuleTerminalNode rtn1 = ( RuleTerminalNode) beta.getSinkPropagator().getSinks()[0];
        
        JoinNode bNode = ( JoinNode ) beta.getSinkPropagator().getSinks()[1];
        JoinNode cNode = ( JoinNode ) bNode.getSinkPropagator().getSinks()[0];
        RuleTerminalNode  rtn2    = ( RuleTerminalNode ) cNode.getSinkPropagator().getSinks()[0];
        TupleToObjectNode riaNode = (TupleToObjectNode) cNode.getSinkPropagator().getSinks()[1];
        
        NotNode notNode = ( NotNode ) beta.getSinkPropagator().getSinks()[2];
        RuleTerminalNode rtn3 = ( RuleTerminalNode) notNode.getSinkPropagator().getSinks()[0];
               
        wm.insert( new LinkingTest.X() );
        wm.insert( new LinkingTest.A() );
        wm.insert( new LinkingTest.B() );
        wm.insert( new LinkingTest.C() );
        wm.flushPropagations();

        // LiaNode  is in it's own segment
        LiaNodeMemory liaMem = wm.getNodeMemory(lian);
        SegmentMemory smem = liaMem.getSegmentMemory();
        assertThat(smem.getRootNode()).isEqualTo(lian);
        assertThat(smem.getTipNode()).isEqualTo(beta);

        assertThat(smem.getFirst()).isNull(); // segment is not initialized yet

        wm.fireAllRules();

        SegmentMemory rtnSmem1 = smem.getFirst();
        assertThat(rtnSmem1.getRootNode()).isEqualTo(rtn1);
        assertThat(rtnSmem1.getTipNode()).isEqualTo(rtn1);

        SegmentMemory bSmem = rtnSmem1.getNext();
        assertThat(bSmem.getRootNode()).isEqualTo(bNode);
        assertThat(bSmem.getTipNode()).isEqualTo(cNode);

        SegmentMemory rtn2Smem = bSmem.getFirst();
        assertThat(rtn2Smem.getRootNode()).isEqualTo(rtn2);
        assertThat(rtn2Smem.getTipNode()).isEqualTo(rtn2); 
        
        SegmentMemory riaSmem = rtn2Smem.getNext();
        assertThat(riaSmem.getRootNode()).isEqualTo(riaNode);
        assertThat(riaSmem.getTipNode()).isEqualTo(riaNode);        
        
        SegmentMemory notSmem = bSmem.getNext();
        assertThat(notSmem.getRootNode()).isEqualTo(notNode);
        assertThat(notSmem.getTipNode()).isEqualTo(rtn3);     
    }

    @Test
    public void testBranchCESingleSegment() throws Exception {
        KieBase kbase = buildKnowledgeBase( "   $a : A() \n" +
                                                  "   if ( $a != null ) do[t1] \n" +
                                                  "   B() \n" );

        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());

        ObjectTypeNode aotn = getObjectTypeNode(kbase, LinkingTest.A.class );

        LeftInputAdapterNode liaNode = (LeftInputAdapterNode) aotn.getObjectSinkPropagator().getSinks()[0];

        ConditionalBranchNode cen1Node = ( ConditionalBranchNode ) liaNode.getSinkPropagator().getSinks()[0];
        JoinNode bNode = ( JoinNode ) cen1Node.getSinkPropagator().getSinks()[0];

        RuleTerminalNode rtn1 = ( RuleTerminalNode ) bNode.getSinkPropagator().getSinks()[0];

        FactHandle bFh = wm.insert( new LinkingTest.B() );
        wm.flushPropagations();

        LiaNodeMemory liaMem = wm.getNodeMemory(liaNode);
        SegmentMemory smem = liaMem.getSegmentMemory();
        assertThat(smem.getAllLinkedMaskTest()).isEqualTo(1);
        assertThat(smem.getLinkedNodeMask()).isEqualTo(4); // B links, but it will not trigger mask
        assertThat(smem.isSegmentLinked()).isFalse();

        PathMemory pmem = wm.getNodeMemory(rtn1);
        assertThat(pmem.getAllLinkedMaskTest()).isEqualTo(1);
        assertThat(pmem.getLinkedSegmentMask()).isEqualTo(0);
        assertThat(pmem.isRuleLinked()).isFalse();

        wm.insert(new LinkingTest.A());
        wm.flushPropagations();

        assertThat(smem.getLinkedNodeMask()).isEqualTo(5); // A links in segment
        assertThat(smem.isSegmentLinked()).isTrue();

        assertThat(pmem.getLinkedSegmentMask()).isEqualTo(1);
        assertThat(pmem.isRuleLinked()).isTrue();

        wm.delete(bFh); // retract B does not unlink the rule
        wm.flushPropagations();

        assertThat(pmem.getLinkedSegmentMask()).isEqualTo(1);
        assertThat(pmem.isRuleLinked()).isTrue();
    }

    @Test
    public void testBranchCEMultipleSegments() throws Exception {
        KieBase kbase = buildKnowledgeBase( "  X() $a : A() \n", // r1
                                                  "  X()  $a : A() \n" +
                                                  "   if ( $a != null ) do[t1] \n" +
                                                  "   B() \n", // r2
                                                  "  X() $a : A() \n"+
                                                  "   if ( $a != null ) do[t1] \n" +
                                                  "   B() \n" +
                                                  "   C() \n" // r3
                                                );

        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());

        ObjectTypeNode aotn = getObjectTypeNode(kbase, LinkingTest.A.class );

        LeftTupleSource liaNode = ((JoinRightAdapterNode) aotn.getObjectSinkPropagator().getSinks()[0]).getBetaNode();

        ConditionalBranchNode cen1Node = ( ConditionalBranchNode ) liaNode.getSinkPropagator().getSinks()[1];
        JoinNode bNode = ( JoinNode ) cen1Node.getSinkPropagator().getSinks()[0];
        RuleTerminalNode rtn2 = ( RuleTerminalNode ) bNode.getSinkPropagator().getSinks()[0];

        JoinNode cNode =  ( JoinNode ) bNode.getSinkPropagator().getSinks()[1];
        RuleTerminalNode rtn3 = ( RuleTerminalNode ) cNode.getSinkPropagator().getSinks()[0];

        FactHandle bFh = wm.insert( new LinkingTest.B() );
        FactHandle cFh = wm.insert( new LinkingTest.C() );
        wm.flushPropagations();

        BetaMemory bNodeBm   = (BetaMemory) wm.getNodeMemory(bNode);
        SegmentMemory  bNodeSmem = bNodeBm.getSegmentMemory();
        assertThat(bNodeSmem.getAllLinkedMaskTest()).isEqualTo(0); // no beta nodes before branch CE, so never unlinks
        assertThat(bNodeSmem.getLinkedNodeMask()).isEqualTo(2);

        PathMemory pmemr2 = wm.getNodeMemory(rtn2);
        assertThat(pmemr2.getAllLinkedMaskTest()).isEqualTo(1);
        assertThat(pmemr2.getLinkedSegmentMask()).isEqualTo(PhreakBuilder.isEagerSegmentCreation() ? 0 : 2);
        assertThat(pmemr2.getSegmentMemories().length).isEqualTo(3);
        assertThat(pmemr2.isRuleLinked()).isFalse();

        PathMemory pmemr3 = wm.getNodeMemory(rtn3);
        assertThat(pmemr3.getAllLinkedMaskTest()).isEqualTo(1);  // notice only the first segment links
        assertThat(pmemr3.getSegmentMemories().length).isEqualTo(3);
        assertThat(pmemr3.isRuleLinked()).isFalse();

        BetaMemory cNodeBm   = (BetaMemory) wm.getNodeMemory(cNode);
        SegmentMemory  cNodeSmem = cNodeBm.getSegmentMemory();

        assertThat(cNodeSmem.getAllLinkedMaskTest()).isEqualTo(PhreakBuilder.isEagerSegmentCreation() ? 0 : 1);
        assertThat(cNodeSmem.getLinkedNodeMask()).isEqualTo(1);

        wm.insert(new LinkingTest.X());
        wm.insert(new LinkingTest.A());
        wm.flushPropagations();

        assertThat(pmemr2.isRuleLinked()).isTrue();
        assertThat(pmemr3.isRuleLinked()).isTrue();

        wm.delete(bFh); // retract B does not unlink the rule
        wm.delete(cFh); // retract C does not unlink the rule
        wm.flushPropagations();

        assertThat(pmemr2.getLinkedSegmentMask()).isEqualTo(PhreakBuilder.isEagerSegmentCreation() ? 1 : 3); // b segment never unlinks, as it has no impact on path unlinking anyway
        assertThat(pmemr2.isRuleLinked()).isTrue();

        assertThat(pmemr3.getLinkedSegmentMask()).isEqualTo(PhreakBuilder.isEagerSegmentCreation() ? 1 : 3); // b segment never unlinks, as it has no impact on path unlinking anyway
        assertThat(pmemr3.isRuleLinked()).isTrue();
    }

    @Test @Ignore
    // TODO the conditional branch node is broken for node sharing, it's dropping the 3rd rules [t1] rtn
    // This is because is probably because the network buider, that handles candidate node for sharing isn't being applied branch logic.
    public void testShorterBranchInMultipleSegments() throws Exception {
        KieBase kbase = buildKnowledgeBase( "  X() $a : A() \n", // r0
                                            "  X()  $a : A() \n" +
                                            "   if ( $a != null ) do[t1] \n" +
                                            "   B() \n", // r1
                                            "  X() $a : A() \n"+
                                            "   if ( $a != null ) do[t1] \n" +
                                            "   B() \n" +
                                            "   C() \n" // r2
                                          );

        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase.newKieSession());
        List<String> list = new ArrayList<>();
        wm.setGlobal("list", list);

        wm.insert(new LinkingTest.X());
        wm.insert(new LinkingTest.A());
        wm.fireAllRules();
        assertThat(list).contains("t1-rule1", "t1-rule2"); // r1
    }

    private KieBase buildKnowledgeBase(String... rules) {
        String str = "";
        str += "package org.kie \n";
        str += "import " + LinkingTest.A.class.getCanonicalName() + "\n" ;
        str += "import " + LinkingTest.B.class.getCanonicalName() + "\n" ;
        str += "import " + LinkingTest.C.class.getCanonicalName() + "\n" ;
        str += "import " + LinkingTest.X.class.getCanonicalName() + "\n" ;
        str += "global java.util.List list \n";

        int i = 0;
        for ( String lhs : rules) {
            String ruleName = "rule" + i++;
            str += "rule " + ruleName +"  when \n";
            str +=  lhs;
            str += "then \n";
            str +=   "if (list!=null) list.add(\"main-" + ruleName + "\");";
            str += "then[t1] \n";
            str +=   "if (list!=null) list.add(\"t1-" + ruleName + "\");";
            str += "end \n";
        }

        KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, str);
        return kbase;
    }

    public ObjectTypeNode getObjectTypeNode(KieBase kbase, Class<?> nodeClass) {
        List<ObjectTypeNode> nodes = ((InternalRuleBase)kbase).getRete().getObjectTypeNodes();
        for ( ObjectTypeNode n : nodes ) {
            if ( ((ClassObjectType)n.getObjectType()).getClassType() == nodeClass ) {
                return n;
            }
        }
        return null;
    }    
}

