package de.tudarmstadt.lt.wiki.hadoop;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.lib.MultipleTextOutputFormat;

public class MultiFileOutputFormat extends MultipleTextOutputFormat<Text, Text> {
	@Override
	protected String generateFileNameForKeyValue(Text key, Text value, String name) {
		return key.toString() + "/" + name;
	}

	@Override
	protected Text generateActualKey(Text key, Text value) {
		return null;
	}
}
