/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.reliability.core;

import org.drools.core.common.Storage;

import java.util.*;

public class BatchingStorageDecorator<K, V> implements Storage<K, V> {

    private final Storage<K, V> storage;

    private Map<K, V> batchingMap = new HashMap<>();

    private Set<K> batchingRemoveSet = new HashSet<>();


    public BatchingStorageDecorator(Storage<K, V> storage) {
        this.storage = storage;
    }

    @Override
    public V get(K key) {
        if (batchingRemoveSet.contains(key)) {
            return null;
        }
        return batchingMap.containsKey(key) ? batchingMap.get(key) : storage.get(key);
    }

    @Override
    public V getOrDefault(K key, V value) {
        if (batchingRemoveSet.contains(key)) {
            return value;
        }
        return batchingMap.containsKey(key) ? batchingMap.get(key) : storage.getOrDefault(key, value);
    }

    @Override
    public V put(K key, V value) {
        batchingRemoveSet.remove(key);
        return batchingMap.put(key, value);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> otherMap) {
        batchingRemoveSet.removeAll(otherMap.keySet());
        batchingMap.putAll(otherMap);
    }

    @Override
    public boolean containsKey(K key) {
        return !batchingRemoveSet.contains(key) && ( batchingMap.containsKey(key) || storage.containsKey(key) );
    }

    @Override
    public V remove(K key) {
        batchingRemoveSet.add(key);
        return batchingMap.remove(key);
    }

    @Override
    public void clear() {
        batchingRemoveSet.clear();
        batchingMap.clear();
        storage.clear();
    }

    @Override
    public Collection<V> values() {
        flush();
        return storage.values();
    }

    @Override
    public Set<K> keySet() {
        if (batchingMap.isEmpty() && batchingRemoveSet.isEmpty()) {
            return storage.keySet();
        }
        Set<K> keys = new HashSet<>();
        keys.addAll(storage.keySet());
        keys.addAll(batchingMap.keySet());
        keys.removeAll(batchingRemoveSet);
        return keys;
    }

    @Override
    public int size() {
        return storage.size() + batchingMap.size() - batchingRemoveSet.size();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public void flush() {
        storage.putAll(batchingMap);
        batchingMap.clear();
        batchingRemoveSet.forEach(storage::remove);
        batchingRemoveSet.clear();
    }
}
