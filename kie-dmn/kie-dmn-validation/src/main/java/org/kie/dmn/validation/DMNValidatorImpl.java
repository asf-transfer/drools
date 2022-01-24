/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
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

package org.kie.dmn.validation;

import static java.util.stream.Collectors.toList;
import static org.kie.dmn.validation.DMNValidator.Validation.ANALYZE_DECISION_TABLE;
import static org.kie.dmn.validation.DMNValidator.Validation.VALIDATE_COMPILATION;
import static org.kie.dmn.validation.DMNValidator.Validation.VALIDATE_MODEL;
import static org.kie.dmn.validation.DMNValidator.Validation.VALIDATE_SCHEMA;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.drools.core.io.impl.FileSystemResource;
import org.drools.core.io.impl.ReaderResource;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.modelcompiler.builder.KieBaseBuilder;
import org.kie.api.command.BatchExecutionCommand;
import org.kie.api.io.Resource;
import org.kie.api.runtime.StatelessKieSession;
import org.kie.dmn.api.core.DMNCompiler;
import org.kie.dmn.api.core.DMNCompilerConfiguration;
import org.kie.dmn.api.core.DMNMessage;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.backend.marshalling.v1x.DMNMarshallerFactory;
import org.kie.dmn.backend.marshalling.v1x.XStreamMarshaller;
import org.kie.dmn.backend.marshalling.v1x.XStreamMarshaller.DMN_VERSION;
import org.kie.dmn.core.api.DMNFactory;
import org.kie.dmn.core.api.DMNMessageManager;
import org.kie.dmn.core.assembler.DMNAssemblerService;
import org.kie.dmn.core.assembler.DMNResource;
import org.kie.dmn.core.assembler.DMNResourceDependenciesSorter;
import org.kie.dmn.core.compiler.DMNCompilerConfigurationImpl;
import org.kie.dmn.core.compiler.DMNCompilerImpl;
import org.kie.dmn.core.compiler.DMNProfile;
import org.kie.dmn.core.impl.DMNMessageImpl;
import org.kie.dmn.core.util.DefaultDMNMessagesManager;
import org.kie.dmn.core.util.Msg;
import org.kie.dmn.core.util.MsgUtil;
import org.kie.dmn.feel.util.ClassLoaderUtil;
import org.kie.dmn.model.api.DMNModelInstrumentedBase;
import org.kie.dmn.model.api.DecisionService;
import org.kie.dmn.model.api.Definitions;
import org.kie.dmn.validation.dtanalysis.InternalDMNDTAnalyser;
import org.kie.dmn.validation.dtanalysis.InternalDMNDTAnalyserFactory;
import org.kie.dmn.validation.dtanalysis.model.DTAnalysis;
import org.kie.internal.command.CommandFactory;
import org.kie.internal.io.ResourceWithConfigurationImpl;
import org.kie.internal.utils.ChainedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

public class DMNValidatorImpl implements DMNValidator {
    public static final Logger LOG = LoggerFactory.getLogger(DMNValidatorImpl.class);
    static final Schema schemav1_1;
    static {
        try {
            schemav1_1 = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                                  .newSchema(new StreamSource(DMNValidatorImpl.class.getResourceAsStream("org/omg/spec/DMN/20151101/dmn.xsd")));
        } catch (SAXException e) {
            throw new RuntimeException("Unable to initialize correctly DMNValidator.", e);
        }
    }
    static final Schema schemav1_2;
    static {
        try {
            schemav1_2 = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                                      .newSchema(new Source[]{new StreamSource(DMNValidatorImpl.class.getResourceAsStream("org/omg/spec/DMN/20180521/DC.xsd")),
                                                              new StreamSource(DMNValidatorImpl.class.getResourceAsStream("org/omg/spec/DMN/20180521/DI.xsd")),
                                                              new StreamSource(DMNValidatorImpl.class.getResourceAsStream("org/omg/spec/DMN/20180521/DMNDI12.xsd")),
                                                              new StreamSource(DMNValidatorImpl.class.getResourceAsStream("org/omg/spec/DMN/20180521/DMN12.xsd"))
                                      });
        } catch (SAXException e) {
            throw new RuntimeException("Unable to initialize correctly DMNValidator.", e);
        }
    }
    static final Schema schemav1_3;
    static {
        try {
            schemav1_3 = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                                      .newSchema(new Source[]{new StreamSource(DMNValidatorImpl.class.getResourceAsStream("org/omg/spec/DMN/20191111/DC.xsd")),
                                                              new StreamSource(DMNValidatorImpl.class.getResourceAsStream("org/omg/spec/DMN/20191111/DI.xsd")),
                                                              new StreamSource(DMNValidatorImpl.class.getResourceAsStream("org/omg/spec/DMN/20191111/DMNDI13.xsd")),
                                                              new StreamSource(DMNValidatorImpl.class.getResourceAsStream("org/omg/spec/DMN/20191111/DMN13.xsd"))
                                      });
        } catch (SAXException e) {
            throw new RuntimeException("Unable to initialize correctly DMNValidator.", e);
        }
    }
    static final Schema schemav1_4;
    static {
        try {
            schemav1_4 = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                                      .newSchema(new Source[]{new StreamSource(DMNValidatorImpl.class.getResourceAsStream("org/omg/spec/DMN/20211108/DC.xsd")),
                                                              new StreamSource(DMNValidatorImpl.class.getResourceAsStream("org/omg/spec/DMN/20211108/DI.xsd")),
                                                              new StreamSource(DMNValidatorImpl.class.getResourceAsStream("org/omg/spec/DMN/20211108/DMNDI13.xsd")),
                                                              new StreamSource(DMNValidatorImpl.class.getResourceAsStream("org/omg/spec/DMN/20211108/DMN14.xsd"))
                                      });
        } catch (SAXException e) {
            throw new RuntimeException("Unable to initialize correctly DMNValidator.", e);
        }
    }

    private Schema overrideSchema = null;

    /**
     * Collect at init time the runtime issues which prevented to build the `kieContainer` correctly.
     */
    private List<DMNMessage> failedInitMsg = new ArrayList<>();

    private final List<DMNProfile> dmnProfiles = new ArrayList<>();
    private final DMNCompilerConfiguration dmnCompilerConfig;

    private final InternalDMNDTAnalyser dmnDTValidator;
    private InternalKnowledgeBase kb11;
    private InternalKnowledgeBase kb12;

    public DMNValidatorImpl(ClassLoader cl, List<DMNProfile> dmnProfiles, Properties p) {
        kb11 = KieBaseBuilder.createKieBaseFromModel(Arrays.asList(org.kie.dmn.validation.bootstrap.ValidationBootstrapModels.V1X_MODEL,
                                                                   org.kie.dmn.validation.bootstrap.ValidationBootstrapModels.V11_MODEL));
        kb12 = KieBaseBuilder.createKieBaseFromModel(Arrays.asList(org.kie.dmn.validation.bootstrap.ValidationBootstrapModels.V1X_MODEL,
                                                                   org.kie.dmn.validation.bootstrap.ValidationBootstrapModels.V12_MODEL));
        ChainedProperties localChainedProperties = new ChainedProperties();
        if (p != null) {
            localChainedProperties.addProperties(p);
        }
        this.dmnProfiles.addAll(DMNAssemblerService.getDefaultDMNProfiles(localChainedProperties));
        this.dmnProfiles.addAll(dmnProfiles);
        final ClassLoader classLoader = cl == null ? ClassLoaderUtil.findDefaultClassLoader() : cl;
        DMNCompilerConfigurationImpl dmnCompilerConfiguration = DMNAssemblerService.compilerConfigWithKModulePrefs(classLoader,
                                                                                                                   localChainedProperties,
                                                                                                                   this.dmnProfiles,
                                                                                                                   (DMNCompilerConfigurationImpl) DMNFactory.newCompilerConfiguration());
        try {
            DMNAssemblerService.applyDecisionLogicCompilerFactory(classLoader, dmnCompilerConfiguration);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to initialize DMNCompiler decisionlogicCompilerFactory based on parameters provided", e);
        }
        this.dmnCompilerConfig = dmnCompilerConfiguration;
        dmnDTValidator = InternalDMNDTAnalyserFactory.newDMNDTAnalyser(this.dmnProfiles);
    }

    @Override
    public void dispose() {
        // since exec model, no more kieContainer to dispose
    }

    public static class ValidatorBuilderImpl implements ValidatorBuilder {


        private final EnumSet<Validation> flags;
        private final DMNValidatorImpl validator;
        private ValidatorImportReaderResolver importResolver;

        public ValidatorBuilderImpl(DMNValidatorImpl dmnValidatorImpl, Validation[] options) {
            this.validator = dmnValidatorImpl;
            this.flags = EnumSet.copyOf(Arrays.asList(options));
        }

        @Override
        public ValidatorBuilder usingImports(ValidatorImportReaderResolver r) {
            this.importResolver = r;
            return this;
        }

        @Override
        public ValidatorBuilder usingSchema(Schema r) {
            validator.setOverrideSchema(r);
            return this;
        }

        @Override
        public List<DMNMessage> theseModels(File... files) {
            DMNMessageManager results = new DefaultDMNMessagesManager( null );
            try {
                Reader[] readers = new Reader[files.length];
                for (int i = 0; i < files.length; i++) {
                    readers[i] = new FileReader(files[i]);
                }
                results.addAll(theseModels(readers));
            } catch (Throwable t) {
                MsgUtil.reportMessage(LOG,
                                      DMNMessage.Severity.ERROR,
                                      null,
                                      results,
                                      t,
                                      null,
                                      Msg.VALIDATION_RUNTIME_PROBLEM,
                                      t.getMessage());
            }
            return results.getMessages();
        }
        
        public List<DMNMessage> theseModels(Resource... resources) {
            DMNMessageManager results = new DefaultDMNMessagesManager( null );
            List<DMNResource> models = new ArrayList<>();
            for (Resource r : resources) {
            	try {
                	// We get passed a Resource, which might be constructed from a Reader, so we have only 1-time opportunity to be sure to read it successfully,
                	// we internalize the content:
                    String content = readContent( r.getReader() );
                    if (flags.contains(VALIDATE_SCHEMA)) {
                    	results.addAll(validator.validateSchema( content, null ));
                    }
                    Definitions dmndefs = unmarshalDefinitionsFromReader(validator.dmnCompilerConfig, new StringReader(content));
                    models.add(new DMNResource(dmndefs, new ResourceWithConfigurationImpl(r, null, x -> {}, y -> {})));
                } catch (Exception t) {
                    MsgUtil.reportMessage(LOG,
                                          DMNMessage.Severity.ERROR,
                                          null,
                                          results,
                                          t,
                                          null,
                                          Msg.VALIDATION_RUNTIME_PROBLEM,
                                          t.getMessage());
                }
            }
            if (flags.contains(VALIDATE_MODEL) || flags.contains(VALIDATE_COMPILATION) || flags.contains(ANALYZE_DECISION_TABLE)) {
                if (results.hasErrors()) {
                    MsgUtil.reportMessage(LOG,
                                          DMNMessage.Severity.ERROR,
                                          null,
                                          results,
                                          null,
                                          null,
                                          Msg.VALIDATION_STOPPED);
                    return results.getMessages();
                }
                DMNAssemblerService.enrichDMNResourcesWithImportsDependencies(models, Collections.emptyList());
                models = DMNResourceDependenciesSorter.sort(models);
                // TODO validateDefinitions(models, results);
            }
            return results.getMessages();
        }

        @Override
        public List<DMNMessage> theseModels(Reader... readers) {
            DMNMessageManager results = new DefaultDMNMessagesManager( null );
            List<Definitions> models = new ArrayList<>();
            for (Reader reader : readers) {
                try {
                    String content = readContent(reader);
                    if (flags.contains(VALIDATE_SCHEMA)) {
                        results.addAll(validator.validateSchema( content, null ));
                    }
                    Definitions dmndefs = unmarshalDefinitionsFromReader(validator.dmnCompilerConfig, new StringReader(content));
                    models.add(dmndefs);
                } catch (Exception t) {
                    MsgUtil.reportMessage(LOG,
                                          DMNMessage.Severity.ERROR,
                                          null,
                                          results,
                                          t,
                                          null,
                                          Msg.VALIDATION_RUNTIME_PROBLEM,
                                          t.getMessage());
                }
            }
            if (flags.contains(VALIDATE_MODEL) || flags.contains(VALIDATE_COMPILATION) || flags.contains(ANALYZE_DECISION_TABLE)) {
                if (results.hasErrors()) {
                    MsgUtil.reportMessage(LOG,
                                          DMNMessage.Severity.ERROR,
                                          null,
                                          results,
                                          null,
                                          null,
                                          Msg.VALIDATION_STOPPED);
                    return results.getMessages();
                }
                validateDefinitions(internalValidatorSortModels(models), results);
            }
            return results.getMessages();
        }

        @Override
        public List<DMNMessage> theseModels(Definitions... models) {
            DMNMessageManager results = new DefaultDMNMessagesManager( null );
            if (flags.contains(VALIDATE_SCHEMA)) {
                MsgUtil.reportMessage(LOG,
                                      DMNMessage.Severity.ERROR,
                                      null,
                                      results,
                                      null,
                                      null,
                                      Msg.FAILED_NO_XML_SOURCE);
            }
            if (flags.contains(VALIDATE_MODEL) || flags.contains(VALIDATE_COMPILATION) || flags.contains(ANALYZE_DECISION_TABLE)) {
                if (results.hasErrors()) {
                    MsgUtil.reportMessage(LOG,
                                          DMNMessage.Severity.ERROR,
                                          null,
                                          results,
                                          null,
                                          null,
                                          Msg.VALIDATION_STOPPED);
                    return results.getMessages();
                }
                validateDefinitions(internalValidatorSortModels(Arrays.asList(models)), results);
            }
            return results.getMessages();
        }

        private void validateDefinitions(List<Definitions> definitions, DMNMessageManager results) {
            List<Definitions> otherModel_Definitions = new ArrayList<>();
            List<DMNModel> otherModel_DMNModels = new ArrayList<>();
            for (Definitions dmnModel : definitions) {
                try {
                    if (flags.contains(VALIDATE_MODEL)) {
                        results.addAll(validator.validateModel(dmnModel, otherModel_Definitions));
                        otherModel_Definitions.add(dmnModel);
                    }
                    if (flags.contains(VALIDATE_COMPILATION) || flags.contains(ANALYZE_DECISION_TABLE)) {
                        DMNCompilerImpl compiler = new DMNCompilerImpl(validator.dmnCompilerConfig);
                        Function<String, Reader> relativeResolver = null;
                        if (importResolver != null) {
                            relativeResolver = locationURI -> importResolver.newReader(dmnModel.getNamespace(),
                                                                                       dmnModel.getName(),
                                                                                       locationURI);
                        }
                        DMNModel model = compiler.compile(dmnModel,
                                                          otherModel_DMNModels,
                                                          null,
                                                          relativeResolver);
                        if (model != null) {
                            results.addAll(model.getMessages());
                            otherModel_DMNModels.add(model);
                            if (flags.contains(ANALYZE_DECISION_TABLE)) {
                                List<DTAnalysis> vs = validator.dmnDTValidator.analyse(model, flags);
                                List<DMNMessage> dtAnalysisResults = vs.stream().flatMap(a -> a.asDMNMessages().stream()).collect(Collectors.toList());
                                results.addAllUnfiltered(dtAnalysisResults);
                            }
                        } else {
                            throw new IllegalStateException("Compiled model is null!");
                        }
                    }
                } catch (Throwable t) {
                    MsgUtil.reportMessage(LOG,
                                          DMNMessage.Severity.ERROR,
                                          null,
                                          results,
                                          t,
                                          null,
                                          Msg.VALIDATION_RUNTIME_PROBLEM,
                                          t.getMessage());
                }
            }
        }

        private List<Definitions> internalValidatorSortModels(List<Definitions> ms) {
            List<DMNResource> dmnResources = ms.stream().map(d -> new DMNResource(new QName(d.getNamespace(), d.getName()), null, d)).collect(Collectors.toList());
            DMNAssemblerService.enrichDMNResourcesWithImportsDependencies(dmnResources, Collections.emptyList());
            List<DMNResource> sortedDmnResources = DMNResourceDependenciesSorter.sort(dmnResources);
            return sortedDmnResources.stream().map(d -> d.getDefinitions()).collect(Collectors.toList());
        }
    }

    public Schema getOverrideSchema() {
        return overrideSchema;
    }

    public void setOverrideSchema(Schema overrideSchema) {
        this.overrideSchema = overrideSchema;
    }

    public ValidatorBuilder validateUsing(Validation... options) {
        return new ValidatorBuilderImpl(this, options);
    }

    @Override
    public List<DMNMessage> validate(Definitions dmnModel) {
        return validate( dmnModel, VALIDATE_MODEL );
    }

    @Override
    public List<DMNMessage> validate(Definitions dmnModel, Validation... options) {
        DMNMessageManager results = new DefaultDMNMessagesManager( null );
        EnumSet<Validation> flags = EnumSet.copyOf( Arrays.asList( options ) );
        if( flags.contains( VALIDATE_SCHEMA ) ) {
            MsgUtil.reportMessage( LOG,
                                   DMNMessage.Severity.ERROR,
                                   dmnModel,
                                   results,
                                   null,
                                   null,
                                   Msg.FAILED_NO_XML_SOURCE );
        }
        try {
            validateModelCompilation( dmnModel, results, flags );
        } catch ( Throwable t ) {
            MsgUtil.reportMessage(LOG,
                                  DMNMessage.Severity.ERROR,
                                  null,
                                  results,
                                  t,
                                  null,
                                  Msg.VALIDATION_RUNTIME_PROBLEM,
                                  t.getMessage());
        }
        return results.getMessages();
    }

    @Override
    public List<DMNMessage> validate(File xmlFile) {
        return validate( xmlFile, VALIDATE_MODEL );
    }

    @Override
    public List<DMNMessage> validate(File xmlFile, Validation... options) {
        return validate(new FileSystemResource(xmlFile), options);
    }

    @Override
    public List<DMNMessage> validate(Reader reader) {
        return validate( reader, VALIDATE_MODEL );
    }

    @Override
    public List<DMNMessage> validate(Reader reader, Validation... options) {
        return validate(new ReaderResource(reader), options);
    }
    
    public List<DMNMessage> validate(Resource resource) {
        return validate( resource, VALIDATE_MODEL );
    }
    
    public List<DMNMessage> validate(Resource resource, Validation... options) {
        DMNMessageManager results = new DefaultDMNMessagesManager( resource );
        EnumSet<Validation> flags = EnumSet.copyOf( Arrays.asList( options ) );
        try {
            // We get passed a Resource, which might be constructed from a Reader, so we have only 1-time opportunity to be sure to read it successfully,
            // we internalize the content:
            String content = readContent( resource.getReader() );
            if( flags.contains( VALIDATE_SCHEMA ) ) {
                results.addAll( validateSchema( content, resource.getSourcePath() ) );
            }
            if( flags.contains( VALIDATE_MODEL ) || flags.contains( VALIDATE_COMPILATION ) || flags.contains( ANALYZE_DECISION_TABLE ) ) {
                DMNResource dmnResource = unmarshallDMNResource(dmnCompilerConfig, resource, content);
                validateModelCompilation( dmnResource, results, flags );
            }
        } catch ( Throwable t ) {
            MsgUtil.reportMessage(LOG,
                                  DMNMessage.Severity.ERROR,
                                  null,
                                  results,
                                  t,
                                  null,
                                  Msg.VALIDATION_RUNTIME_PROBLEM,
                                  t.getMessage());
        }
        return results.getMessages();
    }

    private static String readContent(Reader reader)
            throws IOException {
        char[] b = new char[32 * 1024];
        StringBuilder content = new StringBuilder(  );
        int chars = -1;
        while( (chars = reader.read( b ) ) > 0 ) {
            content.append( b, 0, chars );
        }
        return content.toString();
    }

    private static DMNResource unmarshallDMNResource(DMNCompilerConfiguration config, Resource resource, String content) {
        // TODO KOGITO-6145
        Definitions dmndefs = DMNMarshallerFactory.newMarshallerWithExtensions(config.getRegisteredExtensions()).unmarshal(content);
        dmndefs.normalize();
        return new DMNResource(dmndefs, new ResourceWithConfigurationImpl(resource, null, x -> {}, y -> {}));
    }
    
    private void validateModelCompilation(DMNResource dmnModel, DMNMessageManager results, EnumSet<Validation> flags) {
        if( flags.contains( VALIDATE_MODEL ) ) {
            results.addAll(validateModel(dmnModel, Collections.emptyList()));
        }
        if( flags.contains( VALIDATE_COMPILATION ) ) {
            results.addAll( validateCompilation( dmnModel ) );
        }
        if (flags.contains( ANALYZE_DECISION_TABLE )) {
            results.addAllUnfiltered(analyseDT(dmnModel, flags));
        }
    }
    
    @Deprecated
    private static Definitions unmarshalDefinitionsFromReader(DMNCompilerConfiguration config, Reader reader) {
        Definitions dmndefs = DMNMarshallerFactory.newMarshallerWithExtensions(config.getRegisteredExtensions()).unmarshal(reader);
        dmndefs.normalize();
        return dmndefs;
    }

    @Deprecated
    private void validateModelCompilation(Definitions dmnModel, DMNMessageManager results, EnumSet<Validation> flags) {
        if( flags.contains( VALIDATE_MODEL ) ) {
            results.addAll(validateModel(dmnModel, Collections.emptyList()));
        }
        if( flags.contains( VALIDATE_COMPILATION ) ) {
            results.addAll( validateCompilation( dmnModel ) );
        }
        if (flags.contains( ANALYZE_DECISION_TABLE )) {
            results.addAllUnfiltered(analyseDT(dmnModel, flags));
        }
    }

    private List<DMNMessage> validateSchema(String xml, String path) {
        List<DMNMessage> problems = new ArrayList<>();
        try {
            DMN_VERSION inferDMNVersion = XStreamMarshaller.inferDMNVersion(new StringReader(xml));
            Schema usingSchema = determineSchema(inferDMNVersion);
            Source s = new StreamSource(new StringReader(xml));
            validateSchema(s, usingSchema);
        } catch (Exception e) {
            problems.add(new DMNMessageImpl(DMNMessage.Severity.ERROR, MsgUtil.createMessage(Msg.FAILED_XML_VALIDATION, e.getMessage()), Msg.FAILED_XML_VALIDATION.getType(), null, e).withPath(path));
        }
        return problems;
    }
    
    private Schema determineSchema(DMN_VERSION dmnVersion) {
        if (overrideSchema != null) {
            return overrideSchema;
        }
        switch (dmnVersion) {
            case DMN_v1_1:
                return schemav1_1;
            case DMN_v1_2:
                return schemav1_2;
            case DMN_v1_3:
                return schemav1_3;
            case DMN_v1_4:
            case UNKNOWN:
            default:
                return schemav1_4;
        }
    }

    private void validateSchema(Source s, Schema using) throws SAXException, IOException {
        Validator validator = using.newValidator();
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        validator.validate(s);
    }
    
    private List<DMNMessage> validateModel(DMNResource mainModel, List<DMNResource> otherModels) {
        Definitions mainDefinitions = mainModel.getDefinitions();
        StatelessKieSession kieSession = mainDefinitions instanceof org.kie.dmn.model.v1_1.KieDMNModelInstrumentedBase ? kb11.newStatelessKieSession() : kb12.newStatelessKieSession();
        MessageReporter reporter = new MessageReporter(mainModel);
        kieSession.setGlobal( "reporter", reporter );

        // exclude dynamicDecisionService for validation
        List<DMNModelInstrumentedBase> dmnModelElements = allChildren(mainDefinitions)
                       .filter(d -> !(d instanceof DecisionService &&
                               Boolean.parseBoolean(d.getAdditionalAttributes().get(new QName("http://www.trisotech.com/2015/triso/modeling", "dynamicDecisionService")))))
                       .collect(toList());
        List<Definitions> otherModel_Definitions = otherModels.stream().map(DMNResource::getDefinitions).collect(Collectors.toList());
        BatchExecutionCommand batch = CommandFactory.newBatchExecution(Arrays.asList(CommandFactory.newInsertElements(dmnModelElements, "DEFAULT", false, "DEFAULT"),
                                                                                     CommandFactory.newInsertElements(otherModel_Definitions, "DMNImports", false, "DMNImports")));
        kieSession.execute(batch);

        return reporter.getMessages().getMessages();
    }

    @Deprecated
    private List<DMNMessage> validateModel(Definitions dmnModel, List<Definitions> otherModel_Definitions) {
        StatelessKieSession kieSession = dmnModel instanceof org.kie.dmn.model.v1_1.KieDMNModelInstrumentedBase ? kb11.newStatelessKieSession() : kb12.newStatelessKieSession();
        MessageReporter reporter = new MessageReporter(null);
        kieSession.setGlobal( "reporter", reporter );

        // exclude dynamicDecisionService for validation
        List<DMNModelInstrumentedBase> dmnModelElements = allChildren(dmnModel)
                       .filter(d -> !(d instanceof DecisionService &&
                               Boolean.parseBoolean(d.getAdditionalAttributes().get(new QName("http://www.trisotech.com/2015/triso/modeling", "dynamicDecisionService")))))
                       .collect(toList());
        BatchExecutionCommand batch = CommandFactory.newBatchExecution(Arrays.asList(CommandFactory.newInsertElements(dmnModelElements, "DEFAULT", false, "DEFAULT"),
                                                                                     CommandFactory.newInsertElements(otherModel_Definitions, "DMNImports", false, "DMNImports")));
        kieSession.execute(batch);

        return reporter.getMessages().getMessages();
    }

    private List<DMNMessage> validateCompilation(DMNResource dmnModel) {
        if( dmnModel != null ) {
            DMNCompiler compiler = new DMNCompilerImpl(dmnCompilerConfig);
            DMNModel model = compiler.compile( dmnModel.getResAndConfig().getResource() );
            if( model != null ) {
                return model.getMessages();
            } else {
                throw new IllegalStateException("Compiled model is null!");
            }
        }
        return Collections.emptyList();
    }
    
    @Deprecated
    private List<DMNMessage> validateCompilation(Definitions dmnModel) {
        if( dmnModel != null ) {
            DMNCompiler compiler = new DMNCompilerImpl(dmnCompilerConfig);
            DMNModel model = compiler.compile( dmnModel );
            if( model != null ) {
                return model.getMessages();
            } else {
                throw new IllegalStateException("Compiled model is null!");
            }
        }
        return Collections.emptyList();
    }

    private List<DMNMessage> analyseDT(DMNResource dmnModel, Set<Validation> flags) {
        if (dmnModel != null) {
            DMNCompilerImpl compiler = new DMNCompilerImpl(dmnCompilerConfig);
            DMNModel model = compiler.compile( dmnModel.getResAndConfig().getResource() );
            if (model != null) {
                List<DTAnalysis> vs = dmnDTValidator.analyse(model, flags);
                String path = dmnModel.getResAndConfig().getResource().getSourcePath();
                List<DMNMessage> results = vs.stream().flatMap(a -> a.asDMNMessages().stream()).map(m -> ((DMNMessageImpl) m).withPath(path)).collect(Collectors.toList());
                return results;
            } else {
                throw new IllegalStateException("Compiled model is null!");
            }
        }
        return Collections.emptyList();
    }
    
    @Deprecated
    private List<DMNMessage> analyseDT(Definitions dmnModel, Set<Validation> flags) {
        if (dmnModel != null) {
            DMNCompilerImpl compiler = new DMNCompilerImpl(dmnCompilerConfig);
            DMNModel model = compiler.compile(dmnModel);
            if (model != null) {
                List<DTAnalysis> vs = dmnDTValidator.analyse(model, flags);
                List<DMNMessage> results = vs.stream().flatMap(a -> a.asDMNMessages().stream()).collect(Collectors.toList());
                return results;
            } else {
                throw new IllegalStateException("Compiled model is null!");
            }
        }
        return Collections.emptyList();
    }

    private static Stream<DMNModelInstrumentedBase> allChildren(DMNModelInstrumentedBase root) {
        return Stream.concat( Stream.of(root),
                              root.getChildren().stream().flatMap(DMNValidatorImpl::allChildren) );
    }
}
