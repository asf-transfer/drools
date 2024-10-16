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

import java.util.List;

import org.drools.base.common.NetworkNode;
import org.drools.base.definitions.rule.impl.QueryImpl;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.QueryArgument;
import org.drools.base.rule.QueryElement;
import org.drools.core.RuleBaseConfiguration;
import org.drools.core.base.DroolsQueryImpl;
import org.drools.core.base.InternalViewChangedEventListener;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.Memory;
import org.drools.core.common.MemoryFactory;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.QueryElementFactHandle;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.TupleSets;
import org.drools.core.common.TupleSetsImpl;
import org.drools.core.common.UpdateContext;
import org.drools.core.phreak.BuildtimeSegmentUtilities;
import org.drools.core.phreak.StackEntry;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.core.rule.consequence.InternalMatch;
import org.drools.core.util.AbstractLinkedListNode;
import org.kie.api.runtime.rule.FactHandle;

public class QueryElementNode extends LeftTupleSource implements LeftTupleSinkNode, MemoryFactory<QueryElementNode.QueryElementNodeMemory> {

    private LeftTupleSinkNode previousTupleSinkNode;
    private LeftTupleSinkNode nextTupleSinkNode;

    protected QueryElement    queryElement;

    private boolean           tupleMemoryEnabled;

    protected boolean         openQuery;

    private   boolean         dataDriven;

    private QueryArgument[]   argsTemplate;

    public QueryElementNode() {
        // for serialization
    }

    public QueryElementNode(final int id,
                            final LeftTupleSource tupleSource,
                            final QueryElement queryElement,
                            final boolean tupleMemoryEnabled,
                            final boolean openQuery,
                            final BuildContext context) {
        super(id, context);
        setLeftTupleSource(tupleSource);
        this.setObjectCount(leftInput.getObjectCount() + 1); // 'query' node increase the object count
        this.queryElement = queryElement;
        this.tupleMemoryEnabled = tupleMemoryEnabled;
        this.openQuery = openQuery;
        this.dataDriven = context != null && context.getRule().isDataDriven();
        initMasks( context );
        this.argsTemplate = initArgsTemplate( context );

        hashcode = calculateHashCode();
    }

    private QueryArgument[] initArgsTemplate(BuildContext context) {
        ClassLoader classLoader = context.getRuleBase().getRootClassLoader();
        QueryArgument[] originalArgs = this.queryElement.getArguments();
        QueryArgument[] args = new QueryArgument[originalArgs.length];
        for (int i = 0; i < originalArgs.length; i++) {
            args[i] = originalArgs[i] == null ? QueryArgument.NULL : originalArgs[i].normalize( classLoader );
        }
        return args;
    }

    @Override
    public void networkUpdated(UpdateContext updateContext) {
        this.leftInput.networkUpdated(updateContext);
    }

    @Override
    public int getType() {
        return NodeTypeEnums.QueryElementNode;
    }

    @Override
    public boolean isLeftTupleMemoryEnabled() {
        return false;
    }

    public QueryElement getQueryElement() {
        return queryElement;
    }

    public boolean isOpenQuery() {
        return openQuery;
    }

    @SuppressWarnings("unchecked")
    public InternalFactHandle createFactHandle(final PropagationContext context,
                                               final ReteEvaluator reteEvaluator,
                                               final TupleImpl leftTuple ) {
        InternalFactHandle handle = null;
        if( context.getReaderContext() != null ) {
            handle = context.getReaderContext().createQueryHandle( leftTuple, reteEvaluator, getId() );
        }

        if (handle == null) {
            handle = reteEvaluator.createFactHandle( null, null, null );
        }
        return handle;
    }
    
    public DroolsQueryImpl createDroolsQuery(TupleImpl leftTuple,
                                             InternalFactHandle handle,
                                             StackEntry stackEntry,
                                             final List<PathMemory> pmems,
                                             QueryElementNodeMemory qmem,
                                             LeftTupleSink sink,
                                             ReteEvaluator reteEvaluator) {
        UnificationNodeViewChangedEventListener collector = createCollector( leftTuple, queryElement.getVariableIndexes(), this.tupleMemoryEnabled );
        
        boolean executeAsOpenQuery = openQuery;
        if ( executeAsOpenQuery ) {
            // There is no point in doing an open query if the caller is a non-open query.
            Object object = leftTuple.get( 0 ).getObject();
            if (object instanceof DroolsQueryImpl && !((DroolsQueryImpl) object).isOpen() ) {
                executeAsOpenQuery = false;
            }          
        }

        DroolsQueryImpl queryObject = new DroolsQueryImpl(this.queryElement.getQueryName(),
                                                          getActualArguments( leftTuple, reteEvaluator ),
                                                          collector,
                                                          executeAsOpenQuery,
                                                          stackEntry,
                                                          pmems,
                                                   qmem != null ? qmem.getResultLeftTuples() : null,
                                                          qmem,
                                                          sink);
        collector.setFactHandle( handle );
        handle.setObject( queryObject );
        leftTuple.setContextObject( handle ); // so it can be retracted later and destroyed
        return queryObject;
    }

    public Object[] getActualArguments(TupleImpl leftTuple, ReteEvaluator reteEvaluator ) {
        Object[] args = new Object[argsTemplate.length]; // the actual args, to be created from the  template
        for (int i = 0; i < argsTemplate.length; i++) {
            args[i] = argsTemplate[i].getValue( reteEvaluator, leftTuple );
        }
        return args;
    }

    protected UnificationNodeViewChangedEventListener createCollector(TupleImpl leftTuple, int[] varIndexes, boolean tupleMemoryEnabled ) {
        return new UnificationNodeViewChangedEventListener( leftTuple,
                                                            varIndexes,
                                                            this,
                                                            tupleMemoryEnabled );
    }

    /**
     * Returns the next node
     * @return
     *      The next TupleSinkNode
     */
    @Override
    public LeftTupleSinkNode getNextLeftTupleSinkNode() {
        return this.nextTupleSinkNode;
    }

    /**
     * Sets the next node
     * @param next
     *      The next TupleSinkNode
     */
    @Override
    public void setNextLeftTupleSinkNode(final LeftTupleSinkNode next) {
        this.nextTupleSinkNode = next;
    }

    /**
     * Returns the previous node
     * @return
     *      The previous TupleSinkNode
     */
    @Override
    public LeftTupleSinkNode getPreviousLeftTupleSinkNode() {
        return this.previousTupleSinkNode;
    }

    /**
     * Sets the previous node
     * @param previous
     *      The previous TupleSinkNode
     */
    @Override
    public void setPreviousLeftTupleSinkNode(final LeftTupleSinkNode previous) {
        this.previousTupleSinkNode = previous;
    }

    public static class UnificationNodeViewChangedEventListener
        implements
        InternalViewChangedEventListener {

        protected TupleImpl leftTuple;

        protected QueryElementNode   node;

        protected InternalFactHandle factHandle;

        protected int[]              variables;

        protected boolean            tupleMemoryEnabled;

        public UnificationNodeViewChangedEventListener(TupleImpl leftTuple,
                                                       int[] variables,
                                                       QueryElementNode node,
                                                       boolean tupleMemoryEnabled) {
            this.leftTuple = leftTuple;
            this.variables = variables;
            this.node = node;
            this.tupleMemoryEnabled = tupleMemoryEnabled;
        }

        public InternalFactHandle getFactHandle() {
            return factHandle;
        }

        public void setFactHandle(InternalFactHandle factHandle) {
            this.factHandle = factHandle;
        }

        public void setVariables(int[] variables) {
            this.variables = variables;
        }

        @Override
        public void rowAdded(RuleImpl rule, TupleImpl resultLeftTuple, ReteEvaluator reteEvaluator) {

            QueryTerminalNode queryTerminalNode = (QueryTerminalNode) resultLeftTuple.getSink();
            QueryImpl query = queryTerminalNode.getQuery();
            Declaration[] decls = queryTerminalNode.getRequiredDeclarations();
            DroolsQueryImpl dquery = (DroolsQueryImpl) this.factHandle.getObject();
            Object[] objects = new Object[ determineResultSize( query, dquery ) ];

            Declaration decl;
            for (int variable : this.variables) {
                decl = decls[variable];
                objects[variable] = decl.getValue(reteEvaluator, resultLeftTuple);
            }

            QueryElementFactHandle resultHandle = createQueryResultHandle(leftTuple.getPropagationContext(),
                                                                          reteEvaluator,
                                                                          objects);

            TupleImpl rightTuple = createResultRightTuple(resultHandle, (LeftTuple) resultLeftTuple, dquery.isOpen());

            if ( query.processAbduction((InternalMatch) resultLeftTuple, dquery, objects, reteEvaluator) ) {
                LeftTupleSink sink = dquery.getLeftTupleSink();
                TupleImpl childLeftTuple = TupleFactory.createLeftTuple(this.leftTuple, rightTuple, sink );
                boolean stagedInsertWasEmpty = dquery.getResultLeftTupleSets().addInsert(childLeftTuple);
                if ( stagedInsertWasEmpty ) {
                    dquery.getQueryNodeMemory().setNodeDirtyWithoutNotify();
                }
            }
        }

        private int determineResultSize( QueryImpl query, DroolsQueryImpl dquery) {
            int size = dquery.getElements().length;
            if (query.isReturnBound()) {
                size++;
            }
            return size;
        }

        protected RightTuple createResultRightTuple(QueryElementFactHandle resultHandle, LeftTuple resultLeftTuple, boolean open) {
            RightTuple rightTuple = new RightTuple(resultHandle );
            if ( open ) {
                rightTuple.setBlocked( resultLeftTuple );
                resultLeftTuple.setContextObject( rightTuple );

            }
            rightTuple.setPropagationContext( resultLeftTuple.getPropagationContext() );
            return rightTuple;
        }

        @SuppressWarnings("unchecked")
        protected QueryElementFactHandle createQueryResultHandle(PropagationContext context, ReteEvaluator reteEvaluator, Object[] objects) {
            QueryElementFactHandle handle = null;
            if (context.getReaderContext() != null ) {
                handle = context.getReaderContext().createQueryResultHandle( leftTuple, objects, node.getId() );
            }

            if (handle == null) {
                handle = new QueryElementFactHandle( objects,
                        reteEvaluator.getFactHandleFactory().getNextId(),
                        reteEvaluator.getFactHandleFactory().getNextRecency() );
            }

            return handle;
        }

        @Override
        public void rowRemoved(final RuleImpl rule,
                               final TupleImpl resultLeftTuple,
                               final ReteEvaluator reteEvaluator) {
            RightTuple rightTuple = (RightTuple) resultLeftTuple.getContextObject();
            rightTuple.setBlocked( null );
            resultLeftTuple.setContextObject( null );

            DroolsQueryImpl query = (DroolsQueryImpl) this.factHandle.getObject();
            TupleSets leftTuples = query.getResultLeftTupleSets();
            TupleImpl childLeftTuple = rightTuple.getFirstChild();

            if (childLeftTuple.isStagedOnRight()) {
                ( (SubnetworkTuple) childLeftTuple ).moveStagingFromRightToLeft();
            } else {
                short stagedTypeForQueries = childLeftTuple.getStagedTypeForQueries();// handle clash with already staged entries
                if (stagedTypeForQueries == LeftTuple.INSERT) {
                    leftTuples.removeInsert(childLeftTuple);
                    return;
                } else if (stagedTypeForQueries == LeftTuple.UPDATE) {
                    leftTuples.removeUpdate(childLeftTuple);
                }
            }

            leftTuples.addDelete(childLeftTuple);
        }

        @Override
        public void rowUpdated(final RuleImpl rule,
                               final TupleImpl resultLeftTuple,
                               final ReteEvaluator reteEvaluator) {
            RightTuple rightTuple = (RightTuple) resultLeftTuple.getContextObject();
            if ( rightTuple.getMemory() != null ) {
                // Already sheduled as an insert
                return;
            }

            rightTuple.setBlocked( null );
            resultLeftTuple.setContextObject( null );

            // We need to recopy everything back again, as we don't know what has or hasn't changed
            QueryTerminalNode queryTerminalNode = (QueryTerminalNode) resultLeftTuple.getSink();
            Declaration[] decls = queryTerminalNode.getRequiredDeclarations();
            FactHandle rootHandle = resultLeftTuple.get(0);
            DroolsQueryImpl dquery = (DroolsQueryImpl) rootHandle.getObject();

            Object[] objects = new Object[dquery.getElements().length];

            Declaration decl;
            for (int variable : this.variables) {
                decl = decls[variable];
                objects[variable] = decl.getValue(reteEvaluator, resultLeftTuple);
            }

            QueryElementFactHandle handle = (QueryElementFactHandle) rightTuple.getFactHandle();

            handle.setRecency(reteEvaluator.getFactHandleFactory().getNextRecency());
            handle.setObject( objects );

            if ( dquery.isOpen() ) {
                rightTuple.setBlocked( (LeftTuple) resultLeftTuple );
                resultLeftTuple.setContextObject( rightTuple );
            }

            TupleSets leftTuples = dquery.getResultLeftTupleSets();
            TupleImpl childLeftTuple = rightTuple.getFirstChild();
            short stagedTypeForQueries = childLeftTuple.getStagedTypeForQueries();// handle clash with already staged entries
            if (stagedTypeForQueries == LeftTuple.INSERT) {
                leftTuples.removeInsert(childLeftTuple);
            } else if (stagedTypeForQueries == LeftTuple.UPDATE) {
                leftTuples.removeUpdate(childLeftTuple);
            }
            leftTuples.addUpdate( childLeftTuple  );
        }

        @Override
        public List<?> getResults() {
            throw new UnsupportedOperationException( getClass().getCanonicalName() + " does not support the getResults() method." );
        }

        public TupleImpl getLeftTuple() {
            return leftTuple;
        }

    }

    private int calculateHashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + (openQuery ? 1231 : 1237);
        result = prime * result + ((queryElement == null) ? 0 : queryElement.hashCode());
        result = prime * result + ((leftInput == null) ? 0 : leftInput.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (((NetworkNode)object).getType() != NodeTypeEnums.QueryElementNode || this.hashCode() != object.hashCode() ) {
            return false;
        }

        QueryElementNode other = (QueryElementNode) object;
        if ( this.leftInput.getId() != other.leftInput.getId() ) {
            return false;
        }
        if ( openQuery != other.openQuery ) {
            return false;
        }
        if ( !openQuery && dataDriven != other.dataDriven ) {
            return false;
        }
        if ( queryElement == null ) {
            if ( other.queryElement != null ) {
                return false;
            }
        } else if ( !queryElement.equals( other.queryElement ) ) {
            return false;
        }
        return true;
    }

    @Override
    public QueryElementNodeMemory createMemory(RuleBaseConfiguration config, ReteEvaluator reteEvaluator) {
        return new QueryElementNodeMemory(this);
    }
    
    public static class QueryElementNodeMemory extends AbstractLinkedListNode<Memory> implements SegmentNodeMemory {
        private QueryElementNode node;

        private SegmentMemory smem;

        private SegmentMemory querySegmentMemory;

        private TupleSets resultLeftTuples;

        private long nodePosMaskBit;

        public QueryElementNodeMemory(QueryElementNode node) {
            this.node = node;

            // if there is only one sink there is no split and then no smem staging and no normalization
            // otherwise it uses special tuplset with alternative linking fields (rightParentPrev/Next)
            this.resultLeftTuples = node.getSinkPropagator().size() > 1 ?
                                    new QueryTupleSets() : new TupleSetsImpl();
        }

        public QueryElementNode getNode() {
            return this.node;
        }

        @Override
        public int getNodeType() {
            return NodeTypeEnums.QueryElementNode;
        }

        @Override
        public void setSegmentMemory(SegmentMemory smem) {
            this.smem = smem;
        }

        @Override
        public SegmentMemory getSegmentMemory() {
            return smem;
        }

        public SegmentMemory getQuerySegmentMemory() {
            return querySegmentMemory;
        }

        public void setQuerySegmentMemory(SegmentMemory querySegmentMemory) {
            this.querySegmentMemory = querySegmentMemory;
        }

        public TupleSets getResultLeftTuples() {
            return resultLeftTuples;
        }

        public void correctMemoryOnSinksChanged(TerminalNode removingTn) {
            if (resultLeftTuples instanceof QueryTupleSets ) {
                if (!BuildtimeSegmentUtilities.isTipNode(node, removingTn)) {
                    // a sink has been removed and now there is no longer a split
                    TupleSetsImpl newTupleSets = new TupleSetsImpl();
                    this.resultLeftTuples.addTo( newTupleSets );
                    this.resultLeftTuples = newTupleSets;
                }
            } else {
                if (BuildtimeSegmentUtilities.isTipNode(node, removingTn)) {
                    // a sink has been added and now there is a split
                    TupleSetsImpl newTupleSets = new QueryTupleSets();
                    this.resultLeftTuples.addTo( newTupleSets );
                    this.resultLeftTuples = newTupleSets;
                }
            }
        }

        @Override
        public long getNodePosMaskBit() {
            return nodePosMaskBit;
        }

        @Override
        public void setNodePosMaskBit(long segmentPos) {
            this.nodePosMaskBit = segmentPos;
        }

        @Override
        public void setNodeDirtyWithoutNotify() {
            smem.updateDirtyNodeMask( nodePosMaskBit );
        }

        @Override
        public void setNodeCleanWithoutNotify() {
            smem.updateCleanNodeMask( nodePosMaskBit );
        }

        @Override
        public void reset() {
            resultLeftTuples.resetAll();
        }

        public static class QueryTupleSets extends TupleSetsImpl {
            @Override
            protected TupleImpl getPreviousTuple(TupleImpl tuple ) {
                return tuple.getRightParentPrevious();
            }

            @Override
            protected void setPreviousTuple(TupleImpl tuple, TupleImpl stagedPrevious ) {
                tuple.setRightParentPrevious( stagedPrevious );
            }

            @Override
            protected TupleImpl getNextTuple(TupleImpl tuple ) {
                return tuple.getRightParentNext();
            }

            @Override
            protected void setNextTuple(TupleImpl tuple, TupleImpl stagedNext ) {
                tuple.setRightParentNext( stagedNext );
            }

            @Override
            protected void setStagedType(TupleImpl tuple, short type ) {
                tuple.setStagedTypeForQueries( type );
            }

            @Override
            protected short getStagedType( TupleImpl tuple ) {
                return tuple.getStagedTypeForQueries();
            }

            @Override
            public void addTo(TupleSets tupleSets) {
                addAllInsertsTo( tupleSets );
                addAllDeletesTo( tupleSets );
                addAllUpdatesTo( tupleSets );
            }

            private void addAllInsertsTo( TupleSets tupleSets ) {
                TupleImpl leftTuple = getInsertFirst();
                while (leftTuple != null) {
                    TupleImpl next = getNextTuple(leftTuple );
                    clear( leftTuple );
                    tupleSets.addInsert( leftTuple );
                    leftTuple = next;
                }
                setInsertFirst( null );
            }

            private void addAllUpdatesTo( TupleSets tupleSets ) {
                TupleImpl leftTuple = getUpdateFirst();
                while (leftTuple != null) {
                    TupleImpl next = getNextTuple(leftTuple );
                    clear( leftTuple );
                    tupleSets.addUpdate( leftTuple );
                    leftTuple = next;
                }
                setUpdateFirst( null );
            }

            private void addAllDeletesTo( TupleSets tupleSets ) {
                TupleImpl leftTuple = getDeleteFirst();
                while (leftTuple != null) {
                    TupleImpl next = getNextTuple(leftTuple );
                    clear( leftTuple );
                    tupleSets.addDelete( leftTuple );
                    leftTuple = next;
                }
                setDeleteFirst( null );
            }

            private void clear( TupleImpl leftTuple ) {
                setStagedType( leftTuple, Tuple.NONE );
                setPreviousTuple( leftTuple, null );
                setNextTuple( leftTuple, null );
            }
        }
    }

    @Override
    public ObjectTypeNode getObjectTypeNode() {
        return leftInput.getObjectTypeNode();
    }

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + "(" + this.id + ", " + queryElement.getQueryName() + ")]";
    }

    @Override
    public void doAttach(BuildContext context ) {
        super.doAttach(context);
        this.leftInput.addTupleSink( this, context );
    }

    @Override
    protected boolean doRemove(RuleRemovalContext context,
                               ReteooBuilder builder) {
        if (!isInUse()) {
            getLeftTupleSource().removeTupleSink(this);
            return true;
        }
        return false;
    }
}
