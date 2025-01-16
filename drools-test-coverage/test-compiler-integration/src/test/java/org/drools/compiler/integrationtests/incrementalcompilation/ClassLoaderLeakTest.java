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

import org.drools.compiler.kie.builder.impl.KieContainerImpl;
import org.drools.compiler.kie.builder.impl.KieProject;
import org.drools.model.codegen.ExecutableModelProject;
import org.drools.wiring.api.classloader.ProjectClassLoader;
import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieModule;
import org.kie.api.builder.KieRepository;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.runtime.KieContainer;

import static org.assertj.core.api.Assertions.assertThat;

// DROOLS-6046
public class ClassLoaderLeakTest {

    private static final String KIEBASE = "test";

    private static final String PACKAGE = "org.build.example.rules";

    private static final String RULES = "package "+ PACKAGE +"\n" +
            "\n" +
            "declare Fact1\n" +
            "    x : Integer\n" +
            "    y : String\n" +
            "    z : Integer\n" +
            "end\n" +
            "\n" +
            "declare Fact2\n" +
            "    id : Integer\n" +
            "    f3Ids : java.util.List\n" +
            "end\n" +
            "\n" +
            "declare Fact3\n" +
            "    id : Integer\n" +
            "    a : Integer\n" +
            "    b : String\n" +
            "    c : String\n" +
            "end\n" +
            "\n" +
            "rule \"Test\"\n" +
            "    when\n" +
            "        $f3 : Fact3(a == 5, b == \"M\" || b == \"F\")\n" +
            "        $f2 : Fact2(f3Ids contains $f3.id)\n" +
            "        $f1 : Fact1(z == $f2.id, y == null, x > 1)\n" +
            "    then\n" +
            "    end\n" +
            "\n" +
            "rule \"Test_1%\"\n" +
            "salience 1\n" +
            "    when\n" +
            "        $f3 : Fact3(c == \"QWE\")\n" +
            "        $f2 : Fact2(f3Ids contains $f3.id)\n" +
            "        $f1 : Fact1(z == $f2.id, y == null, x == 1)\n" +
            "    then\n" +
            "    end\n" +
            "\n" +
            "\n" +
            "rule \"Test_2\"\n" +
            "salience 1\n" +
            "    when\n" +
            "        $f3 : Fact3(c == \"XYZ\")\n" +
            "        $f2 : Fact2(f3Ids contains $f3.id)\n" +
            "        $f1 : Fact1(z == $f2.id, y == null, x == 2)\n" +
            "    then\n" +
            "    end\n" +
            "\n" +
            "\n" +
            "rule \"Test_3\"\n" +
            "salience 1\n" +
            "    when\n" +
            "        $f3 : Fact3(c == \"ABC\")\n" +
            "        $f2 : Fact2(f3Ids contains $f3.id)\n" +
            "        $f1 : Fact1(z == $f2.id, y == null, x == 1)\n" +
            "    then\n" +
            "    end\n";

    private KieModule buildKieModule( String version, String rules, boolean withExecModel) {
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();

        ReleaseId rid = ks.newReleaseId(PACKAGE, KIEBASE, version);
        kfs.generateAndWritePomXML(rid);

        KieModuleModel kModuleModel = ks.newKieModuleModel();
        kModuleModel.newKieBaseModel(KIEBASE)
                .addPackage(PACKAGE);
        kfs.writeKModuleXML(kModuleModel.toXML());

        kfs.write("src/main/resources/org/build/example/rules/rules.drl", rules);

        KieBuilder kb = ks.newKieBuilder(kfs);
        if (withExecModel) {
            kb.buildAll(ExecutableModelProject.class);
        } else {
            kb.buildAll();
        }
        if (kb.getResults().hasMessages( Message.Level.ERROR)) {
            throw new RuntimeException("Build Errors:\n" + kb.getResults().toString());
        }

        return kb.getKieModule();
    }

    private void buildKjarsInALoop(boolean withExecModel) {
        KieServices ks = KieServices.Factory.get();
        KieRepository kr = ks.getRepository();

        KieContainer kieContainer = null;
        int oldSize = Integer.MAX_VALUE;

        for (int i = 0; i < 3; i++) {
            KieModule kModule = buildKieModule("1.0."+i, RULES, withExecModel);

            if (kieContainer == null) {
                kieContainer = ks.newKieContainer(kModule.getReleaseId());
            } else {
                kieContainer.updateToVersion(kModule.getReleaseId());
            }

            KieProject kp = (( KieContainerImpl ) kieContainer).getKieProject();
            ProjectClassLoader cl = (ProjectClassLoader) kp.getClassLoader();
            System.out.printf("ProjectClassLoader.store size: %d, " + "retained bytes: %d.%n",
                              cl.getStore().size(),
                              cl.getStore().values().stream()
                            .map(b->b.length).reduce(0,Integer::sum));

            assertThat(cl.getStore().size() <= oldSize).isTrue();
            oldSize = cl.getStore().size();

            kr.removeKieModule(kModule.getReleaseId());
        }
    }

    @Test
    public void loadKjarsInALoopWithExecModel() {
        buildKjarsInALoop(true);
    }

    @Test
    public void loadKjarsInALoopWithoutExecModel() {
        buildKjarsInALoop(false);
    }
}
