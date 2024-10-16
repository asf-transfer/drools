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
package org.drools.core.phreak;

import org.drools.core.common.BetaConstraints;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.TupleSets;
import org.drools.core.reteoo.BetaMemory;
import org.drools.core.reteoo.ExistsNode;
import org.drools.core.reteoo.LeftTuple;
import org.drools.core.reteoo.LeftTupleSink;
import org.drools.core.reteoo.RightTuple;
import org.drools.core.reteoo.TupleFactory;
import org.drools.core.reteoo.TupleImpl;
import org.drools.core.reteoo.TupleMemory;
import org.drools.core.util.FastIterator;

import static org.drools.core.phreak.PhreakJoinNode.updateChildLeftTuple;

public class PhreakExistsNode {
    public void doNode(ExistsNode existsNode,
                       LeftTupleSink sink,
                       BetaMemory bm,
                       ReteEvaluator reteEvaluator,
                       TupleSets srcLeftTuples,
                       TupleSets trgLeftTuples,
                       TupleSets stagedLeftTuples) {
        if (!existsNode.getRightInput().inputIsTupleToObjectNode()) {
            doNormalNode(existsNode, sink, bm, reteEvaluator, srcLeftTuples, trgLeftTuples, stagedLeftTuples);
        } else {
            PhreakSubnetworkNotExistsNode.doSubNetworkNode(existsNode, sink, bm,
                                                           srcLeftTuples, trgLeftTuples, stagedLeftTuples);
        }
    }

    public void doNormalNode(ExistsNode existsNode,
                             LeftTupleSink sink,
                             BetaMemory bm,
                             ReteEvaluator reteEvaluator,
                             TupleSets srcLeftTuples,
                             TupleSets trgLeftTuples,
                             TupleSets stagedLeftTuples) {

        TupleSets srcRightTuples = bm.getStagedRightTuples().takeAll();

        if (srcLeftTuples.getDeleteFirst() != null) {
            doLeftDeletes(bm, srcLeftTuples, trgLeftTuples, stagedLeftTuples);
        }

        if (srcLeftTuples.getUpdateFirst() != null )  {
            RuleNetworkEvaluator.doUpdatesExistentialReorderLeftMemory(bm,
                                                                       srcLeftTuples);
        }

        if ( srcRightTuples.getUpdateFirst() != null ) {
            RuleNetworkEvaluator.doUpdatesExistentialReorderRightMemory(bm,
                                                                        existsNode,
                                                                        srcRightTuples); // this also preserves the next rightTuple
        }

        if (srcRightTuples.getInsertFirst() != null) {
            // left deletes must come before right deletes. Otherwise right deletes could
            // stage a deletion, that is later deleted in the rightDelete, causing potential problems
            doRightInserts(existsNode, sink, bm, reteEvaluator, srcRightTuples, trgLeftTuples);
        }

        if (srcRightTuples.getUpdateFirst() != null) {
            // must come after rightInserts and before rightDeletes, to avoid staging clash
            doRightUpdates(existsNode, sink, bm, reteEvaluator, srcRightTuples, trgLeftTuples, stagedLeftTuples);
        }

        if (srcRightTuples.getDeleteFirst() != null) {
            // must come after rightUpdetes, to avoid staging clash
            doRightDeletes(existsNode, bm, reteEvaluator, srcRightTuples, trgLeftTuples, stagedLeftTuples);
        }


        if (srcLeftTuples.getUpdateFirst() != null) {
            doLeftUpdates(existsNode, sink, bm, reteEvaluator, srcLeftTuples, trgLeftTuples, stagedLeftTuples);
        }

        if (srcLeftTuples.getInsertFirst() != null) {
            doLeftInserts(existsNode, sink, bm, reteEvaluator, srcLeftTuples, trgLeftTuples);
        }

        srcRightTuples.resetAll();
        srcLeftTuples.resetAll();
    }

    public void doLeftInserts(ExistsNode existsNode,
                              LeftTupleSink sink,
                              BetaMemory bm,
                              ReteEvaluator reteEvaluator,
                              TupleSets srcLeftTuples,
                              TupleSets trgLeftTuples) {
        TupleMemory ltm = bm.getLeftTupleMemory();
        TupleMemory rtm = bm.getRightTupleMemory();
        Object contextEntry = bm.getContext();
        BetaConstraints constraints = existsNode.getRawConstraints();

        for (TupleImpl leftTuple = srcLeftTuples.getInsertFirst(); leftTuple != null; ) {
            TupleImpl next = leftTuple.getStagedNext();

            boolean useLeftMemory = RuleNetworkEvaluator.useLeftMemory(existsNode, leftTuple);

            constraints.updateFromTuple( contextEntry,
                                         reteEvaluator,
                                         leftTuple );

            // This method will also remove rightTuples that are from subnetwork where no leftmemory use used
            RuleNetworkEvaluator.findLeftTupleBlocker( existsNode, rtm, contextEntry, constraints, leftTuple, useLeftMemory );

            if (leftTuple.getBlocker() != null) {
                // tuple is not blocked to propagate
                insertChildLeftTuple( sink, trgLeftTuples, leftTuple, leftTuple.getBlocker().getPropagationContext(),useLeftMemory );
            } else if (useLeftMemory) {
                // LeftTuple is not blocked, so add to memory so other RightTuples can match
                ltm.add(leftTuple);
            }
            leftTuple.clearStaged();
            leftTuple = next;
        }
        constraints.resetTuple( contextEntry );
    }

    public void doRightInserts(ExistsNode existsNode,
                               LeftTupleSink sink,
                               BetaMemory bm,
                               ReteEvaluator reteEvaluator,
                               TupleSets srcRightTuples,
                               TupleSets trgLeftTuples) {
        TupleMemory ltm = bm.getLeftTupleMemory();
        TupleMemory rtm = bm.getRightTupleMemory();
        Object contextEntry = bm.getContext();
        BetaConstraints constraints = existsNode.getRawConstraints();

        for (RightTuple rightTuple = (RightTuple) srcRightTuples.getInsertFirst(); rightTuple != null; ) {
            RightTuple next = (RightTuple) rightTuple.getStagedNext();
            rtm.add(rightTuple);

            if ( ltm != null && ltm.size() > 0 ) {
                FastIterator it = existsNode.getLeftIterator( ltm );

                constraints.updateFromFactHandle( contextEntry,
                                                  reteEvaluator,
                                                  rightTuple.getFactHandleForEvaluation() );

                for ( LeftTuple leftTuple =  (LeftTuple) existsNode.getFirstLeftTuple(rightTuple, ltm, it ); leftTuple != null; ) {
                    // preserve next now, in case we remove this leftTuple
                    LeftTuple temp = (LeftTuple) it.next(leftTuple );

                    if ( leftTuple.getStagedType() == LeftTuple.UPDATE ) {
                        // ignore, as it will get processed via left iteration. Children cannot be processed twice
                        leftTuple = temp;
                        continue;
                    }

                    // we know that only unblocked LeftTuples are  still in the memory
                    if ( constraints.isAllowedCachedRight(leftTuple, contextEntry
                                                         ) ) {
                        leftTuple.setBlocker( rightTuple );
                        rightTuple.addBlocked( leftTuple );

                        ltm.remove( leftTuple );

                        insertChildLeftTuple( sink, trgLeftTuples, leftTuple, rightTuple.getPropagationContext(), true );
                    }

                    leftTuple = temp;
                }
            }
            rightTuple.clearStaged();
            rightTuple = next;
        }
        constraints.resetFactHandle( contextEntry );
    }

    public void doLeftUpdates(ExistsNode existsNode,
                              LeftTupleSink sink,
                              BetaMemory bm,
                              ReteEvaluator reteEvaluator,
                              TupleSets srcLeftTuples,
                              TupleSets trgLeftTuples,
                              TupleSets stagedLeftTuples) {
        TupleMemory ltm = bm.getLeftTupleMemory();
        TupleMemory rtm = bm.getRightTupleMemory();
        Object contextEntry = bm.getContext();
        BetaConstraints constraints = existsNode.getRawConstraints();
        boolean leftUpdateOptimizationAllowed = existsNode.isLeftUpdateOptimizationAllowed();

        for (LeftTuple leftTuple = (LeftTuple) srcLeftTuples.getUpdateFirst(); leftTuple != null; ) {
            LeftTuple next = (LeftTuple) leftTuple.getStagedNext();

            FastIterator<TupleImpl> rightIt = existsNode.getRightIterator(rtm);

            RightTuple firstRightTuple = existsNode.getFirstRightTuple(leftTuple, rtm, rightIt);

            // If in memory, remove it, because we'll need to add it anyway if it's not blocked, to ensure iteration order
            RightTuple blocker = leftTuple.getBlocker();
            if (blocker == null) {
                if (leftTuple.getMemory() != null) { // memory can be null, if blocker was deleted in same do loop
                    ltm.remove(leftTuple);
                }
            } else {
                // check if we changed bucket
                if (rtm.isIndexed() && !rightIt.isFullIterator()) {
                    // if newRightTuple is null, we assume there was a bucket change and that bucket is empty
                    if (firstRightTuple == null || firstRightTuple.getMemory() != blocker.getMemory()) {
                        // we changed bucket, so blocker no longer blocks
                        blocker.removeBlocked(leftTuple);
                        blocker = null;
                    }
                }
            }

            constraints.updateFromTuple(contextEntry,
                                        reteEvaluator,
                                        leftTuple);

            if ( !leftUpdateOptimizationAllowed && blocker != null ) {
                blocker.removeBlocked(leftTuple);
                blocker = null;
            }

            // if we where not blocked before (or changed buckets), or the previous blocker no longer blocks, then find the next blocker
            if (blocker == null || !constraints.isAllowedCachedLeft(contextEntry,
                                                                    blocker.getFactHandleForEvaluation())) {

                if (blocker != null) {
                    // remove previous blocker if it exists, as we know it doesn't block any more
                    blocker.removeBlocked(leftTuple);
                }

                // find first blocker, because it's a modify, we need to start from the beginning again
                for (RightTuple newBlocker = firstRightTuple; newBlocker != null; newBlocker = (RightTuple) rightIt.next(newBlocker)) {
                    if (constraints.isAllowedCachedLeft( contextEntry,
                                                         newBlocker.getFactHandleForEvaluation() )) {
                        leftTuple.setBlocker( newBlocker );
                        newBlocker.addBlocked( leftTuple );

                        break;
                    }
                }
            }

            if (leftTuple.getBlocker() == null) {
                // not blocked
                ltm.add(leftTuple); // add to memory so other fact handles can attempt to match

                if (leftTuple.getFirstChild() != null) {
                    // no need to update pctx, as no right available, and pctx will exist on a parent LeftTuple anyway
                    RuleNetworkEvaluator.unlinkAndDeleteChildLeftTuple( leftTuple.getFirstChild(), trgLeftTuples, stagedLeftTuples );
                }
                // with no previous children. do nothing.
            } else if (leftTuple.getFirstChild() == null) {
                // blocked, with no previous children, insert
                insertChildLeftTuple( sink, trgLeftTuples, leftTuple, leftTuple.getBlocker().getPropagationContext(), true );
            } else {
                // blocked, with previous children, modify
                TupleImpl childLeftTuple = leftTuple.getFirstChild();

                while (childLeftTuple != null) {
                    childLeftTuple.setPropagationContext(leftTuple.getBlocker().getPropagationContext());
                    updateChildLeftTuple(childLeftTuple, stagedLeftTuples, trgLeftTuples);

                    childLeftTuple.reAddRight();
                    childLeftTuple = childLeftTuple.getHandleNext();
                }
            }

            leftTuple.clearStaged();
            leftTuple = next;
        }
        constraints.resetTuple( contextEntry );
    }

    public void doRightUpdates(ExistsNode existsNode,
                               LeftTupleSink sink,
                               BetaMemory bm,
                               ReteEvaluator reteEvaluator,
                               TupleSets srcRightTuples,
                               TupleSets trgLeftTuples,
                               TupleSets stagedLeftTuples) {
        TupleMemory ltm = bm.getLeftTupleMemory();
        TupleMemory rtm = bm.getRightTupleMemory();
        Object contextEntry = bm.getContext();
        BetaConstraints constraints = existsNode.getRawConstraints();

        boolean iterateFromStart = existsNode.isIndexedUnificationJoin() || rtm.getIndexType().isComparison();

        for (RightTuple rightTuple = (RightTuple) srcRightTuples.getUpdateFirst(); rightTuple != null; ) {
            RightTuple next = (RightTuple) rightTuple.getStagedNext();

            if ( ltm != null && ltm.size() > 0 ) {
                FastIterator leftIt = existsNode.getLeftIterator( ltm );

                TupleImpl firstLeftTuple = existsNode.getFirstLeftTuple(rightTuple, ltm, leftIt );

                constraints.updateFromFactHandle( contextEntry,
                                                  reteEvaluator,
                                                  rightTuple.getFactHandleForEvaluation() );


                // first process non-blocked tuples, as we know only those ones are in the left memory.
                for ( LeftTuple leftTuple = (LeftTuple) firstLeftTuple; leftTuple != null; ) {
                    // preserve next now, in case we remove this leftTuple
                    LeftTuple temp = (LeftTuple) leftIt.next(leftTuple );

                    if ( leftTuple.getStagedType() == LeftTuple.UPDATE ) {
                        // ignore, as it will get processed via left iteration. Children cannot be processed twice
                        leftTuple = temp;
                        continue;
                    }

                    // we know that only unblocked LeftTuples are  still in the memory
                    if ( constraints.isAllowedCachedRight(leftTuple, contextEntry
                                                         ) ) {
                        leftTuple.setBlocker( rightTuple );
                        rightTuple.addBlocked( leftTuple );

                        // this is now blocked so remove from memory
                        ltm.remove( leftTuple );

                        // subclasses like ForallNotNode might override this propagation
                        insertChildLeftTuple( sink, trgLeftTuples, leftTuple, rightTuple.getPropagationContext(), true );
                    }

                    leftTuple = temp;
                }
            }

            LeftTuple firstBlocked = rightTuple.getTempBlocked();
            if ( firstBlocked != null ) {
                RightTuple rootBlocker = rightTuple.getTempNextRightTuple();
                if ( rootBlocker == null ) {
                    iterateFromStart = true;
                }

                FastIterator<TupleImpl> rightIt = existsNode.getRightIterator( rtm );

                // iterate all the existing previous blocked LeftTuples
                for ( LeftTuple leftTuple = firstBlocked; leftTuple != null; ) {
                    LeftTuple temp = leftTuple.getBlockedNext();

                    leftTuple.clearBlocker(); // must null these as we are re-adding them to the list

                    if ( leftTuple.getStagedType() == LeftTuple.UPDATE ) {
                        // ignore, as it will get processed via left iteration. Children cannot be processed twice
                        // but need to add it back into list first
                        leftTuple.setBlocker( rightTuple );
                        rightTuple.addBlocked( leftTuple );

                        leftTuple = temp;
                        continue;
                    }

                    constraints.updateFromTuple( contextEntry,
                                                 reteEvaluator,
                                                 leftTuple );

                    if ( iterateFromStart ) {
                        rootBlocker = existsNode.getFirstRightTuple( leftTuple, rtm, rightIt );
                    }

                    // we know that older tuples have been checked so continue next
                    for (RightTuple newBlocker = rootBlocker; newBlocker != null; newBlocker = (RightTuple) rightIt.next(newBlocker) ) {
                        // cannot select a RightTuple queued in the delete list
                        // There may be UPDATE RightTuples too, but that's ok. They've already been re-added to the correct bucket, safe to be reprocessed.
                        if ( leftTuple.getStagedType() != LeftTuple.DELETE && newBlocker.getStagedType() != LeftTuple.DELETE &&
                             constraints.isAllowedCachedLeft( contextEntry, newBlocker.getFactHandleForEvaluation() ) ) {
                            leftTuple.setBlocker( newBlocker );
                            newBlocker.addBlocked( leftTuple );

                            break;
                        }
                    }

                    if ( leftTuple.getBlocker() == null ) {
                        // was previous blocked and not in memory, so add
                        if (ltm != null) {
                            ltm.add( leftTuple );
                        }

                        TupleImpl childLeftTuple = leftTuple.getFirstChild();
                        if ( childLeftTuple != null ) {
                            childLeftTuple.setPropagationContext( rightTuple.getPropagationContext() );
                            RuleNetworkEvaluator.unlinkAndDeleteChildLeftTuple( childLeftTuple, trgLeftTuples, stagedLeftTuples );
                        }
                    }

                    leftTuple = temp;
                }
            }
            rightTuple.clearStaged();
            rightTuple = next;
        }
        constraints.resetFactHandle(contextEntry);
    }

    public void doLeftDeletes(BetaMemory bm,
                              TupleSets srcLeftTuples,
                              TupleSets trgLeftTuples,
                              TupleSets stagedLeftTuples) {
        TupleMemory ltm = bm.getLeftTupleMemory();

        for (LeftTuple leftTuple = (LeftTuple) srcLeftTuples.getDeleteFirst(); leftTuple != null; ) {
            LeftTuple  next    = (LeftTuple) leftTuple.getStagedNext();
            RightTuple blocker = leftTuple.getBlocker();
            if (blocker == null) {
                if (leftTuple.getMemory() != null) {
                    // it may have been staged and never actually added
                    ltm.remove(leftTuple);
                }
            } else {
                if (leftTuple.getFirstChild() != null) {
                    // no need to update pctx, as no right available, and pctx will exist on a parent LeftTuple anyway
                    RuleNetworkEvaluator.unlinkAndDeleteChildLeftTuple( leftTuple.getFirstChild(), trgLeftTuples, stagedLeftTuples );
                }
                blocker.removeBlocked(leftTuple);
            }

            leftTuple.clearStaged();
            leftTuple = next;
        }
    }

    public void doRightDeletes(ExistsNode existsNode,
                               BetaMemory bm,
                               ReteEvaluator reteEvaluator,
                               TupleSets srcRightTuples,
                               TupleSets trgLeftTuples,
                               TupleSets stagedLeftTuples) {
        TupleMemory rtm = bm.getRightTupleMemory();
        TupleMemory ltm = bm.getLeftTupleMemory();
        Object contextEntry = bm.getContext();
        BetaConstraints constraints = existsNode.getRawConstraints();

        for (RightTuple rightTuple = (RightTuple) srcRightTuples.getDeleteFirst(); rightTuple != null; ) {
            RightTuple next = (RightTuple) rightTuple.getStagedNext();

            if (rightTuple.getMemory() != null) {
                // it may have been staged and never actually added
                rtm.remove(rightTuple);
            }

            if (rightTuple.getBlocked() != null) {
                FastIterator it = existsNode.getRightIterator(rtm);

                for (LeftTuple leftTuple = rightTuple.getBlocked(); leftTuple != null; ) {
                    LeftTuple temp = leftTuple.getBlockedNext();

                    leftTuple.clearBlocker();

                    if (leftTuple.getStagedType() == LeftTuple.UPDATE) {
                        // ignore, as it will get processed via left iteration. Children cannot be processed twice
                        leftTuple = temp;
                        continue;
                    }

                    constraints.updateFromTuple(contextEntry, reteEvaluator, leftTuple);
                    RightTuple rootBlocker = (RightTuple) rtm.getFirst(leftTuple);

                    // we know that older tuples have been checked so continue previously
                    for (RightTuple newBlocker = rootBlocker; newBlocker != null; newBlocker = (RightTuple) it.next(newBlocker)) {
                        if (!newBlocker.isDeleted() && constraints.isAllowedCachedLeft(contextEntry,
                                                                                       newBlocker.getFactHandleForEvaluation())) {
                            leftTuple.setBlocker(newBlocker);
                            newBlocker.addBlocked(leftTuple);

                            break;
                        }
                    }

                    if (leftTuple.getBlocker() == null) {
                        // was previous blocked and not in memory, so add
                        ltm.add(leftTuple);

                        TupleImpl childLeftTuple = leftTuple.getFirstChild();
                        if (childLeftTuple != null) {
                            childLeftTuple.setPropagationContext(rightTuple.getPropagationContext());
                            RuleNetworkEvaluator.unlinkAndDeleteChildLeftTuple( childLeftTuple, trgLeftTuples, stagedLeftTuples );
                        }
                    }

                    leftTuple = temp;
                }
            }
            rightTuple.setBlocked(null);
            rightTuple.clearStaged();
            rightTuple = next;
        }
    }

    private static void insertChildLeftTuple(LeftTupleSink sink, TupleSets trgLeftTuples, TupleImpl leftTuple, PropagationContext pctx, boolean useLeftMemory ) {
        if (!leftTuple.isExpired()) {
            trgLeftTuples.addInsert(TupleFactory.createLeftTuple(leftTuple, sink, pctx, useLeftMemory));
        }
    }
}
