package de.tudarmstadt.lt.wiki.hadoop;
import java.util.Arrays;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.LazyOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

public class XmlProcessor extends Configured implements Tool {

	public boolean runJob(String inDir, String outDir) throws Exception {
		Configuration conf = getConf();
		FileSystem fs = FileSystem.get(conf);
		String _outDir = outDir;
		int outDirSuffix = 1;
		while (fs.exists(new Path(_outDir))) {
			_outDir = outDir + outDirSuffix;
			outDirSuffix++;
		}
//		conf.set("mapred.job.shuffle.input.buffer.percent","0.2");
		conf.set("xmlinput.start", "<page>");
		conf.set("xmlinput.end", "</page>");
		conf.setBoolean("mapred.output.compress", true);
		conf.set("mapred.output.compression.codec", "org.apache.hadoop.io.compress.GzipCodec");
		Job job = Job.getInstance(conf);
		job.setJarByClass(XmlProcessor.class);
		FileInputFormat.addInputPath(job, new Path(inDir));
		FileOutputFormat.setOutputPath(job, new Path(_outDir));
		job.setMapperClass(XmlProcessorMap.class);
		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(Text.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);
		job.setInputFormatClass(XmlInputFormat.class);
		MultipleOutputs.addNamedOutput(job, "sentences", TextOutputFormat.class, Text.class, NullWritable.class);
		MultipleOutputs.addNamedOutput(job, "pages", TextOutputFormat.class, Text.class, NullWritable.class);
		MultipleOutputs.addNamedOutput(job, "redirects", TextOutputFormat.class, Text.class, Text.class);
		MultipleOutputs.addNamedOutput(job, "links", TextOutputFormat.class, Text.class, Text.class);
		MultipleOutputs.addNamedOutput(job, "implicitlinks", TextOutputFormat.class, Text.class, Text.class);
		MultipleOutputs.addNamedOutput(job, "xml", TextOutputFormat.class, Text.class, NullWritable.class);
		LazyOutputFormat.setOutputFormatClass(job, TextOutputFormat.class);
		job.setJobName("WikiLinkProcessor:XmlProcessor");
		return job.waitForCompletion(true);
	}

	public int run(String[] args) throws Exception {
		System.out.println("args:" + Arrays.asList(args));
		if (args.length != 2) {
			System.out.println("Usage: </path/to/enwiki-*-pages-articles.xml> </path/to/output>");
			System.exit(1);
		}
		// String inDir = "src/test/resources/enwiki-pages-articles-test.xml";
		// String inDir = "/Volumes/G-DRIVE/Downloads/enwiki-20140203-pages-articles.xml.bz2";
		String inDir = args[0];
		// String outDir = "src/test/resources/multi-file-test";
		// String outDir = "/Volumes/G-DRIVE/Downloads/processed/";
		String outDir = args[1];
		//		File outDirFile = new File(outDir);
		//		if (outDirFile.exists()) {
		//			FileUtils.deleteDirectory(outDirFile);
		//		}
		boolean success = runJob(inDir, outDir);
		return success ? 0 : 1;
	}

	public static void main(final String[] args) throws Exception {
		Configuration conf = new Configuration();
		int res = ToolRunner.run(conf, new XmlProcessor(), args);
		System.exit(res);
	}
}