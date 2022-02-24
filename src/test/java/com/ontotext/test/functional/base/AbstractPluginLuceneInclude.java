package com.ontotext.test.functional.base;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.rio.ParserConfig;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.helpers.BasicParserSettings;

import com.ontotext.test.utils.Utils;

public abstract class AbstractPluginLuceneInclude extends SingleRepositoryFunctionalTest {
	private static final String BSBM_TEST_DATA = "/TestBSBMData.nt";

	private static final String Q1 = "PREFIX luc: <http://www.ontotext.com/owlim/lucene#> ASK { luc:excludePredicates luc:setParam \"\" }";

	private static final String Q2 = "PREFIX luc: <http://www.ontotext.com/owlim/lucene#> ASK { luc:includePredicates luc:setParam \"\" }";

	private static final String Q3 = "PREFIX luc: <http://www.ontotext.com/owlim/lucene#> ASK { luc:moleculeSize luc:setParam \"1\" . }";

	private static final String Q4 = "PREFIX luc: <http://www.ontotext.com/owlim/lucene#> ASK { luc:index luc:setParam \"literals\" . }";

	private static final String Q5 = "PREFIX luc: <http://www.ontotext.com/owlim/lucene#> ASK { luc: luc:createIndex \"true\" . }";

	private static final String Q6 = "PREFIX luc: <http://www.ontotext.com/owlim/lucene#> SELECT ?s WHERE { ?s luc: \"Rug*\" . }";

	private static final String Q7 = "PREFIX luc: <http://www.ontotext.com/owlim/lucene#> ASK { luc:moleculeSize luc:setParam \"1\" . }";

	private static final String Q8 = "PREFIX luc: <http://www.ontotext.com/owlim/lucene#> ASK { luc:index luc:setParam \"URIS\"  . }";

	private static final String Q9 = "PREFIX luc: <http://www.ontotext.com/owlim/lucene#> ASK { luc:include luc:setParam \"literals\" . }";

	private static final String Q10 = "PREFIX luc: <http://www.ontotext.com/owlim/lucene#> ASK { luc:includePredicates luc:setParam \"http://xmlns.com/foaf/0.1/name\" }";

	private static final String Q11 = "PREFIX luc: <http://www.ontotext.com/owlim/lucene#> ASK { luc: luc:createIndex \"true\" . }";

	private static final String Q12 = "PREFIX luc: <http://www.ontotext.com/owlim/lucene#> SELECT * WHERE { ?s luc: \"Rug*\" . ?s2 ?p ?s }";

	private static final String Q13 = "PREFIX luc: <http://www.ontotext.com/owlim/lucene#> SELECT ?s WHERE { ?s luc: \"Rug*\" }";

	private static final String Q14 = "PREFIX luc: <http://www.ontotext.com/owlim/lucene#> SELECT ?p WHERE { ?s luc: \"Rug*\" . ?s2 <http://purl.org/stuff/rev#text> ?s }";

	private static final String Q15 = "PREFIX luc: <http://www.ontotext.com/owlim/lucene#> SELECT ?p WHERE { ?s luc: \"Rug*\" . ?s2 <http://purl.org/stuff/rev#text> ?s }";

	private static final String BASE_URI = "http://ex.org/test";

	@Before
	public void createRepositories() throws Exception {
		RepositoryConnection connection = getRepository().getConnection();
		ParserConfig config = new ParserConfig();
		config.set(BasicParserSettings.FAIL_ON_UNKNOWN_DATATYPES, false);
		config.set(BasicParserSettings.VERIFY_DATATYPE_VALUES, false);
		config.set(BasicParserSettings.NORMALIZE_DATATYPE_VALUES, false);
		
		connection.setParserConfig(config);

		java.io.InputStream is = this.getClass().getResourceAsStream(BSBM_TEST_DATA);
		connection.add(is, BASE_URI, RDFFormat.TURTLE);
		is.close();

		connection.commit();
		
		Utils.close(connection);
	}

	private void queryRepository(String query, int expected) throws Exception {
		RepositoryConnection connection = null;
		try {
			connection = getRepository().getConnection();
			assertEquals(expected, Utils.countResults(connection, query, QueryLanguage.SPARQL));
		} finally {
			Utils.close(connection);
		}
	}

	private void queryRepositoryBoolean(String query, boolean expected) throws Exception {
		RepositoryConnection connection = null;
		try {
			connection = getRepository().getConnection();
			assertEquals(expected, Utils.executeBooleanQuery(connection, query, QueryLanguage.SPARQL, true));
		} finally {
			Utils.close(connection);
		}
	}

	@Test
	public void luceneQueries() throws Exception {
		q1();
		q2();
		q3();
		q4();
		q5();
		q6();
		q7();
		q8();
		q9();
		q10();
		q11();
		q12();
		q13();
		q14();
		q15();
	}
	
	public void q1() throws Exception {
		queryRepositoryBoolean(Q1, true);
	}

	public void q2() throws Exception {
		queryRepositoryBoolean(Q2, true);
	}

	public void q3() throws Exception {
		queryRepositoryBoolean(Q3, true);
	}

	public void q4() throws Exception {
		queryRepositoryBoolean(Q4, true);
	}

	public void q5() throws Exception {
		queryRepositoryBoolean(Q5, true);
	}

	public void q6() throws Exception {
		queryRepository(Q6, 23);
	}

	public void q7() throws Exception {
		queryRepositoryBoolean(Q7, true);
	}

	public void q8() throws Exception {
		queryRepositoryBoolean(Q8, true);
	}

	public void q9() throws Exception {
		queryRepositoryBoolean(Q9, true);
	}

	public void q10() throws Exception {
		queryRepositoryBoolean(Q10, true);
	}

	public void q11() throws Exception {
		queryRepositoryBoolean(Q11, true);
	}

	public void q12() throws Exception {
		queryRepository(Q12, 28);
	}

	public void q13() throws Exception {
		queryRepository(Q13, 1);
		System.out.println("---- 13a -----");
		String Q13a = "PREFIX luc: <http://www.ontotext.com/owlim/lucene#> SELECT ?s WHERE { ?s luc: ?key }";
		RepositoryConnection connection = null;
		try {
			connection = getRepository().getConnection();
			TupleQuery preparedQuery = connection.prepareTupleQuery(QueryLanguage.SPARQL, Q13a);
			preparedQuery.setBinding("key", vf.createLiteral("Rug*"));
			TupleQueryResult rezult = ((TupleQuery)preparedQuery).evaluate();
			while (rezult.hasNext()) {
				System.out.println(rezult.next());
			}
			rezult.close();
		} finally {
			Utils.close(connection);
		}
	}

	public void q14() throws Exception {
		queryRepository(Q14, 0);
	}

	public void q15() throws Exception {
		queryRepository(Q15, 0);
	}
}
