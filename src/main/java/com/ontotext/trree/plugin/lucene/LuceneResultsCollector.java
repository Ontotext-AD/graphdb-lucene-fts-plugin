package com.ontotext.trree.plugin.lucene;

import java.io.IOException;
import java.util.Comparator;
import java.util.PriorityQueue;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.document.MapFieldSelector;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Scorer;

public class LuceneResultsCollector extends Collector {
	private IndexReader reader;
	private Scorer scorer;

	private PriorityQueue<ScoreDoc> hits = new PriorityQueue<ScoreDoc>(100, compare);
	private ScoreDoc[] results;
	private int[] docToURI;
	private int docBase;

	private static final FieldSelector select = new MapFieldSelector(LucenePlugin.FIELD_ID);

	private static final Comparator<ScoreDoc> compare = new Comparator<ScoreDoc>() {
		@Override
		public int compare(ScoreDoc h1, ScoreDoc h2) {
			int cmp = Float.compare(h2.score, h1.score);
			if (cmp != 0) {
				return cmp;
			}
			return h1.doc - h2.doc;
		}
	};

	public LuceneResultsCollector(int[] docToURI) {
		this.docToURI = docToURI;
	}

	@Override
	public boolean acceptsDocsOutOfOrder() {
		return true;
	}

	@Override
	public void collect(int doc) throws IOException {
		hits.add(new ScoreDoc(docToId(doc), scorer.score()));
	}

	@Override
	public void setNextReader(IndexReader reader, int docBase) throws IOException {
		this.reader = reader; this.docBase = docBase;
	}

	@Override
	public void setScorer(Scorer scorer) throws IOException {
		this.scorer = scorer;
	}

	private int docToId(int docId) throws IOException {
		if (docToURI[docId+docBase] == 0) {
			Document doc = reader.document(docId, select);
			if (doc != null) {
				Fieldable field = doc.getField(LucenePlugin.FIELD_ID);
				if (field != null) {
					docToURI[docId+docBase] = Integer.parseInt(field.stringValue());
				}
			}
		}
		return docToURI[docId+docBase];
	}

	public ScoreDoc[] getResults() {
		if (results == null) {
			results = new ScoreDoc[hits.size()];
			for (int idx = 0; idx < results.length; idx++) {
				results[idx] = hits.poll();
			}
		}
		return results;
	}
}