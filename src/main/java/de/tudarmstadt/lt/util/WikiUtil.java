package de.tudarmstadt.lt.util;

import java.util.Map;

public class WikiUtil {
	/**
	 * Maps a wiki link target to a resource name, e.g.
	 * <code>USA#History</code> -> <code>United_States</code>
	 * 
	 * @param keepSubsection Whether to keep subsection of the target (<code>#History</code> in the example above)
	 */
	public static String getLinkedResource(Map<String, String> redirects, String target, boolean keepSubsection) {
		int startIndex = target.indexOf('#');
		String subsection = "";
		if (startIndex >= 0) {
			subsection = target.substring(startIndex);
			target = target.substring(0, startIndex);
		}
		String redirectedTarget = redirects.get(target);
		if (redirectedTarget != null) {
			target = redirectedTarget;
			// "a#b" with redirect "a" -> "c#d" will redirect to "c#b" (not "c#b#d" or "c#d#b", i.e. delete "d" if "b" is not empty)
			if (!keepSubsection || !subsection.isEmpty()) {
				startIndex = target.indexOf('#');
				if (startIndex >= 0) {
					target = target.substring(0, startIndex);
				}
			}
		}
		if (keepSubsection) {
			target += subsection;
		}
		return target;
	}

	public static String getLinkedResource(Map<String, String> redirects, String target) {
		return getLinkedResource(redirects, target, false);
	}
}
