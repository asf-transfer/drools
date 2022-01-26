/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.internal.services;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import org.kie.api.internal.runtime.KieRuntimeService;
import org.kie.api.internal.runtime.KieRuntimes;

public class KieRuntimesImpl implements KieRuntimes {
    private Map<String, KieRuntimeService> runtimes;

    @Override
    public Map<String, KieRuntimeService> getRuntimes() {
        if (runtimes == null) {
            runtimes = new HashMap<>();
            ServiceLoader<KieRuntimeService> loader = ServiceLoader.load(KieRuntimeService.class);
            for (KieRuntimeService runtime : loader) {
                runtimes.put(runtime.getServiceInterface().getName(), runtime);
            }
        }
        return this.runtimes;
    }

    @Override
    public Class getServiceInterface() {
        return KieRuntimes.class;
    }
}
