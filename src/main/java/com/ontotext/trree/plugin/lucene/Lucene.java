package com.ontotext.trree.plugin.lucene;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;

public class Lucene {
	static final String OLD_NAMESPACE = "http://www.ontotext.com/";

	private static final ValueFactory F = SimpleValueFactory.getInstance();

	public static final String NAMESPACE = "http://www.ontotext.com/owlim/lucene#";
	public static final IRI QUERY = F.createIRI(NAMESPACE + "query");
	public static final IRI SET_PARAM = F.createIRI(NAMESPACE + "setParam");
	public static final IRI ANALYZER = F.createIRI(NAMESPACE + "analyzer");
	public static final IRI SCORER = F.createIRI(NAMESPACE + "scorer");
	public static final IRI LANGUAGES = F.createIRI(NAMESPACE + "languages");
	public static final IRI INDEX = F.createIRI(NAMESPACE + "index");
	public static final IRI INCLUDE = F.createIRI(NAMESPACE + "include");
	public static final IRI EXCLUDE = F.createIRI(NAMESPACE + "exclude");
	public static final IRI INCLUDE_PREDICATES = F.createIRI(NAMESPACE + "includePredicates");
	public static final IRI EXCLUDE_PREDICATES = F.createIRI(NAMESPACE + "excludePredicates");
	public static final IRI INCLUDE_ENTITIES = F.createIRI(NAMESPACE + "includeEntities");
	public static final IRI EXCLUDE_ENTITIES = F.createIRI(NAMESPACE + "excludeEntities");
	public static final IRI MOLECULE_SIZE = F.createIRI(NAMESPACE + "moleculeSize");
	public static final IRI USE_RDF_RANK = F.createIRI(NAMESPACE + "useRDFRank");
	public static final IRI CREATE_INDEX = F.createIRI(NAMESPACE + "createIndex");
	public static final IRI UPDATE_INDEX = F.createIRI(NAMESPACE + "updateIndex");
	public static final IRI ADD_TO_INDEX = F.createIRI(NAMESPACE + "addToIndex");
	public static final IRI SCORE = F.createIRI(NAMESPACE + "score");

	public static final IRI OLD_QUERY = F.createIRI(OLD_NAMESPACE + "luceneQuery");

}
