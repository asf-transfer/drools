/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.reliability.infinispan;

import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.kie.api.runtime.conf.PersistedSessionOption;
import org.kie.api.time.SessionPseudoClock;
import org.test.domain.StockTick;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledIf("isProtoStream")
@ExtendWith(BeforeAllMethodExtension.class)
class ReliabilityCepTest extends ReliabilityTestBasics {

    private static final String CEP_RULE =
            "import " + StockTick.class.getCanonicalName() + ";" +
                    "global java.util.List results;" +
                    "rule R when\n" +
                    "    $a : StockTick( company == \"DROO\" )\n" +
                    "    $b : StockTick( company == \"ACME\", this after[5s,8s] $a )\n" +
                    "then\n" +
                    "    results.add(\"fired\");\n" +
                    "end\n";

    @ParameterizedTest
    @MethodSource("strategyProviderStoresOnlyWithExplicitSafepoints") // FULL fails with "ReliablePropagationList; no valid constructor"
    void insertAdvanceInsertFailoverFire_shouldRecoverFromFailover(PersistedSessionOption.PersistenceStrategy persistenceStrategy, PersistedSessionOption.SafepointStrategy safepointStrategy) {

        createSession(CEP_RULE, persistenceStrategy, safepointStrategy, EventProcessingOption.STREAM, ClockTypeOption.PSEUDO);

        SessionPseudoClock clock = getSessionClock();

        insert(new StockTick("DROO"));
        clock.advanceTime(6, TimeUnit.SECONDS);
        insert(new StockTick("ACME"));

        //-- Assume JVM down here. Fail-over to other JVM or rebooted JVM
        //-- ksession and kbase are lost. CacheManager is recreated. Client knows only "id"
        failover();
        restoreSession(CEP_RULE, persistenceStrategy, safepointStrategy, EventProcessingOption.STREAM, ClockTypeOption.PSEUDO);
        clock = getSessionClock();

        assertThat(fireAllRules()).isEqualTo(1);
        assertThat(getResults()).containsExactlyInAnyOrder("fired");
        clearResults();

        clock.advanceTime(1, TimeUnit.SECONDS);
        insert(new StockTick("ACME"));

        assertThat(fireAllRules()).isEqualTo(1);
        assertThat(getResults()).containsExactlyInAnyOrder("fired");
        clearResults();

        clock.advanceTime(3, TimeUnit.SECONDS);
        insert(new StockTick("ACME"));

        assertThat(fireAllRules()).isZero();
        assertThat(getResults()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("strategyProviderStoresOnlyWithExplicitSafepoints") // FULL fails with "ReliablePropagationList; no valid constructor"
    void insertAdvanceInsertFailoverFireTwice_shouldRecoverFromFailover(PersistedSessionOption.PersistenceStrategy persistenceStrategy, PersistedSessionOption.SafepointStrategy safepointStrategy) {

        KieSession session1 = createSession(CEP_RULE, persistenceStrategy, safepointStrategy, EventProcessingOption.STREAM, ClockTypeOption.PSEUDO);

        SessionPseudoClock clock = getSessionClock(session1);

        insert(session1, new StockTick("DROO"));
        clock.advanceTime(6, TimeUnit.SECONDS);
        insert(session1, new StockTick("ACME"));

        //-- Assume JVM down here. Fail-over to other JVM or rebooted JVM
        //-- ksession and kbase are lost. CacheManager is recreated. Client knows only "id"
        failover();
        session1 = restoreSession(session1.getIdentifier(), CEP_RULE, persistenceStrategy, safepointStrategy, EventProcessingOption.STREAM, ClockTypeOption.PSEUDO);
        clock = getSessionClock(session1);

        assertThat(fireAllRules(session1)).isEqualTo(1);
        assertThat(getResults(session1)).containsExactlyInAnyOrder("fired");
        clearResults(session1);

        clock.advanceTime(3, TimeUnit.SECONDS);
        insert(session1, new StockTick("ACME"));

        //-- Assume JVM down here. Fail-over to other JVM or rebooted JVM
        //-- ksession and kbase are lost. CacheManager is recreated. Client knows only "id"
        failover();
        session1 = restoreSession(session1.getIdentifier(), CEP_RULE, persistenceStrategy, safepointStrategy, EventProcessingOption.STREAM, ClockTypeOption.PSEUDO);
        clock = getSessionClock(session1);

        assertThat(fireAllRules(session1)).isZero();
        assertThat(getResults(session1)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("strategyProviderStoresOnlyWithExplicitSafepoints")
    void insertAdvanceFailoverExpireFire_shouldExpireAfterFailover(PersistedSessionOption.PersistenceStrategy persistenceStrategy, PersistedSessionOption.SafepointStrategy safepointStrategy) {

        createSession(CEP_RULE, persistenceStrategy, safepointStrategy, EventProcessingOption.STREAM, ClockTypeOption.PSEUDO);
        SessionPseudoClock clock = getSessionClock();

        insert(new StockTick("DROO"));
        clock.advanceTime(6, TimeUnit.SECONDS);
        insert(new StockTick("ACME"));

        failover();
        restoreSession(CEP_RULE, persistenceStrategy, safepointStrategy, EventProcessingOption.STREAM, ClockTypeOption.PSEUDO);
        clock = getSessionClock();

        clock.advanceTime(58, TimeUnit.SECONDS);
        assertThat(fireAllRules()).as("DROO is expired, but a match is available.")
                .isEqualTo(1);
        assertThat(getFactHandles()).as("DROO should have expired because @Expires = 60s")
                .hasSize(1);
    }

    @ParameterizedTest
    @MethodSource("strategyProviderStoresOnlyWithExplicitSafepoints")
    void insertAdvanceFireFailoverExpire_shouldExpireAfterFailover(PersistedSessionOption.PersistenceStrategy persistenceStrategy, PersistedSessionOption.SafepointStrategy safepointStrategy) {

        createSession(CEP_RULE, persistenceStrategy, safepointStrategy, EventProcessingOption.STREAM, ClockTypeOption.PSEUDO);
        SessionPseudoClock clock = getSessionClock();

        insert(new StockTick("DROO"));
        clock.advanceTime(6, TimeUnit.SECONDS);
        insert(new StockTick("ACME"));

        assertThat(fireAllRules()).as("DROO is expired, but a match is available.")
                .isEqualTo(1);

        failover();
        restoreSession(CEP_RULE, persistenceStrategy, safepointStrategy, EventProcessingOption.STREAM, ClockTypeOption.PSEUDO);
        clock = getSessionClock();

        clock.advanceTime(58, TimeUnit.SECONDS);
        fireAllRules();

        assertThat(getFactHandles()).as("DROO should have expired because @Expires = 60s")
                .hasSize(1);
    }

}