/*******************************************************************************
* Copyright 2012
* Copyright (c) 2012 IBM Corp.
* 

* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*   http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
******************************************************************************/
package de.tudarmstadt.lt.wiki.uima;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;

import org.apache.uima.cas.CAS;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.collection.CollectionReader_ImplBase;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.Progress;
import org.apache.uima.util.ProgressImpl;

import de.tudarmstadt.lt.util.MonitoredFileReader;
import de.tudarmstadt.lt.wiki.hadoop.WikiLinkCASExtractor;


/**
 * The collection reader is used to process a set of corpora. It determines the
 * number of files and creates a work item for each one with a corresponding
 * number of threads per file.
 */
public class LineCollectionReader extends CollectionReader_ImplBase {

	// UIMA input parameters
	public final static String LANGUAGE_CODE = "en";
	public final static String INPUT_PATH_PARAM = "InputPath";

	// member variables
	protected int index;
	protected int numFiles;
	protected Iterator<String> files;
	protected String nextLine = null;
	protected BufferedReader reader = null;
	protected String currFile = null;
	protected WikiLinkCASExtractor casExtractor = new WikiLinkCASExtractor();

	/**
	 * Closes the collection reader
	 */
	@Override
	public void close() throws IOException {
		files = null;
		index = 0;
	}

	/**
	 * Gets the next work item in the list and adds the document information to
	 * the CAS.
	 * 
	 * @param cas
	 *            CAS to place the document info into
	 */
	@Override
	public synchronized void getNext(CAS cas) throws IOException,
			CollectionException {
		cas.reset();
		
		String doc = casExtractor.extractDocumentText(nextLine);
		cas.setDocumentText(doc);
		cas.setDocumentLanguage(LANGUAGE_CODE);
		casExtractor.extractAnnotations(nextLine, cas);
		
		nextLine();
		return;
	}

	/**
	 * Gets the progress of the collection reader
	 * 
	 * @return the current index and the total number of work items.
	 */
	@Override
	public Progress[] getProgress() {
		ProgressImpl[] retVal = new ProgressImpl[1];
		retVal[0] = new ProgressImpl(index, numFiles, "InputFiles");
		return retVal;
	}
	
	protected void nextFile() throws IOException {
		if (files.hasNext()) {
			String currFile = files.next();
			reader = new BufferedReader(new MonitoredFileReader(currFile));
			index++;
		}
	}
	
	protected void nextLine() throws IOException {
		while ((nextLine = reader.readLine()) == null && files.hasNext()) {
			nextFile();
		};
	}

	/**
	 * Whether or not there are more work items to be processed.
	 * 
	 * @return <i>True</i> if there are more work items left.
	 */
	@Override
	public boolean hasNext() throws IOException, CollectionException {
		return nextLine != null;
	}

	/**
	 * Processes the input paths and creates the work item list.
	 */
	@Override
	public void initialize() throws ResourceInitializationException {
		super.initialize();

		// get the input files
		String[] inputPaths = (String[]) getConfigParameterValue(INPUT_PATH_PARAM);
		HashSet<String> allFileNames = new HashSet<String>(Arrays.asList(inputPaths));

		// create the work item list and ensure it's not empty
		if (allFileNames.isEmpty())
			throw new ResourceInitializationException(
					"File name list is empty. Can not continue.", null);

		files = allFileNames.iterator();
		numFiles = allFileNames.size();
		try {
			nextFile();
			nextLine();
		} catch (IOException e) {
			throw new ResourceInitializationException(e);
		}
	}

}
