package de.tudarmstadt.lt.wiki.hadoop;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.log4j.Logger;

import de.tudarmstadt.lt.util.FibonacciHeap;
import de.tudarmstadt.lt.util.MapUtil;

class WordSentenceSamplerReduce extends Reducer<Text, Text, Text, Text> {
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
				WordSentenceSampler.getLinks(text, linkRefs, linkTextsWithTarget, null);
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
				context.getCounter("de.tudarmstadt.lt.wiki", "NUM_REDUCE_ERRORS").increment(1);
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