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
package org.kie.memorycompiler;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class KieMemoryCompilerTest {

    private final static String EXAMPLE_CLASS =
            "package org.kie.memorycompiler;\n" +
            "\n" +
            "public class ExampleClass {\n" +
            "\n" +
            "    public int sum(Integer a, Integer b){\n" +
            "        return a + b;\n" +
            "    }\n" +
            "}";

    @Test
    public void compileAndLoadClass() throws Exception {
        Map<String, String> source = singletonMap("org.kie.memorycompiler.ExampleClass", EXAMPLE_CLASS);
        Map<String, Class<?>> compiled = KieMemoryCompiler.compile(source, this.getClass().getClassLoader());

        Class<?> exampleClazz = compiled.get("org.kie.memorycompiler.ExampleClass");
        assertThat(exampleClazz).isNotNull();

        Object instance = exampleClazz.getDeclaredConstructors()[0].newInstance();
        Method sumMethod = exampleClazz.getMethod("sum", Integer.class, Integer.class);
        Object result = sumMethod.invoke(instance, 2, 3);
        assertThat(result).isEqualTo(5);
    }

    @Test
    public void invalidClass() {
        Map<String, String> source = singletonMap("org.kie.memorycompiler.InvalidJavaClass", "Invalid Java Code");
		assertThatExceptionOfType(KieMemoryCompilerException.class).isThrownBy(
				() -> KieMemoryCompiler.compile(source, this.getClass().getClassLoader()));
    }

    private final static String WARNING_CLASS =
            "package org.kie.memorycompiler;\n" +
            "\n" +
            "import java.util.List;\n" +
            "\n" +
            "public class WarningClass {\n" +
            "\n" +
            "    private List<String> warningField;\n" +
            "\n" +
            "    public void setWarningField(Object warningField) {\n" +
            "        this.warningField = (List<String>) warningField;\n" +
            "    }\n" +
            "\n" +
            "    public int minus(Integer a, Integer b) {\n" +
            "        return a - b;\n" +
            "    }\n" +
            "\n" +
            "};\n";

    @Test
    public void doNotFailOnWarning() throws Exception {
        Map<String, String> source = singletonMap("org.kie.memorycompiler.WarningClass", WARNING_CLASS);
        Map<String, Class<?>> compiled = KieMemoryCompiler.compile(source, this.getClass().getClassLoader());

        Class<?> exampleClazz = compiled.get("org.kie.memorycompiler.WarningClass");
        assertThat(exampleClazz).isNotNull();

        Object instance = exampleClazz.getDeclaredConstructors()[0].newInstance();
        Method minusMethod = exampleClazz.getMethod("minus", Integer.class, Integer.class);
        Object result = minusMethod.invoke(instance, 8, 4);
        assertThat(result).isEqualTo(4);
    }

    private final static String EXAMPLE_INNER_CLASS =
            "package org.kie.memorycompiler;\n" +
            "\n" +
            "public class ExampleClass {\n" +
            "\n" +
            "    public int sum(Integer a, Integer b){\n" +
            "        return a + b;\n" +
            "    }\n" +
            "\n" +
            "    public static class InnerClass { }\n" +
            "}";

    @Test
    public void compileInnerClass() throws Exception {
        Map<String, String> source = singletonMap("org.kie.memorycompiler.ExampleClass", EXAMPLE_INNER_CLASS);
        Map<String, Class<?>> compiled = KieMemoryCompiler.compile(source, this.getClass().getClassLoader());

        assertThat(compiled.size()).isEqualTo(2);

        assertThat(compiled.get("org.kie.memorycompiler.ExampleClass")).isNotNull();
        assertThat(compiled.get("org.kie.memorycompiler.ExampleClass$InnerClass")).isNotNull();
    }
}