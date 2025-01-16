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
package org.kie.api.internal.utils;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import static org.kie.api.internal.utils.KieService.UNDEFINED;

public class KieServiceLoader {

    static final KieServiceLoader INSTANCE = new KieServiceLoader();

    private static final KieService DUMMY_SERIVCE = new KieService() {};

    private final Map<String, KieService> serviceCache = new ConcurrentHashMap<>();

    private KieServiceLoader() {}

    <T extends KieService> T lookup(Class<T> serviceClass) {
        return lookup(serviceClass, UNDEFINED);
    }

    <T extends KieService> T lookup(Class<T> serviceClass, String tag) {
        String serviceKey = serviceClass.getName() + ":" + tag;

        T cachedService = (T) serviceCache.get(serviceKey);
        if (cachedService != null) {
            return cachedService == DUMMY_SERIVCE ? null : cachedService;
        }

        T loadedService = load(serviceClass, tag);
        serviceCache.put(serviceKey, loadedService == null ? DUMMY_SERIVCE : loadedService);
        return loadedService;
    }

    <T extends KieService> T load(Class<T> serviceClass, String tag) {
        ServiceLoader<T> loader = ServiceLoader.load(serviceClass);
        T service = null;
        for (T impl : loader) {
            if ( tag.equals(impl.serviceTag()) ) { // accept only services with the specified tag
                if (service == null || impl.compareTo(service) > 0) {
                    service = impl;
                }
            }
        }
        return service;
    }
}
