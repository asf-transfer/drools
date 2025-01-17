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
package org.kie.dmn.core.compiler;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;

import org.drools.io.FileSystemResource;
import org.kie.api.io.Resource;
import org.kie.dmn.api.core.DMNCompiler;
import org.kie.dmn.api.core.DMNCompilerConfiguration;
import org.kie.dmn.api.core.DMNMessage;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.api.core.DMNType;
import org.kie.dmn.api.core.ast.BusinessKnowledgeModelNode;
import org.kie.dmn.api.core.ast.DMNNode;
import org.kie.dmn.api.core.ast.DecisionNode;
import org.kie.dmn.api.core.ast.DecisionServiceNode;
import org.kie.dmn.api.core.ast.InputDataNode;
import org.kie.dmn.api.core.ast.ItemDefNode;
import org.kie.dmn.api.marshalling.DMNExtensionRegister;
import org.kie.dmn.api.marshalling.DMNMarshaller;
import org.kie.dmn.backend.marshalling.v1x.DMNMarshallerFactory;
import org.kie.dmn.core.api.DMNFactory;
import org.kie.dmn.core.ast.BusinessKnowledgeModelNodeImpl;
import org.kie.dmn.core.ast.DMNBaseNode;
import org.kie.dmn.core.ast.DecisionNodeImpl;
import org.kie.dmn.core.ast.DecisionServiceNodeImpl;
import org.kie.dmn.core.ast.ItemDefNodeImpl;
import org.kie.dmn.core.compiler.ImportDMNResolverUtil.ImportType;
import org.kie.dmn.core.impl.BaseDMNTypeImpl;
import org.kie.dmn.core.impl.CompositeTypeImpl;
import org.kie.dmn.core.impl.DMNModelImpl;
import org.kie.dmn.core.impl.SimpleFnTypeImpl;
import org.kie.dmn.core.impl.SimpleTypeImpl;
import org.kie.dmn.core.pmml.DMNImportPMMLInfo;
import org.kie.dmn.core.util.Msg;
import org.kie.dmn.core.util.MsgUtil;
import org.kie.dmn.core.util.NamespaceUtil;
import org.kie.dmn.feel.lang.FEELDialect;
import org.kie.dmn.feel.lang.FEELProfile;
import org.kie.dmn.feel.lang.Type;
import org.kie.dmn.feel.lang.types.AliasFEELType;
import org.kie.dmn.feel.lang.types.BuiltInType;
import org.kie.dmn.feel.lang.types.GenFnType;
import org.kie.dmn.feel.lang.types.GenListType;
import org.kie.dmn.feel.runtime.UnaryTest;
import org.kie.dmn.feel.util.Either;
import org.kie.dmn.model.api.DMNElementReference;
import org.kie.dmn.model.api.DMNModelInstrumentedBase;
import org.kie.dmn.model.api.DRGElement;
import org.kie.dmn.model.api.Decision;
import org.kie.dmn.model.api.DecisionService;
import org.kie.dmn.model.api.DecisionTable;
import org.kie.dmn.model.api.Definitions;
import org.kie.dmn.model.api.FunctionItem;
import org.kie.dmn.model.api.Import;
import org.kie.dmn.model.api.InformationItem;
import org.kie.dmn.model.api.InformationRequirement;
import org.kie.dmn.model.api.ItemDefinition;
import org.kie.dmn.model.api.KnowledgeRequirement;
import org.kie.dmn.model.api.NamedElement;
import org.kie.dmn.model.api.OutputClause;
import org.kie.dmn.model.api.UnaryTests;
import org.kie.dmn.model.v1_1.TInformationItem;
import org.kie.dmn.model.v1_1.extensions.DecisionServices;
import org.kie.internal.io.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.kie.dmn.core.compiler.UnnamedImportUtils.processMergedModel;

public class DMNCompilerImpl implements DMNCompiler {

    private static final Logger logger = LoggerFactory.getLogger( DMNCompilerImpl.class );

    private final DMNDecisionLogicCompiler evaluatorCompiler;
    private DMNCompilerConfiguration dmnCompilerConfig;
    private Deque<DRGElementCompiler> drgCompilers = new LinkedList<>();
    {
        drgCompilers.add( new InputDataCompiler() );
        drgCompilers.add( new BusinessKnowledgeModelCompiler() );
        drgCompilers.add( new DecisionCompiler() );
        drgCompilers.add( new DecisionServiceCompiler() );
        drgCompilers.add( new KnowledgeSourceCompiler() ); // keep last as it's a void compiler
    }
    private final List<AfterProcessDrgElements> afterDRGcallbacks = new ArrayList<>();
    private final static Pattern QNAME_PAT = Pattern.compile("(\\{([^\\}]*)\\})?(([^:]*):)?(.*)");

    public DMNCompilerImpl() {
        this(DMNFactory.newCompilerConfiguration());
    }

    public DMNCompilerImpl(DMNCompilerConfiguration dmnCompilerConfig) {
        this.dmnCompilerConfig = dmnCompilerConfig;
        DMNCompilerConfigurationImpl cc = (DMNCompilerConfigurationImpl) dmnCompilerConfig;
        addDRGElementCompilers(cc.getDRGElementCompilers());
        this.evaluatorCompiler = cc.getDecisionLogicCompilerFactory().newDMNDecisionLogicCompiler(this, cc);
    }

    private void addDRGElementCompiler(DRGElementCompiler compiler) {
        drgCompilers.push(compiler);
    }

    private void addDRGElementCompilers(List<DRGElementCompiler> compilers) {
        ListIterator<DRGElementCompiler> listIterator = compilers.listIterator( compilers.size() );
        while ( listIterator.hasPrevious() ) {
            addDRGElementCompiler( listIterator.previous() );
        }
    }

    @Override
    public DMNModel compile(Resource resource, Collection<DMNModel> dmnModels) {
        try {
            DMNModel model = compile(resource.getReader(), dmnModels, resource);
            return model;
        } catch ( IOException e ) {
            logger.error( "Error retrieving reader for resource: " + resource.getSourcePath(), e );
        }
        return null;
    }

    @Override
    public DMNModel compile(Reader source, Collection<DMNModel> dmnModels) {
        return compile(source, dmnModels, null);
    }

    public DMNModel compile(Reader source, Collection<DMNModel> dmnModels, Resource resource) {
        try {
            Definitions dmndefs = getMarshaller().unmarshal(source);
            DMNModel model = compile(dmndefs, dmnModels, resource, null);
            return model;
        } catch ( Exception e ) {
            logger.error( "Error compiling model from source.", e );
        }
        return null;
    }

    public DMNMarshaller getMarshaller() {
        if (dmnCompilerConfig != null && !dmnCompilerConfig.getRegisteredExtensions().isEmpty()) {
            return DMNMarshallerFactory.newMarshallerWithExtensions(getDmnCompilerConfig().getRegisteredExtensions());
        } else {
            return DMNMarshallerFactory.newDefaultMarshaller();
        }
    }

    @Override
    public DMNModel compile(Definitions dmndefs, Collection<DMNModel> dmnModels) {
        try {
            return compile(dmndefs, dmnModels, null, null);
        } catch (Exception e) {
            logger.error("Error compiling model from source.", e);
        }
        return null;
    }

    @Override
    public DMNModel compile(Definitions dmndefs, Resource resource, Collection<DMNModel> dmnModels) {
        try {
            return compile(dmndefs, dmnModels, resource, null);
        } catch (Exception e) {
            logger.error("Error compiling model from source.", e);
        }
        return null;
    }

    public DMNModel compile(Definitions dmndefs, Collection<DMNModel> dmnModels, Resource resource, Function<String, Reader> relativeResolver) {
        if (dmndefs == null) {
            return null;
        }
        DMNModelImpl model = new DMNModelImpl(dmndefs, resource);
        model.setRuntimeTypeCheck(((DMNCompilerConfigurationImpl) dmnCompilerConfig).getOption(RuntimeTypeCheckOption.class).isRuntimeTypeCheck());
        DMNCompilerConfigurationImpl cc = (DMNCompilerConfigurationImpl) dmnCompilerConfig;
        List<FEELProfile> helperFEELProfiles = cc.getFeelProfiles();
        DMNFEELHelper feel = new DMNFEELHelper(cc.getRootClassLoader(), helperFEELProfiles, model.getFeelDialect());
        DMNCompilerContext ctx = new DMNCompilerContext(feel);
        ctx.setRelativeResolver(relativeResolver);
        List<DMNModel> toMerge = new ArrayList<>();
        if (!dmndefs.getImport().isEmpty()) {
            for (Import i : dmndefs.getImport()) {
                if (ImportDMNResolverUtil.whichImportType(i) == ImportType.DMN) {
                    Either<String, DMNModel> resolvedResult = ImportDMNResolverUtil.resolveImportDMN(i, dmnModels, (DMNModel m) -> new QName(m.getNamespace(), m.getName()));
                    DMNModel located = resolvedResult.cata(msg -> {
                        MsgUtil.reportMessage(logger,
                                              DMNMessage.Severity.ERROR,
                                              i,
                                              model,
                                              null,
                                              null,
                                              Msg.IMPORT_NOT_FOUND_FOR_NODE,
                                              msg,
                                              i);
                        return null;
                    }, Function.identity());
                    if (located != null) {
                        String iAlias = Optional.ofNullable(i.getName()).orElse(located.getName());
                        // incubator-kie-issues#852: The idea is to not treat the anonymous models as import, but to "merge" them
                        //  with original one,
                        // because otherwise we would have to deal with clashing name aliases, or similar issues
                        if (iAlias != null && !iAlias.isEmpty()) {
                            model.setImportAliasForNS(iAlias, located.getNamespace(), located.getName());
                            importFromModel(model, located, iAlias);
                        } else {
                            toMerge.add(located);
                        }
                    }
                } else if (ImportDMNResolverUtil.whichImportType(i) == ImportType.PMML) {
                    processPMMLImport(model, i, relativeResolver);
                    model.setImportAliasForNS(i.getName(), i.getNamespace(), i.getName());
                } else {
                    MsgUtil.reportMessage(logger,
                                          DMNMessage.Severity.ERROR,
                                          null,
                                          model,
                                          null,
                                          null,
                                          Msg.IMPORT_TYPE_UNKNOWN,
                                          i.getImportType());
                }
            }
        }

        toMerge.forEach(mergedModel -> processMergedModel(model, (DMNModelImpl) mergedModel));
        processItemDefinitions(ctx, model, dmndefs);
        processDrgElements(ctx, model, dmndefs);
        return model;
    }

    private void processPMMLImport(DMNModelImpl model, Import i, Function<String, Reader> relativeResolver) {
        ClassLoader rootClassLoader = ((DMNCompilerConfigurationImpl) dmnCompilerConfig).getRootClassLoader();
        Resource relativeResource = resolveRelativeResource(rootClassLoader, model, i, i, relativeResolver);
        try (InputStream pmmlIS = relativeResource.getInputStream()) {
            DMNImportPMMLInfo.from(pmmlIS, (DMNCompilerConfigurationImpl) dmnCompilerConfig, model, i).consume(new PMMLImportErrConsumer(model, i),
                                                                                                               model::addPMMLImportInfo);
        } catch (IOException e) {
            new PMMLImportErrConsumer(model, i).accept(e);
        }
    }

    public static class PMMLImportErrConsumer implements Consumer<Exception> {

        private final DMNModelImpl model;
        private final Import i;
        private final DMNModelInstrumentedBase node;

        public PMMLImportErrConsumer(DMNModelImpl model, Import i) {
            this(model, i, i);
        }

        public PMMLImportErrConsumer(DMNModelImpl model, Import i, DMNModelInstrumentedBase node) {
            this.model = model;
            this.i = i;
            this.node = node;
        }

        @Override
        public void accept(Exception t) {
            logger.error("Unable to locate pmml model from locationURI {}.", i.getLocationURI(), t);
            MsgUtil.reportMessage(logger,
                                  DMNMessage.Severity.ERROR,
                                  i,
                                  model,
                                  null,
                                  null,
                                  Msg.FUNC_DEF_PMML_ERR_LOCATIONURI,
                                  i.getLocationURI());
        }

    }

    protected static Resource resolveRelativeResource(ClassLoader classLoader, DMNModelImpl model, Import i, DMNModelInstrumentedBase node, Function<String, Reader> relativeResolver) {
        if (relativeResolver != null) {
            Reader reader = relativeResolver.apply(i.getLocationURI());
            return ResourceFactory.newReaderResource(reader);
        } else if (model.getResource() != null) {
            return pmmlImportResource(classLoader, model, i, node);
        }
        throw new UnsupportedOperationException("Unable to determine relative Resource for import named: " + i.getName());
    }

    protected static Resource pmmlImportResource(ClassLoader classLoader, DMNModelImpl model, Import i, DMNModelInstrumentedBase node) {
        String locationURI = i.getLocationURI();
        logger.trace("locationURI: {}", locationURI);
        Resource pmmlResource = null;
        try {
            URI resolveRelativeURI = DMNCompilerImpl.resolveRelativeURI(model, locationURI);
            pmmlResource = resolveRelativeURI.isAbsolute() ?
                    ResourceFactory.newFileResource(resolveRelativeURI.getPath()) :
                    ResourceFactory.newClassPathResource(resolveRelativeURI.getPath(), classLoader);
        } catch (URISyntaxException | IOException e) {
            new PMMLImportErrConsumer(model, i, node).accept(e);
        }
        logger.trace("pmmlResource: {}", pmmlResource);
        return pmmlResource;
    }

    protected static URI resolveRelativeURI(DMNModelImpl model, String relative) throws URISyntaxException, IOException {
        URI relativeAsURI = new URI(null, null, relative, null);
        if (model.getResource() instanceof FileSystemResource) {
            FileSystemResource fsr = (FileSystemResource) model.getResource();
            logger.trace("fsr: {}", fsr.getURL());
            URI resolve = fsr.getURL().toURI().resolve(relativeAsURI);
            return resolve;
        } else {
            URI dmnModelURI = new URI(null, null, model.getResource().getSourcePath(), null);
            logger.trace("dmnModelURI: {}", dmnModelURI);
            URI relativeURI = dmnModelURI.resolve(relativeAsURI);
            return relativeURI;
        }
    }

    private void importFromModel(DMNModelImpl model, DMNModel m, String iAlias) {
        model.addImportChainChild(((DMNModelImpl) m).getImportChain(), iAlias);
        for (ItemDefNode idn : m.getItemDefinitions()) {
            model.getTypeRegistry().registerType(idn.getType());
        }
        for (InputDataNode idn : m.getInputs()) {
            model.addInput(idn);
        }
        for (BusinessKnowledgeModelNode bkm : m.getBusinessKnowledgeModels()) {
            model.addBusinessKnowledgeModel(bkm);
        }
        for (DecisionNode dn : m.getDecisions()) {
            model.addDecision(dn);
        }
        for (DecisionServiceNode dsn : m.getDecisionServices()) {
            model.addDecisionService(dsn);
        }
    }

    private void processItemDefinitions(DMNCompilerContext ctx, DMNModelImpl model, Definitions dmndefs) {
        dmndefs.normalize();
        
        List<ItemDefinition> ordered = new ItemDefinitionDependenciesSorter(model.getNamespace()).sort(dmndefs.getItemDefinition());
        
        Set<String> names = new HashSet<>();
        for (ItemDefinition id : ordered) {
            boolean added = names.add(id.getName());
            if (!added) {
                MsgUtil.reportMessage(logger,
                                      DMNMessage.Severity.ERROR,
                                      id,
                                      model,
                                      null,
                                      null,
                                      Msg.DUPLICATED_ITEM_DEFINITION,
                                      id.getName());
            }
            if (id.getItemComponent() != null && !id.getItemComponent().isEmpty()) {
                DMNCompilerHelper.checkVariableName(model, id, id.getName());
                CompositeTypeImpl compType = new CompositeTypeImpl(model.getNamespace(), id.getName(), id.getId(), id.isIsCollection());
                model.getTypeRegistry().registerType(compType);
            }
        }

        for ( ItemDefinition id : ordered ) {
            ItemDefNodeImpl idn = new ItemDefNodeImpl( id );
            DMNType type = buildTypeDef(ctx, model, idn, id, null);
            idn.setType( type );
            model.addItemDefinition( idn );
        }
    }

    private void processDrgElements(DMNCompilerContext ctx, DMNModelImpl model, Definitions dmndefs) {
        for ( DRGElement e : dmndefs.getDrgElement() ) {
            boolean foundIt = false;
            if (e.getId() == null) {
                e.setId(UUID.randomUUID().toString());
            }
            for( DRGElementCompiler dc : drgCompilers ) {
                if ( dc.accept( e ) ) {
                    foundIt = true;
                    dc.compileNode(e, this, model);
                }
            }  
            if ( !foundIt ) {
                MsgUtil.reportMessage( logger,
                                       DMNMessage.Severity.ERROR,
                                       e,
                                       model,
                                       null,
                                       null,
                                       Msg.UNSUPPORTED_ELEMENT,
                                       e.getClass().getSimpleName(),
                                       e.getId() );
            }
        }

        // in DMN v1.1 the DecisionService is not on the DRGElement but as an extension
        if (dmndefs.getExtensionElements() != null) {
            List<DecisionServices> decisionServices = dmndefs.getExtensionElements().getAny().stream().filter(DecisionServices.class::isInstance).map(DecisionServices.class::cast).collect(Collectors.toList());
            for (DecisionServices dss : decisionServices) {
                for (DecisionService ds : dss.getDecisionService()) {
                    // compatibility with DMN v1.1, create Decision Service's variable:
                    if (ds.getVariable() == null) {
                        InformationItem variable = new TInformationItem();
                        variable.setId(UUID.randomUUID().toString());
                        variable.setName(ds.getName());
                        variable.setParent(ds);
                        // the introduction of an on-the-fly ItemDefinition has been removed. The variable type will be evaluated as feel:any, or in v1.2 will receive the (user-defined, explicit) ItemDefinition type.
                        ds.setVariable(variable);
                    }
                    // continuing with normal compilation of Decision Service:
                    for (DRGElementCompiler dc : drgCompilers) {
                        if (dc.accept(ds)) {
                            dc.compileNode(ds, this, model);
                        }
                    }
                }
            }
        }
        for (DecisionServiceNode ds : model.getDecisionServices()) {
            DecisionServiceNodeImpl dsi = (DecisionServiceNodeImpl) ds;
            dsi.addModelImportAliases(model.getImportAliasesForNS());
            for (DRGElementCompiler dc : drgCompilers) {
                if (dsi.getEvaluator() == null && dc.accept(dsi)) { // will compile in fact all DS belonging to this model (not the imported ones).
                    dc.compileEvaluator(dsi, this, ctx, model);
                }
            }
        }

        for ( BusinessKnowledgeModelNode bkm : model.getBusinessKnowledgeModels() ) {
            BusinessKnowledgeModelNodeImpl bkmi = (BusinessKnowledgeModelNodeImpl) bkm;
            bkmi.addModelImportAliases(model.getImportAliasesForNS());
            for( DRGElementCompiler dc : drgCompilers ) {
                if ( bkmi.getEvaluator() == null && dc.accept( bkm ) ) {
                    dc.compileEvaluator(bkm, this, ctx, model);
                }
            }
        }

        for ( DecisionNode d : model.getDecisions() ) {
            DecisionNodeImpl di = (DecisionNodeImpl) d;
            di.addModelImportAliases(model.getImportAliasesForNS());
            for( DRGElementCompiler dc : drgCompilers ) {
                if ( di.getEvaluator() == null && dc.accept( d ) ) {
                    dc.compileEvaluator(d, this, ctx, model);
                }
            }
        }
        
        for (AfterProcessDrgElements callback : afterDRGcallbacks) {
            logger.debug("About to invoke callback: {}", callback);
            callback.callback(this, ctx, model);
        }
        
        detectCycles( model );


    }
    
    @FunctionalInterface
    public interface AfterProcessDrgElements {
        void callback(DMNCompilerImpl compiler, DMNCompilerContext ctx, DMNModelImpl model);
    }
    
    public void addCallback(AfterProcessDrgElements callback) {
        this.afterDRGcallbacks.add(callback);
    }

    private void detectCycles( DMNModelImpl model ) {
        /*
        Boolean.TRUE = node is either safe or already reported for having a cyclic dependency
        Boolean.FALSE = node is being checked at the moment
         */
        final Map<DecisionNodeImpl, Boolean> registry = new HashMap<>();
        for ( DecisionNode decision : model.getDecisions() ) {
            final DecisionNodeImpl decisionNode = (DecisionNodeImpl) decision;
            detectCycles( decisionNode, registry, model );
        }
    }

    private void detectCycles( DecisionNodeImpl node, Map<DecisionNodeImpl, Boolean> registry, DMNModelImpl model ) {
        if ( Boolean.TRUE.equals(registry.get( node ) ) ) return;
        if ( Boolean.FALSE.equals( registry.put( node, Boolean.FALSE ) ) ) {
            MsgUtil.reportMessage( logger,
                                   DMNMessage.Severity.ERROR,
                                   node.getSource(),
                                   model,
                                   null,
                                   null,
                                   Msg.CYCLIC_DEP_FOR_NODE,
                                   node.getName() );
            registry.put( node, Boolean.TRUE );
        }
        for ( DMNNode dependency : node.getDependencies().values() ) {
            if ( dependency instanceof DecisionNodeImpl ) {
                detectCycles( (DecisionNodeImpl) dependency, registry, model );
            }
        }
        registry.put( node, Boolean.TRUE );
    }


    public void linkRequirements(DMNModelImpl model, DMNBaseNode node) {
        for ( InformationRequirement ir : node.getInformationRequirement() ) {
            if ( ir.getRequiredInput() != null ) {
                String id = getId( ir.getRequiredInput() );
                InputDataNode input = model.getInputById( id );
                if ( input != null ) {
                    node.addDependency( input.getName(), input );
                } else {
                    MsgUtil.reportMessage( logger,
                                           DMNMessage.Severity.ERROR,
                                           ir.getRequiredInput(),
                                           model,
                                           null,
                                           null,
                                           Msg.REQ_INPUT_NOT_FOUND_FOR_NODE,
                                           id,
                                           node.getName() );
                }
            } else if ( ir.getRequiredDecision() != null ) {
                String id = getId( ir.getRequiredDecision() );
                DecisionNode dn = model.getDecisionById( id );
                if ( dn != null ) {
                    node.addDependency( dn.getName(), dn );
                } else {
                    MsgUtil.reportMessage( logger,
                                           DMNMessage.Severity.ERROR,
                                           ir.getRequiredDecision(),
                                           model,
                                           null,
                                           null,
                                           Msg.REQ_DECISION_NOT_FOUND_FOR_NODE,
                                           id,
                                           node.getName() );
                }
            }
        }
        for ( KnowledgeRequirement kr : node.getKnowledgeRequirement() ) {
            if ( kr.getRequiredKnowledge() != null ) {
                String id = getId( kr.getRequiredKnowledge() );
                BusinessKnowledgeModelNode bkmn = model.getBusinessKnowledgeModelById( id );
                DecisionServiceNode dsn = model.getDecisionServiceById(id);
                if ( bkmn != null ) {
                    node.addDependency( bkmn.getName(), bkmn );
                } else if (dsn != null) {
                    node.addDependency(dsn.getName(), dsn);
                } else {
                    MsgUtil.reportMessage( logger,
                                           DMNMessage.Severity.ERROR,
                                           kr.getRequiredKnowledge(),
                                           model,
                                           null,
                                           null,
                                          Msg.REQ_BKM_NOT_FOUND_FOR_NODE, // TODO or a DS ?
                                           id,
                                           node.getName() );
                }
            }
        }
    }

    /**
     * For the purpose of Compilation, in the DMNModel the DRGElements are stored with their full ID, so an ElementReference might reference in two forms:
     *  - #id (a local to the model ID)
     *  - namespace#id (an imported DRGElement ID)
     * This method now returns in the first case the proper ID, while leave unchanged in the latter case, in order for the ID to be reconciliable on the DMNModel. 
     */
    public static String getId(DMNElementReference er) {
        String href = er.getHref();
        if (href.startsWith("#")) {
            return href.substring(1);
        } else {
            Definitions rootElement = getRootElement(er);
            String toRemove = String.format("%s#", rootElement.getNamespace());
            return href.replace(toRemove, "");
        }
    }

    /**
     * Recursively navigate the given <code>DMNModelInstrumentedBase</code> until it gets to the root <code>Definitions</code> element.
     * it throws a <code>RuntimeException</code> if such element could not be found.
     *
     * @param toNavigate
     * @return
     * @throws RuntimeException
     */
    public static Definitions getRootElement(DMNModelInstrumentedBase toNavigate) {
        if ( toNavigate instanceof Definitions ) {
            return (Definitions) toNavigate;
        } else if ( toNavigate.getParent() != null ) {
            return getRootElement(toNavigate.getParent());
        } else {
            throw new RuntimeException("Failed to get Definitions parent for " + toNavigate);
        }
    }

    /**
     * @param topLevel null if it is a top level ItemDefinition
     */
    private DMNType buildTypeDef(DMNCompilerContext ctx, DMNModelImpl dmnModel, DMNNode node, ItemDefinition itemDef, DMNType topLevel) {
        BaseDMNTypeImpl type;
        if ( itemDef.getTypeRef() != null ) {
            // this is a reference to an existing type, so resolve the reference
            type = (BaseDMNTypeImpl) resolveTypeRef(dmnModel, itemDef, itemDef, itemDef.getTypeRef());
            if ( type != null ) {
                UnaryTests allowedValuesStr = itemDef.getAllowedValues();
                UnaryTests typeConstraintStr = itemDef.getTypeConstraint();

                // we only want to clone the type definition if it is a top level type (not a field in a composite type)
                // or if it changes the metadata for the base type
                if (topLevel == null || allowedValuesStr != null || typeConstraintStr != null || itemDef.isIsCollection() != type.isCollection()) {

                    // we have to clone this type definition into a new one
                    String name = itemDef.getName();
                    String namespace = dmnModel.getNamespace();
                    String id = itemDef.getId();
                    BaseDMNTypeImpl baseType = type;

                    Type baseFEELType = type.getFeelType();
                    if (baseFEELType instanceof BuiltInType) { // Then it is an ItemDefinition in place for "aliasing" a base FEEL type, for having type(itemDefname) I need to define its SimpleType.
                        baseFEELType = new AliasFEELType(itemDef.getName(), (BuiltInType) baseFEELType);
                    }

                    List<UnaryTest> allowedValues = getUnaryTests(allowedValuesStr, ctx, dmnModel, node, itemDef,
                                                                  Msg.ERR_COMPILING_ALLOWED_VALUES_LIST_ON_ITEM_DEF);
                    List<UnaryTest> typeConstraint = getUnaryTests(typeConstraintStr, ctx, dmnModel, node, itemDef,
                                                                   Msg.ERR_COMPILING_TYPE_CONSTRAINT_LIST_ON_ITEM_DEF);


                    boolean isCollection = itemDef.isIsCollection();
                    if (isCollection) {
                        baseFEELType = new GenListType(baseFEELType);
                    }

                    if (type instanceof CompositeTypeImpl) {
                        CompositeTypeImpl compositeTypeImpl = (CompositeTypeImpl) type;
                        type = new CompositeTypeImpl(namespace, name, id, isCollection, compositeTypeImpl.getFields(), baseType, baseFEELType);
                        if (allowedValues != null && !allowedValues.isEmpty()) {
                            type.setAllowedValues(allowedValues);
                        }
                        if (typeConstraint != null && !typeConstraint.isEmpty()) {
                            type.setTypeConstraint(typeConstraint);
                        }
                    } else if (type instanceof SimpleTypeImpl) {
                        type = new SimpleTypeImpl(namespace, name, id, isCollection, allowedValues, typeConstraint,
                                                  baseType, baseFEELType);
                    }
                    if (topLevel != null) {
                        type.setBelongingType(topLevel);
                    }
                }
                if (topLevel == null) {
                    DMNType registered = dmnModel.getTypeRegistry().registerType( type );
                    if( registered != type ) {
                        MsgUtil.reportMessage( logger,
                                               DMNMessage.Severity.ERROR,
                                               itemDef,
                                               dmnModel,
                                               null,
                                               null,
                                               Msg.DUPLICATED_ITEM_DEFINITION,
                                               itemDef.getName() );
                    }
                }
            }
        } else if (itemDef.getItemComponent() != null && itemDef.getItemComponent().size() > 0) {
            // this is a composite type
            // first, locate preregistered or create anonymous inner composite
            if (topLevel == null) {
                type = (CompositeTypeImpl) dmnModel.getTypeRegistry().resolveType(dmnModel.getNamespace(), itemDef.getName());
            } else {
                DMNCompilerHelper.checkVariableName( dmnModel, itemDef, itemDef.getName() );
                type = new CompositeTypeImpl(dmnModel.getNamespace(), itemDef.getName(), itemDef.getId(), itemDef.isIsCollection());
                type.setBelongingType(topLevel);
            }
            // second, add fields to located composite
            for (ItemDefinition fieldDef : itemDef.getItemComponent()) {
                DMNCompilerHelper.checkVariableName(dmnModel, fieldDef, fieldDef.getName());
                DMNType fieldType = buildTypeDef(ctx, dmnModel, node, fieldDef, type);
                fieldType = fieldType != null ? fieldType : dmnModel.getTypeRegistry().unknown();
                ((CompositeTypeImpl) type).addField(fieldDef.getName(), fieldType);
            }
        } else if (isFunctionItem(itemDef)) {
            FunctionItem fi = itemDef.getFunctionItem();
            String name = itemDef.getName();
            String namespace = dmnModel.getNamespace();
            String id = itemDef.getId();
            Map<String, DMNType> params = new HashMap<>();
            for (InformationItem p : fi.getParameters()) {
                DMNType resolveTypeRef = resolveTypeRef(dmnModel, itemDef, itemDef, p.getTypeRef());
                params.put(p.getName(), resolveTypeRef);
            }
            DMNType returnType = resolveTypeRef(dmnModel, itemDef, itemDef, fi.getOutputTypeRef());
            List<Type> feelPs = fi.getParameters().stream().map(InformationItem::getName).map(n -> ((BaseDMNTypeImpl) params.get(n)).getFeelType()).collect(Collectors.toList());
            GenFnType feeltype = new GenFnType(feelPs, ((BaseDMNTypeImpl) returnType).getFeelType());
            type = new SimpleFnTypeImpl(namespace, name, id, feeltype, params, returnType, fi);
            DMNType registered = dmnModel.getTypeRegistry().registerType(type);
            if (registered != type) {
                MsgUtil.reportMessage(logger,
                                      DMNMessage.Severity.ERROR,
                                      itemDef,
                                      dmnModel,
                                      null,
                                      null,
                                      Msg.DUPLICATED_ITEM_DEFINITION,
                                      itemDef.getName());
            }
        } else {
            BaseDMNTypeImpl unknown = (BaseDMNTypeImpl) resolveTypeRef(dmnModel, itemDef, itemDef, null);
            type = new SimpleTypeImpl(dmnModel.getNamespace(), itemDef.getName(), itemDef.getId(),
                                      itemDef.isIsCollection(), null, null, unknown, unknown.getFeelType());
            if (topLevel == null) {
                DMNType registered = dmnModel.getTypeRegistry().registerType(type);
                if (registered != type) {
                    MsgUtil.reportMessage(logger,
                                          DMNMessage.Severity.ERROR,
                                          itemDef,
                                          dmnModel,
                                          null,
                                          null,
                                          Msg.DUPLICATED_ITEM_DEFINITION,
                                          itemDef.getName());
                }
            } else {
                type.setBelongingType(topLevel);
            }
        }
        return type;
    }

    private List<UnaryTest> getUnaryTests(UnaryTests unaryTestsToRead, DMNCompilerContext ctx, DMNModelImpl dmnModel,
                                          DMNNode node, ItemDefinition itemDef, Msg.Message2 message) {
        List<UnaryTest> toReturn = null;
        if (unaryTestsToRead != null) {
            toReturn = ctx.getFeelHelper().evaluateUnaryTests(
                    ctx,
                    unaryTestsToRead.getText(),
                    dmnModel,
                    itemDef,
                    message,
                    unaryTestsToRead.getText(),
                    node.getName()
            );
        }
        return toReturn;
    }

    private static boolean isFunctionItem(ItemDefinition itemDef) {
        return !(itemDef instanceof org.kie.dmn.model.v1_1.KieDMNModelInstrumentedBase) && !(itemDef instanceof org.kie.dmn.model.v1_2.KieDMNModelInstrumentedBase) && itemDef.getFunctionItem() != null;
    }

    /**
     * Resolve the QName typeRef accordingly to definition of builtin (FEEL) types, local model ItemDef or imported definitions.
     * If the typeRef cannot be resolved, (FEEL) UNKNOWN is returned and an error logged using standard DMN message logging. 
     */
    public DMNType resolveTypeRef(DMNModelImpl dmnModel, NamedElement model, DMNModelInstrumentedBase localElement, QName typeRef) {
        if ( typeRef != null ) {
            QName nsAndName = NamespaceUtil.getNamespaceAndName(localElement, dmnModel.getImportAliasesForNS(), typeRef, dmnModel.getNamespace());

            DMNType type = dmnModel.getTypeRegistry().resolveType(nsAndName.getNamespaceURI(), nsAndName.getLocalPart());
            if (type == null && localElement.getURIFEEL().equals(nsAndName.getNamespaceURI())) {
                if ( model instanceof Decision && ((Decision) model).getExpression() instanceof DecisionTable ) {
                    DecisionTable dt = (DecisionTable) ((Decision) model).getExpression();
                    if ( dt.getOutput().size() > 1 ) {
                        // implicitly define a type for the decision table result
                        CompositeTypeImpl compType = new CompositeTypeImpl( dmnModel.getNamespace(), model.getName()+"_Type", model.getId(), dt.getHitPolicy().isMultiHit() );
                        for ( OutputClause oc : dt.getOutput() ) {
                            DMNType fieldType = resolveTypeRef(dmnModel, model, oc, oc.getTypeRef());
                            compType.addField( oc.getName(), fieldType );
                        }
                        dmnModel.getTypeRegistry().registerType( compType );
                        return compType;
                    } else if ( dt.getOutput().size() == 1 ) {
                        return resolveTypeRef(dmnModel, model, dt.getOutput().get(0), dt.getOutput().get(0).getTypeRef());
                    }
                }
            } else if( type == null ) {
                MsgUtil.reportMessage( logger,
                                       DMNMessage.Severity.ERROR,
                                       localElement,
                                       dmnModel,
                                       null,
                                       null,
                                       Msg.UNKNOWN_TYPE_REF_ON_NODE,
                                       typeRef.toString(),
                                       localElement.getParentDRDElement().getIdentifierString() );
                type = dmnModel.getTypeRegistry().unknown();
            }
            return type;
        }
        return dmnModel.getTypeRegistry().unknown();
    }

    private static QName parseQNameString(String qns) {
        if (qns != null) {
            Matcher m = QNAME_PAT.matcher(qns);
            if (m.matches()) {
                if (m.group(4) != null) {
                    return new QName(m.group(2), m.group(5), m.group(4));
                } else {
                    return new QName(m.group(2), m.group(5));
                }
            } else {
                return new QName(qns);
            }
        } else {
            return null;
        }
    }
    
    /**
     * Internal utilities for new Model exposing typeRef as a String and no longer a XML QName
     */
    DMNType resolveTypeRefUsingString(DMNModelImpl dmnModel, NamedElement model, DMNModelInstrumentedBase localElement, String typeRef) {
    	return resolveTypeRef(dmnModel, model, localElement, parseQNameString(typeRef));
    }

    public DMNCompilerConfiguration getDmnCompilerConfig() {
        return this.dmnCompilerConfig;
    }

    public List<DMNExtensionRegister> getRegisteredExtensions() {
        if ( this.dmnCompilerConfig == null ) {
            return Collections.emptyList();
        } else {
            return this.dmnCompilerConfig.getRegisteredExtensions();
        }
    }
    
    public DMNDecisionLogicCompiler getEvaluatorCompiler() {
        return evaluatorCompiler;
    }
    
}