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
package org.drools.persistence;

import org.drools.core.impl.EnvironmentFactory;
import org.drools.persistence.api.TransactionManager;
import org.drools.persistence.api.TransactionManagerFactory;
import org.drools.persistence.jta.JtaTransactionManager;
import org.drools.persistence.jta.JtaTransactionManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kie.api.runtime.Environment;

import static org.assertj.core.api.Assertions.assertThat;

public class TransactionManagerFactoryTest {

    TransactionManagerFactory transactionManagerFactory =
            getTransactionManagerFactory();

    @AfterEach
    public void cleanup() {
        System.clearProperty("org.kie.txm.factory.class");
        transactionManagerFactory.resetInstance();
    }

    @Test
    public void defaultsToJtaTransactionManagerFactory() throws Exception {

        assertThat(transactionManagerFactory.get() instanceof JtaTransactionManagerFactory).isTrue();
    }

    @Test
    public void createsSystemPropertySpecifiedFactory() throws Exception {
        System.setProperty("org.kie.txm.factory.class",
                           TestTransactionManagerFactory.class.getName());
        transactionManagerFactory.resetInstance();

        assertThat(transactionManagerFactory.get().getClass().getName()).isEqualTo(TestTransactionManagerFactory.class.getName());
    }

    @Test
    public void createAndResetSystemPropertySpecifiedFactory() throws Exception {
        System.setProperty("org.kie.txm.factory.class",
                           TestTransactionManagerFactory.class.getName());

        transactionManagerFactory.resetInstance();

        assertThat(transactionManagerFactory.get().getClass().getName()).isEqualTo(TestTransactionManagerFactory.class.getName());

        System.setProperty("org.kie.txm.factory.class",
                           TestTransactionManagerFactoryTwo.class.getName());

        transactionManagerFactory.resetInstance();

        assertThat(transactionManagerFactory.get().getClass().getName()).isEqualTo(TestTransactionManagerFactoryTwo.class.getName());
        transactionManagerFactory.resetInstance();
    }

    @Test
    public void createsJtaTransactionManager() throws Exception {
        assertThat(transactionManagerFactory.newTransactionManager().getClass().getName()).isEqualTo(JtaTransactionManager.class.getName());
    }

    @Test
    public void createsJtaTransactionManagerWithEnvironment() throws Exception {
        Environment env = EnvironmentFactory.newEnvironment();

        assertThat(transactionManagerFactory.get().getClass().getName()).isEqualTo(JtaTransactionManagerFactory.class.getName());

        assertThat(transactionManagerFactory.newTransactionManager(env)).isNotNull();

        assertThat(transactionManagerFactory.newTransactionManager(env).getClass().getName()).isEqualTo(JtaTransactionManager.class.getName());
    }

    private TransactionManagerFactory getTransactionManagerFactory() {
        try {
            Class<?> factoryClazz = this.getClass().getClassLoader().loadClass("org.drools.persistence.api.TransactionManagerFactory");
            return (TransactionManagerFactory) factoryClazz.getMethod("get").invoke(null);
        } catch (Exception e) {
            return null;
        }
    }

    public static final class TestTransactionManagerFactory extends TransactionManagerFactory {

        @Override
        public TransactionManager newTransactionManager() {
            return null;
        }

        @Override
        public TransactionManager newTransactionManager(Environment environment) {
            return null;
        }
    }

    public static final class TestTransactionManagerFactoryTwo extends TransactionManagerFactory {

        @Override
        public TransactionManager newTransactionManager() {
            return null;
        }

        @Override
        public TransactionManager newTransactionManager(Environment environment) {
            return null;
        }
    }
}

