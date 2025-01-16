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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.drools.core.SessionConfiguration;
import org.drools.core.common.DefaultFactHandle;
import org.drools.core.impl.EnvironmentFactory;
import org.drools.core.impl.RuleBaseFactory;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.kiesession.rulebase.KnowledgeBaseFactory;
import org.drools.serialization.protobuf.marshalling.IdentityPlaceholderResolverStrategy;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.KieServices;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.marshalling.ObjectMarshallingStrategy;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.EnvironmentName;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.internal.marshalling.MarshallerFactory;
import org.kie.internal.runtime.StatefulKnowledgeSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class ObjectMarshallingStrategyStoreTest { 

	private class Thing{
		int id;
		String value;
		Thing( int id, String value ){
			this.id = id;
			this.value = value;
		}
		
		public boolean equals( Object thing )
		{
			return thing!= null && thing instanceof Thing && ((Thing)thing).id == this.id;
		}
		
		public String toString(){
			return "Thing:"+id+","+value;
		}
	}
	
	@Test 
	public void testThrowErrorWhenExistMultipleMarshallingStrategiesWithSameName() throws IOException, ClassNotFoundException {

		Environment env = EnvironmentFactory.newEnvironment();
		final Thing entityOne = new Thing( 1, "Object 1" );
		final Thing entityTwo = new Thing( 2, "Object 2" );
		
		Collection srcItems = new ArrayList();
		srcItems.add( entityOne ); 
		srcItems.add( entityTwo );
		
		ObjectMarshallingStrategy[] strats = new ObjectMarshallingStrategy[] {
				new IdentityPlaceholderResolverStrategy(object -> entityOne.equals(object), Collections.singletonMap(entityOne.id, entityOne)),
				new IdentityPlaceholderResolverStrategy(object -> entityTwo.equals(object), Collections.singletonMap(entityTwo.id, entityTwo)) };

		env.set(EnvironmentName.OBJECT_MARSHALLING_STRATEGIES, strats);

		KieSessionConfiguration ksc = KieServices.get().newKieSessionConfiguration().as(SessionConfiguration.KEY);

		final KieBaseConfiguration kbconf = RuleBaseFactory.newKnowledgeBaseConfiguration();

		kbconf.setOption(EventProcessingOption.STREAM);
		
		InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase(RuleBaseFactory.newRuleBase(kbconf));

		KieSession ks = kbase.newKieSession( ksc, env);
		
		
		ks.insert( entityOne ); 
		ks.insert( entityTwo );
		
		try{
			ProtobufMarshaller marshaller = (ProtobufMarshaller) MarshallerFactory.newMarshaller(kbase, strats);
			// Here ocurrs the bug that shows that NamedObjectMarshallingStrategies are required.
			fail( "A runtime error must be thrown while found strategies with same name" );
		}catch( RuntimeException re ){
            assertThat(re.getMessage().contains("Multiple")).isTrue();
		}
	}
	
	@Test 
	public void testMultipleObjectMarshallingStrategiesOfTheSameClassWithDifferentNames() throws IOException, ClassNotFoundException {

		Environment env = EnvironmentFactory.newEnvironment();
		final Thing entityOne = new Thing( 1, "Object 1" );
		final Thing entityTwo = new Thing( 2, "Object 2" );
		
		Collection srcItems = new ArrayList();
		srcItems.add( entityOne ); 
		srcItems.add( entityTwo );
		
		ObjectMarshallingStrategy[] strats = new ObjectMarshallingStrategy[] {
				new IdentityPlaceholderResolverStrategy("entityOne", object -> entityOne.equals(object), Collections.singletonMap(entityOne.id, entityOne)),
				new IdentityPlaceholderResolverStrategy("entityTwo", object -> entityTwo.equals(object), Collections.singletonMap(entityTwo.id, entityTwo)),
				};

		env.set(EnvironmentName.OBJECT_MARSHALLING_STRATEGIES, strats);

		KieSessionConfiguration ksc = KieServices.get().newKieSessionConfiguration().as(SessionConfiguration.KEY);

		final KieBaseConfiguration kbconf = RuleBaseFactory.newKnowledgeBaseConfiguration();

		kbconf.setOption(EventProcessingOption.STREAM);
		
		InternalKnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase(RuleBaseFactory.newRuleBase(kbconf));

		KieSession ks = kbase.newKieSession( ksc, env);
		
		
		ks.insert( entityOne ); 
		ks.insert( entityTwo );
		
		ProtobufMarshaller marshaller = (ProtobufMarshaller) MarshallerFactory.newMarshaller(kbase, strats);
		
		// Serialize object
		final byte[] b1;
		{
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			marshaller.marshall(bos, ks, System.currentTimeMillis());
			b1 = bos.toByteArray();
			bos.close();
		}

		// Deserialize object
		StatefulKnowledgeSession ksession2;
		{
			try (ByteArrayInputStream bais = new ByteArrayInputStream(b1)) {
				ksession2 = marshaller.unmarshall(bais, ks.getSessionConfiguration(), ks.getEnvironment());
				Collection items = ksession2.getFactHandles();
				assertThat(items.size() == 2).isTrue();
				for (Object item : items) {
					FactHandle factHandle = (FactHandle) item;
					assertThat(srcItems.contains(((DefaultFactHandle) factHandle).getObject())).isTrue();
				}
			} catch (RuntimeException npe) {
				// Here ocurrs the bug that shows that NamedObjectMarshallingStrategies are required.
				fail("This error only happens if identity ObjectMarshallingStrategy use old name");
			}
			
			
		}
	}

}
