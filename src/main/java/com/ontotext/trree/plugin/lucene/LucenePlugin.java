package com.ontotext.trree.plugin.lucene;

import com.ontotext.trree.ReleaseInfo;
import com.ontotext.trree.sdk.*;
import com.ontotext.trree.sdk.Entities.Scope;
import com.ontotext.trree.sdk.impl.RequestContextImpl;
import com.ontotext.trree.util.FileUtils;
import gnu.trove.TLongHashSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.LockObtainFailedException;
import org.eclipse.rdf4j.model.IRI;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class LucenePlugin extends PluginBase implements Preprocessor, PluginDependency, PatternInterpreter,
		UpdateInterpreter
{
	static final String FIELD_ID = "id";
	static final String FIELD_TEXT = "mol";
	static final String FIELD_SYSDATA = "sysdata";
	static final String FIELD_VALUE = "value";
	static final String FIELD_LASTINDEXED = "lastindexed";
	static final String FIELD_MOLECULE = "molecule";

	static final String ATTRIBUTE_ITERATORS = "lucene.iterators";
	static final String TEMP_SUFFIX = ".temp";
	static final String OLD_SUFFIX = ".old";

	private static final String DOCUMENTATION_URL_TEMPLATE = "https://graphdb.ontotext.com/documentation/%s/%s/full-text-search.html";

	private final String documentationUrl;

	private Map<String, LuceneIndex> indices = new HashMap<String, LuceneIndex>();
	private PluginLocator pluginLocator;

	private IndexFilter indexFilter = new IndexFilter();
	private IncludeFilter includeFilter = new IncludeFilter();
	private Pattern excludePattern = null;

	private int moleculeSize = 0;
	private String[] languages = null;
	private Scorer scorer = null;
	private String analyzer = null;
	private Set<IRI> includePredicates = null;
	private Set<IRI> excludePredicates = null;
	private Set<IRI> includeEntities = null;
	private Set<IRI> excludeEntities = null;

	private static final String TRUE = "true";
	private static final String FALSE = "false";
	private static final String NO = "no";
	private static final String YES = "yes";
	private static final String SQUARED = "squared";

	private long idSetParam;
	private long idAnalyzer;
	private long idScorer;
	private long idLanguages;
	private long idIndex;
	private long idInclude;
	private long idExclude;
	private long idIncludePredicates;
	private long idIncludeEntities;
	private long idExcludePredicates;
	private long idExcludeEntities;
	private long idMoleculeSize;
	private long idUseRdfRank;
	private long idCreateIndex;
	private long idUpdateIndex;
	private long idAddToIndex;
	private long idScore;

	static <T> T instantiateClass(String className) {
		try {
			return (T) LucenePlugin.class.getClassLoader().loadClass(className).newInstance();
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to instantiate class " + className, e);
		}
	}

	public LucenePlugin() {
		ReleaseInfo releaseInfo = ReleaseInfo.get();
		String version = releaseInfo.getVersion();
/*
		String edition = releaseInfo.getEdition();
		if ("GRAPHDB_SE".equals(edition)) {
			edition = "standard";
		} else if ("GRAPHDB_ENTERPRISE".equals(edition)) {
			edition = "enterprise";
		} else {
			edition = "free";
		}*/
		//TODO SET THE CORRECT LINK TO GDB 10 DOCUMENTATION
		documentationUrl = "https://graphdb.ontotext.com/documentation/";
		//String.format(DOCUMENTATION_URL_TEMPLATE, version, edition);
	}

	@Override
	public String getName() {
		return "lucene";
	}

	@Override
	public void initialize(InitReason initReason, PluginConnection pluginConnection) {
		Entities entities = pluginConnection.getEntities();
		// initialize recognized predicates
		idSetParam = entities.put(Lucene.SET_PARAM, Scope.SYSTEM);
		idAnalyzer = entities.put(Lucene.ANALYZER, Scope.SYSTEM);
		idScorer = entities.put(Lucene.SCORER, Scope.SYSTEM);
		idLanguages = entities.put(Lucene.LANGUAGES, Scope.SYSTEM);
		idIndex = entities.put(Lucene.INDEX, Scope.SYSTEM);
		idInclude = entities.put(Lucene.INCLUDE, Scope.SYSTEM);
		idExclude = entities.put(Lucene.EXCLUDE, Scope.SYSTEM);
		idIncludePredicates = entities.put(Lucene.INCLUDE_PREDICATES, Scope.SYSTEM);
		idIncludeEntities = entities.put(Lucene.INCLUDE_ENTITIES, Scope.SYSTEM);
		idExcludePredicates = entities.put(Lucene.EXCLUDE_PREDICATES, Scope.SYSTEM);
		idExcludeEntities = entities.put(Lucene.EXCLUDE_ENTITIES, Scope.SYSTEM);
		idMoleculeSize = entities.put(Lucene.MOLECULE_SIZE, Scope.SYSTEM);
		idUseRdfRank = entities.put(Lucene.USE_RDF_RANK, Scope.SYSTEM);
		idCreateIndex = entities.put(Lucene.CREATE_INDEX, Scope.SYSTEM);
		idUpdateIndex = entities.put(Lucene.UPDATE_INDEX, Scope.SYSTEM);
		idAddToIndex = entities.put(Lucene.ADD_TO_INDEX, Scope.SYSTEM);
		idScore = entities.put(Lucene.SCORE, Scope.SYSTEM);

		// locate the existing lucene indices
		File dataDir = getDataDir();
		File[] dirs = dataDir.listFiles();
		if (dirs != null) {
			// Prints warning on init if at least one index exists
			printDeprecationWarning();

			dirs = Arrays.copyOf(dirs, dirs.length + 1);
			dirs[dirs.length - 1] = dataDir;
			for (File dir : dirs) {
				if (!dir.isDirectory()) {
					continue;
				}

				String indexName = dir == dataDir ? "" : dir.getName();

				// skip temporary index directories
				// important to note that the temporary suffixes contain characters (.) that
				// are not allowed in index names, i.e. cannot be created by the user
				if (!LuceneIndex.isValidName(indexName)) {
					continue;
				}

				try {
					LuceneIndex index = new LuceneIndex(indexName, dir);
					// register this index under its name if it's operational
					registerIndex(indexName, index);
				} catch (Exception ex) {
					getLogger().error("Failed initializing index in directory " + dir.getAbsolutePath(), ex);
					continue;
				}
			}
		} else {
			getLogger().info("No Lucene indices were found");
		}
		setDefaults();
	}
	
	private void setDefaults() {
		indexFilter.initialize("literals");
		includeFilter.initialize("literals");
	}

	private ScoreDoc[] search(String indexName, String query) throws ParseException, IOException {
		ScoreDoc[] result = null;
		LuceneIndex index = indices.get(indexName);
		if (index != null) {
			Query parsedQuery = index.getParser().parse(query);
			IndexSearcher searcher = index.getSearcher();
			LuceneResultsCollector collector = new LuceneResultsCollector(index.getDocToURIMapping());
			searcher.search(parsedQuery, collector);
			result = collector.getResults();
		}
		return result;
	}

	@Override
	public StatementIterator interpret(long subject, long predicate, long object, long context,
									   PluginConnection pluginConnection, RequestContext requestContext) {

		Boolean booleanResult = null;

		if (predicate == idSetParam) {
			String value = Utils.getString(pluginConnection.getEntities(), object);
			booleanResult = true;
			try {
				if (Utils.match(subject, idIndex)) {
					indexFilter.initialize(value);
				} else if (Utils.match(subject, idInclude)) {
					includeFilter.initialize(value);
				} else if (Utils.match(subject, idExclude)) {
					try {
						excludePattern = Pattern.compile(value);
					} catch (PatternSyntaxException psx) {
						getLogger()
								.error("Invalid exclusion pattern: " + value
										+ ". No exclusion pattern will be used", psx);
						booleanResult = false;
					}
				} else if (Utils.match(subject, idIncludePredicates)) {
					includePredicates = parseURIList(value);
				} else if (Utils.match(subject, idExcludePredicates)) {
					excludePredicates = parseURIList(value);
				} else if (Utils.match(subject, idIncludeEntities)) {
					includeEntities = parseURIList(value);
				} else if (Utils.match(subject, idExcludeEntities)) {
					excludeEntities = parseURIList(value);
				} else if (Utils.match(subject, idMoleculeSize)) {
					moleculeSize = Integer.parseInt(value);
				} else if (Utils.match(subject, idLanguages)) {
					value = value.trim();
					languages = (value.length() > 0) ? value.split("\\s*,\\s*") : null;
				} else if (Utils.match(subject, idAnalyzer)) {
					analyzer = value;
				} else if (Utils.match(subject, idScorer)) {
					ScorerFactory factory = LucenePlugin.instantiateClass(value);
					scorer = factory.createScorer();
					if (scorer == null) {
						getLogger().error("Failed instantiating scorer object for class " + value);
						booleanResult = false;
					}
				} else if (Utils.match(subject, idUseRdfRank)) {
					if (value.equalsIgnoreCase(NO) || value.equalsIgnoreCase(FALSE)) {
						scorer = null;
					} else {
						RDFRankProvider rdfRankProvider = (RDFRankProvider) pluginLocator.locate("rdfrank");
						if (rdfRankProvider == null) {
							getLogger().error("RDFRank plugin is not available. No scoring will be used");
							booleanResult = false;
						} else {
							final Scorer rdfRankScorer = new RDFRankScorer(rdfRankProvider);
							if (value.equalsIgnoreCase(YES) || value.equalsIgnoreCase(TRUE)) {
								scorer = rdfRankScorer;
							} else if (value.equalsIgnoreCase(SQUARED)) {
								// square the RDF rank score
								scorer = new Scorer() {
									@Override
									public String getName() {
										return rdfRankScorer + "squared";
									}

									@Override
									public double score(long id) {
										double value = rdfRankScorer.score(id);
										return value == RDFRankProvider.NULL_RANK ? RDFRankProvider.NULL_RANK
												: value * value;
									}
								};
							} else {
								getLogger().error(
										"Bad value '" + value + "' passed to " + Lucene.USE_RDF_RANK);
								booleanResult = false;
							}
						}
					}
				} else {
					// this is unknown parameter
					getLogger().error("Unknown parameter " + Utils.getString(pluginConnection.getEntities(), subject));
					booleanResult = false;
				}
			} catch (Exception ex) {
				// something went wrong with the setting of this parameter
				getLogger().error("Failed setting parameter " + Utils.getString(pluginConnection.getEntities(), subject));
				booleanResult = false;
			}
		} else if (Utils.match(predicate, idCreateIndex)) {
			// Prints warning on creating a new index
			printDeprecationWarning();

			String suffix = Utils.matchPrefix(Utils.getString(pluginConnection.getEntities(), subject), Lucene.NAMESPACE);
			booleanResult = suffix == null ? false : createIndex(suffix, pluginConnection);
		} else if (Utils.match(predicate, idUpdateIndex)) {
			// Prints warning on updating an index
			printDeprecationWarning();

			String suffix = Utils.matchPrefix(Utils.getString(pluginConnection.getEntities(), subject), Lucene.NAMESPACE);
			booleanResult = suffix == null ? false : updateIndex(suffix, pluginConnection);
		} else if (Utils.match(predicate, idAddToIndex)) {
			String indexName = Utils.matchPrefix(Utils.getString(pluginConnection.getEntities(), subject), Lucene.NAMESPACE);
			if (indexName != null) {
				booleanResult = addToIndex(indexName, object, pluginConnection);
			} else {
				booleanResult = false;
			}
		} else if (Utils.match(predicate, idScore)) {
			return new ScoreIterator(subject, predicate, object, this, pluginConnection.getEntities(), requestContext);
		}

		if (booleanResult != null) {
			return booleanResult ? StatementIterator.TRUE() : StatementIterator.FALSE();
		}

		// is this is a plain lucene query
		String suffix = Utils.matchPrefix(Utils.getString(pluginConnection.getEntities(), predicate), Lucene.NAMESPACE,
				Lucene.OLD_QUERY);
		if (suffix == null) {
			return null;
		}

		if (object == 0) {
			return StatementIterator.EMPTY;
		}

		String queryString = pluginConnection.getEntities().get(object).stringValue();
		ScoreDoc[] results = null;
		try {
			results = search(suffix, queryString);
			if (results == null) {
				results = new ScoreDoc[0];
			}
		} catch (Exception e) {
			LoggerFactory.getLogger(getClass()).error(
					"Failed executing lucene query '" + queryString + "' on index '" + suffix + "'", e);
			results = new ScoreDoc[0];
		}
		int offset = 0, limit = results.length;
		if (subject != 0) {
			// check if the subject is in the results
			limit = 0;
			for (ScoreDoc hit : results) {
				if (hit.doc == subject) {
					limit = 1;
					break;
				}
				offset++;
			}
		}
		LuceneIterator iter = new LuceneIterator(results, offset, limit);
		iter.subject = subject;
		iter.predicate = predicate;
		iter.object = object;
		iter.context = context;
		addIterator(requestContext, iter);
		return iter;
	}

	/**
	 * Invoked to builds the molecules graph and then iterates to index-it-all. Note: next method addToIndex()
	 * is quite similar, but it builds the molecule and indexes a single entity. Those 2 methods share a
	 * similar logic (especially in their prologues) so if you need to modify one of them, you'd better check
	 * if the next one needs a similar modification.
	 */
	boolean createIndex(String indexName, PluginConnection pluginConnection) {
		if (pluginConnection.getProperties().isReadOnly()) {
			getLogger().error("Can't create Lucene index when in read-only mode");
			return false;
		}

		// check if index name is a valid one
		if (!LuceneIndex.isValidName(indexName)) {
			getLogger().error("Invalid index name: " + indexName);
			return false;
		}

		// create the index into a temporary directory
		File tempIndexDir = new File(getDataDir() + File.separator + indexName + TEMP_SUFFIX);

		// drop previously created temporary directory and create an empty one instead
		FileUtils.deleteFilesFromDirectory(tempIndexDir);
		tempIndexDir.mkdirs();

		LuceneIndex index = null;
		long fingerprint = 0;
		try {
			index = new LuceneIndex(indexName, tempIndexDir);
		} catch (Exception ex) {
			getLogger().error("Failed to initialize index", ex);
			return false;
		}

		try {
			index.configureAnalyzerFactory(analyzer);
		} catch (Exception ex) {
			getLogger().error("Failed to initialize analyzer " + analyzer, ex);
			return false;
		}

		Entities entities = pluginConnection.getEntities();
		Statements statements = pluginConnection.getStatements();

		String indexDesc = getIndexDesc(indexName);
		getLogger().info("Start creating " + indexDesc);

		MoleculeModel moleculesModel = new MoleculeModel();
		moleculesModel.setIncludeFilter(includeFilter);
		moleculesModel.setIndexFilter(indexFilter);
		moleculesModel.setExcludePattern(excludePattern);
		moleculesModel.setDegree(moleculeSize);
		moleculesModel.setStatements(statements);
		moleculesModel.setEntities(entities);
		moleculesModel.setLanguages(languages);
		moleculesModel.setDataDir(tempIndexDir);
		moleculesModel.setIncludePredicates(includePredicates);
		moleculesModel.setExcludePredicates(excludePredicates);
		moleculesModel.setIncludeEntities(includeEntities);
		moleculesModel.setExcludeEntities(excludeEntities);

		getLogger().info("Initializing molecules...");

		try {
			moleculesModel.initialize();
		} catch (IOException iox) {
			getLogger().error("Failed to compute molecules", iox);
			return false;
		}

		getLogger().info("Finished initializing molecules.");

		long numberOfEntities = entities.size();

		Field fieldId = new Field(FIELD_ID, "", Store.YES, Index.NO);
		Field fieldText = new Field(FIELD_TEXT, "", Store.NO, Index.ANALYZED);

		Document doc = new Document();

		doc.add(fieldId);
		doc.add(fieldText);

		TLongHashSet moleculeSet = new TLongHashSet();

		getLogger().info("Start indexing " + numberOfEntities + " entities and their molecules...");

		IndexWriter writer = null;
		try {
			try {
				writer = index.getWriter();
			} catch (CorruptIndexException e) {
				getLogger().error("The " + indexDesc + " is corrupted", e);
				return false;
			} catch (LockObtainFailedException e) {
				getLogger().error("The " + indexDesc + " is locked", e);
				return false;
			} catch (IOException e) {
				getLogger().error("The " + indexDesc + " is not modifiable", e);
				return false;
			}

			assert (writer != null);

			for (long id = 1; id <= numberOfEntities; id++) {
				if (id % 100000 == 0) {
					getLogger().info("Indexed " + id + " entities");
				}

				Entities.Type type = entities.getType(id);
				switch (type) {
				case URI:
					if (!indexFilter.indexURI()) {
						continue;
					}
					break;
				case BNODE:
					if (!indexFilter.indexBNode()) {
						continue;
					}
					break;
				case LITERAL:
					if (!indexFilter.indexLiteral()) {
						continue;
					}
					break;
				}

				// only index base equivalence classes
				if (entities.getClass(id) != id) {
					continue;
				}

				moleculeSet.clear();
				String molecule = moleculesModel.getMolecule(id, moleculeSet);
				if (molecule != null) {
					// update the fields
					fieldId.setValue(Long.toString(id));
					fieldText.setValue(molecule);
					// update fingerprint
					fingerprint ^= id;
					fingerprint ^= molecule.hashCode();
					// use scorer (if present) to boost the document weight
					if (scorer != null) {
						doc.setBoost((float) scorer.score(id));
					}
					// add this molecule to the index
					writer.addDocument(doc);
				}
			}
			storeLastIndexedToIndex(numberOfEntities, writer);
			storeMoleculeToIndex(moleculesModel, writer);
			writer.commit();
			writer.close();
		} catch (IOException iox) {
			getLogger().error("Failed storing " + indexDesc, iox);
			return false;
		} finally {
			try {
				writer.close();
			} catch (IOException iox) {
				getLogger().error("Failed closing " + indexDesc, iox);
			}
		}

		moleculesModel.shutDown();

		getLogger().info("Finished creating " + indexDesc);

		index.setFingerprint(fingerprint);

		// drop previous index
		dropIndex(indexName);

		File indexDir = new File(getDataDir() + File.separator + indexName);
		File oldIndexDir = new File(getDataDir() + File.separator + indexName + OLD_SUFFIX);

		// move lucene files from the old index directory to a temporary folder
		oldIndexDir.mkdirs();
		FileUtils.moveDirectoryFilesTo(indexDir, oldIndexDir);

		// move new lucene files from their temporary directory to the actual one
		indexDir.mkdirs();
		FileUtils.moveDirectoryFilesTo(tempIndexDir, indexDir);
		// update index with its new location
		try {
			index.setDataDir(indexDir);
		} catch (IOException iox) {
			getLogger().error("The newly produced index is not usable", iox);
			return false;
		} finally {
			// delete redundant directory
			FileUtils.recursiveDelete(oldIndexDir);
			FileUtils.recursiveDelete(tempIndexDir);
		}

		// register new index
		registerIndex(indexName, index);

		return true;
	}

	/**
	 * (Incrementally) adds to FTS index all entities that have been added since last createIndex()
	 * 
	 * Note: store the id of the last indexed entity in a sysdata field named FIELD_LASTINDEXED in the index.
	 */
	private boolean updateIndex(String indexName, PluginConnection pluginConnection) {
		if (pluginConnection.getProperties().isReadOnly()) {
			getLogger().error("Can't update Lucene index when in read-only mode");
			return false;
		}

		// check if index name is a valid one
		if (indexName == null || !indexName.matches("[a-zA-Z0-9_]*")) {
			getLogger().error("Invalid index name: " + indexName);
			return false;
		}

		File indexDir = new File(getDataDir() + File.separator + indexName);
		if (!indexDir.exists()) {
			getLogger().error("Non existing index directory: " + indexDir.getAbsolutePath());
			return false;
		}

		Entities entities = pluginConnection.getEntities();
		Statements statements = pluginConnection.getStatements();

		LuceneIndex index = null;
		Document sysdoc;
		long lastIndexedEntityId;
		MoleculeModel moleculesModel = new MoleculeModel();
		IndexSearcher searcher = null;
		try {
			index = new LuceneIndex(indexName, indexDir);
			Query parsedQuery = index.getParser().parse(FIELD_SYSDATA + ":" + FIELD_LASTINDEXED);
			searcher = index.getSearcher();
			TopDocs topDocs = searcher.search(parsedQuery, 2);
			if (topDocs.totalHits != 1) {
				getLogger().error(
						topDocs.totalHits == 0 ? "Missing sysdata in index " + indexName
								: "Duplicate sysdata in index " + indexName);
				searcher.close();
				index.shutDown();
				return false;
			}
			sysdoc = searcher.doc(topDocs.scoreDocs[0].doc);
			String lastEntityIdStr = sysdoc.get(FIELD_VALUE);
			if (lastEntityIdStr == null) {
				getLogger().error("Missing sysdata.lastindexed in index " + indexName);
				index.shutDown();
				return false;
			}
			try {
				lastIndexedEntityId = Long.valueOf(lastEntityIdStr);
			} catch (NumberFormatException nfe) {
				getLogger().error("Non-numeric sysdata.lastindexed value: " + lastEntityIdStr);
				index.shutDown();
				return false;
			}

			loadMoleculeFromIndex(moleculesModel, index);
		} catch (Exception ex) {
			getLogger().error("Failed to initialize index", ex);
			try {
				if (index != null)
					index.shutDown();
			} catch (IOException e) {
			}
			return false;
		} finally { 
			try {
			if (searcher != null)
				searcher.close();
			} catch (Exception e) {}
		}

		long numberOfEntities = entities.size();
		if (lastIndexedEntityId >= numberOfEntities) {
			try {
				if (index != null)
					index.shutDown();
			} catch (IOException e) {
			}
			return false; // no new resource to index
		}

		try {
			index.configureAnalyzerFactory(analyzer);
		} catch (Exception ex) {
			getLogger().error("Failed to initialize analyzer " + analyzer, ex);
			try {
				if (index != null)
					index.shutDown();
			} catch (IOException e) {
			}
			return false;
		}

		IndexWriter writer;
		try {
			writer = index.getWriter();
		} catch (CorruptIndexException e1) {
			getLogger().error("The " + getIndexDesc(indexName) + " is corrupted", e1);
			try {
				if (index != null)
					index.shutDown();
			} catch (IOException e) {
			}
			return false;
		} catch (LockObtainFailedException e1) {
			getLogger().error("The " + getIndexDesc(indexName) + " is locked", e1);
			try {
				if (index != null)
					index.shutDown();
			} catch (IOException e) {
			}
			return false;
		} catch (IOException e1) {
			getLogger().error("The " + getIndexDesc(indexName) + " is not modifiable", e1);
			try {
				if (index != null)
					index.shutDown();
			} catch (IOException e) {
			}
			return false;
		}

		assert (writer != null);

		moleculesModel.setStatements(statements);
		moleculesModel.setEntities(entities);
		long fingerprintUpdate = 0;
		for (long id = lastIndexedEntityId + 1; id <= numberOfEntities; id++) {
			try {
				fingerprintUpdate ^= addToIndex(index, writer, moleculesModel, id, entities);
			} catch (IOException e) {
				getLogger().error("Failed writing to " + getIndexDesc(indexName));
				try {
					writer.close();
				} catch (IOException iox) {
					getLogger().error("Failed closing " + getIndexDesc(indexName), iox);
				}
				try {
					if (index != null)
						index.shutDown();
				} catch (IOException e1) {
				}
				return false;
			}
		}

		// Now update LAST_ENT_ID
		sysdoc = new Document();
		sysdoc.add(new Field(FIELD_SYSDATA, FIELD_LASTINDEXED, Store.NO, Index.NOT_ANALYZED_NO_NORMS));
		sysdoc.add(new Field(FIELD_VALUE, String.valueOf(numberOfEntities), Store.YES, Index.NO));
		try {
			writer.updateDocument(new Term(FIELD_SYSDATA, FIELD_LASTINDEXED), sysdoc);
		} catch (IOException e) {
			getLogger().error("Failed writing to " + getIndexDesc(indexName));
			try {
				writer.close();
			} catch (IOException iox) {
				getLogger().error("Failed closing " + getIndexDesc(indexName), iox);
			}
			try {
				if (index != null)
					index.shutDown();
			} catch (IOException e1) {
			}
			return false;
		}

		try {
			writer.commit();
			writer.close(true);
		} catch (IOException iox) {
			getLogger().error("Failed committing for " + getIndexDesc(indexName), iox);
			try {
				if (index != null)
					index.shutDown();
			} catch (IOException e) {
			}
			return false;
		} finally {
			try {
				writer.close(true);
			} catch (IOException iox) {
				getLogger().error("Failed closing " + getIndexDesc(indexName), iox);
			}
		}

		moleculesModel.shutDown(); // not really needed as there's no graph in this case

		// try to refresh the index object
		try {
			index.refresh();
		} catch (IOException iox) {
			getLogger().error("The newly produced index is not usable", iox);
			try {
				if (index != null)
					index.shutDown();
			} catch (IOException e) {
			}
			return false;
		}

		index.setFingerprint(index.getFingerprint() ^ fingerprintUpdate);
		registerIndex(indexName, index);

		return true;
	}

	/**
	 * (Incrementally) index/add a single entity to an existing index. This is the short version (by indexName
	 * only). See next method for the long version.
	 */
	private boolean addToIndex(String indexName, long id, PluginConnection pluginConnection) {

		if (pluginConnection.getProperties().isReadOnly()) {
			getLogger().error("Can't update Lucene index when in read-only mode");
			return false;
		}

		// check if index name is a valid one
		if (indexName == null || !indexName.matches("[a-zA-Z0-9_]*")) {
			getLogger().error("Invalid index name: " + indexName);
			return false;
		}

		File indexDir = new File(getDataDir() + File.separator + indexName);
		if (!indexDir.exists()) {
			getLogger().error("Non existing index directory: " + indexDir.getAbsolutePath());
			return false;
		}

		LuceneIndex index;
		try {
			index = new LuceneIndex(indexName, indexDir);
		} catch (Exception ex) {
			getLogger().error("Failed to initialize index", ex);
			return false;
		}

		Entities entities = pluginConnection.getEntities();
		Statements statements = pluginConnection.getStatements();

		MoleculeModel moleculesModel = new MoleculeModel();
		try {
			index.configureAnalyzerFactory(analyzer);
			loadMoleculeFromIndex(moleculesModel, index);
			moleculesModel.setStatements(statements);
			moleculesModel.setEntities(entities);
		} catch (Exception ex) {
			getLogger().error("Failed to initialize index " + index, ex);
			return false;
		}

		String indexDesc = getIndexDesc(indexName);
		IndexWriter writer;
		try {
			writer = index.getWriter();
		} catch (CorruptIndexException e) {
			getLogger().error("The " + indexDesc + " is corrupted", e);
			return false;
		} catch (LockObtainFailedException e) {
			getLogger().error("The " + indexDesc + " is locked", e);
			return false;
		} catch (IOException e) {
			getLogger().error("The " + indexDesc + " is not modifiable", e);
			return false;
		}

		long fingerprintUpdate = 0;
		try {
			fingerprintUpdate ^= addToIndex(index, writer, moleculesModel, id, entities);
			writer.commit();
		} catch (IOException iox) {
			getLogger().error("Failed addToIndex for " + indexDesc, iox);
			return false;
		} finally {
			try {
				writer.close();
			} catch (IOException iox) {
				getLogger().error("Failed closing " + indexDesc, iox);
			}
		}

		moleculesModel.shutDown(); // not really needed as there's no graph in this case

		// try to refresh the index object
		try {
			index.refresh();
		} catch (IOException iox) {
			getLogger().error("The newly produced index is not usable", iox);
			return false;
		}

		index.setFingerprint(index.getFingerprint() ^ fingerprintUpdate);
		registerIndex(indexName, index);

		return true;
	}

	/**
	 * (Incrementally) index/add a single entity to an existing index Note: prev method createIndex() is quite
	 * similar, but it builds the molecule and indexes a single entity. Those 2 methods share a similar logic
	 * (especially in their prologues) so if you need to modify one of them, you'd better check if the next
	 * one needs a similar modification.
	 * 
	 * @param index
	 * @param writer
	 * @param moleculesModel
	 * @param id
	 *            id of the entity to add
	 * @return value by which to update the index fingerprint, or 0 if not changed.
	 */
	private long addToIndex(LuceneIndex index, IndexWriter writer, MoleculeModel moleculesModel, long id,
			Entities entities) throws IOException {
		assert (index != null);
		assert (writer != null);
		assert (moleculesModel != null);

		// In creatIndex this is the place to invoke
		// moleculesModel.initialize();
		// but we don't do it here, since we do not build a molecule graph.

		Field fieldId = new Field(FIELD_ID, "", Store.YES, Index.NO);
		Field fieldText = new Field(FIELD_TEXT, "", Store.NO, Index.ANALYZED);

		Document doc = new Document();

		doc.add(fieldId);
		doc.add(fieldText);

		getLogger().info("Add to index entity #" + id + " ...");

		Entities.Type type = entities.getType(id);
		switch (type) {
		case URI:
			if (!moleculesModel.getIndexFilter().indexURI()) {
				return 0;
			}
			break;
		case BNODE:
			if (!moleculesModel.getIndexFilter().indexBNode()) {
				return 0;
			}
			break;
		case LITERAL:
			if (!moleculesModel.getIndexFilter().indexLiteral()) {
				return 0;
			}
			break;
		}

		// only index base equivalence classes
		if (entities.getClass(id) != id) {
			return 0;
		}

		String molecule = moleculesModel.getMoleculeWoGraph(id);
		if (molecule != null) {
			// update the fields
			fieldId.setValue(Long.toString(id));
			fieldText.setValue(molecule);
			// use scorer (if present) to boost the document weight
			if (scorer != null) {
				doc.setBoost((float) scorer.score(id));
			}
			// add this molecule to the index
			writer.addDocument(doc);

			return id ^ molecule.hashCode(); // fingeprint update
		}

		return 0;
	}

	private void dropIndex(String indexName) {
		LuceneIndex index = indices.get(indexName);
		if (index != null) {
			try {
				index.shutDown();
			} catch (IOException e) {
				getLogger().error("Failed to shutdown Lucene " + getIndexDesc(indexName), e);
			}
			indices.remove(indexName);
		}
	}

	private void registerIndex(String indexName, LuceneIndex index) {
		if (index.isOperational()) {
			LuceneIndex old = indices.put(indexName, index);
			if (old != null && !old.equals(index)) {
				try {
					old.shutDown();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			try {
				index.writeProperties();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	public void setLocator(PluginLocator locator) {
		pluginLocator = locator;
	}

	@Override
	public long getFingerprint() {
		long result = 0;
		for (String name : indices.keySet()) {
			result ^= name.hashCode() + indices.get(name).getFingerprint();
		}
		return result;
	}

	private String getIndexDesc(String indexName) {
		return indexName.length() > 0 ? "'" + indexName + "' index" : "default index";
	}

	private Set<IRI> parseURIList(String value) {
		Set<IRI> result = null;
		try {
			result = Utils.parseURIList(value);
		} catch (IllegalArgumentException ex) {
			getLogger().error("URI list contains invalid URI: " + value + ". Ignoring list.");
			throw ex;
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private List<LuceneIterator> getActiveIterators(RequestContext requestContext) {
		List<LuceneIterator> iters = (List<LuceneIterator>) ((RequestContextImpl) requestContext)
				.getAttribute(ATTRIBUTE_ITERATORS);
		if (iters == null) {
			iters = new LinkedList<LuceneIterator>();
			((RequestContextImpl) requestContext).setAttribute(ATTRIBUTE_ITERATORS, iters);
		}
		return iters;
	}

	private void addIterator(RequestContext requestContext, LuceneIterator iterator) {
		getActiveIterators(requestContext).add(iterator);
	}

	protected float getScore(RequestContext requestContext, long subject) {
		Iterator<LuceneIterator> iters = getActiveIterators(requestContext).iterator();
		while (iters.hasNext()) {
			LuceneIterator curr = iters.next();
			if (curr.isClosed()) {
				iters.remove();
			} else {
				if (curr.subject == subject) {
					return curr.score;
				}
			}
		}
		return 0;
	}

	@Override
	public void shutdown(ShutdownReason shutdownReason) {
		for (String indexName : indices.keySet()) {
			LuceneIndex index = indices.get(indexName);
			getLogger().info("Shutting down " + getIndexDesc(indexName));
			try {
				index.shutDown();
			} catch (IOException e) {
				getLogger().error("Failed shutting down Lucene " + getIndexDesc(indexName), e);
			}
		}
	}

	@Override
	public RequestContext preprocess(Request request) {
		return new RequestContextImpl(request);
	}

	@Override
	public double estimate(long subject, long predicate, long object, long context, PluginConnection pluginConnection,
						   RequestContext requestContext) {
		if (Utils.match(predicate, idSetParam, idCreateIndex, idScore)) {
			return 1;
		}
		if (subject == Entities.BOUND) { // We don't prefer orderings where the Lucene triple pattern does not bind the variable because it becomes a filter and is calculated extremely slowly.
			return 1000000;
		}
		return 1000; // TODO: get a better estimate from the index
	}

	/**
	 * Stores last indexed entity id in the index, to be later retrieved for updateIndex() purposes.
	 */
	private void storeLastIndexedToIndex(long numberOfEntities, IndexWriter writer) throws IOException {
		Document doc = new Document();
		doc.add(new Field(FIELD_SYSDATA, FIELD_LASTINDEXED, Store.NO, Index.NOT_ANALYZED_NO_NORMS));
		doc.add(new Field(FIELD_VALUE, String.valueOf(numberOfEntities), Store.YES, Index.NO));
		writer.addDocument(doc);
	}

	/**
	 * Persists molecule parameters to the index, so that they could later be retrieved and used for identical
	 * molecule reconstruction in order to incrementally update index.
	 */
	private void storeMoleculeToIndex(MoleculeModel molecule, IndexWriter writer) throws IOException {
		Document doc = new Document();
		doc.add(new Field(FIELD_SYSDATA, FIELD_MOLECULE, Store.NO, Index.NOT_ANALYZED_NO_NORMS));
		doc.add(new Field(FIELD_VALUE, molecule.toJSON(), Store.YES, Index.NO));
		writer.addDocument(doc);
	}

	/**
	 * Loads molecule parameters from the index. The params must have been previously stored by
	 * storeMoleculeToIndex.
	 */
	private void loadMoleculeFromIndex(MoleculeModel molecule, LuceneIndex index) throws ParseException,
			IOException {
		Query parsedQuery = index.getParser().parse(FIELD_SYSDATA + ":" + FIELD_MOLECULE);
		IndexSearcher searcher = index.getSearcher();
		TopDocs topDocs = searcher.search(parsedQuery, 2);
		if (topDocs.totalHits != 1) {
			String msg = (topDocs.totalHits == 0 ? "Missing sysdata in index " + index.getName()
					: "Duplicate sysdata in index " + index.getName());
			throw new IOException(msg);
		}
		Document sysdoc = searcher.doc(topDocs.scoreDocs[0].doc);
		String moleculeStr = sysdoc.get(FIELD_VALUE);
		if (moleculeStr == null) {
			throw new IOException("Missing sysdata.molecule in index " + index.getName());
		}

		try {
			molecule.parseJSON(moleculeStr);
			assert molecule.toJSON().equals(moleculeStr);
		} catch (JSONparser.Exception e) {
			throw new IOException("Corrupted sysdata.molecule entry in index " + index.getName());
		}
	}

	@Override
	public long[] getPredicatesToListenFor() {
		return new long[] { idAddToIndex, idCreateIndex, idSetParam, idUpdateIndex };
	}

	@Override
	public boolean interpretUpdate(long subject, long predicate, long object, long context, boolean isAddition,
			boolean isExplicit, PluginConnection pluginConnection) {
		interpret(subject, predicate, object, context, pluginConnection, null);
		return true;
	}

	private void printDeprecationWarning() {
		getLogger().warn("The Lucene FTS plugin has been deprecated in favour of new functionality in the Connectors"
				+ " and will be removed in a future version of GraphDB.");
		getLogger().warn("See {} for more information.", documentationUrl);
	}
}
