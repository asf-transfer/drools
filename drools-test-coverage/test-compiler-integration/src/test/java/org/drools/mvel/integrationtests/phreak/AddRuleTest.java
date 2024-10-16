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
package org.drools.mvel.integrationtests.phreak;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.drools.base.base.ClassObjectType;
import org.drools.core.common.BaseNode;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.Memory;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.core.impl.InternalRuleBase;
import org.drools.core.phreak.BuildtimeSegmentUtilities;
import org.drools.core.phreak.EagerPhreakBuilder;
import org.drools.core.phreak.EagerPhreakBuilder.Add;
import org.drools.core.phreak.EagerPhreakBuilder.Pair;
import org.drools.core.phreak.PhreakBuilder;
import org.drools.core.reteoo.BetaMemory;
import org.drools.core.reteoo.BetaNode;
import org.drools.core.reteoo.RightInputAdapterNode;
import org.drools.core.reteoo.EvalConditionNode;
import org.drools.core.reteoo.ExistsNode;
import org.drools.core.reteoo.JoinNode;
import org.drools.core.reteoo.JoinRightAdapterNode;
import org.drools.core.reteoo.LeftInputAdapterNode;
import org.drools.core.reteoo.LeftInputAdapterNode.LiaNodeMemory;
import org.drools.core.reteoo.LeftTupleNode;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.PathEndNode;
import org.drools.core.reteoo.PathMemory;
import org.drools.core.reteoo.Rete;
import org.drools.core.reteoo.RuleTerminalNode;
import org.drools.core.reteoo.SegmentMemory;
import org.drools.core.reteoo.SegmentMemory.SegmentPrototype;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.reteoo.Tuple;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.base.rule.GroupElement;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.KieSessionTestConfiguration;
import org.drools.testcoverage.common.util.KieUtil;
import org.drools.testcoverage.common.util.TestParametersUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.ReleaseId;
import org.kie.api.definition.KiePackage;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.rule.Match;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class AddRuleTest {
    private final KieBaseTestConfiguration kieBaseTestConfiguration;

    public AddRuleTest(KieBaseTestConfiguration kieBaseTestConfiguration) {
        this.kieBaseTestConfiguration = kieBaseTestConfiguration;
    }

    @Parameterized.Parameters(name = "KieBase type={0}")
    public static Collection<Object[]> getParameters() {
        return TestParametersUtil.getKieBaseCloudConfigurations(true);
    }

    @Test
    public void testAddThenSplitProtoAllJoins() {
        if (!PhreakBuilder.isEagerSegmentCreation()) return;

        InternalKnowledgeBase kbase1 = buildKnowledgeBase("r1", "   a : A() B() C() X() E()\n");
        InternalWorkingMemory wm = (InternalWorkingMemory) kbase1.newKieSession();
        ObjectTypeNode aotn = getObjectTypeNode(kbase1.getRete(), A.class);
        ObjectTypeNode cotn = getObjectTypeNode(kbase1.getRete(), C.class);

        LeftInputAdapterNode lian = (LeftInputAdapterNode) aotn.getSinks()[0];
        BetaNode cbeta = ((RightInputAdapterNode)cotn.getSinks()[0]).getBetaNode();

        insertAndFlush(wm);

        SegmentPrototype smemProto0 = kbase1.getSegmentPrototype(lian);
        PathEndNode endNode = smemProto0.getPathEndNodes()[0];

        assertSegmentsLengthAndPos(endNode, 1, wm);
        assertThat(endNode.getSegmentPrototypes()[0]).isSameAs(smemProto0);
        assertThat(endNode.getEagerSegmentPrototypes().length).isEqualTo(0);
        assertThat(smemProto0.getNodesInSegment().length).isEqualTo(6);
        LeftTupleNode[] nodes = smemProto0.getNodesInSegment();
        // the last RTN node is not part of the mask
        assertThat(smemProto0.getAllLinkedMaskTest()).isEqualTo(31);
        assertThat(smemProto0.getLinkedNodeMask()).isEqualTo(0);
        assertThat(smemProto0.getNodeTypesInSegment()).isEqualTo(BuildtimeSegmentUtilities.JOIN_NODE_BIT);

        SegmentPrototype[] oldSmemProtos = endNode.getSegmentPrototypes();
        SegmentPrototype smemProto1 = Add.processSplit(cbeta, kbase1, Collections.singletonList(wm), new HashSet<>());

        assertSegmentsLengthAndPos(endNode, 2, oldSmemProtos, smemProto1, wm);
        assertThat(endNode.getSegmentPrototypes()[0]).isSameAs(smemProto0);
        assertThat(endNode.getSegmentPrototypes()[1]).isSameAs(smemProto1);
        assertThat(endNode.getEagerSegmentPrototypes().length).isEqualTo(0);
        assertThat(smemProto0.getAllLinkedMaskTest()).isEqualTo(7);
        assertThat(smemProto0.getLinkedNodeMask()).isEqualTo(0);
        assertThat(smemProto0.getNodeTypesInSegment()).isEqualTo(BuildtimeSegmentUtilities.JOIN_NODE_BIT);
        assertThat(smemProto0.getNodesInSegment().length).isEqualTo(3);
        assertThat(nodes[0]).isSameAs(smemProto0.getNodesInSegment()[0]);
        assertThat(nodes[1]).isSameAs(smemProto0.getNodesInSegment()[1]);
        assertThat(nodes[2]).isSameAs(smemProto0.getNodesInSegment()[2]);

        assertThat(endNode).isSameAs(smemProto1.getPathEndNodes()[0]);
        assertThat(smemProto1.getAllLinkedMaskTest()).isEqualTo(3);
        assertThat(smemProto1.getLinkedNodeMask()).isEqualTo(0);
        assertThat(smemProto1.getNodeTypesInSegment()).isEqualTo(BuildtimeSegmentUtilities.JOIN_NODE_BIT);
        assertThat(smemProto1.getNodesInSegment().length).isEqualTo(3);
        assertThat(nodes[3]).isSameAs(smemProto1.getNodesInSegment()[0]);
        assertThat(nodes[4]).isSameAs(smemProto1.getNodesInSegment()[1]);
        assertThat(nodes[5]).isSameAs(smemProto1.getNodesInSegment()[2]);
    }

    @Test
    public void testAddThenSplitProtoWithNot() {
        if (!PhreakBuilder.isEagerSegmentCreation()) return;

        // This only really checks that the linkedNodeMask is set, for the 'not' bits
        InternalKnowledgeBase kbase1 = buildKnowledgeBase("r1", "   a : A() not B() C() not X() E()\n");
        InternalWorkingMemory wm = (InternalWorkingMemory) kbase1.newKieSession();
        ObjectTypeNode aotn = getObjectTypeNode(kbase1.getRete(), A.class);
        ObjectTypeNode cotn = getObjectTypeNode(kbase1.getRete(), C.class);

        LeftInputAdapterNode lian = (LeftInputAdapterNode) aotn.getSinks()[0];
        BetaNode cbeta = ((RightInputAdapterNode)cotn.getSinks()[0]).getBetaNode();

        insertAndFlush(wm);

        SegmentMemory smemLian = wm.getNodeMemories().getNodeMemory(lian, wm).getSegmentMemory();
        SegmentPrototype smemProto0 = kbase1.getSegmentPrototype(lian);
        assertThat(smemLian.getSegmentPrototype()).isSameAs(smemProto0);

        assertThat(smemProto0.getNodesInSegment().length).isEqualTo(6);
        assertThat(smemProto0.getAllLinkedMaskTest()).isEqualTo(31);
        assertThat(smemProto0.getLinkedNodeMask()).isEqualTo(10); // nots must be linked in
        assertThat(smemProto0.getNodeTypesInSegment()).isEqualTo(BuildtimeSegmentUtilities.JOIN_NODE_BIT | BuildtimeSegmentUtilities.NOT_NODE_BIT);
        SegmentPrototype smemProto1 = Add.processSplit(cbeta, kbase1, Collections.singletonList(wm), new HashSet<>());

        assertThat(smemProto0.getAllLinkedMaskTest()).isEqualTo(7);
        assertThat(smemProto0.getLinkedNodeMask()).isEqualTo(2);
        assertThat(smemProto0.getNodeTypesInSegment()).isEqualTo(BuildtimeSegmentUtilities.JOIN_NODE_BIT | BuildtimeSegmentUtilities.NOT_NODE_BIT);

        assertThat(smemProto1.getAllLinkedMaskTest()).isEqualTo(3);
        assertThat(smemProto1.getLinkedNodeMask()).isEqualTo(1);
        assertThat(smemProto1.getNodeTypesInSegment()).isEqualTo(BuildtimeSegmentUtilities.JOIN_NODE_BIT | BuildtimeSegmentUtilities.NOT_NODE_BIT);
    }
    @Test
    public void testAddWithSplitThenCreateThirdSplit() {
        if (!PhreakBuilder.isEagerSegmentCreation()) return;

        InternalKnowledgeBase kbase1 = buildKnowledgeBase("r",
                                                          "   a : A() B() C() E(1;) E(2;) E(3;) E(4;)\n",
                                                          "   A() B() C() X()\n");

        InternalWorkingMemory wm = (InternalWorkingMemory) kbase1.newKieSession();
        ObjectTypeNode aotn = getObjectTypeNode(kbase1.getRete(), A.class);
        ObjectTypeNode eotn = getObjectTypeNode(kbase1.getRete(), E.class);
        ObjectTypeNode xotn = getObjectTypeNode(kbase1.getRete(), X.class);

        LeftInputAdapterNode lian = (LeftInputAdapterNode) aotn.getSinks()[0];
        BetaNode ebeta = ((RightInputAdapterNode)eotn.getSinks()[0].getSinks()[0]).getBetaNode();
        BetaNode ebeta3 = ((RightInputAdapterNode)eotn.getSinks()[2].getSinks()[0]).getBetaNode();
        BetaNode xbeta = ((RightInputAdapterNode)xotn.getSinks()[0]).getBetaNode();

        insertAndFlush(wm);

        SegmentMemory smem = wm.getNodeMemories().getNodeMemory(lian, wm).getSegmentMemory();
        SegmentPrototype smemProto0 = kbase1.getSegmentPrototype(lian);
        assertThat(smem.getSegmentPrototype()).isSameAs(smemProto0);
        PathEndNode endNode0 = smemProto0.getPathEndNodes()[0];
        PathEndNode endNode1 = smemProto0.getPathEndNodes()[1];
        assertSegmentsLengthAndPos(endNode0, 2, wm);
        assertSegmentsLengthAndPos(endNode1, 2, wm);

        SegmentPrototype smemProtoE = kbase1.getSegmentPrototype(ebeta);
        assertThat(smemProtoE.getNodesInSegment().length).isEqualTo(5);
        assertThat(smemProtoE.getPos()).isEqualTo(1);
        SegmentPrototype smemProtoX = kbase1.getSegmentPrototype(xbeta);
        assertThat(smemProtoX.getPos()).isEqualTo(1);

        SegmentPrototype[] oldSmemProtos = endNode1.getSegmentPrototypes();
        SegmentPrototype smemProtoE2 = Add.processSplit(ebeta3, kbase1, Collections.singletonList(wm), new HashSet<>());
        assertSegmentsLengthAndPos(endNode0, 3, wm);
        assertSegmentsLengthAndPos(endNode1, 2, oldSmemProtos, smemProtoE2, wm);

        assertThat(endNode0.getSegmentPrototypes()[0]).isSameAs(smemProto0);
        assertThat(endNode0.getSegmentPrototypes()[1]).isSameAs(smemProtoE);
        assertThat(endNode0.getSegmentPrototypes()[2]).isSameAs(smemProtoE2);

        assertThat(smemProtoE.getNodesInSegment().length).isEqualTo(3);
        assertThat(smemProtoE2.getNodesInSegment().length).isEqualTo(2);

        assertThat(smemProtoE.getPos()).isEqualTo(1);
        assertThat(smemProtoE2.getPos()).isEqualTo(2);

        assertThat(smemProtoE.getSegmentPosMaskBit()).isEqualTo(2);
        assertThat(smemProtoE2.getSegmentPosMaskBit()).isEqualTo(4);

        assertThat(smemProtoE.getAllLinkedMaskTest()).isEqualTo(7);
        assertThat(smemProtoE2.getAllLinkedMaskTest()).isEqualTo(1);
    }

    @Test
    public void testAddWithSplitThenCreateThirdSplitInSamePos() {
        if (!PhreakBuilder.isEagerSegmentCreation()) return;

        InternalKnowledgeBase kbase1 = buildKnowledgeBase("r",
                                                          "   a : A() B() C() E(1;) E(2;) E(3;) E(4;)\n",
                                                          "   A() B() C() X() Y()\n");

        InternalWorkingMemory wm = (InternalWorkingMemory) kbase1.newKieSession();
        ObjectTypeNode aotn = getObjectTypeNode(kbase1.getRete(), A.class);
        ObjectTypeNode eotn = getObjectTypeNode(kbase1.getRete(), E.class);
        ObjectTypeNode cotn = getObjectTypeNode(kbase1.getRete(), C.class);
        ObjectTypeNode xotn = getObjectTypeNode(kbase1.getRete(), X.class);
        ObjectTypeNode yotn = getObjectTypeNode(kbase1.getRete(), Y.class);

        LeftInputAdapterNode lian = (LeftInputAdapterNode) aotn.getSinks()[0];
        BetaNode ebeta = ((RightInputAdapterNode)eotn.getSinks()[0].getSinks()[0]).getBetaNode();
        BetaNode cbeta = ((RightInputAdapterNode)cotn.getSinks()[0]).getBetaNode();
        BetaNode ebeta3 = ((RightInputAdapterNode)eotn.getSinks()[2].getSinks()[0]).getBetaNode();
        BetaNode xbeta = ((RightInputAdapterNode)xotn.getSinks()[0]).getBetaNode();

        insertAndFlush(wm);
        wm.fireAllRules();

        SegmentMemory smem = wm.getNodeMemories().getNodeMemory(lian, wm).getSegmentMemory();
        SegmentPrototype smemProto0 = kbase1.getSegmentPrototype(lian);
        assertThat(smem.getSegmentPrototype()).isSameAs(smemProto0);
        PathEndNode endNode0 = smemProto0.getPathEndNodes()[0];
        PathEndNode endNode1 = smemProto0.getPathEndNodes()[1];
        assertSegmentsLengthAndPos(endNode0, 2, wm);
        assertSegmentsLengthAndPos(endNode1, 2, wm);

        // processSplit should return null, as it's alreayd split and this does nothing.
        assertThat(Add.processSplit(cbeta, kbase1, Collections.singletonList(wm), new HashSet<>())).isNull();

        // now add a rule at the given split, so we can check paths were all updated correctly
        addRuleAtGivenSplit(kbase1, wm, cbeta, yotn);

        BetaNode jbeta = ((RightInputAdapterNode)yotn.getSinks()[1]).getBetaNode();
        assertThat(((ClassObjectType)jbeta.getObjectTypeNode().getObjectType()).getClassType()).isSameAs(Y.class);
        assertThat(jbeta.getLeftTupleSource()).isSameAs(cbeta);

        endNode0 = smemProto0.getPathEndNodes()[0];
        endNode1 = smemProto0.getPathEndNodes()[1];
        PathEndNode endNode2 = smemProto0.getPathEndNodes()[2];
        assertSegmentsLengthAndPos(endNode0, 2, wm);
        assertSegmentsLengthAndPos(endNode1, 2, wm);
        assertSegmentsLengthAndPos(endNode2, 2, wm);
    }

    private static void addRuleAtGivenSplit(InternalKnowledgeBase kbase1, InternalWorkingMemory wm, BetaNode cbeta, ObjectTypeNode yotn) {
        RuleImpl rule = new RuleImpl("newrule1");
        BuildContext buildContext = new BuildContext(kbase1, Collections.emptyList() );

        JoinNode joinNode = (JoinNode) BetaNodeBuilder.create(NodeTypeEnums.JoinNode, buildContext)
                                             .setLeftType( C.class )
                                             .setBinding( "object", "$object" )
                                             .setRightType( Y.class )
                                             .setConstraint( "object", "!=", "$object" ).build(cbeta, yotn);

        joinNode.doAttach(buildContext);

        RuleTerminalNode rtn = new RuleTerminalNode(buildContext.getNextNodeId(), joinNode, rule, new GroupElement(), 0, buildContext);
        rtn.setPathEndNodes(new PathEndNode[]{rtn});
        rtn.doAttach(buildContext);
        Arrays.stream(rtn.getPathNodes()).forEach( n -> {n.addAssociatedTerminal(rtn); ((BaseNode)n).addAssociation(rule, null);});
        new EagerPhreakBuilder().addRule(rtn, Collections.singletonList(wm), kbase1);
    }

    @Test
    public void testAddWithSplitAndEvalThenCreateThirdSplit() {
        if (!PhreakBuilder.isEagerSegmentCreation()) return;

        InternalKnowledgeBase kbase1 = buildKnowledgeBase("r",
                                                          "   a : A() B() C() eval(1==1) eval(2==2) E(3;) E(4;)\n",
                                                          "   A() B() C() X()\n");

        InternalWorkingMemory wm = (InternalWorkingMemory) kbase1.newKieSession();
        ObjectTypeNode aotn = getObjectTypeNode(kbase1.getRete(), A.class);
        ObjectTypeNode cotn = getObjectTypeNode(kbase1.getRete(), C.class);
        ObjectTypeNode eotn = getObjectTypeNode(kbase1.getRete(), E.class);

        LeftInputAdapterNode lian = (LeftInputAdapterNode) aotn.getSinks()[0];
        BetaNode cbeta = ((RightInputAdapterNode)cotn.getSinks()[0]).getBetaNode();
        BetaNode ebeta3 = ((RightInputAdapterNode)eotn.getSinks()[0].getSinks()[0]).getBetaNode();
        EvalConditionNode evnode = (EvalConditionNode)  cbeta.getSinks()[0];

        insertAndFlush(wm);

        SegmentMemory smem = wm.getNodeMemories().getNodeMemory(lian, wm).getSegmentMemory();
        SegmentPrototype smemProto0 = kbase1.getSegmentPrototype(lian);
        PathEndNode endNode1 = smemProto0.getPathEndNodes()[1];
        assertSegmentsLengthAndPos(endNode1, 2, wm);

        SegmentPrototype smemProtoEV = kbase1.getSegmentPrototype(evnode);
        assertThat(smemProtoEV.getNodesInSegment().length).isEqualTo(5);
        assertThat(smemProtoEV.getPos()).isEqualTo(1);
        assertThat(smemProtoEV.getAllLinkedMaskTest()).isEqualTo(12); // first two are evals, so each is 0

        SegmentPrototype[] oldSmemProtos = endNode1.getSegmentPrototypes();
        SegmentPrototype smemProtoE4 = Add.processSplit(ebeta3, kbase1, Collections.singletonList(wm), new HashSet<>());

        assertSegmentsLengthAndPos(endNode1, 2, oldSmemProtos, smemProtoE4, wm);


        assertThat(smemProtoEV.getSegmentPosMaskBit()).isEqualTo(2);
        assertThat(smemProtoE4.getSegmentPosMaskBit()).isEqualTo(4);

        assertThat(smemProtoEV.getAllLinkedMaskTest()).isEqualTo(4);
        assertThat(smemProtoE4.getAllLinkedMaskTest()).isEqualTo(1);
    }

    @Test
    public void testChildProtosPosAndEndNodeSegmentsUpdated() {
        if (!PhreakBuilder.isEagerSegmentCreation()) return;

        InternalKnowledgeBase kbase1 = buildKnowledgeBase("r",
                                                          "   a : A() B() B(1;) C(1;) X() X(1;) X(2;) E()\n",
                                                          "   a : A() B() B(1;) C(1;) X() X(1;) X(3;)\n",
                                                          "   a : A() B() B(1;) C(2;)\n");

        InternalWorkingMemory wm = (InternalWorkingMemory) kbase1.newKieSession();
        ObjectTypeNode aotn = getObjectTypeNode(kbase1.getRete(), A.class);
        ObjectTypeNode botn = getObjectTypeNode(kbase1.getRete(), B.class);

        LeftInputAdapterNode lian = (LeftInputAdapterNode) aotn.getSinks()[0];
        BetaNode bbeta = ((RightInputAdapterNode)botn.getSinks()[0]).getBetaNode();

        insertAndFlush(wm);

        SegmentPrototype smemProto0 = kbase1.getSegmentPrototype(lian);
        PathEndNode endNode0 = smemProto0.getPathEndNodes()[0];
        assertThat(endNode0.getPathMemSpec().allLinkedTestMask()).isEqualTo(7);
        assertThat(endNode0.getPathMemSpec().smemCount()).isEqualTo(3);

        assertSegmentsLengthAndPos(endNode0, 3, wm);

        SegmentPrototype[] oldSmemProtos = endNode0.getSegmentPrototypes();
        SegmentPrototype smemProto1 = Add.processSplit(bbeta, kbase1, Collections.singletonList(wm), new HashSet<>());

        assertSegmentsLengthAndPos(endNode0, 4, oldSmemProtos, smemProto1, wm);
        assertThat(endNode0.getPathMemSpec().allLinkedTestMask()).isEqualTo(15);
        assertThat(endNode0.getPathMemSpec().smemCount()).isEqualTo(4);
    }

    /**
     * This tests that masks and segments are not set, when outside of the subnetwork for a given path.
     */
    @Test
    public void testDataStructuresWithSubnetwork() {
        if (!PhreakBuilder.isEagerSegmentCreation()) return;

        InternalKnowledgeBase kbase1 = buildKnowledgeBase("r",
                                                          "   a : A() B() exists ( C() and C(1;) ) E() X()\n");

        InternalWorkingMemory wm = (InternalWorkingMemory) kbase1.newKieSession();
        ObjectTypeNode aotn = getObjectTypeNode(kbase1.getRete(), A.class);
        ObjectTypeNode botn = getObjectTypeNode(kbase1.getRete(), B.class);

        LeftInputAdapterNode lian = (LeftInputAdapterNode) aotn.getSinks()[0];

        insertAndFlush(wm);

        SegmentPrototype smemProto0 = kbase1.getSegmentPrototype(lian);

        PathEndNode endNode0 = smemProto0.getPathEndNodes()[0];
        assertThat(endNode0.getType()).isEqualTo(NodeTypeEnums.TupleToObjectNode);
        assertThat(endNode0.getPathMemSpec().allLinkedTestMask()).isEqualTo(2);
        assertThat(endNode0.getPathMemSpec().smemCount()).isEqualTo(2);

        PathEndNode endNode1 = smemProto0.getPathEndNodes()[1];
        assertThat(endNode1.getType()).isEqualTo(NodeTypeEnums.RuleTerminalNode);
        assertThat(endNode1.getPathMemSpec().allLinkedTestMask()).isEqualTo(3);
        assertThat(endNode1.getPathMemSpec().smemCount()).isEqualTo(2);
    }

    @Test
    public void testFindNewBrancheRootsSimple() {
        InternalKnowledgeBase kbase1 = buildKnowledgeBase("r",
                                                          "   a : A() B() C() E()\n",
                                                          "   a : A() B() X() E()\n");

        ObjectTypeNode cotn = getObjectTypeNode(kbase1.getRete(), C.class);
        ObjectTypeNode xotn = getObjectTypeNode(kbase1.getRete(), X.class);

        BetaNode cBeta = ((RightInputAdapterNode)cotn.getSinks()[0]).getBetaNode();
        BetaNode xBeta = ((RightInputAdapterNode)xotn.getSinks()[0]).getBetaNode();

        TerminalNode[] tns = kbase1.getReteooBuilder().getTerminalNodes("org.kie.r0");
        assertThat(tns.length).isEqualTo(1);
        List<EagerPhreakBuilder.Pair> branchRoots =  EagerPhreakBuilder.getExclusiveBranchRoots(tns[0]);
        assertThat(branchRoots.size()).isEqualTo(1);
        assertThat(((Pair)branchRoots.toArray()[0]).child).isSameAs(cBeta);

        tns = kbase1.getReteooBuilder().getTerminalNodes("org.kie.r1");
        assertThat(tns.length).isEqualTo(1);
        branchRoots =  EagerPhreakBuilder.getExclusiveBranchRoots(tns[0]);
        assertThat(branchRoots.size()).isEqualTo(1);
        assertThat(((Pair)branchRoots.toArray()[0]).child).isSameAs(xBeta);
    }

    @Test
    public void testNewBrancheRootsWithSubnetwork() {
        InternalKnowledgeBase kbase1 = buildKnowledgeBase("r",
                                                          "   a : A() B(1;) C() E() X()\n",
                                                          "   a : A() B(1;) C() exists ( E() and F() ) B(2;)\n");

        ObjectTypeNode cotn = getObjectTypeNode(kbase1.getRete(), C.class);
        ObjectTypeNode fotn = getObjectTypeNode(kbase1.getRete(), F.class);
        ObjectTypeNode xotn = getObjectTypeNode(kbase1.getRete(), X.class);
        ObjectTypeNode botn = getObjectTypeNode(kbase1.getRete(), B.class);

        BetaNode fBeta = ((RightInputAdapterNode)fotn.getSinks()[0]).getBetaNode();
        BetaNode xBeta = ((RightInputAdapterNode)xotn.getSinks()[0]).getBetaNode();
        ExistsNode exists = (ExistsNode) ((RightInputAdapterNode)botn.getSinks()[1].getSinks()[0]).getBetaNode().getLeftTupleSource();

        TerminalNode[] tns = kbase1.getReteooBuilder().getTerminalNodes("org.kie.r0");
        assertThat(tns.length).isEqualTo(1);
        List<EagerPhreakBuilder.Pair> branchRoots =  EagerPhreakBuilder.getExclusiveBranchRoots(tns[0]);
        assertThat(branchRoots.size()).isEqualTo(1);
        Pair[] pairs = branchRoots.toArray(new Pair[1]);
        assertThat(pairs[0].child).isSameAs(xBeta);

        tns = kbase1.getReteooBuilder().getTerminalNodes("org.kie.r1");
        assertThat(tns.length).isEqualTo(1);
        branchRoots =  EagerPhreakBuilder.getExclusiveBranchRoots(tns[0]);
        assertThat(branchRoots.size()).isEqualTo(2);
        assertThat(((Pair)branchRoots.toArray()[0]).child).isSameAs(exists);
        assertThat(((Pair)branchRoots.toArray()[1]).child).isSameAs(fBeta);
    }

    private static void insertAndFlush(InternalWorkingMemory wm) {
        wm.insert(new A(1));
        wm.insert(new B(1));
        wm.insert(new B(2));
        wm.insert(new C(1));
        wm.insert(new C(2));
        wm.insert(new E(1));
        wm.insert(new E(2));
        wm.insert(new E(3));
        wm.insert(new E(4));
        wm.insert(new X(1));
        wm.insert(new Y(1));

        wm.setGlobal("list", new ArrayList<>());
        wm.flushPropagations();
    }

    private static void assertSegmentsLengthAndPos(PathEndNode endNode, int s, InternalWorkingMemory wm) {
        assertSegmentsLengthAndPos(endNode, s, null, null, wm);
    }

    private static void assertSegmentsLengthAndPos(PathEndNode endNode, int s, SegmentPrototype[] oldProtos, SegmentPrototype newProto, InternalWorkingMemory wm) {
        assertThat(endNode.getSegmentPrototypes().length).isEqualTo(s);
        int smemOffset = 0;
        int bitPos = 1;

        for (int i = 0; i < endNode.getSegmentPrototypes().length; i++) {
            assertThat(endNode.getSegmentPrototypes()[i].getPos()).isEqualTo(i);
            assertThat(endNode.getSegmentPrototypes()[i].getSegmentPosMaskBit()).isEqualTo(bitPos);
            bitPos = bitPos << 1;
        }

        if (oldProtos != null) {
            // if old protos exist, assert the split was done correctly and protos all match as expected
            for (int i = 0; i < endNode.getSegmentPrototypes().length; i++) {
                if (i == newProto.getPos()) {
                    // assert the inserted proto, and set the offset
                    assertThat(newProto).isSameAs(endNode.getSegmentPrototypes()[newProto.getPos()]);
                    smemOffset = 1;
                } else {
                    assertThat(oldProtos[i - smemOffset]).isSameAs(endNode.getSegmentPrototypes()[i]);
                }
            }
        }

        SegmentPrototype startProto = wm.getKnowledgeBase().getSegmentPrototype(endNode.getStartTupleSource());
        // ensure all smems are null before start SmemProto. If startProto pos is 0, skip this
        for (int i = 0; i < startProto.getPos(); i++) {
            assertThat(endNode.getSegmentPrototypes()[i]).isNull();
        }

        // assert that all segments contain this path
        for (int i = 0; i < endNode.getSegmentPrototypes().length; i++) {
            SegmentPrototype proto = endNode.getSegmentPrototypes()[i];
            if ( proto != null) { // protos before startTupleSource will be null
                asList(proto.getPathEndNodes()).contains(endNode);
            }
        }

        if ( wm == null ) {
            return;
        }

        for (int i = 0; i < endNode.getSegmentPrototypes().length; i++) {
            SegmentPrototype proto = endNode.getSegmentPrototypes()[i];

            Memory m = wm.getNodeMemories().peekNodeMemory(proto.getRootNode());
            SegmentMemory sm = m.getSegmentMemory();

            assertThat(sm.getSegmentPrototype()).isSameAs(proto);
            assertThat(sm.getRootNode()).isSameAs(proto.getRootNode());
            assertThat(sm.getTipNode()).isSameAs(proto.getTipNode());

            assertThat(sm.getPos()).isEqualTo(proto.getPos());
            assertThat(sm.getAllLinkedMaskTest()).isEqualTo(proto.getAllLinkedMaskTest());
            assertThat(sm.getSegmentPosMaskBit()).isEqualTo(proto.getSegmentPosMaskBit());
        }
    }


    @Test
    public void testPopulatedSingleRuleNoSharing() {
        KieServices ks = KieServices.get();
        KieContainer kieContainer = KieUtil.getKieContainerFromDrls(kieBaseTestConfiguration, KieSessionTestConfiguration.STATEFUL_PSEUDO);
        InternalWorkingMemory wm  = (InternalWorkingMemory) kieContainer.newKieSession();

        wm.insert(new A(1));
        wm.insert(new B(1));
        wm.insert(new C(1));
        wm.insert(new C(2));
        wm.insert(new X(1));
        wm.insert(new E(1));

        wm.fireAllRules();

        String rule = buildKnowledgePackageDrl("r1", "   A() B() C(object == 2) X() E()\n");
        ReleaseId releaseId2 = ks.newReleaseId("org.kie", "testPopulatedSingleRuleNoSharing", "2.0.0");

        KieUtil.getKieModuleFromDrls(releaseId2, kieBaseTestConfiguration, KieSessionTestConfiguration.STATEFUL_PSEUDO,
                                     new HashMap<>(), rule);

        kieContainer.updateToVersion(releaseId2);

        List<Match> list = new ArrayList<>();
        wm.setGlobal("list", list);

        ObjectTypeNode aotn = getObjectTypeNode(wm.getKnowledgeBase().getRete(), A.class);
        LeftInputAdapterNode liaNode = (LeftInputAdapterNode) aotn.getObjectSinkPropagator().getSinks()[0];

        LiaNodeMemory lm = wm.getNodeMemory(liaNode);
        SegmentMemory sm = lm.getSegmentMemory();
        assertThat(sm.getStagedLeftTuples().getInsertFirst()).isNotNull();

        wm.fireAllRules();
        assertThat(sm.getStagedLeftTuples().getInsertFirst()).isNull();
        assertThat(list.size()).isEqualTo(1);

        assertThat(list.get(0).getRule().getName()).isEqualTo("r1");
    }

    @Test
    public void testPopulatedSingleRuleNoSharingWithSubnetworkAtStart() throws Exception {
        KieServices ks = KieServices.get();
        KieContainer kieContainer = KieUtil.getKieContainerFromDrls(kieBaseTestConfiguration, KieSessionTestConfiguration.STATEFUL_PSEUDO);
        InternalWorkingMemory wm  = (InternalWorkingMemory) kieContainer.newKieSession();

        wm.insert(new A(1));
        wm.insert(new A(2));
        wm.insert(new X(1));
        wm.insert(new E(1));

        wm.insert(new C(2));
        wm.fireAllRules();


        String rule = buildKnowledgePackageDrl("r1", "   A() not( B() and C() ) X() E()\n");
        ReleaseId releaseId2 = ks.newReleaseId("org.kie", "testPopulatedSingleRuleNoSharingWithSubnetworkAtStart", "2.0.0");

        KieUtil.getKieModuleFromDrls(releaseId2, kieBaseTestConfiguration, KieSessionTestConfiguration.STATEFUL_PSEUDO,
                                     new HashMap<>(), rule);
        kieContainer.updateToVersion(releaseId2);

        List list = new ArrayList();
        wm.setGlobal("list", list);

        ObjectTypeNode aotn = getObjectTypeNode(wm.getKnowledgeBase().getRete(), A.class );
        LeftInputAdapterNode liaNode = (LeftInputAdapterNode) aotn.getObjectSinkPropagator().getSinks()[0];

        LiaNodeMemory lm = wm.getNodeMemory(liaNode);
        SegmentMemory sm = lm.getSegmentMemory();
        assertThat(sm.getStagedLeftTuples().getInsertFirst()).isNull();

        SegmentMemory subSm = sm.getFirst();
        SegmentMemory mainSm = subSm.getNext();


        assertThat(subSm.getStagedLeftTuples().getInsertFirst()).isNotNull();
        assertThat((Tuple) subSm.getStagedLeftTuples().getInsertFirst().getStagedNext()).isNotNull();
        assertThat((Tuple)subSm.getStagedLeftTuples().getInsertFirst().getStagedNext().getStagedNext()).isNull();
        assertThat(mainSm.getStagedLeftTuples().getInsertFirst()).isNotNull();
        assertThat((Tuple) mainSm.getStagedLeftTuples().getInsertFirst().getStagedNext()).isNotNull();
        assertThat((Tuple)mainSm.getStagedLeftTuples().getInsertFirst().getStagedNext().getStagedNext()).isNull();

        wm.fireAllRules();
        assertThat(subSm.getStagedLeftTuples().getInsertFirst()).isNull();
        assertThat(mainSm.getStagedLeftTuples().getInsertFirst()).isNull();
        assertThat(list.size()).isEqualTo(2);

        assertThat(((Match) list.get(0)).getRule().getName()).isEqualTo("r1");
    }

    @Test
    public void testPopulatedRuleMidwayShare() throws Exception {
        InternalKnowledgeBase kbase1 = buildKnowledgeBase("r1", "   a : A() B() C(1;) X() E()\n");
        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase1.newKieSession());
        List list = new ArrayList();
        wm.setGlobal("list", list);

        wm.insert(new A(1));
        wm.insert(new A(2));
        wm.insert(new A(3));
        wm.insert(new B(1));
        wm.insert(new C(1));
        wm.insert(new C(2));
        wm.insert(new X(1));
        wm.insert(new E(1));

        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(3);

        kbase1.addPackages( buildKnowledgePackage("r2", "   a : A() B() C(2;) X() E()\n") );

        ObjectTypeNode aotn = getObjectTypeNode(kbase1, A.class );
        LeftInputAdapterNode liaNode = (LeftInputAdapterNode) aotn.getObjectSinkPropagator().getSinks()[0];
        JoinNode bNode = (JoinNode) liaNode.getSinkPropagator().getFirstLeftTupleSink();

        JoinNode c1Node = (JoinNode) bNode.getSinkPropagator().getFirstLeftTupleSink();
        JoinNode c2Node = (JoinNode) bNode.getSinkPropagator().getLastLeftTupleSink();

        LiaNodeMemory lm = wm.getNodeMemory(liaNode);
        SegmentMemory sm = lm.getSegmentMemory();

        BetaMemory c1Mem = (BetaMemory) wm.getNodeMemory(c1Node);
        assertThat(c1Mem.getSegmentMemory()).isSameAs(sm.getFirst());
        assertThat(c1Mem.getLeftTupleMemory().size()).isEqualTo(3);
        assertThat(c1Mem.getRightTupleMemory().size()).isEqualTo(1);

        BetaMemory c2Mem  = (BetaMemory) wm.getNodeMemory(c2Node);
        SegmentMemory  c2Smem =  sm.getFirst().getNext();
        assertThat(c2Mem.getSegmentMemory()).isSameAs(c2Smem);
        assertThat(c2Mem.getLeftTupleMemory().size()).isEqualTo(0);
        assertThat(c2Mem.getRightTupleMemory().size()).isEqualTo(0);
        assertThat(c2Smem.getStagedLeftTuples().getInsertFirst()).isNotNull();
        assertThat((Tuple) c2Smem.getStagedLeftTuples().getInsertFirst().getStagedNext()).isNotNull();
        assertThat((Tuple) c2Smem.getStagedLeftTuples().getInsertFirst().getStagedNext().getStagedNext()).isNotNull();
        assertThat((Tuple) c2Smem.getStagedLeftTuples().getInsertFirst().getStagedNext().getStagedNext().getStagedNext()).isNull();

        wm.fireAllRules();
        assertThat(c2Mem.getLeftTupleMemory().size()).isEqualTo(3);
        assertThat(c2Mem.getRightTupleMemory().size()).isEqualTo(1);
        assertThat(c2Smem.getStagedLeftTuples().getInsertFirst()).isNull();
        assertThat(list.size()).isEqualTo(6);

        assertThat(((Match) list.get(0)).getRule().getName()).isEqualTo("r1");
        assertThat(((Match) list.get(1)).getRule().getName()).isEqualTo("r1");
        assertThat(((Match) list.get(2)).getRule().getName()).isEqualTo("r1");
        assertThat(((Match) list.get(3)).getRule().getName()).isEqualTo("r2");
        assertThat(((A) ((Match) list.get(3)).getDeclarationValue("a")).getObject()).isEqualTo(3);
        assertThat(((Match) list.get(4)).getRule().getName()).isEqualTo("r2");
        assertThat(((A) ((Match) list.get(4)).getDeclarationValue("a")).getObject()).isEqualTo(2);
        assertThat(((Match) list.get(5)).getRule().getName()).isEqualTo("r2");
        assertThat(((A) ((Match) list.get(5)).getDeclarationValue("a")).getObject()).isEqualTo(1);
    }

    @Test
    public void testPopulatedRuleWithEvals() throws Exception {
        InternalKnowledgeBase kbase1 = buildKnowledgeBase("r1", "   a:A() B() eval(1==1) eval(1==1) C(1;) \n");
        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase1.newKieSession());
        List list = new ArrayList();
        wm.setGlobal("list", list);

        wm.insert(new A(1));
        wm.insert(new A(2));
        wm.insert(new A(3));
        wm.insert(new B(1));
        wm.insert(new C(1));
        wm.insert(new C(2));

        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(3);

        kbase1.addPackages( buildKnowledgePackage("r2", "   a:A() B() eval(1==1) eval(1==1) C(2;) \n") );

        ObjectTypeNode aotn = getObjectTypeNode(kbase1, A.class );
        LeftInputAdapterNode liaNode = (LeftInputAdapterNode) aotn.getObjectSinkPropagator().getSinks()[0];
        JoinNode bNode = (JoinNode) liaNode.getSinkPropagator().getFirstLeftTupleSink();

        EvalConditionNode e1 = (EvalConditionNode) bNode.getSinkPropagator().getFirstLeftTupleSink();
        EvalConditionNode e2 = (EvalConditionNode) e1.getSinkPropagator().getFirstLeftTupleSink();

        JoinNode c1Node = (JoinNode) e2.getSinkPropagator().getFirstLeftTupleSink();
        JoinNode c2Node = (JoinNode) e2.getSinkPropagator().getLastLeftTupleSink();

        LiaNodeMemory lm = wm.getNodeMemory(liaNode);
        SegmentMemory sm = lm.getSegmentMemory();

        BetaMemory c1Mem = (BetaMemory) wm.getNodeMemory(c1Node);
        assertThat(c1Mem.getSegmentMemory()).isSameAs(sm.getFirst());
        assertThat(c1Mem.getLeftTupleMemory().size()).isEqualTo(3);
        assertThat(c1Mem.getRightTupleMemory().size()).isEqualTo(1);

        BetaMemory c2Mem  = (BetaMemory) wm.getNodeMemory(c2Node);
        SegmentMemory  c2Smem =  sm.getFirst().getNext();
        assertThat(c2Mem.getSegmentMemory()).isSameAs(c2Smem);
        assertThat(c2Mem.getLeftTupleMemory().size()).isEqualTo(0);
        assertThat(c2Mem.getRightTupleMemory().size()).isEqualTo(0);
        assertThat(c2Smem.getStagedLeftTuples().getInsertFirst()).isNotNull();
        assertThat((Tuple) c2Smem.getStagedLeftTuples().getInsertFirst().getStagedNext()).isNotNull();
        assertThat((Tuple) c2Smem.getStagedLeftTuples().getInsertFirst().getStagedNext().getStagedNext()).isNotNull();
        assertThat((Tuple) c2Smem.getStagedLeftTuples().getInsertFirst().getStagedNext().getStagedNext().getStagedNext()).isNull();

        wm.fireAllRules();
        assertThat(c2Mem.getLeftTupleMemory().size()).isEqualTo(3);
        assertThat(c2Mem.getRightTupleMemory().size()).isEqualTo(1);
        assertThat(c2Smem.getStagedLeftTuples().getInsertFirst()).isNull();
        assertThat(list.size()).isEqualTo(6);

        assertThat(((Match) list.get(0)).getRule().getName()).isEqualTo("r1");
        assertThat(((Match) list.get(1)).getRule().getName()).isEqualTo("r1");
        assertThat(((Match) list.get(2)).getRule().getName()).isEqualTo("r1");
        assertThat(((Match) list.get(3)).getRule().getName()).isEqualTo("r2");
        assertThat(((A) ((Match) list.get(3)).getDeclarationValue("a")).getObject()).isEqualTo(3);
        assertThat(((Match) list.get(4)).getRule().getName()).isEqualTo("r2");
        assertThat(((A) ((Match) list.get(4)).getDeclarationValue("a")).getObject()).isEqualTo(2);
        assertThat(((Match) list.get(5)).getRule().getName()).isEqualTo("r2");
        assertThat(((A) ((Match) list.get(5)).getDeclarationValue("a")).getObject()).isEqualTo(1);
    }

    @Test
    public void testPopulatedSharedLiaNode() throws Exception {
        InternalKnowledgeBase kbase1 = buildKnowledgeBase("r1", "   A() B(1;) C() X() E()\n");
        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase1.newKieSession());
        List list = new ArrayList();
        wm.setGlobal("list", list);

        wm.insert(new A(1));
        wm.insert(new A(2));
        wm.insert(new A(3));
        wm.insert(new B(1));
        wm.insert(new B(2));
        wm.insert(new C(1));
        wm.insert(new X(1));
        wm.insert(new E(1));

        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(3);

        kbase1.addPackages( buildKnowledgePackage("r2", "   a : A() B(2;) C() X() E()\n") );

        ObjectTypeNode aotn = getObjectTypeNode(kbase1, A.class );
        LeftInputAdapterNode liaNode = (LeftInputAdapterNode) aotn.getObjectSinkPropagator().getSinks()[0];
        JoinNode bNode1 = (JoinNode) liaNode.getSinkPropagator().getFirstLeftTupleSink();
        JoinNode bNode2 = (JoinNode) liaNode.getSinkPropagator().getLastLeftTupleSink();

        BetaMemory bm = (BetaMemory) wm.getNodeMemory(bNode2);
        SegmentMemory  sm = bm.getSegmentMemory();
        assertThat(sm.getStagedLeftTuples().getInsertFirst()).isNotNull();
        assertThat((Tuple) sm.getStagedLeftTuples().getInsertFirst().getStagedNext()).isNotNull();
        assertThat((Tuple) sm.getStagedLeftTuples().getInsertFirst().getStagedNext().getStagedNext()).isNotNull();
        assertThat((Tuple) sm.getStagedLeftTuples().getInsertFirst().getStagedNext().getStagedNext().getStagedNext()).isNull();

        wm.fireAllRules();
        assertThat(sm.getStagedLeftTuples().getInsertFirst()).isNull();
        assertThat(list.size()).isEqualTo(6);

        assertThat(((Match) list.get(0)).getRule().getName()).isEqualTo("r1");
        assertThat(((Match) list.get(1)).getRule().getName()).isEqualTo("r1");
        assertThat(((Match) list.get(2)).getRule().getName()).isEqualTo("r1");
        assertThat(((Match) list.get(3)).getRule().getName()).isEqualTo("r2");
        assertThat(((Match) list.get(4)).getRule().getName()).isEqualTo("r2");
        assertThat(((Match) list.get(5)).getRule().getName()).isEqualTo("r2");

        List results = new ArrayList();
        results.add(((A)((Match)list.get(3)).getDeclarationValue("a")).getObject());
        results.add(((A)((Match)list.get(4)).getDeclarationValue("a")).getObject());
        results.add(((A)((Match)list.get(5)).getDeclarationValue("a")).getObject());
        assertThat(results.containsAll(asList(1, 2, 3))).isTrue();
    }

    @Test
    public void testPopulatedSharedToRtn() throws Exception {
        InternalKnowledgeBase kbase1 = buildKnowledgeBase("r1", "   A() B() C() X() E()\n");
        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase1.newKieSession());
        List list = new ArrayList();
        wm.setGlobal("list", list);

        wm.insert(new A(1));
        wm.insert(new A(2));
        wm.insert(new B(1));
        wm.insert(new C(1));
        wm.insert(new X(1));
        wm.insert(new E(1));

        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(2);

        kbase1.addPackages( buildKnowledgePackage("r2", "   A() B() C() X() E()\n") );

        ObjectTypeNode eotn = getObjectTypeNode(kbase1, E.class );
        JoinNode eNode = ((JoinRightAdapterNode)eotn.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        RuleTerminalNode rtn = ( RuleTerminalNode ) eNode.getSinkPropagator().getLastLeftTupleSink();

        PathMemory pm = wm.getNodeMemory(rtn);
        SegmentMemory sm = pm.getSegmentMemory();
        assertThat(sm.getStagedLeftTuples().getInsertFirst()).isNotNull();
        assertThat((Tuple) sm.getStagedLeftTuples().getInsertFirst().getStagedNext()).isNotNull();
        assertThat((Tuple) sm.getStagedLeftTuples().getInsertFirst().getStagedNext().getStagedNext()).isNull();

        wm.fireAllRules();
        assertThat(sm.getStagedLeftTuples().getInsertFirst()).isNull();
        assertThat(list.size()).isEqualTo(4);

        assertThat(((Match) list.get(0)).getRule().getName()).isEqualTo("r1");
        assertThat(((Match) list.get(1)).getRule().getName()).isEqualTo("r1");
        assertThat(((Match) list.get(2)).getRule().getName()).isEqualTo("r2");
        assertThat(((Match) list.get(3)).getRule().getName()).isEqualTo("r2");
    }

    @Test
    public void testPopulatedMultipleShares() throws Exception {
        InternalKnowledgeBase kbase1 = buildKnowledgeBase("r1", "   A(1;)  A(2;) B(1;) B(2;) C(1;) X() E()\n");
        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase1.newKieSession());
        List<Match> list = new ArrayList<>();
        wm.setGlobal("list", list);

        wm.insert(new A(1));
        wm.insert(new A(2));
        wm.insert(new A(2));
        wm.insert(new A(3));
        wm.insert(new B(1));
        wm.insert(new B(2));
        wm.insert(new C(1));
        wm.insert(new C(2));
        wm.insert(new X(1));
        wm.insert(new E(1));

        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(2);

        kbase1.addPackages( buildKnowledgePackage("r2", "   A(1;)  A(2;) B(1;) B(2;) C(2;) X() E()\n") );

        kbase1.addPackages( buildKnowledgePackage("r3", "   A(1;)  A(3;) B(1;) B(2;) C(2;) X() E()\n") );

        wm.fireAllRules();
        assertThat(list.size()).isEqualTo(5);

        assertThat(list.stream().filter(m -> m.getRule().getName().equals("r1")).count()).isEqualTo(2);
        assertThat(list.stream().filter(m -> m.getRule().getName().equals("r2")).count()).isEqualTo(2);
        assertThat(list.stream().filter(m -> m.getRule().getName().equals("r3")).count()).isEqualTo(1);
    }

    @Test
    public void testSplitTwoBeforeCreatedSegment() throws Exception {
        InternalKnowledgeBase kbase1 =          buildKnowledgeBase("r1", "   A(1;)  A(2;) B(1;) B(2;) C(1;) C(2;) X(1;) X(2;) E(1;) E(2;)\n");
        kbase1.addPackages( buildKnowledgePackage("r2", "   A(1;)  A(2;) B(1;) B(2;) C(1;) C(2;) X(1;) X(2;) E(1;) E(2;)\n") );
        kbase1.addPackages( buildKnowledgePackage("r3", "   A(1;)  A(2;) B(1;) B(2;) C(1;) C(2;) X(1;) X(2;)\n") );
        kbase1.addPackages( buildKnowledgePackage("r4", "   A(1;)  A(2;) B(1;) B(2;) C(1;) C(2;) \n") );

        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase1.newKieSession());
        List list = new ArrayList();
        wm.setGlobal("list", list);

        wm.insert(new E(1));
        wm.insert(new E(2));
        wm.fireAllRules();

        RuleTerminalNode rtn1 = getRtn( "org.kie.r1", kbase1 );
        RuleTerminalNode rtn2 = getRtn( "org.kie.r2", kbase1 );
        RuleTerminalNode rtn3 = getRtn( "org.kie.r3", kbase1 );
        RuleTerminalNode rtn4 = getRtn( "org.kie.r4", kbase1 );

        PathMemory pm1 = wm.getNodeMemory(rtn1);
        SegmentMemory[] smems = pm1.getSegmentMemories();
        assertThat(smems.length).isEqualTo(4);
        assertThat(smems[0]).isNull();
        assertThat(smems[1]).isNull();
        assertThat(smems[3]).isNull();
        SegmentMemory sm = smems[2];
        assertThat(sm.getPos()).isEqualTo(2);
        assertThat(sm.getSegmentPosMaskBit()).isEqualTo(4);
        assertThat(pm1.getLinkedSegmentMask()).isEqualTo(4);

        kbase1.addPackages( buildKnowledgePackage("r5",  "   A(1;)  A(2;) B(1;) B(2;) \n") );

        smems = pm1.getSegmentMemories();
        assertThat(smems.length).isEqualTo(5);
        assertThat(smems[0]).isNull();
        assertThat(smems[1]).isNull();
        assertThat(smems[2]).isNull();

        sm = smems[3];
        assertThat(sm.getPos()).isEqualTo(3);
        assertThat(sm.getSegmentPosMaskBit()).isEqualTo(8);
        assertThat(pm1.getLinkedSegmentMask()).isEqualTo(8);

        RuleTerminalNode rtn5 = getRtn( "org.kie.r5", kbase1 );
        if (PhreakBuilder.isEagerSegmentCreation()) {
            PathMemory pm5 = (PathMemory) wm.getNodeMemories().peekNodeMemory(rtn5.getMemoryId());
            assertThat(pm5).isNull(); // this path is not yet initialised, as there is no inserted data
            assertThat(rtn5.getSegmentPrototypes().length).isEqualTo(2); // make sure it's proto was created successfully
        } else {
            PathMemory pm5 = (PathMemory) wm.getNodeMemories().peekNodeMemory(rtn5.getMemoryId());
            smems = pm5.getSegmentMemories();
            assertThat(smems.length).isEqualTo(2);
            assertThat(smems[0]).isNull();
            assertThat(smems[1]).isNull();
        }
    }


    @Test
    public void testSplitOneBeforeCreatedSegment() throws Exception {
        InternalKnowledgeBase kbase1 =          buildKnowledgeBase("r1", "   A(1;)  A(2;) B(1;) B(2;) C(1;) C(2;) X(1;) X(2;) E(1;) E(2;)\n");
        kbase1.addPackages( buildKnowledgePackage("r2", "   A(1;)  A(2;) B(1;) B(2;) C(1;) C(2;) X(1;) X(2;) E(1;) E(2;)\n") );
        kbase1.addPackages( buildKnowledgePackage("r3", "   A(1;)  A(2;) B(1;) B(2;) C(1;) C(2;) X(1;) X(2;)\n") );
        kbase1.addPackages( buildKnowledgePackage("r4", "   A(1;)  A(2;) B(1;) B(2;) C(1;) C(2;) \n") );

        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase1.newKieSession());
        List list = new ArrayList();
        wm.setGlobal("list", list);

        wm.insert(new X(1));
        wm.insert(new X(2));
        wm.flushPropagations();

        RuleTerminalNode rtn1 = getRtn( "org.kie.r1", kbase1 );
        RuleTerminalNode rtn2 = getRtn( "org.kie.r2", kbase1 );
        RuleTerminalNode rtn3 = getRtn( "org.kie.r3", kbase1 );
        RuleTerminalNode rtn4 = getRtn( "org.kie.r4", kbase1 );

        PathMemory pm1 = wm.getNodeMemory(rtn1);
        SegmentMemory[] smems = pm1.getSegmentMemories();
        assertThat(smems.length).isEqualTo(4);
        assertThat(smems[0]).isNull();
        assertThat(smems[2]).isNull();
        assertThat(smems[3]).isNull();
        SegmentMemory sm = smems[1];
        assertThat(sm.getPos()).isEqualTo(1);
        assertThat(sm.getSegmentPosMaskBit()).isEqualTo(2);
        assertThat(pm1.getLinkedSegmentMask()).isEqualTo(2);

        PathMemory pm3 = wm.getNodeMemory(rtn3);
        SegmentMemory[] smemsP3 = pm3.getSegmentMemories();
        assertThat(smemsP3.length).isEqualTo(3);
        assertThat(smemsP3[0]).isNull();
        assertThat(smemsP3[2]).isNull();
        sm = smems[1];
        assertThat(sm.getPos()).isEqualTo(1);
        assertThat(sm.getSegmentPosMaskBit()).isEqualTo(2);
        assertThat(pm1.getLinkedSegmentMask()).isEqualTo(2);

        kbase1.addPackages( buildKnowledgePackage("r5",  "   A(1;)  A(2;) B(1;) B(2;) \n") );

        smems = pm1.getSegmentMemories();
        assertThat(smems.length).isEqualTo(5);
        assertThat(smems[0]).isNull();
        assertThat(smems[1]).isNull();
        assertThat(smems[3]).isNull();
        assertThat(smems[4]).isNull();
        sm = smems[2];
        assertThat(sm.getPos()).isEqualTo(2);
        assertThat(sm.getSegmentPosMaskBit()).isEqualTo(4);
        assertThat(pm1.getLinkedSegmentMask()).isEqualTo(4);

        smems = pm3.getSegmentMemories();
        assertThat(smems.length).isEqualTo(4);
        assertThat(smems[0]).isNull();
        assertThat(smems[1]).isNull();
        assertThat(smems[3]).isNull();
        sm = smems[2];
        assertThat(sm.getPos()).isEqualTo(2);
        assertThat(sm.getSegmentPosMaskBit()).isEqualTo(4);
        assertThat(pm1.getLinkedSegmentMask()).isEqualTo(4);

        RuleTerminalNode rtn5 = getRtn( "org.kie.r5", kbase1 );
        if (PhreakBuilder.isEagerSegmentCreation()) {
            PathMemory pm5 = (PathMemory) wm.getNodeMemories().peekNodeMemory(rtn5.getMemoryId());
            assertThat(pm5).isNull(); // this path is not yet initialised, as there is no inserted data
            assertThat(rtn5.getSegmentPrototypes().length).isEqualTo(2); // make sure it's proto was created successfully
        } else {
            PathMemory pm5 =  (PathMemory) wm.getNodeMemories().peekNodeMemory(rtn5.getMemoryId());
            smems = pm5.getSegmentMemories();
            assertThat(smems.length).isEqualTo(2);
            assertThat(smems[0]).isNull();
            assertThat(smems[1]).isNull();
        }
    }

    @Test
    public void testSplitOnCreatedSegment() throws Exception {
        // this test splits D1 and D2 on the later add rule
        InternalKnowledgeBase kbase1 =          buildKnowledgeBase("r1", "   A(1;)  A(2;) B(1;) B(2;) C(1;) C(2;) X(1;) X(2;) E(1;) E(2;)\n");
        kbase1.addPackages( buildKnowledgePackage("r2", "   A(1;)  A(2;) B(1;) B(2;) C(1;) C(2;) X(1;) X(2;) E(1;) E(2;)\n") );
        kbase1.addPackages( buildKnowledgePackage("r3", "   A(1;)  A(2;) B(1;) B(2;) C(1;) C(2;) X(1;) X(2;)\n") );
        kbase1.addPackages( buildKnowledgePackage("r4", "   A(1;)  A(2;) B(1;) B(2;) C(1;) C(2;) \n") );

        InternalWorkingMemory wm = ((InternalWorkingMemory)kbase1.newKieSession());
        List list = new ArrayList();
        wm.setGlobal("list", list);

        wm.insert(new X(1));
        wm.insert(new X(2));
        wm.insert(new X(3));
        wm.flushPropagations();

        RuleTerminalNode rtn1 = getRtn( "org.kie.r1", kbase1 );

        PathMemory pm1 = wm.getNodeMemory(rtn1);
        assertThat(pm1.getLinkedSegmentMask()).isEqualTo(2);
        SegmentMemory[] smems = pm1.getSegmentMemories();
        assertThat(smems.length).isEqualTo(4);
        assertThat(smems[0]).isNull();
        assertThat(smems[2]).isNull();
        assertThat(smems[3]).isNull();
        SegmentMemory sm = smems[1];
        assertThat(sm.getPos()).isEqualTo(1);
        assertThat(sm.getSegmentPosMaskBit()).isEqualTo(2);


        kbase1.addPackages( buildKnowledgePackage("r5", "   A(1;)  A(2;) B(1;) B(2;) C(1;) C(2;) X(1;) X(3;)\n") );
        wm.fireAllRules();

        assertThat(pm1.getLinkedSegmentMask()).isEqualTo(6);
        smems = pm1.getSegmentMemories();
        assertThat(smems.length).isEqualTo(5);
        assertThat(smems[0]).isNull();
        assertThat(smems[3]).isNull();
        assertThat(smems[4]).isNull();
        sm = smems[1];
        assertThat(sm.getPos()).isEqualTo(1);
        assertThat(sm.getSegmentPosMaskBit()).isEqualTo(2);

        sm = smems[2];
        assertThat(sm.getPos()).isEqualTo(2);
        assertThat(sm.getSegmentPosMaskBit()).isEqualTo(4);

        RuleTerminalNode rtn5 = getRtn( "org.kie.r5", kbase1 );
        PathMemory pm5 = wm.getNodeMemory(rtn5);
        assertThat(pm5.getLinkedSegmentMask()).isEqualTo(6);

        smems = pm5.getSegmentMemories();
        assertThat(smems.length).isEqualTo(3);
        assertThat(smems[0]).isNull();
        sm = smems[1];
        assertThat(sm.getPos()).isEqualTo(1);
        assertThat(sm.getSegmentPosMaskBit()).isEqualTo(2);

        sm = smems[2];
        assertThat(sm.getPos()).isEqualTo(2);
        assertThat(sm.getSegmentPosMaskBit()).isEqualTo(4);
    }


    private RuleTerminalNode getRtn(String ruleName, KieBase kbase) {
        return ( RuleTerminalNode ) ((InternalRuleBase) kbase).getReteooBuilder().getTerminalNodes(ruleName)[0];
    }

    private InternalKnowledgeBase buildKnowledgeBase(String ruleName, String... rule) {
        return (InternalKnowledgeBase)KieBaseUtil.getKieBaseFromKieModuleFromDrl("test", kieBaseTestConfiguration, buildKnowledgePackageDrl(ruleName, rule));
    }

    private String buildKnowledgePackageDrl(String ruleName, String... rule) {
        String str = "";
        str += "package org.kie \n";
        str += "import " + A.class.getCanonicalName() + "\n";
        str += "import " + B.class.getCanonicalName() + "\n";
        str += "import " + C.class.getCanonicalName() + "\n";
        str += "import " + E.class.getCanonicalName() + "\n";
        str += "import " + F.class.getCanonicalName() + "\n";
        str += "import " + X.class.getCanonicalName() + "\n";
        str += "import " + Y.class.getCanonicalName() + "\n";
        str += "global java.util.List list \n";

        for (int i = 0; i < rule.length; i++) {
            str += "rule " + (rule.length == 1 ? ruleName : ruleName + i )+ "  when \n";
            str += rule[i];
            str += "then \n";
            str += " list.add( kcontext.getMatch() );\n";
            str += "end \n";
        }

        return str;
    }

    private Collection<KiePackage> buildKnowledgePackage(String ruleName, String rule) {
        return KieBaseUtil.getKieBaseFromKieModuleFromDrl("tmp", kieBaseTestConfiguration, buildKnowledgePackageDrl(ruleName, rule)).getKiePackages();
    }

    public ObjectTypeNode getObjectTypeNode(KieBase kbase, Class<?> nodeClass) {
        return getObjectTypeNode(((InternalRuleBase) kbase).getRete(), nodeClass);
    }

    public ObjectTypeNode getObjectTypeNode(Rete rete, Class<?> nodeClass) {
        List<ObjectTypeNode> nodes = rete.getObjectTypeNodes();
        for (ObjectTypeNode n : nodes) {
            if (((ClassObjectType) n.getObjectType()).getClassType() == nodeClass) {
                return n;
            }
        }
        return null;
    }
}
