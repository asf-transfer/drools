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
package org.kie.memorycompiler.jdknative;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import javax.tools.JavaCompiler;

import org.junit.jupiter.api.Test;
import org.kie.memorycompiler.KieMemoryCompilerException;

public class NativeJavaCompilerTest {

	@Test
	public void simulateJre() {
		NativeJavaCompiler compiler = new NativeJavaCompiler(new NullJavaCompilerFinder());

		assertThatExceptionOfType(KieMemoryCompilerException.class).isThrownBy(() -> compiler.compile(null, null, null, null, null));
	}

	@Test
	public void simulateJreWithException() {
		NativeJavaCompiler compiler = new NativeJavaCompiler(new ExceptionThrowingJavaCompilerFinder());

		assertThatExceptionOfType(KieMemoryCompilerException.class).isThrownBy(() -> compiler.compile(null, null, null, null, null));
	}
	
	private static class NullJavaCompilerFinder implements JavaCompilerFinder {

		@Override
		public JavaCompiler getJavaCompiler() {
			return null;
		}
	}
	
	private static class ExceptionThrowingJavaCompilerFinder implements JavaCompilerFinder {

		@Override
		public JavaCompiler getJavaCompiler() {
			throw new RuntimeException("Test exception");
		}
	}


}
