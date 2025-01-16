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

import java.util.List;

import org.drools.base.common.NetworkNode;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.core.base.DroolsQueryImpl;
import org.drools.core.common.ActivationsManager;
import org.drools.core.common.BetaConstraints;
import org.drools.core.common.Memory;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.TupleSets;
import org.drools.core.common.TupleSetsImpl;
import org.drools.core.reteoo.AbstractTerminalNode;
import org.drools.core.reteoo.TupleFactory;
import org.drools.core.reteoo.TupleImpl;
import org.drools.core.reteoo.AccumulateNode;
import org.drools.core.reteoo.AccumulateNode.AccumulateMemory;
import org.drools.core.reteoo.AsyncReceiveNode;
import org.drools.core.reteoo.AsyncReceiveNode.AsyncReceiveMemory;
import org.drools.core.reteoo.AsyncSendNode;
import org.drools.core.reteoo.AsyncSendNode.AsyncSendMemory;
import org.drools.core.reteoo.BetaMemory;
import org.drools.core.reteoo.BetaNode;
import org.drools.core.reteoo.ConditionalBranchNode;
import org.drools.core.reteoo.ConditionalBranchNode.ConditionalBranchMemory;
import org.drools.core.reteoo.EvalConditionNode;
import org.drools.core.reteoo.EvalConditionNode.EvalMemory;
import org.drools.core.reteoo.ExistsNode;
import org.drools.core.reteoo.FromNode;
import org.drools.core.reteoo.FromNode.FromMemory;
import org.drools.core.reteoo.JoinNode;
import org.drools.core.reteoo.LeftInputAdapterNode;
import org.drools.core.reteoo.LeftTuple;
import org.drools.core.reteoo.LeftTupleNode;
import org.drools.core.reteoo.LeftTupleSink;
import org.drools.core.reteoo.LeftTupleSinkNode;
import org.drools.core.reteoo.LeftTupleSource;
import org.drools.core.reteoo.NotNode;
import org.drools.core.reteoo.ObjectSink;
import org.drools.core.reteoo.PathMemory;
import org.drools.core.reteoo.QueryElementNode;
import org.drools.core.reteoo.QueryElementNode.QueryElementNodeMemory;
import org.drools.core.reteoo.QueryTerminalNode;
import org.drools.core.reteoo.ReactiveFromNode;
import org.drools.core.reteoo.RightInputAdapterNode;
import org.drools.core.reteoo.RightInputAdapterNode.RiaPathMemory;
import org.drools.core.reteoo.RightTuple;
import org.drools.core.reteoo.SegmentMemory;
import org.drools.core.reteoo.SubnetworkTuple;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.reteoo.TimerNode;
import org.drools.core.reteoo.TimerNode.TimerNodeMemory;
import org.drools.core.reteoo.Tuple;
import org.drools.core.reteoo.TupleMemory;
import org.drools.core.util.FastIterator;
import org.drools.core.util.LinkedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.drools.core.phreak.BuildtimeSegmentUtilities.nextNodePosMask;
import static org.drools.core.phreak.PhreakNotNode.updateBlockersAndPropagate;
import static org.drools.core.reteoo.BetaNode.getBetaMemory;

public class RuleNetworkEvaluator {

    private static final Logger log = LoggerFactory.getLogger(RuleNetworkEvaluator.class);

    public static final RuleNetworkEvaluator INSTANCE = new RuleNetworkEvaluator();

    private static final PhreakJoinNode         pJoinNode   = PhreakNetworkNodeFactory.Factory.get().createPhreakJoinNode();
    private static final PhreakEvalNode         pEvalNode   = PhreakNetworkNodeFactory.Factory.get().createPhreakEvalNode();
    private static final PhreakFromNode         pFromNode   = PhreakNetworkNodeFactory.Factory.get().createPhreakFromNode();
    private static final PhreakReactiveFromNode pReactiveFromNode = PhreakNetworkNodeFactory.Factory.get().createPhreakReactiveFromNode();
    private static final PhreakNotNode          pNotNode    = PhreakNetworkNodeFactory.Factory.get().createPhreakNotNode();
    private static final PhreakExistsNode       pExistsNode = PhreakNetworkNodeFactory.Factory.get().createPhreakExistsNode();
    private static final PhreakAccumulateNode   pAccNode    = PhreakNetworkNodeFactory.Factory.get().createPhreakAccumulateNode();
    private static final PhreakAccumulateNode   pGroupByNode = PhreakNetworkNodeFactory.Factory.get().createPhreakGroupByNode();
    private static final PhreakBranchNode       pBranchNode = PhreakNetworkNodeFactory.Factory.get().createPhreakBranchNode();
    private static final PhreakQueryNode        pQueryNode  = PhreakNetworkNodeFactory.Factory.get().createPhreakQueryNode();
    private static final PhreakTimerNode        pTimerNode  = PhreakNetworkNodeFactory.Factory.get().createPhreakTimerNode();
    private static final PhreakAsyncSendNode    pSendNode   = PhreakNetworkNodeFactory.Factory.get().createPhreakAsyncSendNode();
    private static final PhreakAsyncReceiveNode pReceiveNode = PhreakNetworkNodeFactory.Factory.get().createPhreakAsyncReceiveNode();
    private static final PhreakRuleTerminalNode pRtNode     = PhreakNetworkNodeFactory.Factory.get().createPhreakRuleTerminalNode();
    private static final PhreakQueryTerminalNode pQtNode     = PhreakNetworkNodeFactory.Factory.get().createPhreakQueryTerminalNode();

    private static int cycle = 0;


    private RuleNetworkEvaluator() { }

    public void evaluateNetwork(PathMemory pmem, RuleExecutor executor, ReteEvaluator reteEvaluator) {
        evaluateNetwork( pmem, executor, pmem.getActualActivationsManager(reteEvaluator) );
    }

    public void evaluateNetwork(PathMemory pmem, RuleExecutor executor, ActivationsManager activationsManager) {
        SegmentMemory[] smems = pmem.getSegmentMemories();


        SegmentMemory smem = smems[0];
        if (smem == null) {
            // if there's no first smem it's a pure alpha firing and then doesn't require any furthe evaluation
            return;
        }

        LeftInputAdapterNode liaNode = (LeftInputAdapterNode) smem.getRootNode();

        LinkedList<StackEntry> stack = new LinkedList<>();

        NetworkNode node;
        Memory nodeMem;
        boolean firstSegmentIsOnlyLia = liaNode == smem.getTipNode();
        if (firstSegmentIsOnlyLia) {
            // segment only has liaNode in it
            // nothing is staged in the liaNode, so skip to next segment
            smem = smems[1];
            node = smem.getRootNode();
            nodeMem = smem.getNodeMemories()[0];
        } else {
            // lia is in shared segment, so point to next node
            node = liaNode.getSinkPropagator().getFirstLeftTupleSink();
            nodeMem = smem.getNodeMemories()[1]; // skip the liaNode memory
        }

        TupleSets srcTuples = smem.getStagedLeftTuples();
        if (log.isTraceEnabled()) {
            log.trace("Rule[name={}] segments={} {}", ((TerminalNode)pmem.getPathEndNode()).getRule().getName(), smems.length, srcTuples.toStringSizes());
        }
        outerEval(pmem, node, firstSegmentIsOnlyLia ? 1L : 2L, nodeMem, smems, firstSegmentIsOnlyLia ? 1 : 0, srcTuples, activationsManager, stack, true, executor);
    }

    public static String indent(int size) {
        StringBuilder sbuilder = new StringBuilder();
        for (int i = 0; i < size; i++) {
            sbuilder.append("  ");
        }

        return sbuilder.toString();
    }

    public static int getOffset(NetworkNode node) {
        LeftTupleSource lt;
        int offset = 1;
        if (NodeTypeEnums.isTerminalNode(node)) {
            lt = ((TerminalNode) node).getLeftTupleSource();
            offset++;
        } else if (node.getType() == NodeTypeEnums.RightInputAdapterNode) {
            lt = ((RightInputAdapterNode) node).getLeftTupleSource();
        } else {
            lt = (LeftTupleSource) node;
        }
        while (!NodeTypeEnums.isLeftInputAdapterNode(lt)) {
            offset++;
            lt = lt.getLeftTupleSource();
        }

        return offset;
    }

    public void outerEval(PathMemory pmem,
                          NetworkNode node,
                          long bit,
                          Memory nodeMem,
                          SegmentMemory[] smems,
                          int smemIndex,
                          TupleSets trgTuples,
                          ActivationsManager activationsManager,
                          LinkedList<StackEntry> stack,
                          boolean processRian,
                          RuleExecutor executor) {
        innerEval(pmem, node, bit, nodeMem, smems, smemIndex, trgTuples, activationsManager, stack, processRian, executor);
        while (true) {
            // eval
            if (!stack.isEmpty()) {
                StackEntry entry = stack.removeLast();
                evalStackEntry(entry, stack, executor, activationsManager);
            } else {
                return; // stack is empty return;
            }
        }
    }

    public void evalStackEntry(StackEntry entry, LinkedList<StackEntry> stack, RuleExecutor executor, ActivationsManager activationsManager) {
        NetworkNode node = entry.getNode();
        Memory nodeMem = entry.getNodeMem();
        TupleSets trgTuples = entry.getTrgTuples();
        if (node.getType() == NodeTypeEnums.QueryElementNode) {
            // copy across the results, if any from the query node memory
            QueryElementNodeMemory qmem = (QueryElementNodeMemory) nodeMem;
            qmem.setNodeCleanWithoutNotify();
            qmem.getResultLeftTuples().addTo(trgTuples);
        }

        LeftTupleSinkNode sink = entry.getSink();
        PathMemory pmem = entry.getRmem();

        SegmentMemory[] smems = entry.getSmems();
        int smemIndex = entry.getSmemIndex();
        boolean processRian = entry.isProcessRian();

        long bit = entry.getBit();
        if (entry.isResumeFromNextNode()) {
            SegmentMemory smem = smems[smemIndex];
            if (node != smem.getTipNode()) {
                // get next node and node memory in the segment
                LeftTupleSink nextSink = sink.getNextLeftTupleSinkNode();
                if (nextSink == null) {
                    node = sink;
                } else {
                    // there is a nested subnetwork, take out path
                    node = nextSink;
                }

                nodeMem = nodeMem.getNext();
                bit = nextNodePosMask(bit); // update bit to new node
            } else {
                // Reached end of segment, start on new segment.
                SegmentPropagator.propagate(smem,
                                            trgTuples,
                                            activationsManager.getReteEvaluator());
                smem = smems[++smemIndex];
                trgTuples = smem.getStagedLeftTuples().takeAll();
                node = smem.getRootNode();
                nodeMem = smem.getNodeMemories()[0];
                bit = 1; // update bit to start of new segment
            }
        }

        if (log.isTraceEnabled()) {
            int offset = getOffset(node);
            log.trace("{} Resume {} {}", indent(offset), node.toString(), trgTuples.toStringSizes());
        }
        innerEval(pmem, node, bit, nodeMem, smems, smemIndex, trgTuples, activationsManager, stack, processRian, executor);
    }

    public void innerEval(PathMemory pmem,
                          NetworkNode node,
                          long bit,
                          Memory nodeMem,
                          SegmentMemory[] smems,
                          int smemIndex,
                          TupleSets trgTuples,
                          ActivationsManager activationsManager,
                          LinkedList<StackEntry> stack,
                          boolean processRian,
                          RuleExecutor executor) {
        TupleSets srcTuples;
        SegmentMemory smem = smems[smemIndex];
        TupleSets stagedLeftTuples = null;
        while (true) {
            srcTuples = trgTuples; // previous target, is now the source
            if (log.isTraceEnabled()) {
                int offset = getOffset(node);
                log.trace("{} {} {} {}", indent(offset), ++cycle, node.toString(), srcTuples.toStringSizes());
            }

            boolean emptySrcTuples = srcTuples.isEmpty();
            if ( !(NodeTypeEnums.isBetaNode(node) && node.isRightInputIsRiaNode() ) ) {
                // The engine cannot skip a ria node, as the dirty might be several levels deep
                if ( emptySrcTuples && smem.getDirtyNodeMask() == 0) {
                    // empty sources and segment is not dirty, skip to non empty src tuples or dirty segment.
                    boolean foundDirty = false;
                    for ( int i = ++smemIndex, length = smems.length; i < length; i++ ) {
                        if (log.isTraceEnabled()) {
                            int offset = getOffset(node);
                            log.trace("{} Skip Segment {}", indent(offset), i-1);
                        }

                        // this is needed for subnetworks that feed into a parent network that has no right inputs,
                        // and may not yet be initialized
                        if ( smem.isEmpty() && !NodeTypeEnums.isTerminalNode(smem.getTipNode()) ) {
                            RuntimeSegmentUtilities.createChildSegments(activationsManager.getReteEvaluator(), smem, smem.getTipNode().getSinkPropagator());
                        }
                        
                        smem = smems[i];
                        bit = 1;
                        srcTuples = smem.getStagedLeftTuples().takeAll();
                        emptySrcTuples = srcTuples.isEmpty();
                        node = smem.getRootNode();
                        nodeMem = smem.getNodeMemories()[0];
                        if ( !emptySrcTuples ||
                             smem.getDirtyNodeMask() != 0 ||
                             (NodeTypeEnums.isBetaNode(node) && ((BetaNode)node).isRightInputIsRiaNode() )) {
                            // break if dirty or if we reach a subnetwork. It must break for subnetworks, so they can be searched.
                            foundDirty = true;
                            smemIndex = i;
                            break;
                        }
                    }
                    if (!foundDirty) {
                        break;
                    }
                }
                if (log.isTraceEnabled()) {
                    int offset = getOffset(node);
                    log.trace("{} Segment {}", indent(offset), smemIndex);
                    log.trace("{} {} {} {}", indent(offset), cycle, node.toString(), srcTuples.toStringSizes());
                }
            }

            long dirtyMask = smem.getDirtyNodeMask();
            if ( emptySrcTuples ) {
                while ((dirtyMask & bit) == 0 && node != smem.getTipNode() && !(NodeTypeEnums.isBetaNode(node) && ((BetaNode)node).isRightInputIsRiaNode() ) ) {
                    if (log.isTraceEnabled()) {
                        int offset = getOffset(node);
                        log.trace("{} Skip Node {}", indent(offset), node);
                    }
                    bit = nextNodePosMask(bit); // shift to check the next node
                    node = ((LeftTupleSource) node).getSinkPropagator().getFirstLeftTupleSink();
                    nodeMem = nodeMem.getNext();
                }
            }

            boolean terminalNode = true;
            switch (node.getType()) {
                case NodeTypeEnums.RuleTerminalNode:
                    pRtNode.doNode((AbstractTerminalNode) node, activationsManager, srcTuples, executor);
                    break;
                case NodeTypeEnums.QueryTerminalNode:
                    pQtNode.doNode((QueryTerminalNode) node, activationsManager, srcTuples, stack);
                    break;
                case NodeTypeEnums.RightInputAdapterNode:
                    doRiaNode2(activationsManager.getReteEvaluator(), srcTuples, (RightInputAdapterNode) node);
                    break;
                default:
                    terminalNode = false;
            }
            if (terminalNode) {
                break;
            }

            stagedLeftTuples = getTargetStagedLeftTuples(node, activationsManager.getReteEvaluator(), smem);
            LeftTupleSinkNode sink = ((LeftTupleSource) node).getSinkPropagator().getFirstLeftTupleSink();

            trgTuples = evalNode( pmem, node, bit, nodeMem, smems, smemIndex, activationsManager, stack, processRian, executor, srcTuples, smem, stagedLeftTuples, sink );
            if ( trgTuples == null ) {
                break; // Queries exists and has been placed StackEntry, and there are no current trgTuples to process
            }

            if (node != smem.getTipNode()) {
                // get next node and node memory in the segment
                node = sink;
                nodeMem = nodeMem.getNext();
                bit = nextNodePosMask(bit);
            } else {
                // Reached end of segment, start on new segment.
                smem.getFirst().getStagedLeftTuples().addAll( stagedLeftTuples ); // must put back all the LTs
                // end of SegmentMemory, so we know that stagedLeftTuples is not null
                SegmentPropagator.propagate(smem, trgTuples, activationsManager.getReteEvaluator());
                bit = 1;
                smem = smems[++smemIndex];
                trgTuples = smem.getStagedLeftTuples().takeAll();

                if (log.isTraceEnabled()) {
                    int offset = getOffset(node);
                    log.trace("{} Segment {}", indent(offset), smemIndex);
                }
                node = smem.getRootNode();
                nodeMem = smem.getNodeMemories()[0];
            }
            processRian = true; //  make sure it's reset, so ria nodes are processed
        }

        if ( stagedLeftTuples != null && !stagedLeftTuples.isEmpty() ) {
            smem.getFirst().getStagedLeftTuples().addAll( stagedLeftTuples ); // must put back all the LTs
        }
    }

    public TupleSets evalNode(PathMemory pmem, NetworkNode node, long bit, Memory nodeMem,
                                         SegmentMemory[] smems, int smemIndex, ActivationsManager activationsManager, LinkedList<StackEntry> stack,
                                         boolean processRian, RuleExecutor executor, TupleSets srcTuples, SegmentMemory smem,
                                         TupleSets stagedLeftTuples, LeftTupleSinkNode sink ) {
        TupleSets trgTuples = new TupleSetsImpl();
        if ( NodeTypeEnums.isBetaNode( node )) {
            boolean exitInnerEval = evalBetaNode(pmem, node, nodeMem, smems, smemIndex, trgTuples, activationsManager, stack, processRian, executor, srcTuples, stagedLeftTuples, sink);
            if ( exitInnerEval ) {
                return null;
            }
        } else {
            boolean exitInnerEval = false;
            switch (node.getType()) {
                case NodeTypeEnums.EvalConditionNode: {
                    pEvalNode.doNode((EvalConditionNode) node, (EvalMemory) nodeMem, sink,
                            activationsManager.getReteEvaluator(), srcTuples, trgTuples, stagedLeftTuples);
                    break;

                }
                case NodeTypeEnums.FromNode: {
                    pFromNode.doNode((FromNode) node, (FromMemory) nodeMem, sink,
                            activationsManager.getReteEvaluator(), srcTuples, trgTuples, stagedLeftTuples);
                    break;
                }
                case NodeTypeEnums.ReactiveFromNode: {
                    pReactiveFromNode.doNode((ReactiveFromNode) node, (ReactiveFromNode.ReactiveFromMemory) nodeMem, sink,
                                             activationsManager.getReteEvaluator(), srcTuples, trgTuples, stagedLeftTuples);
                    break;
                }
                case NodeTypeEnums.QueryElementNode: {
                    exitInnerEval =  evalQueryNode(pmem, node, bit, nodeMem, smems, smemIndex, trgTuples,
                                                   activationsManager.getReteEvaluator(), stack, srcTuples, sink, stagedLeftTuples);
                    break;
                }
                case NodeTypeEnums.TimerConditionNode: {
                    pTimerNode.doNode( (TimerNode) node, (TimerNodeMemory) nodeMem, pmem, smem, sink, activationsManager, srcTuples, trgTuples, stagedLeftTuples);
                    break;
                }
                case NodeTypeEnums.ConditionalBranchNode: {
                    pBranchNode.doNode((ConditionalBranchNode) node, (ConditionalBranchMemory) nodeMem, sink,
                            activationsManager, srcTuples, trgTuples, stagedLeftTuples, executor);
                    break;
                }
                case NodeTypeEnums.AsyncSendNode: {
                    pSendNode.doNode((AsyncSendNode) node, (AsyncSendMemory) nodeMem, activationsManager.getReteEvaluator(), srcTuples);
                    break;
                }
                case NodeTypeEnums.AsyncReceiveNode: {
                    pReceiveNode.doNode( (AsyncReceiveNode) node, (AsyncReceiveMemory) nodeMem, sink, activationsManager.getReteEvaluator(), srcTuples, trgTuples);
                    break;
                }
            }
            if ( exitInnerEval && trgTuples.isEmpty() ) {
                return null;
            }
        }
        return trgTuples;
    }

    private static TupleSets getTargetStagedLeftTuples(NetworkNode node, ReteEvaluator reteEvaluator, SegmentMemory smem) {
        if (node == smem.getTipNode()) {
            // we are about to process the segment tip, allow it to merge insert/update/delete clashes
            if ( smem.isEmpty() ) {
                RuntimeSegmentUtilities.createChildSegments(reteEvaluator, smem, ((LeftTupleSource) node).getSinkPropagator());
            }
            return smem.getFirst().getStagedLeftTuples().takeAll();
        } else {
            return null;
        }
    }

    private boolean evalQueryNode(PathMemory pmem,
                                  NetworkNode node,
                                  long bit,
                                  Memory nodeMem,
                                  SegmentMemory[] smems,
                                  int smemIndex,
                                  TupleSets trgTuples,
                                  ReteEvaluator reteEvaluator,
                                  LinkedList<StackEntry> stack,
                                  TupleSets srcTuples,
                                  LeftTupleSinkNode sink,
                                  TupleSets stagedLeftTuples) {
        QueryElementNodeMemory qmem = (QueryElementNodeMemory) nodeMem;

        if (srcTuples.isEmpty() && qmem.getResultLeftTuples().isEmpty()) {
            // no point in evaluating query element, and setting up stack, if there is nothing to process
            return false;
        }

        QueryElementNode qnode = (QueryElementNode) node;

        if (log.isTraceEnabled()) {
            int offset = getOffset(node);
            log.trace("{} query result tuples {}", indent(offset), qmem.getResultLeftTuples().toStringSizes() );
        }

        // result tuples can happen when reactivity occurs inside of the query, prior to evaluation
        // we will need special behaviour to add the results again, when this query result resumes
        qmem.getResultLeftTuples().addTo( trgTuples );
        qmem.setNodeCleanWithoutNotify();

        if (!srcTuples.isEmpty()) {
            // only process the Query Node if there are src tuples
            StackEntry stackEntry = new StackEntry(node, bit, sink, pmem, nodeMem, smems,
                                                   smemIndex, trgTuples, true, true);

            stack.add(stackEntry);

            pQueryNode.doNode(qnode, (QueryElementNodeMemory) nodeMem, stackEntry,
                    reteEvaluator, srcTuples, trgTuples, stagedLeftTuples);

            SegmentMemory qsmem = ((QueryElementNodeMemory) nodeMem).getQuerySegmentMemory();
            List<PathMemory> qpmems = qsmem.getPathMemories();

            // Build the evaluation information for each 'or' branch
            for (int i = 0; i < qpmems.size() ; i++) {
                PathMemory qpmem = qpmems.get(i);

                pmem = qpmem;
                smems = qpmem.getSegmentMemories();
                smemIndex = 0;
                SegmentMemory smem = smems[smemIndex]; // 0

                LeftTupleNode liaNode = qpmem.getPathEndNode().getPathNodes()[0];

                if (liaNode == smem.getTipNode()) {
                    // segment only has liaNode in it
                    // nothing is staged in the liaNode, so skip to next segment
                    smem = smems[++smemIndex]; // 1
                    node = smem.getRootNode();
                    nodeMem = smem.getNodeMemories()[0];
                    bit = 1;
                } else {
                    // lia is in shared segment, so point to next node
                    node = liaNode.getSinkPropagator().getFirstLeftTupleSink();
                    nodeMem = smem.getNodeMemories()[1]; // skip the liaNode memory
                    bit = 2;
                }

                trgTuples = smem.getStagedLeftTuples().takeAll();
                stackEntry = new StackEntry(node, bit, null, pmem,
                                            nodeMem, smems, smemIndex,
                                            trgTuples, false, true);
                if (log.isTraceEnabled()) {
                    int offset = getOffset(stackEntry.getNode());
                    log.trace("{} ORQueue branch={} {} {}", indent(offset), i, stackEntry.getNode().toString(), trgTuples.toStringSizes());
                }
                stack.add(stackEntry);
            }
            return true;
        } else {
            return false;
        }

    }

    private boolean evalBetaNode(PathMemory pmem, NetworkNode node, Memory nodeMem,
                                 SegmentMemory[] smems, int smemIndex, TupleSets trgTuples, ActivationsManager activationsManager,
                                 LinkedList<StackEntry> stack, boolean processRian, RuleExecutor executor,
                                 TupleSets srcTuples, TupleSets stagedLeftTuples, LeftTupleSinkNode sink) {
        BetaNode         betaNode = (BetaNode) node;
        BetaMemory bm;
        AccumulateMemory am = null;
        if (NodeTypeEnums.AccumulateNode == node.getType()) {
            am = (AccumulateMemory) nodeMem;
            bm = am.getBetaMemory();
        } else {
            bm = (BetaMemory) nodeMem;
        }

        if (processRian && betaNode.isRightInputIsRiaNode()) {
            // if the subnetwork is nested in this segment, it will create srcTuples containing
            // peer LeftTuples, suitable for the node in the main path.
            doRiaNode( activationsManager, pmem, srcTuples,
                       betaNode, sink, smems, smemIndex, nodeMem, bm, stack, executor );
            return true; // return here, doRiaNode queues the evaluation on the stack, which is necessary to handled nested query nodes
        }

        switchOnDoBetaNode(node, trgTuples, activationsManager.getReteEvaluator(), srcTuples, stagedLeftTuples, sink, bm, am);

        return false;
    }

    private void switchOnDoBetaNode(NetworkNode node, TupleSets trgTuples, ReteEvaluator reteEvaluator, TupleSets srcTuples,
                                    TupleSets stagedLeftTuples, LeftTupleSinkNode sink, BetaMemory bm, AccumulateMemory am) {
        if (log.isTraceEnabled()) {
            int offset = getOffset(node);
            log.trace("{} rightTuples {}", indent(offset), bm.getStagedRightTuples().toStringSizes());
        }

        switch (node.getType()) {
            case NodeTypeEnums.JoinNode: {
                pJoinNode.doNode((JoinNode) node, sink, bm,
                        reteEvaluator, srcTuples, trgTuples, stagedLeftTuples);
                break;
            }
            case NodeTypeEnums.NotNode: {
                pNotNode.doNode((NotNode) node, sink, bm,
                        reteEvaluator, srcTuples, trgTuples, stagedLeftTuples);
                break;
            }
            case NodeTypeEnums.ExistsNode: {
                pExistsNode.doNode((ExistsNode) node, sink, bm,
                        reteEvaluator, srcTuples, trgTuples, stagedLeftTuples);
                break;
            }
            case NodeTypeEnums.AccumulateNode: {
                AccumulateNode accumulateNode = (AccumulateNode) node;
                if (accumulateNode.getAccumulate().isGroupBy()) {
                    pGroupByNode.doNode( accumulateNode, sink, am, reteEvaluator, srcTuples, trgTuples, stagedLeftTuples );
                } else {
                    pAccNode.doNode( accumulateNode, sink, am, reteEvaluator, srcTuples, trgTuples, stagedLeftTuples );
                }
                break;
            }
        }
    }

    private void doRiaNode(ActivationsManager activationsManager,
                           PathMemory pmem,
                           TupleSets srcTuples,
                           BetaNode betaNode,
                           LeftTupleSinkNode sink,
                           SegmentMemory[] smems,
                           int smemIndex,
                           Memory nodeMem,
                           BetaMemory bm,
                           LinkedList<StackEntry> stack,
                           RuleExecutor executor) {
        RiaPathMemory pathMem = bm.getRiaRuleMemory();
        SegmentMemory[] subnetworkSmems = pathMem.getSegmentMemories();
        SegmentMemory subSmem = null;
        for ( int i = 0; subSmem == null; i++) {
            // segment positions outside of the subnetwork, in the parent chain, are null
            // so we must iterate to find the first non null segment memory
            subSmem =  subnetworkSmems[i];
        }

        // Resume the node after the riaNode segment has been processed and the right input memory populated
        StackEntry stackEntry = new StackEntry(betaNode, bm.getNodePosMaskBit(), sink, pmem, nodeMem, smems,
                                               smemIndex, srcTuples, false, false);
        stack.add(stackEntry);
        if (log.isTraceEnabled()) {
            int offset = getOffset(betaNode);
            log.trace("{} RiaQueue {} {}", indent(offset), betaNode.toString(), srcTuples.toStringSizes());
        }


        TupleSets subLts = subSmem.getStagedLeftTuples().takeAll();
        // node is first in the segment, so bit is 1
        innerEval( pathMem, subSmem.getRootNode(), 1,
                   subSmem.getNodeMemories()[0],
                   subnetworkSmems, subSmem.getPos(),
                   subLts, activationsManager, stack, true, executor );
    }

    private void doRiaNode2(ReteEvaluator reteEvaluator,
                            TupleSets srcTuples,
                            RightInputAdapterNode riaNode) {

        ObjectSink[] sinks = riaNode.getObjectSinkPropagator().getSinks();

        BetaNode       betaNode = (BetaNode) sinks[0];
        BetaMemory bm;
        Memory         nodeMem = reteEvaluator.getNodeMemory(betaNode);
        if (NodeTypeEnums.AccumulateNode == betaNode.getType()) {
            bm = ((AccumulateMemory) nodeMem).getBetaMemory();
        } else {
            bm = (BetaMemory) nodeMem;
        }
        TupleSets rightTuples = bm.getStagedRightTuples();

        // Build up iteration array for other sinks
        BetaNode[]       bns    = null;
        BetaMemory[] bms    = null;
        int              length = sinks.length;
        if (length > 1) {
            bns = new BetaNode[sinks.length - 1];
            bms = new BetaMemory[sinks.length - 1];
            for (int i = 1; i < length; i++) {
                bns[i - 1] = (BetaNode) sinks[i];
                Memory nodeMem2 = reteEvaluator.getNodeMemory(bns[i - 1]);
                if (NodeTypeEnums.AccumulateNode == betaNode.getType()) {
                    bms[i - 1] = ((AccumulateMemory) nodeMem2).getBetaMemory();
                } else {
                    bms[i - 1] = (BetaMemory) nodeMem2;
                }
            }
        }

        length--; // subtract one, as first is not in the array;
        for (SubnetworkTuple subnetworkTuple = (SubnetworkTuple) srcTuples.getInsertFirst(); subnetworkTuple != null; ) {
            SubnetworkTuple next = (SubnetworkTuple) subnetworkTuple.getStagedNext();

            if ( bm.getStagedRightTuples().isEmpty() ) {
                bm.setNodeDirtyWithoutNotify();
            }

            subnetworkTuple.prepareStagingOnRight();
            rightTuples.addInsert( subnetworkTuple );

            if (bns != null) {
                for (int i = 0; i < length; i++) {
                    if ( bms[i].getStagedRightTuples().isEmpty() ) {
                        bms[i].setNodeDirtyWithoutNotify();
                    }
                    subnetworkTuple = (SubnetworkTuple) TupleFactory.createPeer(riaNode,
                                                                                subnetworkTuple);
                    bms[i].getStagedRightTuples().addInsert(subnetworkTuple);
                }
            }

            subnetworkTuple = next;
        }

        for (SubnetworkTuple subnetworkTuple = (SubnetworkTuple) srcTuples.getDeleteFirst(); subnetworkTuple != null; ) {
            SubnetworkTuple next = (SubnetworkTuple) subnetworkTuple.getStagedNext();

            if ( rightTuples.isEmpty() ) {
                bm.setNodeDirtyWithoutNotify();
            }

            switch ( subnetworkTuple.getStagedTypeOnRight() ) {
                // handle clash with already staged entries
                case Tuple.INSERT:
                    rightTuples.removeInsert( subnetworkTuple.moveStagingFromLeftToRight() );
                    break;
                case Tuple.UPDATE:
                    rightTuples.removeUpdate( subnetworkTuple.moveStagingFromLeftToRight() );
                    break;
            }

            subnetworkTuple.prepareStagingOnRight();
            rightTuples.addDelete(subnetworkTuple);

            if (bns != null) {
                for (int i = 0; i < length; i++) {
                    subnetworkTuple = (SubnetworkTuple) subnetworkTuple.getPeer();
                    if ( bms[i].getStagedRightTuples().isEmpty() ) {
                        bms[i].setNodeDirtyWithoutNotify();
                    }
                    bms[i].getStagedRightTuples().addDelete(subnetworkTuple);
                    subnetworkTuple.setStagedOnRight();
                }
            }

            subnetworkTuple = next;
        }

        for (SubnetworkTuple subnetworkTuple = (SubnetworkTuple) srcTuples.getUpdateFirst(); subnetworkTuple != null; ) {
            SubnetworkTuple next = (SubnetworkTuple) subnetworkTuple.getStagedNext();

            if ( rightTuples.isEmpty() ) {
                bm.setNodeDirtyWithoutNotify();
            }

            subnetworkTuple.prepareStagingOnRight();
            rightTuples.addUpdate(subnetworkTuple);

            if (bns != null) {
                for (int i = 0; i < length; i++) {
                    subnetworkTuple = (SubnetworkTuple) subnetworkTuple.getPeer();

                    if ( bms[i].getStagedRightTuples().isEmpty() ) {
                        bms[i].setNodeDirtyWithoutNotify();
                    }
                    bms[i].getStagedRightTuples().addUpdate(subnetworkTuple);
                    subnetworkTuple.setStagedOnRight();
                }
            }

            subnetworkTuple = next;
        }

        srcTuples.resetAll();
    }

    public static void findLeftTupleBlocker(BetaNode betaNode, TupleMemory rtm,
                                            Object contextEntry, BetaConstraints constraints,
                                            TupleImpl leftTuple, boolean useLeftMemory) {
        // This method will also remove rightTuples that are from subnetwork where no leftmemory use used
        FastIterator it = betaNode.getRightIterator(rtm);
        for (RightTuple rightTuple = betaNode.getFirstRightTuple(leftTuple, rtm, it); rightTuple != null; ) {
            RightTuple nextRight = (RightTuple) it.next(rightTuple);
            if (constraints.isAllowedCachedLeft(contextEntry,
                                                rightTuple.getFactHandleForEvaluation())) {
                leftTuple.setBlocker(rightTuple);

                if (useLeftMemory) {
                    rightTuple.addBlocked((LeftTuple) leftTuple);
                    break;
                } else if (betaNode.isRightInputIsRiaNode()) {
                    // If we aren't using leftMemory and the right input is a RIAN, then we must iterate and find all subetwork right tuples and remove them
                    // so we don't break
                    rtm.remove(rightTuple);
                } else {
                    break;
                }
            }
            rightTuple = nextRight;
        }
    }


    public static void unlinkAndDeleteChildLeftTuple( TupleImpl childLeftTuple,
                                                      TupleSets trgLeftTuples,
                                                      TupleSets stagedLeftTuples ) {
        childLeftTuple.unlinkFromRightParent();
        childLeftTuple.unlinkFromLeftParent();
        deleteChildLeftTuple( childLeftTuple, trgLeftTuples, stagedLeftTuples );
    }

    public static void deleteChildLeftTuple(TupleImpl childLeftTuple, TupleSets trgLeftTuples, TupleSets stagedLeftTuples ) {
        if (childLeftTuple.isStagedOnRight()) {
            ( (SubnetworkTuple) childLeftTuple ).moveStagingFromRightToLeft();
        } else {
            switch ( childLeftTuple.getStagedType() ) {
                // handle clash with already staged entries
                case Tuple.INSERT:
                    stagedLeftTuples.removeInsert( childLeftTuple );
                    trgLeftTuples.addNormalizedDelete( childLeftTuple );
                    return;
                case Tuple.UPDATE:
                    stagedLeftTuples.removeUpdate( childLeftTuple );
                    break;
            }
        }
        trgLeftTuples.addDelete( childLeftTuple );
    }

    public static void doUpdatesReorderLeftMemory(BetaMemory bm, TupleSets srcLeftTuples) {
        TupleMemory ltm = bm.getLeftTupleMemory();

        // sides must first be re-ordered, to ensure iteration integrity
        for (TupleImpl leftTuple = srcLeftTuples.getUpdateFirst(); leftTuple != null; leftTuple = leftTuple.getStagedNext()) {
            if (leftTuple.getMemory() != null) {
                ltm.remove(leftTuple);
            }
        }

        for (TupleImpl leftTuple = srcLeftTuples.getUpdateFirst(); leftTuple != null; leftTuple = leftTuple.getStagedNext()) {
            ltm.add(leftTuple);
            for (TupleImpl childLeftTuple = leftTuple.getFirstChild(); childLeftTuple != null; ) {
                TupleImpl childNext = childLeftTuple.getHandleNext();
                childLeftTuple.reAddRight();
                childLeftTuple = childNext;
            }
        }
    }

    public static void doUpdatesExistentialReorderLeftMemory(BetaMemory bm, TupleSets srcLeftTuples) {
        TupleMemory ltm = bm.getLeftTupleMemory();

        // sides must first be re-ordered, to ensure iteration integrity
        for (TupleImpl leftTuple = srcLeftTuples.getUpdateFirst(); leftTuple != null; leftTuple = leftTuple.getStagedNext()) {
            if ( leftTuple.getMemory() != null ) {
                ltm.remove(leftTuple);
            }
        }

        for (TupleImpl leftTuple = srcLeftTuples.getUpdateFirst(); leftTuple != null; leftTuple = leftTuple.getStagedNext()) {
            RightTuple blocker = leftTuple.getBlocker();
            if ( blocker == null ) {
                ltm.add(leftTuple);
                for (TupleImpl childLeftTuple = leftTuple.getFirstChild(); childLeftTuple != null; ) {
                    TupleImpl childNext = childLeftTuple.getHandleNext();
                    childLeftTuple.reAddRight();
                    childLeftTuple = childNext;
                }
            } else if ( blocker.getStagedType() != LeftTuple.NONE ) {
                // it's blocker is also being updated, so remove to force it to start from the beginning
                blocker.removeBlocked( (LeftTuple) leftTuple );
            }
        }
    }

    public static void doUpdatesReorderRightMemory(BetaMemory bm, TupleSets srcRightTuples) {
        TupleMemory rtm = bm.getRightTupleMemory();

        for (TupleImpl rightTuple = srcRightTuples.getUpdateFirst(); rightTuple != null; rightTuple = rightTuple.getStagedNext()) {
            if ( rightTuple.getMemory() != null ) {
                rtm.removeAdd(rightTuple);
                doUpdatesReorderChildLeftTuple( rightTuple );
            }
        }
    }

    public static void doUpdatesReorderChildLeftTuple( TupleImpl rightTuple) {
        for (TupleImpl childLeftTuple = rightTuple.getFirstChild(); childLeftTuple != null; ) {
            TupleImpl childNext = childLeftTuple.getRightParentNext();
            childLeftTuple.reAddLeft();
            childLeftTuple = childNext;
        }
    }

    public static void doUpdatesExistentialReorderRightMemory(BetaMemory bm, BetaNode betaNode, TupleSets srcRightTuples) {
        TupleMemory rtm = bm.getRightTupleMemory();

        boolean resumeFromCurrent = !(betaNode.isIndexedUnificationJoin() || rtm.getIndexType().isComparison());

        // remove all the staged rightTuples from the memory before to readd them all
        // this is to avoid split bucket when an updated rightTuple hasn't been moved yet
        // and so it is the first entry in the wrong bucket

        if ( rtm.getIndexType() != TupleMemory.IndexType.NONE) {
            for (TupleImpl rightTuple = srcRightTuples.getDeleteFirst(); rightTuple != null; rightTuple = rightTuple.getStagedNext() ) {
                rtm.remove( rightTuple );
            }
        }

        for (TupleImpl rightTuple = srcRightTuples.getUpdateFirst(); rightTuple != null; rightTuple = rightTuple.getStagedNext()) {
            doRemoveExistentialRightMemoryForReorder(rtm, resumeFromCurrent, (RightTuple) rightTuple);
        }

        for (TupleImpl rightTuple = srcRightTuples.getUpdateFirst(); rightTuple != null; rightTuple = rightTuple.getStagedNext()) {
            doAddExistentialRightMemoryForReorder(rtm, resumeFromCurrent, (RightTuple) rightTuple);
        }

        if ( rtm.getIndexType() != TupleMemory.IndexType.NONE) {
            for (TupleImpl rightTuple = srcRightTuples.getDeleteFirst(); rightTuple != null; rightTuple = rightTuple.getStagedNext() ) {
                rtm.add( rightTuple );
            }
        }
    }

    public static void doExistentialUpdatesReorderChildLeftTuple(ReteEvaluator reteEvaluator, NotNode notNode, RightTuple rightTuple) {
        BetaMemory bm = getBetaMemory(notNode, reteEvaluator);
        TupleMemory rtm = bm.getRightTupleMemory();

        boolean resumeFromCurrent = !(notNode.isIndexedUnificationJoin() || rtm.getIndexType().isComparison());
        doRemoveExistentialRightMemoryForReorder(rtm, resumeFromCurrent, rightTuple);
        doAddExistentialRightMemoryForReorder(rtm, resumeFromCurrent, rightTuple);

        updateBlockersAndPropagate(notNode, rightTuple, reteEvaluator, rtm, bm.getContext(), notNode.getRawConstraints(), !resumeFromCurrent, null, null, null);
    }

    private static void doAddExistentialRightMemoryForReorder(TupleMemory rtm, boolean resumeFromCurrent, RightTuple rightTuple) {
        rtm.add(rightTuple);

        if (resumeFromCurrent) {
            if ( rightTuple.getBlocked() != null && rightTuple.getTempNextRightTuple() == null ) {
                // the next RightTuple was null, but current RightTuple was added back into the same bucket, so reset as root blocker to re-match can be attempted
                rightTuple.setTempNextRightTuple(rightTuple);
            }
        }

        doUpdatesReorderChildLeftTuple(rightTuple);
    }

    private static void doRemoveExistentialRightMemoryForReorder(TupleMemory rtm, boolean resumeFromCurrent, RightTuple rightTuple) {
        if (rightTuple.getMemory() != null) {

            if (resumeFromCurrent) {
                if (rightTuple.getBlocked() != null) {
                    // look for a non-staged right tuple first forward ...
                    RightTuple tempRightTuple = (RightTuple) rightTuple.getNext();
                    while ( tempRightTuple != null && tempRightTuple.getStagedType() != LeftTuple.NONE ) {
                        // next cannot be an updated or deleted rightTuple
                        tempRightTuple = (RightTuple) tempRightTuple.getNext();
                    }

                    // ... and if cannot find one try backward
                    if ( tempRightTuple == null ) {
                        tempRightTuple = (RightTuple) rightTuple.getPrevious();
                        while ( tempRightTuple != null && tempRightTuple.getStagedType() != LeftTuple.NONE ) {
                            // next cannot be an updated or deleted rightTuple
                            tempRightTuple = (RightTuple) tempRightTuple.getPrevious();
                        }
                    }

                    rightTuple.setTempNextRightTuple( tempRightTuple );
                }
            }

            rightTuple.setTempBlocked(rightTuple.getBlocked());
            rightTuple.setBlocked(null);
            rtm.remove(rightTuple);
        }
    }

    public static boolean useLeftMemory(LeftTupleSource tupleSource, TupleImpl tuple) {
        boolean useLeftMemory = true;
        if (!tupleSource.isLeftTupleMemoryEnabled()) {
            // This is a hack, to not add closed DroolsQuery objects
            Object object = tuple.getRootTuple().getFactHandle().getObject();
            if (!(object instanceof DroolsQueryImpl) || !((DroolsQueryImpl) object).isOpen()) {
                useLeftMemory = false;
            }
        }
        return useLeftMemory;
    }

    public static void normalizeStagedTuples(TupleSets stagedLeftTuples, TupleImpl childLeftTuple ) {
        if (!childLeftTuple.isStagedOnRight()) {
            switch ( childLeftTuple.getStagedType() ) {
                // handle clash with already staged entries
                case LeftTuple.INSERT:
                    stagedLeftTuples.removeInsert( childLeftTuple );
                    break;
                case LeftTuple.UPDATE:
                    stagedLeftTuples.removeUpdate( childLeftTuple );
                    break;
            }
        }
    }
}
