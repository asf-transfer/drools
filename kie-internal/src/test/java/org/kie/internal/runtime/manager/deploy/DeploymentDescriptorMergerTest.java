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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.kie.internal.runtime.conf.AuditMode;
import org.kie.internal.runtime.conf.DeploymentDescriptor;
import org.kie.internal.runtime.conf.MergeMode;
import org.kie.internal.runtime.conf.NamedObjectModel;
import org.kie.internal.runtime.conf.ObjectModel;
import org.kie.internal.runtime.conf.PersistenceMode;
import org.kie.internal.runtime.conf.RuntimeStrategy;

import static org.assertj.core.api.Assertions.assertThat;

public class DeploymentDescriptorMergerTest {

    @Test
    public void testDeploymentDesciptorMergeOverrideAll() {
        DeploymentDescriptor primary = new DeploymentDescriptorImpl("org.jbpm.domain");

        primary.getBuilder()
                .addMarshalingStrategy(new ObjectModel("org.jbpm.test.CustomStrategy", new Object[]{"param2"}))
                .setLimitSerializationClasses(true);

        assertThat(primary).isNotNull();
        assertThat(primary.getPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(primary.getAuditPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(primary.getAuditMode()).isEqualTo(AuditMode.JPA);
        assertThat(primary.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(primary.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.SINGLETON);
        assertThat(primary.getMarshallingStrategies().size()).isEqualTo(1);
        assertThat(primary.getConfiguration().size()).isEqualTo(0);
        assertThat(primary.getEnvironmentEntries().size()).isEqualTo(0);
        assertThat(primary.getEventListeners().size()).isEqualTo(0);
        assertThat(primary.getGlobals().size()).isEqualTo(0);
        assertThat(primary.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(primary.getWorkItemHandlers().size()).isEqualTo(0);
        assertThat(primary.getLimitSerializationClasses()).isTrue();

        DeploymentDescriptor secondary = new DeploymentDescriptorImpl("org.jbpm.domain");

        secondary.getBuilder()
                .auditMode(AuditMode.JMS)
                .persistenceMode(PersistenceMode.JPA)
                .persistenceUnit("my.custom.unit")
                .auditPersistenceUnit("my.custom.unit2")
                .setLimitSerializationClasses(false);

        assertThat(secondary).isNotNull();
        assertThat(secondary.getPersistenceUnit()).isEqualTo("my.custom.unit");
        assertThat(secondary.getAuditPersistenceUnit()).isEqualTo("my.custom.unit2");
        assertThat(secondary.getAuditMode()).isEqualTo(AuditMode.JMS);
        assertThat(secondary.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(secondary.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.SINGLETON);
        assertThat(secondary.getMarshallingStrategies().size()).isEqualTo(0);
        assertThat(secondary.getConfiguration().size()).isEqualTo(0);
        assertThat(secondary.getEnvironmentEntries().size()).isEqualTo(0);
        assertThat(secondary.getEventListeners().size()).isEqualTo(0);
        assertThat(secondary.getGlobals().size()).isEqualTo(0);
        assertThat(secondary.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(secondary.getWorkItemHandlers().size()).isEqualTo(0);
        assertThat(secondary.getLimitSerializationClasses()).isFalse();

        // and now let's merge them

        DeploymentDescriptor outcome = DeploymentDescriptorMerger.merge(primary, secondary, MergeMode.OVERRIDE_ALL);

        assertThat(outcome).isNotNull();
        assertThat(outcome.getPersistenceUnit()).isEqualTo("my.custom.unit");
        assertThat(outcome.getAuditPersistenceUnit()).isEqualTo("my.custom.unit2");
        assertThat(outcome.getAuditMode()).isEqualTo(AuditMode.JMS);
        assertThat(outcome.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(outcome.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.SINGLETON);
        assertThat(outcome.getMarshallingStrategies().size()).isEqualTo(0);
        assertThat(outcome.getConfiguration().size()).isEqualTo(0);
        assertThat(outcome.getEnvironmentEntries().size()).isEqualTo(0);
        assertThat(outcome.getEventListeners().size()).isEqualTo(0);
        assertThat(outcome.getGlobals().size()).isEqualTo(0);
        assertThat(outcome.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(outcome.getWorkItemHandlers().size()).isEqualTo(0);
        assertThat(outcome.getLimitSerializationClasses()).isFalse();
    }

    @Test
    public void testDeploymentDesciptorMergeKeepAll() {
        DeploymentDescriptor primary = new DeploymentDescriptorImpl("org.jbpm.domain");

        primary.getBuilder()
                .addMarshalingStrategy(new ObjectModel("org.jbpm.test.CustomStrategy", new Object[]{"param2"}))
                .setLimitSerializationClasses(true);

        assertThat(primary).isNotNull();
        assertThat(primary.getPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(primary.getAuditPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(primary.getAuditMode()).isEqualTo(AuditMode.JPA);
        assertThat(primary.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(primary.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.SINGLETON);
        assertThat(primary.getMarshallingStrategies().size()).isEqualTo(1);
        assertThat(primary.getConfiguration().size()).isEqualTo(0);
        assertThat(primary.getEnvironmentEntries().size()).isEqualTo(0);
        assertThat(primary.getEventListeners().size()).isEqualTo(0);
        assertThat(primary.getGlobals().size()).isEqualTo(0);
        assertThat(primary.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(primary.getWorkItemHandlers().size()).isEqualTo(0);
        assertThat(primary.getLimitSerializationClasses()).isTrue();

        DeploymentDescriptor secondary = new DeploymentDescriptorImpl("org.jbpm.domain");

        secondary.getBuilder()
                .auditMode(AuditMode.JMS)
                .persistenceMode(PersistenceMode.JPA)
                .persistenceUnit("my.custom.unit")
                .auditPersistenceUnit("my.custom.unit2")
                .setLimitSerializationClasses(false);

        assertThat(secondary).isNotNull();
        assertThat(secondary.getPersistenceUnit()).isEqualTo("my.custom.unit");
        assertThat(secondary.getAuditPersistenceUnit()).isEqualTo("my.custom.unit2");
        assertThat(secondary.getAuditMode()).isEqualTo(AuditMode.JMS);
        assertThat(secondary.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(secondary.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.SINGLETON);
        assertThat(secondary.getMarshallingStrategies().size()).isEqualTo(0);
        assertThat(secondary.getConfiguration().size()).isEqualTo(0);
        assertThat(secondary.getEnvironmentEntries().size()).isEqualTo(0);
        assertThat(secondary.getEventListeners().size()).isEqualTo(0);
        assertThat(secondary.getGlobals().size()).isEqualTo(0);
        assertThat(secondary.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(secondary.getWorkItemHandlers().size()).isEqualTo(0);
        assertThat(secondary.getLimitSerializationClasses()).isFalse();

        // and now let's merge them
        DeploymentDescriptor outcome = DeploymentDescriptorMerger.merge(primary, secondary, MergeMode.KEEP_ALL);

        assertThat(outcome).isNotNull();
        assertThat(outcome.getPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(outcome.getAuditPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(outcome.getAuditMode()).isEqualTo(AuditMode.JPA);
        assertThat(outcome.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(outcome.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.SINGLETON);
        assertThat(outcome.getMarshallingStrategies().size()).isEqualTo(1);
        assertThat(outcome.getConfiguration().size()).isEqualTo(0);
        assertThat(outcome.getEnvironmentEntries().size()).isEqualTo(0);
        assertThat(outcome.getEventListeners().size()).isEqualTo(0);
        assertThat(outcome.getGlobals().size()).isEqualTo(0);
        assertThat(outcome.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(outcome.getWorkItemHandlers().size()).isEqualTo(0);
        assertThat(outcome.getLimitSerializationClasses()).isTrue();
    }

    @Test
    public void testDeploymentDesciptorMergeOverrideEmpty() {
        DeploymentDescriptor primary = new DeploymentDescriptorImpl("org.jbpm.domain");

        primary.getBuilder()
                .addMarshalingStrategy(new ObjectModel("org.jbpm.test.CustomStrategy", new Object[]{"param2"}))
                .setLimitSerializationClasses(true);

        assertThat(primary).isNotNull();
        assertThat(primary.getPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(primary.getAuditPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(primary.getAuditMode()).isEqualTo(AuditMode.JPA);
        assertThat(primary.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(primary.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.SINGLETON);
        assertThat(primary.getMarshallingStrategies().size()).isEqualTo(1);
        assertThat(primary.getConfiguration().size()).isEqualTo(0);
        assertThat(primary.getEnvironmentEntries().size()).isEqualTo(0);
        assertThat(primary.getEventListeners().size()).isEqualTo(0);
        assertThat(primary.getGlobals().size()).isEqualTo(0);
        assertThat(primary.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(primary.getWorkItemHandlers().size()).isEqualTo(0);
        assertThat(primary.getLimitSerializationClasses()).isTrue();

        DeploymentDescriptorImpl secondary = new DeploymentDescriptorImpl("org.jbpm.domain");

        secondary.getBuilder()
                .auditMode(AuditMode.JMS)
                .persistenceMode(PersistenceMode.JPA)
                .persistenceUnit(null)
                .auditPersistenceUnit("");

        assertThat(secondary).isNotNull();
        assertThat(secondary.getPersistenceUnit()).isEqualTo(null);
        assertThat(secondary.getAuditPersistenceUnit()).isEqualTo("");
        assertThat(secondary.getAuditMode()).isEqualTo(AuditMode.JMS);
        assertThat(secondary.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(secondary.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.SINGLETON);
        assertThat(secondary.getMarshallingStrategies().size()).isEqualTo(0);
        assertThat(secondary.getConfiguration().size()).isEqualTo(0);
        assertThat(secondary.getEnvironmentEntries().size()).isEqualTo(0);
        assertThat(secondary.getEventListeners().size()).isEqualTo(0);
        assertThat(secondary.getGlobals().size()).isEqualTo(0);
        assertThat(secondary.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(secondary.getWorkItemHandlers().size()).isEqualTo(0);
        secondary.setLimitSerializationClasses(null);
        assertThat(secondary.getLimitSerializationClasses()).isNull();

        // and now let's merge them
        DeploymentDescriptor outcome = DeploymentDescriptorMerger.merge(primary, secondary, MergeMode.OVERRIDE_EMPTY);

        assertThat(outcome).isNotNull();
        assertThat(outcome.getPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(outcome.getAuditPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(outcome.getAuditMode()).isEqualTo(AuditMode.JMS);
        assertThat(outcome.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(outcome.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.SINGLETON);
        assertThat(outcome.getMarshallingStrategies().size()).isEqualTo(1);
        assertThat(outcome.getConfiguration().size()).isEqualTo(0);
        assertThat(outcome.getEnvironmentEntries().size()).isEqualTo(0);
        assertThat(outcome.getEventListeners().size()).isEqualTo(0);
        assertThat(outcome.getGlobals().size()).isEqualTo(0);
        assertThat(outcome.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(outcome.getWorkItemHandlers().size()).isEqualTo(0);
        assertThat(outcome.getLimitSerializationClasses()).isTrue();
    }

    @Test
    public void testDeploymentDesciptorMergeMergeCollections() {
        DeploymentDescriptor primary = new DeploymentDescriptorImpl("org.jbpm.domain");

        primary.getBuilder()
                .addMarshalingStrategy(new ObjectModel("org.jbpm.test.CustomStrategy", new Object[]{"param2"}))
                .setLimitSerializationClasses(true);

        assertThat(primary).isNotNull();
        assertThat(primary.getPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(primary.getAuditPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(primary.getAuditMode()).isEqualTo(AuditMode.JPA);
        assertThat(primary.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(primary.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.SINGLETON);
        assertThat(primary.getMarshallingStrategies().size()).isEqualTo(1);
        assertThat(primary.getConfiguration().size()).isEqualTo(0);
        assertThat(primary.getEnvironmentEntries().size()).isEqualTo(0);
        assertThat(primary.getEventListeners().size()).isEqualTo(0);
        assertThat(primary.getGlobals().size()).isEqualTo(0);
        assertThat(primary.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(primary.getWorkItemHandlers().size()).isEqualTo(0);
        assertThat(primary.getLimitSerializationClasses()).isTrue();

        DeploymentDescriptor secondary = new DeploymentDescriptorImpl("org.jbpm.domain");

        secondary.getBuilder()
                .auditMode(AuditMode.JMS)
                .persistenceMode(PersistenceMode.JPA)
                .persistenceUnit(null)
                .auditPersistenceUnit("")
                .addMarshalingStrategy(new ObjectModel("org.jbpm.test.AnotherCustomStrategy", new Object[]{"param2"}))
                .setLimitSerializationClasses(false);

        assertThat(secondary).isNotNull();
        assertThat(secondary.getPersistenceUnit()).isEqualTo(null);
        assertThat(secondary.getAuditPersistenceUnit()).isEqualTo("");
        assertThat(secondary.getAuditMode()).isEqualTo(AuditMode.JMS);
        assertThat(secondary.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(secondary.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.SINGLETON);
        assertThat(secondary.getMarshallingStrategies().size()).isEqualTo(1);
        assertThat(secondary.getConfiguration().size()).isEqualTo(0);
        assertThat(secondary.getEnvironmentEntries().size()).isEqualTo(0);
        assertThat(secondary.getEventListeners().size()).isEqualTo(0);
        assertThat(secondary.getGlobals().size()).isEqualTo(0);
        assertThat(secondary.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(secondary.getWorkItemHandlers().size()).isEqualTo(0);
        assertThat(secondary.getLimitSerializationClasses()).isFalse();

        // and now let's merge them
        DeploymentDescriptor outcome = DeploymentDescriptorMerger.merge(primary, secondary,
                MergeMode.MERGE_COLLECTIONS);

        assertThat(outcome).isNotNull();
        assertThat(outcome.getPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(outcome.getAuditPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(outcome.getAuditMode()).isEqualTo(AuditMode.JMS);
        assertThat(outcome.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(outcome.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.SINGLETON);
        assertThat(outcome.getMarshallingStrategies().size()).isEqualTo(2);
        assertThat(outcome.getConfiguration().size()).isEqualTo(0);
        assertThat(outcome.getEnvironmentEntries().size()).isEqualTo(0);
        assertThat(outcome.getEventListeners().size()).isEqualTo(0);
        assertThat(outcome.getGlobals().size()).isEqualTo(0);
        assertThat(outcome.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(outcome.getWorkItemHandlers().size()).isEqualTo(0);
        assertThat(outcome.getLimitSerializationClasses()).isFalse();
    }

    @Test
    public void testDeploymentDesciptorMergeHierarchy() {
        DeploymentDescriptor primary = new DeploymentDescriptorImpl("org.jbpm.domain");

        primary.getBuilder()
                .addMarshalingStrategy(new ObjectModel("org.jbpm.test.CustomStrategy", new Object[]{"param2"}));

        assertThat(primary).isNotNull();
        assertThat(primary.getPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(primary.getAuditPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(primary.getAuditMode()).isEqualTo(AuditMode.JPA);
        assertThat(primary.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(primary.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.SINGLETON);
        assertThat(primary.getMarshallingStrategies().size()).isEqualTo(1);
        assertThat(primary.getConfiguration().size()).isEqualTo(0);
        assertThat(primary.getEnvironmentEntries().size()).isEqualTo(0);
        assertThat(primary.getEventListeners().size()).isEqualTo(0);
        assertThat(primary.getGlobals().size()).isEqualTo(0);
        assertThat(primary.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(primary.getWorkItemHandlers().size()).isEqualTo(0);

        DeploymentDescriptor secondary = new DeploymentDescriptorImpl("org.jbpm.domain");

        secondary.getBuilder()
                .auditMode(AuditMode.NONE)
                .persistenceMode(PersistenceMode.JPA)
                .persistenceUnit("my.custom.unit")
                .auditPersistenceUnit("my.custom.unit2");

        assertThat(secondary).isNotNull();
        assertThat(secondary.getPersistenceUnit()).isEqualTo("my.custom.unit");
        assertThat(secondary.getAuditPersistenceUnit()).isEqualTo("my.custom.unit2");
        assertThat(secondary.getAuditMode()).isEqualTo(AuditMode.NONE);
        assertThat(secondary.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(secondary.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.SINGLETON);
        assertThat(secondary.getMarshallingStrategies().size()).isEqualTo(0);
        assertThat(secondary.getConfiguration().size()).isEqualTo(0);
        assertThat(secondary.getEnvironmentEntries().size()).isEqualTo(0);
        assertThat(secondary.getEventListeners().size()).isEqualTo(0);
        assertThat(secondary.getGlobals().size()).isEqualTo(0);
        assertThat(secondary.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(secondary.getWorkItemHandlers().size()).isEqualTo(0);

        DeploymentDescriptor third = new DeploymentDescriptorImpl("org.jbpm.domain");

        third.getBuilder()
                .auditMode(AuditMode.JMS)
                .persistenceMode(PersistenceMode.JPA)
                .persistenceUnit("my.custom.unit2")
                .auditPersistenceUnit("my.custom.altered")
                .runtimeStrategy(RuntimeStrategy.PER_PROCESS_INSTANCE)
                .addEnvironmentEntry(new NamedObjectModel("IS_JTA", "java.lang.Boolean", new Object[]{"false"}));

        assertThat(third).isNotNull();
        assertThat(third.getPersistenceUnit()).isEqualTo("my.custom.unit2");
        assertThat(third.getAuditPersistenceUnit()).isEqualTo("my.custom.altered");
        assertThat(third.getAuditMode()).isEqualTo(AuditMode.JMS);
        assertThat(third.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(third.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.PER_PROCESS_INSTANCE);
        assertThat(third.getMarshallingStrategies().size()).isEqualTo(0);
        assertThat(third.getConfiguration().size()).isEqualTo(0);
        assertThat(third.getEnvironmentEntries().size()).isEqualTo(1);
        assertThat(third.getEventListeners().size()).isEqualTo(0);
        assertThat(third.getGlobals().size()).isEqualTo(0);
        assertThat(third.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(third.getWorkItemHandlers().size()).isEqualTo(0);

        // assemble hierarchy
        List<DeploymentDescriptor> hierarchy = new ArrayList<DeploymentDescriptor>();
        hierarchy.add(third);
        hierarchy.add(secondary);
        hierarchy.add(primary);

        // and now let's merge them
        DeploymentDescriptor outcome = DeploymentDescriptorMerger.merge(hierarchy, MergeMode.MERGE_COLLECTIONS);

        assertThat(outcome).isNotNull();
        assertThat(outcome.getPersistenceUnit()).isEqualTo("my.custom.unit2");
        assertThat(outcome.getAuditPersistenceUnit()).isEqualTo("my.custom.altered");
        assertThat(outcome.getAuditMode()).isEqualTo(AuditMode.JMS);
        assertThat(outcome.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(outcome.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.PER_PROCESS_INSTANCE);
        assertThat(outcome.getMarshallingStrategies().size()).isEqualTo(1);
        assertThat(outcome.getConfiguration().size()).isEqualTo(0);
        assertThat(outcome.getEnvironmentEntries().size()).isEqualTo(1);
        assertThat(outcome.getEventListeners().size()).isEqualTo(0);
        assertThat(outcome.getGlobals().size()).isEqualTo(0);
        assertThat(outcome.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(outcome.getWorkItemHandlers().size()).isEqualTo(0);
    }

    @Test
    public void testDeploymentDesciptorMergeMergeCollectionsAvoidDuplicates() {
        DeploymentDescriptor primary = new DeploymentDescriptorImpl("org.jbpm.domain");

        primary.getBuilder()
                .addMarshalingStrategy(new ObjectModel("org.jbpm.test.CustomStrategy", new Object[]{"param2"}));

        assertThat(primary).isNotNull();
        assertThat(primary.getPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(primary.getAuditPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(primary.getAuditMode()).isEqualTo(AuditMode.JPA);
        assertThat(primary.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(primary.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.SINGLETON);
        assertThat(primary.getMarshallingStrategies().size()).isEqualTo(1);
        assertThat(primary.getConfiguration().size()).isEqualTo(0);
        assertThat(primary.getEnvironmentEntries().size()).isEqualTo(0);
        assertThat(primary.getEventListeners().size()).isEqualTo(0);
        assertThat(primary.getGlobals().size()).isEqualTo(0);
        assertThat(primary.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(primary.getWorkItemHandlers().size()).isEqualTo(0);

        DeploymentDescriptor secondary = new DeploymentDescriptorImpl("org.jbpm.domain");

        secondary.getBuilder()
                .auditMode(AuditMode.JMS)
                .persistenceMode(PersistenceMode.JPA)
                .persistenceUnit(null)
                .auditPersistenceUnit("")
                .addMarshalingStrategy(new ObjectModel("org.jbpm.test.CustomStrategy", new Object[]{"param2"}));

        assertThat(secondary).isNotNull();
        assertThat(secondary.getPersistenceUnit()).isEqualTo(null);
        assertThat(secondary.getAuditPersistenceUnit()).isEqualTo("");
        assertThat(secondary.getAuditMode()).isEqualTo(AuditMode.JMS);
        assertThat(secondary.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(secondary.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.SINGLETON);
        assertThat(secondary.getMarshallingStrategies().size()).isEqualTo(1);
        assertThat(secondary.getConfiguration().size()).isEqualTo(0);
        assertThat(secondary.getEnvironmentEntries().size()).isEqualTo(0);
        assertThat(secondary.getEventListeners().size()).isEqualTo(0);
        assertThat(secondary.getGlobals().size()).isEqualTo(0);
        assertThat(secondary.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(secondary.getWorkItemHandlers().size()).isEqualTo(0);

        // and now let's merge them
        DeploymentDescriptor outcome = DeploymentDescriptorMerger.merge(primary, secondary,
                MergeMode.MERGE_COLLECTIONS);

        assertThat(outcome).isNotNull();
        assertThat(outcome.getPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(outcome.getAuditPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(outcome.getAuditMode()).isEqualTo(AuditMode.JMS);
        assertThat(outcome.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(outcome.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.SINGLETON);
        assertThat(outcome.getMarshallingStrategies().size()).isEqualTo(1);
        assertThat(outcome.getConfiguration().size()).isEqualTo(0);
        assertThat(outcome.getEnvironmentEntries().size()).isEqualTo(0);
        assertThat(outcome.getEventListeners().size()).isEqualTo(0);
        assertThat(outcome.getGlobals().size()).isEqualTo(0);
        assertThat(outcome.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(outcome.getWorkItemHandlers().size()).isEqualTo(0);
    }

    @Test
    public void testDeploymentDesciptorMergeMergeCollectionsAvoidDuplicatesNamedObject() {
        DeploymentDescriptor primary = new DeploymentDescriptorImpl("org.jbpm.domain");

        primary.getBuilder()
                .addWorkItemHandler(new NamedObjectModel("mvel", "Log",
                        "new org.jbpm.process.instance.impl.demo.SystemOutWorkItemHandler()"));

        assertThat(primary).isNotNull();
        assertThat(primary.getPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(primary.getAuditPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(primary.getAuditMode()).isEqualTo(AuditMode.JPA);
        assertThat(primary.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(primary.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.SINGLETON);
        assertThat(primary.getMarshallingStrategies().size()).isEqualTo(0);
        assertThat(primary.getConfiguration().size()).isEqualTo(0);
        assertThat(primary.getEnvironmentEntries().size()).isEqualTo(0);
        assertThat(primary.getEventListeners().size()).isEqualTo(0);
        assertThat(primary.getGlobals().size()).isEqualTo(0);
        assertThat(primary.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(primary.getWorkItemHandlers().size()).isEqualTo(1);

        DeploymentDescriptor secondary = new DeploymentDescriptorImpl("org.jbpm.domain");

        secondary.getBuilder()
                .auditMode(AuditMode.JMS)
                .persistenceMode(PersistenceMode.JPA)
                .persistenceUnit(null)
                .auditPersistenceUnit("")
                .addWorkItemHandler(new NamedObjectModel("mvel", "Log",
                        "new org.jbpm.process.instance.impl.demo.CustomSystemOutWorkItemHandler()"));

        assertThat(secondary).isNotNull();
        assertThat(secondary.getPersistenceUnit()).isEqualTo(null);
        assertThat(secondary.getAuditPersistenceUnit()).isEqualTo("");
        assertThat(secondary.getAuditMode()).isEqualTo(AuditMode.JMS);
        assertThat(secondary.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(secondary.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.SINGLETON);
        assertThat(secondary.getMarshallingStrategies().size()).isEqualTo(0);
        assertThat(secondary.getConfiguration().size()).isEqualTo(0);
        assertThat(secondary.getEnvironmentEntries().size()).isEqualTo(0);
        assertThat(secondary.getEventListeners().size()).isEqualTo(0);
        assertThat(secondary.getGlobals().size()).isEqualTo(0);
        assertThat(secondary.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(secondary.getWorkItemHandlers().size()).isEqualTo(1);

        // and now let's merge them
        DeploymentDescriptor outcome = DeploymentDescriptorMerger.merge(primary, secondary,
                MergeMode.MERGE_COLLECTIONS);

        assertThat(outcome).isNotNull();
        assertThat(outcome.getPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(outcome.getAuditPersistenceUnit()).isEqualTo("org.jbpm.domain");
        assertThat(outcome.getAuditMode()).isEqualTo(AuditMode.JMS);
        assertThat(outcome.getPersistenceMode()).isEqualTo(PersistenceMode.JPA);
        assertThat(outcome.getRuntimeStrategy()).isEqualTo(RuntimeStrategy.SINGLETON);
        assertThat(outcome.getMarshallingStrategies().size()).isEqualTo(0);
        assertThat(outcome.getConfiguration().size()).isEqualTo(0);
        assertThat(outcome.getEnvironmentEntries().size()).isEqualTo(0);
        assertThat(outcome.getEventListeners().size()).isEqualTo(0);
        assertThat(outcome.getGlobals().size()).isEqualTo(0);
        assertThat(outcome.getTaskEventListeners().size()).isEqualTo(0);
        assertThat(outcome.getWorkItemHandlers().size()).isEqualTo(1);

        // let's check if the secondary version is preserved
        NamedObjectModel model = outcome.getWorkItemHandlers().get(0);
        assertThat(model.getName()).isEqualTo("Log");
        assertThat(model.getIdentifier()).isEqualTo("new org.jbpm.process.instance.impl.demo.CustomSystemOutWorkItemHandler()");
    }
}
