package com.ontotext.trree.plugin.lucene;

import com.ontotext.trree.sdk.Entities;
import com.ontotext.trree.sdk.Entities.Scope;
import com.ontotext.trree.sdk.RequestContext;
import com.ontotext.trree.sdk.StatementIterator;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;

public class ScoreIterator extends StatementIterator {
	private final LucenePlugin plugin;
	private final Entities entities;
	private RequestContext requestContext;
	private static final ValueFactory F = SimpleValueFactory.getInstance();

	public ScoreIterator(long subject, long predicate, long object, LucenePlugin plugin, Entities entities,
			RequestContext requestContext) {
		super(subject, predicate, object);
		this.entities = entities;
		this.plugin = plugin;
		this.requestContext = requestContext;
	}

	@Override
	public boolean next() {
		if (subject == 0 || requestContext == null) {
			return false;
		}
		float score = plugin.getScore(requestContext, subject);
        object = entities.put(F.createLiteral(Float.toString(score)), Scope.REQUEST);
		requestContext = null; // so further calls are responded to negatively
		return true;
	}

	@Override
	public void close() {

	}
}
