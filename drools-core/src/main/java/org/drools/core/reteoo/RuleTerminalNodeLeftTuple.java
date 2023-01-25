/*
 * Copyright 2010 Red Hat, Inc. and/or its affiliates.
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

package org.drools.core.reteoo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.drools.core.common.ActivationGroupNode;
import org.drools.core.common.ActivationNode;
import org.drools.core.common.ActivationsManager;
import org.drools.core.common.AgendaItem;
import org.drools.core.common.InternalAgendaGroup;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.definitions.rule.impl.RuleImpl;
import org.drools.core.phreak.RuleAgendaItem;
import org.drools.core.rule.Declaration;
import org.drools.core.rule.GroupElement;
import org.drools.core.rule.consequence.Activation;
import org.drools.core.rule.consequence.Consequence;
import org.drools.core.common.PropagationContext;
import org.kie.api.runtime.rule.FactHandle;

public class RuleTerminalNodeLeftTuple extends BaseLeftTuple implements AgendaItem {
    private static final long serialVersionUID = 540l;
    /**
     * The salience
     */
    private           int                                            salience;
    /**
     * The activation number
     */
    private           long                                           activationNumber;
    private           int                                            queueIndex;
    private           boolean                                        queued;
    private transient InternalAgendaGroup                            agendaGroup;
    private           ActivationGroupNode                            activationGroupNode;
    private           ActivationNode                                 activationNode;
    private           InternalFactHandle                             activationFactHandle;
    private           boolean                                        matched;
    private           boolean                                        active;
    protected         RuleAgendaItem                                 ruleAgendaItem;

    private Runnable callback;

    public RuleTerminalNodeLeftTuple() {
        // constructor needed for serialisation
    }

    // ------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------
    public RuleTerminalNodeLeftTuple(final InternalFactHandle factHandle,
                                     final LeftTupleSink sink,
                                     final boolean leftTupleMemoryEnabled) {
        super(factHandle,
              sink,
              leftTupleMemoryEnabled);
    }

    public RuleTerminalNodeLeftTuple(final InternalFactHandle factHandle,
                                     final LeftTuple leftTuple,
                                     final LeftTupleSink sink) {
        super(factHandle, leftTuple, sink);
    }

    public RuleTerminalNodeLeftTuple(final LeftTuple leftTuple,
                                     final LeftTupleSink sink,
                                     final PropagationContext pctx,
                                     final boolean leftTupleMemoryEnabled) {
        super(leftTuple,
              sink,
              pctx,
              leftTupleMemoryEnabled);
    }

    public RuleTerminalNodeLeftTuple(final LeftTuple leftTuple,
                                     RightTuple rightTuple,
                                     LeftTupleSink sink) {
        super(leftTuple,
              rightTuple,
              sink);
    }

    public RuleTerminalNodeLeftTuple(final LeftTuple leftTuple,
                                     final RightTuple rightTuple,
                                     final LeftTuple currentLeftChild,
                                     final LeftTuple currentRightChild,
                                     final LeftTupleSink sink,
                                     final boolean leftTupleMemoryEnabled) {
        super(leftTuple,
              rightTuple,
              currentLeftChild,
              currentRightChild,
              sink,
              leftTupleMemoryEnabled);
    }

    public void init(final long activationNumber,
                     final int salience,
                     final PropagationContext pctx,
                     final RuleAgendaItem ruleAgendaItem,
                     InternalAgendaGroup agendaGroup) {
        setPropagationContext(pctx);
        this.salience = salience;
        this.activationNumber = activationNumber;
        this.queueIndex = -1;
        this.matched = true;
        this.ruleAgendaItem = ruleAgendaItem;
        this.agendaGroup = agendaGroup;
    }

    public void update(final int salience,
                        final PropagationContext pctx) {
        setPropagationContext(pctx);
        this.salience = salience;
        this.matched = true;
    }

    /**
     * Retrieve the rule.
     *
     * @return The rule.
     */
    public RuleImpl getRule() {
        return getTerminalNode().getRule();
    }

    public Consequence getConsequence() {
        String consequenceName = ((RuleTerminalNode) getTerminalNode()).getConsequenceName();
        return consequenceName.equals(RuleImpl.DEFAULT_CONSEQUENCE_NAME) ? getTerminalNode().getRule().getConsequence() : getTerminalNode().getRule().getNamedConsequence(consequenceName);
    }

    /**
     * Retrieve the tuple.
     *
     * @return The tuple.
     */
    public LeftTuple getTuple() {
        return this;
    }

    public int getSalience() {
        return this.salience;
    }

    public void setSalience(int salience) {
        this.salience = salience;
    }

    public InternalFactHandle getActivationFactHandle() {
        return activationFactHandle;
    }

    public void setActivationFactHandle( InternalFactHandle factHandle ) {
        this.activationFactHandle = factHandle;
    }

    public RuleAgendaItem getRuleAgendaItem() {
        return ruleAgendaItem;
    }

    /*
         * (non-Javadoc)
         *
         * @see org.kie.spi.Activation#getActivationNumber()
         */
    public long getActivationNumber() {
        return this.activationNumber;
    }

    public boolean isQueued() {
        return this.queued;
    }

    public void setQueued(final boolean queued) {
        this.queued = queued;
        if (queued) {
            setActive(true);
        }
    }

    public void setQueueIndex(final int queueIndex) {
        this.queueIndex = queueIndex;
    }

    public void dequeue() {
        if (this.agendaGroup != null) {
            this.agendaGroup.remove(this);
        }
        setQueued(false);
    }

    public int getQueueIndex() {
        return this.queueIndex;
    }

    public void remove() {
        dequeue();
    }

    public ActivationGroupNode getActivationGroupNode() {
        return this.activationGroupNode;
    }

    public void setActivationGroupNode(final ActivationGroupNode activationNode) {
        this.activationGroupNode = activationNode;
    }

    public InternalAgendaGroup getAgendaGroup() {
        return this.agendaGroup;
    }


    public ActivationNode getActivationNode() {
        return this.activationNode;
    }

    public void setActivationNode(final ActivationNode activationNode) {
        this.activationNode = activationNode;
    }

    public GroupElement getSubRule() {
        return getTerminalNode().getSubRule();
    }

    public TerminalNode getTerminalNode() {
        return (TerminalNode) getTupleSink();
    }

    public List<FactHandle> getFactHandles() {
        return getFactHandles(this);
    }

    public String toExternalForm() {
        return "[ " + this.getRule().getName() + " active=" + this.queued + " ]";
    }

    @Override
    public List<Object> getObjects() {
        return getObjects(this);
    }

    @Override
    public List<Object> getObjectsDeep() {
        return getObjectsDeep(this);
    }

    public Object getDeclarationValue(String variableName) {
        Declaration decl = getTerminalNode().getSubRule().getOuterDeclarations().get(variableName);
        // need to double check, but the working memory reference is only used for resolving globals, right?
        return decl.getValue(this);
    }

    public List<String> getDeclarationIds() {
        Declaration[] declArray = ((org.drools.core.reteoo.RuleTerminalNode) getTupleSink()).getAllDeclarations();
        List<String> declarations = new ArrayList<>();
        for (Declaration decl : declArray) {
            declarations.add(decl.getIdentifier());
        }
        return Collections.unmodifiableList(declarations);
    }

    public boolean isMatched() {
        return matched;
    }

    public void setMatched(boolean matched) {
        this.matched = matched;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isRuleAgendaItem() {
        return false;
    }

    public boolean hasBlockers() {
        return false;
    }

    @Override
    public Runnable getCallback() {
        return callback;
    }

    @Override
    public void setCallback( Runnable callback ) {
        this.callback = callback;
    }

    @Override
    public String toString() {
        return "["+toExternalForm()+" [ " + super.toString()+ " ] ]";
    }

    public void cancelActivation(ActivationsManager activationsManager) {
        activationsManager.cancelActivation( this );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        RuleTerminalNodeLeftTuple that = (RuleTerminalNodeLeftTuple) o;
        return ruleAgendaItem.getRule().getName().equals(that.ruleAgendaItem.getRule().getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), ruleAgendaItem.getRule().getName());
    }

    @Override
    public Activation asActivation() {
        return this;
    }
}
