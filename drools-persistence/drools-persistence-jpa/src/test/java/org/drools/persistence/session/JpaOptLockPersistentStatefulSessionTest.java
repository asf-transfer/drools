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

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.drools.commands.impl.CommandBasedStatefulKnowledgeSessionImpl;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.kiesession.rulebase.KnowledgeBaseFactory;
import org.drools.persistence.PersistableRunner;
import org.drools.persistence.jpa.OptimisticLockRetryInterceptor;
import org.drools.persistence.util.DroolsPersistenceUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.api.event.rule.DefaultRuleRuntimeEventListener;
import org.kie.api.event.rule.ObjectInsertedEvent;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.Environment;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.io.ResourceFactory;
import org.kie.internal.persistence.jpa.JPAKnowledgeService;
import org.kie.internal.runtime.StatefulKnowledgeSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.drools.persistence.util.DroolsPersistenceUtil.DROOLS_PERSISTENCE_UNIT_NAME;
import static org.drools.persistence.util.DroolsPersistenceUtil.createEnvironment;

public class JpaOptLockPersistentStatefulSessionTest {

    private static Logger logger = LoggerFactory.getLogger(JpaOptLockPersistentStatefulSessionTest.class);

    private Map<String, Object> context;
    private Environment env;

    private static CountDownLatch ksession1latch = new CountDownLatch(1);
    private static CountDownLatch ksession2latch = new CountDownLatch(1);
    private static volatile boolean isKsession1finished = false;

    public JpaOptLockPersistentStatefulSessionTest() {
    }

    @BeforeEach
    public void setUp() throws Exception {
        context = DroolsPersistenceUtil.setupWithPoolingDataSource(DROOLS_PERSISTENCE_UNIT_NAME);
        env = createEnvironment(context);
    }

    @AfterEach
    public void tearDown() throws Exception {
        DroolsPersistenceUtil.cleanUp(context);
    }    

    @Test
    public void testOptimisticLockInterceptorMaxRetry() {
        String str = "";
        str += "package org.kie.test\n";
        str += "rule rule1\n";
        str += "when\n";
        str += "  Integer(intValue == 1)\n";
        str += "then\n";
        str += "end\n";

        KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
        kbuilder.add(ResourceFactory.newByteArrayResource(str.getBytes()), ResourceType.DRL);
        final InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();

        if (kbuilder.hasErrors()) {
            fail(kbuilder.getErrors().toString());
        }

        kbase.addPackages(kbuilder.getKnowledgePackages());

        final AtomicInteger attempts = new AtomicInteger(0);

        final StatefulKnowledgeSession ksession1 = JPAKnowledgeService.newStatefulKnowledgeSession(kbase, null, env);
        PersistableRunner sscs1 = (PersistableRunner) ((CommandBasedStatefulKnowledgeSessionImpl) ksession1).getRunner();
        OptimisticLockRetryInterceptor interceptor1 = new OptimisticLockRetryInterceptor();
        sscs1.addInterceptor(interceptor1);
        ksession1.addEventListener(new DefaultRuleRuntimeEventListener() {

            public void objectInserted(ObjectInsertedEvent event) {
                attempts.incrementAndGet();
                try {
                    ksession1latch = new CountDownLatch(1);
                    ksession2latch.countDown();
                    ksession1latch.await(); // Wait for ksession2 to commit so ksesison1 will hit OptimisticLockException
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        final long ksessionId = ksession1.getIdentifier();
        StatefulKnowledgeSession ksession2 = JPAKnowledgeService.loadStatefulKnowledgeSession(ksessionId, kbase, null, createEnvironment(context));
        PersistableRunner sscs2 = (PersistableRunner) ((CommandBasedStatefulKnowledgeSessionImpl) ksession2).getRunner();
        OptimisticLockRetryInterceptor interceptor2 = new OptimisticLockRetryInterceptor();
        sscs2.addInterceptor(interceptor2);

        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.execute(() -> {
            try {
                ksession1.insert(1);                
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                ksession1.dispose();
                isKsession1finished = true;
                ksession2latch.countDown();
            }
        });
        try {
            while (!isKsession1finished) {
                try {
                    ksession2latch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                ksession2latch = new CountDownLatch(1);
    
                ksession2.insert(2);
                ksession1latch.countDown();
            }
        } finally {
            ksession2.dispose();

            executor.shutdown();
        }
                try {
            executor.awaitTermination(300, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assertThat(attempts.get()).isEqualTo(4);
    }
}
