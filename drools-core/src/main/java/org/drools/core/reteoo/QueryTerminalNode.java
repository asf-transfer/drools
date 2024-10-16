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

import java.util.Map;

import org.drools.base.definitions.rule.impl.QueryImpl;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.GroupElement;
import org.drools.core.reteoo.builder.BuildContext;

/**
 * Leaf Rete-OO node responsible for enacting <code>Action</code> s on a
 * matched <code>Rule</code>.
 *
 * @see org.kie.api.definition.rule.Rule
 */
public class QueryTerminalNode extends AbstractTerminalNode implements LeftTupleSinkNode {



    private static final long serialVersionUID = 510l;

    public static final short type             = 8;
    
    private transient ObjectTypeNodeId leftInputOtnId;

    // ------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------
    public QueryTerminalNode() {
    }

    /**
     * Constructor
     *
     * @param id node ID
     * @param source the tuple source for this node
     * @param rule the rule this node belongs to
     * @param subrule the subrule this node belongs to
     * @param context the current build context
     */
    public QueryTerminalNode(final int id,
                             final LeftTupleSource source,
                             final RuleImpl rule,
                             final GroupElement subrule,
                             final int subruleIndex,                              
                             final BuildContext context) {
        super( id,
               context.getPartitionId(),
               source,
               context,
               rule, subrule, subruleIndex);
        this.hashcode = calculateHashCode();
    }

    public QueryImpl getQuery() {
        return (QueryImpl) getRule();
    }

    public String toString() {
        return "[QueryTerminalNode(" + this.getId() + "): query=" + this.getQuery().getName() + "]";
    }

    @Override
    public boolean isFireDirect() {
        return false;
    }

    void initDeclarations(Map<String, Declaration> decls, final BuildContext context) {
        this.requiredDeclarations = new Declaration[ getQuery().getParameters().length ];
        int i = 0;
        for ( Declaration declr : getQuery().getParameters() ) {
            this.requiredDeclarations[i++] =  decls.get( declr.getIdentifier() );
        }
    }

    public int getType() {
        return NodeTypeEnums.QueryTerminalNode;
    }


    public ObjectTypeNodeId getInputOtnId() {
        return leftInputOtnId;
    }

    public void setInputOtnId(ObjectTypeNodeId leftInputOtnId) {
        this.leftInputOtnId = leftInputOtnId;
    }

    @Override
    public Declaration[] getSalienceDeclarations() {
        throw new UnsupportedOperationException();
    }

    public void doAttach( BuildContext context ) {
        super.doAttach(context);
        getLeftTupleSource().addTupleSink( this, context );
        addAssociation(context.getRule(), context);
    }
}
