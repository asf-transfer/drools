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

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElements;

import org.kie.api.runtime.ClassObjectFilter;
import org.drools.core.ClassObjectSerializationFilter;
import org.drools.core.base.RuleNameEndsWithAgendaFilter;
import org.drools.core.base.RuleNameEqualsAgendaFilter;
import org.drools.core.base.RuleNameMatchesAgendaFilter;
import org.drools.core.base.RuleNameStartsWithAgendaFilter;
import org.drools.commands.runtime.BatchExecutionCommandImpl;
import org.drools.commands.runtime.GetGlobalCommand;
import org.drools.commands.runtime.SetGlobalCommand;
import org.drools.commands.runtime.process.AbortWorkItemCommand;
import org.drools.commands.runtime.process.CompleteWorkItemCommand;
import org.drools.commands.runtime.process.SignalEventCommand;
import org.drools.commands.runtime.process.StartProcessCommand;
import org.drools.commands.runtime.rule.AgendaGroupSetFocusCommand;
import org.drools.commands.runtime.rule.ClearActivationGroupCommand;
import org.drools.commands.runtime.rule.ClearAgendaCommand;
import org.drools.commands.runtime.rule.ClearAgendaGroupCommand;
import org.drools.commands.runtime.rule.ClearRuleFlowGroupCommand;
import org.drools.commands.runtime.rule.DeleteCommand;
import org.drools.commands.runtime.rule.FireAllRulesCommand;
import org.drools.commands.runtime.rule.FireUntilHaltCommand;
import org.drools.commands.runtime.rule.GetObjectCommand;
import org.drools.commands.runtime.rule.GetObjectsCommand;
import org.drools.commands.runtime.rule.InsertElementsCommand;
import org.drools.commands.runtime.rule.InsertObjectCommand;
import org.drools.commands.runtime.rule.ModifyCommand;
import org.drools.commands.runtime.rule.QueryCommand;
import org.drools.commands.runtime.rule.UpdateCommand;
import org.drools.core.common.DefaultFactHandle;
import org.drools.core.common.DisconnectedFactHandle;
import org.junit.jupiter.api.Test;
import org.drools.commands.jaxb.JaxbListWrapper;
import org.kie.api.command.Command;
import org.kie.api.command.Setter;
import org.kie.api.runtime.rule.AgendaFilter;
import org.kie.api.runtime.rule.FactHandle;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandSerializationTest {

    private Class<?>[] annotatedJaxbClasses = { JaxbListWrapper.class };

    // HELPER METHODS -------------------------------------------------------------------------------------------------------------

    private void verifyDisconnectedFactHandle( DisconnectedFactHandle orig, DisconnectedFactHandle copy ) {
        assertThat(copy).as("copy disconnected fact handle is null").isNotNull();
        assertThat(copy.getId()).as("id").isEqualTo(orig.getId());
        assertThat(copy.getIdentityHashCode()).as("identity hash code").isEqualTo(orig.getIdentityHashCode());
        assertThat(copy.getObjectHashCode()).as("object hash code").isEqualTo(orig.getObjectHashCode());
        assertThat(copy.getRecency()).as("recency").isEqualTo(orig.getRecency());
        assertThat(copy.getEntryPointId()).as("entry point id").isEqualTo(orig.getEntryPointId());
        assertThat(copy.getTraitType()).as("trait type").isEqualTo(orig.getTraitType());
    }

    private <T> T roundTrip( Object obj ) throws Exception {
        Class[] classes = { obj.getClass() };
        JAXBContext ctx = getJaxbContext(classes);
        String xmlOut = marshall(ctx, obj);
        return unmarshall(ctx, xmlOut);
    }

    private <T> T unmarshall( JAXBContext ctx, String xmlIn ) throws Exception {
        ByteArrayInputStream xmlStrInputStream = new ByteArrayInputStream(xmlIn.getBytes(Charset.forName("UTF-8")));
        Object out = ctx.createUnmarshaller().unmarshal(xmlStrInputStream);
        return (T) out;
    }

    private String marshall( JAXBContext ctx, Object obj ) throws Exception {
        StringWriter writer = new StringWriter();
        ctx.createMarshaller().marshal(obj, writer);
        return writer.getBuffer().toString();
    }

    private JAXBContext getJaxbContext( Class<?>... classes ) throws Exception {
        List<Class<?>> jaxbClassList = new ArrayList<Class<?>>();
        jaxbClassList.addAll(Arrays.asList(classes));
        jaxbClassList.addAll(Arrays.asList(annotatedJaxbClasses));
        Class<?>[] jaxbClasses = jaxbClassList.toArray(new Class[jaxbClassList.size()]);
        return JAXBContext.newInstance(jaxbClasses);
    }

    // TESTS ----------------------------------------------------------------------------------------------------------------------

    @Test
    public void updateCommandTest() throws Exception {
        DisconnectedFactHandle discFactHandle = new DisconnectedFactHandle(2, 3, 4, 5l, "entry-point-id", "str-obj", true);

        DisconnectedFactHandle copyDiscFactHandle = roundTrip(discFactHandle);
        verifyDisconnectedFactHandle(discFactHandle, copyDiscFactHandle);

        UpdateCommand cmd = new UpdateCommand(discFactHandle, "new-str-object");
        UpdateCommand copyCmd = roundTrip(cmd);

        verifyDisconnectedFactHandle(discFactHandle, copyCmd.getHandle());
        assertThat(copyCmd.getEntryPoint()).as("entry point").isEqualTo(cmd.getEntryPoint());
        assertThat(copyCmd.getObject()).as("object").isEqualTo(cmd.getObject());
    }

    @Test
    public void insertObjectCommandTest() throws Exception {
        InsertObjectCommand cmd = new InsertObjectCommand("obj", "out-id");
        cmd.setReturnObject(false);
        cmd.setEntryPoint("entry-point");

        InsertObjectCommand copyCmd = roundTrip(cmd);

        assertThat(copyCmd.getObject()).as("object").isEqualTo(cmd.getObject());
        assertThat(copyCmd.getOutIdentifier()).as("out id").isEqualTo(cmd.getOutIdentifier());
        assertThat(copyCmd.isReturnObject()).as("return obj").isEqualTo(cmd.isReturnObject());
        assertThat(copyCmd.getEntryPoint()).as("entry point").isEqualTo(cmd.getEntryPoint());
        assertThat(copyCmd.isDisconnected()).as("disconnected").isEqualTo(cmd.isDisconnected());
    }

    @Test
    public void insertObjectCommandListTest() throws Exception {
        List<String> objectList = new ArrayList<String>();
        objectList.add("obj");
        InsertObjectCommand cmd = new InsertObjectCommand(objectList, "out-id");

        InsertObjectCommand copyCmd = roundTrip(cmd);

        assertThat(copyCmd).isNotNull();
        assertThat(copyCmd.getObject()).isInstanceOf(List.class);
        assertThat(copyCmd.getObject()).as("object").isEqualTo(cmd.getObject());
    }

    @Test
    public void insertObjectCommandEmptyListTest() throws Exception {
        List<String> objectList = new ArrayList<String>();
        objectList.add("one-element");
        InsertObjectCommand cmd = new InsertObjectCommand(objectList, "out-id");

        // test list with 1 element
        InsertObjectCommand copyCmd = roundTrip(cmd);

        assertThat(copyCmd).isNotNull();
        assertThat(copyCmd.getObject()).isInstanceOf(List.class);
        assertThat(copyCmd.getObject()).as("object").isEqualTo(cmd.getObject());

        // test empty list
        objectList.clear();
        copyCmd = roundTrip(cmd);

        assertThat(copyCmd).isNotNull();
        assertThat(copyCmd.getObject()).isInstanceOf(List.class);
        assertThat(copyCmd.getObject()).as("object").isEqualTo(cmd.getObject());

    }

    private static final Random random = new Random();

    private static String randomString() {
        return UUID.randomUUID().toString();
    }

    @Test
    public void batchExecutionImplSerializationTest() throws Exception {

        DefaultFactHandle factHandle = new DefaultFactHandle(13, "entry-point-id",
                                                             42, 84, 400l, "fact");

        BatchExecutionCommandImpl batchCmd = new BatchExecutionCommandImpl();
        batchCmd.setLookup("lookup");
        {
            AbortWorkItemCommand cmd = new AbortWorkItemCommand(23l);

            batchCmd.addCommand(cmd);
        }
        {
            String externalForm = factHandle.toExternalForm();
            assertThat(DisconnectedFactHandle.newFrom(factHandle).toExternalForm()).as("FactHandle string").isEqualTo(externalForm);
            DeleteCommand cmd = new DeleteCommand(factHandle);

            batchCmd.addCommand(cmd);
        }
        {
            GetGlobalCommand cmd = new GetGlobalCommand("global-id");
            cmd.setOutIdentifier("out-id");

            batchCmd.addCommand(cmd);
        }
        {
            SetGlobalCommand cmd = new SetGlobalCommand("global-id", new Integer(23));
            cmd.setOutIdentifier("out-id");

            batchCmd.addCommand(cmd);
        }
        {
            InsertElementsCommand cmd = new InsertElementsCommand();
            cmd.setEntryPoint("entry-point");
            cmd.setOutIdentifier("out-id");
            cmd.setReturnObject(true);
            Map<String, Object> mapObj = new HashMap<String, Object>();
            mapObj.put("key", "value");
            List<Object> objects = new ArrayList<Object>(1);
            objects.add(mapObj);
            cmd.setObjects(objects);

            batchCmd.addCommand(cmd);
        }
        {
            QueryCommand cmd = new QueryCommand();
            List<Object> args = new ArrayList<Object>(3);
            args.add("this");
            args.add(42);
            args.add("other");
            cmd.setArguments(args);
            cmd.setName("query-name");
            cmd.setOutIdentifier("out-id");

            batchCmd.addCommand(cmd);
        }
        {
            InsertObjectCommand cmd = new InsertObjectCommand();
            cmd.setEntryPoint("entry-point");
            cmd.setOutIdentifier("out-id");
            cmd.setReturnObject(true);
            cmd.setObject("object");

            batchCmd.addCommand(cmd);
        }
        {
            ModifyCommand cmd = new ModifyCommand();
            cmd.setFactHandle(DisconnectedFactHandle.newFrom(factHandle));
            List<Setter> setters = new ArrayList<Setter>(2);
            Setter setter = new Setter() {

                @Override
                public String getValue() {
                    return "blue";
                }

                @Override
                public String getAccessor() {
                    return "heart";
                }
            };
            setters.add(setter);
            setter = new Setter() {

                @Override
                public String getValue() {
                    return "hot";
                }

                @Override
                public String getAccessor() {
                    return "fingers";
                }
            };
            setters.add(setter);
            cmd.setSetters(setters);

            batchCmd.addCommand(cmd);
        }
        {
            GetObjectCommand cmd = new GetObjectCommand(factHandle, "out-id");

            batchCmd.addCommand(cmd);
        }

        // TODO: implement serialization for agenda filters
        {
            AgendaFilter [] filters = new AgendaFilter[4];
            filters[0] = new RuleNameEndsWithAgendaFilter("suffix", false);
            filters[1] = new RuleNameEqualsAgendaFilter("name", true);
            filters[2] = new RuleNameMatchesAgendaFilter("regexp", false);
            filters[3] = new RuleNameStartsWithAgendaFilter("prefix", false);

            for( AgendaFilter filter : filters ) {
                FireAllRulesCommand cmd = new FireAllRulesCommand(randomString(), random.nextInt(1000), filter);
                batchCmd.addCommand(cmd);
            }
        }
        {
            AgendaFilter[] filters = new AgendaFilter[4];
            filters[0] = new RuleNameEndsWithAgendaFilter("suffix", false);
            filters[1] = new RuleNameEqualsAgendaFilter("name", true);
            filters[2] = new RuleNameMatchesAgendaFilter("regexp", false);
            filters[3] = new RuleNameStartsWithAgendaFilter("prefix", false);

            for (AgendaFilter filter : filters) {
                FireUntilHaltCommand cmd = new FireUntilHaltCommand(filter);
                batchCmd.addCommand(cmd);
            }
        }
        {
            Map<String, Object> results = new HashMap<String, Object>(1);
            List<String> resultValList = new ArrayList<String>(2);
            resultValList.add("yellow");
            resultValList.add("chances");
            results.put("list", resultValList);
            CompleteWorkItemCommand cmd = new CompleteWorkItemCommand(random.nextInt(1000), results);

            batchCmd.addCommand(cmd);
        }
        {
            ClassObjectFilter filter = new ClassObjectFilter(String.class);
            GetObjectsCommand cmd = new GetObjectsCommand(filter, "out-id");

            batchCmd.addCommand(cmd);
        }
        {
            AgendaGroupSetFocusCommand cmd = new AgendaGroupSetFocusCommand(randomString());
            batchCmd.addCommand(cmd);
        }
        {
            ClearActivationGroupCommand cmd = new ClearActivationGroupCommand(randomString());
            batchCmd.addCommand(cmd);
        }
        {
            ClearAgendaCommand cmd = new ClearAgendaCommand();
            batchCmd.addCommand(cmd);
        }
        {
            ClearAgendaGroupCommand cmd = new ClearAgendaGroupCommand(randomString());
            batchCmd.addCommand(cmd);
        }
        {
            ClearRuleFlowGroupCommand cmd = new ClearRuleFlowGroupCommand(randomString());
            batchCmd.addCommand(cmd);
        }


        BatchExecutionCommandImpl batchCmdCopy = roundTrip(batchCmd);
        assertThat(batchCmdCopy.getLookup()).as("Batch cmd lookup").isEqualTo(batchCmd.getLookup());
        assertThat(batchCmdCopy.getCommands().size()).as("Batch cmd num commands").isEqualTo(batchCmd.getCommands().size());
        // How many times have I written this type of code?
        // This code should use the utility in kie-test-util when it finally gets moved there..
        for( Command copyCmd : batchCmdCopy.getCommands() ) {
            for( Command origCmd : batchCmd.getCommands() ) {
                Class cmdClass = origCmd.getClass();
                if( copyCmd.getClass().equals(cmdClass) ) {
                    if( cmdClass.equals(DeleteCommand.class) ) {
                        compareFactHandles(((DeleteCommand) origCmd).getFactHandle(), ((DeleteCommand) copyCmd).getFactHandle(),
                                           DeleteCommand.class );
                    } else if( cmdClass.equals(FireAllRulesCommand.class) ) {
                        AgendaFilter origFilter = ((FireAllRulesCommand) origCmd).getAgendaFilter();
                        AgendaFilter copyFilter = ((FireAllRulesCommand) copyCmd).getAgendaFilter();
                        if( ! origFilter.getClass().equals(copyFilter.getClass()) ) {
                            continue;
                        }
                        Class agendaFilterClass = origFilter.getClass();
                        for( Field agendaFilterField : agendaFilterClass.getDeclaredFields() ) {
                            agendaFilterField.setAccessible(true);
                            Object afFieldOrigVal = agendaFilterField.get(origFilter);
                            Object afFieldCopyVal = agendaFilterField.get(copyFilter);
                            if( afFieldOrigVal instanceof Pattern ) {
                                afFieldOrigVal = ((Pattern) afFieldOrigVal).pattern();
                                afFieldCopyVal = ((Pattern) afFieldCopyVal).pattern();
                            }
                            assertThat(afFieldCopyVal).as(agendaFilterClass.getSimpleName() + "." + agendaFilterField.getName()).isEqualTo(afFieldOrigVal);
                        }
                        assertThat(((FireAllRulesCommand) copyCmd).getMax()).as(FireAllRulesCommand.class.getSimpleName() + ".max").isEqualTo(((FireAllRulesCommand) origCmd).getMax());
                        assertThat(((FireAllRulesCommand) copyCmd).getOutIdentifier()).as(FireAllRulesCommand.class.getSimpleName() + ".outIdentifier").isEqualTo(((FireAllRulesCommand) origCmd).getOutIdentifier());
                    } else if (cmdClass.equals(FireUntilHaltCommand.class)) {
                        AgendaFilter origFilter = ((FireUntilHaltCommand) origCmd).getAgendaFilter();
                        AgendaFilter copyFilter = ((FireUntilHaltCommand) copyCmd).getAgendaFilter();
                        if (!origFilter.getClass().equals(copyFilter.getClass())) {
                            continue;
                        }
                        Class agendaFilterClass = origFilter.getClass();
                        for (Field agendaFilterField : agendaFilterClass.getDeclaredFields()) {
                            agendaFilterField.setAccessible(true);
                            Object afFieldOrigVal = agendaFilterField.get(origFilter);
                            Object afFieldCopyVal = agendaFilterField.get(copyFilter);
                            if (afFieldOrigVal instanceof Pattern) {
                                afFieldOrigVal = ((Pattern) afFieldOrigVal).pattern();
                                afFieldCopyVal = ((Pattern) afFieldCopyVal).pattern();
                            }
                            assertThat(afFieldCopyVal).as(agendaFilterClass.getSimpleName() + "." + agendaFilterField.getName()).isEqualTo(afFieldOrigVal);
                        }
                    } else {
                        for( Field cmdField : cmdClass.getDeclaredFields() ) {
                            cmdField.setAccessible(true);
                            if( Modifier.isTransient(cmdField.getModifiers()) ) {
                                continue;
                            }
                            Object origVal = cmdField.get(origCmd);
                            assertThat(origVal).as(cmdClass.getSimpleName() + "." + cmdField.getName()).isNotNull();
                            Object copyVal = cmdField.get(copyCmd);
                            assertThat(copyVal).as("Round-tripped " + cmdClass.getSimpleName() + "." + cmdField.getName()).isNotNull();
                            if( origVal instanceof FactHandle ) {
                                compareFactHandles((FactHandle) origVal, (FactHandle) copyVal, cmdClass);
                            } else if( origVal instanceof ClassObjectSerializationFilter ) {
                                assertThat(((ClassObjectSerializationFilter) copyVal).getClass()).as("Original compared to Round-tripped " + cmdClass.getSimpleName() + "." + cmdField.getName()).isEqualTo(((ClassObjectSerializationFilter) origVal).getClass());
                            } else if( origVal instanceof List ) {
                                List origList = (List) origVal;
                                if( ((List) copyVal).isEmpty() ) {
                                    assertThat(origList.isEmpty()).as("Original compared to Round-tripped " + cmdClass.getSimpleName() + "." + cmdField.getName()).isTrue();
                                } else {
                                    if( origList.get(0) instanceof Setter ) {
                                        for( Object obj : (List) origVal )  {
                                            assertThat(obj instanceof Setter).as("Expected a " + Setter.class.getSimpleName() + " instance (not " + obj.getClass().getSimpleName() + " in " + cmdClass.getSimpleName() + "." + cmdField.getName()).isTrue();
                                            Iterator<Object> iter = ((List) copyVal).iterator();
                                            while( iter.hasNext() ) {
                                                Setter copySetter = (Setter) iter.next();
                                                if( ((Setter)obj).getAccessor().equals(copySetter.getAccessor()) ) {
                                                    assertThat(copySetter.getValue()).as("Original compared to Round-tripped " + cmdClass.getSimpleName() + "." + cmdField.getName()).isEqualTo(((Setter) obj).getValue());
                                                    iter.remove();
                                                }
                                            }
                                        }
                                        assertThat(((List) copyVal).isEmpty()).as("Original compared to Round-tripped " + cmdClass.getSimpleName() + "." + cmdField.getName()).isTrue();
                                    } else if( origList.get(0) instanceof Map ) {
                                        Map copyMap = (Map) ((List) copyVal).get(0);
                                        for( Object entry : ((Map) origList.get(0)).entrySet() ) {
                                            assertThat((copyMap).containsKey(((Entry) entry).getKey())).as("Original compared to Round-tripped " + cmdClass.getSimpleName() + "." + cmdField.getName()).isTrue();
                                        }
                                    }
                                }
                            } else {
                                assertThat(origVal.equals(copyVal)).as("Original compared to Round-tripped " + cmdClass.getSimpleName() + "." + cmdField.getName()).isTrue();
                            }
                        }
                    }
               }
            }
        }



        // verify that BatchExecutionCommandImpl.commands has been filled with all
        // of the different types
        Field commandsField = BatchExecutionCommandImpl.class.getDeclaredField("commands");
        XmlElements xmlElemsAnno = commandsField.getAnnotation(XmlElements.class);
        List<Class> cmdTypes = new ArrayList<Class>(xmlElemsAnno.value().length);
        for( XmlElement xmlElem : xmlElemsAnno.value() ) {
            cmdTypes.add(xmlElem.type());
        }

        cmdTypes.remove(SignalEventCommand.class);  // already thoroughly tested..
        cmdTypes.remove(StartProcessCommand.class); // already thoroughly tested..
        for( Command cmd : batchCmd.getCommands() ) {
           cmdTypes.remove(cmd.getClass());
        }

        // other tests for this as part of the REST integration tests..
    }

    private static void compareFactHandles( FactHandle orig, FactHandle copy, Class cmdClass) {
        String origFHString = orig.toExternalForm();
        origFHString = origFHString.substring(0, origFHString.lastIndexOf(":"));
        String copyFHString = copy.toExternalForm();
        copyFHString = copyFHString.substring(0, copyFHString.lastIndexOf(":"));
        assertThat(copyFHString).as(cmdClass.getSimpleName() + ".facthandle string").isEqualTo(origFHString);
    }
}
