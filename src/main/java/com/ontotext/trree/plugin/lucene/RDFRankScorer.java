package com.ontotext.trree.plugin.lucene;

import com.ontotext.trree.sdk.RDFRankProvider;

public class RDFRankScorer implements Scorer {
	private RDFRankProvider rdfRankProvider;

	public RDFRankScorer(RDFRankProvider rdfRankProvider) {
		this.rdfRankProvider = rdfRankProvider;
	}

	@Override
	public String getName() {
		return null;
	}

	@Override
	public double score(long id) {
		return rdfRankProvider.getRank(id);
	}

}
