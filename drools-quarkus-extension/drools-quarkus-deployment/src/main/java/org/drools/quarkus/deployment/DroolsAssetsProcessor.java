package org.drools.quarkus.deployment;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;

import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ArchiveRootBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.index.IndexingUtil;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.maven.dependency.ResolvedDependency;
import io.quarkus.vertx.http.deployment.spi.AdditionalStaticResourceBuildItem;
import org.drools.model.project.codegen.GeneratedFile;
import org.drools.model.project.codegen.context.DroolsModelBuildContext;
import org.drools.model.project.codegen.io.CollectedResource;
import org.drools.model.project.codegen.io.CollectedResourceProducer;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Indexer;

import static org.drools.model.project.codegen.RuleCodegen.ofCollectedResources;
import static org.drools.quarkus.deployment.DroolsQuarkusResourceUtils.compileGeneratedSources;
import static org.drools.quarkus.deployment.DroolsQuarkusResourceUtils.createBuildContext;
import static org.drools.quarkus.deployment.DroolsQuarkusResourceUtils.dumpFilesToDisk;
import static org.drools.quarkus.deployment.DroolsQuarkusResourceUtils.registerResources;

public class DroolsAssetsProcessor {

    @Inject
    ArchiveRootBuildItem root;
    @Inject
    LiveReloadBuildItem liveReload;
    @Inject
    CurateOutcomeBuildItem curateOutcomeBuildItem;
    @Inject
    CombinedIndexBuildItem combinedIndexBuildItem;
    @Inject
    OutputTargetBuildItem outputTargetBuildItem;

    @BuildStep
    public DroolsBuildContextBuildItem generateKogitoBuildContext() {
        // configure the application generator
        DroolsModelBuildContext context =
                createBuildContext(outputTargetBuildItem.getOutputDirectory(),
                        root.getPaths(),
                        combinedIndexBuildItem.getIndex(),
                        curateOutcomeBuildItem.getApplicationModel().getAppArtifact());
        return new DroolsBuildContextBuildItem(context);
    }

    @BuildStep
    public DroolsGeneratedSourcesBuildItem generateSources( Capabilities capabilities, DroolsBuildContextBuildItem contextBuildItem ) {

        final DroolsModelBuildContext context = contextBuildItem.getDroolsModelBuildContext();

        Collection<CollectedResource> collectedResources = CollectedResourceProducer.fromPaths(context.getAppPaths().getPaths());

        Collection<GeneratedFile> generatedFiles = ofCollectedResources(context, collectedResources).generate();

        // The HotReloadSupportClass has to be generated only during the first model generation
        // During actual hot reloads it will be regenerated by the compilation providers in order to retrigger this build step
        // TODO
//        if (!liveReload.isLiveReload()) {
//            generatedFiles.add(new GeneratedFile(GeneratedFileType.SOURCE, HOT_RELOAD_SUPPORT_PATH + ".java", getHotReloadSupportSource()));
//        }

        return new DroolsGeneratedSourcesBuildItem(generatedFiles);
    }

    @BuildStep
    public List<DroolsGeneratedClassesBuildItem> generateModel(
            DroolsGeneratedSourcesBuildItem sources,
            DroolsBuildContextBuildItem contextBuildItem,
            BuildProducer<GeneratedBeanBuildItem> generatedBeans,
            BuildProducer<NativeImageResourceBuildItem> resource,
            BuildProducer<AdditionalStaticResourceBuildItem> staticResProducer,
            BuildProducer<GeneratedResourceBuildItem> genResBI) throws IOException {

        final DroolsModelBuildContext context = contextBuildItem.getDroolsModelBuildContext();

        Collection<GeneratedFile> generatedFiles = sources.getGeneratedFiles();

        // dump files to disk
        dumpFilesToDisk(context.getAppPaths(), generatedFiles);

        // build Java source code and register the generated beans
        Optional<DroolsGeneratedClassesBuildItem> optionalIndex = compileAndIndexJavaSources(
                context,
                generatedFiles,
                generatedBeans,
                liveReload.isLiveReload());

        registerResources(generatedFiles, staticResProducer, resource, genResBI);

        return optionalIndex
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }

    private Optional<DroolsGeneratedClassesBuildItem> compileAndIndexJavaSources(
            DroolsModelBuildContext context,
            Collection<GeneratedFile> generatedFiles,
            BuildProducer<GeneratedBeanBuildItem> generatedBeans,
            boolean useDebugSymbols) throws IOException {

        Collection<ResolvedDependency> dependencies = curateOutcomeBuildItem.getApplicationModel().getRuntimeDependencies();

        Collection<GeneratedBeanBuildItem> generatedBeanBuildItems =
                compileGeneratedSources(context, dependencies, generatedFiles, useDebugSymbols);
        generatedBeanBuildItems.forEach(generatedBeans::produce);
        return Optional.of(indexBuildItems(context, generatedBeanBuildItems));
    }

    private DroolsGeneratedClassesBuildItem indexBuildItems(DroolsModelBuildContext context, Collection<GeneratedBeanBuildItem> buildItems) {
        Indexer kogitoIndexer = new Indexer();
        Set<DotName> kogitoIndex = new HashSet<>();

        for (GeneratedBeanBuildItem generatedBeanBuildItem : buildItems) {
            IndexingUtil.indexClass(
                    generatedBeanBuildItem.getName(),
                    kogitoIndexer,
                    combinedIndexBuildItem.getIndex(),
                    kogitoIndex,
                    context.getClassLoader(),
                    generatedBeanBuildItem.getData());
        }

        Map<String, byte[]> generatedClasses = buildItems.stream().collect(Collectors.toMap(GeneratedBeanBuildItem::getName, GeneratedBeanBuildItem::getData));

        return new DroolsGeneratedClassesBuildItem(kogitoIndexer.complete(), generatedClasses);
    }
}
