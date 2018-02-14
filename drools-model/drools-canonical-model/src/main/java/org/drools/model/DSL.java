/*
 * Copyright 2005 JBoss Inc
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

package org.drools.model;

import java.util.concurrent.TimeUnit;

import org.drools.model.consequences.ConsequenceBuilder;
import org.drools.model.datasources.DataStore;
import org.drools.model.datasources.DataStream;
import org.drools.model.datasources.impl.DataStreamImpl;
import org.drools.model.datasources.impl.SetDataStore;
import org.drools.model.functions.Block0;
import org.drools.model.functions.Block1;
import org.drools.model.functions.Function1;
import org.drools.model.functions.Predicate1;
import org.drools.model.functions.accumulate.AccumulateFunction;
import org.drools.model.impl.AnnotationValueImpl;
import org.drools.model.impl.DeclarationImpl;
import org.drools.model.impl.EntryPointImpl;
import org.drools.model.impl.FromImpl;
import org.drools.model.impl.GlobalImpl;
import org.drools.model.impl.TypeMetaDataImpl;
import org.drools.model.impl.UnitDataImpl;
import org.drools.model.impl.ValueImpl;
import org.drools.model.impl.WindowImpl;
import org.drools.model.impl.WindowReferenceImpl;
import org.drools.model.view.AccumulateExprViewItem;
import org.drools.model.view.CombinedExprViewItem;
import org.drools.model.view.ExistentialExprViewItem;
import org.drools.model.view.ExprViewItem;
import org.drools.model.view.ViewItem;
import org.drools.model.view.ViewItemBuilder;

public class DSL {

    // -- DataSource --

    public static <T> DataStore<T> storeOf( T... items ) {
        return SetDataStore.storeOf( items );
    }

    public static DataStore newDataStore() {
        return storeOf();
    }

    public static DataStream newDataStream() {
        return new DataStreamImpl();
    }

    // -- TypeMetaData --

    public static TypeMetaDataImpl typeMetaData( String pkg, String name) {
        return new TypeMetaDataImpl(pkg, name);
    }

    public static AnnotationValue annotationValue(String key, String value) {
        return new AnnotationValueImpl( key, value );
    }

    // -- Variable --

    public static <T> Variable<T> any(Class<T> type) {
        return declarationOf( type );
    }

    public static <T> Declaration<T> declarationOf( Class<T> type ) {
        return new DeclarationImpl<T>( type );
    }

    public static <T> Declaration<T> declarationOf( Class<T> type, String name ) {
        return new DeclarationImpl<T>( type, name );
    }

    public static <T> Declaration<T> declarationOf( Class<T> type, DeclarationSource source ) {
        return new DeclarationImpl<T>( type ).setSource( source );
    }

    public static <T> Declaration<T> declarationOf( Class<T> type, String name, DeclarationSource source ) {
        return new DeclarationImpl<T>( type, name ).setSource( source );
    }

    public static <T> Declaration<T> declarationOf( Class<T> type, Window window ) {
        return new DeclarationImpl<T>( type ).setWindow( window );
    }

    public static <T> Declaration<T> declarationOf( Class<T> type, String name, Window window ) {
        return new DeclarationImpl<T>( type, name ).setWindow( window );
    }

    public static <T> Declaration<T> declarationOf( Class<T> type, DeclarationSource source, Window window ) {
        return new DeclarationImpl<T>( type ).setSource( source ).setWindow( window );
    }

    public static <T> Declaration<T> declarationOf( Class<T> type, String name, DeclarationSource source, Window window ) {
        return new DeclarationImpl<T>( type, name ).setSource( source ).setWindow( window );
    }

    public static <T> Global<T> globalOf( Class<T> type, String pkg ) {
        return new GlobalImpl<T>( type, pkg );
    }

    public static <T> Global<T> globalOf( Class<T> type, String pkg, String name ) {
        return new GlobalImpl<T>( type, pkg, name );
    }

    public static EntryPoint entryPoint( String name ) {
        return new EntryPointImpl( name );
    }

    public static Window window( Window.Type type, long value ) {
        return new WindowImpl(type, value);
    }

    public static Window window( Window.Type type, long value, TimeUnit timeUnit ) {
        return new WindowImpl(type, value, timeUnit);
    }

    public static <T> WindowReference<T> window( Window.Type type, long value, Class<T> patternType, Predicate1<T>... predicates ) {
        Predicate1<T>[] ps = new Predicate1[predicates.length];
        for (int i = 0; i < predicates.length; i++) {
            ps[i] = new Predicate1.Impl<T>( predicates[i] );
        }
        return new WindowReferenceImpl( type, value, patternType, ps );
    }

    public static <T> WindowReference<T> window( Window.Type type, long value, TimeUnit timeUnit, Class<T> patternType, Predicate1<T>... predicates ) {
        Predicate1<T>[] ps = new Predicate1[predicates.length];
        for (int i = 0; i < predicates.length; i++) {
            ps[i] = new Predicate1.Impl<T>( predicates[i] );
        }
        return new WindowReferenceImpl( type, value, timeUnit, patternType, ps );
    }

    public static UnitData<?> unitData( String name ) {
        return new UnitDataImpl( name );
    }

    public static <T> UnitData<T> unitData( Class<T> type, String name ) {
        return new UnitDataImpl( type, name );
    }

    public static <T> From<T> from( Variable<T> variable ) {
        return new FromImpl<>( variable );
    }

    public static <T> From<T> from( Variable<T> variable, Function1<T, ?> provider ) {
        return new FromImpl<>( variable, provider );
    }

    public static <T> From<T> reactiveFrom( Variable<T> variable, Function1<T, ?> provider ) {
        return new FromImpl<>( variable, provider, true );
    }

    public static ViewItem or( ViewItemBuilder<?> expression, ViewItemBuilder<?>... expressions) {
        if (expressions == null || expressions.length == 0) {
            return expression.get();
        }
        return new CombinedExprViewItem(Condition.Type.OR, combineExprs( expression, expressions ) );
    }

    public static ViewItem and(ViewItemBuilder<?> expression, ViewItemBuilder<?>... expressions) {
        if (expressions == null || expressions.length == 0) {
            return expression.get();
        }
        return new CombinedExprViewItem(Condition.Type.AND, combineExprs( expression, expressions ) );
    }

    private static ViewItem[] combineExprs( ViewItemBuilder<?> expression, ViewItemBuilder<?>... expressions ) {
        ViewItem[] andExprs = new ViewItem[expressions.length+1];
        andExprs[0] = expression.get();
        for (int i = 0; i < expressions.length; i++) {
            andExprs[i+1] = expressions[i].get();
        }
        return andExprs;
    }

    // -- Existential operator --

    public static ExprViewItem not( ViewItemBuilder<?> expression, ViewItemBuilder<?>... expressions) {
        return new ExistentialExprViewItem( Condition.Type.NOT, and( expression, expressions) );
    }

    public static ExprViewItem exists(ViewItemBuilder<?> expression, ViewItemBuilder<?>... expressions) {
        return new ExistentialExprViewItem( Condition.Type.EXISTS, and( expression, expressions) );
    }

    public static ExprViewItem forall(ViewItemBuilder<?> expression, ViewItemBuilder<?>... expressions) {
        return new ExistentialExprViewItem( Condition.Type.FORALL, and( expression, expressions) );
    }

    // -- Accumulate Functions --

    public static <T> ExprViewItem<T> accumulate(ViewItem<?> viewItem, AccumulateFunction... functions) {
        return new AccumulateExprViewItem(viewItem, functions);
    }

    public static AccumulateFunction accFunction( Class<?> accFunctionClass, Variable source) {
        return new AccumulateFunction(source, accFunctionClass);
    }

    // -- RHS --

    public static ConsequenceBuilder._0 execute( Block0 block) {
        return new ConsequenceBuilder._0(block);
    }

    public static ConsequenceBuilder._0 execute(Block1<Drools> block) {
        return new ConsequenceBuilder._0(block);
    }

    public static ConsequenceBuilder._0 executeScript(String language, String script) {
        return new ConsequenceBuilder._0(language, script);
    }

    public static <A> ConsequenceBuilder._1<A> on(Variable<A> dec1) {
        return new ConsequenceBuilder._1(dec1);
    }

    public static <A, B> ConsequenceBuilder._2<A, B> on(Variable<A> decl1, Variable<B> decl2) {
        return new ConsequenceBuilder._2(decl1, decl2);
    }

    public static <A, B, C> ConsequenceBuilder._3<A, B, C> on(Variable<A> decl1, Variable<B> decl2, Variable<C> decl3) {
        return new ConsequenceBuilder._3(decl1, decl2, decl3);
    }

    public static ConsequenceBuilder._N on(Variable... declarations) {
        return new ConsequenceBuilder._N(declarations);
    }

    public static <T> Value<T> valueOf(T value) {
        return new ValueImpl<>( value );
    }
}
