/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.compiler.builder.impl;

import org.drools.drl.extensions.DecisionTableFactory;
import org.drools.drl.parser.DroolsParserException;
import org.drools.compiler.lang.descr.CompositePackageDescr;
import org.drools.core.builder.conf.impl.DecisionTableConfigurationImpl;
import org.drools.drl.ast.descr.PackageDescr;
import org.kie.api.builder.ReleaseId;
import org.kie.api.io.Resource;
import org.kie.internal.builder.DecisionTableConfiguration;
import java.io.IOException;
import java.util.*;

public class ProcessorDecisionTable extends Processor {

    public ProcessorDecisionTable(KnowledgeBuilderConfigurationImpl configuration, ReleaseId releaseId)
    {
        super(configuration, releaseId);
    }

    public PackageDescr process(Resource resource) throws DroolsParserException, IOException {
        DecisionTableConfiguration dtableConfiguration = configuration instanceof DecisionTableConfiguration ?
                (DecisionTableConfiguration) configuration :
                new DecisionTableConfigurationImpl();

        if (!dtableConfiguration.getRuleTemplateConfigurations().isEmpty()) {
            List<String> generatedDrls = DecisionTableFactory.loadFromInputStreamWithTemplates(resource, dtableConfiguration);
            if (generatedDrls.size() == 1) {
                return generatedDrlToPackageDescr(resource, generatedDrls.get(0));
            }
            CompositePackageDescr compositePackageDescr = null;
            for (String generatedDrl : generatedDrls) {
                PackageDescr packageDescr = generatedDrlToPackageDescr(resource, generatedDrl);
                if (packageDescr != null) {
                    if (compositePackageDescr == null) {
                        compositePackageDescr = new CompositePackageDescr(resource, packageDescr);
                    } else {
                        compositePackageDescr.addPackageDescr(resource, packageDescr);
                    }
                }
            }
            return compositePackageDescr;
        }

        dtableConfiguration.setTrimCell( this.configuration.isTrimCellsInDTable() );

        String generatedDrl = DecisionTableFactory.loadFromResource(resource, dtableConfiguration);
        return generatedDrlToPackageDescr(resource, generatedDrl);
    }
}
