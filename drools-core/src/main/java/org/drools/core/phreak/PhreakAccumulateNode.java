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

import org.drools.base.reteoo.AccumulateContextEntry;
import org.drools.base.reteoo.BaseTuple;
import org.drools.base.rule.Accumulate;
import org.drools.base.rule.constraint.AlphaNodeFieldConstraint;
import org.drools.core.common.BetaConstraints;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.TupleSets;
import org.drools.core.common.TupleSetsImpl;
import org.drools.core.reteoo.AccumulateNode;
import org.drools.core.reteoo.AccumulateNode.AccumulateContext;
import org.drools.core.reteoo.AccumulateNode.AccumulateMemory;
import org.drools.core.reteoo.AccumulateNode.BaseAccumulation;
import org.drools.core.reteoo.BetaMemory;
import org.drools.core.reteoo.LeftTuple;
import org.drools.core.reteoo.LeftTupleSink;
import org.drools.core.reteoo.RightTuple;
import org.drools.core.reteoo.TupleFactory;
import org.drools.core.reteoo.TupleImpl;
import org.drools.core.reteoo.TupleMemory;
import org.drools.core.util.AbstractHashTable;
import org.drools.core.util.FastIterator;
import org.kie.api.runtime.rule.FactHandle;

import static org.drools.core.phreak.RuleNetworkEvaluator.normalizeStagedTuples;

public class PhreakAccumulateNode {

    public void doNode(AccumulateNode accNode,
                       LeftTupleSink sink,
                       AccumulateMemory am,
                       ReteEvaluator reteEvaluator,
                       TupleSets srcLeftTuples,
                       TupleSets trgLeftTuples,
                       TupleSets stagedLeftTuples) {

        BetaMemory bm             = am.getBetaMemory();
        TupleSets srcRightTuples = bm.getStagedRightTuples().takeAll();


        // order of left and right operations is to minimise wasted of innefficient joins.
        if (srcLeftTuples.getDeleteFirst() != null) {
            // use the real target here, as dealing direct with left tuples
            doLeftDeletes(accNode, am, reteEvaluator, srcLeftTuples, trgLeftTuples, stagedLeftTuples);
        }

        // We need to collect which leftTuple where updated, so that we can
        // add their result tuple to the real target tuples later
        TupleSets tempLeftTuples = new TupleSetsImpl();

        if (srcRightTuples.getDeleteFirst() != null) {
            doRightDeletes(accNode, am, reteEvaluator, srcRightTuples, tempLeftTuples);
        }

        if (srcRightTuples.getUpdateFirst() != null) {
            RuleNetworkEvaluator.doUpdatesReorderRightMemory(bm, srcRightTuples);
            doRightUpdates(accNode, am, reteEvaluator, srcRightTuples, tempLeftTuples);
        }

        if (srcLeftTuples.getUpdateFirst() != null ) {
            RuleNetworkEvaluator.doUpdatesReorderLeftMemory(bm, srcLeftTuples);
            doLeftUpdates(accNode, am, reteEvaluator, srcLeftTuples, tempLeftTuples);
        }

        if (!accNode.getRightInput().inputIsTupleToObjectNode()) {
            // Non subnetworks ore process right then left. This because it's typically faster to ensure all RightTuples
            // are in place then you can iterate with the left evaluation cached.
            if (srcRightTuples.getInsertFirst() != null) {
                doRightInserts(accNode, am, reteEvaluator, srcRightTuples, tempLeftTuples);
            }

            if (srcLeftTuples.getInsertFirst() != null) {
                doLeftInserts(accNode, am, reteEvaluator, srcLeftTuples, tempLeftTuples);
            }
        } else {
            // subnetworks process left then right. It ensures the LTM is not empty and the acctx is initialised.
            // It then returns and all the matching can safely be done by the RightTuple
            if (srcLeftTuples.getInsertFirst() != null) {
                doLeftInserts(accNode, am, reteEvaluator, srcLeftTuples, tempLeftTuples);
            }

            if (srcRightTuples.getInsertFirst() != null) {
                doRightInserts(accNode, am, reteEvaluator, srcRightTuples, tempLeftTuples);
            }
        }

        Accumulate accumulate = accNode.getAccumulate();
        // we do not need collect retracts. RightTuple retracts end up as updates for lefttuples.
        // LeftTuple retracts are already on the trgLeftTuples
        for (TupleImpl leftTuple = tempLeftTuples.getInsertFirst(); leftTuple != null; ) {
            TupleImpl next = leftTuple.getStagedNext();
            evaluateResultConstraints(accNode, sink, accumulate, leftTuple, leftTuple.getPropagationContext(),
                                      reteEvaluator, am, (BaseAccumulation) leftTuple.getContextObject(),
                                      trgLeftTuples, stagedLeftTuples);
            leftTuple.clearStaged();
            leftTuple = next;
        }

        for (TupleImpl leftTuple = tempLeftTuples.getUpdateFirst(); leftTuple != null; ) {
            TupleImpl next = leftTuple.getStagedNext();
            evaluateResultConstraints( accNode, sink, accumulate, leftTuple, leftTuple.getPropagationContext(),
                                       reteEvaluator, am, (BaseAccumulation) leftTuple.getContextObject(),
                                       trgLeftTuples, stagedLeftTuples );
            leftTuple.clearStaged();
            leftTuple = next;
        }

        srcRightTuples.resetAll();

        srcLeftTuples.resetAll();
    }

    private void doLeftInserts(AccumulateNode accNode,
                               AccumulateMemory am,
                               ReteEvaluator reteEvaluator,
                               TupleSets srcLeftTuples,
                               TupleSets trgLeftTuples) {

        Accumulate accumulate = accNode.getAccumulate();
        BetaMemory bm = am.getBetaMemory();
        TupleMemory ltm = bm.getLeftTupleMemory();
        TupleMemory rtm = bm.getRightTupleMemory();
        Object contextEntry = bm.getContext();
        BetaConstraints constraints = accNode.getRawConstraints();

        boolean leftTupleMemoryEnabled = accNode.isLeftTupleMemoryEnabled();

        for (TupleImpl leftTuple = srcLeftTuples.getInsertFirst(); leftTuple != null; ) {
            TupleImpl next = leftTuple.getStagedNext();

            boolean useLeftMemory = leftTupleMemoryEnabled || RuleNetworkEvaluator.useLeftMemory(accNode, leftTuple);

            if (useLeftMemory) {
                ltm.add(leftTuple);
            }

            BaseAccumulation accresult = initAccumulationContext( am, reteEvaluator, accumulate, leftTuple );
            if (accNode.getRightInput().inputIsTupleToObjectNode()) {
                // This is a subnetwork, do not process further. As all matches will processed
                // by the right insert. This is to avoid double iteration (first right side iteration
                // then left side iteration) or for the join to find matching tuple chains, which it previously
                // did via subsumption checking.
                leftTuple.clearStaged();
                trgLeftTuples.addInsert( leftTuple );
                leftTuple = next;

                continue;
            }

            constraints.updateFromTuple( contextEntry,
                                         reteEvaluator,
                                         leftTuple );

            FastIterator rightIt = accNode.getRightIterator(rtm);

            for (RightTuple rightTuple = accNode.getFirstRightTuple(leftTuple, rtm, rightIt); rightTuple != null; ) {
                RightTuple nextRightTuple = (RightTuple) rightIt.next(rightTuple);

                if (constraints.isAllowedCachedLeft(contextEntry,
                                                    rightTuple.getFactHandleForEvaluation())) {
                    // add a match
                    addMatch(accNode, accumulate, leftTuple, rightTuple,
                             null, null, reteEvaluator, am,
                             accresult, useLeftMemory, true);
                }

                rightTuple = nextRightTuple;
            }

            leftTuple.clearStaged();
            trgLeftTuples.addInsert( leftTuple );

            constraints.resetTuple( contextEntry );

            leftTuple = next;
        }
        constraints.resetTuple( contextEntry );
    }

    BaseAccumulation initAccumulationContext(AccumulateMemory am, ReteEvaluator reteEvaluator, Accumulate accumulate, TupleImpl leftTuple) {
        AccumulateContext accContext = new AccumulateContext();
        leftTuple.setContextObject(accContext);

        initContext(am.workingMemoryContext, reteEvaluator, accumulate, leftTuple, accContext);
        return accContext;
    }

    public static void initContext(Object workingMemoryContext, ReteEvaluator reteEvaluator, Accumulate accumulate, BaseTuple leftTuple, AccumulateContextEntry accContext) {
        // Create the function context, but allow init to override it.
        Object funcContext = accumulate.createFunctionContext();
        funcContext = accumulate.init(workingMemoryContext, accContext, funcContext, leftTuple, reteEvaluator);
        accContext.setFunctionContext(funcContext);
    }

    private void doRightInserts(AccumulateNode accNode,
                                AccumulateMemory am,
                                ReteEvaluator reteEvaluator,
                                TupleSets srcRightTuples,
                                TupleSets trgLeftTuples) {
        Accumulate accumulate = accNode.getAccumulate();

        BetaMemory bm = am.getBetaMemory();
        TupleMemory ltm = bm.getLeftTupleMemory();
        TupleMemory rtm = bm.getRightTupleMemory();
        Object contextEntry = bm.getContext();
        BetaConstraints constraints = accNode.getRawConstraints();

        if (srcRightTuples.getInsertSize() > 32 && rtm instanceof AbstractHashTable ) {
            ((AbstractHashTable) rtm).ensureCapacity(srcRightTuples.getInsertSize());
        }

        boolean tupleMemoryEnabled = accNode.isLeftTupleMemoryEnabled();

        for (TupleImpl rightTuple = srcRightTuples.getInsertFirst(); rightTuple != null; ) {
            TupleImpl next = rightTuple.getStagedNext();
            boolean useTupleMemory = tupleMemoryEnabled || RuleNetworkEvaluator.useLeftMemory(accNode, rightTuple);

            if (useTupleMemory || !accNode.getRightInput().inputIsTupleToObjectNode()) {
                // If tuple memory is off, it will still be when it is not a subnetwork.
                rtm.add(rightTuple);
            }

            if (accNode.getRightInput().inputIsTupleToObjectNode() || (ltm != null && ltm.size() > 0)) {
                constraints.updateFromFactHandle( contextEntry,
                                                  reteEvaluator,
                                                  rightTuple.getFactHandleForEvaluation() );

                FastIterator leftIt = accNode.getLeftIterator( ltm );

                for ( TupleImpl leftTuple = accNode.getFirstLeftTuple( rightTuple, ltm, leftIt ); leftTuple != null; leftTuple = (LeftTuple) leftIt.next( leftTuple ) ) {
                    if ( constraints.isAllowedCachedRight(leftTuple, contextEntry) ) {
                        final BaseAccumulation accctx = (BaseAccumulation) leftTuple.getContextObject();
                        addMatch( accNode, accumulate, leftTuple, rightTuple,
                                  null, null, reteEvaluator, am,
                                  accctx, true, false );

                        // right inserts and updates are done first
                        // so any existing leftTuples we know are updates, but only add if not already added
                        if ( leftTuple.getStagedType() == LeftTuple.NONE ) {
                            trgLeftTuples.addUpdate( leftTuple );
                        }
                    }
                }
            }
            rightTuple.clearStaged();
            rightTuple = next;
        }
        constraints.resetFactHandle( contextEntry );
    }

    private void doLeftUpdates(AccumulateNode accNode,
                               AccumulateMemory am,
                               ReteEvaluator reteEvaluator,
                               TupleSets srcLeftTuples,
                               TupleSets trgLeftTuples) {
        BetaMemory bm = am.getBetaMemory();
        TupleMemory rtm = bm.getRightTupleMemory();
        Accumulate accumulate = accNode.getAccumulate();
        Object contextEntry = bm.getContext();
        BetaConstraints constraints = accNode.getRawConstraints();

        for (TupleImpl leftTuple = srcLeftTuples.getUpdateFirst(); leftTuple != null; ) {
            TupleImpl next = leftTuple.getStagedNext();
            BaseAccumulation accctx = (BaseAccumulation) leftTuple.getContextObject();

            if (accNode.getRightInput().inputIsTupleToObjectNode()) {
                // This is a subnetwork, do not process further. As all matches will processed
                // by the right updates. This is to avoid double iteration (first right side iteration
                // then left side iteration) or for the join to find matching tuple chains, which it previously
                // did via subsumption checking.
                leftTuple.clearStaged();
                trgLeftTuples.addUpdate(leftTuple);
                leftTuple = next;
                continue;
            }

            constraints.updateFromTuple(contextEntry,
                                        reteEvaluator,
                                        leftTuple);

            FastIterator rightIt = accNode.getRightIterator(rtm);
            TupleImpl rightTuple = accNode.getFirstRightTuple(leftTuple, rtm, rightIt);

            TupleImpl childLeftTuple = leftTuple.getFirstChild();

            // first check our index (for indexed nodes only) hasn't changed and we are returning the same bucket
            // if rightTuple is null, we assume there was a bucket change and that bucket is empty
            if (childLeftTuple != null && rtm.isIndexed() && !rightIt.isFullIterator() && (rightTuple == null || (rightTuple.getMemory() != childLeftTuple.getRightParent().getMemory()))) {
                // our index has changed, so delete all the previous matchings
                removePreviousMatchesForLeftTuple(accumulate, leftTuple, reteEvaluator, am, accctx, true);

                childLeftTuple = null; // null so the next check will attempt matches for new bucket
            }

            // we can't do anything if RightTupleMemory is empty
            if (rightTuple != null) {
                doLeftUpdatesProcessChildren(accNode,
                                             am,
                                             reteEvaluator,
                                             bm,
                                             accumulate,
                                             constraints,
                                             rightIt,
                                             leftTuple,
                                             accctx,
                                             rightTuple,
                                             childLeftTuple);
            }

            leftTuple.clearStaged();
            trgLeftTuples.addUpdate(leftTuple);

            leftTuple = next;
        }
        constraints.resetTuple(contextEntry);
    }

    private void doLeftUpdatesProcessChildren(AccumulateNode accNode,
                                              AccumulateMemory am,
                                              ReteEvaluator reteEvaluator,
                                              BetaMemory bm,
                                              Accumulate accumulate,
                                              BetaConstraints constraints,
                                              FastIterator<TupleImpl> rightIt,
                                              TupleImpl leftTuple,
                                              final BaseAccumulation accctx,
                                              TupleImpl rightTuple,
                                              TupleImpl match) {
        if (match == null) {
            // either we are indexed and changed buckets or
            // we had no children before, but there is a bucket to potentially match, so try as normal assert
            for (; rightTuple != null; rightTuple = rightIt.next(rightTuple)) {
                if (constraints.isAllowedCachedLeft(bm.getContext(),
                                                    rightTuple.getFactHandleForEvaluation())) {
                    // add a new match
                    addMatch(accNode, accumulate, leftTuple, rightTuple,
                             null, null, reteEvaluator, am,
                             accctx, true, true);
                }
            }
        } else {
            boolean isDirty = false;
            // in the same bucket, so iterate and compare
            for (; rightTuple != null; rightTuple = rightIt.next(rightTuple)) {
                if (constraints.isAllowedCachedLeft(bm.getContext(),
                                                    rightTuple.getFactHandleForEvaluation())) {
                    if (match == null || match.getRightParent() != rightTuple) {
                        // add a new match
                        addMatch(accNode, accumulate, leftTuple, rightTuple,
                                 match, null, reteEvaluator, am,
                                 accctx, true, true);
                    } else {
                        // we must re-add this to ensure deterministic iteration
                        TupleImpl temp = match.getHandleNext();
                        match.reAddRight();
                        match = temp;
                        isDirty = accumulate.hasRequiredDeclarations();
                    }
                } else if (match != null && match.getRightParent() == rightTuple) {
                    TupleImpl temp = match.getHandleNext();
                    // remove the match
                    boolean reversed = removeMatch(accNode, accumulate, rightTuple, match,
                                                   reteEvaluator, am, accctx, false);
                    match = temp;
                    // the next line means that when a match is removed from the current leftTuple
                    // and the accumulate does not support the reverse operation, then the whole
                    // result is dirty (since removeMatch above is not recalculating the total)
                    // and we need to do this later
                    isDirty = !reversed;
                }
                // else do nothing, was false before and false now.
            }
            if (isDirty) {
                reaccumulateForLeftTuple(accNode,
                                         accumulate,
                                         leftTuple,
                                         null,
                                         null,
                                         reteEvaluator,
                                         am,
                                         accctx,
                                         true);
            }
        }
    }

    private void doRightUpdates(AccumulateNode accNode,
                                AccumulateMemory am,
                                ReteEvaluator reteEvaluator,
                                TupleSets srcRightTuples,
                                TupleSets trgLeftTuples) {
        BetaMemory bm = am.getBetaMemory();
        TupleMemory ltm = bm.getLeftTupleMemory();
        Object contextEntry = bm.getContext();
        BetaConstraints constraints = accNode.getRawConstraints();
        Accumulate accumulate = accNode.getAccumulate();

        for (TupleImpl rightTuple = srcRightTuples.getUpdateFirst(); rightTuple != null; ) {
            TupleImpl next = rightTuple.getStagedNext();

            if ( ltm != null && ltm.size() > 0 ) {
                TupleImpl childLeftTuple = rightTuple.getFirstChild();

                FastIterator leftIt = accNode.getLeftIterator( ltm );
                TupleImpl leftTuple = accNode.getFirstLeftTuple( rightTuple, ltm, leftIt );

                constraints.updateFromFactHandle( contextEntry,
                                                  reteEvaluator,
                                                  rightTuple.getFactHandleForEvaluation() );

                // first check our index (for indexed nodes only) hasn't changed and we are returning the same bucket
                // We assume a bucket change if leftTuple == null
                if ( childLeftTuple != null && ltm.isIndexed() && !leftIt.isFullIterator() && ( leftTuple == null || ( leftTuple.getMemory() != childLeftTuple.getLeftParent().getMemory() ) ) ) {
                    // our index has changed, so delete all the previous matches
                    removePreviousMatchesForRightTuple( accNode,
                                                        accumulate,
                                                        rightTuple,
                                                        reteEvaluator,
                                                        am,
                                                        childLeftTuple,
                                                        trgLeftTuples );
                    childLeftTuple = null; // null so the next check will attempt matches for new bucket
                }

                // if LeftTupleMemory is empty, there are no matches to modify
                if ( leftTuple != null ) {
                    doRightUpdatesProcessChildren( accNode,
                                                   am,
                                                   reteEvaluator,
                                                   bm,
                                                   constraints,
                                                   accumulate,
                                                   leftIt,
                                                   rightTuple,
                                                   childLeftTuple,
                                                   leftTuple,
                                                   trgLeftTuples );
                }
            }
            rightTuple.clearStaged();
            rightTuple = next;
        }
        constraints.resetFactHandle(contextEntry);
    }

    private void doRightUpdatesProcessChildren(AccumulateNode accNode,
                                               AccumulateMemory am,
                                               ReteEvaluator reteEvaluator,
                                               BetaMemory bm,
                                               BetaConstraints constraints,
                                               Accumulate accumulate,
                                               FastIterator<TupleImpl> leftIt,
                                               TupleImpl rightTuple,
                                               TupleImpl childLeftTuple,
                                               TupleImpl leftTuple,
                                               TupleSets trgLeftTuples) {
        if (childLeftTuple == null) {
            // either we are indexed and changed buckets or
            // we had no children before, but there is a bucket to potentially match, so try as normal assert
            for (; leftTuple != null; leftTuple =   leftIt.next(leftTuple)) {
                if (constraints.isAllowedCachedRight(leftTuple, bm.getContext()
                                                    )) {
                    if (leftTuple.getStagedType() == LeftTuple.NONE) {
                        trgLeftTuples.addUpdate(leftTuple);
                    }
                    final BaseAccumulation accctx = (BaseAccumulation) leftTuple.getContextObject();
                    // add a new match
                    addMatch(accNode, accumulate, leftTuple, rightTuple,
                             null, null, reteEvaluator, am,
                             accctx, true, false);
                }
            }
        } else {
            // in the same bucket, so iterate and compare
            for (; leftTuple != null; leftTuple = leftIt.next(leftTuple)) {
                if (constraints.isAllowedCachedRight(leftTuple, bm.getContext()
                                                    )) {
                    if (leftTuple.getStagedType() == LeftTuple.NONE) {
                        trgLeftTuples.addUpdate(leftTuple);
                    }
                    final BaseAccumulation accctx = (BaseAccumulation) leftTuple.getContextObject();
                    TupleImpl temp;
                    if (childLeftTuple != null && childLeftTuple.getLeftParent() == leftTuple) {
                        temp = childLeftTuple.getRightParentNext();
                        // we must re-add this to ensure deterministic iteration
                        childLeftTuple.reAddLeft();
                        removeMatch(accNode,
                                    accumulate,
                                    rightTuple,
                                    childLeftTuple,
                                    reteEvaluator,
                                    am,
                                    accctx,
                                    true);
                        childLeftTuple = temp;
                    }
                    // add a new match
                    addMatch(accNode, accumulate, leftTuple, rightTuple,
                             null, childLeftTuple, reteEvaluator, am,
                             accctx, true, false);
                } else if (childLeftTuple != null && childLeftTuple.getLeftParent() == leftTuple) {
                    if (leftTuple.getStagedType() == LeftTuple.NONE) {
                        trgLeftTuples.addUpdate(leftTuple);
                    }

                    TupleImpl temp = childLeftTuple.getRightParentNext();
                    final BaseAccumulation accctx = (BaseAccumulation) leftTuple.getContextObject();
                    // FIXME This will be really slow, if it re-accumulates on the same LeftTuple (MDP)
                    // remove the match
                    removeMatch(accNode,
                                accumulate,
                                rightTuple,
                                childLeftTuple,
                                reteEvaluator,
                                am,
                                accctx,
                                true);

                    childLeftTuple = temp;
                }
                // else do nothing, was false before and false now.
            }
        }
    }


    private void doLeftDeletes(AccumulateNode accNode,
                               AccumulateMemory am,
                               ReteEvaluator reteEvaluator,
                               TupleSets srcLeftTuples,
                               TupleSets trgLeftTuples,
                               TupleSets stagedLeftTuples) {
        BetaMemory bm = am.getBetaMemory();
        TupleMemory ltm = bm.getLeftTupleMemory();
        Accumulate accumulate = accNode.getAccumulate();

        for (TupleImpl leftTuple = srcLeftTuples.getDeleteFirst(); leftTuple != null; ) {
            TupleImpl next = leftTuple.getStagedNext();
            if (leftTuple.getMemory() != null) {
                // it may have been staged and never actually added
                ltm.remove(leftTuple);

                BaseAccumulation accctx = (BaseAccumulation) leftTuple.getContextObject();
                leftTuple.setContextObject( null );

                removePreviousMatchesForLeftTuple(accumulate, leftTuple, reteEvaluator, am, accctx, false);

                propagateDelete( trgLeftTuples, stagedLeftTuples, accctx );
            }

            leftTuple.clearStaged();
            leftTuple = next;
        }
    }

    protected void propagateDelete( TupleSets trgLeftTuples, TupleSets stagedLeftTuples, Object accPropCtx ) {
        AccumulateContextEntry entry =  (AccumulateContextEntry) accPropCtx;
        if ( entry.isPropagated() ) {
            normalizeStagedTuples( stagedLeftTuples, (TupleImpl) entry.getResultLeftTuple() );
            trgLeftTuples.addDelete( (TupleImpl) entry.getResultLeftTuple() );
        }
    }

    private void doRightDeletes(AccumulateNode accNode,
                                AccumulateMemory am,
                                ReteEvaluator reteEvaluator,
                                TupleSets srcRightTuples,
                                TupleSets trgLeftTuples) {
        TupleMemory rtm = am.getBetaMemory().getRightTupleMemory();
        Accumulate accumulate = accNode.getAccumulate();

        for (TupleImpl rightTuple = srcRightTuples.getDeleteFirst(); rightTuple != null; ) {
            TupleImpl next = rightTuple.getStagedNext();
            if (rightTuple.getMemory() != null) {
                // it may have been staged and never actually added
                rtm.remove(rightTuple);

                if (rightTuple.getFirstChild() != null) {
                    TupleImpl match = rightTuple.getFirstChild();

                    while (match != null) {
                        TupleImpl nextLeft = match.getRightParentNext();

                        TupleImpl leftTuple = match.getLeftParent();
                        final BaseAccumulation accctx = (BaseAccumulation) leftTuple.getContextObject();
                        // FIXME This will be really slow, if it re-accumulates on the same LeftTuple (MDP)
                        removeMatch(accNode, accumulate, rightTuple, match, reteEvaluator, am, accctx, true);

                        if (leftTuple.getStagedType() == LeftTuple.NONE) {
                            trgLeftTuples.addUpdate(leftTuple);
                        }

                        match = nextLeft;
                    }
                }
            }
            rightTuple.clearStaged();
            rightTuple = next;
        }
    }

    protected void evaluateResultConstraints(final AccumulateNode accNode,
                                            final LeftTupleSink sink,
                                            final Accumulate accumulate,
                                            final TupleImpl leftTuple,
                                            final PropagationContext context,
                                            final ReteEvaluator reteEvaluator,
                                            final AccumulateMemory memory,
                                            final BaseAccumulation accctx,
                                            final TupleSets trgLeftTuples,
                                            final TupleSets stagedLeftTuples) {

        PropagationContext propagationContext = accctx.getPropagationContext();
        accctx.setPropagationContext( null );

        Object result = accumulate.getResult(memory.workingMemoryContext, accctx, leftTuple, reteEvaluator);
        propagateResult( accNode, sink, leftTuple, context, reteEvaluator, memory, trgLeftTuples, stagedLeftTuples,
                         null, result, (AccumulateContextEntry) accctx, propagationContext, reteEvaluator.getRuleSessionConfiguration().isAccumulateNullPropagation());
    }

    protected final void propagateResult(AccumulateNode accNode, LeftTupleSink sink, TupleImpl leftTuple, PropagationContext context,
                                         ReteEvaluator reteEvaluator, AccumulateMemory memory, TupleSets trgLeftTuples,
                                         TupleSets stagedLeftTuples, Object key, Object result,
                                         AccumulateContextEntry accPropCtx, PropagationContext propagationContext, boolean allowNullPropagation) {
        if ( !allowNullPropagation && result == null) {
            if ( accPropCtx.isPropagated()) {
                // retract
                trgLeftTuples.addDelete( (TupleImpl) accPropCtx.getResultLeftTuple());
                accPropCtx.setPropagated( false );
            }
            return;
        }

        if ( accPropCtx.getResultFactHandle() == null) {
            InternalFactHandle handle = accNode.createResultFactHandle(context, reteEvaluator, leftTuple, createResult(accNode, key, result ));
            accPropCtx.setResultFactHandle(handle);
            accPropCtx.setResultLeftTuple(TupleFactory.createLeftTuple(handle, leftTuple, sink));
        } else {
            ((InternalFactHandle)accPropCtx.getResultFactHandle()).setObject( createResult(accNode, key, result) );
        }

        // First alpha node filters
        AlphaNodeFieldConstraint[] resultConstraints = accNode.getResultConstraints();
        BetaConstraints resultBinder = accNode.getResultBinder();
        boolean isAllowed = true;
        for ( AlphaNodeFieldConstraint resultConstraint : resultConstraints ) {
            if ( !resultConstraint.isAllowed( accPropCtx.getResultFactHandle(), reteEvaluator ) ) {
                isAllowed = false;
                break;
            }
        }
        if (isAllowed) {
            resultBinder.updateFromTuple( memory.resultsContext, reteEvaluator, leftTuple );
            if (!resultBinder.isAllowedCachedLeft( memory.resultsContext, accPropCtx.getResultFactHandle())) {
                isAllowed = false;
            }
            resultBinder.resetTuple( memory.resultsContext );
        }


        TupleImpl childLeftTuple = (TupleImpl) accPropCtx.getResultLeftTuple();
        childLeftTuple.setPropagationContext( propagationContext != null ? propagationContext : leftTuple.getPropagationContext() );

        if ( accPropCtx.isPropagated()) {
            normalizeStagedTuples( stagedLeftTuples, childLeftTuple );

            if (isAllowed) {
                // modify
                trgLeftTuples.addUpdate(childLeftTuple);
            } else {
                // retract
                trgLeftTuples.addDelete(childLeftTuple);
                accPropCtx.setPropagated( false );
            }
        } else if (isAllowed) {
            // assert
            trgLeftTuples.addInsert(childLeftTuple);
            accPropCtx.setPropagated( true );
        }
    }

    protected Object createResult( AccumulateNode accNode, Object key, Object result ) {
        return result;
    }

    private void addMatch(final AccumulateNode accNode,
                          final Accumulate accumulate,
                          final TupleImpl leftTuple,
                          final TupleImpl rightTuple,
                          final TupleImpl currentLeftChild,
                          final TupleImpl currentRightChild,
                          final ReteEvaluator reteEvaluator,
                          final AccumulateMemory am,
                          final BaseAccumulation accctx,
                          final boolean useLeftMemory,
                          final boolean leftPropagation) {
        TupleImpl tuple = leftTuple;
        InternalFactHandle handle = rightTuple.getFactHandle();

        if (accNode.getRightInput().inputIsTupleToObjectNode()) {
            // if there is a subnetwork, handle must be unwrapped
            tuple = rightTuple;
            handle = rightTuple.getFactHandleForEvaluation();
        }

        if (leftPropagation && handle.isExpired()) {
            return;
        }

        accctx.setPropagationContext(rightTuple.getPropagationContext());

        Object value = accumulate.accumulate(am.workingMemoryContext,
                                             accctx, tuple,
                                             handle, reteEvaluator);

        // in sequential mode, we don't need to keep record of matched tuples
        if (useLeftMemory) {
            // linking left and right by creating a new left tuple
            TupleImpl  match = TupleFactory.createLeftTuple(leftTuple, rightTuple,
                                                            currentLeftChild, currentRightChild,
                                                            accNode,true);

            postAccumulate(accNode, accctx, match);

            match.setContextObject(value);
        }
    }

    void postAccumulate(AccumulateNode accNode, Object accctx, TupleImpl match) {
        // this is only implemented by GroupBy
    }

    /**
     * Removes a match between left and right tuple
     */
    private boolean removeMatch(final AccumulateNode accNode,
                                final Accumulate accumulate,
                                final TupleImpl rightTuple,
                                final TupleImpl match,
                                final ReteEvaluator reteEvaluator,
                                final AccumulateMemory am,
                                final BaseAccumulation accctx,
                                final boolean reaccumulate) {
        // save the matching tuple
        TupleImpl leftParent = match.getLeftParent();
        TupleImpl rightParent = match.getRightParent();

        // removing link between left and right
        match.unlinkFromLeftParent();
        match.unlinkFromRightParent();

        // if there is a subnetwork, we need to unwrap the object from inside the tuple
        FactHandle handle = rightTuple.getFactHandle();
        TupleImpl tuple = leftParent;
        if (accNode.getRightInput().inputIsTupleToObjectNode()) {
            tuple = rightTuple;
            handle = rightTuple.getFactHandleForEvaluation();
        }

        // just reverse this single match
        boolean reversed = accumulate.tryReverse(am.workingMemoryContext,
                                                 accctx,
                                                 tuple,
                                                 handle,
                                                 match,
                                                 reteEvaluator);
        if (!reversed) {
            // otherwise need to recalculate all matches for the given leftTuple
            reaccumulateForLeftTuple(accNode,
                                     accumulate,
                                     leftParent,
                                     rightParent,
                                     match,
                                     reteEvaluator,
                                     am,
                                     accctx,
                                     reaccumulate);
        }

        return reversed;
    }

    protected void reaccumulateForLeftTuple(final AccumulateNode accNode,
                                            final Accumulate accumulate,
                                            final TupleImpl leftParent,
                                            final TupleImpl unused1,
                                            final TupleImpl unused2,
                                            final ReteEvaluator reteEvaluator,
                                            final AccumulateMemory am,
                                            final BaseAccumulation accctx,
                                            final boolean reaccumulate) {
        if (reaccumulate) {
            reinit(accumulate, leftParent, reteEvaluator, am, accctx);

            for (TupleImpl childMatch = leftParent.getFirstChild(); childMatch != null; childMatch = childMatch.getHandleNext()) {
                TupleImpl         rightTuple  = childMatch.getRightParent();
                FactHandle childHandle = rightTuple.getFactHandle();
                TupleImpl          tuple       = leftParent;
                if (accNode.getRightInput().inputIsTupleToObjectNode()) {
                    // if there is a subnetwork, handle must be unwrapped
                    tuple = rightTuple;
                    childHandle = rightTuple.getFactHandleForEvaluation();
                }

                Object value = accumulate.accumulate(am.workingMemoryContext, accctx, tuple, childHandle, reteEvaluator);
                postAccumulate(accNode, accctx, childMatch);
                childMatch.setContextObject(value);
            }
        }
    }

    private void removePreviousMatchesForRightTuple(final AccumulateNode accNode,
                                                    final Accumulate accumulate,
                                                    final TupleImpl rightTuple,
                                                    final ReteEvaluator reteEvaluator,
                                                    final AccumulateMemory memory,
                                                    final TupleImpl firstChild,
                                                    final TupleSets trgLeftTuples) {
        for (TupleImpl match = firstChild; match != null; ) {
            final TupleImpl next = match.getRightParentNext();

            final TupleImpl leftTuple = match.getLeftParent();
            final BaseAccumulation accctx = (BaseAccumulation) leftTuple.getContextObject();
            removeMatch(accNode,
                        accumulate,
                        rightTuple,
                        match,
                        reteEvaluator,
                        memory,
                        accctx,
                        true);

            if (leftTuple.getStagedType() == LeftTuple.NONE) {
                trgLeftTuples.addUpdate(leftTuple);
            }

            match = next;
        }
    }

    private static void removePreviousMatchesForLeftTuple(final Accumulate accumulate,
                                                          final TupleImpl leftTuple,
                                                          final ReteEvaluator reteEvaluator,
                                                          final AccumulateMemory memory,
                                                          final BaseAccumulation accctx,
                                                          boolean reInit) {
        for (TupleImpl match = leftTuple.getFirstChild(); match != null; ) {
            TupleImpl next = match.getHandleNext();
            match.unlinkFromRightParent();
            match.unlinkFromLeftParent();
            match = next;
        }

        if (reInit) {
            reinit(accumulate, leftTuple, reteEvaluator, memory, accctx);
        }
    }

    private static void reinit(Accumulate accumulate, TupleImpl leftTuple, ReteEvaluator reteEvaluator, AccumulateMemory memory, BaseAccumulation accctx) {
        // Create the function context, but allow init to override it.
        Object funcContext = ((AccumulateContextEntry) accctx).getFunctionContext();
        funcContext = accumulate.init(memory.workingMemoryContext, accctx, funcContext, leftTuple, reteEvaluator);
        ((AccumulateContextEntry) accctx).setFunctionContext(funcContext);
    }
}
