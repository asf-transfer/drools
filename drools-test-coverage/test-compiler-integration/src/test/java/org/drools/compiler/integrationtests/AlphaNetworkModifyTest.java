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
package org.drools.compiler.integrationtests;

import java.util.Collection;
import java.util.List;

import org.drools.base.base.ClassObjectType;
import org.drools.core.impl.InternalRuleBase;
import org.drools.core.reteoo.LeftInputAdapterNode;
import org.drools.core.reteoo.LeftTupleSink;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.testcoverage.common.model.Cheese;
import org.drools.testcoverage.common.model.Person;
import org.drools.testcoverage.common.model.Pet;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.TestParametersUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class AlphaNetworkModifyTest {

    private final KieBaseTestConfiguration kieBaseTestConfiguration;

    public AlphaNetworkModifyTest(final KieBaseTestConfiguration kieBaseTestConfiguration) {
        this.kieBaseTestConfiguration = kieBaseTestConfiguration;
    }

    @Parameterized.Parameters(name = "KieBase type={0}")
    public static Collection<Object[]> getParameters() {
        return TestParametersUtil.getKieBaseCloudConfigurations(true);
    }

    private ObjectTypeNode getObjectTypeNode(final KieBase kbase, final String nodeName) {
        final List<ObjectTypeNode> nodes = ((InternalRuleBase)kbase).getRete().getObjectTypeNodes();
        for ( final ObjectTypeNode n : nodes ) {
            if ( ((ClassObjectType)n.getObjectType()).getClassType().getSimpleName().equals( nodeName ) ) {
                return n;
            }
        }
        return null;
    }
    
    @Test
    public void testModifyWithLiaToEval() {
        final String drl =
            "package org.simple \n" +
            "import " + Person.class.getCanonicalName() + "\n" +
            "import " + Cheese.class.getCanonicalName() + "\n" +
            "import " + Pet.class.getCanonicalName() + "\n" +
            "global java.util.List list \n" +
            "rule x1 \n" +
            "when \n" +
            "    $pe : Person() \n" +
            "    $ch : Cheese() \n" +
            "    $ca : Pet() \n" +
            "then \n" +
            "end  \n" +
            "rule x2 \n" +
            "when \n" +
            "    $ch : Cheese() \n" +
            "    $ca : Pet() \n" +
            "    $pe : Person() \n" +
            "then \n" +
            "end  \n" +
            "rule x3 \n" +
            "when \n" +
            "    $ch : Cheese() \n" +
            "then \n" +
            "end  \n" +
            "rule x4 \n" +
            "when \n" +
            "    $ch : Cheese() \n" +
            "    eval( $ch != null ) \n" +
            "then \n" +
            "end  \n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("alpha-network-modify-test", kieBaseTestConfiguration, drl);
        final KieSession wm = kbase.newKieSession();
        try {
            final ObjectTypeNode otnPerson = getObjectTypeNode(kbase, "Person" );
            final ObjectTypeNode otnCheese = getObjectTypeNode(kbase, "Cheese" );
            final ObjectTypeNode otnPet = getObjectTypeNode(kbase, "Pet" );

            assertThat(otnPerson).isNotNull();
            assertThat(otnCheese).isNotNull();
            assertThat(otnPet).isNotNull();

            assertThat(otnPerson.getOtnIdCounter()).isEqualTo(0);
            assertThat(otnCheese.getOtnIdCounter()).isEqualTo(0);
            assertThat(otnPet.getOtnIdCounter()).isEqualTo(0);
            wm.insert( new Person() );
            wm.insert( new Pet("yyy") );
            wm.insert( new Cheese() );
            wm.fireAllRules();

            assertThat(otnPerson.getOtnIdCounter()).isEqualTo(2);
            assertThat(otnCheese.getOtnIdCounter()).isEqualTo(4);
            assertThat(otnPet.getOtnIdCounter()).isEqualTo(2);
        } finally {
            wm.dispose();
        }
    }    
    
    @Test
    public void testModifyWithLiaToFrom() {
        // technically you can't have a modify with InitialFactImpl
        // But added test for completeness
        
        final String drl =
            "package org.simple \n" +
            "import " + Person.class.getCanonicalName() + "\n" +
            "import " + Cheese.class.getCanonicalName() + "\n" +
            "import " + Pet.class.getCanonicalName() + "\n" +
            "global java.util.List list \n" +
            "rule x1 \n" +
            "when \n" +
            "    $pe : Person() from list\n" +
            "then \n" +
            "end  \n" +
            "rule x2 \n" +
            "when \n" +
            "    $ch : Cheese() from list\n" +
            "then \n" +
            "end  \n" +
            "rule x3 \n" +
            "when \n" +
            "    $ch : Cheese() from list\n" +
            "then \n" +
            "end  \n" +
            "rule x4 \n" +
            "when \n" +
            "    $ch : Cheese() from list\n" +
            "then \n" +
            "end  \n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("alpha-network-modify-test", kieBaseTestConfiguration, drl);
        final KieSession wm = kbase.newKieSession();
        try {
            wm.fireAllRules();

            final ObjectTypeNode otnInit = getObjectTypeNode(kbase, "InitialFactImpl" );
            assertThat(otnInit).isNotNull();
            final LeftInputAdapterNode liaNode = ( LeftInputAdapterNode ) otnInit.getObjectSinkPropagator().getSinks()[0];

            final LeftTupleSink[] sinks = liaNode.getSinkPropagator().getSinks();

            assertThat(sinks.length).isEqualTo(2);
            assertThat(sinks[0].getInputOtnId().getId()).isEqualTo(0);
            assertThat(sinks[1].getInputOtnId().getId()).isEqualTo(1);
        } finally {
            wm.dispose();
        }
    }
    
    @Test
    public void testModifyWithLiaToAcc() {
        // technically you can't have a modify with InitialFactImpl
        // But added test for completeness
        
        final String drl =
            "package org.simple \n" +
            "import " + Person.class.getCanonicalName() + "\n" +
            "import " + Cheese.class.getCanonicalName() + "\n" +
            "import " + Pet.class.getCanonicalName() + "\n" +
            "global java.util.List list \n" +
            "rule x1 \n" +
            "when \n" +
            "    Object() from accumulate( $p : Person() and Cheese(), collectList( $p ) )\n" +
            "    Person() \n" +
            "then \n" +
            "end  \n" +
            "rule x2 \n" +
            "when \n" +
            "    Object() from accumulate( $ch : Cheese(), collectList( $ch ) )\n" +
            "    Person() \n" +
            "then \n" +
            "end  \n" +
            "rule x3 \n" +
            "when \n" +
            "    Object() from accumulate( $ch : Cheese(), collectList( $ch ) )\n" +
            "    Person() \n" +
            "then \n" +
            "end  \n" +
            "rule x4 \n" +
            "when \n" +
            "    Object() from accumulate( $ch : Cheese(), collectList( $ch ) )\n" +
            "    Person() \n" +
            "then \n" +
            "end  \n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("alpha-network-modify-test", kieBaseTestConfiguration, drl);
        final KieSession wm = kbase.newKieSession();
        try {
            wm.fireAllRules();


            final ObjectTypeNode otnInit = getObjectTypeNode(kbase, "InitialFactImpl" );
            assertThat(otnInit).isNotNull();
            final LeftInputAdapterNode liaNode = ( LeftInputAdapterNode ) otnInit.getObjectSinkPropagator().getSinks()[0];

            final LeftTupleSink[] sinks = liaNode.getSinkPropagator().getSinks();

            assertThat(sinks[0].getInputOtnId().getId()).isEqualTo(0);
            assertThat(sinks[1].getInputOtnId().getId()).isEqualTo(1);
            assertThat(sinks[2].getInputOtnId().getId()).isEqualTo(2);

            final ObjectTypeNode otnPerson = getObjectTypeNode(kbase, "Person" );
            final ObjectTypeNode otnCheese = getObjectTypeNode(kbase, "Cheese" );
            assertThat(otnPerson).isNotNull();
            assertThat(otnCheese).isNotNull();
            assertThat(otnPerson.getOtnIdCounter()).isEqualTo(0);
            assertThat(otnCheese.getOtnIdCounter()).isEqualTo(0);
            wm.insert( new Person() );
            wm.insert( new Cheese() );
            wm.fireAllRules();

            assertThat(otnPerson.getOtnIdCounter()).isEqualTo(3);
            assertThat(otnCheese.getOtnIdCounter()).isEqualTo(2);
        } finally {
            wm.dispose();
        }
    }       

}
