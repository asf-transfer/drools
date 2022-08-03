/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.drools.ruleunits.dsl.patterns;

import org.drools.model.Index;
import org.drools.model.functions.Block3;
import org.drools.model.functions.Block4;
import org.drools.model.functions.Function1;
import org.drools.model.functions.Function2;
import org.drools.model.functions.Predicate3;

public interface Pattern3Def<A, B, C> extends PatternDef {

    Pattern3Def<A, B, C> filter(Predicate3<A, B, C> predicate);

    <V> Pattern3Def<A, B, C> filter(Function1<C, V> leftExtractor, Index.ConstraintType constraintType, Function2<A, B, V> rightExtractor);
    <V> Pattern3Def<A, B, C> filter(String fieldName, Function1<C, V> leftExtractor, Index.ConstraintType constraintType, Function2<A, B, V> rightExtractor);

    void execute(Block3<A, B, C> block);

    <G> void execute(G globalObject, Block4<G, A, B, C> block);
}
