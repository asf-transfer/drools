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
package org.drools.kiesession;

import java.util.Arrays;
import java.util.Collections;

import org.drools.base.base.ClassObjectType;
import org.drools.core.common.DefaultFactHandle;
import org.drools.core.common.EmptyBetaConstraints;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.PhreakPropagationContextFactory;
import org.drools.core.common.PropagationContextFactory;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.core.phreak.BuildtimeSegmentUtilities;
import org.drools.core.reteoo.BetaMemory;
import org.drools.core.reteoo.RightInputAdapterNode;
import org.drools.core.reteoo.ExistsRight;
import org.drools.core.reteoo.JoinRightAdapterNode;
import org.drools.core.reteoo.NotRight;
import org.drools.core.reteoo.PathEndNode;
import org.drools.core.reteoo.SegmentMemory.SegmentPrototype;
import org.drools.core.reteoo.TerminalNode;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.core.phreak.PhreakNotNode;
import org.drools.core.phreak.RuntimeSegmentUtilities;
import org.drools.core.reteoo.BetaNode;
import org.drools.core.reteoo.ExistsNode;
import org.drools.core.reteoo.JoinNode;
import org.drools.core.reteoo.LeftInputAdapterNode;
import org.drools.core.reteoo.LeftInputAdapterNode.LiaNodeMemory;
import org.drools.core.reteoo.LeftTupleSource;
import org.drools.core.reteoo.MockObjectSource;
import org.drools.core.reteoo.MockTupleSource;
import org.drools.core.reteoo.NotNode;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.RuleTerminalNode;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.core.common.PropagationContext;
import org.drools.kiesession.rulebase.KnowledgeBaseFactory;
import org.drools.kiesession.session.StatefulKnowledgeSessionImpl;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NodeSegmentUnlinkingTest {
    InternalKnowledgeBase kBase;
    BuildContext         buildContext;
    PropagationContext   context;

    LeftInputAdapterNode liaNode;
    BetaNode n1;
    BetaNode             n2;
    BetaNode             n3;
    BetaNode             n4;
    BetaNode             n5;
    BetaNode             n6;
    BetaNode             n7;
    BetaNode             n8;
    RuleImpl             rule1;
    RuleImpl             rule2;
    RuleImpl             rule3;
    RuleImpl             rule4;
    RuleImpl             rule5;

    static final int     JOIN_NODE   = 0;
    static final int     EXISTS_NODE = 1;
    static final int     NOT_NODE    = 2;

    private BetaNode createBetaNode(int id,
                                    int type,
                                    LeftTupleSource leftTupleSource) {
        MockObjectSource mockObjectSource = new MockObjectSource( 8 );

        BetaNode betaNode = null;
        switch ( type ) {
            case JOIN_NODE : {
                RightInputAdapterNode right = new JoinRightAdapterNode(9, mockObjectSource, buildContext);
                betaNode = new JoinNode( id, leftTupleSource, right, new EmptyBetaConstraints(), buildContext );
                break;
            }
            case EXISTS_NODE : {
                RightInputAdapterNode right = new ExistsRight(9, mockObjectSource, buildContext);
                betaNode = new ExistsNode( id, leftTupleSource, right, new EmptyBetaConstraints(), buildContext );
                break;
            }
            case NOT_NODE : {
                RightInputAdapterNode right = new NotRight(9, mockObjectSource, buildContext);
                betaNode = new NotNode( id, leftTupleSource, right, new EmptyBetaConstraints(), buildContext );
                break;
            }
        }

        mockObjectSource.attach(buildContext);
        betaNode.attach(buildContext);

        return betaNode;
    }

    public void  setUp(int type) {
        setUp(type, type, type, type, type, type, type, type);
    }
    
    public void setUp(int... type) {
        kBase = KnowledgeBaseFactory.newKnowledgeBase();
        buildContext = new BuildContext( kBase, Collections.emptyList() );

        PropagationContextFactory pctxFactory = new PhreakPropagationContextFactory();
        context = pctxFactory.createPropagationContext(0, PropagationContext.Type.INSERTION, null, null, null);

        MockTupleSource mockTupleSource = new MockTupleSource(9, buildContext);

        rule1 = new RuleImpl( "rule1" );
        rule2 = new RuleImpl( "rule2" );
        rule3 = new RuleImpl( "rule3" );
        
        ObjectTypeNode otn = new ObjectTypeNode( 3, null, new ClassObjectType( String.class ), buildContext );
        liaNode = new LeftInputAdapterNode(4, otn, buildContext );
        
        // 3, 4, 5, 6 are in same shared segment
        n1 = createBetaNode( 10, type[0], liaNode );
        n2 = createBetaNode( 11, type[1], n1 );
        RuleTerminalNode rtn1 = new RuleTerminalNode( 18,
                                                      n2,
                                                      rule1,
                                                      rule1.getLhs(),
                                                      0,
                                                      buildContext );
        rtn1.attach(buildContext);
        
        
        n3 = createBetaNode( 12, type[2], n1 );
        n4 = createBetaNode( 13, type[3], n3 );
        n5 = createBetaNode( 14, type[4], n4 );
        n6 = createBetaNode( 15, type[5], n5 ); 
        RuleTerminalNode rtn2 = new RuleTerminalNode( 19,
                                                      n6,
                                                      rule2,
                                                      rule2.getLhs(),
                                                      0,
                                                      buildContext );
        rtn2.attach(buildContext);

        n7 = createBetaNode( 16, type[6], n6 );
        n8 = createBetaNode( 17, type[7], n7 );
        RuleTerminalNode rtn3 = new RuleTerminalNode( 20,
                                                      n8,
                                                      rule3,
                                                      rule3.getLhs(),
                                                      0,
                                                      buildContext );
        rtn3.attach(buildContext);
        
        // n1 -> n2 -> r1
        //  \ 
        //   n3 -> n4 -> n5 -> n6 -> r2
        //                      \
        //                      n7 -> n8 -> r3          
        
        n1.addAssociation(rule1, null);
        n1.addAssociation(rule2, null);
        n1.addAssociation(rule3, null);
        n2.addAssociation(rule1, null);
        n2.addAssociation(rule2, null);
        n2.addAssociation(rule3, null);

        n3.addAssociation(rule2, null);
        n3.addAssociation(rule3, null);

        n4.addAssociation(rule2, null);
        n4.addAssociation(rule3, null);
        n5.addAssociation(rule2, null);
        n5.addAssociation(rule3, null);
        n6.addAssociation(rule2, null);
        n6.addAssociation(rule3, null);

        n7.addAssociation(rule3, null);
        n8.addAssociation(rule3, null);

        // assumes no subnetworks
        for (TerminalNode tn : new TerminalNode[] {rtn1, rtn2, rtn3}) {
            tn.setPathEndNodes( new PathEndNode[] {tn});
            tn.resetPathMemSpec(null);
            BuildtimeSegmentUtilities.createPathProtoMemories(tn, null, kBase);
        }
    }

    @Test
    public void testSingleNodeinSegment() {

        rule1 = new RuleImpl( "rule1" );
        rule2 = new RuleImpl( "rule2" );
        rule3 = new RuleImpl( "rule3" );

        kBase = KnowledgeBaseFactory.newKnowledgeBase();
        BuildContext buildContext = new BuildContext( kBase, Collections.emptyList() );

        MockObjectSource mockObjectSource = new MockObjectSource( 8 );
        MockTupleSource mockTupleSource = new MockTupleSource(9, buildContext);

        RightInputAdapterNode right1 = new JoinRightAdapterNode(10, mockObjectSource, buildContext);
        RightInputAdapterNode right2 = new JoinRightAdapterNode(11, mockObjectSource, buildContext);
        RightInputAdapterNode right3 = new JoinRightAdapterNode(12, mockObjectSource, buildContext);
        RightInputAdapterNode right4 = new JoinRightAdapterNode(12, mockObjectSource, buildContext);
        RightInputAdapterNode right5 = new JoinRightAdapterNode(12, mockObjectSource, buildContext);

        // n2 is only node in it's segment
        ObjectTypeNode otn = new ObjectTypeNode( 2, null, new ClassObjectType( String.class ), buildContext );
        BetaNode n1 = new JoinNode( 10, new LeftInputAdapterNode(3, otn, buildContext ), right1,
                                    new EmptyBetaConstraints(), buildContext );
        BetaNode n2 = new JoinNode( 11, n1, right2,
                                    new EmptyBetaConstraints(), buildContext );
        BetaNode n3 = new JoinNode( 12, n1, right3,
                                    new EmptyBetaConstraints(), buildContext );
        BetaNode n4 = new JoinNode( 13, n2, right4,
                                    new EmptyBetaConstraints(), buildContext );
        BetaNode n5 = new JoinNode( 14, n2, right5,
                                    new EmptyBetaConstraints(), buildContext );

        n1.addAssociation(rule1, null);
        n1.addAssociation(rule2, null);
        n1.addAssociation(rule3, null);

        n2.addAssociation(rule2, null);
        n2.addAssociation(rule3, null);

        n3.addAssociation(rule1, null);
        n4.addAssociation(rule2, null);
        n5.addAssociation(rule3, null);

        mockObjectSource.attach(buildContext);
        mockTupleSource.attach(buildContext);
        n1.attach(buildContext);
        n2.attach(buildContext);
        n3.attach(buildContext);
        n4.attach(buildContext);
        n5.attach(buildContext);

        StatefulKnowledgeSessionImpl ksession = (StatefulKnowledgeSessionImpl)kBase.newKieSession();
        SegmentPrototype[] protos = BuildtimeSegmentUtilities.createLeftTupleNodeProtoMemories(n3, null, kBase);
        Arrays.stream(protos).forEach( p -> p.setPathEndNodes( new PathEndNode[0]));

        protos = BuildtimeSegmentUtilities.createLeftTupleNodeProtoMemories(n4, null, kBase);
        Arrays.stream(protos).forEach( p -> p.setPathEndNodes( new PathEndNode[0]));

        protos = BuildtimeSegmentUtilities.createLeftTupleNodeProtoMemories(n5, null, kBase);
        Arrays.stream(protos).forEach( p -> p.setPathEndNodes( new PathEndNode[0]));
        createSegmentMemory( n2, ksession );

        BetaMemory bm = (BetaMemory) ksession.getNodeMemory(n1);
        assertThat(bm.getSegmentMemory()).isNull();

        bm = (BetaMemory) ksession.getNodeMemory(n3);
        assertThat(bm.getSegmentMemory()).isNull();

        bm = (BetaMemory) ksession.getNodeMemory(n4);
        assertThat(bm.getSegmentMemory()).isNull();

        bm = (BetaMemory) ksession.getNodeMemory(n2);
        assertThat(bm.getNodePosMaskBit()).isEqualTo(1);
        assertThat(bm.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(1);
    }
    
    @Test
    public void testLiaNodeInitialisation() {
        setUp( JOIN_NODE );
        // Initialise from lian

        StatefulKnowledgeSessionImpl ksession = (StatefulKnowledgeSessionImpl)kBase.newKieSession();

        RuntimeSegmentUtilities.getOrCreateSegmentMemory(liaNode, ksession);
        liaNode.assertObject((InternalFactHandle) ksession.insert("str"), context, ksession);
        

        LiaNodeMemory liaMem = ksession.getNodeMemory(liaNode);
        assertThat(liaMem.getNodePosMaskBit()).isEqualTo(1);
        assertThat(liaMem.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(3); 
        
        BetaMemory bm1 = (BetaMemory) ksession.getNodeMemory(n1);
        assertThat(bm1.getNodePosMaskBit()).isEqualTo(2);
        assertThat(bm1.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(3);         
        
        // Initialise from n1
        ksession = (StatefulKnowledgeSessionImpl)kBase.newKieSession();

        n1.getRightInput().assertObject( (InternalFactHandle) ksession.insert( "str" ), context, ksession );
        

        liaMem = ksession.getNodeMemory(liaNode);
        assertThat(liaMem.getNodePosMaskBit()).isEqualTo(1);
        assertThat(liaMem.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(3); 
        
        bm1 = (BetaMemory) ksession.getNodeMemory(n1);
        assertThat(bm1.getNodePosMaskBit()).isEqualTo(2);
        assertThat(bm1.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(3);           
    }
    
    @Test
    public void testLiaNodeLinking() {
        setUp( JOIN_NODE );
        // Initialise from lian
        StatefulKnowledgeSessionImpl ksession = (StatefulKnowledgeSessionImpl)kBase.newKieSession();

        RuntimeSegmentUtilities.getOrCreateSegmentMemory(liaNode, ksession);
        
        InternalFactHandle fh1 = (InternalFactHandle) ksession.insert( "str1" );
        n1.getRightInput().assertObject( fh1, context, ksession );
        
        LiaNodeMemory liaMem = ksession.getNodeMemory(liaNode);
        assertThat(liaMem.getNodePosMaskBit()).isEqualTo(1);
        assertThat(liaMem.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(3); 
        
        BetaMemory bm1 = (BetaMemory) ksession.getNodeMemory(n1);
        assertThat(bm1.getNodePosMaskBit()).isEqualTo(2);
        assertThat(bm1.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(3);

        // still unlinked
        assertThat(liaMem.getSegmentMemory().isSegmentLinked()).isFalse();
        
        // now linked
        InternalFactHandle fh2 = (InternalFactHandle) ksession.insert( "str2" );
        liaNode.assertObject( fh2, context, ksession );
        assertThat(liaMem.getSegmentMemory().isSegmentLinked()).isTrue();
        
        // test unlink after one retract
        liaNode.retractLeftTuple( fh2.getFirstLeftTuple(), context, ksession );
        assertThat(liaMem.getSegmentMemory().isSegmentLinked()).isFalse();
        
        // check counter, after multiple asserts
        InternalFactHandle fh3 = (InternalFactHandle) ksession.insert( "str3" );
        InternalFactHandle fh4 = (InternalFactHandle) ksession.insert( "str4" );
        liaNode.assertObject( fh3, context, ksession );
        liaNode.assertObject( fh4, context, ksession );

        assertThat(liaMem.getSegmentMemory().isSegmentLinked()).isTrue();
        
        liaNode.retractLeftTuple( fh3.getFirstLeftTuple(), context, ksession );
        assertThat(liaMem.getSegmentMemory().isSegmentLinked()).isTrue();

        liaNode.retractLeftTuple( fh4.getFirstLeftTuple(), context, ksession );
        assertThat(liaMem.getSegmentMemory().isSegmentLinked()).isFalse();
    }

    @Test
    public void tesMultiNodeSegmentDifferentInitialisationPoints() {
        setUp( JOIN_NODE );
        // Initialise from n3
        StatefulKnowledgeSessionImpl ksession = (StatefulKnowledgeSessionImpl)kBase.newKieSession();

        createSegmentMemory(n3, ksession);

        BetaMemory bm = (BetaMemory) ksession.getNodeMemory(n1);
        assertThat(bm.getSegmentMemory()).isNull();

        bm = (BetaMemory) ksession.getNodeMemory(n3);
        assertThat(bm.getNodePosMaskBit()).isEqualTo(1);
        assertThat(bm.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(15);

        bm = (BetaMemory) ksession.getNodeMemory(n4);
        assertThat(bm.getNodePosMaskBit()).isEqualTo(2);
        assertThat(bm.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(15);

        bm = (BetaMemory) ksession.getNodeMemory(n5);
        assertThat(bm.getNodePosMaskBit()).isEqualTo(4);
        assertThat(bm.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(15);

        bm = (BetaMemory) ksession.getNodeMemory(n6);
        assertThat(bm.getNodePosMaskBit()).isEqualTo(8);
        assertThat(bm.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(15);

        // Initialise from n4
        ksession = (StatefulKnowledgeSessionImpl)kBase.newKieSession();

        bm = createSegmentMemory( n4, ksession );

        bm = (BetaMemory) ksession.getNodeMemory(n1);
        assertThat(bm.getSegmentMemory()).isNull();

        bm = (BetaMemory) ksession.getNodeMemory(n3);
        assertThat(bm.getNodePosMaskBit()).isEqualTo(1);
        assertThat(bm.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(15);

        bm = (BetaMemory) ksession.getNodeMemory(n4);
        assertThat(bm.getNodePosMaskBit()).isEqualTo(2);
        assertThat(bm.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(15);

        bm = (BetaMemory) ksession.getNodeMemory(n5);
        assertThat(bm.getNodePosMaskBit()).isEqualTo(4);
        assertThat(bm.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(15);

        bm = (BetaMemory) ksession.getNodeMemory(n6);
        assertThat(bm.getNodePosMaskBit()).isEqualTo(8);
        assertThat(bm.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(15);

        // Initialise from n5
        ksession = (StatefulKnowledgeSessionImpl)kBase.newKieSession();

        createSegmentMemory( n5, ksession );

        bm = (BetaMemory) ksession.getNodeMemory(n1);
        assertThat(bm.getSegmentMemory()).isNull();

        bm = (BetaMemory) ksession.getNodeMemory(n3);
        assertThat(bm.getNodePosMaskBit()).isEqualTo(1);
        assertThat(bm.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(15);

        bm = (BetaMemory) ksession.getNodeMemory(n4);
        assertThat(bm.getNodePosMaskBit()).isEqualTo(2);
        assertThat(bm.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(15);

        bm = (BetaMemory) ksession.getNodeMemory(n5);
        assertThat(bm.getNodePosMaskBit()).isEqualTo(4);
        assertThat(bm.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(15);

        bm = (BetaMemory) ksession.getNodeMemory(n6);
        assertThat(bm.getNodePosMaskBit()).isEqualTo(8);
        assertThat(bm.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(15);

        // Initialise from n6
        ksession = (StatefulKnowledgeSessionImpl)kBase.newKieSession();

        createSegmentMemory( n6, ksession );

        bm = (BetaMemory) ksession.getNodeMemory(n1);
        assertThat(bm.getSegmentMemory()).isNull();

        bm = (BetaMemory) ksession.getNodeMemory(n3);
        assertThat(bm.getNodePosMaskBit()).isEqualTo(1);
        assertThat(bm.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(15);

        bm = (BetaMemory) ksession.getNodeMemory(n4);
        assertThat(bm.getNodePosMaskBit()).isEqualTo(2);
        assertThat(bm.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(15);

        bm = (BetaMemory) ksession.getNodeMemory(n5);
        assertThat(bm.getNodePosMaskBit()).isEqualTo(4);
        assertThat(bm.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(15);

        bm = (BetaMemory) ksession.getNodeMemory(n6);
        assertThat(bm.getNodePosMaskBit()).isEqualTo(8);
        assertThat(bm.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(15);
    }

    @Test
    public void testAllLinkedInWithJoinNodesOnly() {
        setUp( JOIN_NODE );

        assertThat(n3.getClass()).isEqualTo(JoinNode.class); // make sure it created JoinNodes

        StatefulKnowledgeSessionImpl ksession = (StatefulKnowledgeSessionImpl)kBase.newKieSession();

        DefaultFactHandle f1 = (DefaultFactHandle) ksession.insert( "test1" );
        n3.getRightInput().assertObject( f1, context, ksession );

        BetaMemory bm = (BetaMemory) ksession.getNodeMemory(n3);
        assertThat(bm.getSegmentMemory().isSegmentLinked()).isFalse();

        n4.getRightInput().assertObject( f1, context, ksession );
        assertThat(bm.getSegmentMemory().isSegmentLinked()).isFalse();

        n5.getRightInput().assertObject( f1, context, ksession );
        assertThat(bm.getSegmentMemory().isSegmentLinked()).isFalse();

        n6.getRightInput().assertObject( f1, context, ksession );
        assertThat(bm.getSegmentMemory().isSegmentLinked()).isTrue(); // only after all 4 nodes are populated, is the segment linked in
    }

    @Test
    public void testAllLinkedInWithExistsNodesOnly() {
        setUp( EXISTS_NODE );

        assertThat(n3.getClass()).isEqualTo(ExistsNode.class); // make sure it created ExistsNodes

        StatefulKnowledgeSessionImpl ksession = (StatefulKnowledgeSessionImpl)kBase.newKieSession();

        DefaultFactHandle f1 = (DefaultFactHandle) ksession.insert( "test1" );
        n3.getRightInput().assertObject( f1, context, ksession );

        BetaMemory bm = (BetaMemory) ksession.getNodeMemory(n3);
        assertThat(bm.getSegmentMemory().isSegmentLinked()).isFalse();

        n4.getRightInput().assertObject( f1, context, ksession );
        assertThat(bm.getSegmentMemory().isSegmentLinked()).isFalse();

        n5.getRightInput().assertObject( f1, context, ksession );
        assertThat(bm.getSegmentMemory().isSegmentLinked()).isFalse();

        n6.getRightInput().assertObject( f1, context, ksession );
        assertThat(bm.getSegmentMemory().isSegmentLinked()).isTrue(); // only after all 4 nodes are populated, is the segment linked in
    }

    private static BetaMemory createSegmentMemory(BetaNode node,
                                                  InternalWorkingMemory wm) {
        BetaMemory betaMemory = (BetaMemory) wm.getNodeMemory(node);
        if ( betaMemory.getSegmentMemory() == null ) {
            RuntimeSegmentUtilities.getOrCreateSegmentMemory(node, wm);
        }
        return betaMemory;

    }

    @Test
    public void testAllLinkedInWithNotNodesOnly() {
        setUp( NOT_NODE );

        assertThat(n3.getClass()).isEqualTo(NotNode.class); // make sure it created NotNodes

        StatefulKnowledgeSessionImpl ksession = (StatefulKnowledgeSessionImpl)kBase.newKieSession();

        BetaMemory bm = (BetaMemory) ksession.getNodeMemory(n3);
        createSegmentMemory( n3, ksession );
        assertThat(bm.getSegmentMemory().isSegmentLinked()).isTrue(); // not nodes start off linked

        DefaultFactHandle f1 = (DefaultFactHandle) ksession.insert( "test1" ); // unlinked after first assertion
        n3.getRightInput().assertObject( f1, context, ksession );
                
        // this doesn't unlink on the assertObject, as the node's memory must be processed. So use the helper method the main network evaluator uses.
        PhreakNotNode.unlinkNotNodeOnRightInsert( (NotNode) n3, bm, ksession );
        assertThat(bm.getSegmentMemory().isSegmentLinked()).isFalse();                

        n3.getRightInput().retractRightTuple( f1.getFirstRightTuple(), context, ksession );
        assertThat(bm.getSegmentMemory().isSegmentLinked()).isTrue(); 
                //assertFalse( bm.getSegmentMemory().isSigmentLinked() ); // check retraction unlinks again         
    }

}
