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

import java.io.StringReader;
import java.util.stream.Stream;

import org.drools.compiler.kie.builder.impl.DrlProject;
import org.drools.core.impl.InternalKieContainer;
import org.drools.model.codegen.ExecutableModelProject;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.TestParametersUtil2;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.ReleaseId;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.assertThat;

public class MultipleIncrementalCompilationTest {

    public static Stream<KieBaseTestConfiguration> parameters() {
        return TestParametersUtil2.getKieBaseCloudConfigurations(true).stream();
    }

    private final String PERSON_LIST_COMMON_SRC = "package org.test;\n" +
                                                  "import java.util.ArrayList;\n" +
                                                  "import java.util.List;\n" +
                                                  "public class PersonList {\n" +
                                                  "    private List<Person> persons;\n" +
                                                  "    public PersonList() {\n" +
                                                  "        this.persons = new ArrayList<>();\n" +
                                                  "        persons.add(new Person(\"John\"));\n" +
                                                  "        persons.add(new Person(\"Paul\"));\n" +
                                                  "    }\n" +
                                                  "    public PersonList(List<Person> persons) {\n" +
                                                  "        this.persons = persons;\n" +
                                                  "    }\n" +
                                                  "    public List<Person> getPersons() {\n" +
                                                  "        return persons;\n" +
                                                  "    }\n" +
                                                  "    public void setPersons(List<Person> persons) {\n" +
                                                  "        this.persons = persons;\n" +
                                                  "    }\n";

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    public void testSnapshotUpdateWithFrom(KieBaseTestConfiguration kieBaseTestConfiguration) {
        // DROOLS-6492
        final String personListSrc1 = PERSON_LIST_COMMON_SRC +
                                      "}";

        final String personListSrc2 = PERSON_LIST_COMMON_SRC +
                                      "    public void someMethod1() { }\n" +
                                      "}";

        final String personListSrc3 = PERSON_LIST_COMMON_SRC +
                                      "    public void someMethod1() { }\n" +
                                      "    public void someMethod2() { }\n" +
                                      "}";

        final KieServices ks = KieServices.Factory.get();
        final ReleaseId releaseId = ks.newReleaseId("org.test", "myTest", "1.0.0-SNAPSHOT");

        // 1st run
        buildKjar(kieBaseTestConfiguration, releaseId, personListSrc1);
        final KieContainer kc = ks.newKieContainer(releaseId);
        runRules(kc);

        // 2nd run
        buildKjar(kieBaseTestConfiguration, releaseId, personListSrc2);
        kc.updateToVersion(releaseId);
        runRules(kc);

        // 3rd run
        buildKjar(kieBaseTestConfiguration, releaseId, personListSrc3);
        kc.updateToVersion(releaseId);
        runRules(kc);
    }

    private void buildKjar(KieBaseTestConfiguration kieBaseTestConfiguration, ReleaseId releaseId, String personListSrc) {
        final String personSrc = "package org.test;\n" +
                                 "public class Person {\n" +
                                 "    private String name;\n" +
                                 "    public Person() {}\n" +
                                 "    public Person(String name) {\n" +
                                 "        this.name = name;\n" +
                                 "    }\n" +
                                 "    public String getName() {\n" +
                                 "        return name;\n" +
                                 "    }\n" +
                                 "    public void setName(String name) {\n" +
                                 "        this.name = name;\n" +
                                 "    }\n" +
                                 "}";

        final String drl = "package org.test;\n" +
                           "rule init\n" +
                           "    salience 100\n" +
                           "    when\n" +
                           "        String(this == \"Start\");\n" +
                           "    then\n" +
                           "        insert(new PersonList());\n" +
                           "end\n" +
                           "rule R1\n" +
                           "    dialect\"mvel\"\n" +
                           "    when\n" +
                           "        $list : PersonList()\n" +
                           "        ($p : Person() from $list.persons)\n" +
                           "    then\n" +
                           "        System.out.println($p.getName());\n" +
                           "end";

        final KieServices ks = KieServices.Factory.get();
        final KieFileSystem kfs = ks.newKieFileSystem();
        final KieBuilder kieBuilder = ks.newKieBuilder(kfs);

        kfs.generateAndWritePomXML(releaseId);

        kfs.write("src/main/java/org/test/Person.java",
                  ks.getResources().newReaderResource(new StringReader(personSrc)));

        kfs.write("src/main/java/org/test/PersonList.java",
                  ks.getResources().newReaderResource(new StringReader(personListSrc)));

        kfs.write(ks.getResources()
                    .newReaderResource(new StringReader(drl))
                    .setResourceType(ResourceType.DRL)
                    .setSourcePath("org/test/rules.drl"));

        if (kieBaseTestConfiguration.isExecutableModel()) {
            kieBuilder.buildAll(ExecutableModelProject.class);
        } else {
            kieBuilder.buildAll(DrlProject.class);
        }

        assertThat(kieBuilder.getResults().hasMessages(org.kie.api.builder.Message.Level.ERROR)).isFalse();
    }

    private void runRules(KieContainer kieContainer) {
        final KieSession ksession = ((InternalKieContainer) kieContainer).getKieSession(); // use the same ksession
        ksession.insert("Start");
        int fired = ksession.fireAllRules();
        assertThat(fired).isEqualTo(3);
    }
}
