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
package org.drools.compiler.integrationtests.incrementalcompilation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.testcoverage.common.model.Person;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieUtil;
import org.drools.testcoverage.common.util.TestParametersUtil2;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieServices;
import org.kie.api.builder.ReleaseId;
import org.kie.api.definition.KiePackage;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.assertThat;

public class AddRuleTest {

    public static Stream<KieBaseTestConfiguration> parameters() {
        return TestParametersUtil2.getKieBaseCloudConfigurations(true).stream();
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testMemoriesCCEWhenAddRemoveAddRule(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // JBRULES-3656
        final String rule1 = "import " + AddRuleTest.class.getCanonicalName() + ".*\n" +
                "import java.util.Date\n" +
                "rule \"RTR - 28717 retract\"\n" +
                "when\n" +
                "        $listMembership0 : SimpleMembership( $listMembershipPatientSpaceIdRoot : patientSpaceId,\n" +
                "        ( listId != null && listId == \"28717\" ) ) and not ($patient0 : SimplePatient( $patientSpaceIdRoot : spaceId, spaceId != null &&\n" +
                "        spaceId == $listMembershipPatientSpaceIdRoot ) and\n" +
                "        (($ruleTime0 : RuleTime( $ruleTimeStartOfDay4_1 : startOfDay, $ruleTimeTime4_1 : time ) and $patient1 :\n" +
                "        SimplePatient( spaceId != null && spaceId == $patientSpaceIdRoot, birthDate != null && (birthDate after[0s,1d] $ruleTimeStartOfDay4_1) ) ) ) )\n" +
                "then\n" +
                "end";

        final String rule2 = "import " + AddRuleTest.class.getCanonicalName() + ".*\n" +
                "import java.util.Date\n" +
                "rule \"RTR - 28717 retract\"\n" +
                "when  $listMembership0 : SimpleMembership( $listMembershipPatientSpaceIdRoot : patientSpaceId, ( listId != null && listId == \"28717\" ) )\n" +
                "    and not ($patient0 : SimplePatient( $patientSpaceIdRoot : spaceId, spaceId != null && spaceId == $listMembershipPatientSpaceIdRoot )\n" +
                "    and ( ($ruleTime0 : RuleTime( $ruleTimeStartOfDay4_1 : startOfDay, $ruleTimeTime4_1 : time )\n" +
                "    and $patient1 : SimplePatient( spaceId != null && spaceId == $patientSpaceIdRoot, birthDate != null && (birthDate not after[0s,1d] $ruleTimeStartOfDay4_1) ) ) ) )\n" +
                "then\n" +
                "end";

        final KieServices kieServices = KieServices.get();
        final ReleaseId releaseId = kieServices.newReleaseId("org.kie", "test-memories-cce-when-add-remove-rule", "1.0");
        KieUtil.getKieModuleFromDrls(releaseId, kieBaseTestConfiguration);
        final KieContainer kieContainer = kieServices.newKieContainer(releaseId);
        final InternalKnowledgeBase kbase = (InternalKnowledgeBase) kieContainer.getKieBase();

        kbase.newKieSession();
        kbase.addPackages(TestUtil.createKnowledgeBuilder(null, rule1).getKnowledgePackages());
        kbase.addPackages(TestUtil.createKnowledgeBuilder(null, rule2).getKnowledgePackages());
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testAddRuleWithFrom(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // JBRULES-3499
        final String str1 = "global java.util.List names;\n" +
                "global java.util.List list;\n";

        final String str2 = "import " + Person.class.getCanonicalName() + ";\n" +
                "global java.util.List names;\n" +
                "global java.util.List list;\n" +
                "rule R1 when\n" +
                "   $p : Person( )\n" +
                "   String( this == $p.name ) from names\n" +
                "then\n" +
                " list.add( $p );\n" +
                "end";

        final KieServices kieServices = KieServices.get();
        final ReleaseId releaseId = kieServices.newReleaseId("org.kie", "test-add-rule-with-from", "1.0");
        KieUtil.getKieModuleFromDrls(releaseId, kieBaseTestConfiguration, str1);
        final KieContainer kieContainer = kieServices.newKieContainer(releaseId);

        final InternalKnowledgeBase kbase = (InternalKnowledgeBase) kieContainer.getKieBase();
        final KieSession ksession = kbase.newKieSession();

        final List<String> names = new ArrayList<>();
        names.add("Mark");
        ksession.setGlobal("names", names);

        final List<Person> list = new ArrayList<>();
        ksession.setGlobal("list", list);

        final Person p = new Person("Mark");
        ksession.insert(p);

        ksession.fireAllRules();

        kbase.addPackages(TestUtil.createKnowledgeBuilder(null, str2).getKnowledgePackages());

        ksession.fireAllRules();

        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0)).isSameAs(p);
        ksession.dispose();
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testDynamicallyAddInitialFactRule(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String rule = "package org.drools.compiler.test\n" +
                "global java.util.List list\n" +
                "rule xxx when\n" +
                "   i:Integer()\n" +
                "then\n" +
                "   list.add(i);\n" +
                "end";

        final KieServices kieServices = KieServices.get();
        final ReleaseId releaseId = kieServices.newReleaseId("org.kie", "test-add-dynamically-init-fact-rule", "1.0");
        KieUtil.getKieModuleFromDrls(releaseId, kieBaseTestConfiguration, rule);
        final KieContainer kieContainer = kieServices.newKieContainer(releaseId);

        final InternalKnowledgeBase kbase = (InternalKnowledgeBase) kieContainer.getKieBase();
        final KieSession session = kbase.newKieSession();

        final List list = new ArrayList();
        session.setGlobal("list", list);

        session.insert(5);
        session.fireAllRules();

        assertThat(list.get(0)).isEqualTo(5);

        rule = "package org.drools.compiler.test\n" +
                "global java.util.List list\n" +
                "rule xxx when\n" +
                "then\n" +
                "   list.add(\"x\");\n" +
                "end";
        final Collection<KiePackage> kpkgs = TestUtil.createKnowledgeBuilder(null, rule).getKnowledgePackages();
        kbase.addPackages(kpkgs);

        session.fireAllRules();

        assertThat(list.get(1)).isEqualTo("x");
    }

    public static class RuleTime {
        public Date getTime() {
            return new Date();
        }

        public Date getStartOfDay() {
            return new Date();
        }
    }

    public static class SimpleMembership {
        public String getListId() {
            return "";
        }

        public String getPatientSpaceId() {
            return "";
        }
    }

    public class SimplePatient {
        public String getSpaceId() {
            return "";
        }

        public String getFactHandleString() {
            return "";
        }

        public Date getBirthDate() {
            return new Date();
        }
    }
}
