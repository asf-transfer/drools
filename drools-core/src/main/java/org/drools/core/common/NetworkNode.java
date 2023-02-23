/*
 * Copyright 2005 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.core.common;

import org.drools.core.reteoo.TerminalNode;
import org.kie.api.definition.rule.Rule;

/**
 * Interface used to expose generic information on Rete nodes outside of he package. It is used
 * for exposing information events.
 */
public interface NetworkNode {

    /**
     * Returns the unique id that represents the node in the Rete network
     */
    int getId();

    /**
     * Returns the partition ID to which this node belongs to
     */
    RuleBasePartitionId getPartitionId();
    
    short getType();

    Rule[] getAssociatedRules();

    boolean isAssociatedWith( Rule rule );

    void addAssociatedTerminal(TerminalNode terminalNode);
    void removeAssociatedTerminal(TerminalNode terminalNode);

    int getAssociatedTerminalsSize();

    boolean hasAssociatedTerminal(NetworkNode terminalNode);

    NetworkNode[] getSinks();
}
