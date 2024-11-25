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
package org.drools.reliability.test;

import org.drools.reliability.core.StorageManagerFactory;
import org.drools.reliability.core.TestableStorageManager;
import org.drools.reliability.infinispan.InfinispanStorageManager;
import org.infinispan.manager.DefaultCacheManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.drools.reliability.core.StorageManagerFactory.SESSION_STORAGE_PREFIX;
import static org.drools.reliability.infinispan.InfinispanStorageManagerFactory.INFINISPAN_STORAGE_ALLOWED_PACKAGES;
import static org.drools.reliability.infinispan.InfinispanStorageManagerFactory.INFINISPAN_STORAGE_MODE;
import static org.drools.reliability.test.util.TestConfigurationUtils.DROOLS_RELIABILITY_MODULE_TEST;
import static org.drools.util.Config.getConfig;

/**
 *  This class is a unit test for EmbeddedCacheManager methods with a fake cacheManager instead of Infinispan DefaultCacheManager.
 */
@EnabledIf("isEmbeddedInfinispan")
@ExtendWith(BeforeAllMethodExtension.class)
class EmbeddedStorageManagerTest {

    static {
        System.setProperty(INFINISPAN_STORAGE_ALLOWED_PACKAGES, "org.test.domain");
    }

    @AfterEach
    public void tearDown() {
        ((TestableStorageManager) StorageManagerFactory.get().getStorageManager()).restart(); // make sure that FakeCacheManager is removed
    }

    static boolean isEmbeddedInfinispan() {
        return "INFINISPAN".equalsIgnoreCase(getConfig(DROOLS_RELIABILITY_MODULE_TEST))
                && "EMBEDDED".equalsIgnoreCase(getConfig(INFINISPAN_STORAGE_MODE));
    }

    @Test
    void removeAllSessionCaches_shouldLeaveNonSessionCache() {
        ((InfinispanStorageManager) StorageManagerFactory.get().getStorageManager()).setEmbeddedCacheManager(new FakeCacheManager());

        assertThat(StorageManagerFactory.get().getStorageManager().getStorageNames()).containsExactlyInAnyOrder(
                SESSION_STORAGE_PREFIX + "0_" + "epDefault", SESSION_STORAGE_PREFIX + "1_" + "epDefault", "METADATA_0");

        StorageManagerFactory.get().getStorageManager().removeAllSessionStorages();

        assertThat(StorageManagerFactory.get().getStorageManager().getStorageNames()).containsExactly("METADATA_0");
    }

    @Test
    void removeCachesBySessionId_shouldRemoveSpecifiedCacheOnly() {
        ((InfinispanStorageManager) StorageManagerFactory.get().getStorageManager()).setEmbeddedCacheManager(new FakeCacheManager());

        assertThat(StorageManagerFactory.get().getStorageManager().getStorageNames()).containsExactlyInAnyOrder(
                SESSION_STORAGE_PREFIX + "0_" + "epDefault", SESSION_STORAGE_PREFIX + "1_" + "epDefault", "METADATA_0");

        StorageManagerFactory.get().getStorageManager().removeStoragesBySessionId("1");

        assertThat(StorageManagerFactory.get().getStorageManager().getStorageNames()).containsExactlyInAnyOrder(SESSION_STORAGE_PREFIX + "0_" + "epDefault", "METADATA_0");
    }

    public static class FakeCacheManager extends DefaultCacheManager{

        private Map<String, Object> cacheMap = new ConcurrentHashMap<>();

        public FakeCacheManager() {
            cacheMap.put(SESSION_STORAGE_PREFIX + "0_" + "epDefault", new Object());
            cacheMap.put(SESSION_STORAGE_PREFIX + "1_" + "epDefault", new Object());
            cacheMap.put("METADATA_0", new Object());
        }

        @Override
        public Set<String> getCacheNames() {
            return cacheMap.keySet();
        }

        @Override
        public boolean cacheExists(String cacheName) {
            return cacheMap.containsKey(cacheName);
        }

        @Override
        public void stop() {
            // do nothing
        }
    }
}
