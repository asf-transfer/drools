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
import java.util.ArrayList;
import java.util.List;

import org.kie.dmn.api.feel.runtime.events.FEELEvent.Severity;
import org.kie.dmn.feel.runtime.FEELCollectionFunction;
import org.kie.dmn.feel.runtime.events.InvalidParametersEvent;
import org.kie.dmn.feel.util.NumberEvalHelper;

public class ListReplaceFunction
        extends BaseFEELFunction implements FEELCollectionFunction {

    public static final ListReplaceFunction INSTANCE = new ListReplaceFunction();

    private static final String CANNOT_BE_NULL = "cannot be null";

    private ListReplaceFunction() {
        super("list replace");
    }

    public FEELFnResult<List> invoke(@ParameterName("list") List list, @ParameterName("position") BigDecimal position,
                                     @ParameterName("newItem") Object newItem) {
        if (list == null) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "list", CANNOT_BE_NULL));
        }
        if (position == null) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "position", CANNOT_BE_NULL));
        }
        int intPosition = position.intValue();
        if (intPosition == 0 || Math.abs(intPosition) > list.size()) {
            String paramProblem = String.format("%s outside valid boundaries (1-%s)", intPosition, list.size());
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "position", paramProblem));
        }
        Object e = NumberEvalHelper.coerceNumber(newItem);
        List toReturn = new ArrayList(list);
        int replacementPosition = intPosition > 0 ? intPosition -1 : list.size() - Math.abs(intPosition);
        toReturn.set(replacementPosition, e);
        return FEELFnResult.ofResult(toReturn);
    }

    public FEELFnResult<List> invoke(@ParameterName("list") List list,
                                     @ParameterName("match") AbstractCustomFEELFunction match,
                                     @ParameterName("newItem") Object newItem) {
        if (list == null) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "list", CANNOT_BE_NULL));
        }
        if (match == null) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "match", CANNOT_BE_NULL));
        }
        Object e = NumberEvalHelper.coerceNumber(newItem);
        List toReturn = new ArrayList();
        for (Object o : list) {
            Object matched = match.invokeReflectively(match.getEvaluationContext(), new Object[]{o, e});
            if (matched instanceof Boolean isMatch) {
                if (isMatch) {
                    toReturn.add(e);
                } else {
                    toReturn.add(o);
                }
            } else {
                String paramProblem = String.format("%s returns '%s' instead of boolean", match, matched);
                return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "match", paramProblem));
            }
        }
        return FEELFnResult.ofResult(toReturn);
    }
}
