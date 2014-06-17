package de.tudarmstadt.lt.cw.graph;

import it.unimi.dsi.fastutil.ints.Int2FloatMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;

import java.util.ArrayList;
import java.util.Iterator;

import de.tudarmstadt.lt.cw.CW;

public class ArrayBackedGraphCW extends CW<Integer> {
	ArrayBackedGraph<Float> graph;
	
	final Integer[] nodeLabels;
	final Int2FloatOpenHashMap labelScores;
	Integer lastAssignedLabel = null;
	
	public ArrayBackedGraphCW(int arraySize) {
		nodeLabels = new Integer[arraySize];
		labelScores = new Int2FloatOpenHashMap();
	}
	
//	long foo = 0;
	@Override
	protected void init(IGraph<Integer, Float> graph) {
		this.graph = (ArrayBackedGraph<Float>)graph;
		int numNodes = this.graph.nodes.cardinality();
		nodes = new ArrayList<Integer>(numNodes);
		Iterator<Integer> nodeIt = graph.iterator();
		while (nodeIt.hasNext()) {
			Integer node = nodeIt.next();
			nodes.add(node);
			nodeLabels[node] = node;
		}
		/*if (foo % 100 == 0) {
			System.out.println("Time spent in a: " + a / 1000000000.0);
			System.out.println("Time spent in b: " + b / 1000000000.0);
			System.out.println("Time spent in c: " + c / 1000000000.0);
			System.out.println("Time spent in d: " + d / 1000000000.0);
			System.out.println("Time spent in e: " + e / 1000000000.0);
			System.out.println("Time spent in f: " + f / 1000000000.0);
		}
		foo++;*/
	}
	
	@Override
	protected Integer getNodeLabel(Integer node) {
		return nodeLabels[node];
	}
	
//	long a = 0, b = 0, c = 0, d = 0, e = 0, f = 0;
	@Override
	protected void relabelNode(Integer node) {
//		System.out.println("+++ relabel " + node);
//		System.out.println("BEFORE: " + nodeLabels);
		Integer oldLabel = nodeLabels[node];
		ArrayList<Integer> edges = graph.edgeTargets[node];
		ArrayList<Float> weights = graph.edgeWeights[node];
		if (edges == null) {
			return;
		}
		labelScores.clear();
		for (int i = 0; i < edges.size(); i++) {
			int target = edges.get(i);
			float weight = weights.get(i);
			Integer label = nodeLabels[target];
			labelScores.addTo(label, weight);
		}

		Integer newLabel = oldLabel;
		Float maxScore = 0.0f;
		for (Entry labelScore : labelScores.int2FloatEntrySet()) {
			int n = labelScore.getIntKey();
			float score = labelScore.getFloatValue();
			if (score > maxScore) {
				newLabel = n;
				maxScore = score;
			}
		}
		if (!oldLabel.equals(newLabel)) {
			nodeLabels[node] = newLabel;
			changeInPrevStep = true;
		}
	}
}
