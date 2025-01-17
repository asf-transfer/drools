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

import java.time.chrono.ChronoPeriod;
import java.time.temporal.TemporalAccessor;

import org.kie.dmn.feel.lang.FEELDialect;
import org.kie.dmn.feel.lang.types.BuiltInType;
import org.kie.dmn.feel.runtime.FEELBooleanFunction;
import org.kie.dmn.feel.util.BooleanEvalHelper;

public class IsFunction extends BaseFEELFunction implements FEELBooleanFunction {
    public static final IsFunction INSTANCE = new IsFunction();

    private IsFunction() {
        super("is");
    }

    public FEELFnResult<Boolean> invoke(@ParameterName("value1") Object value1, @ParameterName("value2") Object value2) {
        if (value1 instanceof ChronoPeriod && value2 instanceof ChronoPeriod) {
            // special check for YM durations
            return FEELFnResult.ofResult(value1.equals(value2));
        } else if (value1 instanceof TemporalAccessor && value2 instanceof TemporalAccessor) {
            // Handle specific cases when both time / datetime
            TemporalAccessor left = (TemporalAccessor) value1;
            TemporalAccessor right = (TemporalAccessor) value2;
            if (BuiltInType.determineTypeFromInstance(left) == BuiltInType.TIME && BuiltInType.determineTypeFromInstance(right) == BuiltInType.TIME) {
                return FEELFnResult.ofResult(BooleanEvalHelper.isEqualTimeInSemanticD(left, right));
            } else if (BuiltInType.determineTypeFromInstance(left) == BuiltInType.DATE_TIME && BuiltInType.determineTypeFromInstance(right) == BuiltInType.DATE_TIME) {
                return FEELFnResult.ofResult(BooleanEvalHelper.isEqualDateTimeInSemanticD(left, right));
            } // fallback; continue:
        }
        Boolean fallback = BooleanEvalHelper.isEqual(value1, value2, FEELDialect.FEEL); // if null implying they are not the same semantic domain value.
        return FEELFnResult.ofResult(fallback != null ? fallback : Boolean.FALSE);
    }

}
