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
package org.kie.api.io;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourceType implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(ResourceType.class);

    private static final long serialVersionUID = 1613735834228581906L;

    private final String name;

    private final String description;

    private final String defaultExtension;

    private final String[] otherExtensions;

    private final String defaultPath;

    private final boolean fullyCoveredByExecModel;

    private static final Map<String, ResourceType> CACHE = Collections.synchronizedMap(new HashMap<>());

    public ResourceType(String name,
                        boolean fullyCoveredByExecModel,
                        String description,
                        String defaultPath,
                        String defaultExtension,
                        String... otherExtensions) {
        this.name = name;
        this.fullyCoveredByExecModel = fullyCoveredByExecModel;
        this.description = description;
        this.defaultPath = defaultPath;
        this.defaultExtension = defaultExtension;
        this.otherExtensions = otherExtensions;
    }

    public static ResourceType addResourceTypeToRegistry(final String resourceType,
                                                         final String description,
                                                         final String defaultPath,
                                                         final String defaultExtension,
                                                         final String... otherExtensions) {

        return addResourceTypeToRegistry(resourceType, true, description, defaultPath, defaultExtension, otherExtensions);
    }

    public static ResourceType addResourceTypeToRegistry(final String resourceType,
                                                         boolean isNative,
                                                         final String description,
                                                         final String defaultPath,
                                                         final String defaultExtension,
                                                         final String... otherExtensions) {

        ResourceType resource = new ResourceType( resourceType,
                                                  isNative,
                                                  description,
                                                  defaultPath,
                                                  defaultExtension,
                                                  otherExtensions );

        CACHE.put( resourceType, resource );
        for (String ext : resource.getAllExtensions()) {
            CACHE.put("." + ext, resource);
        }
        return resource;
    }

    /** Drools Rule Language */
    public static final ResourceType DRL = addResourceTypeToRegistry("DRL",
                                                                     "Drools Rule Language",
                                                                     "src/main/resources",
                                                                     "drl");

    public static final ResourceType DRLX = addResourceTypeToRegistry("DRLX",
                                                                      "Drools Extended Rule Language (experimental)",
                                                                      "src/main/resources",
                                                                      "drlx");


    /** Drools Rule Language - Guided Globals definitions - Workaround for double-dot file extensions for 6.0 */
    public static final ResourceType GDRL = addResourceTypeToRegistry("GDRL",
                                                                      "Drools Rule Language",
                                                                      "src/main/resources",
                                                                      "gdrl");

    /** Drools Rule Language - Guided Rules (without DSL) - Workaround for double-dot file extensions for 6.0 */
    public static final ResourceType RDRL = addResourceTypeToRegistry("RDRL",
                                                                      "Drools Rule Language",
                                                                      "src/main/resources",
                                                                      "rdrl");

    /** Drools DSL */
    public static final ResourceType DSL = addResourceTypeToRegistry("DSL",
                                                                     "Drools DSL",
                                                                     "src/main/resources",
                                                                     "dsl");

    /** Drools DSL Rule */
    public static final ResourceType DSLR = addResourceTypeToRegistry("DSLR",
                                                                      "Drools DSL Rule",
                                                                      "src/main/resources",
                                                                      "dslr");

    /** Drools Rule Language - Guided Rules (with DSL) - Workaround for double-dot file extensions for 6.0 */
    public static final ResourceType RDSLR = addResourceTypeToRegistry("RDSLR",
                                                                       "Drools DSL Rule",
                                                                       "src/main/resources",
                                                                       "rdslr");

    /** jBPM BPMN2 Language */
    public static final ResourceType BPMN2 = addResourceTypeToRegistry("BPMN2",
                                                                       "jBPM BPMN2 Language",
                                                                       "src/main/resources",
                                                                       "bpmn", "bpmn2", "bpmn-cm");

    /** jBPM CMMN Language */
    public static final ResourceType CMMN = addResourceTypeToRegistry("CMMN",
                                                                       "jBPM CMMN Language",
                                                                       "src/main/resources",
                                                                       "cmmn");

    /** Decision Table */
    public static final ResourceType DTABLE = addResourceTypeToRegistry("DTABLE",
                                                                        "Decision Table",
                                                                        "src/main/resources",
                                                                        "drl.xls", "drl.xlsx", "drl.csv");

    /** XSD */
    public static final ResourceType XSD = addResourceTypeToRegistry("XSD",
                                                                     "XSD",
                                                                     "src/main/resources",
                                                                     "xsd");

    /** PMML */
    public static final ResourceType PMML = addResourceTypeToRegistry("PMML",
                                                                      false,
                                                                      "Predictive Model Markup Language",
                                                                      "src/main/resources",
                                                                      "pmml");

    /** DESCR */
    public static final ResourceType DESCR = addResourceTypeToRegistry("DESCR",
                                                                       "Knowledge Descriptor",
                                                                       "src/main/resources",
                                                                       "descr");

    /** JAVA */
    public static final ResourceType JAVA = addResourceTypeToRegistry("JAVA",
                                                                      "Java class",
                                                                      "src/main/java",
                                                                      "java");

    /** PROPERTIES */
    public static final ResourceType PROPERTIES = addResourceTypeToRegistry("PROPERTIES",
                                                                            "Properties file",
                                                                            "src/main/resources",
                                                                            "properties");

    /** Score Card - Workaround for double-dot file extensions for 6.0 */
    public static final ResourceType SCARD = addResourceTypeToRegistry("SCARD",
                                                                       "Score Crd",
                                                                       "src/main/resources",
                                                                       "sxls");


    /** Bayesian */
    public static final ResourceType BAYES = addResourceTypeToRegistry("Bayes",
                                                                       "Bayesian Belief Network",
                                                                       "src/main/resources",
                                                                       "xmlbif", "bif");

    /** Drools Rule Language - Guided Decision Trees - Workaround for double-dot file extensions for 6.0 */
    public static final ResourceType TDRL = addResourceTypeToRegistry("TDRL",
                                                                      "Drools Rule Language",
                                                                      "src/main/resources",
                                                                      "tdrl");

    public static final ResourceType TEMPLATE = addResourceTypeToRegistry("TEMPLATE",
                                                                          "Drools Rule Template",
                                                                          "src/main/resources",
                                                                          "drl.template");
    /**
     * @deprecated Since 8. Consider <code>drools-decisiontables</code> or third party templating features
     */
    @Deprecated
    public static final ResourceType DRT = addResourceTypeToRegistry("DRT",
                                                                     "Drools Rule Template",
                                                                     "src/main/resources",
                                                                     "drt");

    public static final ResourceType GDST = addResourceTypeToRegistry("GDST",
                                                                      "Guided Decision Table",
                                                                      "src/main/resources",
                                                                      "gdst");

    public static final ResourceType SCGD = addResourceTypeToRegistry("SCGD",
                                                                      "Guided Score Card",
                                                                      "src/main/resources",
                                                                      "scgd");

    public static final ResourceType SOLVER = addResourceTypeToRegistry("SOLVER",
                                                                        "OptaPlanner Solver Configuration",
                                                                        "src/main/resources",
                                                                        "solver");

    /** Decision Model and Notation (DMN) model  */
    public static final ResourceType DMN = addResourceTypeToRegistry("DMN",
                                                                     false,
                                                                     "Decision Model and Notation",
                                                                     "src/main/resources",
                                                                     "dmn");

    /** DMN FEEL expression language */
    public static final ResourceType FEEL = addResourceTypeToRegistry("FEEL",
                                                                      false,
                                                                     "Friendly Enough Expression Language",
                                                                     "src/main/resources",
                                                                     "feel");

    /** NO-Operation ResourceType - used for example to dynamically disable a given AssemblerService */
    public static final ResourceType NOOP = addResourceTypeToRegistry("NOOP",
                                                                      false,
                                                                      "No-operation type",
                                                                      "src/main/resources",
                                                                      "no_op");

    public static final ResourceType YAML = addResourceTypeToRegistry("YAML",
                                                                      "YAML format DRL",
                                                                      "src/main/resources",
                                                                      "drl.yaml", "drl.yml");

    public static ResourceType determineResourceType(final String resourceName) {
        for ( Map.Entry<String, ResourceType> entry : CACHE.entrySet() ) {
            if (resourceName.endsWith(entry.getKey())) {
                if (entry.getValue().equals(ResourceType.DRT)) {
                    LOG.warn("DRT (Drools Rule Template) is deprecated. Please consider drools-decisiontables or third party templating features.");
                }
                return entry.getValue();
            }
        }
        return null;
    }

    public boolean matchesExtension(String resourceName) {
        if (resourceName != null) {

            if (resourceName.endsWith("." + defaultExtension)) {
                return true;
            }
            for (String extension : otherExtensions) {
                if (resourceName.endsWith("." + extension)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isFullyCoveredByExecModel() {
        return fullyCoveredByExecModel;
    }

    public String getDefaultPath() {
        return defaultPath;
    }

    public String getDefaultExtension() {
        return defaultExtension;
    }

    public List<String> getAllExtensions() {
        final List<String> extensions = new LinkedList<>();
        extensions.add(defaultExtension);
        extensions.addAll(Arrays.asList(otherExtensions));
        return Collections.unmodifiableList(extensions);
    }

    public String getDescription() {
        return description;
    }

    public String getName() {
        return name;
    }

    public String toString() {
        return "ResourceType = '" + this.description + "'";
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) { return true; }
        if (obj == null) { return false;}
        if (getClass() != obj.getClass()) { return false; }
        final ResourceType other = (ResourceType) obj;
        if (!name.equals(other.name)) { return false; }
        return true;
    }

}
