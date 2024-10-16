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

import org.drools.base.common.NetworkNode;
import org.drools.core.common.Memory;
import org.drools.core.common.TupleSets;
import org.drools.core.reteoo.LeftTupleSinkNode;
import org.drools.core.reteoo.PathMemory;
import org.drools.core.reteoo.SegmentMemory;
import org.drools.core.util.AbstractLinkedListNode;

/**
* Created with IntelliJ IDEA.
* User: mdproctor
* Date: 03/05/2013
* Time: 15:47
* To change this template use File | Settings | File Templates.
*/
public class StackEntry extends AbstractLinkedListNode<StackEntry> {
    private final long                 bit;
    private final NetworkNode          node;
    private final LeftTupleSinkNode    sink;
    private final PathMemory           pmem;
    private final Memory               nodeMem;
    private final SegmentMemory[]      smems;
    private final int                  smemIndex;
    private final TupleSets trgTuples;
    private final boolean resumeFromNextNode;
    private final boolean processSubnetwork;


    public StackEntry(NetworkNode node,
                      long bit,
                      LeftTupleSinkNode sink,
                      PathMemory pmem,
                      Memory nodeMem,
                      SegmentMemory[] smems,
                      int smemIndex,
                      TupleSets trgTuples,
                      boolean resumeFromNextNode,
                      boolean processSubnetwork) {
        this.bit                = bit;
        this.node               = node;
        this.sink               = sink;
        this.pmem               = pmem;
        this.nodeMem            = nodeMem;
        this.smems              = smems;
        this.smemIndex          = smemIndex;
        this.trgTuples          = trgTuples;
        this.resumeFromNextNode = resumeFromNextNode;
        this.processSubnetwork  = processSubnetwork;
    }

    public long getBit() {
        return bit;
    }

    public NetworkNode getNode() {
        return node;
    }

    public PathMemory getRmem() {
        return pmem;
    }

    public Memory getNodeMem() {
        return nodeMem;
    }

    public SegmentMemory[] getSmems() {
        return smems;
    }

    public int getSmemIndex() {
        return smemIndex;
    }

    public TupleSets getTrgTuples() {
        return trgTuples;
    }

    public LeftTupleSinkNode getSink() {
        return sink;
    }

    public boolean isResumeFromNextNode() {
        return resumeFromNextNode;
    }


    public boolean isProcessSubnetwork() {
        return processSubnetwork;
    }
}
