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
import java.util.stream.Collectors;

import org.kie.dmn.api.feel.runtime.events.FEELEvent.Severity;
import org.kie.dmn.feel.runtime.FEELNumberFunction;
import org.kie.dmn.feel.runtime.events.InvalidParametersEvent;

public class MedianFunction
        extends BaseFEELFunction implements FEELNumberFunction {
    public static final MedianFunction INSTANCE = new MedianFunction();

    private MedianFunction() {
        super("median");
    }

    public FEELFnResult<BigDecimal> invoke(@ParameterName("list") List<?> list) {
        if (list == null) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "list", "cannot be null"));
        }
        if (list.isEmpty()) {
            return FEELFnResult.ofResult(null); // DMN spec, Table 69: Semantics of list functions, median( [] ) = null
        }

        List<?> sorted = list.stream().sorted().collect(Collectors.toList());

        if (sorted.size() % 2 == 0) {
            // even sized list
            int medianPos0 = (sorted.size() / 2) - 1;
            int medianPos1 = medianPos0 + 1;
            if (!(sorted.get(medianPos0) instanceof BigDecimal) || !(sorted.get(medianPos1) instanceof BigDecimal)) {
                return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "list", "does not contain number"));
            }
            BigDecimal median0 = (BigDecimal) sorted.get(medianPos0);
            BigDecimal median1 = (BigDecimal) sorted.get(medianPos1);
            BigDecimal medianAvg = median0.add(median1).divide(new BigDecimal(2, MathContext.DECIMAL128), MathContext.DECIMAL128);
            return FEELFnResult.ofResult(medianAvg);
        } else {
            int medianPos = sorted.size() / 2;
            Object median = sorted.get(medianPos);
            if (!(median instanceof BigDecimal)) {
                return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "list", "does not contain number"));
            }
            return FEELFnResult.ofResult((BigDecimal) median);
        }
    }

    public FEELFnResult<BigDecimal> invoke(@ParameterName("n") Object[] list) {
        if ( list == null ) {
            // Arrays.asList does not accept null as parameter
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "n", "the single value list cannot be null"));
        }

        return invoke( Arrays.asList( list ) );
    }
}
