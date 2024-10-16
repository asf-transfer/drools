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

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.drools.base.base.ObjectType;
import org.drools.base.common.NetworkNode;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.base.rule.From;
import org.drools.base.rule.Pattern;
import org.drools.base.rule.accessor.DataProvider;
import org.drools.base.rule.constraint.AlphaNodeFieldConstraint;
import org.drools.core.RuleBaseConfiguration;
import org.drools.core.common.BetaConstraints;
import org.drools.core.common.EmptyBetaConstraints;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.Memory;
import org.drools.core.common.MemoryFactory;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.UpdateContext;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.core.util.AbstractLinkedListNode;
import org.drools.core.util.index.TupleList;
import org.drools.util.bitmask.AllSetBitMask;
import org.drools.util.bitmask.BitMask;

import static org.drools.base.reteoo.PropertySpecificUtil.calculateNegativeMask;
import static org.drools.base.reteoo.PropertySpecificUtil.calculatePositiveMask;
import static org.drools.base.reteoo.PropertySpecificUtil.getAccessibleProperties;
import static org.drools.base.reteoo.PropertySpecificUtil.isPropertyReactive;

public class FromNode<T extends FromNode.FromMemory> extends LeftTupleSource
    implements
    LeftTupleSinkNode,
    MemoryFactory<T> {
    private static final long          serialVersionUID = 510l;

    protected DataProvider               dataProvider;
    protected AlphaNodeFieldConstraint[] alphaConstraints;
    protected BetaConstraints            betaConstraints;

    protected LeftTupleSinkNode          previousTupleSinkNode;
    protected LeftTupleSinkNode          nextTupleSinkNode;
    
    protected From                       from;

    protected boolean                    tupleMemoryEnabled;

    protected transient ObjectTypeConf   objectTypeConf;

    public FromNode() {
    }

    public FromNode(final int id,
                    final DataProvider dataProvider,
                    final LeftTupleSource tupleSource,
                    final AlphaNodeFieldConstraint[] constraints,
                    final BetaConstraints binder,
                    final boolean tupleMemoryEnabled,
                    final BuildContext context,
                    final From from) {
        super(id, context);
        this.dataProvider = dataProvider;
        setLeftTupleSource(tupleSource);
        this.setObjectCount(leftInput.getObjectCount() + 1); // 'from' node increases the object count

        this.alphaConstraints = constraints;
        this.betaConstraints = (binder == null) ? EmptyBetaConstraints.getInstance() : binder;
        this.betaConstraints.init(context, getType());
        this.tupleMemoryEnabled = tupleMemoryEnabled;
        this.from = from;

        initMasks(context);

        hashcode = calculateHashCode();
    }

    private int calculateHashCode() {
        int hash = ( 23 * leftInput.hashCode() ) + ( 29 * dataProvider.hashCode() );
        if (from.getResultPattern() != null) {
            hash += 31 * from.getResultPattern().hashCode();
        }
        if (alphaConstraints != null) {
            hash += 37 * Arrays.hashCode( alphaConstraints );
        }
        if (betaConstraints != null) {
            hash += 41 * betaConstraints.hashCode();
        }
        return hash;
    }

    @Override
    public boolean equals( Object object ) {
        if (this == object) {
            return true;
        }

        if (((NetworkNode)object).getType() != NodeTypeEnums.FromNode || this.hashCode() != object.hashCode()) {
            return false;
        }

        FromNode other = (FromNode) object;

        return this.leftInput.getId() == other.leftInput.getId() &&
               dataProvider.equals( other.dataProvider ) &&
               Objects.equals(from.getResultPattern(), other.from.getResultPattern() ) &&
               Arrays.equals( alphaConstraints, other.alphaConstraints ) &&
               betaConstraints.equals( other.betaConstraints );
    }

    public DataProvider getDataProvider() {
        return dataProvider;
    }

    public AlphaNodeFieldConstraint[] getAlphaConstraints() {
        return alphaConstraints;
    }

    public BetaConstraints getBetaConstraints() {
        return betaConstraints;
    }

    @Override
    protected void initDeclaredMask(BuildContext context) {
        super.initDeclaredMask(context);

        if ( declaredMask.isAllSet() ) {
            return;
        }

        if ( context == null || context.getLastBuiltPatterns() == null ) {
            // only happens during unit tests
            declaredMask = AllSetBitMask.get();
            return;
        }

        Pattern pattern = context.getLastBuiltPatterns()[1];
        if ( pattern == null ) {
            return;
        }

        ObjectType objectType = pattern.getObjectType();

        // if pattern is null (e.g. for eval or query nodes) we cannot calculate the mask, so we set it all
        if ( isPropertyReactive( context.getRuleBase(), objectType ) ) {
            Collection<String> leftListenedProperties = pattern.getListenedProperties();
            List<String> accessibleProperties = getAccessibleProperties( context.getRuleBase(), objectType );
            declaredMask = declaredMask.setAll(calculatePositiveMask(objectType, leftListenedProperties, accessibleProperties));
            negativeMask = negativeMask.setAll(calculateNegativeMask(objectType, leftListenedProperties, accessibleProperties));
        }
    }

    @Override
    protected Pattern getLeftInputPattern( BuildContext context ) {
        return context.getLastBuiltPatterns()[0];
    }

    @Override
    protected BitMask setNodeConstraintsPropertyReactiveMask( BitMask mask, ObjectType objectType, List<String> accessibleProperties) {
        for (int i = 0; i < alphaConstraints.length; i++) {
            mask = mask.setAll(alphaConstraints[i].getListenedPropertyMask(Optional.empty(), objectType, accessibleProperties));
        }
        return mask;
    }

    public Class< ? > getResultClass() {
        return from.getResultClass();
    }

    public void networkUpdated(UpdateContext updateContext) {
        this.leftInput.networkUpdated(updateContext);
    }

    @SuppressWarnings("unchecked")
    public RightTuple createRightTuple(final TupleImpl leftTuple,
                                       final PropagationContext context,
                                       final ReteEvaluator reteEvaluator,
                                       final Object object) {
        return new RightTuple(createFactHandle(reteEvaluator, object) );
    }

    public InternalFactHandle createFactHandle( ReteEvaluator reteEvaluator, Object object ) {
        InternalFactHandle handle = reteEvaluator.getFactHandle(object);
        if (handle != null && handle.getObject() == object) {
            return handle;
        }

        if ( objectTypeConf == null ) {
            // use default entry point and object class. Notice that at this point object is assignable to resultClass
            objectTypeConf = new ClassObjectTypeConf( reteEvaluator.getDefaultEntryPointId(), getResultClass(), reteEvaluator.getKnowledgeBase() );
        }

        return reteEvaluator.createFactHandle(object, objectTypeConf, null );
    }


    public void addToCreatedHandlesMap(final Map<Object, RightTuple> matches,
                                       final RightTuple rightTuple) {
        if ( rightTuple.getFactHandle().isValid() ) {
            Object object = rightTuple.getFactHandle().getObject();
            // keeping a list of matches
            RightTuple existingMatch = matches.get(object);
            if ( existingMatch != null ) {
                // this is for the obscene case where two or more objects returned by "from"
                // have the same hash code and evaluate equals() to true, so we need to preserve
                // all of them to avoid leaks
                rightTuple.setNext(existingMatch);
            }
            matches.put( object,
                         rightTuple );
        }
    }


    public T createMemory(final RuleBaseConfiguration config, ReteEvaluator reteEvaluator) {
        BetaMemory beta = new BetaMemory(new TupleList(),
                                         null,
                                         this.betaConstraints.createContext(),
                                         NodeTypeEnums.FromNode );
        return (T) new FromMemory( beta,
                                   this.dataProvider );
    }

    public boolean isLeftTupleMemoryEnabled() {
        return tupleMemoryEnabled;
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

    public int getType() {
        return NodeTypeEnums.FromNode;
    } 

    public static class FromMemory extends AbstractLinkedListNode<Memory>
        implements
        Serializable,
        SegmentNodeMemory {
        private static final long serialVersionUID = 510l;

        private DataProvider      dataProvider;

        private final BetaMemory betaMemory;
        public        Object         providerContext;

        public FromMemory(BetaMemory betaMemory,
                          DataProvider dataProvider) {
            this.betaMemory = betaMemory;
            this.dataProvider = dataProvider;
            this.providerContext = dataProvider.createContext();
        }

        public int getNodeType() {
            return NodeTypeEnums.FromNode;
        }

        public SegmentMemory getSegmentMemory() {
            return betaMemory.getSegmentMemory();
        }

        public void setSegmentMemory(SegmentMemory segmentMemory) {
            betaMemory.setSegmentMemory(segmentMemory);
        }

        public BetaMemory<Object> getBetaMemory() {
            return betaMemory;
        }

        public void reset() {
            this.betaMemory.reset();
            this.providerContext = dataProvider.createContext();
        }

        @Override
        public long getNodePosMaskBit() {
            return betaMemory.getNodePosMaskBit();
        }

        @Override
        public void setNodePosMaskBit( long segmentPos ) {
            betaMemory.setNodePosMaskBit( segmentPos );
        }

        @Override
        public void setNodeDirtyWithoutNotify() {
            betaMemory.setNodeDirtyWithoutNotify();
        }

        @Override
        public void setNodeCleanWithoutNotify() {
            betaMemory.setNodeCleanWithoutNotify();
        }
    }

    @Override
    public ObjectTypeNode getObjectTypeNode() {
        return leftInput.getObjectTypeNode();
    }

    public void doAttach( BuildContext context ) {
        super.doAttach(context);
        this.leftInput.addTupleSink( this, context );
    }

    protected boolean doRemove(final RuleRemovalContext context,
                               final ReteooBuilder builder) {

        if ( !this.isInUse() ) {
            getLeftTupleSource().removeTupleSink( this );
            return true;
        }
        return false;
    }

}
