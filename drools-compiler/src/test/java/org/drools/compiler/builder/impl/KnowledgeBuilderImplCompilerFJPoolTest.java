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
package org.drools.compiler.builder.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertSame;

public class KnowledgeBuilderImplCompilerFJPoolTest {

    private ClassLoader originalClassLoader;
    private ClassLoader customClassLoader;

    @BeforeEach
    void setUp() {
        // Save the original classloader
        originalClassLoader = Thread.currentThread().getContextClassLoader();

        // Create a custom classloader for testing
        customClassLoader = new ClassLoader(ClassLoader.getSystemClassLoader()) {};
        Thread.currentThread().setContextClassLoader(customClassLoader);
    }

    @AfterEach
    void tearDown() {
        // Restore original classloader
        Thread.currentThread().setContextClassLoader(originalClassLoader);
    }

    private static class ClassLoaderCheckTask extends RecursiveAction {
        private final List<ClassLoader> results;
        private final int index;

        public ClassLoaderCheckTask(List<ClassLoader> results, int index) {
            this.results = results;
            this.index = index;
        }

        @Override
        protected void compute() {
            results.set(index, Thread.currentThread().getContextClassLoader());
        }
    }

    @Test
    void testCompilerPoolClassLoader() throws Exception {
        int numTasks = 5;
        List<ClassLoader> results = new ArrayList<>(numTasks);
        for (int i = 0; i < numTasks; i++) {
            results.add(null);
        }

        // Submit tasks to the COMPILER_POOL
        for (int i = 0; i < numTasks; i++) {
            KnowledgeBuilderImpl.ForkJoinPoolHolder.COMPILER_POOL.submit(new ClassLoaderCheckTask(results, i));
        }

        // Wait for completion
        KnowledgeBuilderImpl.ForkJoinPoolHolder.COMPILER_POOL.awaitQuiescence(1, TimeUnit.SECONDS);

        // Verify all worker threads have the expected classloader
        for (ClassLoader workerClassLoader : results) {
            assertSame(
                    customClassLoader,
                    workerClassLoader,
                    "Worker thread should have inherited the custom classloader"
            );
        }
    }
}
