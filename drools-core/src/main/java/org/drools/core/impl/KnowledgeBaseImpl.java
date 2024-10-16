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
package org.drools.core.impl;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.drools.base.base.ClassObjectType;
import org.drools.base.common.PartitionsManager;
import org.drools.base.common.RuleBasePartitionId;
import org.drools.base.definitions.InternalKnowledgePackage;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.factmodel.ClassDefinition;
import org.drools.base.rule.DialectRuntimeRegistry;
import org.drools.base.rule.EntryPointId;
import org.drools.base.rule.Function;
import org.drools.base.rule.ImportDeclaration;
import org.drools.base.rule.InvalidPatternException;
import org.drools.base.rule.TypeDeclaration;
import org.drools.base.rule.WindowDeclaration;
import org.drools.base.ruleunit.RuleUnitDescriptionRegistry;
import org.drools.core.KieBaseConfigurationImpl;
import org.drools.core.RuleBaseConfiguration;
import org.drools.core.SessionConfiguration;
import org.drools.core.base.ClassFieldAccessorCache;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.management.DroolsManagementAgent;
import org.drools.core.phreak.BuildtimeSegmentUtilities;
import org.drools.core.phreak.EagerPhreakBuilder.Add;
import org.drools.core.phreak.PhreakBuilder;
import org.drools.core.reteoo.AsyncReceiveNode;
import org.drools.core.reteoo.CompositePartitionAwareObjectSinkAdapter;
import org.drools.core.reteoo.CoreComponentFactory;
import org.drools.core.reteoo.EntryPointNode;
import org.drools.core.reteoo.LeftTupleNode;
import org.drools.core.reteoo.LeftTupleSource;
import org.drools.core.reteoo.ObjectSinkPropagator;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.Rete;
import org.drools.core.reteoo.ReteooBuilder;
import org.drools.core.reteoo.RuntimeComponentFactory;
import org.drools.core.reteoo.SegmentMemory;
import org.drools.core.reteoo.SegmentMemory.SegmentPrototype;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.core.reteoo.builder.NodeFactory;
import org.drools.core.rule.JavaDialectRuntimeData;
import org.drools.core.rule.accessor.FactHandleFactory;
import org.drools.wiring.api.classloader.ProjectClassLoader;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.builder.ReleaseId;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.definition.KiePackage;
import org.kie.api.definition.process.Process;
import org.kie.api.definition.rule.Query;
import org.kie.api.definition.rule.Rule;
import org.kie.api.definition.type.Expires.Policy;
import org.kie.api.definition.type.FactType;
import org.kie.api.definition.type.Role;
import org.kie.api.internal.io.ResourceTypePackage;
import org.kie.api.internal.utils.KieService;
import org.kie.api.internal.weaver.KieWeavers;
import org.kie.api.io.Resource;
import org.kie.internal.conf.CompositeBaseConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.drools.core.phreak.PhreakBuilder.isEagerSegmentCreation;
import static org.drools.util.ClassUtils.convertClassToResourcePath;
import static org.drools.util.bitmask.BitMaskUtil.isSet;

public class KnowledgeBaseImpl implements InternalRuleBase {

    protected static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseImpl.class);

    private static final KieWeavers WEAVERS = KieService.load( KieWeavers.class );

    private Set<EntryPointNode> addedEntryNodeCache;
    private Set<EntryPointNode> removedEntryNodeCache;

    private String              id;

    private KieBaseConfiguration config;

    private RuleBaseConfiguration    ruleBaseConfig;
    private KieBaseConfigurationImpl kieBaseConfig;

    protected Map<String, InternalKnowledgePackage> pkgs;

    private Map<String, Process> processes;

    private transient ClassLoader rootClassLoader;

    private transient Map<String, Type> globals;

    private final transient Queue<DialectRuntimeRegistry> reloadPackageCompilationData = new ConcurrentLinkedQueue<>();

    // lock for entire rulebase, used for dynamic updates
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private final transient Map<String, TypeDeclaration> classTypeDeclaration = new ConcurrentHashMap<>();

    private ClassFieldAccessorCache classFieldAccessorCache;
    /** The root Rete-OO for this <code>RuleBase</code>. */
    private transient Rete rete;
    private ReteooBuilder reteooBuilder;
    private final transient Map<Integer, SegmentPrototype> segmentProtos = isEagerSegmentCreation() ? new HashMap<>() : new ConcurrentHashMap<>();

    // This is just a hack, so spring can find the list of generated classes
    public List<List<String>> jaxbClasses;

    private ReleaseId resolvedReleaseId;
    private String containerId;

    private final RuleUnitDescriptionRegistry ruleUnitDescriptionRegistry = new RuleUnitDescriptionRegistry();

    private SessionConfiguration sessionConfiguration;

    private List<AsyncReceiveNode> receiveNodes;

    private boolean mutable = true;

    private boolean hasMultipleAgendaGroups = false;

    private final PartitionsManager partitionsManager = new PartitionsManager();

    private boolean partitioned;

    public KnowledgeBaseImpl() { }

    public KnowledgeBaseImpl(String id) {
        this(id, (CompositeBaseConfiguration)  RuleBaseFactory.newKnowledgeBaseConfiguration());
    }

    public KnowledgeBaseImpl(final String id,
                             final CompositeBaseConfiguration config) {
        this.config = config;
        this.ruleBaseConfig = config.as(RuleBaseConfiguration.KEY);
        this.kieBaseConfig = config.as(KieBaseConfigurationImpl.KEY);

        createRulebaseId(id);

        this.rootClassLoader = this.config.getClassLoader();

        this.pkgs = new HashMap<>();
        this.processes = new HashMap<>();
        this.globals = new HashMap<>();

        this.classFieldAccessorCache = new ClassFieldAccessorCache(this.rootClassLoader);

        setupRete();

        sessionConfiguration = RuleBaseFactory.newKnowledgeSessionConfiguration(config.getProperties(), this.config.getClassLoader()).as(SessionConfiguration.KEY);

        mutable = kieBaseConfig.isMutabilityEnabled();
    }

    private void createRulebaseId(final String id) {
        if (id != null) {
            this.id = id;
        } else {
            String key = "";
            if (kieBaseConfig.isMBeansEnabled()) {
                DroolsManagementAgent agent = DroolsManagementAgent.getInstance();
                key = String.valueOf(agent.getNextKnowledgeBaseId());
            }
            this.id = "default" + key;
        }
    }

    public SessionConfiguration getSessionConfiguration() {
        return sessionConfiguration;
    }

    public void removeKiePackage(String packageName) {
        final InternalKnowledgePackage pkg = this.pkgs.get( packageName );
        if (pkg == null) {
            throw new IllegalArgumentException( "Package name '" + packageName + "' does not exist for this Rule Base." );
        }
        kBaseInternal_removeRules( pkg.getRules(), Collections.emptyList() );
        kBaseInternal_removePackage( pkg, Collections.emptyList() );
    }

    public void kBaseInternal_removePackage(InternalKnowledgePackage pkg, Collection<InternalWorkingMemory> workingMemories) {
        // getting the list of referenced globals
        final Set<String> referencedGlobals = new HashSet<>();
        for (InternalKnowledgePackage pkgref : this.pkgs.values()) {
            if (pkgref != pkg) {
                referencedGlobals.addAll( pkgref.getGlobals().keySet() );
            }
        }
        // removing globals declared inside the package that are not shared
        for (String globalName : pkg.getGlobals().keySet()) {
            if (!referencedGlobals.contains( globalName )) {
                this.globals.remove( globalName );
            }
        }
        //and now the rule flows
        for ( String processName : new ArrayList<>(pkg.getRuleFlows().keySet()) ) {
            removeProcess( processName );
        }
        // removing the package itself from the list
        this.pkgs.remove( pkg.getName() );

        pkg.getDialectRuntimeRegistry().onRemove();

        //clear all members of the pkg
        pkg.clear();
    }

    public Rule getRule(String packageName, String ruleName) {
        InternalKnowledgePackage p = getPackage(packageName);
        return p == null ? null : p.getRule( ruleName );
    }

    public Query getQuery(String packageName, String queryName) {
        return getPackage(packageName).getRule( queryName );
    }

    public Collection<KiePackage> getKiePackages() {
        InternalKnowledgePackage[] knowledgePackages = getPackages();
        List<KiePackage> list = new ArrayList<>(knowledgePackages.length);
        Collections.addAll( list, knowledgePackages );
        return list;
    }

    public KiePackage getKiePackage(String packageName) {
        return getPackage(packageName);
    }

    // ------------------------------------------------------------
    // Instance methods
    // ------------------------------------------------------------


    /**
     * @return the id
     */
    public String getId() {
        return this.id;
    }

    public FactHandleFactory newFactHandleFactory() {
        return RuntimeComponentFactory.get().getFactHandleFactoryService().newInstance();
    }

    public FactHandleFactory newFactHandleFactory(long id, long counter) {
        return RuntimeComponentFactory.get().getFactHandleFactoryService().newInstance(id, counter);
    }

    public Collection<Process> getProcesses() {
        readLock();
        try {
            return this.processes.values();
        } finally {
            readUnlock();
        }
    }

    public InternalKnowledgePackage[] getPackages() {
        readLock();
        try {
            return this.pkgs.values().toArray( new InternalKnowledgePackage[this.pkgs.size()] );
        } finally {
            readUnlock();
        }
    }

    // TODO WARN: the below must be mutale as that is used by TraitFactory.getClassFieldAccessorStore
    @Override
    public Map<String, InternalKnowledgePackage> getPackagesMap() {
        return this.pkgs;
    }

    // TODO WARN: the below must be mutable as it's used by org.drools.compiler.builder.impl.KnowledgeBuilderTest
    @Override
    public Map<String, Type> getGlobals() {
        return this.globals;
    }

    public void kBaseInternal_lock() {
        // Always lock to increase the counter
        this.lock.writeLock().lock();
    }

    public void kBaseInternal_unlock() {
        this.lock.writeLock().unlock();
    }

    public void readLock() {
        this.lock.readLock().lock();
    }

    public void readUnlock() {
        this.lock.readLock().unlock();
    }

    public ReentrantReadWriteLock kBaseInternal_getLock() {
        return lock;
    }

    public void kBaseInternal_writeLock() {
        this.lock.writeLock().lock();
    }

    public boolean kBaseInternal_tryWriteLock() {
        return this.lock.writeLock().tryLock();
    }

    public void kBaseInternal_writeUnlock() {
        this.lock.writeLock().unlock();
    }

    /**
     * Add a <code>Package</code> to the network. Iterates through the
     * <code>Package</code> adding Each individual <code>Rule</code> to the
     * network. Before update network each referenced <code>WorkingMemory</code>
     * is locked.
     *
     * @param newPkgs The package to add.
     */
    @Override
    public void addPackages( Collection<? extends KiePackage> newPkgs ) {
        final List<InternalKnowledgePackage> clonedPkgs = new ArrayList<>();
        for (KiePackage newPkg : newPkgs) {
            clonedPkgs.add(((InternalKnowledgePackage)newPkg).deepCloneIfAlreadyInUse(rootClassLoader));
        }

        clonedPkgs.sort(Comparator.comparing( (InternalKnowledgePackage p) -> p.getRules().size() ).reversed().thenComparing( InternalKnowledgePackage::getName ));
        kBaseInternal_addPackages( clonedPkgs, Collections.emptyList() );
    }

    @Override
    public Future<KiePackage> addPackage( final KiePackage newPkg ) {
        InternalKnowledgePackage clonedPkg = ((InternalKnowledgePackage)newPkg).deepCloneIfAlreadyInUse(rootClassLoader);
        CompletableFuture<KiePackage> result = new CompletableFuture<>();
        kBaseInternal_addPackages( Collections.singletonList(clonedPkg), Collections.emptyList() );
        result.complete( getPackage(newPkg.getName()) );
        return result;
    }

    public void kBaseInternal_addPackages(Collection<InternalKnowledgePackage> clonedPkgs, Collection<InternalWorkingMemory> workingMemories) {
        // we need to merge all byte[] first, so that the root classloader can resolve classes
        for (InternalKnowledgePackage newPkg : clonedPkgs) {
            newPkg.checkValidity();

            newPkg.mergeTraitRegistry(this);

            InternalKnowledgePackage pkg = this.pkgs.get( newPkg.getName() );
            if ( pkg == null ) {
                pkg = CoreComponentFactory.get().createKnowledgePackage(newPkg.getName());
                pkg.setClassFieldAccessorCache( this.classFieldAccessorCache );
                pkgs.put( pkg.getName(), pkg );
            }

            // first merge anything related to classloader re-wiring
            pkg.getDialectRuntimeRegistry().merge( newPkg.getDialectRuntimeRegistry(), this.rootClassLoader, true );
        }

        processAllTypesDeclaration( clonedPkgs );

        for ( InternalKnowledgePackage newPkg : clonedPkgs ) {
            // Add functions
            JavaDialectRuntimeData runtime = ((JavaDialectRuntimeData) newPkg.getDialectRuntimeRegistry().getDialectData( "java" ));

            for ( Function function : newPkg.getFunctions().values() ) {
                String functionClassName = function.getClassName();
                try {
                    registerFunctionClassAndInnerClasses( functionClassName, runtime, this::registerAndLoadTypeDefinition );
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException( "Unable to compile function '" + function.getName() + "'", e );
                }
            }
        }

        // now iterate again, this time onBeforeExecute will handle any wiring or cloader re-creating that needs to be done as part of the merge
        for (InternalKnowledgePackage newPkg : clonedPkgs) {
            InternalKnowledgePackage pkg = this.pkgs.get( newPkg.getName() );

            // this needs to go here, as functions will set a java dialect to dirty
            if (newPkg.getFunctions() != null) {
                for (Map.Entry<String, Function> entry : newPkg.getFunctions().entrySet()) {
                    pkg.addFunction( entry.getValue() );
                }
            }

            pkg.getDialectRuntimeRegistry().onBeforeExecute();

            // with the classloader recreated for all byte[] classes, we should now merge and wire any new accessors
            pkg.mergeStore( newPkg );
        }


        for (InternalKnowledgePackage newPkg : clonedPkgs) {
            InternalKnowledgePackage pkg = this.pkgs.get( newPkg.getName() );

            // now merge the new package into the existing one
            mergePackage( pkg, newPkg, workingMemories );

            // add the window declarations to the kbase
            for( WindowDeclaration window : newPkg.getWindowDeclarations().values() ) {
                this.reteooBuilder.addNamedWindow(window, workingMemories);
            }

            // add entry points to the kbase
            for (String entryPointId : newPkg.getEntryPointIds()) {
                this.reteooBuilder.addEntryPoint(entryPointId, workingMemories);
            }

            // add the rules to the RuleBase
            kBaseInternal_addRules( newPkg.getRules(), workingMemories );

            // add the flows to the RuleBase
            if ( newPkg.getRuleFlows() != null ) {
                final Map<String, Process> flows = newPkg.getRuleFlows();
                for ( Process process : flows.values() ) {
                    kBaseInternal_addProcess( process );
                }
            }

            if ( ! newPkg.getResourceTypePackages().isEmpty() ) {
                for ( ResourceTypePackage rtkKpg : newPkg.getResourceTypePackages().values() ) {
                    WEAVERS.weave( newPkg, rtkKpg );
                }
            }

            ruleUnitDescriptionRegistry.add(newPkg.getRuleUnitDescriptionLoader());
        }

        if (ruleBaseConfig.isParallelEvaluation()) {
            setupParallelEvaluation();
        }
    }

    private void setupParallelEvaluation() {
        if (!partitionsManager.hasParallelEvaluation()) {
            disableParallelEvaluation("The rete network cannot be partitioned: disabling multithread evaluation");
            return;
        }
        partitionsManager.init();
        this.partitioned = true;

        if (ruleBaseConfig.isParallelExecution()) {
            for (EntryPointNode epn : rete.getEntryPointNodes().values()) {
                epn.setupParallelExecution(this);
                for (ObjectTypeNode otn : epn.getObjectTypeNodes().values()) {
                    otn.setupParallelExecution(this);
                }
            }
        }
    }

    public void processAllTypesDeclaration( Collection<InternalKnowledgePackage> pkgs ) {
        List<TypeDeclaration> allTypeDeclarations = new ArrayList<>();
        // Add all Type Declarations, this has to be done first incase packages cross reference each other during build process.
        for ( InternalKnowledgePackage newPkg : pkgs ) {
            // we have to do this before the merging, as it does some classloader resolving
            if ( newPkg.getTypeDeclarations() != null ) {
                allTypeDeclarations.addAll( newPkg.getTypeDeclarations().values() );
            }
        }
        Collections.sort( allTypeDeclarations );

        // add type declarations according to the global order
        for ( TypeDeclaration newDecl : allTypeDeclarations ) {
            InternalKnowledgePackage newPkg = null;
            for ( InternalKnowledgePackage kpkg : pkgs ) {
                if ( kpkg.getTypeDeclarations().containsKey( newDecl.getTypeName() ) ) {
                    newPkg = kpkg;
                    break;
                }
            }
            processTypeDeclaration( newDecl, newPkg );
        }
    }

    private void checkParallelEvaluation(RuleImpl rule) {
        if (ruleBaseConfig.isParallelEvaluation()) {
            if (!rule.isMainAgendaGroup()) {
                disableParallelEvaluation( "Agenda-groups are not supported with parallel execution: disabling it" );
            } else if (rule.getActivationGroup() != null) {
                disableParallelEvaluation( "Activation-groups are not supported with parallel execution: disabling it" );
            } else if (!rule.getSalience().isDefault() && ruleBaseConfig.isParallelExecution()) {
                disableParallelEvaluation( "Salience is not supported with parallel execution: disabling it" );
            } else if (rule.isQuery()) {
                disableParallelEvaluation( "Queries are not supported with parallel execution: disabling it" );
            }
        }
    }

    public boolean hasMultipleAgendaGroups() {
        return hasMultipleAgendaGroups;
    }

    private void disableParallelEvaluation(String warningMessage) {
        ruleBaseConfig.enforceSingleThreadEvaluation();
        logger.warn( warningMessage );
        for (EntryPointNode entryPointNode : rete.getEntryPointNodes().values()) {
            for (ObjectTypeNode otn : entryPointNode.getObjectTypeNodes().values()) {
                ObjectSinkPropagator sink = otn.getObjectSinkPropagator();
                if (sink instanceof CompositePartitionAwareObjectSinkAdapter) {
                    otn.setObjectSinkPropagator( ( (CompositePartitionAwareObjectSinkAdapter) sink )
                                                         .asNonPartitionedSinkPropagator( ruleBaseConfig.getAlphaNodeHashingThreshold(), ruleBaseConfig.getAlphaNodeRangeIndexThreshold() ) );
                }
            }
        }
    }

    public interface ClassRegister {
        void register(String name, byte[] bytes) throws ClassNotFoundException;
    }

    public static void registerFunctionClassAndInnerClasses( String functionClassName, JavaDialectRuntimeData runtime, ClassRegister consumer ) throws ClassNotFoundException {
        String className = convertClassToResourcePath(functionClassName);
        String innerClassName = className.substring( 0, className.length() - ".class".length() ) + "$";
        for (Map.Entry<String, byte[]> entry : runtime.getStore().entrySet()) {
            if (entry.getKey().equals( className )) {
                consumer.register( functionClassName, entry.getValue() );
            } else if (entry.getKey().startsWith( innerClassName )) {
                String innerName = functionClassName + entry.getKey().substring( functionClassName.length(), entry.getKey().length() - ".class".length() );
                consumer.register( innerName, entry.getValue() );
            }
        }
    }

    public void registerTypeDeclaration( TypeDeclaration newDecl, InternalKnowledgePackage newPkg ) {
        this.classTypeDeclaration.put( newDecl.getTypeClassName(), newDecl );
    }

    protected void processTypeDeclaration( TypeDeclaration newDecl, InternalKnowledgePackage newPkg ) {
        JavaDialectRuntimeData runtime = ((JavaDialectRuntimeData) newPkg.getDialectRuntimeRegistry().getDialectData( "java" ));

        TypeDeclaration typeDeclaration = this.classTypeDeclaration.get( newDecl.getTypeClassName() );
        if ( typeDeclaration == null ) {
            String className = newDecl.getTypeClassName();

            byte [] def = runtime != null ? runtime.getClassDefinition(convertClassToResourcePath(className)) : null;
            try {
                Class<?> definedKlass = registerAndLoadTypeDefinition( className, def );

                if (newDecl.getTypeClassDef() == null) {
                    newDecl.setTypeClassDef( new ClassDefinition() );
                }
                newDecl.setTypeClass( definedKlass );
            } catch (ClassNotFoundException e) {
                if (newDecl.isNovel()) {
                    throw new RuntimeException( "unable to resolve Type Declaration class '" + className + "'", e );
                }
            }

            this.classTypeDeclaration.put( className, newDecl );
            typeDeclaration = newDecl;
        } else {
            Class<?> definedKlass = typeDeclaration.getTypeClass();

            newDecl.getTypeClassDef().setDefinedClass( definedKlass );
            newDecl.setTypeClass( definedKlass );

            mergeTypeDeclarations( typeDeclaration,
                    newDecl );
        }

        // update existing OTNs
        updateDependentTypes( typeDeclaration );
    }

    public Class<?> registerAndLoadTypeDefinition( String className, byte[] def ) throws ClassNotFoundException {
        try {
            return this.rootClassLoader.loadClass( className );
        } catch (ClassNotFoundException e) {
            if (def != null && rootClassLoader instanceof ProjectClassLoader) {
                return ((ProjectClassLoader)rootClassLoader).defineClass(className, def);
            }
            throw e;
        }
    }

    private void updateDependentTypes( TypeDeclaration typeDeclaration ) {
        // update OTNs
        if( this.ruleBaseConfig.getEventProcessingMode().equals( EventProcessingOption.STREAM ) ) {
            // if the expiration for the type was set, then add 1, otherwise return -1
            long exp = typeDeclaration.getExpirationOffset() > -1 ? typeDeclaration.getExpirationOffset() + 1 : -1;

            // if we are running in STREAM mode, update expiration offset
            for( EntryPointNode ep : this.rete.getEntryPointNodes().values() ) {
                for( ObjectTypeNode node : ep.getObjectTypeNodes().values() ) {
                    if( node.getObjectType().equals( typeDeclaration.getObjectType() ) ) {
                        node.setExpirationOffset( Math.max( node.getExpirationOffset(), exp ) );
                    }
                }
            }
        }
    }

    private void mergeTypeDeclarations( TypeDeclaration existingDecl,
                                        TypeDeclaration newDecl ) {

        if ( ! Objects.equals( existingDecl.getFormat(),
                                  newDecl.getFormat() ) ||
             ! Objects.equals( existingDecl.getObjectType(),
                                  newDecl.getObjectType() ) ||
             ! Objects.equals( existingDecl.getTypeClassName(),
                                  newDecl.getTypeClassName() ) ||
             ! Objects.equals( existingDecl.getTypeName(),
                                  newDecl.getTypeName() ) ) {

            throw new RuntimeException( "Unable to merge Type Declaration for class '" + existingDecl.getTypeName() + "'" );

        }

        existingDecl.setDurationAttribute( mergeLeft( existingDecl.getTypeName(),
                                                      "Unable to merge @duration attribute for type declaration of class:",
                                                      existingDecl.getDurationAttribute(),
                                                      newDecl.getDurationAttribute(),
                                                      false ) );

        existingDecl.setDynamic( mergeLeft( existingDecl.getTypeName(),
                                            "Unable to merge @propertyChangeSupport  (a.k.a. dynamic) attribute for type declaration of class:",
                                            existingDecl.isDynamic(),
                                            newDecl.isDynamic(),
                                            false ) );

        existingDecl.setPropertyReactive( mergeLeft(existingDecl.getTypeName(),
                                                    "Unable to merge @propertyReactive attribute for type declaration of class:",
                                                    existingDecl.isPropertyReactive(),
                                                    newDecl.isPropertyReactive(),
                                                    false) );

        if ( newDecl.getExpirationPolicy() == Policy.TIME_HARD ) {
            if (existingDecl.getExpirationPolicy() == Policy.TIME_SOFT ||
                newDecl.getExpirationOffset() > existingDecl.getExpirationOffset()) {
                existingDecl.setExpirationOffset( newDecl.getExpirationOffset() );
                existingDecl.setExpirationType( Policy.TIME_HARD );
            }
        } else {
            if (existingDecl.getExpirationPolicy() == Policy.TIME_SOFT &&
                newDecl.getExpirationOffset() > existingDecl.getExpirationOffset()) {
                existingDecl.setExpirationOffset( newDecl.getExpirationOffset() );
            }
        }

        if ( newDecl.getNature().equals( TypeDeclaration.Nature.DEFINITION ) && newDecl.isNovel() ) {
            // At this point, the definitions must be equivalent.
            // So the only illegal case is a novel definition of an already existing type
            existingDecl.setNovel( mergeLeft( existingDecl.getTypeName(),
                                              "Unable to merge @novel attribute for type declaration of class:",
                                              existingDecl.isNovel(),
                                              newDecl.isNovel(),
                                              false ) );
        }

        if ( newDecl.getNature().equals( TypeDeclaration.Nature.DEFINITION ) || existingDecl.getResource() == null ) {
            existingDecl.setResource( mergeLeft( existingDecl.getTypeName(),
                                                 "Unable to merge resource attribute for type declaration of class:",
                                                 existingDecl.getResource(),
                                                 newDecl.getResource(),
                                                 true ) );
        }

        existingDecl.setRole( mergeLeft( existingDecl.getTypeName(),
                                         "Unable to merge @role attribute for type declaration of class:",
                                         isSet(existingDecl.getSetMask(), TypeDeclaration.ROLE_BIT)
                                         && newDecl.getRole() != Role.Type.FACT
                                         ? existingDecl.getRole() : null,
                                         newDecl.getRole(),
                                         false ) );

        existingDecl.setTimestampAttribute( mergeLeft( existingDecl.getTypeName(),
                                                       "Unable to merge @timestamp attribute for type declaration of class:",
                                                       existingDecl.getTimestampAttribute(),
                                                       newDecl.getTimestampAttribute(),
                                                       false ) );

        existingDecl.setTypesafe( mergeLeft(existingDecl.getTypeName(),
                                            "Unable to merge @typesafe attribute for type declaration of class:",
                                            existingDecl.isTypesafe(),
                                            newDecl.isTypesafe(),
                                            false ) );
    }

    private <T> T mergeLeft( String typeClass,
                             String errorMsg,
                             T leftVal,
                             T rightVal,
                             boolean override ) {
        T newValue = leftVal;
        if ( ! Objects.equals( leftVal, rightVal ) ) {
            if ( leftVal == null ) {
                newValue = rightVal;
            } else if ( rightVal != null ) {
                if ( override ) {
                    newValue = rightVal;
                } else {
                    throw new RuntimeException( errorMsg + " '" + typeClass + "'" );
                }
            }
        }
        return newValue;
    }

    /**
     * Merge a new package with an existing package.
     * Most of the work is done by the concrete implementations,
     * but this class does some work (including combining imports, compilation data, globals,
     * and the actual Rule objects into the package).
     */
    private void mergePackage( InternalKnowledgePackage pkg, InternalKnowledgePackage newPkg, Collection<InternalWorkingMemory> workingMemories ) {
        // Merge imports
        final Map<String, ImportDeclaration> imports = pkg.getImports();
        imports.putAll(newPkg.getImports());

        // Merge static imports
        for (String staticImport : newPkg.getStaticImports()) {
            pkg.addStaticImport(staticImport);
        }

        // merge globals
        if (newPkg.getGlobals() != null && !newPkg.getGlobals().isEmpty()) {
            Map<String, Type> pkgGlobals = pkg.getGlobals();
            // Add globals
            for (final Map.Entry<String, Type> entry : newPkg.getGlobals().entrySet()) {
                final String identifier = entry.getKey();
                final Type type = entry.getValue();
                if (pkgGlobals.containsKey( identifier ) && !pkgGlobals.get( identifier ).equals( type )) {
                    throw new RuntimeException(pkg.getName() + " cannot be integrated");
                } else {
                    pkg.addGlobal( identifier, type );
                    // this isn't a package merge, it's adding to the rulebase, but I've put it here for convienience
                    addGlobal( identifier, type );
                }
            }
        }

        // merge entry point declarations
        if (newPkg.getEntryPointIds() != null) {
            for (String ep : newPkg.getEntryPointIds()) {
                pkg.addEntryPointId( ep );

            }
        }

        // merge the type declarations
        if (newPkg.getTypeDeclarations() != null) {
            // add type declarations
            for (TypeDeclaration type : newPkg.getTypeDeclarations().values()) {
                // @TODO should we allow overrides? only if the class is not in use.
                if (!pkg.getTypeDeclarations().containsKey( type.getTypeName() )) {
                    // add to package list of type declarations
                    pkg.addTypeDeclaration( type );
                }
            }
        }

        // merge window declarations
        if ( newPkg.getWindowDeclarations() != null ) {
            // add window declarations
            for ( WindowDeclaration window : newPkg.getWindowDeclarations().values() ) {
                if ( !pkg.getWindowDeclarations().containsKey( window.getName() ) ||
                     pkg.getWindowDeclarations().get( window.getName() ).equals( window ) ) {
                    pkg.addWindowDeclaration( window );
                } else {
                    throw new RuntimeException( "Unable to merge two conflicting window declarations for window named: "+window.getName() );
                }
            }
        }

        //Merge rules into the RuleBase package
        //as this is needed for individual rule removal later on
        List<RuleImpl> rulesToBeRemoved = new ArrayList<>();
        for (Rule newRule : newPkg.getRules()) {
            // remove the rule if it already exists
            RuleImpl oldRule = pkg.getRule(newRule.getName());
            if (oldRule != null) {
                rulesToBeRemoved.add(oldRule);
            }
        }
        if (!rulesToBeRemoved.isEmpty()) {
            kBaseInternal_removeRules( rulesToBeRemoved, workingMemories );
        }

        for (Rule newRule : newPkg.getRules()) {
            pkg.addRule((RuleImpl)newRule);
        }

        //Merge The Rule Flows
        if (newPkg.getRuleFlows() != null) {
            for (Process flow : newPkg.getRuleFlows().values()) {
                pkg.addProcess(flow);
            }
        }

        if ( ! newPkg.getResourceTypePackages().isEmpty() ) {
            if (WEAVERS == null) {
                throw new IllegalStateException("Unable to find KieWeavers implementation");
            }
            for ( ResourceTypePackage rtkKpg : newPkg.getResourceTypePackages().values() ) {
                WEAVERS.merge( pkg, rtkKpg );
            }
        }
    }

    @Override
    public void addGlobal(String identifier, Type type) {
        this.globals.put( identifier, type );
    }

    public void removeGlobal(String identifier) {
        // check if there is still at least a package containing the global
        for (InternalKnowledgePackage pkg : pkgs.values()) {
            if ( pkg.getGlobals().get( identifier ) != null) {
                return;
            }
        }

        this.globals.remove( identifier );
    }

    protected void setupRete() {
        this.rete = new Rete( this );
        this.reteooBuilder = new ReteooBuilder( this );

        NodeFactory nodeFactory = CoreComponentFactory.get().getNodeFactoryService();

        // always add the default entry point
        EntryPointNode epn = nodeFactory.buildEntryPointNode(this.reteooBuilder.getNodeIdsGenerator().getNextId(),
                                                             RuleBasePartitionId.MAIN_PARTITION,
                                                             this.rete,
                                                             EntryPointId.DEFAULT);
        epn.attach();

        BuildContext context = new BuildContext(this, Collections.emptyList());
        context.setCurrentEntryPoint(epn.getEntryPoint());
        context.setTupleMemoryEnabled(true);
        context.setPartitionId(RuleBasePartitionId.MAIN_PARTITION);

        ObjectTypeNode otn = nodeFactory.buildObjectTypeNode(this.reteooBuilder.getNodeIdsGenerator().getNextId(),
                                                             epn,
                                                             ClassObjectType.InitialFact_ObjectType,
                                                             context);
        otn.attach(context);
    }

    public void registerAddedEntryNodeCache(EntryPointNode node) {
        if (addedEntryNodeCache == null) {
            addedEntryNodeCache = new HashSet<>();
        }
        addedEntryNodeCache.add(node);
    }

    public Set<EntryPointNode> getAddedEntryNodeCache() {
        return addedEntryNodeCache;
    }

    public void registeRremovedEntryNodeCache(EntryPointNode node) {
        if (removedEntryNodeCache == null) {
            removedEntryNodeCache = new HashSet<>();
        }
        removedEntryNodeCache.add(node);
    }

    public Set<EntryPointNode> getRemovedEntryNodeCache() {
        return removedEntryNodeCache;
    }

    /**
     * Retrieve the Rete-OO network for this <code>RuleBase</code>.
     *
     * @return The RETE-OO network.
     */
    public Rete getRete() {
        return this.rete;
    }

    public ReteooBuilder getReteooBuilder() {
        return this.reteooBuilder;
    }

    public int getNodeCount() {
        // may start in 0
        return this.reteooBuilder.getNodeIdsGenerator().getLastId() + 1;
    }

    public int getMemoryCount() {
        // may start in 0
        return this.reteooBuilder.getMemoryIdsGenerator().getLastId() + 1;
    }

    public boolean hasSegmentPrototypes() {
        return !segmentProtos.isEmpty();
    }

    public void registerSegmentPrototype(LeftTupleNode tupleSource, SegmentPrototype smem) {
        segmentProtos.put(tupleSource.getId(), smem);
    }

    public void invalidateSegmentPrototype(LeftTupleNode rootNode) {
        segmentProtos.remove(rootNode.getId());
    }

    @Override
    public SegmentPrototype getSegmentPrototype(LeftTupleNode node) {
        return segmentProtos.get(node.getId());
    }

    @Override
    public SegmentMemory createSegmentFromPrototype(ReteEvaluator reteEvaluator, LeftTupleSource tupleSource) {
        SegmentPrototype proto = segmentProtos.get(tupleSource.getId());
        return createSegmentFromPrototype(reteEvaluator, proto);
    }

    public SegmentMemory createSegmentFromPrototype(ReteEvaluator reteEvaluator, SegmentPrototype proto) {
        return proto.newSegmentMemory(reteEvaluator);
    }

    public SegmentPrototype getSegmentPrototype(SegmentMemory segment) {
        return segmentProtos.get(segment.getRootNode().getId());
    }

    private static class TypeDeclarationCandidate {

        public TypeDeclaration candidate = null;
        public int             score     = Integer.MAX_VALUE;
    }

    public TypeDeclaration getExactTypeDeclaration( Class<?> clazz ) {
        return this.classTypeDeclaration.get( clazz.getName() );
    }

    public TypeDeclaration getOrCreateExactTypeDeclaration( Class<?> clazz ) {
        return this.classTypeDeclaration.computeIfAbsent( clazz.getName(), c -> TypeDeclaration.createTypeDeclarationForBean( clazz ) );
    }

    public TypeDeclaration getTypeDeclaration( Class<?> clazz ) {
        TypeDeclaration typeDeclaration = getExactTypeDeclaration( clazz );
        if (typeDeclaration == null) {
            // check super classes and keep a score of how up in the hierarchy is there a declaration
            TypeDeclarationCandidate candidate = checkSuperClasses( clazz );
            // now check interfaces
            candidate = checkInterfaces( clazz,
                                         candidate,
                                         1 );
            if (candidate != null) {
                typeDeclaration = candidate.candidate;
            }
        }
        return typeDeclaration;
    }

    private TypeDeclarationCandidate checkSuperClasses( Class<?> clazz ) {

        TypeDeclaration typeDeclaration = null;
        Class<?> current = clazz.getSuperclass();
        int score = 0;
        while ( typeDeclaration == null && current != null ) {
            score++;
            typeDeclaration = this.classTypeDeclaration.get( current.getName() );
            current = current.getSuperclass();
        }
        TypeDeclarationCandidate candidate = null;
        if ( typeDeclaration != null ) {
            candidate = new TypeDeclarationCandidate();
            candidate.candidate = typeDeclaration;
            candidate.score = score;
        }
        return candidate;
    }

    private TypeDeclarationCandidate checkInterfaces( Class<?> clazz,
                                                      TypeDeclarationCandidate baseline,
                                                      int level ) {
        TypeDeclarationCandidate candidate = null;
        if (baseline == null || level < baseline.score) {
            // search
            TypeDeclaration typeDeclaration;
            for (Class<?> ifc : clazz.getInterfaces()) {
                typeDeclaration = this.classTypeDeclaration.get( ifc.getName() );
                if (typeDeclaration != null) {
                    candidate = new TypeDeclarationCandidate();
                    candidate.candidate = typeDeclaration;
                    candidate.score = level;
                    break;
                } else {
                    candidate = checkInterfaces( ifc,
                                                 baseline,
                                                 level + 1 );
                }
            }
        } else {
            candidate = baseline;
        }
        return candidate;
    }

    public Collection<TypeDeclaration> getTypeDeclarations() {
        return this.classTypeDeclaration.values();
    }

    @Override
    public void beforeIncrementalUpdate(KieBaseUpdate kieBaseUpdate) {
    }

    @Override
    public void afterIncrementalUpdate(KieBaseUpdate kieBaseUpdate) {
    }

    public void addRules(Collection<RuleImpl> rules ) throws InvalidPatternException {
        kBaseInternal_addRules( rules, Collections.emptyList() );
    }

    public void kBaseInternal_addRules(Collection<? extends Rule> rules, Collection<InternalWorkingMemory> wms ) {
        List<TerminalNode> terminalNodes = new ArrayList<>(rules.size() * 2);

        for (Rule r : rules) {
            RuleImpl rule = (RuleImpl) r;
            checkParallelEvaluation( rule );
            this.hasMultipleAgendaGroups |= !rule.isMainAgendaGroup();
            terminalNodes.addAll(this.reteooBuilder.addRule(rule, wms));
        }

        if (PhreakBuilder.isEagerSegmentCreation() && !hasSegmentPrototypes()) {
            // All Protos must be created, before inserting objects.
            for (TerminalNode tn : terminalNodes) {
                tn.getPathMemSpec();
                BuildtimeSegmentUtilities.createPathProtoMemories(tn, null, this);
            }
            Set<Integer> visited = new HashSet<>();
            for (TerminalNode tn : terminalNodes) {
                // populate memories
                wms.stream().forEach( wm -> {
                    Add.insertLiaFacts(tn.getPathNodes()[0], wm, visited, true);
                    Add.insertFacts(tn, wm, visited, true);
                });
            }
        }
    }

    public void removeQuery( final String packageName, final String ruleName ) {
        removeRule(packageName, ruleName);
    }

    public void removeRules( Collection<RuleImpl> rules ) {
        kBaseInternal_removeRules( rules, Collections.emptyList() );
    }

    public void removeRule( final String packageName, final String ruleName ) {
        final InternalKnowledgePackage pkg = pkgs.get(packageName);
        if (pkg == null) {
            throw new IllegalArgumentException( "Package name '" + packageName +
                    "' does not exist for this Rule Base." );
        }

        RuleImpl rule = pkg.getRule(ruleName);
        if (rule == null) {
            throw new IllegalArgumentException( "Rule name '" + ruleName +
                    "' does not exist in the Package '" + packageName + "'." );
        }
        kBaseInternal_removeRule(pkg, rule, Collections.emptyList());
    }

    public void kBaseInternal_removeRule(InternalKnowledgePackage pkg, RuleImpl rule, Collection<InternalWorkingMemory> workingMemories) {
        this.reteooBuilder.removeRules(Collections.singletonList(rule), workingMemories);
        pkg.removeRule( rule );
        addReloadDialectDatas( pkg.getDialectRuntimeRegistry() );
    }

    public void kBaseInternal_removeRules(Collection<? extends Rule> rules, Collection<InternalWorkingMemory> workingMemories) {
        this.reteooBuilder.removeRules(rules, workingMemories);
    }

    public void removeFunction( final String packageName, final String functionName ) {
        final InternalKnowledgePackage pkg = this.pkgs.get( packageName );
        if (pkg == null) {
            throw new IllegalArgumentException( "Package name '" + packageName +
                    "' does not exist for this Rule Base." );
        }
        kBaseInternal_removeFunction( pkg, functionName );
    }

    public void kBaseInternal_removeFunction(InternalKnowledgePackage pkg, String functionName ) {
        Function function = pkg.getFunctions().get( functionName );
        if (function == null) {
            throw new IllegalArgumentException( "function name '" + functionName +
                    "' does not exist in the Package '" + pkg.getName() + "'." );
        }

        pkg.removeFunction( functionName );

        if (rootClassLoader instanceof ProjectClassLoader ) {
            ((ProjectClassLoader)rootClassLoader).undefineClass(function.getClassName());
        }

        addReloadDialectDatas( pkg.getDialectRuntimeRegistry() );
    }

    public void addProcess( final Process process ) {
        // XXX: could use a synchronized(processes) here.
        kBaseInternal_lock();
        try {
            kBaseInternal_addProcess( process );
        } finally {
            kBaseInternal_unlock();
        }
    }

    public void kBaseInternal_addProcess(Process process ) {
        this.processes.put( process.getId(), process );
    }

    public void removeProcess( final String id ) {
        Process process = this.processes.get( id );
        if ( process == null ) {
            throw new IllegalArgumentException( "Process '" + id + "' does not exist for this Rule Base." );
        }
        kBaseInternal_removeProcess( id, process );
    }

    public void kBaseInternal_removeProcess(String id, Process process) {
        this.processes.remove( id );
        this.pkgs.get( process.getPackageName() ).removeRuleFlow( id );
    }

    public Process getProcess( final String id ) {
        readLock();
        try {
            return this.processes.get( id );
        } finally {
            readUnlock();
        }
    }

    public InternalKnowledgePackage getPackage( final String name ) {
        return this.pkgs.get( name );
    }

    public RuleBaseConfiguration getRuleBaseConfiguration() {
        return this.ruleBaseConfig;
    }

    public KieBaseConfigurationImpl getKieBaseConfiguration() {
        return this.kieBaseConfig;
    }

    @Override public KieBaseConfiguration getConfiguration() {
        return config;
    }

    public ClassLoader getRootClassLoader() {
        return this.rootClassLoader;
    }

    public void executeQueuedActions() {
        if (mutable) {
            DialectRuntimeRegistry registry;
            while ((registry = reloadPackageCompilationData.poll()) != null) {
                registry.onBeforeExecute();
            }
        }
    }

    private void addReloadDialectDatas( DialectRuntimeRegistry registry ) {
        this.reloadPackageCompilationData.offer( registry );
    }

    @Override
    public RuleBasePartitionId createNewPartitionId() {
        return partitionsManager.createNewPartitionId();
    }

    @Override
    public boolean isPartitioned() {
        return partitioned;
    }

    @Override
    public int getParallelEvaluationSlotsCount() {
        return partitionsManager.getParallelEvaluationSlotsCount();
    }

    public FactType getFactType(String packageName, String typeName) {
        String name = packageName + "." + typeName;
        readLock();
        try {
            for (InternalKnowledgePackage pkg : this.pkgs.values()) {
                FactType type = pkg.getFactType( name );
                if (type != null) {
                    return type;
                }
            }
            return null;
        } finally {
            readUnlock();
        }
    }

    public ClassFieldAccessorCache getClassFieldAccessorCache() {
        return this.classFieldAccessorCache;
    }

    public Set<String> getEntryPointIds() {
        Set<String> entryPointIds = new HashSet<>();
        for (InternalKnowledgePackage pkg : this.pkgs.values()) {
            entryPointIds.addAll( pkg.getEntryPointIds() );
        }
        return entryPointIds;
    }

    public boolean removeObjectsGeneratedFromResource(Resource resource, Collection<InternalWorkingMemory> workingMemories) {
        boolean modified = false;
        for (InternalKnowledgePackage pkg : pkgs.values()) {
            List<RuleImpl> rulesToBeRemoved = pkg.getRulesGeneratedFromResource(resource);
            if (!rulesToBeRemoved.isEmpty()) {
                this.reteooBuilder.removeRules( rulesToBeRemoved, workingMemories );
                // removal of rule from package has to be delayed after the rule has been removed from the phreak network
                // in order to allow the correct flushing of all outstanding staged tuples
                for (RuleImpl rule : rulesToBeRemoved) {
                    pkg.removeRule(rule);
                }
            }

            List<Function> functionsToBeRemoved = pkg.removeFunctionsGeneratedFromResource(resource);
            for (Function function : functionsToBeRemoved) {
                kBaseInternal_removeFunction(pkg, function.getName());
            }

            List<Process> processesToBeRemoved = pkg.removeProcessesGeneratedFromResource(resource);
            for (Process process : processesToBeRemoved) {
                processes.remove(process.getId());
            }

            List<TypeDeclaration> removedTypes = pkg.removeTypesGeneratedFromResource(resource);

            boolean resourceTypePackageSomethingRemoved = pkg.removeFromResourceTypePackageGeneratedFromResource( resource );

            modified |= !rulesToBeRemoved.isEmpty()
                        || !functionsToBeRemoved.isEmpty()
                        || !processesToBeRemoved.isEmpty()
                        || !removedTypes.isEmpty()
                        || resourceTypePackageSomethingRemoved;
        }
        return modified;
    }

    @Override
    public ReleaseId getResolvedReleaseId() {
        return resolvedReleaseId;
    }

    @Override
    public void setResolvedReleaseId(ReleaseId currentReleaseId) {
        this.resolvedReleaseId = currentReleaseId;
    }

    @Override
    public String getContainerId() {
        return containerId;
    }

    @Override
    public void setContainerId(String containerId) {
        this.containerId = containerId;
    }

    public RuleUnitDescriptionRegistry getRuleUnitDescriptionRegistry() {
        return ruleUnitDescriptionRegistry;
    }

    public boolean hasUnits() {
        return ruleUnitDescriptionRegistry.hasUnits();
    }

    public List<AsyncReceiveNode> getReceiveNodes() {
        return receiveNodes;
    }

    public void addReceiveNode( AsyncReceiveNode node) {
        if (receiveNodes == null) {
            receiveNodes = new ArrayList<>();
        }
        receiveNodes.add(node);
    }
}
