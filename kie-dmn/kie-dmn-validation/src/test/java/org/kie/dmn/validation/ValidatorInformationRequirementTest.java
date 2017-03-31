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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.kie.dmn.validation.DMNValidator.Validation.VALIDATE_COMPILATION;
import static org.kie.dmn.validation.DMNValidator.Validation.VALIDATE_MODEL;
import static org.kie.dmn.validation.DMNValidator.Validation.VALIDATE_SCHEMA;

import java.util.List;
import org.junit.Test;
import org.kie.dmn.api.core.DMNMessage;
import org.kie.dmn.api.core.DMNMessageType;

public class ValidatorInformationRequirementTest extends AbstractValidatorTest {

    @Test
    public void testINFOREQ_MISSING_INPUT() {
        List<DMNMessage> validate = validator.validate(
                getReader( "informationrequirement/INFOREQ_MISSING_INPUT.dmn" ),
                VALIDATE_SCHEMA, VALIDATE_MODEL, VALIDATE_COMPILATION);
        assertThat( ValidatorUtil.formatMessages( validate ), validate.size(), is( 2 ) );
        assertTrue( validate.stream().anyMatch( p -> p.getMessageType().equals( DMNMessageType.MISSING_EXPRESSION ) ) );
        assertTrue( validate.stream().anyMatch( p -> p.getMessageType().equals( DMNMessageType.REQ_NOT_FOUND ) ) );
    }

    @Test
    public void testINFOREQ_INPUT_NOT_INPUTDATA() {
        List<DMNMessage> validate = validator.validate(
                getReader( "informationrequirement/INFOREQ_INPUT_NOT_INPUTDATA.dmn" ),
                VALIDATE_SCHEMA, VALIDATE_MODEL, VALIDATE_COMPILATION);
        assertThat( ValidatorUtil.formatMessages( validate ), validate.size(), is( 2 ) );
        assertTrue( validate.stream().anyMatch( p -> p.getMessageType().equals( DMNMessageType.MISSING_EXPRESSION ) ) );
        assertTrue( validate.stream().anyMatch( p -> p.getMessageType().equals( DMNMessageType.REQ_NOT_FOUND ) ) );
    }

    @Test
    public void testINFOREQ_MISSING_DECISION() {
        List<DMNMessage> validate = validator.validate(
                getReader( "informationrequirement/INFOREQ_MISSING_DECISION.dmn" ),
                VALIDATE_SCHEMA, VALIDATE_MODEL, VALIDATE_COMPILATION);
        assertThat( ValidatorUtil.formatMessages( validate ), validate.size(), is( 2 ) );
        assertTrue( validate.stream().anyMatch( p -> p.getMessageType().equals( DMNMessageType.MISSING_EXPRESSION ) ) );
        assertTrue( validate.stream().anyMatch( p -> p.getMessageType().equals( DMNMessageType.REQ_NOT_FOUND ) ) );
    }

    @Test
    public void testINFOREQ_DECISION_NOT_DECISION() {
        List<DMNMessage> validate = validator.validate(
                getReader( "informationrequirement/INFOREQ_DECISION_NOT_DECISION.dmn" ),
                VALIDATE_SCHEMA, VALIDATE_MODEL, VALIDATE_COMPILATION);
        assertThat( ValidatorUtil.formatMessages( validate ), validate.size(), is( 2 ) );
        assertTrue( validate.stream().anyMatch( p -> p.getMessageType().equals( DMNMessageType.MISSING_EXPRESSION ) ) );
        assertTrue( validate.stream().anyMatch( p -> p.getMessageType().equals( DMNMessageType.REQ_NOT_FOUND ) ) );
    }
}
