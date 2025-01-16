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
package org.drools.persistence.kie.persistence.session;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import javax.naming.InitialContext;
import jakarta.transaction.UserTransaction;

import org.drools.core.FlowSessionConfiguration;
import org.drools.commands.impl.CommandBasedStatefulKnowledgeSessionImpl;
import org.drools.commands.impl.FireAllRulesInterceptor;
import org.drools.commands.impl.LoggingInterceptor;
import org.drools.mvel.compiler.Person;
import org.drools.persistence.PersistableRunner;
import org.drools.persistence.util.DroolsPersistenceUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Results;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.command.Command;
import org.kie.api.definition.type.FactType;
import org.kie.api.definition.type.Position;
import org.kie.api.io.Resource;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.time.SessionPseudoClock;
import org.kie.internal.command.CommandFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.drools.persistence.util.DroolsPersistenceUtil.DROOLS_PERSISTENCE_UNIT_NAME;
import static org.drools.persistence.util.DroolsPersistenceUtil.OPTIMISTIC_LOCKING;
import static org.drools.persistence.util.DroolsPersistenceUtil.PESSIMISTIC_LOCKING;
import static org.drools.persistence.util.DroolsPersistenceUtil.createEnvironment;

public class JpaPersistentStatefulSessionTest {

    private static Logger logger = LoggerFactory.getLogger(JpaPersistentStatefulSessionTest.class);
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
    
    private void setUpLocking(String locking) {
        if(PESSIMISTIC_LOCKING.equals(locking)) { 
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
    	setUpLocking(locking);
        String str = "";
        str += "package org.kie.test\n";
        str += "import java.util.concurrent.atomic.AtomicInteger\n";
        str += "global java.util.List list\n";
        str += "rule rule1\n";
        str += "when\n";
        str += " $i: AtomicInteger(intValue > 0)\n";
        str += "then\n";
        str += " list.add( $i );\n";
        str += "end\n";
        str += "\n";

        KieServices ks = KieServices.Factory.get();

        KieFileSystem kfs = ks.newKieFileSystem().write( "src/main/resources/r1.drl", str );
        ks.newKieBuilder( kfs ).buildAll();

        KieBase kbase = ks.newKieContainer(ks.getRepository().getDefaultReleaseId()).getKieBase();
        KieSession ksession = ks.getStoreServices().newKieSession( kbase, null, env );

        List<?> list = new ArrayList<Object>();

        ksession.setGlobal( "list",
                            list );

        AtomicInteger value = new AtomicInteger(4);
        FactHandle atomicFH = ksession.insert( value );

        ksession.fireAllRules();

        assertThat(list.size()).isEqualTo(1);

        value.addAndGet(1);
        ksession.update(atomicFH, value);
        ksession.fireAllRules();

        assertThat(list.size()).isEqualTo(2);
        String externalForm = atomicFH.toExternalForm();
        
        ksession = ks.getStoreServices().loadKieSession(ksession.getIdentifier(), kbase, null, env);
        
        atomicFH = ksession.execute(CommandFactory.fromExternalFactHandleCommand(externalForm));
        
        value.addAndGet(1);
        ksession.update(atomicFH, value);
        
        ksession.fireAllRules();
        
        list = (List<?>) ksession.getGlobal("list");

        assertThat(list.size()).isEqualTo(3);
        
    }
    
    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testLocalTransactionPerStatement(String locking) {
    	setUpLocking(locking);
        String str = "";
        str += "package org.kie.test\n";
        str += "global java.util.List list\n";
        str += "rule rule1\n";
        str += "when\n";
        str += "  Integer(intValue > 0)\n";
        str += "then\n";
        str += "  list.add( 1 );\n";
        str += "end\n";
        str += "\n";

        KieServices ks = KieServices.Factory.get();

        KieFileSystem kfs = ks.newKieFileSystem().write( "src/main/resources/r1.drl", str );
        ks.newKieBuilder( kfs ).buildAll();

        KieBase kbase = ks.newKieContainer(ks.getRepository().getDefaultReleaseId()).getKieBase();
        KieSession ksession = ks.getStoreServices().newKieSession( kbase, null, env );

        List<?> list = new ArrayList<Object>();

        ksession.setGlobal( "list",
                            list );

        ksession.insert( 1 );
        ksession.insert( 2 );
        ksession.insert( 3 );

        ksession.fireAllRules();

        assertThat(list.size()).isEqualTo(3);

    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testUserTransactions(String locking) throws Exception {
    	setUpLocking(locking);
        String str = "";
        str += "package org.kie.test\n";
        str += "global java.util.List list\n";
        str += "rule rule1\n";
        str += "when\n";
        str += "  $i : Integer(intValue > 0)\n";
        str += "then\n";
        str += "  list.add( $i );\n";
        str += "end\n";
        str += "\n";

        KieServices ks = KieServices.Factory.get();

        KieFileSystem kfs = ks.newKieFileSystem().write( "src/main/resources/r1.drl", str );
        ks.newKieBuilder( kfs ).buildAll();

        KieBase kbase = ks.newKieContainer(ks.getRepository().getDefaultReleaseId()).getKieBase();

        UserTransaction ut = (UserTransaction) new InitialContext().lookup( "java:comp/UserTransaction" );
        ut.begin();
        KieSession ksession = ks.getStoreServices().newKieSession( kbase, null, env );
        ut.commit();

        List<?> list = new ArrayList<Object>();

        // insert and commit
        ut = (UserTransaction) new InitialContext().lookup( "java:comp/UserTransaction" );
        ut.begin();
        ksession.setGlobal( "list",
                            list );
        ksession.insert( 1 );
        ksession.insert( 2 );
        ksession.fireAllRules();
        ut.commit();

        // insert and rollback
        ut = (UserTransaction) new InitialContext().lookup( "java:comp/UserTransaction" );
        ut.begin();
        ksession.insert( 3 );
        ut.rollback();

        // check we rolled back the state changes from the 3rd insert
        ut = (UserTransaction) new InitialContext().lookup( "java:comp/UserTransaction" );
        ut.begin();
        ksession.fireAllRules();
        ut.commit();
        assertThat(list.size()).isEqualTo(2);

        // insert and commit
        ut = (UserTransaction) new InitialContext().lookup( "java:comp/UserTransaction" );
        ut.begin();
        ksession.insert( 3 );
        ksession.insert( 4 );
        ut.commit();

        // rollback again, this is testing that we can do consecutive rollbacks and commits without issue
        ut = (UserTransaction) new InitialContext().lookup( "java:comp/UserTransaction" );
        ut.begin();
        ksession.insert( 5 );
        ksession.insert( 6 );
        ut.rollback();

        ksession.fireAllRules();

        assertThat(list.size()).isEqualTo(4);
        
        // now load the ksession
        ksession = ks.getStoreServices().loadKieSession( ksession.getIdentifier(), kbase, null, env );
        
        ut = (UserTransaction) new InitialContext().lookup( "java:comp/UserTransaction" );
        ut.begin();
        ksession.insert( 7 );
        ksession.insert( 8 );
        ut.commit();

        ksession.fireAllRules();

        assertThat(list.size()).isEqualTo(6);
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testInterceptor(String locking) {
    	setUpLocking(locking);
        String str = "";
        str += "package org.kie.test\n";
        str += "global java.util.List list\n";
        str += "rule rule1\n";
        str += "when\n";
        str += "  Integer(intValue > 0)\n";
        str += "then\n";
        str += "  list.add( 1 );\n";
        str += "end\n";
        str += "\n";

        KieServices ks = KieServices.Factory.get();

        KieFileSystem kfs = ks.newKieFileSystem().write( "src/main/resources/r1.drl", str );
        ks.newKieBuilder( kfs ).buildAll();

        KieBase kbase = ks.newKieContainer(ks.getRepository().getDefaultReleaseId()).getKieBase();

        KieSession ksession = ks.getStoreServices().newKieSession( kbase, null, env );
        PersistableRunner sscs = (PersistableRunner)
            ((CommandBasedStatefulKnowledgeSessionImpl) ksession).getRunner();
        sscs.addInterceptor(new LoggingInterceptor());
        sscs.addInterceptor(new FireAllRulesInterceptor());
        sscs.addInterceptor(new LoggingInterceptor());
        List<?> list = new ArrayList<Object>();
        ksession.setGlobal( "list", list );
        ksession.insert( 1 );
        ksession.insert( 2 );
        ksession.insert( 3 );
        ksession.getWorkItemManager().completeWorkItem(0, null);
        assertThat(list.size()).isEqualTo(3);
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testSetFocus(String locking) {
    	setUpLocking(locking);
        String str = "";
        str += "package org.kie.test\n";
        str += "global java.util.List list\n";
        str += "rule rule1\n";
        str += "agenda-group \"badfocus\"";
        str += "when\n";
        str += "  Integer(intValue > 0)\n";
        str += "then\n";
        str += "  list.add( 1 );\n";
        str += "end\n";
        str += "\n";

        KieServices ks = KieServices.Factory.get();

        KieFileSystem kfs = ks.newKieFileSystem().write( "src/main/resources/r1.drl", str );
        ks.newKieBuilder( kfs ).buildAll();

        KieBase kbase = ks.newKieContainer(ks.getRepository().getDefaultReleaseId()).getKieBase();

        KieSession ksession = ks.getStoreServices().newKieSession( kbase, null, env );
        List<?> list = new ArrayList<Object>();
    
        ksession.setGlobal( "list",
                            list );
    
        ksession.insert( 1 );
        ksession.insert( 2 );
        ksession.insert( 3 );
        ksession.getAgenda().getAgendaGroup("badfocus").setFocus();
    
        ksession.fireAllRules();

        assertThat(list.size()).isEqualTo(3);
    }
    
    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testSharedReferences(String locking) {
    	setUpLocking(locking);
        KieServices ks = KieServices.Factory.get();
        KieBase kbase = ks.newKieContainer(ks.getRepository().getDefaultReleaseId()).getKieBase();
        KieSession ksession = ks.getStoreServices().newKieSession( kbase, null, env );

        Person x = new Person( "test" );
        List test = new ArrayList();
        List test2 = new ArrayList();
        test.add( x );
        test2.add( x );

        assertThat(test2.get(0)).isSameAs(test.get(0));

        ksession.insert( test );
        ksession.insert( test2 );
        ksession.fireAllRules();

        KieSession ksession2 = ks.getStoreServices().loadKieSession(ksession.getIdentifier(), kbase, null, env);

        Iterator c = ksession2.getObjects().iterator();
        List ref1 = (List) c.next();
        List ref2 = (List) c.next();

        assertThat(ref2.get(0)).isSameAs(ref1.get(0));

    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testMergeConfig(String locking) {
        // JBRULES-3155
    	setUpLocking(locking);
        String str = "";
        str += "package org.kie.test\n";
        str += "global java.util.List list\n";
        str += "rule rule1\n";
        str += "when\n";
        str += "  $i : Integer(intValue > 0)\n";
        str += "then\n";
        str += "  list.add( $i );\n";
        str += "end\n";
        str += "\n";

        KieServices ks = KieServices.Factory.get();

        KieFileSystem kfs = ks.newKieFileSystem().write( "src/main/resources/r1.drl", str );
        ks.newKieBuilder( kfs ).buildAll();

        KieBase kbase = ks.newKieContainer(ks.getRepository().getDefaultReleaseId()).getKieBase();

        Properties properties = new Properties();
        properties.put("drools.processInstanceManagerFactory", "com.example.CustomJPAProcessInstanceManagerFactory");
        KieSessionConfiguration config = ks.newKieSessionConfiguration(properties);

        KieSession ksession = ks.getStoreServices().newKieSession( kbase, config, env );
        FlowSessionConfiguration sessionConfig = ksession.getSessionConfiguration().as(FlowSessionConfiguration.KEY);

        assertThat(sessionConfig.getProcessInstanceManagerFactory()).isEqualTo("com.example.CustomJPAProcessInstanceManagerFactory");
    }
    
    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testMoreComplexRulesSerialization(String locking) throws Exception {
    	setUpLocking(locking);
        KieServices ks = KieServices.Factory.get();

        Resource drlResource = ks.getResources().newClassPathResource("collect_rules.drl", JpaPersistentStatefulSessionTest.class);
        KieFileSystem kfs = ks.newKieFileSystem().write( "src/main/resources/r1.drl", drlResource );
        ks.newKieBuilder( kfs ).buildAll();

        KieBase kbase = ks.newKieContainer(ks.getRepository().getDefaultReleaseId()).getKieBase();
        //KieSession ksession = ks.getStoreServices().newKieSession( kbase, null, env );
        KieSession ksession = kbase.newKieSession();

        List<String> list = new ArrayList<String>();
        ksession.setGlobal("list", list);

        FactType hereType = kbase.getFactType(this.getClass().getPackage().getName(), "Here");
        assertThat(hereType).isNotNull();
        Object here = hereType.newInstance();
        hereType.set(here, "place", "office");

        FactType locationType = kbase.getFactType(this.getClass().getPackage().getName(), "Location");
        assertThat(locationType).isNotNull();
        Object location = locationType.newInstance();
        locationType.set(location, "thing", "key");
        locationType.set(location, "location", "office");

        ksession.insert(here);
        ksession.insert(location);
        ksession.fireAllRules();
    }

    public static class ListHolder implements Serializable {

        private static final long serialVersionUID = -3058814255413392428L;
        private List<String> things;
        private List<String> food;
        private List<String> exits;

        ListHolder() {
            this.things = new ArrayList<String>();
            this.food = new ArrayList<String>();
            this.exits = new ArrayList<String>();
        }

        public void setThings(List<String> things) {
            this.things = things;
        }

        public List<String> getThings() {
            return things;
        }

        public void setFood(List<String> food) {
            this.food = food;
        }

        public List<String> getFood() {
            return food;
        }

        public void setExits(List<String> exits) {
            this.exits = exits;
        }

        public List<String> getExits() {
            return exits;
        }
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testFamilyRulesSerialization(String locking) throws Exception {
    	setUpLocking(locking);
        KieServices ks = KieServices.Factory.get();

        Resource drlResource = ks.getResources().newClassPathResource("family_rules.drl", JpaPersistentStatefulSessionTest.class);
        KieFileSystem kfs = ks.newKieFileSystem().write( "src/main/resources/r1.drl", drlResource );
        ks.newKieBuilder( kfs ).buildAll();

        KieBase kbase = ks.newKieContainer(ks.getRepository().getDefaultReleaseId()).getKieBase();
        KieSession ksession = ks.getStoreServices().newKieSession( kbase, null, env );
        //KieSession ksession = kbase.newKieSession();

        FactType manType = kbase.getFactType(this.getClass().getPackage().getName(), "Man");
        assertThat(manType).isNotNull();
        FactType womanType = kbase.getFactType(this.getClass().getPackage().getName(), "Woman");
        assertThat(womanType).isNotNull();
        FactType parentType = kbase.getFactType(this.getClass().getPackage().getName(), "Parent");
        assertThat(parentType).isNotNull();

        // create working memory objects
        List<Command<?>> commands = new ArrayList<Command<?>>();

        FamilyListHolder listHolder = new FamilyListHolder();
        commands.add(CommandFactory.newInsert(listHolder));

        // parents
        Object parent1 = parentType.newInstance();
        parentType.set(parent1, "parent", "Eva");
        parentType.set(parent1, "child", "Abel");
        commands.add(CommandFactory.newInsert(parent1));

        Object parent2 = parentType.newInstance();
        parentType.set(parent2, "parent", "Eva");
        parentType.set(parent2, "child", "Kain");
        commands.add(CommandFactory.newInsert(parent2));

        Object parent3 = parentType.newInstance();
        parentType.set(parent3, "parent", "Adam");
        parentType.set(parent3, "child", "Abel");
        commands.add(CommandFactory.newInsert(parent3));

        Object parent4 = parentType.newInstance();
        parentType.set(parent4, "parent", "Adam");
        parentType.set(parent4, "child", "Kain");
        commands.add(CommandFactory.newInsert(parent4));

        Object parent5 = parentType.newInstance();
        parentType.set(parent5, "parent", "Abel");
        parentType.set(parent5, "child", "Josef");
        commands.add(CommandFactory.newInsert(parent5));

        // persons
        Object adam = manType.newInstance();
        manType.set(adam, "name", "Adam");
        commands.add(CommandFactory.newInsert(adam));

        Object eva = womanType.newInstance();
        womanType.set(eva, "name", "Eva");
        womanType.set(eva, "age", 101);
        commands.add(CommandFactory.newInsert(eva));

        Object abel = manType.newInstance();
        manType.set(abel, "name", "Abel");
        commands.add(CommandFactory.newInsert(abel));

        Object kain = manType.newInstance();
        manType.set(kain, "name", "Kain");
        commands.add(CommandFactory.newInsert(kain));

        Object josef = manType.newInstance();
        manType.set(josef, "name", "Josef");
        commands.add(CommandFactory.newInsert(josef));

        // fire all rules
        commands.add(CommandFactory.newFireAllRules());
        ksession.execute(CommandFactory.newBatchExecution(commands));

        // asserts
        List<String> manList = listHolder.getManList();
        assertThat(manList.size()).isEqualTo(4);
        assertThat(manList.contains("Adam")).isTrue();
        assertThat(manList.contains("Kain")).isTrue();
        assertThat(manList.contains("Abel")).isTrue();
        assertThat(manList.contains("Josef")).isTrue();

        List<String> personList = listHolder.getPersonList();
        assertThat(personList.size()).isEqualTo(5);
        assertThat(personList.contains("Adam")).isTrue();
        assertThat(personList.contains("Kain")).isTrue();
        assertThat(personList.contains("Abel")).isTrue();
        assertThat(personList.contains("Josef")).isTrue();
        assertThat(personList.contains("Eva")).isTrue();

        List<String> parentList = listHolder.getParentList();
        assertThat(parentList.size()).isEqualTo(5);
        assertThat(parentList.contains("Adam")).isTrue();
        assertThat(parentList.contains("Eva")).isTrue();
        assertThat(parentList.contains("Abel")).isTrue();

        List<String> motherList = listHolder.getMotherList();
        assertThat(motherList.size()).isEqualTo(2);
        assertThat(motherList.contains("Eva")).isTrue();

        List<String> fatherList = listHolder.getFatherList();
        assertThat(fatherList.size()).isEqualTo(3);
        assertThat(fatherList.contains("Adam")).isTrue();
        assertThat(fatherList.contains("Abel")).isTrue();
        assertThat(fatherList.contains("Eva")).isFalse();
        assertThat(fatherList.contains("Kain")).isFalse();
        assertThat(fatherList.contains("Josef")).isFalse();

        List<String> grandparentList = listHolder.getGrandparentList();
        assertThat(grandparentList.size()).isEqualTo(2);
        assertThat(grandparentList.contains("Eva")).isTrue();
        assertThat(grandparentList.contains("Adam")).isTrue();

        assertThat(listHolder.isGrandmaBlessedAgeTriggered()).isTrue();
    }

    /**
     * Static class to store results from the working memory.
     */
    public static class FamilyListHolder implements Serializable {

        private static final long serialVersionUID = -3058814255413392429L;
        private List<String> things;
        private List<String> food;
        private List<String> exits;
        private List<String> manList;
        private List<String> personList;
        private List<String> parentList;
        private List<String> motherList;
        private List<String> fatherList;
        private List<String> grandparentList;
        private boolean grandmaBlessedAgeTriggered;

        FamilyListHolder() {
            setThings(new ArrayList<String>());
            setFood(new ArrayList<String>());
            setExits(new ArrayList<String>());
            setManList(new ArrayList<String>());
            setPersonList(new ArrayList<String>());
            setParentList(new ArrayList<String>());
            setMotherList(new ArrayList<String>());
            setFatherList(new ArrayList<String>());
            setGrandparentList(new ArrayList<String>());
            grandmaBlessedAgeTriggered = false;
        }

        public void setThings(List<String> things) {
            this.things = things;
        }

        public List<String> getThings() {
            return things;
        }

        public void setFood(List<String> food) {
            this.food = food;
        }

        public List<String> getFood() {
            return food;
        }

        public void setExits(List<String> exits) {
            this.exits = exits;
        }

        public List<String> getExits() {
            return exits;
        }

        public void setManList(List<String> manList) {
            this.manList = manList;
        }

        public List<String> getManList() {
            return manList;
        }

        public void setPersonList(List<String> PersonList) {
            personList = PersonList;
        }

        public List<String> getPersonList() {
            return personList;
        }

        public void setParentList(List<String> parentList) {
            this.parentList = parentList;
        }

        public List<String> getParentList() {
            return parentList;
        }

        public void setMotherList(List<String> motherList) {
            this.motherList = motherList;
        }

        public List<String> getMotherList() {
            return motherList;
        }

        public void setGrandparentList(List<String> grandparentList) {
            this.grandparentList = grandparentList;
        }

        public List<String> getGrandparentList() {
            return grandparentList;
        }

        public void setGrandmaBlessedAgeTriggered(boolean grandmaBlessedAgeTriggered) {
            this.grandmaBlessedAgeTriggered = grandmaBlessedAgeTriggered;
        }

        public boolean isGrandmaBlessedAgeTriggered() {
            return grandmaBlessedAgeTriggered;
        }

        public void setFatherList(List<String> fatherList) {
            this.fatherList = fatherList;
        }

        public List<String> getFatherList() {
            return fatherList;
        }
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testGetCount(String locking) {
        // BZ-1022374
    	setUpLocking(locking);
        String str = "";
        str += "package org.kie.test\n";
        str += "rule rule1\n";
        str += "when\n";
        str += "then\n";
        str += " insertLogical( new String(\"a\") );\n";
        str += "end\n";
        str += "\n";

        KieServices ks = KieServices.Factory.get();

        KieFileSystem kfs = ks.newKieFileSystem().write( "src/main/resources/r1.drl", str );
        ks.newKieBuilder( kfs ).buildAll();

        KieBase kbase = ks.newKieContainer(ks.getRepository().getDefaultReleaseId()).getKieBase();
        KieSession ksession = ks.getStoreServices().newKieSession( kbase, null, env );

        assertThat(ksession.fireAllRules()).isEqualTo(1);
        assertThat(ksession.getFactCount()).isEqualTo(1);
    }

    public static class Door implements Serializable {

        private static final long serialVersionUID = 4173662501120948262L;
        @Position(0)
        private String fromLocation;
        @Position(1)
        private String toLocation;

        public Door() {
            this(null, null);
        }

        public Door(String fromLocation, String toLocation) {
            this.fromLocation = fromLocation;
            this.toLocation = toLocation;
        }

        public String getFromLocation() {
            return fromLocation;
        }

        public void setFromLocation(String fromLocation) {
            this.fromLocation = fromLocation;
        }

        public String getToLocation() {
            return toLocation;
        }

        public void setToLocation(String toLocation) {
            this.toLocation = toLocation;
        }
    }

    public static class Edible implements Serializable {

        private static final long serialVersionUID = -7102636642802292131L;
        @Position(0)
        private String thing;

        public Edible() {
            this(null);
        }

        public Edible(String thing) {
            this.thing = thing;
        }

        public String getThing() {
            return thing;
        }

        public void setThing(String thing) {
            this.thing = thing;
        }
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testSessionConfigurationFromContainer(String locking) {
        // DROOLS-1002
    	setUpLocking(locking);
        String str = "rule R when then end";

        KieServices ks = KieServices.Factory.get();

        KieModuleModel kmodel = ks.newKieModuleModel();
        kmodel.newKieBaseModel( "kbase1" )
              .newKieSessionModel( "ksession1" )
              .setClockType( ClockTypeOption.PSEUDO );

        KieFileSystem kfs = ks.newKieFileSystem()
                              .write( "src/main/resources/r1.drl", str )
                              .writeKModuleXML( kmodel.toXML() );

        Results results = ks.newKieBuilder(kfs).buildAll().getResults();
        System.out.println(results.getMessages());


        KieContainer kcontainer = ks.newKieContainer( ks.getRepository().getDefaultReleaseId() );

        KieSessionConfiguration conf = kcontainer.getKieSessionConfiguration( "ksession1" );
        assertThat(conf.getOption(ClockTypeOption.KEY).getClockType()).isEqualTo("pseudo");

        KieSession ksession = ks.getStoreServices().newKieSession( kcontainer.getKieBase("kbase1"), conf, env );
        assertThat(ksession.getSessionClock() instanceof SessionPseudoClock).isTrue();
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("parameters")
    public void testGetFactHandles(String locking) {
        // DROOLS-1270
    	setUpLocking(locking);
        String str =
                "package org.kie.test\n" +
                "rule rule1 when\n" +
                "  String(this == \"A\")\n" +
                "then\n" +
                "  insertLogical( \"B\" );\n" +
                "end\n" +
                "\n";

        KieServices ks = KieServices.Factory.get();

        KieFileSystem kfs = ks.newKieFileSystem().write( "src/main/resources/r1.drl", str );
        ks.newKieBuilder( kfs ).buildAll();

        KieBase kbase = ks.newKieContainer(ks.getRepository().getDefaultReleaseId()).getKieBase();
        KieSession ksession = ks.getStoreServices().newKieSession( kbase, null, env );

        ksession.insert( "A" );
        ksession.fireAllRules();
        assertThat(ksession.getFactCount()).isEqualTo(2);

        for (FactHandle fh : ksession.getFactHandles()) {
            System.out.println(fh);
            if (fh.toString().contains( "String:A" )) {
                ksession.delete( fh );
            }
        }

        ksession.fireAllRules();
        assertThat(ksession.getFactCount()).isEqualTo(0);
    }
}
