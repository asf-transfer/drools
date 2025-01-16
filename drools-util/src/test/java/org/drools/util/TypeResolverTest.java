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
package org.drools.util;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeResolverTest {

    @Test
    public void testResolvePrimtiveTypes() throws Exception {
        final ClassTypeResolver resolver = new ClassTypeResolver(new HashSet(), Thread.currentThread().getContextClassLoader());
        assertThat(resolver.resolveType("boolean")).isEqualTo(boolean.class);
        assertThat(resolver.resolveType("double")).isEqualTo(double.class);
        assertThat(resolver.resolveType("float")).isEqualTo(float.class);
        assertThat(resolver.resolveType("int")).isEqualTo(int.class);
        assertThat(resolver.resolveType("char")).isEqualTo(char.class);
        assertThat(resolver.resolveType("long")).isEqualTo(long.class);
        assertThat(resolver.resolveType("byte")).isEqualTo(byte.class);
        assertThat(resolver.resolveType("short")).isEqualTo(short.class);
    }

    @Test
    public void testResolveArrayOfPrimitiveTypes() throws Exception {
        final ClassTypeResolver resolver = new ClassTypeResolver(new HashSet(), Thread.currentThread().getContextClassLoader());
        assertThat(resolver.resolveType("boolean[]")).isEqualTo(boolean[].class);
        assertThat(resolver.resolveType("double[]")).isEqualTo(double[].class);
        assertThat(resolver.resolveType("float[]")).isEqualTo(float[].class);
        assertThat(resolver.resolveType("int[]")).isEqualTo(int[].class);
        assertThat(resolver.resolveType("char[]")).isEqualTo(char[].class);
        assertThat(resolver.resolveType("long[]")).isEqualTo(long[].class);
        assertThat(resolver.resolveType("byte[]")).isEqualTo(byte[].class);
        assertThat(resolver.resolveType("short[]")).isEqualTo(short[].class);
    }

    @Test
    public void testResolveMultidimensionnalArrayOfPrimitiveTypes() throws Exception {
        final ClassTypeResolver resolver = new ClassTypeResolver(new HashSet(), Thread.currentThread().getContextClassLoader());
        assertThat(resolver.resolveType("int[][]")).isEqualTo(int[][].class);
        assertThat(resolver.resolveType("int[][][]")).isEqualTo(int[][][].class);
        assertThat(resolver.resolveType("int[][][][]")).isEqualTo(int[][][][].class);
    }

    @Test
    public void testResolveParametrizedTypes() throws Exception {
        final ClassTypeResolver resolver = new ClassTypeResolver(new HashSet(), Thread.currentThread().getContextClassLoader());
        Type type = resolver.resolveParametrizedType("java.util.Map<Integer, java.util.List<String>>");
        assertThat(type).isInstanceOf(ParameterizedType.class);
        assertThat(((ParameterizedType) type).getRawType()).isEqualTo(Map.class);
        assertThat(((ParameterizedType) type).getActualTypeArguments().length).isEqualTo(2);
        assertThat(((ParameterizedType) type).getActualTypeArguments()[0]).isEqualTo(Integer.class);
        assertThat(((ParameterizedType) type).getActualTypeArguments()[1]).isInstanceOf(ParameterizedType.class);
        assertThat(((ParameterizedType) ((ParameterizedType) type).getActualTypeArguments()[1]).getRawType()).isEqualTo(List.class);
        assertThat(((ParameterizedType) ((ParameterizedType) type).getActualTypeArguments()[1]).getActualTypeArguments().length).isEqualTo(1);
        assertThat(((ParameterizedType) ((ParameterizedType) type).getActualTypeArguments()[1]).getActualTypeArguments()[0]).isEqualTo(String.class);
    }

    @Test
    public void testResolveParametrizedTypesWithWildcardFallbackToRaw() throws Exception {
        final ClassTypeResolver resolver = new ClassTypeResolver(new HashSet(), Thread.currentThread().getContextClassLoader());
        Type type = resolver.resolveParametrizedType("java.util.Map<? extends Number, java.util.List<String>>");
        assertThat(type).isEqualTo(Map.class);
    }
}
