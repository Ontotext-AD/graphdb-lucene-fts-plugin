package com.ontotext.trree.plugin.lucene;

import org.eclipse.rdf4j.model.URI;
import org.eclipse.rdf4j.model.impl.URIImpl;

public class Lucene {
	static final String OLD_NAMESPACE = "http://www.ontotext.com/";

	public static final String NAMESPACE = "http://www.ontotext.com/owlim/lucene#";
	public static final URI QUERY = new URIImpl(NAMESPACE + "query");
	public static final URI SET_PARAM = new URIImpl(NAMESPACE + "setParam");
	public static final URI ANALYZER = new URIImpl(NAMESPACE + "analyzer");
	public static final URI SCORER = new URIImpl(NAMESPACE + "scorer");
	public static final URI LANGUAGES = new URIImpl(NAMESPACE + "languages");
	public static final URI INDEX = new URIImpl(NAMESPACE + "index");
	public static final URI INCLUDE = new URIImpl(NAMESPACE + "include");
	public static final URI EXCLUDE = new URIImpl(NAMESPACE + "exclude");
	public static final URI INCLUDE_PREDICATES = new URIImpl(NAMESPACE + "includePredicates");
	public static final URI EXCLUDE_PREDICATES = new URIImpl(NAMESPACE + "excludePredicates");
	public static final URI INCLUDE_ENTITIES = new URIImpl(NAMESPACE + "includeEntities");
	public static final URI EXCLUDE_ENTITIES = new URIImpl(NAMESPACE + "excludeEntities");
	public static final URI MOLECULE_SIZE = new URIImpl(NAMESPACE + "moleculeSize");
	public static final URI USE_RDF_RANK = new URIImpl(NAMESPACE + "useRDFRank");
	public static final URI CREATE_INDEX = new URIImpl(NAMESPACE + "createIndex");
	public static final URI UPDATE_INDEX = new URIImpl(NAMESPACE + "updateIndex");
	public static final URI ADD_TO_INDEX = new URIImpl(NAMESPACE + "addToIndex");
	public static final URI SCORE = new URIImpl(NAMESPACE + "score");

	public static final URI OLD_QUERY = new URIImpl(OLD_NAMESPACE + "luceneQuery");


}
