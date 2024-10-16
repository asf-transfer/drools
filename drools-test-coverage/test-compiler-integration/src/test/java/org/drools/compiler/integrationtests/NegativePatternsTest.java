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

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.KieSessionTestConfiguration;
import org.drools.testcoverage.common.util.TestParametersUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.kie.api.KieBase;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.time.SessionPseudoClock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests negative patterns with or without additional constraints and events are
 * inserted through one or more entry points.
 * BZ-978979
 */
@RunWith(Parameterized.class)
public class NegativePatternsTest {

    private static final int LOOPS = 2;
    private static final int SHORT_SLEEP_TIME = 20;
    private static final int LONG_SLEEP_TIME = 30;

    private final KieBaseTestConfiguration kieBaseTestConfiguration;

    private KieSession ksession;
    private TrackingAgendaEventListener firedRulesListener;

    public NegativePatternsTest(final KieBaseTestConfiguration kieBaseTestConfiguration) {
        this.kieBaseTestConfiguration = kieBaseTestConfiguration;
    }

    @Parameterized.Parameters(name = "KieBase type={0}")
    public static Collection<Object[]> getParameters() {
        return TestParametersUtil.getKieBaseStreamConfigurations(true);
    }

    @Before
    public void prepareKieSession() {
        final String drl = "package org.drools.compiler.integrationtests\n" +
                     "\n" +
                     "import " + NegativePatternsTest.TestEvent.class.getCanonicalName() + "\n" +
                     "\n" +
                     "declare TestEvent\n" +
                     "    @role( event )\n" +
                     "    @expires( 22ms )\n" +
                     "end\n" +
                     "\n" +
                     "rule \"SingleAbsence\"\n" +
                     "    duration( 18ms )\n" +
                     "    when\n" +
                     "        not ( TestEvent ( ) from entry-point EventStream )\n" +
                     "    then\n" +
                     "        // consequence\n" +
                     "end\n" +
                     "\n" +
                     "rule \"SingleConstrained\"\n" +
                     "    duration( 18ms )\n" +
                     "    when\n" +
                     "        not ( TestEvent ( name == \"EventA\" ) from entry-point EventStream )\n" +
                     "    then\n" +
                     "        // consequence\n" +
                     "end\n" +
                     "\n" +
                     "rule \"MultipleEvents\"\n" +
                     "    duration( 18ms )\n" +
                     "    when\n" +
                     "        TestEvent ( name == \"EventA\" ) over window:time( 22ms ) from entry-point EventStream\n" +
                     "        not ( TestEvent ( name == \"EventB\" ) over window:time( 22ms )  from entry-point EventStream )\n" +
                     "    then\n" +
                     "        // consequence\n" +
                     "end\n" +
                     "\n" +
                     "rule \"MultipleEntryPoints\"\n" +
                     "    duration( 18ms )\n" +
                     "    when\n" +
                     "        not (\n" +
                     "            TestEvent( name == \"EventA\" ) from entry-point EventStream\n" +
                     "        )\n" +
                     "        not (\n" +
                     "            TestEvent( name == \"EventB\" ) from entry-point OtherStream\n" +
                     "        )\n" +
                     "    then\n" +
                     "        // consequence\n" +
                     "end\n";

        final KieBase kbase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("subnetwork-test", kieBaseTestConfiguration, drl);
        ksession = kbase.newKieSession(KieSessionTestConfiguration.STATEFUL_PSEUDO.getKieSessionConfiguration(), null);
        firedRulesListener = new TrackingAgendaEventListener();
        ksession.addEventListener(firedRulesListener);
    }

    @After
    public void cleanKieSession() {
        if (ksession != null) {
            ksession.dispose();
        }
    }

    @Test
    public void testSingleEvent() {
        final EntryPoint entryPoint = ksession.getEntryPoint("EventStream");

        int count = 0;

        // no rules should be fired in the beginning
        advanceTime(LONG_SLEEP_TIME);
        assertThat(firedRulesListener.ruleFiredCount("SingleAbsence")).isEqualTo(count);

        // after firing the rule will wait for 18ms
        ksession.fireAllRules();
        assertThat(firedRulesListener.ruleFiredCount("SingleAbsence")).isEqualTo(count);
        count++;
        advanceTime(LONG_SLEEP_TIME);
        ksession.fireAllRules();
        assertThat(firedRulesListener.ruleFiredCount("SingleAbsence")).isEqualTo(count);

        final FactHandle event = entryPoint.insert(new TestEvent(0, "EventA"));
        ksession.fireAllRules();
        advanceTime(LONG_SLEEP_TIME);
        ksession.fireAllRules();
        assertThat(firedRulesListener.ruleFiredCount("SingleAbsence")).isEqualTo(count);

        entryPoint.delete(event);
        ksession.fireAllRules();
        count++;
        advanceTime(LONG_SLEEP_TIME);
        ksession.fireAllRules();
        assertThat(firedRulesListener.ruleFiredCount("SingleAbsence")).isEqualTo(count);

        // rule was already fired and no changes were made to working memory
        ksession.fireAllRules();
        advanceTime(LONG_SLEEP_TIME);
        ksession.fireAllRules();
        assertThat(firedRulesListener.ruleFiredCount("SingleAbsence")).isEqualTo(count);
    }

    @Test
    public void testConstrainedAbsence() {
        final EntryPoint entryPoint = ksession.getEntryPoint("EventStream");

        int count = 0;

        count++;
        for (int i = 0; i < LOOPS; i++) {
            entryPoint.insert(new TestEvent(count, "EventB"));
            ksession.fireAllRules();
            advanceTime(LONG_SLEEP_TIME);
        }

        FactHandle handle;
        for (int i = 0; i < LOOPS; i++) {
            handle = entryPoint.insert(new TestEvent(i, "EventA"));
            advanceTime(LONG_SLEEP_TIME);
            ksession.fireAllRules();

            entryPoint.delete(handle);
            count++;
            advanceTime(LONG_SLEEP_TIME);
            ksession.fireAllRules();
        }

        ksession.fireAllRules();
        assertThat(firedRulesListener.ruleFiredCount("SingleConstrained")).isEqualTo(count);
    }

    @Test
    public void testMultipleEvents() {
        final EntryPoint entryPoint = ksession.getEntryPoint("EventStream");

        int count = 0;

        while (count < LOOPS / 2) {
            entryPoint.insert(new TestEvent(count, "EventA"));
            ksession.fireAllRules();
            count++;
            advanceTime(SHORT_SLEEP_TIME);
            ksession.fireAllRules();
        }
        assertThat(firedRulesListener.ruleFiredCount("MultipleEvents")).isEqualTo(count);

        entryPoint.insert(new TestEvent(count, "EventA"));
        final FactHandle handle = entryPoint.insert(new TestEvent(-1, "EventB"));
        advanceTime(SHORT_SLEEP_TIME);
        ksession.fireAllRules();
        assertThat(firedRulesListener.ruleFiredCount("MultipleEvents")).isEqualTo(count);

        entryPoint.delete(handle);
        ksession.fireAllRules();
        assertThat(firedRulesListener.ruleFiredCount("MultipleEvents")).isEqualTo(count);
        // it shouldn't fire because of the duration
        advanceTime(SHORT_SLEEP_TIME);
        ksession.fireAllRules();
        assertThat(firedRulesListener.ruleFiredCount("MultipleEvents")).isEqualTo(count);
        // it shouldn't fire because event A is gone out of window

        while (count < LOOPS) {
            entryPoint.insert(new TestEvent(count, "EventA"));
            ksession.fireAllRules();
            count++;
            advanceTime(SHORT_SLEEP_TIME);
            ksession.fireAllRules();
        }

        ksession.fireAllRules();
        assertThat(firedRulesListener.ruleFiredCount("MultipleEvents")).isEqualTo(count);
    }

    @Test
    public void testMultipleEntryPoints() {
        final EntryPoint entryPoint = ksession.getEntryPoint("EventStream");

        final EntryPoint otherStream = ksession.getEntryPoint("OtherStream");
        int count = 0;

        count++;
        ksession.fireAllRules();
        advanceTime(LONG_SLEEP_TIME);
        ksession.fireAllRules();
        assertThat(firedRulesListener.ruleFiredCount("MultipleEntryPoints")).isEqualTo(count);

        FactHandle handle;
        for (int i = 0; i < LOOPS; i++) {
            handle = entryPoint.insert(new TestEvent(count, "EventA"));
            ksession.fireAllRules();
            advanceTime(LONG_SLEEP_TIME);

            entryPoint.delete(handle);
            ksession.fireAllRules();
            count++;
            advanceTime(LONG_SLEEP_TIME);
            ksession.fireAllRules();
        }

        for (int i = 0; i < LOOPS; i++) {
            handle = otherStream.insert(new TestEvent(count, "EventB"));
            advanceTime(LONG_SLEEP_TIME);
            ksession.fireAllRules();

            otherStream.delete(handle);
            count++;
            advanceTime(SHORT_SLEEP_TIME);
            ksession.fireAllRules();
        }

        ksession.fireAllRules();
        assertThat(firedRulesListener.ruleFiredCount("MultipleEntryPoints")).isEqualTo(count);
    }

    private void advanceTime(final long amount) {
        final SessionPseudoClock clock = ksession.getSessionClock();
        clock.advanceTime(amount, TimeUnit.MILLISECONDS);
    }

    /**
     * Simple event used for tests.
     */
    public static class TestEvent implements Serializable {

        private static final long serialVersionUID = -6985691286327371275L;
        private final Integer id;
        private final String name;

        public TestEvent(final Integer id, final String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return String.format("TestEvent[id=%s, name=%s]", id, name);
        }
    }

    /**
     * Listener tracking number of rules fired.
     */
    public static class TrackingAgendaEventListener extends DefaultAgendaEventListener {

        private final Map<String, Integer> rulesFired = new HashMap<String, Integer>();

        @Override
        public void afterMatchFired(final AfterMatchFiredEvent event) {
            final String rule = event.getMatch().getRule().getName();
            if (isRuleFired(rule)) {
                rulesFired.put(rule, rulesFired.get(rule) + 1);
            } else {
                rulesFired.put(rule, 1);
            }
        }

        /**
         * Return true if the rule was fired at least once
         *
         * @param rule - name of the rule
         * @return true if the rule was fired
         */
        public boolean isRuleFired(final String rule) {
            return rulesFired.containsKey(rule);
        }

        /**
         * Returns number saying how many times the rule was fired
         *
         * @param rule - name of the rule
         * @return number how many times rule was fired, 0 if rule wasn't fired
         */
        public int ruleFiredCount(final String rule) {
            if (isRuleFired(rule)) {
                return rulesFired.get(rule);
            } else {
                return 0;
            }
        }

        public void clear() {
            rulesFired.clear();
        }
    }
}