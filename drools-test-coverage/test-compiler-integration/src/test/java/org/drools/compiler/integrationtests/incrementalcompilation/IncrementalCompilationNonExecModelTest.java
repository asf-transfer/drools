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

import java.util.stream.Stream;

import org.drools.testcoverage.common.model.Message;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieUtil;
import org.drools.testcoverage.common.util.TestParametersUtil2;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.ReleaseId;
import org.kie.api.definition.type.FactType;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.internal.builder.IncrementalResults;
import org.kie.internal.builder.InternalKieBuilder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Incremental compilation tests which don't work with exec-model. Each test should be fixed by JIRA one-by-one
 */
public class IncrementalCompilationNonExecModelTest {

    public static Stream<KieBaseTestConfiguration> parameters() {
        return TestParametersUtil2.getKieBaseCloudConfigurations(false).stream();
    }

    private static final String DRL2_COMMON_SRC = "package myPkg\n" +
                                                  "import " + Message.class.getCanonicalName() + ";\n" +
                                                  "declare DummyDecl\n" + // If declare exists in the updated drl file, projectClassLoader.reinitTypes() is triggered
                                                  "  i : Integer\n" +
                                                  "end\n" +
                                                  "rule R1\n" + // always fired
                                                  "when\n" +
                                                  "then\n" +
                                                  "  insert(new DummyDecl());\n" +
                                                  "end\n" +
                                                  "rule R2_A\n" +
                                                  "when\n" +
                                                  "  $s : StringWrapper( s == \"ABC\" )\n" +
                                                  "then\n" +
                                                  "end\n" +
                                                  "rule R2_B\n" +
                                                  "when\n" +
                                                  "  $s : StringWrapper( s == \"DEF\" )\n" +
                                                  "then\n" +
                                                  "end\n";

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testCreateFileSetWithDeclaredModel(KieBaseTestConfiguration kieBaseTestConfiguration) throws InstantiationException, IllegalAccessException {

        final String drl1 = "package myPkg\n" +
                            "declare StringWrapper\n" +
                            " s : String\n" +
                            "end\n";

        // Requires 3 AlphaNodes to enable hash index
        final String drl2_1 = DRL2_COMMON_SRC +
                              "rule R2_C\n" +
                              "when\n" +
                              "  $s : StringWrapper( s == \"Hi Universe\" )\n" +
                              "then\n" +
                              "end\n";

        final String drl2_2 = DRL2_COMMON_SRC +
                              "rule R2_C when\n" +
                              "  $s : StringWrapper( s == \"Hello World\" )\n" +
                              "then\n" +
                              "  System.out.println(\"HIT\");\n" +
                              "end\n";

        final KieServices ks = KieServices.Factory.get();

        final KieFileSystem kfs = ks.newKieFileSystem()
                                    .write("src/main/resources/myPkg/r1.drl", drl1)
                                    .write("src/main/resources/myPkg/r2.drl", drl2_1);

        ReleaseId releaseId1 = ks.newReleaseId("org.default", "artifact", "1.1.0");
        kfs.generateAndWritePomXML(releaseId1);

        KieBuilder kieBuilder = KieUtil.getKieBuilderFromKieFileSystem(kieBaseTestConfiguration, kfs, true);
        final KieContainer kieContainer = ks.newKieContainer(releaseId1);

        runRules(kieContainer, 1);

        //---------------------

        kfs.delete("src/main/resources/myPkg/r2.drl");
        kfs.write("src/main/resources/myPkg/r2.drl", drl2_2);
        ReleaseId releaseId2 = ks.newReleaseId("org.default", "artifact", "1.2.0");
        kfs.generateAndWritePomXML(releaseId2);

        // buildAll instead of createFileSet doesn't have the issue
        //        if (kieBaseTestConfiguration.isExecutableModel()) {
        //            kieBuilder = ks.newKieBuilder(kfs).buildAll(ExecutableModelProject.class);
        //        } else {
        //            kieBuilder = ks.newKieBuilder(kfs).buildAll(DrlProject.class);
        //        }

        final IncrementalResults results = ((InternalKieBuilder) kieBuilder).createFileSet("src/main/resources/myPkg/r2.drl").build();
        assertThat(results.getAddedMessages().size()).isEqualTo(0);
        assertThat(results.getRemovedMessages().size()).isEqualTo(0);

        kieContainer.updateToVersion(releaseId2);

        runRules(kieContainer, 2);
    }

    private void runRules(final KieContainer kieContainer, int expectedfireCount) throws InstantiationException, IllegalAccessException {
        KieSession ksession = kieContainer.newKieSession();
        FactType factType = ksession.getKieBase().getFactType("myPkg", "StringWrapper");
        Object fact = factType.newInstance();
        factType.set(fact, "s", "Hello World");
        ksession.insert(fact);
        assertThat(ksession.fireAllRules()).isEqualTo(expectedfireCount);
        ksession.dispose();
    }
}
