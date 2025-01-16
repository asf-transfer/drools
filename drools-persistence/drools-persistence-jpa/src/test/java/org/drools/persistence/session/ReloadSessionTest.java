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
package org.drools.persistence.session;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.drools.core.common.DefaultFactHandle;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.kiesession.rulebase.KnowledgeBaseFactory;
import org.drools.persistence.api.PersistenceContextManager;
import org.drools.persistence.util.DroolsPersistenceUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.event.rule.DefaultRuleRuntimeEventListener;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.persistence.jpa.JPAKnowledgeService;
import org.kie.internal.runtime.StatefulKnowledgeSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.drools.persistence.util.DroolsPersistenceUtil.DROOLS_PERSISTENCE_UNIT_NAME;
import static org.drools.persistence.util.DroolsPersistenceUtil.OPTIMISTIC_LOCKING;
import static org.drools.persistence.util.DroolsPersistenceUtil.PESSIMISTIC_LOCKING;
import static org.kie.api.runtime.EnvironmentName.ENTITY_MANAGER_FACTORY;

public class ReloadSessionTest {

    // Datasource (setup & clean up)
    private Map<String, Object> context;
    private EntityManagerFactory emf;

    private static final String ENTRY_POINT = "ep1";

    private static String simpleRule =
              "package org.kie.test\n"
            + "global java.util.List list\n" 
            + "rule rule1\n" 
            + "when\n"
            + "  Integer(intValue > 0)\n" 
            + "then\n" 
            + "  list.add( 1 );\n"
            + "end\n" 
            + "\n";

    private static final String RULE_WITH_EP =
              "package org.kie.test\n"
            + "global java.util.List list\n"
            + "rule rule1\n"
            + "when\n"
            + "  Integer(intValue > 0) from entry-point " + ENTRY_POINT + " \n"
            + "then\n"
            + "  list.add( 1 );\n"
            + "end\n"
            + "\n";

    public static Stream<String> parameters() {
        return Stream.of(OPTIMISTIC_LOCKING, PESSIMISTIC_LOCKING);
    };
    
    @BeforeEach
    public void setup() {
        context = DroolsPersistenceUtil.setupWithPoolingDataSource(DROOLS_PERSISTENCE_UNIT_NAME);
        emf = (EntityManagerFactory) context.get(ENTITY_MANAGER_FACTORY);

    }

    @AfterEach
    public void cleanUp() {
        DroolsPersistenceUtil.cleanUp(context);
    }

    private Environment createEnvironment(String locking) { 
        Environment env = DroolsPersistenceUtil.createEnvironment(context);
        if( PESSIMISTIC_LOCKING.equals(locking) ) { 
            env.set(EnvironmentName.USE_PESSIMISTIC_LOCKING, true);
        }
        return env;
    }
    private KieBase initializeKnowledgeBase(String rule) { 
        // Initialize knowledge base/session/etc..
        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add( ResourceFactory.newByteArrayResource(rule.getBytes()), ResourceType.DRL);

        if (kbuilder.hasErrors()) {
            fail(kbuilder.getErrors().toString());
        }

        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        kbase.addPackages(kbuilder.getKnowledgePackages());
       
        return kbase;
    }
    
    @ParameterizedTest(name = "{0}")
    @MethodSource("parameters")
    public void reloadKnowledgeSessionTest(String locking) { 
        
        // Initialize drools environment stuff
        Environment env = createEnvironment(locking);
        KieBase kbase = initializeKnowledgeBase(simpleRule);
        StatefulKnowledgeSession commandKSession = JPAKnowledgeService.newStatefulKnowledgeSession( kbase, null, env );
        assertThat(commandKSession.getFactHandles().isEmpty()).as("There should be NO facts present in a new (empty) knowledge session.").isTrue();
        
        // Persist a facthandle to the database
        Integer integerFact = (new Random()).nextInt(Integer.MAX_VALUE-1) + 1;
        commandKSession.insert( integerFact );
       
        // At this point in the code, the fact has been persisted to the database
        //  (within a transaction via the PersistableRunner)
        Collection<FactHandle> factHandles =  commandKSession.getFactHandles();
        assertThat(!factHandles.isEmpty()).as("At least one fact should have been inserted by the ksession.insert() method above.").isTrue();
        FactHandle origFactHandle = factHandles.iterator().next();
        assertThat(Integer.parseInt(((DefaultFactHandle) origFactHandle).getObject().toString()) == integerFact.intValue()).as("The stored fact should contain the same number as the value inserted (but does not).").isTrue();
        
        // Save the sessionInfo id in order to retrieve it later
        long sessionInfoId = commandKSession.getIdentifier();
        
        // Clean up the session, environment, etc.
        PersistenceContextManager pcm = (PersistenceContextManager) commandKSession.getEnvironment().get(EnvironmentName.PERSISTENCE_CONTEXT_MANAGER);
        commandKSession.dispose();
        pcm.dispose();
        emf.close();
     
        // Reload session from the database
        emf = Persistence.createEntityManagerFactory(DROOLS_PERSISTENCE_UNIT_NAME);
        context.put(ENTITY_MANAGER_FACTORY, emf);
        env = createEnvironment(locking);
       
        // Re-initialize the knowledge session:
        StatefulKnowledgeSession newCommandKSession
            = JPAKnowledgeService.loadStatefulKnowledgeSession(sessionInfoId, kbase, null, env);
       
        // Test that the session has been successfully reinitialized
        factHandles =  newCommandKSession.getFactHandles();
        assertThat(!factHandles.isEmpty()).as("At least one fact should have been persisted by the ksession.insert above.").isTrue();
        FactHandle retrievedFactHandle = factHandles.iterator().next();
        assertThat(origFactHandle != retrievedFactHandle).as("If the retrieved and original FactHandle object are the same, then the knowledge session has NOT been reloaded!").isTrue();
        assertThat(Integer.parseInt(((DefaultFactHandle) retrievedFactHandle).getObject().toString()) == integerFact.intValue()).as("The retrieved fact should contain the same info as the original (but does not).").isTrue();
        
        // Test to see if the (retrieved) facts can be processed
        ArrayList<Object>list = new ArrayList<Object>();
        newCommandKSession.setGlobal( "list", list );
        newCommandKSession.fireAllRules();
        assertThat(list.size()).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parameters")
    @Disabled
    public void testListenersAfterSessionReload(String locking) {
        // https://bugzilla.redhat.com/show_bug.cgi?id=826952
        Environment env = createEnvironment(locking);
        KieBase kbase = initializeKnowledgeBase(simpleRule);
        StatefulKnowledgeSession ksession = JPAKnowledgeService.newStatefulKnowledgeSession( kbase, null, env );

        ksession.addEventListener(new DefaultAgendaEventListener());
        ksession.addEventListener(new DefaultRuleRuntimeEventListener());

        assertThat(ksession.getRuleRuntimeEventListeners().size()).isEqualTo(1);
        assertThat(ksession.getAgendaEventListeners().size()).isEqualTo(1);

        ksession = JPAKnowledgeService.loadStatefulKnowledgeSession(ksession.getIdentifier(), kbase, null, env);

        assertThat(ksession.getRuleRuntimeEventListeners().size()).isEqualTo(1);
        assertThat(ksession.getAgendaEventListeners().size()).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parameters")
    public void testInsert(String locking) {
        final Environment env = createEnvironment(locking);
        final KieBase kbase = initializeKnowledgeBase( RULE_WITH_EP );
        KieSession kieSession = JPAKnowledgeService.newStatefulKnowledgeSession( kbase, null, env );
        assertThat(kieSession.getFactHandles().isEmpty()).as("There should be NO facts present in a new (empty) knowledge session.").isTrue();

        kieSession.insert(Integer.valueOf(10));
        kieSession = reloadSession(kieSession, env);

        Collection<? extends Object> objects = kieSession.getObjects();
        assertThat(objects.size()).as("Reloaded working memory should contain the fact.").isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parameters")
    public void testInsertIntoEntryPoint(String locking) {
        // RHBRMS-2815
        final Environment env = createEnvironment(locking);
        final KieBase kbase = initializeKnowledgeBase(RULE_WITH_EP);
        KieSession kieSession = JPAKnowledgeService.newStatefulKnowledgeSession(kbase, null, env);
        assertThat(kieSession.getFactHandles().isEmpty()).as("There should be NO facts present in a new (empty) knowledge session.").isTrue();

        kieSession.getEntryPoint(ENTRY_POINT).insert(Integer.valueOf(10));
        kieSession = reloadSession(kieSession, env);

        Collection<? extends Object> objects = kieSession.getEntryPoint(ENTRY_POINT).getObjects();
        assertThat(objects.size()).as("Reloaded working memory should contain the fact in the entry point.").isEqualTo(1);
    }

    private KieSession reloadSession(final KieSession kieSession, final Environment environment) {
        final long sessionId = kieSession.getIdentifier();
        final KieBase kieBase = kieSession.getKieBase();
        kieSession.dispose();
        return JPAKnowledgeService.loadStatefulKnowledgeSession(sessionId, kieBase, null, environment);
    }
}
