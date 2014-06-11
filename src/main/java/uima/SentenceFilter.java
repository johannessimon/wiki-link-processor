package uima;

import static org.apache.uima.fit.util.JCasUtil.select;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.jobimtext.holing.type.Sentence;

import uima.type.WikiLink;

public class SentenceFilter extends JCasAnnotator_ImplBase {
	public static final String PARAM_WORD_FILE = "WordFile";
	
	Set<String> words;
	int keptSentences = 0;
	int removedSentences = 0;
	
	@Override
	public void initialize(UimaContext context)
			throws ResourceInitializationException {
		super.initialize(context);
		String clusterFileName = (String) context
				.getConfigParameterValue(PARAM_WORD_FILE);
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(clusterFileName)));
			words = new HashSet<String>();
			String line;
			while ((line = reader.readLine()) != null) {
				words.add(line);
			}
			reader.close();
		} catch (Exception e) {
			throw new ResourceInitializationException(e);
		}
	}

	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {
		List<Sentence> sentencesToRemove = new LinkedList<Sentence>();
		for (Sentence s : select(aJCas, Sentence.class)) {
			boolean keepSentence = false;
			List<WikiLink> linksToRemove = new LinkedList<WikiLink>();
			for (WikiLink link : JCasUtil.selectCovered(WikiLink.class, s)) {
				try {
					if (words.contains(link.getCoveredText())) {
						keepSentence = true;
					} else {
						linksToRemove.add(link);
					}
				} catch (Exception e) {
					System.err.println("Error in link annotaiton.");
					linksToRemove.add(link);
				}
			}
			
			for (WikiLink l : linksToRemove) {
				l.removeFromIndexes();
			}
			
			if (keepSentence) {
				keptSentences++;
			} else {
				sentencesToRemove.add(s);
				removedSentences++;
			}
		}
		
		for (Sentence s : sentencesToRemove) {
			s.removeFromIndexes();
		}
	}

}
