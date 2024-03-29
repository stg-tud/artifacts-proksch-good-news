/**
 * Copyright (c) 2011-2013 Darmstadt University of Technology.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Sebastian Proksch - initial API and implementation
 */
package cc.recommenders.usages;

import java.util.Set;

import com.google.common.collect.Sets;

import cc.recommenders.names.IMethodName;
import cc.recommenders.names.ITypeName;

public class Query extends AbstractUsage {

	// make sure the naming is consistent to the hardcoded names in
	// "UsageTypeAdapter"

	private ITypeName type;
	private ITypeName classCtx;
	private IMethodName methodCtx;
	private DefinitionSite definition;
	private Set<CallSite> sites = Sets.newLinkedHashSet();

	public static Query createAsCopyFrom(Usage usage) {
		Query q = new Query();
		q.setType(usage.getType());
		q.setClassContext(usage.getClassContext());
		q.setMethodContext(usage.getMethodContext());
		q.setDefinition(usage.getDefinitionSite());
		for (CallSite s : usage.getAllCallsites()) {
			q.addCallSite(s);
		}
		return q;
	}

	public void setType(ITypeName typeName) {
		this.type = typeName;
	}

	public ITypeName getType() {
		return type;
	}

	public ITypeName getClassContext() {
		return classCtx;
	}

	public IMethodName getMethodContext() {
		return methodCtx;
	}

	public DefinitionSite getDefinitionSite() {
		return definition;
	}

	public void setAllCallsites(Set<CallSite> sites) {
		this.sites = sites;
	}

	public void resetCallsites() {
		Set<CallSite> sites = Sets.newLinkedHashSet();
		setAllCallsites(sites);
	}

	public boolean addCallSite(CallSite site) {
		if (!sites.contains(site)) {
			return sites.add(site);
		} else {
			return false;
		}
	}

	public Set<CallSite> getAllCallsites() {
		return sites;
	}

	public void setClassContext(ITypeName typeName) {
		this.classCtx = typeName;
	}

	public void setMethodContext(IMethodName methodName) {
		this.methodCtx = methodName;
	}

	public void setDefinition(DefinitionSite definition) {
		this.definition = definition;
	}
}