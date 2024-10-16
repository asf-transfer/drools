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
import java.util.List;

import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.core.RuleBaseConfiguration;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.Memory;
import org.drools.core.common.MemoryFactory;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.UpdateContext;
import org.drools.core.reteoo.builder.BuildContext;

public class MockLeftTupleSink extends LeftTupleSource
    implements
    LeftTupleSinkNode,
    MemoryFactory {
    private static final long serialVersionUID = 510l;
    private final List        asserted         = new ArrayList();
    private final List        retracted        = new ArrayList();

    private LeftTupleSinkNode     previousTupleSinkNode;
    private LeftTupleSinkNode     nextTupleSinkNode;

    public MockLeftTupleSink(BuildContext buildContext) {
        super( 0, buildContext );
    }

    public MockLeftTupleSink(final int id, BuildContext buildContext) {
        super( id, buildContext );
    }

    public MockLeftTupleSink(final int id) {
        super(id, null);
    }

    public List getAsserted() {
        return this.asserted;
    }

    public List getRetracted() {
        return this.retracted;
    }

    public void ruleAttached() {
    }

    public Memory createMemory(final RuleBaseConfiguration config, ReteEvaluator reteEvaluator) {
        return new PathMemory(null, null);
    }

    protected boolean doRemove(final RuleRemovalContext context,
                               final ReteooBuilder builder) {
        return true;
    }

    public void doAttach(BuildContext buildContext) {
    }

    /**
     * Returns the next node
     * @return
     *      The next TupleSinkNode
     */
    public LeftTupleSinkNode getNextLeftTupleSinkNode() {
        return this.nextTupleSinkNode;
    }

    /**
     * Sets the next node 
     * @param next
     *      The next TupleSinkNode
     */
    public void setNextLeftTupleSinkNode(final LeftTupleSinkNode next) {
        this.nextTupleSinkNode = next;
    }

    /**
     * Returns the previous node
     * @return
     *      The previous TupleSinkNode
     */
    public LeftTupleSinkNode getPreviousLeftTupleSinkNode() {
        return this.previousTupleSinkNode;
    }

    /**
     * Sets the previous node 
     * @param previous
     *      The previous TupleSinkNode
     */
    public void setPreviousLeftTupleSinkNode(final LeftTupleSinkNode previous) {
        this.previousTupleSinkNode = previous;
    }

    public boolean isLeftTupleMemoryEnabled() {
        return false;
    }

    public void networkUpdated(UpdateContext updateContext) {
    }

    public int getType() {
        return NodeTypeEnums.MockBetaNode;
    }

    public LeftTupleSource getLeftTupleSource() {
        if ( super.getLeftTupleSource() != null) {
            return super.getLeftTupleSource();
        }

        return new MockLeftTupleSink(null) {
            @Override
            public int getType() {
                return NodeTypeEnums.LeftInputAdapterNode;
            }
        };
    }

    public LeftTupleSource getParent() {
        if ( super.getLeftTupleSource() != null) {
            return super.getLeftTupleSource();
        }

        return new MockLeftTupleSink(null) {
            @Override
            public int getType() {
                return NodeTypeEnums.LeftInputAdapterNode;
            }
        };
    }

    @Override
    public ObjectTypeNode getObjectTypeNode() {
        return null;
    }

}
