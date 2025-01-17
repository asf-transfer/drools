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

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Supplier;

import org.kie.dmn.api.core.DMNRuntime;
import org.kie.dmn.api.feel.runtime.events.FEELEvent;
import org.kie.dmn.api.feel.runtime.events.FEELEventListener;
import org.kie.dmn.feel.lang.EvaluationContext;
import org.kie.dmn.feel.lang.FEELDialect;
import org.kie.dmn.feel.util.NumberEvalHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvaluationContextImpl implements EvaluationContext {

    private static final Logger LOG = LoggerFactory.getLogger(EvaluationContextImpl.class);

    private final FEELEventListenersManager eventsManager;
    private ArrayDeque<ExecutionFrame> stack;
    private DMNRuntime dmnRuntime;
    private boolean performRuntimeTypeCheck = false;
    private ClassLoader rootClassLoader;
    private final FEELDialect feelDialect;

    private EvaluationContextImpl(ClassLoader cl, FEELEventListenersManager eventsManager, Deque<ExecutionFrame> stack, FEELDialect feelDialect) {
        this.eventsManager = eventsManager;
        this.rootClassLoader = cl;
        this.stack = new ArrayDeque<>(stack);
        this.feelDialect = feelDialect;
    }

    public EvaluationContextImpl(ClassLoader cl, FEELEventListenersManager eventsManager, FEELDialect feelDialect) {
        this(cl, eventsManager, 32, feelDialect);
    }

    public EvaluationContextImpl(ClassLoader cl, FEELEventListenersManager eventsManager, int size, FEELDialect feelDialect) {
        this(cl, eventsManager, new ArrayDeque<>(), feelDialect);
        // we create a rootFrame to hold all the built in functions
        push( RootExecutionFrame.INSTANCE );
        // and then create a global frame to be the starting frame
        // for function evaluation
        ExecutionFrameImpl global = new ExecutionFrameImpl(RootExecutionFrame.INSTANCE, size);
        push( global );
    }

    @Deprecated
    public EvaluationContextImpl(FEELEventListenersManager eventsManager, DMNRuntime dmnRuntime, FEELDialect feelDialect) {
        this(dmnRuntime.getRootClassLoader(), eventsManager, feelDialect);
        this.dmnRuntime = dmnRuntime;
    }

    private EvaluationContextImpl(FEELEventListenersManager eventsManager, FEELDialect feelDialect) {
        this.eventsManager = eventsManager;
        this.feelDialect = feelDialect;
    }

    @Override
    public EvaluationContext current() {
        EvaluationContextImpl ec = new EvaluationContextImpl(eventsManager, feelDialect);
        ec.stack = stack.clone();
        ec.rootClassLoader = this.rootClassLoader;
        ec.dmnRuntime = this.dmnRuntime;
        ec.performRuntimeTypeCheck = this.performRuntimeTypeCheck;
        return ec;
    }

    public void push(ExecutionFrame obj) {
        stack.push( obj );
    }

    public ExecutionFrame pop() {
        return stack.pop();
    }

    public ExecutionFrame peek() {
        return stack.peek();
    }

    public Deque<ExecutionFrame> getStack() {
        return this.stack;
    }

    @Override
    public void enterFrame() {
        LOG.trace("Creating new head element in stack");
        push( new ExecutionFrameImpl( peek() /*, symbols, scope*/ ) );
    }

    public void enterFrame(int size) {
        push(new ExecutionFrameImpl(peek(), size));
    }

    @Override
    public void exitFrame() {
        LOG.trace("Removing head element from stack");
        pop();
    }

    @Override
    public void setValue(String name, Object value) {
        LOG.trace("put {} -> {} in head stack element", name, value);
        peek().setValue(name, NumberEvalHelper.coerceNumber(value ) );
    }
    
    public void setValues(Map<String, Object> values) {
        values.forEach(this::setValue);
    }

    @Override
    public Object getValue(String name) {
        return peek().getValue( name );
    }

    @Override
    public Object getValue(String[] name) {
        if (name.length == 1) {
          return getValue(name[0]);
        } else if (name.length > 1) {
            Map actualObject = (Map) peek().getValue(name[0]);
            // Each sublevel must be a context until the last one.
            for (int i = 1; i < name.length - 1; i++) {
                actualObject = (Map) actualObject.get(name[i]);
                if (actualObject == null) {
                    return null;
                }
            }
            return actualObject.get(name[name.length - 1]);
        } else {
            return null;
        }
    }

    @Override
    public boolean isDefined(String name) {
        return peek().isDefined( name );
    }

    @Override
    public boolean isDefined(String[] name) {
        if (name.length == 1) {
            return isDefined(name[0]);
        } else if (name.length > 1) {
            Map actualObject = (Map) peek().getValue(name[0]);
            // Each sublevel must be a context until the last one.
            for (int i = 1; i < name.length - 1; i++) {
                actualObject = (Map) actualObject.get(name[i]);
                if (actualObject == null) {
                    return false;
                }
            }
            return actualObject.get(name[name.length - 1]) != null;
        } else {
            return false;
        }
    }

    @Override
    public Map<String, Object> getAllValues() {
        if (stack.peek().getRootObject() != null) {
            throw new RuntimeException();
        }
        int initialCapacity = (stack.peek().getAllValues().size() + stack.peekLast().getAllValues().size()) * 2;
        Map<String, Object> values = new HashMap<>(initialCapacity);
        Iterator<ExecutionFrame> it = stack.descendingIterator();
        while ( it.hasNext() ) {
            values.putAll( it.next().getAllValues() );
        }
        return values;
    }

    @Override
    public void notifyEvt(Supplier<FEELEvent> event) {
        FEELEventListenersManager.notifyListeners(eventsManager, event);
    }

    @Override
    public Collection<FEELEventListener> getListeners() {
        return eventsManager.getListeners();
    }

    @Override
    public DMNRuntime getDMNRuntime() {
        return dmnRuntime;
    }

    public void setDMNRuntime(DMNRuntime runtime) {
        this.dmnRuntime = runtime;
    }

    @Override
    public ClassLoader getRootClassLoader() {
        return this.rootClassLoader;
    }

    public void setPerformRuntimeTypeCheck(boolean performRuntimeTypeCheck) {
        this.performRuntimeTypeCheck = performRuntimeTypeCheck;
    }

    public boolean isPerformRuntimeTypeCheck() {
        return performRuntimeTypeCheck;
    }

    @Override
    public void setRootObject(Object v) {
        peek().setRootObject(v);
    }

    @Override
    public Object getRootObject() {
        return peek().getRootObject();
    }

    @Override
    public FEELDialect getFEELDialect() {
        return feelDialect;
    }
}
