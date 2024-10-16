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
package org.drools.mvel.util;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.drools.base.rule.accessor.RightTupleValueExtractor;
import org.drools.core.base.ClassFieldAccessorCache;
import org.drools.core.impl.KnowledgeBaseImpl;
import org.drools.core.reteoo.MockLeftTupleSink;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.core.util.index.IndexSpec;
import org.drools.mvel.accessors.ClassFieldAccessorStore;
import org.drools.base.base.ClassObjectType;
import org.drools.core.common.DefaultFactHandle;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.reteoo.LeftTuple;
import org.drools.core.reteoo.TupleImpl ;
import org.drools.core.reteoo.RightTuple;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.Pattern;
import org.drools.base.rule.accessor.ReadAccessor;
import org.drools.core.reteoo.Tuple;
import org.drools.core.test.model.Cheese;
import org.drools.core.util.AbstractHashTable;
import org.drools.base.util.IndexedValueReader;
import org.drools.core.util.SingleLinkedEntry;
import org.drools.core.util.index.TupleIndexHashTable;
import org.drools.core.util.index.TupleList;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RightTupleIndexHashTableTest {

    ClassFieldAccessorStore      store  = new ClassFieldAccessorStore();

    @Before
    public void setUp() throws Exception {
        store.setClassFieldAccessorCache( new ClassFieldAccessorCache( Thread.currentThread().getContextClassLoader() ) );
        store.setEagerWire( true );
    }
    public AbstractHashTable.Index getIndexSupplier(IndexedValueReader fieldIndex) {
        return new IndexSpec(new IndexedValueReader[] {fieldIndex}).getIndex();
    }

    @Test
    public void testSingleEntry() throws Exception {
        BuildContext bctx = new BuildContext(new KnowledgeBaseImpl("01"), Collections.emptyList());
        MockLeftTupleSink    sink = new MockLeftTupleSink(0, bctx);

        final ReadAccessor extractor = store.getReader( Cheese.class,
                                                                "type" );

        final Pattern pattern = new Pattern( 0,
                                             new ClassObjectType( Cheese.class ) );

        final Declaration declaration = new Declaration( "typeOfCheese",
                                                         extractor,
                                                         pattern );

        final IndexedValueReader fieldIndex = new IndexedValueReader(declaration, new RightTupleValueExtractor(extractor));

        final TupleIndexHashTable map = new TupleIndexHashTable(new IndexSpec(new IndexedValueReader[]{fieldIndex}).getIndex(), false );

        final Cheese cheddar = new Cheese( "cheddar",
                                           10 );
        final InternalFactHandle cheddarHandle1 = new DefaultFactHandle( 0,
                                                                         cheddar );

        assertThat(map.size()).isEqualTo(0);
        assertThat(map.getFirst(new LeftTuple( cheddarHandle1,
                                               sink,
                true ))).isNull();

        final Cheese stilton1 = new Cheese( "stilton",
                                            35 );
        TupleImpl  stiltonRighTuple = new RightTuple(new DefaultFactHandle(1,
                                                                           stilton1 ),
                                                     null );

        map.add( stiltonRighTuple );

        assertThat(map.size()).isEqualTo(1);
        assertThat(tablePopulationSize(map)).isEqualTo(1);

        final Cheese stilton2 = new Cheese( "stilton",
                                            80 );
        final InternalFactHandle stiltonHandle2 = new DefaultFactHandle( 2,
                                                                         stilton2 );

        final Tuple tuple = map.getFirst( new LeftTuple( stiltonHandle2,
                                                         sink,
                                                         true ) );
        assertThat(tuple.getFactHandle()).isSameAs(stiltonRighTuple.getFactHandle());
        assertThat(tuple.getNext()).isNull();
    }

    @Test
    public void testTwoDifferentEntries() throws Exception {
        BuildContext bctx = new BuildContext(new KnowledgeBaseImpl("01"), Collections.emptyList());
        MockLeftTupleSink    sink = new MockLeftTupleSink(0, bctx);

        final ReadAccessor extractor = store.getReader( Cheese.class,
                                                                "type" );

        final Pattern pattern = new Pattern( 0,
                                             new ClassObjectType( Cheese.class ) );

        final Declaration declaration = new Declaration( "typeOfCheese",
                                                         extractor,
                                                         pattern );

        final IndexedValueReader fieldIndex = new IndexedValueReader(declaration, new RightTupleValueExtractor(extractor));

        final TupleIndexHashTable map = new TupleIndexHashTable( getIndexSupplier(fieldIndex), false );

        assertThat(map.size()).isEqualTo(0);

        final Cheese stilton1 = new Cheese( "stilton",
                                            35 );
        final InternalFactHandle stiltonHandle1 = new DefaultFactHandle( 1,
                                                                         stilton1 );
        map.add( new RightTuple(stiltonHandle1,
                                null ) );

        final Cheese cheddar1 = new Cheese( "cheddar",
                                            35 );
        final InternalFactHandle cheddarHandle1 = new DefaultFactHandle( 2,
                                                                         cheddar1 );
        map.add( new RightTuple(cheddarHandle1,
                                null ) );

        assertThat(map.size()).isEqualTo(2);
        assertThat(tablePopulationSize(map)).isEqualTo(2);

        final Cheese stilton2 = new Cheese( "stilton",
                                            77 );
        final InternalFactHandle stiltonHandle2 = new DefaultFactHandle( 2,
                                                                         stilton2 );
        Tuple tuple = map.getFirst( new LeftTuple( stiltonHandle2,
                                                   sink,
                                                   true ) );
        assertThat(tuple.getFactHandle()).isSameAs(stiltonHandle1);
        assertThat(tuple.getNext()).isNull();

        final Cheese cheddar2 = new Cheese( "cheddar",
                                            5 );
        final InternalFactHandle cheddarHandle2 = new DefaultFactHandle( 2,
                                                                         cheddar2 );
        tuple = map.getFirst( new LeftTuple( cheddarHandle2,
                                             sink,
                                             true ) );
        assertThat(tuple.getFactHandle()).isSameAs(cheddarHandle1);
        assertThat(tuple.getNext()).isNull();
    }

    @Test
    public void testTwoEqualEntries() throws Exception {
        final ReadAccessor extractor = store.getReader( Cheese.class,
                                                                "type" );

        final Pattern pattern = new Pattern( 0,
                                             new ClassObjectType( Cheese.class ) );

        final Declaration declaration = new Declaration( "typeOfCheese",
                                                         extractor,
                                                         pattern );

        final IndexedValueReader fieldIndex = new IndexedValueReader(declaration, new RightTupleValueExtractor(extractor));

        final TupleIndexHashTable map = new TupleIndexHashTable( getIndexSupplier(fieldIndex), false );

        assertThat(map.size()).isEqualTo(0);

        final Cheese stilton1 = new Cheese( "stilton",
                                            35 );
        final InternalFactHandle stiltonHandle1 = new DefaultFactHandle( 1,
                                                                         stilton1 );
        map.add( new RightTuple(stiltonHandle1,
                                null ) );

        final Cheese cheddar1 = new Cheese( "cheddar",
                                            35 );
        final InternalFactHandle cheddarHandle1 = new DefaultFactHandle( 2,
                                                                         cheddar1 );
        map.add( new RightTuple(cheddarHandle1,
                                null ) );

        final Cheese stilton2 = new Cheese( "stilton",
                                            81 );
        final InternalFactHandle stiltonHandle2 = new DefaultFactHandle( 3,
                                                                         stilton2 );
        map.add( new RightTuple(stiltonHandle2,
                                null ) );

        assertThat(map.size()).isEqualTo(3);
        assertThat(tablePopulationSize(map)).isEqualTo(2);

        // Check they are correctly chained to the same FieldIndexEntry
        final Cheese stilton3 = new Cheese( "stilton",
                                            89 );
        final InternalFactHandle stiltonHandle3 = new DefaultFactHandle( 4,
                                                                         stilton2 );

        BuildContext bctx = new BuildContext(new KnowledgeBaseImpl("01"), Collections.emptyList());
        MockLeftTupleSink    sink = new MockLeftTupleSink(0, bctx);

        final TupleImpl tuple = map.getFirst( new LeftTuple( stiltonHandle3,
                                                              sink,
                                                           true ) );
        assertThat(tuple.getFactHandle()).isSameAs(stiltonHandle1);
        assertThat(tuple.getNext().getFactHandle()).isSameAs(stiltonHandle2);
    }

    @Test
    public void testTwoDifferentEntriesSameHashCode() throws Exception {
        final ReadAccessor extractor = store.getReader( TestClass.class,
                                                                "object" );

        final Pattern pattern = new Pattern( 0,
                                             new ClassObjectType( TestClass.class ) );

        final Declaration declaration = new Declaration( "theObject",
                                                         extractor,
                                                         pattern );

        final IndexedValueReader fieldIndex = new IndexedValueReader(declaration, new RightTupleValueExtractor(extractor));

        final TupleIndexHashTable map = new TupleIndexHashTable(getIndexSupplier(fieldIndex), false );

        final TestClass c1 = new TestClass( 0,
                                            new TestClass( 20,
                                                           "stilton" ) );

        final InternalFactHandle ch1 = new DefaultFactHandle( 1,
                                                              c1 );

        map.add( new RightTuple(ch1,
                                null ) );

        final TestClass c2 = new TestClass( 0,
                                            new TestClass( 20,
                                                           "cheddar" ) );
        final InternalFactHandle ch2 = new DefaultFactHandle( 2,
                                                              c2 );
        map.add( new RightTuple(ch2,
                                null ) );

        // same hashcode, but different values, so it should result in  a size of 2
        assertThat(map.size()).isEqualTo(2);

        // however both are in the same table bucket
        assertThat(tablePopulationSize(map)).isEqualTo(1);

        // this table bucket will have two FieldIndexEntries, as they are actually two different values
        SingleLinkedEntry[] entries = getEntries(map);
        assertThat(entries.length).isEqualTo(1);
        TupleList list = (TupleList) entries[0];
        assertThat(list.getFirst().getFactHandle()).isSameAs(ch2);
        assertThat(list.getFirst().getNext()).isNull();

        assertThat(list.getNext().getFirst().getFactHandle()).isSameAs(ch1);
        assertThat(list.getNext().getFirst().getNext()).isNull();
        assertThat(list.getNext().getNext()).isNull();
    }

    @Test
    public void testRemove() throws Exception {
        final ReadAccessor extractor = store.getReader( Cheese.class,
                                                                "type" );

        final Pattern pattern = new Pattern( 0,
                                             new ClassObjectType( Cheese.class ) );

        final Declaration declaration = new Declaration( "typeOfCheese",
                                                         extractor,
                                                         pattern );

        final IndexedValueReader fieldIndex = new IndexedValueReader(declaration, new RightTupleValueExtractor(extractor));

        final TupleIndexHashTable map = new TupleIndexHashTable( getIndexSupplier(fieldIndex), false );

        assertThat(map.size()).isEqualTo(0);

        final Cheese stilton1 = new Cheese( "stilton",
                                            35 );
        final InternalFactHandle stiltonHandle1 = new DefaultFactHandle( 1,
                                                                         stilton1 );
        TupleImpl  stiltonRightTuple1 = new RightTuple(stiltonHandle1,
                                                       null );
        map.add( stiltonRightTuple1 );

        final Cheese cheddar1 = new Cheese( "cheddar",
                                            35 );
        final InternalFactHandle cheddarHandle1 = new DefaultFactHandle( 2,
                                                                         cheddar1 );
        TupleImpl  cheddarRightTuple1 = new RightTuple(cheddarHandle1,
                                                       null );
        map.add( cheddarRightTuple1 );

        final Cheese stilton2 = new Cheese( "stilton",
                                            81 );
        final InternalFactHandle stiltonHandle2 = new DefaultFactHandle( 3,
                                                                         stilton2 );
        TupleImpl  stiltonRightTuple2 = new RightTuple(stiltonHandle2,
                                                       null );
        map.add( stiltonRightTuple2 );

        assertThat(map.size()).isEqualTo(3);
        assertThat(tablePopulationSize(map)).isEqualTo(2);

        // cheddar is in its own bucket, which should be removed once empty. We cannot have
        // empty FieldIndexEntries in the Map, as they get their value  from the first FactEntry.
        map.remove( cheddarRightTuple1 );
        assertThat(map.size()).isEqualTo(2);
        assertThat(tablePopulationSize(map)).isEqualTo(1);

        // We remove t he stiltonHandle2, but there is still  one more stilton, so size  should be the same
        map.remove( stiltonRightTuple2 );
        assertThat(map.size()).isEqualTo(1);
        assertThat(tablePopulationSize(map)).isEqualTo(1);

        //  No more stiltons, so the table should be empty
        map.remove( stiltonRightTuple1 );
        assertThat(map.size()).isEqualTo(0);
        assertThat(tablePopulationSize(map)).isEqualTo(0);
    }

    @Test
    public void testResize() throws Exception {
        final ReadAccessor extractor = store.getReader( Cheese.class,
                                                                "type" );

        final Pattern pattern = new Pattern( 0,
                                             new ClassObjectType( Cheese.class ) );

        final Declaration declaration = new Declaration( "typeOfCheese",
                                                         extractor,
                                                         pattern );

        final IndexedValueReader fieldIndex = new IndexedValueReader(declaration, new RightTupleValueExtractor(extractor));

        final TupleIndexHashTable map = new TupleIndexHashTable( 16, 0.75f, getIndexSupplier(fieldIndex), false );

        assertThat(map.size()).isEqualTo(0);

        final Cheese stilton1 = new Cheese( "stilton",
                                            35 );
        map.add( newRightTuple( 1,
                                stilton1 ) );

        final Cheese stilton2 = new Cheese( "stilton",
                                            81 );
        map.add( newRightTuple( 2,
                                stilton2 ) );

        final Cheese cheddar1 = new Cheese( "cheddar",
                                            35 );
        map.add( newRightTuple( 3,
                                cheddar1 ) );

        final Cheese cheddar2 = new Cheese( "cheddar",
                                            38 );
        map.add( newRightTuple( 4,
                                cheddar2 ) );

        final Cheese brie = new Cheese( "brie",
                                        293 );
        map.add( newRightTuple( 5,
                                brie ) );

        final Cheese mozerella = new Cheese( "mozerella",
                                             15 );
        map.add( newRightTuple( 6,
                                mozerella ) );

        final Cheese dolcelatte = new Cheese( "dolcelatte",
                                              284 );
        map.add( newRightTuple( 7,
                                dolcelatte ) );

        final Cheese camembert1 = new Cheese( "camembert",
                                              924 );
        map.add( newRightTuple( 8,
                                camembert1 ) );

        final Cheese camembert2 = new Cheese( "camembert",
                                              765 );
        map.add( newRightTuple( 9,
                                camembert2 ) );

        final Cheese redLeicestor = new Cheese( "red leicestor",
                                                23 );
        map.add( newRightTuple( 10,
                                redLeicestor ) );

        final Cheese wensleydale = new Cheese( "wensleydale",
                                               20 );
        map.add( newRightTuple( 11,
                                wensleydale ) );

        final Cheese edam = new Cheese( "edam",
                                        12 );
        map.add( newRightTuple( 12,
                                edam ) );

        final Cheese goude1 = new Cheese( "goude",
                                          93 );
        map.add( newRightTuple( 13,
                                goude1 ) );

        final Cheese goude2 = new Cheese( "goude",
                                          88 );
        map.add( newRightTuple( 14,
                                goude2 ) );

        final Cheese gruyere = new Cheese( "gruyere",
                                           82 );
        map.add( newRightTuple( 15,
                                gruyere ) );

        final Cheese emmental = new Cheese( "emmental",
                                            98 );
        map.add( newRightTuple( 16,
                                emmental ) );

        // At this point we have 16 facts but only 12 different types of cheeses
        // so no table resize and thus its size is 16

        assertThat(map.size()).isEqualTo(16);

        SingleLinkedEntry[] table = map.getTable();
        assertThat(table.length).isEqualTo(16);

        final Cheese feta = new Cheese( "feta",
                                        48 );
        map.add( newRightTuple( 2,
                                feta ) );

        // This adds our 13th type of cheese. The map is set with an initial capacity of 16 and
        // a threshold of 75%, that after 12 it should resize the map to 32.
        assertThat(map.size()).isEqualTo(17);

        table = map.getTable();
        assertThat(table.length).isEqualTo(32);

        final Cheese haloumi = new Cheese( "haloumi",
                                           48 );
        map.add( newRightTuple( 2,
                                haloumi ) );

        final Cheese chevre = new Cheese( "chevre",
                                          48 );
        map.add( newRightTuple( 2,
                                chevre ) );

    }

    private TupleImpl  newRightTuple(int id,
                                     Object object) {
        return new RightTuple(new DefaultFactHandle(id,
                                                    object ),
                              null );

    }

    public static class TestClass {
        private int    hashCode;
        private Object object;

        public TestClass() {

        }

        public TestClass(final int hashCode,
                         final Object object) {
            this.hashCode = hashCode;
            this.object = object;
        }

        public Object getObject() {
            return this.object;
        }

        public void setObject(final Object object) {
            this.object = object;
        }

        public void setHashCode(final int hashCode) {
            this.hashCode = hashCode;
        }

        public int hashCode() {
            return this.hashCode;
        }

        public boolean equals(final Object obj) {
            if ( this == obj ) {
                return true;
            }
            if ( obj == null ) {
                return false;
            }
            if ( getClass() != obj.getClass() ) {
                return false;
            }
            final TestClass other = (TestClass) obj;

            if ( this.object == null ) {
                if ( other.object != null ) {
                    return false;
                }
            } else if ( !this.object.equals( other.object ) ) {
                return false;
            }
            return true;
        }
    }

    private int tablePopulationSize(final AbstractHashTable map) throws Exception {
        final Field field = AbstractHashTable.class.getDeclaredField( "table" );
        field.setAccessible( true );
        final SingleLinkedEntry[] array = (SingleLinkedEntry[]) field.get(map);
        int                       size  = 0;
        for ( int i = 0, length = array.length; i < length; i++ ) {
            if ( array[i] != null ) {
                size++;
            }
        }
        return size;
    }

    private SingleLinkedEntry[] getEntries(final AbstractHashTable map) throws Exception {
        final Field field = AbstractHashTable.class.getDeclaredField( "table" );
        field.setAccessible( true );
        final List list = new ArrayList();

        final SingleLinkedEntry[] array = (SingleLinkedEntry[]) field.get(map);
        for ( int i = 0, length = array.length; i < length; i++ ) {
            if ( array[i] != null ) {
                list.add( array[i] );
            }
        }
        return (SingleLinkedEntry[]) list.toArray(new SingleLinkedEntry[list.size()]);
    }

    @Test
    public void testEmptyIterator() {
        final ReadAccessor extractor = store.getReader( Cheese.class,
                                                                "type" );

        final Pattern pattern = new Pattern( 0,
                                             new ClassObjectType( Cheese.class ) );

        final Declaration declaration = new Declaration( "typeOfCheese",
                                                         extractor,
                                                         pattern );

        final IndexedValueReader fieldIndex = new IndexedValueReader(declaration, new RightTupleValueExtractor(extractor));

        final TupleIndexHashTable map = new TupleIndexHashTable(getIndexSupplier(fieldIndex), false );

        final Cheese stilton = new Cheese( "stilton",
                                           55 );
        final InternalFactHandle stiltonHandle = new DefaultFactHandle( 2,
                                                                        stilton );

        BuildContext bctx = new BuildContext(new KnowledgeBaseImpl("01"), Collections.emptyList());
        MockLeftTupleSink    sink = new MockLeftTupleSink(0, bctx);
        assertThat(map.getFirst(new LeftTuple(stiltonHandle, sink, true ))).isNull();
    }

}
