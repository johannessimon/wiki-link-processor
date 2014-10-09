package de.tudarmstadt.lt.wiki.hadoop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;
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

import de.tudarmstadt.lt.util.MapUtil;
import de.tudarmstadt.lt.util.MonitoredFileReader;

public class HadoopSurfaceFormDictionary extends Configured implements Tool {
	private static class HadoopSurfaceFormDictionaryMap extends Mapper<LongWritable, Text, Text, IntWritable> {
		Logger log = Logger.getLogger("de.tudarmstadt.lt.wiki");
		
		Map<String, String> redirects = new HashMap<>();
		
		@Override
		public void setup(Context context) throws IOException {
			Configuration conf = context.getConfiguration();
			FileSystem fs = FileSystem.get(conf);
			String redirectsFilePattern = conf.get("wiki.redirects.dir");
			if (redirectsFilePattern != null) {
				log.info("Reading redirects files: " + redirectsFilePattern);
				try {
					RemoteIterator<LocatedFileStatus> it = fs.listFiles(new Path(redirectsFilePattern), false);
					while (it.hasNext()) {
						LocatedFileStatus fileStat = it.next();
						Path filePath = fileStat.getPath();
						long fileLen = fileStat.getLen();
						String fileName = filePath.getName();
						if (fileName.startsWith("redirects")) {
							InputStream in = fs.open(filePath);
		                    BufferedReader reader = new BufferedReader(new MonitoredFileReader(fileName, in, fileLen, "UTF-8", 0.01));
		                    redirects.putAll(MapUtil.readMapFromReader(reader, "\t"));
						}
					}
				} catch (Exception e) {
					log.error("Error reading redirect files", e);
				}
			} else {
				log.error("For redirects to be processed, you need to specify a redirects file"
						+ "pattern (on HDFS) using -Dwiki.redirects.filepattern=...");
			}
		}
		
		@Override
		public void map(LongWritable key, Text value, Context context)
			throws IOException, InterruptedException {
			try {
				String valueParts[] = value.toString().split("\t");
				String text = valueParts[0];
				String linkParts[] = valueParts[1].split("@");
				String target = linkParts[0];
				String _target = redirects.get(target);
				if (_target != null) {
					target = _target;
				}
				String startEnd[] = linkParts[1].split(":");
				int start = Integer.parseInt(startEnd[0]);
				int end = Integer.parseInt(startEnd[1]);
				String surfaceForm = text.substring(start, end);
				
				context.write(new Text(surfaceForm + "@@" + target), new IntWritable(1));
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
		job.setJarByClass(HadoopSurfaceFormDictionary.class);
		FileInputFormat.addInputPath(job, new Path(inDir));
		FileOutputFormat.setOutputPath(job, new Path(_outDir));
		job.setMapperClass(HadoopSurfaceFormDictionaryMap.class);
		job.setReducerClass(IntSumReducer.class);
		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(IntWritable.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(IntWritable.class);
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
		int res = ToolRunner.run(conf, new HadoopSurfaceFormDictionary(), args);
		System.exit(res);
	}
}