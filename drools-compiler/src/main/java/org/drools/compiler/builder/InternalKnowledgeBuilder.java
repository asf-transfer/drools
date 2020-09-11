/*
 * Copyright (c) 2020. Red Hat, Inc. and/or its affiliates.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.compiler.builder;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.drools.compiler.builder.impl.KnowledgeBuilderConfigurationImpl;
import org.drools.compiler.builder.impl.KnowledgeBuilderImpl;
import org.drools.compiler.compiler.PackageBuilderErrors;
import org.drools.compiler.compiler.PackageBuilderResults;
import org.drools.compiler.compiler.PackageRegistry;
import org.drools.compiler.lang.descr.PackageDescr;
import org.drools.core.definitions.InternalKnowledgePackage;
import org.drools.core.impl.InternalKnowledgeBase;
import org.drools.core.rule.TypeDeclaration;
import org.kie.api.KieBase;
import org.kie.api.definition.KiePackage;
import org.kie.api.internal.io.ResourceTypePackage;
import org.kie.api.io.Resource;
import org.kie.api.io.ResourceConfiguration;
import org.kie.api.io.ResourceType;
import org.kie.internal.builder.AssemblerContext;
import org.kie.internal.builder.CompositeKnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderError;
import org.kie.internal.builder.KnowledgeBuilderErrors;
import org.kie.internal.builder.KnowledgeBuilderResults;
import org.kie.internal.builder.ResultSeverity;

public interface InternalKnowledgeBuilder extends KnowledgeBuilder, DroolsAssemblerContext, AssemblerContext {

    ResourceRemovalResult removeObjectsGeneratedFromResource( Resource resource );

    void addPackage( PackageDescr packageDescr );

    InternalKnowledgePackage getPackage(String name);

    void rewireAllClassObjectTypes();

    class ResourceRemovalResult {
        private boolean modified;
        private Collection<String> removedTypes;

        public ResourceRemovalResult(  ) {
            this( false, Collections.emptyList() );
        }

        public ResourceRemovalResult( boolean modified, Collection<String> removedTypes ) {
            this.modified = modified;
            this.removedTypes = removedTypes;
        }

        public void add(ResourceRemovalResult other) {
            mergeModified( other.modified );
            if (this.removedTypes.isEmpty()) {
                this.removedTypes = other.removedTypes;
            } else {
                this.removedTypes.addAll( other.removedTypes );
            }
        }

        public void mergeModified( boolean otherModified ) {
            this.modified = this.modified || otherModified;
        }

        public boolean isModified() {
            return modified;
        }

        public Collection<String> getRemovedTypes() {
            return removedTypes;
        }
    }

    class Empty implements InternalKnowledgeBuilder {

        private final ClassLoader rootClassLoader;
        private final Supplier<KnowledgeBuilderImpl> lazyBuilder;

        private KnowledgeBuilderImpl knowledgeBuilder;

        public Empty( ClassLoader rootClassLoader, Supplier<KnowledgeBuilderImpl> lazyBuilder ) {
            this.rootClassLoader = rootClassLoader;
            this.lazyBuilder = lazyBuilder;
        }

        private synchronized KnowledgeBuilderImpl getKnowledgeBuilder() {
            if (knowledgeBuilder == null) {
                knowledgeBuilder = lazyBuilder.get();
            }
            return knowledgeBuilder;
        }

        @Override
        public Collection<KiePackage> getKnowledgePackages() {
            return Arrays.stream(getKnowledgeBuilder().getPackages()).collect(Collectors.toList());
        }

        @Override
        public boolean hasErrors() {
            return false;
        }

        @Override
        public KnowledgeBuilderErrors getErrors() {
            return new PackageBuilderErrors();
        }

        @Override
        public KnowledgeBuilderResults getResults( ResultSeverity... severities ) {
            return new PackageBuilderResults();
        }

        @Override
        public boolean hasResults( ResultSeverity... severities ) {
            return false;
        }

        @Override
        public ClassLoader getRootClassLoader() {
            return rootClassLoader;
        }

        @Override
        public void rewireAllClassObjectTypes() {
        }

        @Override
        public Map<String, Class<?>> getGlobals() {
            return Collections.emptyMap();
        }

        @Override
        public void add( Resource resource, ResourceType type ) {
            getKnowledgeBuilder().add(resource, type);
        }

        @Override
        public void add( Resource resource, ResourceType type, ResourceConfiguration configuration ) {
            getKnowledgeBuilder().add(resource, type, configuration);
        }

        @Override
        public KieBase newKieBase() {
            return getKnowledgeBuilder().newKieBase();
        }

        @Override
        public void undo() {
            getKnowledgeBuilder().undo();
        }

        @Override
        public CompositeKnowledgeBuilder batch() {
            return getKnowledgeBuilder().batch();
        }

        @Override
        public <T extends ResourceTypePackage<?>> T computeIfAbsent( ResourceType resourceType, String namespace, Function<? super ResourceType, T> mappingFunction ) {
            return getKnowledgeBuilder().computeIfAbsent( resourceType, namespace, mappingFunction );
        }

        @Override
        public void reportError( KnowledgeBuilderError error ) {
            getKnowledgeBuilder().reportError( error );
        }

        @Override
        public ResourceRemovalResult removeObjectsGeneratedFromResource( Resource resource ) {
            return getKnowledgeBuilder().removeObjectsGeneratedFromResource( resource );
        }

        @Override
        public void addPackage( PackageDescr packageDescr ) {
            getKnowledgeBuilder().addPackage( packageDescr );
        }

        @Override
        public InternalKnowledgePackage getPackage( String name ) {
            return getKnowledgeBuilder().getPackage( name );
        }

        @Override
        public KnowledgeBuilderConfigurationImpl getBuilderConfiguration() {
            return getKnowledgeBuilder().getBuilderConfiguration();
        }

        @Override
        public TypeDeclaration getAndRegisterTypeDeclaration( Class<?> cls, String name ) {
            return getKnowledgeBuilder().getAndRegisterTypeDeclaration(cls, name);
        }

        @Override
        public TypeDeclaration getTypeDeclaration( Class<?> typeClass ) {
            return getKnowledgeBuilder().getTypeDeclaration(typeClass);
        }

        @Override
        public List<PackageDescr> getPackageDescrs( String namespace ) {
            return getKnowledgeBuilder().getPackageDescrs(namespace);
        }

        @Override
        public PackageRegistry getPackageRegistry( String packageName ) {
            return getKnowledgeBuilder().getPackageRegistry(packageName);
        }

        @Override
        public InternalKnowledgeBase getKnowledgeBase() {
            return getKnowledgeBuilder().getKnowledgeBase();
        }
    }
}
