package de.tudarmstadt.lt.wiki.hadoop;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

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

import de.tudarmstadt.lt.util.MapUtil;

public class SurfaceFormDictionaryWordCount extends Configured implements Tool {
	private static class HadoopSurfaceFormDictionaryMap extends Mapper<LongWritable, Text, Text, Text> {
		@Override
		public void map(LongWritable key, Text value, Context context)
			throws IOException, InterruptedException {
			try {
				String[] cols = value.toString().split("\t");
				String word = cols[0].split("@@")[0];
				String target = cols[0].split("@@")[1];
				String count = cols[1];
				
				context.write(new Text(word), new Text(target + ":" + count));
			} catch (Exception e) {
				e.printStackTrace();
				context.getCounter("de.tudarmstadt.lt.wiki.hadoop", "MALFORMATTED_MAP_INPUTS").increment(1);
			}
		}
	}

	private static class WikiSenseDictionaryReduce extends Reducer<Text, Text, Text, Text> {
		@Override
		public void reduce(Text word, Iterable<Text> targetCounts, Context context)
			throws IOException, InterruptedException {
			int numSenses = 0;
			int totalCount = 0;
			Map<String, Integer> targetCountMap = new HashMap<String, Integer>();
			for (Text targetCount : targetCounts) {
				String tc = targetCount.toString();
				String target = tc.substring(0, tc.lastIndexOf(":"));
				int count = Integer.parseInt(tc.substring(tc.lastIndexOf(":") + 1));
				targetCountMap.put(target, count);
				totalCount += count;
				numSenses++;
			}
			
			Map<String, Integer> targetCountMapSorted = MapUtil.sortMapByValue(targetCountMap);
			String targetCountsString = "";
			for (Entry<String, Integer> targetCount : targetCountMapSorted.entrySet()) {
				targetCountsString += targetCount.getKey() + ":" + targetCount.getValue() + "  ";
			}
			
			context.write(word, new Text(totalCount + "\t" + numSenses + "\t" + targetCountsString));
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
		job.setJarByClass(SurfaceFormDictionaryWordCount.class);
		FileInputFormat.addInputPath(job, new Path(inDir));
		FileOutputFormat.setOutputPath(job, new Path(_outDir));
		job.setMapperClass(HadoopSurfaceFormDictionaryMap.class);
		job.setReducerClass(WikiSenseDictionaryReduce.class);
		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(Text.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);
		job.setJobName("WikiLinkProcessor:SurfaceFormDictionaryWordCount");
		return job.waitForCompletion(true);
	}

	public int run(String[] args) throws Exception {
		System.out.println("args:" + Arrays.asList(args));
		if (args.length != 2) {
			System.out.println("Usage: </path/to/surface-form-dict> </path/to/output>");
			System.exit(1);
		}
		String inDir = args[0];
		String outDir = args[1];
		boolean success = runJob(inDir, outDir);
		return success ? 0 : 1;
	}

	public static void main(final String[] args) throws Exception {
		Configuration conf = new Configuration();
		int res = ToolRunner.run(conf, new SurfaceFormDictionaryWordCount(), args);
		System.exit(res);
	}
}