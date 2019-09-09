package com.ontotext.trree.plugin.lucene;

import com.ontotext.trree.sdk.Service;

public interface Scorer extends Service {
	public double score(long id);
}
