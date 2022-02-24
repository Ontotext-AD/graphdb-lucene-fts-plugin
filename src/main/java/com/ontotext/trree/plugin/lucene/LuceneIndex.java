package com.ontotext.trree.plugin.lucene;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Properties;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.SimpleFSDirectory;
import org.apache.lucene.util.Version;

public class LuceneIndex {
	private static final String DEFAULT_MISSING_VERSION = "30";
	private static final String DEFAULT_VERSION = "35";
	private static final String CONFIG_FILE = "index.properties";
	private static final String PARAM_ANALYZER = "analyzer";
	private static final String PARAM_VERSION = "version";
	private static final String PARAM_FINGERPRINT = "fingerprint";

	private String version;
	private IndexSearcher searcher;
	private Analyzer analyzer;
	private QueryParser parser;
	private File dataDir;
	private String name;
	private String analyzerFactory;
	private long fingerprint;
	private int[] docToURIMapping = null;

	static class SynchQueryParser extends QueryParser {

		public SynchQueryParser(Version resolveVersion, String fieldText, Analyzer analyzer) {
			super(resolveVersion, fieldText, analyzer);
		}
		public synchronized Query parse(String query) throws ParseException {
			return super.parse(query);
		}
		
	}

	public LuceneIndex(String name, File dataDir) throws IOException {
		this.name = name;
		this.dataDir = dataDir;
		initialize();
	}

	public void setDataDir(File newDataDir) throws IOException {
		dataDir = newDataDir;
		refresh();
	}

	public void initialize() throws IOException {
		// read analyzer name from configuration file
		boolean propertiesLoaded = false;
		Properties props = new Properties();
		try {
			FileInputStream in = new FileInputStream(getConfigFile());
			props.load(in);
			in.close();
			propertiesLoaded = true;
		} catch (IOException ex) {
			// props could not be loaded
		}
		// read the version
		version = props.getProperty(PARAM_VERSION, propertiesLoaded ? DEFAULT_MISSING_VERSION
				: DEFAULT_VERSION);
		// configure analyzer
		configureAnalyzerFactory(props.getProperty(PARAM_ANALYZER));
		// read fingerprint
		String stringFingerprint = props.getProperty(PARAM_FINGERPRINT);
		if (stringFingerprint != null) {
			try {
				setFingerprint(Long.parseLong(stringFingerprint));
			} catch (NumberFormatException nfx) {
				// invalid fingerprint. just ignore it and use 0 instead
			}
		}
		try {
			refresh();
		} catch (IndexNotFoundException ex) {
			// allow for lazy initialization
		} catch (IOException ex) {
			System.err.println("Error while initializing Lucene Index " + getName());
			throw ex;
		}

	}

	public void refresh() throws IndexNotFoundException, IOException  {
		// attempt to initialize the index found in this directory
		if (searcher != null) {
			searcher.getIndexReader().close();
			searcher.close();
		}
		docToURIMapping = null;
		searcher = new IndexSearcher(IndexReader.open(new SimpleFSDirectory(dataDir), true));
	}

	public void writeProperties() throws IOException {
		// read analyzer name from configuration file
		Properties props = new Properties();
		if (analyzerFactory != null) {
			props.setProperty(PARAM_ANALYZER, analyzerFactory);
		}
		props.setProperty(PARAM_FINGERPRINT, "" + getFingerprint());
		props.setProperty(PARAM_VERSION, version);
		FileOutputStream out = new FileOutputStream(getConfigFile());
		props.store(out, "Lucene index: " + getName());
		out.close();
	}
	
	public void shutDown() throws IOException {
		writeProperties();
		if (searcher != null) {
			searcher.getIndexReader().close();
			searcher.close();
		}
	}
	
	public IndexSearcher getSearcher() {
		return searcher;
	}

	public void configureAnalyzerFactory(String factoryClass) {
		AnalyzerFactory factory = null;
		if (factoryClass != null) {
			analyzerFactory = factoryClass;
			factory = LucenePlugin.instantiateClass(factoryClass);
			// create analyzer
			analyzer = factory.createAnalyzer();
		}
		if (analyzer == null) {
			analyzer = new StandardAnalyzer(resolveVersion(version));
		}

		boolean lowerCaseExpandedTerms = !(factory != null && factory.isCaseSensitive());
		// create a query parser
		parser = new SynchQueryParser(resolveVersion(version), LucenePlugin.FIELD_TEXT, analyzer);
		parser.setAllowLeadingWildcard(true);
		parser.setLowercaseExpandedTerms(lowerCaseExpandedTerms);
	}

	public QueryParser getParser() {
		return parser;
	}

	private File getConfigFile() {
		return new File(dataDir.getAbsolutePath() + File.separator + CONFIG_FILE);
	}

	public IndexWriter getWriter() throws IOException {
		IndexWriterConfig config = new IndexWriterConfig(resolveVersion(version), analyzer);

		IndexWriter ret =  new IndexWriter(new SimpleFSDirectory(dataDir), config);
		return ret;
	}

	public boolean isOperational() {
		return searcher != null;
	}

	public String getName() {
		return name;
	}

	public long getFingerprint() {
		return fingerprint;
	}

	public void setFingerprint(long value) {
		fingerprint = value;
	}

	public int[] getDocToURIMapping() {
		if (docToURIMapping == null) {
			if (searcher != null) {
				docToURIMapping = new int[searcher.getIndexReader().maxDoc()];
			}
		}
		return docToURIMapping;
	}

	public static boolean isValidName(String indexName) {
		return indexName != null && indexName.matches("[a-zA-Z0-9_]*");
	}

	private static Version resolveVersion(String version) {
		return Version.valueOf("LUCENE_" + version);
	}
}
