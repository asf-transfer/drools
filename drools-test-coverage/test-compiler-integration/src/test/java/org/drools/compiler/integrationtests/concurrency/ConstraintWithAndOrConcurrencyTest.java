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
package org.drools.compiler.integrationtests.concurrency;

import java.util.stream.Stream;

import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.TestParametersUtil2;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;

@EnabledIfSystemProperty(named = "runTurtleTests", matches = "true")
public class ConstraintWithAndOrConcurrencyTest extends BaseConcurrencyTest {

    public static Stream<KieBaseTestConfiguration> parameters() {
        return TestParametersUtil2.getKieBaseCloudConfigurations(true).stream();
    }

    protected String getDrl() {
        return "package com.example.reproducer\n" +
               "import " + Bus.class.getCanonicalName() + ";\n" +
               "import static " + StaticUtils.class.getCanonicalName() + ".TOSTRING;\n" +
               "dialect \"mvel\"\n" +
               "rule \"rule_mt_1a\"\n" +
               "    when\n" +
               "        $bus : Bus( $check: \"GAMMA RAY\",\n" +
               "                    $title: \"POWER PLANT\",\n" +
               "                    karaoke.dvd[$title] != null,\n" +
               "                    TOSTRING(karaoke.dvd[$title].artist) == \"BBB\" || TOSTRING(karaoke.dvd[$title].artist) >= \"01\" && TOSTRING(karaoke.dvd[$title].artist) <= \"39\" )\n" +
               "    then\n" +
               "end";
    }

    protected void preprocess(KieBase kieBase) {
        // 1st run
        KieSession kSession1 = kieBase.newKieSession();
        Bus bus1 = new Bus("red", 30);
        bus1.getKaraoke().getDvd().put("POWER PLANT", new Album("POWER PLANT", "BBB")); // match the 1st condition -> short circuit
        bus1.getKaraoke().getDvd().put("Somewhere Out In Space", new Album("Somewhere Out In Space", "GAMMA RAY"));
        kSession1.insert(bus1);
        kSession1.fireAllRules();
        kSession1.dispose();

        // 2nd run
        KieSession kSession2 = kieBase.newKieSession();
        Bus bus2 = new Bus("red", 30);
        bus2.getKaraoke().getDvd().put("POWER PLANT", new Album("POWER PLANT", "00")); // not match the 1st condition, not match the 2nd condition -> short circuit
        bus2.getKaraoke().getDvd().put("Somewhere Out In Space", new Album("Somewhere Out In Space", "GAMMA RAY"));
        kSession2.insert(bus2);
        kSession2.fireAllRules();
        kSession2.dispose();
    }

    protected void insertFacts(KieSession kSession) {
        Bus bus1 = new Bus("red", 30);
        bus1.getKaraoke().getDvd().put("POWER PLANT", new Album("POWER PLANT", "10")); // not match the 1st condition, match the 2nd & 3rd condition
        bus1.getKaraoke().getDvd().put("Somewhere Out In Space", new Album("Somewhere Out In Space", "GAMMA RAY"));
        kSession.insert(bus1);
    }
}
