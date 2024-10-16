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
package org.drools.serialization.protobuf.marshalling;

import java.util.HashMap;
import java.util.Map;

import org.drools.core.common.BaseNode;
import org.drools.core.impl.InternalRuleBase;
import org.drools.core.reteoo.RightInputAdapterNode;
import org.drools.core.reteoo.LeftTupleSink;
import org.drools.core.reteoo.LeftTupleSource;
import org.drools.core.reteoo.ObjectSink;
import org.drools.core.reteoo.ObjectSource;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.QueryTerminalNode;
import org.drools.core.reteoo.RuleTerminalNode;
import org.drools.core.reteoo.WindowNode;

public class RuleBaseNodes {
    public static Map<Integer, BaseNode> getNodeMap(InternalRuleBase kBase) {
        Map<Integer, BaseNode> nodes = new HashMap<>();
        buildNodeMap( kBase, nodes );
        return nodes;
    }
    
    private static void buildNodeMap(InternalRuleBase kBase,
                                     Map<Integer, BaseNode> nodes) {
        for ( ObjectTypeNode sink : kBase.getRete().getObjectTypeNodes() ) {
            nodes.put( sink.getId(),
                       sink );
            addObjectSink( kBase,
                           sink,
                           nodes );
        }
    }

    private static void addObjectSink(InternalRuleBase kBase,
                                      ObjectSink sink,
                                      Map<Integer, BaseNode> nodes) {
        // we don't need to store alpha nodes, as they have no state to serialise
        if ( sink instanceof LeftTupleSource ) {
            LeftTupleSource node = (LeftTupleSource) sink;
            for ( LeftTupleSink leftTupleSink : node.getSinkPropagator().getSinks() ) {
                addLeftTupleSink(kBase,
                                 leftTupleSink,
                                 nodes);
            }
        } else if ( sink instanceof RightInputAdapterNode) {
            RightInputAdapterNode node = (RightInputAdapterNode) sink;
            nodes.put( sink.getId(), ((BaseNode)sink) );
            addLeftTupleSink(kBase, node.getBetaNode(), nodes);
        } else if ( sink instanceof WindowNode ) {
            WindowNode node = (WindowNode) sink;
            nodes.put( sink.getId(), ((BaseNode)sink) );
            for ( ObjectSink objectSink : node.getObjectSinkPropagator().getSinks() ) {
                addObjectSink(kBase, objectSink, nodes);
            }
        } else {
            ObjectSource node = ( ObjectSource ) sink;
            for ( ObjectSink objectSink : node.getObjectSinkPropagator().getSinks() ) {
                addObjectSink( kBase,
                               objectSink,
                               nodes );
            }
        }
    }

    private static void addLeftTupleSink(InternalRuleBase kBase,
                                         LeftTupleSink sink,
                                         Map<Integer, BaseNode> nodes) {
        if ( sink instanceof LeftTupleSource ) {
            nodes.put( sink.getId(),
                       (LeftTupleSource) sink );
            for ( LeftTupleSink leftTupleSink : sink.getSinkPropagator().getSinks() ) {
                addLeftTupleSink( kBase,
                                  leftTupleSink,
                                  nodes );
            }
        } else if ( sink instanceof ObjectSource ) {
            // it may be a RIAN
            nodes.put( sink.getId(),
                       (ObjectSource) sink );
            for ( ObjectSink objectSink : ((ObjectSource)sink).getObjectSinkPropagator().getSinks() ) {
                addObjectSink( kBase,
                               objectSink,
                               nodes );
            }
        } else if ( sink instanceof RuleTerminalNode ) {
            nodes.put( sink.getId(),
                       (RuleTerminalNode) sink );
        } else if ( sink instanceof QueryTerminalNode ) {
            nodes.put( sink.getId(),
                       (QueryTerminalNode) sink );
        }
    }
}
