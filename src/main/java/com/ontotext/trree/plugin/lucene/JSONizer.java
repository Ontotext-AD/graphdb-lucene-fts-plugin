package com.ontotext.trree.plugin.lucene;

import java.util.Collection;
import java.util.Map;
import java.util.Iterator;
import java.util.regex.Pattern;

/**
 * Class JSONizer enables simple, recursive JSON-like serialization of
 * any combination of strings, numbers, array-like and map-like data structures.
 * For details on JSON see http://en.wikipedia.org/wiki/JSON
 * See its complement -- class JSONparser
 * User: stenlee
 * Date: 2/2/12
 */
public class JSONizer {

	protected StringBuilder sb = new StringBuilder();

	public String  toString() {
		return sb.toString();
	}

	public JSONizer  add(CharSequence s) {
		int len = s.length();
		sb.append('\"');
		for (int i = 0; i < len; ++i) {
			char c = s.charAt(i);
			if (c == '\"' || c == '\n' || c == '\\') {
				sb.append('\\');
			}
			sb.append(c);
		}
		sb.append('\"');
		return this;
	}

	public JSONizer  add(Object[] arr) {
		sb.append('[');
		for (int i = 0; i < arr.length; i++) {
			if (i != 0) {
				sb.append(", ");
			}
			add(arr[i]);
		}
		sb.append(']');
		return this;
	}

	public JSONizer  add(int[] arr) {
		if (arr.length == 0) {
			sb.append("[]");
			return this;
		}
		sb.append("[");
		for (int i = 0; i < arr.length; i++) {
			if (i != 0) {
				sb.append(", ");
			}
			add(arr[i]);
		}
		sb.append("]");
		return this;
	}

	public JSONizer  add(long[] arr) {
		if (arr.length == 0) {
			sb.append("[]");
			return this;
		}
		sb.append("[");
		for (int i = 0; i < arr.length; i++) {
			if (i != 0) {
				sb.append(", ");
			}
			add(arr[i]);
		}
		sb.append("]");
		return this;
	}

	public interface SimpleCollection {}

	public interface SimpleArray extends SimpleCollection {
		int       size();
		Object    get(int idx);
	}

	public JSONizer  add(SimpleArray arr) {
		int length = arr.size();
		sb.append('[');
		for (int i = 0; i < length; i++) {
			if (i != 0) {
				sb.append(", ");
			}
			Object val = arr.get(i);
			if (val != null) {
				add(val);
			}
		}
		sb.append(']');
		return this;
	}

	public JSONizer  add(Collection c) {
		sb.append('[');
		boolean firstItem = true;
		Iterator i = c.iterator();
		while (i.hasNext()) {
			if (!firstItem) {
				sb.append(", ");
			} else {
				firstItem = false;
			}
			add(i.next());
		}
		sb.append(']');
		return this;
	}

	public interface SimpleMap extends SimpleCollection {
		boolean   hasNext();
		void      next();
		Object    currentKey();
		Object    currentValue();
	}

	public JSONizer  add(SimpleMap map) {
		sb.append('{');

		boolean firstEntry = true;
		while (map.hasNext()) {
			map.next();
			Object keyObj = map.currentKey();
			if (!(keyObj instanceof String)) {
				throw new IllegalArgumentException("Map key has non-String value: " + keyObj);
			}
			Object valObj = map.currentValue();
			if (valObj == null) {
				continue;
			}

			if (firstEntry) {
				firstEntry = false;
			}
			else {
				sb.append(", ");
			}
			add((String) keyObj);
			sb.append(':');
			add(valObj);
		}
		sb.append('}');
		return this;
	}

	public JSONizer  add(final Map map) {
		return add(new SimpleMap() {
			Iterator  entries = map.entrySet().iterator();
			Map.Entry currentEntry;

			@Override
			public boolean hasNext() {
				currentEntry = null;
				return entries.hasNext();
			}

			@Override
			public void next() {
				currentEntry = (Map.Entry) entries.next();
			}

			@Override
			public Object currentKey() {
				return currentEntry.getKey();
			}

			@Override
			public Object currentValue() {
				return currentEntry.getValue();
			}
		});
	}


	public JSONizer  add(Object obj) {
		if (obj instanceof String) {
			return add((String)obj);
		}
		if (obj instanceof Boolean || obj instanceof Number) {
			sb.append('"').append(obj.toString()).append('"');
			return this;
		}
		if (obj instanceof Object[]) {
			return add((Object[])obj);
		}
		if (obj instanceof int[]) {
			return add((int[])obj);
		}
		if (obj instanceof long[]) {
			return add((long[])obj);
		}
		if (obj instanceof Map) {
			return add((Map)obj);
		}
		if (obj instanceof Collection) {
			return add((Collection)obj);
		}

		if (obj instanceof JSONizable) {
			if (obj instanceof JSONizableAsSimpleArray) {
				return add(((JSONizableAsSimpleArray) obj).toSimpleArray());
			}
			if (obj instanceof JSONizableAsSimpleMap) {
				return add(((JSONizableAsSimpleMap)obj).toSimpleMap());
			}
			assert false : "JSONizer.add for this type of JSONizable " + obj.getClass()
					       + "not implemented yet; consider implementing it!";
			return this;
		}

		if (obj instanceof SimpleCollection) {
			if (obj instanceof SimpleArray) {
				return add((SimpleArray) obj);
			}
			if (obj instanceof SimpleMap) {
				return add((SimpleMap) obj);
			}
			assert false : "JSONizer.add for this type of SimpleCollection " + obj.getClass()
					       + "not implemented yet; consider implementing it!";
			return this;
		}

		if (obj instanceof long[] || obj instanceof double[]) {
			assert false : "JSONizer.add for type " + obj.getClass()
					                            + "not implemented yet; consider implementing it!";
			return this;
		}

		return this;
	}

	public static interface JSONizable {

	}

	public static interface JSONizableAsSimpleMap extends JSONizable {
		SimpleMap  toSimpleMap();
	}

	public static interface JSONizableAsSimpleArray extends JSONizable {
		SimpleArray  toSimpleArray();
	}

	public static class PatternAsSimpleArray implements SimpleArray {
		private final Pattern p;

		public PatternAsSimpleArray(Pattern p) {
			this.p = p;
		}

		@Override
		public int size() {
			return (p != null ? 2 : 0);
		}

		@Override
		public Object get(int idx) {
			assert p != null;
			switch (idx) {
			case 0: return p.pattern();
			case 1: return p.flags();
			}
			assert false: "Illegal idx: " + idx;
			return null;
		}
	}
}
