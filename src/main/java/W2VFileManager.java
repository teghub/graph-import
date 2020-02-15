import entity.NamedEntity;
import entity.Relation;
import utility.EmojiUtils;
import utility.Utils;
import zemberek.morphology.analysis.SingleAnalysis;
import zemberek.tokenization.Token;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;

public class W2VFileManager {

    private List<NamedEntity> namedEntityList;
    private List<Relation> relationList;
    private static String REGEXSPACE = "[\\p{Space}\\p{Blank}\\s+]";
    private static DecimalFormat df = new DecimalFormat("#,###,##0.00");

    private Map<String, Integer> wordMap; // word - id pairs are stored
    private Map<String, Integer> newsMap; // news_id - id pairs are stored

    private Map<String, Integer> frequencyMap;
    private boolean useLemma;

    public W2VFileManager() {
        namedEntityList = new ArrayList<>();
        relationList = new ArrayList<>();
        wordMap = new HashMap<>();
        newsMap = new HashMap<>();

        frequencyMap = new HashMap<>();
        useLemma = true;

    }

    public void readNodes(String inputPath, String outputPath) {
        BufferedWriter bw = null;
        BufferedReader br = null;

        try {
            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8));
            br = new BufferedReader( new InputStreamReader(new FileInputStream(inputPath), StandardCharsets.UTF_8));

            bw.write("nodeId:ID,:LABEL,description,annotation\n");
            String line = br.readLine();
            while (line != null) {
                String[] tokens = line.split("\t");
                NamedEntity ne = new NamedEntity(tokens[0], tokens[1]);
                namedEntityList.add(ne);

                bw.write(ne.getId() + ",\"" + ne.getLabel() + "\",\"" + ne.getLabel() + "\",\"" + ne.getAnnotation() + "\"\n");
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

    public void readRelations(String inputPath, String outputPath) {
        BufferedReader br = null;
        BufferedWriter bw = null;

        try {
            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8));
            br = new BufferedReader( new InputStreamReader(new FileInputStream(inputPath), StandardCharsets.UTF_8));

            bw.write(":START_ID,:TYPE,:END_ID,weight\n");

            String line = br.readLine();
            //DecimalFormat df = new DecimalFormat("#.###");
            int count = 1;
            while (line != null) {
                String[] tokens = line.split("\t");
                NamedEntity from = getNamedEntity(Long.parseLong(tokens[0]));
                NamedEntity to = getNamedEntity(Long.parseLong(tokens[1]));

                String weight;
                try {
                    weight = df.format(Double.parseDouble(tokens[2]));
                    weight = weight.replaceAll(",", ".");
                } catch (NumberFormatException e) {
                    weight = tokens[2];
                }
                Relation relation = new Relation(from, to, weight);
                relation.setWeight(weight);

                relationList.add(relation);

                //System.out.println(count++);
                bw.write(relation.getStart().getId() + "," + relation.getLabel() + "," + relation.getEnd().getId() + "," + relation.getWeight() + "\n");

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

    public NamedEntity getNamedEntity(Long id) {
        for (NamedEntity ne : namedEntityList) {
            if (ne.getId().equals(id)) {
                return ne;
            }
        }
        return null;
    }

    // function to sort hashmap by values
    public void sortByValueDesc() {
        // Create a list from elements of HashMap
        List<Map.Entry<String, Integer> > list =
                new LinkedList<Map.Entry<String, Integer> >(frequencyMap.entrySet());

        // Sort the list
        Collections.sort(list, new Comparator<Map.Entry<String, Integer> >() {
            public int compare(Map.Entry<String, Integer> o1,
                               Map.Entry<String, Integer> o2)
            {
                return (o2.getValue()).compareTo(o1.getValue());
            }
        });

        // put data from sorted list to hashmap
        HashMap<String, Integer> temp = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, Integer> aa : list) {
            temp.put(aa.getKey(), aa.getValue());
        }
        frequencyMap = temp;
    }

    public void initFreqMap(String inputPath) {
        BufferedReader br = null;
        String line;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(inputPath), StandardCharsets.UTF_8));

            while ((line = br.readLine()) != null) {
                frequencyMap.put(line.toLowerCase(), 0);
            }

        } catch (IOException e) {e.printStackTrace();}
        finally {
            try {
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // fill map with frequency of each noun and verb by lemmatizing the dataset
    public void setFrequencyMap(String inputPath) {
        BufferedReader br = null;
        String line;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(inputPath), StandardCharsets.UTF_8));
            TextProcessing textProcessing = new TextProcessing();
            while ((line = br.readLine()) != null) {
                String content=null, date=null, id=null, title=null;

                String[] args = line.split("\t");
                id = args[0];
                if (args.length < 4) {
                    System.out.println("Not enough args!: " + id);
                    continue;
                }
                date = args[1];
                title = args[2];
                content = args[3];

                content = Utils.replaceSequence(content, textProcessing.getCharacterMap());
                content = EmojiUtils.removeEmoji(content);
                content = content.replaceAll(REGEXSPACE, " ");

                List<String> sentenceList = textProcessing.getZemberek().extractSentences(content);
                for (String sentence : sentenceList) {
                    List<SingleAnalysis> singleAnalysisList = textProcessing.getZemberek().disambiguateSentence(sentence);
                    for (SingleAnalysis singleAnalysis : singleAnalysisList) {
                        if (singleAnalysis.getPos().getStringForm().equalsIgnoreCase("noun") ||
                                singleAnalysis.getPos().getStringForm().equalsIgnoreCase("verb")) {
                            String lemma;
                            if (useLemma) { // use lemma
                                if (singleAnalysis.getPos().getStringForm().equalsIgnoreCase("verb")) { // take into account -mek -mak suffixes
                                    lemma = singleAnalysis.getDictionaryItem().lemma;
                                }
                                else {
                                    lemma = singleAnalysis.getDictionaryItem().normalizedLemma();
                                }
                                if (lemma.isEmpty() || lemma.equals("UNK") || lemma.contains("?")) { // unknown lemma
                                    lemma = singleAnalysis.surfaceForm();
                                }
                            }
                            else { // use tokens instead of lemma
                                lemma = singleAnalysis.surfaceForm();
                            }
                            if (frequencyMap.get(lemma) == null) {
                                frequencyMap.put(lemma, 1);
                            } else {
                                frequencyMap.put(lemma, frequencyMap.get(lemma) + 1);
                            }
                        }
                    }
                }
            }

        } catch (IOException e) {e.printStackTrace();}
        finally {
            try {
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // fill frequency map by using the lemmatized dataset
    public void setFrequencyMap2(String inputPath) {
        initFreqMap("word2vec\\data\\w2v_entries.txt");
        BufferedReader br = null;
        String line;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(inputPath), StandardCharsets.UTF_8));
            TextProcessing textProcessing = new TextProcessing();
            while ((line = br.readLine()) != null) {
                String content=null, date=null, id=null, title=null;

                String[] args = line.split("\t");
                id = args[0];
                if (args.length < 4) {
                    System.out.println("Not enough args!: " + id);
                    continue;
                }
                date = args[1];
                title = args[2];
                content = args[3];

                List<String> sentenceList = textProcessing.getZemberek().extractSentences(content);
                for (String sentence : sentenceList) {
                    List<Token> tokenList = textProcessing.getZemberek().tokenizeSentence(sentence);
                    for (Token token : tokenList) {
                        String word = token.getText().toLowerCase();
                        if(frequencyMap.get(word) != null) {
                            frequencyMap.put(word, frequencyMap.get(word) + 1);
                        }
                    }
                }
            }

        } catch (IOException e) {e.printStackTrace();}
        finally {
            try {
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // remove the entries that occur less than the wordLimit
    public void limitFrequencyMap(int wordLimit) {
        sortByValueDesc(); // sort frequencyMap descending by value
        int cnt = 0;
        HashMap<String, Integer> tmp = new HashMap<>();
        for (Map.Entry<String, Integer> entry : frequencyMap.entrySet()) {
            if (entry.getKey().length() > 1) {
                if (cnt++ == wordLimit) {
                    break;
                }
                tmp.put(entry.getKey(), entry.getValue());
            }
        }

        frequencyMap = tmp;
    }

    public void writeFrequencyMap(String outputPath) {
        BufferedWriter bw = null;

        try {
            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8));

            for (Map.Entry<String, Integer> entry : frequencyMap.entrySet()) {
                bw.write(entry.getKey() + "\t" + entry.getValue() + "\n");
            }

        }
        catch (IOException e){
            e.printStackTrace();
        } finally {
            try {
                if (bw != null)  bw.close();
            } catch (IOException e) { e.printStackTrace();}
        }
    }

    public void readFrequencyMap(String inputPath) {
        BufferedReader br = null;

        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(inputPath), StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                String[] args = line.split("\t");
                frequencyMap.put(args[0], Integer.parseInt(args[1]));
            }
            br.close();
        } catch (IOException e) {e.printStackTrace();}

    }

    // Retrieve unique nouns from news.  If useLemma is set, retrieve lemmatized nouns .
    public void prepareW2VInput(String inputPath, String outputPath) {
        BufferedWriter bw = null;
        BufferedWriter nounWriter = null;
        BufferedReader br = null;
        try {
            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8));
            nounWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("word2vec\\data\\noun_verb_set_lemmatized.txt"), StandardCharsets.UTF_8));
            br = new BufferedReader(new InputStreamReader(new FileInputStream(inputPath), StandardCharsets.UTF_8));

            String line;
            TextProcessing textProcessing = new TextProcessing();
            HashSet<String> nounSet = new HashSet<>();
            while ((line = br.readLine()) != null) {
                String content=null, date=null, id=null, title=null;

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


                content = Utils.replaceSequence(content, textProcessing.getCharacterMap());
                content = EmojiUtils.removeEmoji(content);
                content = content.replaceAll(REGEXSPACE, " ");

                List<String> sentenceList = textProcessing.getZemberek().extractSentences(content);
                for (String sentence : sentenceList) {
                    List<SingleAnalysis> singleAnalysisList = textProcessing.getZemberek().disambiguateSentence(sentence);
                    StringBuilder sb = new StringBuilder();
                    for (SingleAnalysis singleAnalysis : singleAnalysisList) {
                        String pos = singleAnalysis.getPos().getStringForm();

                        if (pos.equalsIgnoreCase("punc") || singleAnalysis.surfaceForm().equals("'")) // remove punctuations
                            continue;

                        if (useLemma) { // use lemma
                            String lemma = singleAnalysis.getDictionaryItem().normalizedLemma();
                            if (lemma.isEmpty() || lemma.equals("UNK") || lemma.contains("?")) { // unknown lemma
                                lemma = singleAnalysis.surfaceForm();
                            }

                            lemma = lemma.trim();
                            if (!lemma.isEmpty()) {
                                sb.append(lemma + " ");
                            }

                            if (pos.equalsIgnoreCase("noun") || pos.equalsIgnoreCase("verb")) {
                                nounSet.add(lemma);
                            }
                        }
                        else { // use tokens instead of lemma
                            sb.append(singleAnalysis.surfaceForm() + " ");

                            if (pos.equalsIgnoreCase("noun") || pos.equalsIgnoreCase("verb")) {
                                nounSet.add(singleAnalysis.surfaceForm());
                            }
                        }
                    }

                    bw.write(sb.toString() + ". ");
                    sb.delete(0, sb.length());

                }
                bw.write("\n");
            }

            for (String noun : nounSet) {
                nounWriter.write(noun + "\n");
            }



        } catch (IOException e) {e.printStackTrace();}
        finally {
            try {
                bw.close();
                nounWriter.close();
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void createNodesandRelations() {
        BufferedReader br = null;
        BufferedWriter bw = null;
        BufferedWriter bw_relation = null;
        try {
            // create word nodes
            br = new BufferedReader(new InputStreamReader(new FileInputStream("word2vec\\data\\w2v_entries.txt"), StandardCharsets.UTF_8));
            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("word2vec\\neo4j\\words.csv"), StandardCharsets.UTF_8));

            bw.write("wid:ID,:LABEL,word\n");
            String line;
            Integer idIterator = 1;
            while ((line = br.readLine()) != null) {
                //if (frequencyMap.get(line.toLowerCase()) != null) {
                    bw.write(idIterator + ",Word," + line.toLowerCase() + "\n");
                    wordMap.put(line.toLowerCase(), idIterator++);
                //}
            }

            br.close();
            bw.close();

            // create news nodes and create relationship between words - news
            br = new BufferedReader(new InputStreamReader(new FileInputStream("word2vec\\data\\haberler_db2.txt"), StandardCharsets.UTF_8));
            bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("word2vec\\neo4j\\news.csv"), StandardCharsets.UTF_8));
            bw_relation = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("word2vec\\neo4j\\nwr.csv"), StandardCharsets.UTF_8));


            bw_relation.write(":START_ID,:TYPE,:END_ID,weight\n");
            bw.write("nid:ID,:LABEL,description,date\n");
            TextProcessing textProcessing = new TextProcessing();
            line = br.readLine(); // skip header
            while ((line = br.readLine()) != null) {
                String[] args = line.split("\t");
                String id = args[0];
                if (args.length < 4) {
                    System.out.println("Not enough args!: " + id);
                } else {
                    String date = args[1];
                    bw.write(idIterator + ",News," + id + "," + date + "\n");
                    newsMap.put(id, idIterator++);

                    String content = args[3];

                    content = Utils.replaceSequence(content, textProcessing.getCharacterMap());
                    content = EmojiUtils.removeEmoji(content);
                    content = content.replaceAll(REGEXSPACE, " ");

                    Map<String, Integer> wordFreqMap = new HashMap<>(); // word_id - freq pairs
                    List<String> sentenceList = textProcessing.getZemberek().extractSentences(content);
                    for (String sentence : sentenceList) {
                        List<SingleAnalysis> singleAnalysisList = textProcessing.getZemberek().disambiguateSentence(sentence);
                        for (SingleAnalysis singleAnalysis : singleAnalysisList) {
                            String pos = singleAnalysis.getPos().getStringForm();

                            String lemma = singleAnalysis.getDictionaryItem().normalizedLemma().toLowerCase().trim();
                            if (pos.equalsIgnoreCase("verb")) {
                                lemma = singleAnalysis.getDictionaryItem().lemma.toLowerCase().trim();
                            }
                            if (pos.equalsIgnoreCase("noun") || pos.equalsIgnoreCase("verb")) {
                                if (wordMap.containsKey(lemma.toLowerCase())) {

                                    if (wordFreqMap.keySet().contains(lemma)) {
                                        wordFreqMap.put(lemma, wordFreqMap.get(lemma) + 1);
                                    } else {
                                        wordFreqMap.put(lemma, 1);
                                    }

                                }
                            }
                        }
                    }
                    for (String word : wordFreqMap.keySet()) {
                        bw_relation.write(newsMap.get(id) + ",CONTAINS," + wordMap.get(word) + "," + wordFreqMap.get(word) + "\n");
                        //bw_relation.write(newsMap.get(id) + ",CONTAINS," + wordMap.get(lemma) + ",1.0\n");
                    }
                }
            }


            br.close();
            bw.close();
            bw_relation.close();

            // create word to word relations using word2vec weights
            br = new BufferedReader(new InputStreamReader(new FileInputStream("word2vec\\data\\similarities_t60_lemma.txt"), StandardCharsets.UTF_8));
            bw_relation = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("word2vec\\neo4j\\wwr.csv"), StandardCharsets.UTF_8));

            bw_relation.write(":START_ID,:TYPE,:END_ID,weight\n");

            while ((line = br.readLine()) != null) {
                String[] args = line.split("\t");
                if (args.length < 3) {
                    System.out.println("Not enough args!: " + line);
                }
                String start = args[0];
                String end = args[1];
                Double weight = Double.parseDouble(args[2]);
                if (weight > 0.6 && wordMap.get(args[0]) != null && wordMap.get(args[1]) != null )
                    bw_relation.write(wordMap.get(args[0]) + ",CosineSim," + wordMap.get(args[1]) + ",\"" + df.format(weight) + "\"\n");
            }

        } catch (IOException e) {e.printStackTrace();}
        finally {
            try {
                br.close();
                bw.close();
                bw_relation.close();
            } catch (IOException e) {e.printStackTrace();}
        }
    }


    public static void main(String[] args) {
        W2VFileManager w2v = new W2VFileManager();
        //w2v.readNodes("word2vec\\named_entities_10K.txt", "word2vec\\nodes_10k.csv");
        //w2v.readRelations("word2vec\\similarities_10k_70t.txt", "word2vec\\relations_10k.csv");
       // w2v.prepareW2VInput("haberler\\tokenized_db.txt", "word2vec\\w2v_input_lemmatized.txt");

       // w2v.createNodesandRelations();
        // /*
        //w2v.setFrequencyMap("word2vec\\data\\w2v_input.txt");
        //System.out.println("Lemmatization is completed!");
        //w2v.writeFrequencyMap("word2vec\\data\\w2v_entries_freq.txt");
        //System.out.println("Entries had written!");
        //w2v.limitFrequencyMap(1000);
        //w2v.readFrequencyMap("word2vec\\data\\w2v_entries_freq.txt");
        w2v.createNodesandRelations();
        // */
        // */
        /*
        TextProcessing tp = new TextProcessing();
        String sentence = "Ankara'ya ulaşım için yola çıktım.";
        List<SingleAnalysis> singleAnalysisList = tp.getZemberek().disambiguateSentence(sentence);

        for (SingleAnalysis sa : singleAnalysisList) {
            System.out.println(sa.getDictionaryItem().normalizedLemma() + " || " + sa.getDictionaryItem().lemma);
        }
        */
        /*
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream("C:\\Users\\frat1\\PycharmProjects\\word2vec\\trwiki-20191201-pages-articles-multistream.xml"), StandardCharsets.UTF_8));
            String line;
            int cnt = 0;
            while ((line = br.readLine()) != null) {
                System.out.println(line);

                if (cnt++ > 20)
                    break;
            }

            br.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
        */

    }
}
