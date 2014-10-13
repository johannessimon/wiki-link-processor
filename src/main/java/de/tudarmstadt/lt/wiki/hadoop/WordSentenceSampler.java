package de.tudarmstadt.lt.wiki.hadoop;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

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
import de.tudarmstadt.lt.util.MapUtil;

public class WordSentenceSampler extends Configured implements Tool {
	// Maps e.g. "a b c bla de f", "abc@0:5  def@10:14" to
	// ("a b c", "de f") and ("a b c@@abc" and "de f@@def"(
	private static void getLinks(String text, String linkRefs, Collection<String> linkTextsWithTarget, Collection<String> linkTexts) {
		String links[] = linkRefs.split("  ");
		for (String link : links) {
			String linkParts[] = link.split("@");
			String target = linkParts[0];
			String startEnd[] = linkParts[1].split(":");
			int start = Integer.parseInt(startEnd[0]);
			int end = Integer.parseInt(startEnd[1]);
			String surfaceForm = text.substring(start, end);
			
			if (linkTextsWithTarget != null) {
				linkTextsWithTarget.add(surfaceForm + "@@" + target);
			}
			if (linkTexts != null) {
				linkTexts.add(surfaceForm);
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
				// it is important that this is a set, otherwise words that
				// appear twice in the same sentence will add this sentence twice
				Set<String> linkTexts = new HashSet<String>();
				getLinks(text, linkRefs, null, linkTexts);
				for (String linkText : linkTexts) {
					if (linkText.equals("\"government failure\"")) {
						System.out.println("foo");
					}
					context.write(new Text(linkText), value);
				}
			} catch (Exception e) {
				log.error("Can't process line: " + value.toString(), e);
			}
		}
	}

	private static class WordSentenceSamplerReduce extends Reducer<Text, Text, Text, Text> {
		Logger log = Logger.getLogger("de.tudarmstadt.lt.wiki");
		
		final static int MAX_WORD_SAMPLES = 5;
		final static int MAX_WORD_SENSE_SAMPLES = 3;
		final static int MAX_WORD_SENSES = 10;
		final static int MIN_WORD_SENSES = 3;
		
		@Override
		public void reduce(Text key, Iterable<Text> values, Context context)
			throws IOException, InterruptedException {
			context.getCounter("de.tudarmstadt.lt.wiki", "NUM_LINK_TEXTS").increment(1);
			String word = key.toString();
			Random r = new Random();
			Map<String, Integer> targetCounts = new HashMap<String, Integer>();
			FibonacciHeap<String> sampleSentences = new FibonacciHeap<String>();
			Map<String, FibonacciHeap<String>> targetSampleSentences = new HashMap<String, FibonacciHeap<String>>();
			for (Text value : values) {
				try {
					String valueString = value.toString();
					String valueParts[] = valueString.split("\t");
					String text = valueParts[0];
					String linkRefs = valueParts[1];
					Collection<String> linkTextsWithTarget = new LinkedList<String>();
					getLinks(text, linkRefs, linkTextsWithTarget, null);
					for (String linkTextWithTarget : linkTextsWithTarget) {
						String[] linkTextSplits = linkTextWithTarget.split("@@");
						String linkText = linkTextSplits[0];
						String target = linkTextSplits[1];
						if (linkText.equals(word)) {
							MapUtil.addIntTo(targetCounts, target, 1);
							FibonacciHeap<String> sentences = targetSampleSentences.get(target);
							if (sentences == null) {
								sentences = new FibonacciHeap<String>();
								targetSampleSentences.put(target, sentences);
							}
							sentences.enqueue(valueString, r.nextDouble());
						}
					}
					sampleSentences.enqueue(valueString, r.nextDouble());
				} catch (Exception e) {
					log.error("Can't process line: " + value.toString(), e);
				}

				while (sampleSentences.size() >
					MAX_WORD_SAMPLES + MAX_WORD_SENSES*MAX_WORD_SENSE_SAMPLES) {
					sampleSentences.dequeueMin();
				}
				for (FibonacciHeap<String> sentences : targetSampleSentences.values()) {
					while (sentences.size() > MAX_WORD_SENSE_SAMPLES) {
						sentences.dequeueMin();
					}
				}
			}

			if (targetCounts.size() >= MIN_WORD_SENSES) {
				context.getCounter("de.tudarmstadt.lt.wiki", "NUM_POLYSEMOUS_LINK_TEXTS").increment(1);
				HashSet<String> sentencesUsed = new HashSet<String>();
				// keep only most frequent N senses
				List<String> sortedTargets = MapUtil.sortMapKeysByValue(targetCounts);
				if (sortedTargets.size() > MAX_WORD_SENSES) {
					sortedTargets = sortedTargets.subList(0, MAX_WORD_SENSES);
					context.getCounter("de.tudarmstadt.lt.wiki", "NUM_POLYSEMOUS_LINK_TEXTS_CUTOFF").increment(1);
				}
				for (String target : sortedTargets) {
					FibonacciHeap<String> sentences = targetSampleSentences.get(target);
					while (!sentences.isEmpty()) {
						String sentence = sentences.dequeueMin().getValue();
						context.write(new Text(word + "@@" + target), new Text(sentence));
						sentencesUsed.add(sentence);
					}
				}
				int samplesCollected = 0;
				while (!sampleSentences.isEmpty() &&
						samplesCollected < MAX_WORD_SAMPLES) {
					String sentence = sampleSentences.dequeueMin().getValue();
					if (!sentencesUsed.contains(sentence)) {
						context.write(key, new Text(sentence));
						samplesCollected++;
					}
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