package de.tudarmstadt.lt.util;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class WikiUtilTest {

	@Test
	public void testGetLinkedResource() {
		Map<String, String> redirects = new HashMap<String, String>();
		redirects.put("a", "foo#1");
		redirects.put("b", "bar");
		
		assertEquals("foo#1", WikiUtil.getLinkedResource(redirects, "a", true));
		assertEquals("foo", WikiUtil.getLinkedResource(redirects, "a", false));
		assertEquals("bar", WikiUtil.getLinkedResource(redirects, "b", true));
		assertEquals("bar", WikiUtil.getLinkedResource(redirects, "b", false));
		assertEquals("foo#2", WikiUtil.getLinkedResource(redirects, "a#2", true));
		assertEquals("foo", WikiUtil.getLinkedResource(redirects, "a#2", false));
		assertEquals("foo", WikiUtil.getLinkedResource(redirects, "a", false));
		assertEquals("bar#2", WikiUtil.getLinkedResource(redirects, "b#2", true));
		assertEquals("bar", WikiUtil.getLinkedResource(redirects, "b#2", false));
		assertEquals("bar", WikiUtil.getLinkedResource(redirects, "b", false));
	}

}
