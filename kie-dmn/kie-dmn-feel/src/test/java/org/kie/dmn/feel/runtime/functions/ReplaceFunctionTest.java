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
package org.kie.dmn.feel.runtime.functions;

import org.junit.jupiter.api.Test;
import org.kie.dmn.feel.runtime.events.InvalidParametersEvent;

class ReplaceFunctionTest {

    private static final ReplaceFunction replaceFunction = ReplaceFunction.INSTANCE;

    @Test
    void invokeNull() {
        FunctionTestUtil.assertResultError(replaceFunction.invoke(null, null, null), InvalidParametersEvent.class);
        FunctionTestUtil.assertResultError(replaceFunction.invoke("testString", null, null), InvalidParametersEvent.class);
        FunctionTestUtil.assertResultError(replaceFunction.invoke("testString", "test", null), InvalidParametersEvent.class);
        FunctionTestUtil.assertResultError(replaceFunction.invoke(null, "test", null), InvalidParametersEvent.class);
        FunctionTestUtil.assertResultError(replaceFunction.invoke(null, "test", "ttt"), InvalidParametersEvent.class);
        FunctionTestUtil.assertResultError(replaceFunction.invoke(null, null, "ttt"), InvalidParametersEvent.class);
    }

    @Test
    void invokeNullWithFlags() {
        FunctionTestUtil.assertResultError(replaceFunction.invoke(null, null, null, null), InvalidParametersEvent.class);
        FunctionTestUtil.assertResultError(replaceFunction.invoke("testString", null, null, null), InvalidParametersEvent.class);
        FunctionTestUtil.assertResultError(replaceFunction.invoke("testString", "test", null, null), InvalidParametersEvent.class);
        FunctionTestUtil.assertResultError(replaceFunction.invoke(null, "test", null, null), InvalidParametersEvent.class);
        FunctionTestUtil.assertResultError(replaceFunction.invoke(null, "test", "ttt", null), InvalidParametersEvent.class);
        FunctionTestUtil.assertResultError(replaceFunction.invoke(null, null, "ttt", null), InvalidParametersEvent.class);
        FunctionTestUtil.assertResultError(replaceFunction.invoke(null, null, null, "s"), InvalidParametersEvent.class);
        FunctionTestUtil.assertResultError(replaceFunction.invoke("testString", null, null, "s"), InvalidParametersEvent.class);
        FunctionTestUtil.assertResultError(replaceFunction.invoke("testString", "test", null, "s"), InvalidParametersEvent.class);
        FunctionTestUtil.assertResultError(replaceFunction.invoke(null, "test", null, "s"), InvalidParametersEvent.class);
        FunctionTestUtil.assertResultError(replaceFunction.invoke(null, "test", "ttt", "s"), InvalidParametersEvent.class);
        FunctionTestUtil.assertResultError(replaceFunction.invoke(null, null, "ttt", "s"), InvalidParametersEvent.class);
    }

    @Test
    void invokeUnsupportedFlags() {
        FunctionTestUtil.assertResultError(replaceFunction.invoke("testString", "^test", "ttt", "g"), InvalidParametersEvent.class);
        FunctionTestUtil.assertResultError(replaceFunction.invoke("testString", "^test", "ttt", "p"), InvalidParametersEvent.class);
        FunctionTestUtil.assertResultError(replaceFunction.invoke("testString", "^test", "ttt", "X"), InvalidParametersEvent.class);
        FunctionTestUtil.assertResultError(replaceFunction.invoke("testString", "^test", "ttt", "X"), InvalidParametersEvent.class);
        FunctionTestUtil.assertResultError(replaceFunction.invoke("testString", "^test", "ttt", "iU"), InvalidParametersEvent.class);
        FunctionTestUtil.assertResultError(replaceFunction.invoke("testString", "^test", "ttt", "iU asd"), InvalidParametersEvent.class);
    }

    @Test
    void invokeInvalidRegExPattern() {
        FunctionTestUtil.assertResultError(replaceFunction.invoke("testString", "(?=\\s)", "ttt"), InvalidParametersEvent.class);
    }

    @Test
    void invokeWithoutFlagsPatternMatches() {
        FunctionTestUtil.assertResult(replaceFunction.invoke("testString", "^test", "ttt"), "tttString");
        FunctionTestUtil.assertResult(replaceFunction.invoke("testStringtest", "^test", "ttt"), "tttStringtest");
    }

    @Test
    void invokeWithoutFlagsPatternNotMatches() {
        FunctionTestUtil.assertResult(replaceFunction.invoke("testString", "ttest", "ttt"), "testString");
        FunctionTestUtil.assertResult(replaceFunction.invoke("testString", "$test", "ttt"), "testString");
    }

    @Test
    void invokeWithFlagDotAll() {
        FunctionTestUtil.assertResult(replaceFunction.invoke("fo\nbar", "o.b", "ttt", "s"), "ftttar");
    }

    @Test
    void invokeWithFlagMultiline() {
        FunctionTestUtil.assertResult(replaceFunction.invoke("foo\nbar", "^b", "ttt", "m"), "foo\ntttar");
    }

    @Test
    void invokeWithFlagCaseInsensitive() {
        FunctionTestUtil.assertResult(replaceFunction.invoke("foobar", "^fOO", "ttt", "i"), "tttbar");
    }

    @Test
    void invokeWithAllFlags() {
        FunctionTestUtil.assertResult(replaceFunction.invoke("fo\nbar", "O.^b", "ttt", "smi"), "ftttar");
    }
}