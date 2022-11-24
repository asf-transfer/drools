/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 *
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.drools.modelcompiler.operators;

import java.beans.Introspector;
import java.util.List;
import java.util.UUID;

import org.drools.compiler.kie.builder.impl.DrlProject;
import org.drools.modelcompiler.BaseModelTest.RUN_TYPE;
import org.drools.modelcompiler.ExecutableModelProject;
import org.drools.modelcompiler.KJARUtils;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import static org.junit.Assert.fail;

public class BaseOperatorsTest {

    protected KieSession getKieSession(String drl, RUN_TYPE testRunType) {
        KieServices ks = KieServices.get();
        ReleaseId releaseId = ks.newReleaseId("org.kie", "kjar-test-" + UUID.randomUUID(), "1.0");

        KieModuleModel model = KieServices.get().newKieModuleModel();
        ks.getRepository().removeKieModule(releaseId);

        KieFileSystem kfs = ks.newKieFileSystem();
        kfs.writeKModuleXML(model.toXML());
        kfs.writePomXML(KJARUtils.getPom(releaseId));
        kfs.write("src/main/resources/com/sample/Sample1.drl", drl);

        KieBuilder kieBuilder;
        if (testRunType.equals(RUN_TYPE.STANDARD_FROM_DRL)) {
            kieBuilder = ks.newKieBuilder(kfs).buildAll(DrlProject.class);
        } else if (testRunType.equals(RUN_TYPE.PATTERN_DSL)) {
            kieBuilder = ks.newKieBuilder(kfs).buildAll(ExecutableModelProject.class);
        } else {
            throw new UnsupportedOperationException(testRunType + " is not supported");
        }

        List<Message> messages = kieBuilder.getResults().getMessages();
        if (!messages.isEmpty()) {
            fail(messages.toString());
        }

        KieContainer kieContainer = ks.newKieContainer(releaseId);
        return kieContainer.newKieSession();
    }

    protected static String getPropertyName(Class clazz) {
        // returns a property name of ValueHolder class
        return Introspector.decapitalize(clazz.getSimpleName()) + "Value";
    }

    protected static String getInstanceValueString(Class clazz) {
        if (clazz.equals(Character.class)) {
            return "ValueHolder.constantCharacterValue()"; // DRL converts char to String so we cannot express Character constructor
        } else {
            return "new " + clazz.getSimpleName() + "(\"0\")";
        }
    }
}
