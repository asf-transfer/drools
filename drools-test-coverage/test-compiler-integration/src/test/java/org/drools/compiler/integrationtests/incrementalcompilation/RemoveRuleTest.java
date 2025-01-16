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

import java.util.Arrays;
import java.util.stream.Stream;

import org.drools.core.common.DefaultFactHandle;
import org.drools.core.reteoo.EntryPointNode;
import org.drools.core.reteoo.ObjectSink;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.TupleImpl;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.model.Tuple;
import org.drools.testcoverage.common.model.Address;
import org.drools.testcoverage.common.model.Cheese;
import org.drools.testcoverage.common.model.Person;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieUtil;
import org.drools.testcoverage.common.util.TestParametersUtil2;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.ReleaseId;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class RemoveRuleTest {

    public static Stream<KieBaseTestConfiguration> parameters() {
        return TestParametersUtil2.getKieBaseCloudConfigurations(true).stream();
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testRemoveBigRule(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // JBRULES-3496
        final String str =
                "package org.drools.compiler.test\n" +
                        "\n" +
                        "declare SimpleFact\n" +
                        "   patientSpaceId : String\n" +
                        "   block : int\n" +
                        "end\n" +
                        "\n" +
                        "declare SimpleMembership\n" +
                        "   patientSpaceId : String\n" +
                        "   listId : String\n" +
                        "end\n" +
                        "\n" +
                        "declare SimplePatient\n" +
                        "   spaceId : String\n" +
                        "end\n" +
                        "\n" +
                        "rule \"RTR - 47146 retract\"\n" +
                        "agenda-group \"list membership\"\n" +
                        "when\n" +
                        "   $listMembership0 : SimpleMembership( $listMembershipPatientSpaceIdRoot : patientSpaceId, ( listId != null && listId == \"47146\" ) )\n" +
                        "   not ( $patient0 : SimplePatient( $patientSpaceIdRoot : spaceId, spaceId != null && spaceId == $listMembershipPatientSpaceIdRoot ) \n" +
                        "       and ( ( " +
                        "           SimpleFact( patientSpaceId == $patientSpaceIdRoot, block == 1 )\n" +
                        "         ) or ( " +
                        "           SimpleFact( patientSpaceId == $patientSpaceIdRoot, block == 2 )\n" +
                        "         ) or (" +
                        "           SimpleFact( patientSpaceId  == $patientSpaceIdRoot, block == 3 )\n" +
                        "         ) or (" +
                        "           SimpleFact( patientSpaceId == $patientSpaceIdRoot, block == 4 )\n" +
                        "         ) or (" +
                        "           SimpleFact( patientSpaceId == $patientSpaceIdRoot, block == 5 )\n" +
                        "       ) ) and ( ( " +
                        "           SimpleFact( patientSpaceId == $patientSpaceIdRoot, block == 6 )\n" +
                        "         ) or (" +
                        "           SimpleFact( patientSpaceId == $patientSpaceIdRoot, block == 7 )\n" +
                        "         ) or (" +
                        "           SimpleFact( patientSpaceId == $patientSpaceIdRoot, block == 8 )\n" +
                        "       ) ) and ( ( " +
                        "           SimpleFact( patientSpaceId == $patientSpaceIdRoot, block == 9 )\n" +
                        "         ) or (" +
                        "           SimpleFact( patientSpaceId == $patientSpaceIdRoot, block == 10 )\n" +
                        "         ) or (" +
                        "           SimpleFact( patientSpaceId == $patientSpaceIdRoot, block == 11 )\n" +
                        "         ) or (" +
                        "           SimpleFact( patientSpaceId == $patientSpaceIdRoot, block == 12 )\n" +
                        "         ) or (" +
                        "           SimpleFact( patientSpaceId == $patientSpaceIdRoot, block == 13 )\n" +
                        "         ) or ( (" +
                        "            SimpleFact( patientSpaceId == $patientSpaceIdRoot, block == 14 )\n" +
                        "           ) and (" +
                        "              SimpleFact( patientSpaceId == $patientSpaceIdRoot, block == 15 )\n" +
                        "         ) ) or ( ( " +
                        "            SimpleFact( patientSpaceId == $patientSpaceIdRoot, block == 16 )\n" +
                        "           ) and ( " +
                        "             SimpleFact( patientSpaceId == $patientSpaceIdRoot, block == 17 )\n" +
                        "         ) ) or ( ( " +
                        "             SimpleFact( patientSpaceId == $patientSpaceIdRoot, block == 18 )\n" +
                        "           ) and (" +
                        "             SimpleFact( patientSpaceId == $patientSpaceIdRoot, block == 19 )\n" +
                        "         ) ) or (" +
                        "           SimpleFact( patientSpaceId == $patientSpaceIdRoot, block == 20 )\n" +
                        "         ) or (" +
                        "           SimpleFact( patientSpaceId == $patientSpaceIdRoot, block == 21 )\n" +
                        "         ) or (" +
                        "           SimpleFact( patientSpaceId == $patientSpaceIdRoot, block == 22 )\n" +
                        "         ) or ( ( " +
                        "             SimpleFact( patientSpaceId == $patientSpaceIdRoot, block == 23 )\n" +
                        "         ) and (" +
                        "             SimpleFact( patientSpaceId == $patientSpaceIdRoot, block == 24 )\n" +
                        "     ) ) ) and ( ( " +
                        "           SimpleFact( patientSpaceId == $patientSpaceIdRoot, block == 25 )\n" +
                        "         ) or (" +
                        "           SimpleFact( patientSpaceId == $patientSpaceIdRoot, block == 26 )\n" +
                        "     ) ) )\n" +
                        "then\n" +
                        "end\n";

        final KieServices ks = KieServices.Factory.get();

        final ReleaseId releaseId1 = KieServices.get().newReleaseId("org.kie", "test-remove-big-rule", "1.0");
        KieUtil.getKieModuleFromDrls(releaseId1, kieBaseTestConfiguration, str);

        final KieContainer kc = ks.newKieContainer(releaseId1);
        final InternalKnowledgeBase kbase = (InternalKnowledgeBase) kc.getKieBase();

        final ReleaseId releaseId2 = KieServices.get().newReleaseId("org.kie", "test-remove-big-rule", "1.1");
        KieUtil.getKieModuleFromDrls(releaseId2, kieBaseTestConfiguration);

        kc.updateToVersion(releaseId2);

        final EntryPointNode epn = kbase.getRete().getEntryPointNodes().values().iterator().next();
        for (final ObjectTypeNode otn : epn.getObjectTypeNodes().values()) {
            final ObjectSink[] sinks = otn.getObjectSinkPropagator().getSinks();
            if (sinks.length > 0) {
                fail( otn + " has sinks " + Arrays.toString( sinks ) );
            }
        }
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testRemoveRuleWithFromNode(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // JBRULES-3631
        final String str =
                "package org.drools.compiler;\n" +
                        "import " + Person.class.getCanonicalName() + ";\n" +
                        "import " + Address.class.getCanonicalName() + ";\n" +
                        "rule R1 when\n" +
                        "   not( Person( name == \"Mark\" ));\n" +
                        "then\n" +
                        "end\n" +
                        "rule R2 when\n" +
                        "   $p: Person( name == \"Mark\" );\n" +
                        "   not( Address() from $p.getAddresses() );\n" +
                        "then\n" +
                        "end\n";

        final KieServices kieServices = KieServices.get();
        final ReleaseId releaseId = kieServices.newReleaseId("org.kie", "test-remove-rule-with-from-node", "1.0");
        KieUtil.getKieModuleFromDrls(releaseId, kieBaseTestConfiguration, str);
        final KieContainer kieContainer = kieServices.newKieContainer(releaseId);
        final KieBase kbase = kieContainer.getKieBase();
        assertThat(kbase.getKiePackage("org.drools.compiler").getRules().size()).isEqualTo(2);
        kbase.removeRule( "org.drools.compiler", "R2" );

        assertThat(kbase.getKiePackage("org.drools.compiler").getRules().size()).isEqualTo(1);
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testRuleRemovalWithJoinedRootPattern(KieBaseTestConfiguration kieBaseTestConfiguration) {
        String str = "";
        str += "package org.drools.compiler \n";
        str += "import " + Person.class.getCanonicalName() + ";\n";
        str += "import " + Cheese.class.getCanonicalName() + ";\n";
        str += "rule rule1 \n";
        str += "when \n";
        str += "  String() \n";
        str += "  Person() \n";
        str += "then \n";
        str += "end  \n";
        str += "rule rule2 \n";
        str += "when \n";
        str += "  String() \n";
        str += "  Cheese() \n";
        str += "then \n";
        str += "end  \n";

        final KieServices kieServices = KieServices.get();
        final ReleaseId releaseId = kieServices.newReleaseId("org.kie", "test-remove-rule-with-joined-root-pattern", "1.0");
        KieUtil.getKieModuleFromDrls(releaseId, kieBaseTestConfiguration, str);
        final KieContainer kieContainer = kieServices.newKieContainer(releaseId);

        final KieBase kbase = kieContainer.getKieBase();
        final KieSession ksession = kbase.newKieSession();
        final DefaultFactHandle handle = (DefaultFactHandle) ksession.insert("hello");
        ksession.fireAllRules();
        TupleImpl leftTuple = handle.getFirstLeftTuple();
        assertThat(leftTuple).isNotNull();
        assertThat(leftTuple.getPeer()).isNotNull();
        kbase.removeRule("org.drools.compiler", "rule2");
        leftTuple = handle.getFirstLeftTuple();
        assertThat(leftTuple).isNotNull();
        assertThat((Tuple) leftTuple.getHandleNext()).isNull();
    }

     @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testRemoveAccumulateRule(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // DROOLS-4864
        final String str =
                "package org.drools.compiler.test\n" +
                "\n" +
                "rule Acc no-loop\n" +
                "when\n" +
                "    accumulate(\n" +
                "            String( $l : length, this == \"test\" );\n" +
                "            $max : max( $l ))\n" +
                "then end\n";

        final KieServices ks = KieServices.Factory.get();

        final ReleaseId releaseId1 = KieServices.get().newReleaseId("org.kie", "test-remove-acc-rule", "1.0");
        KieUtil.getKieModuleFromDrls(releaseId1, kieBaseTestConfiguration, str);

        final KieContainer kc = ks.newKieContainer(releaseId1);
        final InternalKnowledgeBase kbase = (InternalKnowledgeBase) kc.getKieBase();

        KieSession ksession = kbase.newKieSession();
        ksession.insert("xxx");
        final ReleaseId releaseId2 = KieServices.get().newReleaseId("org.kie", "test-remove-acc-rule", "1.1");
        KieUtil.getKieModuleFromDrls(releaseId2, kieBaseTestConfiguration);

        kc.updateToVersion(releaseId2);

        final EntryPointNode epn = kbase.getRete().getEntryPointNodes().values().iterator().next();
        for (final ObjectTypeNode otn : epn.getObjectTypeNodes().values()) {
            final ObjectSink[] sinks = otn.getObjectSinkPropagator().getSinks();
            if (sinks.length > 0) {
                fail( otn + " has sinks " + Arrays.toString( sinks ) );
            }
        }
    }
}
