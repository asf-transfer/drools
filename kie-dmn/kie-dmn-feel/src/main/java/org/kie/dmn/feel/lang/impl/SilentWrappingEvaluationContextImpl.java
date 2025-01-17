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
package org.kie.dmn.feel.lang.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

import org.kie.dmn.api.core.DMNRuntime;
import org.kie.dmn.api.feel.runtime.events.FEELEvent;
import org.kie.dmn.api.feel.runtime.events.FEELEventListener;
import org.kie.dmn.feel.lang.EvaluationContext;
import org.kie.dmn.feel.lang.FEELDialect;

/**
 * This EvaluationContext should only be used to "try" evaluations
 */
public class SilentWrappingEvaluationContextImpl implements EvaluationContext {

    private EvaluationContext wrapped;

    /**
     * This EvaluationContext should only be used to "try" evaluations
     */
    public SilentWrappingEvaluationContextImpl(EvaluationContext wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public void enterFrame() {
        wrapped.enterFrame();
    }

    @Override
    public void exitFrame() {
        wrapped.exitFrame();
    }

    @Override
    public EvaluationContext current() {
        return new SilentWrappingEvaluationContextImpl(wrapped.current());
    }

    @Override
    public void setValue(String name, Object value) {
        wrapped.setValue(name, value);
    }

    @Override
    public Object getValue(String name) {
        return wrapped.getValue(name);
    }

    @Override
    public Object getValue(String[] name) {
        return wrapped.getValue(name);
    }

    @Override
    public boolean isDefined(String name) {
        return wrapped.isDefined(name);
    }

    @Override
    public boolean isDefined(String[] name) {
        return isDefined(name);
    }

    @Override
    public Map<String, Object> getAllValues() {
        return wrapped.getAllValues();
    }

    @Override
    public void notifyEvt(Supplier<FEELEvent> event) {
        // do nothing.
    }

    @Override
    public Collection<FEELEventListener> getListeners() {
        return Collections.emptyList();
    }

    @Override
    public void setRootObject(Object v) {
        wrapped.setRootObject(v);
    }

    @Override
    public Object getRootObject() {
        return wrapped.getRootObject();
    }

    @Override
    public DMNRuntime getDMNRuntime() {
        return wrapped.getDMNRuntime();
    }

    @Override
    public ClassLoader getRootClassLoader() {
        return wrapped.getRootClassLoader();
    }

    @Override
    public FEELDialect getFEELDialect() {
        return wrapped.getFEELDialect();
    }
}
