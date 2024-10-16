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

import org.drools.core.common.Memory;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.TupleSets;
import org.drools.core.common.TupleSetsImpl;
import org.drools.core.reteoo.TupleToObjectNode.SubnetworkPathMemory;
import org.drools.core.util.AbstractLinkedListNode;

public class BetaMemory<C> extends AbstractLinkedListNode<Memory> implements SegmentNodeMemory {

    private              TupleMemory leftTupleMemory;
    private              TupleMemory rightTupleMemory;
    private              TupleSets   stagedRightTuples;
    private              C           context;
    // the node type this memory belongs to
    private              int         nodeType;
    private SegmentMemory            segmentMemory;
    private long                     nodePosMaskBit;
    private int                      counter;
    private SubnetworkPathMemory     subnetworkPathMemory;

    public BetaMemory() {
    }

    public BetaMemory(final TupleMemory tupleMemory,
                      final TupleMemory objectMemory,
                      final C context,
                      final int nodeType) {
        this.leftTupleMemory = tupleMemory;
        this.rightTupleMemory = objectMemory;
        this.stagedRightTuples = new TupleSetsImpl();
        this.context = context;
        this.nodeType = nodeType;
    }

    public TupleSets getStagedRightTuples() {
        return stagedRightTuples;
    }

    public void setStagedRightTuples(TupleSets stagedRightTuples) {
        this.stagedRightTuples = stagedRightTuples;
    }

    public TupleMemory getRightTupleMemory() {
        return this.rightTupleMemory;
    }

    public TupleMemory getLeftTupleMemory() {
        return this.leftTupleMemory;
    }

    public SubnetworkPathMemory getSubnetworkPathMemory() {
        return subnetworkPathMemory;
    }

    public void setSubnetworkPathMemory(SubnetworkPathMemory subnetworkPathMemory) {
        this.subnetworkPathMemory = subnetworkPathMemory;
    }

    /**
     * @return the context
     */
    public C getContext() {
        return context;
    }

    public boolean linkNode(LeftTupleSource tupleSource, ReteEvaluator reteEvaluator) {
        return linkNode(tupleSource, reteEvaluator, true);
    }

    public boolean linkNode(LeftTupleSource tupleSource, ReteEvaluator reteEvaluator, boolean notify) {
        if (segmentMemory == null) {
            segmentMemory = getOrCreateSegmentMemory( tupleSource, reteEvaluator );
        }
        return notify ?
               segmentMemory.linkNode(nodePosMaskBit, reteEvaluator) :
               segmentMemory.linkNodeWithoutRuleNotify(nodePosMaskBit);
    }

    public boolean unlinkNode(ReteEvaluator reteEvaluator) {
        return segmentMemory.unlinkNode(nodePosMaskBit, reteEvaluator);
    }

    public int getNodeType() {
        return this.nodeType;
    }

    public SegmentMemory getSegmentMemory() {
        return segmentMemory;
    }

    public void setSegmentMemory(SegmentMemory segmentMemory) {
        this.segmentMemory = segmentMemory;
    }

    public long getNodePosMaskBit() {
        return nodePosMaskBit;
    }

    public void setNodePosMaskBit(long nodePosMaskBit) {
        this.nodePosMaskBit = nodePosMaskBit;
    }

    public int getCounter() {
        return counter;
    }

    public void setCounter(int counter) {
        this.counter = counter;
    }

    public int getAndIncCounter() {
        return counter++;
    }

    public int getAndDecCounter() {
        return counter--;
    }

    public boolean setNodeDirty(LeftTupleSource tupleSource, ReteEvaluator reteEvaluator) {
        return setNodeDirty(tupleSource, reteEvaluator, true);
    }

    public boolean setNodeDirty(LeftTupleSource tupleSource, ReteEvaluator reteEvaluator, boolean notify) {
        if (segmentMemory == null) {
            segmentMemory = getOrCreateSegmentMemory( tupleSource, reteEvaluator );
        }
        return notify ?
               segmentMemory.notifyRuleLinkSegment(reteEvaluator, nodePosMaskBit) :
               segmentMemory.linkSegmentWithoutRuleNotify(nodePosMaskBit);
    }

    public void setNodeDirtyWithoutNotify() {
        if (segmentMemory != null) {
            segmentMemory.updateDirtyNodeMask( nodePosMaskBit );
        }
    }

    public void setNodeCleanWithoutNotify() {
        if (segmentMemory != null) {
            segmentMemory.updateCleanNodeMask( nodePosMaskBit );
        }
    }

    @Override
    public void reset() {
        if (leftTupleMemory != null) {
            leftTupleMemory.clear();
        }
        if (rightTupleMemory != null) {
            rightTupleMemory.clear();
        }
        stagedRightTuples.resetAll();
        counter = 0;
    }
}
