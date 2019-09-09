package com.ontotext.trree.plugin.lucene;


import java.util.*;

/**
 * Class JSONparser is used to reconstruct the tree structure that was previously
 * serialized into JSON format.
 * See its complement JSONizer
 * User: stenlee
 * Date: 2/3/12
 */
public class JSONparser {

	public static class Exception extends java.lang.Exception {
		public final String  src;
		public final int     pos;

		public Exception(String msg, String src, int pos) {
			super(msg);
			this.src = src;
			this.pos = pos;
		}

		public Exception(String msg) {
			super(msg);
			src = null;
			pos = 0;
		}
	}

	public static Val  parse(String src) throws Exception {
		ValVal res = new ValVal();
		int  p = parseVal(src, 0, res);
		return res.val;
	}

	private static int  skipSpace(String src, final int pos) throws Exception {
		int p = pos;
		try {
			while (Character.isSpaceChar(src.charAt(p))) {
				p++;
			}
			return p;
		} catch (StringIndexOutOfBoundsException exn) {
			throw new Exception("String contains only white space", src, pos);
		}
	}

	private static class ValVal {
		Val val;
	}

	private static int  parseVal(String src, final int pos, ValVal res) throws Exception {
		int  p = skipSpace(src, pos);
		char c = src.charAt(p++);
		if (c == '[') {
			ArrayList<Val> vals = new ArrayList<Val>();
			int idx = 0;
			boolean afterVal = false;
			while (true) {
				p = skipSpace(src, p); // skip the '[' or the ','
				c = src.charAt(p++);
				if (c == ']') {
					break;
				}
				if (c == ',') {
					if (afterVal) {
						afterVal = false;
					} else {
						vals.add(idx++, NoVal.VAL);
					}
				} else if (!afterVal) {
					p = parseVal(src, p-1, res);
					vals.add(idx++, res.val);
					afterVal = true;
				} else {
					throw new Exception("Expected comma or closing bracket", src, p-1);
				}
			}
			res.val = new ArrVal(vals);
			return p; // skip the ']'
		}

		StringBuilder sb = new StringBuilder();

		if (c == '{') {
			HashMap<String, Val> vals = new HashMap<String, Val>();
			while (true) {
				p = skipSpace(src, p); // skip the '{' or the ','
				c = src.charAt(p++);
				if (c == '}') {
					break;
				}
				if (c != '"') {
					throw new Exception("Expected quoted name", src, p-1);
				}
				sb.delete(0, sb.length());
				p = parseStr(src, p, sb);
				p = skipSpace(src, p);
				c = src.charAt(p++);
				if (c != ':') {
					throw new Exception("Expected semicolon", src, p-1);
				}
				p = parseVal(src, p, res);
				vals.put(sb.toString(), res.val);

				p = skipSpace(src, p);
				c = src.charAt(p++);
				if (c == ',') {
					continue;
				}
				if (c == '}') {
					break;
				}
				throw new Exception("Expected comma or closing brace", src, p-1);
			}
			res.val = new MapVal(vals);
			return p;
		}

		if (c == '"') {
			p = parseStr(src, p, sb);
			assert src.charAt(p-1) == '"';
			res.val = new StrVal(sb.toString());
			return p;
		}

		throw new Exception("Expected some value", src, p-1);
	}

	/** Parses a StrVal (removing the quotes and processing any encountered escape sequences)
	 *  @param pos  1 + the index of the opening quotes
	 *  @return 1 + the index of the closing quotes
	 */
	private static int  parseStr(String src, final int pos, StringBuilder sb) throws Exception {
		assert src.charAt(pos-1) == '"';
		int p = pos;

		try {
			while (true) {
				char c = src.charAt(p++);
				if (c == '\\') {
					c = src.charAt(p++);
					switch (c) {
					case 'n': c = '\n'; break;
					case 't': c = '\t'; break;
					}
				} else if (c == '"') {
					return p;
				}
				sb.append(c);
			}
		} catch (StringIndexOutOfBoundsException exn) {
			throw new Exception("Ill-terminated string", src, pos);
		}
	}

	public static final int NOVAL = 0, STR = 1, MAP = 2, ARR = 3;

	public abstract static class Val {
		public final int type;

		protected Val(int t) {
			type = t;
		}

		public int  asInt() throws Exception {
			try {
				return Integer.parseInt(((StrVal) this).val);
			} catch (RuntimeException exn) {
				throw new Exception("Expected int, found " + this.toString());
			}
		}

		public long  asLong() throws Exception {
			try {
				return Long.parseLong(((StrVal) this).val);
			} catch (RuntimeException exn) {
				throw new Exception("Expected long, found " + this.toString());
			}
		}

		public String asString() throws Exception {
			try {
				return ((StrVal) this).val;
			} catch (RuntimeException exn) {
				throw new Exception("Expected String, found " + getClass().getCanonicalName());
			}
		}

		public boolean asBoolean() throws Exception {
			try {
				String str = ((StrVal) this).val;
				if (str.equals("true")) {
					return true;
				}
				if (str.equals("false")) {
					return false;
				}
				throw new Exception("Expected 'true' or 'false', found '" + str + "'");
			} catch (RuntimeException exn) {
				throw new Exception("Expected boolean");
			}
		}

		public MapVal asMap() throws Exception {
			try {
				return (MapVal) this;
			} catch (RuntimeException exn) {
				throw new Exception("Expected {...}, found " + getClass().getCanonicalName());
			}
		}

		public ArrVal asArr() throws Exception {
			try {
				return (ArrVal) this;
			} catch (RuntimeException exn) {
				throw new Exception("Expected [...], found " + getClass().getCanonicalName());
			}
		}
	}

	public static class NoVal extends Val {

		private NoVal() {
			super(NOVAL);
		}

		public static final NoVal  VAL = new NoVal();
	}

	public static class StrVal extends Val {
		public final String val;

		StrVal(String v) {
			super(STR);
			val = v;
		}
	}

	public static class MapVal extends Val {
		public final Map<String, Val> vals;

		MapVal(Map<String, Val> vals) {
			super(MAP);
			this.vals = vals;
		}

		public int  size() {
			return vals.size();
		}

		public Set<String>  keys() {
			return vals.keySet();
		}

		public Val  get(String key) {
			Val res = vals.get(key);
			return res != null ? res : NoVal.VAL;
		}
	}

	public static class ArrVal extends Val {
		public final List<Val> vals;

		ArrVal(List<Val> vals) {
			super(ARR);
			this.vals = vals;
		}

		public int  size() {
			return vals.size();
		}

		public Val  get(int idx) {
			Val res = vals.get(idx);
			return res != null ? res : NoVal.VAL;
		}

		public int[]  asIntArray() throws Exception {
			int[] res = new int[vals.size()];
			for (int idx = 0; idx < res.length; idx++) {
				res[idx] = vals.get(idx).asInt();
			}
			return res;
		}

		public long[]  asLongArray() throws Exception {
			long[] res = new long[vals.size()];
			for (int idx = 0; idx < res.length; idx++) {
				res[idx] = vals.get(idx).asLong();
			}
			return res;
		}
	}
}
