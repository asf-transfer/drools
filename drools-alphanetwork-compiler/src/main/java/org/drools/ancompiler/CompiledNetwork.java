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
package org.drools.ancompiler;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.drools.core.common.BaseNode;
import org.drools.base.common.NetworkNode;
import org.drools.core.common.ReteEvaluator;
import org.drools.base.common.RuleBasePartitionId;
import org.drools.core.reteoo.AlphaNode;
import org.drools.core.reteoo.BetaNode;
import org.drools.core.reteoo.LeftInputAdapterNode;
import org.drools.core.reteoo.ObjectSink;
import org.drools.core.reteoo.ObjectSinkPropagator;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.WindowNode;

/**
 * This is the base type for all generated classes that that represent a "compiled" portion of the RETE network.
 * By compiled we mean IF statements, switch statements, etc. as opposed to nodes, propagators, etc.
 */
public abstract class CompiledNetwork implements ObjectSinkPropagator {

    protected ObjectTypeNode objectTypeNode;
    protected ObjectSinkPropagator originalSinkPropagator;

    /**
     * Returns the unique id that represents the node in the Rete network
     *
     * @return unique int value
     */
    public int getId() {
        return objectTypeNode.getId();
    }

    /**
     * Returns the partition ID to which this node belongs to
     *
     * @return partition id
     */
    public RuleBasePartitionId getPartitionId() {
        return objectTypeNode.getPartitionId();
    }

    /**
     * The object implements the writeExternal method to save its contents
     * by calling the methods of DataOutput for its primitive values or
     * calling the writeObject method of ObjectOutput for objects, strings,
     * and arrays.
     *
     * @param out the stream to write the object to
     * @throws java.io.IOException Includes any I/O exceptions that may occur
     * @serialData Overriding methods should use this tag to describe
     * the data layout of this Externalizable object.
     * List the sequence of element types and, if possible,
     * relate the element to a public/protected field and/or
     * method of this Externalizable class.
     */
    public void writeExternal(ObjectOutput out) throws IOException {
    }

    /**
     * The object implements the readExternal method to restore its
     * contents by calling the methods of DataInput for primitive
     * types and readObject for objects, strings and arrays.  The
     * readExternal method must read the values in the same sequence
     * and with the same types as were written by writeExternal.
     *
     * @param in the stream to read data from in order to restore the object
     * @throws java.io.IOException    if I/O errors occur
     * @throws ClassNotFoundException If the class for an object being
     *                                restored cannot be found.
     */
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
    }

    /**
     * Sets the OTN for this network. Calling this method will set all variables in the generated subclasses
     * by walking the {@link org.kie.reteoo.ObjectTypeNode} using a {@link ObjectTypeNodeParser}.
     *
     * @param objectTypeNode node for whom this network was created
     */
    public final void setObjectTypeNode(final ObjectTypeNode objectTypeNode) {
        this.objectTypeNode = objectTypeNode;

        // Use this to set all the fields from the RETE
       if(!isInlined()) {
           NodeReferenceSetter setter = new NodeReferenceSetter();
           ObjectTypeNodeParser parser = new ObjectTypeNodeParser(objectTypeNode);
           parser.accept(setter);
       }
    }

    // Sets the starting node for the evaluation of the compiled Alpha Network
    // both in the CompiledNetwork and the ObjectTypeNode itself
    public void setStartingObjectTypeNode(ObjectTypeNode objectTypeNode) {
        setObjectTypeNode(objectTypeNode);
        setOriginalSinkPropagator(objectTypeNode.getObjectSinkPropagator());
        objectTypeNode.setObjectSinkPropagator(this);
    }

    public void setOriginalSinkPropagator(ObjectSinkPropagator originalSinkPropagator) {
        this.originalSinkPropagator = originalSinkPropagator;
    }

    /*
        Use this only for testing
     */
    public ObjectSinkPropagator getOriginalSinkPropagator() {
        return originalSinkPropagator;
    }

    /**
     * Generated subclasses need to implement this method to set member variables based on the specified
     * NetworkNode.
     *
     * @param networkNode node to set to set
     */
    protected abstract void setNetworkNodeReference(NetworkNode networkNode);

    /**
     * Use to initialize the inlined expression so that the ANC can instantiate without depending on the Rete.
     * Should be used instead of #setNetworkNodeReference
     * See #isInlined
     */
    public void initConstraintsResults() { }

    protected abstract boolean isInlined();

    public NetworkHandlerAdaptor createNodeReferenceSetter() {
        return new NodeReferenceSetter();
    }

    /**
     * Handler implementation to call {@link CompiledNetwork#setNetworkNodeReference} for each node
     * encountered in the network.
     */
    private class NodeReferenceSetter extends NetworkHandlerAdaptor {

        @Override
        public void startNonHashedAlphaNode(AlphaNode alphaNode) {
            setNetworkNodeReference(alphaNode);
        }

        @Override
        public void startBetaNode(BetaNode betaNode) {
            setNetworkNodeReference(betaNode);
        }

        @Override
        public void startLeftInputAdapterNode(LeftInputAdapterNode leftInputAdapterNode) {
            setNetworkNodeReference(leftInputAdapterNode);
        }

        @Override
        public void startHashedAlphaNode(AlphaNode hashedAlpha, Object hashedValue) {
            setNetworkNodeReference(hashedAlpha);
        }

        @Override
        public void startWindowNode(WindowNode windowNode) {
            setNetworkNodeReference(windowNode);
        }
    }

    @Override
    public ObjectSinkPropagator addObjectSink(ObjectSink sink, int alphaNodeHashingThreshold, int alphaNodeRangeIndexThreshold) {
        return originalSinkPropagator.addObjectSink(sink, alphaNodeHashingThreshold, alphaNodeRangeIndexThreshold);
    }

    @Override
    public ObjectSinkPropagator removeObjectSink(ObjectSink sink) {
        return originalSinkPropagator.removeObjectSink(sink);
    }

    @Override
    public void changeSinkPartition(ObjectSink sink, RuleBasePartitionId oldPartition, RuleBasePartitionId newPartition, int alphaNodeHashingThreshold, int alphaNodeRangeIndexThreshold) {
        originalSinkPropagator.changeSinkPartition(sink, oldPartition, newPartition, alphaNodeHashingThreshold, alphaNodeRangeIndexThreshold);
    }

    @Override
    public BaseNode getMatchingNode(BaseNode candidate) {
        return originalSinkPropagator.getMatchingNode(candidate);
    }

    @Override
    public ObjectSink[] getSinks() {
        return originalSinkPropagator.getSinks();
    }

    @Override
    public int size() {
        return originalSinkPropagator.size();
    }

    @Override
    public boolean isEmpty() {
        return originalSinkPropagator.isEmpty();
    }

    @Override
    public void doLinkSubnetwork(ReteEvaluator reteEvaluator) {
        originalSinkPropagator.doLinkSubnetwork(reteEvaluator);
    }

    @Override
    public void doUnlinkSubnetwork(ReteEvaluator reteEvaluator) {
        originalSinkPropagator.doUnlinkSubnetwork(reteEvaluator);
    }

    public abstract void init(Object... args);
}
