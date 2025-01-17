/**
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.dmn.api.core.DMNContext;
import org.kie.dmn.api.core.DMNDecisionResult;
import org.kie.dmn.api.core.DMNMessage;
import org.kie.dmn.api.core.DMNMessageType;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.api.core.DMNResult;
import org.kie.dmn.api.core.DMNRuntime;
import org.kie.dmn.api.core.ast.BusinessKnowledgeModelNode;
import org.kie.dmn.api.core.ast.DecisionNode;
import org.kie.dmn.api.core.ast.DecisionServiceNode;
import org.kie.dmn.api.core.ast.InputDataNode;
import org.kie.dmn.api.core.ast.ItemDefNode;
import org.kie.dmn.api.core.event.AfterConditionalEvaluationEvent;
import org.kie.dmn.api.core.event.AfterEvaluateConditionalEvent;
import org.kie.dmn.api.core.event.AfterEvaluateDecisionTableEvent;
import org.kie.dmn.core.api.DMNFactory;
import org.kie.dmn.core.api.event.DefaultDMNRuntimeEventListener;
import org.kie.dmn.core.util.DMNRuntimeUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.dmn.core.util.DynamicTypeUtils.entry;
import static org.kie.dmn.core.util.DynamicTypeUtils.prototype;

public class DMNInputRuntimeTest extends BaseInterpretedVsCompiledTest {

    @ParameterizedTest
    @MethodSource("params")
    void inputStringEvaluateAll(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        final DMNRuntime runtime = DMNRuntimeUtil.createRuntime("0001-input-data-string.dmn", this.getClass());
        final DMNModel dmnModel = runtime.getModel("https://github.com/kiegroup/drools/kie-dmn", "_0001-input-data" +
                "-string");
        assertThat(dmnModel).isNotNull();

        final DMNContext context = DMNFactory.newContext();
        context.set("Full Name", "John Doe");

        final DMNResult dmnResult = runtime.evaluateAll(dmnModel, context);

        assertThat(dmnResult.getDecisionResults()).hasSize(1);
        assertThat(dmnResult.getDecisionResultByName("Greeting Message").getResult()).isEqualTo("Hello John Doe");

        final DMNContext result = dmnResult.getContext();

        assertThat(result.get("Greeting Message")).isEqualTo("Hello John Doe");
    }

    @ParameterizedTest
    @MethodSource("params")
    void inputStringEvaluateDecisionByName(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        final DMNRuntime runtime = DMNRuntimeUtil.createRuntime("0001-input-data-string.dmn", this.getClass());
        final DMNModel dmnModel = runtime.getModel("https://github.com/kiegroup/drools/kie-dmn", "_0001-input-data" +
                "-string");
        assertThat(dmnModel).isNotNull();

        final DMNContext context = DMNFactory.newContext();
        context.set("Full Name", "John Doe");

        DMNResult dmnResult = runtime.evaluateByName(dmnModel, context, "Greeting Message");

        assertThat(dmnResult.getDecisionResults()).hasSize(1);
        assertThat(dmnResult.getDecisionResultByName("Greeting Message").getResult()).isEqualTo("Hello John Doe");

        final DMNContext result = dmnResult.getContext();

        assertThat(result.get("Greeting Message")).isEqualTo("Hello John Doe");

        dmnResult = runtime.evaluateByName(dmnModel, context, "nonExistantName");
        assertThat(dmnResult.getDecisionResults()).hasSize(1);
        assertThat(dmnResult.getDecisionResultByName("Greeting Message").getEvaluationStatus()).isEqualTo(DMNDecisionResult.DecisionEvaluationStatus.NOT_EVALUATED);

        dmnResult = runtime.evaluateByName(dmnModel, context, "");
        assertThat(dmnResult.getDecisionResults()).hasSize(1);
        assertThat(dmnResult.getDecisionResultByName("Greeting Message").getEvaluationStatus()).isEqualTo(DMNDecisionResult.DecisionEvaluationStatus.NOT_EVALUATED);

        dmnResult = runtime.evaluateByName(dmnModel, context, (String) null);
        assertThat(dmnResult.getDecisionResults()).hasSize(1);
        assertThat(dmnResult.getDecisionResultByName("Greeting Message").getEvaluationStatus()).isEqualTo(DMNDecisionResult.DecisionEvaluationStatus.NOT_EVALUATED);
    }

    @ParameterizedTest
    @MethodSource("params")
    void inputStringEvaluateDecisionById(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        final DMNRuntime runtime = DMNRuntimeUtil.createRuntime("0001-input-data-string.dmn", this.getClass());
        final DMNModel dmnModel = runtime.getModel("https://github.com/kiegroup/drools/kie-dmn", "_0001-input-data" +
                "-string");
        assertThat(dmnModel).isNotNull();

        final DMNContext context = DMNFactory.newContext();
        context.set("Full Name", "John Doe");

        DMNResult dmnResult = runtime.evaluateById(dmnModel, context, "d_GreetingMessage");

        assertThat(dmnResult.getDecisionResults()).hasSize(1);
        assertThat(dmnResult.getDecisionResultById("d_GreetingMessage").getResult()).isEqualTo("Hello John Doe");

        final DMNContext result = dmnResult.getContext();

        assertThat(result.get("Greeting Message")).isEqualTo("Hello John Doe");

        dmnResult = runtime.evaluateById(dmnModel, context, "nonExistantId");
        assertThat(dmnResult.getDecisionResults()).hasSize(1);
        assertThat(dmnResult.getDecisionResultByName("Greeting Message").getEvaluationStatus()).isEqualTo(DMNDecisionResult.DecisionEvaluationStatus.NOT_EVALUATED);

        dmnResult = runtime.evaluateById(dmnModel, context, "");
        assertThat(dmnResult.getDecisionResults()).hasSize(1);
        assertThat(dmnResult.getDecisionResultByName("Greeting Message").getEvaluationStatus()).isEqualTo(DMNDecisionResult.DecisionEvaluationStatus.NOT_EVALUATED);

        dmnResult = runtime.evaluateById(dmnModel, context, (String) null);
        assertThat(dmnResult.getDecisionResults()).hasSize(1);
        assertThat(dmnResult.getDecisionResultByName("Greeting Message").getEvaluationStatus()).isEqualTo(DMNDecisionResult.DecisionEvaluationStatus.NOT_EVALUATED);
    }

    @ParameterizedTest
    @MethodSource("params")
    void inputStringAllowedValuesEvaluateAll(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        final DMNRuntime runtime = DMNRuntimeUtil.createRuntime("0003-input-data-string-allowed-values.dmn",
                                                                this.getClass());
        final DMNModel dmnModel = runtime.getModel("https://github.com/kiegroup/kie-dmn", "0003-input-data-string" +
                "-allowed-values");
        assertThat(dmnModel).isNotNull();

        final DMNContext context = DMNFactory.newContext();
        context.set("Employment Status", "SELF-EMPLOYED");

        final DMNResult dmnResult = runtime.evaluateAll(dmnModel, context);

        final DMNContext result = dmnResult.getContext();

        assertThat(result.get("Employment Status Statement")).isEqualTo("You are SELF-EMPLOYED");
    }

    @ParameterizedTest
    @MethodSource("params")
    void inputStringNotInTypeScopeEvaluateAll(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        testInputStringNotAllowedValuesEvaluateAll("NOT-ALLOWED-VALUE");
    }

    @ParameterizedTest
    @MethodSource("params")
    void inputStringWrongTypeEvaluateAll(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        testInputStringNotAllowedValuesEvaluateAll(new Object());
    }

    private void testInputStringNotAllowedValuesEvaluateAll(final Object inputValue) {
        final DMNRuntime runtime = DMNRuntimeUtil.createRuntime("0003-input-data-string-allowed-values.dmn",
                                                                this.getClass());
        final DMNModel dmnModel = runtime.getModel("https://github.com/kiegroup/kie-dmn", "0003-input-data-string" +
                "-allowed-values");
        assertThat(dmnModel).isNotNull();

        final DMNContext context = DMNFactory.newContext();
        context.set("Employment Status", inputValue);

        final DMNResult dmnResult = runtime.evaluateAll(dmnModel, context);

        assertThat(dmnResult.getDecisionResults()).hasSize(1);
        assertThat(dmnResult.getDecisionResultByName("Employment Status Statement").getResult()).isNull();
        assertThat(dmnResult.getMessages()).hasSize(1);
        assertThat(dmnResult.getMessages().get(0).getSeverity()).isEqualTo(DMNMessage.Severity.ERROR);
        assertThat(dmnResult.getDecisionResults().get(0).getMessages()).hasSize(1);
        assertThat(dmnResult.getDecisionResults().get(0).getMessages().get(0).getSeverity()).isEqualTo(DMNMessage.Severity.ERROR);
    }

    @ParameterizedTest
    @MethodSource("params")
    void inputNumberEvaluateAll(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        inputNumberEvaluateAll(
                "0002-input-data-number.dmn",
                "0002-input-data-number");
    }

    @ParameterizedTest
    @MethodSource("params")
    void inputNumberEvaluateAllDMNWithScientificNotation(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        inputNumberEvaluateAll(
                "0002-input-data-number-scientific-notation.dmn",
                "0002-input-data-number-scientific-notation");
    }

    private void inputNumberEvaluateAll(final String testDMNFileName, final String modelName) {
        final DMNRuntime runtime = DMNRuntimeUtil.createRuntime(testDMNFileName, this.getClass());
        final DMNModel dmnModel = runtime.getModel("https://github.com/kiegroup/kie-dmn", modelName);
        assertThat(dmnModel).isNotNull();

        final DMNContext context = DMNFactory.newContext();
        context.set("Monthly Salary", new BigDecimal(1000));

        final DMNResult dmnResult = runtime.evaluateAll(dmnModel, context);

        final DMNContext result = dmnResult.getContext();

        final Object resultValue = result.get("Yearly Salary");
        assertThat(resultValue).isInstanceOf(BigDecimal.class);
        assertThat((BigDecimal) resultValue).usingComparator(BigDecimal::compareTo).isEqualTo(new BigDecimal(12000));
    }

    @ParameterizedTest
    @MethodSource("params")
    void getRequiredInputsByName(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        final DMNRuntime runtime = DMNRuntimeUtil.createRuntime("0001-input-data-string.dmn", this.getClass());
        final DMNModel dmnModel = runtime.getModel("https://github.com/kiegroup/drools/kie-dmn", "_0001-input-data" +
                "-string");
        assertThat(dmnModel).isNotNull();

        Set<InputDataNode> inputs = dmnModel.getRequiredInputsForDecisionName("Greeting Message");

        assertThat(inputs).hasSize(1);
        assertThat(inputs.iterator().next().getName()).isEqualTo("Full Name");

        inputs = dmnModel.getRequiredInputsForDecisionName("nonExistantDecisionName");
        assertThat(inputs).hasSize(0);
    }

    @ParameterizedTest
    @MethodSource("params")
    void getRequiredInputsById(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        final DMNRuntime runtime = DMNRuntimeUtil.createRuntime("0001-input-data-string.dmn", this.getClass());
        final DMNModel dmnModel = runtime.getModel("https://github.com/kiegroup/drools/kie-dmn", "_0001-input-data" +
                "-string");
        assertThat(dmnModel).isNotNull();

        Set<InputDataNode> inputs = dmnModel.getRequiredInputsForDecisionId("d_GreetingMessage");

        assertThat(inputs).hasSize(1);
        assertThat(inputs.iterator().next().getName()).isEqualTo("Full Name");

        inputs = dmnModel.getRequiredInputsForDecisionId("nonExistantId");
        assertThat(inputs).hasSize(0);
    }

    @ParameterizedTest
    @MethodSource("params")
    void nonexistantInputNodeName(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        final DMNRuntime runtime = DMNRuntimeUtil.createRuntime("0001-input-data-string.dmn", this.getClass());
        final DMNModel dmnModel = runtime.getModel("https://github.com/kiegroup/drools/kie-dmn", "_0001-input-data" +
                "-string");
        assertThat(dmnModel).isNotNull();

        final DMNContext context = DMNFactory.newContext();
        context.set("Nonexistant Input", "John Doe");

        final DMNResult dmnResult = runtime.evaluateAll(dmnModel, context);

        assertThat(dmnResult.getDecisionResults()).hasSize(1);
        assertThat(dmnResult.getDecisionResultByName("Greeting Message").getResult()).isNull();
        assertThat(dmnResult.getMessages()).hasSize(1);
        assertThat(dmnResult.getMessages().get(0).getSeverity()).isEqualTo(DMNMessage.Severity.ERROR);
        assertThat(dmnResult.getDecisionResults().get(0).getMessages()).hasSize(1);
        assertThat(dmnResult.getDecisionResults().get(0).getMessages().get(0).getSeverity()).isEqualTo(DMNMessage.Severity.ERROR);
    }

    @ParameterizedTest
    @MethodSource("params")
    void allowedValuesChecksInsideCollectionElement(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        final DMNRuntime runtime = DMNRuntimeUtil.createRuntime("AllowedValuesChecks.dmn", this.getClass());
        final DMNModel dmnModel = runtime.getModel(
                "http://www.trisotech.com/definitions/_238bd96d-47cd-4746-831b-504f3e77b442",
                "AllowedValuesChecks");
        assertThat(dmnModel).isNotNull();
        assertThat(dmnModel.hasErrors()).as(DMNRuntimeUtil.formatMessages(dmnModel.getMessages())).isFalse();

        final DMNContext ctx1 = runtime.newContext();
        ctx1.set("p1", prototype(entry("Name", "P1"), entry("Interests", Collections.singletonList("Golf"))));
        final DMNResult dmnResult1 = runtime.evaluateAll(dmnModel, ctx1);
        assertThat(dmnResult1.hasErrors()).as(DMNRuntimeUtil.formatMessages(dmnResult1.getMessages())).isFalse();
        assertThat(dmnResult1.getContext().get("MyDecision")).isEqualTo("The Person P1 likes 1 thing(s).");

        final DMNContext ctx2 = runtime.newContext();
        ctx2.set("p1", prototype(entry("Name", "P2"), entry("Interests", Collections.singletonList("x"))));
        final DMNResult dmnResult2 = runtime.evaluateAll(dmnModel, ctx2);
        assertThat(dmnResult2.hasErrors()).as(DMNRuntimeUtil.formatMessages(dmnResult2.getMessages())).isTrue();
        assertThat(dmnResult2.getMessages().stream().anyMatch(m -> m.getMessageType().equals(DMNMessageType.ERROR_EVAL_NODE))).isTrue();

        final DMNContext ctx3 = runtime.newContext();
        ctx3.set("p1", prototype(entry("Name", "P3"), entry("Interests", Arrays.asList("Golf", "Computer"))));
        final DMNResult dmnResult3 = runtime.evaluateAll(dmnModel, ctx3);
        assertThat(dmnResult3.hasErrors()).as(DMNRuntimeUtil.formatMessages(dmnResult3.getMessages())).isFalse();
        assertThat(dmnResult3.getContext().get("MyDecision")).isEqualTo("The Person P3 likes 2 thing(s).");

        final DMNContext ctx4 = runtime.newContext();
        ctx4.set("p1", prototype(entry("Name", "P4"), entry("Interests", Arrays.asList("Golf", "x"))));
        final DMNResult dmnResult4 = runtime.evaluateAll(dmnModel, ctx4);
        assertThat(dmnResult4.hasErrors()).as(DMNRuntimeUtil.formatMessages(dmnResult4.getMessages())).isTrue();
        assertThat(dmnResult4.getMessages().stream().anyMatch(m -> m.getMessageType().equals(DMNMessageType.ERROR_EVAL_NODE))).isTrue();
    }

    @ParameterizedTest
    @MethodSource("params")
    void allowedValuesChecksInsideCollection(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        final DMNRuntime runtime = DMNRuntimeUtil.createRuntime("valid_models/DMNv1_5" +
                                                                        "/AllowedValuesChecksInsideCollection.dmn",
                                                                this.getClass());
        final DMNModel dmnModel = runtime.getModel(
                "http://www.trisotech.com/definitions/_238bd96d-47cd-4746-831b-504f3e77b442",
                "AllowedValuesChecksInsideCollection");
        assertThat(dmnModel).isNotNull();
        assertThat(dmnModel.hasErrors()).as(DMNRuntimeUtil.formatMessages(dmnModel.getMessages())).isFalse();

        final DMNContext ctx1 = runtime.newContext();
        ctx1.set("p1", prototype(entry("Name", "P1"), entry("Interests", Collections.singletonList("Golf"))));
        final DMNResult dmnResult1 = runtime.evaluateAll(dmnModel, ctx1);
        assertThat(dmnResult1.hasErrors()).as(DMNRuntimeUtil.formatMessages(dmnResult1.getMessages())).isFalse();
        assertThat(dmnResult1.getContext().get("MyDecision")).isEqualTo("The Person P1 likes 1 thing(s).");

        final DMNContext ctx2 = runtime.newContext();
        ctx2.set("p1", prototype(entry("Name", "P2"), entry("Interests", Collections.singletonList("x"))));
        final DMNResult dmnResult2 = runtime.evaluateAll(dmnModel, ctx2);
        assertThat(dmnResult2.hasErrors()).as(DMNRuntimeUtil.formatMessages(dmnResult2.getMessages())).isTrue();
        assertThat(dmnResult2.getMessages().stream().anyMatch(m -> m.getMessageType().equals(DMNMessageType.ERROR_EVAL_NODE))).isTrue();

        final DMNContext ctx3 = runtime.newContext();
        ctx3.set("p1", prototype(entry("Name", "P3"), entry("Interests", Arrays.asList("Golf", "Computer"))));
        final DMNResult dmnResult3 = runtime.evaluateAll(dmnModel, ctx3);
        assertThat(dmnResult3.hasErrors()).as(DMNRuntimeUtil.formatMessages(dmnResult3.getMessages())).isFalse();
        assertThat(dmnResult3.getContext().get("MyDecision")).isEqualTo("The Person P3 likes 2 thing(s).");

        final DMNContext ctx4 = runtime.newContext();
        ctx4.set("p1", prototype(entry("Name", "P4"), entry("Interests", Arrays.asList("Golf", "x"))));
        final DMNResult dmnResult4 = runtime.evaluateAll(dmnModel, ctx4);
        assertThat(dmnResult4.hasErrors()).as(DMNRuntimeUtil.formatMessages(dmnResult4.getMessages())).isTrue();
        assertThat(dmnResult4.getMessages().stream().anyMatch(m -> m.getMessageType().equals(DMNMessageType.ERROR_EVAL_NODE))).isTrue();
    }

    @ParameterizedTest
    @MethodSource("params")
    void typeConstraintsChecks(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        final DMNRuntime runtime = DMNRuntimeUtil.createRuntime("valid_models/DMNv1_5/TypeConstraintsChecks.dmn",
                                                                this.getClass());
        final DMNModel dmnModel = runtime.getModel(
                "http://www.trisotech.com/definitions/_238bd96d-47cd-4746-831b-504f3e77b442",
                "TypeConstraintsChecks");
        assertThat(dmnModel).isNotNull();
        assertThat(dmnModel.hasErrors()).as(DMNRuntimeUtil.formatMessages(dmnModel.getMessages())).isFalse();

        final DMNContext ctx1 = runtime.newContext();
        ctx1.set("p1", prototype(entry("Name", "P1"), entry("Interests", Collections.singletonList("Golf"))));
        final DMNResult dmnResult1 = runtime.evaluateAll(dmnModel, ctx1);
        assertThat(dmnResult1.hasErrors()).as(DMNRuntimeUtil.formatMessages(dmnResult1.getMessages())).isFalse();
        assertThat(dmnResult1.getContext().get("MyDecision")).isEqualTo("The Person P1 likes 1 thing(s).");

        final DMNContext ctx2 = runtime.newContext();
        ctx2.set("p1", prototype(entry("Name", "P2"), entry("Interests", Collections.singletonList("x"))));
        final DMNResult dmnResult2 = runtime.evaluateAll(dmnModel, ctx2);
        assertThat(dmnResult2.hasErrors()).as(DMNRuntimeUtil.formatMessages(dmnResult2.getMessages())).isFalse();
        assertThat(dmnResult2.getContext().get("MyDecision")).isEqualTo("The Person P2 likes 1 thing(s).");

        final DMNContext ctx3 = runtime.newContext();
        ctx3.set("p1", prototype(entry("Name", "P3"), entry("Interests", Arrays.asList("Golf", "Computer"))));
        final DMNResult dmnResult3 = runtime.evaluateAll(dmnModel, ctx3);
        assertThat(dmnResult3.hasErrors()).as(DMNRuntimeUtil.formatMessages(dmnResult3.getMessages())).isTrue();
        assertThat(dmnResult3.getMessages().stream().anyMatch(m -> m.getMessageType().equals(DMNMessageType.ERROR_EVAL_NODE))).isTrue();
    }

    @ParameterizedTest
    @MethodSource("params")
    void evaluationHitIdsCheck(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        final String ifElementId = "_3C702CE4-E5A0-4B6F-905D-C2621FFFA387";
        final String thenElementId = "_6481FF12-61B5-451C-B775-4143D9B6CD6B";
        final String elseElementId = "_2CD02CB2-6B56-45C4-B461-405E89D45633";
        final String ruleId0 = "_1578BD9E-2BF9-4BFC-8956-1A736959C937";
        final String ruleId1 = "_31CD7AA3-A806-4E7E-B512-821F82043620";
        final String ruleId3 = "_2545E1A8-93D3-4C8A-A0ED-8AD8B10A58F9";
        final String ruleId4 = "_510A50DA-D5A4-4F06-B0BE-7F8F2AA83740";
        final DMNRuntime runtime = DMNRuntimeUtil.createRuntime("valid_models/DMNv1_5/RiskScore_Simple.dmn",
                                                                this.getClass());

        final List<String> evaluateConditionalIds = new ArrayList<>();
        final List<String> conditionalEvaluationIds = new ArrayList<>();
        final List<String> executedRuleIds = new ArrayList<>();
        runtime.addListener(new DefaultDMNRuntimeEventListener() {

            @Override
            public void afterConditionalEvaluation(AfterConditionalEvaluationEvent event) {
                conditionalEvaluationIds.add(event.getExecutedId());
            }

            @Override
            public void afterEvaluateConditional(AfterEvaluateConditionalEvent event) {
                evaluateConditionalIds.add(event.getExecutedId());
            }

            @Override
            public void afterEvaluateDecisionTable(AfterEvaluateDecisionTableEvent event) {
                executedRuleIds.addAll(event.getSelectedIds());
            }
        });
        final DMNModel dmnModel = runtime.getModel(
                "https://kie.org/dmn/_A3317FB1-7BF8-4904-A5F4-2CD63AF3AEC9",
                "DMN_A77074C1-21FE-4F7E-9753-F84661569AFC");
        assertThat(dmnModel).isNotNull();
        assertThat(dmnModel.hasErrors()).as(DMNRuntimeUtil.formatMessages(dmnModel.getMessages())).isFalse();

        final DMNContext ctx1 = runtime.newContext();
        ctx1.set("Credit Score", "Poor");
        ctx1.set("DTI", 33);
        final DMNResult dmnResult1 = runtime.evaluateAll(dmnModel, ctx1);
        assertThat(dmnResult1.hasErrors()).as(DMNRuntimeUtil.formatMessages(dmnResult1.getMessages())).isFalse();
        assertThat(dmnResult1.getContext().get("Risk Score")).isEqualTo(BigDecimal.valueOf(50));
        assertThat(evaluateConditionalIds).hasSize(1).allMatch(id -> id.equals(ifElementId));
        assertThat(conditionalEvaluationIds).hasSize(1).allMatch(id -> id.equals(elseElementId));
        assertThat(executedRuleIds).hasSize(2).contains(ruleId0, ruleId3);

        //
        evaluateConditionalIds.clear();
        conditionalEvaluationIds.clear();
        executedRuleIds.clear();
        final DMNContext ctx2 = runtime.newContext();
        ctx2.set("Credit Score", "Excellent");
        ctx2.set("DTI", 10);
        final DMNResult dmnResult2 = runtime.evaluateAll(dmnModel, ctx2);
        assertThat(dmnResult2.hasErrors()).as(DMNRuntimeUtil.formatMessages(dmnResult1.getMessages())).isFalse();
        assertThat(dmnResult2.getContext().get("Risk Score")).isEqualTo(BigDecimal.valueOf(20));
        assertThat(evaluateConditionalIds).hasSize(1).allMatch(id -> id.equals(ifElementId));
        assertThat(conditionalEvaluationIds).hasSize(1).allMatch(id -> id.equals(thenElementId));
        assertThat(executedRuleIds).hasSize(2).contains(ruleId1, ruleId4);
    }

    @ParameterizedTest
    @MethodSource("params")
    void dmnInputDataNodeTypeTest(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        // DROOLS-1569
        final DMNRuntime runtime = DMNRuntimeUtil.createRuntime("DMNInputDataNodeTypeTest.dmn", this.getClass());
        final String MODEL_NAMESPACE = "http://www.trisotech.com/definitions/_17396034-163a-48aa-9a7f-c6eb17f9cc6c";
        final String FEEL_NAMESPACE = org.kie.dmn.model.v1_5.KieDMNModelInstrumentedBase.URI_FEEL;
        final DMNModel dmnModel = runtime.getModel(MODEL_NAMESPACE, "DMNInputDataNodeTypeTest");
        assertThat(dmnModel).isNotNull();
        assertThat(dmnModel.hasErrors()).as(DMNRuntimeUtil.formatMessages(dmnModel.getMessages())).isFalse();

        final InputDataNode idnMembership = dmnModel.getInputs().stream().filter(idn -> idn.getName().equals(
                "Membership Level")).findFirst().get();
        assertThat(idnMembership.getType().getBaseType().getNamespace()).isEqualTo(FEEL_NAMESPACE);
        assertThat(idnMembership.getType().getBaseType().getName()).isEqualTo("string");
        assertThat(idnMembership.getType().isCollection()).isFalse();
        assertThat(idnMembership.getType().isComposite()).isFalse();
        assertThat(idnMembership.getType().getAllowedValues()).hasSize(3);
        assertThat(idnMembership.getType().getAllowedValues().get(0).toString()).isEqualTo("\"Gold\"");
        assertThat(idnMembership.getType().getAllowedValues().get(1).toString()).isEqualTo("\"Silver\"");
        assertThat(idnMembership.getType().getAllowedValues().get(2).toString()).isEqualTo("\"None\"");

        final InputDataNode idnMembershipLevels = dmnModel.getInputs().stream().filter(idn -> idn.getName().equals(
                "Membership Levels")).findFirst().get();
        assertThat(idnMembershipLevels.getType().getBaseType().getNamespace()).isEqualTo(MODEL_NAMESPACE);
        assertThat(idnMembershipLevels.getType().getBaseType().getName()).isEqualTo("tMembershipLevel");
        assertThat(idnMembershipLevels.getType().isCollection()).isTrue();
        assertThat(idnMembershipLevels.getType().isComposite()).isFalse();
        assertThat(idnMembershipLevels.getType().getAllowedValues()).isEmpty();

        final InputDataNode idnPercent =
                dmnModel.getInputs().stream().filter(idn -> idn.getName().equals("Percent")).findFirst().get();
        assertThat(idnPercent.getType().getBaseType().getNamespace()).isEqualTo(FEEL_NAMESPACE);
        assertThat(idnPercent.getType().getBaseType().getName()).isEqualTo("number");
        assertThat(idnPercent.getType().isCollection()).isFalse();
        assertThat(idnPercent.getType().isComposite()).isFalse();
        assertThat(idnPercent.getType().getAllowedValues()).hasSize(1);
        assertThat(idnPercent.getType().getAllowedValues().get(0).toString()).isEqualTo("[0..100]");

        final InputDataNode idnCarDamageResponsibility =
                dmnModel.getInputs().stream().filter(idn -> idn.getName().equals("Car Damage Responsibility")).findFirst().get();
        assertThat(idnCarDamageResponsibility.getType().getBaseType()).isNull();
        assertThat(idnCarDamageResponsibility.getType().isCollection()).isFalse();
        assertThat(idnCarDamageResponsibility.getType().isComposite()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("params")
    void inputClauseTypeRefWithAllowedValues(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        final DMNRuntime runtime = DMNRuntimeUtil.createRuntime("actualInputMatchInputValues-forTypeRef.dmn",
                                                                this.getClass());
        final DMNModel dmnModel = runtime.getModel("https://www.drools.org/kie-dmn/definitions", "definitions");
        assertThat(dmnModel).isNotNull();
        assertThat(dmnModel.hasErrors()).as(DMNRuntimeUtil.formatMessages(dmnModel.getMessages())).isFalse();

        final DMNContext context = runtime.newContext();
        context.set("MyInput", "a");

        final DMNResult dmnResult = runtime.evaluateAll(dmnModel, context);

        assertThat(dmnResult.hasErrors()).as(DMNRuntimeUtil.formatMessages(dmnResult.getMessages())).isFalse();
        final DMNContext result = dmnResult.getContext();
        assertThat(result.get("MyDecision")).isEqualTo("Decision taken");
    }

    @ParameterizedTest
    @MethodSource("params")
    void inputDataTypeRefWithAllowedValues(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        final DMNRuntime runtime = DMNRuntimeUtil.createRuntime("actualInputMatchInputValues-forTypeRef.dmn",
                                                                this.getClass());
        final DMNModel dmnModel = runtime.getModel("https://www.drools.org/kie-dmn/definitions", "definitions");
        assertThat(dmnModel).isNotNull();
        assertThat(dmnModel.hasErrors()).as(DMNRuntimeUtil.formatMessages(dmnModel.getMessages())).isFalse();

        final DMNContext context = runtime.newContext();
        context.set("MyInput", "zzz");              // <<< `zzz` is NOT in the list of allowed value as declared by
        // the typeRef for this inputdata

        final DMNResult dmnResult = runtime.evaluateAll(dmnModel, context);
        assertThat(dmnResult.hasErrors()).as(DMNRuntimeUtil.formatMessages(dmnResult.getMessages())).isTrue();
        assertThat(dmnResult.getMessages()).hasSize(1);
        assertThat(dmnResult.getMessages().get(0).getSourceId()).isEqualTo("_3d560678-a126-4654-a686-bc6d941fe40b");
    }

    @ParameterizedTest
    @MethodSource("params")
    void missingInputData(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        final List<DMNMessage> messages = DMNRuntimeUtil.createExpectingDMNMessages("missing_input_data.dmn",
                                                                                    getClass());
        assertThat(messages.get(0).getMessageType()).isEqualTo(DMNMessageType.ERR_COMPILING_FEEL);
    }

    @ParameterizedTest
    @MethodSource("params")
    void ordering(boolean useExecModelCompiler) {
        init(useExecModelCompiler);
        final DMNRuntime runtime = DMNRuntimeUtil.createRuntime("Order.dmn", this.getClass());
        final DMNModel dmnModel = runtime.getModel("http://www.trisotech.com/definitions/_6318588b-c32f-4070-848b" +
                                                           "-bd8017e6b94e", "Drawing 1");

        int index = 1;
        for (InputDataNode node : dmnModel.getInputs()) {
            assertThat(node.getName().endsWith("" + index++)).isTrue();
        }

        index = 1;
        for (DecisionNode node : dmnModel.getDecisions()) {
            assertThat(node.getName().endsWith("" + index++)).isTrue();
        }

        index = 1;
        for (BusinessKnowledgeModelNode node : dmnModel.getBusinessKnowledgeModels()) {
            assertThat(node.getName().endsWith("" + index++)).isTrue();
        }

        index = 1;
        for (ItemDefNode node : dmnModel.getItemDefinitions()) {
            assertThat(node.getName().endsWith("" + index++)).isTrue();
        }

        index = 1;
        for (DecisionServiceNode node : dmnModel.getDecisionServices()) {
            assertThat(node.getName().endsWith("" + index++)).isTrue();
        }
    }
}
