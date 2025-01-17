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
import java.math.MathContext;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.kie.dmn.api.feel.runtime.events.FEELEvent;
import org.kie.dmn.api.feel.runtime.events.FEELEvent.Severity;
import org.kie.dmn.feel.runtime.FEELNumberFunction;
import org.kie.dmn.feel.runtime.events.InvalidParametersEvent;

public class MeanFunction
        extends BaseFEELFunction implements FEELNumberFunction {

    public static final MeanFunction INSTANCE = new MeanFunction();

    private SumFunction sum = SumFunction.INSTANCE;

    private MeanFunction() {
        super( "mean" );
    }

    public FEELFnResult<BigDecimal> invoke(@ParameterName( "list" ) List list) {
        if ( list == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "list", "cannot be null"));
        }

        FEELFnResult<BigDecimal> s = sum.invoke( list );
        
        Function<FEELEvent, FEELFnResult<BigDecimal>> ifLeft = event ->
                FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "list", "unable to sum the elements which is required to calculate the mean"));
        
        Function<BigDecimal, FEELFnResult<BigDecimal>> ifRight = (sum) -> {
            try {
                return FEELFnResult.ofResult( sum.divide( BigDecimal.valueOf( list.size() ), MathContext.DECIMAL128 ) );
            } catch (Exception e) {
                return FEELFnResult.ofError( new InvalidParametersEvent(Severity.ERROR, "unable to perform division to calculate the mean", e) );
            }
        };
        
        return s.cata(ifLeft, ifRight);
    }

    public FEELFnResult<BigDecimal> invoke(@ParameterName( "n" ) Object[] list) {
        if ( list == null ) { 
            // Arrays.asList does not accept null as parameter
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "n", "cannot be null"));
        }
        
        return invoke( Arrays.asList( list ) );
    }
}
