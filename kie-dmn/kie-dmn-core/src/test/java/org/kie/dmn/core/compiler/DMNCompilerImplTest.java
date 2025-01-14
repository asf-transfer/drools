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
package org.kie.dmn.core.compiler;

import java.io.StringReader;
import java.util.Collections;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kie.dmn.api.core.DMNCompilerConfiguration;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.core.api.DMNFactory;
import org.kie.dmn.core.impl.DMNModelImpl;
import org.kie.dmn.model.api.DMNElementReference;
import org.kie.dmn.model.api.Definitions;
import org.kie.dmn.model.api.InformationRequirement;
import org.kie.dmn.model.v1_5.TDMNElementReference;
import org.kie.dmn.model.v1_5.TDefinitions;
import org.kie.dmn.model.v1_5.TInformationRequirement;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DMNCompilerImplTest {

    private static final String nameSpace = "http://www.montera.com.au/spec/DMN/local-hrefs";
    private static Definitions parent;
    private static DMNCompilerImpl dmnCompiler;
    private static DMNCompilerImpl.AfterProcessDrgElements mockCallback;
    private static DMNCompilerImpl.AfterProcessDrgElements mockCallbackForModel;

    @BeforeAll
    static void setup() {
        String modelName = "LocalHrefs";
        parent = new TDefinitions();
        parent.setName(modelName);
        parent.setNamespace(nameSpace);

        DMNCompilerConfiguration config = DMNFactory.newCompilerConfiguration();
        dmnCompiler = new DMNCompilerImpl(config);
        mockCallback = Mockito.mock(DMNCompilerImpl.AfterProcessDrgElements.class);
        dmnCompiler.addCallback(mockCallback);
        mockCallbackForModel = Mockito.mock(DMNCompilerImpl.AfterProcessDrgElements.class);
    }

    @Test
    void getId() {
        String localPart = "reference";
        DMNElementReference elementReference = new TDMNElementReference();
        elementReference.setHref(String.format("%s#%s", nameSpace, localPart));
        elementReference.setParent(parent);
        String retrieved = DMNCompilerImpl.getId(elementReference);
        assertThat(retrieved).isNotNull().isEqualTo(localPart);

        String expected = String.format("%s#%s", "http://a-different-namespace", localPart);
        elementReference.setHref(expected);
        retrieved = DMNCompilerImpl.getId(elementReference);
        assertThat(retrieved).isNotNull().isEqualTo(expected);
    }

    @Test
    void getRootElement() {
        String localPart = "reference";
        DMNElementReference elementReference = new TDMNElementReference();
        String href = String.format("%s#%s", nameSpace, localPart);
        elementReference.setHref(href);
        elementReference.setParent(parent);
        Definitions retrieved = DMNCompilerImpl.getRootElement(elementReference);
        assertThat(retrieved).isNotNull().isEqualTo(parent);

        InformationRequirement informationRequirement = new TInformationRequirement();
        elementReference.setParent(informationRequirement);
        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> DMNCompilerImpl.getRootElement(elementReference));

        informationRequirement.setParent(parent);
        retrieved = DMNCompilerImpl.getRootElement(elementReference);
        assertThat(retrieved).isNotNull().isEqualTo(parent);
    }

    @Test
    void testCompileWithCallback() {
        String dmnXml = """
                <definitions xmlns="http://www.omg.org/spec/DMN/20151101/dmn.xsd" id="definitions" name="definitions" namespace="http://www.trisotech.com/definitions/_f52ca843-504b-4c3b-a6bc-4d377bffef7a">
                  <decision id="decision" name="Decision">
                    <variable id="variable" name="Decision" typeRef="string"/>
                  </decision>
                </definitions>""";
        DMNModel mockModel = mock(DMNModel.class);
        dmnCompiler.addCallbackForModel(mockCallbackForModel, mockModel);


        Definitions definitions = dmnCompiler.getMarshaller().unmarshal(new StringReader(dmnXml));
        DMNModel model = dmnCompiler.compile(definitions, Collections.emptyList());
        dmnCompiler.addCallbackForModel(mockCallbackForModel, model);
        dmnCompiler.compile(definitions, Collections.emptyList());


        assertThat(model).isNotNull();
        verify(mockCallback, times(2)).callback(eq(dmnCompiler), any(), any(DMNModelImpl.class));
        verify(mockCallbackForModel, never()).callback(any(), any(), any());
    }
}