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
package org.kie.dmn.core.ast;

import org.junit.jupiter.api.Test;
import org.kie.dmn.api.core.EvaluatorResult;
import org.kie.dmn.api.feel.runtime.events.FEELEvent;
import org.kie.dmn.feel.lang.FEELDialect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.dmn.core.ast.DMNLiteralExpressionEvaluator.getFEELDialectAdaptedResultType;

class DMNLiteralExpressionEvaluatorTest {

    @Test
    void getFEELDialectAdaptedResultTypeFEELDialectNoErrorSeverity() {
        FEELDialect feelDialect = FEELDialect.FEEL;
        Object nullValue = null;
        for (FEELEvent.Severity severity : FEELEvent.Severity.values()) {
            if (severity != FEELEvent.Severity.ERROR) {
                assertThat(getFEELDialectAdaptedResultType(severity, nullValue, feelDialect)).isEqualTo(EvaluatorResult.ResultType.SUCCESS);
            }
        }
        Object notNullValue = 234;
        for (FEELEvent.Severity severity : FEELEvent.Severity.values()) {
            if (severity != FEELEvent.Severity.ERROR) {
                assertThat(getFEELDialectAdaptedResultType(severity, notNullValue, feelDialect)).isEqualTo(EvaluatorResult.ResultType.SUCCESS);
            }
        }
    }

    @Test
    void getFEELDialectAdaptedResultTypeFEELDialectErrorSeverity() {
        FEELDialect feelDialect = FEELDialect.FEEL;
        Object nullValue = null;
        FEELEvent.Severity severity = FEELEvent.Severity.ERROR;
        assertThat(getFEELDialectAdaptedResultType(severity, nullValue, feelDialect)).isEqualTo(EvaluatorResult.ResultType.FAILURE);
        Object notNullValue = 234;
        assertThat(getFEELDialectAdaptedResultType(severity, notNullValue, feelDialect)).isEqualTo(EvaluatorResult.ResultType.FAILURE);
    }

    @Test
    void getFEELDialectAdaptedResultTypeBFEELDialectNoErrorSeverity() {
        FEELDialect feelDialect = FEELDialect.BFEEL;
        Object nullValue = null;
        for (FEELEvent.Severity severity : FEELEvent.Severity.values()) {
            if (severity != FEELEvent.Severity.ERROR) {
                assertThat(getFEELDialectAdaptedResultType(severity, nullValue, feelDialect)).isEqualTo(EvaluatorResult.ResultType.SUCCESS);
            }
        }
        Object notNullValue = 234;
        for (FEELEvent.Severity severity : FEELEvent.Severity.values()) {
            if (severity != FEELEvent.Severity.ERROR) {
                assertThat(getFEELDialectAdaptedResultType(severity, notNullValue, feelDialect)).isEqualTo(EvaluatorResult.ResultType.SUCCESS);
            }
        }
    }

    @Test
    void getFEELDialectAdaptedResultTypeBFEELDialectErrorSeverity() {
        FEELDialect feelDialect = FEELDialect.BFEEL;
        Object nullValue = null;
        FEELEvent.Severity severity = FEELEvent.Severity.ERROR;
        assertThat(getFEELDialectAdaptedResultType(severity, nullValue, feelDialect)).isEqualTo(EvaluatorResult.ResultType.FAILURE);
        Object notNullValue = 234;
        assertThat(getFEELDialectAdaptedResultType(severity, notNullValue, feelDialect)).isEqualTo(EvaluatorResult.ResultType.SUCCESS);
    }
}