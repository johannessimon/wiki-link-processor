package wiki;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.reduce.IntSumReducer;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */



/**
 * Counts the number of in-links per resource. To do so, lines of the following
 * format:
 * "link text" \t link_target \t Sentence containing <head>link text</head>
 * are mapped to key-value pairs of the form
 * link_target -> 1
 * and then reduced to
 * link_target -> count(link_target)
 */
public class ResourceInlinkCount {
	public static class Map extends Mapper<LongWritable, Text, Text, IntWritable> {
		HashMap<String, String> redirects = new HashMap<String, String>();
		public Map() {
			try {
				Path path = new Path("wiki/enwiki-redirects.txt");
				FileSystem fs = FileSystem.get(new Configuration());
				BufferedReader br = new BufferedReader(new InputStreamReader(fs.open(path)));
				String line;
				while ((line = br.readLine()) != null) {
//					System.out.print('+');
					String[] parts = line.split("\t");
					if (parts.length == 2) {
						String from = formatResourceName(parts[0]);
						String to = formatResourceName(parts[1]);
//						System.out.println("REDIRECT : " + from + " -> " + to);
						redirects.put(from, to);
					} else {
						System.err.println("Error: redirect line does not have 2 columns!: " + line);
					}
				}
				System.out.println();
				System.out.println("Done reading redirects. Entries: " + redirects.size());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		private String formatResourceName(String resource) {
			return resource.replace(' ', '_');
		}
		
		private String getLinkedResource(String target) {
			int startIndex = target.indexOf('#');
			if (startIndex >= 0) {
				target = target.substring(0, startIndex);
			}
			String redirectedTarget = redirects.get(target);
//			System.out.println("REDIRECT SEARCH: " + target + " -> " + redirectedTarget);
			if (redirectedTarget != null)
				return target = redirectedTarget;
			return target;
		}

		@Override
		public void map(LongWritable key, Text Value, Context context)
				throws IOException, InterruptedException {
			String line = Value.toString();
			String parts[] = line.split("\t");
			// A valid line has 3 spits: link text, link target and context
			if (parts.length == 3) {
				String to = getLinkedResource(parts[1]);
				context.write(new Text(to), new IntWritable(1));
			}
		}
	}

	public static class Map2 extends Mapper<LongWritable, Text, IntWritable, IntWritable> {
		@Override
		public void map(LongWritable key, Text value, Context context)
				throws IOException, InterruptedException {
			String line = value.toString();
			String[] parts = line.split("\t");
			if (parts.length == 2) {
				int count = Integer.parseInt(parts[1]);
				//			System.out.println("### MAP2 " + value + " -> 1" );
				context.write(new IntWritable(count), new IntWritable(1));
			}
		}
	}

	public static boolean runJob1(String inDir, String outDir) throws Exception {
		Configuration conf = new Configuration();
		conf.set("mapred.child.java.opts", "-Xmx1200M");
		conf.set("mapred.job.map.memory.mb", "1280");
		conf.set("mapreduce.job.queuename","smalljob");
		Job job = Job.getInstance(conf);
		job.setJarByClass(ResourceInlinkCount.class);
		FileInputFormat.addInputPath(job, new Path(inDir));
		FileOutputFormat.setOutputPath(job, new Path(outDir));
		job.setMapperClass(Map.class);
		job.setReducerClass(IntSumReducer.class);
		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(IntWritable.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(IntWritable.class);
		job.setInputFormatClass(TextInputFormat.class);
		return job.waitForCompletion(true);
	}

	public static boolean runJob2(String inDir, String outDir) throws Exception {
		Configuration conf = new Configuration();
		conf.set("mapreduce.job.queuename","smalljob");
		Job job = Job.getInstance(conf);
		job.setJarByClass(ResourceInlinkCount.class);
		FileInputFormat.addInputPath(job, new Path(inDir));
		FileOutputFormat.setOutputPath(job, new Path(outDir));
		job.setMapperClass(Map2.class);
		job.setReducerClass(IntSumReducer.class);
		job.setMapOutputKeyClass(IntWritable.class);
		job.setMapOutputValueClass(IntWritable.class);
		job.setOutputKeyClass(IntWritable.class);
		job.setOutputValueClass(IntWritable.class);
		job.setInputFormatClass(TextInputFormat.class);
		return job.waitForCompletion(true);
	}

	public static void main(String[] args) throws Exception {
		String inDir = args[0];
		String outDir = args[1];
		String tmpDir = outDir + "_inlink_counts";
		System.out.println("1+");
		boolean success = runJob1(inDir, tmpDir);
		System.out.println("2 " + success);
		if (success) {
			System.out.println("3");
			success = runJob2(tmpDir, outDir);
		}
		System.out.println("4 " + success);
		System.exit(success ? 0 : 1);
	}
}