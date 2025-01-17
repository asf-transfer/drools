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
import org.kie.dmn.feel.runtime.FEELFunction;
import org.kie.dmn.feel.runtime.FEELStringFunction;
import org.kie.dmn.feel.runtime.events.InvalidParametersEvent;
import org.kie.dmn.feel.util.TypeUtil;

public class StringFunction
        extends BaseFEELFunction implements FEELStringFunction {

    public static final StringFunction INSTANCE = new StringFunction();

    private StringFunction() {
        super(FEELConversionFunctionNames.STRING);
    }

    public FEELFnResult<String> invoke(@ParameterName("from") Object val) {
        if ( val == null ) {
            return FEELFnResult.ofResult( null );
        } else {
            return FEELFnResult.ofResult( TypeUtil.formatValue(val, false) );
        }
    }

    public FEELFnResult<String> invoke(@ParameterName( "mask" ) String mask, @ParameterName("p") Object[] params) {
        if ( mask == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "mask", "cannot be null"));
        } else {
            return FEELFnResult.ofResult( String.format( mask, params ) );
        }
    }

}
