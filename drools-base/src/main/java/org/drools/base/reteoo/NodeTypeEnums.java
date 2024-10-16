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
package org.drools.base.reteoo;

import org.drools.base.common.NetworkNode;

/**
 *
 */
public class NodeTypeEnums {
    public static final int ObjectSourceMask = 1;
    public static final int TupleSourceMask = 1 << 2;
    public static final int ObjectSinkMask = 1 << 3;
    public static final int TupleSinkMask        = 1 << 4;
    public static final int TupleNodeMask        = 1 << 5;
    public static final int LeftInputAdapterMask = 1 << 6;
    public static final int TerminalNodeMask = 1 << 7;
    public static final int EndNodeMask = 1 << 8;
    public static final int BetaMask    = 1 << 9;

    public static final int BetaRightMask    = 1 << 10;

    public static final int MemoryFactoryMask = 1 << 11;
    
    public static final int shift = 15; // This must shift the node IDs, enough so their bits are not mutated by the masks.

    // ObjectSource, ObjectSink
    public static final int EntryPointNode          = (100 << shift) | ObjectSourceMask | ObjectSinkMask;
    public static final int ReteNode                = (120 << shift) | ObjectSourceMask | ObjectSinkMask;
    public static final int ObjectTypeNode          = (130 << shift) | ObjectSourceMask | ObjectSinkMask;
    public static final int AlphaNode               = (140 << shift) | ObjectSourceMask | ObjectSinkMask;
    public static final int WindowNode              = (150 << shift) | ObjectSourceMask | ObjectSinkMask | MemoryFactoryMask;

    // ObjectSource, LeftTupleSink
    public static final int TupleToObjectNode = (160 << shift) | ObjectSourceMask | TupleSinkMask |
                                                TupleNodeMask | EndNodeMask | MemoryFactoryMask;

    public static final int JoinRightAdapterNode   = (162 << shift) | ObjectSinkMask | BetaRightMask;
    public static final int ExistsRightAdapterNode   = (164 << shift) | ObjectSinkMask | BetaRightMask;
    public static final int NotRightAdapterNode   = (166 << shift) | ObjectSinkMask | BetaRightMask;
    public static final int AccumulateRightAdapterNode   = (168 << shift) | ObjectSinkMask | BetaRightMask;


    // LefTTupleSink, LeftTupleNode
    public static final int RuleTerminalNode        = (180 << shift) | TupleSinkMask | TerminalNodeMask |
                                                      TupleNodeMask | EndNodeMask | MemoryFactoryMask;
    public static final int QueryTerminalNode       = (190 << shift) | TupleSinkMask | TerminalNodeMask |
                                                      TupleNodeMask | EndNodeMask | MemoryFactoryMask;

    // LeftTupleSource, LeftTupleNode
    public static final int LeftInputAdapterNode    = (210 << shift) | ObjectSinkMask | TupleSourceMask |
                                                      TupleNodeMask | LeftInputAdapterMask | MemoryFactoryMask;
    public static final int AlphaTerminalNode       = (220 << shift) | ObjectSinkMask | TupleSourceMask | TupleNodeMask | LeftInputAdapterMask | MemoryFactoryMask;

    // LeftTupleSource, LefTTupleSink, LeftTupleNode
    public static final int EvalConditionNode       = (230 << shift) | TupleSourceMask | TupleSinkMask | TupleNodeMask | MemoryFactoryMask;
    public static final int TimerConditionNode      = (240 << shift) | TupleSourceMask | TupleSinkMask | TupleNodeMask | MemoryFactoryMask;
    public static final int AsyncSendNode           = (250 << shift) | TupleSourceMask | TupleSinkMask | TupleNodeMask | MemoryFactoryMask;
    public static final int AsyncReceiveNode        = (260 << shift) | TupleSourceMask | TupleSinkMask | TupleNodeMask | MemoryFactoryMask;
    public static final int FromNode                = (270 << shift) | TupleSourceMask | TupleSinkMask | TupleNodeMask | MemoryFactoryMask;
    public static final int ReactiveFromNode        = (280 << shift) | TupleSourceMask | TupleSinkMask | TupleNodeMask | MemoryFactoryMask;

    public static final int QueryElementNode        = (300 << shift) | TupleSourceMask | TupleSinkMask | TupleNodeMask | MemoryFactoryMask;
    public static final int ConditionalBranchNode   = (310 << shift) | TupleSourceMask | TupleSinkMask | TupleNodeMask | MemoryFactoryMask;

    // LeftTupleSource, LefTTupleSink, LeftTupleNode, BetaNode
    public static final int BetaNode                = (320 << shift) | TupleSourceMask | TupleSinkMask | BetaMask | TupleNodeMask | MemoryFactoryMask;
    public static final int JoinNode                = (330 << shift) | TupleSourceMask | TupleSinkMask | BetaMask | TupleNodeMask | MemoryFactoryMask;
    public static final int NotNode                 = (340 << shift) | TupleSourceMask | TupleSinkMask | BetaMask | TupleNodeMask | MemoryFactoryMask;
    public static final int ExistsNode              = (350 << shift) | TupleSourceMask | TupleSinkMask | BetaMask | TupleNodeMask | MemoryFactoryMask;
    public static final int AccumulateNode          = (360 << shift) | TupleSourceMask | TupleSinkMask | BetaMask | TupleNodeMask | MemoryFactoryMask;

    public static final int MockBetaNode             = (400 << shift) | TupleSourceMask | TupleSinkMask | BetaMask | TupleNodeMask;
    public static final int MockAlphaNode            = (500 << shift) | ObjectSourceMask | ObjectSinkMask;


    public static boolean isObjectSource(NetworkNode node) {
        return (node.getType() & ObjectSourceMask) != 0;
    }

    public static boolean isObjectSink(NetworkNode node) {
        return (node.getType() & ObjectSinkMask) != 0;
    }

    public static boolean isLeftTupleSource(NetworkNode node) {
        return (node.getType() & TupleSourceMask) != 0;
    }

    public static boolean isBetaNode(NetworkNode node) {
        return (node.getType() & BetaMask) != 0;
    }

    public static boolean isBetaNodeWithSubnetwork(NetworkNode node) {
        return isBetaNode(node) && node.inputIsTupleToObjectNode();
    }

    public static boolean isBetaNodeWithoutSubnetwork(NetworkNode node) {
        return isBetaNode(node) && !node.inputIsTupleToObjectNode();
    }

    public static boolean isTerminalNode(NetworkNode node) {
        return (node.getType() & TerminalNodeMask) != 0;
    }

    public static boolean isLeftTupleSink(NetworkNode node) {
        return (node.getType() & TupleSinkMask) != 0;
    }

    public static boolean isEndNode(NetworkNode node) {
        return (node.getType() & EndNodeMask) != 0;
    }

    public static boolean isLeftTupleNode(NetworkNode node) {
        return (node.getType() & TupleNodeMask) != 0;
    }

    public static boolean isMemoryFactory(NetworkNode node) {
        return (node.getType() & MemoryFactoryMask) != 0;
    }
    /**
     * This is here because AlphaTerminalNode extends LeftInputAdapter node, so cannot be switched by getType return,
     * when all you need ot know is if it's an lian.
     * @param node
     * @return
     */
    public static boolean isLeftInputAdapterNode(NetworkNode node) {
        return (node.getType() & LeftInputAdapterMask) != 0;
    }

    public static boolean isBetaRightNode(NetworkNode node) {
        return (node.getType() & BetaRightMask) != 0;
    }

}
