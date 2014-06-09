package util;

import java.util.Map;

public class WikiHelper {
	public static String getLinkedResource(String target, Map<String, String> redirects) {
		int startIndex = target.indexOf('#');
		String subsection = "";
		if (startIndex >= 0) {
			subsection = target.substring(startIndex);
			target = target.substring(0, startIndex);
		}
		String redirectedTarget = redirects.get(target);
		if (redirectedTarget != null)
			return target = redirectedTarget;
		target += subsection;
		return target;
	}
	
	public static String formatResourceName(String resource) {
		return resource.replace(' ', '_');
	}
}
