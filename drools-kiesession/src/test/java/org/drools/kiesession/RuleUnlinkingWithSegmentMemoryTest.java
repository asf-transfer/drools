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

import java.util.Collections;
import java.util.List;

import org.drools.base.base.ClassObjectType;
import org.drools.core.common.DefaultFactHandle;
import org.drools.core.common.EmptyBetaConstraints;
import org.drools.base.common.NetworkNode;
import org.drools.core.common.PhreakPropagationContextFactory;
import org.drools.core.common.PropagationContextFactory;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.core.impl.RuleBaseFactory;
import org.drools.core.phreak.RuntimeSegmentUtilities;
import org.drools.core.phreak.BuildtimeSegmentUtilities;
import org.drools.core.reteoo.BetaMemory;
import org.drools.core.reteoo.BetaNode;
import org.drools.core.reteoo.RightInputAdapterNode;
import org.drools.core.reteoo.ExistsNode;
import org.drools.core.reteoo.ExistsRight;
import org.drools.core.reteoo.JoinNode;
import org.drools.core.reteoo.JoinRightAdapterNode;
import org.drools.core.reteoo.LeftInputAdapterNode;
import org.drools.core.reteoo.LeftTupleSink;
import org.drools.core.reteoo.LeftTupleSource;
import org.drools.core.reteoo.MockObjectSource;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.core.reteoo.NotNode;
import org.drools.core.reteoo.NotRight;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.PathEndNode;
import org.drools.core.reteoo.PathMemory;
import org.drools.core.reteoo.RuleTerminalNode;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.base.rule.GroupElement;
import org.drools.base.rule.GroupElement.Type;
import org.drools.core.common.PropagationContext;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.kiesession.rulebase.KnowledgeBaseFactory;
import org.drools.kiesession.session.StatefulKnowledgeSessionImpl;
import org.junit.Test;
import org.kie.api.KieBaseConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

public class RuleUnlinkingWithSegmentMemoryTest {
    InternalKnowledgeBase kBase;
    BuildContext       buildContext;
    PropagationContext context;

    LeftInputAdapterNode lian;
    BetaNode n1;
    BetaNode           n2;
    BetaNode           n3;
    BetaNode           n4;
    BetaNode           n5;
    BetaNode           n6;
    BetaNode           n7;
    BetaNode           n8;
    BetaNode           n9;
    BetaNode           n10;
    
    RuleTerminalNode rtn1;
    RuleTerminalNode   rtn2;
    RuleTerminalNode   rtn3;

    RuleImpl           rule1;
    RuleImpl           rule2;
    RuleImpl           rule3;

    static final int   JOIN_NODE             = 0;
    static final int   EXISTS_NODE           = 1;
    static final int   NOT_NODE              = 2;
    static final int   RULE_TERMINAL_NODE    = 3;    
    
    private NetworkNode createNetworkNode(int id,
                                          int type,
                                          LeftTupleSource leftTupleSource,
                                          RuleImpl rule) {
        MockObjectSource mockObjectSource = new MockObjectSource( 8 );

        LeftTupleSink networkNode = null;
        switch ( type ) {
            case JOIN_NODE : {
                RightInputAdapterNode right = new JoinRightAdapterNode(9, mockObjectSource, buildContext);
                networkNode = new JoinNode( id, leftTupleSource, right, new EmptyBetaConstraints(), buildContext );
                break;
            }
            case EXISTS_NODE : {
                RightInputAdapterNode right = new ExistsRight(9, mockObjectSource, buildContext);
                networkNode = new ExistsNode( id, leftTupleSource, right, new EmptyBetaConstraints(), buildContext );
                break;
            }
            case NOT_NODE : {
                RightInputAdapterNode right = new NotRight(9, mockObjectSource, buildContext);
                networkNode = new NotNode( id, leftTupleSource, right, new EmptyBetaConstraints(), buildContext );
                break;
            }   
            case RULE_TERMINAL_NODE : {
                networkNode = new RuleTerminalNode( id, leftTupleSource, rule, new GroupElement( Type.AND ), 0, buildContext);
                break;
            }             
        }

        mockObjectSource.attach();
        if ( NodeTypeEnums.isLeftTupleSource( networkNode ) ) {
            ((LeftTupleSource)networkNode).attach(buildContext);
        } else {
            ((RuleTerminalNode)networkNode).attach(buildContext);
        }

        return networkNode;
    }
    
    public void setUp(int type) {
        KieBaseConfiguration kconf = RuleBaseFactory.newKnowledgeBaseConfiguration();
        kBase = KnowledgeBaseFactory.newKnowledgeBase(RuleBaseFactory.newRuleBase(kconf));

        buildContext = new BuildContext( kBase, Collections.emptyList() );

        PropagationContextFactory pctxFactory = new PhreakPropagationContextFactory();
        context = pctxFactory.createPropagationContext(0, PropagationContext.Type.INSERTION, null, null, null);

        ObjectTypeNode otn = new ObjectTypeNode( 4, null, new ClassObjectType( String.class ), buildContext );
        lian = new LeftInputAdapterNode(5, otn, buildContext );

        n1 = (BetaNode) createNetworkNode( 10, type, lian, null );
        n2 = (BetaNode) createNetworkNode( 11, type, n1, null );
        n3 = (BetaNode) createNetworkNode( 12, type, n2, null );
        rule1 =  new RuleImpl("rule1");
        rule1.setActivationListener( "agenda" );
        rtn1 = ( RuleTerminalNode ) createNetworkNode( 18, RULE_TERMINAL_NODE, n3, rule1);
        
      
        n4 = (BetaNode) createNetworkNode( 13, type, n3, null );
        n5 = (BetaNode) createNetworkNode( 14, type, n4, null );
        rule2 =  new RuleImpl("rule2");
        rule2.setActivationListener( "agenda" );          
        rtn2 = ( RuleTerminalNode ) createNetworkNode( 19, RULE_TERMINAL_NODE, n5, rule2 );        
        

        n6 = (BetaNode) createNetworkNode( 15, type, n5, null );
        n7 = (BetaNode) createNetworkNode( 16, type, n6, null );
        n8 = (BetaNode) createNetworkNode( 17, type, n7, null );
        rule3 =  new RuleImpl("rule3");
        rule3.setActivationListener( "agenda" ); 
        rtn3 = ( RuleTerminalNode ) createNetworkNode( 20, RULE_TERMINAL_NODE, n8, rule3 );

        lian.addAssociation(rule1, null);
        lian.addAssociation(rule2, null);
        lian.addAssociation(rule3, null);
        n1.addAssociation(rule1, null);
        n1.addAssociation(rule2, null);
        n1.addAssociation(rule3, null);
        n2.addAssociation(rule1, null);
        n2.addAssociation(rule2, null);
        n2.addAssociation(rule3, null);
        n3.addAssociation(rule1, null);
        n3.addAssociation(rule2, null);
        n3.addAssociation(rule3, null);

        n4.addAssociation(rule2, null);
        n4.addAssociation(rule3, null);
        n5.addAssociation(rule2, null);
        n5.addAssociation(rule3, null);
        
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
    public void testRuleSegmentsAllLinkedTestMasks() {
        setUp( JOIN_NODE );

        InternalKnowledgeBase kBase = KnowledgeBaseFactory.newKnowledgeBase();
        StatefulKnowledgeSessionImpl wm = new StatefulKnowledgeSessionImpl( 1L, kBase );
        
        PathMemory rs = wm.getNodeMemory( rtn1 );
        assertThat(rs.isRuleLinked()).isFalse();
        assertThat(rs.getAllLinkedMaskTest()).isEqualTo(1);
        
        rs = wm.getNodeMemory( rtn2 );
        assertThat(rs.isRuleLinked()).isFalse();
        assertThat(rs.getAllLinkedMaskTest()).isEqualTo(3);
        
        rs = wm.getNodeMemory( rtn3 );
        assertThat(rs.isRuleLinked()).isFalse();
        assertThat(rs.getAllLinkedMaskTest()).isEqualTo(7);
    }   
    
    
    @Test
    public void testSegmentNodeReferencesToSegments() {
        setUp( JOIN_NODE );

        StatefulKnowledgeSessionImpl wm = new StatefulKnowledgeSessionImpl( 1L, kBase );

        BetaMemory bm = null;
        List<PathMemory> list;
        
        PathMemory rtn1Rs = wm.getNodeMemory( rtn1 );
        PathMemory rtn2Rs = wm.getNodeMemory( rtn2 );
        PathMemory rtn3Rs = wm.getNodeMemory( rtn3 );

        // lian
        RuntimeSegmentUtilities.getOrCreateSegmentMemory(lian, wm);
        LeftInputAdapterNode.LiaNodeMemory lmem = wm.getNodeMemory( lian );
        assertThat(lmem.getNodePosMaskBit()).isEqualTo(1);

        // n1
        RuntimeSegmentUtilities.getOrCreateSegmentMemory(n1, wm);
        bm = (BetaMemory) wm.getNodeMemory(n1);
        assertThat(bm.getNodePosMaskBit()).isEqualTo(2);
        assertThat(bm.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(15);
        assertThat(bm.getSegmentMemory().getSegmentPosMaskBit()).isEqualTo(1);        
        list = bm.getSegmentMemory().getPathMemories();
        assertThat(list.size()).isEqualTo(3);
        assertThat(list.contains(rtn1Rs)).isTrue();
        assertThat(list.contains(rtn2Rs)).isTrue();
        assertThat(list.contains(rtn3Rs)).isTrue();           
        
        // n2
        bm = (BetaMemory) wm.getNodeMemory(n2);
        assertThat(bm.getNodePosMaskBit()).isEqualTo(4);
        assertThat(bm.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(15);
        assertThat(bm.getSegmentMemory().getSegmentPosMaskBit()).isEqualTo(1);        
        list = bm.getSegmentMemory().getPathMemories();
        assertThat(list.size()).isEqualTo(3);
        assertThat(list.contains(rtn1Rs)).isTrue();
        assertThat(list.contains(rtn2Rs)).isTrue();
        assertThat(list.contains(rtn3Rs)).isTrue();           
       
        // n3
        bm = (BetaMemory) wm.getNodeMemory(n3);
        assertThat(bm.getNodePosMaskBit()).isEqualTo(8);
        assertThat(bm.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(15);
        assertThat(bm.getSegmentMemory().getSegmentPosMaskBit()).isEqualTo(1);        
        list = bm.getSegmentMemory().getPathMemories();
        assertThat(list.size()).isEqualTo(3);
        assertThat(list.contains(rtn1Rs)).isTrue();
        assertThat(list.contains(rtn2Rs)).isTrue();
        assertThat(list.contains(rtn3Rs)).isTrue();           
        
        // n4
        RuntimeSegmentUtilities.getOrCreateSegmentMemory(n4, wm);
        bm = (BetaMemory) wm.getNodeMemory(n4);
        assertThat(bm.getNodePosMaskBit()).isEqualTo(1);
        assertThat(bm.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(3);
        assertThat(bm.getSegmentMemory().getSegmentPosMaskBit()).isEqualTo(2);        
        list = bm.getSegmentMemory().getPathMemories();
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.contains(rtn2Rs)).isTrue();
        assertThat(list.contains(rtn3Rs)).isTrue();        
        
        // n5
        bm = (BetaMemory) wm.getNodeMemory(n5);
        assertThat(bm.getNodePosMaskBit()).isEqualTo(2);
        assertThat(bm.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(3);
        assertThat(bm.getSegmentMemory().getSegmentPosMaskBit()).isEqualTo(2);        
        list = bm.getSegmentMemory().getPathMemories();
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.contains(rtn2Rs)).isTrue();
        assertThat(list.contains(rtn3Rs)).isTrue();
        
        // n6
        RuntimeSegmentUtilities.getOrCreateSegmentMemory(n6, wm);
        bm = (BetaMemory) wm.getNodeMemory(n6);
        assertThat(bm.getNodePosMaskBit()).isEqualTo(1);
        assertThat(bm.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(7);
        assertThat(bm.getSegmentMemory().getSegmentPosMaskBit()).isEqualTo(4);        
        list = bm.getSegmentMemory().getPathMemories();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.contains(rtn3Rs)).isTrue();    
        
        // n7
        bm = (BetaMemory) wm.getNodeMemory(n7);
        assertThat(bm.getNodePosMaskBit()).isEqualTo(2);
        assertThat(bm.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(7);
        assertThat(bm.getSegmentMemory().getSegmentPosMaskBit()).isEqualTo(4);        
        list = bm.getSegmentMemory().getPathMemories();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.contains(rtn3Rs)).isTrue();    
        
        // n8
        bm = (BetaMemory) wm.getNodeMemory(n8);
        assertThat(bm.getNodePosMaskBit()).isEqualTo(4);
        assertThat(bm.getSegmentMemory().getAllLinkedMaskTest()).isEqualTo(7);
        assertThat(bm.getSegmentMemory().getSegmentPosMaskBit()).isEqualTo(4);        
        list = bm.getSegmentMemory().getPathMemories();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.contains(rtn3Rs)).isTrue();
    }       
    
    @Test
    public void testRuleSegmentLinking() {
        setUp( JOIN_NODE );

        StatefulKnowledgeSessionImpl wm = new StatefulKnowledgeSessionImpl( 1L, kBase );

        BetaMemory bm = null;
        List<PathMemory> list;
        
        PathMemory rtn1Rs = wm.getNodeMemory( rtn1 );
        PathMemory rtn2Rs = wm.getNodeMemory( rtn2 );
        PathMemory rtn3Rs = wm.getNodeMemory( rtn3 );
        
        DefaultFactHandle f1 = (DefaultFactHandle) wm.insert( "test1" );

        lian.assertObject( f1, context, wm );
        n1.getRightInput().assertObject( f1, context, wm );
        n3.getRightInput().assertObject( f1, context, wm );
        n4.getRightInput().assertObject( f1, context, wm );
        n8.getRightInput().assertObject( f1, context, wm );

        assertThat(rtn1Rs.isRuleLinked()).isFalse();
        assertThat(rtn2Rs.isRuleLinked()).isFalse();
        assertThat(rtn3Rs.isRuleLinked()).isFalse();
        

        // Link in Rule1
        bm = (BetaMemory) wm.getNodeMemory(n2);
        assertThat(bm.getSegmentMemory().isSegmentLinked()).isFalse();
        
        DefaultFactHandle f2 = (DefaultFactHandle) wm.insert( "test2" );
        n2.getRightInput().assertObject( f2, context, wm );
        assertThat(bm.getSegmentMemory().isSegmentLinked()).isTrue();

        assertThat(rtn1Rs.isRuleLinked()).isTrue();
        assertThat(rtn2Rs.isRuleLinked()).isFalse();
        assertThat(rtn3Rs.isRuleLinked()).isFalse();        
        
        // Link in Rule2
        bm = (BetaMemory) wm.getNodeMemory(n5);
        assertThat(bm.getSegmentMemory().isSegmentLinked()).isFalse();
        
        n5.getRightInput().assertObject( f1, context, wm );
        assertThat(bm.getSegmentMemory().isSegmentLinked()).isTrue();

        assertThat(rtn1Rs.isRuleLinked()).isTrue();
        assertThat(rtn2Rs.isRuleLinked()).isTrue();
        assertThat(rtn3Rs.isRuleLinked()).isFalse();         
        
        // Link in Rule3
        n6.getRightInput().assertObject( f1, context, wm );
        n7.getRightInput().assertObject( f1, context, wm );
        assertThat(bm.getSegmentMemory().isSegmentLinked()).isTrue();

        assertThat(rtn1Rs.isRuleLinked()).isTrue();
        assertThat(rtn2Rs.isRuleLinked()).isTrue();
        assertThat(rtn3Rs.isRuleLinked()).isTrue();  
        
        // retract n2, should unlink all rules
        n2.getRightInput().retractRightTuple( f2.getFirstRightTuple(), context, wm );
        assertThat(rtn1Rs.isRuleLinked()).isFalse();
        assertThat(rtn2Rs.isRuleLinked()).isFalse();
        assertThat(rtn3Rs.isRuleLinked()).isFalse();        
    }
   
}
