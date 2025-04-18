package org.eclipse.rdf4j.query.algebra.evaluation.impl.evaluationsteps;

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.algebra.evaluation.QueryEvaluationStep;
import org.eclipse.rdf4j.query.algebra.evaluation.QueryValueEvaluationStep;

import java.util.function.Predicate;

public class PreFilterQueryEvaluationStep implements QueryEvaluationStep {

	private final QueryEvaluationStep wrapped;
	private final Predicate<BindingSet> condition;

    public PreFilterQueryEvaluationStep(QueryEvaluationStep wrapped,
										QueryValueEvaluationStep condition) {
		this.wrapped = wrapped;
        this.condition = condition.asPredicate();
    }

	@Override
	public CloseableIteration<BindingSet> evaluate(BindingSet leftBindings) {
        if (!condition.test(leftBindings)) {
            // Usage of this method assume this instance is returned
            return QueryEvaluationStep.EMPTY_ITERATION;
        }

        return wrapped.evaluate(leftBindings);
    }
}
