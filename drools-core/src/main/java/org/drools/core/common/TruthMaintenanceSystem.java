/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
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

package org.drools.core.common;

import java.util.function.BiFunction;

import org.drools.core.beliefsystem.BeliefSet;
import org.drools.core.beliefsystem.BeliefSystem;
import org.drools.core.beliefsystem.ModedAssertion;
import org.drools.core.reteoo.ObjectTypeConf;
import org.drools.core.spi.Activation;
import org.drools.core.spi.PropagationContext;
import org.drools.core.spi.Tuple;
import org.drools.core.util.LinkedList;
import org.drools.core.util.ObjectHashMap;
import org.kie.api.runtime.rule.FactHandle;

public interface TruthMaintenanceSystem {

    ObjectHashMap getEqualityKeyMap();

    void put(final EqualityKey key);
    EqualityKey get(Object object);
    void remove(final EqualityKey key);

    InternalFactHandle insert(Object object, Object tmsValue, Activation activation);
    InternalFactHandle insertPositive(Object object, Activation activation);
    void delete(FactHandle fh);

    void readLogicalDependency(InternalFactHandle handle, Object object, Object value, Activation activation, ObjectTypeConf typeConf);

    void clear();

    BeliefSystem getBeliefSystem();

    InternalFactHandle insertOnTms(Object object, ObjectTypeConf typeConf, PropagationContext propagationContext,
                                   InternalFactHandle handle, BiFunction<Object, ObjectTypeConf, InternalFactHandle> fhFactory);

    void updateOnTms(InternalFactHandle handle, Object object, Activation activation);

    void deleteFromTms(InternalFactHandle handle, EqualityKey key, PropagationContext propagationContext );

    static <M extends ModedAssertion<M>> void removeLogicalDependencies(Activation<M> activation, Tuple leftTuple) {
        final LinkedList<LogicalDependency<M>> list = activation.getLogicalDependencies();
        if ( list == null || list.isEmpty() ) {
            return;
        }

        PropagationContext context = leftTuple.findMostRecentPropagationContext();

        for ( LogicalDependency<M> node = list.getFirst(); node != null; node = node.getNext() ) {
            removeLogicalDependency( node, context );
        }
        activation.setLogicalDependencies( null );
    }

    static <M extends ModedAssertion<M>> void removeLogicalDependency(final LogicalDependency<M> node, final PropagationContext context) {
        final BeliefSet<M> beliefSet = ( BeliefSet ) node.getJustified();
        beliefSet.getBeliefSystem().delete( node, beliefSet, context );
    }
}
