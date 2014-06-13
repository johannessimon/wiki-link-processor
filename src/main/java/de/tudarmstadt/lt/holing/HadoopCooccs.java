package de.tudarmstadt.lt.holing;

import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.TextInputFormat;
import org.apache.hadoop.mapred.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.util.Version;


public class HadoopCooccs implements Tool {
	Configuration conf = null;
	public static class Map extends MapReduceBase implements
			Mapper<LongWritable, Text, Text, Text> {

	    private Analyzer snowball = new WatsonAnalyzer(Version.LUCENE_35); 

		public void map(LongWritable key, Text value,
				OutputCollector<Text, Text> output, Reporter reporter)
				throws IOException {
	    	String sentence = value.toString();
	        TokenStream ts = snowball.tokenStream("", new StringReader(sentence));
	        List<Text> tokens = new LinkedList<Text>();
	        while (ts.incrementToken()) {
	            OffsetAttribute oa = ts.getAttribute(OffsetAttribute.class);
	            int start = oa.startOffset(); int end = oa.endOffset();
	            tokens.add(new Text(sentence.substring(start, end)));
	        }
	        for (Text token : tokens) {
	        	for (Text token2 : tokens) {
	        		if (!token.equals(token2)) {
	        			output.collect(token, token2);
	        		}
	        	}
	        }
		}
	}

	public static void main(String[] args) throws Exception {
		JobConf conf = new JobConf();
		HadoopCooccs job = new HadoopCooccs();
		int res = ToolRunner.run(conf, job, args);
		System.exit(res);
	}

	public void setConf(Configuration conf) {
		this.conf = conf;
	}

	public Configuration getConf() {
		return conf;
	}

	public int run(String[] args) throws Exception {
		JobConf job = new JobConf(conf, HadoopCooccs.class);

		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);

		job.setMapperClass(Map.class);

		job.setInputFormat(TextInputFormat.class);
		job.setOutputFormat(TextOutputFormat.class);
		
		job.setNumReduceTasks(0);

		job.setBoolean("mapred.output.compress", true);
		job.set("mapred.output.compression.codec", "org.apache.hadoop.io.compress.GzipCodec");

		FileInputFormat.setInputPaths(job, new Path(args[0]));
		FileOutputFormat.setOutputPath(job, new Path(args[1]));

		JobClient.runJob(job);
		return 0;
	}
}
