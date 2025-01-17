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

import java.util.List;
import java.util.StringJoiner;

import org.kie.dmn.api.feel.runtime.events.FEELEvent.Severity;
import org.kie.dmn.feel.runtime.FEELStringFunction;
import org.kie.dmn.feel.runtime.events.InvalidParametersEvent;

public class StringJoinFunction extends BaseFEELFunction implements FEELStringFunction {

    public static final StringJoinFunction INSTANCE = new StringJoinFunction();

    private StringJoinFunction() {
        super("string join");
    }

    public FEELFnResult<String> invoke(@ParameterName("list") List<?> list, @ParameterName("delimiter") String delimiter) {
        if ( list == null ) {
            return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "list", "cannot be null"));
        }
        if (list.isEmpty()) {
            return FEELFnResult.ofResult(""); // If list is empty, the result is the empty string
        }
        StringJoiner sj = new StringJoiner(delimiter != null ? delimiter : ""); // If delimiter is null, the string elements are joined without a separator
        for (Object element : list) {
            if (element == null) {
                continue; // Null elements in the list parameter are ignored.
            } else if (element instanceof CharSequence) {
                sj.add((CharSequence) element);
            } else {
                return FEELFnResult.ofError(new InvalidParametersEvent(Severity.ERROR, "list", "contains an element which is not a string"));
            }
        }
        return FEELFnResult.ofResult(sj.toString());
    }

    public FEELFnResult<String> invoke(@ParameterName("list") List<?> list) {
        return invoke(list, null);
    }
}
