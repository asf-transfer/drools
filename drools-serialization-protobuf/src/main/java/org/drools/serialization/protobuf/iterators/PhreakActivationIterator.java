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
package org.drools.serialization.protobuf.iterators;

import java.util.ArrayList;
import java.util.List;

import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.core.common.BaseNode;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.Memory;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.impl.InternalRuleBase;
import org.drools.core.phreak.RuleExecutor;
import org.drools.core.reteoo.PathMemory;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.rule.consequence.InternalMatch;
import org.drools.core.util.Iterator;

public class PhreakActivationIterator implements Iterator<InternalMatch> {

    private final java.util.Iterator<InternalMatch> agendaItemIter;

    private PhreakActivationIterator(ReteEvaluator reteEvaluator, InternalRuleBase kbase) {
        agendaItemIter =  collectAgendaItems(kbase, reteEvaluator).iterator();
    }

    public static PhreakActivationIterator iterator(ReteEvaluator reteEvaluator) {
        return new PhreakActivationIterator( reteEvaluator, reteEvaluator.getKnowledgeBase() );
    }

    public InternalMatch next() {
        if ( agendaItemIter.hasNext() ) {
            return agendaItemIter.next();
        } else {
            return null;
        }
    }

    private static List<InternalMatch> collectAgendaItems(InternalRuleBase kbase, ReteEvaluator reteEvaluator) {
        List<InternalMatch> internalMatches = new ArrayList<>();

        for (TerminalNode[] nodeArray : kbase.getReteooBuilder().getTerminalNodes().values()) {
            for (TerminalNode node : nodeArray) {
                if (node.getType() == NodeTypeEnums.RuleTerminalNode) {
                    PathMemory pathMemory = (PathMemory) reteEvaluator.getNodeMemories().peekNodeMemory(node);
                    if (pathMemory != null && pathMemory.getRuleAgendaItem() != null) {
                        RuleExecutor ruleExecutor = pathMemory.getRuleAgendaItem().getRuleExecutor();
                        ruleExecutor.getDormantMatches().addAllToCollection(internalMatches);
                        ruleExecutor.getActiveMatches().addAllToCollection(internalMatches);
                    }
                }
            }
        }

        return internalMatches;
    }
}
