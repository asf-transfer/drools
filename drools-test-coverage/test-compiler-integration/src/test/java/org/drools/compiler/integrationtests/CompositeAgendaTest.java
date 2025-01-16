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
package org.drools.compiler.integrationtests;

import org.drools.testcoverage.common.model.A;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.kie.api.KieBase;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.internal.conf.ParallelExecutionOption;
import org.kie.internal.utils.KieHelper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import static org.assertj.core.api.Assertions.fail;

@EnabledIfSystemProperty(named = "runTurtleTests", matches = "true")
public class CompositeAgendaTest {

    @Test 
    @Disabled
    public void testInALoop() {
        for (int i = 0; i < 100; i++) {
            testCreateHaltDisposeAgenda();
            System.out.println("Done: " + i);
        }
    }

    @Test
    @Timeout(600000)
    public void testCreateHaltDisposeAgenda() {
        final String drl = " import " + A.class.getCanonicalName() + ";\n" +
                " declare A @role( event ) end\n" +
                " global java.util.concurrent.atomic.LongAdder firings;\n" +
                " rule R0 when\n" +
                "     A( value > 0,$Aid: id )\n" +
                " then\n" +
                "     firings.add(1);\n" +
                " end\n" +
                " rule R1 when\n" +
                "     A(value > 1)\n" +
                " then\n" +
                "     firings.add(1);\n" +
                " end\n" +
                " rule R2 when\n" +
                "     A(value > 2)\n" +
                " then\n" +
                "     firings.add(1);\n" +
                " end\n" +
                " rule R3 when\n" +
                "     A(value > 3)\n" +
                " then\n" +
                "     firings.add(1);\n" +
                " end\n" +
                " rule R4 when\n" +
                "     A(value > 4)\n" +
                " then\n" +
                "     firings.add(1);\n" +
                " end\n" +
                " rule R5 when\n" +
                "     A(value > 5)\n" +
                " then\n" +
                "     firings.add(1);\n" +
                " end\n" +
                " rule R6 when\n" +
                "     A(value > 6)\n" +
                " then\n" +
                "     firings.add(1);\n" +
                " end\n" +
                " rule R7 when\n" +
                "     A(value > 7)\n" +
                " then\n" +
                "     firings.add(1);\n" +
                " end";

        final KieBaseConfiguration kieBaseConfiguration = KieBaseTestConfiguration.STREAM_IDENTITY.getKieBaseConfiguration();
        kieBaseConfiguration.setOption(ParallelExecutionOption.FULLY_PARALLEL);
        final KieBase kieBase = new KieHelper().addContent(drl, ResourceType.DRL).build(kieBaseConfiguration);
        final KieSession kieSession = kieBase.newKieSession();

        final LongAdder firingCounter = new LongAdder();
        kieSession.setGlobal("firings", firingCounter);

        final ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit((Runnable) kieSession::fireUntilHalt);
        try {
            final EventInsertThread eventInsertThread = new EventInsertThread(kieSession);
            executor.submit(eventInsertThread);

            try {
                Thread.sleep(5000);
            } catch (final InterruptedException e) {
                e.printStackTrace();
            }
            eventInsertThread.setActive(false);
        } finally {
            try {
                kieSession.halt(); // This may hit GC overhead limit exceeded
            } catch (Throwable th) {
                th.printStackTrace();
                throw th;
            } finally {
                kieSession.dispose();
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(10, TimeUnit.MILLISECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (final InterruptedException e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                }
            }
        }
    }

    public static class EventInsertThread implements Runnable {

        private volatile boolean active = true;
        private KieSession kieSession;

        public EventInsertThread(final KieSession kieSession) {
            this.kieSession = kieSession;
        }

        public void setActive(final boolean active) {
            this.active = active;
        }

        @Override
        public void run() {
            while (active) {
                kieSession.insert(new A(100));
            }
        }
    }
}
