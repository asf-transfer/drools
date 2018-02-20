/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
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

package org.kie.dmn.backend.marshalling.v1_1;

import java.io.InputStream;
import java.io.InputStreamReader;

import javax.xml.XMLConstants;

import org.junit.Ignore;
import org.junit.Test;
import org.kie.dmn.api.marshalling.v1_1.DMNMarshaller;
import org.kie.dmn.model.v1_1.Decision;
import org.kie.dmn.model.v1_1.DecisionService;
import org.kie.dmn.model.v1_1.Definitions;
import org.kie.dmn.model.v1_1.InputData;
import org.kie.dmn.model.v1_1.LiteralExpression;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class DMNXMLLoaderTest {

    @Test
    public void testLoadingDefinitions() {
        final DMNMarshaller DMNMarshaller = DMNMarshallerFactory.newDefaultMarshaller();

        final InputStream is = this.getClass().getResourceAsStream( "0001-input-data-string.dmn" );
        final InputStreamReader isr = new InputStreamReader( is );
        final Definitions def = DMNMarshaller.unmarshal( isr );

        assertThat( def, not( nullValue() ) );
        assertThat( def.getName(), is("0001-input-data-string") );
        assertThat( def.getId(), is("_0001-input-data-string") );
        assertThat( def.getNamespace(), is("https://github.com/agilepro/dmn-tck") );

        assertThat( def.getDrgElement().size(), is( 2 ) );
        assertThat( def.getDrgElement().get( 0 ), is( instanceOf( Decision.class ) ) );

        Decision dec = (Decision) def.getDrgElement().get( 0 );
        assertThat( dec.getName(), is("Greeting Message") );
        assertThat( dec.getId(), is("d_GreetingMessage") );
        assertThat( dec.getVariable().getName(), is("Greeting Message") );
        assertThat( dec.getVariable().getTypeRef().getPrefix(), is( "feel" ) );
        assertThat( dec.getVariable().getTypeRef().getLocalPart(), is( "string" ) );
        assertThat( dec.getVariable().getTypeRef().getNamespaceURI(), is( XMLConstants.NULL_NS_URI ) );

        assertThat( dec.getInformationRequirement().size(), is( 1 ) );
        assertThat( dec.getInformationRequirement().get( 0 ).getRequiredInput().getHref(), is( "#i_FullName" ) );
        assertThat( dec.getExpression(), is( instanceOf( LiteralExpression.class ) ) );

        LiteralExpression le = (LiteralExpression) dec.getExpression();
        assertThat( le.getText(), is("\"Hello \" + Full Name") );

        InputData idata = (InputData) def.getDrgElement().get( 1 );
        assertThat( idata.getId(), is( "i_FullName" ) );
        assertThat( idata.getName(), is( "Full Name" ) );
        assertThat( idata.getVariable().getName(), is( "Full Name" ) );
        assertThat( idata.getVariable().getTypeRef().getPrefix(), is( "feel" ) );
        assertThat( idata.getVariable().getTypeRef().getLocalPart(), is( "string" ) );
        assertThat( idata.getVariable().getTypeRef().getNamespaceURI(), is( XMLConstants.NULL_NS_URI ) );
    }

    @Test
    public void testLoadingDecisionServices() {
        final DMNMarshaller DMNMarshaller = DMNMarshallerFactory.newDefaultMarshaller();

        final InputStream is = this.getClass().getResourceAsStream("004-decision-services.dmn");
        final InputStreamReader isr = new InputStreamReader(is);
        final Definitions def = DMNMarshaller.unmarshal(isr);

        assertThat(def.getDecisionService().size(), is(2));

        DecisionService decisionService1 = def.getDecisionService().get(0);
        assertThat(decisionService1.getId(), is("_70386614-9838-420b-a2ae-ff901ada63fb"));
        assertThat(decisionService1.getName(), is("A Only Knowing B and C"));
        assertThat(decisionService1.getOutputDecision().size(), is(1));
        assertThat(decisionService1.getEncapsulatedDecision().size(), is(0));
        assertThat(decisionService1.getInputDecision().size(), is(2));
        assertThat(decisionService1.getInputData().size(), is(0));
        assertThat(decisionService1.getOutputDecision().get(0).getHref(), is("#_c2b44706-d479-4ceb-bb74-73589d26dd04"));

        DecisionService decisionService2 = def.getDecisionService().get(1);
        assertThat(decisionService2.getId(), is("_4620ef13-248a-419e-bc68-6b601b725a03"));
        assertThat(decisionService2.getName(), is("A only as output knowing D and E"));
        assertThat(decisionService2.getOutputDecision().size(), is(1));
        assertThat(decisionService2.getEncapsulatedDecision().size(), is(2));
        assertThat(decisionService2.getInputDecision().size(), is(0));
        assertThat(decisionService2.getInputData().size(), is(2));
        assertThat(decisionService2.getInputData().get(0).getHref(), is("#_bcea16fb-6c19-4bde-b37d-73407002c064"));
        assertThat(decisionService2.getInputData().get(1).getHref(), is("#_207b9195-a441-47f2-9414-2fad64b463f9"));

    }

    @Test
    @Ignore("No unmarshaller implemented")
    public void testLoadingExample() {
        final DMNMarshaller DMNMarshaller = DMNMarshallerFactory.newDefaultMarshaller();

        final InputStream is = this.getClass().getResourceAsStream( "/src/test/resources/ch11example.xml" );
        final InputStreamReader isr = new InputStreamReader( is );
        final Object o = DMNMarshaller.unmarshal( isr );

        final Definitions root = (Definitions) o;

        assertNotNull( root );
    }

    @Test
    @Ignore("No unmarshaller implemented")
    public void testLoadingDishDecision() {
        final DMNMarshaller DMNMarshaller = DMNMarshallerFactory.newDefaultMarshaller();

        final InputStream is = this.getClass().getResourceAsStream( "/src/test/resources/dish-decision.xml" );
        final InputStreamReader isr = new InputStreamReader( is );
        final Object o = DMNMarshaller.unmarshal( isr );

        final Definitions root = (Definitions) o;

        assertNotNull( root );
    }

}
