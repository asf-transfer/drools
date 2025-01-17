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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.kie.dmn.api.feel.runtime.events.FEELEvent.Severity;
import org.kie.dmn.feel.runtime.FEELCollectionFunction;
import org.kie.dmn.feel.runtime.events.InvalidParametersEvent;

public class FlattenFunction
        extends BaseFEELFunction implements FEELCollectionFunction {

    public static final FlattenFunction INSTANCE = new FlattenFunction();

    private FlattenFunction() {
        super( "flatten" );
    }

    public FEELFnResult<List> invoke(@ParameterName( "list" ) Object list) {
        if ( list == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "list", "cannot be null"));
        }
        // spec requires us to return a new list
        final List<Object> result = new ArrayList<>();
        flattenList( list, result );
        return FEELFnResult.ofResult( result );
    }

    private void flattenList(Object list, List<Object> result) {
        if( list instanceof Collection ) {
            for( Object element : ((Collection)list) ) {
                if( element instanceof Collection ) {
                    flattenList( element, result );
                } else {
                    result.add( element );
                }
            }
        } else {
            result.add( list );
        }
    }

}
