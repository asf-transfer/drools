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
package org.drools.beliefs.bayes;

import org.drools.beliefs.graph.Graph;
import org.drools.beliefs.graph.GraphNode;
import org.drools.util.bitmask.OpenBitSet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.drools.beliefs.bayes.GraphTest.addNode;
import static org.drools.beliefs.bayes.GraphTest.assertLinkedNode;
import static org.drools.beliefs.bayes.GraphTest.assertLinkedVertex;
import static org.drools.beliefs.bayes.GraphTest.bitSet;
import static org.drools.beliefs.bayes.GraphTest.connectChildToParents;
import static org.drools.beliefs.bayes.GraphTest.connectParentToChildren;

public class JunctionTreeBuilderTest {
    @Test
    public void testOpenBitSet() {
        OpenBitSet b1 = bitSet("00000111");
        OpenBitSet b2 = bitSet("00000111");
        OpenBitSet b3 = bitSet("00000110");
        OpenBitSet b4 = bitSet("00001110");

        assertThat(OpenBitSet.andNotCount(b1, b2)).isEqualTo(0); // b1 and b3 are equal

        assertThat(OpenBitSet.andNotCount(b2, b3)).isEqualTo(1); // b2 is not a subset of b3
        assertThat(OpenBitSet.andNotCount(b3, b2)).isEqualTo(0); // b3 is a subset of b2

        assertThat(OpenBitSet.andNotCount(b2, b4)).isEqualTo(1); // b2 is not a subset of b4
        assertThat(OpenBitSet.andNotCount(b4, b2)).isEqualTo(1); // b4 is not a subset of b3
    }

    @Test
    public void testMoralize1() {
        Graph<BayesVariable> graph = new BayesNetwork();

        GraphNode x0 = addNode(graph);
        GraphNode x1 = addNode(graph);
        GraphNode x2 = addNode(graph);
        GraphNode x3 = addNode(graph);
        GraphNode x4 = addNode(graph);

        connectParentToChildren(x2, x1);
        connectParentToChildren(x3, x1);
        connectParentToChildren(x4, x1);

        JunctionTreeBuilder jtBuilder = new JunctionTreeBuilder( graph );

        assertLinkedNode(jtBuilder, 1, 2, 3, 4);
        assertLinkedNode(jtBuilder, 2, 1);
        assertLinkedNode(jtBuilder, 3, 1);
        assertLinkedNode(jtBuilder, 4, 1);

        jtBuilder.moralize();

        assertLinkedNode(jtBuilder, 1, 2, 3, 4);
        assertLinkedNode(jtBuilder, 2, 1, 3, 4);
        assertLinkedNode(jtBuilder, 3, 1, 2, 4);
        assertLinkedNode(jtBuilder, 4, 1, 2, 3);
    }

    @Test
    public void testMoralize2() {
        Graph<BayesVariable> graph = new BayesNetwork();

        GraphNode x0 = addNode(graph);
        GraphNode x1 = addNode(graph);
        GraphNode x2 = addNode(graph);
        GraphNode x3 = addNode(graph);
        GraphNode x4 = addNode(graph);
        GraphNode x5 = addNode(graph);
        GraphNode x6 = addNode(graph);

        connectParentToChildren(x1, x2, x3);
        connectParentToChildren(x2, x4);
        connectParentToChildren(x4, x5);
        connectParentToChildren(x3, x5);
        connectParentToChildren(x6, x5);

        JunctionTreeBuilder jtBuilder = new JunctionTreeBuilder( graph );
        jtBuilder.moralize();

        assertLinkedNode(jtBuilder, x1.getId(), 2, 3);

        assertLinkedNode(jtBuilder, x2.getId(), 1, 4);

        assertLinkedNode(jtBuilder, x3.getId(), 1, 4, 5, 6);

        assertLinkedNode(jtBuilder, x4.getId(), 2, 3, 5, 6);

        assertLinkedNode(jtBuilder, x5.getId(), 3, 4, 6);

        assertLinkedNode(jtBuilder, x6.getId(), 3, 4, 5);

    }

    @Test
    public void testEliminationCandidate1() {
        Graph<BayesVariable> graph = new BayesNetwork();

        GraphNode x0 = addNode(graph);
        GraphNode x1 = addNode(graph);
        GraphNode x2 = addNode(graph);
        GraphNode x3 = addNode(graph);
        GraphNode x4 = addNode(graph);

        connectParentToChildren(x1, x2, x3, x4);

        JunctionTreeBuilder jtBuilder = new JunctionTreeBuilder( graph );
        jtBuilder.moralize();

        EliminationCandidate vt1 = new EliminationCandidate(graph, jtBuilder.getAdjacencyMatrix(), x1 );

        assertThat(vt1.getNewEdgesRequired()).isEqualTo(3);

        assertThat(vt1.getCliqueBitSit()).isEqualTo(bitSet("11110"));
    }

    @Test
    public void testEliminationCandidate2() {
        Graph graph = new BayesNetwork();
        GraphNode x0 = addNode(graph);
        GraphNode x1 = addNode(graph);
        GraphNode x2 = addNode(graph);
        GraphNode x3 = addNode(graph);
        GraphNode x4 = addNode(graph);

        connectParentToChildren(x1, x2, x3, x4);
        connectParentToChildren(x3, x4);

        JunctionTreeBuilder jtBuilder = new JunctionTreeBuilder( graph );
        jtBuilder.moralize();

        EliminationCandidate vt1 = new EliminationCandidate(graph, jtBuilder.getAdjacencyMatrix(), x1 );

        assertThat(vt1.getNewEdgesRequired()).isEqualTo(2);
        assertThat(vt1.getCliqueBitSit()).isEqualTo(bitSet("11110"));
    }

    @Test
    public void testCreateClique() {
        Graph<BayesVariable> graph = new BayesNetwork();

        GraphNode dX0 = addNode(graph);
        GraphNode dX1 = addNode(graph);
        GraphNode dX2 = addNode(graph);
        GraphNode dX3 = addNode(graph);
        GraphNode dX4 = addNode(graph);
        GraphNode dX5 = addNode(graph);

        connectParentToChildren(dX1, dX2, dX3, dX4);

        JunctionTreeBuilder jtBuilder = new JunctionTreeBuilder( graph );
        // do not moralize, as we want to test just the clique creation through elimination of the provided vertices

        Set<Integer> vertices = new HashSet<Integer>();
        boolean[] adjList = new boolean[] { false, false, true, true, true, false };

        boolean[][] clonedAdjMatrix = JunctionTreeBuilder.cloneAdjacencyMarix(jtBuilder.getAdjacencyMatrix());
        jtBuilder.createClique(dX1.getId(), clonedAdjMatrix, vertices, adjList );

        assertThat(vertices).hasSize(3);
        assertThat(vertices).containsExactlyInAnyOrder(2, 3, 4);

        assertLinkedNode(jtBuilder, 1, 2, 3, 4);
        assertLinkedNode(jtBuilder, 2, 1, 3, 4);
        assertLinkedNode(jtBuilder, 3, 1, 2, 4);
        assertLinkedNode(jtBuilder, 4, 1, 2, 3);
    }

    @Test
    public void testCliqueSuperSet() {
        Graph<BayesVariable> graph = new BayesNetwork();
        JunctionTreeBuilder jtBuilder = new JunctionTreeBuilder( graph );

        List<OpenBitSet> cliques = new ArrayList<OpenBitSet>();
        OpenBitSet OpenBitSet1 = bitSet("00011110");
        jtBuilder.updateCliques(cliques, OpenBitSet1);
        assertThat(cliques).hasSize(1);

        // ignore subset
        OpenBitSet OpenBitSet2 = bitSet("00000110");
        jtBuilder.updateCliques(cliques, OpenBitSet2);
        assertThat(cliques).hasSize(1);
        assertThat(cliques.get(0)).isEqualTo(OpenBitSet1);

        // add overlapping, as not a pure subset
        OpenBitSet OpenBitSet3 = bitSet("01000110");
        jtBuilder.updateCliques(cliques, OpenBitSet3);
        assertThat(cliques).hasSize(2);
        assertThat(cliques.get(0)).isEqualTo(OpenBitSet1);
        assertThat(cliques.get(1)).isEqualTo(OpenBitSet3);
    }

    @Test
    public void testPriorityQueueWithMinimalNewEdges() {
        Graph<BayesVariable> graph = new BayesNetwork();

        GraphNode x0 = addNode(graph);
        GraphNode x1 = addNode(graph);
        GraphNode x2 = addNode(graph);
        GraphNode x3 = addNode(graph);
        GraphNode x4 = addNode(graph);

        GraphNode x5 = addNode(graph);
        GraphNode x6 = addNode(graph);
        GraphNode x7 = addNode(graph);
        GraphNode x8 = addNode(graph);

        GraphNode x9 = addNode(graph);
        GraphNode x10 = addNode(graph);
        GraphNode x11 = addNode(graph);
        GraphNode x12 = addNode(graph);

        // 3 new edges
        connectParentToChildren(x2, x1);
        connectParentToChildren(x3, x1);
        connectParentToChildren(x4, x1);

        // 1 new edge
        // we give this a high weight, to show required new edges is compared first
        connectParentToChildren(x6, x5);
        connectParentToChildren(x7, x5);
        x5.setContent(new BayesVariable<String>("x5", x0.getId(), new String[]{"a", "b", "c"}, new double[][]{{0.1, 0.1, 0.1}}));
        x6.setContent(new BayesVariable<String>("x6", x0.getId(), new String[]{"a", "b", "c"}, new double[][]{{0.1, 0.1, 0.1}}));
        x7.setContent(new BayesVariable<String>("x7", x0.getId(), new String[]{"a", "b", "c"}, new double[][]{{0.1, 0.1, 0.1}}));

        // 6 new edges
        connectParentToChildren(x9, x8);
        connectParentToChildren(x10, x8);
        connectParentToChildren(x11, x8);
        connectParentToChildren(x12, x8);

        JunctionTreeBuilder jtBuilder = new JunctionTreeBuilder( graph );
        //jtBuilder.moralize(); // don't moralize, as we want to force a simpler construction for required edges, for the purposes of testing

        PriorityQueue<EliminationCandidate> p = new PriorityQueue<EliminationCandidate>(graph.size());

        EliminationCandidate elmCandVert = new EliminationCandidate(graph, jtBuilder.getAdjacencyMatrix(), x1);
        p.add( elmCandVert );

        elmCandVert = new EliminationCandidate(graph, jtBuilder.getAdjacencyMatrix(), x5);
        p.add( elmCandVert );

        elmCandVert = new EliminationCandidate(graph, jtBuilder.getAdjacencyMatrix(), x8);
        p.add( elmCandVert );

        EliminationCandidate v = p.remove();
        int id = v.getV().getId();
        assertThat(id).isEqualTo(5);
        assertThat(v.getNewEdgesRequired()).isEqualTo(1);

        v = p.remove();
        id = v.getV().getId();
        assertThat(id).isEqualTo(1);
        assertThat(v.getNewEdgesRequired()).isEqualTo(3);

        v = p.remove();
        id = v.getV().getId();
        assertThat(id).isEqualTo(8);
        assertThat(v.getNewEdgesRequired()).isEqualTo(6);

        assertThat(p).isEmpty();
    }

    @Test
    public void testPriorityQueueWithMaximalCliqueWeight() {
        Graph<BayesVariable> graph = new BayesNetwork();

        GraphNode x0 = addNode(graph);
        GraphNode x1 = addNode(graph);
        GraphNode x2 = addNode(graph);
        GraphNode x3 = addNode(graph);
        GraphNode x4 = addNode(graph);
        GraphNode x5 = addNode(graph);
        GraphNode x6 = addNode(graph);
        GraphNode x7 = addNode(graph);
        GraphNode x8 = addNode(graph);
        GraphNode x9 = addNode(graph);
        GraphNode x10 = addNode(graph);
        GraphNode x11 = addNode(graph);
        GraphNode x12 = addNode(graph);

        connectParentToChildren(x2, x1);
        connectParentToChildren(x3, x1);
        connectParentToChildren(x4, x1);
        x1.setContent(new BayesVariable<String>("x1", x0.getId(), new String[]{"a", "b"}, new double[][]{{0.1, 0.1}}));
        x2.setContent(new BayesVariable<String>("x2", x0.getId(), new String[]{"a", "b"}, new double[][]{{0.1, 0.1}}));
        x3.setContent(new BayesVariable<String>("x3", x0.getId(), new String[]{"a", "b"}, new double[][]{{0.1, 0.1}}));
        x4.setContent(new BayesVariable<String>("x4", x0.getId(), new String[]{"a", "b"}, new double[][]{{0.1, 0.1}}));

        connectParentToChildren(x6, x5);
        connectParentToChildren(x7, x5);
        connectParentToChildren(x8, x5);
        x5.setContent(new BayesVariable<String>("x5", x0.getId(), new String[]{"a", "b", "c"}, new double[][]{{0.1, 0.1, 0.1}}));
        x6.setContent(new BayesVariable<String>("x6", x0.getId(), new String[]{"a", "b", "c"}, new double[][]{{0.1, 0.1, 0.1}}));
        x7.setContent(new BayesVariable<String>("x7", x0.getId(), new String[]{"a", "b", "c"}, new double[][]{{0.1, 0.1, 0.1}}));
        x8.setContent(new BayesVariable<String>("x8", x0.getId(), new String[]{"a", "b", "c"}, new double[][]{{0.1, 0.1, 0.1}}));


        connectParentToChildren(x10, x9);
        connectParentToChildren(x11, x9);
        connectParentToChildren(x12, x9);
        x9.setContent(new BayesVariable<String>("x9", x0.getId(), new String[]{"a"}, new double[][]{{0.1}}));
        x10.setContent(new BayesVariable<String>("x10", x0.getId(), new String[]{"a"}, new double[][]{{0.1}}));
        x11.setContent(new BayesVariable<String>("x11", x0.getId(), new String[]{"a", "b"}, new double[][]{{0.1, 0.1}}));
        x12.setContent(new BayesVariable<String>("x12", x0.getId(), new String[]{"a", "b"}, new double[][]{{0.1, 0.1}}));

        JunctionTreeBuilder jtBuilder = new JunctionTreeBuilder( graph );
        //jtBuilder.moralize(); // don't moralize, as we want to force a simpler construction for required edges, for the purposes of testing

        PriorityQueue<EliminationCandidate> p = new PriorityQueue<EliminationCandidate>(graph.size());

        EliminationCandidate elmCandVert = new EliminationCandidate(graph, jtBuilder.getAdjacencyMatrix(), x1);
        p.add( elmCandVert );

        elmCandVert = new EliminationCandidate(graph, jtBuilder.getAdjacencyMatrix(), x5);
        p.add( elmCandVert );

        elmCandVert = new EliminationCandidate(graph, jtBuilder.getAdjacencyMatrix(), x9);
        p.add( elmCandVert );

        EliminationCandidate v = p.remove();
        int id = v.getV().getId();
        assertThat(id).isEqualTo(9);
        assertThat(v.getWeightRequired()).isEqualTo(4);

        v = p.remove();
        id = v.getV().getId();
        assertThat(id).isEqualTo(1);
        assertThat(v.getWeightRequired()).isEqualTo(16);

        v = p.remove();
        id = v.getV().getId();
        assertThat(id).isEqualTo(5);
        assertThat(v.getWeightRequired()).isEqualTo(81);

        assertThat(p).isEmpty();
    }

    @Test
    public void testIterativeEliminationUsingEdgeAndWeight() {
        Graph<BayesVariable> graph = new BayesNetwork();

        GraphNode x0 = addNode(graph);
        GraphNode x1 = addNode(graph);
        GraphNode x2 = addNode(graph);
        GraphNode x3 = addNode(graph);
        GraphNode x4 = addNode(graph);

        GraphNode x5 = addNode(graph);
        GraphNode x6 = addNode(graph);

        //          *
        //        / | \
        //       *  | *
        //       |  | |
        //       *  | *
        //        \  /
        //         *

        connectParentToChildren(x1, x2);
        connectParentToChildren(x1, x3);
        connectParentToChildren(x1, x6);

        connectParentToChildren(x2, x4);
        connectParentToChildren(x3, x5);

        connectParentToChildren(x4, x6);
        connectParentToChildren(x5, x6);

        // need to ensure x5 followed by x4 are removed first
        x1.setContent(new BayesVariable<String>("x1", x0.getId(), new String[]{"a", "b", "c", "d", "e", "f"}, new double[][]{{0.1, 0.1, 0.1, 0.1, 0.1, 0.1}}));
        x2.setContent(new BayesVariable<String>("x2", x0.getId(), new String[]{"a", "b"}, new double[][]{{0.1, 0.1}}));
        x3.setContent(new BayesVariable<String>("x3", x0.getId(), new String[]{"a", "b"}, new double[][]{{0.1, 0.1}}));
        x4.setContent(new BayesVariable<String>("x4", x0.getId(), new String[]{"a", "b"}, new double[][]{{0.1, 0.1}}));
        x5.setContent(new BayesVariable<String>("x5", x0.getId(), new String[]{"a"}, new double[][]{{0.1 }}));
        x6.setContent(new BayesVariable<String>("x6", x0.getId(), new String[]{"a", "b"}, new double[][]{{0.1, 0.1}}));


        JunctionTreeBuilder jtBuilder = new JunctionTreeBuilder( graph );
        //jtBuilder.moralize(); // don't moralize, as we want to force a simpler construction for vertex elimination order and updates

        boolean[][] clonedAdjMatrix = jtBuilder.cloneAdjacencyMarix(jtBuilder.getAdjacencyMatrix());
        PriorityQueue<EliminationCandidate> p = new PriorityQueue<EliminationCandidate>(graph.size());
        Map<Integer, EliminationCandidate> elmVertMap = new HashMap<Integer, EliminationCandidate>();

        for ( GraphNode<BayesVariable> v : graph ) {
            if   ( v.getId() == 0 ) {
                continue;
            }
            EliminationCandidate elmCandVert = new EliminationCandidate(graph, clonedAdjMatrix, v);
            p.add( elmCandVert );
            elmVertMap.put( v.getId(), elmCandVert );
        }

        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 1, 2, 3, 6);
        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 2, 1, 4);
        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 3, 1, 5);
        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 4, 2, 6);
        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 5, 3, 6);
        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 6, 1, 4, 5);

        assertThat(elmVertMap.get(1).getNewEdgesRequired()).isEqualTo(3);
        assertThat(elmVertMap.get(2).getNewEdgesRequired()).isEqualTo(1);
        assertThat(elmVertMap.get(3).getNewEdgesRequired()).isEqualTo(1);
        assertThat(elmVertMap.get(4).getNewEdgesRequired()).isEqualTo(1);
        assertThat(elmVertMap.get(5).getNewEdgesRequired()).isEqualTo(1);
        assertThat(elmVertMap.get(6).getNewEdgesRequired()).isEqualTo(3);

        // 5 has the lowest new edges and weight
        EliminationCandidate v = p.remove();
        int id = v.getV().getId();
        assertThat(id).isEqualTo(5);
        Set<Integer> verticesToUpdate = new HashSet<Integer>();
        boolean[] adjList = clonedAdjMatrix[ id  ];
        jtBuilder.createClique(5, clonedAdjMatrix, verticesToUpdate, adjList);
        assertThat(verticesToUpdate).hasSize(4);
        assertThat(verticesToUpdate).contains(1, 3, 6);
        jtBuilder.eliminateVertex(p, elmVertMap, clonedAdjMatrix, adjList, verticesToUpdate, v );

        // assert all new edges
        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 1, 2, 3, 6);
        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 2, 1, 4);
        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 3, 1, 5, 6);
        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 4, 2, 6);
        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 5, 3, 6);
        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 6, 1, 3, 4, 5);

        // assert new edges were correctly recalculated
        assertThat(elmVertMap.get(1).getNewEdgesRequired()).isEqualTo(2);
        assertThat(elmVertMap.get(2).getNewEdgesRequired()).isEqualTo(1);
        assertThat(elmVertMap.get(3).getNewEdgesRequired()).isEqualTo(0);
        assertThat(elmVertMap.get(6).getNewEdgesRequired()).isEqualTo(2);
        assertThat(elmVertMap.get(4).getNewEdgesRequired()).isEqualTo(1);

        // 3 next as it has no new edges now, after recalculation
        v = p.remove();
        id = v.getV().getId();
        assertThat(id).isEqualTo(3);
        verticesToUpdate = new HashSet<Integer>();
        jtBuilder.createClique(3, clonedAdjMatrix, verticesToUpdate, adjList);
        jtBuilder.eliminateVertex(p, elmVertMap, clonedAdjMatrix, adjList, verticesToUpdate, v );

        // 4 is next
        v = p.remove();
        id = v.getV().getId();
        assertThat(id).isEqualTo(4);
        verticesToUpdate = new HashSet<Integer>();
        adjList = clonedAdjMatrix[ id  ];
        jtBuilder.createClique(4, clonedAdjMatrix, verticesToUpdate, adjList);
        assertThat(verticesToUpdate).hasSize(3);
        assertThat(verticesToUpdate).containsExactlyInAnyOrder(1, 2, 6); // don't forget 3 and 5 were already eliminated
        jtBuilder.eliminateVertex(p, elmVertMap, clonedAdjMatrix, adjList, verticesToUpdate, v );

        // assert all new edges
        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 1, 2, 3, 6);
        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 2, 1, 4, 6);
        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 3, 1, 5, 6);
        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 4, 2, 6);
        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 5, 3, 6);
        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 6, 1, 2, 3, 4, 5);

        // assert new edges were correctly recalculated
        assertThat(elmVertMap.get(1).getNewEdgesRequired()).isEqualTo(0);
        assertThat(elmVertMap.get(2).getNewEdgesRequired()).isEqualTo(0);
        assertThat(elmVertMap.get(6).getNewEdgesRequired()).isEqualTo(0);


        // 1, 2 and 6 all have no new edges, and same cluster, so it uses id to ensure arbitrary is deterministic
        v = p.remove();
        id = v.getV().getId();
        assertThat(id).isEqualTo(1);
        verticesToUpdate = new HashSet<Integer>();
        jtBuilder.createClique(1, clonedAdjMatrix, verticesToUpdate, adjList);
        jtBuilder.eliminateVertex(p, elmVertMap, clonedAdjMatrix, adjList, verticesToUpdate, v );


        v = p.remove();
        id = v.getV().getId();
        assertThat(id).isEqualTo(2);
        verticesToUpdate = new HashSet<Integer>();
        jtBuilder.createClique(2, clonedAdjMatrix, verticesToUpdate, adjList);
        jtBuilder.eliminateVertex(p, elmVertMap, clonedAdjMatrix, adjList, verticesToUpdate, v);

        v = p.remove();
        id = v.getV().getId();
        assertThat(id).isEqualTo(6);
        verticesToUpdate = new HashSet<Integer>();
        jtBuilder.createClique(6, clonedAdjMatrix, verticesToUpdate, adjList);
        jtBuilder.eliminateVertex(p, elmVertMap, clonedAdjMatrix, adjList, verticesToUpdate, v );

        assertThat(p).isEmpty();
    }

    @Test
    public void testTriangulate1() {
        Graph<BayesVariable> graph = new BayesNetwork();

        GraphNode x0 = addNode(graph);
        GraphNode x1 = addNode(graph);
        GraphNode x2 = addNode(graph);
        GraphNode x3 = addNode(graph);
        GraphNode x4 = addNode(graph);

        GraphNode x5 = addNode(graph);
        GraphNode x6 = addNode(graph);

        //          *
        //        / | \
        //       *  | *
        //       |  | |
        //       *  | *
        //        \  /
        //         *

        connectParentToChildren(x1, x2);
        connectParentToChildren(x1, x3);
        connectParentToChildren(x1, x6);

        connectParentToChildren(x2, x4);
        connectParentToChildren(x3, x5);

        connectParentToChildren(x4, x6);
        connectParentToChildren(x5, x6);

        JunctionTreeBuilder jtBuilder = new JunctionTreeBuilder( graph );
        jtBuilder.moralize();
        jtBuilder.triangulate();

        // assert all new edges
        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 1, 2, 3, 4, 5, 6);
        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 2, 1, 4);
        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 3, 1, 5);
        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 4, 1, 2, 5, 6);
        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 5, 1, 3, 4, 6);
        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 6, 1, 4, 5);
    }

    @Test
    public void testTriangulate2() {
        Graph<BayesVariable> graph = new BayesNetwork();

        GraphNode x0 = addNode(graph);
        GraphNode x1 = addNode(graph);
        GraphNode x2 = addNode(graph);
        GraphNode x3 = addNode(graph);
        GraphNode x4 = addNode(graph);

        GraphNode x5 = addNode(graph);
        GraphNode x6 = addNode(graph);

        //            *
        //           /
        //          *
        //        /  \
        //       *   *
        //       |   |
        //       *   |
        //        \  /
        //         *

        connectParentToChildren(x1, x2);
        connectParentToChildren(x1, x3);


        connectParentToChildren(x2, x4);
        connectParentToChildren(x2, x6);

        connectParentToChildren(x3, x5);

        connectParentToChildren(x5, x6);

        JunctionTreeBuilder jtBuilder = new JunctionTreeBuilder( graph );
        jtBuilder.moralize();
        List<OpenBitSet> cliques =  jtBuilder.triangulate();

        // assert all new edges
        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 1, 2, 3);
        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 2, 1, 3, 4, 5, 6);
        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 3, 1, 2, 5);
        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 4, 2);
        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 5, 2, 3, 6);
        assertLinkedVertex(jtBuilder.getAdjacencyMatrix(), 6, 2, 5);

        assertThat(cliques).hasSize(5); // 5th is 0, which is just a dummy V to get numbers aligned
        assertThat(cliques).contains(bitSet("1110")); // x1, x2, x3 //a, b, c
        assertThat(cliques).contains(bitSet("10100")); // x2, x4
        assertThat(cliques).contains(bitSet("1100100")); // x2, x5, x6
        assertThat(cliques).contains(bitSet("101100")); // x2, x3, x5
    }

    @Test
    public void testSepSetCompareWithDifferentMass() {
        Graph<BayesVariable> graph = new BayesNetwork();
        GraphNode x0 = addNode(graph);
        GraphNode x1 = addNode(graph);
        GraphNode x2 = addNode(graph);
        GraphNode x3 = addNode(graph);
        GraphNode x4 = addNode(graph);
        GraphNode x5 = addNode(graph);
        GraphNode x6 = addNode(graph);


        OpenBitSet OpenBitSet1_1 = bitSet("00001110");
        OpenBitSet OpenBitSet1_2 = bitSet("01101100");
        SeparatorSet s1 = new SeparatorSet( OpenBitSet1_1, 0, OpenBitSet1_2, 0, graph);

        OpenBitSet OpenBitSet2_1 = bitSet("00001110");
        OpenBitSet OpenBitSet2_2 = bitSet("00100100");
        SeparatorSet s2 = new SeparatorSet( OpenBitSet2_1, 0, OpenBitSet2_2, 0, graph);

        List<SeparatorSet> list = new ArrayList<SeparatorSet>();
        list.add( s1 );
        list.add( s2 );
        Collections.sort(list);

        assertThat(list.get(0)).isEqualTo(s1);
    }

    @Test
    public void testSepSetCompareWithDifferentCost() {
        Graph<BayesVariable> graph = new BayesNetwork();
        GraphNode x0 = addNode(graph);
        GraphNode x1 = addNode(graph);
        GraphNode x2 = addNode(graph);
        GraphNode x3 = addNode(graph);
        GraphNode x4 = addNode(graph);
        GraphNode x5 = addNode(graph);
        GraphNode x6 = addNode(graph);

        x1.setContent(new BayesVariable<String>("x1", x0.getId(), new String[]{"a"}, new double[][]{{0.1 }}));
        x2.setContent(new BayesVariable<String>("x2", x0.getId(), new String[]{"a", "b"}, new double[][]{{0.1, 0.1}}));
        x3.setContent(new BayesVariable<String>("x3", x0.getId(), new String[]{"a", "b"}, new double[][]{{0.1, 0.1}}));

        OpenBitSet OpenBitSet1_1 = bitSet("00001110");
        OpenBitSet OpenBitSet1_2 = bitSet("01101100");
        SeparatorSet s1 = new SeparatorSet( OpenBitSet1_1, 0, OpenBitSet1_2, 0, graph);

        OpenBitSet OpenBitSet2_1 = bitSet("00001110");
        OpenBitSet OpenBitSet2_2 = bitSet("00100110");
        SeparatorSet s2 = new SeparatorSet( OpenBitSet2_1, 0, OpenBitSet2_2, 0, graph);

        List<SeparatorSet> list = new ArrayList<SeparatorSet>();
        list.add( s1 );
        list.add( s2 );
        Collections.sort( list );

        assertThat(list.get(0)).isEqualTo(s1);

        // repeat, reversing the costs, to be sure no other factor is in play.
        x1.setContent(new BayesVariable<String>("x3", x0.getId(), new String[]{"a", "b"}, new double[][]{{0.1, 0.1}}));
        x2.setContent(new BayesVariable<String>("x2", x0.getId(), new String[]{"a", "b"}, new double[][]{{0.1, 0.1}}));
        x3.setContent(new BayesVariable<String>("x1", x0.getId(), new String[]{"a"}, new double[][]{{0.1 }}));

        s1 = new SeparatorSet( OpenBitSet1_1, 0, OpenBitSet1_2, 0, graph);
        s2 = new SeparatorSet( OpenBitSet2_1, 0, OpenBitSet2_2, 0, graph);

        list = new ArrayList<SeparatorSet>();
        list.add( s1 );
        list.add( s2 );
        Collections.sort( list );

        assertThat(list.get(0)).isEqualTo(s2); // was s1 before
    }

    @Test
    public void testSepSetCompareWithSameIntersect() {
        Graph<BayesVariable> graph = new BayesNetwork();
        GraphNode x0 = addNode(graph);
        GraphNode x1 = addNode(graph);
        GraphNode x2 = addNode(graph);
        GraphNode x3 = addNode(graph);
        GraphNode x4 = addNode(graph);
        GraphNode x5 = addNode(graph);
        GraphNode x6 = addNode(graph);


        OpenBitSet OpenBitSet1_1 = bitSet("00001110");
        OpenBitSet OpenBitSet1_2 = bitSet("01000010");
        SeparatorSet s1 = new SeparatorSet( OpenBitSet1_1, 0, OpenBitSet1_2, 0, graph);

        OpenBitSet OpenBitSet2_1 = bitSet("00001110");
        OpenBitSet OpenBitSet2_2 = bitSet("01000100");
        SeparatorSet s2 = new SeparatorSet( OpenBitSet2_1, 0, OpenBitSet2_2, 0, graph);

        List<SeparatorSet> list = new ArrayList<SeparatorSet>();
        list.add( s1 );
        list.add( s2 );
        Collections.sort( list );

        assertThat(list.get(0)).isEqualTo(s1);

        // reverse the bits, to show the arbitrary is deterministic
        OpenBitSet1_2 = bitSet("01000100");
        s1 = new SeparatorSet( OpenBitSet1_1, 0, OpenBitSet1_2, 0, graph);

        OpenBitSet2_2 = bitSet("01000010");
        s2 = new SeparatorSet( OpenBitSet2_1, 0, OpenBitSet2_2, 0, graph);

        list = new ArrayList<SeparatorSet>();
        list.add( s1 );
        list.add( s2 );
        Collections.sort( list );

        assertThat(list.get(0)).isEqualTo(s2); // was s1 before
    }


    @Test
    public void testJunctionTree() {
        Graph<BayesVariable> graph = new BayesNetwork();

        GraphNode x0 = addNode(graph);
        GraphNode x1 = addNode(graph);

        GraphNode x2 = addNode(graph);
        GraphNode x3 = addNode(graph);

        GraphNode x4 = addNode(graph);
        GraphNode x5 = addNode(graph);
        GraphNode x6 = addNode(graph);

        OpenBitSet OpenBitSet1 = bitSet("00001110");
        OpenBitSet OpenBitSet2 = bitSet("00011100");
        OpenBitSet OpenBitSet3 = bitSet("00110000");
        OpenBitSet OpenBitSet4 = bitSet("01110000");

        List<OpenBitSet> cliques = new ArrayList<OpenBitSet>();
        cliques.add ( OpenBitSet1 );
        cliques.add ( OpenBitSet2 );
        cliques.add ( OpenBitSet3 );
        cliques.add ( OpenBitSet4 );

        List<SeparatorSet> separatorSets = new ArrayList<SeparatorSet>();
        for ( int i = 0; i < cliques.size(); i++ ) {
            OpenBitSet ci = cliques.get(i);
            for ( int j = i+1; j < cliques.size(); j++ ) {
                OpenBitSet cj = cliques.get(j);
                if ( ci.intersects( cj ) ) {
                    SeparatorSet separatorSet = new SeparatorSet( ci, 0, cj, 0, graph );
                }
            }
        }
        Collections.sort(separatorSets);
    }

    @Test
    public void testJunctionTreeNoPruning() {
        Graph<BayesVariable> graph = new BayesNetwork();
        GraphNode x0 = addNode(graph);
        GraphNode x1 = addNode(graph);
        GraphNode x2 = addNode(graph);
        GraphNode x3 = addNode(graph);
        GraphNode x4 = addNode(graph);
        GraphNode x5 = addNode(graph);
        GraphNode x6 = addNode(graph);
        GraphNode x7 = addNode(graph);

        List<OpenBitSet> list = new ArrayList<OpenBitSet>();
        OpenBitSet OpenBitSet1 = bitSet("00001111");
        OpenBitSet OpenBitSet2 = bitSet("00111100");
        OpenBitSet OpenBitSet3 = bitSet("11100000"); // linear

        OpenBitSet intersect1And2 = OpenBitSet2.clone();
        intersect1And2.and(OpenBitSet1);

        OpenBitSet intersect2And3 = OpenBitSet2.clone();
        intersect2And3.and(OpenBitSet3);

        list.add(OpenBitSet1);
        list.add(OpenBitSet2);
        list.add(OpenBitSet3);

        JunctionTreeBuilder jtBuilder = new JunctionTreeBuilder( graph );
        JunctionTreeClique jtNode = jtBuilder.junctionTree(list, false).getRoot();


        assertThat(jtNode.getBitSet()).isEqualTo(OpenBitSet1);
        assertThat(jtNode.getChildren()).hasSize(1);
        JunctionTreeSeparator sep =  jtNode.getChildren().get(0);
        assertThat(sep.getParent().getBitSet()).isEqualTo(OpenBitSet1);
        assertThat(sep.getChild().getBitSet()).isEqualTo(OpenBitSet2);
        assertThat(sep.getBitSet()).isEqualTo(intersect1And2);

        jtNode = sep.getChild();
        assertThat(jtNode.getBitSet()).isEqualTo(OpenBitSet2);
        assertThat(jtNode.getChildren()).hasSize(1);
        sep =   jtNode.getChildren().get(0);
        assertThat(sep.getParent().getBitSet()).isEqualTo(OpenBitSet2);
        assertThat(sep.getChild().getBitSet()).isEqualTo(OpenBitSet3);
        assertThat(sep.getBitSet()).isEqualTo(intersect2And3);
    }


    @Test
    public void testJunctionWithPruning1() {
        Graph<BayesVariable> graph = new BayesNetwork();
        GraphNode x0 = addNode(graph);
        GraphNode x1 = addNode(graph);
        GraphNode x2 = addNode(graph);
        GraphNode x3 = addNode(graph);
        GraphNode x4 = addNode(graph);
        GraphNode x5 = addNode(graph);
        GraphNode x6 = addNode(graph);
        GraphNode x7 = addNode(graph);

        List<OpenBitSet> list = new ArrayList<OpenBitSet>();
        OpenBitSet OpenBitSet1 = bitSet("00001111");
        OpenBitSet OpenBitSet2 = bitSet("00111100");
        OpenBitSet OpenBitSet3 = bitSet("11100001"); // links to 2 and 1, but should still result in a single path. As the 3 -> 1 link, gets pruned

        OpenBitSet intersect1And2 = OpenBitSet2.clone();
        intersect1And2.and(OpenBitSet1);

        OpenBitSet intersect2And3 = OpenBitSet2.clone();
        intersect2And3.and(OpenBitSet3);

        list.add(OpenBitSet1);
        list.add(OpenBitSet2);
        list.add(OpenBitSet3);

        JunctionTreeBuilder jtBuilder = new JunctionTreeBuilder( graph );
        JunctionTreeClique jtNode = jtBuilder.junctionTree(list, false).getRoot();


        assertThat(jtNode.getBitSet()).isEqualTo(OpenBitSet1);
        assertThat(jtNode.getChildren()).hasSize(2);
        JunctionTreeSeparator sep =  jtNode.getChildren().get(0);
        assertThat(sep.getParent().getBitSet()).isEqualTo(OpenBitSet1);
        assertThat(sep.getChild().getBitSet()).isEqualTo(OpenBitSet2);
        assertThat(sep.getChild().getChildren()).isEmpty();

        sep =  jtNode.getChildren().get(1);
        assertThat(sep.getParent().getBitSet()).isEqualTo(OpenBitSet1);
        assertThat(sep.getChild().getBitSet()).isEqualTo(OpenBitSet3);
        assertThat(sep.getChild().getChildren()).isEmpty();

    }

    @Test
    public void testJunctionWithPruning2() {
        Graph<BayesVariable> graph = new BayesNetwork();
        GraphNode x0 = addNode(graph);
        GraphNode x1 = addNode(graph);
        GraphNode x2 = addNode(graph);
        GraphNode x3 = addNode(graph);
        GraphNode x4 = addNode(graph);
        GraphNode x5 = addNode(graph);
        GraphNode x6 = addNode(graph);
        GraphNode x7 = addNode(graph);

        List<OpenBitSet> list = new ArrayList<OpenBitSet>();
        OpenBitSet OpenBitSet1 = bitSet("00001111");
        OpenBitSet OpenBitSet2 = bitSet("00111100");
        OpenBitSet OpenBitSet3 = bitSet("11100000");
        OpenBitSet OpenBitSet4 = bitSet("00100001");

        OpenBitSet intersect1And2 = OpenBitSet2.clone();
        intersect1And2.and(OpenBitSet1);

        OpenBitSet intersect2And3 = OpenBitSet2.clone();
        intersect2And3.and(OpenBitSet3);

        OpenBitSet intersect1And4 = OpenBitSet1.clone();
        intersect1And4.and(OpenBitSet4);

        list.add(OpenBitSet1);
        list.add(OpenBitSet2);
        list.add(OpenBitSet3);
        list.add(OpenBitSet4);

        JunctionTreeBuilder jtBuilder = new JunctionTreeBuilder( graph );
        JunctionTreeClique jtNode = jtBuilder.junctionTree(list, false).getRoot();
        JunctionTreeClique root = jtNode;


        assertThat(root.getBitSet()).isEqualTo(OpenBitSet1);
        assertThat(root.getChildren()).hasSize(2);
        JunctionTreeSeparator sep =  root.getChildren().get(0);
        assertThat(sep.getParent().getBitSet()).isEqualTo(OpenBitSet1);
        assertThat(sep.getChild().getBitSet()).isEqualTo(OpenBitSet2);
        assertThat(sep.getChild().getChildren()).hasSize(1);

        jtNode = sep.getChild();
        assertThat(jtNode.getBitSet()).isEqualTo(OpenBitSet2);
        assertThat(jtNode.getChildren()).hasSize(1);
        sep =   jtNode.getChildren().get(0);
        assertThat(sep.getParent().getBitSet()).isEqualTo(OpenBitSet2);
        assertThat(sep.getChild().getBitSet()).isEqualTo(OpenBitSet3);
        assertThat(sep.getBitSet()).isEqualTo(intersect2And3);
        assertThat(sep.getChild().getChildren()).isEmpty();

        sep =  root.getChildren().get(1);
        assertThat(sep.getParent().getBitSet()).isEqualTo(OpenBitSet1);
        assertThat(sep.getChild().getBitSet()).isEqualTo(OpenBitSet4);
        assertThat(sep.getBitSet()).isEqualTo(intersect1And4);
        assertThat(sep.getChild().getChildren()).isEmpty();
    }

    @Test
    public void testJunctionWithPruning3() {
        Graph<BayesVariable> graph = new BayesNetwork();
        GraphNode x0 = addNode(graph);
        GraphNode x1 = addNode(graph);
        GraphNode x2 = addNode(graph);
        GraphNode x3 = addNode(graph);
        GraphNode x4 = addNode(graph);
        GraphNode x5 = addNode(graph);
        GraphNode x6 = addNode(graph);
        GraphNode x7 = addNode(graph);

        List<OpenBitSet> list = new ArrayList<OpenBitSet>();
        OpenBitSet OpenBitSet1 = bitSet("00001111");
        OpenBitSet OpenBitSet2 = bitSet("00011110");
        OpenBitSet OpenBitSet3 = bitSet("11100000");
        OpenBitSet OpenBitSet4 = bitSet("01100001");

        OpenBitSet intersect1And2 = OpenBitSet2.clone();
        intersect1And2.and(OpenBitSet1);

        OpenBitSet intersect2And3 = OpenBitSet2.clone();
        intersect2And3.and(OpenBitSet3);

        OpenBitSet intersect1And4 = OpenBitSet1.clone();
        intersect1And4.and(OpenBitSet4);

        OpenBitSet intersect3And4 = OpenBitSet3.clone();
        intersect3And4.and(OpenBitSet4);

        list.add(OpenBitSet1);
        list.add(OpenBitSet2);
        list.add(OpenBitSet3);
        list.add(OpenBitSet4);

        JunctionTreeBuilder jtBuilder = new JunctionTreeBuilder( graph );
        JunctionTreeClique jtNode = jtBuilder.junctionTree(list, false).getRoot();;
        JunctionTreeClique root = jtNode;

        assertThat(root.getBitSet()).isEqualTo(OpenBitSet1);
        assertThat(root.getChildren()).hasSize(2);
        JunctionTreeSeparator sep =  root.getChildren().get(0);
        assertThat(sep.getParent().getBitSet()).isEqualTo(OpenBitSet1);
        assertThat(sep.getChild().getBitSet()).isEqualTo(OpenBitSet2);
        assertThat(sep.getChild().getChildren()).isEmpty();

        sep =  root.getChildren().get(1);
        assertThat(sep.getParent().getBitSet()).isEqualTo(OpenBitSet1);
        assertThat(sep.getChild().getBitSet()).isEqualTo(OpenBitSet4);
        assertThat(sep.getBitSet()).isEqualTo(intersect1And4);
        assertThat(sep.getChild().getChildren()).hasSize(1);

        jtNode = sep.getChild();
        assertThat(jtNode.getBitSet()).isEqualTo(OpenBitSet4);
        assertThat(jtNode.getChildren()).hasSize(1);
        sep =   jtNode.getChildren().get(0);
        assertThat(sep.getParent().getBitSet()).isEqualTo(OpenBitSet4);
        assertThat(sep.getChild().getBitSet()).isEqualTo(OpenBitSet3);
        assertThat(sep.getBitSet()).isEqualTo(intersect3And4);
        assertThat(sep.getChild().getChildren()).isEmpty();
    }


    @Test
    public void testMapNodeToCliques() {
        Graph<BayesVariable> graph = new BayesNetwork();
        JunctionTreeBuilder tbuilder = new JunctionTreeBuilder(graph);

        GraphNode x0 = addNode(graph);
        GraphNode x1 = addNode(graph);
        GraphNode x2 = addNode(graph);
        GraphNode x3 = addNode(graph);
        GraphNode x4 = addNode(graph);
        GraphNode x5 = addNode(graph);
        GraphNode x6 = addNode(graph);
        GraphNode x7 = addNode(graph);

        OpenBitSet clique0 = bitSet("01010101");
        OpenBitSet clique1 = bitSet("10010001");
        OpenBitSet clique2 = bitSet("10111010");

        OpenBitSet[] nodeToCliques = new OpenBitSet[8];
        tbuilder.mapVarNodeToCliques(nodeToCliques, 0, clique0);
        tbuilder.mapVarNodeToCliques(nodeToCliques, 1, clique1);
        tbuilder.mapVarNodeToCliques(nodeToCliques, 2, clique2);

        assertThat(nodeToCliques[0]).isEqualTo(bitSet("011"));
        assertThat(nodeToCliques[1]).isEqualTo(bitSet("100"));
        assertThat(nodeToCliques[2]).isEqualTo(bitSet("001"));
        assertThat(nodeToCliques[3]).isEqualTo(bitSet("100"));
        assertThat(nodeToCliques[4]).isEqualTo(bitSet("111"));
        assertThat(nodeToCliques[5]).isEqualTo(bitSet("100"));
        assertThat(nodeToCliques[6]).isEqualTo(bitSet("001"));
        assertThat(nodeToCliques[7]).isEqualTo(bitSet("110"));
    }


    @Test
    public void testMapNodeToClique() {
        Graph<BayesVariable> graph = new BayesNetwork();
        JunctionTreeBuilder tbuilder = new JunctionTreeBuilder(graph);

        GraphNode<BayesVariable> x0 = addNode(graph);
        GraphNode<BayesVariable> x1 = addNode(graph);
        GraphNode<BayesVariable> x2 = addNode(graph);
        GraphNode<BayesVariable> x3 = addNode(graph);
        GraphNode<BayesVariable> x4 = addNode(graph);
        GraphNode<BayesVariable> x5 = addNode(graph);
        GraphNode<BayesVariable> x6 = addNode(graph);
        GraphNode<BayesVariable> x7 = addNode(graph);

        connectChildToParents(x1, x2, x3);
        connectChildToParents(x3, x6, x7);

        OpenBitSet clique0 = bitSet("01001110");
        OpenBitSet clique1 = bitSet("11001110");
        OpenBitSet clique2 = bitSet("11101000");
        OpenBitSet clique3 = bitSet("00010011");

        JunctionTreeClique jtNode0 = new JunctionTreeClique(0, graph, clique0);
        JunctionTreeClique jtNode1 = new JunctionTreeClique(1, graph, clique1);
        JunctionTreeClique jtNode2 = new JunctionTreeClique(2, graph, clique2);
        JunctionTreeClique jtNode3 = new JunctionTreeClique(3, graph, clique3);
        JunctionTreeClique[] jtNodes  = new JunctionTreeClique[] { jtNode0, jtNode1, jtNode2, jtNode3};

        OpenBitSet[] nodeToCliques = new OpenBitSet[8];
        tbuilder.mapVarNodeToCliques(nodeToCliques, 0, clique0);
        tbuilder.mapVarNodeToCliques(nodeToCliques, 1, clique1);
        tbuilder.mapVarNodeToCliques(nodeToCliques, 2, clique2);
        tbuilder.mapVarNodeToCliques(nodeToCliques, 3, clique3);

        //int[] nodeToClique = new int[8];
        tbuilder.mapNodeToCliqueFamily(nodeToCliques, jtNodes);

        assertThat(x0.getContent().getFamily()).isEqualTo(3);
        assertThat(x1.getContent().getFamily()).isEqualTo(0);
        assertThat(x2.getContent().getFamily()).isEqualTo(0);
        assertThat(x3.getContent().getFamily()).isEqualTo(2);
        assertThat(x4.getContent().getFamily()).isEqualTo(3);
        assertThat(x5.getContent().getFamily()).isEqualTo(2);
        assertThat(x6.getContent().getFamily()).isEqualTo(0);
        assertThat(x7.getContent().getFamily()).isEqualTo(2);
    }

    @Test
    public void testFullExample1() {
        // from "Bayesian Belief Network Propagation Engine In Java"
        // the result here is slightly different, due to ordering, but it's still correct.
        // http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.135.7921&rep=rep1&type=pdf
        Graph<BayesVariable> graph = new BayesNetwork();
        GraphNode xa = addNode(graph);
        GraphNode xb = addNode(graph);
        GraphNode xc = addNode(graph);
        GraphNode xd = addNode(graph);
        GraphNode xe = addNode(graph);
        GraphNode xf = addNode(graph);
        GraphNode xg = addNode(graph);
        GraphNode xh = addNode(graph);

        connectParentToChildren(xa, xb, xc);
        connectParentToChildren(xb, xd);
        connectParentToChildren(xc, xe, xg);
        connectParentToChildren(xd, xf);
        connectParentToChildren(xe, xf, xh);
        connectParentToChildren(xg, xh);

        OpenBitSet clique1 = bitSet("00111000"); // d, e, f
        OpenBitSet clique2 = bitSet("00011100"); // c, d, e
        OpenBitSet clique3 = bitSet("01010100"); // c, e, g
        OpenBitSet clique4 = bitSet("11010000"); // e, g, h
        OpenBitSet clique5 = bitSet("00001110"); // b, c, d
        OpenBitSet clique6 = bitSet("00000111"); // a, b, c

        OpenBitSet clique1And2 = bitSet("00011000"); // d, e
        OpenBitSet clique2And3 = bitSet("00010100"); // c, e
        OpenBitSet clique2And5 = bitSet("00001100"); // c, d
        OpenBitSet clique3And4 = bitSet("01010000"); // e, g
        OpenBitSet clique5And6 = bitSet("00000110"); // b, c

        // clique1
        JunctionTreeBuilder jtBuilder = new JunctionTreeBuilder( graph );
        JunctionTreeClique root = jtBuilder.build(false).getRoot();
        assertThat(root.getBitSet()).isEqualTo(clique1);
        assertThat(root.getChildren()).hasSize(1);

        // clique2
        JunctionTreeSeparator sep =  root.getChildren().get(0);
        assertThat(sep.getBitSet()).isEqualTo(clique1And2);
        JunctionTreeClique jtNode2 = sep.getChild();
        assertThat(sep.getParent().getBitSet()).isEqualTo(clique1);
        assertThat(jtNode2.getBitSet()).isEqualTo(clique2);
        assertThat(jtNode2.getChildren()).hasSize(2);

        // clique3
        sep =  jtNode2.getChildren().get(0);
        assertThat(sep.getBitSet()).isEqualTo(clique2And3);
        JunctionTreeClique jtNode3 =sep.getChild();
        assertThat(sep.getParent().getBitSet()).isEqualTo(clique2);
        assertThat(jtNode3.getBitSet()).isEqualTo(clique3);
        assertThat(jtNode3.getChildren()).hasSize(1);

        // clique4
        sep =  jtNode3.getChildren().get(0);
        assertThat(sep.getBitSet()).isEqualTo(clique3And4);
        JunctionTreeClique jtNode4 = sep.getChild();
        assertThat(sep.getParent().getBitSet()).isEqualTo(clique3);
        assertThat(jtNode4.getBitSet()).isEqualTo(clique4);
        assertThat(jtNode4.getChildren()).isEmpty();

        // clique5
        sep =  jtNode2.getChildren().get(1);
        assertThat(sep.getBitSet()).isEqualTo(clique2And5);
        JunctionTreeClique jtNode5 = sep.getChild();
        assertThat(sep.getParent().getBitSet()).isEqualTo(clique2);
        assertThat(jtNode5.getBitSet()).isEqualTo(clique5);
        assertThat(jtNode5.getChildren()).hasSize(1);

        //clique 6
        sep =  jtNode5.getChildren().get(0);
        assertThat(sep.getBitSet()).isEqualTo(clique5And6);
        JunctionTreeClique jtNode6 = sep.getChild();
        assertThat(sep.getParent().getBitSet()).isEqualTo(clique5);
        assertThat(jtNode6.getBitSet()).isEqualTo(clique6);
        assertThat(jtNode6.getChildren()).isEmpty();
    }

    @Test
    public void testFullExample2() {
        // Bayesian Networks -  A Self-contained introduction with implementation remarks
        // http://www.mathcs.emory.edu/~whalen/Papers/BNs/Intros/BayesianNetworksTutorial.pdf
        Graph<BayesVariable> graph = new BayesNetwork();
        GraphNode xElectricity = addNode(graph);   // 0
        GraphNode xTelecom = addNode(graph);       // 1
        GraphNode xRail = addNode(graph);          // 2
        GraphNode xAirTravel = addNode(graph);     // 3
        GraphNode xTransportation = addNode(graph);// 4
        GraphNode xUtilities = addNode(graph);     // 5
        GraphNode xUSBanks = addNode(graph);       // 6
        GraphNode xUSStocks = addNode(graph);      // 7

        connectParentToChildren( xElectricity, xRail, xAirTravel, xUtilities, xTelecom );
        connectParentToChildren( xTelecom, xUtilities, xUSBanks );
        connectParentToChildren( xRail, xTransportation );
        connectParentToChildren( xAirTravel, xTransportation );
        connectParentToChildren( xUtilities, xUSStocks );
        connectParentToChildren( xUSBanks, xUSStocks );
        connectParentToChildren( xTransportation, xUSStocks );


        OpenBitSet clique1 = bitSet("11110000"); // Utilities, Transportation, USBanks, UStocks
        OpenBitSet clique2 = bitSet("01110001"); // Electricity, Transportation, Utilities, USBanks
        OpenBitSet clique3 = bitSet("01100011"); // Electricity, Telecom, Utilities, USBanks
        OpenBitSet clique4 = bitSet("00011101"); // Electricity, Rail, AirTravel, Transportation

        OpenBitSet clique1And2 = bitSet("01110000"); // Utilities, Transportation, USBanks
        OpenBitSet clique2And3 = bitSet("01100001"); // Electricity, Utilities, USBanks
        OpenBitSet clique2And4 = bitSet("00010001"); // Electricity, Transportation


        xElectricity.setContent(new BayesVariable<String>("Electricity", xElectricity.getId(),
                                                          new String[]{"Working", "Reduced", "NotWorking"}, new double[][]{{0.6, 0.3, 0.099}}));

        xTelecom.setContent(new BayesVariable<String>("Telecom", xTelecom.getId(),
                                                      new String[]{"Working", "Reduced", "NotWorking"}, new double[][]{{0.544, 0.304, 0.151}}));

        xRail.setContent(new BayesVariable<String>("Rail", xRail.getId(),
                                                   new String[]{"Working", "Reduced", "NotWorking"}, new double[][]{{0.579, 0.230, 0.190}}));

        xAirTravel.setContent(new BayesVariable<String>("AirTravel", xAirTravel.getId(),
                                                        new String[]{"Working", "Reduced", "NotWorking"}, new double[][]{{0.449, 0.330, 0.219}}));

        xTransportation.setContent(new BayesVariable<String>("Transportation", xTransportation.getId(),
                                                             new String[]{"Working", "Moderate", "Severe", "Failure"}, new double[][]{{0.658, 0.167, 0.097, 0.077}}));

        xUtilities.setContent(new BayesVariable<String>("Utilities", xUtilities.getId(),
                                                        new String[]{"Working", "Moderate", "Severe", "Failure"}, new double[][]{{0.541, 0.272, 0.097, 0.088}}));

        xUSBanks.setContent(new BayesVariable<String>("USBanks", xUSBanks.getId(),
                                                      new String[]{"Working", "Reduced", "NotWorking"}, new double[][]{{0.488, 0.370, 0.141}}));

        xUSStocks.setContent(new BayesVariable<String>("USStocks", xUSStocks.getId(),
                                                       new String[]{"Up", "Down", "Crash"}, new double[][]{{0.433, 0.386, 0.179}}));

        JunctionTreeBuilder jtBuilder = new JunctionTreeBuilder( graph );
        JunctionTreeClique root = jtBuilder.build(false).getRoot();

        // clique1
        assertThat(root.getBitSet()).isEqualTo(clique1);
        assertThat(root.getChildren()).hasSize(1);

        // clique2
        JunctionTreeSeparator sep =  root.getChildren().get(0);
        assertThat(sep.getBitSet()).isEqualTo(clique1And2);
        JunctionTreeClique jtNode2 = sep.getChild();
        assertThat(sep.getParent().getBitSet()).isEqualTo(clique1);
        assertThat(jtNode2.getBitSet()).isEqualTo(clique2);
        assertThat(jtNode2.getChildren()).hasSize(2);

        // clique3
        assertThat(jtNode2.getParentSeparator()).isSameAs(sep);
        sep =  jtNode2.getChildren().get(0);
        assertThat(sep.getBitSet()).isEqualTo(clique2And3);
        JunctionTreeClique jtNode3 = sep.getChild();
        assertThat(sep.getParent().getBitSet()).isEqualTo(clique2);
        assertThat(jtNode3.getBitSet()).isEqualTo(clique3);
        assertThat(jtNode3.getChildren()).isEmpty();

        // clique4
        sep =  jtNode2.getChildren().get(1);
        assertThat(sep.getBitSet()).isEqualTo(clique2And4);
        JunctionTreeClique jtNode4 = sep.getChild();
        assertThat(sep.getParent().getBitSet()).isEqualTo(clique2);
        assertThat(jtNode4.getBitSet()).isEqualTo(clique4);
        assertThat(jtNode4.getChildren()).isEmpty();
    }

}
