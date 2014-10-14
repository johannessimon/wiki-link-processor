package de.tudarmstadt.lt.wiki.hadoop;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.reduce.IntSumReducer;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Logger;

import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;

public class WordCount extends Configured implements Tool {
	private static class WordCountMap extends Mapper<LongWritable, Text, Text, IntWritable> {
		Logger log = Logger.getLogger("de.tudarmstadt.lt.wiki");
		StanfordCoreNLP pipeline;
		
		@Override
		public void setup(Context context) {
			Properties props = new Properties();
			props.put("annotators", "tokenize, ssplit, pos, lemma");
			pipeline = new StanfordCoreNLP(props);
		}
		
		@Override
		public void map(LongWritable key, Text value, Context context)
			throws IOException, InterruptedException {
			try {
				String valueParts[] = value.toString().split("\t");
				String text = valueParts[0];
				Annotation document = new Annotation(text);
				pipeline.annotate(document);
				List<CoreLabel> tokens = document.get(TokensAnnotation.class);
				String linkRefs = valueParts[1];
				String links[] = linkRefs.split("  ");
				for (String link : links) {
					String linkParts[] = link.split("@");
					String startEnd[] = linkParts[1].split(":");
					int start = Integer.parseInt(startEnd[0]);
					int end = Integer.parseInt(startEnd[1]);
					for (CoreLabel token : tokens) {
						if (token.beginPosition() == start &&
							token.endPosition() == end &&
							(token.tag().equals("NN") ||
							 token.tag().equals("NNS"))) {
							context.write(new Text(token.lemma()), new IntWritable(1));
							break;
						}
					}
				}
			} catch (Exception e) {
				log.error("Can't process line: " + value.toString(), e);
			}
		}
	}

	public boolean runJob(String inDir, String outDir) throws Exception {
		Configuration conf = getConf();
		FileSystem fs = FileSystem.get(conf);
		String _outDir = outDir;
		int outDirSuffix = 1;
		while (fs.exists(new Path(_outDir))) {
			_outDir = outDir + outDirSuffix;
			outDirSuffix++;
		}
		conf.setBoolean("mapred.output.compress", true);
		conf.set("mapred.output.compression.codec", "org.apache.hadoop.io.compress.GzipCodec");
		Job job = Job.getInstance(conf);
		job.setJarByClass(WordCount.class);
		FileInputFormat.addInputPath(job, new Path(inDir));
		FileOutputFormat.setOutputPath(job, new Path(_outDir));
		job.setMapperClass(WordCountMap.class);
		job.setReducerClass(IntSumReducer.class);
		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(IntWritable.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(IntWritable.class);
		job.setJobName("WikiLinkProcessor:WordCount");
		return job.waitForCompletion(true);
	}

	public int run(String[] args) throws Exception {
		System.out.println("args:" + Arrays.asList(args));
		if (args.length != 2) {
			System.out.println("Usage: </path/to/wiki-links> </path/to/output>");
			System.exit(1);
		}
		String inDir = args[0];
		String outDir = args[1];
		boolean success = runJob(inDir, outDir);
		return success ? 0 : 1;
	}

	public static void main(final String[] args) throws Exception {
		Configuration conf = new Configuration();
		int res = ToolRunner.run(conf, new WordCount(), args);
		System.exit(res);
	}
}