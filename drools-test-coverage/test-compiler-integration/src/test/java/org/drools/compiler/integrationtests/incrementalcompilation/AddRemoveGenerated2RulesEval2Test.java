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

import java.util.stream.Stream;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "runTurtleTests", matches = "true")
public class AddRemoveGenerated2RulesEval2Test extends AbstractAddRemoveGenerated2RulesTest {


    public static Stream<ConstraintsPair> parameters() {
        // Placeholder is replaced by actual variable name during constraints generation.
        // This is needed, because when generator generates the same constraint 3-times for a rule,
        // in each constraint must be different variable name, so Drools can process it
        // (variable is "global" in scope of the rule).
        return generateRulesConstraintsCombinations(
                " Integer() \n",
                " ${variableNamePlaceholder}: Integer() eval(${variableNamePlaceholder} == 1) \n",
                " ${variableNamePlaceholder}: Integer() and (eval(true) or eval(${variableNamePlaceholder} == 1) )\n").stream();
    }
}
