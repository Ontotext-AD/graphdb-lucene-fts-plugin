package com.ontotext.test.functional.owlim_se;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.ontotext.graphdb.Config;
import com.ontotext.test.TemporaryLocalFolder;
import org.junit.*;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.config.RepositoryConfig;
import org.eclipse.rdf4j.rio.ParserConfig;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.helpers.BasicParserSettings;

import com.ontotext.test.functional.base.SingleRepositoryFunctionalTest;
import com.ontotext.test.utils.OwlimSeRepositoryDescription;
import com.ontotext.test.utils.Utils;

public class TestOwlimSePluginLuceneConcurrentSearch extends SingleRepositoryFunctionalTest {
	private static final String TEST_DATA = "/TestBSBMData.nt";
	private static final String BASE_URI = "http://ex.org/test";

	private static final String QUERY_BUILD_INDEX = "PREFIX luc: <http://www.ontotext.com/owlim/lucene#> ASK { luc:literals luc:createIndex \"true\" . }";
	private static final String QUERY_REBUILD_INDEX = "PREFIX luc: <http://www.ontotext.com/owlim/lucene#>\n INSERT DATA { luc:literals luc:updateIndex _:b1 . }";
	private static final String QUERY_INSERT_DATA = "INSERT DATA {<urn:%s> <http://www.w3.org/2000/01/rdf-schema#label> \"Test Roger %s\"}";
	private static final String QUERY_SEARCH = "PREFIX luc: <http://www.ontotext.com/owlim/lucene#> ASK { ?label luc:literals \"Rog*\" . }";


	private static final int NUM_THREADS = 4;
	private static final int NUM_SEARCHES = 1000;

	@ClassRule
	public static TemporaryLocalFolder tmpFolder = new TemporaryLocalFolder();

	@Override
	protected RepositoryConfig createRepositoryConfiguration() {
		OwlimSeRepositoryDescription repositoryDescription = new OwlimSeRepositoryDescription();
		repositoryDescription.getOwlimSailConfig().setRuleset("empty");
		return repositoryDescription.getRepositoryConfig();
	}

	@BeforeClass
	public static void setWorkDir() {
		System.setProperty("graphdb.home.work", String.valueOf(tmpFolder.getRoot()));
		Config.reset();
	}

	@AfterClass
	public static void resetWorkDir() {
		System.clearProperty("graphdb.home.work");
		Config.reset();
	}

	@Before
	public void createRepositories() throws Exception {
		RepositoryConnection connection = getRepository().getConnection();
		ParserConfig config = new ParserConfig();
		config.set(BasicParserSettings.FAIL_ON_UNKNOWN_DATATYPES, false);
		config.set(BasicParserSettings.VERIFY_DATATYPE_VALUES, false);
		config.set(BasicParserSettings.NORMALIZE_DATATYPE_VALUES, false);

		connection.setParserConfig(config);

        java.io.InputStream is = this.getClass().getResourceAsStream(TEST_DATA);
        connection.add(is, BASE_URI, RDFFormat.TURTLE);
        is.close();

		connection.commit();
		assertEquals(true, Utils.executeBooleanQuery(connection, QUERY_BUILD_INDEX, QueryLanguage.SPARQL, true));
		Utils.close(connection);
	}

	private volatile boolean someoneFailedConcurrentSearch = false;

	@Test
	public void concurrentSearch() throws InterruptedException, RepositoryException {
		ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
		for (int i=0; i<NUM_THREADS; ++i) {
			executor.execute(new Searcher());
		}
		executor.shutdown();
		executor.awaitTermination(1, TimeUnit.MINUTES);

		assertEquals("FINAL", false, someoneFailedConcurrentSearch);
	}

	@Test
	public void concurrentReadWriteSearch() throws InterruptedException, RepositoryException {
		ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
		for (int i=0; i<NUM_THREADS; ++i) {

			if (i == 0) {
				executor.execute(new Inserter());
			}
			else if (i == 1) {
				executor.execute(new Reindexer());
			}
			else {
				executor.execute(new Searcher());
			}
		}
		executor.shutdown();
		executor.awaitTermination(1, TimeUnit.MINUTES);

		assertEquals("FINAL", false, someoneFailedConcurrentSearch);
	}

	private class Searcher implements Runnable {

		private RepositoryConnection connection;

		public Searcher() throws RepositoryException {
			this.connection = getRepository().getConnection();
		}

		@Override
		public void run() {
			try {
				for (int i=0; !someoneFailedConcurrentSearch && i < NUM_SEARCHES; ++i) {
					someoneFailedConcurrentSearch = !Utils.executeBooleanQuery(connection, QUERY_SEARCH, QueryLanguage.SPARQL, false);
					assertEquals("ASK", false, someoneFailedConcurrentSearch);
				}
			} catch (Exception e) {
				e.printStackTrace(System.err);
				someoneFailedConcurrentSearch = true;
			} finally {
				Utils.close(connection);
			}
		}

	}

	private class Inserter implements Runnable {

		private RepositoryConnection connection;

		public Inserter() throws RepositoryException {
			this.connection = getRepository().getConnection();
		}

		@Override
		public void run() {

			RepositoryConnection conn = null;

			try {
				conn = getRepository().getConnection();

				for (int i=0; !someoneFailedConcurrentSearch && i < NUM_SEARCHES; ++i) {
					String update = String.format(QUERY_INSERT_DATA, i, i);
					Utils.update(conn, update, false, "http://www.ontotext.com/");
				}
			} catch (Exception e) {
				e.printStackTrace(System.err);
				someoneFailedConcurrentSearch = true;
			} finally {
				if (conn != null) {
					Utils.close(conn);
				}
			}
		}
	}


	private class Reindexer implements Runnable {

		private RepositoryConnection connection;

		public Reindexer() throws RepositoryException {
			this.connection = getRepository().getConnection();
		}

		@Override
		public void run() {

			RepositoryConnection conn = null;

			try {
				conn = getRepository().getConnection();

				for (int i=0; i < NUM_SEARCHES; ++i) {
					String update = String.format(QUERY_INSERT_DATA, i, i);
					Utils.update(conn, update, false, "http://www.ontotext.com/");
				}
			} catch (Exception e) {
				e.printStackTrace(System.err);
				someoneFailedConcurrentSearch = true;
			} finally {
				if (conn != null) {
					Utils.close(conn);
				}
			}
		}

	}
}
