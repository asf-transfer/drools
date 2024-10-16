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

import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.SuperCacheFixer;

public class RightTuple extends TupleImpl {

    private LeftTuple            blocked;

    private RightTuple tempNextRightTuple;
    private LeftTuple  tempBlocked;

    private boolean              retracted;

    public RightTuple() { }
    
    public RightTuple(InternalFactHandle handle) {
        // This constructor is here for DSL testing
        this.handle =  handle;
    }

    public RightTuple(InternalFactHandle handle,
                      RightTupleSink sink) {
        setSink(sink);
        this.handle = handle;
        // add to end of RightTuples on handle
        handle.addLastRightTuple( this );
    }


    // This constructor is for SubNetworkTuples
    public RightTuple(InternalFactHandle factHandle,
                      Sink sink,
                      boolean leftTupleMemoryEnabled) {
        super(factHandle, sink, leftTupleMemoryEnabled);
    }

    public RightTuple(InternalFactHandle factHandle,
                      TupleImpl leftTuple,
                      Sink sink) {
        super(factHandle, leftTuple, sink);
    }

    public RightTuple(TupleImpl leftTuple,
                      Sink sink,
                      PropagationContext pctx,
                      boolean leftTupleMemoryEnabled) {
        super(leftTuple, sink, pctx, leftTupleMemoryEnabled);
    }

    public RightTuple(TupleImpl leftTuple,
                      TupleImpl rightTuple,
                      Sink sink) {
        super(leftTuple, rightTuple, sink);
    }

    public RightTuple(TupleImpl leftTuple,
                      TupleImpl rightTuple,
                      Sink sink,
                      boolean leftTupleMemoryEnabled) {
        super( leftTuple,
               rightTuple,
               null,
               null,
               sink,
               leftTupleMemoryEnabled );
    }

    public RightTuple(TupleImpl leftTuple,
                      TupleImpl rightTuple,
                      TupleImpl currentLeftChild,
                      TupleImpl currentRightChild,
                      Sink sink,
                      boolean leftTupleMemoryEnabled) {
        super(leftTuple, rightTuple, currentLeftChild, currentRightChild, sink, leftTupleMemoryEnabled);
    }

    public void reAdd() {
        getFactHandle().addLastRightTuple( this );
    }

    public void unlinkFromRightParent() {
        getFactHandle().removeRightTuple( this );
        setFactHandle( null );
        this.handlePrevious = null;
        this.handleNext = null;
        this.blocked = null;
        setPrevious( null );
        setNext( null );
        this.memory = null;
        this.firstChild = null;
        this.lastChild = null;
        setSink(null);
    }

    public LeftTuple getBlocked() {
        return this.blocked;
    }
    
    public void setBlocked(LeftTuple leftTuple) {
        this.blocked = leftTuple;
    }
    
    public void addBlocked(LeftTuple leftTuple) {
        if ( this.blocked != null && leftTuple != null ) {
            leftTuple.setBlockedNext( this.blocked );
            this.blocked.setBlockedPrevious( leftTuple );
        }
        this.blocked = leftTuple;
    }

    public void removeBlocked(LeftTuple leftTuple) {
        LeftTuple previous =  leftTuple.getBlockedPrevious();
        LeftTuple next =  leftTuple.getBlockedNext();
        if ( previous != null && next != null ) {
            //remove  from middle
            previous.setBlockedNext( next );
            next.setBlockedPrevious( previous );
        } else if ( next != null ) {
            //remove from first
            this.blocked = next;
            next.setBlockedPrevious( null );
        } else if ( previous != null ) {
            //remove from end
            previous.setBlockedNext( null );
        } else {
            this.blocked = null;
        }
        leftTuple.clearBlocker();
    }

    public LeftTuple getTempBlocked() {
        return tempBlocked;
    }

    public void setTempBlocked(LeftTuple tempBlocked) {
        this.tempBlocked = tempBlocked;
    }

    public RightTuple getTempNextRightTuple() {
        return tempNextRightTuple;
    }

    public void setTempNextRightTuple(RightTuple tempNextRightTuple) {
        this.tempNextRightTuple = tempNextRightTuple;
    }


    public void clearStaged() {
        super.clearStaged();
        this.tempNextRightTuple = null;
        this.tempBlocked = null;
    }

    @Override
    public ObjectTypeNodeId getInputOtnId() {
        return SuperCacheFixer.getRightInputOtnId(this);
    }

    public void retractTuple( PropagationContext context, ReteEvaluator reteEvaluator ) {
        if (!retracted) {
            SuperCacheFixer.getRightTupleSink(this).retractRightTuple(this, context, reteEvaluator);
            retracted = true;
        }
    }

    public void setExpired( ReteEvaluator reteEvaluator, PropagationContext pctx ) {
        super.setExpired();
        // events expired at firing time should have a chance to produce a join (DROOLS-1329)
        // but shouldn't participate to an accumulate (DROOLS-4393)
        if (this.getSink().getType() == NodeTypeEnums.AccumulateRightAdapterNode) {
            retractTuple( pctx, reteEvaluator );
        }
    }

    public InternalFactHandle getFactHandleForEvaluation() {
        return handle;
    }

    public boolean isLeftTuple() {
        return false;
    }
}
