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
package org.drools.reliability.infinispan;

import java.nio.file.Paths;
import java.util.Set;

import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.Storage;
import org.drools.util.FileUtils;
import org.infinispan.Cache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.marshall.JavaSerializationMarshaller;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.globalstate.ConfigurationStorage;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManagerAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.drools.reliability.core.StorageManager.createStorageId;
import static org.drools.reliability.infinispan.InfinispanStorageManagerFactory.DELIMITER;
import static org.drools.reliability.infinispan.InfinispanStorageManagerFactory.INFINISPAN_STORAGE_DIRECTORY;
import static org.drools.reliability.infinispan.InfinispanStorageManagerFactory.SESSION_STORAGE_PREFIX;
import static org.drools.reliability.infinispan.InfinispanStorageManagerFactory.SHARED_STORAGE_PREFIX;
import static org.drools.util.Config.getConfig;

public class EmbeddedStorageManager implements InfinispanStorageManager {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedStorageManager.class);

    public static final String GLOBAL_STATE_DIR = getConfig(INFINISPAN_STORAGE_DIRECTORY, "global/state");

    static final EmbeddedStorageManager INSTANCE = new EmbeddedStorageManager();

    private DefaultCacheManager embeddedCacheManager;

    private EmbeddedCacheManagerAdmin cacheContainerAdmin;
    private Configuration cacheConfiguration;

    public static final String CACHE_DIR = "cache";

    private EmbeddedStorageManager() {}

    @Override
    public void initStorageManager() {
        LOG.info("Using Embedded Cache Manager");

        // Set up a clustered Cache Manager.
        GlobalConfigurationBuilder global = new GlobalConfigurationBuilder();
        global.serialization()
              .marshaller(new JavaSerializationMarshaller())
              .allowList()
              .addRegexps(InfinispanStorageManager.getAllowedPackages());
        global.globalState()
              .enable()
              .persistentLocation(GLOBAL_STATE_DIR)
              .configurationStorage(ConfigurationStorage.OVERLAY);
        global.metrics().gauges(false);

        // Initialize the default Cache Manager.
        embeddedCacheManager = new DefaultCacheManager(global.build());
        cacheContainerAdmin = embeddedCacheManager.administration();

        // Create a distributed cache with synchronous replication.
        ConfigurationBuilder builder = new ConfigurationBuilder();
        builder.persistence().passivation(false)
               .addSoftIndexFileStore()
               .shared(false)
               .dataLocation(CACHE_DIR + "/data")
               .indexLocation(CACHE_DIR + "/index");

        builder.unsafe().unreliableReturnValues(true);

        builder.clustering()
               .cacheMode(CacheMode.LOCAL);

        cacheConfiguration = builder.build();
    }

    @Override
    public <K, V> Storage<K, V> internalGetOrCreateStorageForSession(ReteEvaluator reteEvaluator, String cacheName) {
        Cache<K, V> cache = embeddedCacheManager.administration().getOrCreateCache(createStorageId(reteEvaluator, cacheName), cacheConfiguration);
        return InfinispanStorage.fromCache(cache);
    }

    @Override
    public <K, V> Storage<K, V> getOrCreateSharedStorage(String cacheName) {
        Cache<K, V> cache = embeddedCacheManager.administration().getOrCreateCache(SHARED_STORAGE_PREFIX + cacheName, cacheConfiguration);
        return InfinispanStorage.fromCache(cache);
    }

    @Override
    public void close() {
        embeddedCacheManager.stop();
    }

    @Override
    public void removeStorage(String storageName) {
        if (embeddedCacheManager.cacheExists(storageName)) {
            cacheContainerAdmin.removeCache(storageName);
        }
    }

    @Override
    public void removeStoragesBySessionId(String sessionId) {
        embeddedCacheManager.getCacheNames()
                            .stream()
                            .filter(cacheName -> cacheName.startsWith(SESSION_STORAGE_PREFIX + sessionId + DELIMITER))
                            .forEach(this::removeStorage);
    }

    @Override
    public void removeAllSessionStorages() {
        embeddedCacheManager.getCacheNames()
                            .stream()
                            .filter(cacheName -> cacheName.startsWith(SESSION_STORAGE_PREFIX))
                            .forEach(this::removeStorage);
    }

    @Override
    public Set<String> getStorageNames() {
        return embeddedCacheManager.getCacheNames();
    }

    @Override
    public void setRemoteCacheManager(RemoteCacheManager remoteCacheManager) {
        throw new UnsupportedOperationException("setRemoteCacheManager is not supported in " + this.getClass());
    }

    //--- test purpose

    @Override
    public void restart() {
        // JVM crashed
        embeddedCacheManager.stop();
        embeddedCacheManager = null;
        cacheConfiguration = null;

        // Reboot
        initStorageManager();
    }

    @Override
    public void restartWithCleanUp() {
        // JVM down
        embeddedCacheManager.stop();
        embeddedCacheManager = null;
        cacheConfiguration = null;

        // Remove GlobalState and FileStore
        cleanUpGlobalStateAndFileStore();

        // Reboot
        initStorageManager();
    }

    @Override
    public void setEmbeddedCacheManager(DefaultCacheManager embeddedCacheManager) {
        if (this.embeddedCacheManager != null) {
            this.embeddedCacheManager.stop();
        }
        this.embeddedCacheManager = embeddedCacheManager;
    }

    // test purpose to remove GlobalState and FileStore
    private static void cleanUpGlobalStateAndFileStore() {
        FileUtils.deleteDirectory(Paths.get(GLOBAL_STATE_DIR));
    }

    @Override
    public org.infinispan.client.hotrod.configuration.ConfigurationBuilder provideAdditionalRemoteConfigurationBuilder() {
        throw new UnsupportedOperationException("provideRemoteConfigurationBuilder is not supported in " + this.getClass());
    }

    @Override
    public boolean isRemote() {
        return false;
    }
}
