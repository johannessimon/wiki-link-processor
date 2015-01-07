package de.tudarmstadt.lt.wiki.hadoop;
import java.io.IOException;
import java.util.Arrays;

import org.apache.commons.lang.StringUtils;
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

public class SurfaceFormDictionaryWordCount extends Configured implements Tool {
	private static class HadoopSurfaceFormDictionaryMap extends Mapper<LongWritable, Text, Text, Text> {
		@Override
		public void map(LongWritable key, Text value, Context context)
			throws IOException, InterruptedException {
			String word = value.toString().split("@@")[0];
			String target = value.toString().split("@@")[1];
			String count = value.toString().split("\t")[1];
			
			context.write(new Text(word), new Text(target + ":" + count));
		}
	}

	private static class WikiSenseDictionaryReduce extends Reducer<Text, Text, Text, Text> {
		@Override
		public void reduce(Text word, Iterable<Text> targetCounts, Context context)
			throws IOException, InterruptedException {
			int numSenses = 0;
			int totalCount = 0;
			for (Text targetCount : targetCounts) {
				int count = Integer.parseInt(targetCount.toString().split(":")[1]);
				totalCount += count;
				numSenses++;
			}
			
			context.write(word, new Text(totalCount + "\t" + numSenses + "\t" + StringUtils.join(targetCounts.iterator(), "  ")));
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