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
package org.drools.serialization.protobuf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.io.StringReader;

import org.drools.core.impl.RuleBaseFactory;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.kiesession.rulebase.KnowledgeBaseFactory;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.KieServices;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.io.ResourceType;
import org.kie.api.marshalling.Marshaller;
import org.kie.api.runtime.KieSession;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.marshalling.MarshallerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class UnmarshallingTest {

    @Test
    public void testMarshallWithNot() throws Exception {
        String whenBenNotVilgaxRule =
                "import " + getClass().getCanonicalName() + ".*\n" +
                        "rule one\n" +
                        "when\n" +
                        "   Ben()\n" +
                        "   not(Vilgax())\n" +
                        "then\n" +
                        "   //System.out.println(\"Ben!\");\n" +
                        "end\n" +
                        "\n" +
                        "rule two\n" +
                        "when\n" +
                        "   Ben()\n" +
                        "then\n" +
                        "   //System.out.println(\"Vilgax..\");\n" +
                        "end\n";

        KieBase knowledgeBase = initializeKnowledgeBase( whenBenNotVilgaxRule );

        // Initialize Knowledge session and insert Ben
        KieSession ksession = knowledgeBase.newKieSession();
        ksession.insert( new Ben() );

        // Marshall
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MarshallerFactory.newMarshaller( knowledgeBase ).marshall( baos,
                                                                   ksession );

        // Clean up
        //  - mimicing when a session is reloaded from a database.
        ksession.dispose();

        // Re-initialize 
        knowledgeBase = initializeKnowledgeBase( whenBenNotVilgaxRule );

        // Unmarshall
        ByteArrayInputStream bais = new ByteArrayInputStream( baos.toByteArray() );
        try {
            ksession = MarshallerFactory.newMarshaller( knowledgeBase ).unmarshall( bais );
        } catch ( Throwable t ) {
            t.printStackTrace();
            fail( t.getClass().getSimpleName() + " thrown when trying to unmarshall (see stack trace in output)." );
        }
        int rules = ksession.fireAllRules();
        assertThat(rules).isEqualTo(2);
    }

    private KieBase initializeKnowledgeBase( String rule ) {
        // Setup knowledge base
        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add( ResourceFactory.newReaderResource(new StringReader(rule)),
                      ResourceType.DRL );
        if ( kbuilder.hasErrors() ) {
            throw new RuntimeException( kbuilder.getErrors().toString() );
        }
        KieBaseConfiguration config = RuleBaseFactory.newKnowledgeBaseConfiguration();
        config.setOption( EventProcessingOption.STREAM );
        InternalKnowledgeBase knowledgeBase = KnowledgeBaseFactory.newKnowledgeBase(RuleBaseFactory.newRuleBase(config));
        knowledgeBase.addPackages( kbuilder.getKnowledgePackages() );

        return knowledgeBase;
    }

    public static class Ben
        implements
        Serializable {
        private static final long serialVersionUID = 9127145048523653863L;

        @Override
        public String toString() {
            return "Ben[]";
        }

    }

    public static class Vilgax
        implements
        Serializable {
        private static final long serialVersionUID = 5337858943537739516L;

        @Override
        public String toString() {
            return "Vilgax[]";
        }
    }

    @Test
    public void testMarshallWithTimer() throws Exception {
        // DROOLS-2210
        String drl =
                "declare String @role(event) end\n" +
                "\n" +
                "rule R1 when\n" +
                "        $s : String( ) over window:time( 5s )\n" +
                "    then\n" +
                "        delete( $s );\n" +
                "end\n";

        KieBase kBase = initializeKnowledgeBase( drl );

        KieSession ksession = kBase.newKieSession();
        ksession.insert( "test" );
        assertThat(ksession.fireAllRules()).isEqualTo(1);

        Marshaller marshaller = KieServices.get().getMarshallers().newMarshaller(kBase);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        marshaller.marshall( baos, ksession );

        ksession.dispose();

        ByteArrayInputStream bais = new ByteArrayInputStream( baos.toByteArray() );
        ksession = marshaller.unmarshall( bais );
        assertThat(ksession.fireAllRules()).isEqualTo(0);
    }
}
