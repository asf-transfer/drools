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

import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.PropagationContext;

public class TupleFactory {
    public static TupleImpl createPeer(Sink n, TupleImpl original) {
        TupleImpl peer;
        switch(n.getType()) {
            case NodeTypeEnums.JoinNode:
            case NodeTypeEnums.AccumulateNode:
            case NodeTypeEnums.QueryElementNode:
            case NodeTypeEnums.FromNode:
                peer = new LeftTuple();
                break;
            case NodeTypeEnums.ExistsNode:
            case NodeTypeEnums.NotNode:
                peer = new NotNodeLeftTuple();
                break;
            case NodeTypeEnums.AsyncReceiveNode:
            case NodeTypeEnums.ConditionalBranchNode:
            case NodeTypeEnums.EvalConditionNode:
            case NodeTypeEnums.TimerConditionNode:
                peer = new EvalNodeLeftTuple();
                break;
            case NodeTypeEnums.ReactiveFromNode:
                peer = new ReactiveFromNodeLeftTuple();
                break;
            case NodeTypeEnums.TupleToObjectNode:
                peer = new SubnetworkTuple();
                break;
            case NodeTypeEnums.QueryTerminalNode:
            case NodeTypeEnums.RuleTerminalNode:
                peer = AgendaComponentFactory.get().createTerminalTuple();
                break;
            default:
                throw new IllegalStateException("Tuple factory not found for node: " + n);
        }

        peer.initPeer(original, n);
        original.setPeer( peer );
        return peer;
    }

    public static TupleImpl createLeftTuple(Sink s,
                                            final InternalFactHandle factHandle,
                                            boolean leftTupleMemoryEnabled) {

        switch(s.getType()) {
            case NodeTypeEnums.JoinNode:
            case NodeTypeEnums.AccumulateNode:
            case NodeTypeEnums.QueryElementNode:
            case NodeTypeEnums.FromNode:
            case NodeTypeEnums.AsyncSendNode:
                return new LeftTuple(factHandle, s, leftTupleMemoryEnabled);
            case NodeTypeEnums.ExistsNode:
            case NodeTypeEnums.NotNode:
                return new NotNodeLeftTuple(factHandle, s, leftTupleMemoryEnabled);
            case NodeTypeEnums.AsyncReceiveNode:
            case NodeTypeEnums.ConditionalBranchNode:
            case NodeTypeEnums.EvalConditionNode:
            case NodeTypeEnums.TimerConditionNode:
                return new EvalNodeLeftTuple(factHandle, s, leftTupleMemoryEnabled);
            case NodeTypeEnums.ReactiveFromNode:
                return new ReactiveFromNodeLeftTuple(factHandle, s, leftTupleMemoryEnabled);
            case NodeTypeEnums.TupleToObjectNode:
                return new SubnetworkTuple(factHandle, s, leftTupleMemoryEnabled);
            case NodeTypeEnums.QueryTerminalNode:
            case NodeTypeEnums.RuleTerminalNode:
                return AgendaComponentFactory.get().createTerminalTuple(factHandle, s, leftTupleMemoryEnabled);
            default:
                throw new IllegalStateException("Tuple factory not found for node: " + s);
        }
    }

    public static TupleImpl createLeftTuple(final InternalFactHandle factHandle,
                                            final TupleImpl leftTuple,
                                            final Sink s) {
        switch(s.getType()) {
            case NodeTypeEnums.JoinNode:
            case NodeTypeEnums.AccumulateNode:
            case NodeTypeEnums.QueryElementNode:
            case NodeTypeEnums.FromNode:
            case NodeTypeEnums.AsyncSendNode:
                return new LeftTuple(factHandle, leftTuple, s);
            case NodeTypeEnums.ExistsNode:
            case NodeTypeEnums.NotNode:
                return new NotNodeLeftTuple(factHandle, leftTuple, s);
            case NodeTypeEnums.AsyncReceiveNode:
            case NodeTypeEnums.ConditionalBranchNode:
            case NodeTypeEnums.EvalConditionNode:
            case NodeTypeEnums.TimerConditionNode:
                return new EvalNodeLeftTuple(factHandle, leftTuple, s);
            case NodeTypeEnums.ReactiveFromNode:
                return new ReactiveFromNodeLeftTuple(factHandle, leftTuple, s);
            case NodeTypeEnums.TupleToObjectNode:
                return new SubnetworkTuple(factHandle, leftTuple, s);
            case NodeTypeEnums.QueryTerminalNode:
            case NodeTypeEnums.RuleTerminalNode:
                return AgendaComponentFactory.get().createTerminalTuple(factHandle, leftTuple, s);
            default:
                throw new IllegalStateException("Tuple factory not found for node: " + s);
        }
    }

    public static TupleImpl createLeftTuple(TupleImpl leftTuple,
                                            Sink s,
                                            PropagationContext pctx,
                                            boolean leftTupleMemoryEnabled) {
        switch(s.getType()) {
            case NodeTypeEnums.JoinNode:
            case NodeTypeEnums.AccumulateNode:
            case NodeTypeEnums.QueryElementNode:
            case NodeTypeEnums.FromNode:
            case NodeTypeEnums.AsyncSendNode:
                return new LeftTuple(leftTuple, s, pctx, leftTupleMemoryEnabled);
            case NodeTypeEnums.ExistsNode:
            case NodeTypeEnums.NotNode:
                return new NotNodeLeftTuple(leftTuple, s, pctx, leftTupleMemoryEnabled);
            case NodeTypeEnums.AsyncReceiveNode:
            case NodeTypeEnums.ConditionalBranchNode:
            case NodeTypeEnums.EvalConditionNode:
            case NodeTypeEnums.TimerConditionNode:
                return new EvalNodeLeftTuple(leftTuple, s, pctx, leftTupleMemoryEnabled);
            case NodeTypeEnums.ReactiveFromNode:
                throw new IllegalStateException("ReactFromNode does not implement this constructor.");
            case NodeTypeEnums.TupleToObjectNode:
                return new SubnetworkTuple(leftTuple, s, pctx, leftTupleMemoryEnabled);
            case NodeTypeEnums.QueryTerminalNode:
            case NodeTypeEnums.RuleTerminalNode:
                return AgendaComponentFactory.get().createTerminalTuple(leftTuple, s, pctx, leftTupleMemoryEnabled);
            default:
                throw new IllegalStateException("Tuple factory not found for node: " + s);
        }
    }

    public static TupleImpl createLeftTuple(TupleImpl leftTuple,
                                            TupleImpl rightTuple,
                                            Sink s) {
        switch(s.getType()) {
            case NodeTypeEnums.JoinNode:
            case NodeTypeEnums.AccumulateNode:
            case NodeTypeEnums.QueryElementNode:
            case NodeTypeEnums.FromNode:
            case NodeTypeEnums.AsyncSendNode:
                return new LeftTuple(leftTuple, rightTuple, s);
            case NodeTypeEnums.ExistsNode:
            case NodeTypeEnums.NotNode:
                return new NotNodeLeftTuple(leftTuple, rightTuple, s);
            case NodeTypeEnums.AsyncReceiveNode:
            case NodeTypeEnums.ConditionalBranchNode:
            case NodeTypeEnums.EvalConditionNode:
            case NodeTypeEnums.TimerConditionNode:
                return new EvalNodeLeftTuple(leftTuple, rightTuple, s);
            case NodeTypeEnums.ReactiveFromNode:
                throw new IllegalStateException("ReactFromNode does not implement this constructor.");
            case NodeTypeEnums.TupleToObjectNode:
                return new SubnetworkTuple(leftTuple, rightTuple, s);
            case NodeTypeEnums.QueryTerminalNode:
            case NodeTypeEnums.RuleTerminalNode:
                return AgendaComponentFactory.get().createTerminalTuple(leftTuple, rightTuple, s);
            default:
                throw new IllegalStateException("Tuple factory not found for node: " + s);
        }
    }

    public static TupleImpl createLeftTuple(TupleImpl leftTuple,
                                            TupleImpl rightTuple,
                                            TupleImpl currentLeftChild,
                                            TupleImpl currentRightChild,
                                            Sink s,
                                            boolean leftTupleMemoryEnabled) {
        switch(s.getType()) {
            case NodeTypeEnums.JoinNode:
            case NodeTypeEnums.AccumulateNode:
            case NodeTypeEnums.QueryElementNode:
            case NodeTypeEnums.FromNode:
            case NodeTypeEnums.AsyncSendNode:
                return new LeftTuple(leftTuple, rightTuple, currentLeftChild, currentRightChild, s, leftTupleMemoryEnabled);
            case NodeTypeEnums.ExistsNode:
            case NodeTypeEnums.NotNode:
                return new NotNodeLeftTuple(leftTuple, rightTuple, currentLeftChild, currentRightChild, s, leftTupleMemoryEnabled);
            case NodeTypeEnums.AsyncReceiveNode:
            case NodeTypeEnums.ConditionalBranchNode:
            case NodeTypeEnums.EvalConditionNode:
            case NodeTypeEnums.TimerConditionNode:
                return new EvalNodeLeftTuple(leftTuple, rightTuple, currentLeftChild, currentRightChild, s, leftTupleMemoryEnabled);
            case NodeTypeEnums.ReactiveFromNode:
                return new ReactiveFromNodeLeftTuple(leftTuple, rightTuple, currentLeftChild, currentRightChild, s, leftTupleMemoryEnabled);
            case NodeTypeEnums.TupleToObjectNode:
                return new SubnetworkTuple(leftTuple, rightTuple, currentLeftChild, currentRightChild, s, leftTupleMemoryEnabled);
            case NodeTypeEnums.QueryTerminalNode:
            case NodeTypeEnums.RuleTerminalNode:
                return AgendaComponentFactory.get().createTerminalTuple(leftTuple, rightTuple, currentLeftChild, currentRightChild, s, leftTupleMemoryEnabled);
            default:
                throw new IllegalStateException("Tuple factory not found for node: " + s);
        }
    }
}
