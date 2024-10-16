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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.core.common.BaseNode;
import org.drools.core.common.DefaultEventHandle;
import org.drools.core.common.InternalAgenda;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.Memory;
import org.drools.core.common.MemoryFactory;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.PropagationContextFactory;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.SuperCacheFixer;
import org.drools.core.common.TupleSets;
import org.drools.core.impl.InternalRuleBase;
import org.drools.core.reteoo.AbstractTerminalNode;
import org.drools.core.reteoo.AccumulateNode;
import org.drools.core.reteoo.AccumulateNode.AccumulateContext;
import org.drools.core.reteoo.AccumulateNode.AccumulateMemory;
import org.drools.core.reteoo.AlphaTerminalNode;
import org.drools.core.reteoo.AsyncReceiveNode;
import org.drools.core.reteoo.AsyncSendNode;
import org.drools.core.reteoo.BetaMemory;
import org.drools.core.reteoo.BetaNode;
import org.drools.core.reteoo.RightInputAdapterNode;
import org.drools.core.reteoo.ConditionalBranchNode;
import org.drools.core.reteoo.EvalConditionNode;
import org.drools.core.reteoo.FromNode;
import org.drools.core.reteoo.FromNode.FromMemory;
import org.drools.core.reteoo.LeftInputAdapterNode;
import org.drools.core.reteoo.LeftTuple;
import org.drools.core.reteoo.LeftTupleNode;
import org.drools.core.reteoo.LeftTupleSink;
import org.drools.core.reteoo.LeftTupleSinkNode;
import org.drools.core.reteoo.LeftTupleSource;
import org.drools.core.reteoo.ObjectSink;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.PathEndNode;
import org.drools.core.reteoo.PathMemory;
import org.drools.core.reteoo.QueryElementNode;
import org.drools.core.reteoo.TupleToObjectNode;
import org.drools.core.reteoo.TupleToObjectNode.SubnetworkPathMemory;
import org.drools.core.reteoo.RightTuple;
import org.drools.core.reteoo.RuleTerminalNodeLeftTuple;
import org.drools.core.reteoo.RuntimeComponentFactory;
import org.drools.core.reteoo.SegmentMemory;
import org.drools.core.reteoo.SegmentNodeMemory;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.reteoo.TimerNode;
import org.drools.core.reteoo.Tuple;
import org.drools.core.reteoo.TupleFactory;
import org.drools.core.reteoo.TupleImpl;
import org.drools.core.reteoo.TupleMemory;
import org.drools.core.reteoo.WindowNode;
import org.drools.core.util.FastIterator;
import org.kie.api.definition.rule.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.drools.core.phreak.BuildtimeSegmentUtilities.JOIN_NODE_BIT;
import static org.drools.core.phreak.BuildtimeSegmentUtilities.NOT_NODE_BIT;
import static org.drools.core.phreak.BuildtimeSegmentUtilities.REACTIVE_EXISTS_NODE_BIT;
import static org.drools.core.phreak.BuildtimeSegmentUtilities.canBeDisabled;
import static org.drools.core.phreak.BuildtimeSegmentUtilities.isNonTerminalTipNode;
import static org.drools.core.phreak.BuildtimeSegmentUtilities.isRootNode;
import static org.drools.core.phreak.BuildtimeSegmentUtilities.isSet;
import static org.drools.core.phreak.BuildtimeSegmentUtilities.nextNodePosMask;
import static org.drools.core.phreak.BuildtimeSegmentUtilities.updateNodeTypesMask;
import static org.drools.core.phreak.EagerPhreakBuilder.Add.attachAdapterAndPropagate;
import static org.drools.core.phreak.EagerPhreakBuilder.deleteLeftTuple;
import static org.drools.core.phreak.RuntimeSegmentUtilities.createSubnetworkSegmentMemory;
import static org.drools.core.phreak.RuntimeSegmentUtilities.getOrCreateSegmentMemory;
import static org.drools.core.phreak.RuntimeSegmentUtilities.getQuerySegmentMemory;
import static org.drools.core.phreak.TupleEvaluationUtil.forceFlushLeftTuple;

class LazyPhreakBuilder implements PhreakBuilder {

    private static final Logger log = LoggerFactory.getLogger(LazyPhreakBuilder.class);

    /**
     * This method is called after the rule nodes have been added to the network
     * For add tuples are processed after the segments and pmems have been adjusted
     */
    @Override
    public void addRule(TerminalNode tn, Collection<InternalWorkingMemory> wms, InternalRuleBase kBase) {
        if (log.isTraceEnabled()) {
            log.trace("Adding Rule {}", tn.getRule().getName());
        }

        boolean hasProtos = kBase.hasSegmentPrototypes();
        boolean hasWms = !wms.isEmpty();

        if (!hasProtos && !hasWms) {
            return;
        }

        RuleImpl rule = tn.getRule();
        LeftTupleNode firstSplit = getNetworkSplitPoint(tn);
        PathEndNodes pathEndNodes = getPathEndNodes(kBase, firstSplit, tn, rule, hasProtos, hasWms);

        // Insert the facts for the new paths. This will iterate each new path from EndNode to the splitStart - but will not process the splitStart itself (as tha already exist).
        // It does not matter that the prior segments have not yet been processed for splitting, as this will only apply for branches of paths that did not exist before

        for (InternalWorkingMemory wm : wms) {
            wm.flushPropagations();

            if (NodeTypeEnums.isLeftInputAdapterNode(firstSplit) && firstSplit.getAssociatedTerminalsSize() == 1) {
                // rule added with no sharing
                insertLiaFacts(firstSplit, wm);
            } else {
                PathEndNodeMemories tnms = getPathEndMemories(wm, pathEndNodes);

                if (tnms.subjectPmem == null) {
                    // If the existing PathMemories are not yet initialized there are no Segments or tuples to process
                    continue;
                }

                Map<PathMemory, SegmentMemory[]> prevSmemsLookup = reInitPathMemories(tnms.otherPmems, null);

                // must collect all visited SegmentMemories, for link notification
                Set<SegmentMemory> smemsToNotify = handleExistingPaths(tn, prevSmemsLookup, tnms.otherPmems, wm, ExistingPathStrategy.ADD_STRATEGY);

                addNewPaths(wm, smemsToNotify, tnms.subjectPmems);

                processLeftTuples(firstSplit, wm, true, rule);

                notifySegments(smemsToNotify, wm);
            }
        }

        if (hasWms) {
            insertFacts( pathEndNodes, wms );
        } else {
            for (PathEndNode node : pathEndNodes.otherEndNodes) {
                node.resetPathMemSpec(null);
            }
        }
    }

    /**
     * This method is called before the rule nodes are removed from the network.
     * For remove tuples are processed before the segments and pmems have been adjusted
     */
    @Override
    public void removeRule( TerminalNode tn, Collection<InternalWorkingMemory> wms, InternalRuleBase kBase) {
        if (log.isTraceEnabled()) {
            log.trace("Removing Rule {}", tn.getRule().getName());
        }

        boolean hasProtos = kBase.hasSegmentPrototypes();
        boolean hasWms = !wms.isEmpty();

        if (!hasProtos && !hasWms) {
            return;
        }

        RuleImpl      rule       = tn.getRule();
        LeftTupleNode firstSplit = getNetworkSplitPoint(tn);
        PathEndNodes pathEndNodes = getPathEndNodes(kBase, firstSplit, tn, rule, hasProtos, hasWms);

        for (InternalWorkingMemory wm : wms) {
            wm.flushPropagations();

            PathEndNodeMemories tnms = getPathEndMemories(wm, pathEndNodes);

            if ( !tnms.subjectPmems.isEmpty() ) {
                if (NodeTypeEnums.isLeftInputAdapterNode(firstSplit) && firstSplit.getAssociatedTerminalsSize() == 1) {
                    if (tnms.subjectPmem != null) {
                        flushStagedTuples(firstSplit, tnms.subjectPmem, wm);
                    }

                    processLeftTuples(firstSplit, wm, false, tn.getRule());

                    removeNewPaths(wm, tnms.subjectPmems);
                } else {
                    flushStagedTuples(tn, tnms.subjectPmem, pathEndNodes.subjectSplits, wm);

                    processLeftTuples(firstSplit, wm, false, tn.getRule());

                    removeNewPaths(wm, tnms.subjectPmems);

                    Map<PathMemory, SegmentMemory[]> prevSmemsLookup = reInitPathMemories(tnms.otherPmems, tn);

                    // must collect all visited SegmentMemories, for link notification
                    Set<SegmentMemory> smemsToNotify = handleExistingPaths(tn, prevSmemsLookup, tnms.otherPmems, wm, ExistingPathStrategy.REMOVE_STRATEGY);

                    notifySegments(smemsToNotify, wm);
                }
            }

            if (tnms.subjectPmem != null && tnms.subjectPmem.isInitialized() && tnms.subjectPmem.getRuleAgendaItem().isQueued()) {
                // SubjectPmem can be null, if it was never initialized
                tnms.subjectPmem.getRuleAgendaItem().dequeue();
            }
        }

        if (!hasWms) {
            for (PathEndNode node : pathEndNodes.otherEndNodes) {
                node.resetPathMemSpec(null);
            }
        }
    }

    public interface ExistingPathStrategy {
        ExistingPathStrategy ADD_STRATEGY = new AddExistingPaths();
        ExistingPathStrategy REMOVE_STRATEGY = new RemoveExistingPaths();

        SegmentMemory[] getSegmenMemories(PathMemory pmem);

        void adjustSegment(InternalWorkingMemory wm, Set<SegmentMemory> smemsToNotify, SegmentMemory smem, int smemSplitAdjustAmount);

        void handleSplit(PathMemory pmem, SegmentMemory[] prevSmems, SegmentMemory[] smems, int smemIndex, int prevSmemIndex,
                         LeftTupleNode parentNode, LeftTupleNode node, TerminalNode tn,
                         Set<LeftTupleNode> visited, Set<SegmentMemory> smemsToNotify, Map<LeftTupleNode, SegmentMemory> nodeToSegmentMap,
                         InternalWorkingMemory wm);

        void processSegmentMemories(SegmentMemory[] smems, PathMemory pmem);

        int incSmemIndex1(int smemIndex);
        int incSmemIndex2(int smemIndex);

        int incPrevSmemIndex1(int prevSmemIndex);
        int incPrevSmemIndex2(int prevSmemIndex);
    }

    public static class AddExistingPaths implements ExistingPathStrategy {

        @Override
        public SegmentMemory[] getSegmenMemories(PathMemory pmem) {
            return pmem.getSegmentMemories();
        }

        @Override
        public void adjustSegment(InternalWorkingMemory wm, Set<SegmentMemory> smemsToNotify, SegmentMemory smem, int smemSplitAdjustAmount) {
            smemsToNotify.add(smem);
            smem.unlinkSegment(wm);
            correctSegmentMemoryAfterSplitOnAdd(smem, smemSplitAdjustAmount);
        }

        @Override
        public void handleSplit(PathMemory pmem, SegmentMemory[] prevSmems, SegmentMemory[] smems, int smemIndex, int prevSmemIndex,
                                LeftTupleNode parentNode, LeftTupleNode node, TerminalNode tn,
                                Set<LeftTupleNode> visited, Set<SegmentMemory> smemsToNotify, Map<LeftTupleNode, SegmentMemory> nodeToSegmentMap,
                                InternalWorkingMemory wm) {
            if (smems[smemIndex - 1] != null) {
                SegmentMemory sm2 = nodeToSegmentMap.get(node);
                if (sm2 == null) {
                    SegmentMemory sm1 = smems[smemIndex - 1];
                    correctMemoryOnSplitsChanged(parentNode, null, wm);
                    sm2 = splitSegment(wm, sm1, parentNode);
                    nodeToSegmentMap.put(node, sm2);
                    smemsToNotify.add(sm1);
                    smemsToNotify.add(sm2);
                }

                smems[smemIndex] = sm2;
            }
        }

        @Override
        public void processSegmentMemories(SegmentMemory[] smems, PathMemory pmem) {

        }

        @Override
        public int incSmemIndex1(int smemIndex) {
            return smemIndex+1;
        }


        @Override
        public int incPrevSmemIndex1(int prevSmemIndex) {
            return prevSmemIndex;
        }

        @Override
        public int incSmemIndex2(int smemIndex) {
            return smemIndex;
        }

        @Override
        public int incPrevSmemIndex2(int prevSmemIndex) {
            return prevSmemIndex+1;
        }
    }

    public static class RemoveExistingPaths implements ExistingPathStrategy {

        @Override
        public SegmentMemory[] getSegmenMemories(PathMemory pmem) {
            return new SegmentMemory[ pmem.getSegmentMemories().length ];
        }

        @Override
        public void adjustSegment(InternalWorkingMemory wm, Set<SegmentMemory> smemsToNotify, SegmentMemory smem, int smemSplitAdjustAmount) {
            smemsToNotify.add(smem);
            smem.unlinkSegment(wm);
            correctSegmentMemoryAfterSplitOnRemove(smem, smemSplitAdjustAmount);
        }

        @Override
        public void handleSplit(PathMemory pmem, SegmentMemory[] prevSmems, SegmentMemory[] smems, int smemIndex, int prevSmemIndex,
                                LeftTupleNode parentNode, LeftTupleNode node, TerminalNode tn,
                                Set<LeftTupleNode> visited, Set<SegmentMemory> smemsToNotify, Map<LeftTupleNode, SegmentMemory> nodeToSegmentMap,
                                InternalWorkingMemory wm) {
            if (visited.contains(node)) {
                return;
            }

            correctMemoryOnSplitsChanged(parentNode, tn, wm);

            SegmentMemory sm1 = smems[smemIndex];
            SegmentMemory sm2 = prevSmems[prevSmemIndex];

            if (sm1 != null || sm2 != null) {
                // Temporarily remove the terminal node of the rule to be removed from the rete network to avoid that
                // its path memory could be added to an existing segment memory during the merge of 2 segments
                LeftTupleSource removedTerminalSource = tn.getLeftTupleSource();
                removedTerminalSource.removeTupleSink( tn );

                if (sm1 == null) {
                    sm1 = createChildSegment(wm, parentNode);
                    smems[smemIndex] = sm1;
                    sm1.add(sm2);
                } else if (sm2 == null) {
                    sm2 = createChildSegment(wm, node);
                    prevSmems[prevSmemIndex] = sm2;
                    sm1.add(sm2);
                }

                mergeSegment(sm1, sm2);
                smemsToNotify.add(sm1);
                sm1.unlinkSegment(wm);
                sm2.unlinkSegment(wm);
                visited.add(node);

                // Add back the the terminal node of the rule to be removed into the rete network to permit the network
                // traversal up from it and the removal of all the nodes exclusively belonging to the removed rule
                removedTerminalSource.addTupleSink( tn );
            }
        }

        @Override
        public void processSegmentMemories(SegmentMemory[] smems, PathMemory pmem) {
            for (int i = 0; i < smems.length; i++) {
                if (smems[i] != null) {
                    pmem.setSegmentMemory( smems[i].getPos(), smems[i] );
                }
            }
        }

        @Override
        public int incSmemIndex1(int smemIndex) {
            return smemIndex;
        }

        @Override
        public int incPrevSmemIndex1(int prevSmemIndex) {
            return prevSmemIndex+1;
        }

        @Override
        public int incSmemIndex2(int smemIndex) {
            return smemIndex+1;
        }

        @Override
        public int incPrevSmemIndex2(int prevSmemIndex) {
            return prevSmemIndex;
        }
    }

    private static Set<SegmentMemory> handleExistingPaths(TerminalNode tn, Map<PathMemory, SegmentMemory[]> prevSmemsLookup,
                                                          List<PathMemory> pmems, InternalWorkingMemory wm, ExistingPathStrategy strategy) {
        Set<SegmentMemory>                smemsToNotify    = new HashSet<>();
        Set<SegmentMemory>                visitedSegments  = new HashSet<>();
        Set<LeftTupleNode> visitedNodes = new HashSet<>();
        Map<LeftTupleNode, SegmentMemory> nodeToSegmentMap = new HashMap<>();

        for (PathMemory pmem : pmems) {
            LeftTupleNode[] nodes = pmem.getPathEndNode().getPathNodes();

            SegmentMemory[] prevSmems = prevSmemsLookup.get(pmem);
            SegmentMemory[] smems     = strategy.getSegmenMemories(pmem);

            LeftTupleNode node;
            int           prevSmemIndex         = 0;
            int           smemIndex             = 0;
            int           smemSplitAdjustAmount = 0;
            int           nodeIndex             = 0;
            int           nodeTypesInSegment    = 0;

            // excluding the rule just added iterate while not split (i.e. find the next split, prior to this rule being added)
            // note it's checking for when the parent is the split, and thus node is the next root root.

            smems[smemIndex] = prevSmems[prevSmemIndex];
            do {
                node = nodes[nodeIndex++];
                LeftTupleSource parentNode = node.getLeftTupleSource();
                nodeTypesInSegment = BuildtimeSegmentUtilities.updateNodeTypesMask( parentNode, nodeTypesInSegment );
                if (isSplit(parentNode)) {
                    smemIndex = strategy.incSmemIndex1(smemIndex);
                    prevSmemIndex = strategy.incPrevSmemIndex1(prevSmemIndex);
                    if (isSplit(parentNode, tn)) { // check if the split is there even without the processed rule
                        smemIndex = strategy.incSmemIndex2(smemIndex);
                        prevSmemIndex = strategy.incPrevSmemIndex2(prevSmemIndex);
                        smems[smemIndex] = prevSmems[prevSmemIndex];
                        if ( smems[smemIndex] != null && smemSplitAdjustAmount > 0 && visitedSegments.add(smems[smemIndex])) {
                            strategy.adjustSegment( wm, smemsToNotify, smems[smemIndex], smemSplitAdjustAmount );
                        }
                    } else {
                        strategy.handleSplit(pmem, prevSmems, smems, smemIndex, prevSmemIndex,
                                parentNode, node, tn, visitedNodes,
                                smemsToNotify, nodeToSegmentMap, wm);
                        smemSplitAdjustAmount++;
                    }
                    checkEagerSegmentCreation(parentNode, wm, nodeTypesInSegment );
                    nodeTypesInSegment = 0;
                }
            } while (!NodeTypeEnums.isEndNode(node));
            strategy.processSegmentMemories(smems, pmem);
        }
        return smemsToNotify;
    }

    private static void addNewPaths(InternalWorkingMemory wm, Set<SegmentMemory> smemsToNotify, List<PathMemory> pmems) {
        // Multiple paths may be renetrant, in the case of a second subnetwork on the same rule.
        // Must make sure we don't duplicate the child smem, and when found, just update it with new pmem.
        Set<LeftTupleNode> visited = new HashSet<>();
        for (PathMemory pmem : pmems) {
            LeftTupleSink tipNode = pmem.getPathEndNode();

            LeftTupleNode child  = tipNode;
            LeftTupleNode parent = tipNode.getLeftTupleSource();

            while (true) {
                if (visited.add(child)) {
                    if ( parent != null && parent.getAssociatedTerminalsSize() != 1 && child.getAssociatedTerminalsSize() == 1 ) {
                        // This is the split point that the new path enters an existing path.
                        // If the parent has other child SegmentMemorys then it must create a new child SegmentMemory
                        // If the parent is a query node, then it's internal data structure needs changing
                        // all right input data must be propagated
                        Memory mem = wm.getNodeMemories().peekNodeMemory( parent );
                        if ( mem != null && mem.getSegmentMemory() != null ) {
                            SegmentMemory sm = mem.getSegmentMemory();
                            if ( sm.getFirst() != null && sm.size() < parent.getSinkPropagator().size()) {
                                LeftTupleSink[] sinks = parent.getSinkPropagator().getSinks();
                                for (int i = sm.size(); i < sinks.length; i++) {
                                    SegmentMemory childSmem = createChildSegment( wm, sinks[i] );
                                    sm.add( childSmem );
                                    pmem.setSegmentMemory( childSmem.getPos(), childSmem );
                                    smemsToNotify.add( childSmem );
                                }
                            }
                            correctMemoryOnSplitsChanged( parent, null, wm );
                        }
                    } else {
                        Memory mem = wm.getNodeMemories().peekNodeMemory( child );
                        // The root of each segment
                        if ( mem != null ) {
                            SegmentMemory sm = mem.getSegmentMemory();
                            if ( sm != null && !sm.getPathMemories().contains( pmem ) ) {
                                RuntimeSegmentUtilities.addSegmentToPathMemory(pmem, sm);
                                sm.notifyRuleLinkSegment( wm, pmem );
                            }
                        }
                    }
                } else {
                    Memory mem = wm.getNodeMemories().peekNodeMemory( child );
                    if ( mem != null ) {
                        mem.getSegmentMemory().notifyRuleLinkSegment( wm, pmem );
                    }
                }

                if (parent == null) {
                    break;
                }

                child = parent;
                parent = parent.getLeftTupleSource();
            }
        }
    }


    private static void removeNewPaths(InternalWorkingMemory wm, List<PathMemory> pmems) {
        Set<Integer> visitedNodes = new HashSet<>();
        for (PathMemory pmem : pmems) {
            LeftTupleSink tipNode = pmem.getPathEndNode();

            LeftTupleNode child  = tipNode;
            LeftTupleNode parent = tipNode.getLeftTupleSource();

            while (true) {
                if (child.getAssociatedTerminalsSize() == 1 && NodeTypeEnums.isBetaNode(child)) {
                    // If this is a beta node, it'll delete all the right input data
                    deleteRightInputData((LeftTupleSink) child, wm);
                }

                if (parent != null && parent.getAssociatedTerminalsSize() != 1 && child.getAssociatedTerminalsSize() == 1) {
                    // This is the split point that the new path enters an existing path.
                    // If the parent has other child SegmentMemorys then it must create a new child SegmentMemory
                    // If the parent is a query node, then it's internal data structure needs changing
                    // all right input data must be propagated
                    if (!visitedNodes.contains( child.getId() )) {
                        Memory mem = wm.getNodeMemories().peekNodeMemory( parent );
                        if ( mem != null && mem.getSegmentMemory() != null ) {
                            SegmentMemory sm = mem.getSegmentMemory();
                            if ( sm.getFirst() != null ) {
                                SegmentMemory childSm = wm.getNodeMemories().peekNodeMemory( child ).getSegmentMemory();
                                sm.remove( childSm );
                            }
                        }
                    }
                } else {
                    Memory mem = wm.getNodeMemories().peekNodeMemory(child);
                    // The root of each segment
                    if (mem != null) {
                        SegmentMemory sm = mem.getSegmentMemory();
                        if (sm != null && sm.getPathMemories().contains( pmem )) {
                            mem.getSegmentMemory().removePathMemory(pmem);
                        }
                    }
                }

                if (parent == null) {
                    break;
                }

                visitedNodes.add(child.getId());
                child = parent;
                parent = parent.getLeftTupleSource();
            }
        }
    }

    private static boolean isSplit(LeftTupleNode node) {
        return isSplit(node, null);
    }

    private static boolean isSplit(LeftTupleNode node, TerminalNode removingTN) {
        return node != null && BuildtimeSegmentUtilities.isTipNode( node, removingTN );
    }

    public static class Flushed {
        SegmentMemory segmentMemory;
        PathMemory pathMemory;

        public Flushed(SegmentMemory segmentMemory, PathMemory pathMemory) {
            this.segmentMemory = segmentMemory;
            this.pathMemory = pathMemory;
        }
    }

    public static void flushStagedTuples(TerminalNode tn, PathMemory pmem, List<LeftTupleNode> splits, InternalWorkingMemory wm) {
        // first flush the subject rule, then flush any staging lists that are part of a merge
        if ( pmem.isInitialized() ) {
            RuleNetworkEvaluator.INSTANCE.evaluateNetwork(pmem, pmem.getRuleAgendaItem().getRuleExecutor(), wm);
        }

        // With the removing rules being flushed, we need to check any splits that will be merged, to see if they need flushing
        // Beware that flushing a higher up node, might again cause lower nodes to have more staged items. So track flushed items
        // incase they need to be reflushed
        List<Flushed> flushed = new ArrayList<>();

        for (LeftTupleNode node : splits) {
            if (!isSplit(node, tn)) { // check if the split is there even without the processed rule
                Memory mem = wm.getNodeMemories().peekNodeMemory(node);
                if ( mem != null) {
                    SegmentMemory smem = mem.getSegmentMemory();

                    if ( !smem.isEmpty() ) {
                        for ( SegmentMemory childSmem = smem.getFirst(); childSmem != null; childSmem = childSmem.getNext() ) {
                            if ( !childSmem.getStagedLeftTuples().isEmpty() ) {
                                PathMemory childPmem = childSmem.getPathMemories().get(0);
                                flushed.add( new Flushed(childSmem, childPmem));
                                forceFlushLeftTuple(childPmem, childSmem, wm, childSmem.getStagedLeftTuples().takeAll());
                            }
                        }
                    }
                }
            }
        }

        int flushCount = 1; // need to ensure that there is one full iteration, without any flushing. To avoid one flush causing populat of another already flushed segment
        while (!flushed.isEmpty() && flushCount != 0) {
            flushCount = 0;
            for (Flushed path : flushed) {
                if ( !path.segmentMemory.getStagedLeftTuples().isEmpty() ) {
                    flushCount++;
                    forceFlushLeftTuple(pmem, path.segmentMemory, wm, path.segmentMemory.getStagedLeftTuples().takeAll());
                }
            }
        }
    }

    private static void flushStagedTuples(LeftTupleNode splitStartNode, PathMemory pmem, InternalWorkingMemory wm) {
        if ( !pmem.isInitialized() ) {
            // The rule has never been linked in and evaluated, so there will be nothing to flush.
            return;
        }
        int             smemIndex = getSegmentPos(splitStartNode); // index before the segments are merged
        SegmentMemory[] smems     = pmem.getSegmentMemories();

        SegmentMemory   sm        = null;

        // If there is no sharing, then there will not be any staged tuples in later segemnts, and thus no need to search for them if the current sm is empty.
        int length = smems.length;
        if ( splitStartNode.getAssociatedTerminalsSize() == 1 ) {
            length = 1;
        }

        while (smemIndex < length) {
            sm = smems[smemIndex];
            if (sm != null && !sm.getStagedLeftTuples().isEmpty()) {
                break;
            }
            smemIndex++;
        }

        if ( smemIndex < length ) {
            // it only found a SM that needed flushing, if smemIndex < length
            forceFlushLeftTuple(pmem, sm, wm, sm.getStagedLeftTuples().takeAll());
        }
    }

    private static Map<PathMemory, SegmentMemory[]> reInitPathMemories(List<PathMemory> pathMems, TerminalNode removingTN) {
        Map<PathMemory, SegmentMemory[]> previousSmems = new HashMap<>();
        for (PathMemory pmem : pathMems) {
            // Re initialise all the PathMemories
            previousSmems.put(pmem, pmem.getSegmentMemories());

            PathEndNode pathEndNode = pmem.getPathEndNode();
            pathEndNode.resetPathMemSpec(removingTN); // re-initialise the PathMemory
            AbstractTerminalNode.initPathMemory(pathEndNode, pmem);
        }
        return previousSmems;
    }

    private static void notifySegments(Set<SegmentMemory> smems, InternalWorkingMemory wm) {
        for (SegmentMemory sm : smems) {
            sm.notifyRuleLinkSegment(wm);
        }
    }

    private static void correctMemoryOnSplitsChanged(LeftTupleNode splitStart, TerminalNode removingTN, InternalWorkingMemory wm) {
        if (splitStart.getType() == NodeTypeEnums.QueryElementNode) {
            QueryElementNode.QueryElementNodeMemory mem = (QueryElementNode.QueryElementNodeMemory) wm.getNodeMemories().peekNodeMemory(splitStart);
            if (mem != null) {
                mem.correctMemoryOnSinksChanged(removingTN);
            }
        }
    }


    public static void correctSegmentMemoryAfterSplitOnAdd(SegmentMemory sm) {
        correctSegmentMemoryAfterSplitOnAdd(sm, 1);
    }

    public static void correctSegmentMemoryAfterSplitOnAdd(SegmentMemory sm, int i) {
        sm.setPos(sm.getPos() + i);
        sm.setSegmentPosMaskBit(sm.getSegmentPosMaskBit() << i);
    }

    public static void correctSegmentMemoryAfterSplitOnRemove(SegmentMemory sm, int i) {
        sm.setPos(sm.getPos() - i);
        sm.setSegmentPosMaskBit(sm.getSegmentPosMaskBit() >> i);
    }

    private static int getSegmentPos(LeftTupleNode lts) {
        int counter = 0;
        while (!NodeTypeEnums.isLeftInputAdapterNode(lts)) {
            lts = lts.getLeftTupleSource();
            if (BuildtimeSegmentUtilities.isTipNode(lts, null)) {
                counter++;
            }
        }
        return counter;
    }

    private static void insertLiaFacts(LeftTupleNode startNode, InternalWorkingMemory wm) {
        // rule added with no sharing
        PropagationContextFactory pctxFactory = RuntimeComponentFactory.get().getPropagationContextFactory();
        final PropagationContext  pctx        = pctxFactory.createPropagationContext(wm.getNextPropagationIdCounter(), PropagationContext.Type.RULE_ADDITION, null, null, null);
        LeftInputAdapterNode      lian        = (LeftInputAdapterNode) startNode;
        attachAdapterAndPropagate(wm, lian, pctx);
    }

    private static void insertFacts(PathEndNodes endNodes, Collection<InternalWorkingMemory> wms) {
        Set<LeftTupleNode> visited = new HashSet<>();

        for ( PathEndNode endNode : endNodes.subjectEndNodes ) {
            LeftTupleNode[]  nodes = endNode.getPathNodes();
            for ( int i = 0; i < nodes.length; i++ ) {
                LeftTupleNode node = nodes[i];
                if  ( NodeTypeEnums.isBetaNode(node) && node.getAssociatedTerminalsSize() == 1 ) {
                    if (!visited.add( node )) {
                        continue;// this is to avoid rentering a path, and processing nodes twice. This can happen for nested subnetworks.
                    }
                    BetaNode bn = (BetaNode) node;

                    if (!bn.getRightInput().inputIsTupleToObjectNode()) {
                        for ( InternalWorkingMemory wm : wms ) {
                            attachAdapterAndPropagate(wm, bn);
                        }
                    }
                }
            }
        }
    }

    private static void deleteRightInputData(LeftTupleSink node, InternalWorkingMemory wm) {
        if (wm.getNodeMemories().peekNodeMemory(node) != null) {
            BetaNode       bn = (BetaNode) node;
            BetaMemory bm;
            if (bn.getType() == NodeTypeEnums.AccumulateNode) {
                bm = ((AccumulateMemory) wm.getNodeMemory(bn)).getBetaMemory();
            } else {
                bm = (BetaMemory) wm.getNodeMemory(bn);
            }

            TupleMemory  rtm = bm.getRightTupleMemory();
            FastIterator<TupleImpl> it  = rtm.fullFastIterator();
            for (TupleImpl rightTuple = BetaNode.getFirstTuple(rtm, it); rightTuple != null; ) {
                TupleImpl next = it.next(rightTuple);
                rtm.remove(rightTuple);
                rightTuple.unlinkFromRightParent();
                rightTuple = next;
            }

            if (!bm.getStagedRightTuples().isEmpty()) {
                bm.setNodeDirtyWithoutNotify();
            }
            TupleSets srcRightTuples = bm.getStagedRightTuples().takeAll();

            unlinkRightTuples(srcRightTuples.getInsertFirst());
            unlinkRightTuples(srcRightTuples.getUpdateFirst());
            unlinkRightTuples(srcRightTuples.getDeleteFirst());

            deleteFactsFromRightInput(bn, wm);
        }
    }

    private static void deleteFactsFromRightInput(BetaNode bn, InternalWorkingMemory wm) {
        BaseNode source = bn.getRightInput().getParent();
        if (source.getType() == NodeTypeEnums.WindowNode) {
            WindowNode.WindowMemory memory = wm.getNodeMemory(((WindowNode) source));
            for (DefaultEventHandle factHandle : memory.getFactHandles()) {
                factHandle.forEachRightTuple( rt -> {
                    if (source.equals(rt.getSink())) {
                        rt.unlinkFromRightParent();
                    }
                });
            }
        }
    }

    private static void unlinkRightTuples(TupleImpl rightTuple) {
        for (TupleImpl rt = rightTuple; rt != null; ) {
            TupleImpl next = rt.getStagedNext();
            // this RightTuple could have been already unlinked by the former cycle
            if (rt.getFactHandle() != null) {
                rt.unlinkFromRightParent();
            }
            rt = next;
        }
    }

    /**
     * Populates the SegmentMemory with staged LeftTuples. If the parent is not a Beta or From node, it iterates up to find the first node with memory. If necessary
     * It traverses to the LiaNode's ObjectTypeNode. It then iterates the LeftTuple chains, where an existing LeftTuple is staged
     * as delete. Or a new LeftTuple is created and staged as an insert.
     */
    private static void processLeftTuples(LeftTupleNode node, InternalWorkingMemory wm, boolean insert, Rule rule) {
        // *** if you make a fix here, it most likely needs to be in PhreakActivationIteratorToo ***

        // Must iterate up until a node with memory is found, this can be followed to find the LeftTuples
        // which provide the potential peer of the tuple being added or removed

        if ( node.getType() == NodeTypeEnums.AlphaTerminalNode) {
            processLeftTuplesOnLian( wm, insert, rule, (LeftInputAdapterNode) node );
            return;
        }

        Memory memory = wm.getNodeMemories().peekNodeMemory(node);
        if (memory == null || memory.getSegmentMemory() == null) {
            // segment has never been initialized, which means the rule(s) have never been linked and thus no Tuples to fix
            return;
        }
        SegmentMemory sm = memory.getSegmentMemory();

        while (!NodeTypeEnums.isLeftInputAdapterNode(node)) {

            if (NodeTypeEnums.isBetaNode(node)) {
                BetaMemory    bm;
                if (NodeTypeEnums.AccumulateNode == node.getType()) {
                    AccumulateMemory am = (AccumulateMemory) memory;
                    bm = am.getBetaMemory();
                    FastIterator it = bm.getLeftTupleMemory().fullFastIterator();
                    Tuple        lt = BetaNode.getFirstTuple(bm.getLeftTupleMemory(), it);
                    for (; lt != null; lt = (TupleImpl) it.next(lt)) {
                        AccumulateContext accctx = (AccumulateContext) lt.getContextObject();
                        visitChild( (TupleImpl) accctx.getResultLeftTuple(), insert, wm, rule);
                    }
                } else if (NodeTypeEnums.ExistsNode == node.getType() && !node.inputIsTupleToObjectNode()) { // do not process exists with subnetworks
                    // If there is a subnetwork, then there is no populated RTM, but the LTM is populated,
                    // so this would be processed in the "else".

                    bm = (BetaMemory) wm.getNodeMemory((MemoryFactory) node);
                    FastIterator it = bm.getRightTupleMemory().fullFastIterator(); // done off the RightTupleMemory, as exists only have unblocked tuples on the left side
                    for (RightTuple rt = (RightTuple) BetaNode.getFirstTuple(bm.getRightTupleMemory(), it); rt != null; rt = (RightTuple) it.next(rt)) {
                        for (LeftTuple lt = rt.getBlocked(); lt != null; lt = lt.getBlockedNext()) {
                            visitLeftTuple(wm, insert, rule, lt);
                        }
                    }
                } else {
                    bm = (BetaMemory) wm.getNodeMemory((MemoryFactory) node);
                    FastIterator it = bm.getLeftTupleMemory().fullFastIterator();
                    for (TupleImpl lt = (TupleImpl)BetaNode.getFirstTuple(bm.getLeftTupleMemory(), it); lt != null; lt = (TupleImpl) it.next(lt)) {
                        visitLeftTuple(wm, insert, rule, lt);
                    }
                }
                return;
            } else if (NodeTypeEnums.FromNode == node.getType()) {
                FromMemory   fm  = (FromMemory) wm.getNodeMemory((MemoryFactory) node);
                TupleMemory  ltm = fm.getBetaMemory().getLeftTupleMemory();
                FastIterator it  = ltm.fullFastIterator();
                for (TupleImpl lt = ltm.getFirst(null); lt != null; lt = (TupleImpl) it.next(lt)) {
                    visitChild(lt, insert, wm, rule);
                }
                return;
            }
            if (sm.getRootNode() == node) {
                sm = wm.getNodeMemory((MemoryFactory<Memory>) node.getLeftTupleSource()).getSegmentMemory();
            }
            node = node.getLeftTupleSource();
        }

        // No beta or from nodes, so must retrieve LeftTuples from the LiaNode.
        // This is done by scanning all the LeftTuples referenced from the FactHandles in the ObjectTypeNode
        processLeftTuplesOnLian( wm, insert, rule, (LeftInputAdapterNode) node );
    }

    private static void processLeftTuplesOnLian( InternalWorkingMemory wm, boolean insert, Rule rule, LeftInputAdapterNode lian ) {
        BaseNode os = lian.getObjectSource();
        while (os.getType() != NodeTypeEnums.ObjectTypeNode) {
            os = os.getParent();
        }

        ObjectTypeNode otn  = (ObjectTypeNode) os;
        Iterator<InternalFactHandle> it = otn.getFactHandlesIterator(wm);
        while (it.hasNext()) {
            InternalFactHandle fh = it.next();
            fh.forEachLeftTuple( lt -> {
                TupleImpl nextLt = lt.getHandleNext();

                // Each lt is for a different lian, skip any lian not associated with the rule. Need to use lt parent (souce) not child to check the lian.
                if (SuperCacheFixer.getLeftTupleSource(lt).isAssociatedWith(rule)) {
                    visitChild(lt, insert, wm, rule);

                    if (lt.getHandlePrevious() != null && nextLt != null) {
                        lt.getHandlePrevious().setHandleNext( nextLt );
                        nextLt.setHandlePrevious( lt.getHandlePrevious() );
                    }
                }
            });
        }
    }

    private static void visitLeftTuple(InternalWorkingMemory wm, boolean insert, Rule rule, TupleImpl lt) {
        TupleImpl childLt = lt.getFirstChild();
        while (childLt != null) {
            TupleImpl nextLt = childLt.getHandleNext();
            visitChild(childLt, insert, wm, rule);
            childLt = nextLt;
        }
    }

    private static void visitChild(TupleImpl lt, boolean insert, InternalWorkingMemory wm, Rule rule) {
        TupleImpl prevLt = null;
        LeftTupleSinkNode sink = (LeftTupleSinkNode) lt.getSink();

        for ( ; sink != null; sink = sink.getNextLeftTupleSinkNode() ) {

            if ( lt != null ) {
                if (lt.getSink().isAssociatedWith(rule)) {

                    if (lt.getSink().getAssociatedTerminalsSize() > 1) {
                        if (lt.getFirstChild() != null) {
                            for ( TupleImpl child = lt.getFirstChild(); child != null; child =  child.getHandleNext() ) {
                                visitChild(child, insert, wm, rule);
                            }
                        } else if (lt.getSink().getType() == NodeTypeEnums.TupleToObjectNode) {
                            insertPeerRightTuple(lt, wm, rule, insert);
                        }
                    } else if (!insert) {
                        iterateLeftTuple( lt, wm );
                        TupleImpl lt2 = null;
                        for ( TupleImpl peerLt = lt.getPeer();
                              peerLt != null && peerLt.getSink().isAssociatedWith(rule) && peerLt.getSink().getAssociatedTerminalsSize() == 1;
                              peerLt = peerLt.getPeer() ) {
                            iterateLeftTuple( peerLt, wm );
                            lt2 = peerLt;
                        }

                        // this sink is not shared and is associated with the rule being removed delete it's children
                        deleteLeftTuple(lt, lt2, prevLt);
                        break; // only one rule is deleted at a time, we know there are no more peers to delete so break.
                    }
                }

                prevLt = lt;
                lt = lt.getPeer();
            } else {
                // there is a sink without a peer LT, so create the peer LT
                prevLt = insertPeerLeftTuple(prevLt, sink, wm, insert);
            }
        }
    }

    private static void insertPeerRightTuple( TupleImpl lt, InternalWorkingMemory wm, Rule rule, boolean insert ) {
        // There's a shared RightInputAdaterNode, so check if one of its sinks is associated only to the new rule
        TupleImpl         prevLt = null;
        TupleToObjectNode tton   = (TupleToObjectNode) lt.getSink();

        for (ObjectSink sink : tton.getObjectSinkPropagator().getSinks()) {
            if (lt != null) {
                if (prevLt != null && !insert && sink.isAssociatedWith(rule) && sink.getAssociatedTerminalsSize() == 1) {
                    prevLt.setPeer( null );
                }
                prevLt = lt;
                lt = lt.getPeer();
            } else if (insert) {
                BetaNode bn = ((RightInputAdapterNode) sink).getBetaNode();
                BetaMemory bm = (BetaMemory) wm.getNodeMemory(bn);
                prevLt = TupleFactory.createPeer(tton, prevLt);
                bm.linkNode( bn, wm );
                bm.getStagedRightTuples().addInsert(prevLt);
            }
        }
    }

    /**
     * Create all missing peers
     */
    private static TupleImpl insertPeerLeftTuple(TupleImpl lt, LeftTupleSinkNode node, InternalWorkingMemory wm, boolean insert) {
        TupleImpl peer = TupleFactory.createPeer(node, lt);

        if ( node.getLeftTupleSource().getType() == NodeTypeEnums.AlphaTerminalNode ) {
            if (insert) {
                TerminalNode rtn = ( TerminalNode ) node;
                InternalAgenda agenda = wm.getAgenda();
                RuleAgendaItem agendaItem = AlphaTerminalNode.getRuleAgendaItem( wm, agenda, rtn, insert );
                PhreakRuleTerminalNode.doLeftTupleInsert( rtn, agendaItem.getRuleExecutor(), agenda, agendaItem, (RuleTerminalNodeLeftTuple) peer );
            }
            return peer;
        }

        LeftInputAdapterNode.LiaNodeMemory liaMem = null;
        if ( NodeTypeEnums.isLeftInputAdapterNode(node.getLeftTupleSource())) {
            liaMem = wm.getNodeMemory(((LeftInputAdapterNode) node.getLeftTupleSource()));
        }

        Memory memory = wm.getNodeMemories().peekNodeMemory(node);
        if (memory == null || memory.getSegmentMemory() == null) {
            throw new IllegalStateException("Defensive Programming: this should not be possilbe, as the addRule code should init child segments if they are needed ");
        }

        if ( liaMem == null) {
            memory.getSegmentMemory().getStagedLeftTuples().addInsert(peer);
        } else {
            // If parent is Lian, then this must be called, so that any linking or unlinking can be done.
            LeftInputAdapterNode.doInsertSegmentMemoryWithFlush(wm, true, liaMem, memory.getSegmentMemory(), peer, node.getLeftTupleSource().isStreamMode());
        }

        return peer;
    }

    private static void iterateLeftTuple(TupleImpl lt, InternalWorkingMemory wm) {
        if (NodeTypeEnums.isTerminalNode(lt.getSink())) {
            PathMemory pmem = (PathMemory) wm.getNodeMemories().peekNodeMemory( lt.getSink());
            if (pmem != null) {
                PhreakRuleTerminalNode.doLeftDelete( pmem.getActualActivationsManager( wm ), pmem.getRuleAgendaItem().getRuleExecutor(), (RuleTerminalNodeLeftTuple) lt );
            }
        } else {
            if (lt.getContextObject() instanceof AccumulateContext) {
                TupleImpl resultLt = (TupleImpl) (( AccumulateContext ) lt.getContextObject()).getResultLeftTuple();
                if (resultLt != null) {
                    iterateLeftTuple( resultLt, wm );
                }
            }
            for (TupleImpl child = lt.getFirstChild(); child != null; child = child.getHandleNext()) {
                for (TupleImpl peer = child; peer != null; peer = peer.getPeer()) {
                    if (peer.getPeer() == null) {
                        // it's unnnecessary to visit the unshared networks, so only iterate the last peer
                        iterateLeftTuple( peer, wm );
                    }
                }
            }
        }
    }

    private static LeftTupleNode getNetworkSplitPoint(LeftTupleNode node) {
        while (!NodeTypeEnums.isLeftInputAdapterNode(node) && node.getAssociatedTerminalsSize() == 1) {
            node = node.getLeftTupleSource();
        }

        return node;
    }

    public static SegmentMemory splitSegment(InternalWorkingMemory wm, SegmentMemory sm1, LeftTupleNode splitNode) {
        // create new segment, starting after split
        LeftTupleNode childNode = splitNode.getSinkPropagator().getFirstLeftTupleSink();
        SegmentMemory sm2 = new SegmentMemory(childNode); // we know there is only one sink
        wm.getNodeMemories().peekNodeMemory( childNode ).setSegmentMemory( sm2 );

        // Move the children of sm1 to sm2
        if (sm1.getFirst() != null) {
            for (SegmentMemory sm = sm1.getFirst(); sm != null; ) {
                SegmentMemory next = sm.getNext();
                sm1.remove(sm);
                sm2.add(sm);
                sm = next;
            }
        }

        sm1.add(sm2);

        sm2.setPos(sm1.getPos());  // clone for now, it's corrected later
        sm2.setSegmentPosMaskBit(sm1.getSegmentPosMaskBit()); // clone for now, it's corrected later
        sm2.setLinkedNodeMask(sm1.getLinkedNodeMask());  // clone for now, it's corrected later

        sm2.mergePathMemories(sm1);

        // re-assigned tip nodes
        sm2.setTipNode(sm1.getTipNode());
        sm1.setTipNode(splitNode); // splitNode is now tip of original segment

        if (NodeTypeEnums.isLeftInputAdapterNode(sm1.getTipNode())) {
            if (!sm1.getStagedLeftTuples().isEmpty()) {
                // Segments with only LiaNode's cannot have staged LeftTuples, so move them down to the new Segment
                sm2.getStagedLeftTuples().addAll(sm1.getStagedLeftTuples());
            }
        }

        // find the pos of the node in the segment
        int pos = nodeSegmentPosition(sm1, splitNode);

        splitNodeMemories(sm1, sm2, pos);

        splitBitMasks(sm1, sm2, pos);

        correctSegmentMemoryAfterSplitOnAdd(sm2);

        return sm2;
    }

    private static void mergeSegment(SegmentMemory sm1, SegmentMemory sm2) {
        if (NodeTypeEnums.isLeftInputAdapterNode(sm1.getTipNode()) && !sm2.getStagedLeftTuples().isEmpty()) {
            // If a rule has not been linked, lia can still have child segments with staged tuples that did not get flushed
            // these are safe to just move to the parent SegmentMemory
            sm1.getStagedLeftTuples().addAll(sm2.getStagedLeftTuples());
        }

        // sm1 may not be linked yet to sm2 because sm2 has been just created
        if (sm1.contains(sm2)) {
            sm1.remove(sm2);
        }

        if (sm2.getFirst() != null) {
            for (SegmentMemory sm = sm2.getFirst(); sm != null; ) {
                SegmentMemory next = sm.getNext();
                sm2.remove(sm);
                sm1.add(sm);
                sm = next;
            }
        }
        // re-assigned tip nodes
        sm1.setTipNode(sm2.getTipNode());

        mergeNodeMemories(sm1, sm2);

        mergeBitMasks(sm1, sm2);
    }

    private static void splitBitMasks(SegmentMemory sm1, SegmentMemory sm2, int pos) {
        int  splitPos                 = pos + 1; // +1 as zero based
        long currentAllLinkedMaskTest = sm1.getAllLinkedMaskTest();
        long currentLinkedNodeMask    = sm1.getLinkedNodeMask();
        long mask                     = (1L << splitPos) - 1;

        sm1.setAllLinkedMaskTest(mask & currentAllLinkedMaskTest);
        sm1.setLinkedNodeMask(sm1.getLinkedNodeMask() & sm1.getAllLinkedMaskTest());

        mask = currentAllLinkedMaskTest >> splitPos;
        sm2.setAllLinkedMaskTest(mask);
        sm2.setLinkedNodeMask(mask & (currentLinkedNodeMask >> splitPos));
    }

    private static void mergeBitMasks(SegmentMemory sm1, SegmentMemory sm2) {
        Memory[] smNodeMemories2 = sm2.getNodeMemories();

        long mask = sm2.getAllLinkedMaskTest() << smNodeMemories2.length;
        sm1.setAllLinkedMaskTest(mask & sm1.getAllLinkedMaskTest());

        mask = sm2.getAllLinkedMaskTest() << smNodeMemories2.length;
        sm1.setLinkedNodeMask(mask & sm1.getLinkedNodeMask());
    }

    private static void splitNodeMemories(SegmentMemory sm1, SegmentMemory sm2, int pos) {
        List<Memory> smNodeMemories1 = new ArrayList<>(Arrays.asList(sm1.getNodeMemories()));
        List<Memory> smNodeMemories2 = new ArrayList<>();

        Memory mem = smNodeMemories1.get(0);
        long nodePosMask = 1;
        for (int i = 0, length = smNodeMemories1.size(); i < length; i++) {
            Memory next = mem.getNext();
            if (i > pos) {
                smNodeMemories1.remove(mem);
                addToMemoryList(smNodeMemories2, mem);
                mem.setSegmentMemory(sm2);

                // correct the NodePosMaskBit
                if (mem instanceof SegmentNodeMemory) {
                    ( (SegmentNodeMemory) mem ).setNodePosMaskBit( nodePosMask );
                }
                nodePosMask = nextNodePosMask(nodePosMask);
            }
            mem = next;
        }
        sm1.setNodeMemories(smNodeMemories1.toArray(new Memory[smNodeMemories1.size()]));
        sm2.setNodeMemories(smNodeMemories2.toArray(new Memory[smNodeMemories2.size()]));
    }

    private static void mergeNodeMemories(SegmentMemory sm1, SegmentMemory sm2) {
        List<Memory> mergedMemories = new ArrayList<>();

        int nodePosMask = 1;
        for (Memory mem : sm1.getNodeMemories() ) {
            nodePosMask = nodePosMask >> 1;
            mergedMemories.add(mem);
        }

        for (Memory mem : sm2.getNodeMemories() ) {
            addToMemoryList(mergedMemories, mem);
            mem.setSegmentMemory(sm1);

            // correct the NodePosMaskBit
            if (mem instanceof SegmentNodeMemory) {
                ( (SegmentNodeMemory) mem ).setNodePosMaskBit( nodePosMask );
            }
            nodePosMask = nodePosMask >> 1;
        }

        sm1.setNodeMemories(mergedMemories.toArray(new Memory[mergedMemories.size()]));
    }

    private static void addToMemoryList(List<Memory> smNodeMemories, Memory mem) {
        if (!smNodeMemories.isEmpty()) {
            Memory last = smNodeMemories.get(smNodeMemories.size()-1);
            last.setNext(mem);
            mem.setPrevious(last);
        }
        smNodeMemories.add(mem);
    }

    private static int nodeSegmentPosition(SegmentMemory sm1, LeftTupleNode splitNode) {
        LeftTupleNode lt = splitNode;
        int nodePos = 0;
        while (lt != sm1.getRootNode()) {
            lt = lt.getLeftTupleSource();
            nodePos++;
        }
        return nodePos;
    }

    private static PathEndNodeMemories getPathEndMemories(InternalWorkingMemory wm,
                                                          PathEndNodes pathEndNodes) {
        PathEndNodeMemories tnMems = new PathEndNodeMemories();

        for (LeftTupleNode node : pathEndNodes.otherEndNodes) {
            if (node.getType() == NodeTypeEnums.TupleToObjectNode) {
                SubnetworkPathMemory subnMem = (SubnetworkPathMemory) wm.getNodeMemories().peekNodeMemory(node);
                if (subnMem != null) {
                    tnMems.otherPmems.add(subnMem);
                }
            } else {
                PathMemory pmem = (PathMemory) wm.getNodeMemories().peekNodeMemory(node);
                if (pmem != null) {
                    tnMems.otherPmems.add(pmem);
                }
            }
        }

        tnMems.subjectPmem = (PathMemory) wm.getNodeMemories().peekNodeMemory(pathEndNodes.subjectEndNode);
        if (tnMems.subjectPmem == null && !tnMems.otherPmems.isEmpty()) {
            // If "other pmem's are initialized, then the subject needs to be initialized too.
            tnMems.subjectPmem = wm.getNodeMemory( pathEndNodes.subjectEndNode);
        }

        for (LeftTupleNode node : pathEndNodes.subjectEndNodes) {
            if (node.getType() == NodeTypeEnums.TupleToObjectNode) {
                SubnetworkPathMemory subnMem = (SubnetworkPathMemory) wm.getNodeMemories().peekNodeMemory(node);
                if (subnMem == null && !tnMems.otherPmems.isEmpty()) {
                    subnMem = (SubnetworkPathMemory) wm.getNodeMemory((MemoryFactory<Memory>) node);
                }
                if (subnMem != null) {
                    tnMems.subjectPmems.add(subnMem);
                }
            } else {
                PathMemory pmem = (PathMemory) wm.getNodeMemories().peekNodeMemory(node);
                if (pmem != null) {
                    tnMems.subjectPmems.add(pmem);
                }
            }
        }

        return tnMems;
    }

    private static class PathEndNodeMemories {
        PathMemory subjectPmem;
        List<PathMemory> subjectPmems = new ArrayList<>();
        List<PathMemory> otherPmems = new ArrayList<>();
    }

    private static PathEndNodes getPathEndNodes(InternalRuleBase kBase,
                                                LeftTupleNode lt,
                                                TerminalNode tn,
                                                Rule processedRule,
                                                boolean hasProtos,
                                                boolean hasWms) {
        PathEndNodes endNodes = new PathEndNodes();
        endNodes.subjectEndNode = tn;
        endNodes.subjectEndNodes.add(tn);
        if (hasWms && BuildtimeSegmentUtilities.isTipNode(lt, null)) {
            endNodes.subjectSplit = lt;
            endNodes.subjectSplits.add(lt);
        }

        if (hasProtos) {
            invalidateRootNode( kBase, lt );
        }

        collectPathEndNodes(kBase, lt, endNodes, tn, processedRule, hasProtos, hasWms, hasProtos && isSplit(lt));

        return endNodes;
    }

    private static void collectPathEndNodes(InternalRuleBase kBase,
                                            LeftTupleNode lt,
                                            PathEndNodes endNodes,
                                            TerminalNode tn,
                                            Rule processedRule,
                                            boolean hasProtos,
                                            boolean hasWms,
                                            boolean isBelowNewSplit) {
        // Traverses the sinks in reverse order in order to collect PathEndNodes so that
        // the outermost (sub)network are evaluated before the innermost one
        for (LeftTupleSinkNode sink = lt.getSinkPropagator().getLastLeftTupleSink(); sink != null; sink = sink.getPreviousLeftTupleSinkNode()) {
            if (sink == tn) {
                continue;
            }
            if (hasProtos) {
                if (isBelowNewSplit) {
                    if ( isRootNode( sink, null )) {
                        kBase.invalidateSegmentPrototype( sink );
                    }
                } else {
                    isBelowNewSplit = isSplit(sink);
                    if (isBelowNewSplit) {
                        invalidateRootNode( kBase, sink );
                    }
                }
            }
            if (NodeTypeEnums.isLeftTupleSource(sink)) {
                if (hasWms && BuildtimeSegmentUtilities.isTipNode(sink, null)) {
                    if (!BuildtimeSegmentUtilities.isTipNode(sink, tn)) {
                        endNodes.subjectSplits.add(sink);
                    }
                }

                collectPathEndNodes(kBase, sink, endNodes, tn, processedRule, hasProtos, hasWms, isBelowNewSplit);
            } else if (NodeTypeEnums.isTerminalNode(sink)) {
                endNodes.otherEndNodes.add((PathEndNode) sink);
            } else if (NodeTypeEnums.TupleToObjectNode == sink.getType()) {
                if (sink.isAssociatedWith( processedRule )) {
                    endNodes.subjectEndNodes.add( (PathEndNode) sink );
                }
                if (sink.getAssociatedTerminalsSize() > 1 || !sink.isAssociatedWith(processedRule)) {
                    endNodes.otherEndNodes.add( (PathEndNode) sink );
                }
            } else {
                throw new RuntimeException("Error: Unknown Node. Defensive programming test..");
            }
        }
    }

    private static void invalidateRootNode(InternalRuleBase kBase, LeftTupleNode lt) {
        while (!isRootNode( lt, null )) {
            lt = lt.getLeftTupleSource();
        }
        kBase.invalidateSegmentPrototype( lt );
    }

    private static class PathEndNodes {
        PathEndNode   subjectEndNode;
        LeftTupleNode subjectSplit;
        List<PathEndNode>   subjectEndNodes = new ArrayList<>();
        List<LeftTupleNode> subjectSplits   = new ArrayList<>();
        List<PathEndNode>   otherEndNodes   = new ArrayList<>();
    }

    static SegmentMemory createChildSegment(ReteEvaluator reteEvaluator, LeftTupleNode node) {
        Memory memory = reteEvaluator.getNodeMemory((MemoryFactory) node);
        if (memory.getSegmentMemory() == null) {
            if (NodeTypeEnums.isEndNode(node)) {
                // RTNS and TupleToObjectNode's have their own segment, if they are the child of a split.
                createChildSegmentForTerminalNode( node, memory );
            } else {
                createSegmentMemory((LeftTupleSource) node, reteEvaluator);
            }
        }
        return memory.getSegmentMemory();
    }

    static SegmentMemory createSegmentMemory(ReteEvaluator reteEvaluator, LeftTupleNode segmentRoot) {
        if (NodeTypeEnums.isTerminalNode(segmentRoot)) {
            Memory memory = reteEvaluator.getNodeMemory((MemoryFactory) segmentRoot);
            return createChildSegmentForTerminalNode(segmentRoot, memory );
        }
        return createSegmentMemory((LeftTupleSource) segmentRoot, reteEvaluator);
    }

    private static SegmentMemory createChildSegmentForTerminalNode( LeftTupleNode node, Memory memory ) {
        SegmentMemory childSmem = new SegmentMemory( node ); // rtns or TupleToObjectNodes don't need a queue
        PathMemory pmem = (PathMemory) memory;

        childSmem.setPos( pmem.getSegmentMemories().length - 1 );
        pmem.setSegmentMemory(childSmem);
        RuntimeSegmentUtilities.addSegmentToPathMemory(pmem, childSmem);

        childSmem.setTipNode(node);
        childSmem.setNodeMemories(new Memory[] {memory});
        return childSmem;
    }

    private static SegmentMemory createSegmentMemory(LeftTupleSource segmentRoot, ReteEvaluator reteEvaluator) {
        LeftTupleSource tupleSource = segmentRoot;
        SegmentMemory smem = new SegmentMemory(segmentRoot);

        // Iterate all nodes on the same segment, assigning their position as a bit mask value
        // allLinkedTestMask is the resulting mask used to test if all nodes are linked in
        long nodePosMask = 1;
        long allLinkedTestMask = 0;
        boolean updateNodeBit = true;  // nodes after a branch CE can notify, but they cannot impact linking

        int nodeTypesInSegment = 0;
        List<Memory> memories = new ArrayList<>();
        while (true) {
            nodeTypesInSegment = updateNodeTypesMask(tupleSource, nodeTypesInSegment);
            if (NodeTypeEnums.isBetaNode(tupleSource)) {
                allLinkedTestMask = processBetaNode((BetaNode)tupleSource, reteEvaluator, smem, memories, nodePosMask, allLinkedTestMask, updateNodeBit);
            } else {
                switch (tupleSource.getType()) {
                    case NodeTypeEnums.LeftInputAdapterNode:
                    case NodeTypeEnums.AlphaTerminalNode:
                        allLinkedTestMask = processLiaNode((LeftInputAdapterNode) tupleSource, reteEvaluator, smem, memories, nodePosMask, allLinkedTestMask);
                        break;
                    case NodeTypeEnums.EvalConditionNode:
                        processEvalNode((EvalConditionNode) tupleSource, reteEvaluator, smem, memories);
                        break;
                    case NodeTypeEnums.ConditionalBranchNode:
                        updateNodeBit = processBranchNode((ConditionalBranchNode) tupleSource, reteEvaluator, smem, memories);
                        break;
                    case NodeTypeEnums.FromNode:
                        processFromNode((FromNode) tupleSource, reteEvaluator, smem, memories);
                        break;
                    case NodeTypeEnums.ReactiveFromNode:
                        processReactiveFromNode((MemoryFactory) tupleSource, reteEvaluator, smem, memories, nodePosMask);
                        break;
                    case NodeTypeEnums.TimerConditionNode:
                        processTimerNode((TimerNode) tupleSource, reteEvaluator, smem, memories, nodePosMask);
                        break;
                    case NodeTypeEnums.AsyncSendNode:
                        processAsyncSendNode((AsyncSendNode) tupleSource, reteEvaluator, smem, memories);
                        break;
                    case NodeTypeEnums.AsyncReceiveNode:
                        processAsyncReceiveNode((AsyncReceiveNode) tupleSource, reteEvaluator, smem, memories, nodePosMask);
                        break;
                    case NodeTypeEnums.QueryElementNode:
                        updateNodeBit = processQueryNode((QueryElementNode) tupleSource, reteEvaluator, segmentRoot, smem, memories, nodePosMask);
                        break;
                }
            }

            nodePosMask = nextNodePosMask(nodePosMask);

            if (tupleSource.getSinkPropagator().size() == 1) {
                LeftTupleSinkNode sink = tupleSource.getSinkPropagator().getFirstLeftTupleSink();
                if (NodeTypeEnums.isLeftTupleSource(sink)) {
                    tupleSource = (LeftTupleSource) sink;
                } else {
                    // rtn or TupleToObjectNode
                    // While not technically in a segment, we want to be able to iterate easily from the last node memory to the TupleToObjectNode/rtn memory
                    // we don't use createNodeMemory, as these may already have been created by, but not added, by the method updateTupleToObjectAndTerminalMemory
                    Memory memory = reteEvaluator.getNodeMemory((MemoryFactory) sink);
                    if (sink.getType() == NodeTypeEnums.TupleToObjectNode) {
                        PathMemory subnMem = (SubnetworkPathMemory)memory;
                        memories.add( subnMem );

                        TupleToObjectNode tton  = (TupleToObjectNode) sink;
                        ObjectSink[]      nodes = tton.getObjectSinkPropagator().getSinks();
                        for ( ObjectSink node : nodes ) {
                            if ( NodeTypeEnums.isLeftTupleSource(node) )  {
                                getOrCreateSegmentMemory( (LeftTupleSource) node, reteEvaluator );
                            }
                        }
                    } else if (NodeTypeEnums.isTerminalNode(sink)) {
                        memories.add( memory );
                    }
                    memory.setSegmentMemory(smem);
                    smem.setTipNode(sink);
                    break;
                }
            } else {
                // not in same segment
                smem.setTipNode(tupleSource);
                break;
            }
        }
        smem.setAllLinkedMaskTest(allLinkedTestMask);
        smem.setNodeMemories(memories.toArray(new Memory[memories.size()]));

        // Update the memory linked references
        Memory lastMem = null;
        for (Memory mem : memories) {
            if (lastMem != null) {
                mem.setPrevious(lastMem);
                lastMem.setNext(mem);
            }
            lastMem = mem;
        }

        // iterate to find root and determine the SegmentNodes position in the RuleSegment
        LeftTupleSource pathRoot = segmentRoot;
        int ruleSegmentPosMask = 1;
        int counter = 0;
        while (!NodeTypeEnums.isLeftInputAdapterNode(pathRoot)) {
            LeftTupleSource leftTupleSource = pathRoot.getLeftTupleSource();
            if (isNonTerminalTipNode(leftTupleSource, null)) {
                // for each new found segment, increase the mask bit position
                ruleSegmentPosMask = ruleSegmentPosMask << 1;
                counter++;
            }
            pathRoot = leftTupleSource;
        }
        smem.setSegmentPosMaskBit(ruleSegmentPosMask);
        smem.setPos(counter);

        updateSubnetworkAndTerminalMemory(tupleSource, tupleSource, smem, reteEvaluator, false, nodeTypesInSegment);

        reteEvaluator.getKnowledgeBase().registerSegmentPrototype(segmentRoot, smem.getSegmentPrototype().initFromSegmentMemory(smem));

        return smem;
    }

    private static boolean processQueryNode(QueryElementNode queryNode, ReteEvaluator reteEvaluator, LeftTupleSource segmentRoot, SegmentMemory smem, List<Memory> memories, long nodePosMask) {
        // Initialize the QueryElementNode and have it's memory reference the actual query SegmentMemory
        SegmentMemory querySmem = getQuerySegmentMemory(reteEvaluator, queryNode);
        QueryElementNode.QueryElementNodeMemory queryNodeMem = smem.createNodeMemory(queryNode, reteEvaluator);
        queryNodeMem.setNodePosMaskBit(nodePosMask);
        queryNodeMem.setQuerySegmentMemory(querySmem);
        queryNodeMem.setSegmentMemory(smem);
        memories.add(queryNodeMem);
        return ! queryNode.getQueryElement().isAbductive();
    }

    private static void processFromNode(MemoryFactory tupleSource, ReteEvaluator reteEvaluator, SegmentMemory smem, List<Memory> memories) {
        Memory mem = smem.createNodeMemory(tupleSource, reteEvaluator);
        memories.add(mem);
        mem.setSegmentMemory(smem);
    }

    private static void processAsyncSendNode(MemoryFactory tupleSource, ReteEvaluator reteEvaluator, SegmentMemory smem, List<Memory> memories) {
        Memory mem = smem.createNodeMemory(tupleSource, reteEvaluator);
        mem.setSegmentMemory(smem);
        memories.add(mem);
    }

    private static void processAsyncReceiveNode(AsyncReceiveNode tupleSource, ReteEvaluator reteEvaluator, SegmentMemory smem, List<Memory> memories, long nodePosMask) {
        AsyncReceiveNode.AsyncReceiveMemory tnMem = smem.createNodeMemory( tupleSource, reteEvaluator );
        memories.add(tnMem);
        tnMem.setNodePosMaskBit(nodePosMask);
        tnMem.setSegmentMemory(smem);
    }

    private static void processReactiveFromNode(MemoryFactory tupleSource, ReteEvaluator reteEvaluator, SegmentMemory smem, List<Memory> memories, long nodePosMask) {
        FromNode.FromMemory mem = ((FromNode.FromMemory) smem.createNodeMemory(tupleSource, reteEvaluator));
        memories.add(mem);
        mem.setSegmentMemory(smem);
        mem.setNodePosMaskBit(nodePosMask);
    }

    private static boolean processBranchNode(ConditionalBranchNode tupleSource, ReteEvaluator reteEvaluator, SegmentMemory smem, List<Memory> memories) {
        ConditionalBranchNode.ConditionalBranchMemory branchMem = smem.createNodeMemory(tupleSource, reteEvaluator);
        memories.add(branchMem);
        branchMem.setSegmentMemory(smem);
        // nodes after a branch CE can notify, but they cannot impact linking
        return false;
    }

    private static void processEvalNode(EvalConditionNode tupleSource, ReteEvaluator reteEvaluator, SegmentMemory smem, List<Memory> memories) {
        EvalConditionNode.EvalMemory evalMem = smem.createNodeMemory(tupleSource, reteEvaluator);
        memories.add(evalMem);
        evalMem.setSegmentMemory(smem);
    }

    private static void processTimerNode(TimerNode tupleSource, ReteEvaluator reteEvaluator, SegmentMemory smem, List<Memory> memories, long nodePosMask) {
        TimerNode.TimerNodeMemory tnMem = smem.createNodeMemory( tupleSource, reteEvaluator );
        memories.add(tnMem);
        tnMem.setNodePosMaskBit(nodePosMask);
        tnMem.setSegmentMemory(smem);
    }

    private static long processLiaNode(LeftInputAdapterNode tupleSource, ReteEvaluator reteEvaluator, SegmentMemory smem, List<Memory> memories, long nodePosMask, long allLinkedTestMask) {
        LeftInputAdapterNode.LiaNodeMemory liaMemory = smem.createNodeMemory(tupleSource, reteEvaluator);
        memories.add(liaMemory);
        liaMemory.setSegmentMemory(smem);
        liaMemory.setNodePosMaskBit(nodePosMask);
        allLinkedTestMask = allLinkedTestMask | nodePosMask;
        return allLinkedTestMask;
    }

    private static long processBetaNode(BetaNode betaNode, ReteEvaluator reteEvaluator, SegmentMemory smem, List<Memory> memories, long nodePosMask, long allLinkedTestMask, boolean updateNodeBit) {
        BetaMemory bm;
        if (NodeTypeEnums.AccumulateNode == betaNode.getType()) {
            AccumulateNode.AccumulateMemory accMemory = ((AccumulateNode.AccumulateMemory) smem.createNodeMemory(betaNode, reteEvaluator));
            memories.add(accMemory);
            accMemory.setSegmentMemory(smem);

            bm = accMemory.getBetaMemory();
        } else {
            bm = (BetaMemory) smem.createNodeMemory(betaNode, reteEvaluator);
            memories.add(bm);
        }

        bm.setSegmentMemory(smem);

        // this must be set first, to avoid recursion as sub networks can be initialised multiple ways
        // and bm.getSegmentMemory == null check can be used to avoid recursion.
        bm.setSegmentMemory(smem);

        if (betaNode.getRightInput().inputIsTupleToObjectNode()) {
            TupleToObjectNode tton = createSubnetworkSegmentMemory(betaNode, reteEvaluator);

            PathMemory subnetworkPathMemory = reteEvaluator.getNodeMemory(tton);
            bm.setSubnetworkPathMemory((SubnetworkPathMemory) subnetworkPathMemory);
            if (updateNodeBit && canBeDisabled(betaNode) && subnetworkPathMemory.getAllLinkedMaskTest() > 0) {
                // only TupleToObjectNode's with reactive subnetworks can be disabled and thus need checking
                allLinkedTestMask = allLinkedTestMask | nodePosMask;
            }
        } else if (updateNodeBit && canBeDisabled(betaNode)) {
            allLinkedTestMask = allLinkedTestMask | nodePosMask;

        }
        bm.setNodePosMaskBit(nodePosMask);
        if (NodeTypeEnums.NotNode == betaNode.getType()) {
            // not nodes start up linked in
            smem.linkNodeWithoutRuleNotify(bm.getNodePosMaskBit());
        }
        return allLinkedTestMask;
    }

    /**
     * This adds the segment memory to the terminal node or TupleToObjectNode node's list of memories.
     * In the case of the terminal node this allows it to know that all segments from
     * the tip to root are linked.
     * In the case of the ria node its all the segments up to the start of the subnetwork.
     * This is because the TupleToObjectNode only cares if all of it's segments are linked, then
     * it sets the bit of node it is the right input for.
     */
    private static int updateSubnetworkAndTerminalMemory(LeftTupleSource lt,
                                                         LeftTupleSource originalLt,
                                                         SegmentMemory smem,
                                                         ReteEvaluator reteEvaluator,
                                                         boolean fromPrototype,
                                                         int nodeTypesInSegment) {

        nodeTypesInSegment = checkSegmentBoundary(lt, reteEvaluator, nodeTypesInSegment);

        PathMemory pmem = null;
        for (LeftTupleSink sink : lt.getSinkPropagator().getSinks()) {
            if (NodeTypeEnums.isLeftTupleSource(sink)) {
                nodeTypesInSegment = updateSubnetworkAndTerminalMemory((LeftTupleSource) sink, originalLt, smem, reteEvaluator, fromPrototype, nodeTypesInSegment);
            } else if (sink.getType() == NodeTypeEnums.TupleToObjectNode) {
                // Even though we don't add the pmem and smem together, all pmem's for all pathend nodes must be initialized
                SubnetworkPathMemory subnMem = (SubnetworkPathMemory) reteEvaluator.getNodeMemory((MemoryFactory) sink);
                // Only add the TupleToObjectNode, if the LeftTupleSource is part of the TupleToObjectNode subnetwork
                if (inSubNetwork((TupleToObjectNode) sink, originalLt)) {
                    pmem = subnMem;

                    if (fromPrototype) {
                        ObjectSink[] nodes = ((TupleToObjectNode) sink).getObjectSinkPropagator().getSinks();
                        for ( ObjectSink node : nodes ) {
                            // check if the SegmentMemory has been already created by the BetaNode and if so avoid to build it twice
                            if ( NodeTypeEnums.isLeftTupleSource(node) && reteEvaluator.getNodeMemory((MemoryFactory) node).getSegmentMemory() == null )  {
                                restoreSegmentFromPrototype(reteEvaluator, (LeftTupleSource) node, nodeTypesInSegment);
                            }
                        }
                    } else if ( ( pmem.getAllLinkedMaskTest() & ( 1L << pmem.getSegmentMemories().length ) ) == 0 ) {
                        // must eagerly initialize child segment memories
                        ObjectSink[] nodes = ((TupleToObjectNode) sink).getObjectSinkPropagator().getSinks();
                        for ( ObjectSink node : nodes ) {
                            if ( NodeTypeEnums.isLeftTupleSource(node) )  {
                                getOrCreateSegmentMemory( (LeftTupleSource) node, reteEvaluator );
                            }
                        }
                    }
                }

            } else if (NodeTypeEnums.isTerminalNode(sink)) {
                pmem = reteEvaluator.getNodeMemory((AbstractTerminalNode) sink);
            }

            if (pmem != null && smem.getPos() < pmem.getSegmentMemories().length) {
                RuntimeSegmentUtilities.addSegmentToPathMemory(pmem, smem);
                if (smem.isSegmentLinked()) {
                    // not's can cause segments to be linked, and the rules need to be notified for evaluation
                    smem.notifyRuleLinkSegment(reteEvaluator);
                }
                checkEagerSegmentCreation(sink.getLeftTupleSource(), reteEvaluator, nodeTypesInSegment);
                pmem = null;
            }
        }
        return nodeTypesInSegment;
    }

    private static void restoreSegmentFromPrototype(ReteEvaluator reteEvaluator, LeftTupleSource segmentRoot, int nodeTypesInSegment) {
        SegmentMemory smem = reteEvaluator.getKnowledgeBase().createSegmentFromPrototype(reteEvaluator, segmentRoot);
        if ( smem != null ) {
            updateSubnetworkAndTerminalMemory(segmentRoot, segmentRoot, smem, reteEvaluator, true, nodeTypesInSegment);
        }
    }

    private static int checkSegmentBoundary(LeftTupleSource lt, ReteEvaluator reteEvaluator, int nodeTypesInSegment) {
        if ( isRootNode( lt, null ) )  {
            // we are in a new child segment
            checkEagerSegmentCreation(lt.getLeftTupleSource(), reteEvaluator, nodeTypesInSegment);
            nodeTypesInSegment = 0;
        }
        return updateNodeTypesMask(lt, nodeTypesInSegment);
    }


    /**
     * Is the LeftTupleSource a node in the sub network for the RightInputAdapterNode
     * To be in the same network, it must be a node is after the two output of the parent
     * and before the TupleToObjectNode.
     */
    private static boolean inSubNetwork(TupleToObjectNode tton, LeftTupleSource leftTupleSource) {
        LeftTupleSource startTupleSource = tton.getStartTupleSource().getLeftTupleSource();
        LeftTupleSource current = tton.getLeftTupleSource();

        while (current != startTupleSource) {
            if (current == leftTupleSource) {
                return true;
            }
            current = current.getLeftTupleSource();
        }

        return false;
    }

    public static void checkEagerSegmentCreation(LeftTupleSource lt, ReteEvaluator reteEvaluator, int nodeTypesInSegment) {
        // A Not node has to be eagerly initialized unless in its segment there is at least a join node
        if ( isSet(nodeTypesInSegment, NOT_NODE_BIT) &&
                !isSet(nodeTypesInSegment, JOIN_NODE_BIT) &&
                !isSet(nodeTypesInSegment, REACTIVE_EXISTS_NODE_BIT) ) {
            getOrCreateSegmentMemory(lt, reteEvaluator);
        }
    }
}
