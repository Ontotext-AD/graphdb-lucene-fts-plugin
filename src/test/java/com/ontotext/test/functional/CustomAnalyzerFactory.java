package com.ontotext.test.functional;

import com.ontotext.trree.plugin.lucene.AnalyzerFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.util.Version;

/**
 * Simple custom analyzer factory to use in tests.
 */
public class CustomAnalyzerFactory implements AnalyzerFactory {
	@Override
	public Analyzer createAnalyzer() {
		return new CustomAnalyzer(Version.LUCENE_36);
	}

	@Override
	public boolean isCaseSensitive() {
		return false;
	}
}
