package de.tudarmstadt.lt.util;

import java.util.HashMap;
import java.util.Map;

public class IndexUtil {
	@SuppressWarnings("rawtypes")
	private final static IdentityIndex identityIndexInstance = new IdentityIndex();
	
	public interface Index<A, B> {
		public A get(B b);
		public B getIndex(A a);
	}
	
	public static class StringIndex implements Index<String, Integer> {
		Map<Integer, String> index = new HashMap<Integer, String>();
		Map<String, Integer> rIndex = new HashMap<String, Integer>();
		
		public String get(Integer i) {
			return index.get(i);
		}
		
		public Integer getIndex(String str) {
			Integer i = rIndex.get(str);
			if (i == null) {
				i = rIndex.size();
				rIndex.put(str, i);
				index.put(i, str);
			}
			return i;
		}
	}
	
	public static class IdentityIndex<N> implements Index<N, N> {
		public N get(N b) {
			return b;
		}

		public N getIndex(N a) {
			return a;
		}
	}
	
	@SuppressWarnings("unchecked")
	public static <N> IdentityIndex<N> getIdentityIndex() {
		return identityIndexInstance;
	}
}
