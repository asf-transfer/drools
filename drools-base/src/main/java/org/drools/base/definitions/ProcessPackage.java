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
package org.drools.base.definitions;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.kie.api.definition.process.Process;
import org.kie.api.internal.io.ResourceTypePackage;
import org.kie.api.io.ResourceType;

/**
 * A package containing BPM processes
 */
public class ProcessPackage implements ResourceTypePackage<Process> {

    /**
     * Finds or creates and registers a package in the given registry instance
     * @return the package that has been found
     */
    public static ProcessPackage getOrCreate(ResourceTypePackageRegistry rtps) {
        ProcessPackage rtp = (ProcessPackage) rtps.get(ResourceType.BPMN2);
        if (rtp == null) {
            rtp = new ProcessPackage();
            // register the same instance for all types. There is no distinction
            rtps.put(ResourceType.BPMN2, rtp);
            rtps.put(ResourceType.CMMN, rtp);
        }
        return rtp;
    }

    private final Map<String, Process> ruleFlows = new HashMap<>();

    public Map<String, Process> getRuleFlows() {
        return this.ruleFlows;
    }

    /**
     * The ResourceType for {@link ProcessPackage} is always BPMN2,
     * but there is no distinction between CMMN as they all live under
     * the same package.
     */
    @Override
    public ResourceType getResourceType() {
        return ResourceType.BPMN2;
    }

    public Process lookup(String id) {
        return ruleFlows.get(id);
    }

    @Override
    public void add(Process processedResource) {
        this.ruleFlows.put(processedResource.getId(), processedResource);
    }

    public Iterator<Process> iterator() {
        return getRuleFlows().values().iterator();
    }

    public void remove(String id) {
        ruleFlows.remove(id);
    }
}
