package de.tudarmstadt.lt.wiki.uima;

/*******************************************************************************
 * Copyright 2013
 * Copyright (c) 2013 IBM Corp.
 *
 * and
 *
 * FG Language Technology
 * Technische Universitaet Darmstadt
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

import static org.apache.uima.fit.util.JCasUtil.select;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.resource.ResourceInitializationException;
import org.jobimtext.holing.type.JoBim;

import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.dependency.Dependency;
/**
 * The DependencyHolingAnnotator is a UIMA Annotator that annotates a text with
 * dependency {@link JoBim}s. It creates JoBims with 'Governor'--'Dependent' relations
 * from already present Dependency annotations.
 * These Dependencies can stem from a Parser like the StanfordParser.
 */
public class DependencyHolingAnnotator extends JCasAnnotator_ImplBase {
	/**
	 * Specifies whether to use holes when creating JoBims
	 */
	private boolean withHole = true;
	private static String MODEL_ID = "DependencyParseModel";
	/**
	 * Initialize the annotator.
	 *
	 * @param aContext UIMA Annotator context
	 */
	@Override
	public void initialize(UimaContext aContext)
			throws ResourceInitializationException {
		super.initialize(aContext);
	}
	/**
	 * Process all Dependencies in the CAS.
	 * Create and add a pair of JoBims for each Dependency.
	 *
	 * @param aJCas JCas that should be processed
	 */
	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {
		// TODO Auto-generated method stub
		if (withHole) {
			for (Dependency d : select(aJCas, Dependency.class)) {
				addJobim(aJCas, d.getGovernor(), d.getDependent(), d.getDependencyType(), 0);
				addJobim(aJCas, d.getDependent(), d.getGovernor(), d.getDependencyType(), 1);
			}
		} else {
			for (Dependency d : select(aJCas, Dependency.class)) {
				addJobim(aJCas, d.getGovernor(), d.getDependent(), d.getDependencyType());
				// without holing, Dependent receives a 'negative' relation type
				// to distinguish it from the Governor
				addJobim(aJCas, d.getDependent(), d.getGovernor(), "-" + d.getDependencyType());
			}
		}
	}
	/**
	 * Add JoBim with a relation to a CAS. Set 'hole' position.
	 *
	 * @param jcas CAS where the dependency relation is added to
	 * @param t1 First token in the relation
	 * @param t2 Second token in the relation
	 * @param relation Type of relationship
	 * @param holePosition Position of the 'hole', starting from '0' ('-1' means no hole)
	 */
	public void addJobim(JCas jcas, Token t1, Token t2, String relation,
			int holePosition) {
		JoBim jb = createJobim(jcas, t1, t2, relation);
		jb.setHole(holePosition);
	}
	/**
	 * Add JoBim with a relation to a CAS, without setting a 'hole' position.
	 *
	 * @param jcas CAS where the dependency relation is added to
	 * @param t1 First token in the relation
	 * @param t2 Second token in the relation
	 * @param relation Type of relationship
	 */
	public void addJobim(JCas jcas, Token t1, Token t2, String relation) {
		createJobim(jcas, t1, t2, relation);
	}
	/**
	 * Add JoBim with a relation to a CAS.
	 * Return JoBim for further annotations. By default, no hole is set.
	 *
	 * @param jcas CAS where the dependency relation is added to
	 * @param t1 First token in the relation
	 * @param t2 Second token in the relation
	 * @param relation Type of relationship
	 */
	public JoBim createJobim(JCas jcas, Token t1, Token t2, String relation) {
		// System.out.println(relation);
		JoBim jb = new JoBim(jcas, t1.getBegin(), t1.getEnd());
		jb.setModel(MODEL_ID);
		jb.setKey(t1);
		FSArray array = new FSArray(jcas, 1);
		array.set(0, t2);
		jb.setValues(array);
		jb.setRelation(relation);
		// no hole visible
		jb.setHole(-1);
		jb.addToIndexes();
		return jb;
	}
} 