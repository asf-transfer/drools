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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.naming.InitialContext;
import jakarta.transaction.UserTransaction;

import org.drools.commands.ChainableRunner;
import org.drools.commands.impl.CommandBasedStatefulKnowledgeSessionImpl;
import org.drools.commands.impl.FireAllRulesInterceptor;
import org.drools.commands.impl.LoggingInterceptor;
import org.drools.core.FlowSessionConfiguration;
import org.drools.core.SessionConfiguration;
import org.drools.core.impl.RuleBaseFactory;
import org.drools.mvel.compiler.Address;
import org.drools.mvel.compiler.Person;
import org.drools.persistence.PersistableRunner;
import org.drools.persistence.util.DroolsPersistenceUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.internal.command.CommandFactory;
import org.kie.internal.persistence.jpa.JPAKnowledgeService;
import org.kie.internal.runtime.StatefulKnowledgeSession;
import org.kie.internal.utils.ChainedProperties;
import org.kie.internal.utils.KieHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.fail;
import static org.drools.persistence.util.DroolsPersistenceUtil.DROOLS_PERSISTENCE_UNIT_NAME;
import static org.drools.persistence.util.DroolsPersistenceUtil.OPTIMISTIC_LOCKING;
import static org.drools.persistence.util.DroolsPersistenceUtil.PESSIMISTIC_LOCKING;
import static org.drools.persistence.util.DroolsPersistenceUtil.createEnvironment;

public class JpaPersistentStatefulSessionTest {

    private Map<String, Object> context;
    private Environment env;

    public static Stream<String> parameters() {
    	return Stream.of(OPTIMISTIC_LOCKING, PESSIMISTIC_LOCKING);
    };

    @BeforeEach
    public void setUp() throws Exception {
        context = DroolsPersistenceUtil.setupWithPoolingDataSource(DROOLS_PERSISTENCE_UNIT_NAME);
        env = createEnvironment(context);
    }

    private void setLocking(String locking) {
        if (PESSIMISTIC_LOCKING.equals(locking)) {
            env.set(EnvironmentName.USE_PESSIMISTIC_LOCKING, true);
        }
    	
    }
    
    @AfterEach
    public void tearDown() throws Exception {
        DroolsPersistenceUtil.cleanUp(context);
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testFactHandleSerialization(String locking) {
    	setLocking(locking);
        factHandleSerialization(false);
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testFactHandleSerializationWithOOPath(String locking) {
    	setLocking(locking);
        factHandleSerialization(true);
    }

    private void factHandleSerialization(final boolean withOOPath) {
        final String str = "package org.kie.test\n" +
                "import java.util.concurrent.atomic.AtomicInteger\n" +
                "global java.util.List list\n" +
                "rule rule1\n" +
                "when\n" +
                (withOOPath ? " AtomicInteger($i: /intValue[this > 0])\n" : " $i: AtomicInteger(intValue > 0)\n") +
                "then\n" +
                " list.add( $i );\n" +
                "end\n" +
                "\n";

        final KieBase kbase = new KieHelper().addContent(str, ResourceType.DRL).build();

        KieSession ksession = kbase.newKieSession();//KieServices.get().getStoreServices().newKieSession(kbase, null, env);
        List<AtomicInteger> list = new ArrayList<>();

        ksession.setGlobal("list", list);

        final AtomicInteger value = new AtomicInteger(4);
        FactHandle atomicFH = ksession.insert(value);

        ksession.fireAllRules();

        assertThat(list).hasSize(1);

        value.addAndGet(1);
        ksession.update(atomicFH, value);
        ksession.fireAllRules();

        assertThat(list).hasSize(2);
        final String externalForm = atomicFH.toExternalForm();

        //ksession = KieServices.get().getStoreServices().loadKieSession(ksession.getIdentifier(), kbase, null, env);

        atomicFH = ksession.execute(CommandFactory.fromExternalFactHandleCommand(externalForm));

        value.addAndGet(1);
        ksession.update(atomicFH, value);

        ksession.fireAllRules();

        list = (List<AtomicInteger>) ksession.getGlobal("list");

        assertThat(list).hasSize(3);
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testLocalTransactionPerStatement(String locking) {
    	setLocking(locking);
        localTransactionPerStatement(false);
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testLocalTransactionPerStatementWithOOPath(String locking) {
    	setLocking(locking);
        localTransactionPerStatement(true);
    }

    private void localTransactionPerStatement(final boolean withOOPath) {
        final String rule = getSimpleRule(withOOPath);

        final KieBase kbase = new KieHelper().addContent(rule, ResourceType.DRL).build();

        final KieSession ksession = KieServices.get().getStoreServices().newKieSession(kbase, null, env);
        final List<Integer> list = new ArrayList<>();

        ksession.setGlobal("list", list);

        insertIntRange(ksession, 1, 3);

        ksession.fireAllRules();

        assertThat(list).hasSize(3);
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testUserTransactions(String locking) throws Exception {
    	setLocking(locking);
        userTransactions(false);
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testUserTransactionsWithOOPath() throws Exception {
        userTransactions(true);
    }

    private void userTransactions(final boolean withOOPath) throws Exception {
        final String str = "package org.kie.test\n" +
                "global java.util.List list\n" +
                "rule rule1\n" +
                "when\n" +
                (withOOPath ? " $i: Integer( /intValue[this > 0])\n" : " $i : Integer(intValue > 0)\n") +
                "then\n" +
                " list.add( $i );\n" +
                "end\n";

        final KieBase kbase = new KieHelper().addContent(str, ResourceType.DRL).build();

        UserTransaction ut = (UserTransaction) new InitialContext().lookup("java:comp/UserTransaction");
        ut.begin();
        KieSession ksession = KieServices.get().getStoreServices().newKieSession(kbase, null, env);
        ut.commit();

        final List<Integer> list = new ArrayList<>();

        // insert and commit
        ut = (UserTransaction) new InitialContext().lookup("java:comp/UserTransaction");
        ut.begin();
        ksession.setGlobal("list", list);
        insertIntRange(ksession, 1, 2);
        ksession.fireAllRules();
        ut.commit();

        // insert and rollback
        ut = (UserTransaction) new InitialContext().lookup("java:comp/UserTransaction");
        ut.begin();
        ksession.insert(3);
        ut.rollback();

        // check we rolled back the state changes from the 3rd insert
        ut = (UserTransaction) new InitialContext().lookup("java:comp/UserTransaction");
        ut.begin();
        ksession.fireAllRules();
        ut.commit();
        assertThat(list).hasSize(2);

        // insert and commit
        ut = (UserTransaction) new InitialContext().lookup("java:comp/UserTransaction");
        ut.begin();
        insertIntRange(ksession, 3, 4);
        ut.commit();

        // rollback again, this is testing that we can do consecutive rollbacks and commits without issue
        ut = (UserTransaction) new InitialContext().lookup("java:comp/UserTransaction");
        ut.begin();
        insertIntRange(ksession, 5, 6);
        ut.rollback();

        ksession.fireAllRules();

        assertThat(list).hasSize(4);

        // now load the ksession
        ksession = JPAKnowledgeService.loadStatefulKnowledgeSession(ksession.getIdentifier(), kbase, null, env);

        ut = (UserTransaction) new InitialContext().lookup("java:comp/UserTransaction");
        ut.begin();

        insertIntRange(ksession, 7, 8);
        ut.commit();

        ksession.fireAllRules();

        assertThat(list).hasSize(6);
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testInterceptor() {
        interceptor(false);
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testInterceptorWithOOPath() {
        interceptor(true);
    }

    private void interceptor(final boolean withOOPath) {
        final String str = getSimpleRule(withOOPath);

        final KieBase kbase = new KieHelper().addContent(str, ResourceType.DRL).build();

        final KieSession ksession = KieServices.get().getStoreServices().newKieSession(kbase, null, env);
        final PersistableRunner sscs = (PersistableRunner) ((CommandBasedStatefulKnowledgeSessionImpl) ksession).getRunner();
        sscs.addInterceptor(new LoggingInterceptor());
        sscs.addInterceptor(new FireAllRulesInterceptor());
        sscs.addInterceptor(new LoggingInterceptor());
        final List<Integer> list = new ArrayList<>();
        ksession.setGlobal("list", list);
        insertIntRange(ksession, 1, 3);
        ksession.getWorkItemManager().completeWorkItem(0, null);
        assertThat(list).hasSize(3);
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testInterceptorOnRollback() throws Exception {
        interceptorOnRollback(false);
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testInterceptorOnRollbackWithOOPAth() throws Exception {
        interceptorOnRollback(true);
    }

    private void interceptorOnRollback(final boolean withOOPath) throws Exception {
        final String str = getSimpleRule(withOOPath);

        final KieBase kbase = new KieHelper().addContent(str, ResourceType.DRL).build();

        final KieSession ksession = KieServices.get().getStoreServices().newKieSession(kbase, null, env);
        final PersistableRunner sscs = (PersistableRunner) ((CommandBasedStatefulKnowledgeSessionImpl) ksession).getRunner();
        sscs.addInterceptor(new LoggingInterceptor());
        sscs.addInterceptor(new FireAllRulesInterceptor());
        sscs.addInterceptor(new LoggingInterceptor());

        ChainableRunner runner = sscs.getChainableRunner();

        assertThat(runner.getClass()).isEqualTo(LoggingInterceptor.class);
        runner = (ChainableRunner) runner.getNext();
        assertThat(runner.getClass()).isEqualTo(FireAllRulesInterceptor.class);
        runner = (ChainableRunner) runner.getNext();
        assertThat(runner.getClass()).isEqualTo(LoggingInterceptor.class);

        final UserTransaction ut = InitialContext.doLookup("java:comp/UserTransaction");
        ut.begin();
        final List<?> list = new ArrayList<>();
        ksession.setGlobal("list", list);
        ksession.insert(1);
        ut.rollback();

        ksession.insert(3);

        runner = sscs.getChainableRunner();

        assertThat(runner.getClass()).isEqualTo(LoggingInterceptor.class);
        runner = (ChainableRunner) runner.getNext();
        assertThat(runner.getClass()).isEqualTo(FireAllRulesInterceptor.class);
        runner = (ChainableRunner) runner.getNext();
        assertThat(runner.getClass()).isEqualTo(LoggingInterceptor.class);

    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testSetFocus() {
        testFocus(false);
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testSetFocusWithOOPath() {
        testFocus(true);
    }

    private void testFocus(final boolean withOOPath) {
        final String str = "package org.kie.test\n" +
                "global java.util.List list\n" +
                "rule rule1\n" +
                "agenda-group \"badfocus\"" +
                "when\n" +
                (withOOPath ? "  Integer(/intValue[this > 0])\n" : "  Integer(intValue > 0)\n") +
                "then\n" +
                "  list.add( 1 );\n" +
                "end\n" +
                "\n";

        final KieBase kbase = new KieHelper().addContent(str, ResourceType.DRL).build();

        final KieSession ksession = KieServices.get().getStoreServices().newKieSession(kbase, null, env);
        final List<Integer> list = new ArrayList<>();

        ksession.setGlobal("list", list);

        insertIntRange(ksession, 1, 3);
        ksession.getAgenda().getAgendaGroup("badfocus").setFocus();

        ksession.fireAllRules();

        assertThat(list).hasSize(3);
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testSharedReferences() {
        final KieBase kbase = new KieHelper().getKieContainer().getKieBase();

        final KieSession ksession = KieServices.get().getStoreServices().newKieSession(kbase, null, env);

        final Person x = new Person("test");
        final List<Person> test = new ArrayList<>();
        final List<Person> test2 = new ArrayList<>();
        test.add(x);
        test2.add(x);

        assertThat(test.get(0)).isSameAs(test2.get(0));

        ksession.insert(test);
        ksession.insert(test2);
        ksession.fireAllRules();

        final StatefulKnowledgeSession ksession2 = JPAKnowledgeService.loadStatefulKnowledgeSession(ksession.getIdentifier(), kbase, null, env);

        final Iterator c = ksession2.getObjects().iterator();
        final List ref1 = (List) c.next();
        final List ref2 = (List) c.next();

        assertThat(ref1.get(0)).isSameAs(ref2.get(0));

    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testMergeConfig() {
        // JBRULES-3155
        final KieBase kbase = new KieHelper().getKieContainer().getKieBase();

        final Properties properties = new Properties();
        properties.put("drools.processInstanceManagerFactory", "com.example.CustomJPAProcessInstanceManagerFactory");
        final KieSessionConfiguration config = RuleBaseFactory.newKnowledgeSessionConfiguration(ChainedProperties.getChainedProperties(null).addProperties(properties), null);

        final KieSession ksession = KieServices.get().getStoreServices().newKieSession(kbase, config, env);
        final SessionConfiguration sessionConfig = ksession.getSessionConfiguration().as(SessionConfiguration.KEY);

        assertThat(sessionConfig.as(FlowSessionConfiguration.KEY).getProcessInstanceManagerFactory()).isEqualTo("com.example.CustomJPAProcessInstanceManagerFactory");
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testCreateAndDestroySession() {
        assertThatIllegalStateException().isThrownBy(() -> createAndDestroySession(false));
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testCreateAndDestroySessionWithOOPath() {
        assertThatIllegalStateException().isThrownBy(() -> createAndDestroySession(true));
    }

    public void createAndDestroySession(final boolean withOOPath) {
        final String str = getSimpleRule(withOOPath);

        final KieBase kbase = new KieHelper().addContent(str, ResourceType.DRL).build();

        final KieSession ksession = KieServices.get().getStoreServices().newKieSession(kbase, null, env);
        final List<Integer> list = new ArrayList<>();

        ksession.setGlobal("list", list);

        insertIntRange(ksession, 1, 3);

        ksession.fireAllRules();

        assertThat(list).hasSize(3);

        final long ksessionId = ksession.getIdentifier();
        ksession.destroy();

        JPAKnowledgeService.loadStatefulKnowledgeSession(ksessionId, kbase, null, env);
        fail("There should not be any session with id " + ksessionId);

    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testCreateAndDestroyNonPersistentSession() {
        assertThatIllegalStateException().isThrownBy(() -> createAndDestroyNonPersistentSession(true));
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testCreateAndDestroyNonPersistentSessionWithOOPath() {
        assertThatIllegalStateException().isThrownBy(() -> createAndDestroyNonPersistentSession(true));
    }

    private void createAndDestroyNonPersistentSession(final boolean withOOPath) {
        final String str = getSimpleRule(withOOPath);

        final KieBase kbase = new KieHelper().addContent(str, ResourceType.DRL).build();

        final KieSession ksession = kbase.newKieSession();
        final List<Integer> list = new ArrayList<>();

        ksession.setGlobal("list",
                list);

        insertIntRange(ksession, 1, 3);

        ksession.fireAllRules();

        assertThat(list).hasSize(3);

        final long ksessionId = ksession.getIdentifier();
        ksession.destroy();

        ksession.fireAllRules();
        fail("Session should already be disposed " + ksessionId);
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testFromNodeWithModifiedCollection() {
        fromNodeWithModifiedCollection(false);
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testFromNodeWithModifiedCollectionWithOOPath() {
        fromNodeWithModifiedCollection(true);
    }

    private void fromNodeWithModifiedCollection(final boolean withOOPath) {
        // DROOLS-376
        final String str = "package org.drools.test\n" +
                "import org.drools.mvel.compiler.Person\n" +
                "import org.drools.mvel.compiler.Address\n" +
                "rule rule1\n" +
                "when\n" +
                (withOOPath ?
                        " $p: Person($list : addresses, /addresses[street == \"y\"])\n" :
                        " $p: Person($list : addresses)\n" + " $a: Address(street == \"y\") from $list\n"
                ) +
                "then\n" +
                " $list.add( new Address(\"z\") );\n" +
                " $list.add( new Address(\"w\") );\n" +
                "end\n";

        final KieBase kbase = new KieHelper().addContent(str, ResourceType.DRL).build();

        final KieSession ksession = KieServices.get().getStoreServices().newKieSession(kbase, null, env);

        final Person p1 = new Person("John");
        p1.addAddress(new Address("x"));
        p1.addAddress(new Address("y"));

        ksession.insert(p1);

        ksession.fireAllRules();

        assertThat(p1.getAddresses()).hasSize(4);

        ksession.dispose();

        // Should not fail here
    }

    private String getSimpleRule(final boolean withOOPath) {
        return "package org.kie.test\n" +
                "global java.util.List list\n" +
                "rule rule1\n" +
                "when\n" +
                (withOOPath ? "  Integer(/intValue[this > 0])\n" : "  Integer(intValue > 0)\n") +
                "then\n" +
                "  list.add( 1 );\n" +
                "end\n" +
                "\n";
    }

    /**
     * Insert integer range into session
     *
     * @param ksession Session to insert ints in
     * @param from start of the range of ints to be inserted to ksession (inclusive)
     * @param to end of the range of ints to be inserted to ksession (inclusive)
     */
    private void insertIntRange(final KieSession ksession, final int from, final int to){
        IntStream.rangeClosed(from, to).forEach(ksession::insert);
    }
}
