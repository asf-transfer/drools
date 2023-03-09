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

package org.drools.reliability;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.conf.PersistedSessionOption;
import org.kie.internal.utils.KieHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.drools.reliability.ReliabilityTestUtils.failover;

@ExtendWith(BeforeAllMethodExtension.class)
class ReliabilityTest {

    private static final String BASIC_RULE =
            "import " + Person.class.getCanonicalName() + ";" +
            "global java.util.List results;" +
            "rule X when\n" +
            "  $s: String()\n" +
            "  $p: Person( getName().startsWith($s) )\n" +
            "then\n" +
            "  results.add( $p.getAge() );\n" +
            "end";
	private long savedSessionId;
	private KieSession session;

    static Stream<PersistedSessionOption.Strategy> strategyProvider() {
        return Stream.of(PersistedSessionOption.Strategy.STORES_ONLY, PersistedSessionOption.Strategy.FULL);
    }

    static Stream<PersistedSessionOption.Strategy> strategyProviderStoresOnly() {
        return Stream.of(PersistedSessionOption.Strategy.STORES_ONLY);
    }

    @AfterEach
    public void tearDown() {
        // We can remove this when we implement ReliableSession.dispose() to call CacheManager.removeCachesBySessionId(id)
        CacheManager.INSTANCE.removeAllSessionCaches();

    }

    @ParameterizedTest
    @MethodSource("strategyProviderStoresOnly") // FULL fails with "ReliablePropagationList; no valid constructor"
    void insertFailoverInsertFire_shouldRecoverFromFailover(PersistedSessionOption.Strategy strategy) {

        // 1st round
        {
            session = getFirstKieSession(BASIC_RULE, strategy);

            savedSessionId = session.getIdentifier();

            session.insert("M");
            session.insert(new Person("Mark", 37));
        }

        //-- Assume JVM down here. Fail-over to other JVM or rebooted JVM
        //-- ksession and kbase are lost. CacheManager is recreated. Client knows only "id"
        failover();

        // 2nd round
        {
            session = getSubsequentKieSession(BASIC_RULE, savedSessionId, strategy);

            session.insert(new Person("Edson", 35));
			session.insert(new Person("Mario", 40));

			assertThat(session.fireAllRules()).isEqualTo(2);
			assertThat(getResults(session)).containsExactlyInAnyOrder(37, 40);
        }
    }

    @ParameterizedTest
    @MethodSource("strategyProvider")
    void noFailover(PersistedSessionOption.Strategy strategy) {


        // 1st round
        {
            session = getFirstKieSession(BASIC_RULE, strategy);

            savedSessionId = session.getIdentifier();

            session.insert("M");
            session.insert(new Person("Mark", 37));
        }

        // 2nd round
        {
            session = getSubsequentKieSession(BASIC_RULE, savedSessionId, strategy);

            session.insert(new Person("Toshiya", 35));
			session.insert(new Person("Mario", 40));

			assertThat(session.fireAllRules()).isEqualTo(2);
			assertThat(getResults(session)).containsExactlyInAnyOrder(37, 40);
        }
    }

    @ParameterizedTest
    @MethodSource("strategyProviderStoresOnly") // FULL fails with "ReliablePropagationList; no valid constructor"
    void insertFireInsertFailoverInsertFire_shouldMatchFactInsertedBeforeFailover(PersistedSessionOption.Strategy strategy) {

        // 1st round
        {
            session = getFirstKieSession(BASIC_RULE, strategy);

            savedSessionId = session.getIdentifier();

            session.insert("M");
            session.insert(new Person("Matteo", 41));

            assertThat(session.fireAllRules()).isEqualTo(1);
            assertThat(getResults(session)).containsExactlyInAnyOrder(41);

            session.insert(new Person("Mark", 47)); // This is not yet matched
        }

        failover();

        // 2nd round
        {
            session = getSubsequentKieSession(BASIC_RULE, savedSessionId, strategy);

            session.insert(new Person("Toshiya", 45));
			session.insert(new Person("Mario", 49));

			assertThat(session.fireAllRules()).isEqualTo(2);
			assertThat(getResults(session)).containsExactlyInAnyOrder(47, 49);
        }
    }

    @ParameterizedTest
    @MethodSource("strategyProviderStoresOnly") // FULL fails with "ReliablePropagationList; no valid constructor"
    void insertFireFailoverInsertFire_shouldNotRepeatFiredMatch(PersistedSessionOption.Strategy strategy) {
        // 1st round
        {
            session = getFirstKieSession(BASIC_RULE, strategy);

            savedSessionId = session.getIdentifier();

            session.insert("M");
            session.insert(new Person("Mark", 37));

            assertThat(session.fireAllRules()).isEqualTo(1);
        }

        failover();

        // 2nd round
        {
            session = getSubsequentKieSession(BASIC_RULE, savedSessionId, strategy);

            session.insert(new Person("Edson", 35));
			session.insert(new Person("Mario", 40));

			assertThat(session.fireAllRules()).isEqualTo(1); // Only Mario matches.
			assertThat(getResults(session)).containsExactlyInAnyOrder(40);
        }
    }

    @ParameterizedTest
    @MethodSource("strategyProviderStoresOnly") // FULL fails with "ReliablePropagationList; no valid constructor"
    void updateInRHS_insertFireFailoverInsertFire_shouldNotMatchUpdatedFact(PersistedSessionOption.Strategy strategy) {
        String drl =
                "import " + Person.class.getCanonicalName() + ";" +
                        "global java.util.List results;" +
                        "rule X when\n" +
                        "  $s: String()\n" +
                        "  $p: Person( getName().startsWith($s) )\n" +
                        "then\n" +
                        "  results.add( $p.getAge() );\n" +
                        "  $p.setName(\"-\");\n" +
                        "  update($p); \n" + // updated Person will not match
                       "end";

        // 1st round
        {
            session = getFirstKieSession(drl, strategy);

            savedSessionId = session.getIdentifier();

            session.insert("M");
            session.insert(new Person("Mark", 37));
            session.insert(new Person("Nicole", 27));

            assertThat(session.fireAllRules()).isEqualTo(1);
        }

        failover();

        // 2nd round
        {
            session = getSubsequentKieSession(BASIC_RULE, savedSessionId, strategy);

            session.insert(new Person("John", 22));
			session.insert(new Person("Mary", 42));

			assertThat(session.fireAllRules()).isEqualTo(1);
			assertThat(getResults(session)).containsExactlyInAnyOrder(42);
        }
    }
    

    private List<Integer> getResults(KieSession kieSession) {
        return (List<Integer>) kieSession.getGlobal("results");
    }

    private KieSession getFirstKieSession(String drl, PersistedSessionOption.Strategy strategy) {
        return getKieSession(drl, PersistedSessionOption.newSession(strategy));
    }

    private KieSession getSubsequentKieSession(String drl, long savedSessionId, PersistedSessionOption.Strategy strategy) {
        return getKieSession(drl, PersistedSessionOption.fromSession(savedSessionId, strategy));
    }

    private KieSession getKieSession(String drl, PersistedSessionOption option) {
        KieBase kbase = new KieHelper().addContent(drl, ResourceType.DRL).build();
        KieSessionConfiguration conf = KieServices.get().newKieSessionConfiguration();
        conf.setOption(option);
        KieSession kieSession = kbase.newKieSession(conf, null);
        List<Integer> results = new ArrayList<>();
        kieSession.setGlobal("results", results);
        return kieSession;
    }

}
