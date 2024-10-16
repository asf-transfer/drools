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
package org.drools.core.reteoo;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.drools.base.reteoo.NodeTypeEnums.isBetaNode;
import static org.drools.base.reteoo.NodeTypeEnums.isLeftTupleSink;
import static org.drools.base.reteoo.NodeTypeEnums.isLeftTupleSource;
import static org.drools.base.reteoo.NodeTypeEnums.isObjectSink;
import static org.drools.base.reteoo.NodeTypeEnums.isObjectSource;

public class NodeTypeEnumTest {
    EntryPointNode         epNode     = new EntryPointNode();
    Rete                   reteNod    = new Rete();
    ObjectTypeNode         otNode     = new ObjectTypeNode();
    AlphaNode              alphaNode  = new AlphaNode();
    WindowNode             winNode    = new WindowNode();

    TupleToObjectNode      ttoNode = new TupleToObjectNode();

    RuleTerminalNode       rtNode     = new RuleTerminalNode();
    QueryTerminalNode      qtNode     = new QueryTerminalNode();

    LeftInputAdapterNode   liaNode    = new LeftInputAdapterNode();

    EvalConditionNode      evalNode   = new EvalConditionNode();
    FromNode               fromNode   = new FromNode();

    QueryElementNode       uNode      = new QueryElementNode();

    NotNode                notNode    = new NotNode();
    ExistsNode             existsNode = new ExistsNode();
    JoinNode               joinNode   = new JoinNode();
    AccumulateNode         accNode    = new AccumulateNode();

    @Test
    public void tesObjectSource() {
        assertThat(isObjectSource(epNode)).isTrue();
        assertThat(isObjectSource(reteNod)).isTrue();
        assertThat(isObjectSource(otNode)).isTrue();
        assertThat(isObjectSource(alphaNode)).isTrue();

        assertThat(isObjectSource(ttoNode)).isTrue();

        assertThat(isObjectSource(rtNode)).isFalse();
        assertThat(isObjectSource(qtNode)).isFalse();

        assertThat(isObjectSource(liaNode)).isFalse();

        assertThat(isObjectSource(evalNode)).isFalse();
        assertThat(isObjectSource(fromNode)).isFalse();

        assertThat(isObjectSource(uNode)).isFalse();

        assertThat(isObjectSource(notNode)).isFalse();
        assertThat(isObjectSource(existsNode)).isFalse();
        assertThat(isObjectSource(joinNode)).isFalse();
        assertThat(isObjectSource(accNode)).isFalse();       
    }
    
    @Test
    public void tesObjectSink() {
        assertThat(isObjectSink(epNode)).isTrue();
        assertThat(isObjectSink(reteNod)).isTrue();
        assertThat(isObjectSink(otNode)).isTrue();
        assertThat(isObjectSink(alphaNode)).isTrue();

        assertThat(isObjectSink(ttoNode)).isFalse();

        assertThat(isObjectSink(rtNode)).isFalse();
        assertThat(isObjectSink(qtNode)).isFalse();

        assertThat(isObjectSink(liaNode)).isTrue();

        assertThat(isObjectSink(evalNode)).isFalse();
        assertThat(isObjectSink(fromNode)).isFalse();

        assertThat(isObjectSink(uNode)).isFalse();

        assertThat(isObjectSink(notNode)).isFalse();
        assertThat(isObjectSink(existsNode)).isFalse();
        assertThat(isObjectSink(joinNode)).isFalse();
        assertThat(isObjectSink(accNode)).isFalse();       
    }    
    
    @Test
    public void tesLeftTupleSource() {
        assertThat(isLeftTupleSource(epNode)).isFalse();
        assertThat(isLeftTupleSource(reteNod)).isFalse();
        assertThat(isLeftTupleSource(otNode)).isFalse();
        assertThat(isLeftTupleSource(alphaNode)).isFalse();
        assertThat(isLeftTupleSource(ttoNode)).isFalse();

        assertThat(isLeftTupleSource(rtNode)).isFalse();
        assertThat(isLeftTupleSource(qtNode)).isFalse();

        assertThat(isLeftTupleSource(liaNode)).isTrue();

        assertThat(isLeftTupleSource(evalNode)).isTrue();
        assertThat(isLeftTupleSource(fromNode)).isTrue();

        assertThat(isLeftTupleSource(uNode)).isTrue();

        assertThat(isLeftTupleSource(notNode)).isTrue();
        assertThat(isLeftTupleSource(existsNode)).isTrue();
        assertThat(isLeftTupleSource(joinNode)).isTrue();
        assertThat(isLeftTupleSource(accNode)).isTrue();       
    }    
    
    @Test
    public void tesLeftTupleSink() {
        assertThat(isLeftTupleSink(epNode)).isFalse();
        assertThat(isLeftTupleSink(reteNod)).isFalse();
        assertThat(isLeftTupleSink(otNode)).isFalse();
        assertThat(isLeftTupleSink(alphaNode)).isFalse();

        assertThat(isLeftTupleSink(ttoNode)).isTrue();

        assertThat(isLeftTupleSink(rtNode)).isTrue();
        assertThat(isLeftTupleSink(qtNode)).isTrue();

        assertThat(isLeftTupleSink(liaNode)).isFalse();

        assertThat(isLeftTupleSink(evalNode)).isTrue();
        assertThat(isLeftTupleSink(fromNode)).isTrue();

        assertThat(isLeftTupleSink(uNode)).isTrue();

        assertThat(isLeftTupleSink(notNode)).isTrue();
        assertThat(isLeftTupleSink(existsNode)).isTrue();
        assertThat(isLeftTupleSink(joinNode)).isTrue();
        assertThat(isLeftTupleSink(accNode)).isTrue();       
    }     
    
    @Test
    public void testBetaNode() {
        assertThat(isBetaNode(epNode)).isFalse();
        assertThat(isBetaNode(reteNod)).isFalse();
        assertThat(isBetaNode(otNode)).isFalse();
        assertThat(isBetaNode(alphaNode)).isFalse();

        assertThat(isBetaNode(ttoNode)).isFalse();

        assertThat(isBetaNode(rtNode)).isFalse();
        assertThat(isBetaNode(qtNode)).isFalse();

        assertThat(isBetaNode(liaNode)).isFalse();

        assertThat(isBetaNode(evalNode)).isFalse();
        assertThat(isBetaNode(fromNode)).isFalse();

        assertThat(isBetaNode(uNode)).isFalse();

        assertThat(isBetaNode(notNode)).isTrue();
        assertThat(isBetaNode(existsNode)).isTrue();
        assertThat(isBetaNode(joinNode)).isTrue();
        assertThat(isBetaNode(accNode)).isTrue();       
    }       
}
