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
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.drools.commands.impl.CommandBasedStatefulKnowledgeSessionImpl;
import org.drools.core.common.InternalAgenda;
import org.drools.core.common.InternalAgendaGroup;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.kiesession.rulebase.KnowledgeBaseFactory;
import org.drools.persistence.util.DroolsPersistenceUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.drools.io.ClassPathResource;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.command.ExecutableCommand;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.Context;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.KieSession;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.command.RegistryContext;
import org.kie.internal.persistence.jpa.JPAKnowledgeService;
import org.kie.internal.utils.KieHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.drools.persistence.util.DroolsPersistenceUtil.DROOLS_PERSISTENCE_UNIT_NAME;
import static org.drools.persistence.util.DroolsPersistenceUtil.createEnvironment;

public class AgendaRuleFlowGroupsTest {
    
    private Map<String, Object> context;

    public static Stream<Boolean> parameters() {
    	return Stream.of(true);
    };
    
    
    @BeforeEach
    public void setUp() throws Exception {
        context = DroolsPersistenceUtil.setupWithPoolingDataSource(DROOLS_PERSISTENCE_UNIT_NAME);
    }
	
	@AfterEach
	public void tearDown() {
		DroolsPersistenceUtil.cleanUp(context);
	}

    @ParameterizedTest
    @MethodSource("parameters")
	public void testRuleFlowGroupOnly(boolean locking) throws Exception {
		
		CommandBasedStatefulKnowledgeSessionImpl ksession = createSession(locking, -1, "ruleflow-groups.drl");

        InternalAgendaGroup[] groups = ((InternalAgenda)stripSession(ksession).getAgenda()).getAgendaGroupsManager().getAgendaGroups();
        // only main is available
        assertThat(groups.length).isEqualTo(1);
        assertThat(groups[0].getName()).isEqualTo("MAIN");
		long id = ksession.getIdentifier();
		List<String> list = new ArrayList<String>();
		list.add("Test");
		
		ksession.insert(list);
		ksession.execute(new ActivateRuleFlowCommand("ruleflow-group"));
		
		ksession.dispose();        
        ksession = createSession(locking, id, "ruleflow-groups.drl");
        
        groups = ((InternalAgenda)stripSession(ksession).getAgenda()).getAgendaGroupsManager().getAgendaGroups();
        // main and rule flow is now on the agenda
        assertThat(groups.length).isEqualTo(2);
        assertThat(groups[0].getName()).isEqualTo("MAIN");
        assertThat(groups[1].getName()).isEqualTo("ruleflow-group");
	}
    
    @ParameterizedTest
    @MethodSource("parameters")
    public void testAgendaGroupOnly(boolean locking) throws Exception {
        
        CommandBasedStatefulKnowledgeSessionImpl ksession = createSession(locking, -1, "agenda-groups.drl");

        InternalAgendaGroup[] groups = ((InternalAgenda)stripSession(ksession).getAgenda()).getAgendaGroupsManager().getAgendaGroups();
        // only main is available
        assertThat(groups.length).isEqualTo(1);
        assertThat(groups[0].getName()).isEqualTo("MAIN");
        long id = ksession.getIdentifier();
        List<String> list = new ArrayList<String>();
        list.add("Test");
        
        ksession.insert(list);
        ksession.execute(new ActivateAgendaGroupCommand("agenda-group"));
        
        ksession.dispose();        
        ksession = createSession(locking, id, "agenda-groups.drl");
        
        groups = ((InternalAgenda)stripSession(ksession).getAgenda()).getAgendaGroupsManager().getAgendaGroups();
        // main and agenda group is now on the agenda
        assertThat(groups.length).isEqualTo(2);
        assertThat(groups[0].getName()).isEqualTo("MAIN");
        assertThat(groups[1].getName()).isEqualTo("agenda-group");
        
    }
    
    @ParameterizedTest
    @MethodSource("parameters")
    public void testAgendaGroupAndRuleFlowGroup(boolean locking) throws Exception {
        
        CommandBasedStatefulKnowledgeSessionImpl ksession = createSession(locking, -1, "agenda-groups.drl", "ruleflow-groups.drl");

        InternalAgendaGroup[] groups = ((InternalAgenda)stripSession(ksession).getAgenda()).getAgendaGroupsManager().getAgendaGroups();
        // only main is available
        assertThat(groups.length).isEqualTo(1);
        assertThat(groups[0].getName()).isEqualTo("MAIN");
        long id = ksession.getIdentifier();
        List<String> list = new ArrayList<String>();
        list.add("Test");
        
        ksession.insert(list);
        ksession.execute(new ActivateAgendaGroupCommand("agenda-group"));
        ksession.execute(new ActivateRuleFlowCommand("ruleflow-group"));
        
        ksession.dispose();        
        ksession = createSession(locking, id, "agenda-groups.drl", "ruleflow-groups.drl");
        
        groups = ((InternalAgenda)stripSession(ksession).getAgenda()).getAgendaGroupsManager().getAgendaGroups();
        // main and agenda group is now on the agenda
        assertThat(groups.length).isEqualTo(3);
        assertThat(groups[0].getName()).isEqualTo("MAIN");
        assertThat(groups[1].getName()).isEqualTo("ruleflow-group");
        assertThat(groups[2].getName()).isEqualTo("agenda-group");
        
    }
    
    private KieSession stripSession(KieSession ksession) {
        if (ksession instanceof CommandBasedStatefulKnowledgeSessionImpl) {
            return ((RegistryContext)((CommandBasedStatefulKnowledgeSessionImpl) ksession).
                    getRunner().createContext()).lookup( KieSession.class );
        }
        
        return ksession;
    }
	
	private CommandBasedStatefulKnowledgeSessionImpl createSession(boolean locking, long id, String...rules) {
		
		KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
		for (String rule : rules) {
		    kbuilder.add( new ClassPathResource(rule), ResourceType.DRL );
		}
        InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();

        if ( kbuilder.hasErrors() ) {
            fail( kbuilder.getErrors().toString() );
        }

        kbase.addPackages( kbuilder.getKnowledgePackages() );

        Environment env = createEnvironment(context);
        if( locking ) { 
            env.set(EnvironmentName.USE_PESSIMISTIC_LOCKING, true);
        }
        if (id == -1) {
            return (CommandBasedStatefulKnowledgeSessionImpl) JPAKnowledgeService.newStatefulKnowledgeSession( kbase, null, env );
	    }  else {
	        return (CommandBasedStatefulKnowledgeSessionImpl) JPAKnowledgeService.loadStatefulKnowledgeSession( id, kbase, null, env );
	    }
	}
	
	@SuppressWarnings("serial")
	public class ActivateRuleFlowCommand implements ExecutableCommand<Object> {
		
		private String ruleFlowGroupName;
		
		public ActivateRuleFlowCommand(String ruleFlowGroupName){
			this.ruleFlowGroupName = ruleFlowGroupName;
		}

	    public Void execute(Context context) {
	        KieSession ksession = ((RegistryContext) context).lookup( KieSession.class );
	        ((InternalAgenda) ksession.getAgenda()).activateRuleFlowGroup(ruleFlowGroupName);
	        return null;
	    }

	}
	
	@SuppressWarnings("serial")
    public class ActivateAgendaGroupCommand implements ExecutableCommand<Object> {
        
        private String agendaGroupName;
        
        public ActivateAgendaGroupCommand(String agendaGroupName){
            this.agendaGroupName = agendaGroupName;
        }

        public Void execute(Context context) {
            KieSession ksession = ((RegistryContext) context).lookup( KieSession.class );
            ksession.getAgenda().getAgendaGroup(agendaGroupName).setFocus();
            return null;
        }

    }
	
	@SuppressWarnings("serial")
	public class ExceptionCommand implements ExecutableCommand<Object> {

	    public Void execute(Context context) {
	    	throw new RuntimeException();
	    }

	}

    @ParameterizedTest
    @MethodSource("parameters")
    public void testConflictingAgendaAndRuleflowGroups() throws Exception {

        String drl = "package org.drools.test; " +
                     "" +
                     "rule Test " +
                     "  agenda-group 'ag' " +
                     "  ruleflow-group 'rf' " +
                     "when " +
                     "then " +
                     "end ";

        KieHelper helper = new KieHelper();
        helper.addContent( drl, ResourceType.DRL );
        Results res = helper.verify();

        System.err.println( res.getMessages() );

        assertThat(res.getMessages(Message.Level.WARNING).size()).isEqualTo(1);
        assertThat(res.getMessages(Message.Level.ERROR).size()).isEqualTo(0);

    }


}
