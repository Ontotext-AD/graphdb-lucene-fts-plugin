package com.ontotext.trree.plugin.lucene;

import java.util.Arrays;
import java.util.Iterator;

public class Filter implements JSONizer.JSONizableAsSimpleMap {
	protected String[]  flags;
	protected boolean[] values;

	public Filter(String... flags) {
		if (flags == null) {
			throw new IllegalArgumentException();
		}

		this.flags = Arrays.copyOf(flags, flags.length);
		this.values = new boolean[flags.length];
	}

	public void initialize(String config) {
		Arrays.fill(values, false);

		if (config != null) {
			String[] tokens = config.trim().split("\\s*,\\s*");
			for (String token : tokens) {
				String lowerCaseToken = token.toLowerCase();
				for (int idx = 0; idx < flags.length; idx++) {
					if (lowerCaseToken.startsWith(flags[idx])) {
						values[idx] = true;
					}
				}
			}
		}
	}

	public int  getFlagNumber() {
		return flags.length;
	}

	public String  getFlagName(int index) {
		return flags[index];
	}

	public boolean getFlagValue(int index) {
		return values[index];
	}

	@Override
	public JSONizer.SimpleMap toSimpleMap() {
		return new JSONizer.SimpleMap() {
			int  flagId = -1;

			public boolean  hasNext() {
				return flagId + 1 < flags.length;
			}

			public void  next() {
				flagId++;
			}

			public Object    currentKey() {
				assert flagId < flags.length;
				return flags[flagId];
			}

			public Object    currentValue() {
				assert flagId < flags.length;
				return values[flagId];
			}
		};
	}

	public void  initFromMap(JSONparser.MapVal map) throws JSONparser.Exception {
/*		this.flags  = new String[map.size()];
		this.values = new boolean[map.size()];
		Iterator<String> keys = map.keys().iterator();
		int i = 0;
		while (keys.hasNext()) {
			String key = keys.next();
			flags[i]  = key;
			values[i] = map.get(key).asBoolean();
			i++;
		} */
		for (int i = 0; i < flags.length; i++) {
			values[i] = map.get(flags[i]).asBoolean();
		}
	}
}
