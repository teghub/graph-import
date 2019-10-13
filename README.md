# Importing Textual Data into Neo4j

This section introduces codes for importing textual data into the Neo4j. It consists of two phases.
In the first phase, we have tokenized the textual data using Zemberek ( https://github.com/ahmetaa/zemberek-nlp )  .
Then using morphology module of Zemberek, we find Parts-of-speech tag of each token. We need POS tags because our NER module uses POS tags as a feature. We feed our tokenized and pos tagged data to the NER module and extract named entities from the service. You can get more information about the NER module from: https://github.com/teghub/ner 

In the first phase you should execute the following methods in "FileTokenizer.java":
	1) void tokenizeContent(String contentPath, String tokenizedPath )
		It tokenizes the raw textual content and removes non unicode characters and emojis from the texts. Besides we remove web links and some non-alphanumeric characters since we observe that Zemberek disambiguates those kind of stuff inaccurately.
		
	2) void lemmatizeContent(String inputPath, String outputPath)
		It lemmatizes the tokenized content using Zemberek's morphology module.
	
	3) void prepareCrfInput (String tokenizedPath, String crfInputPath)
		Extracts POS tags of each token and makes it ready for our NER module. In our NER module, each line must consists of a token  - POS Tag - Dummy Annotation triples. You need to feed the output (crfInputPath) of this method into our NER module.

Finally, we have prepared a csv format input for Neo4j. The input consists of "news" and "named entities" which are contained in news. 

In the second phase you should execute the following method in "FileTokenizer.java":
	4) prepareNeo4jInputFromCRF(String tokenizedPath, String crfOutputPath, String outputPath)
		A csv file is created which contains three fields: nid:ID,word,NamedEntity
		The first field indicates the id of news. The second field shows the token (which is in our case a named entity)
		Finally the third field illustrates the category of the named entity (PERSON, LOCATION, ORGANIZATION, DATE, TIME, MONEY or PERCENT)
		
		You can feed this csv file into Neo4j using batch import feature of Neo4j or we have also written a script for it. You can find more information at:
		https://github.com/teghub/news_prediction
		
