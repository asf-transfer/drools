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
package org.kie.dmn.core.impl;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;

import org.drools.base.common.DroolsObjectInputStream;
import org.drools.base.common.DroolsObjectOutputStream;
import org.kie.api.io.Resource;
import org.kie.dmn.api.core.DMNMessage;
import org.kie.dmn.api.core.DMNMessageType;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.api.core.ast.BusinessKnowledgeModelNode;
import org.kie.dmn.api.core.ast.DMNNode;
import org.kie.dmn.api.core.ast.DecisionNode;
import org.kie.dmn.api.core.ast.DecisionServiceNode;
import org.kie.dmn.api.core.ast.InputDataNode;
import org.kie.dmn.api.core.ast.ItemDefNode;
import org.kie.dmn.api.feel.runtime.events.FEELEvent;
import org.kie.dmn.api.marshalling.DMNExtensionRegister;
import org.kie.dmn.backend.marshalling.v1x.DMNMarshallerFactory;
import org.kie.dmn.core.api.DMNMessageManager;
import org.kie.dmn.core.assembler.DMNAssemblerService;
import org.kie.dmn.core.ast.BusinessKnowledgeModelNodeImpl;
import org.kie.dmn.core.ast.DecisionNodeImpl;
import org.kie.dmn.core.compiler.DMNCompilerImpl;
import org.kie.dmn.core.compiler.DMNTypeRegistry;
import org.kie.dmn.core.compiler.DMNTypeRegistryV11;
import org.kie.dmn.core.compiler.DMNTypeRegistryV12;
import org.kie.dmn.core.compiler.DMNTypeRegistryV13;
import org.kie.dmn.core.compiler.DMNTypeRegistryV14;
import org.kie.dmn.core.compiler.DMNTypeRegistryV15;
import org.kie.dmn.core.pmml.DMNImportPMMLInfo;
import org.kie.dmn.core.util.DefaultDMNMessagesManager;
import org.kie.dmn.feel.lang.FEELDialect;
import org.kie.dmn.model.api.DMNModelInstrumentedBase;
import org.kie.dmn.model.api.Definitions;

import static org.kie.dmn.core.compiler.UnnamedImportUtils.isInUnnamedImport;
import static org.kie.dmn.core.impl.TupleIdentifier.createTupleIdentifier;
import static org.kie.dmn.core.impl.TupleIdentifier.createTupleIdentifierByName;

public class DMNModelImpl
        implements DMNModel, DMNMessageManager, Externalizable {
    
    private enum SerializationFormat {
        // To ensure backward compatibility, append only:
        DMN_XML
    }

    private SerializationFormat serializedAs = SerializationFormat.DMN_XML;
    private Resource resource;
    private Definitions definitions;
    
    private Map<TupleIdentifier, InputDataNode>              inputs       = new LinkedHashMap<>();
    private Map<TupleIdentifier, DecisionNode>               decisions    = new LinkedHashMap<>();
    private Map<TupleIdentifier, BusinessKnowledgeModelNode> bkms         = new LinkedHashMap<>();
    private Map<TupleIdentifier, ItemDefNode>                itemDefs     = new LinkedHashMap<>();
    private Map<TupleIdentifier, DecisionServiceNode> decisionServices    = new LinkedHashMap<>();
    private Map<TupleIdentifier, DMNImportPMMLInfo> pmmlImportInfo = new HashMap<>();
    private Map<TupleIdentifier, QName> importAliases = new HashMap<>();

    // these are messages created at loading/compilation time
    private DMNMessageManager messages;

    private DMNTypeRegistry types;
    /**
     * a compile-time preference to indicate if type-check should be performed during runtime evaluation.
     */
    private boolean runtimeTypeCheck = false;

    private ImportChain importChain;

    private FEELDialect feelDialect;

    public DMNModelImpl() {
        // needed because Externalizable.
    }

    public DMNModelImpl(Definitions definitions) {
        this.definitions = definitions;
        wireTypeRegistry(definitions);
        importChain = new ImportChain(this);
        messages = new DefaultDMNMessagesManager(null);
        String expressionLanguage = definitions.getExpressionLanguage() != null ? definitions.getExpressionLanguage() : "";
        try {
            feelDialect = FEELDialect.fromNamespace(expressionLanguage);
        } catch (IllegalArgumentException e) {
            feelDialect = FEELDialect.FEEL;
        }
    }

    public DMNModelImpl(Definitions dmndefs, Resource resource) {
        this(dmndefs);
        this.setResource(resource);
        messages = new DefaultDMNMessagesManager(resource);
    }

    public FEELDialect getFeelDialect() {
        return feelDialect;
    }

    private void wireTypeRegistry(Definitions definitions) {
        if (definitions instanceof org.kie.dmn.model.v1_1.TDefinitions) {
            types = new DMNTypeRegistryV11(Collections.unmodifiableMap(importAliases));
        } else if (definitions instanceof org.kie.dmn.model.v1_2.TDefinitions) {
            types = new DMNTypeRegistryV12(Collections.unmodifiableMap(importAliases));
        } else if (definitions instanceof org.kie.dmn.model.v1_3.TDefinitions) {
            types = new DMNTypeRegistryV13(Collections.unmodifiableMap(importAliases));
        } else if (definitions instanceof org.kie.dmn.model.v1_4.TDefinitions) {
            types = new DMNTypeRegistryV14(Collections.unmodifiableMap(importAliases));
        } else {
            types = new DMNTypeRegistryV15(Collections.unmodifiableMap(importAliases));
        }
    }
    
    public DMNTypeRegistry getTypeRegistry() {
        return this.types;
    }

    @Override
    public String getNamespace() {
        return definitions != null ? definitions.getNamespace() : null;
    }

    public String getName() {
        return definitions != null ? definitions.getName() : null;
    }

    @Override
    public Definitions getDefinitions() {
        return definitions;
    }

    public void setDefinitions(Definitions definitions) {
        this.definitions = definitions;
    }

    /**
     * Given a DMNNode, compute the proper name of the node, considering DMN-Imports.
     * For DMNNode in this current model, name is simply the name of the model.
     * For imported DMNNodes, this is the name with the prefix of the direct-dependency of the import `name`.
     * For transitively-imported DMNNodes, it is always null.
     */
    public String nameInCurrentModel(DMNNode node) {
        if (node.getModelNamespace().equals(this.getNamespace())) {
            return node.getName();
        } else {
            Optional<String> lookupAlias = getImportAliasFor(node.getModelNamespace(), node.getModelName());
            return lookupAlias.map(s -> s + "." + node.getName()).orElse(null);
        }
    }

    public void addInput(InputDataNode idn) {
        computeDRGElementModelLocalId(idn).forEach(id -> inputs.put(createTupleIdentifier(id, nameInCurrentModel(idn)), idn));
    }

    @Override
    public InputDataNode getInputById(String id) {
        return this.inputs.get( new TupleIdentifier(id, null) );
    }

    @Override
    public InputDataNode getInputByName(String name) {
        if ( name == null ) {
            return null;
        }
        return inputs.get(new TupleIdentifier(null, name));
    }

    @Override
    public Set<InputDataNode> getInputs() {
        return new LinkedHashSet<>(this.inputs.values());
    }

    public void addDecision(DecisionNode dn) {
        computeDRGElementModelLocalId(dn).forEach(id -> decisions.put(createTupleIdentifier(id, nameInCurrentModel(dn)), dn));
    }

    private List<String> computeDRGElementModelLocalId(DMNNode node) {
        // incubator-kie-issues#852: The idea is to not treat the anonymous models as import, but to "merge" them with original opne,
        // Here, if the node comes from an unnamed imported model, then it is stored only with its id, to be looked for
        // as if defined in the model itself
        if (node.getModelNamespace().equals(definitions.getNamespace())) {
            return Collections.singletonList(node.getId());
        } else if (isInUnnamedImport(node, this)) {
            // the node is an unnamed import
            return Arrays.asList(node.getId(), node.getModelNamespace() + "#" + node.getId());
        } else {
            return Collections.singletonList(node.getModelNamespace() + "#" + node.getId());
        }
    }


    @Override
    public DecisionNode getDecisionById(String id) {
        return id != null ? this.decisions.get(new TupleIdentifier(id, null)) : null;
    }

    @Override
    public DecisionNode getDecisionByName(String name) {
        if ( name == null ) {
            return null;
        }
        return decisions.get(new TupleIdentifier(null, name));
    }

    @Override
    public Set<DecisionNode> getDecisions() {
        return new LinkedHashSet<>(this.decisions.values());
    }

    @Override
    public Set<InputDataNode> getRequiredInputsForDecisionName(String decisionName) {
        DecisionNodeImpl decision = (DecisionNodeImpl) getDecisionByName( decisionName );
        Set<InputDataNode> inputs = new HashSet<>();
        if ( decision != null ) {
            collectRequiredInputs( decision.getDependencies().values(), inputs );
        }
        return inputs;
    }

    @Override
    public Set<InputDataNode> getRequiredInputsForDecisionId(String decisionId) {
        DecisionNodeImpl decision = (DecisionNodeImpl) getDecisionById( decisionId );
        Set<InputDataNode> inputs = new HashSet<>();
        if ( decision != null ) {
            collectRequiredInputs( decision.getDependencies().values(), inputs );
        }
        return inputs;
    }

    public void addDecisionService(DecisionServiceNode dsn) {
        computeDRGElementModelLocalId(dsn).forEach(id -> decisionServices.put(createTupleIdentifier(id, dsn.getName()), dsn));
    }

    public DecisionServiceNode getDecisionServiceById(String id) {
        return this.decisionServices.get(new TupleIdentifier(id, null));
    }

    public DecisionServiceNode getDecisionServiceByName(String name) {
        if (name == null) {
            return null;
        }
        return decisionServices.get(new TupleIdentifier(null, name));
    }

    @Override
    public Collection<DecisionServiceNode> getDecisionServices() {
        return new LinkedHashSet<>(this.decisionServices.values());
    }

    public void addBusinessKnowledgeModel(BusinessKnowledgeModelNode bkm) {
        computeDRGElementModelLocalId(bkm).forEach(id -> bkms.put(createTupleIdentifier(id, bkm.getName()), bkm));
    }

    @Override
    public BusinessKnowledgeModelNode getBusinessKnowledgeModelById(String id) {
        return this.bkms.get( new TupleIdentifier(id, null) );
    }

    @Override
    public BusinessKnowledgeModelNode getBusinessKnowledgeModelByName(String name) {
        if ( name == null ) {
            return null;
        }
        return bkms.get(new TupleIdentifier(null, name));
    }

    @Override
    public Set<BusinessKnowledgeModelNode> getBusinessKnowledgeModels() {
        return new LinkedHashSet<>(this.bkms.values());
    }

    private void collectRequiredInputs(Collection<DMNNode> deps, Set<InputDataNode> inputs) {
        deps.forEach( dep -> {
            if ( dep instanceof InputDataNode ) {
                inputs.add( (InputDataNode) dep );
            } else if ( dep instanceof DecisionNode ) {
                collectRequiredInputs( ((DecisionNodeImpl) dep).getDependencies().values(), inputs );
            } else if ( dep instanceof BusinessKnowledgeModelNode ) {
                collectRequiredInputs( ((BusinessKnowledgeModelNodeImpl) dep).getDependencies().values(), inputs );
            }
        } );
    }

    public void addItemDefinition(ItemDefNode idn) {
        // if ID is null, generate an ID for it
        String id = idn.getId() != null ? idn.getId() : "_" + this.itemDefs.size();
        this.itemDefs.put( createTupleIdentifier(id, idn.getName()), idn );
    }

    @Override
    public ItemDefNode getItemDefinitionById(String id) {
        return this.itemDefs.get( new TupleIdentifier(id, null) );
    }

    @Override
    public ItemDefNode getItemDefinitionByName(String name) {
        if ( name == null ) {
            return null;
        }
        return itemDefs.get(new TupleIdentifier(null, name));
    }

    @Override
    public Set<ItemDefNode> getItemDefinitions() {
        return new LinkedHashSet<>(this.itemDefs.values());
    }

    @Override
    public List<DMNMessage> getMessages() {
        return messages.getMessages();
    }

    @Override
    public List<DMNMessage> getMessages(DMNMessage.Severity... sevs) {
        return messages.getMessages( sevs );
    }

    @Override
    public boolean hasErrors() {
        return messages.hasErrors();
    }

    @Override
    public void addAll(List<? extends DMNMessage> messages) {
        this.messages.addAll( messages );
    }

    @Override
    public DMNMessage addMessage(DMNMessage msg) {
        return messages.addMessage( msg );
    }

    @Override
    public DMNMessage addMessage(DMNMessage.Severity severity, String message, DMNMessageType messageType, DMNModelInstrumentedBase source) {
        return messages.addMessage( severity, message, messageType, source );
    }

    @Override
    public DMNMessage addMessage(DMNMessage.Severity severity, String message, DMNMessageType messageType, DMNModelInstrumentedBase source, Throwable exception) {
        return messages.addMessage( severity, message, messageType, source, exception );
    }

    @Override
    public DMNMessage addMessage(DMNMessage.Severity severity, String message, DMNMessageType messageType, DMNModelInstrumentedBase source, FEELEvent feelEvent) {
        return messages.addMessage( severity, message, messageType, source, feelEvent );
    }
    
    public boolean removeDMNNodeFromIndexes( DMNNode node ) {
        if ( node instanceof DecisionNode ) {
            return this.decisions.entrySet().removeIf( kv -> kv.getValue().equals(node) );
        } else if ( node instanceof BusinessKnowledgeModelNode ) {
            return this.bkms.entrySet().removeIf( kv -> kv.getValue().equals(node) );
        } else if ( node instanceof InputDataNode ) {
            return this.inputs.entrySet().removeIf( kv -> kv.getValue().equals(node) );
        }
        return false;
    }

    public void setResource(Resource resource) {
        this.resource = resource;
    }

    @Override
    public Resource getResource() {
        return resource;
    }
    
    /**
     * @return a compile-time preference to indicate if type-check should be performed during runtime evaluation.
     */
    public boolean isRuntimeTypeCheck() {
        return runtimeTypeCheck;
    }

    public void setRuntimeTypeCheck(boolean runtimeTypeCheck) {
        this.runtimeTypeCheck = runtimeTypeCheck;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(serializedAs);
        out.writeObject(resource);

        if ( !(out instanceof DroolsObjectOutputStream) ) {
            throw new UnsupportedOperationException();
            // TODO assume some defaults
        }
        
        DroolsObjectOutputStream os = (DroolsObjectOutputStream) out;
        DMNCompilerImpl compiler = (DMNCompilerImpl) os.getCustomExtensions().get(DMNAssemblerService.DMN_COMPILER_CACHE_KEY);
        List<DMNExtensionRegister> dmnRegisteredExtensions = compiler.getRegisteredExtensions();
        
        String output = DMNMarshallerFactory.newMarshallerWithExtensions(dmnRegisteredExtensions).marshal(this.definitions);

        out.writeObject(output);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        this.serializedAs = (SerializationFormat) in.readObject();
        this.resource = (Resource) in.readObject();
        this.messages = new DefaultDMNMessagesManager(this.resource);
        String xml = (String) in.readObject();
        
        if ( !(in instanceof DroolsObjectInputStream) ) {
            throw new UnsupportedOperationException();
            // TODO assume some defaults
        }
        
        DroolsObjectInputStream is = (DroolsObjectInputStream) in;
        DMNCompilerImpl compiler = (DMNCompilerImpl) is.getCustomExtensions().get(DMNAssemblerService.DMN_COMPILER_CACHE_KEY);
        List<DMNExtensionRegister> dmnRegisteredExtensions = compiler.getRegisteredExtensions();
        
        Definitions definitions = DMNMarshallerFactory.newMarshallerWithExtensions(dmnRegisteredExtensions).unmarshal(xml);
        
        this.definitions = definitions;
        this.wireTypeRegistry(definitions);
        
        DMNModelImpl compiledModel = (DMNModelImpl) compiler.compile(definitions);
        this.inputs    = compiledModel.inputs    ;
        this.decisions = compiledModel.decisions ;
        this.bkms      = compiledModel.bkms      ;
        this.itemDefs  = compiledModel.itemDefs  ;
        this.messages  = compiledModel.messages  ;
        this.types     = compiledModel.types     ;
        this.runtimeTypeCheck = compiledModel.runtimeTypeCheck;
        this.importAliases = compiledModel.importAliases;
    }

    public void setImportAliasForNS(String iAlias, String iNS, String iModelName) {
        if (getImportAliasFor(iNS, iModelName).isEmpty()) {
            this.importAliases.put(createTupleIdentifierByName(iAlias), new QName(iNS, iModelName));
        }
    }

    public Map<String, QName> getImportAliasesForNS() {
        return importAliases.entrySet()
                .stream().collect(Collectors.toMap(tupleIdentifierQNameEntry -> tupleIdentifierQNameEntry.getKey().getName(),
                                                   Entry::getValue));
    }

    public Optional<String> getImportAliasFor(String ns, String iModelName) {
        QName lookup = new QName(ns, iModelName);
        return this.importAliases.entrySet().stream().filter(kv -> kv.getValue().equals(lookup)).map(kv -> kv.getKey().getName()).findFirst();
    }

    public QName getImportNamespaceAndNameforAlias(String iAlias) {
        return this.importAliases.get(createTupleIdentifierByName(iAlias));
    }

    public void addImportChainChild(ImportChain child, String alias) {
        this.importChain.children.add(ImportChain.from(child, alias));
    }

    public ImportChain getImportChain() {
        return this.importChain;
    }

    public Map<String, Collection<List<String>>> getImportChainAliases() {
        return this.importChain.getImportChainAliases();
    }

    public List<DMNModel> getImportChainDirectChildModels() {
        return this.importChain.getImportChainDirectChildModels();
    }


    @Override
    public void addAllUnfiltered(List<? extends DMNMessage> messages) {
        this.messages.addAllUnfiltered( messages );
    }

    public void addPMMLImportInfo(DMNImportPMMLInfo info) {
        this.pmmlImportInfo.put(createTupleIdentifierByName(info.getImportName()), info);
    }

    public Map<String, DMNImportPMMLInfo> getPmmlImportInfo() {
        return pmmlImportInfo.entrySet()
                .stream().collect(Collectors.toMap(tupleIdentifierQNameEntry -> tupleIdentifierQNameEntry.getKey().getName(),
                                                   Entry::getValue));

    }

    private static class ImportChain {
        private final String alias;
        private final DMNModel node;
        
        private final List<ImportChain> children = new ArrayList<>();
        
        public ImportChain(DMNModel node) {
            this(node, null);
        }

        private ImportChain(DMNModel node, String alias) {
            this.alias = alias;
            this.node = node;
        }
        
        public static ImportChain from(ImportChain from, String alias) {
            ImportChain result = new ImportChain(from.node, alias);
            result.children.addAll(from.children);
            return result;
        }

        /**
         * For any given namespace, will return the list of available aliases (also transitive ones).
         */
        public Map<String, Collection<List<String>>> getImportChainAliases() {
            Map<String, Collection<List<String>>> result = new HashMap<>();
            for (ImportChain l : children) {
                Map<String, Collection<List<String>>> leafResult = l.getImportChainAliases(); 
                for (Entry<String, Collection<List<String>>> kv : leafResult.entrySet()) {
                    Collection<List<String>> allPrefixesUnderNamespace = result.computeIfAbsent(kv.getKey(), k -> new ArrayList<>());
                    for (List<String> ps : kv.getValue()) {
                        List<String> prefixed = new ArrayList<>();
                        if (alias != null) { // if alias is null then I am root hence what need to be added is directly the result of the recursive call, as-is.
                            prefixed.add(alias);
                        }
                        prefixed.addAll(ps);
                        allPrefixesUnderNamespace.add(prefixed);
                    }
                }
            }
            if (alias != null) {
                Collection<List<String>> allPrefixesUnderMyNamespace = result.computeIfAbsent(node.getNamespace(), k -> new ArrayList<>());
                allPrefixesUnderMyNamespace.add(List.of(alias));
            }
            return result;
        }

        /**
         * return the list of child models not including transitive ones.
         */
        public List<DMNModel> getImportChainDirectChildModels() {
            return children.stream().map(chain -> chain.node).collect(Collectors.toList());
        }
    }

}
