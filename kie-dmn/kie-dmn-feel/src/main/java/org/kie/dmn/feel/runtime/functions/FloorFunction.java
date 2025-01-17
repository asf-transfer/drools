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
import java.math.RoundingMode;

import org.kie.dmn.api.feel.runtime.events.FEELEvent.Severity;
import org.kie.dmn.feel.runtime.FEELNumberFunction;
import org.kie.dmn.feel.runtime.events.InvalidParametersEvent;

public class FloorFunction
        extends BaseFEELFunction implements FEELNumberFunction {

    public static final FloorFunction INSTANCE = new FloorFunction();

    private FloorFunction() {
        super( "floor" );
    }

    public FEELFnResult<BigDecimal> invoke(@ParameterName( "n" ) BigDecimal n) {
        return invoke(n, BigDecimal.ZERO);
    }

    public FEELFnResult<BigDecimal> invoke(@ParameterName( "n" ) BigDecimal n, @ParameterName( "scale" ) BigDecimal scale) {
        if ( n == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "n", "cannot be null"));
        }
        if ( scale == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "scale", "cannot be null"));
        }
        // Based on Table 76: Semantics of numeric functions, the scale is in range −6111 .. 6176
        if (scale.compareTo(BigDecimal.valueOf(-6111)) < 0 || scale.compareTo(BigDecimal.valueOf(6176)) > 0) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "scale", "must be in range between -6111 to 6176."));
        }

        return FEELFnResult.ofResult( n.setScale( scale.intValue(), RoundingMode.FLOOR ) );
    }
}
