package com.ontotext.trree.plugin.lucene;

public class IndexFilter extends Filter {
	public IndexFilter() {
		super("uri", "bnode", "literal");
	}

	public boolean indexURI() {
		return getFlagValue(0);
	}

	public boolean indexBNode() {
		return getFlagValue(1);
	}

	public boolean indexLiteral() {
		return getFlagValue(2);
	}
}
