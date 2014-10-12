package de.tudarmstadt.lt.wiki.hadoop;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Logger;

import de.tudarmstadt.lt.util.FibonacciHeap;

public class WordSentenceSampler extends Configured implements Tool {
	// Maps e.g. "a b c bla de f", "abc@0:5  def@10:14" to
	// ("a b c", "de f") and ("a b c@@abc" and "de f@@def"(
	private static void getLinks(String text, String linkRefs, Collection<String> textsWithLink, Collection<String> textsWithoutLink) {
		String links[] = linkRefs.split("  ");
		for (String link : links) {
			String linkParts[] = link.split("@");
			String target = linkParts[0];
			String startEnd[] = linkParts[1].split(":");
			int start = Integer.parseInt(startEnd[0]);
			int end = Integer.parseInt(startEnd[1]);
			String surfaceForm = text.substring(start, end);
			
			if (textsWithLink != null) {
				textsWithLink.add(surfaceForm + "@@" + target);
			}
			if (textsWithoutLink != null) {
				textsWithoutLink.add(surfaceForm);
			}
		}
	}
	
	private static class WordSentenceSamplerMap extends Mapper<LongWritable, Text, Text, Text> {
		Logger log = Logger.getLogger("de.tudarmstadt.lt.wiki");
		
		@Override
		public void map(LongWritable key, Text value, Context context)
			throws IOException, InterruptedException {
			try {
				String valueParts[] = value.toString().split("\t");
				String text = valueParts[0];
				String linkRefs = valueParts[1];
				Collection<String> textsWithoutLink = new LinkedList<String>();
				getLinks(text, linkRefs, null, textsWithoutLink);
				for (String linkText : textsWithoutLink) {
					context.write(new Text(linkText), value);
				}
			} catch (Exception e) {
				log.error("Can't process line: " + value.toString(), e);
			}
		}
	}

	private static class WordSentenceSamplerReduce extends Reducer<Text, Text, Text, Text> {
		Logger log = Logger.getLogger("de.tudarmstadt.lt.wiki");
		
		final static int MAX_WORD_SAMPLES = 10;
		final static int MAX_WORD_SENSE_SAMPLES = 3;
		
		@Override
		public void reduce(Text key, Iterable<Text> values, Context context)
			throws IOException, InterruptedException {
			String word = key.toString();
			Random r = new Random();
			FibonacciHeap<String> wordSampleSentences = new FibonacciHeap<String>();
			Map<String, FibonacciHeap<String>> wordSenseSampleSentences = new HashMap<String, FibonacciHeap<String>>();
			for (Text value : values) {
				try {
					String valueParts[] = value.toString().split("\t");
					String text = valueParts[0];
					String linkRefs = valueParts[1];
					Collection<String> textsWithLink = new LinkedList<String>();
					getLinks(text, linkRefs, textsWithLink, null);
					for (String linkText : textsWithLink) {
						String _word = linkText.split("@@")[0];
						if (_word.equals(word)) {
							FibonacciHeap<String> sentences = wordSenseSampleSentences.get(linkText);
							if (sentences == null) {
								sentences = new FibonacciHeap<String>();
								wordSenseSampleSentences.put(linkText, sentences);
							}
							sentences.enqueue(text, r.nextDouble());
						}
					}
					wordSampleSentences.enqueue(text, r.nextDouble());
				} catch (Exception e) {
					log.error("Can't process line: " + value.toString(), e);
				}

				while (wordSampleSentences.size() > MAX_WORD_SAMPLES) {
					wordSampleSentences.dequeueMin();
				}
				for (Entry<String, FibonacciHeap<String>> entry : wordSenseSampleSentences.entrySet()) {
					FibonacciHeap<String> sentences = entry.getValue();
					while (sentences.size() > MAX_WORD_SENSE_SAMPLES) {
						sentences.dequeueMin();
					}
				}
			}
			
			while (!wordSampleSentences.isEmpty()) {
				context.write(key, new Text(wordSampleSentences.dequeueMin().getValue()));
			}
			for (Entry<String, FibonacciHeap<String>> entry : wordSenseSampleSentences.entrySet()) {
				FibonacciHeap<String> sentences = entry.getValue();
				while (!sentences.isEmpty()) {
					context.write(new Text(entry.getKey()), new Text(sentences.dequeueMin().getValue()));
				}
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
//		job.setInputFormatClass(TextInputFormat.class);
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