package com.ontotext.test.functional.owlim_se;

import com.ontotext.test.utils.StandardUtils;
import org.eclipse.rdf4j.repository.config.RepositoryConfig;

import com.ontotext.test.functional.base.AbstractPluginLuceneFTS;

public class TestOwlimSePluginLuceneFTS extends AbstractPluginLuceneFTS {

	public TestOwlimSePluginLuceneFTS(boolean useUpdate) {
		super(useUpdate);
	}

	@Override
	protected RepositoryConfig createRepositoryConfiguration() {
		return StandardUtils.createOwlimSe("empty");
	}
}
