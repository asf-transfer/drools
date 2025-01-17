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
package org.kie.dmn.core.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.kie.dmn.api.core.DMNRuntime;
import org.kie.dmn.api.feel.runtime.events.FEELEvent;
import org.kie.dmn.api.feel.runtime.events.FEELEventListener;
import org.kie.dmn.core.BaseDMNContextTest;
import org.kie.dmn.feel.lang.EvaluationContext;
import org.kie.dmn.feel.lang.FEELDialect;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

class DMNContextFEELCtxWrapperTest extends BaseDMNContextTest {

    @Test
    void emptyContext() {
        DMNContextFEELCtxWrapper ctx = new DMNContextFEELCtxWrapper(new EvaluationContextMock(Collections.emptyMap()));
        testCloneAndAlter(ctx, Collections.emptyMap(), Collections.emptyMap());
    }

    @Test
    void contextWithEntries() {
        DMNContextFEELCtxWrapper ctx = new DMNContextFEELCtxWrapper(new EvaluationContextMock(DEFAULT_ENTRIES));
        testCloneAndAlter(ctx, DEFAULT_ENTRIES, Collections.emptyMap());
    }

    @Test
    void contextWithEntriesAndMetadata() {
        DMNContextFEELCtxWrapper ctx = new DMNContextFEELCtxWrapper(new EvaluationContextMock(DEFAULT_ENTRIES), DEFAULT_METADATA);
        testCloneAndAlter(ctx, DEFAULT_ENTRIES, DEFAULT_METADATA);
    }

    @Test
    void pushScopeException() {
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> {
            DMNContextFEELCtxWrapper ctx = new DMNContextFEELCtxWrapper(new EvaluationContextMock(Collections.emptyMap()));
            ctx.pushScope("scopeName", "scopeNamespace");
        });
    }

    @Test
    void popScopeException() {
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> {
            DMNContextFEELCtxWrapper ctx = new DMNContextFEELCtxWrapper(new EvaluationContextMock(Collections.emptyMap()));
            ctx.popScope();
        });
    }

    @Test
    void scopeNamespaceException() {
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> {
            DMNContextFEELCtxWrapper ctx = new DMNContextFEELCtxWrapper(new EvaluationContextMock(Collections.emptyMap()));
            ctx.scopeNamespace();
        });
    }

    private static class EvaluationContextMock implements EvaluationContext {
        private Map<String, Object> entries;

        public EvaluationContextMock(Map<String, Object> entries) {
            this.entries = new HashMap<>(entries);
        }

        // only these methods are required for the mock to behave as expected
        @Override
        public void setValue(String name, Object value) {
            entries.put(name, value);
        }

        @Override
        public Object getValue(String name) {
            return entries.get(name);
        }

        @Override
        public boolean isDefined(String name) {
            return entries.containsKey(name);
        }

        @Override
        public Map<String, Object> getAllValues() {
            return Collections.unmodifiableMap(entries);
        }

        // the rest is unneeded
        @Override
        public void enterFrame() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void exitFrame() {
            throw new UnsupportedOperationException();
        }

        @Override
        public EvaluationContext current() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getValue(String[] name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isDefined(String[] name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DMNRuntime getDMNRuntime() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ClassLoader getRootClassLoader() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void notifyEvt(Supplier<FEELEvent> event) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Collection<FEELEventListener> getListeners() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setRootObject(Object v) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getRootObject() {
            throw new UnsupportedOperationException();
        }

        @Override
        public FEELDialect getFEELDialect() {
            // Defaulting FEELDialect to FEEL
            return FEELDialect.FEEL;
        }
    }

}
