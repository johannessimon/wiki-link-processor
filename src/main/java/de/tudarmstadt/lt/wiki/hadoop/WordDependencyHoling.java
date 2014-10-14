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

import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.CoreMap;

public class WordDependencyHoling extends Configured implements Tool {
	private static class WordDependencyHolingMap extends Mapper<LongWritable, Text, Text, IntWritable> {
		Logger log = Logger.getLogger("de.tudarmstadt.lt.wiki");
		StanfordCoreNLP pipeline;
		
		@Override
		public void setup(Context context) {
			Properties props = new Properties();
			props.put("annotators", "tokenize, ssplit, pos, lemma, parse");
			pipeline = new StanfordCoreNLP(props);
		}
		
		@Override
		public void map(LongWritable key, Text value, Context context)
			throws IOException, InterruptedException {
			try {
				String text = value.toString();
				Annotation document = new Annotation(text);
				pipeline.annotate(document);
				List<CoreMap> sentences = document.get(SentencesAnnotation.class);
				for (CoreMap sentence : sentences) {
					SemanticGraph dependencies = sentence.get(CollapsedDependenciesAnnotation.class);
					for (SemanticGraphEdge dep : dependencies.edgeIterable()) {
						IndexedWord source = dep.getSource();
						IndexedWord target = dep.getTarget();
						String rel = dep.getRelation().getShortName();
						if (dep.getRelation().getSpecific() != null) {
							rel += "_" + dep.getRelation().getSpecific();
						}
						if (source.tag().equals("NN") || source.tag().equals("NNS")) {
							context.write(
									new Text(source.lemma() + "\t" + rel + "(@@," + target.lemma() + ")"),
									new IntWritable(1));
						}
						if (target.tag().equals("NN") || target.tag().equals("NNS")) {
							context.write(
									new Text(target.lemma() + "\t" + rel + "(" + source.lemma() + ", @@)"),
									new IntWritable(1));
						}
					}
				}
				
			} catch (Exception e) {
				log.error("Can't process line: " + value.toString(), e);
				context.getCounter("de.tudarmstadt.lt.wiki", "NUM_MAP_ERRORS").increment(1);
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
		job.setJarByClass(WordDependencyHoling.class);
		FileInputFormat.addInputPath(job, new Path(inDir));
		FileOutputFormat.setOutputPath(job, new Path(_outDir));
		job.setMapperClass(WordDependencyHolingMap.class);
		job.setReducerClass(IntSumReducer.class);
		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(IntWritable.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(IntWritable.class);
		job.setJobName("WikiLinkProcessor:WordDependencyHoling");
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
		int res = ToolRunner.run(conf, new WordDependencyHoling(), args);
		System.exit(res);
	}
}