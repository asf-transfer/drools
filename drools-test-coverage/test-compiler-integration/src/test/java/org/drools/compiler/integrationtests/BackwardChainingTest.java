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

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.drools.compiler.integrationtests.incrementalcompilation.TestUtil;
import org.drools.base.InitialFact;
import org.drools.base.base.ClassObjectType;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.impl.InternalRuleBase;
import org.drools.core.reteoo.AccumulateNode;
import org.drools.core.reteoo.AccumulateRight;
import org.drools.core.reteoo.BetaMemory;
import org.drools.core.reteoo.BetaNode;
import org.drools.core.reteoo.ExistsNode;
import org.drools.core.reteoo.ExistsRight;
import org.drools.core.reteoo.FromNode;
import org.drools.core.reteoo.JoinRightAdapterNode;
import org.drools.core.reteoo.NotNode;
import org.drools.core.reteoo.NotRight;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.QueryElementNode;
import org.drools.core.reteoo.TupleToObjectNode;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.kiesession.session.StatefulKnowledgeSessionImpl;
import org.drools.testcoverage.common.model.Address;
import org.drools.testcoverage.common.model.Person;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.KieSessionTestConfiguration;
import org.drools.testcoverage.common.util.KieUtil;
import org.drools.testcoverage.common.util.SerializationHelper;
import org.drools.testcoverage.common.util.TestParametersUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieModule;
import org.kie.api.builder.ReleaseId;
import org.kie.api.definition.KiePackage;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.LiveQuery;
import org.kie.api.runtime.rule.QueryResults;
import org.kie.api.runtime.rule.QueryResultsRow;
import org.kie.api.runtime.rule.Row;
import org.kie.api.runtime.rule.Variable;
import org.kie.api.runtime.rule.ViewChangedEventListener;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.conf.PropertySpecificOption;
import org.kie.internal.io.ResourceFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.kie.api.runtime.rule.Variable.v;

@RunWith(Parameterized.class)
public class BackwardChainingTest extends AbstractBackwardChainingTest {

    public BackwardChainingTest(final KieBaseTestConfiguration kieBaseTestConfiguration) {
        super(kieBaseTestConfiguration);
    }

    @Parameterized.Parameters(name = "KieBase type={0}")
    public static Collection<Object[]> getParameters() {
        return TestParametersUtil.getKieBaseCloudConfigurations(true);
    }

    @Test(timeout = 10000)
    public void testQueryPatternBindingAsResult() throws IOException, ClassNotFoundException {
        String str = "" +
                     "package org.drools.compiler.test  \n" +
                     "import " + Person.class.getCanonicalName() + "\n" +
                     "global java.util.List list\n" +
                     "query peeps( Person $p, String $name, String $likes, int $age ) \n" +
                     "    $p := Person( $name := name, $likes := likes, $age := age; ) \n" +
                     "end\n";

        str += "rule x1\n" +
               "when\n" +
               "    String( this == \"go1\" )\n" +
               //     output, output, output          ,output
               "    ?peeps($p, $name1; $likes1 : $likes, $age1 : $age )\n" +
               "then\n" +
               "   list.add( $p );\n" +
               "   list.add( $name1 + \" : \" + $age1 );\n" +
               "end \n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("backward-chaining-test", kieBaseTestConfiguration, str);
        KieSession ksession = kbase.newKieSession();
        try {
            final List list = new ArrayList<>();
            ksession.setGlobal("list", list);

            final Person p1 = new Person("darth",
                                         "stilton",
                                         100);
            final Person p2 = new Person("darth",
                                         "stilton",
                                         200);
            final Person p3 = new Person("yoda",
                                         "stilton",
                                         300);
            final Person p4 = new Person("luke",
                                         "brie",
                                         300);
            final Person p5 = new Person("bobba",
                                         "cheddar",
                                         300);

            ksession.insert(p1);
            ksession.insert(p2);
            ksession.insert(p3);
            ksession.insert(p4);
            ksession.insert(p5);

            ksession.insert("go1");
            ksession = SerializationHelper.getSerialisedStatefulKnowledgeSession(ksession, true);
            ksession.fireAllRules();
            if (kieBaseTestConfiguration.isIdentity()) {
                assertThat(list.size()).isEqualTo(10);
                assertThat(list.get(list.indexOf("darth : 100") - 1)).isEqualTo(p1);
                assertThat(list.contains("darth : 100")).isTrue();
                assertThat(list.get(list.indexOf("darth : 200") - 1)).isEqualTo(p2);
                assertThat(list.contains("darth : 200")).isTrue();
                assertThat(list.get(list.indexOf("yoda : 300") - 1)).isEqualTo(p3);
                assertThat(list.contains("yoda : 300")).isTrue();
                assertThat(list.get(list.indexOf("luke : 300") - 1)).isEqualTo(p4);
                assertThat(list.contains("luke : 300")).isTrue();
                assertThat(list.get(list.indexOf("bobba : 300") - 1)).isEqualTo(p5);
                assertThat(list.contains("bobba : 300")).isTrue();
            } else {
                assertThat(list.size()).isEqualTo(8);
                assertThat(list.get(list.indexOf("darth : 100") - 1)).isEqualTo(p1);
                assertThat(list.contains("darth : 100")).isTrue();
                assertThat(list.get(list.indexOf("yoda : 300") - 1)).isEqualTo(p3);
                assertThat(list.contains("yoda : 300")).isTrue();
                assertThat(list.get(list.indexOf("luke : 300") - 1)).isEqualTo(p4);
                assertThat(list.contains("luke : 300")).isTrue();
                assertThat(list.get(list.indexOf("bobba : 300") - 1)).isEqualTo(p5);
                assertThat(list.contains("bobba : 300")).isTrue();
            }
        } finally {
            ksession.dispose();
        }
    }

    @Test(timeout = 10000)
    public void testQueriesWithNestedAccessorsAllOutputs() throws IOException, ClassNotFoundException {
        String drl = "" +
                "package org.drools.compiler.test  \n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "global java.util.List list\n" +
                "query peeps( String $name, String $likes, String $street ) \n" +
                "   Person( $name := name, $likes := likes, $street := address.street ) \n" +
                "end\n";

        drl += "rule x1\n" +
                "when\n" +
                "    String( this == \"go1\" )\n" +
                //         output, output,         ,output
                "    ?peeps($name1; $likes1 : $likes, $street1 : $street )\n" +
                "then\n" +
                "   list.add( $name1 + \" : \" + $likes1 + \" : \" +  $street1 );\n" +
                "end \n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("backward-chaining-test", kieBaseTestConfiguration, drl);
        KieSession ksession = kbase.newKieSession();
        try {
            final List<String> list = new ArrayList<>();
            ksession.setGlobal("list", list);

            final Person p1 = new Person("darth",
                                         "stilton",
                                         100);
            p1.setAddress(new Address("s1"));

            final Person p2 = new Person("yoda",
                                         "stilton",
                                         300);
            p2.setAddress(new Address("s2"));

            ksession.insert(p1);
            ksession.insert(p2);

            ksession.insert("go1");
            ksession = SerializationHelper.getSerialisedStatefulKnowledgeSession(ksession, true);
            ksession.fireAllRules();
            assertThat(list.size()).isEqualTo(2);
            assertThat(list.contains("darth : stilton : s1")).isTrue();
            assertThat(list.contains("yoda : stilton : s2")).isTrue();
        } finally {
            ksession.dispose();
        }
    }

    @Test(timeout = 10000)
    public void testQueriesWithNestedAcecssorsMixedArgs() {
        String drl = "" +
                "package org.drools.compiler.test  \n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "global java.util.List list\n" +
                "query peeps( String $name, String $likes, String $street ) \n" +
                "   Person( $name := name, $likes := likes, $street := address.street ) \n" +
                "end\n";

        drl += "rule x1\n" +
                "when\n" +
                "    $s : String()\n" +
                //         output, output,         ,input
                "    ?peeps($name1; $likes1 : $likes, $street : $s )\n" +
                "then\n" +
                "   list.add( $name1 + \" : \" + $likes1 + \" : \" +  $s );\n" +
                "end \n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("backward-chaining-test", kieBaseTestConfiguration, drl);
        final KieSession ksession = kbase.newKieSession();
        try {
            final List<String> list = new ArrayList<>();
            ksession.setGlobal("list", list);

            final Person p1 = new Person("darth",
                                         "stilton",
                                         100);
            p1.setAddress(new Address("s1"));

            final Person p2 = new Person("yoda",
                                         "stilton",
                                         300);
            p2.setAddress(new Address("s2"));

            ksession.insert(p1);
            ksession.insert(p2);

            ksession.insert("s1");
            ksession.fireAllRules();
            assertThat(list.size()).isEqualTo(1);
            assertThat(list.contains("darth : stilton : s1")).isTrue();

            list.clear();
            ksession.insert("s2");
            ksession.fireAllRules();
            assertThat(list.size()).isEqualTo(1);
            assertThat(list.contains("yoda : stilton : s2")).isTrue();
        } finally {
            ksession.dispose();
        }
    }

    @Test(timeout = 10000)
    public void testQueryWithDynamicData() throws IOException, ClassNotFoundException {
        String drl = "" +
                "package org.drools.compiler.test  \n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "global java.util.List list\n" +
                "query peeps( Person $p, String $name, String $likes, int $age ) \n" +
                "    $p := Person( ) from new Person( $name, $likes, $age ) \n" +
                "end\n";

        drl += "rule x1\n" +
                "when\n" +
                "    $n1 : String( )\n" +
                //     output, input     ,input                 ,input
                "    ?peeps($p; $name : $n1, $likes : \"stilton\", $age : 100 )\n" +
                "then\n" +
                "   list.add( $p );\n" +
                "end \n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("backward-chaining-test", kieBaseTestConfiguration, drl);
        KieSession ksession = kbase.newKieSession();
        try {
            final List<Person> list = new ArrayList<>();
            ksession.setGlobal("list", list);

            final Person p1 = new Person("darth",
                                         "stilton",
                                         100);

            final Person p2 = new Person("yoda",
                                         "stilton",
                                         100);

            ksession.insert("darth");
            ksession = SerializationHelper.getSerialisedStatefulKnowledgeSession(ksession, true);
            ksession.fireAllRules();
            assertThat(list.size()).isEqualTo(1);
            assertThat(list.get(0)).isEqualTo(p1);

            list.clear();
            ksession = SerializationHelper.getSerialisedStatefulKnowledgeSession(ksession, true);
            ksession.insert("yoda");
            ksession = SerializationHelper.getSerialisedStatefulKnowledgeSession(ksession, true);
            ksession.fireAllRules();
            System.out.println(list);
            assertThat(list.size()).isEqualTo(1);
            assertThat(list.get(0)).isEqualTo(p2);
        } finally {
            ksession.dispose();
        }
    }

    @Test(timeout = 10000)
    public void testQueryWithDyanmicInsert() throws IOException, ClassNotFoundException {
        String drl = "" +
                "package org.drools.compiler.test  \n" +
                "import " + Person.class.getCanonicalName() + "\n" +
                "global java.util.List list\n" +
                "query peeps( Person $p, String $name, String $likes, int $age ) \n" +
                "    $p := Person( ) from new Person( $name, $likes, $age ) \n" +
                "end\n";

        drl += "rule x1\n" +
                "when\n" +
                "    $n1 : String( )\n" +
                "    not Person( name == 'darth' )\n " +
                //     output, input     ,input                 ,input
                "    ?peeps($p; $name : $n1, $likes : \"stilton\", $age : 100 )\n" +
                "then\n" +
                "   insert( $p );\n" +
                "end \n";

        drl += "rule x2\n" +
                "when\n" +
                "    $p : Person( )\n" +
                "then\n" +
                "   list.add( $p );\n" +
                "end \n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("backward-chaining-test", kieBaseTestConfiguration, drl);
        KieSession ksession = kbase.newKieSession();
        try {
            final List<Person> list = new ArrayList<>();
            ksession.setGlobal("list", list);

            final Person p1 = new Person("darth",
                                         "stilton",
                                         100);

            ksession.insert("darth");
            ksession = SerializationHelper.getSerialisedStatefulKnowledgeSession(ksession, true);
            ksession.fireAllRules();
            ksession.insert("yoda"); // darth exists, so yoda won't get created
            ksession = SerializationHelper.getSerialisedStatefulKnowledgeSession(ksession, true);
            ksession.fireAllRules();
            assertThat(list.size()).isEqualTo(1);
            assertThat(list.get(0)).isEqualTo(p1);
        } finally {
            ksession.dispose();
        }
    }

    @Test(timeout = 10000)
    public void testQueryWithOr() {
        final String drl = "" +
                "package org.drools.compiler.test  \n" +

                "import java.util.List\n" +
                "import java.util.ArrayList\n" +
                "import " + BackwardChainingTest.class.getName() + ".Q\n" +
                "import " + BackwardChainingTest.class.getName() + ".R\n" +
                "import " + BackwardChainingTest.class.getName() + ".S\n" +

                "global List list\n" +

                "dialect \"mvel\"\n" +
                "\n" +
                "query q(int x)\n" +
                "    Q( x := value )\n" +
                "end\n" +
                "\n" +
                "query r(int x)\n" +
                "    R( x := value )\n" +
                "end\n" +
                "\n" +
                "query s(int x)\n" +
                "    S( x := value )    \n" +
                "end\n" +
                "\n" +

                "query p(int x)\n" +
                "    (?q(x;) and ?r(x;) ) \n" +
                "    or\n" +
                "    ?s(x;)\n" +
                "end\n" +

                "rule init when\n" +
                "then\n" +
                " insert( new Q(1) );\n " +
                " insert( new Q(5) );\n " +
                " insert( new Q(6) );\n " +
                " insert( new R(1) );\n " +
                " insert( new R(4) );\n " +
                " insert( new R(6) );\n " +
                " insert( new R(2) );\n " +
                " insert( new S(2) );\n " +
                " insert( new S(3) );\n " +
                " insert( new S(6) );\n " +
                "end\n" +
                "";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("backward-chaining-test", kieBaseTestConfiguration, drl);
        final KieSession ksession = kbase.newKieSession();
        try {
            final List<Integer> list = new ArrayList<>();
            ksession.setGlobal("list", list);

            ksession.fireAllRules();

            QueryResults results;

            list.clear();
            results = ksession.getQueryResults("p", new Integer[]{0});
            for (final QueryResultsRow result : results) {
                list.add((Integer) result.get("x"));
            }
            assertThat(list.size()).isEqualTo(0);

            list.clear();
            results = ksession.getQueryResults("p", new Integer[]{1});
            for (final QueryResultsRow result : results) {
                list.add((Integer) result.get("x"));
            }

            assertThat(list.size()).isEqualTo(1);
            assertThat(list.get(0).intValue()).isEqualTo(1);

            list.clear();
            results = ksession.getQueryResults("p", new Integer[]{2});
            for (final QueryResultsRow result : results) {
                list.add((Integer) result.get("x"));
            }
            assertThat(list.size()).isEqualTo(1);
            assertThat(list.get(0).intValue()).isEqualTo(2);

            list.clear();
            results = ksession.getQueryResults("p", new Integer[]{3});
            for (final QueryResultsRow result : results) {
                list.add((Integer) result.get("x"));
            }
            assertThat(list.size()).isEqualTo(1);
            assertThat(list.get(0).intValue()).isEqualTo(3);

            list.clear();
            results = ksession.getQueryResults("p", new Integer[]{4});
            for (final QueryResultsRow result : results) {
                list.add((Integer) result.get("x"));
            }
            assertThat(list.size()).isEqualTo(0);

            list.clear();
            results = ksession.getQueryResults("p", new Integer[]{5});
            for (final QueryResultsRow result : results) {
                list.add((Integer) result.get("x"));
            }
            assertThat(list.size()).isEqualTo(0);

            list.clear();
            results = ksession.getQueryResults("p", new Integer[]{6});
            for (final QueryResultsRow result : results) {
                list.add((Integer) result.get("x"));
            }
            assertThat(list.size()).isEqualTo(2);
            assertThat(list.get(0).intValue()).isEqualTo(6);
            assertThat(list.get(1).intValue()).isEqualTo(6);
        } finally {
            ksession.dispose();
        }
    }

    @Test(timeout = 10000)
    public void testGeneology() throws IOException, ClassNotFoundException {
        // from http://kti.mff.cuni.cz/~bartak/prolog/genealogy.html

        final String drl = "" +
                "package org.drools.compiler.test2  \n" +
                "global java.util.List list\n" +
                "dialect \"mvel\"\n" +

                "query man( String name ) \n" +
                "   " + BackwardChainingTest.class.getName() + ".Man( name := name ) \n" +
                "end\n" +

                "query woman( String name ) \n" +
                "   " + BackwardChainingTest.class.getName() + ".Woman( name := name ) \n" +
                "end\n" +

                "query parent( String parent, String child ) \n" +
                "   " + BackwardChainingTest.class.getName() + ".Parent( parent := parent, child := child ) \n" +
                "end\n" +

                "query father( String father, String child ) \n" +
                "   ?man( father; ) \n" +
                "   ?parent( father, child; ) \n" +
                "end\n" +

                "query mother( String mother, String child ) \n" +
                "   ?woman( mother; ) \n" +
                "   ?parent( mother, child; ) \n" +
                "end\n" +

                "query son( String son, String parent ) \n" +
                "   ?man( son; ) \n" +
                "   ?parent( parent, son; ) \n" +
                "end\n" +

                "query daughter( String daughter, String parent ) \n" +
                "   ?woman( daughter; ) \n" +
                "   ?parent( parent, daughter; ) \n" +
                "end\n" +

                "query siblings( String c1, String c2 ) \n" +
                "   ?parent( $p, c1; ) \n" +
                "   ?parent( $p, c2; ) \n" +
                "   eval( !c1.equals( c2 ) )\n" +
                "end\n" +

                "query fullSiblings( String c1, String c2 )\n" +
                "   ?parent( $p1, c1; ) ?parent( $p1, c2; )\n" +
                "   ?parent( $p2, c1; ) ?parent( $p2, c2; )\n" +
                "   eval( !c1.equals( c2 ) && !$p1.equals( $p2 )  )\n" +
                "end\n" +

                "query fullSiblings2( String c1, String c2 )\n" +
                "   ?father( $p1, c1; ) ?father( $p1, c2; )\n" +
                "   ?mother( $p2, c1; ) ?mother( $p2, c2; )\n" +
                "   eval( !c1.equals( c2 ) )\n" +
                "end\n" +

                "query uncle( String uncle, String n )\n" +
                "   ?man( uncle; ) ?siblings( uncle, parent; )\n" +
                "   ?parent( parent, n; )\n " +
                "end\n" +

                "query aunt( String aunt, String n )\n" +
                "   ?woman( aunt; ) ?siblings( aunt, parent; )\n" +
                "   ?parent( parent, n; )\n " +
                "end\n" +

                "query grantParents( String gp, String gc )\n" +
                "   ?parent( gp, p; ) ?parent( p, gc; )\n" +
                "end\n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("backward-chaining-test", kieBaseTestConfiguration, drl);
        KieSession ksession = kbase.newKieSession();
        try {
            final List<String> list = new ArrayList<>();
            ksession.setGlobal("list", list);

            // grand parents
            ksession.insert(new Man("john"));
            ksession.insert(new Woman("janet"));

            // parent
            ksession.insert(new Man("adam"));
            ksession.insert(new Parent("john",
                                       "adam"));
            ksession.insert(new Parent("janet",
                                       "adam"));

            ksession.insert(new Man("stan"));
            ksession.insert(new Parent("john",
                                       "stan"));
            ksession.insert(new Parent("janet",
                                       "stan"));

            // grand parents
            ksession.insert(new Man("carl"));
            ksession.insert(new Woman("tina"));
            //
            // parent
            ksession.insert(new Woman("eve"));
            ksession.insert(new Parent("carl",
                                       "eve"));
            ksession.insert(new Parent("tina",
                                       "eve"));
            //
            // parent
            ksession.insert(new Woman("mary"));
            ksession.insert(new Parent("carl",
                                       "mary"));
            ksession.insert(new Parent("tina",
                                       "mary"));

            ksession.insert(new Man("peter"));
            ksession.insert(new Parent("adam",
                                       "peter"));
            ksession.insert(new Parent("eve",
                                       "peter"));

            ksession.insert(new Man("paul"));
            ksession.insert(new Parent("adam",
                                       "paul"));
            ksession.insert(new Parent("mary",
                                       "paul"));

            ksession.insert(new Woman("jill"));
            ksession.insert(new Parent("adam",
                                       "jill"));
            ksession.insert(new Parent("eve",
                                       "jill"));

            QueryResults results;
            ksession = SerializationHelper.getSerialisedStatefulKnowledgeSession(ksession, true);

            list.clear();
            results = ksession.getQueryResults("woman", v);
            for (final QueryResultsRow result : results) {
                list.add((String) result.get("name"));
            }
            assertThat(list.size()).isEqualTo(5);
            assertContains(new String[]{"janet", "mary", "tina", "eve", "jill"}, list);

            list.clear();
            results = ksession.getQueryResults("man", v);
            for (final QueryResultsRow result : results) {
                list.add((String) result.get("name"));
            }
            assertThat(list.size()).isEqualTo(6);
            assertContains(new String[]{"stan", "john", "peter", "carl", "adam", "paul"}, list);

            list.clear();
            results = ksession.getQueryResults("father", v, v);
            for (final QueryResultsRow result : results) {
                list.add(result.get("father") + ", " + result.get("child"));
            }
            assertThat(list.size()).isEqualTo(7);
            assertContains(new String[]{"john, adam", "john, stan",
                                   "carl, eve", "carl, mary",
                                   "adam, peter", "adam, paul",
                                   "adam, jill"}, list);

            list.clear();
            results = ksession.getQueryResults("mother", v, v);
            for (final QueryResultsRow result : results) {
                list.add(result.get("mother") + ", " + result.get("child"));
            }
            assertThat(list.size()).isEqualTo(7);
            assertContains(new String[]{"janet, adam", "janet, stan",
                                   "mary, paul", "tina, eve",
                                   "tina, mary", "eve, peter",
                                   "eve, jill"},
                           list);

            list.clear();
            results = ksession.getQueryResults("son",
                                               v, v);
            for (final QueryResultsRow result : results) {
                list.add(result.get("son") + ", " + result.get("parent"));
            }
            assertThat(list.size()).isEqualTo(8);
            assertContains(new String[]{"stan, john", "stan, janet",
                                   "peter, adam", "peter, eve",
                                   "adam, john", "adam, janet",
                                   "paul, mary", "paul, adam"}, list);

            list.clear();
            results = ksession.getQueryResults("daughter", v, v);
            for (final QueryResultsRow result : results) {
                list.add(result.get("daughter") + ", " + result.get("parent"));
            }
            assertThat(list.size()).isEqualTo(6);
            assertContains(new String[]{"mary, carl", "mary, tina",
                                   "eve, carl", "eve, tina",
                                   "jill, adam", "jill, eve"}, list);

            list.clear();
            results = ksession.getQueryResults("siblings", v, v);
            for (final QueryResultsRow result : results) {
                list.add(result.get("c1") + ", " + result.get("c2"));
            }
            assertThat(list.size()).isEqualTo(16);
            assertContains(new String[]{"eve, mary", "mary, eve",
                                   "adam, stan", "stan, adam",
                                   "adam, stan", "stan, adam",
                                   "peter, paul", "peter, jill",
                                   "paul, peter", "paul, jill",
                                   "jill, peter", "jill, paul",
                                   "peter, jill", "jill, peter",
                                   "eve, mary", "mary, eve"}, list);

            list.clear();
            
            results = ksession.getQueryResults("parent", v, v);
            for (final QueryResultsRow result : results) {
                list.add(result.get("parent") + ":" + result.get("child"));
            }
            System.out.println(list);

//            "query fullSiblings( String c1, String c2 )\n" +
//            "   ?parent( $p1, c1; ) ?parent( $p1, c2; )\n" +
//            "   ?parent( $p2, c1; ) ?parent( $p2, c2; )\n" +
//            "   eval( !c1.equals( c2 ) && !$p1.equals( $p2 )  )\n" +
//            "end\n" +

            list.clear();
            results = ksession.getQueryResults("fullSiblings", v, v);
            for (final QueryResultsRow result : results) {
                list.add(result.get("c1") + ", " + result.get("c2"));
            }
            System.out.println(list);
            assertThat(list.size()).isEqualTo(12);
            assertContains(new String[]{"eve, mary", "mary, eve",
                                   "adam, stan", "stan, adam",
                                   "adam, stan", "stan, adam",
                                   "peter, jill", "jill, peter",
                                   "peter, jill", "jill, peter",
                                   "eve, mary", "mary, eve"}, list);

            list.clear();
            results = ksession.getQueryResults("fullSiblings", v, v);
            for (final QueryResultsRow result : results) {
                list.add(result.get("c1") + ", " + result.get("c2"));
            }
            assertThat(list.size()).isEqualTo(12);
            assertContains(new String[]{"eve, mary", "mary, eve",
                                   "adam, stan", "stan, adam",
                                   "adam, stan", "stan, adam",
                                   "peter, jill", "jill, peter",
                                   "peter, jill", "jill, peter",
                                   "eve, mary", "mary, eve"}, list);

            list.clear();
            results = ksession.getQueryResults("uncle", v, v);
            for (final QueryResultsRow result : results) {
                list.add(result.get("uncle") + ", " + result.get("n"));
            }
            assertThat(list.size()).isEqualTo(6);
            assertContains(new String[]{"stan, peter",
                                   "stan, paul",
                                   "stan, jill",
                                   "stan, peter",
                                   "stan, paul",
                                   "stan, jill"}, list);

            list.clear();
            results = ksession.getQueryResults("aunt", v, v);
            for (final QueryResultsRow result : results) {
                list.add(result.get("aunt") + ", " + result.get("n"));
            }
            assertThat(list.size()).isEqualTo(6);
            assertContains(new String[]{"mary, peter",
                                   "mary, jill",
                                   "mary, peter",
                                   "mary, jill",
                                   "eve, paul",
                                   "eve, paul"}, list);

            list.clear();
            results = ksession.getQueryResults("grantParents", v, v);
            for (final QueryResultsRow result : results) {
                list.add(result.get("gp") + ", " + result.get("gc"));
            }
            assertThat(list.size()).isEqualTo(12);
            assertContains(new String[]{"carl, peter",
                                   "carl, jill",
                                   "carl, paul",
                                   "john, peter",
                                   "john, paul",
                                   "john, jill",
                                   "janet, peter",
                                   "janet, paul",
                                   "janet, jill",
                                   "tina, peter",
                                   "tina, jill",
                                   "tina, paul",}, list);
        } finally {
            ksession.dispose();
        }
    }

    @Test(timeout = 10000)
    public void testDynamicRulesWithSharing() {
        String drl = "" +
                "package org.drools.compiler.test1  \n" +
                "\n" +
                "declare Location\n" +
                "    thing : String \n" +
                "    location : String \n" +
                "end" +
                "\n" +
                "declare Edible\n" +
                "   thing : String\n" +
                "end" +
                "\n" +
                "query whereFood( String x, String y ) \n" +
                "    Location(x, y;) Edible(x;) \n" +
                "end\n" +
                "\n" +
                "rule init when\n" +
                "then\n" +
                "        \n" +
                "        insert( new Location(\"apple\", \"kitchen\") );\n" +
                "        insert( new Location(\"crackers\", \"kitchen\") );\n" +
                "        insert( new Location(\"broccoli\", \"kitchen\") );\n" +
                "        insert( new Location(\"computer\", \"office\") );\n" +

                "        insert( new Edible(\"apple\") );\n" +
                "        insert( new Edible(\"crackers\") );\n" +
                "end\n" +
                "";

        final KieBase kieBase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("backward-chaining-test", kieBaseTestConfiguration);
        final KnowledgeBuilder knowledgeBuilder = TestUtil.createKnowledgeBuilder(kieBase, drl);

        drl = "" +
                "package org.drools.compiler.test2  \n" +

                "import org.drools.compiler.test1.*\n" +
                "import java.util.List\n" +
                "import java.util.ArrayList\n" +

                "import java.util.Map\n" +
                "import java.util.HashMap\n" +

                "global List list\n" +
                "\n" +
                "rule look2 when\n" +
                "     $place : String() // just here to give a OTN lookup point\n" +
                "     whereFood(thing, $place;)\n" +
                "then\n" +
                "      list.add( \"2:\" + thing );\n" +
                "end\n";

        knowledgeBuilder.add(ResourceFactory.newByteArrayResource(drl.getBytes()), ResourceType.DRL);

        if (knowledgeBuilder.hasErrors()) {
            fail(knowledgeBuilder.getErrors().toString());
        }

        drl = "" +
                "package org.drools.compiler.test3  \n" +

                "import org.drools.compiler.test1.*\n" +
                "import java.util.List\n" +
                "import java.util.ArrayList\n" +

                "import java.util.Map\n" +
                "import java.util.HashMap\n" +

                "global List list\n" +
                "\n" +
                "rule look3 when\n" +
                "     $place : String() // just here to give a OTN lookup point\n" +
                "     whereFood(thing, $place;)\n" +
                "then\n" +
                "      list.add( \"3:\" + thing );\n" +
                "end\n";

        knowledgeBuilder.add(ResourceFactory.newByteArrayResource(drl.getBytes()), ResourceType.DRL);

        if (knowledgeBuilder.hasErrors()) {
            fail(knowledgeBuilder.getErrors().toString());
        }

        drl = "" +
                "package org.drools.compiler.test4  \n" +

                "import org.drools.compiler.test1.*\n" +
                "import java.util.List\n" +
                "import java.util.ArrayList\n" +

                "import java.util.Map\n" +
                "import java.util.HashMap\n" +

                "global List list\n" +
                "\n" +
                "rule look4 when\n" +
                "     $place : String() // just here to give a OTN lookup point\n" +
                "     whereFood(thing, $place;)\n" +
                "then\n" +
                "      list.add( \"4:\" + thing );\n" +
                "end\n";

        knowledgeBuilder.add(ResourceFactory.newByteArrayResource(drl.getBytes()), ResourceType.DRL);

        if (knowledgeBuilder.hasErrors()) {
            fail(knowledgeBuilder.getErrors().toString());
        }

        final Map<String, KiePackage> pkgs = new HashMap<>();
        for (final KiePackage pkg : knowledgeBuilder.getKnowledgePackages()) {
            pkgs.put(pkg.getName(), pkg);
        }

        final InternalKnowledgeBase kbase =
                (InternalKnowledgeBase) KieBaseUtil.getKieBaseFromKieModuleFromDrl("backward-chaining-test", kieBaseTestConfiguration);
        kbase.addPackages(Arrays.asList(pkgs.get("org.drools.compiler.test1"), pkgs.get("org.drools.compiler.test2")));

        final KieSession ksession = kbase.newKieSession();
        try {
            final List<Map<String, Object>> list = new ArrayList<>();
            ksession.setGlobal("list", list);
            ksession.insert("kitchen");

            ksession.fireAllRules();

            assertThat(list.size()).isEqualTo(2);
            assertContains(new String[]{"2:crackers", "2:apple"}, list);

            list.clear();
            kbase.addPackages(Collections.singletonList(pkgs.get("org.drools.compiler.test3")));

            ksession.fireAllRules();
            assertThat(list.size()).isEqualTo(2);
            assertContains(new String[]{"3:crackers", "3:apple"}, list);

            list.clear();
            kbase.addPackages(Collections.singletonList(pkgs.get("org.drools.compiler.test4")));

            ksession.fireAllRules();
            assertThat(list.size()).isEqualTo(2);
            assertContains(new String[]{"4:crackers", "4:apple"}, list);
        } finally {
            ksession.dispose();
        }
    }

    @Test(timeout = 10000)
    public void testOpenBackwardChain() {
        // http://www.amzi.com/AdventureInProlog/advtop.php

        final String drl = "" +
                "package org.drools.compiler.test  \n" +

                "import java.util.List\n" +
                "import java.util.ArrayList\n" +
                "import " + Person.class.getCanonicalName() + "\n" +

                "global List list\n" +

                "dialect \"mvel\"\n" +

                "declare Location\n" +
                "    thing : String \n" +
                "    location : String \n" +
                "end" +
                "\n" +
                "query isContainedIn( String x, String y ) \n" +
                "    Location(x, y;)\n" +
                "    or \n" +
                "    ( Location(z, y;) and isContainedIn(x, z;) )\n" +
                "end\n" +
                "\n" +
                "rule look when \n" +
                "    Person( $l : likes ) \n" +
                "    isContainedIn( $l, 'office'; )\n" +
                "then\n" +
                "   insertLogical( 'blah' );" +
                "end\n" +
                "rule existsBlah when \n" +
                "    exists String( this == 'blah') \n" +
                "then\n" +
                "   list.add( 'exists blah' );" +
                "end\n" +
                "\n" +
                "rule notBlah when \n" +
                "    not String( this == 'blah') \n" +
                "then\n" +
                "   list.add( 'not blah' );" +
                "end\n" +
                "\n" +
                "rule init when\n" +
                "then\n" +
                "        insert( new Location(\"desk\", \"office\") );\n" +
                "        insert( new Location(\"envelope\", \"desk\") );\n" +
                "        insert( new Location(\"key\", \"envelope\") );\n" +
                "end\n" +
                "\n" +
                "rule go1 when \n" +
                "    String( this == 'go1') \n" +
                "then\n" +
                "        list.add( drools.getRule().getName() ); \n" +
                "        insert( new Location('lamp', 'desk') );\n" +
                "end\n" +
                "\n" +
                "rule go2 when \n" +
                "    String( this == 'go2') \n" +
                "    $l : Location('lamp', 'desk'; )\n" +
                "then\n" +
                "    list.add( drools.getRule().getName() ); \n" +
                "    retract( $l );\n" +
                "end\n" +
                "\n" +
                "rule go3 when \n" +
                "    String( this == 'go3') \n" +
                "then\n" +
                "        list.add( drools.getRule().getName() ); \n" +
                "        insert( new Location('lamp', 'desk') );\n" +
                "end\n" +
                "\n" +
                "rule go4 when \n" +
                "    String( this == 'go4') \n" +
                "    $l : Location('lamp', 'desk'; )\n" +
                "then\n" +
                "        list.add( drools.getRule().getName() ); \n" +
                "    modify( $l ) { thing = 'book' };\n" +
                "end\n" +
                "\n" +
                "rule go5 when \n" +
                "    String( this == 'go5') \n" +
                "    $l : Location('book', 'desk'; )\n" +
                "then\n" +
                "    list.add( drools.getRule().getName() ); \n" +
                "    modify( $l ) { thing = 'lamp' };\n" +
                "end\n" +
                "\n" +
                "rule go6 when \n" +
                "    String( this == 'go6') \n" +
                "    $l : Location( 'lamp', 'desk'; )\n" +
                "then\n" +
                "    list.add( drools.getRule().getName() ); \n" +
                "    modify( $l ) { thing = 'book' };\n" +
                "end\n" +
                "\n" +
                "rule go7 when \n" +
                "    String( this == 'go7') \n" +
                "    $p : Person( likes == 'lamp' ) \n" +
                "then\n" +
                "    list.add( drools.getRule().getName() ); \n" +
                "    modify( $p ) { likes = 'key' };\n" +
                "end\n" +
                "\n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("backward-chaining-test", kieBaseTestConfiguration, drl);
        final KieSession ksession = kbase.newKieSession();
        try {
            final List<String> list = new ArrayList<>();
            ksession.setGlobal("list", list);

            final Person p = new Person();
            p.setLikes("lamp");
            ksession.insert(p);
            ksession.fireAllRules();
            assertThat(list.get(0)).isEqualTo("not blah");

            list.clear();

            InternalFactHandle fh = (InternalFactHandle) ksession.insert("go1");
            ksession.fireAllRules();

            fh = getFactHandle(fh, ksession);
            ksession.delete(fh);
            assertThat(list.get(0)).isEqualTo("go1");
            assertThat(list.get(1)).isEqualTo("exists blah");

            fh = (InternalFactHandle) ksession.insert("go2");
            ksession.fireAllRules();

            fh = getFactHandle(fh, ksession);
            ksession.delete(fh);
            assertThat(list.get(2)).isEqualTo("go2");
            assertThat(list.get(3)).isEqualTo("not blah");

            fh = (InternalFactHandle) ksession.insert("go3");
            ksession.fireAllRules();

            fh = getFactHandle(fh, ksession);
            ksession.delete(fh);
            assertThat(list.get(4)).isEqualTo("go3");
            assertThat(list.get(5)).isEqualTo("exists blah");

            fh = (InternalFactHandle) ksession.insert("go4");
            ksession.fireAllRules();

            fh = getFactHandle(fh, ksession);
            ksession.delete(fh);
            assertThat(list.get(6)).isEqualTo("go4");
            assertThat(list.get(7)).isEqualTo("not blah");

            fh = (InternalFactHandle) ksession.insert("go5");
            ksession.fireAllRules();

            fh = getFactHandle(fh, ksession);
            ksession.delete(fh);
            assertThat(list.get(8)).isEqualTo("go5");
            assertThat(list.get(9)).isEqualTo("exists blah");

            // This simulates a modify of the root DroolsQuery object, but first we break it
            fh = (InternalFactHandle) ksession.insert("go6");
            ksession.fireAllRules();

            fh = getFactHandle(fh, ksession);
            ksession.delete(fh);
            assertThat(list.get(10)).isEqualTo("go6");
            assertThat(list.get(11)).isEqualTo("not blah");

            // now fix it
            fh = (InternalFactHandle) ksession.insert("go7");
            ksession.fireAllRules();

            fh = getFactHandle(fh, ksession);
            ksession.delete(fh);
            assertThat(list.get(12)).isEqualTo("go7");
            assertThat(list.get(13)).isEqualTo("exists blah");
        } finally {
            ksession.dispose();
        }
    }

    @Test(timeout = 10000)
    public void testCompile() {
        final String drl = "declare Location\n"
                + "thing : String\n"
                + "location : String\n"
                + "end\n\n"
                + "query isContainedIn( String x, String y )\n"
                + "Location( x := thing, y := location)\n"
                + "or \n"
                + "( Location(z := thing, y := location) and ?isContainedIn( x := x, z := y ) )\n"
                + "end\n";

        KieBaseUtil.getKieBaseFromKieModuleFromDrl("backward-chaining-test", kieBaseTestConfiguration, drl);
    }

    @Test(timeout = 10000)
    public void testInsertionOrder() {
        final String drl = "" +
                "package org.drools.compiler.integrationtests  \n" +

                "import java.util.List\n" +
                "import java.util.ArrayList\n" +

                "global List list\n" +

                "dialect \"mvel\"\n" +

                "declare Person\n" +
                "   name : String\n" +
                "   likes : String\n" +
                "end\n" +
                "\n" +
                "declare Location\n" +
                "    thing : String \n" +
                "    location : String \n" +
                "end\n" +
                "\n" +
                "declare Edible\n" +
                "   thing : String\n" +
                "end\n" +
                "\n" +
                "\n" +
                "query hasFood( String x, String y ) \n" +
                "    Location(x, y;) " +
                "     or \n " +
                "    ( Location(z, y;) and hasFood(x, z;) )\n" +
                "end\n" +
                "\n" +
                "rule look when \n" +
                "    Person( $l : likes ) \n" +
                "    hasFood( $l, 'kitchen'; )\n" +
                "then\n" +
                "   list.add( 'kitchen has ' + $l );" +
                "end\n" +
                "rule go1 when\n" +
                "    String( this == 'go1') \n" +
                "then\n" +
                "        insert( new Person('zool', 'peach') );\n" +
                "        insert( new Location(\"table\", \"kitchen\") );\n" +
                "        insert( new Location(\"peach\", \"table\") );\n" +
                "end\n" +
                "rule go2 when\n" +
                "    String( this == 'go2') \n" +
                "then\n" +
                "        insert( new Person('zool', 'peach') );\n" +
                "        insert( new Location(\"peach\", \"table\") );\n" +
                "        insert( new Location(\"table\", \"kitchen\") );\n" +
                "end\n" +
                "\n" +
                "rule go3 when\n" +
                "    String( this == 'go3') \n" +
                "then\n" +
                "        insert( new Location(\"table\", \"kitchen\") );\n" +
                "        insert( new Location(\"peach\", \"table\") );\n" +
                "        insert( new Person('zool', 'peach') );\n" +
                "end\n" +
                "\n" +
                "rule go4 when\n" +
                "    String( this == 'go4') \n" +
                "then\n" +
                "        insert( new Location(\"peach\", \"table\") );\n" +
                "        insert( new Location(\"table\", \"kitchen\") );\n" +
                "        insert( new Person('zool', 'peach') );\n" +
                "end\n" +
                "rule go5 when\n" +
                "    String( this == 'go5') \n" +
                "then\n" +
                "        insert( new Location(\"peach\", \"table\") );\n" +
                "        insert( new Person('zool', 'peach') );\n" +
                "        insert( new Location(\"table\", \"kitchen\") );\n" +
                "end\n" +
                "rule go6 when\n" +
                "    String( this == 'go6') \n" +
                "then\n" +
                "        insert( new Location(\"table\", \"kitchen\") );\n" +
                "        insert( new Person('zool', 'peach') );\n" +
                "        insert( new Location(\"peach\", \"table\") );\n" +
                "end\n" +
                "\n" +
                "\n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("backward-chaining-test", kieBaseTestConfiguration, drl);

        for (int i = 1; i <= 6; i++) {
            final KieSession ksession = kbase.newKieSession();
            try {
                final List<String> list = new ArrayList<>();
                ksession.setGlobal("list", list);
                ksession.fireAllRules();
                list.clear();
                final FactHandle fh = ksession.insert("go" + i);
                ksession.fireAllRules();
                ksession.delete(fh);
                assertThat(list.size()).isEqualTo(1);
                assertThat(list.get(0)).isEqualTo("kitchen has peach");
            } finally {
                ksession.dispose();
            }
        }
    }

    @Test(timeout = 10000)
    public void testQueryFindAll() {
        final Object[] objects = new Object[]{42, "a String", 100};
        final int oCount = objects.length;

        final List<Object> queryList = new ArrayList<>();
        final List<Object> ruleList = new ArrayList<>();
        // expect all inserted objects + InitialFact
        runTestQueryFindAll(0, queryList, ruleList, objects);

        assertThat(queryList.size()).isEqualTo(oCount);
        assertContains(objects, queryList);

        // expect inserted objects + InitialFact
        queryList.clear();
        ruleList.clear();
        runTestQueryFindAll(1, queryList, ruleList, objects);
        assertThat(queryList.size()).isEqualTo(oCount * oCount);

        queryList.clear();
        ruleList.clear();
        runTestQueryFindAll(2, queryList, ruleList, objects);
        assertThat(queryList.size()).isEqualTo(oCount * oCount);
    }

    private void runTestQueryFindAll(final int iCase,
                                     final List<Object> queryList,
                                     final List<Object> ruleList,
                                     final Object[] objects) {
        String drl = "" +
                "package org.drools.compiler.test \n" +
                "global java.util.List queryList \n" +
                "global java.util.List ruleList \n" +
                "query object( Object o ) \n" +
                "    o := Object( ) \n" +
                "end \n" +
                "rule findObjectByQuery \n" +
                "when \n";
        switch (iCase) {
            case 0:
                // omit Object()
                drl += "    object( $a ; ) \n";
                break;
            case 1:
                drl += "    Object() ";
                drl += "    object( $a ; ) \n";
                break;
            case 2:
                drl += "    object( $a ; ) \n";
                drl += "    Object() ";
                break;
        }
        drl +=
                "then \n" +
                "//   System.out.println( \"Object by query: \" + $a );\n" +
                "    queryList.add( $a ); \n" +
                "end \n" +
                "rule findObject \n" +
                "salience 10 \n" +
                "when \n" +
                "    $o: Object() \n" +
                "then " +
                "//   System.out.println( \"Object: \" + $o );\n" +
                "    ruleList.add( $o ); \n" +
                "end \n" +
                "";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("backward-chaining-test", kieBaseTestConfiguration, drl);
        final KieSession ksession = kbase.newKieSession();
        try {
            ksession.setGlobal("queryList", queryList);
            ksession.setGlobal("ruleList", ruleList);
            for (final Object o : objects) {
                ksession.insert(o);
            }
            ksession.fireAllRules();
        } finally {
            ksession.dispose();
        }
    }

    @Test(timeout = 10000)
    public void testQueryWithObject() {
        final String drl = "" +
                "package org.drools.compiler.test  \n" +

                "import java.util.List\n" +
                "import java.util.ArrayList\n" +

                "global List list\n" +

                "dialect \"mvel\"\n" +
                "\n" +

                "import " + BackwardChainingTest.class.getName() + ".Q\n" +
                "import " + BackwardChainingTest.class.getName() + ".R\n" +
                "import " + BackwardChainingTest.class.getName() + ".S\n" +

                "query object(Object o)\n" +
                "    o := Object() \n" +
                "end\n" +

                "rule collectObjects when\n" +
                "   String( this == 'go1' )\n" +
                "   object( o; )\n" +
                "then\n" +
                "   list.add( o );\n" +
                "end\n" +

                "rule init when\n" +
                "   String( this == 'init' )\n" +
                "then\n" +
                " insert( new Q(1) );\n " +
                " insert( new Q(5) );\n " +
                " insert( new Q(6) );\n " +
                " insert( new R(1) );\n " +
                " insert( new R(4) );\n " +
                " insert( new R(6) );\n " +
                " insert( new R(2) );\n " +
                " insert( new S(2) );\n " +
                " insert( new S(3) );\n " +
                " insert( new S(6) );\n " +
                "end\n" +
                "";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("backward-chaining-test", kieBaseTestConfiguration, drl);
        KieSession ksession = kbase.newKieSession();
        List<Integer> list = new ArrayList<>();
        try {
            ksession.setGlobal("list", list);

            ksession.insert("init");
            ksession.fireAllRules();

            ksession.insert("go1");
            ksession.fireAllRules();

            assertThat(list.size()).isEqualTo(12);
            assertContains(new Object[]{
                    "go1", "init", new Q(6), new R(6), new S(3),
                    new R(2), new R(1), new R(4), new S(2),
                    new S(6), new Q(1), new Q(5)},
                           list);
        } finally {
            ksession.dispose();
        }

        // now reverse the go1 and init order
        ksession = kbase.newKieSession();
        try {
            list = new ArrayList<>();
            ksession.setGlobal("list", list);

            ksession.insert("go1");
            ksession.fireAllRules();

            ksession.insert("init");
            ksession.fireAllRules();

            assertThat(list.size()).isEqualTo(12);
            assertContains(new Object[]{
                    "go1", "init", new Q(6), new R(6), new S(3),
                    new R(2), new R(1), new R(4), new S(2),
                    new S(6), new Q(1), new Q(5)},
                           list);
        } finally {
            ksession.dispose();
        }
    }

    public static void assertContains(final Object[] objects,
                                final List list) {
        for (final Object object : objects) {
            if (!list.contains(object)) {
                fail("does not contain:" + object);
            }
        }
    }

    public static void assertContains(final List objects,
                                final List list) {
        if (!list.contains(objects)) {
            fail("does not contain:" + objects);
        }
    }

    public static InternalFactHandle getFactHandle(final FactHandle factHandle,
                                            final KieSession ksession) {
        final Map<Long, FactHandle> handles = new HashMap<>();
        ksession.getFactHandles().forEach(fh -> handles.put(((InternalFactHandle) fh).getId(), fh));
        return (InternalFactHandle) handles.get(((InternalFactHandle) factHandle).getId());
    }

    public static class Man
            implements
            Serializable {

        private String name;

        public Man(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "Man [name=" + name + "]";
        }
    }

    public static class Woman
            implements
            Serializable {

        private String name;

        public Woman(final String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "Woman [name=" + name + "]";
        }
    }

    public static class Parent
            implements
            Serializable {

        private String parent;
        private String child;

        public Parent(final String parent,
                      final String child) {
            this.parent = parent;
            this.child = child;
        }

        public String getParent() {
            return parent;
        }

        public void setParent(final String parent) {
            this.parent = parent;
        }

        public String getChild() {
            return child;
        }

        public void setChild(final String child) {
            this.child = child;
        }

        @Override
        public String toString() {
            return "Parent [parent=" + parent + ", child=" + child + "]";
        }
    }

    public static class Q {

        int value;

        public Q(final int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public void setValue(final int value) {
            this.value = value;
        }

        public String toString() {
            return "Q" + value;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + value;
            return result;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Q other = (Q) obj;
            return value == other.value;
        }
    }

    public static class R {

        int value;

        public R(final int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public void setValue(final int value) {
            this.value = value;
        }

        public String toString() {
            return "R" + value;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + value;
            return result;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final R other = (R) obj;
            return value == other.value;
        }
    }

    public static class S {

        int value;

        public S(final int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public void setValue(final int value) {
            this.value = value;
        }

        public String toString() {
            return "S" + value;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + value;
            return result;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final S other = (S) obj;
            return value == other.value;
        }
    }

    @Test(timeout = 10000)
    public void testQueryWithClassLiterals() {
        final String drl = "" +
                "package org.drools.test \n" +

                "import java.util.List\n" +
                "import java.util.ArrayList\n" +

                "global List list\n" +

                "declare Foo end \n" +

                "query klass( Class $c )\n" +
                " Object( this.getClass() == $c ) \n" +
                "end\n" +

                "rule R when\n" +
                " o : String( this == 'go1' )\n" +
                " klass( String.class ; )\n" +
                "then\n" +
                " list.add( o );\n" +
                " insert( new Foo() ); \n" +
                "end\n" +

                "rule S when\n" +
                " o : Foo()\n" +
                " klass( Foo.class ; )\n" +
                "then\n" +
                " list.add( o );\n" +
                "end\n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("backward-chaining-test", kieBaseTestConfiguration, drl);
        final KieSession ksession = kbase.newKieSession();
        try {
            final List list = new ArrayList();
            ksession.setGlobal("list", list);

            ksession.insert("go1");
            ksession.fireAllRules();

            assertThat(list.size()).isEqualTo(2);
            assertThat(list.get(0)).isEqualTo("go1");
            assertThat(list.get(1).getClass().getName()).isEqualTo("org.drools.test.Foo");
        } finally {
            ksession.dispose();
        }
    }

    @Test(timeout = 10000)
    public void testQueryIndexingWithUnification() {
        final String drl = "" +
                "package org.drools.test \n" +

                "import java.util.List\n" +
                "import java.util.ArrayList\n" +

                "global List list\n" +

                "declare Foo id : int end \n" +
                "declare Bar " +
                " name : String " +
                " val : int " +
                "end \n" +

                "query fooffa( String $name, Foo $f )\n" +
                " Bar( name == $name, $id : val )\n" +
                " $f := Foo( id == $id ) \n" +
                "end\n" +

                "rule R when\n" +
                " o : String( this == 'go' )\n" +
                " fooffa( \"x\", $f ; )\n" +
                "then\n" +
                " list.add( $f );\n" +
                "end\n" +

                "rule S when\n" +
                "then\n" +
                " insert( new Foo( 1 ) );\n" +
                " insert( new Bar( \"x\", 1 ) );\n" +
                "end\n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("backward-chaining-test", kieBaseTestConfiguration, drl);
        final KieSession ksession = kbase.newKieSession();
        try {
            final List<Integer> list = new ArrayList<>();
            ksession.setGlobal("list",
                               list);

            ksession.fireAllRules();

            ksession.insert("go");
            ksession.fireAllRules();

            assertThat(list.size()).isEqualTo(1);
        } finally {
            ksession.dispose();
        }
    }

    @Test(timeout = 10000)
    public void testQueryWithEvents() {
        final String drl = "global java.util.List list; " +
                "" +
                "declare Inner\n" +
                "  @role(event)\n" +
                "end\n" +

                "rule \"Input\"\n" +
                "when\n" +
                "then\n" +
                "    insert( \"X\" );\n" +
                "    insert( new Inner( ) );\n" +
                "end\n" +
                "\n" +
                "query myAgg(  )\n" +
                "    Inner(  )\n" +
                "end\n" +
                "\n" +
                "rule \"React\"\n" +
                "when\n" +
                "    String()\n" +
                "    myAgg(  )\n" +
                "then\n" +
                "    list.add( 42 );\n" +
                "end";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("backward-chaining-test", kieBaseTestConfiguration, drl);
        final KieSession ksession = kbase.newKieSession();
        try {
            final ArrayList list = new ArrayList();
            ksession.setGlobal("list", list);
            ksession.fireAllRules();
            assertThat(list).isEqualTo(Collections.singletonList(42));
        } finally {
            ksession.dispose();
        }
    }

    @Test(timeout = 10000)
    public void testNpeOnQuery() {
        final String drl =
                "global java.util.List list; " +
                        "query foo( Integer $i ) " +
                        "   $i := Integer( this < 10 ) " +
                        "end\n" +
                        "\n" +

                        "rule r1 when " +
                        "   foo( $i ; ) " +
                        "   Integer( this == 10 ) " +
                        "then " +
                        "   System.out.println(\"10 \" + $i);" +
                        "   list.add( 10 );\n" +
                        "end\n" +
                        "\n" +

                        "rule r2 when " +
                        "   foo( $i; ) " +
                        "   Integer( this == 20 ) " +
                        "then " +
                        "   System.out.println(\"20 \" + $i);" +
                        "   list.add( 20 );\n" +
                        "end\n" +

                        "rule r3 when " +
                        "   $i : Integer( this == 1 ) " +
                        "then " +
                        "   System.out.println($i);" +
                        "   update( kcontext.getKieRuntime().getFactHandle( $i ), $i + 1 );" +
                        "end\n" +
                        "\n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("backward-chaining-test", kieBaseTestConfiguration, drl);
        final KieSession kieSession = kbase.newKieSession();
        try {
            final List<Integer> list = new ArrayList<>();
            kieSession.setGlobal("list", list);

            kieSession.insert(1);
            kieSession.insert(20);

            kieSession.fireAllRules();

            assertThat(list.size()).isEqualTo(1);
            assertThat((int) list.get(0)).isEqualTo(20);
        } finally {
            kieSession.dispose();
        }
    }

    @Test
    public void testQueryWithEvalAndTypeBoxingUnboxing() {
        final String drl = "package org.drools.test;\n" +
                "\n" +
                "global java.util.List list \n;" +
                "\n" +
                "query primitiveInt( int $a )\n" +
                " Integer( intValue == $a )\n" +
                " eval( $a == 178 )\n" +
                "end\n" +
                "\n" +
                "query boxedInteger( Integer $a )\n" +
                " Integer( this == $a )\n" +
                " eval( $a == 178 )\n" +
                "end\n" +
                "\n" +
                "query boxInteger( int $a )\n" +
                " Integer( this == $a )\n" +
                " eval( $a == 178 )\n" +
                "end\n" +
                "\n" +
                "query unboxInteger( Integer $a )\n" +
                " Integer( intValue == $a )\n" +
                " eval( $a == 178 )\n" +
                "end\n" +
                "\n" +
                "query cast( int $a )\n" +
                " Integer( intValue == $a )\n" +
                " eval( $a == 178 )\n" +
                "end\n" +
                "" +
                "rule Init when then insert( 178 ); end\n" +
                "\n" +
                "rule Check\n" +
                "when\n" +
                " String()\n" +
                " ?primitiveInt( 178 ; )\n" +
                " ?boxedInteger( $x ; )\n" +
                " ?boxInteger( $x ; )\n" +
                " ?unboxInteger( $y ; )\n" +
                " ?cast( $z ; )\n" +
                "then\n" +
                " list.add( $x ); \n" +
                " list.add( $y ); \n" +
                " list.add( $z ); \n" +
                "end";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("backward-chaining-test", kieBaseTestConfiguration, drl);
        final KieSession ksession = kbase.newKieSession();
        try {
            final ArrayList list = new ArrayList();
            ksession.setGlobal("list", list);

            ksession.fireAllRules();
            assertThat(list.isEmpty()).isTrue();

            ksession.insert("go");
            ksession.fireAllRules();

            assertThat(list).isEqualTo(Arrays.asList(178, 178, 178));
        } finally {
            ksession.dispose();
        }
    }
    @Test
    public void testNaniSearchsNoPropReactivity() throws IOException, ClassNotFoundException {
        testNaniSearchs( PropertySpecificOption.ALLOWED);
    }

    @Test
    public void testNaniSearchsWithPropReactivity() throws IOException, ClassNotFoundException {
        // DROOLS-1453
        testNaniSearchs(PropertySpecificOption.ALWAYS);
    }

    private void testNaniSearchs(final PropertySpecificOption propertySpecificOption) throws IOException, ClassNotFoundException {
        // http://www.amzi.com/AdventureInProlog/advtop.php

        final String drl = "" +
                "package org.drools.compiler.test  \n" +

                "import java.util.List\n" +
                "import java.util.ArrayList\n" +

                "import java.util.Map\n" +
                "import java.util.HashMap\n" +

                "global List list\n" +

                "dialect \"mvel\"\n" +

                "declare Room" +
                "    name : String\n" +
                "end\n" +
                "\n" +
                "declare Location\n" +
                "    thing : String \n" +
                "    location : String \n" +
                "end" +
                "\n" +
                "declare Door\n" +
                "   fromLocation : String\n" +
                "   toLocation : String\n" +
                "end" +
                "\n" +
                "declare Edible\n" +
                "   thing : String\n" +
                "end" +
                "\n" +
                "declare TastesYucky\n" +
                "   thing : String\n" +
                "end\n" +
                "\n" +
                "declare Here\n" +
                "   place : String \n" +
                "end\n" +
                "\n" +

                "query whereFood( String x, String y ) \n" +
                "    ( Location(x, y;) and\n" +
                "      Edible(x;) ) " +
                "     or \n " +
                "    ( Location(z, y;) and ?whereFood(x, z;) )\n" +
                "end\n" +

                "query connect( String x, String y ) \n" +
                "    Door(x, y;)\n" +
                "    or \n" +
                "    Door(y, x;)\n" +
                "end\n" +
                "\n" +
                "query isContainedIn( String x, String y ) \n" +
                "    Location(x, y;)\n" +
                "    or \n" +
                "    ( Location(z, y;) and ?isContainedIn(x, z;) )\n" +
                "end\n" +
                "\n" +
                "query look(String place, List things, List food, List exits ) \n" +
                "    Here(place;)\n" +
                "    things := List() from accumulate( Location(thing, place;),\n" +
                "                                      collectList( thing ) )\n" +
                "    food := List() from accumulate( ?whereFood(thing, place;) ," +
                "                                    collectList( thing ) )\n" +
                "    exits := List() from accumulate( ?connect(place, exit;),\n" +
                "                                    collectList( exit ) )\n" +
                "end\n" +
                "\n" +
                "rule reactiveLook when\n" +
                "    Here( place : place) \n" +
                "    ?look(place, things, food, exits;)\n" +
                "then\n" +
                "    Map map = new HashMap();" +
                "    list.add(map);" +
                "    map.put( 'place', place); " +
                "    map.put( 'things', things); " +
                "    map.put( 'food', food); " +
                "    map.put( 'exits', exits); " +
                "    System.out.println( \"You are in the \" + place);\n" +
                "    System.out.println( \"  You can see \" + things );\n" +
                "    System.out.println( \"  You can eat \" + food );\n" +
                "    System.out.println( \"  You can go to \" + exits );\n" +
                "end\n" +
                "\n" +
                "rule init when\n" +
                "then\n" +
                "        insert( new Room(\"kitchen\") );\n" +
                "        insert( new Room(\"office\") );\n" +
                "        insert( new Room(\"hall\") );\n" +
                "        insert( new Room(\"dining room\") );\n" +
                "        insert( new Room(\"cellar\") );\n" +
                "        \n" +
                "        insert( new Location(\"apple\", \"kitchen\") );\n" +

                "        insert( new Location(\"desk\", \"office\") );\n" +
                "        insert( new Location(\"apple\", \"desk\") );\n" +
                "        insert( new Location(\"flashlight\", \"desk\") );\n" +
                "        insert( new Location(\"envelope\", \"desk\") );\n" +
                "        insert( new Location(\"key\", \"envelope\") );\n" +

                "        insert( new Location(\"washing machine\", \"cellar\") );\n" +
                "        insert( new Location(\"nani\", \"washing machine\") );\n" +
                "        insert( new Location(\"broccoli\", \"kitchen\") );\n" +
                "        insert( new Location(\"crackers\", \"kitchen\") );\n" +
                "        insert( new Location(\"computer\", \"office\") );\n" +
                "        \n" +
                "        insert( new Door(\"office\", \"hall\") );\n" +
                "        insert( new Door(\"kitchen\", \"office\") );\n" +
                "        insert( new Door(\"hall\", \"dining room\") );\n" +
                "        insert( new Door(\"kitchen\", \"cellar\") );\n" +
                "        insert( new Door(\"dining room\", \"kitchen\") );\n" +
                "        \n" +
                "        insert( new Edible(\"apple\") );\n" +
                "        insert( new Edible(\"crackers\") );\n" +
                "        \n" +
                "        insert( new TastesYucky(\"broccoli\") );  " +
                "end\n" +
                "" +
                "rule go1 when\n" +
                "   String( this == 'go1' )\n" +
                "then\n" +
                "   insert( new Here(\"kitchen\") );\n" +
                "end\n" +
                "\n" +
                "rule go2 when\n" +
                "   String( this == 'go2' )\n" +
                "   $h : Here( place == \"kitchen\")" +
                "then\n" +
                "   modify( $h ) { place = \"office\" };\n" +
                "end\n" +
                "";

        final ReleaseId releaseId1 = KieServices.get().newReleaseId("org.kie", "backward-chaining-test", "1");
        final Map<String, String> kieModuleConfigurationProperties = new HashMap<>();
        kieModuleConfigurationProperties.put(PropertySpecificOption.PROPERTY_NAME, propertySpecificOption.toString());

        final KieModule kieModule = KieUtil.getKieModuleFromDrls(releaseId1,
                kieBaseTestConfiguration,
                KieSessionTestConfiguration.STATEFUL_REALTIME,
                kieModuleConfigurationProperties,
                drl);
        final KieContainer kieContainer = KieServices.get().newKieContainer(kieModule.getReleaseId());
        final KieBase kbase = kieContainer.getKieBase();

        KieSession ksession = kbase.newKieSession();
        try {
            final List<Map<String, Object>> list = new ArrayList<>();
            ksession.setGlobal("list", list);

            ksession.fireAllRules();

            ksession.insert("go1");
            ksession = SerializationHelper.getSerialisedStatefulKnowledgeSession(ksession, true);
            ksession.fireAllRules();

            Map<String, Object> map = list.get(0);
            assertThat(map.get("place")).isEqualTo("kitchen");
            List<String> items = (List<String>) map.get("things");
            assertThat(items.size()).isEqualTo(3);
            assertContains(new String[]{"apple", "broccoli", "crackers"}, items);

            items = (List<String>) map.get("food");
            assertThat(items.size()).isEqualTo(2);
            assertContains(new String[]{"apple", "crackers"}, items);

            items = (List<String>) map.get("exits");
            assertThat(items.size()).isEqualTo(3);
            assertContains(new String[]{"office", "cellar", "dining room"}, items);

            ksession.insert("go2");
            ksession = SerializationHelper.getSerialisedStatefulKnowledgeSession(ksession, true);
            ksession.fireAllRules();

            map = list.get(1);
            assertThat(map.get("place")).isEqualTo("office");
            items = (List<String>) map.get("things");
            assertThat(items.size()).isEqualTo(2);
            assertContains(new String[]{"computer", "desk",}, items);

            items = (List<String>) map.get("food");
            assertThat(items.size()).isEqualTo(1);
            assertContains(new String[]{"apple"}, items); // notice the apple is on the desk in the office

            items = (List<String>) map.get("exits");
            assertThat(items.size()).isEqualTo(2);
            assertContains(new String[]{"hall", "kitchen"}, items);

            QueryResults results = ksession.getQueryResults("isContainedIn", "key", "office");
            assertThat(results.size()).isEqualTo(1);
            final QueryResultsRow result = results.iterator().next();
            assertThat(result.get("x")).isEqualTo("key");
            assertThat(result.get("y")).isEqualTo("office");

            results = ksession.getQueryResults("isContainedIn", "key", Variable.v);
            List<List<String>> l = new ArrayList<>();
            for (final QueryResultsRow r : results) {
                l.add(Arrays.asList((String) r.get("x"), (String) r.get("y")));
            }
            assertThat(results.size()).isEqualTo(3);
            assertContains(Arrays.asList("key", "desk"), l);
            assertContains(Arrays.asList("key", "office"), l);
            assertContains(Arrays.asList("key", "envelope"), l);

            results = ksession.getQueryResults("isContainedIn", Variable.v, "office");
            l = new ArrayList<>();
            for (final QueryResultsRow r : results) {
                l.add(Arrays.asList((String) r.get("x"), (String) r.get("y")));
            }

            assertThat(results.size()).isEqualTo(6);
            assertContains(Arrays.asList("desk", "office"), l);
            assertContains(Arrays.asList("computer", "office"), l);
            assertContains(Arrays.asList("apple", "office"), l);
            assertContains(Arrays.asList("envelope", "office"), l);
            assertContains(Arrays.asList("flashlight", "office"), l);
            assertContains(Arrays.asList("key", "office"), l);

            results = ksession.getQueryResults("isContainedIn", Variable.v, Variable.v);
            l = new ArrayList<>();
            for (final QueryResultsRow r : results) {
                l.add(Arrays.asList((String) r.get("x"), (String) r.get("y")));
            }
            assertThat(results.size()).isEqualTo(17);
            assertContains(Arrays.asList("apple", "kitchen"), l);
            assertContains(Arrays.asList("apple", "desk"), l);
            assertContains(Arrays.asList("envelope", "desk"), l);
            assertContains(Arrays.asList("desk", "office"), l);
            assertContains(Arrays.asList("computer", "office"), l);
            assertContains(Arrays.asList("washing machine", "cellar"), l);
            assertContains(Arrays.asList("key", "envelope"), l);
            assertContains(Arrays.asList("broccoli", "kitchen"), l);
            assertContains(Arrays.asList("nani", "washing machine"), l);
            assertContains(Arrays.asList("crackers", "kitchen"), l);
            assertContains(Arrays.asList("flashlight", "desk"), l);
            assertContains(Arrays.asList("nani", "cellar"), l);
            assertContains(Arrays.asList("apple", "office"), l);
            assertContains(Arrays.asList("envelope", "office"), l);
            assertContains(Arrays.asList("flashlight", "office"), l);
            assertContains(Arrays.asList("key", "office"), l);
            assertContains(Arrays.asList("key", "desk"), l);
        } finally {
            ksession.dispose();
        }
    }

    @Test //(timeout = 10000)
    public void testSubNetworksAndQueries() {
        final String drl = "" +
                "package org.drools.compiler.test  \n" +

                "import java.util.List\n" +
                "import java.util.ArrayList\n" +

                "import java.util.Map\n" +
                "import java.util.HashMap\n" +

                "global List list\n" +

                "dialect \"mvel\"\n" +
                "\n" +
                "declare Location\n" +
                "    thing : String \n" +
                "    location : String \n" +
                "end" +
                "\n" +
                "declare Edible\n" +
                "   thing : String\n" +
                "end" +
                "\n" +
                "query whereFood( String x, String y ) \n" +
                "    Location(x, y;) Edible(x;) \n" +
                "end\n" +
                "\n" +
                "query look(String place, List food ) \n" +
                "    $s : String() // just here to give a OTN lookup point\n" +
                "    food := List() from accumulate( whereFood(thing, place;) ," +
                "                                    collectList( thing ) )\n" +
                "    exists( whereFood(thing, place;) )\n" +
                "    not( whereFood(thing, place;) and\n " +
                "         String( this == $s ) from thing )\n" +
                "end\n" +
                "\n" +
                "rule init when\n" +
                "then\n" +
                "        \n" +
                "        insert( new Location(\"apple\", \"kitchen\") );\n" +
                "        insert( new Location(\"crackers\", \"kitchen\") );\n" +
                "        insert( new Location(\"broccoli\", \"kitchen\") );\n" +
                "        insert( new Location(\"computer\", \"office\") );\n" +

                "        insert( new Edible(\"apple\") );\n" +
                "        insert( new Edible(\"crackers\") );\n" +
                "end\n" +
                "";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("backward-chaining-test", kieBaseTestConfiguration, drl);

        // Get the accumulate node, so we can test it's memory later
        // now check beta memory was correctly cleared
        final List<ObjectTypeNode> nodes = ((InternalRuleBase) kbase).getRete().getObjectTypeNodes();
        ObjectTypeNode node = null;
        for (final ObjectTypeNode n : nodes) {
            if ((( ClassObjectType ) n.getObjectType()).getClassType() == String.class) {
                node = n;
                break;
            }
        }

        assertThat(node).isNotNull();
        final BetaNode stringBetaNode = ((JoinRightAdapterNode) node.getObjectSinkPropagator().getSinks()[0]).getBetaNode();
        final QueryElementNode  queryElementNode1 = (QueryElementNode) stringBetaNode.getSinkPropagator().getSinks()[0];
        final TupleToObjectNode riaNode1          = (TupleToObjectNode) queryElementNode1.getSinkPropagator().getSinks()[0];
        final AccumulateNode    accNode           =((AccumulateRight) riaNode1.getObjectSinkPropagator().getSinks()[0]).getBetaNode();

        final QueryElementNode  queryElementNode2 = (QueryElementNode) accNode.getSinkPropagator().getSinks()[0];
        final TupleToObjectNode riaNode2          = (TupleToObjectNode) queryElementNode2.getSinkPropagator().getSinks()[0];
        final ExistsNode        existsNode        = ((ExistsRight)riaNode2.getObjectSinkPropagator().getSinks()[0]).getBetaNode();

        final QueryElementNode queryElementNode3 = (QueryElementNode) existsNode.getSinkPropagator().getSinks()[0];
        final FromNode          fromNode = (FromNode) queryElementNode3.getSinkPropagator().getSinks()[0];
        final TupleToObjectNode riaNode3 = (TupleToObjectNode) fromNode.getSinkPropagator().getSinks()[0];
        final NotNode           notNode  = ((NotRight)riaNode3.getObjectSinkPropagator().getSinks()[0]).getBetaNode();

        final KieSession ksession = kbase.newKieSession();
        try {
            final InternalWorkingMemory wm = (( StatefulKnowledgeSessionImpl ) ksession);
            final AccumulateNode.AccumulateMemory accMemory = (AccumulateNode.AccumulateMemory ) wm.getNodeMemory(accNode);
            final BetaMemory existsMemory = (BetaMemory) wm.getNodeMemory(existsNode);
            final FromNode.FromMemory fromMemory = (FromNode.FromMemory ) wm.getNodeMemory(fromNode);
            final BetaMemory notMemory = (BetaMemory) wm.getNodeMemory(notNode);

            final List<Map<String, Object>> list = new ArrayList<>();
            ksession.setGlobal("list", list);
            final FactHandle fh = ksession.insert("bread");

            ksession.fireAllRules();

            final List food = new ArrayList();

            // Execute normal query and check no subnetwork tuples are left behind
            QueryResults results = ksession.getQueryResults("look", "kitchen", Variable.v);
            assertThat(results.size()).isEqualTo(1);

            for (final org.kie.api.runtime.rule.QueryResultsRow row : results) {
                food.addAll((Collection) row.get("food"));
            }
            assertThat(food.size()).isEqualTo(2);
            assertContains(new String[]{"crackers", "apple"}, food);

            assertThat(accMemory.getBetaMemory().getRightTupleMemory().size()).isEqualTo(0);
            assertThat(existsMemory.getRightTupleMemory().size()).isEqualTo(0);
            assertThat(fromMemory.getBetaMemory().getLeftTupleMemory().size()).isEqualTo(0);
            assertThat(notMemory.getRightTupleMemory().size()).isEqualTo(0);

            // Now execute an open query and ensure the memory is left populated
            food.clear();
            final List foodUpdated = new ArrayList();
            final LiveQuery query = ksession.openLiveQuery("look",
                    new Object[]{"kitchen", Variable.v},
                    new ViewChangedEventListener() {

                        public void rowUpdated(final Row row) {
                            foodUpdated.addAll((Collection) row.get("food"));
                        }

                        public void rowDeleted(final Row row) {
                        }

                        public void rowInserted(final Row row) {
                            food.addAll((Collection) row.get("food"));
                        }
                    });
            assertThat(food.size()).isEqualTo(2);
            assertContains(new String[]{"crackers", "apple"}, food);

            assertThat(accMemory.getBetaMemory().getRightTupleMemory().size()).isEqualTo(2);
            assertThat(existsMemory.getRightTupleMemory().size()).isEqualTo(0); // This is zero, as it's held directly on the LeftTuple context
            assertThat(fromMemory.getBetaMemory().getLeftTupleMemory().size()).isEqualTo(2);
            assertThat(notMemory.getRightTupleMemory().size()).isEqualTo(0);

            food.clear();
            // Now try again, make sure it only delete's it's own tuples
            results = ksession.getQueryResults("look", "kitchen", Variable.v);
            assertThat(results.size()).isEqualTo(1);

            for (final org.kie.api.runtime.rule.QueryResultsRow row : results) {
                food.addAll((Collection) row.get("food"));
            }
            assertThat(food.size()).isEqualTo(2);
            assertContains(new String[]{"crackers", "apple"}, food);

            assertThat(accMemory.getBetaMemory().getRightTupleMemory().size()).isEqualTo(2);
            assertThat(existsMemory.getRightTupleMemory().size()).isEqualTo(0);  // This is zero, as it's held directly on the LeftTuple context
            assertThat(fromMemory.getBetaMemory().getLeftTupleMemory().size()).isEqualTo(2);
            assertThat(notMemory.getRightTupleMemory().size()).isEqualTo(0);
            food.clear();

            // do an update and check it's  still memory size 2
            // however this time the food should be empty, as 'crackers' now blocks the not.
            ksession.update(fh, "crackers");
            ksession.fireAllRules();

            assertThat(accMemory.getBetaMemory().getRightTupleMemory().size()).isEqualTo(2);
            assertThat(existsMemory.getLeftTupleMemory().size()).isEqualTo(1);
            assertThat(existsMemory.getRightTupleMemory().size()).isEqualTo(0);  // This is zero, as it's held directly on the LeftTuple context
            assertThat(fromMemory.getBetaMemory().getLeftTupleMemory().size()).isEqualTo(2);
            assertThat(notMemory.getRightTupleMemory().size()).isEqualTo(0);  // This is zero, as it's held directly on the LeftTuple context

            assertThat(foodUpdated.size()).isEqualTo(0);

            // do an update and check it's  still memory size 2
            // this time
            ksession.update(fh, "oranges");
            ksession.fireAllRules();

            assertThat(accMemory.getBetaMemory().getRightTupleMemory().size()).isEqualTo(2);
            assertThat(existsMemory.getLeftTupleMemory().size()).isEqualTo(1);
            assertThat(existsMemory.getRightTupleMemory().size()).isEqualTo(0);  // This is zero, as it's held directly on the LeftTuple context
            assertThat(fromMemory.getBetaMemory().getLeftTupleMemory().size()).isEqualTo(2);
            assertThat(notMemory.getRightTupleMemory().size()).isEqualTo(0);

            assertThat(food.size()).isEqualTo(2);
            assertContains(new String[]{"crackers", "apple"}, food);

            // Close the open
            query.close();
            assertThat(accMemory.getBetaMemory().getRightTupleMemory().size()).isEqualTo(0);
            assertThat(existsMemory.getRightTupleMemory().size()).isEqualTo(0);
            assertThat(fromMemory.getBetaMemory().getLeftTupleMemory().size()).isEqualTo(0);
            assertThat(notMemory.getRightTupleMemory().size()).isEqualTo(0);
        } finally {
            ksession.dispose();
        }
    }

    @Test(timeout = 10000)
    public void testInsertionOrderTwo() {
        final StringBuilder drlBuilder = new StringBuilder("" +
                "package org.drools.compiler.test \n" +
                "import java.util.List \n" +
                "global List list \n" +
                "declare Thing \n" +
                "    thing : String @key \n" +
                "end \n" +
                "declare Edible extends Thing \n" +
                "end \n" +
                "declare Location extends Thing \n" +
                "    location : String  @key \n" +
                "end \n" +
                "declare Here \n" +
                "    place : String \n" +
                "end \n" +
                "rule kickOff \n" +
                "when \n" +
                "    Integer( $i: intValue ) \n" +
                "then \n" +
                "    switch( $i ){ \n");

        String[] facts = new String[]{"new Edible( 'peach' )", "new Location( 'peach', 'table' )", "new Here( 'table' )"};
        int f = 0;
        for (final String fact : facts) {
            for (final String fact1 : facts) {
                for (final String fact2 : facts) {
                    // use a Set to make sure we only include 3 unique values
                    final Set<String> set = new HashSet<>();
                    set.add(fact);
                    set.add(fact1);
                    set.add(fact2);
                    if (set.size() == 3) {
                        drlBuilder.append("    case ").append(f++).append(": \n")
                                .append("        insert( ").append(fact).append(" ); \n")
                                .append("        insert( ").append(fact1).append(" ); \n")
                                .append("        insert( ").append(fact2).append(" ); \n")
                                .append("        break; \n");
                    }
                }
            }
        }

        facts = new String[]{"new Edible( 'peach' )", "new Location( 'table', 'office' )", "new Location( 'peach', 'table' )", "new Here( 'office' )"};
        int h = f;
        for (final String fact : facts) {
            for (final String fact1 : facts) {
                for (final String fact3 : facts) {
                    for (final String fact2 : facts) {
                        // use a Set to make sure we only include 3 unique values
                        final Set<String> set = new HashSet<>();
                        set.add(fact);
                        set.add(fact1);
                        set.add(fact3);
                        set.add(fact2);
                        if (set.size() == 4) {
                            drlBuilder.append("    case ").append(h++).append(": \n")
                                    .append("        insert( ").append(fact).append(" ); \n")
                                    .append("        insert( ").append(fact1).append(" ); \n")
                                    .append("        insert( ").append(fact3).append(" ); \n")
                                    .append("        insert( ").append(fact2).append(" ); \n")
                                    .append("        break; \n");
                        }
                    }
                }
            }
        }

        drlBuilder.append("    } \n" + "end \n" + "\n"
                + "query whereFood( String x, String y ) \n"
                + "    ( Location(x, y;) and Edible(x;) ) \n "
                + "    or  \n"
                + "    ( Location(z, y;) and whereFood(x, z;) ) \n"
                + "end "
                + "query look(String place, List things, List food)  \n" + "    Here(place;) \n"
                + "    things := List() from accumulate( Location(thing, place;), \n"
                + "                                      collectList( thing ) ) \n"
                + "    food := List() from accumulate( whereFood(thing, place;), \n"
                + "                                    collectList( thing ) ) \n"
                + "end \n" + "rule reactiveLook \n" + "when \n" + "    Here( $place : place)  \n"
                + "    look($place, $things, $food;) \n"
                + "then \n"
                + "    list.addAll( $things ); \n"
                + "    list.addAll( $food   ); \n"
                + "end \n" + "");

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("backward-chaining-test", kieBaseTestConfiguration, drlBuilder.toString());

        for (int i = 0; i < f; i++) {
            final KieSession ksession = kbase.newKieSession();
            try {
                final List<String> list = new ArrayList<>();
                ksession.setGlobal("list", list);
                ksession.fireAllRules();
                list.clear();
                InternalFactHandle fh = (InternalFactHandle) ksession.insert(i);
                ksession.fireAllRules();

                assertThat(list.size()).isEqualTo(2);
                assertThat(list.get(0)).isEqualTo("peach");
                assertThat(list.get(1)).isEqualTo("peach");
                list.clear();

                final InternalFactHandle[] handles = ksession.getFactHandles().toArray(new InternalFactHandle[0]);
                for (int j = 0; j < handles.length; j++) {
                    if (handles[j].getObject() instanceof InitialFact || handles[j].getObject() instanceof Integer) {
                        continue;
                    }
                    handles[j] = getFactHandle(handles[j], ksession);
                    final Object o = handles[j].getObject();

                    // first retract + assert
                    ksession.delete(handles[j]);

                    handles[j] = (InternalFactHandle) ksession.insert(o);

                    ksession.fireAllRules();
                    assertThat(list.size()).isEqualTo(2);
                    assertThat(list.get(0)).isEqualTo("peach");
                    assertThat(list.get(1)).isEqualTo("peach");
                    list.clear();

                    // now try update
                    // session was serialised so need to get factHandle
                    handles[j] = getFactHandle(handles[j], ksession);
                    ksession.update(handles[j], handles[j].getObject());

                    ksession.fireAllRules();
                    assertThat(list.size()).isEqualTo(2);
                    assertThat(list.get(0)).isEqualTo("peach");
                    assertThat(list.get(1)).isEqualTo("peach");
                    list.clear();
                }

                fh = getFactHandle(fh, ksession);
                ksession.delete(fh);
            } finally {
                ksession.dispose();
            }
        }

        for (int i = f; i < h; i++) {
            final KieSession ksession = kbase.newKieSession();
            try {
                final List<String> list = new ArrayList<>();
                ksession.setGlobal("list", list);
                ksession.fireAllRules();
                list.clear();

                InternalFactHandle fh = (InternalFactHandle) ksession.insert(i);
                ksession.fireAllRules();
                assertThat(list.size()).isEqualTo(2);
                assertThat(list.get(0)).isEqualTo("table");
                assertThat(list.get(1)).isEqualTo("peach");
                list.clear();

                final InternalFactHandle[] handles = ksession.getFactHandles().toArray(new InternalFactHandle[0]);
                for (int j = 0; j < handles.length; j++) {
                    if (handles[j].getObject() instanceof InitialFact || handles[j].getObject() instanceof Integer) {
                        continue;
                    }

                    handles[j] = getFactHandle(handles[j], ksession);
                    final Object o = handles[j].getObject();

                    ksession.delete(handles[j]);
                    handles[j] = (InternalFactHandle) ksession.insert(o);

                    ksession.fireAllRules();
                    assertThat(list.size()).isEqualTo(2);
                    assertThat(list.get(0)).isEqualTo("table");
                    assertThat(list.get(1)).isEqualTo("peach");
                    list.clear();

                    // now try update
                    handles[j] = getFactHandle(handles[j], ksession);
                    ksession.update(handles[j], handles[j].getObject());

                    ksession.fireAllRules();
                    assertThat(list.size()).isEqualTo(2);
                    assertThat(list.get(0)).isEqualTo("table");
                    assertThat(list.get(1)).isEqualTo("peach");
                    list.clear();
                }

                fh = getFactHandle(fh, ksession);
                ksession.delete(fh);
            } finally {
                ksession.dispose();
            }
        }
    }
}
