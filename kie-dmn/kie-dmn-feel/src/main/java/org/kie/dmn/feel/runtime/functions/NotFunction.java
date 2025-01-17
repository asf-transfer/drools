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

import org.kie.dmn.api.feel.runtime.events.FEELEvent.Severity;
import org.kie.dmn.feel.runtime.FEELBooleanFunction;
import org.kie.dmn.feel.runtime.events.InvalidParametersEvent;

/**
 * The not() function is a special case because
 * it doubles both as a function and as a unary
 * test.
 */
public class NotFunction
        extends BaseFEELFunction implements FEELBooleanFunction {

    public static final NotFunction INSTANCE = new NotFunction();

    private NotFunction() {
        super( "not" );
    }

    public FEELFnResult<Boolean> invoke(@ParameterName("negand") Object negand) {
        if ( negand != null && !(negand instanceof Boolean) ) {
            return FEELFnResult.ofError( new InvalidParametersEvent( Severity.ERROR, "negand", "must be a boolean value" ) );
        }
        return FEELFnResult.ofResult( negand == null ? null : !((Boolean) negand) );
    }

    @Override
    public Object defaultValue() {
        return false;
    }

}
