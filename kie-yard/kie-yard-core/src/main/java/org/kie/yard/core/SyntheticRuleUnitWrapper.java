/*
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
package org.kie.yard.core;

import java.util.Map;

import org.drools.ruleunits.api.RuleUnitInstance;
import org.drools.ruleunits.api.RuleUnitProvider;
import org.drools.ruleunits.dsl.SyntheticRuleUnit;

public class SyntheticRuleUnitWrapper implements Firable {

    private final SyntheticRuleUnit wrapped;

    public SyntheticRuleUnitWrapper(SyntheticRuleUnit wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public int fire(Map<String, Object> context, YaRDDefinitions units) {
        RuleUnitInstance<SyntheticRuleUnit> unitInstance = RuleUnitProvider.get().createRuleUnitInstance(wrapped);
        return unitInstance.fire();
    }
}
