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
package org.drools.persistence.map.impl;

import static org.drools.persistence.util.DroolsPersistenceUtil.OPTIMISTIC_LOCKING;
import static org.drools.persistence.util.DroolsPersistenceUtil.PESSIMISTIC_LOCKING;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.drools.persistence.api.PersistentSession;
import org.drools.persistence.api.PersistentWorkItem;
import org.drools.persistence.map.EnvironmentBuilder;
import org.drools.persistence.map.KnowledgeSessionStorage;
import org.drools.persistence.map.KnowledgeSessionStorageEnvironmentBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.internal.persistence.jpa.JPAKnowledgeService;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.KieSession;

public class MapBasedPersistenceTest extends MapPersistenceTest{
    
    private SimpleKnowledgeSessionStorage storage;
    
    public static Stream<String> parameters() {
        return Stream.of("not relevant");
    };

    @BeforeEach
    public void createStorage(){
        storage = new SimpleKnowledgeSessionStorage();
    }
    
    @Override
    protected KieSession createSession(String locking, KieBase kbase) {
        
        EnvironmentBuilder envBuilder = new KnowledgeSessionStorageEnvironmentBuilder( storage );
        Environment env = KieServices.Factory.get().newEnvironment();
        env.set( EnvironmentName.TRANSACTION_MANAGER,
                 envBuilder.getTransactionManager() );
        env.set( EnvironmentName.PERSISTENCE_CONTEXT_MANAGER,
                 envBuilder.getPersistenceContextManager() );

        return JPAKnowledgeService.newStatefulKnowledgeSession( kbase,
                                                                null,
                                                                env );
    }
    
    @Override
    protected KieSession disposeAndReloadSession(KieSession ksession, KieBase kbase) {
        long sessionId = ksession.getIdentifier();
        ksession.dispose();
        EnvironmentBuilder envBuilder = new KnowledgeSessionStorageEnvironmentBuilder( storage );
        Environment env = KieServices.Factory.get().newEnvironment();
        env.set( EnvironmentName.TRANSACTION_MANAGER,
                 envBuilder.getTransactionManager() );
        env.set( EnvironmentName.PERSISTENCE_CONTEXT_MANAGER,
                 envBuilder.getPersistenceContextManager() );
        
        return JPAKnowledgeService.loadStatefulKnowledgeSession( sessionId, kbase, null, env );
    }
    
    @Override
    protected long getSavedSessionsCount() {
        return storage.ksessions.size();
    }
    
    private static class SimpleKnowledgeSessionStorage
        implements
        KnowledgeSessionStorage {

        public Map<Long, PersistentSession>  ksessions = new HashMap<Long, PersistentSession>();
        public Map<Long, PersistentWorkItem> workItems = new HashMap<Long, PersistentWorkItem>();

        public PersistentSession findSessionInfo(Long id) {
            return ksessions.get( id );
        }

        public void saveOrUpdate(PersistentSession storedObject) {
            ksessions.put( storedObject.getId(),
                           storedObject );
        }

        public void saveOrUpdate(PersistentWorkItem workItem) {
            workItems.put( workItem.getId(),
                           workItem );
        }

        public Long getNextWorkItemId() {
            return new Long( workItems.size() + 1 );
        }

        public PersistentWorkItem findWorkItemInfo(Long id) {
            return workItems.get( id );
        }

        public void remove(PersistentWorkItem workItem) {
            workItems.remove( workItem.getId() );
        }

        public Long getNextStatefulKnowledgeSessionId() {
            return new Long(ksessions.size() + 1 );
        }

        public void lock(PersistentSession session) {
            throw new UnsupportedOperationException("Map based persistence does not support locking.");
        }

        @Override
        public void lock(PersistentWorkItem workItem) {
            throw new UnsupportedOperationException("Map based persistence does not support locking.");
        }
    }
}
