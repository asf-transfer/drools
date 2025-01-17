/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.kie.dmn.core;

import java.util.Collections;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.dmn.api.core.DMNContext;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.api.core.DMNResult;
import org.kie.dmn.api.core.DMNRuntime;
import org.kie.dmn.core.impl.DMNModelImpl;
import org.kie.dmn.core.util.DMNRuntimeUtil;
import org.kie.dmn.feel.lang.FEELDialect;

import static org.assertj.core.api.Assertions.assertThat;

public class DMNInputRuntimeBFEELTest extends BaseInterpretedVsCompiledTest {

    @ParameterizedTest
    @MethodSource("params")
    void constraintsChecksBFEEL(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        final DMNRuntime runtime = DMNRuntimeUtil.createRuntime("valid_models/DMNv1_5/B-FEEL/ConstraintsChecksBFeel" +
                                                                        ".dmn", this.getClass());
        final DMNModel dmnModel = runtime.getModel(
                "http://www.trisotech.com/definitions/_238bd96d-47cd-4746-831b-504f3e77b442",
                "ConstraintsChecksBFEEL");
        assertThat(dmnModel).isNotNull();
        assertThat(dmnModel.hasErrors()).as(DMNRuntimeUtil.formatMessages(dmnModel.getMessages())).isFalse();
        assertThat(dmnModel).isInstanceOf(DMNModelImpl.class);
        assertThat(((DMNModelImpl) dmnModel).getFeelDialect()).isEqualTo(FEELDialect.BFEEL);
    }

    @ParameterizedTest
    @MethodSource("params")
    void bFeelChecks(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        final DMNRuntime runtime = DMNRuntimeUtil.createRuntime("valid_models/DMNv1_5/B-FEEL/BFeelChecks.dmn",
                                                                this.getClass());
        final DMNModel dmnModel = runtime.getModel(
                "http://www.trisotech.com/definitions/_238bd96d-47cd-4746-831b-504f3e77b442",
                "BFEELChecks");
        assertThat(dmnModel).isNotNull();
        assertThat(dmnModel.hasErrors()).as(DMNRuntimeUtil.formatMessages(dmnModel.getMessages())).isFalse();
        assertThat(dmnModel).isInstanceOf(DMNModelImpl.class);
        assertThat(((DMNModelImpl) dmnModel).getFeelDialect()).isEqualTo(FEELDialect.BFEEL);

        final DMNContext ctx1 = runtime.newContext();
        ctx1.set("user", "a");
        final DMNResult dmnResult1 = runtime.evaluateAll(dmnModel, ctx1);
        assertThat(dmnResult1.getDecisionResultByName("Decision1").getResult()).isEqualTo(false);
        assertThat(dmnResult1.getDecisionResultByName("Decision2").getResult()).isEqualTo(Collections.emptyList());
    }

    @ParameterizedTest
    @MethodSource("params")
    void bFeelOverrideChecks(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        final DMNRuntime runtime = DMNRuntimeUtil.createRuntime("valid_models/DMNv1_5/B-FEEL/BFeelOverrideChecks.dmn",
                                                                this.getClass());
        final DMNModel dmnModel = runtime.getModel(
                "http://www.trisotech.com/definitions/_238bd96d-47cd-4746-831b-504f3e77b442",
                "BFEELOverrideChecks");
        assertThat(dmnModel).isNotNull();
        assertThat(dmnModel.hasErrors()).as(DMNRuntimeUtil.formatMessages(dmnModel.getMessages())).isFalse();
        assertThat(dmnModel).isInstanceOf(DMNModelImpl.class);
        assertThat(((DMNModelImpl) dmnModel).getFeelDialect()).isEqualTo(FEELDialect.FEEL);

        final DMNContext ctx1 = runtime.newContext();
        ctx1.set("user", "a");
        final DMNResult dmnResult1 = runtime.evaluateAll(dmnModel, ctx1);
        assertThat(dmnResult1.getDecisionResultByName("Decision1").getResult()).isEqualTo(false);
        assertThat(dmnResult1.getDecisionResultByName("Decision2").getResult()).isNull();
        assertThat(dmnResult1.getDecisionResultByName("Decision3").getResult()).isEqualTo(Collections.emptyList());
    }
}
