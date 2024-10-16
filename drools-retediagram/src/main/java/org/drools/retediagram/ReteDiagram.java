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
package org.drools.retediagram;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.parse.Parser;
import org.drools.base.base.ClassObjectType;
import org.drools.core.common.BaseNode;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.core.reteoo.AccumulateNode;
import org.drools.core.reteoo.AlphaNode;
import org.drools.core.reteoo.BetaNode;
import org.drools.core.reteoo.EntryPointNode;
import org.drools.core.reteoo.JoinNode;
import org.drools.core.reteoo.LeftInputAdapterNode;
import org.drools.core.reteoo.LeftTupleSource;
import org.drools.core.reteoo.NotNode;
import org.drools.core.reteoo.ObjectSource;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.Rete;
import org.drools.core.reteoo.TupleToObjectNode;
import org.drools.core.reteoo.RuleTerminalNode;
import org.drools.core.reteoo.Sink;
import org.drools.base.rule.constraint.BetaConstraint;
import org.drools.base.base.ObjectType;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieRuntime;
import org.kie.api.runtime.KieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class ReteDiagram {

    private static final Logger LOG = LoggerFactory.getLogger(ReteDiagram.class);

    public enum Layout {
        PARTITION, VLEVEL
    }

    private Layout layout;
    private File outputPath;
    private boolean prefixTimestamp;
    private boolean outputSVG;
    private boolean outputPNG;
    private boolean openSVG;
    private boolean openPNG;
    private boolean printDebugVerticalCluster = false;
    
    private ReteDiagram() { }
   
    /**
     * With default settings.
     */
    public static ReteDiagram newInstance() {
        File outpath = new File(".");
        try {
            outpath = Files.createTempDirectory("retediagram").toFile();
        } catch (Exception e) {
            // do nothing.
        }
        return new ReteDiagram()
                .configLayout(Layout.VLEVEL)
                .configFilenameScheme(outpath, true)
                .configGraphvizRender(true, true)
                .configOpenFile(false, false)
                ;
    }
    
    /**
     * Changes diagram Layout
     */
    public ReteDiagram configLayout(Layout layout) {
        this.layout = layout;
        return this;
    }
    
    public ReteDiagram configPrintDebugVerticalCluster(boolean printDebugVerticalCluster) {
        this.printDebugVerticalCluster = printDebugVerticalCluster;
        return this;
    }

    public ReteDiagram configFilenameScheme(File outputPath, boolean prefixTimestamp) {
        this.outputPath = outputPath;
        this.prefixTimestamp = prefixTimestamp;
        return this;
    }
    
    public ReteDiagram configGraphvizRender(boolean outputSVG, boolean outputPNG) {
        this.outputSVG = outputSVG;
        this.outputPNG = outputPNG;
        return this;
    }
    
    public ReteDiagram configOpenFile(boolean openSVG, boolean openPNG) {
        this.openSVG = openSVG;
        this.openPNG = openPNG;
        return this;
    }
    
    public void diagramRete(KieBase kbase) {
        diagramRete((InternalKnowledgeBase) kbase);
    }

    public void diagramRete(KieRuntime session) {
        diagramRete((InternalKnowledgeBase)session.getKieBase());
    }

    public void diagramRete(KieSession session) {
        diagramRete((InternalKnowledgeBase)session.getKieBase());
    }

    public void diagramRete(InternalKnowledgeBase kBase) {
        diagramRete(kBase.getRete());
    }

    public void diagramRete(Rete rete) {
        String timestampPrefix = (new SimpleDateFormat("yyyyMMddHHmmssSSS")).format(new Date());
        String fileNameNoExtension = (prefixTimestamp?timestampPrefix+".":"") + rete.getRuleBase().getId();
        String gvFileName = fileNameNoExtension + ".gv";
        String svgFileName = fileNameNoExtension + ".svg";
        String pngFileName = fileNameNoExtension + ".png";
        File gvFile = new File(outputPath, gvFileName);
        File svgFile = new File(outputPath, svgFileName);
        File pngFile = new File(outputPath, pngFileName);
        try (PrintStream out = new PrintStream(new FileOutputStream(gvFile));) {
            out.println("digraph g {\n" +
                    "graph [fontname = \"Arial\" fontsize=11];\n" +
                    " node [fontname = \"Arial\" fontsize=11];\n" +
                    " edge [fontname = \"Arial\" fontsize=11];");
            HashMap<Class<? extends BaseNode>, Set<BaseNode>> levelMap = new HashMap<>();
            HashMap<Class<? extends BaseNode>, List<BaseNode>> nodeMap = new HashMap<>();
            List<Vertex<BaseNode,BaseNode>> vertexes = new ArrayList<>();
            Set<Integer> visitedNodesIDs = new HashSet<>();
            for (EntryPointNode entryPointNode : rete.getEntryPointNodes().values()) {
                visitNodes( entryPointNode, "", visitedNodesIDs, nodeMap, vertexes, levelMap, out);
            }
            
            out.println();
            printNodeMap(nodeMap, out);
            
            out.println();
            printVertexes(vertexes, out);
            
            out.println();
            printLevelMap(levelMap, out, vertexes);
            
            out.println();
            if (layout == Layout.PARTITION) {
                printPartitionMap(nodeMap, out, vertexes);
            }
            
            out.println("}");
        } catch (Exception e) {
            LOG.error("Error building diagram", e);
        }
        LOG.info("Written gvFile: {}", gvFile);

        if (outputSVG) {
            try {
                MutableGraph g = new Parser().read(gvFile);
                Graphviz.fromGraph(g).render(Format.SVG).toFile(svgFile);
                LOG.info("Written svgFile: {}", svgFile);
            } catch (Exception e) {
                LOG.error("Error building SVG file", e);
            }
        }
        if (outputPNG) {
            try {
                MutableGraph g = new Parser().read(gvFile);
                Graphviz.fromGraph(g).render(Format.PNG).toFile(pngFile);
                LOG.info("Written pngFile: {}", pngFile);
            } catch (Exception e) {
                LOG.error("Error building PNG file", e);
            }
        }
        
        if (outputSVG && openSVG) {
            try {
                java.awt.Desktop.getDesktop().open(svgFile);
            } catch (Exception e) {
                LOG.error("Error opening SVG file", e);
            }
        }
        if (outputPNG && openPNG) {
            try {
                java.awt.Desktop.getDesktop().open(pngFile);
            } catch (Exception e) {
                LOG.error("Error opening PNG file", e);
            }
        }
    }
    
    private static void printVertexes(List<Vertex<BaseNode, BaseNode>> vertexes, PrintStream out ) {
        for ( Vertex<BaseNode, BaseNode> v : vertexes ) {
            out.println(printNodeId(v.from) + " -> " + printNodeId(v.to) + " ;");
        }
    }

    private static void printNodeMap(HashMap<Class<? extends BaseNode>, List<BaseNode>> nodeMap, PrintStream out) {
        printNodeMapNodes(nodeMap.get(EntryPointNode.class), out);
        printNodeMapNodes(nodeMap.get(ObjectTypeNode.class), out);
        printNodeMapNodes(nodeMap.getOrDefault(AlphaNode.class, Collections.emptyList()), out);
        // LIAs
        List<BaseNode> l3 = nodeMap.entrySet().stream()
                .filter(kv->LeftInputAdapterNode.class.isAssignableFrom( kv.getKey() ))
                .flatMap(kv->kv.getValue().stream()).collect(toList());
        printNodeMapNodes(l3, out);
        printNodeMapNodes(nodeMap.getOrDefault(TupleToObjectNode.class, Collections.emptyList()), out);
        // Level 4: BN
        List<BaseNode> l4 = nodeMap.entrySet().stream()
                                .filter(kv->BetaNode.class.isAssignableFrom( kv.getKey() ))
                                .flatMap(kv->kv.getValue().stream()).collect(toList());
        printNodeMapNodes(l4, out);
        printNodeMapNodes(nodeMap.get(RuleTerminalNode.class), out);
    }

    public static void printNodeMapNodes(List<BaseNode> nodes, PrintStream out) {
        for (BaseNode node : nodes) {
            out.println(printNodeId(node) + " " + printNodeAttributes(node) + " ;");
        }
    }
    
    public static class Vertex<F,T> {
        public final F from;
        public final T to;
        public Vertex(F from, T to) {
            this.from = from;
            this.to = to;
        }
        public static <F, T> Vertex<F, T> of(F from, T to) {
            return new Vertex<>(from, to);
        }
    }
    
    private static void printPartitionMap(HashMap<Class<? extends BaseNode>, List<BaseNode>> nodeMap, PrintStream out, List<Vertex<BaseNode, BaseNode>> vertexes) {
        Map<Integer, List<BaseNode>> byPartition = nodeMap.entrySet().stream()
            .flatMap(kv->kv.getValue().stream())
            .collect(groupingBy(n->n.getPartitionId() == null ? 0 : n.getPartitionId().getId()));
        
        for (Entry<Integer, List<BaseNode>> kv : byPartition.entrySet()) {
            printClusterMapCluster("P"+kv.getKey(), new HashSet<>(kv.getValue()), out);
        }
    }

    private void printLevelMap(HashMap<Class<? extends BaseNode>, Set<BaseNode>> levelMap, PrintStream out, List<Vertex<BaseNode, BaseNode>> vertexes) {

        // Level 1: OTN
        Set<BaseNode> l1 = levelMap.entrySet().stream()
                                .filter(kv->ObjectTypeNode.class.isAssignableFrom( kv.getKey() ))
                                .flatMap(kv->kv.getValue().stream()).collect(toSet());
        printLevelMapLevel("l1", l1, out);
        
        // Level 2: AN
        Set<BaseNode> l2 = levelMap.entrySet().stream()
                                .filter(kv->AlphaNode.class.isAssignableFrom( kv.getKey() ))
                                .flatMap(kv->kv.getValue().stream()).collect(toSet());
        printLevelMapLevel("l2", l2, out);
        
        // Level 3: LIA
        Set<BaseNode> l3 = levelMap.entrySet().stream()
                                .filter(kv->LeftInputAdapterNode.class.isAssignableFrom( kv.getKey() ))
                                .flatMap(kv->kv.getValue().stream()).collect(toSet());
        printLevelMapLevel("l3", l3, out);
        
        // RIA
        Set<BaseNode> lria = levelMap.entrySet().stream()
                                .filter(kv-> TupleToObjectNode.class.isAssignableFrom(kv.getKey()))
                                .flatMap(kv->kv.getValue().stream()).collect(toSet());
        printLevelMapLevel("lria", lria, out);
        
        // RIA beta sources
        Set<BaseNode> lriaSources = new HashSet<>();
        Set<Vertex<BaseNode, BaseNode>> onlyBetas = vertexes.stream().filter(v->v.from instanceof BetaNode).collect(toSet());
        for (BaseNode ria : lria) {
            Set<BaseNode> t = onlyBetas.stream()
                    .filter(v->v.to.equals(ria))
                    .map(v->v.from)
                    .collect(toSet());
            lriaSources.addAll(t);
        }
        for (BaseNode lriaSource : lriaSources) {
            lriaSources.addAll( recurseIncomingVertex(lriaSource, onlyBetas) );
        }
        printLevelMapLevel("lriaSources", lriaSources, out);
        
        // subnetwork Betas
        Set<BaseNode> lsubbeta = levelMap.entrySet().stream()
                                .filter(kv->BetaNode.class.isAssignableFrom( kv.getKey() ))
                                .flatMap(kv->kv.getValue().stream())
                                .filter(b-> ((BetaNode) b).getObjectType() == null )
                                .collect(toSet());
        printLevelMapLevel("lsubbeta", lsubbeta, out);

        // Level 4: BN
        Set<BaseNode> l4 = levelMap.entrySet().stream()
                                .filter(kv->BetaNode.class.isAssignableFrom( kv.getKey() ))
                                .flatMap(kv->kv.getValue().stream())
                                .filter(b-> !lriaSources.contains(b) )
                                .filter(b-> !lsubbeta.contains(b) )
                                .collect(toSet());
        printLevelMapLevel("l4", l4, out);

        // Level 5: RTN
        Set<BaseNode> l5 = levelMap.entrySet().stream()
                                .filter(kv->RuleTerminalNode.class.isAssignableFrom( kv.getKey() ))
                                .flatMap(kv->kv.getValue().stream()).collect(toSet());
        printLevelMapLevel("l5", l5, out);
        
        out.println(
                    ((this.printDebugVerticalCluster) ? "" : " edge[style=invis];\n") +
                " l1->l2->l3->lriaSources->lria->lsubbeta->l4->l5;");
    }

    private static Set<BaseNode> recurseIncomingVertex(BaseNode to, Set<Vertex<BaseNode, BaseNode>> vertexes) {
        Set<BaseNode> acc = new HashSet<>();
        for (Vertex<BaseNode, BaseNode> v : vertexes) {
            if (v.to.equals(to)) {
                acc.add( v.from );
                acc.addAll( recurseIncomingVertex(v.from, vertexes) );
            }
        }
        return acc;
    }
    
    private static void printClusterMapCluster(String levelId, Set<BaseNode> value, PrintStream out) {
        StringBuilder nodeIds = new StringBuilder();
        for (BaseNode n : value) {
            nodeIds.append(printNodeId(n)+"; ");
        }
        String level = String.format(" subgraph cluster_%1$s{style=dotted; labelloc=b; label=\"%1$s\"; %2$s}",
                levelId,
                nodeIds.toString());
        out.println(level);
    }

    private void printLevelMapLevel(String levelId, Set<BaseNode> value, PrintStream out) {
        StringBuilder nodeIds = new StringBuilder();
        for (BaseNode n : value) {
            nodeIds.append(printNodeId(n)+"; ");
        }
        if (layout == Layout.PARTITION) { 
            String level = String.format(" subgraph %1$s{%1$s[" + ((this.printDebugVerticalCluster) ? "shape=point, xlabel=\"%1$s\"" : "shape=none, label=\"\"") +  "]; %2$s}",
                    levelId,
                    nodeIds.toString());
            out.println(level);
        } else {
            String level = String.format(" {rank=same; %1$s[" + ((this.printDebugVerticalCluster) ? "shape=point, xlabel=\"%1$s\"" : "shape=none, label=\"\"") + "]; %2$s}",
                    levelId,
                    nodeIds.toString());
            out.println(level);
        }
    }

    private static void visitNodes(BaseNode node, String ident, Set<Integer> visitedNodesIDs, HashMap<Class<? extends BaseNode>, List<BaseNode>> nodeMap, List<Vertex<BaseNode, BaseNode>> vertexes, Map<Class<? extends BaseNode>, Set<BaseNode>> levelMap, PrintStream out) {
        if (!visitedNodesIDs.add( node.getId() )) {
            return;
        }
        addToNodeMap(node, nodeMap);
        addToLevel(node, levelMap);
        Sink[] sinks = getSinks( node );
        if (sinks != null) {
            for (Sink sink : sinks) {
                vertexes.add(Vertex.of(node, (BaseNode)sink));
                if (sink instanceof BaseNode) {
                    visitNodes((BaseNode)sink, ident + " ", visitedNodesIDs, nodeMap, vertexes, levelMap, out);
                }
            }
        }
    }

    private static void addToNodeMap(BaseNode node, HashMap<Class<? extends BaseNode>, List<BaseNode>> nodeMap) {
        nodeMap.computeIfAbsent(node.getClass(), k -> new ArrayList<>()).add(node);
    }

    private static void addToLevel(BaseNode node, Map<Class<? extends BaseNode>, Set<BaseNode>> levelMap) {
        levelMap.computeIfAbsent(node.getClass(), k -> new HashSet<>()).add(node);
    }

    private static String printNodeId(BaseNode node) {
        if (node instanceof EntryPointNode ) {
            return "EP"+node.getId();
        } else if (node instanceof ObjectTypeNode ) {
            return "OTN"+node.getId();
        } else if (node instanceof AlphaNode ) {
            return "AN"+node.getId();
        } else if (node instanceof LeftInputAdapterNode ) {
            return "LIA"+node.getId();
        } else if (node instanceof TupleToObjectNode) {
            return "RIA"+node.getId();
        } else if (node instanceof BetaNode ) {
            return "BN"+node.getId();
        } else if (node instanceof RuleTerminalNode ) {
            return "RTN"+node.getId();
        }
        return "UNK"+node.getId();
    }

    private static String printNodeAttributes(BaseNode node) {
        if (node instanceof EntryPointNode ) {
            EntryPointNode n = (EntryPointNode) node;
            return String.format("[shape=circle width=0.15 fillcolor=black style=filled label=\"\" xlabel=\"%1$s\"]",
                    n.getEntryPoint().getEntryPointId());
        } else if (node instanceof ObjectTypeNode ) {
            ObjectTypeNode n = (ObjectTypeNode) node;
            return String.format("[shape=rect style=rounded label=\"%1$s\"]",
                    strObjectType(n.getObjectType()) );
        } else if (node instanceof AlphaNode ) {
            AlphaNode n = (AlphaNode) node;
            return String.format("[label=\"%1$s\"]",
                    escapeDot(n.getConstraint().toString()));
        } else if (node instanceof LeftInputAdapterNode ) {
            return "[shape=house orientation=-90]";
        } else if (node instanceof TupleToObjectNode) {
            return "[shape=house orientation=90]";
        } else if (node instanceof JoinNode ) {
            BetaNode         n           = (BetaNode) node;
            BetaConstraint[] constraints = n.getConstraints();
            String           label       = "\u22C8";
            if (constraints.length > 0) {
                label = strObjectType(n.getObjectType(), false);
                label = label + "( "+ Arrays.stream(constraints).map(Object::toString).collect(joining(", ")) + " )";
            }
            return String.format("[shape=box label=\"%1$s\" href=\"http://drools.org\"]",
                    escapeDot(label));
        } else if (node instanceof NotNode ) {
            NotNode n = (NotNode) node;
            String label = "\u22C8";
            if (n.getObjectType() != null) {
                label = strObjectType(n.getObjectType(), false);
                label = label + "(";
                if ( n.getConstraints().length>0 ) {
                    label = label + " "+ Arrays.stream(n.getConstraints()).map(Object::toString).collect(joining(", ")) + " ";
                }
                label = label + ")";
            }
            return String.format("[shape=box label=\"not( %1$s )\"]", label );
        } else if (node instanceof AccumulateNode ) {
            AccumulateNode n = (AccumulateNode) node;
            return String.format("[shape=box label=<%1$s<BR/>%2$s<BR/>%3$s>]", 
                    n, Arrays.asList(n.getAccumulate().getAccumulators()), Arrays.asList(n.getConstraints()) );
        } else if (node instanceof RuleTerminalNode ) {
            RuleTerminalNode n = (RuleTerminalNode) node;
            return String.format("[shape=doublecircle width=0.2 fillcolor=black style=filled label=\"\" xlabel=\"%1$s\" href=\"http://drools.org\"]",
                    n.getRule().getName());
        }
        return String.format("[shape=box style=dotted label=\"%1$s\"]", node.toString());
    }
    
    private static String strObjectType(ObjectType ot) {
        return strObjectType(ot, true);
    }
    
    private static String strObjectType(ObjectType ot, boolean prependAbbrPackage) {
        if (ot instanceof ClassObjectType) {
            return abbrvClassForObjectType((ClassObjectType) ot, prependAbbrPackage);
        }
        return "??"+ ((ot==null)?"null":ot.toString());
    }

    private static String abbrvClassForObjectType(ClassObjectType cot, boolean prependAbbrPackage) {
        Class<?> classType = cot.getClassType();
        StringBuilder result = new StringBuilder();
        if (prependAbbrPackage) {
            String[] packageToken = classType.getPackage().getName().split("\\.");
            for (String pt : packageToken) {
                result.append(pt.charAt(0) + ".");
            }
        }
        result.append(classType.getSimpleName());
        return result.toString();
    }

    private static String escapeDot(String string) {
        String escapeQuote = string.replace("\"", "\\\"");
        return escapeQuote;
    }

    public static Sink[] getSinks( BaseNode node ) {
        Sink[] sinks = null;
        if (node instanceof EntryPointNode ) {
            EntryPointNode source = (EntryPointNode) node;
            Collection<ObjectTypeNode> otns = source.getObjectTypeNodes().values();
            sinks = otns.toArray(new Sink[otns.size()]);
        } else if (node instanceof ObjectSource ) {
            ObjectSource source = (ObjectSource) node;
            sinks = source.getObjectSinkPropagator().getSinks();
        } else if (node instanceof LeftTupleSource ) {
            LeftTupleSource source = (LeftTupleSource) node;
            sinks = source.getSinkPropagator().getSinks();
        }
        return sinks;
    }
}
