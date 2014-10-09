#!/bin/bash

JAR=target/wiki-link-processor-0.0.1-SNAPSHOT.jar
PACKAGE=de.tudarmstadt.lt.wiki.hadoop

# preprocess wikipedia dump (wiki/ folder must contain enwiki-pages-articles.xml)
hadoop jar $JAR $PACKAGE.HadoopWikiXmlProcessor\
   wiki\
   wiki-links &&

# move redirects to a single file so we can more easily load it later
hadoop fs -text wiki-links/redirects* | hadoop fs -put - wiki-redirects.gz &&

# create dictionary/thesaurus from surface forms of links
hadoop jar $JAR $PACKAGE.HadoopSurfaceFormDictionary\
   -Dwiki.redirects.file=wiki-redirects.gz\
   -Dmapreduce.map.memory.mb=2048\
   -Dmapreduce.map.java.opts=-Xmx2G\
   wiki-links/*links*\
   wiki-links-surface-forms &&

# counts surface forms (irrespective of their sense)
hadoop jar $JAR $PACKAGE.HadoopSurfaceFormDictionaryWordCount\
   wiki-links-surface-forms\
   wiki-links-surface-forms-counts &&

# copy counts and sort them locally
hadoop fs -text wiki-surface-forms-counts/part*\
   > wiki-surface-forms-counts &&
sort wiki-surface-forms-counts -t'       ' -k 2 -nr\
   > wiki-surface-forms-counts.sorted
