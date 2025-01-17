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

import java.util.Arrays;
import java.util.List;

import org.kie.dmn.api.feel.runtime.events.FEELEvent.Severity;
import org.kie.dmn.feel.runtime.FEELBooleanFunction;
import org.kie.dmn.feel.runtime.events.InvalidParametersEvent;

public class AllFunction
        extends BaseFEELFunction implements FEELBooleanFunction {

    public static final AllFunction INSTANCE = new AllFunction();

    private AllFunction() {
        super( "all" );
    }

    public FEELFnResult<Boolean> invoke(@ParameterName( "list" ) List list) {
        if ( list == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "list", "cannot be null"));
        }
        boolean result = true;
        boolean containsNull = false;
        // Spec. definition: return false if any item is false, else true if all items are true, else null
        for ( final Object element : list ) {
            if (element != null && !(element instanceof Boolean)) {
                return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "an element in the list is not a Boolean"));
            } else {
                if (element != null) {
                    result &= (Boolean) element;
                } else if (!containsNull) {
                    containsNull = true;
                }
            }
        }
        if (containsNull && result) {
            return FEELFnResult.ofResult( null );
        } else {
            return FEELFnResult.ofResult( result );
        }
    }

    public FEELFnResult<Boolean> invoke(@ParameterName( "b" ) Object[] list) {
        if ( list == null ) { 
            // Arrays.asList does not accept null as parameter
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "b", "cannot be null"));
        }
        return invoke( Arrays.asList( list ) );
    }
}
