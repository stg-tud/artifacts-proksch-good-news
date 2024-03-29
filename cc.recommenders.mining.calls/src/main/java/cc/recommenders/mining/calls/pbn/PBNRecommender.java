/**
 * Copyright (c) 2010-2013 Darmstadt University of Technology.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Sebastian Proksch - initial API and implementation
 */
package cc.recommenders.mining.calls.pbn;

import static cc.recommenders.datastructures.Tuple.newTuple;
import static cc.recommenders.mining.calls.pbn.PBNModelConstants.CALL_PREFIX;
import static cc.recommenders.mining.calls.pbn.PBNModelConstants.CLASS_CONTEXT_TITLE;
import static cc.recommenders.mining.calls.pbn.PBNModelConstants.DEFINITION_TITLE;
import static cc.recommenders.mining.calls.pbn.PBNModelConstants.METHOD_CONTEXT_TITLE;
import static cc.recommenders.mining.calls.pbn.PBNModelConstants.PATTERN_TITLE;
import static cc.recommenders.mining.calls.pbn.PBNModelConstants.STATE_TRUE;
import static cc.recommenders.mining.calls.pbn.PBNModelConstants.newClassContext;
import static cc.recommenders.mining.calls.pbn.PBNModelConstants.newDefinition;
import static cc.recommenders.mining.calls.pbn.PBNModelConstants.newMethodContext;
import static cc.recommenders.mining.calls.pbn.PBNModelConstants.newParameterSite;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static com.google.common.collect.Sets.newHashSet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.recommenders.commons.bayesnet.BayesianNetwork;
import org.eclipse.recommenders.commons.bayesnet.Node;
import org.eclipse.recommenders.jayes.BayesNet;
import org.eclipse.recommenders.jayes.BayesNode;
import org.eclipse.recommenders.jayes.inference.junctionTree.JunctionTreeAlgorithm;
import org.eclipse.recommenders.jayes.util.NumericalInstabilityException;

import cc.recommenders.datastructures.Tuple;
import cc.recommenders.io.Logger;
import cc.recommenders.mining.calls.ICallsRecommender;
import cc.recommenders.mining.calls.ProposalHelper;
import cc.recommenders.mining.calls.QueryOptions;
import cc.recommenders.names.IMethodName;
import cc.recommenders.names.ITypeName;
import cc.recommenders.names.VmMethodName;
import cc.recommenders.usages.CallSite;
import cc.recommenders.usages.Query;

public class PBNRecommender implements ICallsRecommender<Query> {

	private BayesNet bayesNet;
	private BayesNode patternNode;
	private BayesNode classContextNode;
	private BayesNode methodContextNode;
	private BayesNode definitionNode;

	private Map<IMethodName, BayesNode> callNodes = newHashMap();
	private Map<String, BayesNode> paramNodes = newHashMap();

	private JunctionTreeAlgorithm junctionTreeAlgorithm;
	private QueryOptions options;

	private Set<IMethodName> queriedMethods = newHashSet();

	public PBNRecommender(BayesianNetwork network, QueryOptions options) {
		this.options = options;
		initializeNetwork(network);
	}

	private void initializeNetwork(final BayesianNetwork network) {
		bayesNet = new BayesNet();

		initializeNodes(network);
		initializeArcs(network);
		initializeProbabilities(network);

		junctionTreeAlgorithm = new JunctionTreeAlgorithm();
		if (!options.useDoublePrecision) {
			junctionTreeAlgorithm.getFactory().setFloatingPointType(float.class);
		}
		junctionTreeAlgorithm.setNetwork(bayesNet);
	}

	private void initializeNodes(final BayesianNetwork network) {
		for (final Node node : network.getNodes()) {
			BayesNode bayesNode = createNodeFrom(node);
			assignToClassMember(node, bayesNode);
		}
	}

	private BayesNode createNodeFrom(Node node) {
		BayesNode bayesNode = bayesNet.createNode(node.getIdentifier());
		String[] states = node.getStates();
		for (int i = 0; i < states.length; i++) {
			try {
				bayesNode.addOutcome(states[i]);
			} catch (IllegalArgumentException e) {
				Logger.err("error when adding outcome %s: %s", states[i], e.getMessage());
			}
		}
		return bayesNode;
	}

	private void assignToClassMember(Node node, BayesNode bayesNode) {
		String nodeTitle = node.getIdentifier();
		if (nodeTitle.equals(CLASS_CONTEXT_TITLE)) {
			classContextNode = bayesNode;
		} else if (nodeTitle.equals(METHOD_CONTEXT_TITLE)) {
			methodContextNode = bayesNode;
		} else if (nodeTitle.equals(DEFINITION_TITLE)) {
			definitionNode = bayesNode;
		} else if (nodeTitle.equals(PATTERN_TITLE)) {
			patternNode = bayesNode;
		} else if (nodeTitle.startsWith(CALL_PREFIX)) {
			IMethodName call = VmMethodName.get(nodeTitle.substring(CALL_PREFIX.length()));
			callNodes.put(call, bayesNode);
		} else {
			paramNodes.put(nodeTitle, bayesNode);
		}
	}

	private void initializeArcs(final BayesianNetwork network) {
		for (final Node node : network.getNodes()) {
			Node[] parents = node.getParents();
			BayesNode children = bayesNet.getNode(node.getIdentifier());
			List<BayesNode> bnParents = newArrayList();
			for (int i = 0; i < parents.length; i++) {
				String parentTitle = parents[i].getIdentifier();
				bnParents.add(bayesNet.getNode(parentTitle));
			}
			children.setParents(bnParents);
		}
	}

	private void initializeProbabilities(final BayesianNetwork network) {
		for (final Node node : network.getNodes()) {
			final BayesNode bayesNode = bayesNet.getNode(node.getIdentifier());
			bayesNode.setProbabilities(node.getProbabilities());
		}
	}

	protected void clearEvidence() {
		junctionTreeAlgorithm.setEvidence(new HashMap<BayesNode, String>());
		queriedMethods.clear();
	}

	@Override
	public Set<Tuple<IMethodName, Double>> query(Query u) {
		clearEvidence();

		if (options.useClassContext) {
			addEvidenceIfAvailableInNetwork(classContextNode, newClassContext(u.getClassContext()));
		}
		if (options.useMethodContext) {
			addEvidenceIfAvailableInNetwork(getMethodContextNode(), newMethodContext(u.getMethodContext()));
		}
		if (options.useDefinition) {
			addEvidenceIfAvailableInNetwork(definitionNode, newDefinition(u.getDefinitionSite()));
		}

		ITypeName type = u.getType();
		for (CallSite site : u.getAllCallsites()) {
			markRebasedSite(type, site);
		}
		
		return collectCallProbabilities();
	}

	private void addEvidenceIfAvailableInNetwork(BayesNode node, String outcome) {
		if (node.getOutcomes().contains(outcome)) {
			junctionTreeAlgorithm.addEvidence(node, outcome);
			// debug("outcome marked '%s'", node.getName());
		} else {
			debug("unknown outcome: %s (%s)", outcome, node.getName());
		}
	}

	private void markRebasedSite(ITypeName type, CallSite site) {
		switch (site.getKind()) {
		case PARAMETER:
			if (options.useParameterSites) {
				String nodeTitle = newParameterSite(site.getMethod(), site.getArgIndex());
				BayesNode node = paramNodes.get(nodeTitle);
				if (node != null) {
					junctionTreeAlgorithm.addEvidence(node, STATE_TRUE);
					// debug("outcome marked 'parameter'");
				} else {
					debug("unknown node: %s (%s)", nodeTitle, type);
				}
			}
			break;
		case RECEIVER:
			// TODO re-enable rebasing (here and in modelBuilder)
			// IMethodName rebasedName = rebase(type, site.targetMethod);
			// BayesNode node = callNodes.get(rebasedName);

			// it is not necessary to call OUMC.newCallSite(...), because the
			// prefix is already stripped in that map (see
			// assignToClassMember())
			BayesNode node = callNodes.get(site.getMethod());
			if (node != null) {
				// queriedMethods.add(rebasedName);
				queriedMethods.add(site.getMethod());
				junctionTreeAlgorithm.addEvidence(node, STATE_TRUE);
				// debug("outcome marked 'method call'");
			} else {
				debug("unknown node: %S%s (%s)", CALL_PREFIX, site.getMethod(), type);
			}
			break;
		}
	}

	private Set<Tuple<IMethodName, Double>> collectCallProbabilities() {
		Set<Tuple<IMethodName, Double>> res = ProposalHelper.createSortedSet();
		try {
			for (IMethodName methodName : callNodes.keySet()) {
				if (!isPartOfQuery(methodName)) {
					BayesNode node = callNodes.get(methodName);
					if (node == null) {
						debug("no node found for %s", methodName);
					} else {
						double[] beliefs = junctionTreeAlgorithm.getBeliefs(node);
						boolean isGreaterOrEqualToMinProbability = beliefs[0] >= options.minProbability;
						if (isGreaterOrEqualToMinProbability) {
							Tuple<IMethodName, Double> tuple = newTuple(methodName, beliefs[0]);
							res.add(tuple);
						}
					}
				}
			}
		} catch (NumericalInstabilityException e) {
			Logger.err("NumericalInstabilityException: %s", e.getMessage());
		}
		return res;
	}

	private boolean isPartOfQuery(IMethodName methodName) {
		return queriedMethods.contains(methodName);
	}

	@Override
	public Set<Tuple<String, Double>> getPatternsWithProbability() {
		Set<Tuple<String, Double>> res = ProposalHelper.createSortedSet();
		clearEvidence();
		double[] beliefs = junctionTreeAlgorithm.getBeliefs(patternNode);
		for (int i = 0; i < patternNode.getOutcomeCount(); i++) {
			String outcome = patternNode.getOutcomeName(i);
			Tuple<String, Double> tuple = newTuple(outcome, beliefs[i]);
			res.add(tuple);
		}
		return res;
	}

	@Override
	public Set<Tuple<IMethodName, Double>> queryPattern(String patternName) {
		clearEvidence();
		junctionTreeAlgorithm.addEvidence(patternNode, patternName);
		return collectCallProbabilities();
	}

	@Override
	public int getSize() {
		int size = 0;
		for (BayesNode n : bayesNet.getNodes()) {
			int numValues = n.getProbabilities().length;
			int bytePerValue = options.useDoublePrecision ? 8 : 4;
			size += numValues * bytePerValue;
		}
		return size;
	}
	
	protected double[] getBeliefs(BayesNode node) {
		return this.junctionTreeAlgorithm.getBeliefs(node);
	}
	

	protected BayesNode getClassContextNode() {
		return this.classContextNode;
	}
	
	protected BayesNode getDefinitionNode() {
		return this.definitionNode;
	}
	
	protected BayesNode getMethodContextNode() {
		return this.methodContextNode;
	}
	
	protected QueryOptions getOptions() {
		return this.options;
	}
	
	protected BayesNode getPatternNode() {
		return this.patternNode;
	}
	

	private static void debug(String msg, Object... args) {
		// Logger.debug(msg, args);
	}

}