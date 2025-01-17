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
package org.kie.dmn.feel.runtime.functions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.kie.dmn.api.feel.runtime.events.FEELEvent.Severity;
import org.kie.dmn.feel.runtime.FEELCollectionFunction;
import org.kie.dmn.feel.runtime.events.InvalidParametersEvent;

public class InsertBeforeFunction
        extends BaseFEELFunction implements FEELCollectionFunction {

    public static final InsertBeforeFunction INSTANCE = new InsertBeforeFunction();

    private InsertBeforeFunction() {
        super( "insert before" );
    }

    public FEELFnResult<List> invoke(@ParameterName( "list" ) List list, @ParameterName( "position" ) BigDecimal position, @ParameterName( "newItem" ) Object newItem) {
        if ( list == null ) { 
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "list", "cannot be null"));
        }
        if ( position == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "position", "cannot be null"));
        }
        if ( position.intValue() == 0 ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "position", "cannot be zero (parameter 'position' is 1-based)"));
        }
        if ( position.abs().intValue() > list.size() ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "position", "inconsistent with 'list' size"));
        }

        // spec requires us to return a new list
        final List<Object> result = new ArrayList<>( list );
        if( position.intValue() > 0 ) {
            result.add( position.intValue() - 1, newItem );
        } else {
            result.add( list.size() + position.intValue(), newItem );
        }
        return FEELFnResult.ofResult( result );
    }
}
