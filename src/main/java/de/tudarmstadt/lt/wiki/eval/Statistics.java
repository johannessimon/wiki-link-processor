package de.tudarmstadt.lt.wiki.eval;

import org.apache.log4j.Logger;

public class Statistics {
	static Logger log = Logger.getLogger("de.tudarmstadt.lt.wiki.eval");
	
	int numProcessesSentences = 0;
	int numInstances = 0;
	int numMappingFails = 0;
	int numMappingSuccesses = 0;
	int numMissingSenseClusters = 0;
	int numBaselineBackedMappingMatches = 0;
	int numBaselineBackedMappingMatchesFails = 0;
	int numMappingMatches = 0;
	int numMappingMatchesFails = 0;
	int numBaselineMappingMatches = 0;
	int numBaselineMappingMatchesFails = 0;

	public void print() {
		log.info("=== Statistics ===");
		log.info("# processed sentences:\t" + numProcessesSentences);
		log.info("# words covered entirely by JoBim annotation:\t" + numInstances);
		log.info("# - Successful assignments of cluster: " + numMappingSuccesses + "/" + numInstances);
		log.info("# - Failed assignments of cluster:     " + numMappingFails + "/" + numInstances);
		log.info("# - No clusters found at all:          " + numMissingSenseClusters + "/" + numInstances);
		log.info("");
		log.info("# Evaluation");
		log.info("# Correct mappings:                    " + numMappingMatches + "/" + numMappingSuccesses);
		log.info("# Incorrect mappings:                  " + numMappingMatchesFails + "/" + numMappingSuccesses);
		log.info("# Correct mappings (baseline-backed):  " + numBaselineBackedMappingMatches + "/" + numInstances);
		log.info("# Incorrect mappings (baseline-backed):" + numBaselineBackedMappingMatchesFails + "/" + numInstances);
		log.info("");
		log.info("# Baseline comparison");
		log.info("# Correct baseline mappings:           " + numBaselineMappingMatches + "/" + numInstances);
		log.info("# Incorrect baseline mappings:         " + numBaselineMappingMatchesFails + "/" + numInstances);
	}
}
