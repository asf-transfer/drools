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
package org.drools.modelcompiler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.drools.base.definitions.InternalKnowledgePackage;
import org.drools.base.factmodel.GeneratedFact;
import org.drools.base.util.Drools;
import org.drools.compiler.builder.DroolsAssemblerContext;
import org.drools.compiler.builder.impl.KnowledgeBuilderConfigurationImpl;
import org.drools.compiler.builder.impl.KnowledgeBuilderImpl;
import org.drools.compiler.compiler.io.memory.MemoryFileSystem;
import org.drools.compiler.kie.builder.impl.BuildContext;
import org.drools.compiler.kie.builder.impl.FileKieModule;
import org.drools.compiler.kie.builder.impl.InternalKieModule;
import org.drools.compiler.kie.builder.impl.KieBaseUpdater;
import org.drools.compiler.kie.builder.impl.KieBaseUpdaterImplContext;
import org.drools.compiler.kie.builder.impl.KieBaseUpdaterOptions;
import org.drools.compiler.kie.builder.impl.KieBaseUpdaters;
import org.drools.compiler.kie.builder.impl.KieBaseUpdatersContext;
import org.drools.compiler.kie.builder.impl.KieContainerImpl;
import org.drools.compiler.kie.builder.impl.KieProject;
import org.drools.compiler.kie.builder.impl.MemoryKieModule;
import org.drools.compiler.kie.builder.impl.ResultsImpl;
import org.drools.compiler.kie.builder.impl.ZipKieModule;
import org.drools.compiler.kie.util.KieJarChangeSet;
import org.drools.compiler.kproject.models.KieBaseModelImpl;
import org.drools.compiler.kproject.models.KieModuleModelImpl;
import org.drools.core.RuleBaseConfiguration;
import org.drools.core.reteoo.CoreComponentFactory;
import org.drools.io.InternalResource;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.model.Model;
import org.drools.model.NamedModelItem;
import org.drools.util.IoUtils;
import org.drools.util.PortablePath;
import org.drools.util.StringUtils;
import org.drools.wiring.api.ResourceProvider;
import org.drools.wiring.api.classloader.ProjectClassLoader;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.KieServices;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.kie.api.builder.Results;
import org.kie.api.builder.model.KieBaseModel;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.definition.KiePackage;
import org.kie.api.definition.process.Process;
import org.kie.api.internal.utils.KieService;
import org.kie.api.io.Resource;
import org.kie.api.io.ResourceConfiguration;
import org.kie.api.io.ResourceType;
import org.kie.internal.builder.ChangeType;
import org.kie.internal.builder.CompositeKnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderConfiguration;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.builder.ResourceChange;
import org.kie.internal.builder.ResourceChangeSet;
import org.kie.internal.builder.conf.AlphaNetworkCompilerOption;
import org.kie.util.maven.support.DependencyFilter;
import org.kie.util.maven.support.PomModel;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.drools.compiler.kie.builder.impl.AbstractKieModule.checkStreamMode;
import static org.drools.model.impl.ModelComponent.areEqualInModel;
import static org.drools.modelcompiler.util.StringUtil.fileNameToClass;
import static org.kie.api.io.ResourceType.determineResourceType;

public class CanonicalKieModule implements InternalKieModule {

    public static final String PROJECT_MODEL_CLASS = "org.drools.project.model.ProjectModel";
    public static final String MODEL_FILE_DIRECTORY = "META-INF/kie/";
    public static final String MODEL_FILE_NAME = "drools-model";
    public static final String SERVICES_DIRECTORY = "META-INF/services/";
    public static final String RULE_UNIT_SERVICES_FILE = SERVICES_DIRECTORY + "org.drools.ruleunits.api.RuleUnit";
    public static final String ANC_FILE_NAME = "alpha-network-compiler";
    public static final String GENERATED_CLASS_NAMES = "generated-class-names";
    public static final String MODEL_VERSION = "Drools-Model-Version:";

    private static final Predicate<String> NON_MODEL_RESOURCES = res -> {
            ResourceType type = determineResourceType(res);
            return type != null && !type.isFullyCoveredByExecModel();
    };

    private final InternalKieModule internalKieModule;
    private final ConcurrentMap<String, CanonicalKiePackages> pkgsInKbase = new ConcurrentHashMap<>();
    private final Map<String, Model> models = new HashMap<>();
    private Collection<String> ruleClassesNames;
    private boolean incrementalUpdate = false;
    private Set<String> generatedClassNames;

    private ProjectClassLoader moduleClassLoader;

    public CanonicalKieModule(ReleaseId releaseId, KieModuleModel kieProject, File file) {
        this(releaseId, kieProject, file, null);
    }

    public CanonicalKieModule(ReleaseId releaseId, KieModuleModel kieProject, File file, Collection<String> ruleClassesNames) {
        this(file.isDirectory() ? new FileKieModule(releaseId, kieProject, file) : new ZipKieModule(releaseId, kieProject, file), ruleClassesNames);
    }

    public CanonicalKieModule(InternalKieModule internalKieModule) {
        this(internalKieModule, null);
    }

    public CanonicalKieModule(InternalKieModule internalKieModule, Collection<String> ruleClassesNames) {
        this.internalKieModule = internalKieModule;
        this.ruleClassesNames = ruleClassesNames;
    }

    private static boolean areModelVersionsCompatible(String runtimeVersion, String compileVersion) {
        return true;
    }

    private CanonicalKieModuleModel getModuleModel() throws ClassNotFoundException {
        return createInstance( getModuleClassLoader(), getProjectModelClassName() );
    }

    private String getProjectModelClassName() {
        return getModuleClassLoader().isDynamic() ? getProjectModelClassNameNameWithReleaseId(internalKieModule.getReleaseId()) : PROJECT_MODEL_CLASS;
    }

    public static String getProjectModelClassNameNameWithReleaseId(ReleaseId releaseId) {
        return CanonicalKieModule.PROJECT_MODEL_CLASS + releaseId2JavaName(releaseId);
    }

    public static String releaseId2JavaName(ReleaseId releaseId) {
        return "_" + (releaseId.getGroupId() + "_" + releaseId.getArtifactId() + "_" + releaseId.getVersion()).replaceAll( "\\W", "_" );
    }

    private String getProjectModelResourceName() {
        return getProjectModelClassName().replace('.', '/') + ".class";
    }

    private static <T> T createInstance(ClassLoader cl, String className) throws ClassNotFoundException {
        try {
            return (T) cl.loadClass(className).getConstructor().newInstance();
        } catch (InstantiationException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private static KieBaseConfiguration getKieBaseConfiguration(KieBaseModelImpl kBaseModel, ClassLoader cl, KieBaseConfiguration conf) {
        if (conf == null) {
            conf = getKnowledgeBaseConfiguration(kBaseModel, cl);
        } else if (conf instanceof RuleBaseConfiguration) {
            ((RuleBaseConfiguration) conf).setClassLoader(cl);
        }
        return conf;
    }

    private static KieBaseConfiguration getKnowledgeBaseConfiguration(KieBaseModelImpl kBaseModel, ClassLoader cl) {
        KieBaseConfiguration kbConf = KieServices.get().newKieBaseConfiguration(null, cl);
        if (kBaseModel != null) {
            kbConf.setOption(kBaseModel.getEqualsBehavior());
            kbConf.setOption(kBaseModel.getEventProcessingMode());
            kbConf.setOption(kBaseModel.getDeclarativeAgenda());
            kbConf.setOption(kBaseModel.getSequential());
            kbConf.setOption(kBaseModel.getMutability());
        }
        return kbConf;
    }

    public static String getModelFileWithGAV(ReleaseId releaseId) {
        return MODEL_FILE_DIRECTORY + releaseId.getGroupId() + "/" + releaseId.getArtifactId() + "/" + MODEL_FILE_NAME;
    }

    public static String getANCFile(ReleaseId releaseId) {
        return MODEL_FILE_DIRECTORY + releaseId.getGroupId() + "/" + releaseId.getArtifactId() + "/" + ANC_FILE_NAME;
    }

    public static String getGeneratedClassNamesFile(ReleaseId releaseId) {
        return MODEL_FILE_DIRECTORY + releaseId.getGroupId() + "/" + releaseId.getArtifactId() + "/" + GENERATED_CLASS_NAMES;
    }

    @Override
    public Map<String, byte[]> getClassesMap() {
        return internalKieModule.getClassesMap();
    }

    @Override
    public void addGeneratedClassNames(Set<String> classNames) {
        generatedClassNames.addAll(classNames);
    }

    public Set<String> getGeneratedClassNames() {
        return generatedClassNames;
    }

    @Override
    public ResultsImpl build() {
        // TODO should this initialize the CanonicalKieModule in some way? (doesn't seem necessary so far)
        return new ResultsImpl();
    }

    @Override
    public InternalKnowledgeBase createKieBase(KieBaseModelImpl kBaseModel, KieProject kieProject, BuildContext buildContext, KieBaseConfiguration conf) {
        this.moduleClassLoader = ((ProjectClassLoader) kieProject.getClassLoader());
        if (generatedClassNames == null) {
            generatedClassNames = findGeneratedClassNamesWithDependencies();
        }
        moduleClassLoader.setGeneratedClassNames(generatedClassNames);
        KieBaseConfiguration kBaseConf = getKieBaseConfiguration(kBaseModel, moduleClassLoader, conf);

        CanonicalKiePackages kpkgs = pkgsInKbase.computeIfAbsent(kBaseModel.getName(), k -> createKiePackages(kieProject, kBaseModel, buildContext, kBaseConf));
        checkStreamMode(kBaseModel, conf, kpkgs.getKiePackages());
        InternalKnowledgeBase kieBase = new KieBaseBuilder(kBaseModel, kBaseConf).createKieBase(kpkgs);

        registerNonNativeResources( kBaseModel, kieProject, kieBase, buildContext );
        return kieBase;
    }

    @Override
    public void afterKieBaseCreationUpdate(String name, InternalKnowledgeBase kBase) {
        KnowledgeBuilder knowledgeBuilderForKieBase = getKnowledgeBuilderForKieBase(name);


        final List<KieBaseUpdaterOptions.OptionEntry> options;
        if(knowledgeBuilderForKieBase instanceof KnowledgeBuilderImpl) {// When using executable module in tests
            KnowledgeBuilderImpl knowledgeBuilderForImpl = (KnowledgeBuilderImpl) knowledgeBuilderForKieBase;
            KnowledgeBuilderConfigurationImpl builderConfiguration = knowledgeBuilderForImpl.getBuilderConfiguration();
            options = singletonList(
                    new KieBaseUpdaterOptions.OptionEntry(
                            AlphaNetworkCompilerOption.class,
                            builderConfiguration.getOption(AlphaNetworkCompilerOption.KEY)));
        } else if(resourceFileExists(getANCFile(internalKieModule.getReleaseId()))) { // executable model with ANC
            options = singletonList(
                    new KieBaseUpdaterOptions.OptionEntry(
                            AlphaNetworkCompilerOption.class,
                            AlphaNetworkCompilerOption.LOAD));
        } else { // Default case when loaded from executable model kjar
            options = emptyList();
        }

        KieContainerImpl.CompositeRunnable compositeUpdater = new KieContainerImpl.CompositeRunnable();
        KieBaseUpdaters updaters = KieService.load(KieBaseUpdaters.class);
        updaters.getChildren()
                .stream()
                .map(kbu -> kbu.create(new KieBaseUpdatersContext(new KieBaseUpdaterOptions(options),
                                                              kBase.getRete(),
                                                              kBase.getRootClassLoader()
                )))
                .forEach(compositeUpdater::add);

        compositeUpdater.run();
    }

    private void registerNonNativeResources(KieBaseModelImpl kBaseModel, KieProject kieProject, InternalKnowledgeBase kieBase, BuildContext buildContext) {
        KnowledgeBuilder kbuilder = getKnowledgeBuilderForKieBase(kBaseModel.getName());
        if (kbuilder == null) {
            kbuilder = kieProject.buildKnowledgePackages(kBaseModel, buildContext, NON_MODEL_RESOURCES);
        }
        if ( !kbuilder.hasErrors() ) {
            for (KiePackage pk : kbuilder.getKnowledgePackages()) {
                // Workaround to "mark" already compiled packages (as found inside the kjar and retrieved by createKiePackages(kieProject, kBaseModel, messages, kBaseConf))
                // as "PMML" packages
                boolean isInternalKnowldgePackage = pk instanceof InternalKnowledgePackage;
                final InternalKnowledgePackage originalPackage = kieBase.getPackage( pk.getName() );
                if ( originalPackage != null && isInternalKnowldgePackage && (( InternalKnowledgePackage ) pk).getResourceTypePackages().get( ResourceType.PMML ) != null ) {
                    originalPackage.getResourceTypePackages().put( ResourceType.PMML, (( InternalKnowledgePackage ) pk).getResourceTypePackages().get( ResourceType.PMML ) );
                } else if ( originalPackage == null ) {
                    kieBase.addPackage( pk );
                }
            }
        }
    }

    private CanonicalKiePackages createKiePackages(KieProject kieProject, KieBaseModelImpl kBaseModel, BuildContext buildContext, KieBaseConfiguration conf) {
        Set<String> includes = kieProject == null ? Collections.emptySet() : kieProject.getTransitiveIncludes(kBaseModel);
        List<Process> processes = findProcesses(internalKieModule, kBaseModel);
        Collection<Model> modelsForKBase;

        if (includes.isEmpty()) {
            modelsForKBase = getModelForKBase(kBaseModel);
        } else {
            modelsForKBase = new ArrayList<>(getModelForKBase(kBaseModel));

            for (String include : includes) {
                if (StringUtils.isEmpty(include)) {
                    continue;
                }
                InternalKieModule includeModule = kieProject.getKieModuleForKBase(include);
                if (includeModule == null) {
                    String text = "Unable to build KieBase, could not find include: " + include;
                    buildContext.getMessages().addMessage(Message.Level.ERROR, KieModuleModelImpl.KMODULE_SRC_PATH.asString(), text).setKieBaseName(kBaseModel.getName());
                    continue;
                }
                if (!(includeModule instanceof CanonicalKieModule)) {
                    String text = "It is not possible to mix drl based and executable model based projects. Found a drl project: " + include;
                    buildContext.getMessages().addMessage(Message.Level.ERROR, KieModuleModelImpl.KMODULE_SRC_PATH.asString(), text).setKieBaseName(kBaseModel.getName());
                    continue;
                }
                KieBaseModelImpl includeKBaseModel = (KieBaseModelImpl) kieProject.getKieBaseModel(include);
                CanonicalKieModule canonicalInclude = (CanonicalKieModule) includeModule;
                canonicalInclude.setModuleClassLoader((ProjectClassLoader) kieProject.getClassLoader());
                modelsForKBase.addAll(canonicalInclude.getModelForKBase(includeKBaseModel));
                processes.addAll(findProcesses(includeModule, includeKBaseModel));
            }
        }

        CanonicalKiePackages canonicalKiePkgs = new KiePackagesBuilder(conf, getBuilderConfiguration( kBaseModel ), modelsForKBase).build();
        CanonicalKiePackages canonicalKiePackages = mergeProcesses(processes, canonicalKiePkgs);

        modelsForKBase.clear();
        this.models.clear();

        return canonicalKiePackages;
    }

    private KnowledgeBuilderConfiguration getBuilderConfiguration( KieBaseModelImpl kBaseModel ) {
        KnowledgeBuilder builder = getKnowledgeBuilderForKieBase( kBaseModel.getName() );
        return builder != null ? (( DroolsAssemblerContext ) builder).getBuilderConfiguration() : createBuilderConfiguration(kBaseModel, moduleClassLoader);
    }

    private CanonicalKiePackages mergeProcesses(List<Process> processes, CanonicalKiePackages canonicalKiePkgs) {
        for (Process process : processes) {
            InternalKnowledgePackage canonicalKiePkg = (InternalKnowledgePackage) canonicalKiePkgs.getKiePackage(process.getPackageName());
            if (canonicalKiePkg == null) {
                canonicalKiePkg = CoreComponentFactory.get().createKnowledgePackage(process.getPackageName());
                canonicalKiePkgs.addKiePackage(canonicalKiePkg);
            }
            canonicalKiePkg.addProcess(process);
        }
        return canonicalKiePkgs;
    }

    private List<Process> findProcesses(InternalKieModule kieModule, KieBaseModelImpl kBaseModel) {
        List<Process> processes = new ArrayList<>();
        Collection<KiePackage> pkgs = kieModule.getKnowledgePackagesForKieBase(kBaseModel.getName());
        if (pkgs == null) {
            List<Resource> processResources = kieModule.getFileNames().stream()
                    .filter(fileName -> {
                        ResourceType resourceType = determineResourceType(fileName);
                        return resourceType == ResourceType.BPMN2;
                    })
                    .map(fileName -> {
                        final Resource processResource = kieModule.getResource(fileName);
                        processResource.setResourceType(determineResourceType(fileName));
                        return processResource;
                    })
                    .collect(toList());
            if (!processResources.isEmpty()) {
                KnowledgeBuilderImpl kbuilder = (KnowledgeBuilderImpl) KnowledgeBuilderFactory.newKnowledgeBuilder( createBuilderConfiguration(kBaseModel, moduleClassLoader));
                for (Resource processResource : processResources) {
                    kbuilder.add(processResource, processResource.getResourceType());
                }
                for (InternalKnowledgePackage knowledgePackage : kbuilder.getPackages()) {
                    processes.addAll(knowledgePackage.getProcesses());
                }
            }
        } else {
            for (KiePackage pkg : pkgs) {
                processes.addAll(pkg.getProcesses());
            }
        }
        return processes;
    }

    public CanonicalKiePackages getKiePackages(KieBaseModelImpl kBaseModel) {
        return pkgsInKbase.computeIfAbsent(kBaseModel.getName(), k -> createKiePackages(null, kBaseModel, null, getKnowledgeBaseConfiguration(kBaseModel, getModuleClassLoader())));
    }

    public ProjectClassLoader getModuleClassLoader() {
        if (moduleClassLoader == null) {
            moduleClassLoader = createModuleClassLoader(null);
            moduleClassLoader.storeClasses(getClassesMap());
        }
        return moduleClassLoader;
    }

    public void setModuleClassLoader(ProjectClassLoader moduleClassLoader) {
        pkgsInKbase.clear();
        models.clear();
        this.moduleClassLoader = moduleClassLoader;
    }

    public void setIncrementalUpdate(boolean incrementalUpdate) {
        this.incrementalUpdate = incrementalUpdate;
    }

    private Map<String, Model> getModels() {
        if (models.isEmpty()) {

            // During incremental update, to keep compatible classes generated from declared types, the new kmodule
            // is loaded with the classloader of the old one. This implies that the models cannot be retrieved from the
            // project model class but loaded one by one from the classloader itself.

            if (!incrementalUpdate) {
                try {
                    initModels(getModuleModel());
                    return models;
                } catch (ClassNotFoundException cnfe) { }
            }
            try {
                initModelsFromProjectDescriptor();
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        return models;
    }

    private void initModelsFromProjectDescriptor() throws ClassNotFoundException {
        for (String rulesFile : getRuleClassNames()) {
            Model model = createInstance(getModuleClassLoader(), rulesFile);
            models.put(model.getName(), model);
        }
    }

    private void initModels(CanonicalKieModuleModel kmodel) throws ClassNotFoundException {
        if (kmodel != null) {
            ruleClassesNames = new ArrayList<>();
            for (Model model : kmodel.getModels()) {
                models.put(model.getName(), model);
                ruleClassesNames.add(model.getClass().getCanonicalName());
            }
        } else {
            initModelsFromProjectDescriptor();
        }
    }

    private Collection<String> getRuleClassNames() {
        if (ruleClassesNames == null) {
            ruleClassesNames = findRuleClassesNames();
        }
        return ruleClassesNames;
    }

    public Collection<Model> getModelForKBase(KieBaseModelImpl kBaseModel) {
        Map<String, Model> modelsMap = getModels();
        if (kBaseModel.getPackages().isEmpty()) {
            return modelsMap.values();
        }
        Collection<Model> models = new ArrayList<>();
        for (String pkg : kBaseModel.getPackages()) {
            if (pkg.equals("*")) {
                return modelsMap.values();
            }
            Model model = modelsMap.get(pkg);
            if (model != null) {
                models.add(model);
            }
        }
        return models;
    }

    // This method indicates if the kjar was already compiled with the executable model
    public boolean hasModelFile() {
        return resourceFileExists(getModelFileWithGAV(internalKieModule.getReleaseId()));
    }

    private Collection<String> findRuleClassesNames() {
        ReleaseId releaseId = internalKieModule.getReleaseId();
        String modelFiles = readExistingResourceWithName(getModelFileWithGAV(releaseId));

        String[] lines = modelFiles.split("\n");
        String header = lines[0];
        if (!header.startsWith(MODEL_VERSION)) {
            throw new RuntimeException("Malformed drools-model file");
        }
        String version = header.substring(MODEL_VERSION.length());
        if (!areModelVersionsCompatible( Drools.getFullVersion(), version)) {
            throw new RuntimeException("Kjar compiled with version " + version + " is not compatible with current runtime version " + Drools.getFullVersion());
        }

        return Stream.of(lines).skip(1).collect(toList());
    }

    private Set<String> findGeneratedClassNamesWithDependencies() {
        Set<String> generatedClassNames = new HashSet<>(findGeneratedClassNames(internalKieModule));

        Map<ReleaseId, InternalKieModule> kieDependencies = internalKieModule.getKieDependencies();
        kieDependencies.values().forEach(depKieModule -> {
            generatedClassNames.addAll(findGeneratedClassNames(depKieModule));
        });
        return generatedClassNames;
    }

    private Set<String> findGeneratedClassNames(InternalKieModule kieModule) {
        String generatedClassNamesFile = getGeneratedClassNamesFile(kieModule.getReleaseId());
        if (!kieModule.hasResource(generatedClassNamesFile)) {
            return new HashSet<>();
        }
        String content = readExistingResourceWithName(kieModule, generatedClassNamesFile);
        if (content.trim().isEmpty()) {
            return new HashSet<>();
        }
        String[] lines = content.split("\n");
        return Stream.of(lines).collect(Collectors.toSet());
    }

    private String readExistingResourceWithName(String fileName) {
        return readExistingResourceWithName(internalKieModule, fileName);
    }

    private String readExistingResourceWithName(InternalKieModule kieModule, String fileName) {
        String modelFiles;
        try {
            Resource modelFile = kieModule.getResource(fileName);
            modelFiles = new String(IoUtils.readBytesFromInputStream(modelFile.getInputStream()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return modelFiles;
    }

    private boolean resourceFileExists(String fileName) {
        Resource modelFile = internalKieModule.getResource(fileName);
        return modelFile != null;
    }

    @Override
    public KieJarChangeSet getChanges(InternalKieModule newKieModule) {
        KieJarChangeSet result = findChanges(newKieModule);

        Map<String, Model> oldModels = getModels();
        Map<String, Model> newModels = ((CanonicalKieModule) newKieModule).getModels();

        for (Map.Entry<String, Model> entry : oldModels.entrySet()) {
            Model newModel = newModels.get(entry.getKey());
            if (newModel == null) {
                result.registerChanges(entry.getKey(), buildAllItemsChangeSet(entry.getValue(), ChangeType.REMOVED));
                continue;
            }

            Model oldModel = entry.getValue();
            for (ResourceChangeSet changeSet : calculateResourceChangeSet(oldModel, newModel)) {
                if (!changeSet.getChanges().isEmpty()) {
                    result.registerChanges(entry.getKey(), changeSet);
                }
            }
        }

        for (Map.Entry<String, Model> entry : newModels.entrySet()) {
            if (oldModels.get(entry.getKey()) == null) {
                result.registerChanges(entry.getKey(), buildAllItemsChangeSet(entry.getValue(), ChangeType.ADDED));
            }
        }

        KieJarChangeSet internalChanges = internalKieModule.getChanges(((CanonicalKieModule) newKieModule).internalKieModule);
        internalChanges.removeFile(getProjectModelResourceName());
        return result.merge(internalChanges);
    }

    private ResourceChangeSet buildAllItemsChangeSet(Model oldModel, ChangeType changeType) {
        ResourceChangeSet changeSet = new ResourceChangeSet(oldModel.getName(), ChangeType.UPDATED);
        for (NamedModelItem item : oldModel.getRules()) {
            changeSet.getChanges().add(new ResourceChange(changeType, ResourceChange.Type.RULE, item.getName()));
        }
        for (NamedModelItem item : oldModel.getQueries()) {
            changeSet.getChanges().add(new ResourceChange(changeType, ResourceChange.Type.RULE, item.getName()));
        }
        for (NamedModelItem item : oldModel.getGlobals()) {
            changeSet.getChanges().add(new ResourceChange(changeType, ResourceChange.Type.GLOBAL, item.getName()));
        }
        for (NamedModelItem item : oldModel.getTypeMetaDatas()) {
            changeSet.getChanges().add(new ResourceChange(changeType, ResourceChange.Type.DECLARATION, item.getName()));
        }
        return changeSet;
    }

    private KieJarChangeSet findChanges(InternalKieModule newKieModule) {
        KieJarChangeSet result = new KieJarChangeSet();
        Collection<String> oldFiles = getFileNames();
        Collection<String> newFiles = newKieModule.getFileNames();

        ArrayList<String> removedFiles = new ArrayList<>(oldFiles);
        removedFiles.removeAll(newFiles);
        if (!removedFiles.isEmpty()) {
            for (String file : removedFiles) {
                if (isChange(file, this)) {
                    result.removeFile(file);
                }
            }
        }

        for (String file : newFiles) {
            if (oldFiles.contains(file) && isChange(file, this)) {
                // check for modification
                if (isClassChanged( newKieModule, file )) {
                    // parse the file to figure out the difference
                    result.registerChanges(file, new ResourceChangeSet(file, ChangeType.UPDATED));
                }
            } else if (isChange(file, ((CanonicalKieModule) newKieModule))) {
                // file was added
                result.addFile(file);
            }
        }
        return result;
    }

    private boolean isChange(String fileName, CanonicalKieModule module) {
        return fileName.endsWith(".class") &&
                !fileName.equals(getProjectModelResourceName()) &&
                module.getRuleClassNames().stream().noneMatch(fileNameToClass(fileName)::startsWith);
    }

    private static final String GENERATED_FACT_MARKER = GeneratedFact.class.getCanonicalName().replace( '.', '/' );
    private boolean isClassChanged( InternalKieModule newKieModule, String file ) {
        byte[] oldBytes = getBytes( file );
        return new String(oldBytes).contains(GENERATED_FACT_MARKER) || !Arrays.equals(oldBytes, newKieModule.getBytes( file ));
    }

    private Collection<ResourceChangeSet> calculateResourceChangeSet(Model oldModel, Model newModel) {
        ResourceChangeSet changeSet = new ResourceChangeSet(oldModel.getName(), ChangeType.UPDATED);
        changeSet.setPackageName(oldModel.getName());
        Map<String, ResourceChangeSet> changes = new HashMap<>();
        changes.put(oldModel.getName(), changeSet);

        addModifiedItemsToChangeSet(changeSet, ResourceChange.Type.RULE, oldModel.getRules(), newModel.getRules());
        addModifiedItemsToChangeSet(changeSet, ResourceChange.Type.RULE, oldModel.getQueries(), newModel.getQueries());
        addModifiedItemsToChangeSet(changeSet, ResourceChange.Type.GLOBAL, oldModel.getGlobals(), newModel.getGlobals());
        addModifiedItemsToChangeSet(changeSet, changes, ResourceChange.Type.DECLARATION, oldModel.getTypeMetaDatas(), newModel.getTypeMetaDatas());

        return changes.values();
    }

    private void addModifiedItemsToChangeSet(ResourceChangeSet changeSet, ResourceChange.Type type, List<? extends NamedModelItem> oldItems, List<? extends NamedModelItem> newItems) {
        addModifiedItemsToChangeSet(changeSet, null, type, oldItems, newItems);
    }

    private void addModifiedItemsToChangeSet(ResourceChangeSet mainChangeSet, Map<String, ResourceChangeSet> changes, ResourceChange.Type type, List<? extends NamedModelItem> oldItems, List<? extends NamedModelItem> newItems) {
        if (oldItems.isEmpty()) {
            if (!newItems.isEmpty()) {
                for (NamedModelItem newItem : newItems) {
                    registerChange(mainChangeSet, changes, type, ChangeType.ADDED, newItem);
                }
            }
            return;
        } else if (newItems.isEmpty()) {
            for (NamedModelItem oldItem : oldItems) {
                registerChange(mainChangeSet, changes, type, ChangeType.REMOVED, oldItem);
            }
            return;
        }

        oldItems.sort(Comparator.comparing(NamedModelItem::getName));
        newItems.sort(Comparator.comparing(NamedModelItem::getName));

        Iterator<? extends NamedModelItem> oldRulesIterator = oldItems.iterator();
        Iterator<? extends NamedModelItem> newRulesIterator = newItems.iterator();

        NamedModelItem currentOld = oldRulesIterator.next();
        NamedModelItem currentNew = newRulesIterator.next();

        while (true) {
            int compare = currentOld.getName().compareTo(currentNew.getName());
            if (compare == 0) {
                if (!areEqualInModel(currentOld, currentNew)) {
                    registerChange(mainChangeSet, changes, type, ChangeType.UPDATED, currentOld);
                }
                if (oldRulesIterator.hasNext()) {
                    currentOld = oldRulesIterator.next();
                } else {
                    break;
                }
                if (newRulesIterator.hasNext()) {
                    currentNew = newRulesIterator.next();
                } else {
                    registerChange(mainChangeSet, changes, type, ChangeType.REMOVED, currentOld);
                    break;
                }
            } else if (compare < 0) {
                registerChange(mainChangeSet, changes, type, ChangeType.REMOVED, currentOld);
                if (oldRulesIterator.hasNext()) {
                    currentOld = oldRulesIterator.next();
                } else {
                    registerChange(mainChangeSet, changes, type, ChangeType.ADDED, currentNew);
                    break;
                }
            } else {
                registerChange(mainChangeSet, changes, type, ChangeType.ADDED, currentNew);
                if (newRulesIterator.hasNext()) {
                    currentNew = newRulesIterator.next();
                } else {
                    registerChange(mainChangeSet, changes, type, ChangeType.REMOVED, currentOld);
                    break;
                }
            }
        }

        while (oldRulesIterator.hasNext()) {
            registerChange(mainChangeSet, changes, type, ChangeType.REMOVED, oldRulesIterator.next());
        }

        while (newRulesIterator.hasNext()) {
            registerChange(mainChangeSet, changes, type, ChangeType.ADDED, newRulesIterator.next());
        }
    }

    private void registerChange(ResourceChangeSet mainChangeSet, Map<String, ResourceChangeSet> changes, ResourceChange.Type resourceChangeType, ChangeType changeType, NamedModelItem item) {
        getChangeSetForItem(mainChangeSet, changes, item).getChanges().add(new ResourceChange(changeType, resourceChangeType, item.getName()));
    }

    private ResourceChangeSet getChangeSetForItem(ResourceChangeSet mainChangeSet, Map<String, ResourceChangeSet> changes, NamedModelItem item) {
        return changes != null ? changes.computeIfAbsent(item.getPackage(), pkg -> new ResourceChangeSet(pkg, ChangeType.UPDATED)) : mainChangeSet;
    }

    @Override
    public KieBaseUpdater createKieBaseUpdater(KieBaseUpdaterImplContext context) {
        return new CanonicalKieBaseUpdater(context);
    }

    @Override
    public void updateKieModule(InternalKieModule newKM) {
        CanonicalKieModule newCanonicalKieModule = (CanonicalKieModule) newKM;
        newCanonicalKieModule.setModuleClassLoader(this.getModuleClassLoader());
    }

    public InternalKieModule getInternalKieModule() {
        return internalKieModule;
    }

    // Delegate methods

    @Override
    public CanonicalKieModule cloneForIncrementalCompilation(ReleaseId releaseId, KieModuleModel kModuleModel, MemoryFileSystem newFs) {
        MemoryKieModule clonedInternal = ((MemoryKieModule) internalKieModule).cloneForIncrementalCompilation(releaseId, kModuleModel, newFs);
        return new CanonicalKieModule(clonedInternal);
    }

    @Override
    public void cacheKnowledgeBuilderForKieBase(String kieBaseName, KnowledgeBuilder kbuilder) {
        internalKieModule.cacheKnowledgeBuilderForKieBase(kieBaseName, kbuilder);
    }

    @Override
    public KnowledgeBuilder getKnowledgeBuilderForKieBase(String kieBaseName) {
        return internalKieModule.getKnowledgeBuilderForKieBase(kieBaseName);
    }

    @Override
    public InternalKnowledgePackage getPackage(String packageName) {
        return internalKieModule.getPackage(packageName);
    }

    @Override
    public Collection<KiePackage> getKnowledgePackagesForKieBase(String kieBaseName) {
        return internalKieModule.getKnowledgePackagesForKieBase(kieBaseName);
    }

    @Override
    public void cacheResultsForKieBase(String kieBaseName, Results results) {
        internalKieModule.cacheResultsForKieBase(kieBaseName, results);
    }

    @Override
    public Map<String, Results> getKnowledgeResultsCache() {
        return internalKieModule.getKnowledgeResultsCache();
    }

    @Override
    public KieModuleModel getKieModuleModel() {
        return internalKieModule.getKieModuleModel();
    }

    @Override
    public byte[] getBytes() {
        return internalKieModule.getBytes();
    }

    @Override
    public boolean hasResource(String fileName) {
        return internalKieModule.hasResource(fileName);
    }

    @Override
    public InternalResource getResource(String fileName) {
        return internalKieModule.getResource(fileName);
    }

    @Override
    public ResourceConfiguration getResourceConfiguration(String fileName) {
        return internalKieModule.getResourceConfiguration(fileName);
    }

    @Override
    public Map<ReleaseId, InternalKieModule> getKieDependencies() {
        return internalKieModule.getKieDependencies();
    }

    @Override
    public void addKieDependency(InternalKieModule dependency) {
        internalKieModule.addKieDependency(dependency);
    }

    @Override
    public Collection<ReleaseId> getJarDependencies(DependencyFilter filter) {
        return internalKieModule.getJarDependencies(filter);
    }

    @Override
    public Collection<ReleaseId> getUnresolvedDependencies() {
        return internalKieModule.getUnresolvedDependencies();
    }

    @Override
    public void setUnresolvedDependencies(Collection<ReleaseId> unresolvedDependencies) {
        internalKieModule.setUnresolvedDependencies(unresolvedDependencies);
    }

    @Override
    public boolean isAvailable(String pResourceName) {
        return internalKieModule.isAvailable(pResourceName);
    }

    @Override
    public byte[] getBytes(String pResourceName) {
        return internalKieModule.getBytes(pResourceName);
    }

    @Override
    public byte[] getBytes(PortablePath resourcePath) {
        return internalKieModule.getBytes(resourcePath);
    }

    @Override
    public Collection<String> getFileNames() {
        return internalKieModule.getFileNames();
    }

    @Override
    public File getFile() {
        return internalKieModule.getFile();
    }

    @Override
    public ResourceProvider createResourceProvider() {
        return internalKieModule.createResourceProvider();
    }

    @Override
    public boolean addResourceToCompiler(CompositeKnowledgeBuilder ckbuilder, KieBaseModel kieBaseModel, String fileName) {
        return internalKieModule.addResourceToCompiler(ckbuilder, kieBaseModel, fileName);
    }

    @Override
    public boolean addResourceToCompiler(CompositeKnowledgeBuilder ckbuilder, KieBaseModel kieBaseModel, String fileName, ResourceChangeSet rcs) {
        return internalKieModule.addResourceToCompiler(ckbuilder, kieBaseModel, fileName, rcs);
    }

    @Override
    public long getCreationTimestamp() {
        return internalKieModule.getCreationTimestamp();
    }

    @Override
    public InputStream getPomAsStream() {
        return internalKieModule.getPomAsStream();
    }

    @Override
    public PomModel getPomModel() {
        return internalKieModule.getPomModel();
    }

    @Override
    public KnowledgeBuilderConfiguration createBuilderConfiguration( KieBaseModel kBaseModel, ClassLoader classLoader) {
        return internalKieModule.createBuilderConfiguration(kBaseModel, classLoader);
    }

    @Override
    public ReleaseId getReleaseId() {
        return internalKieModule.getReleaseId();
    }

    @Override
    public boolean isVerifiable() {
        return false;
    }
}
