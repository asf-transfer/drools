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

import java.io.Serializable;

import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.core.common.MemoryFactory;
import org.drools.core.phreak.BuildtimeSegmentUtilities;
import org.drools.core.reteoo.SegmentMemory.SegmentPrototype;

import static org.drools.core.phreak.BuildtimeSegmentUtilities.nextNodePosMask;

public interface PathEndNode extends LeftTupleSinkNode, MemoryFactory<PathMemory> {
    LeftTupleNode[] getPathNodes();

    void nullPathMemSpec();

    LeftTupleSource getStartTupleSource();

    boolean hasPathNode(LeftTupleNode node);

    void setPathEndNodes(PathEndNode[] pathEndNodes);
    PathEndNode[] getPathEndNodes();

    void setSegmentPrototypes(SegmentPrototype[] smems);

    SegmentPrototype[] getSegmentPrototypes();

    SegmentPrototype[] getEagerSegmentPrototypes();

    void setEagerSegmentPrototypes(SegmentPrototype[] eagerSegmentPrototypes);

    PathMemSpec getPathMemSpec();

    PathMemSpec getPathMemSpec(TerminalNode removingTN);

    void resetPathMemSpec(TerminalNode removingTN);

    void setPathMemSpec(PathMemSpec pathMemSpec);

    class PathMemSpec implements Serializable {
        long allLinkedTestMask;
        int smemCount;

        public PathMemSpec( long allLinkedTestMask, int smemCount ) {
            this.allLinkedTestMask = allLinkedTestMask;
            this.smemCount = smemCount;
        }

        public void update( long allLinkedTestMask, int smemCount ) {
            this.allLinkedTestMask = allLinkedTestMask;
            this.smemCount = smemCount;
        }

        public long allLinkedTestMask() {
            return allLinkedTestMask;
        }

        public int smemCount() {
            return smemCount;
        }
    }

    default PathMemSpec calculatePathMemSpec(LeftTupleSource startTupleSource, TerminalNode removingTN) {
        int counter = 1;
        long allLinkedTestMask = 0;

        LeftTupleSource tupleSource = getLeftTupleSource();

        if ( BuildtimeSegmentUtilities.isNonTerminalTipNode(tupleSource, removingTN)) {
            counter++;
        }

        boolean updateBitInNewSegment = true; // Avoids more than one isBetaNode check per segment
        boolean updateAllLinkedTest = !hasConditionalBranchNode(tupleSource); // if there is a CEN, do not set bit until it's reached
        boolean subnetworkBoundaryCrossed = false;
        while (  !NodeTypeEnums.isLeftInputAdapterNode(tupleSource) ) {
            if ( !subnetworkBoundaryCrossed && tupleSource.getType() == NodeTypeEnums.ConditionalBranchNode ) {
                // start recording now we are after the BranchCE, but only if we are not outside the target subnetwork
                updateAllLinkedTest = tupleSource.getType() == NodeTypeEnums.ConditionalBranchNode;
            }

            if ( updateAllLinkedTest && updateBitInNewSegment &&
                    NodeTypeEnums.isBetaNode( tupleSource ) &&
                    NodeTypeEnums.AccumulateNode != tupleSource.getType()) { // accumulates can never be disabled
                BetaNode bn = ( BetaNode) tupleSource;
                if ( bn.getRightInput().inputIsTupleToObjectNode() ) {
                    updateBitInNewSegment = false;
                    TupleToObjectNode tton = (TupleToObjectNode) bn.getRightInput().getParent();
                    // only ria's without reactive subnetworks can be disabled and thus need checking
                    // The getNodeMemory will call this method recursive for sub networks it reaches
                    if ( tton.getPathMemSpec(removingTN).allLinkedTestMask != 0 ) {
                        allLinkedTestMask = allLinkedTestMask | 1;
                    }
                } else if ( NodeTypeEnums.NotNode != bn.getType() || ((NotNode)bn).isEmptyBetaConstraints()) {
                    updateBitInNewSegment = false;
                    // non empty not nodes can never be disabled and thus don't need checking
                    allLinkedTestMask = allLinkedTestMask | 1;
                }
            }

            tupleSource = tupleSource.getLeftTupleSource();
            if ( BuildtimeSegmentUtilities.isNonTerminalTipNode(tupleSource, removingTN) ) {
                updateBitInNewSegment = true; // allow bit to be set for segment
                allLinkedTestMask = nextNodePosMask(allLinkedTestMask);
                counter++;
            }

            if ( tupleSource == startTupleSource.getLeftTupleSource() ) {
                // stop tracking if we move outside of a subnetwork boundary (if one is set)
                subnetworkBoundaryCrossed = true;
                updateAllLinkedTest = false;
            }
        }

        if ( !subnetworkBoundaryCrossed ) {
            allLinkedTestMask = allLinkedTestMask | 1;
        }

        return new PathMemSpec(allLinkedTestMask, counter);
    }

    static boolean hasConditionalBranchNode(LeftTupleNode tupleSource) {
        while (  !NodeTypeEnums.isLeftInputAdapterNode(tupleSource) ) {
            if ( tupleSource.getType() == NodeTypeEnums.ConditionalBranchNode ) {
                return true;
            }
            tupleSource = tupleSource.getLeftTupleSource();
        }
        return false;
    }
}
