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
package org.drools.base.common;

import java.io.Serializable;

import org.drools.base.reteoo.BaseTerminalNode;
import org.kie.api.definition.rule.Rule;

/**
 * Interface used to expose generic information on Rete nodes outside of he package. It is used
 * for exposing information events.
 */
public interface NetworkNode extends Serializable {

    /**
     * Returns the unique id that represents the node in the Rete network
     */
    int getId();

    /**
     * Returns the partition ID to which this node belongs to
     */
    RuleBasePartitionId getPartitionId();
    
    int getType();

    Rule[] getAssociatedRules();

    boolean isAssociatedWith( Rule rule );

    void addAssociatedTerminal(BaseTerminalNode terminalNode);
    void removeAssociatedTerminal(BaseTerminalNode terminalNode);

    int getAssociatedTerminalsSize();

    boolean hasAssociatedTerminal(BaseTerminalNode terminalNode);

    NetworkNode[] getSinks();

    default boolean inputIsTupleToObjectNode() {
        // not ideal, but this was here to allow NetworkNode to be in drools-base
        return false;
    }
}
