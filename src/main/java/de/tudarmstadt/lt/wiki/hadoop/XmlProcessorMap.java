package de.tudarmstadt.lt.wiki.hadoop;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.apache.log4j.Logger;

import de.tudarmstadt.lt.util.WikiUtil;
import de.tudarmstadt.lt.wiki.WikiProcessor;
import de.tudarmstadt.lt.wiki.WikiProcessor.WikiXmlRecord;

public class XmlProcessorMap extends Mapper<LongWritable, Text, Text, Text> {
	private MultipleOutputs<Text, Text> mos;
	private WikiProcessor p;
	private int maxSentenceLength;
	private int maxPageLength;
	private int maxNumLinksPerPage;
	private int maxNumImplicitLinksPerPage;
	
	Logger log = Logger.getLogger("de.tudarmstadt.lt.wiki");
	
	@Override
	public void setup(Context context) {
		mos = new MultipleOutputs<Text, Text>(context);
		p = new WikiProcessor();
		Configuration conf = context.getConfiguration();
		maxSentenceLength = conf.getInt("wiki.sentence.maxlength", 1_000);
		maxPageLength = conf.getInt("wiki.page.maxlength", 1_000_000);
		maxNumLinksPerPage = conf.getInt("wiki.links.maxperpage", 1_000);
		maxNumImplicitLinksPerPage = conf.getInt("wiki.implicitlinks.maxperpage", 10_000);
		log.info("Max sentence length is " + maxSentenceLength);
		log.info("Max page length is " + maxPageLength);
		log.info("Max num links per page is " + maxNumLinksPerPage);
	}
	
	@Override
	public void cleanup(Context context) {
		try {
			mos.close();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void map(LongWritable key, Text value, Context context)
		throws IOException, InterruptedException {
		String xml = value.toString();
		
		WikiXmlRecord record = p.parseXml(xml);
		if (record == null) {
			return;
		}
			
		String pageTitle = WikiUtil.formatResourceName(record.title);
		Text pageTitleText = new Text(pageTitle);
		mos.write("pages", pageTitleText, NullWritable.get());

		if (record.redirect != null) {
			mos.write("redirects", pageTitleText, new Text(WikiUtil.formatResourceName(record.redirect)));
		}
		
		if (record.text != null) {
			if (record.text.length() <= maxPageLength) {
				List<String> sentences = new LinkedList<String>();
				Map<Integer, List<String>> sentenceLinks = new HashMap<Integer, List<String>>();
				Map<Integer, List<String>> implicitSentenceLinks = new HashMap<Integer, List<String>>();
				p.parse(record.text, sentences, sentenceLinks, implicitSentenceLinks);
				int sIndex = 0;
				for (String sentence : sentences) {
					if (sentence.length() <= maxSentenceLength) {
						Text sentenceText = new Text(sentence);
						mos.write("sentences", sentenceText, NullWritable.get());
						
						List<String> links = new LinkedList<String>();
						List<String> explicitLinks = sentenceLinks.get(sIndex);
						if (explicitLinks != null) {
							links.addAll(explicitLinks);
						}
						if (!links.isEmpty()) {
							if (links.size() < maxNumLinksPerPage) {
								mos.write("links", sentenceText, new Text(StringUtils.join(links, "  ")));
							} else {
								log.error("(" + pageTitle + ") too many links: " + links.subList(0, 100));
							}
						}
						
						List<String> implicitLinks = implicitSentenceLinks.get(sIndex);
						if (implicitLinks != null) {
							links.addAll(implicitLinks);
						}
						if (!links.isEmpty()) {
							if (links.size() < maxNumImplicitLinksPerPage) {
								mos.write("implicitlinks", sentenceText, new Text(StringUtils.join(links, "  ")));
							} else {
								log.error("(" + pageTitle + ") too many implicit links: " + links.subList(0, 100));
							}
						}
						sIndex++;
					} else {
						log.info("(" + pageTitle + ") Skipping sentence of length " + sentence.length() + " as it is too long.");
					}
				}
			} else {
				log.info("(" + pageTitle + ") Skipping page of length " + record.text.length() + " as it is too long.");
			}
		}
	}
}