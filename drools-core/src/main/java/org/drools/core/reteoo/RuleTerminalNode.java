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
import java.util.Objects;

import org.drools.base.base.SalienceInteger;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.GroupElement;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.phreak.PhreakRuleTerminalNode;
import org.drools.core.phreak.RuleExecutor;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.core.rule.consequence.InternalMatch;

/**
 * Leaf Rete-OO node responsible for enacting <code>Action</code> s on a
 * matched <code>Rule</code>.
 *
 * @see org.kie.api.definition.rule.Rule
 */
public class RuleTerminalNode extends AbstractTerminalNode {
    private static final long             serialVersionUID = 510l;

    protected Declaration[]                 salienceDeclarations;
    protected Declaration[]                 enabledDeclarations;

    protected boolean                       fireDirect;

    protected transient ObjectTypeNodeId leftInputOtnId = ObjectTypeNodeId.DEFAULT_ID;

    protected String                        consequenceName;

    // ------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------
    public RuleTerminalNode() {
    }

    public RuleTerminalNode(final int id,
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

        setFireDirect( rule.getActivationListener().equals( "direct" ) );
        if ( isFireDirect() ) {
            rule.setSalience( new SalienceInteger(Integer.MAX_VALUE) );
        }

        setDeclarations( getSubRule().getOuterDeclarations() );

        initInferredMask();

        hashcode = calculateHashCode();
    }
    
    public void setDeclarations(Map<String, Declaration> decls) {
        setEnabledDeclarations( getRule().findEnabledDeclarations( decls ) );
        setSalienceDeclarations( getRule().findSalienceDeclarations( decls ) );
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[RuleTerminalNode(").append(getId()).append("): rule=").append(getRule().getName());
        if (consequenceName != null) {
            sb.append(", consequence=").append(consequenceName);
        }
        sb.append("]");
        return sb.toString();
    }

    public void doAttach( BuildContext context ) {
        super.doAttach(context);
        getLeftTupleSource().addTupleSink(this, context);
        addAssociation(context.getRule(), context);
    }

    void initDeclarations(Map<String, Declaration> decls, final BuildContext context) {
        this.consequenceName = context.getConsequenceName();

        String[] requiredDeclarationNames = getRule().getRequiredDeclarationsForConsequence(getConsequenceName());
        this.requiredDeclarations = new Declaration[requiredDeclarationNames.length];
        int i = 0;
        for ( String str : requiredDeclarationNames ) {
            this.requiredDeclarations[i++] = decls.get( str );
        }
    }
    
    public Declaration[] getSalienceDeclarations() {
        return salienceDeclarations;
    }

    public void setSalienceDeclarations(Declaration[] salienceDeclarations) {
        this.salienceDeclarations = salienceDeclarations;
    }

    public Declaration[] getEnabledDeclarations() {
        return enabledDeclarations;
    }

    public void setEnabledDeclarations( Declaration[] enabledDeclarations ) {
        this.enabledDeclarations = enabledDeclarations;
    }

    public String getConsequenceName() {
        return consequenceName == null ? RuleImpl.DEFAULT_CONSEQUENCE_NAME : consequenceName;
    }

    public void cancelMatch(InternalMatch match, ReteEvaluator reteEvaluator) {
        if ( match.isQueued() ) {
            TupleImpl leftTuple = match.getTuple();
            if ( match.getRuleAgendaItem() != null ) {
                // phreak must also remove the LT from the rule network evaluator
                if ( leftTuple.getMemory() != null ) {
                    leftTuple.getMemory().remove( leftTuple );
                }
            }
            RuleExecutor ruleExecutor = ((RuleTerminalNodeLeftTuple)leftTuple).getRuleAgendaItem().getRuleExecutor();
            PhreakRuleTerminalNode.doLeftDelete(ruleExecutor.getPathMemory().getActualActivationsManager( reteEvaluator ), ruleExecutor, (RuleTerminalNodeLeftTuple) leftTuple);
        }
    }

    protected int calculateHashCode() {
        return (31 * super.calculateHashCode()) + (consequenceName == null ? 0 : consequenceName.hashCode());
    }

    @Override
    public boolean equals(final Object object) {
        return super.equals(object) &&
               Objects.equals(consequenceName, ((RuleTerminalNode)object).consequenceName);
    }

    public int getType() {
        return NodeTypeEnums.RuleTerminalNode;
    }

    public ObjectTypeNodeId getInputOtnId() {
        return leftInputOtnId;
    }

    public void setInputOtnId(ObjectTypeNodeId leftInputOtnId) {
        this.leftInputOtnId = leftInputOtnId;
    }

    public boolean isFireDirect() {
        return fireDirect;
    }

    public void setFireDirect(boolean fireDirect) {
        this.fireDirect = fireDirect;
    }
}
