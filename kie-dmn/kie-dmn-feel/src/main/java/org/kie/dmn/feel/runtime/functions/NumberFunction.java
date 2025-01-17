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

import org.kie.dmn.api.feel.runtime.events.FEELEvent.Severity;
import org.kie.dmn.feel.runtime.FEELNumberFunction;
import org.kie.dmn.feel.runtime.events.InvalidParametersEvent;
import org.kie.dmn.feel.util.NumberEvalHelper;

public class NumberFunction
        extends BaseFEELFunction implements FEELNumberFunction {

    public static final NumberFunction INSTANCE = new NumberFunction();

    private NumberFunction() {
        super(FEELConversionFunctionNames.NUMBER);
    }

    public FEELFnResult<BigDecimal> invoke(@ParameterName("from") String from, @ParameterName("grouping separator") String group, @ParameterName("decimal separator") String decimal) {
        if ( from == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "from", "cannot be null"));
        }
        if ( group != null && !group.equals( " " ) && !group.equals( "." ) && !group.equals( "," ) ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "group", "not a valid one, can only be one of: dot ('.'), comma (','), space (' ') "));
        }
        if ( decimal != null ) {
            if (!decimal.equals( "." ) && !decimal.equals( "," )) {
                return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "decimal", "not a valid one, can only be one of: dot ('.'), comma (',') "));
            } else if (group != null && decimal.equals( group )) {
                return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "decimal", "cannot be the same as parameter 'group' "));
            }
        }
        
        if ( group != null ) {
            from = from.replaceAll( "\\" + group, "" );
        }
        if ( decimal != null ) {
            from = from.replaceAll( "\\" + decimal, "." );
        }

        BigDecimal result = NumberEvalHelper.getBigDecimalOrNull(from );
        if( from != null && result == null ) {
            // conversion failed
            return FEELFnResult.ofError( new InvalidParametersEvent(Severity.ERROR, "unable to calculate final number result" ) );
        } else {
            return FEELFnResult.ofResult( result );
        }
    }

}
