package de.tudarmstadt.lt.wiki.hadoop;

import static org.apache.uima.fit.factory.TypeSystemDescriptionFactory.createTypeSystemDescription;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.log4j.Logger;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.cas.CASException;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.CasCreationUtils;

import de.tudarmstadt.lt.util.MapUtil;
import de.tudarmstadt.lt.util.MonitoredFileReader;
import de.tudarmstadt.lt.wiki.uima.StanfordLemmatizer;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.dependency.Dependency;
import de.tudarmstadt.ukp.dkpro.core.maltparser.MaltParser;
import de.tudarmstadt.ukp.dkpro.core.opennlp.OpenNlpPosTagger;
import de.tudarmstadt.ukp.dkpro.core.opennlp.OpenNlpSegmenter;

class WikiLinkTokenizerMap extends Mapper<LongWritable, Text, Text, Text> {
	Logger log = Logger.getLogger("de.tudarmstadt.lt.wiki");
	
	Map<String, String> redirects = new HashMap<>();
	AnalysisEngine engine;
	JCas jCas;

	public AnalysisEngineDescription buildAnalysisEngine() throws ResourceInitializationException {
		AnalysisEngineDescription segmenter = AnalysisEngineFactory.createEngineDescription(OpenNlpSegmenter.class);
		AnalysisEngineDescription pos = AnalysisEngineFactory.createEngineDescription(OpenNlpPosTagger.class);
		AnalysisEngineDescription lemmatizer = AnalysisEngineFactory.createEngineDescription(StanfordLemmatizer.class);
		AnalysisEngineDescription depParser = AnalysisEngineFactory.createEngineDescription(MaltParser.class);

		return AnalysisEngineFactory.createEngineDescription(
				segmenter, pos, lemmatizer, depParser);
	}
	
	private Collection<Dependency> collapseDependencies(JCas jCas, Collection<Dependency> deps, Collection<Token> tokens) {
		List<Dependency> collapsedDeps = new ArrayList<>(deps);
		for (Token token : tokens) {
			if (token.getPos().getPosValue().equals("IN")) {
				List<Dependency> toRemove = new ArrayList<>();
				String depType = "prep_" + token.getCoveredText().toLowerCase();
				Token source = null;
				Token target = null;
				int begin = -1;
				int end = -1;
				for (Dependency dep : collapsedDeps) {
					if (dep.getGovernor() == token && dep.getDependencyType().toLowerCase().equals("mwe")) {
						depType = "prep_" + dep.getDependent().getCoveredText() + "_" + token.getCoveredText().toLowerCase();
						toRemove.add(dep);
					} else if (dep.getGovernor() == token && dep.getDependencyType().toLowerCase().equals("pobj")) {
						end = dep.getEnd();
						target = dep.getDependent();
						toRemove.add(dep);
					} else if (dep.getDependent() == token && dep.getDependencyType().toLowerCase().equals("prep")) {
						begin = dep.getBegin();
						source = dep.getGovernor();
						toRemove.add(dep);
					}
				}
				if (source != null && target != null) {
					Dependency collapsedDep = new Dependency(jCas, begin, end);
					collapsedDep.setGovernor(source);
					collapsedDep.setDependent(target);
					collapsedDep.setDependencyType(depType);
					collapsedDeps.add(collapsedDep);
					collapsedDeps.removeAll(toRemove);
				}
			}
		}
		return collapsedDeps;
	}
	
	@Override
	public void setup(Context context) throws IOException {
		Configuration conf = context.getConfiguration();
		FileSystem fs = FileSystem.get(conf);
		String redirectsFilePattern = conf.get("wiki.redirects.file");
		if (redirectsFilePattern != null) {
			log.info("Reading redirects file: " + redirectsFilePattern);
			try {
				Path filePath = new Path(redirectsFilePattern);//fileStat.getPath();
				FileStatus fileStat = fs.getFileStatus(filePath);
				long fileLen = fileStat.getLen();
				String fileName = filePath.getName();
				log.info("Processing redirects file: " + filePath);
				InputStream in = fs.open(filePath);
				context.progress(); // Avoid getting time outs
                BufferedReader reader = new BufferedReader(new MonitoredFileReader(fileName, in, fileLen, "UTF-8", 0.01));
                redirects = MapUtil.readMapFromReader(reader, "\t");
				context.progress();
			} catch (Exception e) {
				log.error("Error reading redirect files", e);
			}
		} else {
			log.error("For redirects to be processed, you need to specify a redirects file"
					+ "pattern (on HDFS) using -Dwiki.redirects.file=...");
		}
		try {
			log.info("Initializing analysis engine...");
			engine = AnalysisEngineFactory.createEngine(buildAnalysisEngine());
			log.info("Done initializing analysis engine");
			context.progress();
			log.info("Initializing CAS and type system..");
			jCas = CasCreationUtils.createCas(createTypeSystemDescription(), null, null).getJCas();
			log.info("Done initializing CAS and type system..");
			context.progress();
		} catch (ResourceInitializationException e) {
			log.error("Couldn't initialize analysis engine", e);
		} catch (CASException e) {
			log.error("Couldn't create new CAS", e);
		}
	}
	
	@Override
	public void map(LongWritable key, Text value, Context context)
		throws IOException, InterruptedException {
		try {
			String valueParts[] = value.toString().split("\t");
			String linkTextLemma = valueParts[0];
			String text = valueParts[1];
			String link = valueParts[2];
			String target = link.split("@@")[1].split("@")[0];
			jCas.reset();
			jCas.setDocumentText(text);
			jCas.setDocumentLanguage("en");
			engine.process(jCas);
			List<Token> tokens = new ArrayList<Token>(JCasUtil.select(jCas, Token.class));
			List<String> tokenLemmas = new ArrayList<String>();
			for (int i = 0; i < tokens.size(); i++) {
				Token token = tokens.get(i);
				tokenLemmas.add(token.getLemma().getValue());
			}
			String tokenizedText = StringUtils.join(tokenLemmas, " ");

			List<String> bims = new ArrayList<String>();
			Collection<Dependency> deps = JCasUtil.select(jCas, Dependency.class);
			Collection<Dependency> depsCollapsed = collapseDependencies(jCas, deps, tokens);
			for (Dependency dep : depsCollapsed) {
				Token sourceToken = dep.getGovernor();
				Token targetToken = dep.getDependent();
				String sourceLemma = sourceToken.getLemma().getValue();
				String targetLemma = targetToken.getLemma().getValue();
				if (sourceLemma.equals(linkTextLemma)) {
					String rel = dep.getDependencyType();
					String bim = rel + "(@@," + targetLemma + ")";
					bims.add(bim);
				}
				if (targetLemma.equals(linkTextLemma)) {
					String rel = dep.getDependencyType();
					String bim = rel + "(" + sourceLemma + ",@@)";
					bims.add(bim);
				}
			}
			String bimsText = StringUtils.join(bims, " ");
			context.write(new Text(linkTextLemma), new Text(target + "\t" + tokenizedText + "\t" + bimsText));
		} catch (RuntimeException | UIMAException e) {
			log.error("Can't process line: " + value.toString(), e);
		}
	}
}