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

import org.drools.base.common.RuleBasePartitionId;
import org.drools.base.rule.EntryPointId;
import org.drools.core.SessionConfiguration;
import org.drools.core.WorkingMemoryEntryPoint;
import org.drools.core.common.DefaultEventHandle;
import org.drools.core.common.DefaultFactHandle;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.QueryElementFactHandle;
import org.drools.core.impl.EnvironmentFactory;
import org.drools.core.impl.RuleBaseFactory;
import org.drools.core.marshalling.MarshallerReaderContext;
import org.drools.core.reteoo.CoreComponentFactory;
import org.drools.core.reteoo.EntryPointNode;
import org.drools.core.reteoo.ObjectTypeConf;
import org.drools.core.reteoo.Rete;
import org.drools.core.reteoo.builder.NodeFactory;
import org.drools.kiesession.entrypoints.NamedEntryPoint;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.kiesession.rulebase.KnowledgeBaseFactory;
import org.drools.kiesession.session.StatefulKnowledgeSessionImpl;
import org.drools.mvel.compiler.Person;
import org.drools.serialization.protobuf.marshalling.ObjectMarshallingStrategyStoreImpl;
import org.drools.util.StringUtils;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.marshalling.ObjectMarshallingStrategy;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.RuleRuntime;
import org.kie.internal.marshalling.MarshallerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class FactHandleMarshallingTest {

    private InternalKnowledgeBase createKnowledgeBase() {
        KieBaseConfiguration config = RuleBaseFactory.newKnowledgeBaseConfiguration();
        config.setOption( EventProcessingOption.STREAM );
        return KnowledgeBaseFactory.newKnowledgeBase( config );
    }
    
    private InternalFactHandle createEventFactHandle(StatefulKnowledgeSessionImpl wm, InternalKnowledgeBase kBase) {
        // EntryPointNode
        Rete rete = kBase.getRete();

        NodeFactory nFacotry = CoreComponentFactory.get().getNodeFactoryService();

        RuleBasePartitionId partionId = RuleBasePartitionId.MAIN_PARTITION;
        EntryPointNode entryPointNode = nFacotry.buildEntryPointNode(1, partionId, rete , EntryPointId.DEFAULT);
        WorkingMemoryEntryPoint wmEntryPoint = new NamedEntryPoint( EntryPointId.DEFAULT, entryPointNode, wm);

        DefaultEventHandle factHandle = new DefaultEventHandle(1, new Person(), 0, (new Date()).getTime(), 0, wmEntryPoint);
        
        return factHandle;
    }
       
    private StatefulKnowledgeSessionImpl createWorkingMemory(InternalKnowledgeBase kBase) {
        // WorkingMemoryEntryPoint
        KieSessionConfiguration ksconf = RuleBaseFactory.newKnowledgeSessionConfiguration();
        ksconf.setOption( ClockTypeOption.PSEUDO );
        SessionConfiguration sessionConf = ksconf.as(SessionConfiguration.KEY);
        StatefulKnowledgeSessionImpl wm = new StatefulKnowledgeSessionImpl(1L, kBase, true, sessionConf, EnvironmentFactory.newEnvironment());
        
        return wm;
    }
    
    @Test
    public void backwardsCompatibleEventFactHandleTest() throws IOException, ClassNotFoundException {
        InternalKnowledgeBase kBase = createKnowledgeBase();
        StatefulKnowledgeSessionImpl wm = createWorkingMemory(kBase);
        InternalFactHandle factHandle = createEventFactHandle(wm, kBase);
        
        // marshall/serialize workItem
        byte [] byteArray;
        {
            ObjectMarshallingStrategy[] strats 
                = new ObjectMarshallingStrategy[] { 
                    MarshallerFactory.newSerializeMarshallingStrategy(), 
                    new MarshallerProviderImpl().newIdentityMarshallingStrategy() };
    
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ProtobufMarshallerWriteContext outContext = new ProtobufMarshallerWriteContext( baos, null, null, null,
                    new ObjectMarshallingStrategyStoreImpl(strats), true, true, null);
            OldOutputMarshallerMethods.writeFactHandle_v1(outContext, outContext,
                                                          outContext.getObjectMarshallingStrategyStore(), 2, factHandle);
            outContext.close();
            byteArray = baos.toByteArray();
        }
        
        // unmarshall/deserialize workItem
        InternalFactHandle newFactHandle;
        {
            // Only put serialization strategy in 
            ObjectMarshallingStrategy[] newStrats 
                = new ObjectMarshallingStrategy[] { 
                    MarshallerFactory.newSerializeMarshallingStrategy()  };
    
            ByteArrayInputStream bais = new ByteArrayInputStream(byteArray);
            ProtobufMarshallerReaderContext inContext = new ProtobufMarshallerReaderContext( bais, null, null,
                new ObjectMarshallingStrategyStoreImpl(newStrats), Collections.EMPTY_MAP,
                true, true, null);
            inContext.setWorkingMemory( wm );
            newFactHandle = readFactHandle(inContext);
            inContext.close();
        }

        assertThat(compareInstances(factHandle, newFactHandle)).as("Serialized FactHandle not the same as the original.").isTrue();
    }

    private static InternalFactHandle readFactHandle( MarshallerReaderContext context ) throws IOException,
            ClassNotFoundException {
        int type = context.readInt();
        long id = context.readLong();
        long recency = context.readLong();

        long startTimeStamp = 0;
        long duration = 0;
        boolean expired = false;
        if (type == 2) {
            startTimeStamp = context.readLong();
            duration = context.readLong();
            expired = context.readBoolean();
        }

        int strategyIndex = context.readInt();
        ObjectMarshallingStrategy strategy = null;
        // This is the old way of de/serializing strategy objects
        if (strategyIndex >= 0) {
            strategy = context.getResolverStrategyFactory().getStrategy( strategyIndex );
        }
        // This is the new way
        else if (strategyIndex == -2) {
            String strategyClassName = context.readUTF();
            if (!StringUtils.isEmpty( strategyClassName )) {
                strategy = context.getResolverStrategyFactory().getStrategyObject( strategyClassName );
                if (strategy == null) {
                    throw new IllegalStateException( "No strategy of type " + strategyClassName + " available." );
                }
            }
        }

        // If either way retrieves a strategy, use it
        Object object = null;
        if (strategy != null) {
            object = strategy.read( (ObjectInputStream) context );
        }

        EntryPoint entryPoint = null;
        if (context.readBoolean()) {
            String entryPointId = context.readUTF();
            if (entryPointId != null && !entryPointId.equals( "" )) {
                entryPoint = ((RuleRuntime)context.getWorkingMemory()).getEntryPoint( entryPointId );
            }
        }

        EntryPointId confEP;
        if ( entryPoint != null ) {
            confEP = ((NamedEntryPoint) entryPoint).getEntryPoint();
        } else {
            confEP = context.getWorkingMemory().getEntryPoint();
        }
        ObjectTypeConf typeConf = context.getWorkingMemory().getObjectTypeConfigurationRegistry().getOrCreateObjectTypeConf( confEP, object );


        InternalFactHandle handle;
        switch (type) {
            case 0: {

                handle = new DefaultFactHandle( id,
                        object,
                        recency,
                        (WorkingMemoryEntryPoint) entryPoint );
                break;

            }
            case 1: {
                handle = new QueryElementFactHandle( object,
                        id,
                        recency );
                break;
            }
            case 2: {
                handle = new DefaultEventHandle(id, object, recency, startTimeStamp, duration,
                                                (WorkingMemoryEntryPoint) entryPoint );
                ( (DefaultEventHandle) handle ).setExpired(expired);
                break;
            }
            default: {
                throw new IllegalStateException( "Unable to marshal FactHandle, as type does not exist:" + type );
            }
        }

        return handle;
    }

    private boolean compareInstances(Object objA, Object objB) {
        boolean same = true;
        if( objA != null && objB != null ) { 
            if( ! objA.getClass().equals(objB.getClass()) ) { 
                return false;
            }
            String className = objA.getClass().getName();
            if( className.startsWith("java") ) { 
                return objA.equals(objB);
            }
            
            try { 
                Field [] fields = objA.getClass().getDeclaredFields();
                if( fields.length == 0 ) { 
                    same = true;
                }
                else { 
                    for( int i = 0; same && i < fields.length; ++i ) { 
                        fields[i].setAccessible(true);
                        Object subObjA = fields[i].get(objA);
                        Object subObjB = fields[i].get(objB);
                        if( ! compareInstances(subObjA, subObjB) ) { 
                           return false; 
                        }
                    }
                }
            }
            catch( Exception e ) { 
                same = false;
                fail(e.getClass().getSimpleName() + ":" + e.getMessage() );
            }
        }
        else if( objA != objB ) { 
            return false;
        }
        
        return same;
    }
}
