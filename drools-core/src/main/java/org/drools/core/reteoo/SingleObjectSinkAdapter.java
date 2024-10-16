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

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.core.common.BaseNode;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.reteoo.AccumulateNode.AccumulateMemory;

public class SingleObjectSinkAdapter implements ObjectSinkPropagator {

    private static final long serialVersionUID = 510l;

    private ObjectSink      sink;
    private ObjectSink[]    sinks;

    public SingleObjectSinkAdapter() { }

    public SingleObjectSinkAdapter(final ObjectSink sink) {
        this.sink = sink;
        this.sinks = new ObjectSink[]{this.sink};
    }

    public void readExternal(ObjectInput in) throws IOException,
                                            ClassNotFoundException {
        this.sink = (ObjectSink) in.readObject();
        this.sinks = new ObjectSink[]{this.sink};
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject( sink );
    }

    public ObjectSinkPropagator addObjectSink(ObjectSink sink, int alphaNodeHashingThreshold, int alphaNodeRangeIndexThreshold) {
        final CompositeObjectSinkAdapter sinkAdapter = new CompositeObjectSinkAdapter( alphaNodeHashingThreshold, alphaNodeRangeIndexThreshold );
        sinkAdapter.addObjectSink( this.sink, alphaNodeHashingThreshold, alphaNodeRangeIndexThreshold );
        sinkAdapter.addObjectSink( sink, alphaNodeHashingThreshold, alphaNodeRangeIndexThreshold );
        return sinkAdapter;
    }

    public ObjectSinkPropagator removeObjectSink(final ObjectSink sink) {
        if (this.sink.equals( sink )) {
            return EmptyObjectSinkAdapter.getInstance();
        }
        throw new IllegalArgumentException( "Cannot remove " + sink + " when this sink propagator only contains " + this.sink );
    }

    public void propagateAssertObject(final InternalFactHandle factHandle,
                                      final PropagationContext context,
                                      final ReteEvaluator reteEvaluator) {
        this.sink.assertObject( factHandle, context, reteEvaluator );
    }

    public void propagateModifyObject(InternalFactHandle factHandle,
                                            ModifyPreviousTuples modifyPreviousTuples,
                                            PropagationContext context,
                                            ReteEvaluator reteEvaluator) {
        this.sink.modifyObject( factHandle, modifyPreviousTuples, context, reteEvaluator );
    }
    
    public void byPassModifyToBetaNode (final InternalFactHandle factHandle,
                                        final ModifyPreviousTuples modifyPreviousTuples,
                                        final PropagationContext context,
                                        final ReteEvaluator reteEvaluator) {
        sink.byPassModifyToBetaNode( factHandle, modifyPreviousTuples, context, reteEvaluator );
    }
    
    public void doLinkSubnetwork(ReteEvaluator reteEvaluator) {
        staticDoLinkSubnetwork(sink, reteEvaluator);
    }
    
    public static void staticDoLinkSubnetwork(ObjectSink sink, ReteEvaluator reteEvaluator) {
        BetaMemory bm;
        BetaNode betaNode;
        if ( sink.getType() == NodeTypeEnums.AccumulateRightAdapterNode ) {
            AccumulateRight accnode = ( AccumulateRight ) sink;
            betaNode = accnode.getBetaNode();
            AccumulateMemory accMem = ( AccumulateMemory ) reteEvaluator.getNodeMemory( betaNode );
            bm = accMem.getBetaMemory();
        } else {
            RightInputAdapterNode betaRight = (RightInputAdapterNode) sink;
            betaNode = betaRight.getBetaNode();
            bm = RightInputAdapterNode.getBetaMemoryFromRightInput(betaNode, reteEvaluator);
        }

        if ( bm.getStagedRightTuples().isEmpty() ) {
            if ( bm.getRightTupleMemory().size() == 0 ) {
                bm.linkNode( betaNode, reteEvaluator );
            } else {
                bm.setNodeDirty( betaNode, reteEvaluator );
            }
        }
    }
    
    public void doUnlinkSubnetwork(ReteEvaluator reteEvaluator) {
        staticDoUnlinkSubnetwork(sink, reteEvaluator);
    }   
    
    public static void staticDoUnlinkSubnetwork(ObjectSink sink, ReteEvaluator reteEvaluator) {
        BetaMemory bm;
        if ( sink.getType() == NodeTypeEnums.AccumulateRightAdapterNode ) {
            AccumulateNode accnode = ((AccumulateRight)sink).getBetaNode();
            AccumulateMemory accMem = ( AccumulateMemory ) reteEvaluator.getNodeMemory( accnode );
            bm = accMem.getBetaMemory();
        }  else {
            BetaNode betaNode = ((RightInputAdapterNode) sink).getBetaNode();
            bm = RightInputAdapterNode.getBetaMemoryFromRightInput(betaNode, reteEvaluator);
        }

        if (sink.getType() == NodeTypeEnums.NotNode) {
            bm.linkNode( ( BetaNode ) sink, reteEvaluator );
        } else {
            bm.unlinkNode(reteEvaluator);
        }
    }

    public BaseNode getMatchingNode(BaseNode candidate) {
        return sink.equals(candidate) ? (BaseNode) sink : null;
    }

    public ObjectSink[] getSinks() {
        return sinks;
    }

    public int size() {
        return 1;
    }

    public boolean isEmpty() {
        return false;
    }
}
