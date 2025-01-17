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

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAmount;
import java.util.Arrays;
import java.util.List;

import org.kie.dmn.api.feel.runtime.events.FEELEvent.Severity;
import org.kie.dmn.feel.runtime.FEELDurationFunction;
import org.kie.dmn.feel.lang.types.impl.ComparablePeriod;
import org.kie.dmn.feel.runtime.events.InvalidParametersEvent;

public class DurationFunction
        extends BaseFEELFunction implements FEELDurationFunction {

    public static final DurationFunction INSTANCE = new DurationFunction();

    private DurationFunction() {
        super(FEELConversionFunctionNames.DURATION);
    }

    public FEELFnResult<TemporalAmount> invoke(@ParameterName( "from" ) String val) {
        if ( val == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "from", "cannot be null"));
        }

        try {
            // try to parse as days/hours/minute/seconds
            return FEELFnResult.ofResult( Duration.parse( val ) );
        } catch( DateTimeParseException e ) {
            // if it failed, try to parse as years/months
            try {
                return FEELFnResult.ofResult(ComparablePeriod.parse(val).normalized());
            } catch( DateTimeParseException e2 ) {
                // failed to parse, so return null according to the spec
                return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "from", "date-parsing exception", 
                                        new RuntimeException(new Throwable() { public final List<Throwable> causes = Arrays.asList( new Throwable[]{e, e2} );  } ))); 
            }
        }
        
    }

    /**
     * This is the identity function implementation
     *
     * @param val
     * @return
     */
    public FEELFnResult<TemporalAmount> invoke(@ParameterName( "from" ) TemporalAmount val) {
        if ( val == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "from", "cannot be null"));
        }
        return FEELFnResult.ofResult( val );
    }
}
