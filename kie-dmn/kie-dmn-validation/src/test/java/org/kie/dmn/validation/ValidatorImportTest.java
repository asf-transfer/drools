/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.dmn.validation;

import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;
import org.kie.dmn.api.core.DMNContext;
import org.kie.dmn.api.core.DMNMessage;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.api.core.DMNResult;
import org.kie.dmn.api.core.DMNRuntime;
import org.kie.dmn.core.api.DMNFactory;
import org.kie.dmn.core.util.DMNRuntimeUtil;
import org.kie.dmn.validation.DMNValidator.Validation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.kie.dmn.core.util.DynamicTypeUtils.entry;
import static org.kie.dmn.core.util.DynamicTypeUtils.mapOf;

public class ValidatorImportTest extends AbstractValidatorTest {

    public static final Logger LOG = LoggerFactory.getLogger(ValidatorImportTest.class);

    @Ignore
    @Test
    public void testBaseModel_OK() {
        DMNRuntime runtime = DMNRuntimeUtil.createRuntimeWithAdditionalResources("import/Base-model.dmn", this.getClass(), "import/Import-base-model.dmn");
        DMNModel dmnModel = runtime.getModel("http://www.trisotech.com/dmn/definitions/_b33fa7d9-f501-423b-afa8-15ded7e7f493", "Import base model");
        assertThat(dmnModel, notNullValue());
        assertThat(DMNRuntimeUtil.formatMessages(dmnModel.getMessages()), dmnModel.hasErrors(), is(false));

        DMNContext context = DMNFactory.newContext();
        context.set("Customer", mapOf(entry("full name", "John Doe"), entry("age", 47)));
        DMNResult dmnResult = runtime.evaluateAll(dmnModel, context);
        LOG.debug("{}", dmnResult);
        assertThat(DMNRuntimeUtil.formatMessages(dmnResult.getMessages()), dmnResult.hasErrors(), is(false));
    }

    @Test
    public void testBaseModel_OK__ReaderInput() throws IOException {
        try (final Reader reader0 = getReader("import/Base-model.dmn");
                final Reader reader1 = getReader("import/Import-base-model.dmn");) {
            final List<DMNMessage> messages = validator.validateUsing( // VALIDATE_SCHEMA, disabled, due to QName use not compliant. 
                                                                       Validation.VALIDATE_MODEL,
                                                                       Validation.VALIDATE_COMPILATION)
                                                       .theseModels(reader0, reader1);
            assertThat( messages.toString(), messages.size(), is( 0 ) );
        }
    }

    @Test
    public void testBaseModel_OK__FileInput() throws IOException {
        final List<DMNMessage> messages = validator.validateUsing( // VALIDATE_SCHEMA, disabled, due to QName use not compliant. 
                                                                   Validation.VALIDATE_MODEL,
                                                                   Validation.VALIDATE_COMPILATION)
                                                   .theseModels(getFile("import/Base-model.dmn"),
                                                                getFile("import/Import-base-model.dmn"));
        assertThat(messages.toString(), messages.size(), is(0));
    }

    @Test
    public void testBaseModel_OK__DefinitionsInput() throws IOException {
        final List<DMNMessage> messages = validator.validateUsing( // VALIDATE_SCHEMA, disabled, due to QName use not compliant. 
                                                                   Validation.VALIDATE_MODEL,
                                                                   Validation.VALIDATE_COMPILATION)
                                                   .theseModels(getDefinitions(Arrays.asList("import/Base-model.dmn", "import/Import-base-model.dmn"),
                                                                               "http://www.trisotech.com/definitions/_70df1ad5-2a33-4ede-b8b2-869988ac1d30",
                                                                               "Base model"),
                                                                getDefinitions(Arrays.asList("import/Base-model.dmn", "import/Import-base-model.dmn"),
                                                                               "http://www.trisotech.com/dmn/definitions/_b33fa7d9-f501-423b-afa8-15ded7e7f493",
                                                                               "Import base model"));
        assertThat(messages.toString(), messages.size(), is(0));
    }
}
