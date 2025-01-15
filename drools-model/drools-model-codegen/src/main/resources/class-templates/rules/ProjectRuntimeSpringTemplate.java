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
package org.drools.project.model;

import java.util.concurrent.ConcurrentHashMap;

import org.drools.base.util.Drools;
import org.drools.core.SessionConfiguration;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieRuntimeBuilder;
import org.kie.api.runtime.StatelessKieSession;
import org.drools.modelcompiler.KieBaseBuilder;

@org.springframework.stereotype.Component
public class ProjectRuntime implements KieRuntimeBuilder {

    private static final ProjectModel model = new ProjectModel();
    private static final java.util.Map<String, KieBase> kbases = initKieBases();

    private org.drools.core.SessionConfiguration conf = org.drools.core.impl.RuleBaseFactory.newKnowledgeSessionConfiguration().as(SessionConfiguration.KEY);

    public static final ProjectRuntime INSTANCE = new ProjectRuntime();

    private static java.util.Map<String, KieBase> initKieBases() {
        java.util.Map<String, KieBase> kbaseMap = new ConcurrentHashMap<>();
        if (Drools.isNativeImage()) {
        }
        return kbaseMap;
    }

    @Override
    public KieBase getKieBase() {
        return getKieBase("$defaultKieBase$");
    }

    @Override
    public KieBase getKieBase(String name) {
        return kbases.computeIfAbsent( name, n -> KieBaseBuilder.createKieBaseFromModel(model.getModelsForKieBase(n), model.getKieModuleModel().getKieBaseModels().get(n)) );
    }

    @Override
    public KieSession newKieSession() {
        return newKieSession("$defaultKieSession$");
    }

    @Override
    public KieSession newKieSession(String sessionName) {
        KieBase kbase = getKieBaseForSession(sessionName);
        if (kbase == null) {
            throw new RuntimeException("Unknown KieSession with name '" + sessionName + "'");
        }
        return kbase.newKieSession(getConfForSession(sessionName), null);
    }

    @Override
    public StatelessKieSession newStatelessKieSession() {
        return newStatelessKieSession("$defaultStatelessKieSession$");
    }

    @Override
    public StatelessKieSession newStatelessKieSession(String sessionName) {
        KieBase kbase = getKieBaseForSession(sessionName);
        if (kbase == null) {
            throw new RuntimeException("Unknown StatelessKieSession with name '" + sessionName + "'");
        }
        return kbase.newStatelessKieSession(getConfForSession(sessionName));
    }

    private KieBase getKieBaseForSession(String sessionName) {
        switch(sessionName) {
            // populated via codegen
        }
        return null;
    }

    private org.kie.api.runtime.KieSessionConfiguration getConfForSession(String sessionName) {
        switch(sessionName) {
            // populated via codegen
        }
        return conf;
    }
}
