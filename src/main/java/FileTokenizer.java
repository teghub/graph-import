import entity.NamedEntity;
import entity.News;
import entity.Triplet;
import utility.EmojiUtils;
import utility.Utils;
import zemberek.morphology.analysis.SingleAnalysis;
import zemberek.tokenization.Token;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.regex.Pattern;

public class FileTokenizer {

    private static String REGEXPUNCT = "[\\p{Punct}\\p{IsPunctuation}]";
    private static String REGEXSPACE = "[\\p{Space}\\p{Blank}\\s+]";
    private static Pattern PATTERNAPOST = Pattern.compile("[\\pL\\pN]'.*"/*"[a-zA-Z0-9]'.*"*/);//Pattern.compile("[a-zA-Z]'.*");
    private static Pattern PATTERNDIGIT = Pattern.compile("[\\d+]'.*");
    private static Pattern APOST = Pattern.compile("^'.*");

    private TextProcessing textProcessing;

    public FileTokenizer() {
        textProcessing = new TextProcessing();
    }

    // write updated pos tags to sentenceTripletList and put them in tripletList
    public void updatePosTags(List<Triplet> sentenceTripletList, List<Triplet> tripletList) {
        StringBuilder sb = new StringBuilder();
        for (Triplet triplet : sentenceTripletList) {
            sb.append(triplet.getWord() + " ");
        }

        if (sentenceTripletList.size() == 0) {
            System.out.println("Empty sentence!");
            return;
        } else if (sb.length() > 2 && Pattern.matches(REGEXPUNCT, "" + sb.charAt(sb.length() - 2))) { // last character is a punctuation
            sb.deleteCharAt(sb.length() - 1);
        } else { // last character is not a punctuation. Put dot in order to disambiguate sentence
            sb.setCharAt(sb.length() - 1, '.');
        }

        List<SingleAnalysis> analysisList = textProcessing.getZemberek().disambiguateSentence(sb.toString());
        int index = 0;
        int length = sentenceTripletList.size();

        // append pos tags
        for (SingleAnalysis singleAnalysis : analysisList) {
            if (index < length) {
                String norm_word = TextProcessing.normalizedString(sentenceTripletList.get(index).getWord());
                String norm_sf = TextProcessing.normalizedString(singleAnalysis.surfaceForm());

                String clean_word = norm_word.replaceAll(REGEXPUNCT, ""); // remove all punctiation characters
                // clean_word = clean_word.replaceAll("’", ""); // remove apostrophe
                norm_sf = norm_sf.replaceAll(REGEXPUNCT, "");
                // norm_sf = norm_sf.replaceAll("’", "");

                if ((clean_word.length() == norm_sf.length() || norm_sf.length() > 0) && clean_word.startsWith(norm_sf)) { // words are matched
                    Triplet triplet = sentenceTripletList.get(index++);
                    String pos = singleAnalysis.getPos().getStringForm();
                    if (triplet.getPos() != null) { // pos = Apost
                        pos = triplet.getPos();
                    }
                    tripletList.add(new Triplet(triplet.getWord(),/* triplet.getMorphology(),*/ pos, triplet.getAnnotation()));
                } else { // words do not match, check prefix for matching
                    int position = norm_word.indexOf("'");
                    if (position > 0 && position < norm_sf.length()) {
                        String prefix = norm_word.substring(0, position);
                        String sub_sf = norm_sf.substring(0, position);
                        if (prefix.equals(sub_sf)) {
                            Triplet triplet = sentenceTripletList.get(index++);
                            String pos = singleAnalysis.getPos().getStringForm();
                            tripletList.add(new Triplet(triplet.getWord(), /* triplet.getMorphology(),*/ pos, triplet.getAnnotation()));
                        }
                    } else if (norm_sf.length() > 0 && clean_word.contains(norm_sf)) { // zemberek bazı lemmalarda ? koyuyor başına
                        Triplet triplet = sentenceTripletList.get(index++);
                        String pos = singleAnalysis.getPos().getStringForm();
                        tripletList.add(new Triplet(triplet.getWord(), /* triplet.getMorphology(),*/ pos, triplet.getAnnotation()));

                    } else {
                        System.out.println("Word - Surface Form mismatch!");
                        System.out.println(norm_word + " - " + norm_sf);
                    }


                }
            } else {
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
            br = new BufferedReader(new InputStreamReader(new FileInputStream(inputPath), StandardCharsets.UTF_8));

            String line = br.readLine();

            while (line != null) {

                line = Utils.replaceSequence(line, textProcessing.getCharacterMap());
                line = EmojiUtils.removeEmoji(line);
                line = line.replaceAll(REGEXSPACE, " "); // eliminate whitespaces

                //List<String> sentences = zemberek.extractSentences(line);
                //for (String sentence : sentences) {
                String sentence = line;
                String[] tokenArray = sentence.split(" ");

                if (tokenArray.length == 1 && tokenArray[0].equals("")) {
                } else {
                    boolean isNamedEntity = false;
                    String annotation = null;
                    int index = 0;
                    for (String token : tokenArray) {
                        if (token.isEmpty()) {
                            //System.out.println("Token is empty!");
                            continue;
                        }
                        /*
                        if (token.equals(".")) {
                            sentenceTripletList.add(new entity.Triplet(token, null, "O"));
                            updatePosTags(sentenceTripletList, tripletList); // find pos tag of each token
                            for (entity.Triplet triplet : tripletList) {
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
                         */

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
                            sentenceTripletList.add(new Triplet(token, pos, "O"));
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
        } finally {
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
            br = new BufferedReader(new InputStreamReader(new FileInputStream(inputPath), StandardCharsets.UTF_8));

            String line = br.readLine();
            while (line != null) {

                List<String> sentences = textProcessing.getZemberek().extractSentences(line);
                for (String sentence : sentences) {
                    List<Token> tokens = textProcessing.getZemberek().tokenizeSentence(sentence);
                    for (Token token : tokens) {
                        if (token.getText().startsWith("'") && token.getText().length() > 1) {
                            sentenceTripletList.add(new Triplet(token.getText(), "Apost", "O"));
                        } else {
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
        } finally {
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
    public void tokenizeContent(String inputPath, String outputPath, boolean onlyContent) {
        BufferedReader br = null;
        BufferedWriter bw = null;

        try {
            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8));
            br = new BufferedReader(new InputStreamReader(new FileInputStream(inputPath), StandardCharsets.UTF_8));

            String line = br.readLine(); // skip header
            while ((line = br.readLine()) != null) {
                String content = null, date = null, id = null, title = null;
                if (onlyContent) {
                    content = line;
                } else {
                    String[] args = line.split("\t");
                    id = args[0];
                    if (args.length < 4) {
                        System.out.println("Not enough args!: " + id);
                        continue;
                    }
                    date = args[1].replaceAll("\t", " ");
                    title = args[2];
                    content = args[3];
                }

                content = content.replaceAll(" ", " ");

                content = Utils.replaceSequence(content, textProcessing.getCharacterMap());
                content = EmojiUtils.removeEmoji(content);

                content = content.replaceAll(REGEXSPACE, " ");

                if (!content.trim().isEmpty()) { // condition for empty news
                    if (!onlyContent)
                        bw.write(id + "\t" + date + "\t" + title + "\t");

                    List<String> sentences = textProcessing.getZemberek().extractSentences(content);
                    for (String sentence : sentences) {
                        List<String> tokens = textProcessing.tokenizeSentence(sentence);
                        for (String token : tokens) {
                            bw.write(token + " ");
                        }

                    }
                    bw.write("\n");
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                bw.close();
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    // lemmatize tokenized content
    public void lemmatizeContent(String inputPath, String outputPath, boolean onlyContent) {
        BufferedWriter bw = null;
        BufferedReader br = null;
        try {
            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8));
            br = new BufferedReader(new InputStreamReader(new FileInputStream(inputPath), StandardCharsets.UTF_8));

            String line;
            while ((line = br.readLine()) != null) {
                String content, date = null, id = null, title = null;
                if (onlyContent)
                    content = line;
                else {
                    String[] args = line.split("\t");
                    id = args[0];
                    if (args.length < 4) {
                        System.out.println("Not enough args!: " + id);
                        continue;
                    }
                    date = args[1];
                    title = args[2];
                    content = args[3];

                    if (!content.trim().isEmpty())
                        bw.write(id + "\t" + date + "\t" + title + "\t");
                }

                content = Utils.replaceSequence(content, textProcessing.getCharacterMap());
                content = EmojiUtils.removeEmoji(content);


                List<String> sentenceList = textProcessing.getZemberek().extractSentences(content);
                for (String sentence : sentenceList) {
                    List<String> lemmas = textProcessing.lemmatizeSentence(sentence);
                    for (String lemma : lemmas) {
                        bw.write(lemma + " ");
                    }
                }
                bw.write("\n");
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                bw.close();
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // lemmatize output of CRF (prediction file)
    public void lemmatizePredictions(String inputPath, String outputPath) {
        BufferedWriter bw = null;
        BufferedReader br = null;

        Logger logger = Logger.getLogger("lemmatizePredictions");
        FileHandler fh;

        int count = 0;
        Pattern patternDot = Pattern.compile("[a-zA-Z].[a-zA-Z]");
        try {
            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8));
            br = new BufferedReader(new InputStreamReader(new FileInputStream(inputPath), StandardCharsets.UTF_8));

            // This block configure the logger with handler and formatter
            fh = new FileHandler("logs/lemmatizePredictions.log");
            logger.addHandler(fh);
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);
            logger.setUseParentHandlers(false);

            String line = null; //= br.readLine();
            StringBuilder sentence = new StringBuilder();
            List<String> annotationList = new ArrayList<>();
            List<String> tokenList = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                count++;

                // line = utility.Utils.replaceSequence(line, characterMap);

                if (!line.isEmpty()) { // contains token
                    String[] token_annotation = line.split("\t");

                    String token = token_annotation[0];
                    //bw.write(zemberek.lemmatize(token) + "\t" + token_annotation[1] + "\n");

                    int j = 0;
                    if (token.length() > 1) { // token is not a punctuation
                        token = token.replaceAll("'", "");
                        //token = token.replaceAll(".", "");
                    }

                    if (!token.isEmpty()) {
                        sentence.append(token + " ");
                        annotationList.add(token_annotation[1]);
                        tokenList.add(token.toLowerCase());
                    }
                } else { // end of sentence
                    if (sentence.toString().length() > 2) {
                        List<SingleAnalysis> singleAnalysisList = textProcessing.getZemberek().disambiguateSentence(sentence.toString());
                        int index = 0;
                        int tokenCnt = tokenList.size();
                        for (SingleAnalysis sa : singleAnalysisList) {
                            // /*
                            if (index < tokenCnt && sa.surfaceForm().equals(".") && !tokenList.get(index).equals(".")) {
                                //logger.info("Dot Mismatch! " + sentence.toString());
                                continue;
                            }
                            // */

                            if (index < tokenCnt) {
                                String lemma = sa.getDictionaryItem().normalizedLemma();//lemma;
                                if (lemma.isEmpty() || lemma.equalsIgnoreCase("UNK") || (lemma.equals("?") && !tokenList.get(index).equals("?"))) {
                                    //String log = "Unknown lemma! (Token - surface form)" + "\n" +
                                    //        tokenList.get(index) + " - " + sa.surfaceForm();
                                    //logger.info(log);
                                    lemma = tokenList.get(index);
                                }

                                //lemma = zemberek.normalizedString(lemma).toLowerCase();
                                lemma = lemma.trim();//.toLowerCase();
                                if (!lemma.isEmpty()) {
                                    bw.write(lemma + "\t" + annotationList.get(index++) + /* "\t" + sa.getPos() + */ "\n");
                                } else {
                                    System.out.println("Lemma is Empty!");
                                }

                            } else {
                                String log = "Index Out Of Bounds: " + sa.surfaceForm() + "\n" +
                                        "Sentence: " + sentence.toString() + "\n" +
                                        "Line No = " + count;

                                logger.info(log);
                                String lemma = sa.getDictionaryItem().normalizedLemma().trim();
                                bw.write(lemma + "\t" + "O" + /* "\t" + sa.getPos() + */ "\n");
                            }
                        }

                        sentence.delete(0, sentence.length());
                        annotationList.clear();
                        tokenList.clear();
                        bw.write("\n");
                    }
                }
                // line = br.readLine();
            }
        } catch (Exception e) {
            System.out.println(count);
            e.printStackTrace();
        } finally {
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

    // merge token and predicted annotation
    public void mergeCrfFiles(String crfInputPath, String predPath, String outputPath) {
        BufferedWriter bw = null;
        BufferedReader br = null;
        BufferedReader br2 = null;

        try {
            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8));
            br = new BufferedReader(new InputStreamReader(new FileInputStream(crfInputPath), StandardCharsets.UTF_8));
            br2 = new BufferedReader(new InputStreamReader(new FileInputStream(predPath), StandardCharsets.UTF_8));

            String line = br.readLine();
            String line2 = br2.readLine();

            while (line != null) {
                if (line.isEmpty()) {
                    bw.write("\n");
                } else {
                    String[] tokens1 = line.split("\t");
                    String[] tokens2 = line2.split("\t");
                    if (!tokens1[0].toLowerCase().equals(tokens2[0].toLowerCase())) {
                        System.out.println("Token - Prediction mismatch!");
                        System.out.println(tokens1[0] + " - " + tokens2[0]);
                        System.out.println("Annot: " + tokens2[1]);
                    }

                    bw.write(tokens1[0] + "\t" + tokens2[1] + "\n");
                }
                line2 = br2.readLine();
                line = br.readLine();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (bw != null) {
                    br.close();
                    br2.close();
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
        BufferedWriter dateWriter = null;
        BufferedReader bufferedReader = null;

        long count = 1;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(tokenizedContentPath), StandardCharsets.UTF_8));
            bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(predictionPath), StandardCharsets.UTF_8));
            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8));
            dateWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("neo4j\\dates3.csv"), StandardCharsets.UTF_8));

            bw.write("nid:ID,word,NamedEntity,date,enum\n");
            dateWriter.write("date,dbid\n");
            String line;// = br.readLine(); // skip header

            while ((line = br.readLine()) != null) {
                String[] args = line.split("\t");

                // /*
                String title = null, content = null, dateStr = null;
                Long id = count;
                if (args.length < 4) { // only content
                    content = line;
                } else {
                    id = Long.parseLong(args[0]);

                    if (id == 275867)
                        System.out.println(id);

                    dateStr = args[1].replaceAll("\t", " ");
                    int idx = dateStr.indexOf("T");
                    dateStr = dateStr.substring(0, idx != -1 ? idx : dateStr.length());

                    title = args[2];
                    content = args[3];
                }
                // */

                content = content.replace("\"", "'"); // necessary for neo4j

                List<String> sentences = textProcessing.getZemberek().extractSentences(content);

                News news = new News(id, dateStr, title, content);
                textProcessing.getNeo4jManager().getNewsList().add(news);

                List<NamedEntity> namedEntityList = new ArrayList<>();
                for (String sentence : sentences) {
                    //neo4jManager.findNamedEntityMatches(bufferedReader, news, zemberek);
                    if (!sentence.trim().isEmpty())
                        textProcessing.getNeo4jManager().getNamedEntities(bufferedReader, namedEntityList, true, sentence + " ||" + id);
                }

                for (NamedEntity ne : namedEntityList) {
                    String word = ne.getLabel();
                    if (!content.toLowerCase().contains(word)) {
                        System.out.println(id + " - " + word);
                    }
                    if (word.contains(",")) {
                        word = word.replaceAll(",", ".");
                    }
                    String s = id + "," + word + ',' + ne.getAnnotation() + ',' + dateStr + ',' + count + "\n";
                    bw.write(s);
                }

                if (namedEntityList.size() > 0) {
                    count++;
                    dateWriter.write(dateStr + ',' + id + "\n");
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                br.close();
                bufferedReader.close();
                bw.close();
                dateWriter.close();
            } catch (IOException e) {
            }

            System.out.println("Count = " + (count - 1));
        }
    }

    public static void main(String[] args) {
        String CRFPATH = "haberler-crf\\disambiguated-crf\\";
        String NEWSPATH = "haberler\\";
        String NEO4JPATH = "neo4j\\";

        String contentPath = NEWSPATH + "content_orig.txt";
        String tokenizedPath = NEWSPATH + "tokenized_db2.txt";
        String lemmatizedPath = NEWSPATH + "lemmatized_db2.txt";

        String crfInputPath = NEWSPATH + "haberler_crf_input.txt";
        String crfOutputPath = CRFPATH + "haberler_crf_output.txt";

        String rawNewsPath = NEWSPATH + "haberler_db2.txt";

        FileTokenizer ft = new FileTokenizer();

        // ft.transformItuEnamextoCRF("NERResources//IWT.MUClabeled", "iwt.txt");

        // You need to execute phases one by one because there is a dependecy between our NER module and this module.
        // phase 1: tokenize the content & transform the tokenized content into crf input format
        ft.tokenizeContent(rawNewsPath, tokenizedPath , false);
        ft.lemmatizeContent(tokenizedPath, lemmatizedPath, false);
        // ft.prepareCrfInput (tokenizedPath, crfInputPath);

        // phase 2: get nodes and relations from crf output
        // ft.prepareNeo4jInputFromCRF(tokenizedPath, crfOutputPath, NEO4JPATH + "neo4j_input_noun.txt");

    }
}