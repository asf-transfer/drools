/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.reliability;

import org.drools.core.SessionConfiguration;
import org.drools.core.common.EntryPointFactory;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.ObjectStore;
import org.drools.core.impl.RuleBase;
import org.drools.kiesession.factory.RuntimeComponentFactoryImpl;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.kiesession.session.StatefulKnowledgeSessionImpl;
import org.kie.api.runtime.Environment;

public class ReliableRuntimeComponentFactoryImpl extends RuntimeComponentFactoryImpl {

    public static final ReliableRuntimeComponentFactoryImpl DEFAULT = new ReliableRuntimeComponentFactoryImpl();

    @Override
    public EntryPointFactory getEntryPointFactory() {
        return new ReliableNamedEntryPointFactory();
    }

    @Override
    public InternalWorkingMemory createStatefulSession(RuleBase ruleBase, Environment environment, SessionConfiguration sessionConfig, boolean fromPool) {
        InternalKnowledgeBase kbase = (InternalKnowledgeBase) ruleBase;
        if (fromPool || kbase.getSessionPool() == null) {
            StatefulKnowledgeSessionImpl session = (StatefulKnowledgeSessionImpl) getWorkingMemoryFactory()
                    .createWorkingMemory(kbase.nextWorkingMemoryCounter(), kbase, sessionConfig, environment);
            return internalInitSession(kbase, sessionConfig, session);
        }
        return (InternalWorkingMemory) kbase.getSessionPool().newKieSession(sessionConfig);
    }

    private StatefulKnowledgeSessionImpl internalInitSession(InternalKnowledgeBase kbase, SessionConfiguration sessionConfig, StatefulKnowledgeSessionImpl session) {
        if (sessionConfig.isKeepReference()) {
            kbase.addStatefulSession(session);
        }

        // re-propagate objects from the cache to the new session
        ReliableObjectStore reliableObjectStore = (ReliableObjectStore) session.getObjectStore();
        reliableObjectStore.replayObjectStoreEventFromCache(session);

        return session;
    }

    @Override
    public int servicePriority() {
        return 1;
    }
}
