/*
 * Copyright 2011 Red Hat, Inc. and/or its affiliates.
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

package org.drools.drl.ast.dsl.impl;

import org.drools.drl.ast.dsl.AnnotationDescrBuilder;
import org.drools.drl.ast.dsl.EnumDeclarationDescrBuilder;
import org.drools.drl.ast.dsl.EnumLiteralDescrBuilder;
import org.drools.drl.ast.descr.EnumLiteralDescr;


public class EnumLiteralDescrBuilderImpl extends BaseDescrBuilderImpl<EnumDeclarationDescrBuilder, EnumLiteralDescr>
    implements
        EnumLiteralDescrBuilder {


    protected EnumLiteralDescrBuilderImpl( final EnumDeclarationDescrBuilder parent ) {
        super(parent, new EnumLiteralDescr() );
    }

    protected EnumLiteralDescrBuilderImpl( final EnumDeclarationDescrBuilder parent, final EnumLiteralDescr descr ) {
        super(parent, descr);
    }

    public EnumLiteralDescrBuilder index(int index) {
        descr.setIndex( index );
        return this;
    }

    public EnumLiteralDescrBuilder name(String name) {
        descr.setName( name );
        return this;
    }

    public EnumLiteralDescrBuilder constructorArg( String expr ) {
        descr.addConstructorArg( expr );
        return this;
    }

    public AnnotationDescrBuilder<EnumLiteralDescrBuilder> newAnnotation( String name ) {
        AnnotationDescrBuilder<EnumLiteralDescrBuilder> annotation = new AnnotationDescrBuilderImpl<>( this, name );
        descr.addAnnotation( annotation.getDescr() );
        return annotation;
    }
}
