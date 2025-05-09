/*******************************************************************************
 * Copyright (c) 2020 Eclipse RDF4J contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *******************************************************************************/
package org.eclipse.rdf4j.sail.shacl.ast.planNodes;

import java.util.Objects;

import org.apache.commons.text.StringEscapeUtils;
import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.sail.shacl.wrapper.data.ConnectionsGroup;

public class EqualsJoin implements PlanNode {
	private final PlanNode left;
	private final PlanNode right;
	private final boolean useAsFilter;
	private boolean printed = false;
	private ValidationExecutionLogger validationExecutionLogger;

	public EqualsJoin(PlanNode left, PlanNode right, boolean useAsFilter, ConnectionsGroup connectionsGroup) {
		this.left = PlanNodeHelper.handleSorting(this, left, connectionsGroup);
		this.right = PlanNodeHelper.handleSorting(this, right, connectionsGroup);
		this.useAsFilter = useAsFilter;

	}

	@Override
	public CloseableIteration<? extends ValidationTuple> iterator() {
		return new LoggingCloseableIteration(this, validationExecutionLogger) {

			private CloseableIteration<? extends ValidationTuple> leftIterator;
			private CloseableIteration<? extends ValidationTuple> rightIterator;

			ValidationTuple next;
			ValidationTuple nextLeft;
			ValidationTuple nextRight;

			@Override
			protected void init() {
				leftIterator = left.iterator();
				rightIterator = right.iterator();
			}

			void calculateNext() {
				if (next != null) {
					return;
				}

				if (nextLeft == null && leftIterator.hasNext()) {
					nextLeft = leftIterator.next();
				}

				if (nextRight == null && rightIterator.hasNext()) {
					nextRight = rightIterator.next();
				}

				if (nextLeft == null) {
					return;
				}

				while (next == null) {
					if (nextRight != null) {

						if (nextLeft.sameTargetAs(nextRight)) {
							if (useAsFilter) {
								next = nextLeft;
							} else {
								next = ValidationTupleHelper.join(nextLeft, nextRight);
							}
							nextRight = null;
						} else {

							int compareTo = nextLeft.compareActiveTarget(nextRight);

							if (compareTo < 0) {
								if (leftIterator.hasNext()) {
									nextLeft = leftIterator.next();
								} else {
									nextLeft = null;
									break;
								}
							} else {
								if (rightIterator.hasNext()) {
									nextRight = rightIterator.next();
								} else {
									nextRight = null;
									break;
								}
							}

						}
					} else {
						return;
					}
				}

			}

			@Override
			public void localClose() {
				try {
					if (leftIterator != null) {
						leftIterator.close();
					}
				} finally {
					if (rightIterator != null) {
						rightIterator.close();
					}
				}
			}

			@Override
			protected boolean localHasNext() {
				calculateNext();
				return next != null;
			}

			@Override
			protected ValidationTuple loggingNext() {
				calculateNext();
				ValidationTuple temp = next;
				next = null;
				return temp;
			}

		};
	}

	@Override
	public int depth() {
		return 0;
	}

	@Override
	public void getPlanAsGraphvizDot(StringBuilder stringBuilder) {
		if (printed) {
			return;
		}
		printed = true;
		left.getPlanAsGraphvizDot(stringBuilder);

		stringBuilder.append(getId() + " [label=\"" + StringEscapeUtils.escapeJava(this.toString()) + "\"];")
				.append("\n");
		stringBuilder.append(left.getId() + " -> " + getId() + " [label=\"left\"];").append("\n");
		stringBuilder.append(right.getId() + " -> " + getId() + " [label=\"right\"];").append("\n");
		right.getPlanAsGraphvizDot(stringBuilder);
	}

	@Override
	public String getId() {
		return null;
	}

	@Override
	public String toString() {
		return "EqualsJoin{" + "useAsFilter=" + useAsFilter + '}';
	}

	@Override
	public void receiveLogger(ValidationExecutionLogger validationExecutionLogger) {
		this.validationExecutionLogger = validationExecutionLogger;
		left.receiveLogger(validationExecutionLogger);
		right.receiveLogger(validationExecutionLogger);
	}

	@Override
	public boolean producesSorted() {
		return true;
	}

	@Override
	public boolean requiresSorted() {
		return true;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		EqualsJoin that = (EqualsJoin) o;
		return useAsFilter == that.useAsFilter && left.equals(that.left) && right.equals(that.right);
	}

	@Override
	public int hashCode() {
		return Objects.hash(left, right, useAsFilter);
	}
}
