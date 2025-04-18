package org.eclipse.rdf4j.query.algebra.evaluation.impl.evaluationsteps.values;

import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.query.Binding;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.algebra.evaluation.QueryBindingSet;
import org.eclipse.rdf4j.query.algebra.evaluation.QueryValueEvaluationStep;

import java.util.Set;

public class ScopedQueryValueEvaluationStep implements QueryValueEvaluationStep {

    /**
	 * The set of binding names that are "in scope" for the filter. The filter must not include bindings that are (only)
	 * included because of the depth-first evaluation strategy in the evaluation of the constraint.
	 */
    private final Set<String> scopeBindingNames;
    private final QueryValueEvaluationStep wrapped;

    public ScopedQueryValueEvaluationStep(Set<String> scopeBindingNames, QueryValueEvaluationStep condition) {
        this.scopeBindingNames = scopeBindingNames;
        this.wrapped = condition;
    }

	@Override
	public Value evaluate(BindingSet bindings) {
		BindingSet scopeBindings = createScopeBindings(scopeBindingNames, bindings);

		return wrapped.evaluate(scopeBindings);
	}

	private BindingSet createScopeBindings(Set<String> scopeBindingNames, BindingSet bindings) {
		QueryBindingSet scopeBindings = new QueryBindingSet(scopeBindingNames.size());
		for (String scopeBindingName : scopeBindingNames) {
			Binding binding = bindings.getBinding(scopeBindingName);
			if (binding != null) {
				scopeBindings.addBinding(binding);
			}
		}

		return scopeBindings;
	}
}
