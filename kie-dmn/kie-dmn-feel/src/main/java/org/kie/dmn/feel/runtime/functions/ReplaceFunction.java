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
import org.kie.dmn.feel.runtime.FEELStringFunction;
import org.kie.dmn.feel.runtime.events.InvalidParametersEvent;
import org.kie.dmn.feel.util.XQueryImplUtil;

public class ReplaceFunction
        extends BaseFEELFunction implements FEELStringFunction {

    public static final ReplaceFunction INSTANCE = new ReplaceFunction();

    private ReplaceFunction() {
        super( "replace" );
    }

    public FEELFnResult<String> invoke(@ParameterName("input") String input, @ParameterName("pattern") String pattern,
                                       @ParameterName( "replacement" ) String replacement ) {
        return invoke(input, pattern, replacement, null);
    }

    public FEELFnResult<String> invoke(@ParameterName("input") String input, @ParameterName("pattern") String pattern,
                                       @ParameterName( "replacement" ) String replacement, @ParameterName("flags") String flags) {
        if ( input == null ) {
            return FEELFnResult.ofError( new InvalidParametersEvent( Severity.ERROR, "input", "cannot be null" ) );
        }
        if ( pattern == null ) {
            return FEELFnResult.ofError( new InvalidParametersEvent( Severity.ERROR, "pattern", "cannot be null" ) );
        }
        if ( replacement == null ) {
            return FEELFnResult.ofError( new InvalidParametersEvent( Severity.ERROR, "replacement", "cannot be null" ) );
        }

        try {
            return FEELFnResult.ofResult(XQueryImplUtil.executeReplaceFunction(input, pattern, replacement, flags));
        } catch (Exception e) {
            String errorMessage = String.format("Provided parameters lead to an error. Input: '%s', Pattern: '%s', Replacement: '%s', Flags: '%s'. ",
                    input, pattern, replacement, flags);
            if (e.getMessage() != null && !e.getMessage().isEmpty()) {
                errorMessage += e.getMessage();
            }
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, errorMessage, e));
        }
    }

}
