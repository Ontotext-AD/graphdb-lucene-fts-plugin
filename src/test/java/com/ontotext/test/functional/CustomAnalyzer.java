package com.ontotext.test.functional;

import org.apache.lucene.analysis.LowerCaseTokenizer;
import org.apache.lucene.analysis.ReusableAnalyzerBase;
import org.apache.lucene.util.Version;

import java.io.Reader;

/**
 * Simple custom analyzer to use in tests.
 */
public class CustomAnalyzer extends ReusableAnalyzerBase {
	private final Version matchVersion;

	public CustomAnalyzer(Version matchVersion) {
		this.matchVersion = matchVersion;
	}

	@Override
	protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
		return new TokenStreamComponents(new LowerCaseTokenizer(matchVersion, reader));
	}
}
