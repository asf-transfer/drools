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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.drools.ancompiler.CompiledNetwork;
import org.drools.core.common.BetaConstraints;
import org.drools.core.reteoo.JoinNode;
import org.drools.core.reteoo.JoinRightAdapterNode;
import org.drools.core.reteoo.ObjectSink;
import org.drools.core.reteoo.ObjectSinkPropagator;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.testcoverage.common.model.Cheese;
import org.drools.testcoverage.common.model.Person;
import org.drools.testcoverage.common.model.Pet;
import org.drools.testcoverage.common.model.Pet.PetType;
import org.drools.testcoverage.common.model.Primitives;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.KieUtil;
import org.drools.testcoverage.common.util.TestParametersUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.kie.api.KieBase;
import org.kie.api.builder.KieModule;
import org.kie.api.conf.BetaRangeIndexOption;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class JoinNodeRangeIndexingTest {

    private final KieBaseTestConfiguration kieBaseTestConfiguration;

    public JoinNodeRangeIndexingTest(final KieBaseTestConfiguration kieBaseTestConfiguration) {
        this.kieBaseTestConfiguration = kieBaseTestConfiguration;
    }

    @Parameterized.Parameters(name = "KieBase type={0}")
    public static Collection<Object[]> getParameters() {
        return TestParametersUtil.getKieBaseCloudConfigurations(true);
    }

    private KieBase getKieBaseWithRangeIndexOption(String drl) {
        KieModule kieModule = KieUtil.getKieModuleFromDrls("indexing-test", kieBaseTestConfiguration, drl);
        return KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, BetaRangeIndexOption.ENABLED);
    }

    @Test
    public void testRangeIndexForJoin() {
        final String drl = "import " + Person.class.getCanonicalName() + ";\n" +
                           "import " + Pet.class.getCanonicalName() + ";\n" +
                           "rule R1\n" +
                           "when\n" +
                           "   $pet : Pet()\n" +
                           "   Person( age > $pet.age )\n" +
                           "then\n" +
                           "end\n";

        final KieBase kbase = getKieBaseWithRangeIndexOption(drl);

        assertIndexedTrue(kbase, Person.class);

        final KieSession ksession = kbase.newKieSession();
        try {

            ksession.insert(new Pet(PetType.CAT, 10));
            ksession.insert(new Person("Paul", 20));
            assertThat(ksession.fireAllRules()).isEqualTo(1);
        } finally {
            ksession.dispose();
        }
    }

    private void assertIndexedTrue(KieBase kbase, Class<?> factClass) {
        assertIndexed(kbase, factClass, true);
    }

    private void assertIndexedFalse(KieBase kbase, Class<?> factClass) {
        assertIndexed(kbase, factClass, false);
    }

    private void assertIndexed(KieBase kbase, Class<?> factClass, boolean isIndexed) {
        final ObjectTypeNode otn = KieUtil.getObjectTypeNode(kbase, factClass);
        assertThat(otn).isNotNull();

        ObjectSinkPropagator objectSinkPropagator = otn.getObjectSinkPropagator();
        if (this.kieBaseTestConfiguration.useAlphaNetworkCompiler()) {
            objectSinkPropagator = ((CompiledNetwork) objectSinkPropagator).getOriginalSinkPropagator();
        }

        // This method expect to test only one JoinNode of factClass
        boolean isPassedForJoinNode = false;
        ObjectSink[] sinks = objectSinkPropagator.getSinks();
        for (ObjectSink sink : sinks) {
            if (sink instanceof JoinRightAdapterNode) {
                JoinNode join = ((JoinRightAdapterNode) sink).getBetaNode();
                BetaConstraints betaConstraints = join.getRawConstraints();
                assertThat(betaConstraints.isIndexed()).isEqualTo(isIndexed);
                isPassedForJoinNode = true;
            }
        }
        assertThat(isPassedForJoinNode).isTrue();
    }

    @Test
    public void testCoercionBigDecimalVsInt() {
        final String drl = "package org.drools.compiler.integrationtests;\n" +
                           "import " + Cheese.class.getCanonicalName() + ";\n" +
                           "import " + Primitives.class.getCanonicalName() + ";\n" +
                           "global java.util.List list;\n" +
                           "rule R\n" +
                           "    when\n" +
                           "        Cheese($price : price)\n" +
                           "        p : Primitives(bigDecimal < $price)\n" +
                           "    then\n" +
                           "        list.add( p );\n" +
                           "end";

        // Integer is coerced to BigDecimal

        final KieBase kbase = getKieBaseWithRangeIndexOption(drl);

        assertIndexedTrue(kbase, Primitives.class);

        final KieSession ksession = kbase.newKieSession();
        try {
            final List<Primitives> list = new ArrayList<>();
            ksession.setGlobal("list", list);

            final Primitives bd42 = new Primitives();
            bd42.setBigDecimal(new BigDecimal("42"));
            ksession.insert(bd42);

            final Primitives bd43 = new Primitives();
            bd43.setBigDecimal(new BigDecimal("43"));
            ksession.insert(bd43);

            ksession.insert(new Cheese("gorgonzola", 43));
            ksession.fireAllRules();

            assertThat(list).containsExactly(bd42);
        } finally {
            ksession.dispose();
        }
    }

    @Test
    public void testCoercionIntVsBigDecimal() {
        final String drl = "package org.drools.compiler.integrationtests;\n" +
                           "import " + Person.class.getCanonicalName() + ";\n" +
                           "import " + Primitives.class.getCanonicalName() + ";\n" +
                           "global java.util.List list;\n" +
                           "rule R\n" +
                           "\n" +
                           "    when\n" +
                           "        Person($salary : salary)\n" +
                           "        p : Primitives(intPrimitive < $salary)\n" +
                           "    then\n" +
                           "        list.add( p );\n" +
                           "end";

        // BigDecimal is coerced to Integer

        final KieBase kbase = getKieBaseWithRangeIndexOption(drl);

        assertIndexedTrue(kbase, Primitives.class);

        final KieSession ksession = kbase.newKieSession();
        try {
            final List<Primitives> list = new ArrayList<>();
            ksession.setGlobal("list", list);

            final Primitives i42 = new Primitives();
            i42.setIntPrimitive(42);
            ksession.insert(i42);

            final Primitives i43 = new Primitives();
            i43.setIntPrimitive(43);
            ksession.insert(i43);

            Person john = new Person("John");
            john.setSalary(new BigDecimal("43.0"));
            ksession.insert(john);
            ksession.fireAllRules();

            assertThat(list).containsExactly(i42);
        } finally {
            ksession.dispose();
        }
    }

    @Test
    public void testCoercionStringVsIntWithMap() {

        final String drl = "package org.drools.compiler.integrationtests;\n" +
                           "import " + Map.class.getCanonicalName() + ";\n" +
                           "import " + Cheese.class.getCanonicalName() + ";\n" +
                           "global java.util.List list;\n" +
                           "rule R\n" +
                           "    when\n" +
                           "        $map : Map()" +
                           "        p : Cheese(type < $map.get(\"key\"))\n" +
                           "    then\n" +
                           "        list.add( p );\n" +
                           "end";

        // Integer is coerced to String (thus, String comparison)

        final KieBase kbase = getKieBaseWithRangeIndexOption(drl);

        // We don't index this case
        assertIndexedFalse(kbase, Cheese.class);

        final KieSession ksession = kbase.newKieSession();
        try {
            final List<Cheese> list = new ArrayList<>();
            ksession.setGlobal("list", list);

            final Cheese cheese1 = new Cheese("1");
            ksession.insert(cheese1);

            final Cheese cheese5 = new Cheese("5");
            ksession.insert(cheese5);

            final Cheese cheese10 = new Cheese("10");
            ksession.insert(cheese10);

            Map<String, Object> map = new HashMap<>();
            map.put("key", 5);
            ksession.insert(map);

            ksession.fireAllRules();

            assertThat(list).containsExactly(cheese1); // If we do String comparison, cheese10 is also contained
        } finally {
            ksession.dispose();
        }
    }

    @Test
    public void testCoercionIntVsStringWithMap() {
        // we don't enable range index for this case
        final String drl = "package org.drools.compiler.integrationtests;\n" +
                           "import " + Cheese.class.getCanonicalName() + ";\n" +
                           "import " + MapHolder.class.getCanonicalName() + ";\n" +
                           "global java.util.List list;\n" +
                           "rule R\n" +
                           "    when\n" +
                           "        Cheese($type : type)" +
                           "        p : MapHolder(map.get(\"key\") < $type)\n" +
                           "    then\n" +
                           "        list.add( p );\n" +
                           "end";

        // String is coerced to Integer (thus, Number comparison)

        final KieBase kbase = getKieBaseWithRangeIndexOption(drl);

        // We don't index this case
        assertIndexedFalse(kbase, MapHolder.class);

        final KieSession ksession = kbase.newKieSession();
        try {
            final List<MapHolder> list = new ArrayList<>();
            ksession.setGlobal("list", list);

            final MapHolder holder1 = new MapHolder();
            holder1.getMap().put("key", 1);
            ksession.insert(holder1);

            final MapHolder holder5 = new MapHolder();
            holder5.getMap().put("key", 5);
            ksession.insert(holder5);

            final MapHolder holder10 = new MapHolder();
            holder10.getMap().put("key", 10);
            ksession.insert(holder10);

            final Cheese cheese = new Cheese("5");
            ksession.insert(cheese);

            ksession.fireAllRules();

            assertThat(list).containsExactly(holder1);
        } finally {
            ksession.dispose();
        }
    }

    public static class MapHolder {

        private Map<String, Object> map = new HashMap<>();

        public Map<String, Object> getMap() {
            return map;
        }

        public void setMap(Map<String, Object> map) {
            this.map = map;
        }

    }

    @Test
    public void testJoinWithGlobal() {
        final String drl = "import " + Person.class.getCanonicalName() + ";\n" +
                           "global Integer minAge;\n" +
                           "rule R1\n" +
                           "when\n" +
                           "   Person( age > minAge )\n" +
                           "then\n" +
                           "end\n";

        // Actually, [age > minAge] becomes an AlphaNode and doesn't use index.

        final KieBase kbase = getKieBaseWithRangeIndexOption(drl);
        final KieSession ksession = kbase.newKieSession();

        try {
            ksession.setGlobal("minAge", 15);
            ksession.insert(new Person("Paul", 20));
            assertThat(ksession.fireAllRules()).isEqualTo(1);
        } finally {
            ksession.dispose();
        }
    }

    @Test
    public void testInsertUpdateDelete() {
        final String drl = "import " + Person.class.getCanonicalName() + ";\n" +
                           "import " + Pet.class.getCanonicalName() + ";\n" +
                           "global java.util.Set result;\n" +
                           "rule R1\n" +
                           "when\n" +
                           "   $pet : Pet()\n" +
                           "   $person : Person( age > $pet.age )\n" +
                           "then\n" +
                           "   result.add( $person.getName() + \" > \" + $pet.getName() );\n" +
                           "end\n" +
                           "rule R2\n" +
                           "   salience 100\n" +
                           "when\n" +
                           "   String (this == \"trigger R2\")\n" +
                           "   $person : Person( name == \"Paul\" )\n" +
                           "then\n" +
                           "   modify($person) {setAge(20)}\n" +
                           "end\n" +
                           "rule R3\n" +
                           "   salience 100\n" +
                           "when\n" +
                           "   String (this == \"trigger R3\")\n" +
                           "   $pet : Pet( )\n" +
                           "then\n" +
                           "   modify($pet) {setAge($pet.getAge() - 5)}\n" +
                           "end\n" +
                           "rule R4\n" +
                           "   salience 100\n" +
                           "when\n" +
                           "   String (this == \"trigger R4\")\n" +
                           "   $pet : Pet( name == \"Oliver\" )\n" +
                           "then\n" +
                           "   delete($pet);\n" +
                           "end\n";

        final KieBase kbase = getKieBaseWithRangeIndexOption(drl);

        assertIndexedTrue(kbase, Person.class);

        final KieSession ksession = kbase.newKieSession();
        try {
            final Set<String> result = new HashSet<>();
            ksession.setGlobal("result", result);

            ksession.insert(new Pet("Oliver", 5));
            ksession.insert(new Pet("Leo", 10));
            ksession.insert(new Pet("Milo", 20));

            ksession.insert(new Person("John", 10));
            ksession.insert(new Person("Paul", 10));

            assertThat(ksession.fireAllRules()).isEqualTo(2);
            assertThat(result).containsExactlyInAnyOrder("John > Oliver", "Paul > Oliver");

            ksession.insert("trigger R2"); // set Paul's age = 20
            assertThat(ksession.fireAllRules()).isEqualTo(3);
            assertThat(result).containsExactlyInAnyOrder("John > Oliver", "Paul > Oliver", "Paul > Leo");

            ksession.insert("trigger R3"); // set all Pets' age minus 5
            assertThat(ksession.fireAllRules()).isEqualTo(8);
            assertThat(result).containsExactlyInAnyOrder("John > Oliver", "John > Leo", "Paul > Oliver", "Paul > Leo", "Paul > Milo");

            ksession.insert("trigger R4"); // delete Oliver
            ksession.insert(new Person("George", 15));
            assertThat(ksession.fireAllRules()).isEqualTo(2);
            assertThat(result).containsExactlyInAnyOrder("John > Oliver", "John > Leo", "Paul > Oliver", "Paul > Leo", "Paul > Milo", "George > Leo");

        } finally {
            ksession.dispose();
        }
    }

    @Test
    public void testBoxed() {
        final String drl = "import " + Person.class.getCanonicalName() + ";\n" +
                           "import " + IntegerHolder.class.getCanonicalName() + ";\n" +
                           "rule R1\n" +
                           "when\n" +
                           "   $holder : IntegerHolder()\n" +
                           "   Person( age > $holder.value )\n" +
                           "then\n" +
                           "end\n";

        final KieBase kbase = getKieBaseWithRangeIndexOption(drl);

        assertIndexedTrue(kbase, Person.class);

        final KieSession ksession = kbase.newKieSession();
        try {

            ksession.insert(new IntegerHolder(10));
            ksession.insert(new Person("Paul", 20));
            assertThat(ksession.fireAllRules()).isEqualTo(1);
        } finally {
            ksession.dispose();
        }
    }

    @Test
    public void testBoxed2() {
        final String drl = "import " + Person.class.getCanonicalName() + ";\n" +
                           "import " + IntegerHolder.class.getCanonicalName() + ";\n" +
                           "rule R1\n" +
                           "when\n" +
                           "   $p : Person( )\n" +
                           "   IntegerHolder(value > $p.age)\n" +
                           "then\n" +
                           "end\n";

        final KieBase kbase = getKieBaseWithRangeIndexOption(drl);

        assertIndexedTrue(kbase, IntegerHolder.class);

        final KieSession ksession = kbase.newKieSession();
        try {
            ksession.insert(new IntegerHolder(30));
            ksession.insert(new Person("Paul", 20));
            assertThat(ksession.fireAllRules()).isEqualTo(1);
        } finally {
            ksession.dispose();
        }
    }

    public static class IntegerHolder {

        private Integer value;

        public IntegerHolder(Integer value) {
            this.value = value;
        }

        public Integer getValue() {
            return value;
        }
    }

    @Test
    public void testMultipleFacts() {

        final String drl = "import " + Person.class.getCanonicalName() + ";\n" +
                           "import " + Pet.class.getCanonicalName() + ";\n" +
                           "global java.util.Set result;\n" +
                           "rule R1\n" +
                           "when\n" +
                           "   $pet : Pet()\n" +
                           "   $person : Person( age > $pet.age )\n" +
                           "then\n" +
                           "   result.add( $person.getName() + \" > \" + $pet.getName() );\n" +
                           "end\n";

        final KieBase kbase = getKieBaseWithRangeIndexOption(drl);

        assertIndexedTrue(kbase, Person.class);

        final KieSession ksession = kbase.newKieSession();
        Set<String> result = new HashSet<>();
        ksession.setGlobal("result", result);
        try {
            ksession.insert(new Pet("Charlie", 5));
            ksession.insert(new Pet("Max", 10));
            ksession.insert(new Pet("Buddy", 15));
            ksession.insert(new Pet("Oscar", 20));

            ksession.insert(new Person("John", 2));
            ksession.insert(new Person("Paul", 10));
            ksession.insert(new Person("George", 20));
            ksession.insert(new Person("Ringo", 30));

            assertThat(ksession.fireAllRules()).isEqualTo(8);

            assertThat(result).containsExactlyInAnyOrder("Paul > Charlie", "George > Charlie", "George > Max", "George > Buddy", "Ringo > Charlie", "Ringo > Max", "Ringo > Buddy", "Ringo > Oscar");

        } finally {
            ksession.dispose();
        }
    }
}
