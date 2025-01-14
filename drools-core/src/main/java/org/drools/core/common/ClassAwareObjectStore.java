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
package org.drools.core.common;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;

import org.drools.base.factmodel.traits.CoreWrapper;
import org.kie.api.runtime.ClassObjectFilter;
import org.kie.api.runtime.ObjectFilter;

public class ClassAwareObjectStore implements Externalizable, ObjectStore {

    private Lock lock;

    private Map<String, SingleClassStore> storesMap = new HashMap<>();
    private List<ConcreteClassStore> concreteStores = new CopyOnWriteArrayList<>();

    private FactHandleMap equalityMap;

    private boolean isEqualityBehaviour;

    private int size;

    public ClassAwareObjectStore() { }

    public ClassAwareObjectStore( boolean isEqualityBehaviour, Lock lock ) {
        this.lock = lock;
        this.isEqualityBehaviour = isEqualityBehaviour;
        if (isEqualityBehaviour) {
            this.equalityMap = new FactHandleMap(false);
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(storesMap);
        out.writeObject(concreteStores);
        out.writeObject(equalityMap);
        out.writeInt(size);
        out.writeBoolean(isEqualityBehaviour);
        out.writeObject(lock);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        storesMap = (Map<String, SingleClassStore>) in.readObject();
        concreteStores = (List<ConcreteClassStore>) in.readObject();
        equalityMap = (FactHandleMap) in.readObject();
        size = in.readInt();
        isEqualityBehaviour = in.readBoolean();
        lock = (Lock)in.readObject();
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public void clear() {
        storesMap.clear();
        concreteStores = new CopyOnWriteArrayList<>();
        if (isEqualityBehaviour) {
            equalityMap.clear();
        }
        size = 0;
    }

    @Override
    public Object getObjectForHandle(InternalFactHandle handle) {
        try {
            this.lock.lock();
            InternalFactHandle reconnectedHandle = reconnect(handle);
            return reconnectedHandle != null ? reconnectedHandle.getObject() : null;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public InternalFactHandle reconnect(InternalFactHandle handle) {
        if (handle == null) {
            return null;
        }
        String handleClass = handle.getObjectClassName();
        if (handleClass != null) {
            SingleClassStore store = getClassStore(handleClass);
            if (store == null || !store.isConcrete()) {
                return null;
            }

            return handle.isNegated() ?
                   ((ConcreteClassStore) store).getNegMap().get(handle) :
                   ((ConcreteClassStore) store).getIdentityMap().get(handle);
        }

        if (isEqualityBehaviour) {
            return equalityMap.get(handle);
        }

        for (ConcreteClassStore stores : concreteStores) {
            InternalFactHandle reconnectedHandle = stores.getAssertMap().get(handle);
            if (reconnectedHandle != null) {
                return reconnectedHandle;
            }
        }

        return null;
    }

    @Override
    public InternalFactHandle getHandleForObject(Object object) {
        if ( object == null ) {
            return null;
        }

        return isEqualityBehaviour ?
               equalityMap.get(object) :
               getOrCreateConcreteClassStore(object).getAssertMap().get(object);
    }

    @Override
    public void updateHandle(InternalFactHandle handle, Object object) {
        removeHandle(handle);
        handle.setObject(object);
        addHandle(handle, object);
    }

    @Override
    public void addHandle(InternalFactHandle handle, Object object) {
        if ( getOrCreateConcreteClassStore(object).addHandle(handle, object) ) {
            size++;
        }
    }

    @Override
    public void removeHandle(InternalFactHandle handle) {
        if ( getOrCreateConcreteClassStore(handle.getObject()).removeHandle(handle) != null ) {
            size--;
        }
    }

    @Override
    public Iterator<Object> iterateObjects() {
        return new CompositeObjectIterator(concreteStores, true);
    }

    public Iterator<Object> iterateObjects(Class<?> clazz) {
        return getOrCreateClassStore(clazz).objectsIterator(true);
    }

    @Override
    public Iterator<Object> iterateObjects(ObjectFilter filter) {
        if (filter instanceof ClassObjectFilter) {
            return getOrCreateClassStore(((ClassObjectFilter) filter).getFilteredClass()).objectsIterator(true);
        }
        return new CompositeObjectIterator(concreteStores, true, filter);
    }

    @Override
    public Iterator<InternalFactHandle> iterateFactHandles() {
        return new CompositeFactHandleIterator(concreteStores, true);
    }

    @Override
    public Iterator<InternalFactHandle> iterateFactHandles(ObjectFilter filter) {
        if (filter instanceof ClassObjectFilter) {
            return getOrCreateClassStore(((ClassObjectFilter) filter).getFilteredClass()).factHandlesIterator(true);
        }
        return new CompositeFactHandleIterator(concreteStores, true, filter);
    }

    @Override
    public Iterator<Object> iterateNegObjects(ObjectFilter filter) {
        if (filter instanceof ClassObjectFilter) {
            return getOrCreateClassStore(((ClassObjectFilter) filter).getFilteredClass()).objectsIterator(false);
        }
        return new CompositeObjectIterator(concreteStores, false, filter);
    }

    @Override
    public Iterator<InternalFactHandle> iterateNegFactHandles(ObjectFilter filter) {
        if (filter instanceof ClassObjectFilter) {
            return getOrCreateClassStore(((ClassObjectFilter) filter).getFilteredClass()).factHandlesIterator(false);
        }
        return new CompositeFactHandleIterator(concreteStores, false, filter);
    }

    @Override
    public FactHandleClassStore getStoreForClass(Class<?> clazz) {
        return getOrCreateClassStore(clazz);
    }

    @Override
    public boolean clearClassStore(Class<?> clazz) {
        return storesMap.remove( clazz.getName() ) != null;
    }

    // /////////////////////
    // /// Internal Store
    // /////////////////////

    private SingleClassStore getClassStore(String className) {
        return storesMap.get(className);
    }

    public static Class<?> getActualClass(Object object) {
        return object instanceof CoreWrapper ? ((CoreWrapper)object).getCore().getClass() : object.getClass();
    }

    public SingleClassStore getOrCreateClassStore(Class<?> clazz) {
        SingleClassStore store = storesMap.get(clazz.getName());
        if (store == null) {
            store = createClassStoreAndAddConcreteSubStores(clazz);
            storesMap.put(clazz.getName(), store);
        }
        return store;
    }

    private ConcreteClassStore getOrCreateConcreteClassStore(Object object) {
        return getOrCreateConcreteClassStore(getActualClass(object));
    }

    private ConcreteClassStore getOrCreateConcreteClassStore(Class<?> clazz) {
        SingleClassStore existingStore = getOrCreateClassStore(clazz);
        if (existingStore.isConcrete()) {
            return (ConcreteClassStore) existingStore;
        } else {
            // The existing store was abstract so has to be converted in a concrete one
            return makeStoreConcrete(existingStore);
        }
    }

    private ConcreteClassStore makeStoreConcrete(SingleClassStore storeToMakeConcrete) {
        ConcreteClassStore store = storeToMakeConcrete.makeConcrete(this.isEqualityBehaviour);
        Class<?> storedClass = storeToMakeConcrete.getStoredClass();

        for (SingleClassStore classStore : storesMap.values()) {
            if (classStore.getStoredClass().isAssignableFrom(storedClass)) {
                classStore.addConcreteStore(store);
            }
        }
        concreteStores.add(store);
        return store;
    }

    private SingleClassStore createClassStoreAndAddConcreteSubStores(Class<?> clazz) {
        SingleClassStore newStore = isEqualityBehaviour ? new ConcreteEqualityClassStore(clazz, equalityMap) : new ConcreteIdentityClassStore(clazz);
        for (SingleClassStore classStore : storesMap.values()) {
            if (classStore.isConcrete() && clazz.isAssignableFrom(classStore.getStoredClass())) {
                newStore.addConcreteStore(((ConcreteClassStore) classStore));
            }
        }
        return newStore;
    }

    public interface SingleClassStore extends Externalizable, FactHandleClassStore {
        Class<?> getStoredClass();

        void addConcreteStore(ConcreteClassStore store);

        Iterator<Object> objectsIterator(boolean assrt);
        Iterator<InternalFactHandle> factHandlesIterator(boolean assrt);

        boolean isConcrete();
        ConcreteClassStore makeConcrete(boolean isEqualityBehaviour);

        default Iterator<InternalFactHandle> iterator() {
            return factHandlesIterator(true);
        }
    }

    private abstract static class AbstractClassStore implements SingleClassStore {
        private Class<?> storedClass;
        private List<ConcreteClassStore> concreteStores = new ArrayList<>();

        public AbstractClassStore() { }

        private AbstractClassStore(Class<?> storedClass) {
            this.storedClass = storedClass;
        }

        public void addConcreteStore(ConcreteClassStore store) {
            concreteStores.add(store);
        }

        public Iterator<Object> objectsIterator(boolean assrt) {
            return new CompositeObjectIterator(concreteStores, assrt);
        }

        public Iterator<InternalFactHandle> factHandlesIterator(boolean assrt) {
            return new CompositeFactHandleIterator(concreteStores, assrt);
        }

        @Override
        public Class<?> getStoredClass() {
            return storedClass;
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject(storedClass);
            out.writeObject(concreteStores);
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            storedClass = (Class<?>)in.readObject();
            concreteStores = (List<ConcreteClassStore>)in.readObject();
        }

        @Override
        public String toString() {
            return "Object store for class: " + storedClass;
        }
    }

    private interface ConcreteClassStore extends SingleClassStore {
        boolean addHandle(InternalFactHandle handle, Object object);
        InternalFactHandle removeHandle(InternalFactHandle handle);

        FactHandleMap getAssertMap();
        FactHandleMap getIdentityMap();
        FactHandleMap getNegMap();
    }

    private static class ConcreteIdentityClassStore extends AbstractClassStore implements ConcreteClassStore {

        private FactHandleMap identityMap;

        private FactHandleMap negMap;

        public ConcreteIdentityClassStore() { }

        public ConcreteIdentityClassStore(Class<?> storedClass) {
            super(storedClass);
        }

        @Override
        public boolean addHandle(InternalFactHandle handle, Object object) {
            if ( handle.isNegated() ) {
                if (negMap == null) {
                    negMap = new FactHandleMap(true);
                }
                negMap.put(object, handle);
                return false;
            }
            return identityMap.put(object, handle) == null;
        }

        @Override
        public InternalFactHandle removeHandle(InternalFactHandle handle) {
            if ( handle.isNegated() ) {
                if (negMap == null) {
                    negMap = new FactHandleMap(true);
                }
                negMap.remove(handle);
                return null;
            }
            return identityMap.remove(handle);
        }

        @Override
        public FactHandleMap getAssertMap() {
            return identityMap;
        }

        @Override
        public FactHandleMap getNegMap() {
            return negMap;
        }

        @Override
        public FactHandleMap getIdentityMap() {
            return identityMap;
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal(out);
            out.writeObject(identityMap);
            out.writeObject(negMap);
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            super.readExternal(in);
            identityMap = (FactHandleMap)in.readObject();
            negMap = (FactHandleMap)in.readObject();
        }

        @Override
        public boolean isConcrete() {
            return identityMap != null;
        }

        @Override
        public ConcreteClassStore makeConcrete(boolean isEqualityBehaviour) {
            identityMap = new FactHandleMap(!isEqualityBehaviour);
            return this;
        }
    }

    private static class ConcreteEqualityClassStore extends ConcreteIdentityClassStore {

        private FactHandleMap equalityMap;

        public ConcreteEqualityClassStore() { }

        public ConcreteEqualityClassStore(Class<?> storedClass, FactHandleMap equalityMap) {
            super(storedClass);
            this.equalityMap = equalityMap;
        }

        @Override
        public boolean addHandle(InternalFactHandle handle, Object object) {
            boolean isNew = super.addHandle(handle, object);
            equalityMap.put(object, handle);
            return isNew;
        }

        @Override
        public InternalFactHandle removeHandle(InternalFactHandle handle) {
            InternalFactHandle removedHandle = super.removeHandle(handle);
            equalityMap.remove(handle);
            return removedHandle;
        }

        @Override
        public FactHandleMap getAssertMap() {
            return equalityMap;
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal(out);
            out.writeObject(equalityMap);
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            super.readExternal(in);
            equalityMap = (FactHandleMap)in.readObject();
        }
    }

    private static abstract class AbstractCompositeIterator<T> implements Iterator<T> {
        protected final Iterator<ConcreteClassStore> stores;
        protected final boolean assrt;
        protected final ObjectFilter filter;

        protected Iterator<T> currentIterator;
        protected T currentNext;

        private AbstractCompositeIterator(Iterable<ConcreteClassStore> stores, boolean assrt) {
            this(stores, assrt, null);
        }

        private AbstractCompositeIterator(Iterable<ConcreteClassStore> stores, boolean assrt, ObjectFilter filter) {
            this.stores = stores.iterator();
            this.assrt = assrt;
            this.filter = filter;
            fetchNext();
        }

        private void fetchNext() {
            while (currentNext == null) {
                nextIterator();
                if (currentIterator == null) {
                    break;
                }
                currentNext = currentIterator.next();
                if (filter != null && !accept()) {
                    currentNext = null;
                }
            }
        }

        private void nextIterator() {
            while (currentIterator == null || !currentIterator.hasNext()) {
                if (stores.hasNext()) {
                    fetchNextIterator();
                } else {
                    currentIterator = null;
                    break;
                }
            }
        }

        protected abstract void fetchNextIterator();

        protected abstract boolean accept();

        @Override
        public boolean hasNext() {
            return currentNext != null;
        }

        @Override
        public T next() {
            T next = currentNext;
            currentNext = null;
            fetchNext();
            return next;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static class CompositeObjectIterator extends AbstractCompositeIterator<Object> {
        private CompositeObjectIterator(Iterable<ConcreteClassStore> stores, boolean assrt) {
            super(stores, assrt);
        }

        private CompositeObjectIterator(Iterable<ConcreteClassStore> stores, boolean assrt, ObjectFilter filter) {
            super(stores, assrt, filter);
        }

        @Override
        protected void fetchNextIterator() {
            if (assrt) {
                currentIterator = stores.next().getIdentityMap().getObjects().iterator();
            } else {
                FactHandleMap negMap = stores.next().getNegMap();
                while (negMap == null && stores.hasNext()) {
                    negMap = stores.next().getNegMap();
                }
                currentIterator = negMap != null ? negMap.getObjects().iterator() : null;
            }
        }

        @Override
        protected boolean accept() {
            return filter.accept(currentNext);
        }
    }

    private static class CompositeFactHandleIterator extends AbstractCompositeIterator<InternalFactHandle> {
        private CompositeFactHandleIterator(Iterable<ConcreteClassStore> stores, boolean assrt) {
            super(stores, assrt);
        }

        private CompositeFactHandleIterator(Iterable<ConcreteClassStore> stores, boolean assrt, ObjectFilter filter) {
            super(stores, assrt, filter);
        }

        @Override
        protected void fetchNextIterator() {
            if (assrt) {
                currentIterator = stores.next().getIdentityMap().getFacts().iterator();
            } else {
                FactHandleMap negMap = stores.next().getNegMap();
                while (negMap == null && stores.hasNext()) {
                    negMap = stores.next().getNegMap();
                }
                currentIterator = negMap != null ? negMap.getFacts().iterator() : null;
            }
        }

        @Override
        protected boolean accept() {
            return filter.accept(currentNext.getObject());
        }
    }

    private static class FactHandleMap implements Externalizable {
        private Map<Object, InternalFactHandle> facts;
        private Map<Long, InternalFactHandle> factsById;

        public FactHandleMap() { }

        public FactHandleMap(boolean identity) {
            facts = identity ? new IdentityHashMap<>() : new HashMap<>();
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject(facts);
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            facts = (Map<Object, InternalFactHandle>) in.readObject();
        }

        public InternalFactHandle get(Object obj) {
            return facts.get(obj);
        }

        public InternalFactHandle put(Object obj, InternalFactHandle fh) {
            InternalFactHandle existing = facts.put(obj, fh);
            if (factsById != null) {
                factsById.put(fh.getId(), fh);
            }
            return existing;
        }

        public InternalFactHandle get(InternalFactHandle fh) {
            return fh.isDisconnected() ? factsIndexedById().get(fh.getId()) : facts.get(fh.getObject());
        }

        public InternalFactHandle remove(InternalFactHandle fh) {
            InternalFactHandle retrieved = facts.remove(fh.getObject());
            if (factsById != null) {
                factsById.remove(fh.getId());
            }
            return retrieved;
        }

        private Map<Long, InternalFactHandle> factsIndexedById() {
            if (factsById == null) {
                factsById = new HashMap<>();
                for (InternalFactHandle fh : facts.values()) {
                    factsById.put(fh.getId(), fh);
                }
            }
            return factsById;
        }

        public Collection<Object> getObjects() {
            return facts.keySet();
        }

        public Collection<InternalFactHandle> getFacts() {
            return facts.values();
        }

        public void clear() {
            facts.clear();
            factsById = null;
        }
    }
}
