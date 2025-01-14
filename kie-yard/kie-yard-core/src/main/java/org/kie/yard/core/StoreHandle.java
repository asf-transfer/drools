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

import org.drools.ruleunits.api.DataHandle;
import org.drools.ruleunits.api.DataSource;
import org.drools.ruleunits.api.SingletonStore;

public class StoreHandle<T> {

    private SingletonStore<T> wrapped;
    private DataHandle datahandle;

    private StoreHandle(T value) {
        wrapped = DataSource.createSingleton();
        datahandle = wrapped.set(value);
    }

    private StoreHandle() {
        wrapped = DataSource.createSingleton();
        datahandle = null;
    }

    public static <T> StoreHandle<T> of(T value) {
        return new StoreHandle<>(value);
    }

    public static <T> StoreHandle<T> empty(Class<T> type) {
        return new StoreHandle<>();
    }

    public DataHandle set(T value) {
        datahandle = wrapped.set(value);
        return datahandle;
    }

    public void clear() {
        datahandle = null;
        wrapped.clear();
    }

    public boolean isValuePresent() {
        return !(datahandle == null);
    }

    public T get() {
        if (datahandle == null) {
            throw new IllegalStateException("was never set");
        }
        @SuppressWarnings("unchecked")
        T result = (T) datahandle.getObject();
        return result;
    }
}
