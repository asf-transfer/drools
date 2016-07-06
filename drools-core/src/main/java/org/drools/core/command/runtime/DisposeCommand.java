/*
 * Copyright 2010 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.core.command.runtime;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAccessType;

import org.drools.core.command.impl.ContextImpl;
import org.drools.core.command.impl.GenericCommand;
import org.drools.core.command.impl.KnowledgeCommandContext;
import org.kie.internal.command.Context;
import org.kie.api.runtime.KieSession;

import java.util.Map;

@XmlRootElement
@XmlAccessorType(XmlAccessType.NONE)
public class DisposeCommand
    implements
    GenericCommand<Void> {

    public Void execute(Context context) {
        //KieSession ksession = ((KnowledgeCommandContext) context).getKieSession();
        KieSession ksession = (KieSession) ((Map<String, Object>)context.get(ContextImpl.REGISTRY)).get(KieSession.class.getName());
        ksession.dispose();
        return null;
    }

    public String toString() {
        return "ksession.dispose();";
    }

}
