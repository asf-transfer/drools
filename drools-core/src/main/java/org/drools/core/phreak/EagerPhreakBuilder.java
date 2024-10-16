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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.drools.base.common.NetworkNode;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.core.WorkingMemory;
import org.drools.core.common.BaseNode;
import org.drools.core.common.DefaultEventHandle;
import org.drools.core.common.InternalAgenda;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.Memory;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.PropagationContextFactory;
import org.drools.core.common.SuperCacheFixer;
import org.drools.core.common.TupleSets;
import org.drools.core.impl.InternalRuleBase;
import org.drools.core.reteoo.AccumulateNode.AccumulateContext;
import org.drools.core.reteoo.AccumulateNode.AccumulateMemory;
import org.drools.core.reteoo.AlphaTerminalNode;
import org.drools.core.reteoo.BetaMemory;
import org.drools.core.reteoo.BetaNode;
import org.drools.core.reteoo.BetaNode.RightTupleSinkAdapter;
import org.drools.core.reteoo.FromNode.FromMemory;
import org.drools.core.reteoo.LeftInputAdapterNode;
import org.drools.core.reteoo.LeftInputAdapterNode.LeftTupleSinkAdapter;
import org.drools.core.reteoo.LeftTuple;
import org.drools.core.reteoo.LeftTupleNode;
import org.drools.core.reteoo.LeftTupleSinkNode;
import org.drools.core.reteoo.ObjectSink;
import org.drools.core.reteoo.ObjectSource;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.PathEndNode;
import org.drools.core.reteoo.PathEndNode.PathMemSpec;
import org.drools.core.reteoo.PathMemory;
import org.drools.core.reteoo.QueryElementNode;
import org.drools.core.reteoo.RightInputAdapterNode;
import org.drools.core.reteoo.TupleToObjectNode;
import org.drools.core.reteoo.RightTuple;
import org.drools.core.reteoo.RuleTerminalNodeLeftTuple;
import org.drools.core.reteoo.RuntimeComponentFactory;
import org.drools.core.reteoo.SegmentMemory;
import org.drools.core.reteoo.SegmentMemory.MemoryPrototype;
import org.drools.core.reteoo.SegmentMemory.SegmentPrototype;
import org.drools.core.reteoo.SegmentNodeMemory;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.reteoo.Tuple;
import org.drools.core.reteoo.TupleFactory;
import org.drools.core.reteoo.TupleImpl;
import org.drools.core.reteoo.TupleMemory;
import org.drools.core.reteoo.WindowNode;
import org.drools.core.util.FastIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.drools.core.phreak.BuildtimeSegmentUtilities.isAssociatedWith;

public class EagerPhreakBuilder implements PhreakBuilder {

    private static final Logger log = LoggerFactory.getLogger(EagerPhreakBuilder.class);

    /**
     * This method is called after the rule nodes have been added to the network
     * For add tuples are processed after the segments and pmems have been adjusted
     */
    @Override
    public void addRule(TerminalNode tn, Collection<InternalWorkingMemory> wms, InternalRuleBase kBase) {
        if (log.isTraceEnabled()) {
            log.trace("Adding Rule {}", tn.getRule().getName());
        }

        for (InternalWorkingMemory wm : wms) {
            wm.flushPropagations();
        }

        Set<SegmentMemoryPair> smemsToNotify = new HashSet<>();

        Set<Integer> visited = new HashSet<>();
        if (tn.getPathNodes()[0].getAssociatedTerminalsSize() == 1) {
            BuildtimeSegmentUtilities.createPathProtoMemories(tn, null, kBase);

            // rule added with no sharing, so populate it's lian
            wms.forEach(wm -> Add.insertLiaFacts(tn.getPathNodes()[0], wm, visited, false));
        } else {
            List<Pair> exclBranchRoots = getExclusiveBranchRoots(tn);

            // Process existing branches from the split  points
            exclBranchRoots.forEach(pair -> Add.processSplit(pair.parent, kBase, wms, smemsToNotify));

            Add.addNewPaths(exclBranchRoots, tn, wms, kBase, smemsToNotify);

            exclBranchRoots.forEach(pair -> processLeftTuples(pair.parent, true, tn, wms));
        }

        for (InternalWorkingMemory wm : wms) {
            Add.addExistingSegmentMemories(Arrays.asList(tn.getPathEndNodes()), wm);
            Add.insertFacts(tn, wm, visited, false);
        }

        smemsToNotify.forEach(pair -> pair.sm.notifyRuleLinkSegment(pair.wm));
    }

    /**
     * This method is called before the rule nodes are removed from the network.
     * For remove tuples are processed before the segments and pmems have been adjusted
     *
     * Note the
     */
    @Override
    public void removeRule(TerminalNode tn, Collection<InternalWorkingMemory> wms, InternalRuleBase kBase) {
        if (log.isTraceEnabled()) {
            log.trace("Removing Rule {}", tn.getRule().getName());
        }

        for (InternalWorkingMemory wm : wms) {
            wm.flushPropagations();
        }

        List<Pair> exclBranchRoots = getExclusiveBranchRoots(tn);

        for (InternalWorkingMemory wm : wms) {
            PathMemory pmem = (PathMemory) wm.getNodeMemories().peekNodeMemory(tn);
            if (pmem != null) {
                List<LeftTupleNode> splits = exclBranchRoots.stream().map( pair -> pair.parent).filter(Objects::nonNull).collect(Collectors.toList());
                LazyPhreakBuilder.flushStagedTuples(tn, pmem, splits, wm);
            }
        }

        Set<SegmentMemoryPair> smemsToNotify = new HashSet<>();

        if (exclBranchRoots.isEmpty()) {
            LeftTupleNode lian = tn.getPathNodes()[0];
            processLeftTuples(lian, false, tn, wms);
            Remove.removeExistingPaths(exclBranchRoots, tn, wms, kBase);
        } else {
            exclBranchRoots.forEach(pair -> processLeftTuples(pair.parent, false, tn, wms));
            Remove.removeExistingPaths(exclBranchRoots, tn, wms, kBase);

            // Process existing branches from the split  points
            Set<Integer> visited = new HashSet<>();
            exclBranchRoots.forEach(pair -> Remove.processMerges(pair.parent, tn, kBase, wms, visited, smemsToNotify));
        }

        for (InternalWorkingMemory wm : wms) {
            PathMemory pmem = (PathMemory) wm.getNodeMemories().peekNodeMemory(tn);

            if (pmem!= null && pmem.isInitialized() && pmem.getRuleAgendaItem().isQueued()) {
                // pmem can be null, if it was never initialized
                pmem.getRuleAgendaItem().dequeue();
            }
        }

        smemsToNotify.forEach(pair -> pair.sm.notifyRuleLinkSegment(pair.wm));
    }

    public static void notifyImpactedSegments(SegmentMemory smem, InternalWorkingMemory wm, Set<SegmentMemoryPair> segmentsToNotify) {
        if (smem.getAllLinkedMaskTest() > 0) {
            segmentsToNotify.add(new SegmentMemoryPair(smem, wm));
        }
    }

    public static class SegmentMemoryPair {
        public SegmentMemory sm;

        public InternalWorkingMemory wm;

        public int nodeId;
        public long sessionId;

        public SegmentMemoryPair(SegmentMemory sm, InternalWorkingMemory wm) {
            this.sm = sm;
            this.wm = wm;
            this.nodeId = sm.getRootNode().getId();
            this.sessionId = wm.getIdentifier();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (getClass() != o.getClass()) {
                return false;
            }
            SegmentMemoryPair that = (SegmentMemoryPair) o;
            return nodeId == that.nodeId && sessionId == that.sessionId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(nodeId, sessionId);
        }
    }

    public static class Pair {
        public final LeftTupleNode parent;
        public final LeftTupleNode child;

        public Pair(LeftTupleNode parent, LeftTupleNode child) {
            this.parent = parent;
            this.child = child;
        }

        @Override
        public String toString() {
            return "Pair{" +
                   "parent=" + parent +
                   ", child=" + child +
                   '}';
        }
    }

    public static List<Pair> getExclusiveBranchRoots(TerminalNode tn) {
        List<Pair> exclbranchRoots = new ArrayList<>();
        Set<Integer> visited = new HashSet<>(); // PathEndNodes may havde the same root exclusive branch node.
        Arrays.stream(tn.getPathEndNodes()).forEach(endNode -> {
            LeftTupleNode node = endNode;
            if (node.getAssociatedTerminalsSize() > 1) {
                // this can happen if the whole subnetwork is shared.
                return;
            }

            while (node.getLeftTupleSource()  != null) {
                if (NodeTypeEnums.isBetaNodeWithSubnetwork(node) && ((BetaNode)node).getRightInput().getParent().getAssociatedTerminalsSize() > 1) {
                    exclbranchRoots.add( new Pair((LeftTupleNode) ((BetaNode)node).getRightInput().getParent(), node));
                }

                if (node.getLeftTupleSource().getAssociatedTerminalsSize()> 1) {
                    if (visited.add(node.getId())) {
                        exclbranchRoots.add( new Pair(node.getLeftTupleSource(), node));
                    }
                    return;
                }
                node = node.getLeftTupleSource();
            }
        });
        return exclbranchRoots;
    }

    public static class Add {

        public static void insertLiaFacts(LeftTupleNode startNode, InternalWorkingMemory wm, Set<Integer> visited, boolean allBranches) {
            // rule added with no sharing
            PropagationContextFactory pctxFactory = RuntimeComponentFactory.get().getPropagationContextFactory();
            final PropagationContext  pctx        = pctxFactory.createPropagationContext(wm.getNextPropagationIdCounter(), PropagationContext.Type.RULE_ADDITION, null, null, null);
            LeftInputAdapterNode      lian        = (LeftInputAdapterNode) startNode;
            if (allBranches && visited.add(lian.getId()) || lian.getAssociatedTerminalsSize() == 1 ) {
                attachAdapterAndPropagate(wm, lian, pctx);
            }
        }

        public static void attachAdapterAndPropagate(InternalWorkingMemory wm, LeftInputAdapterNode lian, PropagationContext pctx) {
            List<DetachedTuple>  detachedTuples = new ArrayList<>();
            LeftTupleSinkAdapter liaAdapter     = new LeftTupleSinkAdapter(lian, detachedTuples);
            lian.getObjectSource().updateSink(liaAdapter, pctx, wm);
            detachedTuples.forEach(d -> d.reattachToLeft());
        }

        public static void attachAdapterAndPropagate(InternalWorkingMemory wm, BetaNode bn) {
            PropagationContextFactory pctxFactory = RuntimeComponentFactory.get().getPropagationContextFactory();
            final PropagationContext pctx = pctxFactory.createPropagationContext(wm.getNextPropagationIdCounter(), PropagationContext.Type.RULE_ADDITION, null, null, null);
            List<DetachedTuple> detachedTuples = new ArrayList<>();
            RightTupleSinkAdapter bnAdapter = new RightTupleSinkAdapter(bn, detachedTuples);
            bn.getRightInput().updateSink(bnAdapter, pctx, wm);
            detachedTuples.forEach(d -> d.reattachToRight());
        }

        public static SegmentPrototype processSplit(LeftTupleNode splitNode, InternalRuleBase kbase, Collection<InternalWorkingMemory> wms, Set<SegmentMemoryPair> smemsToNotify) {
            LeftTupleNode segmentRoot = BuildtimeSegmentUtilities.findSegmentRoot(splitNode);
            SegmentPrototype proto1 = kbase.getSegmentPrototype(segmentRoot);
            if ( proto1.getTipNode() != splitNode) {
                // split does not already exist, add it.
                return splitSegment(proto1, splitNode, kbase, wms, smemsToNotify);
            }

            // split already exists, add it.
            return null;
        }

        private static void addExistingSegmentMemories(Collection<PathEndNode> pathEndNodes, InternalWorkingMemory wm) {
            // Iterates the path to find existing SegmentMemories that can be added to the new PathMemory
            pathEndNodes.forEach(endNode -> Arrays.stream(endNode.getSegmentPrototypes()).forEach(proto -> {
                if (!isInsideSubnetwork(endNode, proto)) { // ths proto is before the start of the subnetwork
                    return;
                }

                LeftTupleNode node = proto.getRootNode();
                Memory mem = wm.getNodeMemories().peekNodeMemory(node);

                if (mem != null && mem.getSegmentMemory() != null) {
                    SegmentMemory smem = mem.getSegmentMemory();

                    PathMemory pmem = (PathMemory) wm.getNodeMemories().peekNodeMemory(endNode);
                    if (pmem == null) {
                        pmem = RuntimeSegmentUtilities.initializePathMemory(wm, endNode);
                    }
                    if (pmem.getSegmentMemories()[proto.getPos()] == null) {
                        // we check null, as this might have been fixed due to eager initialisation
                        RuntimeSegmentUtilities.addSegmentToPathMemory(pmem, smem);
                    }
                }
            }));
        }

        public static void insertFacts(TerminalNode tn, InternalWorkingMemory wm, Set<Integer> visited, boolean allBranches) {
            for ( PathEndNode endNode : tn.getPathEndNodes() ) {
                LeftTupleNode[]  nodes = endNode.getPathNodes();

                // Iterate each PathEnd of the TerminalNode. Stop at either the path branch start (to avoid processing nodes twice), or
                // when the path is no longer uniquely associated with the terminal node.
                for ( int i = nodes.length-1;
                      i > 0 &&
                      nodes[i].getPathIndex() >= endNode.getStartTupleSource().getPathIndex() &&
                      (allBranches && visited.add(nodes[i].getId()) || nodes[i].getAssociatedTerminalsSize() == 1 );
                      i-- ) {
                    LeftTupleNode node = nodes[i];
                    if  ( NodeTypeEnums.isBetaNode(node) ) {
                        BetaNode bn = (BetaNode) node;

                        if (!bn.getRightInput().inputIsTupleToObjectNode()) {
                            attachAdapterAndPropagate(wm, bn);
                        }
                    }
                }
            }
        }

        public static void splitSegment(InternalWorkingMemory wm, SegmentMemory sm1, SegmentPrototype proto1, SegmentPrototype proto2,
                                                 Set<SegmentMemoryPair> smemsToNotify) {
            Memory[] origMemories = sm1.getNodeMemories();

            // create new segment, starting after split
            SegmentMemory sm2 = proto2.shallowNewSegmentMemory(); // we know there is only one sink

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

            sm2.mergePathMemories(sm1);

            // preserve values that get changed updateSegmentMemory, for split
            long currentLinkedNodeMask  = sm1.getLinkedNodeMask();
            proto1.shallowUpdateSegmentMemory(sm1);

            if (NodeTypeEnums.isLeftInputAdapterNode(sm1.getTipNode())) {
                if (!sm1.getStagedLeftTuples().isEmpty()) {
                    // Segments with only LiaNode's cannot have staged LeftTuples, so move them down to the new Segment
                    sm2.getStagedLeftTuples().addAll(sm1.getStagedLeftTuples());
                }
            }

            splitBitMasks(sm1, sm2, currentLinkedNodeMask);

            Memory[] mem1 = new Memory[proto1.getMemories().length];
            Memory[] mem2 = new Memory[proto2.getMemories().length];

            System.arraycopy(origMemories, 0, mem1, 0, mem1.length);

            // As the segmentMemory needs updating, no point in a separate arraycopy
            for (int i = 0; i < mem2.length; i++) {
                Memory mem = origMemories[mem1.length + i];
                mem2[i] = mem;
                mem.setSegmentMemory(sm2);
                if (mem instanceof SegmentNodeMemory) {
                    ((SegmentNodeMemory) mem).setNodePosMaskBit(proto2.getMemories()[i].getNodePosMaskBit());
                }
            }

            // break the references, between segments
            mem1[mem1.length-1].setNext(null);
            mem2[0].setPrevious(null);

            sm1.setNodeMemories(mem1);
            sm2.setNodeMemories(mem2);

            notifyImpactedSegments(sm1, wm, smemsToNotify);
            notifyImpactedSegments(sm2, wm, smemsToNotify);
        }

        public static SegmentPrototype splitSegment(SegmentPrototype proto1, LeftTupleNode splitNode, InternalRuleBase kbase, Collection<InternalWorkingMemory> wms, Set<SegmentMemoryPair> smemsToNotify) {
            boolean proto1WasEager = proto1.requiresEager();

            // Create the new segment proto
            LeftTupleNode proto2RootNode = splitNode.getSinkPropagator().getFirstLeftTupleSink();
            SegmentPrototype proto2 = new SegmentPrototype(proto2RootNode, proto1.getTipNode());
            kbase.registerSegmentPrototype(proto2RootNode, proto2);
            proto2.setPos(proto1.getPos()+1);

            // Split the nodes across proto1 and proto2
            splitProtos(proto1, proto2, splitNode);

            // now split any existing Segments, using the updated Protos
            for (InternalWorkingMemory wm : wms) {
                Memory mem = wm.getNodeMemories().peekNodeMemory(proto1.getRootNode());
                if ( mem != null && mem.getSegmentMemory() != null) {
                    splitSegment(wm, mem.getSegmentMemory(), proto1, proto2, smemsToNotify);
                }
            }

            // Paths need updating, update the protos and the smems
            PathEndNode[] endNodes = proto1.getPathEndNodes();
            for (PathEndNode endNode : endNodes) {
                // process eager changes
                splitEagerProtos(proto1, proto1WasEager, proto2, endNode);

                proto2.setPathEndNodes(proto1.getPathEndNodes());

                // insert the new proto into the segment list
                SegmentPrototype[] oldList = endNode.getSegmentPrototypes();
                SegmentPrototype[] newList = new SegmentPrototype[oldList.length + 1];
                System.arraycopy(oldList, 0, newList, 0, proto1.getPos()+1);
                newList[proto2.getPos()] = proto2;

                // If the proto isn't end, copy over the remaining protos and adjust their pos and bit pos
                if ( proto2.getPos()+1 != newList.length) {
                    for (int i = proto2.getPos()+1; i <newList.length; i++) {
                        newList[i] = oldList[i-1];
                        newList[i].setPos(i);
                        newList[i].setSegmentPosMaskBit(1 << i);
                    }
                }
                endNode.setSegmentPrototypes(newList);

                updatePaths(proto1, wms, endNode, newList);
            }

            return proto2;
        }

        private static void splitProtos(SegmentPrototype proto1, SegmentPrototype proto2, LeftTupleNode splitNode) {
            proto1.setTipNode(splitNode);

            LeftTupleNode[] nodes = proto1.getNodesInSegment();

            MemoryPrototype[] mems = proto1.getMemories();

            int arraySplit = splitNode.getPathIndex() - proto1.getRootNode().getPathIndex() + 1;
            LeftTupleNode[] proto1Nodes = new LeftTupleNode[arraySplit];
            LeftTupleNode[] proto2Nodes = new LeftTupleNode[nodes.length - arraySplit];
            System.arraycopy(nodes, 0, proto1Nodes, 0, proto1Nodes.length);
            System.arraycopy(nodes, arraySplit, proto2Nodes, 0, proto2Nodes.length);
            proto1.setNodesInSegment(proto1Nodes);
            proto2.setNodesInSegment(proto2Nodes);

            setNodeTypes(proto1, proto1Nodes);
            setNodeTypes(proto2, proto2Nodes);

            // Split the memory protos across proto1 and proto2
            MemoryPrototype[] proto1Mems = new MemoryPrototype[proto1Nodes.length];
            MemoryPrototype[] proto2Mems = new MemoryPrototype[proto2Nodes.length];
            System.arraycopy(mems, 0, proto1Mems, 0, proto1Mems.length);
            proto1.setMemories(proto1Mems);

            // proto2Mems needs updating, no point in arraycopy, so just use standard for loop to copy
            int bitPos = 1;
            for (int i = 0; i < proto2Mems.length; i++ ) {
                proto2Mems[i] = mems[i+arraySplit];
                proto2Mems[i].setNodePosMaskBit(bitPos);
                bitPos = bitPos << 1;
            }

            proto2.setMemories(proto2Mems);
            splitBitMasks(proto1, proto2);
        }

        private static void splitEagerProtos(SegmentPrototype proto1, boolean proto1WasEager, SegmentPrototype proto2, PathEndNode endNode) {
            if (proto1WasEager) { // if it wasn't eager before, nothing can be eager after
                SegmentPrototype[] eager = endNode.getEagerSegmentPrototypes();
                if (proto1.requiresEager() && proto2.requiresEager()) {
                    // keep proto1 and add proto2
                    SegmentPrototype[] newEager = new SegmentPrototype[eager.length+1];
                    System.arraycopy(eager, 0, newEager, 0, eager.length);
                    newEager[newEager.length-1] = proto2; // I don't think order matters, so just add to the end
                    endNode.setEagerSegmentPrototypes(newEager);
                } else if (proto2.requiresEager()) {
                    // proto2 is no longer eager, find proto1 and swap proto1 with proto2
                    for ( int i = 0; i < eager.length; i++){
                        if (eager[i] == proto1) {
                            eager[i] = proto2;
                            break;
                        }
                    }
                } // else if ( proto1.requiresEager() && !proto2.requiresEager()) do nothing as proto1 already in the array
            }
        }

        private static void splitBitMasks(SegmentMemory sm1, SegmentMemory sm2, long currentLinkedNodeMask) {
            // @TODO Haven't made this work for more than 64 nodes, as per SegmentUtilities.nextNodePosMask (mdp)
            int  splitPos              = sm1.getSegmentPrototype().getNodesInSegment().length; // +1 as zero based
            long currentDirtyNodeMask  = sm1.getDirtyNodeMask();
            long splitMask         =  ((1L << (splitPos)) - 1);

            sm1.setDirtyNodeMask(currentDirtyNodeMask & splitMask);
            sm1.setLinkedNodeMask(currentLinkedNodeMask & splitMask);

            sm2.setLinkedNodeMask(currentLinkedNodeMask >> splitPos);
            sm2.setDirtyNodeMask(currentDirtyNodeMask >> splitPos);
        }

        private static void splitBitMasks(SegmentPrototype sm1, SegmentPrototype sm2) {
            // @TODO Haven't made this work for more than 64 nodes, as per SegmentUtilities.nextNodePosMask (mdp)
            int  splitPos          = sm1.getNodesInSegment().length; // +1 as zero based
            long splitMask         = ((1L << (splitPos)) - 1);

            long currentLinkedNodeMask = sm1.getLinkedNodeMask();
            long currentAllLinkedMaskTest = sm1.getAllLinkedMaskTest();

            sm1.setLinkedNodeMask(currentLinkedNodeMask & splitMask);
            sm1.setAllLinkedMaskTest(currentAllLinkedMaskTest & splitMask);

            sm2.setLinkedNodeMask(currentLinkedNodeMask >> splitPos);
            sm2.setAllLinkedMaskTest(currentAllLinkedMaskTest >> splitPos);

            sm2.setSegmentPosMaskBit(sm1.getSegmentPosMaskBit() << 1);
        }


        private static void addNewPaths(List<Pair> exclBranchRoots, TerminalNode tn,
                                        Collection<InternalWorkingMemory> wms, InternalRuleBase kBase,
                                        Set<SegmentMemoryPair> smemsToNotify) {
            // create protos
            BuildtimeSegmentUtilities.createPathProtoMemories(tn, null, kBase);

            // update SegmentProtos with new EndNodes
            for ( PathEndNode endNode : tn.getPathEndNodes() ) {
                BuildtimeSegmentUtilities.updateSegmentEndNodes(endNode);
            }

            // Process the root nodes of each new branch. Not it's important to do this in order of inner subnetwork first.
            for (InternalWorkingMemory wm : wms) {
                for (PathEndNode endNode : tn.getPathEndNodes() ) {
                    if (endNode.getAssociatedTerminalsSize() > 1) {
                        // can only happen on TupleToObjectNodes, and we need to notify, incase they are already linked in
                        Memory mem = wm.getNodeMemories().peekNodeMemory(endNode);
                        if (mem != null && mem.getSegmentMemory() != null) {
                            SegmentMemory sm = mem.getSegmentMemory();

                            notifyImpactedSegments(sm, wm, smemsToNotify);
                        }

                        break; // skip this EndNode, it was not new, it already existed
                    }

                    PathMemory pmem = null; // lazy create this on demand, only if there are existing segments

                    for (SegmentPrototype sproto : endNode.getSegmentPrototypes() ) {
                        if (!isInsideSubnetwork(endNode, sproto)) {
                            continue;
                        }
                        if (sproto.getRootNode() != endNode) {
                            Memory mem = wm.getNodeMemories().peekNodeMemory(sproto.getRootNode());
                            if (mem != null && mem.getSegmentMemory() != null) {
                                if (pmem == null) {
                                    pmem = RuntimeSegmentUtilities.initializePathMemory(wm, endNode);
                                }
                                SegmentMemory sm = mem.getSegmentMemory();
                                pmem.getSegmentMemories()[sproto.getPos()] = sm;
                                sm.getPathMemories().add(pmem);
                                notifyImpactedSegments(sm, wm, smemsToNotify);
                            }
                        } else if (pmem != null) {
                            // segment with just the PathEndNode, so create
                            SegmentMemory sm = sproto.shallowNewSegmentMemory();
                            sm.setNodeMemories( new Memory[]{pmem});
                            pmem.setSegmentMemory(sm);
                            RuntimeSegmentUtilities.addSegmentToPathMemory(pmem, sm);
                            notifyImpactedSegments(sm, wm, smemsToNotify);
                        }
                    }
                }

                // If the parent has other child SegmentMemories then it must create a new child SegmentMemory
                // If the parent is a query node, then it's internal data structure needs changing
                // all right input data must be propagated
                Set<Integer> visited = new HashSet<>();
                for (int i = exclBranchRoots.size()-1; i >= 0; i--)  { // last is the most inner
                    LeftTupleNode child = exclBranchRoots.get(i).child;
                    LeftTupleNode parent = exclBranchRoots.get(i).parent;

                    Memory parentMem = wm.getNodeMemories().peekNodeMemory(parent);

                    // If the parent has propagations to existing child, then add it to the parent's child smem list
                    if (parentMem != null && parentMem.getSegmentMemory() != null &&
                        !parentMem.getSegmentMemory().isEmpty()) {
                        SegmentMemory sm = parentMem.getSegmentMemory();
                        SegmentMemory childSmem = RuntimeSegmentUtilities.createChildSegment(wm, child);
                        sm.add(childSmem);
                        sm.notifyRuleLinkSegment(wm);
                        notifyImpactedSegments(sm, wm, smemsToNotify);
                        notifyImpactedSegments(childSmem, wm, smemsToNotify);
                    }

                    if (visited.add(parent.getId())) {
                        // only vist the same parent node once, i.e. in case of subnetwork splits.
                        correctMemoryOnSplitsChanged(parent, wm);
                    }


                }
            }
        }
    }

    public static boolean isInsideSubnetwork(PathEndNode endNode, SegmentPrototype smproto) {
        return smproto.getRootNode().getPathIndex() >= endNode.getStartTupleSource().getPathIndex();
    }

    public static class Remove {
        private static void removeExistingPaths(List<Pair> exclBranchRoots, TerminalNode tn, Collection<InternalWorkingMemory> wms, InternalRuleBase kbase) {
            // update existing SegmentProtos (before removing path branch root) to remove EndNodes
            // for nodes after, just remove them from the cache
            for ( PathEndNode endNode : tn.getPathEndNodes() ) {
                if (endNode.getAssociatedTerminalsSize() > 1) {
                    // do nothing, this whole subnetwork is still shared
                    continue;
                }
                for ( int i = 0; i < endNode.getSegmentPrototypes().length; i++) {
                    SegmentPrototype smproto = endNode.getSegmentPrototypes()[i];
                    if (smproto.getRootNode().getAssociatedTerminalsSize() > 1) {
                        // update the segments before the branch being removed.
                        PathEndNode[] existingNodes = smproto.getPathEndNodes();
                        PathEndNode[] newNodes = new PathEndNode[existingNodes.length - 1];
                        for (int j = 0, k = 0; j < existingNodes.length; j++) {
                            if (existingNodes[j] == endNode) {
                                // this forces the skipping of the node, j will increase, k will not
                                continue;
                            }
                            newNodes[k] = existingNodes[j];
                            k++;
                        }
                        smproto.setPathEndNodes(newNodes);
                    } else {
                        // unregister the segments exclusive to the branch being used
                        kbase.invalidateSegmentPrototype(smproto.getRootNode());
                    }
                }
            }

            for (InternalWorkingMemory wm : wms) {
                // iterate in reverse, to do the most inner network first.
                Set<Integer> visited = new HashSet<>();
                for (int i = exclBranchRoots.size() - 1; i >= 0; i--) { // last is the most inner
                    LeftTupleNode child = exclBranchRoots.get(i).child;
                    LeftTupleNode parent = exclBranchRoots.get(i).parent;
                    if (parent.getType() == NodeTypeEnums.TupleToObjectNode) {
                        continue; // A TupleToObjectNode as it's also a PathEnd doesn't have a child segment
                    }

                    // If it exists, remove the child segment memory for the path being removed.
                    if (visited.add(child.getId())) {
                        Memory mem = wm.getNodeMemories().peekNodeMemory(parent);
                        if (mem != null && mem.getSegmentMemory() != null) {
                            SegmentMemory sm = mem.getSegmentMemory();
                            if (sm.getFirst() != null) {
                                SegmentMemory childSm = wm.getNodeMemories().peekNodeMemory(child).getSegmentMemory();
                                sm.remove(childSm);
                            }
                        }
                    }

                    if (visited.add(parent.getId())) {
                        // only vist the same parent node once, i.e. in case of subnetwork splits.
                        correctMemoryOnSplitsChanged(parent, wm);
                    }
                }

                for (PathEndNode endNode : tn.getPathEndNodes()) {
                    PathMemory pmem = (PathMemory) wm.getNodeMemories().peekNodeMemory(endNode);

                    // Iterate from root to tip.
                    // For non-exclusive, remove the PathMemory.
                    // For exclusive delete the right inputs and clear up the node memories also clean up node memories
                    for (SegmentPrototype smproto : endNode.getSegmentPrototypes()) {
                        if (!isInsideSubnetwork(endNode, smproto)) {
                            // While SegmentPrototypes are added for entire path, for traversal reasons.
                            // SegmenrMemory's themselves are only added to the PathMemory for path or subpath the are part of.
                            continue;
                        }

                        if (smproto.getRootNode().getAssociatedTerminalsSize() > 1) {
                            if (pmem != null) {
                                Memory mem = wm.getNodeMemories().peekNodeMemory(smproto.getRootNode());
                                // The root of each segment
                                if (mem != null) {
                                    SegmentMemory sm = mem.getSegmentMemory();
                                    if (sm != null) {
                                        sm.removePathMemory(pmem);
                                    }
                                }
                            }
                        } else {
                            // This is exclusive to the branch being removed, so it'll need right inputs cleaned up
                            for (int i = 0; i < smproto.getNodesInSegment().length; i++) {
                                LeftTupleNode n = smproto.getNodesInSegment()[i];
                                Memory mem = wm.getNodeMemories().peekNodeMemory(n);
                                if (mem != null && NodeTypeEnums.isBetaNode(n)) {
                                    deleteRightInputData(n, mem, wm);
                                }
                            }
                        }
                    }
                }
            }
        }


        private static void processMerges(LeftTupleNode splitNode, TerminalNode tn, InternalRuleBase kBase, Collection<InternalWorkingMemory> wms, Set<Integer> visited, Set<SegmentMemoryPair> smemsToNotify) {
            // it's possible for a rule to have multiple exclBranches, pointing to the same parent. So need to ensure it's processed once.
            if ( !visited.add(splitNode.getId())) {
                return;
            }

            if ( !BuildtimeSegmentUtilities.isTipNode(splitNode, tn)) {
                // with the tn ignored, it's no longer a semgnet tip so it's segment is ready to merge
                LeftTupleNode segmentRoot = BuildtimeSegmentUtilities.findSegmentRoot(splitNode, tn);

                SegmentPrototype proto1 = kBase.getSegmentPrototype(segmentRoot);

                // find the remaining child and get it's proto
                LeftTupleNode ltn = null;
                for (NetworkNode n : splitNode.getSinks()) {
                    if (n.getAssociatedTerminalsSize() == 1 && n.hasAssociatedTerminal(tn)) {
                        continue;
                    } else {
                        ltn = (LeftTupleNode) n;
                        break;
                    }
                }
                if ( ltn == null) {
                    // the excl branch is being removed, but there is no merge. For example see AddRemoveRulesTest.testInsertRemoveFireWith2Nots
                    //return;
                    throw new RuntimeException();
                }

                SegmentPrototype proto2 = kBase.getSegmentPrototype(ltn);

                mergeSegments(proto1, proto2, kBase, wms);

                notifyImpactedSegments(wms, proto1, smemsToNotify);
            }
        }

        public static void mergeSegments(SegmentPrototype proto1, SegmentPrototype proto2, InternalRuleBase kbase, Collection<InternalWorkingMemory> wms) {
            boolean proto2WasEager = proto2.requiresEager();

            LeftTupleNode[] origNodes = proto1.getNodesInSegment();

            mergeProtos(proto1, proto2, origNodes);

            // Now merge any existing Segments, using the updated Protos
            for (InternalWorkingMemory wm : wms) {
                Memory mem1 = wm.getNodeMemories().peekNodeMemory(proto1.getRootNode());
                Memory mem2 = wm.getNodeMemories().peekNodeMemory(proto2.getRootNode());
                mergeSegment(proto1, mem1, proto2, origNodes, mem2, wm);
            }

            // Paths need updating, update the protos and the smems
            PathEndNode[] endNodes = proto1.getPathEndNodes();
            for (PathEndNode endNode : endNodes) {
                // process eager changes
                mergeEagerProtos(proto1, proto2, proto2WasEager, endNode);
                proto1.setPathEndNodes(proto2.getPathEndNodes());

                // insert the new proto into the segment list
                SegmentPrototype[] newList = new SegmentPrototype[endNode.getSegmentPrototypes().length - 1];
                copyWithRemoval(endNode.getSegmentPrototypes(), newList, proto2);

                // If the proto isn't end, copy over the remaining protos and adjust their pos and bit pos
                if ( proto1.getPos()+1 != newList.length) {
                    for (int i = proto1.getPos()+1; i <newList.length; i++) {
                        newList[i].setPos(i);
                        newList[i].setSegmentPosMaskBit(1 << i);
                    }
                }
                endNode.setSegmentPrototypes(newList);

                updatePaths(proto1, wms, endNode, newList);
            }

            kbase.invalidateSegmentPrototype(proto2.getRootNode());
        }


        private static void mergeProtos(SegmentPrototype proto1, SegmentPrototype proto2, LeftTupleNode[] origNodes) {
            proto1.setTipNode(proto2.getTipNode());
            LeftTupleNode[] nodes = new LeftTupleNode[proto1.getNodesInSegment().length + proto2.getNodesInSegment().length];

            System.arraycopy(proto1.getNodesInSegment(), 0, nodes,
                             0, proto1.getNodesInSegment().length);

            System.arraycopy(proto2.getNodesInSegment(), 0, nodes,
                             proto1.getNodesInSegment().length, proto2.getNodesInSegment().length);

            proto1.setNodesInSegment(nodes);

            MemoryPrototype[] protoMems = new MemoryPrototype[proto1.getMemories().length + proto2.getMemories().length];

            System.arraycopy(proto1.getMemories(), 0, protoMems,
                             0, proto1.getMemories().length);

            System.arraycopy(proto2.getMemories(), 0, protoMems,
                             proto1.getMemories().length, proto2.getMemories().length);

            proto1.setNodesInSegment(nodes);
            proto1.setMemories(protoMems);

            int bitPos = 1;
            for (MemoryPrototype protoMem : protoMems) {
                protoMem.setNodePosMaskBit(bitPos);
                bitPos = bitPos << 1;
            }
            setNodeTypes(proto1, nodes);

            mergeBitMasks(proto1, proto2, origNodes);
        }


        private static void mergeSegment(SegmentPrototype proto1, Memory m1, SegmentPrototype proto2, LeftTupleNode[] origNodes, Memory m2, InternalWorkingMemory wm) {
            SegmentMemory sm1 = (m1 != null) ? m1.getSegmentMemory() : null;
            SegmentMemory sm2 = (m2 != null) ? m2.getSegmentMemory() : null;
            if ( sm1 == null && sm2 == null) {
                return;
            }

            if ( sm1 == null) {
                // To be able to merge sm1 must exist
                sm1 = RuntimeSegmentUtilities.getOrCreateSegmentMemory(proto1.getRootNode(), wm);
            }

            if ( sm2 == null) {
                // To be able to merge sm2 must exist
                sm2 = RuntimeSegmentUtilities.getOrCreateSegmentMemory(proto2.getRootNode(), wm);
            }

            // merge the memories and reassign back to sm1
            Memory[] mems1 = sm1.getNodeMemories();
            Memory[] mems2 = sm2.getNodeMemories();

            Memory[] mems = new Memory[mems1.length + mems2.length];
            System.arraycopy(mems1, 0, mems,
                             0, mems1.length);

            // As the segmentMemory needs updating, no point in a separate arraycopy
            for (int i = 0; i < mems2.length; i++) {
                Memory mem = mems2[i];
                mems[mems1.length + i] = mem;
                mem.setSegmentMemory(sm1);

                // make sure all the mems still reference each other
                mem.setPrevious(mems[mems1.length + i -1]);
                mems[mems1.length + i -1].setNext(mem);

                if (mem instanceof SegmentNodeMemory) {
                    ((SegmentNodeMemory) mem).setNodePosMaskBit(proto2.getMemories()[i].getNodePosMaskBit());
                }
            }

            sm1.setNodeMemories(mems);

            mergeSegment(sm1, sm2, proto1, origNodes);
        }

        private static void mergeSegment(SegmentMemory sm1, SegmentMemory sm2, SegmentPrototype proto1, LeftTupleNode[] origNodes) {
            if (NodeTypeEnums.isLeftInputAdapterNode(sm1.getTipNode()) && !sm2.getStagedLeftTuples().isEmpty()) {
                // If a rule has not been linked, lia can still have child segments with staged tuples that did not get flushed
                // these are safe to just move to the parent SegmentMemory
                sm1.getStagedLeftTuples().addAll(sm2.getStagedLeftTuples());
            }

            // sm1 may not be linked yet to sm2 because sm2 has been just created
            if (sm1.contains(sm2)) {
                sm1.remove(sm2);
            }

            // add all child sms
            if (sm2.getFirst() != null) {
                for (SegmentMemory sm = sm2.getFirst(); sm != null; ) {
                    SegmentMemory next = sm.getNext();
                    sm2.remove(sm);
                    sm1.add(sm);
                    sm = next;
                }
            }

            // preserve values that get changed updateSegmentMemory, for merge
            long currentLinkedNodeMask = sm1.getLinkedNodeMask();
            proto1.shallowUpdateSegmentMemory(sm1);

            mergeBitMasks(sm1, sm2, origNodes, currentLinkedNodeMask);
        }

        private static void mergeBitMasks(SegmentPrototype sm1, SegmentPrototype sm2, LeftTupleNode[] origNodes) {
            // @TODO Haven't made this work for more than 64 nodes, as per SegmentUtilities.nextNodePosMask (mdp)
            int shiftBits = origNodes.length;

            long currentLinkedNodeMask = sm1.getLinkedNodeMask();
            long currentAllLinkedMaskTest = sm1.getAllLinkedMaskTest();

            long linkedBitsToAdd = sm2.getLinkedNodeMask() << shiftBits;
            long allBitsToAdd = sm2.getAllLinkedMaskTest() << shiftBits;

            sm1.setLinkedNodeMask(linkedBitsToAdd | currentLinkedNodeMask);
            sm1.setAllLinkedMaskTest(allBitsToAdd | currentAllLinkedMaskTest);
        }

        private static void mergeBitMasks(SegmentMemory sm1, SegmentMemory sm2, LeftTupleNode[] origNodes, long currentLinkedNodeMask) {
            // @TODO Haven't made this work for more than 64 nodes, as per SegmentUtilities.nextNodePosMask (mdp)
            int shiftBits = origNodes.length;

            long linkedBitsToAdd = sm2.getLinkedNodeMask() << shiftBits;
            long dirtyBitsToAdd = sm2.getDirtyNodeMask() << shiftBits;
            sm1.setLinkedNodeMask(linkedBitsToAdd | currentLinkedNodeMask);
            sm1.setDirtyNodeMask(dirtyBitsToAdd | sm1.getDirtyNodeMask());
        }

        private static void mergeEagerProtos(SegmentPrototype proto1, SegmentPrototype proto2, boolean proto2WasEager, PathEndNode endNode) {
            if (!proto1.requiresEager() && !proto2.requiresEager()) {
                return;
            }

            SegmentPrototype[] eager = endNode.getEagerSegmentPrototypes();
            if (proto1.requiresEager() && proto2.requiresEager()) {
                // keep proto1 and remove proto2
                SegmentPrototype[] newEager = new SegmentPrototype[eager.length-1];
                copyWithRemoval(eager, newEager, proto2);
                endNode.setEagerSegmentPrototypes(newEager);
            }  else if (proto1.requiresEager() && proto2WasEager) {
                // find proto2 and swap proto2 with proto1
                for ( int i = 0; i < eager.length; i++){
                    if (eager[i] == proto2) {
                        eager[i] = proto1;
                        break;
                    }
                }
            } // else if ( proto1.requiresEager() && !proto2.requiresEager()) do nothing as proto1 already in the array
        }

        private static void deleteRightInputData(LeftTupleNode node, Memory m, InternalWorkingMemory wm) {
            BetaNode       bn = (BetaNode) node;
            BetaMemory bm;
            if (bn.getType() == NodeTypeEnums.AccumulateNode) {
                bm = ((AccumulateMemory) m).getBetaMemory();
            } else {
                bm = (BetaMemory) m;
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

        private static void deleteFactsFromRightInput(BetaNode bn, InternalWorkingMemory wm) {
            BaseNode source = bn.getRightInput();
            if (source.getType() == NodeTypeEnums.WindowNode) {
                WindowNode.WindowMemory memory = (WindowNode.WindowMemory) wm.getNodeMemories().peekNodeMemory(source);
                if (memory != null) {
                    for (DefaultEventHandle factHandle : memory.getFactHandles()) {
                        factHandle.forEachRightTuple(rt -> {
                            if (source.equals(rt.getSink())) {
                                rt.unlinkFromRightParent();
                            }
                        });
                    }
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

        private static void copyWithRemoval(SegmentPrototype[] orinalProtos, SegmentPrototype[] newProtos, SegmentPrototype protoToRemove) {
            for (int i = 0, j = 0; i < orinalProtos.length; i++) {
                if (orinalProtos[i] == protoToRemove) {
                    // j is not increased here.
                    continue;
                }
                newProtos[j] = orinalProtos[i];
                j++;
            }
        }
    }

    private static void correctMemoryOnSplitsChanged(LeftTupleNode splitStart, InternalWorkingMemory wm) {
        if (splitStart.getType() == NodeTypeEnums.QueryElementNode) {
            QueryElementNode.QueryElementNodeMemory mem = (QueryElementNode.QueryElementNodeMemory) wm.getNodeMemories().peekNodeMemory(splitStart);
            if (mem != null) {
                mem.correctMemoryOnSinksChanged(null);
            }
        }
    }

    /**
     * Populates the SegmentMemory with staged LeftTuples. If the parent is not a Beta or From node, it iterates up to find the first node with memory. If necessary
     * It traverses to the LiaNode's ObjectTypeNode. It then iterates the LeftTuple chains, where an existing LeftTuple is staged
     * as delete. Or a new LeftTuple is created and staged as an insert.
     */
    private static void processLeftTuples(LeftTupleNode node, boolean insert, TerminalNode tn, Collection<InternalWorkingMemory> wms) {
        for (InternalWorkingMemory wm : wms) {
            // *** if you make a fix here, it most likely needs to be in PhreakActivationIteratorToo ***

            // Must iterate up until a node with memory is found, this can be followed to find the LeftTuples
            // which provide the potential peer of the tuple being added or removed

            if (node.getType() == NodeTypeEnums.AlphaTerminalNode) {
                processLeftTuplesOnLian(wm, insert, tn, (LeftInputAdapterNode) node);
                return;
            }

            Memory memory = wm.getNodeMemories().peekNodeMemory(node);
            if (memory == null || memory.getSegmentMemory() == null) {
                // segment has never been initialized, which means the rule(s) have never been linked and thus no Tuples to fix
                return;
            }

            // this visited is here due to subnetworks causing potential parent revisiting.
            Set<LeftTupleNode> visited = new HashSet<>();

            while (!NodeTypeEnums.isLeftInputAdapterNode(node)) {
                if (!visited.add(node)) {
                    return;
                }
                if (NodeTypeEnums.isBetaNode(node)) {
                    BetaMemory bm;
                    if (NodeTypeEnums.AccumulateNode == node.getType()) {
                        AccumulateMemory am = (AccumulateMemory) memory;
                        bm = am.getBetaMemory();
                        FastIterator it = bm.getLeftTupleMemory().fullFastIterator();
                        Tuple lt = BetaNode.getFirstTuple(bm.getLeftTupleMemory(), it);
                        for (; lt != null; lt = (TupleImpl) it.next(lt)) {
                            AccumulateContext accctx = (AccumulateContext) lt.getContextObject();
                            visitChild((TupleImpl) accctx.getResultLeftTuple(), insert, wm, tn);
                        }
                    } else if (NodeTypeEnums.ExistsNode == node.getType() &&
                               !((BetaNode) node).getRightInput().inputIsTupleToObjectNode()) { // do not process exists with subnetworks
                        // If there is a subnetwork, then there is no populated RTM, but the LTM is populated,
                        // so this would be procsssed in the "else".

                        bm = (BetaMemory) wm.getNodeMemories().peekNodeMemory(node);
                        if (bm != null) {
                            FastIterator it = bm.getRightTupleMemory().fullFastIterator(); // done off the RightTupleMemory, as exists only have unblocked tuples on the left side
                            RightTuple   rt = (RightTuple) BetaNode.getFirstTuple(bm.getRightTupleMemory(), it);
                            for (; rt != null; rt = (RightTuple) it.next(rt)) {
                                for (LeftTuple lt = rt.getBlocked(); lt != null; lt = lt.getBlockedNext()) {
                                    visitChild(wm, insert, tn, it, lt);
                                }
                            }
                        }
                    } else {
                        bm = (BetaMemory) wm.getNodeMemories().peekNodeMemory(node);
                        if (bm != null) {
                            FastIterator<TupleImpl> it = bm.getLeftTupleMemory().fullFastIterator();
                            TupleImpl lt = BetaNode.getFirstTuple(bm.getLeftTupleMemory(), it);
                            visitChild(wm, insert, tn, it, lt);
                        }
                    }
                    return;
                } else if (NodeTypeEnums.FromNode == node.getType()) {
                    FromMemory fm = (FromMemory) wm.getNodeMemories().peekNodeMemory(node);
                    if (fm != null) {
                        TupleMemory ltm = fm.getBetaMemory().getLeftTupleMemory();
                        FastIterator it = ltm.fullFastIterator();
                        for (TupleImpl lt = (TupleImpl) ltm.getFirst(null); lt != null; lt = (TupleImpl) it.next(lt)) {
                            visitChild(lt, insert, wm, tn);
                        }
                    }
                    return;
                }
                node = node.getLeftTupleSource();
            }

            // No beta or from nodes, so must retrieve LeftTuples from the LiaNode.
            // This is done by scanning all the LeftTuples referenced from the FactHandles in the ObjectTypeNode
            processLeftTuplesOnLian(wm, insert, tn, (LeftInputAdapterNode) node);
        }
    }

    private static void processLeftTuplesOnLian( InternalWorkingMemory wm, boolean insert, TerminalNode tn, LeftInputAdapterNode lian ) {
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
                if (isAssociatedWith(SuperCacheFixer.getLeftTupleSource(lt), tn)) {
                    visitChild(lt, insert, wm, tn);

                    if (lt.getHandlePrevious() != null) {
                        lt.getHandlePrevious().setHandleNext( nextLt );
                        if (nextLt != null) {
                            nextLt.setHandlePrevious( lt.getHandlePrevious() );
                        }
                    }
                }
            });
        }
    }

    private static void visitChild(InternalWorkingMemory wm, boolean insert, TerminalNode tn, FastIterator<TupleImpl> it, TupleImpl lt) {
        for (; lt != null; lt = it.next(lt)) {
            TupleImpl childLt = lt.getFirstChild();
            while (childLt != null) {
                TupleImpl nextLt = childLt.getHandleNext();
                visitChild(childLt, insert, wm, tn);
                childLt = nextLt;
            }
        }
    }

    private static void visitChild(TupleImpl lt, boolean insert, InternalWorkingMemory wm, TerminalNode tn) {
        TupleImpl prevLt = null;

        LeftTupleSinkNode sink = (LeftTupleSinkNode) lt.getSink();

        for ( ; sink != null; sink = sink.getNextLeftTupleSinkNode() ) {
            if ( lt != null ) {
                if (isAssociatedWith(lt.getSink(), tn)) {

                    if (lt.getSink().getAssociatedTerminalsSize() > 1) {
                        if (lt.getFirstChild() != null) {
                            for ( TupleImpl child = lt.getFirstChild(); child != null; child =  child.getHandleNext() ) {
                                visitChild(child, insert, wm, tn);
                            }
                        } else if (lt.getSink().getType() == NodeTypeEnums.TupleToObjectNode) {
                            insertPeerRightTuple(lt, wm, tn, insert);
                        }
                    } else if (!insert) {
                        iterateLeftTuple( lt, wm );
                        TupleImpl lt2 = null;
                        for ( TupleImpl peerLt = lt.getPeer();
                              peerLt != null && isAssociatedWith(peerLt.getSink(), tn) && peerLt.getSink().getAssociatedTerminalsSize() == 1;
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

    private static void insertPeerRightTuple(TupleImpl lt, InternalWorkingMemory wm, TerminalNode tn, boolean insert ) {
        // There's a shared RightInputAdapterNode, so check if one of its sinks is associated only to the new rule
        TupleImpl         prevLt = null;
        TupleToObjectNode tton   = (TupleToObjectNode) lt.getSink();

        for (ObjectSink sink : tton.getObjectSinkPropagator().getSinks()) {
            if (lt != null) {
                if (prevLt != null && !insert && isAssociatedWith(sink, tn) && sink.getAssociatedTerminalsSize() == 1) {
                    prevLt.setPeer( null );
                }
                prevLt = lt;
                lt = lt.getPeer();
            } else if (insert) {
                BetaNode betaNode = ((RightInputAdapterNode)sink).getBetaNode();
                BetaMemory bm = (BetaMemory) wm.getNodeMemories().peekNodeMemory(betaNode);
                if (bm != null) {
                    prevLt = TupleFactory.createPeer(tton, prevLt);
                    bm.linkNode(betaNode, wm);
                    bm.getStagedRightTuples().addInsert(prevLt);
                }
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
            liaMem = (LeftInputAdapterNode.LiaNodeMemory) wm.getNodeMemories().peekNodeMemory(node.getLeftTupleSource());
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

    static void deleteLeftTuple(TupleImpl removingLt, TupleImpl removingLt2, TupleImpl prevLt) {
        // only the first LT in a peer chain is hooked into left and right parents or the FH.
        // If the first LT is being remove, those hooks need to be shifted to the next peer,
        // or nulled if there is no next peer.
        // When there is a subnetwork, it needs to shift to the peer of the next lt.
        // if it is not the first LT in the peer chain, leftParent and rightParent are null.
        // And the previous peer will need to point to the peer after removingLt, or removingLt2 if it exists.

        boolean isFirstLt = prevLt == null; // is this the first LT in a peer chain chain
        TupleImpl nextPeerLt    = (removingLt2 == null ) ? removingLt.getPeer() : removingLt2.getPeer(); // if there is a subnetwork, skip to the peer after that

        if( !isFirstLt ) {
            // This LT is not the first tuple in a peer chain. So just correct the peer chain linked list
            prevLt.setPeer( nextPeerLt );
        } else {
            if ( nextPeerLt == null ) {
                removingLt.unlinkFromLeftParent();
                removingLt.unlinkFromRightParent();
                return;
            }

            InternalFactHandle fh = removingLt.getFactHandle();

            // This is the first LT in a peer chain. Only this LT is hooked into the left and right parent LT and RT and
            // if it's the root (form the lian) it will be hooked itno the FH.
            TupleImpl leftPrevious = removingLt.getHandlePrevious();
            TupleImpl leftNext     = removingLt.getHandleNext();

            TupleImpl rightPrevious = removingLt.getRightParentPrevious();
            TupleImpl rightNext     = removingLt.getRightParentNext();

            TupleImpl leftParent  = removingLt.getLeftParent();
            TupleImpl rightParent = removingLt.getRightParent();

            // This tuple is the first peer and thus is linked into the left parent LT.

            nextPeerLt.setFactHandle(removingLt.getFactHandle());

            // correct the linked list
            if (leftPrevious != null) {
                nextPeerLt.setHandlePrevious(leftPrevious);
                leftPrevious.setHandleNext(nextPeerLt);
            }

            if (leftNext != null) {
                nextPeerLt.setHandleNext(leftNext);
                leftNext.setHandlePrevious(nextPeerLt);
            }

            // correct the linked list
            if (rightPrevious != null) {
                nextPeerLt.setRightParentPrevious(rightPrevious);
                rightPrevious.setRightParentNext(nextPeerLt);
            }

            if (rightNext != null) {
                nextPeerLt.setRightParentNext(rightNext);
                rightNext.setRightParentPrevious(nextPeerLt);
            }

            // correct the parent's first/last references
            if (leftParent!=null) {
                nextPeerLt.setLeftParent(leftParent);

                if (leftParent.getFirstChild() == removingLt) {
                    leftParent.setFirstChild(nextPeerLt);
                }

                if (leftParent.getLastChild() == removingLt) {
                    leftParent.setLastChild(nextPeerLt);
                }
            } else {
                // is the LT for the LIAN, if so we need to process the FH too
                fh.removeLeftTuple(removingLt);
                if (leftPrevious == null) {
                    // The removed tuple was first in linked list, add the peer at its original position
                    fh.addFirstLeftTuple( nextPeerLt );
                }
            }

            if ( rightParent != null ) {
                // This tuple is the first peer and thus is linked into the right parent RT.
                nextPeerLt.setRightParent(rightParent);

                // correct the parent's first/last references
                // if nextLT is null, it's ok for parent's reference to be null
                if (rightParent.getFirstChild() == removingLt) {
                    // if next peer exists, set it to this
                    rightParent.setFirstChild(nextPeerLt);
                }

                if (rightParent.getLastChild() == removingLt) {
                    rightParent.setLastChild(nextPeerLt);
                }
            }
        }
    }

    private static void updatePaths(SegmentPrototype proto, Collection<InternalWorkingMemory> wms, PathEndNode endNode, SegmentPrototype[] newList) {
        PathMemSpec spec = endNode.getPathMemSpec();
        spec.update(BuildtimeSegmentUtilities.getPathAllLinkedMaskTest(endNode.getSegmentPrototypes(), endNode),
                    endNode.getSegmentPrototypes().length);

        // Now update the PathMemories array of SegmentMemories.
        // Also update all SegmentMemory after the split.
        for (WorkingMemory wm : wms) {
            PathMemory pmem = (PathMemory) wm.getNodeMemories().peekNodeMemory(endNode);

            if (pmem != null) {
                pmem.setAllLinkedMaskTest(spec.allLinkedTestMask());

                // @TODO alphaterminalnodes shouldn't really have segments created?
                SegmentMemory[] newSmems = new SegmentMemory[newList.length];
                for (int i = 0; i < newList.length; i++) {
                    SegmentPrototype smproto = newList[i];
                    if (!isInsideSubnetwork(endNode, smproto)) {
                        continue;
                    }
                    Memory mem = wm.getNodeMemories().peekNodeMemory(smproto.getRootNode());
                    if (mem != null && mem.getSegmentMemory() != null) {
                        SegmentMemory sm = mem.getSegmentMemory();
                        newSmems[i] = sm;

                        // only update segment after proto
                        if (i > proto.getPos()) {
                            long currentLinkedNodeMask  = sm.getLinkedNodeMask();
                            smproto.shallowUpdateSegmentMemory(sm);
                            sm.setLinkedNodeMask(currentLinkedNodeMask);
                        }

                        // update link mask status on pmem for all segments from proto1 onwards
                        if (i >= proto.getPos()) {
                            if (sm.getAllLinkedMaskTest() > 0 && sm.isSegmentLinked() ) {
                                pmem.setLinkedSegmentMask(pmem.getLinkedSegmentMask() | sm.getSegmentPosMaskBit());
                            } else {
                                pmem.setLinkedSegmentMask(pmem.getLinkedSegmentMask() & ~sm.getSegmentPosMaskBit());
                            }
                        }
                    } else {
                        // ensure the pmem is unset for this position, can happen if the segment that set this before shifted up
                        pmem.setLinkedSegmentMask(pmem.getLinkedSegmentMask() & ~(1 << i ));
                    }
                }
                pmem.setSegmentMemories(newSmems);
            }
        }
    }

    private static void notifyImpactedSegments(Collection<InternalWorkingMemory> wms, SegmentPrototype proto1, Set<SegmentMemoryPair> smemsToNotify) {
        // any impacted segments must be notified for potential linking
        for (InternalWorkingMemory wm : wms) {
            Memory mem1 = wm.getNodeMemories().peekNodeMemory(proto1.getRootNode());
            if (mem1 != null && mem1.getSegmentMemory() != null) {
                // there was a split segment, both need notifying.
                notifyImpactedSegments(mem1.getSegmentMemory(), wm, smemsToNotify);
            }
        }
    }

    private static void setNodeTypes(SegmentPrototype proto, LeftTupleNode[] protoNodes) {
        int nodeTypesInSegment = 0;
        for ( LeftTupleNode node : protoNodes) {
            nodeTypesInSegment = BuildtimeSegmentUtilities.updateNodeTypesMask(node, nodeTypesInSegment);
        }
        proto.setNodeTypesInSegment(nodeTypesInSegment);
    }

}