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
import org.kie.dmn.feel.util.XQueryImplUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MatchesFunction
        extends BaseFEELFunction implements FEELBooleanFunction {
    private static final Logger log = LoggerFactory.getLogger(MatchesFunction.class);
    public static final MatchesFunction INSTANCE = new MatchesFunction();

    private MatchesFunction() {
        super( "matches" );
    }

    public FEELFnResult<Boolean> invoke(@ParameterName("input") String input, @ParameterName("pattern") String pattern) {
        return invoke( input, pattern, null );
    }

    public FEELFnResult<Boolean> invoke(@ParameterName("input") String input, @ParameterName("pattern") String pattern, @ParameterName("flags") String flags) {
        log.debug("Input:  {} , Pattern: {}, Flags: {}", input, pattern, flags);

        if ( input == null ) {
            return FEELFnResult.ofError( new InvalidParametersEvent( Severity.ERROR, "input", "cannot be null" ) );
        }
        if ( pattern == null ) {
            return FEELFnResult.ofError( new InvalidParametersEvent( Severity.ERROR, "pattern", "cannot be null" ) );
        }

        try {
            return FEELFnResult.ofResult(XQueryImplUtil.executeMatchesFunction(input, pattern, flags));
        } catch (Exception e) {
            String errorMessage = String.format("Provided parameters lead to an error. Input: '%s', Pattern: '%s', Flags: '%s'. ", input, pattern, flags);
            if (e.getMessage() != null && !e.getMessage().isEmpty()) {
                errorMessage += e.getMessage();
            }
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, errorMessage, e));
        }
    }

    @Override
    public Object defaultValue() {
        return false;
    }
}
