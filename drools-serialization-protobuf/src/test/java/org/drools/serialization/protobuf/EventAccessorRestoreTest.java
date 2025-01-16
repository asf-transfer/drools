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
package org.drools.serialization.protobuf;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import org.drools.base.common.DroolsObjectInputStream;
import org.drools.base.common.DroolsObjectOutputStream;
import org.drools.mvel.CommonTestMethodBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kie.api.KieBase;
import org.kie.api.marshalling.Marshaller;
import org.kie.api.marshalling.ObjectMarshallingStrategy;
import org.kie.api.marshalling.ObjectMarshallingStrategyAcceptor;
import org.kie.api.runtime.KieSession;
import org.kie.internal.marshalling.MarshallerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class EventAccessorRestoreTest extends CommonTestMethodBase {

    @TempDir
	public Path temp;

    private Path kbaseFile = null;

    private KieBase kbase;

    @BeforeEach
    public void setUp() {
        String str =
                "package org.drools.test;\n" +
                "" +
                "global java.util.List list; \n" +
                "\n" +
                "declare Tick @role(event)  \n" +
                " @timestamp( time ) \n" +
                " id : int \n" +
                " time : long \n" +
                "end \n" +
                "" +
                "" +
                "rule \"Init\" when\n" +
                "   $i : Integer() \n" +
                "then\n" +
                "   Tick tick = new Tick( $i, new java.util.Date().getTime() ); \n" +
                "   insert( tick ); \n" +
                "   System.out.println( tick ); \n" +
                "   list.add( tick ); \n" +
                "end\n" +
                "";

        kbase = loadKnowledgeBaseFromString( str );

        KieSession ksession = kbase.newKieSession();

        try {
            kbaseFile = Files.createFile(temp.resolve("test.bin" ));
            FileOutputStream fos = new FileOutputStream( kbaseFile.toFile() ) ;
            saveSession( fos, ksession );
            fos.close();
        } catch ( Exception e ) {
            e.printStackTrace();
            fail( e.getMessage() );
        }
    }

    public void saveSession( FileOutputStream output, KieSession ksession ) throws IOException {
        DroolsObjectOutputStream droolsOut = new DroolsObjectOutputStream( output );
        Marshaller mas = createMarshaller(  );
        mas.marshall( droolsOut, ksession );
        droolsOut.flush();
        droolsOut.close();
    }

    private Marshaller createMarshaller( ) {
        ObjectMarshallingStrategyAcceptor acceptor = MarshallerFactory.newClassFilterAcceptor( new String[]{ "*.*" } );
        ObjectMarshallingStrategy strategy = MarshallerFactory.newSerializeMarshallingStrategy( acceptor );
        return MarshallerFactory.newMarshaller( kbase, new ObjectMarshallingStrategy[] { strategy } );
    }

    public KieSession loadSession( FileInputStream input ) throws IOException, ClassNotFoundException {
        KieSession ksession = null;
        DroolsObjectInputStream droolsIn = new DroolsObjectInputStream( input, this.getClass().getClassLoader() );
        try {
            Marshaller mas = createMarshaller();
            ksession = mas.unmarshall(droolsIn);
        } catch ( EOFException e ) {
            e.printStackTrace();
            fail( e.getMessage() );
        } finally {
            droolsIn.close();
        }
        return ksession;
    }


    @Test
    public void testDeserialization() {
        try {
            FileInputStream fis = new FileInputStream( kbaseFile.toFile() );
            KieSession knowledgeSession = loadSession( fis );

            ArrayList list = new ArrayList();
            knowledgeSession.setGlobal( "list", list );

            knowledgeSession.insert( 30 );
            knowledgeSession.fireAllRules();

            assertThat(list.size()).isEqualTo(1);
            assertThat(list.get(0).getClass().getSimpleName()).isEqualTo("Tick");

        } catch ( Exception e ) {
            e.printStackTrace();
            fail( e.getMessage() );
        }

    }

}
