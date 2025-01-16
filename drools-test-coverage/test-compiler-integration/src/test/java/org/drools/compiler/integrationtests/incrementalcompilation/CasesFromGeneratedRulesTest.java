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
package org.drools.compiler.integrationtests.incrementalcompilation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class CasesFromGeneratedRulesTest {

    @Test
    @Timeout(40000)
    public void testInsertFactsFireRulesRemoveRulesReinsertRulesRevertedRules() {
        String rule1 = """
                package com.rules;
                global java.util.List list
                rule R1
                 when
                  Integer()
                 then
                 list.add('R1');
                end
                """;

        String rule2 = """
                package com.rules;
                global java.util.List list
                rule R2
                 when
                  Integer()
                 exists(Integer() and Integer())
                 exists(Integer() and Integer())
                 then
                 list.add('R2');
                end
                """;

        AddRemoveTestCases.insertFactsFireRulesRemoveRulesReinsertRules1(rule1, rule2, TestUtil.RULE1_NAME, TestUtil.RULE2_NAME, null, 1, 2);
    }

    @Test
    @Timeout(40000)
    public void testInsertFactsRemoveRulesFireRulesRemoveRules() {
        String rule1 = """
                package com.rules;
                global java.util.List list
                rule R1
                 when
                  exists(Integer())
                  not(Double() and Double())
                  Integer() not(Double() and Double())
                 then
                 list.add('R1');
                end
                """;

        String rule2 = """
                package com.rules;
                global java.util.List list
                rule R2
                 when
                  exists(Integer())
                  not(Double() and Double())
                  exists(Integer())
                 then
                 list.add('R2');
                end
                """;

        AddRemoveTestCases.insertFactsRemoveRulesFireRulesRemoveRules2(rule1, rule2, TestUtil.RULE1_NAME, TestUtil.RULE2_NAME, null, 1);
    }
}
