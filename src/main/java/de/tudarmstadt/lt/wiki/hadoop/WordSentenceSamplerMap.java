package de.tudarmstadt.lt.wiki.hadoop;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.log4j.Logger;

class WordSentenceSamplerMap extends Mapper<LongWritable, Text, Text, Text> {
		Logger log = Logger.getLogger("de.tudarmstadt.lt.wiki");
		
		@Override
		public void map(LongWritable key, Text value, Context context)
			throws IOException, InterruptedException {
			try {
				String valueParts[] = value.toString().split("\t");
//				String text = valueParts[0];
				String linkRefs = valueParts[1];
				// it is important that this is a set, otherwise words that
				// appear twice in the same sentence will add this sentence twice
				Set<String> linkTexts = new HashSet<String>();
				String links[] = linkRefs.split("  ");
				for (String link : links) {
					context.getCounter("de.tudarmstadt.lt.wiki", "NUM_LINKS").increment(1);
					String linkParts[] = link.split("@");
					String lemma = linkParts[0];
					linkTexts.add(lemma);
				}
				
				for (String linkText : linkTexts) {
					context.write(new Text(linkText), value);
				}
			} catch (Exception e) {
				log.error("Can't process line: " + value.toString(), e);
				context.getCounter("de.tudarmstadt.lt.wiki", "NUM_MAP_ERRORS").increment(1);
			}
		}
	}