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
package org.drools.serialization.protobuf.iterators;

import org.drools.core.common.BaseNode;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.MemoryFactory;
import org.drools.core.reteoo.AccumulateNode;
import org.drools.core.reteoo.AccumulateNode.AccumulateContext;
import org.drools.core.reteoo.AccumulateNode.AccumulateMemory;
import org.drools.core.reteoo.BetaMemory;
import org.drools.core.reteoo.BetaNode;
import org.drools.core.reteoo.EvalConditionNode;
import org.drools.core.reteoo.ExistsNode;
import org.drools.core.reteoo.FromNode;
import org.drools.core.reteoo.FromNode.FromMemory;
import org.drools.core.reteoo.JoinNode;
import org.drools.core.reteoo.LeftInputAdapterNode;
import org.drools.core.reteoo.LeftTupleSink;
import org.drools.core.reteoo.LeftTupleSource;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.core.reteoo.NotNode;
import org.drools.core.reteoo.ObjectSource;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.QueryElementNode;
import org.drools.core.reteoo.RightTuple;
import org.drools.core.reteoo.Tuple;
import org.drools.core.reteoo.TupleImpl;
import org.drools.core.util.FastIterator;
import org.drools.core.util.Iterator;

public class LeftTupleIterator
    implements
    Iterator<TupleImpl> {
    protected InternalWorkingMemory wm;

    protected LeftTupleSink         node;

    protected TupleImpl currentLeftTuple;

    private   java.util.Iterator<InternalFactHandle> otnIterator;

    LeftTupleIterator() {

    }

    private LeftTupleIterator(InternalWorkingMemory wm,
                              LeftTupleSink         node) {
        this.wm = wm;
        this.node = node;        
        setFirstLeftTupleForNode();
    }
    
    public static Iterator<TupleImpl> iterator(InternalWorkingMemory wm,
                                               LeftTupleSink         node) {
        return new LeftTupleIterator(wm, node);
    }

    public void setFirstLeftTupleForNode() {
        LeftTupleSource source = node.getLeftTupleSource();

        this.currentLeftTuple = getFirstLeftTuple( source,
                                                   node,
                                                   wm );
    }

    public TupleImpl getFirstLeftTuple(LeftTupleSource source,
                                       LeftTupleSink sink,
                                       InternalWorkingMemory wm) {
        if (source.getType() == NodeTypeEnums.AccumulateNode ) { // TODO WHAT ABOUTGROUPBY ? (mdp)
            AccumulateMemory accmem = (AccumulateMemory) wm.getNodeMemory( (MemoryFactory) source );
            BetaMemory memory = accmem.getBetaMemory();
            
            FastIterator localIt = memory.getLeftTupleMemory().fullFastIterator();
            Tuple leftTuple = BetaNode.getFirstTuple( memory.getLeftTupleMemory(), localIt );
            if( leftTuple != null ) {
                AccumulateContext accctx = (AccumulateContext) leftTuple.getContextObject();
                return (TupleImpl) accctx.getResultLeftTuple();
            }
            return null;
        }

        switch (source.getType()) {
            case NodeTypeEnums.JoinNode:
            case NodeTypeEnums.NotNode:
            case NodeTypeEnums.FromNode:
            case NodeTypeEnums.AccumulateNode: {
                BetaMemory memory;
                FastIterator localIt;
                switch (source.getType()) {
                    case NodeTypeEnums.FromNode:
                        memory = ((FromMemory) wm.getNodeMemory( (MemoryFactory) source )).getBetaMemory();
                        break;
                    case NodeTypeEnums.AccumulateNode:
                        memory = ((AccumulateMemory) wm.getNodeMemory( (MemoryFactory) source )).getBetaMemory();
                        break;
                    default:
                        memory = (BetaMemory) wm.getNodeMemory( (MemoryFactory) source );
                }
                localIt = memory.getLeftTupleMemory().fullFastIterator();
                Tuple leftTuple = BetaNode.getFirstTuple( memory.getLeftTupleMemory(), localIt );

                while ( leftTuple != null ) {
                    for (TupleImpl childleftTuple = leftTuple.getFirstChild(); childleftTuple != null; childleftTuple = childleftTuple.getHandleNext() ) {
                        if (childleftTuple.getSink() == sink ) {
                            return childleftTuple;
                        }
                    }
                    leftTuple = (TupleImpl) localIt.next(leftTuple );
                }
                break;
            }
            case NodeTypeEnums.ExistsNode: {
                BetaMemory memory = (BetaMemory) wm.getNodeMemory( (MemoryFactory) source );
                FastIterator localIt = memory.getRightTupleMemory().fullFastIterator();

                RightTuple rightTuple = (RightTuple) BetaNode.getFirstTuple(memory.getRightTupleMemory(), localIt);

                while ( rightTuple != null ) {
                    if ( rightTuple.getBlocked() != null ) {
                        for ( TupleImpl leftTuple = rightTuple.getBlocked(); leftTuple != null; leftTuple = leftTuple.getBlockedNext() ) {
                            for ( TupleImpl childleftTuple = leftTuple.getFirstChild(); childleftTuple != null; childleftTuple = childleftTuple.getHandleNext() ) {
                                if (childleftTuple.getSink() == sink ) {
                                    return childleftTuple;
                                }
                            }
                        }

                    }
                    rightTuple = (RightTuple) localIt.next(rightTuple);
                }
                break;
            }
            case NodeTypeEnums.LeftInputAdapterNode:
            case NodeTypeEnums.AlphaTerminalNode: {
                BaseNode os = ((LeftInputAdapterNode) source).getParentObjectSource();
                while ( !(os instanceof ObjectTypeNode) ) {
                    os = os.getParent();
                }

                ObjectTypeNode otn = (ObjectTypeNode) os;
                otnIterator = otn.getFactHandlesIterator(wm);

                while (otnIterator.hasNext()) {
                    InternalFactHandle handle = otnIterator.next();
                    TupleImpl leftTuple = handle.findFirstLeftTuple(lt -> lt.getSink() == sink);
                    if ( leftTuple != null ) {
                        return leftTuple;
                    }
                }
                break;
            }
            case NodeTypeEnums.EvalConditionNode:
            case NodeTypeEnums.QueryElementNode: {
                TupleImpl parentLeftTuple = getFirstLeftTuple(source.getLeftTupleSource(),
                                                              (LeftTupleSink) source,
                                                              wm );

                while ( parentLeftTuple != null ) {
                    for ( TupleImpl leftTuple = parentLeftTuple.getFirstChild(); leftTuple != null; leftTuple = leftTuple.getHandleNext() ) {
                        if (leftTuple.getSink() == sink ) {
                            return leftTuple;
                        }
                    }

                    parentLeftTuple = getNextLeftTuple(  source.getLeftTupleSource(),
                                                         (LeftTupleSink) source,
                                                         parentLeftTuple,
                                                         wm );
                }
                break;
            }

        }
        return null;
    }

    public TupleImpl getNextLeftTuple(LeftTupleSource source,
                                      LeftTupleSink sink,
                                      TupleImpl leftTuple,
                                      InternalWorkingMemory wm) {

        if ( otnIterator != null ) {
            TupleImpl leftParent = leftTuple.getLeftParent();
            
            while ( leftTuple != null ) {
                leftTuple = leftTuple.getHandleNext();
                
                for ( ; leftTuple != null; leftTuple = leftTuple.getHandleNext() ) {
                    // Iterate to find the next left tuple for this sink, skip tuples for other sinks due to sharing split
                    if (leftTuple.getSink() == sink ) {
                        return leftTuple;
                    }
                }
            }
                                    
            // We have a parent LeftTuple so try there next
            if ( leftParent != null ) {
                // we know it has to be evalNode query element node
                while ( leftParent != null ) {
                    leftParent = getNextLeftTuple( source.getLeftTupleSource(),
                                                   (LeftTupleSink) source,
                                                   leftParent,
                                                   wm );

                    if ( leftParent != null ) {
                        for ( leftTuple = leftParent.getFirstChild(); leftTuple != null; leftTuple = leftTuple.getHandleNext() ) {
                            if (leftTuple.getSink() == sink ) {
                                return leftTuple;
                            }
                        }
                    }
                }
                return null;
            }

            // We have exhausted the current FactHandle, now try the next
            while ( otnIterator.hasNext() ) {
                InternalFactHandle handle = otnIterator.next();
                leftTuple = handle.findFirstLeftTuple( lt -> lt.getSink() == sink);
                if ( leftTuple != null ) {
                    return leftTuple;
                }
            }
            // We've exhausted this OTN so set the iterator to null
            otnIterator = null;

        } else if ( source instanceof AccumulateNode ) {
            // when using phreak, accumulate result tuples will not link to leftParent, but to parent instead 
            BetaMemory memory = ((AccumulateMemory) wm.getNodeMemory( (MemoryFactory) source )).getBetaMemory();
            FastIterator localIt = memory.getLeftTupleMemory().fullFastIterator( leftTuple.getParent() );

            TupleImpl childLeftTuple = leftTuple;
            leftTuple = childLeftTuple.getParent();

            while ( leftTuple != null ) {
                if ( childLeftTuple == null ) {
                    childLeftTuple = leftTuple.getFirstChild();
                } else {
                    childLeftTuple = childLeftTuple.getHandleNext();
                }
                for ( ; childLeftTuple != null; childLeftTuple = childLeftTuple.getHandleNext() ) {
                    if (childLeftTuple.getSink() == sink ) {
                        return childLeftTuple;
                    }
                }
                leftTuple = (TupleImpl) localIt.next(leftTuple );
            }

        } else if ( source instanceof JoinNode || source instanceof NotNode|| source instanceof FromNode || source instanceof AccumulateNode ) {
            BetaMemory memory;
            FastIterator localIt;
            if ( source instanceof FromNode ) {
                memory = ((FromMemory) wm.getNodeMemory( (MemoryFactory) source )).getBetaMemory();
            } else if ( source instanceof AccumulateNode ) {
                memory = ((AccumulateMemory) wm.getNodeMemory( (MemoryFactory) source )).getBetaMemory();
            } else {
                memory = (BetaMemory) wm.getNodeMemory( (MemoryFactory) source );
            }
            
            localIt = memory.getLeftTupleMemory().fullFastIterator( leftTuple.getLeftParent() );

            TupleImpl childLeftTuple = leftTuple;
            leftTuple = childLeftTuple.getLeftParent();

            while ( leftTuple != null ) {
                if ( childLeftTuple == null ) {
                    childLeftTuple = leftTuple.getFirstChild();
                } else {
                    childLeftTuple = childLeftTuple.getHandleNext();
                }
                for ( ; childLeftTuple != null; childLeftTuple = childLeftTuple.getHandleNext() ) {
                    if (childLeftTuple.getSink() == sink ) {
                        return childLeftTuple;
                    }
                }
                leftTuple = (TupleImpl) localIt.next(leftTuple );
            }
        }
        if ( source instanceof ExistsNode ) {
            BetaMemory memory = (BetaMemory) wm.getNodeMemory( (MemoryFactory) source );
            if (leftTuple != null) {
                RightTuple   rightTuple = (RightTuple) leftTuple.getLeftParent().getBlocker();
                FastIterator localIt    = memory.getRightTupleMemory().fullFastIterator( rightTuple );

                for ( TupleImpl childleftTuple = leftTuple.getHandleNext(); childleftTuple != null; childleftTuple = childleftTuple.getHandleNext() ) {
                    if (childleftTuple.getSink() == sink ) {
                        return childleftTuple;
                    }
                }

                leftTuple = leftTuple.getLeftParent();

                // now move onto next RightTuple
                while ( rightTuple != null ) {
                    if ( rightTuple.getBlocked() != null ) {
                        if ( leftTuple != null ) {
                            leftTuple = leftTuple.getBlockedNext();
                        } else {
                            leftTuple = rightTuple.getBlocked();
                        }
                        for ( ; leftTuple != null; leftTuple = leftTuple.getBlockedNext() ) {
                            for ( TupleImpl childleftTuple = leftTuple.getFirstChild(); childleftTuple != null; childleftTuple = childleftTuple.getHandleNext() ) {
                                if (childleftTuple.getSink() == sink ) {
                                    return childleftTuple;
                                }
                            }
                        }

                    }
                    rightTuple = (RightTuple) localIt.next(rightTuple);
                }
            }
        } else if ( source instanceof EvalConditionNode || source instanceof QueryElementNode ) {
            TupleImpl childLeftTuple = leftTuple;
            if ( leftTuple != null ) {
                leftTuple = leftTuple.getLeftParent();

                while ( leftTuple != null ) {
                    if ( childLeftTuple != null ) {
                        childLeftTuple = childLeftTuple.getHandleNext();
                    } else {
                        childLeftTuple = leftTuple.getFirstChild();
                    }
                    for ( ; childLeftTuple != null; childLeftTuple = childLeftTuple.getHandleNext() ) {
                        if (childLeftTuple.getSink() == sink ) {
                            return childLeftTuple;
                        }
                    }
                    leftTuple = getNextLeftTuple( source.getLeftTupleSource(),
                                                  (LeftTupleSink) source,
                                                  leftTuple,
                                                  wm );
                }
            }
        }
        return null;
    }

    public void setNextLeftTuple() {
        LeftTupleSource source = node.getLeftTupleSource();
        currentLeftTuple = getNextLeftTuple( source,
                                             node,
                                             currentLeftTuple,
                                             wm );
    }

    public TupleImpl next() {
        TupleImpl leftTuple = null;
        if ( this.currentLeftTuple != null ) {
            leftTuple = currentLeftTuple;
            setNextLeftTuple();
        }

        return leftTuple;
    }
}
