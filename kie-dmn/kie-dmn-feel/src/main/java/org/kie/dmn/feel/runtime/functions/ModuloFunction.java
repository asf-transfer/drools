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

import org.kie.dmn.api.feel.runtime.events.FEELEvent.Severity;
import org.kie.dmn.feel.runtime.FEELNumberFunction;
import org.kie.dmn.feel.runtime.events.InvalidParametersEvent;

public class ModuloFunction
        extends BaseFEELFunction implements FEELNumberFunction {
    public static final ModuloFunction INSTANCE = new ModuloFunction();

    private ModuloFunction() {
        super( "modulo" );
    }

    public FEELFnResult<BigDecimal> invoke(@ParameterName( "dividend" ) BigDecimal divident, @ParameterName( "divisor" ) BigDecimal divisor) {
        if ( divident == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "divident", "cannot be null"));
        }
        if ( divisor == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "divisor", "cannot be null"));
        }
        return FloorFunction.INSTANCE.invoke(divident.divide(divisor,
                                                             MathContext.DECIMAL128))
                                     .map(f -> divident.subtract(divisor.multiply(f,
                                                                                  MathContext.DECIMAL128),
                                                                 MathContext.DECIMAL128));
    }
}
