package com.ontotext.trree.plugin.lucene;

import com.ontotext.trree.sdk.Entities;
import com.ontotext.trree.sdk.StatementIterator;
import com.ontotext.trree.sdk.Statements;
import com.ontotext.trree.util.TableStorage;
import gnu.trove.TLongHashSet;
import gnu.trove.TLongIterator;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Value;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class MoleculeModel implements JSONizer.JSONizableAsSimpleMap {
	private Statements statements;
	private Entities entities;
	private int entityBitSize;
	private int degree = 0;
	private IncludeFilter includeFilter = new IncludeFilter();
	private IndexFilter   indexFilter = new IndexFilter();
	private Pattern exclude = null;
	private boolean allowMissingLanguage = true;
	private HashSet<String> languages;
	private File dataDir;
	private TableStorage graph;
	private long[] includePredicates;
	private long[] excludePredicates;
	private long[] includeEntities;
	private long[] excludeEntities;

	private static final String MOLECULE_SEPARATOR = " ";

	public void setStatements(Statements statements) {
		this.statements = statements;
	}

	public void setEntities(Entities entities) {
		this.entities = entities;
		entityBitSize = entities.getEntityIdSize();
	}

	public int getDegree() {
		return degree;
	}

	public void setDegree(int d) {
		if (d < 0) {
			throw new IllegalArgumentException();
		}
		degree = d;
	}

	public void setIncludeFilter(IncludeFilter f) {
		assert f != null;
		includeFilter = f;
	}

	public void setIndexFilter(IndexFilter f) {
		assert f != null;
		indexFilter = f;
	}

	public IndexFilter  getIndexFilter() {
		return indexFilter;
	}

	public Pattern getExcludePattern() {
		return exclude;
	}

	public void setExcludePattern(Pattern p) {
		exclude = p;
	}

	public void setLanguages(String[] l) {
		allowMissingLanguage = false;
		if (l == null || l.length == 0) {
			languages = null;
			allowMissingLanguage = true;
		} else {
			languages = new HashSet<String>();
			for (String language : l) {
				if (language.toLowerCase().equals("none")) {
					allowMissingLanguage = true;
				} else {
					languages.add(language);
				}
			}
		}
	}

	public boolean isMissingLanguageAllowed() {
		return allowMissingLanguage;
	}

	public File getDataDir() {
		return dataDir;
	}

	public void setDataDir(File dir) {
		dataDir = dir;
	}

	public void initialize() throws IOException {
		StatementIterator iter = statements.get(0, 0, 0, 0);
		int degree = getDegree();
		if (degree > 0) {
			long size = entities.size() + 1;

			// initialize table storage
			graph = new TableStorage(getDataDir().getAbsolutePath() + File.separator + "graph",
					                        size, size, entityBitSize);

			// stuff the whole repository into the table storage
			while (iter.next()) {
				boolean isRelevant = true;

				Entities.Type subjectType = entities.getType(iter.subject);
				Entities.Type objectType = entities.getType(iter.object);

				if (checkPredicate(iter.predicate) && (checkEntity(iter.subject, subjectType)
						&& checkEntity(iter.object, objectType))) {
					// skip redundant connections
					if (objectType == Entities.Type.URI) {
						if (!includeFilter.includeURI() && degree == 1) {
							isRelevant = false;
						}
					} else if (objectType == Entities.Type.LITERAL) {
						if (!includeFilter.includeLiteral()) {
							isRelevant = false;
						}
					}
					if (isRelevant) {
						graph.add(iter.subject, iter.object);
					}
				}
			}
		}
	}

	public String getMolecule(long id, TLongHashSet molecule) {
		// build molecule for this entity
		buildMolecule(id, entities.getType(id), molecule);

		StringBuilder stringizedMolecule = new StringBuilder();

		TLongIterator iter = molecule.iterator();
		while (iter.hasNext()) {
			long node = iter.next();
			Entities.Type type = entities.getType(node);

			boolean include = false;

			// Special processing for the centre of a molecule
			if (node == id) {
				if (includeFilter.includeCentre()) {
					if (type != Entities.Type.BNODE) {
						include = true;
					}
				}
			}

			if (type == Entities.Type.URI) {
				if (includeFilter.includeURI()) {
					include = true;
				}
			}

			// skip literals with non-configured languages
			if (type == Entities.Type.LITERAL) {
				if (includeFilter.includeLiteral()) {
					if (languages == null) {
						assert allowMissingLanguage;
						include = true;
					} else {
						String language = entities.getLanguage(node);
						if (language == null) {
							if (allowMissingLanguage) {
								include = true;
							}
						} else {
							if (languages.contains(language)) {
								include = true;
							}
						}
					}
				}
			}

			if (include) {
				// read this entity from the pool
				Value value = entities.get(node);
				if (value == null) {
					continue;
				}

				String stringValue = stringify(value);

				// check if exclusion pattern will allow this value
				if (exclude != null) {
					if (exclude.matcher(stringValue).matches()) {
						continue;
					}
				}

				if (stringizedMolecule.length() > 0) {
					stringizedMolecule.append(MOLECULE_SEPARATOR);
				}

				stringizedMolecule.append(stringValue);
			}
		}
		return stringizedMolecule.toString();
	}

	private String stringify(Value value) {
		return (value instanceof IRI) ? ((IRI) value).getLocalName() : value.stringValue();
	}

	private TLongHashSet buildMolecule(long id, Entities.Type type, TLongHashSet molecule) {
		if (molecule == null) {
			molecule = new TLongHashSet();
		}

		buildMolecule(id, type, molecule, getDegree());

		return molecule;
	}

	private void buildMolecule(long id, Entities.Type type, TLongHashSet molecule, int hops) {
		// have we visited this entity
		if (molecule.contains(id)) {
			return;
		}

		// add this entity to the molecule
		molecule.add(id);

		// proceed further only if there are remaining hops to be done
		if (hops > 0) {
			TableStorage.Iterator iter = graph.rowIterator(id);
			while (iter.hasNext()) {
				long next = iter.next();
				Entities.Type nextType = entities.getType(next);
				int nextHops = nextType == Entities.Type.BNODE ? hops : hops - 1;
				// proceed building molecule with next node
				buildMolecule(next, nextType, molecule, nextHops);
			}
		}
	}

	public void setIncludePredicates(Set<IRI> list) {
		includePredicates = resolveURIList(list);
	}

	public void setExcludePredicates(Set<IRI> list) {
		excludePredicates = resolveURIList(list);
	}

	public void setIncludeEntities(Set<IRI> list) {
		includeEntities = resolveURIList(list);
	}

	public void setExcludeEntities(Set<IRI> list) {
		excludeEntities = resolveURIList(list);
	}

	private boolean checkPredicate(long predicate) {
		return checkId(predicate, includePredicates, excludePredicates);
	}

	private boolean checkEntity(long entity, Entities.Type type) {
		if (type != Entities.Type.URI) {
			return true;
		}
		return checkId(entity, null, excludeEntities);
	}

	private boolean checkId(long id, long[] include, long[] exclude) {
		if (include != null && Arrays.binarySearch(include, id) < 0) {
			return false;
		}
		if (exclude != null && Arrays.binarySearch(exclude, id) >= 0) {
			return false;
		}
		return true;
	}

	private long[] resolveURIList(Set<IRI> list) {
		if (list == null) {
			return null;
		}

		long[] result = new long[list.size()];
		int count = 0;

		for (IRI uri : list) {
			long id = entities.resolve(uri);
			if (id != 0) {
				result[count++] = id;
			}
		}

		// trim array if needed
		if (count < result.length) {
			result = Arrays.copyOf(result, count);
		}

		// sort array in order to be able to search fast in it
		Arrays.sort(result);

		return result;
	}

	public void shutDown() {
		if (graph != null) {
			graph.shutDown();
		}
	}

	/** Similar to buildMolecule() but does not rely on the subject-object graph.
	 *  Method is used to build single molecules for incremental index update.
	 */
	private void buildMoleculeWoGraph(long id, Entities.Type type, int hops, TLongHashSet molecule) {
		// have we visited this entity
		if (molecule.contains(id)) {
			return;
		}

		// add this entity to the molecule
		molecule.add(id);

		// proceed further only if there are remaining hops to be done
		if (hops > 0) {
			StatementIterator iter = statements.get(id, 0, 0, 0);
			while (iter.next()) {
				if (!checkPredicate(iter.predicate))
					continue;
				long next = iter.object;
				Entities.Type nextType = entities.getType(next);
				int nextHops = nextType == Entities.Type.BNODE ? hops : hops - 1;
				// proceed building molecule with next node
				buildMoleculeWoGraph(next, nextType, nextHops, molecule);
			}
		}
	}

	public String getMoleculeWoGraph(long id) {
		TLongHashSet molecule = new TLongHashSet();

		// build molecule for this entity
		buildMoleculeWoGraph(id, entities.getType(id), getDegree(), molecule);

		StringBuilder stringizedMolecule = new StringBuilder();

		TLongIterator iter = molecule.iterator();
		while (iter.hasNext()) {
			long node = iter.next();
			Entities.Type type = entities.getType(node);

			boolean include = false;

			// Special processing for the centre of a molecule
			if (node == id) {
				if (includeFilter.includeCentre()) {
					if (type != Entities.Type.BNODE) {
						include = true;
					}
				}
			}

			if (type == Entities.Type.URI) {
				if (includeFilter.includeURI()) {
					include = true;
				}
			}

			// skip literals with non-configured languages
			if (type == Entities.Type.LITERAL) {
				if (includeFilter.includeLiteral()) {
					if (languages == null) {
						assert allowMissingLanguage;
						include = true;
					} else {
						String language = entities.getLanguage(node);
						if (language == null) {
							if (allowMissingLanguage) {
								include = true;
							}
						} else {
							if (languages.contains(language)) {
								include = true;
							}
						}
					}
				}
			}

			if (include) {
				// read this entity from the pool
				Value value = entities.get(node);
				if (value == null) {
					continue;
				}

				String stringValue = stringify(value);

				// check if exclusion pattern will allow this value
				if (exclude != null) {
					if (exclude.matcher(stringValue).matches()) {
						continue;
					}
				}

				if (stringizedMolecule.length() > 0) {
					stringizedMolecule.append(MOLECULE_SEPARATOR);
				}

				stringizedMolecule.append(stringValue);
			}
		}
		return stringizedMolecule.length() > 0 ? stringizedMolecule.toString() : null;
	}

	private final static String
		str_entityBitSize    = "entityBitSize",
		str_degree           = "degree",
		str_includeFilter    = "includeFilter",
		str_indexFilter      = "indexFilter",
		str_exclude          = "exclude",
		str_allowMissingLang = "allowMissingLang",
		str_langs            = "langs",
	    str_dataDir          = "dataDir",
		str_includePredicates= "includePredicates",
		str_excludePredicates= "excludePredicates",
		str_includeEntities  = "includeEntities",
		str_excludeEntities  = "excludeEntities";

	public String  toJSON() {
		return new JSONizer().add(this).toString();
	}

	public void  parseJSON(String str) throws JSONparser.Exception {
		JSONparser.MapVal map = JSONparser.parse(str).asMap();

		this.entityBitSize = map.get(str_entityBitSize).asInt();
		this.degree        = map.get(str_degree).asInt();
		this.includeFilter.initFromMap(map.get(str_includeFilter).asMap());
		this.indexFilter.initFromMap(map.get(str_indexFilter).asMap());

		JSONparser.ArrVal arrVal = map.get(str_exclude).asArr();
		if (arrVal.size() == 2) {
			String regex = arrVal.get(0).asString();
			int    flags = arrVal.get(1).asInt();
			this.exclude = Pattern.compile(regex, flags);
		} else if (arrVal.size() != 0) {
			throw new JSONparser.Exception("Expected [<regex>, <flags>]");
		}

		this.allowMissingLanguage = map.get(str_allowMissingLang).asBoolean();
		JSONparser.Val val = map.get(str_langs);
		if (val != JSONparser.NoVal.VAL && val.asArr().size() > 0) {
			this.languages = new HashSet<String>();
			for (JSONparser.Val v : val.asArr().vals) {
				this.languages.add(((JSONparser.StrVal) v).val);
			}
		}

		this.dataDir = new File(map.get(str_dataDir).asString());

		val = map.get(str_includePredicates);
		this.includePredicates = (val.type == JSONparser.ARR ? val.asArr().asLongArray() : null);

		val = map.get(str_excludePredicates);
		this.excludePredicates = (val.type == JSONparser.ARR ? val.asArr().asLongArray() : null);

		val = map.get(str_includeEntities);
		this.includeEntities = (val.type == JSONparser.ARR ? val.asArr().asLongArray() : null);

		val = map.get(str_excludeEntities);
		this.excludeEntities = (val.type == JSONparser.ARR ? val.asArr().asLongArray() : null);
	}

	@Override
	public JSONizer.SimpleMap toSimpleMap() {
		return new SimpleMapAdapter();
	}

	private class SimpleMapAdapter implements JSONizer.SimpleMap {
		int fieldId = -1;
		final static int nFields = 11;

		@Override
		public boolean hasNext() {
			return fieldId + 1 < nFields;
		}

		@Override
		public void next() {
			fieldId++;
		}

		private Object currentKeyOrValue(boolean key) {
			switch (fieldId) {
				case 0: return key ? str_entityBitSize : entityBitSize;
				case 1: return key ? str_degree : degree;
				case 2: return key ? str_includeFilter : includeFilter;
				case 3: return key ? str_indexFilter : indexFilter;
				case 4: return key ? str_exclude : new JSONizer.PatternAsSimpleArray(exclude);
				case 5: return key ? str_allowMissingLang : allowMissingLanguage;
				case 6: return key ? str_langs : languages;
				case 7: return key ? str_dataDir : dataDir.getAbsolutePath();
				case 8: return key ? str_includePredicates : includePredicates;
				case 9: return key ? str_excludePredicates : excludePredicates;
				case 10:return key ? str_includeEntities : includeEntities;
				case 11:return key ? str_excludeEntities : excludeEntities;
			}
			assert false;
			return "<none>";
		}

		@Override
		public Object currentKey() {
			return currentKeyOrValue(true);
		}

		@Override
		public Object currentValue() {
			return currentKeyOrValue(false);
		}
	}
}
