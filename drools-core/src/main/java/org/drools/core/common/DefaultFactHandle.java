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
package org.drools.core.common;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.drools.base.common.RuleBasePartitionId;
import org.drools.base.factmodel.traits.TraitTypeEnum;
import org.drools.base.rule.EntryPointId;
import org.drools.core.WorkingMemoryEntryPoint;
import org.drools.core.impl.InternalRuleBase;
import org.drools.core.reteoo.LeftTuple;
import org.drools.core.reteoo.ObjectTypeNodeId;
import org.drools.core.reteoo.RightTuple;
import org.drools.core.reteoo.Tuple;
import org.drools.core.reteoo.TupleImpl;
import org.drools.core.util.AbstractLinkedListNode;
import org.drools.util.StringUtils;
import org.kie.api.runtime.rule.FactHandle;

/**
 * Implementation of <code>FactHandle</code>.
 */
@XmlRootElement(name = "fact-handle")
@XmlAccessorType(XmlAccessType.NONE)
public class DefaultFactHandle extends AbstractLinkedListNode<DefaultFactHandle>
                              implements
                              InternalFactHandle {

    private static final long       serialVersionUID = 510l;
    /** Handle id. */

    static final String     FACT_FORMAT_VERSION = "0";

    protected long id;
    protected long recency;
    protected Object object;
    private EqualityKey key;
    protected int objectHashCode;
    protected int identityHashCode;

    protected EntryPointId entryPointId;

    private boolean disconnected;

    private boolean valid = true;

    private boolean negated;

    protected String objectClassName;

    protected LinkedTuples linkedTuples;

    protected transient WorkingMemoryEntryPoint wmEntryPoint;

    public DefaultFactHandle() {
    }

    public DefaultFactHandle(final Object object) {
        this.object = object;
    }

    public DefaultFactHandle(final long id, final Object object) {
        // this is only used by tests, left as legacy as so many test rely on it.
        this( id, object, id, null );
    }

    /**
     * Construct.
     *
     * @param id
     *            Handle id.
     */
    public DefaultFactHandle(final long id,
                             final Object object,
                             final long recency,
                             final WorkingMemoryEntryPoint wmEntryPoint) {
        this( id, 0, object, recency, wmEntryPoint );
    }

    public DefaultFactHandle(final long id,
                             final int identityHashCode,
                             final Object object,
                             final long recency,
                             final WorkingMemoryEntryPoint wmEntryPoint ) {
        this(id, identityHashCode, object, recency, wmEntryPoint == null ? null : wmEntryPoint.getEntryPoint());
        if (wmEntryPoint != null) {
            setLinkedTuples( wmEntryPoint.getKnowledgeBase() );
            this.wmEntryPoint = wmEntryPoint;
        } else {
            this.linkedTuples = new SingleLinkedTuples();
        }
    }

    protected DefaultFactHandle(final long id,
                                final int identityHashCode,
                                final Object object,
                                final long recency,
                                final EntryPointId entryPointId ) {
        this.id = id;
        this.entryPointId = entryPointId;
        this.recency = recency;
        setObject( object );
        this.identityHashCode = identityHashCode;
    }

    public DefaultFactHandle(long id,
                             String wmEntryPointId,
                             int identityHashCode,
                             int objectHashCode,
                             long recency,
                             Object object) {
        this.id = id;
        this.entryPointId = new EntryPointId( wmEntryPointId );
        this.recency = recency;
        setObject( object );
        this.identityHashCode = identityHashCode;
        this.objectHashCode = objectHashCode;
        this.disconnected = true;
    }

    /**
     * @see Object
     */
    public boolean equals( final Object object ) {
        return this == object || ( object instanceof DefaultFactHandle && this.id == ( (DefaultFactHandle) object ).id );
    }

    public void disconnect() {
        this.key = null;
        this.linkedTuples = null;
        this.entryPointId = null;
        this.disconnected = true;
    }

    public boolean isNegated() {
        return negated;
    }

    public void setNegated(boolean negated) {
        this.negated = negated;
    }

    @Override
    public <K> K as(Class<K> klass) throws ClassCastException {
        if ( klass.isAssignableFrom( object.getClass() ) ) {
            return (K) object;
        }
        throw new ClassCastException( "The Handle's Object can't be cast to " + klass );
    }

    @Override
    public boolean isDisconnected() {
        return disconnected;
    }

    @Override
    public void setDisconnected( boolean disconnected ) {
        this.disconnected = disconnected;
    }

    public int getObjectHashCode() {
        if (this.objectHashCode == 0) {
            this.objectHashCode = object.hashCode();
        }
        return objectHashCode;
    }

    public int getIdentityHashCode() {
        if (this.identityHashCode == 0) {
            this.identityHashCode = determineIdentityHashCode( object );
        }
        return this.identityHashCode;
    }

    public static int determineIdentityHashCode( Object object ) {
        return System.identityHashCode( object );
    }

    protected void setIdentityHashCode( int identityHashCode ) {
        this.identityHashCode = identityHashCode;
    }

    protected void setObjectHashCode( int hashCode ) {
        this.objectHashCode = hashCode;
    }

    /**
     * @see Object
     */
    public int hashCode() {
        return Long.hashCode(this.id);
    }

    /**
     * format_version:id:identity:hashcode:recency
     *
     * @see FactHandle
     */
    public final String toExternalForm() {
        return getFormatVersion() + ":" + this.id +
               ":" +
               getIdentityHashCode() +
               ":" +
               getObjectHashCode() +
               ":" +
               getRecency() +
               ":" +
                ( ( this.entryPointId != null ) ? this.entryPointId.getEntryPointId() : "null" ) +
               ":" +
               getTraitType().name() +
               ":" +
                getObjectClassName();
    }

    protected String getFormatVersion() {
        return FACT_FORMAT_VERSION;
    }

    @XmlAttribute(name = "external-form")
    public String getExternalForm() {
        return toExternalForm();
    }

    public void setExternalForm(String externalForm) {
        populateFactHandleFromExternalForm( externalForm, this );
    }

    /**
     * @see Object
     */
    public String toString() {
        return "[fact " + toExternalForm() + ":" + this.object + "]";
    }

    public long getRecency() {
        return this.recency;
    }

    public void setRecency( final long recency ) {
        this.recency = recency;
    }

    public long getId() {
        return this.id;
    }

    public void invalidate() {
        valid = false;
    }

    public boolean isValid() {
        return valid;
    }

    public Object getObject() {
        return this.object;
    }

    public String getObjectClassName() {
        if (object != null) {
            this.objectClassName = object.getClass().getName();
        }
        return this.objectClassName;
    }

    public void setObject( final Object object ) {
        this.object = object;
        this.objectClassName = null;
        this.objectHashCode = 0;
        this.identityHashCode = 0;
    }

    /**
     * @return the key
     */
    public EqualityKey getEqualityKey() {
        return this.key;
    }

    /**
     * @param key the key to set
     */
    public void setEqualityKey( final EqualityKey key ) {
        this.key = key;
    }

    /**
     * Always returns false, since the DefaultFactHandle is
     * only used for regular Facts, and not for Events
     */
    @Override
    public boolean isEvent() {
        return false;
    }

    @Override
    public boolean isTraitOrTraitable() {
        return false;
    }

    @Override
    public boolean isTraitable() {
        return false;
    }

    @Override
    public boolean isTraiting() {
        return false;
    }

    @Override
    public TraitTypeEnum getTraitType() {
        return TraitTypeEnum.NON_TRAIT;
    }

    protected void setTraitType(TraitTypeEnum traitType) { }

    public ReteEvaluator getReteEvaluator() {
        return wmEntryPoint.getReteEvaluator();
    }

    public EntryPointId getEntryPointId() {
        return entryPointId;
    }

    @Override
    public WorkingMemoryEntryPoint getEntryPoint(ReteEvaluator reteEvaluator) {
        if (wmEntryPoint == null) {
            wmEntryPoint = reteEvaluator.getEntryPoint( entryPointId.getEntryPointId() );
        }
        return wmEntryPoint;
    }

    protected void setLinkedTuples(InternalRuleBase kbase) {
        linkedTuples = kbase != null && kbase.isPartitioned() ?
                       new CompositeLinkedTuples(kbase.getParallelEvaluationSlotsCount()) :
                       new SingleLinkedTuples();
    }

    public void addFirstLeftTuple( TupleImpl leftTuple ) {
        linkedTuples.addFirstLeftTuple( leftTuple );
    }

    public void addLastLeftTuple( TupleImpl leftTuple ) {
        linkedTuples.addLastLeftTuple( leftTuple );
    }

    public void removeLeftTuple( TupleImpl leftTuple ) {
        linkedTuples.removeLeftTuple( leftTuple );
    }

    public void addLastRightTuple( TupleImpl rightTuple ) {
        linkedTuples.addLastRightTuple( rightTuple );
    }

    public void removeRightTuple( TupleImpl rightTuple ) {
        linkedTuples.removeRightTuple( rightTuple );
    }

    public void clearLeftTuples() {
        linkedTuples.clearLeftTuples();
    }

    public void clearRightTuples() {
        linkedTuples.clearRightTuples();
    }

    public DefaultFactHandle clone() {
        DefaultFactHandle clone = new DefaultFactHandle( this.id, this.identityHashCode, this.object, this.recency, this.entryPointId );
        clone.key = this.key;
        clone.linkedTuples = this.linkedTuples.clone();

        clone.objectHashCode = this.objectHashCode;
        clone.disconnected = this.disconnected;
        clone.negated = this.negated;
        clone.wmEntryPoint = this.wmEntryPoint;
        return clone;
    }

    public static DefaultFactHandle createFromExternalFormat( String externalFormat ) {
        String[] elements = splitExternalForm( externalFormat );
        DefaultFactHandle handle;
        if (FACT_FORMAT_VERSION.equals( elements[0]) ) {
            handle = new DefaultFactHandle();
        } else if (DefaultEventHandle.EVENT_FORMAT_VERSION.equals(elements[0])) {
            handle = new DefaultEventHandle();
        } else {
            throw new RuntimeException( "Unknown fact handle version format: " + elements[0]);
        }
        populateFactHandleFromExternalForm( elements, handle );
        return handle;
    }

    private static String[] splitExternalForm( String externalFormat ) {
        String[] elements = externalFormat.split( ":" );
        if (elements.length < 6) {
            throw new IllegalArgumentException( "externalFormat did not have enough elements ["+externalFormat+"]" );
        }
        return elements;
    }

    private static void populateFactHandleFromExternalForm( String externalFormat, DefaultFactHandle handle ) {
        populateFactHandleFromExternalForm( splitExternalForm( externalFormat ), handle );
    }

    private static void populateFactHandleFromExternalForm( String[] elements, DefaultFactHandle handle ) {
        handle.id = Long.parseLong( elements[1] );
        handle.identityHashCode = Integer.parseInt( elements[2] );
        handle.objectHashCode = Integer.parseInt( elements[3] );
        handle.recency = Long.parseLong( elements[4] );
        handle.entryPointId = StringUtils.isEmpty( elements[5] ) || "null".equals( elements[5].trim() ) ?
                            null :
                            new EntryPointId( elements[5].trim() );
        handle.disconnected = true;
        handle.setTraitType( elements.length > 6 ? TraitTypeEnum.valueOf( elements[6] ) : TraitTypeEnum.NON_TRAIT );
        handle.objectClassName = elements.length > 7 ? elements[7] : null;
    }

    public boolean isExpired() {
        return false;
    }

    public boolean isPendingRemoveFromStore() {
        return false;
    }

    public static class SingleLinkedTuples implements LinkedTuples {
        private TupleImpl firstRightTuple;
        private TupleImpl lastRightTuple;

        private TupleImpl  firstLeftTuple;
        private TupleImpl  lastLeftTuple;

        public SingleLinkedTuples clone() {
            SingleLinkedTuples clone = new SingleLinkedTuples();
            clone.firstLeftTuple = this.firstLeftTuple;
            clone.lastLeftTuple = this.lastLeftTuple;
            clone.firstRightTuple = this.firstRightTuple;
            clone.lastRightTuple = this.lastRightTuple;
            return clone;
        }

        @Override
        public LinkedTuples cloneEmpty() {
            return new SingleLinkedTuples();
        }

        @Override
        public boolean hasTuples() {
            return firstLeftTuple != null || firstRightTuple != null;
        }

        @Override
        public void addFirstLeftTuple( TupleImpl leftTuple ) {
            TupleImpl previous = firstLeftTuple;
            leftTuple.setHandlePrevious( null );
            if ( previous == null ) {
                // no other LeftTuples, just add.
                leftTuple.setHandleNext( null );
                firstLeftTuple = leftTuple;
                lastLeftTuple = leftTuple;
            } else {
                leftTuple.setHandleNext( previous );
                previous.setHandlePrevious( leftTuple );
                firstLeftTuple = leftTuple;
            }
        }

        @Override
        public void addLastLeftTuple( TupleImpl leftTuple) {
            TupleImpl previous = lastLeftTuple;
            if ( previous == null ) {
                // no other LeftTuples, just add.
                leftTuple.setHandlePrevious( null );
                leftTuple.setHandleNext( null );
                firstLeftTuple = leftTuple;
            } else {
                leftTuple.setHandlePrevious( previous );
                leftTuple.setHandleNext( null );
                previous.setHandleNext( leftTuple );
            }
            lastLeftTuple = leftTuple;
        }

        @Override
        public void removeLeftTuple( TupleImpl leftTuple ) {
            TupleImpl previous = leftTuple.getHandlePrevious();
            TupleImpl next = leftTuple.getHandleNext();

            if ( previous != null && next != null ) {
                // remove  from middle
                previous.setHandleNext( next );
                next.setHandlePrevious( previous );
            } else if ( next != null ) {
                // remove from first
                next.setHandlePrevious( null );
                firstLeftTuple = next;
            } else if ( previous != null ) {
                // remove from end
                previous.setHandleNext( null );
                lastLeftTuple = previous;
            } else {
                // single remaining item, no previous or next
                firstLeftTuple = null;
                lastLeftTuple = null;
            }
            leftTuple.setHandlePrevious( null );
            leftTuple.setHandleNext( null );
        }

        @Override
        public void addFirstRightTuple( TupleImpl rightTuple ) {
            TupleImpl previousFirst =  firstRightTuple;
            firstRightTuple = rightTuple;
            rightTuple.setHandlePrevious( null );
            if ( previousFirst == null ) {
                rightTuple.setHandleNext( null );
                lastRightTuple = rightTuple;
            } else {
                rightTuple.setHandleNext( previousFirst );
                previousFirst.setHandlePrevious( rightTuple );
            }
        }

        @Override
        public void addLastRightTuple( TupleImpl rightTuple ) {
            TupleImpl previousLast = lastRightTuple;
            if ( previousLast == null ) {
                rightTuple.setHandlePrevious( null );
                rightTuple.setHandleNext( null );
                firstRightTuple = rightTuple;
            } else {
                rightTuple.setHandlePrevious( previousLast );
                rightTuple.setHandleNext( null );
                previousLast.setHandleNext(rightTuple );
            }
            lastRightTuple = rightTuple;
        }

        @Override
        public void removeRightTuple( TupleImpl rightTuple ) {
            RightTuple previous = (RightTuple) rightTuple.getHandlePrevious();
            RightTuple next     = (RightTuple) rightTuple.getHandleNext();

            if ( previous != null && next != null ) {
                // remove from middle
                previous.setHandleNext( next );
                next.setHandlePrevious( previous );
            } else if ( next != null ) {
                // remove from first
                next.setHandlePrevious( null );
                firstRightTuple = next;
            } else if ( previous != null ) {
                // remove from end
                previous.setHandleNext( null );
                lastRightTuple = previous;
            } else {
                // single remaining item, no previous or next
                firstRightTuple = null;
                lastRightTuple = null;
            }
            rightTuple.setHandlePrevious( null );
            rightTuple.setHandleNext( null );
        }

        @Override
        public void clearLeftTuples() {
            firstLeftTuple = null;
            lastLeftTuple = null;
        }

        @Override
        public void clearRightTuples() {
            firstRightTuple = null;
            lastRightTuple = null;
        }

        @Override
        public void forEachRightTuple(Consumer<TupleImpl> rightTupleConsumer) {
            for (TupleImpl rightTuple = firstRightTuple; rightTuple != null; ) {
                TupleImpl nextRightTuple = rightTuple.getHandleNext();
                rightTupleConsumer.accept( rightTuple );
                rightTuple = nextRightTuple;
            }
        }

        @Override
        public void forEachLeftTuple(Consumer<TupleImpl> leftTupleConsumer) {
            for ( TupleImpl leftTuple = firstLeftTuple; leftTuple != null; ) {
                TupleImpl nextLeftTuple = leftTuple.getHandleNext();
                leftTupleConsumer.accept( leftTuple );
                leftTuple = nextLeftTuple;
            }
        }

        public TupleImpl findFirstLeftTuple(Predicate<TupleImpl> lefttTuplePredicate ) {
            for ( TupleImpl leftTuple = firstLeftTuple; leftTuple != null; ) {
                TupleImpl nextLeftTuple = leftTuple.getHandleNext();
                if (lefttTuplePredicate.test( leftTuple )) {
                    return leftTuple;
                }
                leftTuple = nextLeftTuple;
            }
            return null;
        }

        @Override
        public TupleImpl getFirstLeftTuple(int partition) {
            return getFirstLeftTuple();
        }

        TupleImpl getFirstLeftTuple() {
            return firstLeftTuple;
        }

        @Override
        public TupleImpl getFirstRightTuple(int partition) {
            return getFirstRightTuple();
        }

        TupleImpl getFirstRightTuple() {
            return firstRightTuple;
        }

        public TupleImpl detachLeftTupleAfter(RuleBasePartitionId partitionId, ObjectTypeNodeId otnId) {
            TupleImpl tuple = lastLeftTuple;
            TupleImpl detached = null;
            // Find the first Tuple that comes after the current ID, so it can be detached.
            while (tuple != null && otnId.before(tuple.getInputOtnId())) {
                detached = tuple;
                tuple = tuple.getHandlePrevious();
            }

            if (detached != null) {
                if (firstLeftTuple == detached) {
                    firstLeftTuple = null;
                    lastLeftTuple = null;
                }

                if (lastLeftTuple == detached) {
                    lastLeftTuple = null;
                }

                if (detached.getHandlePrevious() != null) {
                    lastLeftTuple = detached.getHandlePrevious();
                    detached.setHandlePrevious(null);
                    lastLeftTuple.setHandleNext(null);
                }
            }
            return detached;
        }

        public TupleImpl detachRightTupleAfter(RuleBasePartitionId partitionId, ObjectTypeNodeId otnId) {
            TupleImpl tuple = lastRightTuple;
            TupleImpl detached = null;
            // Find the first Tuple that comes after the current ID, so it can be detached.
            while (tuple != null && otnId.before(tuple.getInputOtnId())) {
                detached = tuple;
                tuple = tuple.getHandlePrevious();
            }

            if (detached != null) {
                if (firstRightTuple == detached) {
                    firstRightTuple = null;
                    lastRightTuple = null;
                }

                if (lastRightTuple == detached) {
                    lastRightTuple = null;
                }

                if (detached.getHandlePrevious() != null) {
                    lastRightTuple = detached.getHandlePrevious();
                    detached.setHandlePrevious(null);
                    lastRightTuple.setHandleNext(null);
                }
            }
            return detached;
        }

        public void reattachToLeft(TupleImpl tuple) {
            if (lastLeftTuple == null) {
                lastLeftTuple = tuple;
            } else {
                lastLeftTuple.setHandleNext(tuple);
                tuple.setHandlePrevious(lastLeftTuple);
                lastLeftTuple = tuple;
            }
        }

        public void reattachToRight(TupleImpl tuple) {
            if (lastRightTuple == null) {
                lastRightTuple = tuple;
            } else {
                lastRightTuple.setHandleNext(tuple);
                tuple.setHandlePrevious(lastRightTuple);
                lastRightTuple = tuple;
            }
        }
    }

    public static class CompositeLinkedTuples implements LinkedTuples {

        private final LinkedTuples[] partitionedTuples;

        public CompositeLinkedTuples(int parallelEvaluationSlotsCount) {
            this.partitionedTuples = new LinkedTuples[parallelEvaluationSlotsCount];
        }

        private LinkedTuples getPartitionedTuple(int partition) {
            LinkedTuples tuples = partitionedTuples[partition];
            return tuples != null ? tuples : DummyLinkedTuples.INSTANCE;
        }

        private LinkedTuples getOrCreatePartitionedTuple(int partition) {
            LinkedTuples tuples = partitionedTuples[partition];
            if (tuples == null) {
                tuples = ( partitionedTuples[partition] = new SingleLinkedTuples() );
            }
            return tuples;
        }

        @Override
        public LinkedTuples cloneEmpty() {
            return new CompositeLinkedTuples(partitionedTuples.length);
        }

        @Override
        public boolean hasTuples() {
            for (int i = 0; i < partitionedTuples.length; i++) {
                if (getPartitionedTuple(i).hasTuples()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public LinkedTuples clone() {
            CompositeLinkedTuples clone = new CompositeLinkedTuples(partitionedTuples.length);
            for (int i = 0; i < partitionedTuples.length; i++) {
                clone.partitionedTuples[i] = partitionedTuples[i] == null ? null : partitionedTuples[i].clone();
            }
            return clone;
        }

        private LinkedTuples getPartitionedTuple(Tuple tuple) {
            return getPartitionedTuple(tuple.getSink().getPartitionId().getParallelEvaluationSlot());
        }

        private LinkedTuples getOrCreatePartitionedTuple(Tuple tuple) {
            return getOrCreatePartitionedTuple(tuple.getSink().getPartitionId().getParallelEvaluationSlot());
        }

        @Override
        public void addFirstLeftTuple( TupleImpl leftTuple ) {
            getOrCreatePartitionedTuple(leftTuple).addFirstLeftTuple( leftTuple );
        }

        @Override
        public void addLastLeftTuple( TupleImpl leftTuple ) {
            getOrCreatePartitionedTuple(leftTuple).addLastLeftTuple( leftTuple );
        }

        @Override
        public void removeLeftTuple( TupleImpl leftTuple ) {
            getPartitionedTuple(leftTuple).removeLeftTuple( leftTuple );
        }

        @Override
        public void addFirstRightTuple( TupleImpl rightTuple ) {
            getOrCreatePartitionedTuple(rightTuple).addFirstRightTuple( rightTuple );
        }

        @Override
        public void addLastRightTuple( TupleImpl rightTuple ) {
            getOrCreatePartitionedTuple(rightTuple).addLastRightTuple( rightTuple );
        }

        @Override
        public void removeRightTuple( TupleImpl rightTuple ) {
            if (rightTuple.getSink() != null) {
                getPartitionedTuple( rightTuple ).removeRightTuple( rightTuple );
            }
        }

        @Override
        public TupleImpl detachLeftTupleAfter(RuleBasePartitionId partitionId, ObjectTypeNodeId otnId) {
            return getOrCreatePartitionedTuple(partitionId.getId()).detachLeftTupleAfter(partitionId, otnId);
        }

        @Override
        public TupleImpl detachRightTupleAfter(RuleBasePartitionId partitionId, ObjectTypeNodeId otnId) {
            return getOrCreatePartitionedTuple(partitionId.getId()).detachRightTupleAfter(partitionId, otnId);
        }

        @Override
        public void reattachToLeft(TupleImpl tuple) {
            getOrCreatePartitionedTuple(tuple).reattachToLeft(tuple);
        }

        @Override
        public void reattachToRight(TupleImpl tuple) {
            getOrCreatePartitionedTuple(tuple).reattachToRight(tuple);
        }

        @Override
        public void clearLeftTuples() {
            for (int i = 0; i < partitionedTuples.length; i++) {
                clearLeftTuples(i);
            }
        }

        public void clearLeftTuples(int partition) {
            getPartitionedTuple(partition).clearLeftTuples();
        }

        @Override
        public void clearRightTuples() {
            for (int i = 0; i < partitionedTuples.length; i++) {
                clearRightTuples(i);
            }
        }

        public void clearRightTuples(int partition) {
            getPartitionedTuple(partition).clearRightTuples();
        }

        @Override
        public void forEachRightTuple( Consumer<TupleImpl> rightTupleConsumer ) {
            for (int i = 0; i < partitionedTuples.length; i++) {
                forEachRightTuple( i, rightTupleConsumer );
            }
        }

        public void forEachRightTuple( int partition, Consumer<TupleImpl> rightTupleConsumer ) {
            getPartitionedTuple(partition).forEachRightTuple( rightTupleConsumer );
        }

        @Override
        public void forEachLeftTuple( Consumer<TupleImpl> leftTupleConsumer ) {
            for (int i = 0; i < partitionedTuples.length; i++) {
                forEachLeftTuple( i, leftTupleConsumer );
            }
        }

        public void forEachLeftTuple( int partition, Consumer<TupleImpl> leftTupleConsumer ) {
            getPartitionedTuple(partition).forEachLeftTuple( leftTupleConsumer );
        }

        @Override
        public TupleImpl findFirstLeftTuple(Predicate<TupleImpl> lefttTuplePredicate ) {
            return Stream.of( partitionedTuples )
                         .map( t -> t.findFirstLeftTuple( lefttTuplePredicate ) )
                         .filter( Objects::nonNull )
                         .findFirst()
                         .orElse( null );
        }

        @Override
        public TupleImpl getFirstLeftTuple(int partition) {
            return ((SingleLinkedTuples) partitionedTuples[partition]).getFirstLeftTuple();
        }

        @Override
        public TupleImpl getFirstRightTuple(int partition) {
            return ((SingleLinkedTuples) partitionedTuples[partition]).getFirstRightTuple();
        }
    }

    public static class DummyLinkedTuples implements LinkedTuples {

        private static final DummyLinkedTuples INSTANCE = new DummyLinkedTuples();

        @Override
        public LinkedTuples clone() {
            return this;
        }

        @Override
        public LinkedTuples cloneEmpty() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasTuples() {
            return false;
        }

        @Override
        public void addFirstLeftTuple(TupleImpl leftTuple) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addLastLeftTuple(TupleImpl leftTuple) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeLeftTuple(TupleImpl leftTuple) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addFirstRightTuple(TupleImpl rightTuple) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addLastRightTuple(TupleImpl rightTuple) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeRightTuple(TupleImpl rightTuple) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clearLeftTuples() { }

        @Override
        public void clearRightTuples() { }

        @Override
        public void forEachRightTuple(Consumer<TupleImpl> rightTupleConsumer) { }

        @Override
        public void forEachLeftTuple(Consumer<TupleImpl> leftTupleConsumer) { }

        @Override
        public LeftTuple findFirstLeftTuple(Predicate<TupleImpl> leftTuplePredicate) {
            return null;
        }

        @Override
        public LeftTuple getFirstLeftTuple(int partition) {
            return null;
        }

        @Override
        public TupleImpl getFirstRightTuple(int partition) {
            return null;
        }
    }

    @Override
    public void forEachRightTuple(Consumer<TupleImpl> rightTupleConsumer) {
        linkedTuples.forEachRightTuple( rightTupleConsumer );
    }

    @Override
    public void forEachLeftTuple(Consumer<TupleImpl> leftTupleConsumer) {
        linkedTuples.forEachLeftTuple( leftTupleConsumer );
    }

    @Override
    public TupleImpl findFirstLeftTuple(Predicate<TupleImpl> lefttTuplePredicate ) {
        return linkedTuples.findFirstLeftTuple( lefttTuplePredicate );
    }

    @Override
    public TupleImpl getFirstLeftTuple() {
        if (linkedTuples instanceof SingleLinkedTuples) {
            return ( (SingleLinkedTuples) linkedTuples ).getFirstLeftTuple();
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public TupleImpl getFirstRightTuple() {
        if (linkedTuples instanceof SingleLinkedTuples) {
            return ( (SingleLinkedTuples) linkedTuples ).getFirstRightTuple();
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public LinkedTuples getLinkedTuples() {
        return linkedTuples;
    }

    @Override
    public LinkedTuples detachLinkedTuples() {
        LinkedTuples detached = linkedTuples;
        linkedTuples = new SingleLinkedTuples();
        return detached;
    }

    @Override
    public LinkedTuples detachLinkedTuplesForPartition(int i) {
        LinkedTuples detached = ( (CompositeLinkedTuples) linkedTuples ).getPartitionedTuple(i);
        ( (CompositeLinkedTuples) linkedTuples ).partitionedTuples[i] = null;
        return detached;
    }
}
