/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.drl.engine.compilation.model;

import org.kie.efesto.common.api.exceptions.KieEfestoCommonException;
import org.kie.efesto.compilationmanager.api.model.EfestoCompilationContext;
import org.kie.efesto.compilationmanager.api.model.EfestoCompilationContextImpl;
import org.kie.internal.builder.KnowledgeBuilderConfiguration;
import org.kie.memorycompiler.KieMemoryCompiler;

public interface DrlCompilationContext extends EfestoCompilationContext {

    public static DrlCompilationContext buildWithEfestoCompilationContext(EfestoCompilationContext efestoCompilationContext) {
        if (efestoCompilationContext instanceof EfestoCompilationContextImpl) {
            return new DrlCompilationContextImpl((EfestoCompilationContextImpl)efestoCompilationContext);
        } else {
            throw new KieEfestoCommonException("DrlCompilationContext.buildWithEfestoCompilationContext supports only EfestoCompilationContextImpl : efestoCompilationContext = " + efestoCompilationContext.getClass());
        }
    }

    KnowledgeBuilderConfiguration newKnowledgeBuilderConfiguration();

    public static DrlCompilationContext buildWithParentClassLoader(ClassLoader parentClassLoader) {
        return new DrlCompilationContextImpl(new KieMemoryCompiler.MemoryCompilerClassLoader(parentClassLoader));
    }
}
