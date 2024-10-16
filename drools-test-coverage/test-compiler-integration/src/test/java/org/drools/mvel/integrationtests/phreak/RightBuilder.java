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
package org.drools.mvel.integrationtests.phreak;

import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.PhreakPropagationContext;
import org.drools.core.common.TupleSets;
import org.drools.core.reteoo.RightTuple;
import org.drools.core.reteoo.RightTupleSink;
import org.drools.core.reteoo.SegmentMemory;
import org.drools.core.reteoo.TupleImpl;

public class RightBuilder {
    private InternalWorkingMemory wm;
    private RightTupleSink        sink;
    private TupleSets             rightTuples;
    private Scenario              scenario;

    public RightBuilder(Scenario scenario) {
        this.wm = scenario.getWorkingMemory();
        this.scenario = scenario;
        this.sink = scenario.getBetaNode().getRightInput();
        this.rightTuples = scenario.getRightTuples();
    }

    public RightBuilder insert(Object... objects) {
        for (Object object : objects) {
            InternalFactHandle fh         = (InternalFactHandle) wm.insert(object);
            TupleImpl          rightTuple = new RightTuple(fh, sink );
            rightTuple.setPropagationContext( new PhreakPropagationContext() );
            rightTuples.addInsert( rightTuple );
        }
        return this;
    }

    public RightBuilder update(Object... objects) {
        for ( Object object : objects ) {
            InternalFactHandle fh = (InternalFactHandle) wm.insert( object );
            TupleImpl rightTuple = fh.getFirstRightTuple();
            rightTuple.setPropagationContext( new PhreakPropagationContext() );
            rightTuples.addUpdate( rightTuple );
        }
        return this;
    }

    public RightBuilder delete(Object... objects) {
        for ( Object object : objects ) {
            InternalFactHandle fh = (InternalFactHandle) wm.insert( object );
            TupleImpl rightTuple = fh.getFirstRightTuple();
            rightTuple.setPropagationContext( new PhreakPropagationContext() );
            rightTuples.addDelete( rightTuple );
        }
        return this;
    }

    public StagedBuilder result() {
        StagedBuilder stagedBuilder = new StagedBuilder( scenario, null );
        scenario.setExpectedResultBuilder( stagedBuilder );
        return stagedBuilder; 
    }    
    
    public StagedBuilder preStaged(SegmentMemory sm) {
        StagedBuilder stagedBuilder = new StagedBuilder( scenario, sm );
        scenario.addPreStagedBuilder( stagedBuilder );
        return stagedBuilder;        
    }    
    
    public StagedBuilder postStaged(SegmentMemory sm) {
        StagedBuilder stagedBuilder = new StagedBuilder( scenario, sm );
        scenario.addPostStagedBuilder( stagedBuilder );
        return stagedBuilder;        
    } 
    
    public Scenario run() {
        return this.scenario.run();
    }      
}
