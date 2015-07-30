/*
 * Copyright 2015 JBoss Inc
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

package org.drools.compiler.rule.builder.dialect.mvel;

import java.util.HashMap;
import java.util.Map;

import org.drools.core.definitions.rule.impl.RuleImpl;
import org.drools.core.rule.Declaration;
import org.drools.core.spi.DeclarationScopeResolver;

public class InstrumentedDeclarationScopeResolver extends DeclarationScopeResolver {
    private Map declarations;

    public InstrumentedDeclarationScopeResolver() {
        super( new HashMap() );
    }

    public void setDeclarations(final Map map) {
        this.declarations = map;
    }

    @Override
    public Map getDeclarations( RuleImpl rule ) {
        return this.declarations;
    }
    
    @Override
    public Declaration getDeclaration( RuleImpl rule, String name) {
        return ( Declaration ) this.declarations.get( name );
    }
}
