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
package org.kie.internal.runtime.manager.deploy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.kie.internal.runtime.conf.AuditMode;
import org.kie.internal.runtime.conf.DeploymentDescriptor;
import org.kie.internal.runtime.conf.NamedObjectModel;
import org.kie.internal.runtime.conf.ObjectModel;
import org.kie.internal.runtime.conf.PersistenceMode;
import org.kie.internal.runtime.conf.RuntimeStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class DeploymentDescriptorTest {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentDescriptorTest.class);

    @Test
    public void testWriteDeploymentDescriptorXml() {
        DeploymentDescriptor descriptor = new DeploymentDescriptorImpl("org.jbpm.domain");

        descriptor.getBuilder()
                  .addMarshalingStrategy(new ObjectModel("org.jbpm.testCustomStrategy",
                                                         new ObjectModel("java.lang.String", new Object[]{"param1"}),
                                                         "param2"))
                  .addRequiredRole("experts");

        String deploymentDescriptorXml = descriptor.toXml();
        assertThat(deploymentDescriptorXml).isNotNull();
        logger.info(deploymentDescriptorXml);

        ByteArrayInputStream stream = new ByteArrayInputStream(deploymentDescriptorXml.getBytes());
        DeploymentDescriptor fromXml = DeploymentDescriptorIO.fromXml(stream);

        assertThat(fromXml).isNotNull();
        assertThat(fromXml.getPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(fromXml.getAuditPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(fromXml.getAuditMode()).isEqualTo(AuditMode.JPA);
        assertThat(fromXml.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(fromXml.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.SINGLETON);
        assertThat(fromXml.getMarshallingStrategies().size()).isEqualTo(1);
        assertThat(fromXml.getConfiguration().size()).isEqualTo(0);
        assertThat(fromXml.getEnvironmentEntries().size()).isEqualTo(0);
        assertThat(fromXml.getEventListeners().size()).isEqualTo(0);
        assertThat(fromXml.getGlobals().size()).isEqualTo(0);
        assertThat(fromXml.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(fromXml.getWorkItemHandlers().size()).isEqualTo(0);
        assertThat(fromXml.getRequiredRoles().size()).isEqualTo(1);
    }

    @Test
    public void testReadDeploymentDescriptorFromXml() throws Exception {
        InputStream input = this.getClass().getResourceAsStream("/deployment/deployment-descriptor-defaults.xml");

        DeploymentDescriptor descriptor = DeploymentDescriptorIO.fromXml(input);
        assertThat(descriptor).isNotNull();
        assertThat(descriptor.getPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(descriptor.getAuditPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(descriptor.getAuditMode()).isEqualTo(AuditMode.JPA);
        assertThat(descriptor.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(descriptor.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.SINGLETON);
        assertThat(descriptor.getMarshallingStrategies().size()).isEqualTo(0);
        assertThat(descriptor.getConfiguration().size()).isEqualTo(0);
        assertThat(descriptor.getEnvironmentEntries().size()).isEqualTo(0);
        assertThat(descriptor.getEventListeners().size()).isEqualTo(0);
        assertThat(descriptor.getGlobals().size()).isEqualTo(0);
        assertThat(descriptor.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(descriptor.getWorkItemHandlers().size()).isEqualTo(0);
        assertThat(descriptor.getRequiredRoles().size()).isEqualTo(0);
    }

    @Test
    public void testReadDeploymentDescriptorMSFromXml() throws Exception {
        InputStream input = this.getClass().getResourceAsStream("/deployment/deployment-descriptor-defaults-and-ms.xml");

        DeploymentDescriptor descriptor = DeploymentDescriptorIO.fromXml(input);
        assertThat(descriptor).isNotNull();
        assertThat(descriptor.getPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(descriptor.getAuditPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(descriptor.getAuditMode()).isEqualTo(AuditMode.JPA);
        assertThat(descriptor.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(descriptor.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.SINGLETON);
        assertThat(descriptor.getMarshallingStrategies().size()).isEqualTo(1);
        assertThat(descriptor.getConfiguration().size()).isEqualTo(0);
        assertThat(descriptor.getEnvironmentEntries().size()).isEqualTo(0);
        assertThat(descriptor.getEventListeners().size()).isEqualTo(0);
        assertThat(descriptor.getGlobals().size()).isEqualTo(0);
        assertThat(descriptor.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(descriptor.getWorkItemHandlers().size()).isEqualTo(0);
        assertThat(descriptor.getRequiredRoles().size()).isEqualTo(1);
    }

    @Test
    public void testReadPartialDeploymentDescriptorFromXml() throws Exception {
        InputStream input = this.getClass().getResourceAsStream("/deployment/partial-deployment-descriptor.xml");

        DeploymentDescriptor descriptor = DeploymentDescriptorIO.fromXml(input);
        assertThat(descriptor).isNotNull();
        assertThat(descriptor.getPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(descriptor.getAuditPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(descriptor.getAuditMode()).isEqualTo(AuditMode.JPA);
        assertThat(descriptor.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(descriptor.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.PER_PROCESS_INSTANCE);
        assertThat(descriptor.getMarshallingStrategies().size()).isEqualTo(0);
        assertThat(descriptor.getConfiguration().size()).isEqualTo(0);
        assertThat(descriptor.getEnvironmentEntries().size()).isEqualTo(0);
        assertThat(descriptor.getEventListeners().size()).isEqualTo(0);
        assertThat(descriptor.getGlobals().size()).isEqualTo(0);
        assertThat(descriptor.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(descriptor.getWorkItemHandlers().size()).isEqualTo(0);
        assertThat(descriptor.getRequiredRoles().size()).isEqualTo(0);
    }

    @Test
    public void testCreateDeploymentDescriptorWithSetters() {
        DeploymentDescriptorImpl descriptor = new DeploymentDescriptorImpl("org.jbpm.domain");

        descriptor.setAuditMode(AuditMode.JMS);
        descriptor.setEnvironmentEntries(null);

        List<ObjectModel> marshallingStrategies = new ArrayList<ObjectModel>();
        marshallingStrategies.add(new ObjectModel("org.jbpm.testCustomStrategy",
                                                  new ObjectModel("java.lang.String", new Object[]{"param1"}),
                                                  "param2"));
        descriptor.setMarshallingStrategies(marshallingStrategies);

        List<String> roles = new ArrayList<String>();
        roles.add("experts");

        descriptor.setRequiredRoles(roles);

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.getPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(descriptor.getAuditPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(descriptor.getAuditMode()).isEqualTo(AuditMode.JMS);
        assertThat(descriptor.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(descriptor.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.SINGLETON);
        assertThat(descriptor.getMarshallingStrategies().size()).isEqualTo(1);
        assertThat(descriptor.getConfiguration().size()).isEqualTo(0);
        assertThat(descriptor.getEnvironmentEntries().size()).isEqualTo(0);
        assertThat(descriptor.getEventListeners().size()).isEqualTo(0);
        assertThat(descriptor.getGlobals().size()).isEqualTo(0);
        assertThat(descriptor.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(descriptor.getWorkItemHandlers().size()).isEqualTo(0);
        assertThat(descriptor.getRequiredRoles().size()).isEqualTo(1);
    }

    @Test
    public void testPrintDescriptor() {
        DeploymentDescriptor descriptor = new DeploymentDescriptorImpl("org.jbpm.domain");

        descriptor.getBuilder()
                  .addWorkItemHandler(new NamedObjectModel("mvel", "Log", "new org.jbpm.process.instance.impl.demo.SystemOutWorkItemHandler()"))
                  .addWorkItemHandler(new NamedObjectModel("mvel", "WebService", "new org.jbpm.process.workitem.webservice.WebServiceWorkItemHandler(ksession)"))
                  .addWorkItemHandler(new NamedObjectModel("mvel", "Rest", "new org.jbpm.process.workitem.rest.RESTWorkItemHandler()"))
                  .addWorkItemHandler(new NamedObjectModel("mvel", "Service Task", "new org.jbpm.process.workitem.bpmn2.ServiceTaskHandler(ksession)"));

        logger.debug(descriptor.toXml());
    }

    @Test
    public void testWriteDeploymentDescriptorXmlWithDuplicateNamedObjects() {
        DeploymentDescriptor descriptor = new DeploymentDescriptorImpl("org.jbpm.domain");

        descriptor.getBuilder()
                  .addWorkItemHandler(new NamedObjectModel("mvel", "Log", "new org.jbpm.process.instance.impl.demo.SystemOutWorkItemHandler()"))
                  .addWorkItemHandler(new NamedObjectModel("mvel", "Log", "new org.jbpm.process.instance.impl.demo.CustomSystemOutWorkItemHandler()"))
                  .addRequiredRole("experts");

        String deploymentDescriptorXml = descriptor.toXml();
        assertThat(deploymentDescriptorXml).isNotNull();
        logger.info(deploymentDescriptorXml);

        ByteArrayInputStream stream = new ByteArrayInputStream(deploymentDescriptorXml.getBytes());
        DeploymentDescriptor fromXml = DeploymentDescriptorIO.fromXml(stream);

        assertThat(fromXml).isNotNull();
        assertThat(fromXml.getPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(fromXml.getAuditPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(fromXml.getAuditMode()).isEqualTo(AuditMode.JPA);
        assertThat(fromXml.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(fromXml.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.SINGLETON);
        assertThat(fromXml.getMarshallingStrategies().size()).isEqualTo(0);
        assertThat(fromXml.getConfiguration().size()).isEqualTo(0);
        assertThat(fromXml.getEnvironmentEntries().size()).isEqualTo(0);
        assertThat(fromXml.getEventListeners().size()).isEqualTo(0);
        assertThat(fromXml.getGlobals().size()).isEqualTo(0);
        assertThat(fromXml.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(fromXml.getWorkItemHandlers().size()).isEqualTo(1);
        assertThat(fromXml.getRequiredRoles().size()).isEqualTo(1);
    }

    @Test
    public void testCreateDeploymentDescriptorWithPrefixedRoles() {
        DeploymentDescriptorImpl descriptor = new DeploymentDescriptorImpl("org.jbpm.domain");

        descriptor.setAuditMode(AuditMode.JMS);
        descriptor.setEnvironmentEntries(null);

        List<ObjectModel> marshallingStrategies = new ArrayList<ObjectModel>();
        marshallingStrategies.add(new ObjectModel("org.jbpm.testCustomStrategy",
                                                  new ObjectModel("java.lang.String", new Object[]{"param1"}),
                                                  "param2"));
        descriptor.setMarshallingStrategies(marshallingStrategies);

        List<String> roles = new ArrayList<String>();
        roles.add("view:managers");
        roles.add("execute:experts");
        roles.add("all:everyone");
        roles.add("employees");

        descriptor.setRequiredRoles(roles);

        assertThat(descriptor).isNotNull();
        assertThat(descriptor.getPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(descriptor.getAuditPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(descriptor.getAuditMode()).isEqualTo(AuditMode.JMS);
        assertThat(descriptor.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(descriptor.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.SINGLETON);
        assertThat(descriptor.getMarshallingStrategies().size()).isEqualTo(1);
        assertThat(descriptor.getConfiguration().size()).isEqualTo(0);
        assertThat(descriptor.getEnvironmentEntries().size()).isEqualTo(0);
        assertThat(descriptor.getEventListeners().size()).isEqualTo(0);
        assertThat(descriptor.getGlobals().size()).isEqualTo(0);
        assertThat(descriptor.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(descriptor.getWorkItemHandlers().size()).isEqualTo(0);
        assertThat(descriptor.getRequiredRoles().size()).isEqualTo(4);

        List<String> toVerify = descriptor.getRequiredRoles();
        assertThat(toVerify.size()).isEqualTo(4);
        assertThat(toVerify.contains("view:managers")).isTrue();
        assertThat(toVerify.contains("execute:experts")).isTrue();
        assertThat(toVerify.contains("all:everyone")).isTrue();
        assertThat(toVerify.contains("employees")).isTrue();

        toVerify = descriptor.getRequiredRoles(DeploymentDescriptor.TYPE_ALL);
        assertThat(toVerify.size()).isEqualTo(4);
        assertThat(toVerify.contains("managers")).isTrue();
        assertThat(toVerify.contains("experts")).isTrue();
        assertThat(toVerify.contains("everyone")).isTrue();
        assertThat(toVerify.contains("employees")).isTrue();

        toVerify = descriptor.getRequiredRoles(DeploymentDescriptor.TYPE_EXECUTE);
        assertThat(toVerify.size()).isEqualTo(2);
        assertThat(toVerify.contains("experts")).isTrue();
        assertThat(toVerify.contains("employees")).isTrue();

        toVerify = descriptor.getRequiredRoles(DeploymentDescriptor.TYPE_VIEW);
        assertThat(toVerify.size()).isEqualTo(2);
        assertThat(toVerify.contains("managers")).isTrue();
        assertThat(toVerify.contains("employees")).isTrue();
    }

    @Test
    public void testWriteDeploymentDescriptorXmlWithTransientElements() {
        DeploymentDescriptor descriptor = new DeploymentDescriptorImpl("org.jbpm.domain");

        descriptor.getBuilder()
                  .addMarshalingStrategy(new TransientObjectModel("org.jbpm.testCustomStrategy",
                                                                  new ObjectModel("java.lang.String", new Object[]{"param1"}),
                                                                  "param2"))
                  .addWorkItemHandler(new TransientNamedObjectModel("mvel", "Log", "new org.jbpm.process.instance.impl.demo.SystemOutWorkItemHandler()"))
                  .addRequiredRole("experts");

        String deploymentDescriptorXml = descriptor.toXml();
        assertThat(deploymentDescriptorXml).isNotNull();
        logger.info(deploymentDescriptorXml);

        ByteArrayInputStream stream = new ByteArrayInputStream(deploymentDescriptorXml.getBytes());
        DeploymentDescriptor fromXml = DeploymentDescriptorIO.fromXml(stream);

        assertThat(fromXml).isNotNull();
        assertThat(fromXml.getPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(fromXml.getAuditPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(fromXml.getAuditMode()).isEqualTo(AuditMode.JPA);
        assertThat(fromXml.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(fromXml.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.SINGLETON);
        assertThat(fromXml.getMarshallingStrategies().size()).isEqualTo(0);
        assertThat(fromXml.getConfiguration().size()).isEqualTo(0);
        assertThat(fromXml.getEnvironmentEntries().size()).isEqualTo(0);
        assertThat(fromXml.getEventListeners().size()).isEqualTo(0);
        assertThat(fromXml.getGlobals().size()).isEqualTo(0);
        assertThat(fromXml.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(fromXml.getWorkItemHandlers().size()).isEqualTo(0);
        assertThat(fromXml.getRequiredRoles().size()).isEqualTo(1);
    }

    @Test
    public void testEmptyDeploymentDescriptor() {
        DeploymentDescriptorImpl descriptor = new DeploymentDescriptorImpl("org.jbpm.domain");

        descriptor.getBuilder()
                  .addMarshalingStrategy(new ObjectModel("org.jbpm.testCustomStrategy",
                                                         new ObjectModel("java.lang.String", new Object[]{"param1"}),
                                                         "param2"))
                  .addRequiredRole("experts");

        assertThat(descriptor.isEmpty()).isFalse();

        InputStream input = this.getClass().getResourceAsStream("/deployment/empty-descriptor.xml");
        DeploymentDescriptor fromXml = DeploymentDescriptorIO.fromXml(input);

        assertThat(fromXml).isNotNull();
        assertThat(((DeploymentDescriptorImpl) fromXml).isEmpty()).isTrue();

        assertThat(fromXml.getPersistenceUnit()).isNull();
        assertThat(fromXml.getAuditPersistenceUnit()).isNull();
        assertThat(fromXml.getAuditMode()).isEqualTo(AuditMode.JPA);
        assertThat(fromXml.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(fromXml.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.SINGLETON);
        assertThat(fromXml.getMarshallingStrategies().size()).isEqualTo(0);
        assertThat(fromXml.getConfiguration().size()).isEqualTo(0);
        assertThat(fromXml.getEnvironmentEntries().size()).isEqualTo(0);
        assertThat(fromXml.getEventListeners().size()).isEqualTo(0);
        assertThat(fromXml.getGlobals().size()).isEqualTo(0);
        assertThat(fromXml.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(fromXml.getWorkItemHandlers().size()).isEqualTo(0);
        assertThat(fromXml.getRequiredRoles().size()).isEqualTo(0);
    }
}
