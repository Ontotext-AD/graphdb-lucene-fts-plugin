package com.ontotext.test.functional.owlim_se;

import com.ontotext.test.functional.base.AbstractPluginLuceneFTS;
import com.ontotext.test.utils.StandardUtils;
import org.eclipse.rdf4j.repository.config.RepositoryConfig;

public class TestOwlimSePluginLuceneCustomAnalyzerFTS extends AbstractPluginLuceneFTS {

	public TestOwlimSePluginLuceneCustomAnalyzerFTS(boolean useUpdate) {
		super(useUpdate);
	}

	@Override
	protected RepositoryConfig createRepositoryConfiguration() {
		return StandardUtils.createOwlimSe("empty");
	}

	@Override
	protected String getCustomAnalyzer() {
		return "com.ontotext.test.functional.CustomAnalyzerFactory";
	}
}
