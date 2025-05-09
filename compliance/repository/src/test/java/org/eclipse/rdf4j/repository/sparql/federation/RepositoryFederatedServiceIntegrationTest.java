/*******************************************************************************
 * Copyright (c) 2019 Eclipse RDF4J contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *******************************************************************************/
package org.eclipse.rdf4j.repository.sparql.federation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.rdf4j.common.iteration.Iterations;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.query.algebra.evaluation.federation.FederatedService;
import org.eclipse.rdf4j.query.algebra.evaluation.federation.FederatedServiceResolver;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Lists;

/**
 * Integration tests for {@link RepositoryFederatedService}
 *
 * @author Andreas Schwarte
 */
public class RepositoryFederatedServiceIntegrationTest {

	private static final ValueFactory vf = SimpleValueFactory.getInstance();

	private SailRepository serviceRepo;
	private SailRepository localRepo;
	private RepositoryFederatedService federatedService;

	@BeforeEach
	public void before() {
		serviceRepo = new SailRepository(new MemoryStore());
		serviceRepo.init();

		federatedService = new RepositoryFederatedService(serviceRepo);

		localRepo = new SailRepository(new MemoryStore());
		localRepo.setFederatedServiceResolver(new FederatedServiceResolver() {

			@Override
			public FederatedService getService(String serviceUrl) throws QueryEvaluationException {
				return federatedService;
			}
		});
		localRepo.init();
	}

	@AfterEach
	public void after() {
		federatedService.shutdown();
		localRepo.shutDown();
		serviceRepo.shutDown();
		System.setProperty("org.eclipse.rdf4j.repository.debug", "false");
	}

	@Test
	public void test() {

		addData(serviceRepo, Lists.newArrayList(vf.createStatement(iri("s1"), RDFS.LABEL, l("val1"))));

		String query = "SELECT ?var WHERE { VALUES ?var { 'val1' 'val2' } . SERVICE <urn:dummy> { ?s ?p ?var  } }";

		assertResultEquals(evaluateQuery(query), "var", Lists.newArrayList(l("val1")));
	}

	@Test
	public void test2() {

		addData(serviceRepo, Lists.newArrayList(
				vf.createStatement(iri("s1"), RDFS.LABEL, l("val1")),
				vf.createStatement(iri("s2"), RDFS.LABEL, l("val2")),
				vf.createStatement(iri("s3"), RDFS.LABEL, l("val3"))));

		String query = "SELECT ?var WHERE { VALUES ?var { 'val1' 'val2' } . SERVICE <urn:dummy> { ?s ?p ?var  } }";

		assertResultEquals(evaluateQuery(query), "var", Lists.newArrayList(l("val1"), l("val2")));
	}

	@Test
	public void test3() {

		addData(serviceRepo, Lists.newArrayList(
				vf.createStatement(iri("s1"), RDFS.LABEL, l("val1")),
				vf.createStatement(iri("s2"), RDFS.LABEL, l("val2")),
				vf.createStatement(iri("s3"), RDFS.LABEL, l("val3"))));

		String query = "SELECT ?var WHERE { VALUES ?var { 'val1' 'val2' } . SERVICE <urn:dummy> { SELECT ?var { ?s ?p ?var } LIMIT 1000  } } order by ?var";

		assertResultEquals(evaluateQuery(query), "var", Lists.newArrayList(l("val1"), l("val2")));
	}

	@Test
	public void test3a() {

		addData(serviceRepo, Lists.newArrayList(
				vf.createStatement(iri("s1"), RDFS.LABEL, l("val1")),
				vf.createStatement(iri("s2"), RDFS.LABEL, l("val2")),
				vf.createStatement(iri("s3"), RDFS.LABEL, l("val3"))));

		String query = "SELECT ?s ?var WHERE { VALUES ?var { 'val1' 'val2' } . OPTIONAL { SERVICE <urn:dummy> { SELECT ?s ?var { ?s ?p ?var . FILTER (?var='val2') } LIMIT 1  } } }";

		List<BindingSet> res = evaluateQuery(query);
		assertResultEquals(res, "var", Lists.newArrayList(l("val1"), l("val2")));
		assertResultEquals(res, "s", Lists.newArrayList(null, (iri("s2"))));
	}

	@Test
	public void test4() {

		addData(serviceRepo, Lists.newArrayList(
				vf.createStatement(iri("s1"), RDFS.LABEL, l("val1")),
				vf.createStatement(iri("s2"), RDFS.LABEL, l("val2")),
				vf.createStatement(iri("s3"), RDFS.LABEL, l("val3"))));

		String query = "SELECT ?var WHERE { SERVICE <urn:dummy> { ?s ?p ?var } . SERVICE <urn:dummy> {  ?s ?p ?var  } } order by ?var";

		assertResultEquals(evaluateQuery(query), "var", Lists.newArrayList(l("val1"), l("val2"), l("val3")));
	}

	@Test
	public void test4a() {

		addData(serviceRepo, Lists.newArrayList(
				vf.createStatement(iri("s1"), RDFS.LABEL, l("val1")),
				vf.createStatement(iri("s2"), RDFS.LABEL, l("val2")),
				vf.createStatement(iri("s3"), RDFS.LABEL, l("val3"))));

		// Note: here we apply a workaround and explicitly project "?__rowIdx"
		String query = "SELECT ?var WHERE { SERVICE <urn:dummy> { SELECT ?var { ?s ?p ?var } LIMIT 3 } . SERVICE <urn:dummy> { SELECT ?s ?var ?__rowIdx { ?s ?p ?var } LIMIT 3  } } order by ?var";

		assertResultEquals(evaluateQuery(query), "var", Lists.newArrayList(l("val1"), l("val2"), l("val3")));
	}

	@Test
	public void test4b() {

		addData(serviceRepo, Lists.newArrayList(
				vf.createStatement(iri("s1"), RDFS.LABEL, l("val1")),
				vf.createStatement(iri("s2"), RDFS.LABEL, l("val2")),
				vf.createStatement(iri("s3"), RDFS.LABEL, l("val3"))));

		String query = "SELECT ?var WHERE { SERVICE <urn:dummy> { SELECT ?var { ?s ?p ?var } LIMIT 3 } . SERVICE <urn:dummy> { SELECT ?s ?var { ?s ?p ?var } LIMIT 3  } } order by ?var";

		assertResultEquals(evaluateQuery(query), "var", Lists.newArrayList(l("val1"), l("val2"), l("val3")));
	}

	@Test
	public void test5() {

		addData(serviceRepo, Lists.newArrayList(vf.createStatement(iri("s1"), RDFS.LABEL, l("val1"))));

		String query = "SELECT ?var ?output WHERE { VALUES ?var { 'val1' 'val2' } . SERVICE <urn:dummy> { BIND(CONCAT(?var, '_processed') AS ?output) } }";

		List<BindingSet> res = evaluateQuery(query);
		assertResultEquals(res, "var", Lists.newArrayList(l("val1"), l("val2")));
		assertResultEquals(res, "output", Lists.newArrayList(l("val1_processed"), l("val2_processed")));
	}

	@Test
	public void test5a() {

		addData(serviceRepo, Lists.newArrayList(vf.createStatement(iri("s1"), RDFS.LABEL, l("val1"))));
		addData(serviceRepo, Lists.newArrayList(vf.createStatement(iri("s2"), RDFS.LABEL, l("val2"))));

		// Note: here we apply a workaround and explicitly project "?__rowIdx"
		String query = "SELECT ?var ?output WHERE { SERVICE <urn:dummy> { SELECT ?var { ?s ?p ?var } LIMIT 3 }  . SERVICE <urn:dummy> { SELECT (CONCAT(?var, '_processed') AS ?output) ?__rowIdx WHERE { } } }";

		List<BindingSet> res = evaluateQuery(query);
		assertResultEquals(res, "var", Lists.newArrayList(l("val1"), l("val2")));
		assertResultEquals(res, "output", Lists.newArrayList(l("val1_processed"), l("val2_processed")));
	}

	@Test
	public void test5b() {

		addData(serviceRepo, Lists.newArrayList(vf.createStatement(iri("s1"), RDFS.LABEL, l("val1"))));
		addData(serviceRepo, Lists.newArrayList(vf.createStatement(iri("s2"), RDFS.LABEL, l("val2"))));

		String query = "SELECT ?var ?output WHERE { SERVICE <urn:dummy> { SELECT ?var { ?s ?p ?var } LIMIT 3 }  . SERVICE <urn:dummy> { SELECT (CONCAT(?var, '_processed') AS ?output) WHERE { } } }";

		List<BindingSet> res = evaluateQuery(query);
		assertResultEquals(res, "var", Lists.newArrayList(l("val1"), l("val2")));
		assertResultEquals(res, "output", Lists.newArrayList(l("val1_processed"), l("val2_processed")));
	}

	@Test
	public void test6() {

		addData(serviceRepo, Lists.newArrayList(vf.createStatement(iri("s1"), RDFS.LABEL, l("val1"))));
		addData(serviceRepo, Lists.newArrayList(vf.createStatement(iri("s2"), RDFS.LABEL, l("val2"))));
		addData(serviceRepo, Lists.newArrayList(vf.createStatement(iri("s3"), RDFS.LABEL, l("val3"))));

		String query = "SELECT ?var ?cnt WHERE { SERVICE <urn:dummy> { SELECT ?var { ?s ?p ?var } LIMIT 2 }  . SERVICE <urn:dummy> { SELECT ?var ?cnt ?__rowIdx WHERE { SELECT (COUNT(?s2) AS ?cnt) WHERE { ?s2 ?p2 ?var  } } } }";

		List<BindingSet> res = evaluateQuery(query);
		assertEquals(2, res.size());
		BindingSet b1 = res.get(0);
		assertEquals(l("val1"), b1.getValue("var"));
		assertEquals(1, ((Literal) b1.getValue("cnt")).intValue());
	}

	@Test
	public void test6b() {

		addData(serviceRepo, Lists.newArrayList(vf.createStatement(iri("s1"), RDFS.LABEL, l("val1"))));
		addData(serviceRepo, Lists.newArrayList(vf.createStatement(iri("s2"), RDFS.LABEL, l("val2"))));
		addData(serviceRepo, Lists.newArrayList(vf.createStatement(iri("s3"), RDFS.LABEL, l("val3"))));

		String query = "SELECT ?var ?cnt WHERE { SERVICE <urn:dummy> { SELECT ?var { ?s ?p ?var } LIMIT 1 }  . SERVICE <urn:dummy> { SELECT ?var ?cnt ?__rowIdx WHERE { SELECT (COUNT(?s2) AS ?cnt) WHERE { ?s2 ?p2 ?var  } } } }";

		List<BindingSet> res = evaluateQuery(query);
		assertEquals(1, res.size());
		BindingSet b1 = res.get(0);
		assertEquals(l("val1"), b1.getValue("var"));
		assertEquals(1, ((Literal) b1.getValue("cnt")).intValue());
	}

	@Test
	public void test7_CrossProduct() {

		addData(serviceRepo, Lists.newArrayList(vf.createStatement(iri("s1"), RDFS.LABEL, l("serviceVal"))));

		String query = "SELECT ?var ?o WHERE { VALUES ?var { 'val1' 'val2' } . SERVICE <urn:dummy> { ?s ?p ?o  } }";

		List<BindingSet> res = evaluateQuery(query);
		assertResultEquals(res, "var", Lists.newArrayList(l("val1"), l("val2")));
		assertResultEquals(res, "o", Lists.newArrayList(l("serviceVal"), l("serviceVal")));
	}

	@Test
	public void test8_subSelectAll() {

		addData(serviceRepo, Lists.newArrayList(vf.createStatement(iri("s1"), RDFS.LABEL, l("val1"))));

		String query = "SELECT ?var WHERE { SERVICE <urn:dummy> { SELECT ?var WHERE { VALUES ?var { 'val1' 'val2' } } } . SERVICE <urn:dummy> { SELECT * WHERE { ?s ?p ?var }  } }";

		assertResultEquals(evaluateQuery(query), "var", Lists.newArrayList(l("val1")));
	}

	@Test
	public void test8a_subSelectAll() {

		addData(serviceRepo, Lists.newArrayList(vf.createStatement(iri("s1"), RDFS.LABEL, l("val1"))));

		// query has multiple whitespaces "SELECT *", thus does not insert "?__rowIdx" and goes into fallback evaluation
		String query = "SELECT ?var WHERE { SERVICE <urn:dummy> { SELECT ?var WHERE { VALUES ?var { 'val1' 'val2' } } } . SERVICE <urn:dummy> { SELECT   * WHERE { ?s ?p ?var }  } }";

		assertResultEquals(evaluateQuery(query), "var", Lists.newArrayList(l("val1")));
	}

	@Test
	public void test9_connectionHandling() throws Exception {

		/*
		 * The purpose of this test is to simulate concurrent access to the RepositoryFederatedService and thus
		 * demonstrate correct behavior for the connection handling. Particularly, this test should terminate properly,
		 * and there should not be any hanging connections waiting for the shutdown.
		 */

		System.setProperty("org.eclipse.rdf4j.repository.debug", "true");
		List<Value> values = Lists.newArrayList();
		for (int i = 0; i < 10; i++) {
			values.add(l("value" + i));
		}
		addData(serviceRepo,
				values.stream()
						.map(value -> vf.createStatement(iri("s1"), RDFS.LABEL, value))
						.collect(Collectors.toList()));

		ExecutorService executor = Executors.newFixedThreadPool(5);
		try {
			for (int i = 0; i < 5; i++) {
				executor.submit(() -> {

					String query = "SELECT ?var WHERE { SERVICE <urn:dummy> { ?s ?p ?var  } }";
					assertResultEquals(evaluateQuery(query), "var", values);
				});
			}

		} finally {
			executor.shutdown();
			executor.awaitTermination(10, TimeUnit.SECONDS);
		}

	}

	@Test
	public void test10_consumePartially() {

		/*
		 * The purpose of this test is validate that connections are closed properly, even if a result is consume only
		 * partially. If it wasn't working we would see a hanging junit testing waiting for connections to close
		 */

		List<Value> values = Lists.newArrayList();
		for (int i = 0; i < 10; i++) {
			values.add(l("value" + i));
		}
		addData(serviceRepo,
				values.stream()
						.map(value -> vf.createStatement(iri("s1"), RDFS.LABEL, value))
						.collect(Collectors.toList()));

		String query = "SELECT ?var WHERE { SERVICE <urn:dummy> { ?s ?p ?var  } }";
		try (RepositoryConnection conn = localRepo.getConnection()) {
			try (TupleQueryResult tqr = conn.prepareTupleQuery(query).evaluate()) {

				// consume only two items
				tqr.next();
				tqr.next();
			}
		}
	}

	private void addData(Repository repo, Collection<? extends Statement> m) {
		try (RepositoryConnection conn = repo.getConnection()) {
			conn.add(m);
		}
	}

	private List<BindingSet> evaluateQuery(String query) {
		try (RepositoryConnection conn = localRepo.getConnection()) {
			try (TupleQueryResult tqr = conn.prepareTupleQuery(query).evaluate()) {
				return Iterations.asList(tqr);
			}
		}
	}

	private IRI iri(String localName) {
		return vf.createIRI("http://example.org/resource/", localName);
	}

	private Literal l(String literal) {
		return vf.createLiteral(literal);
	}

	private void assertResultEquals(List<BindingSet> res, String bindingName, List<Value> expected) {
		assertEquals(expected, res.stream().map(b -> b.getValue(bindingName)).collect(Collectors.toList()));
	}
}
