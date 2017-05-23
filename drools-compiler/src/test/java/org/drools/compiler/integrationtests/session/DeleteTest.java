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

package org.drools.compiler.integrationtests.session;

import java.util.ArrayList;
import java.util.List;
import org.drools.compiler.Cheese;
import org.drools.compiler.CommonTestMethodBase;
import org.drools.compiler.Person;
import org.drools.compiler.PersonInterface;
import org.drools.compiler.integrationtests.MiscTest;
import org.drools.compiler.integrationtests.SerializationHelper;
import org.junit.Test;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteTest extends CommonTestMethodBase {

    private static Logger logger = LoggerFactory.getLogger(DeleteTest.class);

    @Test
    public void testRetractLeftTuple() throws Exception {
        // JBRULES-3420
        final String str = "import " + MiscTest.class.getName() + ".*\n" +
                "rule R1 salience 3\n" +
                "when\n" +
                "   $b : InterfaceB( )\n" +
                "   $a : ClassA( b == null )\n" +
                "then\n" +
                "   $a.setB( $b );\n" +
                "   update( $a );\n" +
                "end\n" +
                "rule R2 salience 2\n" +
                "when\n" +
                "   $b : ClassB( id == \"123\" )\n" +
                "   $a : ClassA( b != null && b.id == $b.id )\n" +
                "then\n" +
                "   $b.setId( \"456\" );\n" +
                "   update( $b );\n" +
                "end\n" +
                "rule R3 salience 1\n" +
                "when\n" +
                "   InterfaceA( $b : b )\n" +
                "then\n" +
                "   delete( $b );\n" +
                "end\n";

        final KieBase kbase = loadKnowledgeBaseFromString(str);
        final KieSession ksession = kbase.newKieSession();

        ksession.insert(new MiscTest.ClassA());
        ksession.insert(new MiscTest.ClassB());
        assertEquals(3, ksession.fireAllRules());
    }

    @Test
    public void testAssertRetract() throws Exception {
        // postponed while I sort out KnowledgeHelperFixer
        final KieBase kbase = loadKnowledgeBase("assert_retract.drl");
        final KieSession ksession = kbase.newKieSession();

        final List list = new ArrayList();
        ksession.setGlobal("list", list);

        final PersonInterface person = new Person("michael", "cheese");
        person.setStatus("start");
        ksession.insert(person);

        ksession.fireAllRules();

        final List<String> results = (List<String>) ksession.getGlobal("list");
        for (final String result : results) {
            logger.info(result);
        }
        assertEquals(5, results.size());
        assertTrue(results.contains("first"));
        assertTrue(results.contains("second"));
        assertTrue(results.contains("third"));
        assertTrue(results.contains("fourth"));
        assertTrue(results.contains("fifth"));
    }

    @Test
    public void testEmptyAfterRetractInIndexedMemory() {
        String str = "";
        str += "package org.simple \n";
        str += "import org.drools.compiler.Person\n";
        str += "global java.util.List list \n";
        str += "rule xxx dialect 'mvel' \n";
        str += "when \n";
        str += "  Person( $name : name ) \n";
        str += "  $s : String( this == $name) \n";
        str += "then \n";
        str += "  list.add($s); \n";
        str += "end  \n";

        final KieBase kbase = loadKnowledgeBaseFromString(str);
        final KieSession ksession = createKnowledgeSession(kbase);
        final List list = new ArrayList();
        ksession.setGlobal("list", list);

        final Person p = new Person("ackbar");
        ksession.insert(p);
        ksession.insert("ackbar");
        ksession.fireAllRules();
        ksession.dispose();

        assertEquals(1, list.size());
        assertEquals("ackbar", list.get(0));
    }

    @Test
    public void testModifyRetractAndModifyInsert() throws Exception {
        final KieBase kbase = SerializationHelper.serializeObject( loadKnowledgeBase( "test_ModifyRetractInsert.drl" ) );
        final KieSession ksession = createKnowledgeSession( kbase );

        final List list = new ArrayList();
        ksession.setGlobal("results", list);

        final Person bob = new Person("Bob");
        bob.setStatus("hungry");
        ksession.insert(bob);
        ksession.insert(new Cheese());
        ksession.insert(new Cheese());

        ksession.fireAllRules(2);

        assertEquals("should have fired only once", 1, list.size());
    }

    @Test
    public void testModifyRetractWithFunction() throws Exception {
        final KieBase kbase = SerializationHelper.serializeObject(loadKnowledgeBase("test_RetractModifyWithFunction.drl"));
        final KieSession ksession = createKnowledgeSession(kbase);

        final Cheese stilton = new Cheese("stilton", 7);
        final Cheese muzzarella = new Cheese("muzzarella", 9);
        final int sum = stilton.getPrice() + muzzarella.getPrice();
        final FactHandle stiltonHandle = ksession.insert(stilton);
        final FactHandle muzzarellaHandle = ksession.insert(muzzarella);

        ksession.fireAllRules();

        assertEquals(sum, stilton.getPrice());
        assertEquals(1, ksession.getFactCount());
        assertNotNull(ksession.getObject(stiltonHandle));
        assertNotNull(ksession.getFactHandle(stilton));

        assertNull(ksession.getObject(muzzarellaHandle));
        assertNull(ksession.getFactHandle(muzzarella));
    }
}