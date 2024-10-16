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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.drools.base.base.ValueType;
import org.drools.base.common.NetworkNode;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.base.rule.IndexableConstraint;
import org.drools.base.rule.accessor.FieldValue;
import org.drools.base.rule.accessor.ReadAccessor;
import org.drools.base.rule.constraint.AlphaNodeFieldConstraint;
import org.drools.base.util.index.ConstraintTypeOperator;
import org.drools.core.common.BaseNode;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.util.index.AlphaRangeIndex;

import static org.drools.base.util.index.IndexUtil.isBigDecimalEqualityConstraint;

public class CompositeObjectSinkAdapter implements ObjectSinkPropagator {

    //    /** You can override this property via a system property (eg -Ddrools.hashThreshold=4) */
    //    public static final String HASH_THRESHOLD_SYSTEM_PROPERTY = "drools.hashThreshold";
    //
    //    /** The threshold for when hashing kicks in */
    //    public static final int    THRESHOLD_TO_HASH              = Integer.parseInt( System.getProperty( HASH_THRESHOLD_SYSTEM_PROPERTY,
    //                                                                                                      "3" ) );

    private static final long serialVersionUID = 510L;

    private List<ObjectSinkNode>        otherSinks;
    private List<AlphaNode>        hashableSinks;
    private List<AlphaNode>        rangeIndexableSinks = null;

    private List<FieldIndex>    hashedFieldIndexes;
    private List<FieldIndex>    rangeIndexedFieldIndexes;

    private Map<HashKey, AlphaNode>             hashedSinkMap;
    private Map<FieldIndex, AlphaRangeIndex>          rangeIndexMap;

    private int               alphaNodeHashingThreshold;
    private int               alphaNodeRangeIndexThreshold;

    private ObjectSink[]      sinks;

    private Map<NetworkNode, NetworkNode> sinksMap;

    public CompositeObjectSinkAdapter() {
        this( 3, 3 );
    }

    public CompositeObjectSinkAdapter(final int alphaNodeHashingThreshold, final int alphaNodeRangeIndexThreshold) {
        this.alphaNodeHashingThreshold = alphaNodeHashingThreshold;
        this.alphaNodeRangeIndexThreshold = alphaNodeRangeIndexThreshold;
    }

    public void readExternal(ObjectInput in) throws IOException,
                                            ClassNotFoundException {
        otherSinks = (List<ObjectSinkNode>) in.readObject();
        hashableSinks = (List<AlphaNode>) in.readObject();
        rangeIndexableSinks = (List<AlphaNode>) in.readObject();
        hashedFieldIndexes = (List) in.readObject();
        rangeIndexedFieldIndexes = (List) in.readObject();
        hashedSinkMap = (Map<HashKey, AlphaNode>) in.readObject();
        rangeIndexMap = (Map<FieldIndex, AlphaRangeIndex>) in.readObject();
        alphaNodeHashingThreshold = in.readInt();
        alphaNodeRangeIndexThreshold = in.readInt();
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject( otherSinks );
        out.writeObject( hashableSinks );
        out.writeObject( rangeIndexableSinks );
        out.writeObject( hashedFieldIndexes );
        out.writeObject( rangeIndexedFieldIndexes );
        out.writeObject( hashedSinkMap );
        out.writeObject( rangeIndexMap );
        out.writeInt( alphaNodeHashingThreshold );
        out.writeInt( alphaNodeRangeIndexThreshold );
    }

    public List<ObjectSinkNode> getOthers() {
        return this.otherSinks;
    }

    public List<AlphaNode> getHashableSinks() {
        return this.hashableSinks;
    }

    public Map<HashKey, AlphaNode> getHashedSinkMap() {
        return this.hashedSinkMap;
    }

    public List<AlphaNode> getRangeIndexableSinks() {
        return rangeIndexableSinks;
    }

    public Map<FieldIndex, AlphaRangeIndex> getRangeIndexMap() {
        return rangeIndexMap;
    }

    public ObjectSinkPropagator addObjectSink(ObjectSink sink) {
        return addObjectSink(sink, 0, 0);
    }

    public ObjectSinkPropagator addObjectSink(ObjectSink sink, int alphaNodeHashingThreshold, int alphaNodeRangeIndexThreshold) {
        this.sinks = null; // dirty it, so it'll rebuild on next get
        if (this.sinksMap != null) {
            this.sinksMap.put( sink, sink );
        }
        if ( sink.getType() ==  NodeTypeEnums.AlphaNode ) {
            final AlphaNode alphaNode = (AlphaNode) sink;
            final ReadAccessor readAccessor = getHashableAccessor(alphaNode);

            // hash indexing
            if ( readAccessor != null ) {
                final int index = readAccessor.getIndex();
                final FieldIndex fieldIndex = registerFieldIndex( index, readAccessor );

                //DROOLS-678 : prevent null values from being hashed as 0s
                final FieldValue value = ((IndexableConstraint)alphaNode.getConstraint()).getField();
                if ( fieldIndex.getCount() >= this.alphaNodeHashingThreshold && this.alphaNodeHashingThreshold != 0 && ! value.isNull() ) {
                    if ( !fieldIndex.isHashed() ) {
                        hashSinks( fieldIndex );
                    }

                    // no need to check, we know  the sink  does not exist
                    this.hashedSinkMap.put( new HashKey( index,
                                                         value,
                                                         fieldIndex.getFieldExtractor() ),
                                            alphaNode );
                } else {
                    if ( this.hashableSinks == null ) {
                        this.hashableSinks = new ArrayList<>();
                    }
                    this.hashableSinks.add( alphaNode );
                }
                return this;
            }

            // range indexing
            if (isRangeIndexable(alphaNode)) {
                IndexableConstraint indexableConstraint = (IndexableConstraint) alphaNode.getConstraint();
                ReadAccessor ReadAccessor = indexableConstraint.getFieldExtractor();
                final int index = ReadAccessor.getIndex();
                final FieldIndex fieldIndex = registerFieldIndexForRange(index, ReadAccessor);
                final FieldValue value = indexableConstraint.getField();
                if (fieldIndex.getCount() >= this.alphaNodeRangeIndexThreshold && this.alphaNodeRangeIndexThreshold != 0 && !value.isNull()) {
                    if (!fieldIndex.isRangeIndexed()) {
                        rangeIndexSinks(fieldIndex);
                    }
                    this.rangeIndexMap.get(fieldIndex).add(alphaNode);
                } else {
                    if (rangeIndexableSinks == null) {
                        rangeIndexableSinks = new ArrayList<>();
                    }
                    rangeIndexableSinks.add(alphaNode);
                }
                return this;
            }
        }

        if ( this.otherSinks == null ) {
            this.otherSinks = new ArrayList<>();
        }

        this.otherSinks.add( (ObjectSinkNode) sink );
        return this;
    }

    static ReadAccessor getHashableAccessor(AlphaNode alphaNode) {
        AlphaNodeFieldConstraint fieldConstraint = alphaNode.getConstraint();
        if ( fieldConstraint instanceof IndexableConstraint ) {
            IndexableConstraint indexableConstraint = (IndexableConstraint) fieldConstraint;
            if ( isHashable( indexableConstraint ) ) {
                return indexableConstraint.getFieldExtractor();
            }
        }
        return null;
    }

    private static boolean isHashable( IndexableConstraint indexableConstraint ) {
        return indexableConstraint.getConstraintType() == ConstraintTypeOperator.EQUAL && indexableConstraint.getField() != null &&
                indexableConstraint.getFieldExtractor().getValueType() != ValueType.OBJECT_TYPE &&
               !isBigDecimalEqualityConstraint(indexableConstraint) &&
               // our current implementation does not support hashing of deeply nested properties
                indexableConstraint.getFieldExtractor().getIndex() >= 0;
    }

    public ObjectSinkPropagator removeObjectSink(final ObjectSink sink) {
        this.sinks = null; // dirty it, so it'll rebuild on next get
        if (this.sinksMap != null) {
            this.sinksMap.remove( sink );
        }
        if ( sink.getType() ==  NodeTypeEnums.AlphaNode ) {
            final AlphaNode alphaNode = (AlphaNode) sink;
            final AlphaNodeFieldConstraint fieldConstraint = alphaNode.getConstraint();

            if ( fieldConstraint instanceof IndexableConstraint ) {
                final IndexableConstraint indexableConstraint = (IndexableConstraint) fieldConstraint;
                final FieldValue value = indexableConstraint.getField();

                // hash index
                if ( isHashable( indexableConstraint ) ) {
                    final ReadAccessor fieldAccessor = indexableConstraint.getFieldExtractor();
                    final int index = fieldAccessor.getIndex();
                    final FieldIndex fieldIndex = unregisterFieldIndex( index );

                    if ( fieldIndex.isHashed() ) {
                        HashKey hashKey = new HashKey( index,
                                                       value,
                                                       fieldAccessor );
                        this.hashedSinkMap.remove( hashKey );
                        if ( fieldIndex.getCount() <= this.alphaNodeHashingThreshold - 1 ) {
                            // we have less than three so unhash
                            unHashSinks( fieldIndex );
                        }
                    } else {
                        this.hashableSinks.remove( alphaNode );
                    }

                    if ( this.hashableSinks != null && this.hashableSinks.isEmpty() ) {
                        this.hashableSinks = null;
                    }

                    return size() == 1 ? new SingleObjectSinkAdapter( getSinks()[0] ) : this;
                }

                // range index
                if (isRangeIndexable(alphaNode)) {
                    final ReadAccessor fieldAccessor = indexableConstraint.getFieldExtractor();
                    final int index = fieldAccessor.getIndex();
                    final FieldIndex fieldIndex = unregisterFieldIndexForRange(index);

                    if (fieldIndex.isRangeIndexed()) {
                        AlphaRangeIndex alphaRangeIndex = this.rangeIndexMap.get(fieldIndex);
                        alphaRangeIndex.remove(alphaNode);
                        if (fieldIndex.getCount() <= this.alphaNodeRangeIndexThreshold - 1) {
                            // we have less than THRESHOLD so unindex
                            unRangeIndexSinks(fieldIndex, alphaRangeIndex);
                        }
                    } else {
                        this.rangeIndexableSinks.remove(alphaNode);
                    }

                    if (this.rangeIndexableSinks != null && this.rangeIndexableSinks.isEmpty()) {
                        this.rangeIndexableSinks = null;
                    }

                    return size() == 1 ? new SingleObjectSinkAdapter(getSinks()[0]) : this;

                }
            }
        }

        // BetaRights must be removed by identity
        for (Iterator<ObjectSinkNode> it = otherSinks.iterator(); it.hasNext();) {
            if (sink == it.next()) {
                it.remove();
                break;
            }
        }

        if ( this.otherSinks.isEmpty() ) {
            this.otherSinks = null;
        }

        return size() == 1 ? new SingleObjectSinkAdapter( getSinks()[0] ) : this;
    }

    void hashSinks(final FieldIndex fieldIndex) {
        if ( this.hashedSinkMap == null ) {
            this.hashedSinkMap = new HashMap<>();
        }

        final int index = fieldIndex.getIndex();
        final ReadAccessor fieldReader = fieldIndex.getFieldExtractor();

        Iterator<AlphaNode> sinkIterator = this.hashableSinks.iterator();
        while ( sinkIterator.hasNext() ) {
            final AlphaNode alphaNode = sinkIterator.next();
            final AlphaNodeFieldConstraint fieldConstraint = alphaNode.getConstraint();
            final IndexableConstraint indexableConstraint = (IndexableConstraint)  fieldConstraint;

            // only alpha nodes that have an Operator.EQUAL are in hashableSinks, so only check if it is
            // the right field index
            if ( index == indexableConstraint.getFieldExtractor().getIndex() ) {
                final FieldValue value = indexableConstraint.getField();
                this.hashedSinkMap.put( new HashKey( index,
                                                     value,
                                                     fieldReader ),
                                        alphaNode );

                // remove the alpha from the possible candidates of hashable sinks since it is now hashed
                sinkIterator.remove();
            }
        }

        if ( this.hashableSinks.isEmpty() ) {
            this.hashableSinks = null;
        }

        fieldIndex.setHashed( true );
    }

    void unHashSinks(final FieldIndex fieldIndex) {
        final int index = fieldIndex.getIndex();
        // this is the list of sinks that need to be removed from the hashedSinkMap
        final List<HashKey> unhashedSinks = new ArrayList<>();

        for ( AlphaNode alphaNode : this.hashedSinkMap.values() ) {
            final IndexableConstraint indexableConstraint = (IndexableConstraint) alphaNode.getConstraint();

            // only alpha nodes that have an Operator.EQUAL are in sinks, so only check if it is
            // the right field index
            if ( index == indexableConstraint.getFieldExtractor().getIndex() ) {
                final FieldValue value = indexableConstraint.getField();
                if ( this.hashableSinks == null ) {
                    this.hashableSinks = new ArrayList<>();
                }
                this.hashableSinks.add( alphaNode );

                unhashedSinks.add( new HashKey( index,
                                                value,
                                                fieldIndex.getFieldExtractor() ) );
            }
        }

        for ( HashKey hashKey : unhashedSinks ) {
            this.hashedSinkMap.remove( hashKey );
        }

        if ( this.hashedSinkMap.isEmpty() ) {
            this.hashedSinkMap = null;
        }

        fieldIndex.setHashed( false );
    }

    /**
     * Returns a FieldIndex which Keeps a count on how many times a particular field is used with an equality check
     * in the sinks.
     */
    private FieldIndex registerFieldIndex(final int index,
                                          final ReadAccessor fieldExtractor) {
        FieldIndex fieldIndex = null;

        // is linkedlist null, if so create and add
        if ( this.hashedFieldIndexes == null ) {
            this.hashedFieldIndexes = new ArrayList<>();
            fieldIndex = new FieldIndex( index,
                                         fieldExtractor );
            this.hashedFieldIndexes.add( fieldIndex );
        }

        // still null, so see if it already exists
        if ( fieldIndex == null ) {
            fieldIndex = findFieldIndex( index );
        }

        // doesn't exist so create it
        if ( fieldIndex == null ) {
            fieldIndex = new FieldIndex( index,
                                         fieldExtractor );
            this.hashedFieldIndexes.add( fieldIndex );
        }

        fieldIndex.increaseCounter();

        return fieldIndex;
    }

    private FieldIndex unregisterFieldIndex(final int index) {
        final FieldIndex fieldIndex = findFieldIndex( index );
        if (fieldIndex == null) {
            throw new IllegalStateException("Cannot find field index for index " + index + "!");
        }

        fieldIndex.decreaseCounter();

        // if the fieldcount is 0 then remove it from the linkedlist
        if ( fieldIndex.getCount() == 0 ) {
            this.hashedFieldIndexes.remove( fieldIndex );

            // if the linkedlist is empty then null it
            if ( this.hashedFieldIndexes.isEmpty() ) {
                this.hashedFieldIndexes = null;
            }
        }
        return fieldIndex;
    }

    private FieldIndex findFieldIndex(final int index) {
        for ( FieldIndex node : this.hashedFieldIndexes ) {
            if ( node.getIndex() == index ) {
                return node;
            }
        }
        return null;
    }

    /**
     * Pick sinks from rangeIndexableSinks (which were stored until index threshold is exceeded) and put them into rangeIndex.
     */
    void rangeIndexSinks(final FieldIndex fieldIndex) {
        if (rangeIndexMap == null) {
            rangeIndexMap = new HashMap<>();
        }
        AlphaRangeIndex alphaRangeIndex = rangeIndexMap.computeIfAbsent(fieldIndex, AlphaRangeIndex::new);

        final int index = fieldIndex.getIndex();

        if (rangeIndexableSinks != null) {
            Iterator<AlphaNode> sinkIterator = this.rangeIndexableSinks.iterator();

            while (sinkIterator.hasNext()) {
                final AlphaNode alphaNode = sinkIterator.next();
                final AlphaNodeFieldConstraint fieldConstraint = alphaNode.getConstraint();
                final IndexableConstraint indexableConstraint = (IndexableConstraint) fieldConstraint;

                if (index == indexableConstraint.getFieldExtractor().getIndex()) {
                    alphaRangeIndex.add(alphaNode);

                    // remove the alpha from the possible candidates of range indexable sinks since it is now range indexed
                    sinkIterator.remove();
                }
            }
            if (rangeIndexableSinks.isEmpty()) {
                rangeIndexableSinks = null;
            }
        }

        fieldIndex.setRangeIndexed(true);
    }

    void unRangeIndexSinks(final FieldIndex fieldIndex, AlphaRangeIndex alphaRangeIndex) {
        if (rangeIndexableSinks == null) {
            rangeIndexableSinks = new ArrayList<>();
        }
        rangeIndexableSinks.addAll(alphaRangeIndex.getAllValues());

        alphaRangeIndex.clear();
        rangeIndexMap.remove(fieldIndex);
        if (rangeIndexMap.isEmpty()) {
            rangeIndexMap = null;
        }

        fieldIndex.setRangeIndexed(false);
    }

    private boolean isRangeIndexable(AlphaNode alphaNode) {
        AlphaNodeFieldConstraint fieldConstraint = alphaNode.getConstraint();
        if (fieldConstraint instanceof IndexableConstraint) {
            IndexableConstraint indexableConstraint = (IndexableConstraint) fieldConstraint;
            ConstraintTypeOperator constraintType = indexableConstraint.getConstraintType();
            return (constraintType.isAscending() || constraintType.isDescending()) &&
                    indexableConstraint.getField() != null && !indexableConstraint.getField().isNull() &&
                    indexableConstraint.getFieldExtractor().getValueType() != ValueType.OBJECT_TYPE &&
                    // our current implementation does not support range indexing of deeply nested properties
                    indexableConstraint.getFieldExtractor().getIndex() >= 0;
        }
        return false;
    }

    /**
     * Returns a FieldIndex which Keeps a count on how many times a particular field is used with a range check
     * in the sinks.
     */
    private FieldIndex registerFieldIndexForRange(final int index,
                                                  final ReadAccessor fieldExtractor) {
        if (rangeIndexedFieldIndexes == null) {
            rangeIndexedFieldIndexes = new ArrayList<>();
        }
        FieldIndex fieldIndex = findFieldIndexForRange(index);

        // doesn't exist so create it
        if (fieldIndex == null) {
            fieldIndex = new FieldIndex(index,
                                        fieldExtractor);
            rangeIndexedFieldIndexes.add(fieldIndex);
        }

        fieldIndex.increaseCounter();

        return fieldIndex;
    }

    private FieldIndex unregisterFieldIndexForRange(final int index) {
        final FieldIndex fieldIndex = findFieldIndexForRange( index );
        if (fieldIndex == null) {
            throw new IllegalStateException("Cannot find field index for index " + index + "!");
        }
        fieldIndex.decreaseCounter();

        // if the fieldcount is 0 then remove it from the linkedlist
        if ( fieldIndex.getCount() == 0 ) {
            rangeIndexedFieldIndexes.remove( fieldIndex );
        }
        if (rangeIndexedFieldIndexes.isEmpty()) {
            rangeIndexedFieldIndexes = null;
        }
        return fieldIndex;
    }

    private FieldIndex findFieldIndexForRange(final int index) {
        if (rangeIndexedFieldIndexes == null) {
            return null;
        }
        for (FieldIndex node : rangeIndexedFieldIndexes) {
            if (node.getIndex() == index) {
                return node;
            }
        }
        return null;
    }

    public void propagateAssertObject(final InternalFactHandle factHandle,
                                      final PropagationContext context,
                                      final ReteEvaluator reteEvaluator) {
        final Object object = factHandle.getObject();

        // Iterates the FieldIndex collection, which tells you if particularly field is hashed or not
        // if the field is hashed then it builds the hashkey to return the correct sink for the current objects slot's
        // value, one object may have multiple fields indexed.
        if ( this.hashedFieldIndexes != null ) {
            // Iterate the FieldIndexes to see if any are hashed
            for ( FieldIndex fieldIndex : this.hashedFieldIndexes ) {
                if ( !fieldIndex.isHashed() ) {
                    continue;
                }
                // this field is hashed so set the existing hashKey and see if there is a sink for it
                final AlphaNode sink = this.hashedSinkMap.get( new HashKey( fieldIndex, object ) );
                if ( sink != null ) {
                    // go straight to the AlphaNode's propagator, as we know it's true and no need to retest
                    sink.getObjectSinkPropagator().propagateAssertObject( factHandle, context, reteEvaluator );
                }
            }
        }

        // Range indexing
        if (this.rangeIndexMap != null) {
            // Iterate the FieldIndexes to see if any are range indexed
            for (Map.Entry<FieldIndex, AlphaRangeIndex> entry : this.rangeIndexMap.entrySet()) {
                if (!entry.getKey().isRangeIndexed()) {
                    continue;
                }
                for (AlphaNode sink : entry.getValue().getMatchingAlphaNodes(object)) {
                    // go straight to the AlphaNode's propagator, as we know it's true and no need to retest
                    sink.getObjectSinkPropagator().propagateAssertObject(factHandle, context, reteEvaluator);
                }
            }
        }

        // propagate unhashed
        if ( this.hashableSinks != null ) {
            for ( ObjectSinkNode sink : this.hashableSinks ) {
                doPropagateAssertObject( factHandle,
                                         context,
                                         reteEvaluator,
                                         sink );
            }
        }

        // propagate un-rangeindexed
        if ( this.rangeIndexableSinks != null ) {
            for ( ObjectSinkNode sink : this.rangeIndexableSinks ) {
                doPropagateAssertObject( factHandle,
                                         context,
                                         reteEvaluator,
                                         sink );
            }
        }

        if ( this.otherSinks != null ) {
            // propagate others
            for ( ObjectSinkNode sink : this.otherSinks ) {
                doPropagateAssertObject( factHandle,
                                         context,
                                         reteEvaluator,
                                         sink );
            }
        }
    }

    public void propagateModifyObject(final InternalFactHandle factHandle,
                                      final ModifyPreviousTuples modifyPreviousTuples,
                                      final PropagationContext context,
                                      final ReteEvaluator reteEvaluator) {
        final Object object = factHandle.getObject();

        // Iterates the FieldIndex collection, which tells you if particularly field is hashed or not
        // if the field is hashed then it builds the hashkey to return the correct sink for the current objects slot's
        // value, one object may have multiple fields indexed.
        if ( this.hashedFieldIndexes != null ) {
            // Iterate the FieldIndexes to see if any are hashed
            for ( FieldIndex fieldIndex : this.hashedFieldIndexes ) {
                if ( !fieldIndex.isHashed() ) {
                    continue;
                }
                // this field is hashed so set the existing hashKey and see if there is a sink for it
                final AlphaNode sink = this.hashedSinkMap.get( new HashKey( fieldIndex, object ) );
                if ( sink != null ) {
                    // go straight to the AlphaNode's propagator, as we know it's true and no need to retest
                    sink.getObjectSinkPropagator().propagateModifyObject( factHandle, modifyPreviousTuples, context, reteEvaluator );
                }
            }
        }

        // Range indexing
        if (this.rangeIndexMap != null) {
            // Iterate the FieldIndexes to see if any are range indexed
            for (Map.Entry<FieldIndex, AlphaRangeIndex> entry : this.rangeIndexMap.entrySet()) {
                if (!entry.getKey().isRangeIndexed()) {
                    continue;
                }
                for (AlphaNode sink : entry.getValue().getMatchingAlphaNodes(object)) {
                    // go straight to the AlphaNode's propagator, as we know it's true and no need to retest
                    sink.getObjectSinkPropagator().propagateModifyObject(factHandle, modifyPreviousTuples, context, reteEvaluator);
                }
            }
        }

        // propagate unhashed
        if ( this.hashableSinks != null ) {
            for ( ObjectSinkNode sink : this.hashableSinks ) {
                doPropagateModifyObject( factHandle,
                                         modifyPreviousTuples,
                                         context,
                                         reteEvaluator,
                                         sink );
            }
        }

        // propagate un-rangeindexed
        if ( this.rangeIndexableSinks != null ) {
            for ( ObjectSinkNode sink : this.rangeIndexableSinks ) {
                doPropagateModifyObject( factHandle,
                                         modifyPreviousTuples,
                                         context,
                                         reteEvaluator,
                                         sink );
            }
        }

        if ( this.otherSinks != null ) {
            // propagate others
            for ( ObjectSinkNode sink : this.otherSinks ) {
                doPropagateModifyObject( factHandle,
                                         modifyPreviousTuples,
                                         context,
                                         reteEvaluator,
                                         sink );
            }
        }
    }
    
    public void byPassModifyToBetaNode (final InternalFactHandle factHandle,
                                        final ModifyPreviousTuples modifyPreviousTuples,
                                        final PropagationContext context,
                                        final ReteEvaluator reteEvaluator) {
        final Object object = factHandle.getObject();

        // We need to iterate in the same order as the assert
        if ( this.hashedFieldIndexes != null ) {
            // Iterate the FieldIndexes to see if any are hashed
            for ( FieldIndex fieldIndex : this.hashedFieldIndexes ) {
                if ( !fieldIndex.isHashed() ) {
                    continue;
                }
                // this field is hashed so set the existing hashKey and see if there is a sink for it
                final AlphaNode sink = this.hashedSinkMap.get( new HashKey( fieldIndex, object ) );
                if ( sink != null ) {
                    // only alpha nodes are hashable
                    sink.getObjectSinkPropagator().byPassModifyToBetaNode( factHandle, modifyPreviousTuples, context, reteEvaluator );
                }
            }
        }

        // Range indexing
        if (this.rangeIndexMap != null) {
            // Iterate the FieldIndexes to see if any are range indexed
            for (Map.Entry<FieldIndex, AlphaRangeIndex> entry : this.rangeIndexMap.entrySet()) {
                if (!entry.getKey().isRangeIndexed()) {
                    continue;
                }
                for (AlphaNode sink : entry.getValue().getMatchingAlphaNodes(object)) {
                    sink.getObjectSinkPropagator().byPassModifyToBetaNode(factHandle, modifyPreviousTuples, context, reteEvaluator);
                }
            }
        }

        // propagate unhashed
        if ( this.hashableSinks != null ) {
            for ( AlphaNode sink : this.hashableSinks ) {
                // only alpha nodes are hashable
                sink.getObjectSinkPropagator().byPassModifyToBetaNode( factHandle, modifyPreviousTuples, context, reteEvaluator );
            }
        }

        // propagate un-rangeindexed
        if ( this.rangeIndexableSinks != null ) {
            for ( AlphaNode sink : this.rangeIndexableSinks ) {
                sink.getObjectSinkPropagator().byPassModifyToBetaNode( factHandle, modifyPreviousTuples, context, reteEvaluator );
            }
        }

        if ( this.otherSinks != null ) {
            // propagate others
            for ( ObjectSinkNode sink : this.otherSinks ) {
                // compound alpha, lianode or betanode
                sink.byPassModifyToBetaNode( factHandle, modifyPreviousTuples, context, reteEvaluator );
            }
        }
    }

    /**
     * This is a Hook method for subclasses to override. Please keep it protected unless you know
     * what you are doing.
     */
    protected void doPropagateAssertObject(InternalFactHandle factHandle,
                                           PropagationContext context,
                                           ReteEvaluator reteEvaluator,
                                           ObjectSink sink) {
        sink.assertObject( factHandle,
                           context,
                           reteEvaluator );
    }

    protected void doPropagateModifyObject(InternalFactHandle factHandle,
                                           final ModifyPreviousTuples modifyPreviousTuples,
                                           PropagationContext context,
                                           ReteEvaluator reteEvaluator,
                                           ObjectSink sink) {
        sink.modifyObject( factHandle,
                           modifyPreviousTuples,
                           context,
                           reteEvaluator );
    }

    public BaseNode getMatchingNode(BaseNode candidate) {
        if (sinksMap == null) {
            reIndexNodes();
        }
        return (( BaseNode ) sinksMap.get( candidate ));
    }

    public void reIndexNodes() {
        sinksMap = new HashMap<>();
        if ( this.otherSinks != null ) {
            for ( ObjectSinkNode sink : this.otherSinks ) {
                sinksMap.put( sink, sink );
            }
        }

        if ( this.hashableSinks != null ) {
            for ( ObjectSinkNode sink : this.hashableSinks ) {
                sinksMap.put( sink, sink );
            }
        }

        if ( this.rangeIndexableSinks != null ) {
            for ( ObjectSinkNode sink : this.rangeIndexableSinks ) {
                sinksMap.put( sink, sink );
            }
        }

        if ( this.hashedSinkMap != null ) {
            for ( ObjectSink sink : this.hashedSinkMap.values() ) {
                sinksMap.put( sink, sink );
            }
        }

        if ( this.rangeIndexMap != null ) {
            Collection<AlphaRangeIndex> alphaRangeIndexes = rangeIndexMap.values();
            for (AlphaRangeIndex alphaRangeIndex : alphaRangeIndexes) {
                Collection<AlphaNode> alphaNodes = alphaRangeIndex.getAllValues();
                alphaNodes.forEach(sink -> sinksMap.put( sink, sink ));
            }
        }
    }

    public ObjectSink[] getSinks() {
        if ( this.sinks != null ) {
            return sinks;
        }
        ObjectSink[] newSinks = new ObjectSink[size()];
        int at = 0;

        if ( this.hashedFieldIndexes != null ) {
            // Iterate the FieldIndexes to see if any are hashed
            for ( FieldIndex fieldIndex : this.hashedFieldIndexes ) {
                if ( !fieldIndex.isHashed() ) {
                    continue;
                }
                // this field is hashed so set the existing hashKey and see if there is a sink for it
                final int index = fieldIndex.getIndex();
                for ( Map.Entry<HashKey, AlphaNode> entry : this.hashedSinkMap.entrySet() ) {
                    if (entry.getKey().getIndex() == index) {
                        newSinks[at++] = entry.getValue();
                    }
                }
            }
        }

        if ( this.rangeIndexedFieldIndexes != null ) {
            // Iterate the FieldIndexes to see if any are range indexed
            for ( FieldIndex fieldIndex : this.rangeIndexedFieldIndexes ) {
                if ( !fieldIndex.isRangeIndexed() ) {
                    continue;
                }
                Collection<AlphaNode> alphaNodes = this.rangeIndexMap.get(fieldIndex).getAllValues();
                for (AlphaNode sink : alphaNodes) {
                    newSinks[at++] = sink;
                }
            }
        }

        if ( this.hashableSinks != null ) {
            for ( ObjectSinkNode sink : this.hashableSinks ) {
                newSinks[at++] = sink;
            }
        }

        if ( this.rangeIndexableSinks != null ) {
            for ( ObjectSinkNode sink : this.rangeIndexableSinks ) {
                newSinks[at++] = sink;
            }
        }

        if ( this.otherSinks != null ) {
            for ( ObjectSinkNode sink : this.otherSinks ) {
                newSinks[at++] = sink;
            }
        }
        this.sinks = newSinks;
        return newSinks;
    }
    
    public void doLinkSubnetwork(ReteEvaluator reteEvaluator) {
        if ( this.otherSinks != null ) {
            // this is only used for TupleToObjectNode nodes when exists are shared, we know there is no indexing for those
            for ( ObjectSinkNode sink : this.otherSinks ) {
                SingleObjectSinkAdapter.staticDoLinkSubnetwork(sink, reteEvaluator);
            }
        }
    }

    public void doUnlinkSubnetwork(ReteEvaluator reteEvaluator) {
        if ( this.otherSinks != null ) {
            // this is only used for TupleToObjectNode nodes when exists are shared, we know there is no indexing for those
            for ( ObjectSinkNode sink : this.otherSinks ) {
                SingleObjectSinkAdapter.staticDoUnlinkSubnetwork(sink, reteEvaluator);
            }
        }
    }     

    public int size() {
        return (this.otherSinks != null ? this.otherSinks.size() : 0) + (this.hashableSinks != null ? this.hashableSinks.size() : 0) + (this.hashedSinkMap != null ? this.hashedSinkMap.size() : 0)
                + (this.rangeIndexableSinks != null ? this.rangeIndexableSinks.size() : 0)
                + (this.rangeIndexMap != null ? rangeIndexMap.values().stream().map(AlphaRangeIndex::size).reduce(0, Integer::sum) : 0);
    }

    public boolean isEmpty() {
        return false;
    }

    public List getOtherSinks() {
        return otherSinks;
    }

    public List<FieldIndex> getHashedFieldIndexes() {
        return hashedFieldIndexes;
    }

    public List<FieldIndex> getRangeIndexedFieldIndexes() {
        return rangeIndexedFieldIndexes;
    }

    public static class HashKey implements Externalizable {

        private int index;
        private Object value;
        private boolean isNull = false;
        private int hashCode;

        public HashKey() { }

        public HashKey(FieldIndex fieldIndex, Object value) {
            this.setValue( fieldIndex.getIndex(), value, fieldIndex.getFieldExtractor() );
        }

        public HashKey(final int index,
                       final FieldValue value,
                       final ReadAccessor extractor) {
            this.setValue( index, extractor, value );
        }

        public void readExternal(ObjectInput in) throws IOException,
                                                ClassNotFoundException {
            index = in.readInt();
            value = in.readObject();
            isNull = in.readBoolean();
            hashCode = in.readInt();
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeInt( index );
            out.writeObject(value);
            out.writeBoolean( isNull );
            out.writeInt( hashCode );
        }

        public int getIndex() {
            return this.index;
        }

        public void setValue(final int index,
                             final Object value,
                             final ReadAccessor extractor) {
            this.index = index;
            Object extractedValue = extractor.getValue( null, value );

            if ( extractedValue != null ) {
                try {
                    this.setHashCode(extractedValue.hashCode());
                } catch (UnsupportedOperationException e) {
                    this.setHashCode( 0 );
                }
                this.value = extractedValue;
            } else {
                this.isNull = true;
                this.setHashCode( 0 );
            }
        }

        public void setValue(final int index,
                             final ReadAccessor extractor,
                             final FieldValue value) {
            this.index = index;

            this.isNull = value.isNull();
            if ( !isNull ) {
                this.value = extractor.getValueType().coerce( value.getValue() );
                this.setHashCode( this.value != null ? this.value.hashCode() : 0 );
            } else {
                this.setHashCode( 0 );
            }
        }

        private void setHashCode(final int hashSeed) {
            final int PRIME = 31;
            int result = 1;
            result = PRIME * result + hashSeed;
            result = PRIME * result + this.index;
            this.hashCode = result;
        }

        public Object getObjectValue() {
            return this.value;
        }

        public int hashCode() {
            return this.hashCode;
        }

        public boolean equals(final Object object) {
            if (!(object instanceof HashKey)) {
                return false;
            }
            final HashKey other = (HashKey) object;

            if (this.isNull != other.isNull || this.index != other.index) {
                return false;
            }

            return Objects.equals(this.value, other.getObjectValue());
        }
    }

    public static class FieldIndex implements Externalizable {
        private static final long    serialVersionUID = 510l;
        private int                  index;
        private ReadAccessor         fieldExtractor;

        private int                  count;

        private boolean              hashed;
        private boolean              rangeIndexed;

        public FieldIndex() {
        }

        public FieldIndex(final int index,
                          final ReadAccessor fieldExtractor) {
            this.index = index;
            this.fieldExtractor = fieldExtractor;
        }

        public void readExternal(ObjectInput in) throws IOException,
                                                ClassNotFoundException {
            index = in.readInt();
            fieldExtractor = (ReadAccessor) in.readObject();
            count = in.readInt();
            hashed = in.readBoolean();
            rangeIndexed = in.readBoolean();
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeInt( index );
            out.writeObject(fieldExtractor);
            out.writeInt( count );
            out.writeBoolean( hashed );
            out.writeBoolean( rangeIndexed );
        }

        public int getIndex() {
            return this.index;
        }

        public int getCount() {
            return this.count;
        }

        public ReadAccessor getFieldExtractor() {
            return this.fieldExtractor;
        }

        public boolean isHashed() {
            return this.hashed;
        }

        public void setHashed(final boolean hashed) {
            this.hashed = hashed;
        }

        public boolean isRangeIndexed() {
            return rangeIndexed;
        }

        public void setRangeIndexed(boolean rangeIndexed) {
            this.rangeIndexed = rangeIndexed;
        }

        public void increaseCounter() {
            this.count++;
        }

        public void decreaseCounter() {
            this.count--;
        }
    }
}
