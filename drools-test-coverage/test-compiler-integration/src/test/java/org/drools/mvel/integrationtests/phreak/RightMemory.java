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
import org.drools.core.reteoo.BetaMemory;
import org.drools.core.reteoo.BetaNode;
import org.drools.core.reteoo.RightTuple;
import org.drools.core.reteoo.SegmentMemory;
import org.drools.core.reteoo.TupleImpl;
import org.drools.core.reteoo.TupleMemory;

import java.util.ArrayList;
import java.util.List;

public class RightMemory {

    private Scenario scenario;

    public RightMemory(Scenario scenario, Object... objects) {
        this.scenario = scenario;
        scenario.getRightMemory().addAll( getRightTuples( objects ) );
    }

    public List<TupleImpl> getRightTuples(Object... objects) {
        BetaNode node = scenario.getBetaNode();
        BetaMemory bm = scenario.getBm();
        TupleMemory rtm = bm.getRightTupleMemory();
        InternalWorkingMemory wm = scenario.getWorkingMemory();

        if ( objects == null ) {
            objects = new Object[0];
        }
        
        List<TupleImpl> rightTuples = new ArrayList<>();
        for ( Object object : objects ) {
            InternalFactHandle fh = (InternalFactHandle) wm.insert( object );
            TupleImpl expectedRightTuple = new RightTuple(fh, node.getRightInput() ); //node.createLeftTuple( fh, node, true );
            expectedRightTuple.setPropagationContext( new PhreakPropagationContext() );
            rightTuples.add( expectedRightTuple );
        }
        
        scenario.setTestRightMemory( true );
        
        return rightTuples;
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
