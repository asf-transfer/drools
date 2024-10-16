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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.core.common.Memory;
import org.drools.core.common.MemoryFactory;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.TupleSets;
import org.drools.core.common.TupleSetsImpl;
import org.drools.core.phreak.BuildtimeSegmentUtilities;
import org.drools.core.phreak.RuntimeSegmentUtilities;
import org.drools.core.reteoo.AsyncReceiveNode.AsyncReceiveMemory;
import org.drools.core.reteoo.QueryElementNode.QueryElementNodeMemory;
import org.drools.core.reteoo.TupleToObjectNode.SubnetworkPathMemory;
import org.drools.core.reteoo.TimerNode.TimerNodeMemory;
import org.drools.core.util.LinkedList;
import org.drools.core.util.DoubleLinkedEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.drools.core.phreak.RuntimeSegmentUtilities.getQuerySegmentMemory;

public class SegmentMemory extends LinkedList<SegmentMemory>
        implements
        DoubleLinkedEntry<SegmentMemory> {

    protected static final Logger log = LoggerFactory.getLogger(SegmentMemory.class);
    protected static final boolean IS_LOG_TRACE_ENABLED = log.isTraceEnabled();

    private SegmentPrototype   proto;
    private Memory[]       nodeMemories;
    private final List<PathMemory>   pathMemories = new ArrayList<>(1);;
    private final TupleSets stagedLeftTuples = new TupleSetsImpl();
    private long linkedNodeMask;
    private long dirtyNodeMask;
    private long allLinkedMaskTest;
    private long segmentPosMaskBit;
    private int pos = -1;
    private boolean active;
    private SegmentMemory previous;
    private SegmentMemory next;

    private transient List<PathMemory>  dataDrivenPathMemories;

    private transient List<SegmentMemory> peersWithDataDrivenPathMemories;

    public SegmentMemory() {

    }

    public SegmentMemory(LeftTupleNode rootNode) {
        this.proto = new SegmentPrototype(rootNode, null);
    }

    public <T extends Memory> T createNodeMemory(MemoryFactory<T> memoryFactory, ReteEvaluator reteEvaluator) {
        return reteEvaluator.getNodeMemory(memoryFactory);
    }

    public LeftTupleNode getRootNode() {
        return proto.getRootNode();
    }

    public SegmentPrototype getSegmentPrototype() {
        return proto;
    }

    public LeftTupleNode getTipNode() {
        return proto.getTipNode();
    }

    public void setTipNode(LeftTupleNode tipNode) {
        this.proto.setTipNode(tipNode);
    }

    public LeftTupleSink getSinkFactory() {
        return (LeftTupleSink) proto.getRootNode();
    }

    public Memory[] getNodeMemories() {
        return nodeMemories;
    }

    public void setNodeMemories(Memory[] nodeMemories) {
        this.nodeMemories = nodeMemories;
    }

    public long getLinkedNodeMask() {
        return linkedNodeMask;
    }

    public void setLinkedNodeMask(long linkedNodeMask) {
        this.linkedNodeMask = linkedNodeMask;
    }

    public long getDirtyNodeMask() {
        return dirtyNodeMask;
    }

    public void setDirtyNodeMask(long dirtyNodeMask) {
        this.dirtyNodeMask = dirtyNodeMask;
    }

    public void updateDirtyNodeMask(long mask) {
        dirtyNodeMask |= mask;
    }

    public void updateCleanNodeMask(long mask) {
        dirtyNodeMask &= (~mask);
    }


    public String getRuleNames() {
        StringBuilder sbuilder = new StringBuilder();
        for (int i = 0; i < pathMemories.size(); i++) {
            if (i > 0) {
                sbuilder.append(", ");
            }
            sbuilder.append(pathMemories.get(i));
        }

        return sbuilder.toString();
    }

    public boolean linkNode(long mask, ReteEvaluator reteEvaluator) {
        linkedNodeMask |= mask;
        dirtyNodeMask |= mask;
        if (IS_LOG_TRACE_ENABLED) {
            log.trace("LinkNode notify=true nmask={} smask={} spos={} rules={}", mask, linkedNodeMask, pos, getRuleNames());
        }

        return notifyRuleLinkSegment( reteEvaluator );
    }

    public boolean linkNodeWithoutRuleNotify(long mask) {
        linkedNodeMask |= mask;
        dirtyNodeMask |= mask;
        if (IS_LOG_TRACE_ENABLED) {
            log.trace("LinkNode notify=false nmask={} smask={} spos={} rules={}", mask, linkedNodeMask, pos, getRuleNames());
        }

        return linkSegmentWithoutRuleNotify();
    }

    public boolean linkSegmentWithoutRuleNotify(long mask) {
        dirtyNodeMask |= mask;
        return linkSegmentWithoutRuleNotify();
    }

    private boolean linkSegmentWithoutRuleNotify() {
        boolean dataDrivePmemLinked = false;
        if (isSegmentLinked()) {
            for (int i = 0, length = pathMemories.size(); i < length; i++) {
                // do not use foreach, don't want Iterator object creation
                PathMemory pmem = pathMemories.get(i);
                pmem.linkSegmentWithoutRuleNotify(segmentPosMaskBit);
                dataDrivePmemLinked |= ( pmem.isDataDriven() && pmem.isRuleLinked() );
            }
        }
        return dataDrivePmemLinked;
    }

    public boolean notifyRuleLinkSegment(ReteEvaluator reteEvaluator, long mask) {
        dirtyNodeMask |= mask;
        return notifyRuleLinkSegment(reteEvaluator);
    }

    public boolean notifyRuleLinkSegment(ReteEvaluator reteEvaluator) {
        boolean dataDrivePmemLinked = false;
        if (isSegmentLinked()) {
            for (int i = 0, length = pathMemories.size(); i < length; i++) {
                // do not use foreach, don't want Iterator object creation
                PathMemory pmem = pathMemories.get(i);
                notifyRuleLinkSegment(reteEvaluator, pmem);
                dataDrivePmemLinked |= ( pmem.isDataDriven() && pmem.isRuleLinked() );
            }
        }
        return dataDrivePmemLinked;
    }

    public void notifyRuleLinkSegment(ReteEvaluator reteEvaluator, PathMemory pmem) {
        pmem.linkSegment(segmentPosMaskBit, reteEvaluator);
    }

    public boolean unlinkNode(long mask, ReteEvaluator reteEvaluator) {
        boolean dataDrivePmemLinked = false;
        boolean linked = isSegmentLinked();
        // some node unlinking does not unlink the segment, such as nodes after a Branch CE
        linkedNodeMask &= ~mask;
        dirtyNodeMask |= mask;

        if (IS_LOG_TRACE_ENABLED) {
            log.trace("UnlinkNode notify=true nmask={} smask={} spos={} rules={}", mask, linkedNodeMask, pos, getRuleNames());
        }

        if (linked && !isSegmentLinked()) {
            for (int i = 0, length = pathMemories.size(); i < length; i++) {
                // do not use foreach, don't want Iterator object creation
                PathMemory pmem = pathMemories.get(i);
                // the data driven pmem has to be flushed only if the pmem was formerly linked
                dataDrivePmemLinked |= ( pmem.isDataDriven() && pmem.isRuleLinked() );
                pmem.unlinkedSegment(segmentPosMaskBit, reteEvaluator);
            }
        } else {
            // if not unlinked, then we still need to notify if the rule is linked
            for (int i = 0, length = pathMemories.size(); i < length; i++) {
                // do not use foreach, don't want Iterator object creation
                if (pathMemories.get(i).isRuleLinked() ){
                    pathMemories.get(i).doLinkRule(reteEvaluator);
                }
            }
        }
        return dataDrivePmemLinked;
    }

    public void unlinkSegment(ReteEvaluator reteEvaluator) {
        for (int i = 0, length = pathMemories.size(); i < length; i++) {
            // do not use foreach, don't want Iterator object creation
            pathMemories.get(i).unlinkedSegment(segmentPosMaskBit, reteEvaluator);
        }
    }

    public void unlinkNodeWithoutRuleNotify(long mask) {
        linkedNodeMask &= ~mask;
        if (IS_LOG_TRACE_ENABLED) {
            log.trace("UnlinkNode notify=false nmask={} smask={} spos={} rules={}", mask, linkedNodeMask, pos, getRuleNames());
        }
    }

    public long getAllLinkedMaskTest() {
        return allLinkedMaskTest;
    }

    public void setAllLinkedMaskTest(long allLinkedTestMask) {
        this.allLinkedMaskTest = allLinkedTestMask;
    }

    public boolean isSegmentLinked() {
        return (linkedNodeMask & allLinkedMaskTest) == allLinkedMaskTest;
    }

    public List<PathMemory> getPathMemories() {
        return pathMemories;
    }

    public void addPathMemory(PathMemory pathMemory) {
        if (pathMemories.contains(pathMemory)) {
            System.out.println("!!!");
        }

        pathMemories.add(pathMemory);
        if (getAllLinkedMaskTest() > 0 && isSegmentLinked()) {
            pathMemory.linkSegmentWithoutRuleNotify(segmentPosMaskBit);
        }
        if (pathMemory.isDataDriven()) {
            if (dataDrivenPathMemories == null) {
                dataDrivenPathMemories = new ArrayList<>();
            }
            dataDrivenPathMemories.add(pathMemory);
        }
    }

    public void mergePathMemories(SegmentMemory segmentMemory) {
        for (PathMemory pmem : segmentMemory.getPathMemories()) {
            if ( isAssociatedWith( pmem ) ) {
                addPathMemory( pmem );
            }
        }
    }

    private boolean isAssociatedWith( PathMemory pmem ) {
        if (NodeTypeEnums.TupleToObjectNode == pmem.getNodeType()) {
            for (PathEndNode endNode : pmem.getPathEndNode().getPathEndNodes() ) {
                if (NodeTypeEnums.isTerminalNode(endNode)) {
                    if ( proto.getRootNode().hasAssociatedTerminal((AbstractTerminalNode)endNode)) {
                        return true;
                    }
                }
            }
            return false;
        }
        return proto.getRootNode().hasAssociatedTerminal((AbstractTerminalNode)pmem.getPathEndNode() );
    }

    public void removePathMemory(PathMemory pathMemory) {
        pathMemories.remove( pathMemory );
        if (pathMemory.isDataDriven()) {
            dataDrivenPathMemories.remove( pathMemory );
            if (dataDrivenPathMemories.isEmpty()) {
                dataDrivenPathMemories = null;
            }
        }
    }

    public PathMemory getFirstDataDrivenPathMemory() {
        return dataDrivenPathMemories == null ? null : dataDrivenPathMemories.get(0);
    }

    public boolean hasDataDrivenPathMemories() {
        return dataDrivenPathMemories != null;
    }

    public List<PathMemory> getDataDrivenPathMemories() {
        return dataDrivenPathMemories;
    }

    public long getSegmentPosMaskBit() {
        return segmentPosMaskBit;
    }

    public void setSegmentPosMaskBit(long segmentPosMaskBit) {
        this.segmentPosMaskBit = segmentPosMaskBit;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean evaluating) {
        this.active = evaluating;
    }

    public int getPos() {
        return pos;
    }

    public void setPos(int pos) {
        this.pos = pos;
    }

    public TupleSets getStagedLeftTuples() {
        return stagedLeftTuples;
    }

    @Override
    public void add(SegmentMemory segmentMemory) {
        super.add(segmentMemory);
        if (segmentMemory.hasDataDrivenPathMemories()) {
            if (peersWithDataDrivenPathMemories == null) {
                peersWithDataDrivenPathMemories = new ArrayList<>();
            }
            peersWithDataDrivenPathMemories.add(segmentMemory);
        }
    }

    @Override
    public void remove(SegmentMemory segmentMemory) {
        super.remove(segmentMemory);
        if (peersWithDataDrivenPathMemories != null) {
            peersWithDataDrivenPathMemories.remove(segmentMemory);
            if (peersWithDataDrivenPathMemories.isEmpty()) {
                peersWithDataDrivenPathMemories = null;
            }
        }
    }

    public Iterator<SegmentMemory> getPeersWithDataDrivenPathMemoriesIterator() {
        return peersWithDataDrivenPathMemories == null ? Collections.emptyIterator() : peersWithDataDrivenPathMemories.iterator();
    }

    public SegmentMemory getNext() {
        return this.next;
    }

    public void setNext(SegmentMemory next) {
        this.next = next;
        if ( this.next == this) {
            throw new RuntimeException();
        }
    }

    public SegmentMemory getPrevious() {
        return this.previous;
    }

    public void setPrevious(SegmentMemory previous) {
        this.previous = previous;
    }

    public void clear() {
        previous = null;
        next = null;
    }

    @Override
    public int hashCode() {
        return proto.getRootNode().getId();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || (obj instanceof SegmentMemory &&
                               proto.getRootNode().getId() == ((SegmentMemory) obj).proto.getRootNode().getId() &&
                               proto.getTipNode().getId() == ((SegmentMemory) obj).proto.getTipNode().getId());
    }

    public void reset(SegmentPrototype segmentPrototype) {
        this.dirtyNodeMask = 0L;
        this.linkedNodeMask = segmentPrototype != null ? segmentPrototype.linkedNodeMask : 0L;
        stagedLeftTuples.resetAll();
    }

    @Override
    public String toString() {
        return "Segment root " + proto.getRootNode() + " tip " + proto.getTipNode();
    }

    public static class SegmentPrototype {
        private LeftTupleNode rootNode;
        private LeftTupleNode tipNode;
        long linkedNodeMask;
        long allLinkedMaskTest;
        long segmentPosMaskBit;
        int pos;

        boolean requiresEager;

        int nodeTypesInSegment = 0;

        MemoryPrototype[] memories;

        LeftTupleNode[] nodesInSegment;

        PathEndNode[] pathEndNodes;

        public SegmentPrototype(LeftTupleNode rootNode, LeftTupleNode tipNode) {
            this.rootNode = rootNode;
            this.tipNode = tipNode;
        }

        public SegmentMemory newSegmentMemory(ReteEvaluator reteEvaluator) {
            SegmentMemory smem = new SegmentMemory();
            updateSegmentMemory(smem, reteEvaluator);
            return smem;
        }

        public SegmentMemory shallowNewSegmentMemory() {
            SegmentMemory smem = new SegmentMemory();
            shallowUpdateSegmentMemory(smem);
            return smem;
        }

        public void shallowUpdateSegmentMemory(SegmentMemory smem) {
            smem.proto = this;
            smem.allLinkedMaskTest = this.allLinkedMaskTest;
            smem.segmentPosMaskBit = this.segmentPosMaskBit;
            smem.linkedNodeMask = this.linkedNodeMask;
            smem.pos = this.pos;
        }

        public void updateSegmentMemory(SegmentMemory smem, ReteEvaluator reteEvaluator) {
            shallowUpdateSegmentMemory(smem);
            Memory[] nodeMemories = new Memory[getNodesInSegment().length];
            for ( int i = 0; i < memories.length; i++) {
                Memory mem = reteEvaluator.getNodeMemory((MemoryFactory) getNodesInSegment()[i]);
                if (i > 0) {
                    mem.setPrevious(nodeMemories[i-1]);
                    nodeMemories[i-1].setNext(mem);
                }
                nodeMemories[i] = mem;
                mem.setSegmentMemory(smem);
                MemoryPrototype proto = memories[i];
                if (proto != null) {
                    proto.populateMemory(reteEvaluator, mem);
                }
            }
            smem.setNodeMemories(nodeMemories);
        }

        public SegmentPrototype initFromSegmentMemory(SegmentMemory smem) {
            this.linkedNodeMask = smem.linkedNodeMask;
            this.allLinkedMaskTest = smem.allLinkedMaskTest;
            this.segmentPosMaskBit = smem.segmentPosMaskBit;
            this.pos = smem.pos;
            int i = 0;
            memories = new MemoryPrototype[smem.nodeMemories.length];
            for (Memory mem : smem.nodeMemories) {
                memories[i++] = MemoryPrototype.get(mem);
            }
            return this;
        }

        public LeftTupleNode getRootNode() {
            return rootNode;
        }

        public LeftTupleNode getTipNode() {
            return tipNode;
        }

        public void setTipNode(LeftTupleNode tipNode) {
            this.tipNode = tipNode;
        }

        public void linkNode(long mask) {
            linkedNodeMask |= mask;
        }

        public long getLinkedNodeMask() {
            return linkedNodeMask;
        }

        public void setLinkedNodeMask(long linkedNodeMask) {
            this.linkedNodeMask = linkedNodeMask;
        }

        public void setAllLinkedMaskTest(long allLinkedMaskTest) {
            this.allLinkedMaskTest = allLinkedMaskTest;
        }

        public long getAllLinkedMaskTest() {
            return allLinkedMaskTest;
        }

        public void setSegmentPosMaskBit(long segmentPosMaskBit) {
            this.segmentPosMaskBit = segmentPosMaskBit;
        }

        public long getSegmentPosMaskBit() {
            return segmentPosMaskBit;
        }

        public int getPos() {
            return pos;
        }

        public void setPos(int pos) {
            this.pos = pos;
        }

        public MemoryPrototype[] getMemories() {
            return memories;
        }

        public void setMemories(MemoryPrototype[] memories) {
            this.memories = memories;
        }

        public LeftTupleNode[] getNodesInSegment() {
            return nodesInSegment;
        }

        public void setNodesInSegment(LeftTupleNode[] nodesInSegment) {
            this.nodesInSegment = nodesInSegment;
        }

        public int getNodeTypesInSegment() {
            return nodeTypesInSegment;
        }

        public void setNodeTypesInSegment(int nodeTypesInSegment) {
            this.nodeTypesInSegment = nodeTypesInSegment;
            requiresEager = BuildtimeSegmentUtilities.requiresAnEagerSegment(nodeTypesInSegment);
        }

        public boolean requiresEager() {
            return requiresEager;
        }

        public PathEndNode[] getPathEndNodes() {
            return pathEndNodes;
        }

        public void setPathEndNodes(PathEndNode[] pathEndNodes) {
            this.pathEndNodes = pathEndNodes;
        }

        public String toString() {
            StringBuilder sbuilder = new StringBuilder();
            sbuilder.append("SegmentMemory[");
            sbuilder.append("root " + rootNode + " tip " + tipNode + ", ");
            sbuilder.append("linkedNodeMask " + linkedNodeMask + ", ");
            sbuilder.append("allLinkedMaskTest " + allLinkedMaskTest + ", ");
            sbuilder.append("segmentPosMaskBit " + segmentPosMaskBit + ", ");
            sbuilder.append("pos " + pos + ", ");
            sbuilder.append("nodeTypesInSegment " + nodeTypesInSegment + " ");
            sbuilder.append("nodes ");
            if (nodesInSegment != null) {
                Arrays.stream(nodesInSegment).forEach(sbuilder::append);
            }
            sbuilder.append("]");
            return sbuilder.toString();
        }
    }

    public abstract static class MemoryPrototype {
        protected long nodePosMaskBit;

        public static MemoryPrototype get(Memory memory) {
            if (memory instanceof BetaMemory) {
                BetaMemory betaMemory = (BetaMemory)memory;
                return new BetaMemoryPrototype(betaMemory.getNodePosMaskBit(), betaMemory.getSubnetworkPathMemory() != null ? betaMemory.getSubnetworkPathMemory().getTupleToObjectNode() : null);
            }
            if (memory instanceof LeftInputAdapterNode.LiaNodeMemory) {
                return new LiaMemoryPrototype(((LeftInputAdapterNode.LiaNodeMemory)memory).getNodePosMaskBit());
            }
            if (memory instanceof QueryElementNode.QueryElementNodeMemory) {
                QueryElementNode.QueryElementNodeMemory queryMemory = (QueryElementNode.QueryElementNodeMemory)memory;
                return new QueryMemoryPrototype(queryMemory.getNodePosMaskBit(), queryMemory.getNode());
            }
            if (memory instanceof TimerNodeMemory) {
                return new TimerMemoryPrototype(((TimerNodeMemory)memory).getNodePosMaskBit());
            }
            if (memory instanceof AccumulateNode.AccumulateMemory) {
                BetaMemory betaMemory = ((AccumulateNode.AccumulateMemory)memory).getBetaMemory();
                return new AccumulateMemoryPrototype(new BetaMemoryPrototype( betaMemory.getNodePosMaskBit(), betaMemory.getSubnetworkPathMemory() != null ? betaMemory.getSubnetworkPathMemory().getTupleToObjectNode() : null) );
            }
            if (memory instanceof ReactiveFromNode.ReactiveFromMemory) {
                return new ReactiveFromMemoryPrototype(((ReactiveFromNode.ReactiveFromMemory)memory).getNodePosMaskBit());
            }
            return null;
        }

        public abstract void populateMemory(ReteEvaluator reteEvaluator, Memory memory);

        public void setNodePosMaskBit(long nodePosMaskBit) {
            this.nodePosMaskBit = nodePosMaskBit;
        }

        public long getNodePosMaskBit() {
            return nodePosMaskBit;
        }
    }

    public static class BetaMemoryPrototype extends MemoryPrototype {
        private final TupleToObjectNode tton;

        public BetaMemoryPrototype(long nodePosMaskBit, TupleToObjectNode tton) {
            this.nodePosMaskBit = nodePosMaskBit;
            this.tton           = tton;
        }

        @Override
        public void populateMemory(ReteEvaluator reteEvaluator, Memory memory) {
            BetaMemory betaMemory = (BetaMemory)memory;
            betaMemory.setNodePosMaskBit(nodePosMaskBit);
            if (tton != null) {
                SubnetworkPathMemory riaMem = (SubnetworkPathMemory) reteEvaluator.getNodeMemories().peekNodeMemory(tton);
                if (riaMem == null) {
                    riaMem = (SubnetworkPathMemory) RuntimeSegmentUtilities.initializePathMemory(reteEvaluator, tton);
                }
                betaMemory.setSubnetworkPathMemory(riaMem);
            }
        }
    }

    public static class LiaMemoryPrototype extends MemoryPrototype {
        public LiaMemoryPrototype(long nodePosMaskBit) {
            this.nodePosMaskBit = nodePosMaskBit;
        }

        @Override
        public void populateMemory(ReteEvaluator reteEvaluator, Memory liaMemory) {
            ((SegmentNodeMemory)liaMemory).setNodePosMaskBit( nodePosMaskBit );
        }
    }

    public static class ReactiveFromMemoryPrototype extends MemoryPrototype {
        public ReactiveFromMemoryPrototype(long nodePosMaskBit) {
            this.nodePosMaskBit = nodePosMaskBit;
        }

        @Override
        public void populateMemory(ReteEvaluator reteEvaluator, Memory memory) {
            ((ReactiveFromNode.ReactiveFromMemory)memory).setNodePosMaskBit( nodePosMaskBit );
        }
    }

    public static class ConditionalBranchMemoryPrototype extends MemoryPrototype {

        public ConditionalBranchMemoryPrototype() {
        }

        @Override
        public void populateMemory(ReteEvaluator reteEvaluator, Memory memory) {
        }
    }


    public static class RightInputAdapterPrototype extends MemoryPrototype {

        public RightInputAdapterPrototype() {
        }

        @Override
        public void populateMemory(ReteEvaluator reteEvaluator, Memory memory) {
        }
    }

    public static class TerminalPrototype extends MemoryPrototype {

        public TerminalPrototype() {
        }

        @Override
        public void populateMemory(ReteEvaluator reteEvaluator, Memory memory) {
        }
    }

    public static class FromMemoryPrototype extends MemoryPrototype {

        public FromMemoryPrototype() {
        }

        @Override
        public void populateMemory(ReteEvaluator reteEvaluator, Memory memory) {
        }
    }

    public static class EvalMemoryPrototype extends MemoryPrototype {

        public EvalMemoryPrototype() {
        }

        @Override
        public void populateMemory(ReteEvaluator reteEvaluator, Memory memory) {
        }
    }

    public static class QueryMemoryPrototype extends MemoryPrototype {
        private final QueryElementNode queryNode;

        public QueryMemoryPrototype(long nodePosMaskBit, QueryElementNode queryNode) {
            this.nodePosMaskBit = nodePosMaskBit;
            this.queryNode = queryNode;
        }

        @Override
        public void populateMemory(ReteEvaluator reteEvaluator, Memory mem) {
            QueryElementNodeMemory qmem = (QueryElementNodeMemory)  mem;
            SegmentMemory querySmem = getQuerySegmentMemory(reteEvaluator, queryNode);
            qmem.setQuerySegmentMemory(querySmem);
            qmem.setNodePosMaskBit(nodePosMaskBit);
        }
    }

    public static class TimerMemoryPrototype extends MemoryPrototype {

        public TimerMemoryPrototype(long nodePosMaskBit) {
            this.nodePosMaskBit = nodePosMaskBit;
        }

        @Override
        public void populateMemory(ReteEvaluator reteEvaluator, Memory mem) {
            TimerNodeMemory tmem = (TimerNodeMemory)  mem;
            tmem.setNodePosMaskBit(nodePosMaskBit);
        }
    }

    public static class AsyncSendMemoryPrototype extends MemoryPrototype {

        public AsyncSendMemoryPrototype() {
        }

        @Override
        public void populateMemory(ReteEvaluator reteEvaluator, Memory mem) {
        }
    }

    public static class AsyncReceiveMemoryPrototype extends MemoryPrototype {

        public AsyncReceiveMemoryPrototype(long nodePosMaskBit) {
            this.nodePosMaskBit = nodePosMaskBit;
        }

        @Override
        public void populateMemory(ReteEvaluator reteEvaluator, Memory mem) {
            AsyncReceiveMemory amem = (AsyncReceiveMemory)  mem;
            amem.setNodePosMaskBit(nodePosMaskBit);
        }
    }

    public static class AccumulateMemoryPrototype extends MemoryPrototype {

        private final BetaMemoryPrototype betaProto;

        public AccumulateMemoryPrototype(BetaMemoryPrototype betaProto) {
            this.betaProto = betaProto;
        }

        @Override
        public void populateMemory(ReteEvaluator reteEvaluator, Memory accMemory) {
            betaProto.populateMemory(reteEvaluator, ((AccumulateNode.AccumulateMemory)accMemory).getBetaMemory());
        }
    }
}
