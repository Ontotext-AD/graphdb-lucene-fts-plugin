package com.ontotext.trree.plugin.lucene;

public class IncludeFilter extends Filter {
	public IncludeFilter() {
		super("uri", "centre", "literal");
	}

	public boolean includeURI() {
		return getFlagValue(0);
	}

	public boolean includeCentre() {
		return getFlagValue(1);
	}

	public boolean includeLiteral() {
		return getFlagValue(2);
	}
}
