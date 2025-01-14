/*
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
package org.kie.yard.api.model;

import java.util.Objects;

/**
 * Interface instead of enum due to possible custom operators.
 */
public interface Operators {

    String NOT_EQUALS = "!=";
    String EQUALS = "=";
    String GREATER_OR_EQUAL = ">=";
    String GREATER_THAN = ">";
    String LESS_OR_EQUAL = "<=";
    String LESS_THAN = "<";

    String[] ALL = {EQUALS, LESS_OR_EQUAL, LESS_THAN, GREATER_OR_EQUAL, GREATER_THAN, NOT_EQUALS};

    static int compare(final String operator,
                       final String other) {
        return getWeight(operator) - getWeight(other);
    }

    static int getWeight(final String operator) {
        for (int i = 0; i < ALL.length; i++) {
            if (Objects.equals(operator, ALL[i])) {
                return i;
            }
        }
        return 0;
    }
}
