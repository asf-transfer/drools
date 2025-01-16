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
package org.drools.quarkus.ruleunit.examples.reactive;

import jakarta.inject.Inject;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.awaitility.Awaitility.await;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
@QuarkusTestResource(KafkaTestResourceLifecycleManager.class)
public class RuntimeTest {

    @Inject
    @Connector(value = "smallrye-in-memory")
    InMemoryConnector connector;

    @Test
    public void sendEvents() {
        InMemorySource<Event> incomingEvents = connector.source("events");
        InMemorySink<Alert> outgoingAlerts = connector.sink("alerts");

        incomingEvents.send(new Event("temperature", 20));
        incomingEvents.send(new Event("temperature", 40));

        await().<List<? extends Message<Alert>>>until(outgoingAlerts::received, t -> t.size() == 1);
        assertThat(outgoingAlerts.received().size()).isEqualTo(1);
        assertThat(outgoingAlerts.received().get(0).getPayload().getSeverity()).isEqualTo("warning");

    }
}
