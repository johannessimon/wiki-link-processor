package de.tudarmstadt.lt.wiki.hadoop;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.log4j.Logger;

class WikiLinkFilterMap extends Mapper<LongWritable, Text, Text, Text> {
	Logger log = Logger.getLogger("de.tudarmstadt.lt.wiki");
	Set<String> lemmaWhitelist;
	// avoid garbage collection to slow down filtering
	List<String> linksToKeep = new ArrayList<String>(1000);
	
	@Override
	public void setup(Context context) throws IOException {
		Configuration conf = context.getConfiguration();
		String lemmaWhitelistArr[] = conf.get("wiki.filter.words").split(",");
		lemmaWhitelist = new HashSet<String>();
		for(String lemma : lemmaWhitelistArr) {
			lemmaWhitelist.add(lemma.trim());
		}
	}
	
	@Override
	public void map(LongWritable key, Text value, Context context)
		throws IOException, InterruptedException {
		String valueParts[] = value.toString().split("\t");
		String text = valueParts[0];
		String links[] = valueParts[1].split("  ");
		linksToKeep.clear();
		for (String link : links) {
			String linkParts[] = link.split("@@");
			String lemma = linkParts[0];
			if (lemmaWhitelist.contains(lemma)) {
				linksToKeep.add(link);
			}
		}
		if (!linksToKeep.isEmpty()) {
			context.write(new Text(text), new Text(StringUtils.join(linksToKeep, "  ")));
		}
	}
}