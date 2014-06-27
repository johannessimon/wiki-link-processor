package de.tudarmstadt.lt.wiki.hadoop;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;

import de.tudarmstadt.lt.util.WikiUtil;
import de.tudarmstadt.lt.wiki.WikiProcessor;
import de.tudarmstadt.lt.wiki.WikiProcessor.WikiXmlRecord;

public class HadoopWikiXmlProcessorMap extends Mapper<LongWritable, Text, Text, Text> {
	private MultipleOutputs<Text, Text> mos;
	private WikiProcessor p;
	
	@Override
	public void setup(Context context) {
		mos = new MultipleOutputs<Text, Text>(context);
		p = new WikiProcessor();
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
			List<String> sentences = new LinkedList<String>();
			Map<Integer, List<String>> sentenceLinks = new HashMap<Integer, List<String>>();
			p.parse(record.text, sentences, sentenceLinks);
			int sIndex = 0;
			for (String sentence : sentences) {
				Text sentenceText = new Text(sentence);
				mos.write("sentences", sentenceText, NullWritable.get());
				
				List<String> links = sentenceLinks.get(sIndex);
				if (links != null) {
					mos.write("links", sentenceText, new Text(StringUtils.join(links, "  ")));
				}
				sIndex++;
			}
		}
	}
}