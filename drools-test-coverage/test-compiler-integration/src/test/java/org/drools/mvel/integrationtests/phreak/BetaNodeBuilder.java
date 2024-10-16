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
package org.drools.mvel.integrationtests.phreak;

import org.drools.core.reteoo.JoinRightAdapterNode;
import org.drools.core.reteoo.LeftTupleSource;
import org.drools.mvel.accessors.ClassFieldAccessorStore;
import org.drools.base.base.ClassObjectType;
import org.drools.core.common.BetaConstraints;
import org.drools.core.common.EmptyBetaConstraints;
import org.drools.core.common.SingleBetaConstraints;
import org.drools.core.reteoo.BetaNode;
import org.drools.core.reteoo.CoreComponentFactory;
import org.drools.core.reteoo.EntryPointNode;
import org.drools.core.reteoo.ExistsNode;
import org.drools.core.reteoo.JoinNode;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.core.reteoo.NotNode;
import org.drools.core.reteoo.ObjectSource;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.core.reteoo.builder.NodeFactory;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.Pattern;
import org.drools.base.rule.accessor.ReadAccessor;

public class BetaNodeBuilder {
    BuildContext buildContext;
    int          nodeType;
    Class        leftType = Object.class;
    Class        rightType = Object.class;
    String       leftFieldName;
    String       leftVariableName;
    String       constraintFieldName;
    String       constraintOperator;
    String       constraintVariableName;

    public BetaNodeBuilder(int nodeType, BuildContext buildContext) {
        this.nodeType = nodeType;
        this.buildContext = buildContext;
    }

    public static BetaNodeBuilder create(int nodeType, BuildContext buildContext) {
        return new BetaNodeBuilder(nodeType, buildContext);
    }

    public BetaNodeBuilder setLeftType(Class type) {
        this.leftType = type;
        return this;
    }

    public BetaNodeBuilder setRightType(Class type) {
        this.rightType = type;
        return this;
    }

    public BetaNodeBuilder setBinding(String leftFieldName,
                                      String leftVariableName) {
        this.leftFieldName = leftFieldName;
        this.leftVariableName = leftVariableName;
        return this;
    }

    public BetaNodeBuilder setConstraint(String constraintFieldName,
                                         String constraintOperator,
                                         String constraintVariableName) {
        this.constraintFieldName = constraintFieldName;
        this.constraintOperator = constraintOperator;
        this.constraintVariableName = constraintVariableName;
        return this;
    }

    public BetaNode build() {
        return build(null, null);
    }

    public BetaNode build(LeftTupleSource leftInput, ObjectSource rightInput) {
        NodeFactory nFactory = CoreComponentFactory.get().getNodeFactoryService();

        EntryPointNode epn = buildContext.getRuleBase().getRete().getEntryPointNodes().values().iterator().next();

        ObjectTypeNode otn = nFactory.buildObjectTypeNode(buildContext.getNextNodeId(),
                                                          epn,
                                                          new ClassObjectType(leftType),
                                                          buildContext);

        if (leftInput == null) {
            leftInput = nFactory.buildLeftInputAdapterNode(buildContext.getNextNodeId(), otn, buildContext, false);
        }

        if (rightInput == null) {
            rightInput = nFactory.buildObjectTypeNode(buildContext.getNextNodeId(),
                                                      epn,
                                                      new ClassObjectType(rightType),
                                                      buildContext);
        }

        ReteTesterHelper reteTesterHelper = new ReteTesterHelper();

        Pattern pattern = new Pattern(0, new ClassObjectType(leftType));

        //BetaNodeFieldConstraint betaConstraint = null;
        BetaConstraints betaConstraints;
        if (constraintFieldName != null) {
            ClassFieldAccessorStore store = (ClassFieldAccessorStore) reteTesterHelper.getStore();

            ReadAccessor extractor = store.getReader(leftType,
                                                             leftFieldName);

            Declaration declr = new Declaration(leftVariableName,
                                                extractor,
                                                pattern);
            betaConstraints = new SingleBetaConstraints(reteTesterHelper.getBoundVariableConstraint(rightType,
                                                                                                    constraintFieldName,
                                                                                                    declr,
                                                                                                    constraintOperator), buildContext.getRuleBase().getRuleBaseConfiguration());
        } else {
            betaConstraints = new EmptyBetaConstraints();
        }

        switch (nodeType) {
            case NodeTypeEnums.JoinNode:
                return new JoinNode(buildContext.getNextNodeId(), leftInput,
                                    new JoinRightAdapterNode(buildContext.getNextNodeId(), rightInput, buildContext), betaConstraints, buildContext);
            case NodeTypeEnums.NotNode:
                return new NotNode(buildContext.getNextNodeId(), leftInput,
                                   new JoinRightAdapterNode(buildContext.getNextNodeId(), rightInput, buildContext), betaConstraints, buildContext);
            case NodeTypeEnums.ExistsNode:
                return new ExistsNode(buildContext.getNextNodeId(), leftInput,
                                      new JoinRightAdapterNode(buildContext.getNextNodeId(), rightInput, buildContext), betaConstraints, buildContext);
        }
        throw new IllegalStateException("Unable to build Node");
    }

}
