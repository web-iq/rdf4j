/*******************************************************************************
 * Copyright (c) 2021 Eclipse RDF4J contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *******************************************************************************/
package org.eclipse.rdf4j.sail.shacl.ast.planNodes;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.text.StringEscapeUtils;
import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.sail.SailConnection;
import org.eclipse.rdf4j.sail.shacl.ast.SparqlFragment;
import org.eclipse.rdf4j.sail.shacl.ast.StatementMatcher;
import org.eclipse.rdf4j.sail.shacl.ast.StatementMatcher.Variable;
import org.eclipse.rdf4j.sail.shacl.ast.constraintcomponents.ConstraintComponent;
import org.eclipse.rdf4j.sail.shacl.ast.targets.EffectiveTarget;

/**
 * Used to signal bulk validation. This plan node should only be used from EffectiveTarget#getAllTargets
 */
public class AllTargetsPlanNode implements PlanNode {

	private final Select select;
	private StackTraceElement[] stackTrace;
	private boolean printed;
	private ValidationExecutionLogger validationExecutionLogger;

	public AllTargetsPlanNode(SailConnection sailConnection,
			Resource[] dataGraph, ArrayDeque<EffectiveTarget.EffectiveTargetFragment> chain,
			List<Variable<Value>> vars,
			ConstraintComponent.Scope scope) {

		List<SparqlFragment> sparqlFragments = chain.stream()
				.map(EffectiveTarget.EffectiveTargetFragment::getQueryFragment)
				.collect(Collectors.toList());

		SparqlFragment sparqlFragment = SparqlFragment.join(sparqlFragments);

		List<String> varNames = vars.stream().map(StatementMatcher.Variable::getName).collect(Collectors.toList());

		this.select = new Select(sailConnection, sparqlFragment, null,
				new AllTargetsBindingSetMapper(varNames, scope, false, dataGraph), dataGraph);

	}

	@Override
	public CloseableIteration<? extends ValidationTuple> iterator() {

		return new LoggingCloseableIteration(this, validationExecutionLogger) {

			private CloseableIteration<? extends ValidationTuple> iterator;

			@Override
			protected void init() {
				iterator = select.iterator();
			}

			@Override
			public void localClose() {
				if (iterator != null) {
					iterator.close();
				}
			}

			@Override
			protected ValidationTuple loggingNext() {
				return iterator.next();
			}

			@Override
			protected boolean localHasNext() {
				return iterator.hasNext();
			}
		};

	}

	@Override
	public int depth() {
		return select.depth() + 1;
	}

	@Override
	public void getPlanAsGraphvizDot(StringBuilder stringBuilder) {
		if (printed) {
			return;
		}
		printed = true;
		stringBuilder.append(getId() + " [label=\"" + StringEscapeUtils.escapeJava(this.toString()) + "\"];")
				.append("\n");
		stringBuilder.append(select.getId() + " -> " + getId()).append("\n");
		select.getPlanAsGraphvizDot(stringBuilder);
	}

	@Override
	public String getId() {
		return System.identityHashCode(this) + "";
	}

	@Override
	public String toString() {
		return "AllTargetsPlanNode";
	}

	@Override
	public void receiveLogger(ValidationExecutionLogger validationExecutionLogger) {
		this.validationExecutionLogger = validationExecutionLogger;
		select.receiveLogger(validationExecutionLogger);
	}

	@Override
	public boolean producesSorted() {
		return false;
	}

	@Override
	public boolean requiresSorted() {
		return false;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		AllTargetsPlanNode that = (AllTargetsPlanNode) o;
		return select.equals(that.select);
	}

	@Override
	public int hashCode() {
		return Objects.hash(select);
	}

	public static class AllTargetsBindingSetMapper implements Function<BindingSet, ValidationTuple> {
		private final List<String> varNames;
		private final ConstraintComponent.Scope scope;
		private final boolean hasValue;
		private final Resource[] contexts;

		public AllTargetsBindingSetMapper(List<String> varNames, ConstraintComponent.Scope scope, boolean hasValue,
				Resource[] contexts) {
			this.varNames = varNames;
			this.scope = scope;
			this.hasValue = hasValue;
			this.contexts = contexts;
		}

		@Override
		public ValidationTuple apply(BindingSet b) {
			return new ValidationTuple(b, varNames, scope, false, contexts);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			AllTargetsBindingSetMapper that = (AllTargetsBindingSetMapper) o;
			return hasValue == that.hasValue && varNames.equals(that.varNames) && scope == that.scope
					&& Arrays.equals(contexts, that.contexts);
		}

		@Override
		public int hashCode() {
			int result = Objects.hash(varNames, scope, hasValue);
			result = 31 * result + Arrays.hashCode(contexts);
			return result;
		}
	}

}
