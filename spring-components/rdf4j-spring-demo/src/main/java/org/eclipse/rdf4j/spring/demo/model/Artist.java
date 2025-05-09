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

package org.eclipse.rdf4j.spring.demo.model;

import java.util.Objects;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;

/**
 * @author Florian Kleedorfer
 * @since 4.0.0
 */
public class Artist {
	public static final Variable ARTIST_ID = SparqlBuilder.var("artist_id");
	public static final Variable ARTIST_FIRST_NAME = SparqlBuilder.var("artist_firstName");
	public static final Variable ARTIST_LAST_NAME = SparqlBuilder.var("artist_lastName");
	private IRI id;
	private String firstName;
	private String lastName;

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public IRI getId() {
		return id;
	}

	public void setId(IRI id) {
		this.id = id;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		Artist artist = (Artist) o;
		return Objects.equals(id, artist.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}
}
