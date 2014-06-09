package uima;

import static org.apache.uima.fit.util.JCasUtil.select;

import java.io.FileInputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.jobimtext.holing.type.Sentence;

import uima.type.WikiLink;
import de.tudarmstadt.lt.wsi.Cluster;
import de.tudarmstadt.lt.wsi.ClusterReaderWriter;

public class SentenceFilter extends JCasAnnotator_ImplBase {
	public static final String PARAM_CLUSTER_FILE = "ClusterFile";
	
	Map<String, List<Cluster>> clusters;
	int keptSentences = 0;
	int removedSentences = 0;
	
	@Override
	public void initialize(UimaContext context)
			throws ResourceInitializationException {
		super.initialize(context);
		String clusterFileName = (String) context
				.getConfigParameterValue(PARAM_CLUSTER_FILE);
		try {
			clusters = ClusterReaderWriter.readClusters(new FileInputStream(clusterFileName));
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
				if (clusters.containsKey(link.getCoveredText())) {
					keepSentence = true;
				} else {
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
