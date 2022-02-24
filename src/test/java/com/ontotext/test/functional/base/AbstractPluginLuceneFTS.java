package com.ontotext.test.functional.base;

import com.ontotext.test.utils.SparqlHelper;
import com.ontotext.test.utils.Utils;
import com.ontotext.trree.plugin.lucene.Lucene;
import org.eclipse.rdf4j.common.exception.RDF4JException;
import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public abstract class AbstractPluginLuceneFTS extends SingleRepositoryFunctionalTest {
	private boolean useUpdate;
	//We need static factory for constants
	protected static final ValueFactory F = SimpleValueFactory.getInstance();

	public AbstractPluginLuceneFTS(boolean useUpdate) {
		this.useUpdate = useUpdate;
	}

	@Parameters
	public static List<Object[]> getParams() {
		return Arrays.<Object[]>asList(
				new Object[] { true },
				new Object[] { false });
	}

	@Before
	public void setUp() throws Exception {
		RepositoryConnection connection = null;

		try {
			connection = getRepository().getConnection();
			connection.begin();

			connection.clear();
			connection.commit();

			SparqlHelper helper = new SparqlHelper(connection);

			String update =
					"INSERT DATA { " +
					"ex:a ex:label \"some funky label\" . " +
					"ex:b ex:label \"alabala\" . " +
					"ex:c ex:label \"pretty informative label\" . " +
					"_:b1 ex:label \"first blank label\" . " +
					"_:b2 ex:label \"second blank label\" . " +
					"ex:a ex:link _:b1 . " +
					"_:b1 ex:link ex:b . " +
					"ex:b ex:link _:b2 . " +
					"_:b2 ex:link ex:c . " +
					"_:b3 ex:label \"test\" . " +
					"_:b3 ex:label \"test\"@bg . " +
					"_:b3 ex:label \"danke\" . " +
					"_:b3 ex:label \"danke\"@de . " +
					" }";

			helper.update( update, false);
		} finally {
			Utils.close(connection);
		}
	}

	@Before
	public void prepareTestCase() throws RDF4JException {
		// set default parameter values
		setParam(Lucene.LANGUAGES, "");
		setParam(Lucene.INDEX, "literals");
		setParam(Lucene.INCLUDE, "literals");
		setParam(Lucene.INCLUDE_ENTITIES, "");
		setParam(Lucene.INCLUDE_PREDICATES, "");
		setParam(Lucene.EXCLUDE, "");
		setParam(Lucene.EXCLUDE_ENTITIES, "");
		setParam(Lucene.EXCLUDE_PREDICATES, "");
		setParam(Lucene.MOLECULE_SIZE, "0");
		String customAnalyzer = getCustomAnalyzer();
		if (customAnalyzer != null) {
			setParam(Lucene.ANALYZER, customAnalyzer);
		}
	}

	private void testQuery(String indexName, String query, Value... values) throws RDF4JException {
		RepositoryConnection connection = null;
		try {
			connection = getRepository().getConnection();

			String sparql =
					"SELECT ?s { ?s <" + Lucene.NAMESPACE + indexName + "> \"" + query + "\" }";

			SparqlHelper helper = new SparqlHelper(connection);
			helper.tupleQuery(sparql, false);

			for( Value value : values) {
				helper.verify("s", value);
			}
			helper.verifyNoBindingsRemaining();
		} finally {
			Utils.close(connection);
		}
	}

	private void setParam(IRI name, String value) throws RDF4JException {
		assertTrue(eval(
				name,
				Lucene.SET_PARAM,
				vf.createLiteral(value)));
	}

	private void createIndex(String name) throws RDF4JException {
		assertTrue(eval(
				vf.createIRI(Lucene.NAMESPACE + name),
				Lucene.CREATE_INDEX,
				vf.createLiteral("true")));
	}

	private void addToIndex(String name, Value node) throws RDF4JException {
		assertTrue(eval(
				vf.createIRI(Lucene.NAMESPACE + name),
				Lucene.ADD_TO_INDEX,
				node));
	}

	private boolean eval(IRI subject, IRI predicate, Value object) throws RDF4JException {
		RepositoryConnection connection = null;
		try {
			connection = getRepository().getConnection();

			boolean result = false;
			if (useUpdate) {
				connection.begin();
				connection.add(subject,  predicate, object);
				connection.commit();
				result = true;
			} else {
				RepositoryResult<Statement> iter = connection.getStatements(subject,  predicate, object, false);
				result = iter.hasNext();
				Utils.close(iter);
			}
			return result;
		} finally {
			Utils.close(connection);
		}
	}

	protected String getCustomAnalyzer() {
		return null;
	}

	private static final Literal FIRST_BLANK_LABEL = F.createLiteral("first blank label");
	private static final Literal SECOND_BLANK_LABEL = F.createLiteral("second blank label");
	private static final Literal SOME_FUNKY_LABEL = F.createLiteral("some funky label");
	private static final Literal PRETTY_INFORMATIVE_LABEL = F.createLiteral("pretty informative label");
	private static final Literal DANKE  = F.createLiteral("danke");
	private static final Literal DANKE_DE  = F.createLiteral("danke", "de");
	private static final Literal TEST  = F.createLiteral("test");
	private static final Literal TEST_BG  = F.createLiteral("test", "bg");
	private static final Literal ALABALA = F.createLiteral("alabala");

	private static final IRI LABEL_PREDICATE  = F.createIRI(SparqlHelper.exampleNamespace() + "label");
	private static final IRI LINK_PREDICATE  = F.createIRI(SparqlHelper.exampleNamespace() + "link");
	private static final IRI A = F.createIRI(SparqlHelper.exampleNamespace() + "a");
	private static final IRI B = F.createIRI(SparqlHelper.exampleNamespace() + "b");
	private static final IRI C = F.createIRI(SparqlHelper.exampleNamespace() + "c");

	private static final BNode B1 = F.createBNode("b1");
	private static final BNode B2 = F.createBNode("b2");

	@Test
	public void testMoleculeSize0OnlyLiterals() throws Exception {
		setParam(Lucene.MOLECULE_SIZE, "0");
		setParam(Lucene.INDEX, "uris,literals");
		setParam(Lucene.INCLUDE, "literals");
		createIndex("idx");
		testQuery("idx", "blan*", FIRST_BLANK_LABEL, SECOND_BLANK_LABEL);
		testQuery("idx", "example.com");
		testQuery("idx", "al*bal*", ALABALA);
	}

	@Test
	public void testMoleculeSize0NotOnlyLiterals() throws Exception {
		setParam(Lucene.MOLECULE_SIZE, "0");
		setParam(Lucene.INDEX, "uris,literals");
		setParam(Lucene.INCLUDE, "   uri, literal  ");
		createIndex("idx");
		testQuery("idx", "blan*", FIRST_BLANK_LABEL, SECOND_BLANK_LABEL);
		testQuery("idx", "c", C);
	}

	@Test
	public void testMoleculeSize1NotOnlyLiterals() throws Exception {
		setParam(Lucene.MOLECULE_SIZE, "1");
		setParam(Lucene.INDEX, "uris,literals,bnodes");
		setParam(Lucene.INCLUDE, "uri,literal");
		createIndex("idx");
		testQuery("idx", "blan*", FIRST_BLANK_LABEL, SECOND_BLANK_LABEL,
				A, B, B1, B2);
		testQuery("idx", "c", B, C, B2);
		testQuery("idx", "some", A, SOME_FUNKY_LABEL);
		testQuery("idx", "first", A, FIRST_BLANK_LABEL, B1);
		testQuery("idx", "label", A, B, C,
				LABEL_PREDICATE, FIRST_BLANK_LABEL, B1, B2,
				SECOND_BLANK_LABEL, SOME_FUNKY_LABEL, PRETTY_INFORMATIVE_LABEL);
		testQuery("idx", "second", B, SECOND_BLANK_LABEL, B2);
		testQuery("idx", "alabala", B, ALABALA);
	}

	@Test
	public void testMoleculeSize1OnlyLiterals() throws Exception {
		setParam(Lucene.MOLECULE_SIZE, "1");
		setParam(Lucene.INDEX, "uris,literals,bnodes");
		setParam(Lucene.INCLUDE, "literals");
		createIndex("idx");
		testQuery("idx", "blan*", FIRST_BLANK_LABEL, SECOND_BLANK_LABEL,
				A, B, B1, B2);
		testQuery("idx", "example.com");
		testQuery("idx", "example.com AND b");
		testQuery("idx", "some", A, SOME_FUNKY_LABEL);
		testQuery("idx", "first", A, FIRST_BLANK_LABEL, B1);
		testQuery("idx", "label", A, B, C,
				FIRST_BLANK_LABEL, B1, B2, SECOND_BLANK_LABEL, SOME_FUNKY_LABEL,
				PRETTY_INFORMATIVE_LABEL);
		testQuery("idx", "second", B, SECOND_BLANK_LABEL, B2);
		testQuery("idx", "alabala", B, ALABALA);
	}

	@Test
	public void testMoleculeSize2NotOnlyLiterals() throws Exception {
		setParam(Lucene.MOLECULE_SIZE, "2");
		setParam(Lucene.INDEX, "uris,literals,bnodes");
		setParam(Lucene.INCLUDE, "uris, literals");
		createIndex("idx");
		testQuery("idx", "blan*", FIRST_BLANK_LABEL, SECOND_BLANK_LABEL,
				A, B, B1, B2);
		testQuery("idx", "c", A, B, C,
				B1, B2);
		testQuery("idx", "c AND b", A, B, B1);
		testQuery("idx", "some", A, SOME_FUNKY_LABEL);
		testQuery("idx", "first", A, FIRST_BLANK_LABEL, B1);
		testQuery("idx", "label", A, B, C,
				LABEL_PREDICATE, FIRST_BLANK_LABEL, B1, B2,
				SECOND_BLANK_LABEL, SOME_FUNKY_LABEL, PRETTY_INFORMATIVE_LABEL);
		testQuery("idx", "second", A, B,
				SECOND_BLANK_LABEL, B1, B2);
		testQuery("idx", "alabala", A, B, ALABALA, B1);
		testQuery("idx", "informative", PRETTY_INFORMATIVE_LABEL, C,
				B, B2);
	}

	@Test
	public void testMoleculeSize2OnlyLiterals() throws Exception {
		setParam(Lucene.MOLECULE_SIZE, "2");
		setParam(Lucene.INDEX, "uris,literals,bnodes");
		setParam(Lucene.INCLUDE, "literal");
		createIndex("idx");
		testQuery("idx", "blan*", FIRST_BLANK_LABEL, SECOND_BLANK_LABEL,
				A, B, B1, B2);
		testQuery("idx", "example.com");
		testQuery("idx", "example.com AND b");
		testQuery("idx", "some", A, SOME_FUNKY_LABEL);
		testQuery("idx", "first", A, FIRST_BLANK_LABEL, B1);
		testQuery("idx", "label", A, B, C,
				FIRST_BLANK_LABEL, B1, B2, SECOND_BLANK_LABEL, SOME_FUNKY_LABEL,
				PRETTY_INFORMATIVE_LABEL);
		testQuery("idx", "second", A, B,
				SECOND_BLANK_LABEL, B1, B2);
		testQuery("idx", "alabala", A, B, ALABALA, B1);
		testQuery("idx", "informative", PRETTY_INFORMATIVE_LABEL, C,
				B, B2);
	}

	@Test
	public void testMoleculeSize3NotOnlyLiterals() throws Exception {
		setParam(Lucene.MOLECULE_SIZE, "3");
		setParam(Lucene.INDEX, "uris,literals,bnodes");
		setParam(Lucene.INCLUDE, "uris,literals");
		createIndex("idx");
		testQuery("idx", "blan*", FIRST_BLANK_LABEL, SECOND_BLANK_LABEL,
				A, B, B1, B2);
		testQuery("idx", "c", A, B, C,
				B1, B2);
		testQuery("idx", "c AND b", A, B, B1);
		testQuery("idx", "some", A, SOME_FUNKY_LABEL);
		testQuery("idx", "first", A, FIRST_BLANK_LABEL, B1);
		testQuery("idx", "label", A, B, C,
				LABEL_PREDICATE, FIRST_BLANK_LABEL, B1, B2,
				SECOND_BLANK_LABEL, SOME_FUNKY_LABEL, PRETTY_INFORMATIVE_LABEL);
		testQuery("idx", "second", A, B,
				SECOND_BLANK_LABEL, B1, B2);
		testQuery("idx", "alabala", A, B, ALABALA, B1);
		testQuery("idx", "informative", PRETTY_INFORMATIVE_LABEL, C,
				B, A, B1, B2);
	}

	@Test
	public void testMoleculeSize3NotOnlyLiteralsNoBNodes() throws Exception {
		setParam(Lucene.MOLECULE_SIZE, "3");
		setParam(Lucene.INCLUDE, "uris,literals");
		setParam(Lucene.INDEX, "uris,literals");
		createIndex("idx");
		testQuery("idx", "blan*", FIRST_BLANK_LABEL, SECOND_BLANK_LABEL,
				A, B);
		testQuery("idx", "c", A, B, C);
		testQuery("idx", "c AND b", A, B);
		testQuery("idx", "some", A, SOME_FUNKY_LABEL);
		testQuery("idx", "first", A, FIRST_BLANK_LABEL);
		testQuery("idx", "label", A, B, C,
				LABEL_PREDICATE, FIRST_BLANK_LABEL, SECOND_BLANK_LABEL,
				SOME_FUNKY_LABEL, PRETTY_INFORMATIVE_LABEL);
		testQuery("idx", "second", A, B, SECOND_BLANK_LABEL);
		testQuery("idx", "alabala", A, B, ALABALA);
		testQuery("idx", "informative", PRETTY_INFORMATIVE_LABEL, C,
				B, A);
	}

	@Test
	public void testLanguages() throws Exception {
		// test with filter by language
		setParam(Lucene.MOLECULE_SIZE, "0");
		setParam(Lucene.LANGUAGES, "bg,de");
		createIndex("idx");
		testQuery("idx", "danke", DANKE_DE);
		testQuery("idx", "test", TEST_BG);

		setParam(Lucene.MOLECULE_SIZE, "0");
		setParam(Lucene.LANGUAGES, "bg");
		createIndex("idx");
		testQuery("idx", "danke");
		testQuery("idx", "test", TEST_BG);

		// test without filter by language
		setParam(Lucene.MOLECULE_SIZE, "0");
		setParam(Lucene.LANGUAGES, "");
		createIndex("idx");
		testQuery("idx", "danke", DANKE_DE, DANKE);
		testQuery("idx", "test", TEST_BG, TEST);

		// test with filter by language allowing only no-language literals
		setParam(Lucene.MOLECULE_SIZE, "0");
		setParam(Lucene.LANGUAGES, "none");
		createIndex("idx");
		testQuery("idx", "danke", DANKE);

		// test with filter by language allowing no-language and language literals
		setParam(Lucene.MOLECULE_SIZE, "0");
		setParam(Lucene.LANGUAGES, "none, de");
		createIndex("idx");
		testQuery("idx", "danke", DANKE, DANKE_DE);

		// test with filter by language allowing only language literals
		setParam(Lucene.MOLECULE_SIZE, "0");
		setParam(Lucene.LANGUAGES, "de");
		createIndex("idx");
		testQuery("idx", "danke", DANKE_DE);

		setParam(Lucene.LANGUAGES, "");
	}

	@Test
	public void testExclude() throws Exception {
		setParam(Lucene.MOLECULE_SIZE, "0");
		setParam(Lucene.INDEX, "literals");
		setParam(Lucene.INCLUDE, "literals");
		createIndex("idx");
		testQuery("idx", "blan*", FIRST_BLANK_LABEL, SECOND_BLANK_LABEL);

		setParam(Lucene.EXCLUDE, "sec.*");
		createIndex("idx");
		testQuery("idx", "blan*", FIRST_BLANK_LABEL);

		setParam(Lucene.EXCLUDE, "sec.*|fir.*");
		createIndex("idx");
		testQuery("idx", "blan*");
	}

	@Test
	public void testDefaultIndex() throws Exception {
		setParam(Lucene.MOLECULE_SIZE, "0");
		setParam(Lucene.INDEX, "literals");
		setParam(Lucene.INCLUDE, "literals");
		createIndex("");
		testQuery("", "blan*", FIRST_BLANK_LABEL, SECOND_BLANK_LABEL);
	}

	@Test
	public void testQueryResultsOrder() throws Exception {
		setParam(Lucene.MOLECULE_SIZE, "3");
		setParam(Lucene.INDEX, "uris,literals,bnodes");
		setParam(Lucene.INCLUDE, "uris,literals");
		createIndex("idx");

		Value[] expected = new Value[] { LABEL_PREDICATE, B2, A,
				SOME_FUNKY_LABEL, C, PRETTY_INFORMATIVE_LABEL,
				FIRST_BLANK_LABEL, SECOND_BLANK_LABEL, B, B1, };

		RepositoryConnection connection = null;
		TupleQueryResult result = null;
		try {
			connection = getRepository().getConnection();

			String sparql =
					"SELECT ?s { ?s <" + Lucene.NAMESPACE + "idx" + "> \"label\" }";

			TupleQuery query = connection.prepareTupleQuery(QueryLanguage.SPARQL, sparql);

			result = query.evaluate();

			int index = 0;
			while (result.hasNext()) {
				Value val = result.next().getBinding("s").getValue();
				Value expectedVal = expected[index];
				if(expectedVal instanceof BNode ) {
					assertTrue("Unexpected results order (BNode)", val instanceof BNode);
				}
				else {
					assertEquals("Unexpected results order", expected[index], val);
				}
				index++;
			}
			assertEquals(expected.length, index);
		}
		finally {
			Utils.close(result);
			Utils.close(connection);
		}
	}

	@Test
	public void testScored() throws Exception {
		setParam(Lucene.MOLECULE_SIZE, "3");
		setParam(Lucene.INDEX, "uris,literals,bnodes");
		setParam(Lucene.INCLUDE, "uris,literals");
		createIndex("idx");

		RepositoryConnection connection = null;
		try {
			connection = getRepository().getConnection();

			String sparql =
					"SELECT * {"
							+ "?node <http://www.ontotext.com/owlim/lucene#score> ?score ."
							+ "?node <http://www.ontotext.com/owlim/lucene#index> \"label\" ." + "}";
			TupleQuery query = connection.prepareTupleQuery(QueryLanguage.SPARQL, sparql);

			TupleQueryResult res = query.evaluate();

			float prevScore = Float.MAX_VALUE;
			while (res.hasNext()) {
				BindingSet bs = res.next();
				assertTrue(bs.hasBinding("score"));
				String score = bs.getBinding("score").getValue().stringValue();
				float s = Float.parseFloat(score); // this will throw NFX if invalid
				assertTrue("Invalid ordering ", s <= prevScore);
				prevScore = s;
			}
			Utils.close(res);
		}
		finally {
			Utils.close(connection);
		}
	}

	@Test
	public void testIncludeExclude() throws Exception {
		setParam(Lucene.MOLECULE_SIZE, "3");
		setParam(Lucene.INCLUDE, "uris,literals");
		setParam(Lucene.INDEX, "uris,literals");
		setParam(Lucene.INCLUDE_PREDICATES, LINK_PREDICATE.stringValue());
		createIndex("idx");

		testQuery("idx", "label", LABEL_PREDICATE, FIRST_BLANK_LABEL,
				SECOND_BLANK_LABEL, SOME_FUNKY_LABEL, PRETTY_INFORMATIVE_LABEL);

		setParam(Lucene.INCLUDE_PREDICATES, LABEL_PREDICATE.stringValue());
		setParam(Lucene.INCLUDE_ENTITIES, A.stringValue());
		setParam(Lucene.INDEX, "uris");
		createIndex("idx");

		testQuery("idx", "label", LABEL_PREDICATE, A, C);

		setParam(Lucene.INCLUDE_PREDICATES, LINK_PREDICATE.stringValue() + ", " + LABEL_PREDICATE.stringValue() + ", ");
		setParam(Lucene.INCLUDE_ENTITIES, B.stringValue() + ", " + C.stringValue());
		setParam(Lucene.INDEX, "uris");
		createIndex("idx");

		testQuery("idx", "pretty AND informative", A, B, C);

		setParam(Lucene.INCLUDE_PREDICATES, LINK_PREDICATE.stringValue() + ", " + LABEL_PREDICATE.stringValue());
		setParam(Lucene.INCLUDE_ENTITIES, B.stringValue() + ", <" + C.stringValue() + ">");
		setParam(Lucene.EXCLUDE_ENTITIES, "<" + C.stringValue() + ">");
		setParam(Lucene.INDEX, "uris");
		createIndex("idx");

		testQuery("idx", "pretty AND informative");

		setParam(Lucene.INCLUDE_PREDICATES, LINK_PREDICATE.stringValue() + ", " + LABEL_PREDICATE.stringValue());
		setParam(Lucene.INCLUDE_ENTITIES, B.stringValue() + ", <" + C.stringValue() + ">");
		setParam(Lucene.EXCLUDE_ENTITIES, B.stringValue());
		setParam(Lucene.INDEX, "uris");
		createIndex("idx");

		testQuery("idx", "pretty AND informative", C);

		setParam(Lucene.INCLUDE_PREDICATES, LINK_PREDICATE.stringValue() + ", " + LABEL_PREDICATE.stringValue());
		setParam(Lucene.EXCLUDE_PREDICATES, LABEL_PREDICATE.stringValue());
		setParam(Lucene.INDEX, "uris");
		createIndex("idx");

		testQuery("idx", "pretty AND informative");

		setParam(Lucene.INCLUDE_PREDICATES, LINK_PREDICATE.stringValue() + ", " + LABEL_PREDICATE.stringValue());
		setParam(Lucene.EXCLUDE_PREDICATES, LINK_PREDICATE.stringValue());
		setParam(Lucene.INDEX, "uris");
		createIndex("idx");

		testQuery("idx", "pretty AND informative", C);

		// =================
		setParam(Lucene.INCLUDE_PREDICATES, "");
		setParam(Lucene.EXCLUDE_PREDICATES, "");
		setParam(Lucene.INDEX, "uris");
		setParam(Lucene.INCLUDE, "literals");
		createIndex("idx");
		testQuery("idx", "pretty AND informative", C);

		setParam(Lucene.EXCLUDE_PREDICATES, LABEL_PREDICATE.stringValue());
		createIndex("idx");
		testQuery("idx", "pretty AND informative");

		setParam(Lucene.EXCLUDE_PREDICATES, "");
		setParam(Lucene.INCLUDE_PREDICATES, LABEL_PREDICATE.stringValue());
		createIndex("idx");
		testQuery("idx", "pretty AND informative", C);
	}

	@Test
	public void testStackoverflowErrorBBC84() throws Exception {
		setParam(Lucene.MOLECULE_SIZE, "1");
		setParam(Lucene.INCLUDE, "uris,literals");
		setParam(Lucene.INDEX, "uris,literals");
		createIndex("idx");

		RepositoryConnection connection = null;
		try {
			connection = getRepository().getConnection();
			connection.prepareUpdate(QueryLanguage.SPARQL, "insert data {\n" +
			"<urn:1> <urn:p> _:f .\n"+
			"_:f <urn:q> _:f .\n"+
					"}").execute();
		} finally {
			Utils.close(connection);
		}
		addToIndex("idx", vf.createIRI("urn:1"));
	}
}
