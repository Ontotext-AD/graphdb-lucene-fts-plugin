package com.ontotext.test.functional.owlim_se;

import org.eclipse.rdf4j.repository.config.RepositoryConfig;

import com.ontotext.test.functional.base.AbstractPluginLuceneInclude;
import com.ontotext.test.utils.OwlimSeRepositoryDescription;

public class TestOwlimSePluginLuceneInclude extends AbstractPluginLuceneInclude {

	@Override
	protected RepositoryConfig createRepositoryConfiguration() {
		OwlimSeRepositoryDescription repositoryDescription = new OwlimSeRepositoryDescription();
		repositoryDescription.getOwlimSailConfig().setRuleset("empty");
		return repositoryDescription.getRepositoryConfig();
	}

}
