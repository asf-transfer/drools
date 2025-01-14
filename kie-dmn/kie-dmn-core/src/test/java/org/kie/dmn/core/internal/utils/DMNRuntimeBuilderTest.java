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
package org.kie.dmn.core.internal.utils;

import java.io.File;
import java.util.Collections;

import org.drools.util.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.api.io.Resource;
import org.kie.dmn.api.core.DMNContext;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.api.core.DMNResult;
import org.kie.dmn.api.core.DMNRuntime;
import org.kie.dmn.core.api.DMNFactory;
import org.kie.dmn.core.impl.DMNRuntimeImpl;
import org.kie.internal.io.ResourceFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class DMNRuntimeBuilderTest {

    private DMNRuntimeBuilder dmnRuntimeBuilder;

    @BeforeEach
    void setup() {
        dmnRuntimeBuilder = DMNRuntimeBuilder.fromDefaults();
        assertThat(dmnRuntimeBuilder).isNotNull();
    }

    @Test
    void buildFromConfiguration() {
        final DMNRuntimeImpl retrieved = (DMNRuntimeImpl) dmnRuntimeBuilder
                .buildConfiguration()
                .fromResources(Collections.emptyList()).getOrElseThrow(RuntimeException::new);
        assertThat(retrieved).isNotNull();
    }

    @Test
    void fromDefaultsMultipleDecisionWithoutInputDataReference() {
        File modelFile = FileUtils.getFile("Invalid_decisions_model.dmn");
        assertThat(modelFile).isNotNull().exists();
        Resource modelResource = ResourceFactory.newFileResource(modelFile);
        DMNRuntime dmnRuntime = DMNRuntimeBuilder.fromDefaults().buildConfiguration()
                .fromResources(Collections.singletonList(modelResource)).getOrElseThrow(RuntimeException::new);
        assertThat(dmnRuntime).isNotNull();
        String nameSpace = "https://kie.org/dmn/_BDC29BCF-B5DC-4AD7-8A5F-43DC08780F97";

        final DMNModel dmnModel = dmnRuntime.getModel(
                nameSpace,
                "DMN_1A4BD262-7672-4887-9F25-986EE5277D16");
        assertThat(dmnModel).isNotNull();
        DMNContext context = DMNFactory.newContext();
        context.set( "Person Age", 24 );
        String errorMessage = "DMN: Error compiling FEEL expression 'Person Age >= 18' for name 'Can Drive?' on node 'Can Drive?': syntax error near 'Age' (DMN id: _563E78C7-EFD1-4109-9F30-B14922EF68DF, Error compiling the referenced FEEL expression) ";
        assertThatIllegalStateException().isThrownBy(() -> {
            dmnRuntime.evaluateAll(dmnModel, context);
        }).withMessage(errorMessage);
    }

    @Test
    void evaluateMultipleDecisionModel() {
        File modelFile = FileUtils.getFile("MultipleDecision.dmn");
        assertThat(modelFile).isNotNull().exists();
        Resource modelResource = ResourceFactory.newFileResource(modelFile);
        DMNRuntime dmnRuntime = DMNRuntimeBuilder.fromDefaults().buildConfiguration()
                .fromResources(Collections.singletonList(modelResource)).getOrElseThrow(RuntimeException::new);
        assertThat(dmnRuntime).isNotNull();
        String nameSpace = "https://kie.org/dmn/_CADD03FC-4ABD-46D2-B631-E7FDE384D6D7";

        final DMNModel dmnModel = dmnRuntime.getModel(
                nameSpace,
                "DMN_54AA2CFA-2374-4FCE-8F16-B594DFF87EBE");
        assertThat(dmnModel).isNotNull();
        DMNContext context = DMNFactory.newContext();
        context.set( "Person Age", 24 );
        DMNResult dmnResult = dmnRuntime.evaluateAll(dmnModel, context);
        assertThat(dmnResult).isNotNull();
    }

    @Test
    void evaluateWrongDecisionWithoutInputDataReferencesByName() {
        File modelFile = FileUtils.getFile("Invalid_decisions_model.dmn");
        assertThat(modelFile).isNotNull().exists();
        Resource modelResource = ResourceFactory.newFileResource(modelFile);
        DMNRuntime dmnRuntime = DMNRuntimeBuilder.fromDefaults().buildConfiguration()
                .fromResources(Collections.singletonList(modelResource)).getOrElseThrow(RuntimeException::new);
        assertThat(dmnRuntime).isNotNull();
        String nameSpace = "https://kie.org/dmn/_BDC29BCF-B5DC-4AD7-8A5F-43DC08780F97";

        final DMNModel dmnModel = dmnRuntime.getModel(
                nameSpace,
                "DMN_1A4BD262-7672-4887-9F25-986EE5277D16");
        assertThat(dmnModel).isNotNull();
        DMNContext context = DMNFactory.newContext();
        context.set( "Person Age", 24 );
        String errorMessage = "DMN: Error compiling FEEL expression 'Person Age >= 18' for name 'Can Drive?' on node 'Can Drive?': syntax error near 'Age' (DMN id: _563E78C7-EFD1-4109-9F30-B14922EF68DF, Error compiling the referenced FEEL expression) ";
        assertThatIllegalStateException().isThrownBy(() -> {
            dmnRuntime.evaluateByName(dmnModel, context, "Can Drive");
        }).withMessage(errorMessage);
    }

    @Test
    void evaluateRightDecisionWithoutInputDataReferencesByName() {
        File modelFile = FileUtils.getFile("Invalid_decisions_model.dmn");
        assertThat(modelFile).isNotNull().exists();
        Resource modelResource = ResourceFactory.newFileResource(modelFile);
        DMNRuntime dmnRuntime = DMNRuntimeBuilder.fromDefaults().buildConfiguration()
                .fromResources(Collections.singletonList(modelResource)).getOrElseThrow(RuntimeException::new);
        assertThat(dmnRuntime).isNotNull();
        String nameSpace = "https://kie.org/dmn/_BDC29BCF-B5DC-4AD7-8A5F-43DC08780F97";

        final DMNModel dmnModel = dmnRuntime.getModel(
                nameSpace,
                "DMN_1A4BD262-7672-4887-9F25-986EE5277D16");
        assertThat(dmnModel).isNotNull();
        DMNContext context = DMNFactory.newContext();
        context.set( "Person Age", 24 );
        DMNResult dmnResult = dmnRuntime.evaluateByName(dmnModel, context, "Can Vote?");
        assertThat(dmnResult).isNotNull();
    }

    @Test
    void evaluateWrongDecisionWithoutInputDataReferencesById() {
        File modelFile = FileUtils.getFile("Invalid_decisions_model.dmn");
        assertThat(modelFile).isNotNull().exists();
        Resource modelResource = ResourceFactory.newFileResource(modelFile);
        DMNRuntime dmnRuntime = DMNRuntimeBuilder.fromDefaults().buildConfiguration()
                .fromResources(Collections.singletonList(modelResource)).getOrElseThrow(RuntimeException::new);
        assertThat(dmnRuntime).isNotNull();
        String nameSpace = "https://kie.org/dmn/_BDC29BCF-B5DC-4AD7-8A5F-43DC08780F97";

        final DMNModel dmnModel = dmnRuntime.getModel(
                nameSpace,
                "DMN_1A4BD262-7672-4887-9F25-986EE5277D16");
        assertThat(dmnModel).isNotNull();
        DMNContext context = DMNFactory.newContext();
        context.set( "Person Age", 24 );
        String errorMessage = "DMN: Error compiling FEEL expression 'Person Age >= 18' for name 'Can Drive?' on node 'Can Drive?': syntax error near 'Age' (DMN id: _563E78C7-EFD1-4109-9F30-B14922EF68DF, Error compiling the referenced FEEL expression) ";
        assertThatIllegalStateException().isThrownBy(() -> {
            dmnRuntime.evaluateById(dmnModel, context, "_563E78C7-EFD1-4109-9F30-B14922EF68DF");
        }).withMessage(errorMessage);
    }

    @Test
    void evaluateRightDecisionWithoutInputDataReferencesById() {
        File modelFile = FileUtils.getFile("Invalid_decisions_model.dmn");
        assertThat(modelFile).isNotNull().exists();
        Resource modelResource = ResourceFactory.newFileResource(modelFile);
        DMNRuntime dmnRuntime = DMNRuntimeBuilder.fromDefaults().buildConfiguration()
                .fromResources(Collections.singletonList(modelResource)).getOrElseThrow(RuntimeException::new);
        assertThat(dmnRuntime).isNotNull();
        String nameSpace = "https://kie.org/dmn/_BDC29BCF-B5DC-4AD7-8A5F-43DC08780F97";

        final DMNModel dmnModel = dmnRuntime.getModel(
                nameSpace,
                "DMN_1A4BD262-7672-4887-9F25-986EE5277D16");
        assertThat(dmnModel).isNotNull();
        DMNContext context = DMNFactory.newContext();
        context.set( "Person Age", 24 );
        DMNResult dmnResult = dmnRuntime.evaluateById(dmnModel, context, "_7ACCB8BC-A382-4530-B8EE-AD32D187FD8B");
        assertThat(dmnResult).isNotNull();
    }

    @Test
    void evaluateDecisionWithInvalidFeelError() {
        File modelFile = FileUtils.getFile("InvalidFeel.dmn");
        assertThat(modelFile).isNotNull().exists();
        Resource modelResource = ResourceFactory.newFileResource(modelFile);
        DMNRuntime dmnRuntime = DMNRuntimeBuilder.fromDefaults().buildConfiguration()
                .fromResources(Collections.singletonList(modelResource)).getOrElseThrow(RuntimeException::new);
        assertThat(dmnRuntime).isNotNull();
        String nameSpace = "https://kie.org/dmn/_9DF86C49-C80A-4744-9F50-BCE65A89C98C";

        final DMNModel dmnModel = dmnRuntime.getModel(
                nameSpace,
                "DMN_33900B8B-73DD-4D1E-87E9-F6C3FE534B43");
        assertThat(dmnModel).isNotNull();
        DMNContext context = DMNFactory.newContext();
        context.set("Person Age", 24);
        String errorMessage = "DMN: Error compiling FEEL expression 'Person Age >?= 18' for name 'Can Drive' on node 'Can Drive': Unknown variable '?' (DMN id: _F477B6E0-C617-4087-9648-DE25A711C5F9, Error compiling the referenced FEEL expression) ";
        assertThatIllegalStateException().isThrownBy(() -> {
            dmnRuntime.evaluateByName(dmnModel, context, "Can Drive");
        }).withMessage(errorMessage);

    }

    @Test
    void evaluateMultipleErrorDecision() {
        File modelFile = FileUtils.getFile("MultipleErrorDecision.dmn");
        assertThat(modelFile).isNotNull().exists();
        Resource modelResource = ResourceFactory.newFileResource(modelFile);
        DMNRuntime dmnRuntime = DMNRuntimeBuilder.fromDefaults().buildConfiguration()
                .fromResources(Collections.singletonList(modelResource)).getOrElseThrow(RuntimeException::new);
        assertThat(dmnRuntime).isNotNull();
        String nameSpace = "https://kie.org/dmn/_36ADF828-4BE5-41E1-8808-6245D13C6AB4";

        final DMNModel dmnModel = dmnRuntime.getModel(
                nameSpace,
                "DMN_45A15AF7-9910-4EAD-B249-8AE218B3BF43");
        assertThat(dmnModel).isNotNull();
        DMNContext context = DMNFactory.newContext();
        context.set( "Person Age", 24 );
        String errorMessage = "DMN: Error compiling FEEL expression 'Age + 20.?>' for name 'ContextEntry-1' on node 'Can Vote?': syntax error near '+' (DMN id: _B7D17199-0568-40EE-94D0-FDFAB0E97868, Error compiling the referenced FEEL expression) , DMN: Error compiling FEEL expression 'if Age > 25 \"YES\" elsesss \"NO\"' for name 'Can Vote?' on node 'Can Vote?': syntax error near '\"YES\"' (DMN id: _59E71393-14B3-405D-A0B4-3C1E6562823F, Error compiling the referenced FEEL expression) ";
        assertThatIllegalStateException().isThrownBy(() -> {
            dmnRuntime.evaluateByName(dmnModel, context, "Can Vote");
        }).withMessage(errorMessage);
    }

    @Test
    void evaluateMultipleErrorModel() {
        File modelFile = FileUtils.getFile("MultipleError.dmn");
        assertThat(modelFile).isNotNull().exists();
        Resource modelResource = ResourceFactory.newFileResource(modelFile);
        DMNRuntime dmnRuntime = DMNRuntimeBuilder.fromDefaults().buildConfiguration()
                .fromResources(Collections.singletonList(modelResource)).getOrElseThrow(RuntimeException::new);
        assertThat(dmnRuntime).isNotNull();
        String nameSpace = "https://kie.org/dmn/_231A34DE-33C6-4787-A51F-228C910D5EAF";

        final DMNModel dmnModel = dmnRuntime.getModel(
                nameSpace,
                "DMN_DC99A8C4-4524-407D-B3D1-577442AED995");
        assertThat(dmnModel).isNotNull();
        DMNContext context = DMNFactory.newContext();
        context.set( "Person Age", 24 );
        String errorMessage = "DMN: Error compiling FEEL expression 'Person Age >= 18' for name 'Can Vote?' on node 'Can Vote?': syntax error near 'Age' (DMN id: _E3EF0CCA-0F1E-42B1-8C65-124D77C07E38, Error compiling the referenced FEEL expression) , DMN: Error compiling FEEL expression 'Person Age >=18' for name 'Can Drive?' on node 'Can Drive?': syntax error near 'Age' (DMN id: _B2F31CDD-29D1-4C20-93B8-8FB8E11E1FFC, Error compiling the referenced FEEL expression) ";
        assertThatIllegalStateException().isThrownBy(() -> {
            dmnRuntime.evaluateByName(dmnModel, context, "Can Vote?", "Can Drive?");
        }).withMessage(errorMessage);
    }

}