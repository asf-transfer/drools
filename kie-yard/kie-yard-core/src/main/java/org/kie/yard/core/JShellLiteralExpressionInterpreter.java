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

import java.util.Map;
import java.util.Map.Entry;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

public class JShellLiteralExpressionInterpreter implements Firable {

    private final String name;
    private final QuotedExprParsed quoted;
    private final ScriptEngine engine;
    private final CompiledScript compiledScript;

    public JShellLiteralExpressionInterpreter(final String nameString,
                                              final QuotedExprParsed quotedExprParsed) {
        this.name = nameString;
        this.quoted = quotedExprParsed;
        try {
            final ScriptEngineManager manager = new ScriptEngineManager();
            engine = manager.getEngineByName("jshell");
            final Compilable compiler = (Compilable) engine;
            compiledScript = compiler.compile(quoted.getRewrittenExpression());
        } catch (Exception e) {
            throw new IllegalArgumentException("parse error", e);
        }
    }

    @Override
    public int fire(final Map<String, Object> context,
                    final YaRDDefinitions units) {
        final Bindings bindings = engine.createBindings();
        // deliberately escape all symbols; a normal symbol will
        // never be in the detected-by-unquoting set, so this
        // set can't be used to selectively put in scope
        for (Entry<String, Object> inKV : context.entrySet()) {
            bindings.put(QuotedExprParsed.escapeIdentifier(inKV.getKey()), inKV.getValue());
        }
        for (Entry<String, StoreHandle<Object>> outKV : units.outputs().entrySet()) {
            if (!outKV.getValue().isValuePresent()) {
                continue;
            }
            bindings.put(QuotedExprParsed.escapeIdentifier(outKV.getKey()), outKV.getValue().get());
        }
        try {
            var result = compiledScript.eval(bindings);
            units.outputs().get(name).set(result);
            return 1;
        } catch (ScriptException e) {
            throw new RuntimeException("interpretation failed at runtime", e);
        }
    }
}
