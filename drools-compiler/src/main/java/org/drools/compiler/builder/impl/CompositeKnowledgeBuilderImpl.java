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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.drools.compiler.lang.descr.CompositePackageDescr;
import org.kie.api.internal.assembler.KieAssemblers;
import org.kie.api.internal.utils.KieService;
import org.kie.api.io.Resource;
import org.kie.api.io.ResourceConfiguration;
import org.kie.api.io.ResourceType;
import org.kie.api.io.ResourceWithConfiguration;
import org.kie.internal.builder.ChangeType;
import org.kie.internal.builder.CompositeKnowledgeBuilder;
import org.kie.internal.builder.ResourceChange;
import org.kie.internal.builder.ResourceChangeSet;
import org.kie.internal.io.ResourceWithConfigurationImpl;

public class CompositeKnowledgeBuilderImpl implements CompositeKnowledgeBuilder {

    private static final KieAssemblers ASSEMBLERS = KieService.load(KieAssemblers.class);

    private final KnowledgeBuilderImpl kBuilder;

    private final Map<ResourceType, List<ResourceDescr>> resourcesByType = new HashMap<>();

    private RuntimeException buildException = null;

    public ResourceType currentType = null;

    PackageDescrBuilder packageDescrBuilder;

    public CompositeKnowledgeBuilderImpl(KnowledgeBuilderImpl kBuilder) {
        this.kBuilder = kBuilder;
        this.packageDescrBuilder = new PackageDescrBuilder(
                kBuilder.getBuilderConfiguration(),
                kBuilder.getReleaseId(),
                kBuilder::getDslExpander);
    }

    public CompositeKnowledgeBuilder type(ResourceType type) {
        currentType = type;
        return this;
    }

    public CompositeKnowledgeBuilder add(Resource resource) {
        if (currentType == null) {
            throw new RuntimeException("You must declare the type of the resource");
        }
        return add(resource, currentType);
    }

    public CompositeKnowledgeBuilder add(Resource resource, ResourceType type) {
        return add(resource, type, resource.getConfiguration());
    }

    public CompositeKnowledgeBuilder add(Resource resource, ResourceType type, ResourceChangeSet changes) {
        return add(resource, type, resource.getConfiguration(), changes);
    }

    public CompositeKnowledgeBuilder add(Resource resource, ResourceType type, ResourceConfiguration configuration) {
        return add(resource, type, configuration, null);
    }

    public CompositeKnowledgeBuilder add(Resource resource, ResourceType type, ResourceConfiguration configuration, ResourceChangeSet changes) {
        ResourceDescr resourceDescr = new ResourceDescr(configuration, resource, changes);
        List<ResourceDescr> resourceDescrs = this.resourcesByType.get(type);
        if (resourceDescrs == null) {
            resourceDescrs = new ArrayList<>();
            resourcesByType.put(type, resourceDescrs);
        }
        resourceDescrs.add(resourceDescr);
        return this;
    }

    private List<Resource> getResources() {
        List<Resource> resources = new ArrayList<>();
        for (List<ResourceDescr> resourceDescrs : resourcesByType.values()) {
            for (ResourceDescr resourceDescr : resourceDescrs) {
                resources.add(resourceDescr.resource);
            }
        }
        return resources;
    }

    public void build() {
        build(true);
    }

    public void build(boolean buildRules) {
        buildException = null;
        kBuilder.registerBuildResources(getResources());
        buildResources();
        Collection<CompositePackageDescr> packages = buildPackageDescr();
        buildAssemblerResourcesBeforeRules();
        if (buildRules) {
            kBuilder.doFirstBuildStep(packages);
        } else {
            kBuilder.buildPackagesWithoutRules(packages);
        }
        buildProcesses();
        buildAssemblerResourcesAfterRules();
        kBuilder.doSecondBuildStep(packages);
        resourcesByType.clear();
        if (buildException != null) {
            throw buildException;
        }
    }

    private void buildProcesses() {
        buildResourceType(ResourceBuilder.BPMN2_RESOURCE_BUILDER, ResourceType.BPMN2);
        buildResourceType(ResourceBuilder.CMMN_RESOURCE_BUILDER, ResourceType.CMMN);
    }

    private void buildResources() {
        buildResourceType(ResourceBuilder.DSL_RESOURCE_BUILDER, ResourceType.DSL);
        buildResourceType(ResourceBuilder.XSD_RESOURCE_BUILDER, ResourceType.XSD);
    }

    private void buildResourceType(ResourceBuilder resourceBuilder, ResourceType resourceType) {
        List<ResourceDescr> resourcesByType = this.resourcesByType.remove(resourceType);
        if (resourcesByType != null) {
            for (ResourceDescr resourceDescr : resourcesByType) {
                try {
                    kBuilder.setAssetFilter(resourceDescr.getFilter());
                    resourceBuilder.build( kBuilder, resourceDescr );
                } catch (RuntimeException e) {
                    if (buildException == null) {
                        buildException = e;
                    }
                } catch (Exception e) {
                    if (buildException == null) {
                        buildException = new RuntimeException( e );
                    }
                } finally{
                    kBuilder.setAssetFilter(null);
                }
            }
        }
    }

    private static ResourceWithConfiguration descrToResourceWithConfiguration(ResourceDescr rd) {
        return new ResourceWithConfigurationImpl(rd.resource,
                                                 rd.configuration,
                                                 kb -> ((KnowledgeBuilderImpl) kb).setAssetFilter(rd.getFilter()),
                                                 kb -> ((KnowledgeBuilderImpl) kb).setAssetFilter(null));
    }

    private Collection<CompositePackageDescr> buildPackageDescr() {
        Collection<CompositePackageDescr> packages = packageDescrBuilder.build(resourcesByType);
        this.buildException = packageDescrBuilder.getBuildException();
        kBuilder.getBuildResultCollector().addAll(packageDescrBuilder.getResults());
        this.resourcesByType.remove(ResourceType.DRT); // drt is a template for dtables but doesn't have to be built on its own
        return packages;
    }

    private void buildAssemblerResourcesBeforeRules() {
        try {
            for (Map.Entry<ResourceType, List<ResourceDescr>> resourceTypeListEntry : resourcesByType.entrySet()) {
                ResourceType type = resourceTypeListEntry.getKey();
                List<ResourceDescr> descrs = resourceTypeListEntry.getValue();
                for (ResourceDescr descr : descrs) {
                    ASSEMBLERS.addResourceBeforeRules(this.kBuilder, descr.resource, type, descr.configuration);
                }
            }
        } catch (RuntimeException e) {
            if (buildException == null) {
                buildException = e;
            }
        } catch (Exception e) {
            if (buildException == null) {
                buildException = new RuntimeException(e);
            }
        }
    }

    private void buildAssemblerResourcesAfterRules() {
        try {
            for (Map.Entry<ResourceType, List<ResourceDescr>> entry : resourcesByType.entrySet()) {
                List<ResourceWithConfiguration> rds = entry.getValue().stream().map(CompositeKnowledgeBuilderImpl::descrToResourceWithConfiguration).collect(Collectors.toList());
                ASSEMBLERS.addResourcesAfterRules(kBuilder, rds, entry.getKey());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static class ResourceDescr {
        final Resource resource;
        final ResourceConfiguration configuration;
        final ResourceChangeSet changes;
        final Map<String, ResourceChange> changeMap;
        final ChangeType globalChangeType;

        private ResourceDescr(ResourceConfiguration configuration, Resource resource, ResourceChangeSet changes) {
            this.configuration = configuration;
            this.resource = resource;
            this.changes = changes;
            if ( changes != null ) {
                changeMap = new HashMap<>();
                if (!changes.getChanges().isEmpty()) {
                    for ( ResourceChange c : changes.getChanges() ) {
                        changeMap.put( assetId( c.getType(), c.getName() ), c );
                    }
                    globalChangeType = null;
                } else {
                    globalChangeType = changes.getChangeType();
                }
            } else {
                changeMap = null;
                globalChangeType = null;
            }
        }

        public AssetFilter getFilter() {
            return changeMap == null ? null : this.new ChangeSetAssetFilter();
        }

        private class ChangeSetAssetFilter implements AssetFilter {
            @Override
            public Action accept(ResourceChange.Type type, String pkgName, String assetName) {
                if (globalChangeType != null) {
                    return toFilterAction( globalChangeType );
                }
                ResourceChange change = changeMap.get( assetId(type, assetName) );
                return change != null ? toFilterAction( change.getChangeType() ) : Action.DO_NOTHING;
            }

            private Action toFilterAction( ChangeType changeType ) {
                switch (changeType) {
                    case ADDED: return Action.ADD;
                    case REMOVED: return Action.REMOVE;
                    case UPDATED: return Action.UPDATE;
                }
                return Action.DO_NOTHING;
            }
        }

        private String assetId(ResourceChange.Type type, String assetName) {
            return type + "_" + assetName;
        }
    }

    @FunctionalInterface
    private interface ResourceBuilder {
        void build(KnowledgeBuilderImpl kBuilder, ResourceDescr resourceDescr) throws Exception;

        ResourceBuilder DSL_RESOURCE_BUILDER = ( kBuilder, resourceDescr ) -> kBuilder.addDsl( resourceDescr.resource );

        ResourceBuilder XSD_RESOURCE_BUILDER = ( kBuilder, resourceDescr ) -> kBuilder.addPackageFromXSD( resourceDescr.resource, resourceDescr.configuration );

        ResourceBuilder BPMN2_RESOURCE_BUILDER = ( kBuilder, resourceDescr ) -> kBuilder.addKnowledgeResource( resourceDescr.resource, ResourceType.BPMN2, resourceDescr.configuration );

        ResourceBuilder CMMN_RESOURCE_BUILDER = ( kBuilder, resourceDescr ) -> kBuilder.addKnowledgeResource( resourceDescr.resource, ResourceType.CMMN, resourceDescr.configuration );
    }

}
