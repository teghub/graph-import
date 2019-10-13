import zemberek.morphology.analysis.SingleAnalysis;
import zemberek.tokenization.Token;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileTokenizer {

    private Zemberek zemberek;
    private Neo4jManager neo4jManager;
    private BufferedReader bufferedReader = null;

    private static String REGEXPUNCT = "[\\p{Punct}\\p{IsPunctuation}]";
    private static String REGEXSPACE = "[\\p{Space}\\p{Blank}\\s+]";
    private static Pattern PATTERNAPOST =  Pattern.compile( "[\\pL\\pN]'.*"/*"[a-zA-Z0-9]'.*"*/);//Pattern.compile("[a-zA-Z]'.*");
    private static Pattern PATTERNDIGIT = Pattern.compile("[\\d+]'.*");
    private static Pattern APOST = Pattern.compile("^'.*");

    private HashMap<String, String> characterMap;

    public FileTokenizer() {
        zemberek = new Zemberek();
        neo4jManager = new Neo4jManager();

        characterMap = new HashMap<>();
        characterMap.put("̇ş", "ş");
        characterMap.put("̇n", "n");
        characterMap.put("î", "i");
        characterMap.put("â", "a");
        characterMap.put("̇ş".toUpperCase(), "Ş");
        characterMap.put("̇n".toUpperCase(), "N");
        characterMap.put("î".toUpperCase(), "İ");
        characterMap.put("â".toUpperCase(), "A");
        characterMap.put("Å", "A");
        characterMap.put("Å".toLowerCase(), "a");
        characterMap.put("►", "");
        characterMap.put("û", "u");
        characterMap.put("û".toUpperCase(), "U");
        characterMap.put("ü", "ü");
        characterMap.put("ü".toUpperCase(), "Ü");


        characterMap.put("http.*\\s", " ");
        characterMap.put("http.*", "");
        characterMap.put("www.*\\s", " ");
        characterMap.put("’", "'");
        characterMap.put("´", "'");
        characterMap.put("`", "'");
        characterMap.put("‘", "'");
        characterMap.put("”", "\"");
        characterMap.put("“", "\"");
        characterMap.put("\"", " \" ");
        characterMap.put("-", " - ");
        characterMap.put("–", " – ");
        characterMap.put("\\.'", ". '");
        characterMap.put("\\$", " \\$ ");
        characterMap.put("&", " & ");
        characterMap.put("/", " / ");
        characterMap.put("\\^", " \\^ ");
        characterMap.put(" ", " ");
        characterMap.put(" ", " "); // weird square character
        characterMap.put("Æ", "");
    }

    // write updated pos tags to sentenceTripletList and put them in tripletList
    public void updatePosTags(List<Triplet> sentenceTripletList, List<Triplet> tripletList)  {
        StringBuilder sb = new StringBuilder();
        for (Triplet triplet : sentenceTripletList) {
            sb.append(triplet.getWord() + " ");
        }

        if (sentenceTripletList.size() == 0) {
            System.out.println("Empty sentence!");
            return;
        }
        else if (sb.length() > 2 && Pattern.matches(REGEXPUNCT, ""+sb.charAt(sb.length()-2))) { // last character is a punctuation
            sb.deleteCharAt(sb.length()-1);
        }
        else { // last character is not a punctuation. Put dot in order to disambiguate sentence
            sb.setCharAt(sb.length() - 1, '.');
        }

        List<SingleAnalysis> analysisList = zemberek.disambiguateSentence(sb.toString());
        int index = 0;
        int length = sentenceTripletList.size();

        // append pos tags
        for (SingleAnalysis singleAnalysis : analysisList) {
            if (index < length ) {
                String norm_word = zemberek.normalizedString(sentenceTripletList.get(index).getWord());
                String norm_sf = zemberek.normalizedString(singleAnalysis.surfaceForm());

                String clean_word = norm_word.replaceAll(REGEXPUNCT, ""); // remove all punctiation characters
                // clean_word = clean_word.replaceAll("’", ""); // remove apostrophe
                norm_sf = norm_sf.replaceAll(REGEXPUNCT, "");
                // norm_sf = norm_sf.replaceAll("’", "");

                if ((clean_word.length()== norm_sf.length() || norm_sf.length() > 0) && clean_word.startsWith(norm_sf)) { // words are matched
                    Triplet triplet = sentenceTripletList.get(index++);
                    String pos = singleAnalysis.getPos().getStringForm();
                    if (triplet.getPos() != null) { // pos = Apost
                        pos = triplet.getPos();
                    }
                    tripletList.add(new Triplet(triplet.getWord(),/* triplet.getMorphology(),*/ pos, triplet.getAnnotation()));
                }
                else { // words do not match, check prefix for matching
                    int position = norm_word.indexOf("'");
                    if (position > 0 && position < norm_sf.length()) {
                        String prefix = norm_word.substring(0, position);
                        String sub_sf = norm_sf.substring(0, position);
                        if (prefix.equals(sub_sf)) {
                            Triplet triplet = sentenceTripletList.get(index++);
                            String pos = singleAnalysis.getPos().getStringForm();
                            tripletList.add(new Triplet(triplet.getWord(), /* triplet.getMorphology(),*/ pos, triplet.getAnnotation()));
                        }
                    }

                    else if (norm_sf.length() > 0 && clean_word.contains(norm_sf)) { // zemberek bazı lemmalarda ? koyuyor başına
                        Triplet triplet = sentenceTripletList.get(index++);
                        String pos = singleAnalysis.getPos().getStringForm();
                        tripletList.add(new Triplet(triplet.getWord(), /* triplet.getMorphology(),*/ pos, triplet.getAnnotation()));

                    }


                }
            }
            else {
                //System.out.println("Index out of bounds - Last string: " + sentenceTripletList.get(length - 1 ).getWord());
            }
        }
    }

    // transform itu datasets to the crf format
    public void transformItuEnamextoCRF(String inputPath, String outputPath) {
        BufferedWriter bw = null;
        BufferedReader br = null;
        List<Triplet> tripletList = new ArrayList<>();
        List<Triplet> sentenceTripletList = new ArrayList<>();

        try {
            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8));
            br = new BufferedReader( new InputStreamReader(new FileInputStream(inputPath), StandardCharsets.UTF_8));

            String line = br.readLine();

            while (line != null) {

                line = Utils.replaceSequence(line, characterMap);
                line = EmojiUtils.removeEmoji(line);
                line = line.replaceAll(REGEXSPACE," "); // eliminate whitespaces

                //List<String> sentences = zemberek.extractSentences(line);
                //for (String sentence : sentences) {
                String sentence = line;
                String[] tokenArray = sentence.split(" ");

                if (tokenArray.length == 1 && tokenArray[0].equals("")) {}
                else {
                    boolean isNamedEntity = false;
                    String annotation = null;
                    int index = 0;
                    for (String token : tokenArray) {
                        if (token.isEmpty()) {
                            //System.out.println("Token is empty!");
                            continue;
                        }

                        if (token.equals(".")) {
                            sentenceTripletList.add(new Triplet(token, null, "O"));
                            updatePosTags(sentenceTripletList, tripletList); // find pos tag of each token
                            for (Triplet triplet : tripletList) {
                                bw.write(triplet.getWord() + "\t" + triplet.getPos() + "\t" + triplet.getAnnotation() + "\n");
                            }

                            if (tripletList.size() > 0) {
                                //bw.write("<S> <S>\n");
                                bw.write("\n");
                            }

                            sentenceTripletList.clear();
                            tripletList.clear();
                            continue;
                        }
                        if (token.startsWith("<b_")) { // named entity detected
                            isNamedEntity = true;
                            index = 1;
                        } else if (index == 1 && isNamedEntity) { // extract annotation
                            int first = token.indexOf("\"");
                            int second = token.indexOf("\"", first + 1);
                            annotation = token.substring(first + 1, second);

                            int x = !token.contains("<e_") ? token.length() : token.indexOf("<");
                            String entity = token.substring(token.indexOf(">") + 1, x);
                            sentenceTripletList.add(new Triplet(entity, null, annotation));
                            index++;
                        } else if (index > 1 && isNamedEntity) { // still named entity
                            int x = !token.contains("<e_") ? token.length() : token.indexOf("<");
                            String entity = token.substring(0, x);
                            sentenceTripletList.add(new Triplet(entity, null, annotation));
                        } else if (!isNamedEntity) { // it is not named entity
                            String pos = null;
                            if (token.startsWith("'") && token.length() > 1) {
                                pos = "Apost";
                            }
                            sentenceTripletList.add(new Triplet(token,  pos, "O"));
                        }
                        if (token.contains("<e_")) { // end of named entity
                            index = 0;
                            annotation = null;
                            isNamedEntity = false;
                        }

                    }
                }

                updatePosTags(sentenceTripletList, tripletList); // find pos tag of each token
                for (Triplet triplet : tripletList) {
                    bw.write(triplet.getWord() + "\t" + triplet.getPos() + "\t" + triplet.getAnnotation() + "\n");
                }

                if (tripletList.size() > 0) {
                    //bw.write("<S> <S>\n");
                    bw.write("\n");
                }

                sentenceTripletList.clear();
                tripletList.clear();
                // }
                line = br.readLine();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally{
            try {
                if (bw != null) {
                    br.close();
                    bw.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // prepare input for crf from tokenized content
    public void prepareCrfInput(String inputPath, String outputPath) {
        BufferedWriter bw = null;
        BufferedReader br = null;
        List<Triplet> tripletList = new ArrayList<>();
        List<Triplet> sentenceTripletList = new ArrayList<>();

        try {
            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8));
            br = new BufferedReader( new InputStreamReader(new FileInputStream(inputPath), StandardCharsets.UTF_8));

            String line = br.readLine();
            while (line != null) {

                List<String> sentences = zemberek.extractSentences(line);
                for (String sentence : sentences) {
                    List<Token> tokens = zemberek.tokenizeSentence(sentence);
                    for (Token token : tokens) {
                        if (token.getText().startsWith("'") && token.getText().length() > 1) {
                            sentenceTripletList.add(new Triplet(token.getText(), "Apost", "O"));
                        }
                        else {
                            sentenceTripletList.add(new Triplet(token.getText(), null, "O"));
                        }

                    }

                    updatePosTags(sentenceTripletList, tripletList); // find pos tag of each token
                    for (Triplet triplet : tripletList) {
                        String token = triplet.getWord().trim();
                        if (!token.isEmpty()) {
                            bw.write(token + "\t" + triplet.getPos() + "\t" + triplet.getAnnotation() + "\n");
                        }
                    }
                    if (tripletList.size() > 0) {
                        //bw.write("<S> <S>\n");
                        bw.write("\n");
                    }

                    sentenceTripletList.clear();
                    tripletList.clear();
                }
                line = br.readLine();
            }


        } catch (IOException e) {
            e.printStackTrace();
        } finally{
            try {
                if (bw != null) {
                    br.close();
                    bw.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // preprocess apostrophes and tokenize content
    public void tokenizeContent(String inputPath, String outputPath) {
        BufferedReader br = null;
        BufferedWriter bw = null;

        try {
            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8));
            br = new BufferedReader( new InputStreamReader(new FileInputStream(inputPath), StandardCharsets.UTF_8));

            String line = br.readLine(); // skip header
            line = br.readLine();
            while (line != null) {
                // String[] args = line.split("\t");
                // String date = args[0];
                // String content = args[1];
                String content = line;


                content = content.replaceAll(" ", " ");

                content = Utils.replaceSequence(content, characterMap);
                content = EmojiUtils.removeEmoji(content);

                content = content.replaceAll(REGEXSPACE, " ");

                if (!content.trim().isEmpty()) { // condition for empty news
                    // bw.write(date + "\t");
                    List<String> sentences = zemberek.extractSentences(content);
                    for (String sentence : sentences) {
                        List<Token> tokens = zemberek.tokenizeSentence(sentence);
                        //String[] tokens = sentence.split(" ");
                        for (Token token : tokens) {
                            String tokenStr = token.getText().trim();
                            if (tokenStr.startsWith("'") && tokenStr.length() > 1) {
                                tokenStr = "' " + token.getText().substring(1);
                            }
                            boolean isMatched = false;
                            Matcher matcher = PATTERNAPOST.matcher(tokenStr);
                            while (matcher.find()) {
                                isMatched = true;
                                //Prints the start index of the match.
                                String str = tokenStr.substring(0, matcher.start() + 1);
                                String apost = tokenStr.substring(matcher.start() + 2);

                                bw.write(str + " ' " + apost + " ");

                            }
                            if (!isMatched) {
                                bw.write(tokenStr + " ");
                            }
                        }

                    }
                    bw.write("\n");
                }
                line = br.readLine();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try{
                bw.close();
                br.close();
            } catch (IOException e) {e.printStackTrace();}

        }
    }

    // lemmatize tokenized content
    public void lemmatizeContent(String inputPath, String outputPath) {
        BufferedWriter bw = null;
        BufferedReader br = null;

        try {
            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8));
            br = new BufferedReader( new InputStreamReader(new FileInputStream(inputPath), StandardCharsets.UTF_8));

            //String date = br.readLine();
            String line = br.readLine();
            while (line != null) {

                line = Utils.replaceSequence(line, characterMap);
                line = EmojiUtils.removeEmoji(line);

                String content = line;

                //bw.write(date + "\n");
                List<String> sentenceList = zemberek.extractSentences(content);
                for (String sentence : sentenceList) {
                    StringBuilder sb = new StringBuilder();

                    //sentence = sentence.replaceAll(REGEXPUNCT, "");
                    List<SingleAnalysis> singleAnalysisList = zemberek.disambiguateSentence(sentence);
                    for (SingleAnalysis singleAnalysis : singleAnalysisList) {
                        String lemma = singleAnalysis.getDictionaryItem().normalizedLemma();//lemma;
                        if (lemma.isEmpty() || lemma.equals("UNK") || lemma.contains("?")) { // lemma is errorenous
                            lemma = singleAnalysis.surfaceForm();
                        }

                        lemma = lemma.trim();
                        if (!lemma.isEmpty()) {
                            sb.append(lemma + " ");
                        }
                    }

                    String str = sb.toString().replaceAll(REGEXSPACE, " ");
                    //bw.write(zemberek.normalizedString(sb.toString()).toLowerCase());
                    bw.write(sb.toString()/*.toLowerCase()*/);
                }
                bw.write("\n");

                line = br.readLine();

            }

        } catch ( IOException e) {
            e.printStackTrace();
        } finally {
            try{
                bw.close();
                br.close();
            } catch (IOException e) {e.printStackTrace();}
        }
    }

    // lemmatize output of CRF (prediction file)
    public void lemmatizePredictions(String inputPath, String outputPath) {
        BufferedWriter bw = null;
        BufferedReader br = null;

        Map<String, Set<String>> neMap = new HashMap<>();
        neMap.put("PERSON", new HashSet<>());
        neMap.put("ORGANIZATION", new HashSet<>());
        neMap.put("LOCATION", new HashSet<>());
        neMap.put("DATE", new HashSet<>());
        neMap.put("MONEY", new HashSet<>());
        neMap.put("PERCENT", new HashSet<>());
        neMap.put("TIME", new HashSet<>());

        int count = 0;
        String punct = "'–";
        Pattern patternDot = Pattern.compile("[a-zA-Z].[a-zA-Z]");
        try {
            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8));
            br = new BufferedReader( new InputStreamReader(new FileInputStream(inputPath), StandardCharsets.UTF_8));

            String line = br.readLine();
            StringBuilder sentence = new StringBuilder();
            List<String> annotationList = new ArrayList<>();
            List<String> tokenList = new ArrayList<>();
            while (line != null) {
                count++;

                line = Utils.replaceSequence(line, characterMap);

                if (!line.isEmpty()) { // contains token
                    String[] token_annotation = line.split("\t");

                    String token = token_annotation[0];
                    //bw.write(zemberek.lemmatize(token) + "\t" + token_annotation[1] + "\n");

                    int j = 0;
                    if (token.length() > 1) {
                        token = token.replaceAll("'", "");

                    }

                    if (!token.isEmpty()) {
                        sentence.append(token + " ");
                        annotationList.add(token_annotation[1]);
                        tokenList.add(token.toLowerCase());
                    }
                }

                else { // end of sentence
                    if (sentence.toString().length() > 2) {
                        List<SingleAnalysis> singleAnalysisList = zemberek.disambiguateSentence(sentence.toString());
                        int index = 0;
                        for (SingleAnalysis sa : singleAnalysisList) {
                            if (index < tokenList.size() && sa.surfaceForm().equals(".") && !tokenList.get(index).equals(".")) {
                                System.out.println("Dot: " + sentence.toString());
                                continue;
                            }

                            if (index < tokenList.size()) {
                                String lemma = sa.getDictionaryItem().normalizedLemma();//lemma;
                                if (lemma.isEmpty() || lemma.equals("UNK") || (lemma.equals("?") && !tokenList.get(index).equals("?"))) {
                                    lemma = tokenList.get(index);
                                }
                                //System.out.println(lemma);
                                //lemma = zemberek.normalizedString(lemma).toLowerCase();
                                lemma = lemma.trim();//.toLowerCase();
                                if (!lemma.isEmpty()) {
                                    bw.write(lemma + "\t" + annotationList.get(index++) + "\t" + sa.getPos() + "\n");
                                }

                            }
                            else {
                                System.out.println("Index Out Of Bounds: " + sa.surfaceForm() + " | "  + count);
                                System.out.println(sentence.toString());
                            }
                        }

                        sentence.delete(0, sentence.length());
                        annotationList.clear();
                        tokenList.clear();
                        bw.write("\n");
                    }
                }
                line = br.readLine();
            }
        } catch (Exception e) {
            System.out.println(count);
            e.printStackTrace();
        } finally{
            try {
                if (bw != null) {
                    br.close();
                    bw.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // prepare neo4j input file from tokenized content and crf output file
    public void prepareNeo4jInputFromCRF(String tokenizedContentPath, String predictionPath, String outputPath) {
        BufferedReader br = null;
        BufferedWriter bw = null;
        long count = 1;
        try {
            br = new BufferedReader( new InputStreamReader(new FileInputStream(tokenizedContentPath), StandardCharsets.UTF_8));
            bufferedReader = new BufferedReader( new InputStreamReader(new FileInputStream(predictionPath), StandardCharsets.UTF_8));
            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8));

            //bw.write("nid:ID,word,NamedEntity,date\n");
            bw.write("nid:ID,word,NamedEntity\n");
            String line = br.readLine(); // skip header
            line = br.readLine();
            bufferedReader.readLine(); // skip header

            while (line != null) {
                /*
                String[] args = line.split("\t");

                String date = args[0];
                int idx = date.indexOf("T");
                date = date.substring(0,idx != -1 ? idx : date.length());

                String content = args[1];
                 */
                String content = line;

                content = content.replace("\"", "'"); // necessary for neo4j

                List<String> sentences = zemberek.extractSentences(content);

                News news = new News(count, null, null, content);
                neo4jManager.getNewsList().add(news);

                List<NamedEntity> namedEntityList = new ArrayList<>();
                for (String sentence : sentences) {
                    //neo4jManager.findNamedEntityMatches(bufferedReader, news, zemberek);
                    neo4jManager.getNamedEntities(bufferedReader, zemberek, namedEntityList);
                }

                for (NamedEntity ne : namedEntityList) {
                    String word = ne.getLabel();
                    if (word.contains(",")) {
                        //System.out.println(word);
                        word = word.replaceAll(",", ".");
                    }
                    //bw.write(count + "," + word + "," + ne.getAnnotation() + "," + date + "\n");
                    bw.write(count + "," + word + "," + ne.getAnnotation() + "\n");
                }

                count++;

                line = br.readLine();
                // if (count > 10) break;
            }

        } catch ( IOException e) {
            e.printStackTrace();
        } finally {
            try{
                br.close();
                bufferedReader.close();
                bw.close();
            } catch (IOException e) {}

            System.out.println("Count = " + count);
        }
    }

    public static void main(String[] args) {
        String CRFPATH = "haberler-crf\\disambiguated-crf\\";
        String NEWSPATH = "haberler\\";
        String NEO4JPATH ="neo4j\\";

        String contentPath = NEWSPATH+"content_orig.txt";
        String tokenizedPath = NEWSPATH+"tokenized.txt";
        String lemmatizedPath = NEWSPATH+"lemmatized.txt";

        String crfInputPath = NEWSPATH+"crf_input.txt";
        String crfOutputPath = CRFPATH+"crf_output.txt";

        FileTokenizer ft = new FileTokenizer();
        // You need to execute phases one by one because there is a dependecy between our NER module and this module.

        // phase 1: tokenize the content & transform the tokenized content into crf input format
        ft.tokenizeContent(contentPath, tokenizedPath );
        ft.lemmatizeContent(tokenizedPath, lemmatizedPath);
        ft.prepareCrfInput (tokenizedPath, crfInputPath);

        // phase 2: get nodes and relations from crf output
        ft.prepareNeo4jInputFromCRF(tokenizedPath, crfOutputPath, NEO4JPATH+"neo4j_input.txt");
    }


}
