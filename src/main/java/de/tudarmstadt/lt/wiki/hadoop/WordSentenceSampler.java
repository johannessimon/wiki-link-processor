package de.tudarmstadt.lt.wiki.hadoop;
import java.util.Arrays;
import java.util.Collection;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

public class WordSentenceSampler extends Configured implements Tool {
	// Maps e.g. "a b c bla de f", "abc@0:5  def@10:14" to
	// ("a b c", "de f") and ("a b c@@abc" and "de f@@def")
	static void getLinks(String text, String linkRefs, Collection<String> linkTextsWithTarget, Collection<String> linkTexts) {
		String links[] = linkRefs.split("  ");
		for (String link : links) {
			// link is e.g. "tablet@@Tablet_computer@10:16"
			String linkParts[] = link.split("@");
			String surfaceForm = linkParts[0];
			String target = linkParts[linkParts.length-2];
			
			if (linkTextsWithTarget != null) {
				linkTextsWithTarget.add(surfaceForm + "@@" + target);
			}
			if (linkTexts != null) {
				linkTexts.add(surfaceForm);
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
		job.setJarByClass(WordSentenceSampler.class);
		FileInputFormat.addInputPath(job, new Path(inDir));
		FileOutputFormat.setOutputPath(job, new Path(_outDir));
		job.setMapperClass(WordSentenceSamplerMap.class);
		job.setReducerClass(WordSentenceSamplerReduce.class);
		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(Text.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);
		job.setJobName("WikiLinkProcessor:WordSentenceSampler");
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
		int res = ToolRunner.run(conf, new WordSentenceSampler(), args);
		System.exit(res);
	}
}