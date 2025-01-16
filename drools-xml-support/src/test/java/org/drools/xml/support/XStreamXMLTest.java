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
package org.drools.xml.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.thoughtworks.xstream.XStream;
import org.drools.commands.runtime.rule.ModifyCommand.SetterImpl;
import org.drools.core.base.RuleNameEndsWithAgendaFilter;
import org.drools.commands.runtime.process.StartProcessCommand;
import org.drools.commands.runtime.rule.AgendaGroupSetFocusCommand;
import org.drools.commands.runtime.rule.ClearActivationGroupCommand;
import org.drools.commands.runtime.rule.ClearAgendaCommand;
import org.drools.commands.runtime.rule.ClearAgendaGroupCommand;
import org.drools.commands.runtime.rule.ClearRuleFlowGroupCommand;
import org.drools.commands.runtime.rule.DeleteCommand;
import org.drools.commands.runtime.rule.FireAllRulesCommand;
import org.drools.commands.runtime.rule.GetFactHandlesCommand;
import org.drools.commands.runtime.rule.ModifyCommand;
import org.drools.commands.runtime.rule.UpdateCommand;
import org.drools.core.common.DefaultFactHandle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.drools.commands.runtime.ExecutionResultImpl;
import org.drools.commands.runtime.FlatQueryResults;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.QueryResults;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.utll.xml.XStreamUtils.createTrustingXStream;

public class XStreamXMLTest {

    private XStream xstream;

    @BeforeEach
    public void setup() {
        xstream = createTrustingXStream();
        xstream = XStreamXML.newXStreamMarshaller(xstream);
    }

    @Test
    public void testMarshallStartProcessCmd() {
        StartProcessCommand cmd = new StartProcessCommand("some-process-id", "some-out-identifier");
        String xmlString = xstream.toXML(cmd);
        assertThat(xmlString.contains("processId=\"some-process-id\"")).isTrue();
        assertThat(xmlString.contains("out-identifier=\"some-out-identifier\"")).isTrue();
    }

    @Test
    public void testMarshallStartProcessCmdWithNoOutIdentifier() {
        // the "out-identifier" is optional -> the marshalling should succeed even if it is null
        StartProcessCommand cmd = new StartProcessCommand("some-process-id");
        String xmlString = xstream.toXML(cmd);
        assertThat(xmlString.contains("processId=\"some-process-id\"")).isTrue();
    }

    @Test
    public void testUnMarshallStartProcessCmdWithNoOutIdentifier() {
        // the "out-identifier" is optional -> the unmarshalling should create valid object
        Object obj = xstream.fromXML(
                "<start-process processId=\"some-process-id\"/>");
        assertThat(obj.getClass()).isEqualTo(StartProcessCommand.class);
        StartProcessCommand cmd = (StartProcessCommand)obj;
        assertThat(cmd.getProcessId()).isEqualTo("some-process-id");
    }

    @Test
    public void testMarshallAgendaGroupSetFocusCommand() {
        AgendaGroupSetFocusCommand cmd = new AgendaGroupSetFocusCommand("foo-group");
        String xmlString = xstream.toXML( cmd );
        assertThat(xmlString).isEqualTo("<set-focus name=\"foo-group\"/>");
        AgendaGroupSetFocusCommand cmd2 = (AgendaGroupSetFocusCommand) xstream.fromXML( xmlString );
        assertThat(cmd2.getName()).isEqualTo(cmd.getName());
    }

    @Test
    public void testClearActivationGroupCommand() {
        ClearActivationGroupCommand cmd = new ClearActivationGroupCommand("foo-group");
        String xmlString = xstream.toXML( cmd );
        assertThat(xmlString).isEqualTo("<clear-activation-group name=\"foo-group\"/>");

        ClearActivationGroupCommand cmd2 = (ClearActivationGroupCommand) xstream.fromXML( xmlString );
        assertThat(cmd2.getName()).isEqualTo(cmd.getName());
    }

    @Test
    public void testClearAgendaGroupCommand() {
        ClearAgendaGroupCommand cmd = new ClearAgendaGroupCommand("foo-group");
        String xmlString = xstream.toXML( cmd );
        assertThat(xmlString).isEqualTo("<clear-agenda-group name=\"foo-group\"/>");

        ClearAgendaGroupCommand cmd2 = (ClearAgendaGroupCommand) xstream.fromXML( xmlString );
        assertThat(cmd2.getName()).isEqualTo(cmd.getName());
    }

    @Test
    public void testClearAgendaCommand() {
        ClearAgendaCommand cmd = new ClearAgendaCommand();
        String xmlString = xstream.toXML(cmd);
        assertThat(xmlString).isEqualTo("<clear-agenda/>");

        ClearAgendaCommand cmd2 = (ClearAgendaCommand) xstream.fromXML( xmlString );
    }

    @Test
    public void testClearRuleFlowGroupCommand() {
        ClearRuleFlowGroupCommand cmd = new ClearRuleFlowGroupCommand("foo-group");
        String xmlString = xstream.toXML(cmd);
        assertThat(xmlString).isEqualTo("<clear-ruleflow-group name=\"foo-group\"/>");

        ClearRuleFlowGroupCommand cmd2 = (ClearRuleFlowGroupCommand) xstream.fromXML( xmlString );
        assertThat(cmd2.getName()).isEqualTo(cmd.getName());
    }

    @Test
    public void testModifyCommand() {

        FactHandle factHandle = DefaultFactHandle.createFromExternalFormat("0:1:10:10:20:null:NON_TRAIT:null");

        ModifyCommand cmd = new ModifyCommand(factHandle, Arrays.asList(new SetterImpl("name", "value")));
        String xmlString = xstream.toXML( cmd );
        assertThat(xmlString).isEqualTo("<modify fact-handle=\"0:1:10:10:20:null:NON_TRAIT:null\">\n" +
                "  <set accessor=\"name\" value=\"value\"/>\n" +
                "</modify>");

        ModifyCommand cmd2 = (ModifyCommand) xstream.fromXML( xmlString );
        assertThat(cmd2.getFactHandle().toExternalForm()).isEqualTo(factHandle.toExternalForm());
        assertThat(cmd2.getSetters().size()).isEqualTo(1);
        assertThat(cmd2.getSetters().get(0).getAccessor()).isEqualTo("name");
        assertThat(cmd2.getSetters().get(0).getValue()).isEqualTo("value");
    }

    @Test
    public void testDeleteCommand() {

        FactHandle factHandle = DefaultFactHandle.createFromExternalFormat("0:1:10:10:20:null:NON_TRAIT:null");

        DeleteCommand cmd = new DeleteCommand(factHandle);
        String xmlString = xstream.toXML( cmd );
        assertThat(xmlString).isEqualTo("<delete fact-handle=\"0:1:10:10:20:null:NON_TRAIT:null\"/>");

        DeleteCommand cmd2 = (DeleteCommand) xstream.fromXML( xmlString );
        assertThat(cmd2.getFactHandle().toExternalForm()).isEqualTo(factHandle.toExternalForm());
    }

    @Test
    public void testGetFactHandlesCommand() {
        GetFactHandlesCommand cmd = new GetFactHandlesCommand();
        String xmlString = xstream.toXML( cmd );
        assertThat(xmlString).isEqualTo("<get-fact-handles disconnected=\"false\"/>");

        GetFactHandlesCommand cmd2 = (GetFactHandlesCommand) xstream.fromXML( xmlString );
        assertThat(cmd2.getOutIdentifier()).isNull();
    }

    @Test
    public void testGetFactHandlesCommandWithOutIdentifier() {
        GetFactHandlesCommand cmd = new GetFactHandlesCommand();
        cmd.setOutIdentifier("facts");
        String xmlString = xstream.toXML( cmd );
        assertThat(xmlString).isEqualTo("<get-fact-handles disconnected=\"false\" out-identifier=\"facts\"/>");

        GetFactHandlesCommand cmd2 = (GetFactHandlesCommand) xstream.fromXML( xmlString );
        assertThat(cmd2.getOutIdentifier()).isEqualTo("facts");
    }

    @Test
    public void testExecutionResults() {

        final Message msg = new Message("Hello World!");
        final FactHandle msgHandle = new DefaultFactHandle( 1,
                null,
                10,
                10,
                20,
                msg );

        final Message msg2 = new Message("Hello World again!");
        final FactHandle msgHandle2 = new DefaultFactHandle( 2,
                null,
                10,
                10,
                20,
                msg2 );

        HashMap<String, Object> factHandles = new LinkedHashMap<String, Object>();
        factHandles.put("first", msgHandle);
        factHandles.put("second", msgHandle2);

        ExecutionResultImpl executionResult = new ExecutionResultImpl();
        executionResult.setFactHandles(factHandles);

        HashMap<String, Object> results = new LinkedHashMap<String, Object>();
        results.put("message1", msg);
        results.put("message2", msg2);

        executionResult.setResults(results);

        String xmlString = xstream.toXML(executionResult);
        assertThat(xmlString).isEqualTo("<execution-results>\n" +
                "  <result identifier=\"message1\">\n" +
                "    <org.drools.xml.support.XStreamXMLTest_-Message>\n" +
                "      <msg>Hello World!</msg>\n" +
                "    </org.drools.xml.support.XStreamXMLTest_-Message>\n" +
                "  </result>\n" +
                "  <result identifier=\"message2\">\n" +
                "    <org.drools.xml.support.XStreamXMLTest_-Message>\n" +
                "      <msg>Hello World again!</msg>\n" +
                "    </org.drools.xml.support.XStreamXMLTest_-Message>\n" +
                "  </result>\n" +
                "  <fact-handle identifier=\"first\" external-form=\"0:1:10:10:20:null:NON_TRAIT:org.drools.xml.support.XStreamXMLTest$Message\"/>\n" +
                "  <fact-handle identifier=\"second\" external-form=\"0:2:10:10:20:null:NON_TRAIT:org.drools.xml.support.XStreamXMLTest$Message\"/>\n" +
                "</execution-results>");

        ExecutionResultImpl executionResult2 = (ExecutionResultImpl) xstream.fromXML( xmlString );
        assertThat(executionResult2.getFactHandles().size()).isEqualTo(executionResult.getFactHandles().size());
        assertThat(executionResult2.getResults().size()).isEqualTo(executionResult.getResults().size());
    }

    @Test
    public void testGetFactHandlesExecutionResults() {

        final Message msg = new Message("Hello World!");
        final FactHandle msgHandle = new DefaultFactHandle( 1,
                null,
                10,
                10,
                20,
                msg );

        final Message msg2 = new Message("Hello World again!");
        final FactHandle msgHandle2 = new DefaultFactHandle( 2,
                null,
                10,
                10,
                20,
                msg2 );

        List<FactHandle> factHandleList = new ArrayList<FactHandle>();
        factHandleList.add(msgHandle);
        factHandleList.add(msgHandle2);

        HashMap<String, Object> factHandles = new LinkedHashMap<String, Object>();

        ExecutionResultImpl executionResult = new ExecutionResultImpl();
        executionResult.setFactHandles(factHandles);

        HashMap<String, Object> results = new LinkedHashMap<String, Object>();
        results.put("facts", factHandleList);


        executionResult.setResults(results);

        String xmlString = xstream.toXML(executionResult);
        assertThat(xmlString).isEqualTo("<execution-results>\n" +
                "  <result identifier=\"facts\">\n" +
                "    <list>\n" +
                "      <fact-handle external-form=\"0:1:10:10:20:null:NON_TRAIT:org.drools.xml.support.XStreamXMLTest$Message\"/>\n" +
                "      <fact-handle external-form=\"0:2:10:10:20:null:NON_TRAIT:org.drools.xml.support.XStreamXMLTest$Message\"/>\n" +
                "    </list>\n" +
                "  </result>\n" +
                "</execution-results>");

        ExecutionResultImpl executionResult2 = (ExecutionResultImpl) xstream.fromXML( xmlString );
        assertThat(executionResult2.getFactHandles().size()).isEqualTo(executionResult.getFactHandles().size());
        assertThat(executionResult2.getResults().size()).isEqualTo(executionResult.getResults().size());
    }

    @Test
    public void testQueryResultsConverter() {
        final Message msg = new Message("Hello World!");
        final FactHandle msgHandle = new DefaultFactHandle( 1,
                                                            null,
                                                            10,
                                                            10,
                                                            20,
                                                            msg );
        Set<String> identifiers = new HashSet<String>(  ) {{
            add("greeting");
        }};
        ArrayList<Map<String, FactHandle>> idFactHandleMaps = new ArrayList<Map<String, FactHandle>>(  ) {{
            add( new HashMap<String, FactHandle>(  ) {{
                put( "greeting", msgHandle );
            }} );
        }};
        ArrayList<Map<String, Object>> factHandleResultMap = new ArrayList<Map<String, Object>>(  ) {{
            add( new HashMap<String, Object>(  ) {{
                put( "greeting", msg );
            }} );
        }};

        final String EXPECTED_XML = "<query-results>\n"
                                    + "  <identifiers>\n"
                                    + "    <identifier>greeting</identifier>\n"
                                    + "  </identifiers>\n"
                                    + "  <row>\n"
                                    + "    <identifier id=\"greeting\">\n"
                                    + "      <org.drools.xml.support.XStreamXMLTest_-Message>\n"
                                    + "        <msg>Hello World!</msg>\n"
                                    + "      </org.drools.xml.support.XStreamXMLTest_-Message>\n"
                                    + "      <fact-handle external-form=\"0:1:10:10:20:null:NON_TRAIT:org.drools.xml.support.XStreamXMLTest$Message\"/>\n"
                                    + "    </identifier>\n"
                                    + "  </row>\n"
                                    + "</query-results>";

        QueryResults results = new FlatQueryResults( identifiers, idFactHandleMaps, factHandleResultMap );
        String xmlString = xstream.toXML( results );
        assertThat(xmlString).isEqualTo(EXPECTED_XML);

        QueryResults results2 = (QueryResults) xstream.fromXML( xmlString );
        assertThat(results2).isEqualTo(results);
    }

    private static class Message {
        String msg;

        public Message() {}

        public Message(String msg) {
            this.msg = msg;
        }

        public String getMsg() {
            return msg;
        }

        public void setMsg(String msg) {
            this.msg = msg;
        }

        @Override
        public boolean equals(Object o) {
            if ( this == o ) return true;
            if ( !(o instanceof Message) ) return false;

            Message message = (Message) o;

            return !(msg != null ? !msg.equals( message.msg ) : message.msg != null);

        }

        @Override
        public int hashCode() {
            return msg != null ? msg.hashCode() : 0;
        }
    }

    @Test
    public void testUpdateCommand() {
        FactHandle factHandle = DefaultFactHandle.createFromExternalFormat("0:1:10:10:20:null:NON_TRAIT:null");
        UpdateCommand cmd = new UpdateCommand(factHandle, new Message("Hello World!"), new String[]{"msg"});
        String xmlString = xstream.toXML(cmd);
        String expected = "<update fact-handle=\"0:1:10:10:20:null:NON_TRAIT:null\" entryPoint=\"DEFAULT\">\n" +
                          "  <org.drools.xml.support.XStreamXMLTest_-Message>\n" +
                          "    <msg>Hello World!</msg>\n" +
                          "  </org.drools.xml.support.XStreamXMLTest_-Message>\n" +
                          "  <modifiedProperty value=\"msg\"/>\n" +
                          "</update>";
        assertThat(expected).isEqualToIgnoringWhitespace(xmlString);

        UpdateCommand cmd2 = (UpdateCommand) xstream.fromXML(xmlString);
        assertThat(cmd2.getHandle().toExternalForm()).isEqualTo(factHandle.toExternalForm());
        assertThat(cmd2.getModifiedProperties().length).isEqualTo(1);
        assertThat(cmd2.getModifiedProperties()[0]).isEqualTo("msg");
    }

    @Test
    public void testFireAllRulesCommand() {
        RuleNameEndsWithAgendaFilter filter = new RuleNameEndsWithAgendaFilter("mySuffix", true);
        FireAllRulesCommand cmd = new FireAllRulesCommand("ABC", 100, filter);
        String xmlString = xstream.toXML(cmd);
        String expected = "<fire-all-rules max=\"100\" out-identifier=\"ABC\">\n" +
                          "  <agendaFilter class=\"org.drools.core.base.RuleNameEndsWithAgendaFilter\">\n" +
                          "    <suffix>mySuffix</suffix>\n" +
                          "    <accept>true</accept>\n" +
                          "  </agendaFilter>\n" +
                          "</fire-all-rules>";
        assertThat(expected).isEqualToIgnoringWhitespace(xmlString);

        FireAllRulesCommand cmd2 = (FireAllRulesCommand) xstream.fromXML(xmlString);
        assertThat(cmd2.getMax()).isEqualTo(100);
        assertThat(cmd2.getAgendaFilter().getClass()).isEqualTo(RuleNameEndsWithAgendaFilter.class);
        assertThat(((RuleNameEndsWithAgendaFilter) cmd2.getAgendaFilter()).getSuffix()).isEqualTo("mySuffix");
        assertThat(((RuleNameEndsWithAgendaFilter) cmd2.getAgendaFilter()).isAccept()).isTrue();
    }
}
