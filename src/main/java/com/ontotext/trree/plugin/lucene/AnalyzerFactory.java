package com.ontotext.trree.plugin.lucene;

import org.apache.lucene.analysis.Analyzer;

public interface AnalyzerFactory {
	Analyzer createAnalyzer();
	boolean isCaseSensitive();
}
