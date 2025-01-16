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
package org.drools.persistence.timer.integrationtests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.drools.core.ClockType;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.core.impl.RuleBaseFactory;
import org.drools.kiesession.rulebase.KnowledgeBaseFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.definition.KiePackage;
import org.kie.api.definition.type.FactType;
import org.kie.api.io.Resource;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.kie.api.time.SessionPseudoClock;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderError;
import org.kie.internal.builder.KnowledgeBuilderErrors;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.persistence.jpa.JPAKnowledgeService;
import org.kie.internal.runtime.StatefulKnowledgeSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.drools.persistence.util.DroolsPersistenceUtil.DROOLS_PERSISTENCE_UNIT_NAME;
import static org.drools.persistence.util.DroolsPersistenceUtil.OPTIMISTIC_LOCKING;
import static org.drools.persistence.util.DroolsPersistenceUtil.PESSIMISTIC_LOCKING;
import static org.drools.persistence.util.DroolsPersistenceUtil.cleanUp;
import static org.drools.persistence.util.DroolsPersistenceUtil.createEnvironment;
import static org.drools.persistence.util.DroolsPersistenceUtil.setupWithPoolingDataSource;

public class TimerAndCalendarTest {
    
    private Map<String, Object> context;

    public static Stream<String> parameters() {
    	return Stream.of(OPTIMISTIC_LOCKING, PESSIMISTIC_LOCKING);
    };
    
    @BeforeEach
    public void before() throws Exception {
        context = setupWithPoolingDataSource(DROOLS_PERSISTENCE_UNIT_NAME);
    }

    @AfterEach
    public void after() throws Exception {
        cleanUp(context);
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    @Disabled("beta4 phreak")
    public void testTimerRuleAfterIntReloadSession(String locking) throws Exception {
        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        KieSession ksession = createSession(locking, kbase);

        // must advance time or it won't save.
        SessionPseudoClock clock = (SessionPseudoClock) ksession.getSessionClock();
        clock.advanceTime( 300,
                           TimeUnit.MILLISECONDS );

        // if we do not call 'ksession.fireAllRules()', this test will run successfully.
        ksession.fireAllRules();

        clock = (SessionPseudoClock) ksession.getSessionClock();
        clock.advanceTime( 300,
                           TimeUnit.MILLISECONDS );

        ksession = disposeAndReloadSession( ksession,
                                            kbase );

        clock = (SessionPseudoClock) ksession.getSessionClock();
        clock.advanceTime( 300,
                           TimeUnit.MILLISECONDS );

        // build timer rule, if the rule is fired, the list size will increase every 500ms
        String timerRule = "package org.drools.test\n" +
                           "global java.util.List list \n" +
                           "rule TimerRule \n" +
                           "   timer (int:1000 500) \n" +
                           "when \n" + "then \n" +
                           "        list.add(list.size()); \n" +
                           " end";
        Resource resource = ResourceFactory.newByteArrayResource(timerRule.getBytes());
        Collection<KiePackage> kpackages = buildKnowledgePackage( resource,
                                                                        ResourceType.DRL );
        kbase.addPackages( kpackages );

        clock = (SessionPseudoClock) ksession.getSessionClock();
        clock.advanceTime( 10,
                           TimeUnit.MILLISECONDS );
        ksession.fireAllRules();

        clock = (SessionPseudoClock) ksession.getSessionClock();
        clock.advanceTime( 10,
                           TimeUnit.MILLISECONDS );

        ksession = disposeAndReloadSession( ksession,
                                            kbase );

        clock = (SessionPseudoClock) ksession.getSessionClock();
        clock.advanceTime( 10,
                           TimeUnit.MILLISECONDS );

        List<Integer> list = Collections.synchronizedList( new ArrayList<Integer>() );
        ksession.setGlobal( "list",
                            list );
        assertThat(list.size()).isEqualTo(0);

        clock = (SessionPseudoClock) ksession.getSessionClock();
        clock.advanceTime( 1700,
                           TimeUnit.MILLISECONDS );
        assertThat(list.size()).isEqualTo(2);

        ksession = disposeAndReloadSession( ksession,
                                            kbase );
        ksession.setGlobal( "list",
                            list );

        clock = (SessionPseudoClock) ksession.getSessionClock();
        clock.advanceTime( 1000,
                           TimeUnit.MILLISECONDS );

        // if the rule is fired, the list size will greater than one.
        assertThat(list.size()).isEqualTo(4);
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    @Disabled("beta4 phreak")
    public void testTimerRuleAfterCronReloadSession(String locking) throws Exception {
        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        KieSession ksession = createSession(locking, kbase);

        // must advance time or it won't save.
        SessionPseudoClock clock = (SessionPseudoClock) ksession.getSessionClock();
        clock.advanceTime( 300,
                           TimeUnit.MILLISECONDS );

        // if we do not call 'ksession.fireAllRules()', this test will run successfully.
        ksession.fireAllRules();

        clock = (SessionPseudoClock) ksession.getSessionClock();
        clock.advanceTime( 300,
                           TimeUnit.MILLISECONDS );

        ksession = disposeAndReloadSession( ksession,
                                            kbase );

        clock = (SessionPseudoClock) ksession.getSessionClock();
        clock.advanceTime( 300,
                           TimeUnit.MILLISECONDS );

        // build timer rule, if the rule is fired, the list size will increase every 300ms
        String timerRule = "package org.drools.test\n" +
                           "global java.util.List list \n" +
                           "rule TimerRule \n" +
                           "   timer (cron: * * * * * ?) \n" +
                           "when \n" + "then \n" +
                           "        list.add(list.size()); \n" +
                           " end";
        Resource resource = ResourceFactory.newByteArrayResource( timerRule.getBytes() );
        Collection<KiePackage> kpackages = buildKnowledgePackage( resource,
                                                                        ResourceType.DRL );
        kbase.addPackages( kpackages );

        List<Integer> list = Collections.synchronizedList( new ArrayList<Integer>() );
        ksession.setGlobal( "list",
                            list );

        ksession.setGlobal( "list",
                            list );
        clock.advanceTime( 10,
                           TimeUnit.MILLISECONDS );
        ksession.fireAllRules();

        clock = (SessionPseudoClock) ksession.getSessionClock();
        clock.advanceTime( 10,
                           TimeUnit.MILLISECONDS );

        ksession = disposeAndReloadSession( ksession,
                                            kbase );
        ksession.setGlobal( "list",
                            list );

        clock = (SessionPseudoClock) ksession.getSessionClock();
        clock.advanceTime( 10,
                           TimeUnit.MILLISECONDS );

        assertThat(list.size()).isEqualTo(1);

        clock = (SessionPseudoClock) ksession.getSessionClock();
        clock.advanceTime( 3,
                           TimeUnit.SECONDS );
        assertThat(list.size()).isEqualTo(4);

        ksession = disposeAndReloadSession( ksession,
                                            kbase );
        ksession.setGlobal( "list",
                            list );

        clock = (SessionPseudoClock) ksession.getSessionClock();
        clock.advanceTime( 2,
                           TimeUnit.SECONDS );

        // if the rule is fired, the list size will greater than one.
        assertThat(list.size()).isEqualTo(6);
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testEventExpires(String locking) throws Exception {
        String timerRule = "package org.drools.test\n" +
                           "declare TestEvent \n" +
                           "    @role( event )\n" +
                           "    @expires( 10s )\n" +
                           "end\n" +
                           "" +
                           "rule TimerRule \n" +
                           "    when \n" +
                           "        TestEvent( ) from entry-point \"Test\"\n" +
                           "    then \n" +
                           "end";
        KieBaseConfiguration kbconf = RuleBaseFactory.newKnowledgeBaseConfiguration();
        kbconf.setOption( EventProcessingOption.STREAM );
        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        Resource resource = ResourceFactory.newByteArrayResource( timerRule.getBytes() );
        Collection<KiePackage> kpackages = buildKnowledgePackage( resource,
                                                                        ResourceType.DRL );
        kbase.addPackages( kpackages );
        KieSession ksession = createSession(locking, kbase);

        FactType type = kbase.getFactType( "org.drools.test",
                                           "TestEvent" );
        assertThat(type).as("could not get type").isNotNull();

        ksession = disposeAndReloadSession( ksession,
                                            kbase );
        ksession.getEntryPoint( "Test" ).insert( type.newInstance() );
        ksession.fireAllRules();
        ksession = disposeAndReloadSession( ksession,
                                            kbase );

        ksession = disposeAndReloadSession( ksession,
                                            kbase );
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testTimerWithRemovingRule() throws Exception {
        // DROOLS-576
        // Only reproducible with RETEOO
        InternalKnowledgeBase kbase1 = KnowledgeBaseFactory.newKnowledgeBase();

        String str1 = "package org.test; " +
                "import java.util.*; " +

                "global java.util.List list; " +

                "rule R1\n" +
                "    timer ( int: 5s )\n" +
                "when\n" +
                "    $s : String( )\n" +
                "then\n" +
                "    list.add( $s );\n" +
                "end\n";

        Resource resource1 = ResourceFactory.newByteArrayResource(str1.getBytes());
        Collection<KiePackage> kpackages1 = buildKnowledgePackage( resource1,
                                                                ResourceType.DRL );
        kbase1.addPackages( kpackages1 );

        StatefulKnowledgeSession ksession1 = JPAKnowledgeService.newStatefulKnowledgeSession(kbase1, null,
                createEnvironment(context));
        long ksessionId = ksession1.getIdentifier();

        ArrayList<String> list = new ArrayList<String>();
        ksession1.setGlobal( "list", list );

        ksession1.insert("hello");
        ksession1.fireAllRules();

        ksession1.dispose(); // dispose before firing

        assertThat(list.size()).isEqualTo(0);

        Thread.sleep(5000);

        // A new kbase without the timer's activated rule
        InternalKnowledgeBase kbase2  = KnowledgeBaseFactory.newKnowledgeBase();

        String str2 = "package org.test; " +
                "import java.util.*; " +

                "global java.util.List list; " +

                "rule R2\n" +
                "when\n" +
                "    $s : Integer( )\n" +
                "then\n" +
                "    list.add( $s );\n" +
                "end\n";

        Resource resource2 = ResourceFactory.newByteArrayResource(str2.getBytes());
        Collection<KiePackage> kpackages2 = buildKnowledgePackage( resource2,
                                                                        ResourceType.DRL );
        kbase2.addPackages( kpackages2 );

        StatefulKnowledgeSession ksession2 = JPAKnowledgeService.loadStatefulKnowledgeSession(ksessionId, kbase2, null,
                createEnvironment(context));

        ksession2.setGlobal( "list", list );

        ksession2.fireAllRules();

        ksession2.dispose();

        assertThat(list.size()).isEqualTo(0);
    }

    private KieSession createSession(String locking, KieBase kbase) {
        final KieSessionConfiguration conf = RuleBaseFactory.newKnowledgeSessionConfiguration();
        conf.setOption( ClockTypeOption.get( ClockType.PSEUDO_CLOCK.getId() ) );
        Environment env = createEnvironment(context);
        if(PESSIMISTIC_LOCKING.equals(locking)) { 
            env.set(EnvironmentName.USE_PESSIMISTIC_LOCKING, true);
        }
        StatefulKnowledgeSession ksession = JPAKnowledgeService.newStatefulKnowledgeSession( kbase,
                                                                                             conf,
                                                                                             env );
        return ksession;
    }

    private KieSession disposeAndReloadSession(KieSession ksession, KieBase kbase) {
        long ksessionId = ksession.getIdentifier();
        ksession.dispose();

        final KieSessionConfiguration conf = RuleBaseFactory.newKnowledgeSessionConfiguration();
        conf.setOption( ClockTypeOption.get( ClockType.PSEUDO_CLOCK.getId() ) );

        StatefulKnowledgeSession newksession = JPAKnowledgeService.loadStatefulKnowledgeSession( ksessionId,
                                                                                                 kbase,
                                                                                                 conf,
                                                                                                 createEnvironment(context) );
        return newksession;
    }

    private Collection<KiePackage> buildKnowledgePackage(Resource resource,
                                                               ResourceType resourceType) {
        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add( resource,
                      resourceType );
        KnowledgeBuilderErrors errors = kbuilder.getErrors();
        if ( errors != null && errors.size() > 0 ) {
            for ( KnowledgeBuilderError error : errors ) {
                System.err.println( "Error: " + error.getMessage() );
            }
            fail( "KnowledgeBase did not build" );
        }
        Collection<KiePackage> packages = kbuilder.getKnowledgePackages();
        return packages;
    }

}
