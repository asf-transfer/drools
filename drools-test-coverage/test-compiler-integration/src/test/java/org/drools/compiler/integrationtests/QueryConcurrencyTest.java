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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.drools.mvel.expr.MvelEvaluator;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.TestParametersUtil2;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "runTurtleTests", matches = "true")
public class QueryConcurrencyTest {

    private static int LOOP = 500;

    private static int THREADS = 32;
    private static int REQUESTS = 32;

    public static Stream<KieBaseTestConfiguration> parameters() {
        return TestParametersUtil2.getKieBaseCloudConfigurations(false).stream();
    }

    @ParameterizedTest(name = "KieBase type={0}")
	@MethodSource("parameters")
    @Timeout(300000)
    public void testConstraintConcurrency(KieBaseTestConfiguration kieBaseTestConfiguration) {
        final String drl =
                "package com.example.reproducer\n" +
                        "import " + Bus.class.getCanonicalName() + ";\n" +
                        "import static " + QueryConcurrencyTest.class.getCanonicalName() + ".TOSTRING;\n" +
                        "dialect \"mvel\"\n" +
                        "query checkLength(String $s, int $l)\n" +
                        "    $s := String( length == $l )\n" +
                        "end\n" +
                        "rule \"rule_mt_1a\"\n" +
                        "    when\n" +
                        "        $bus : Bus( $check: \"GAMMA RAY\",\n" +
                        "                    $title: \"POWER PLANT\")\n" +
                        "        checkLength(TOSTRING($bus.karaoke.dvd[$title].artist), 9;)\n" +
                        "    then\n" +

                        "end";

        List<Exception> exceptions = new ArrayList<>();

        System.setProperty(MvelEvaluator.THREAD_SAFETY_PROPERTY, "synced_till_eval");

        try {
            KieBase kieBase = null;
            if (kieBaseTestConfiguration.isExecutableModel()) { // There's no such a thing as jitting, so we can create the KieBase once
                kieBase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("accumulate-test", kieBaseTestConfiguration, drl);
            }
            for (int i = 0; i < LOOP; i++) {
                if (!kieBaseTestConfiguration.isExecutableModel()) { // to reset MVELConstraint Jitting we need to create a new KieBase each time
                    kieBase = KieBaseUtil.getKieBaseFromKieModuleFromDrl("accumulate-test", kieBaseTestConfiguration, drl);
                }
                ExecutorService executor = Executors.newFixedThreadPool(THREADS);
                CountDownLatch latch = new CountDownLatch(THREADS);
                for (int j = 0; j < REQUESTS; j++) {
                    KieBase finalKieBase = kieBase;
                    executor.execute(new Runnable() {

                        public void run() {
                            KieSession kSession = finalKieBase.newKieSession();

                            Bus bus1 = new Bus("red", 30);
                            bus1.getKaraoke().getDvd().put("POWER PLANT", new Album("POWER PLANT", "GAMMA RAY"));
                            bus1.getKaraoke().getDvd().put("Somewhere Out In Space", new Album("Somewhere Out In Space", "GAMMA RAY"));
                            kSession.insert(bus1);
                            kSession.insert("GAMMA RAY");

                            try {
                                latch.countDown();
                                latch.await();
                            } catch (InterruptedException e) {
                                // ignore
                            }

                            try {
                                kSession.fireAllRules();
                            } catch (Exception e) {
                                exceptions.add(e);
                            } finally {
                                kSession.dispose();
                            }
                        }
                    });
                }

                executor.shutdown();
                try {
                    executor.awaitTermination(1000000, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    // ignore
                }
            }

            if (!exceptions.isEmpty()) {
                exceptions.get(0).printStackTrace();
            }

            assertThat(exceptions.size()).isEqualTo(0);

        } finally {
            System.clearProperty(MvelEvaluator.THREAD_SAFETY_PROPERTY);
        }
    }

    public static class Bus {

        private String name;
        private int capacity;
        private BigDecimal weight;
        private Karaoke karaoke = new Karaoke();

        public Bus(String name, int capacity) {
            this.name = name;
            this.capacity = capacity;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getPerson() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public BigDecimal getWeight() {
            return weight;
        }

        public void setWeight(BigDecimal weight) {
            this.weight = weight;
        }

        public Karaoke getKaraoke() {
            return karaoke;
        }
    }

    public static class Karaoke {

        private Map<String, Album> dvd = new HashMap<>();

        public Map<String, Album> getDvd() {
            return dvd;
        }

        public void fix() {
            dvd = Collections.unmodifiableMap(dvd);
        }
    }

    public static class Album {

        private String title;
        private String artist;

        public Album(String title, String artist) {
            this.title = title;
            this.artist = artist;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getArtist() {
            return artist;
        }

        public void setArtist(String artist) {
            this.artist = artist;
        }
    }

    public static String TOSTRING(String s) {
        return s == null ? "null" : s;
    }

    public static String TOSTRING(Object o) {
        return o == null ? "null" : o.toString();
    }
}
