package com.ontotext.trree.plugin.lucene;

import org.apache.lucene.search.ScoreDoc;

import com.ontotext.trree.sdk.StatementIterator;

public class LuceneIterator extends StatementIterator {

	private ScoreDoc[] results;

	private int index = 0;
	private int upper = 0;
	private int limit = 0;

	public float score = 0;

	public LuceneIterator(ScoreDoc[] results, int offset, int limit) {
		this.results = results;
		this.index = offset;
		this.upper = offset + limit;
		this.limit = limit;
	}

	@Override
	public boolean next() {
		if (index < upper) {
			subject = object = results[index].doc & 0xFFFFFFFFL;
			score = results[index].score;
			index++;
			return true;
		} else {
			results = null;
			return false;
		}
	}

	public long size() {
		return limit;
	}

	
	@Override
	public void close() {
		// shirnk the rest
		upper = index;
		
	}
	public boolean isClosed() {
		return results == null;
	}
}
