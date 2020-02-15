import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TfIdfManager {

    private static String REGEXSPACE = "[\\p{Space}\\p{Blank}\\s+]";
    private static String REGEXPUNCT = "[\\p{Punct}\\p{IsPunctuation}]";
    private static DecimalFormat df = new DecimalFormat("#,###,##0.000");

    private static int LINELIMIT = 11998;

    List<List<String>> docs; // list of documents
    Map<String, Double> scoreMap; // term - tf score
    Map<String, Integer> termCntMap; // term - number of occurence of the term in the document

    List<String> newsList;

    public TfIdfManager() {
        docs = new ArrayList<>();

        scoreMap = new HashMap<>();
        termCntMap = new HashMap<>();

        newsList = new ArrayList<>();
    }

    public List<List<String>> getDocs() {
        return docs;
    }

    public Map<String, Double> getScoreMap() {
        return scoreMap;
    }

    // fill docs with words.
    public void setDocs(String inputPath) {
        BufferedReader br = null;
        int lineNo = 0;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(inputPath), StandardCharsets.UTF_8));
            String line;

            while ((line = br.readLine()) != null) {
                //if (lineNo++ == LINELIMIT) {System.out.println(line); break;}

                String[] args = line.split("\t");
                String id = args[0];
                if (args.length < 4) {
                    System.out.println("Not enough args!: " + id);
                    continue;
                }
                String content = args[3].toLowerCase().trim();
                content = content.replaceAll(REGEXPUNCT, "");
                //content = content.replaceAll(REGEXSPACE, " ");

                String[] terms = content.split(" ");
                List<String> doc = Arrays.asList(terms);

                docs.add(doc);

                newsList.add(line);
            }

        } catch (IOException e) {e.printStackTrace();}
        finally {
            try {
                br.close();
            } catch (IOException e) {e.printStackTrace();}
        }
    }

    /**
     * @param term String represents a term
     * @return the inverse term frequency of term in documents
     */
    public double idf(String term) {
        int n = termCntMap.get(term);
        return Math.log(docs.size() / (1.0 + n));
    }

    // calculate tf-idf score of each term and store the result in scoreMap
    public void tfIdf() {
        for (List<String> doc : docs) {

            Map<String, Integer> docTermCntMap = new HashMap<>();
            for (String term : doc) {
                if (term.equalsIgnoreCase("")) continue;

                // calculate term frequency in each document
                Integer count = docTermCntMap.get(term);
                docTermCntMap.put(term, (count==null) ? 1 : count+1);
            }
            if (docTermCntMap.size() == 0) continue;
            int max = Collections.max(docTermCntMap.values()); // number of occurence of the most frequent term in the doc

            for (String term : docTermCntMap.keySet()) {
                // calculate tf score and store the max tf of each term
                double tf = 0.5 + 0.5 * docTermCntMap.get(term) / max;

                scoreMap.putIfAbsent(term, 0.0d);
                double score = tf;//*idf;

                if (scoreMap.get(term) < score)
                    scoreMap.put(term, score);

                // how many docs contain the term
                Integer count = termCntMap.get(term);
                termCntMap.put(term, (count==null) ? 1 : count+1);
            }

        }

        // calculate idf score of each term
        for (Map.Entry<String, Double> entry : scoreMap.entrySet()) {
            double score = entry.getValue()*idf(entry.getKey());
            scoreMap.put(entry.getKey(), score);
        }
    }


    public void pruneWords(double limit) {
        // prune words whose tfidf scores are less than the limit. Additionally, prune words that contain numbers
        Map<String, Double> temp = new HashMap<>();
        for (Map.Entry<String, Double> entry : scoreMap.entrySet()) {
            if (limit <= entry.getValue() && !isNumeric(entry.getKey())) {
                temp.put(entry.getKey(), entry.getValue());
            }
        }

        scoreMap = temp;
    }

    public void prepareNeo4jInput(String outputPath) throws IOException {
        // prepare input file that will be fed into Neo4j
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8));

        bw.write("nid:ID,word,score,date,enum\n");
        int lineNo = 0;
        for (String line : newsList) {
            lineNo++;
            String[] args = line.split("\t");

            String id = args[0];
            if (args.length < 4) {
                System.out.println("Not enough args!: " + id);
                continue;
            }
            String content = args[3].toLowerCase().trim();

            String[] words = content.split(" ");

            for (String word : words) {
                if (scoreMap.get(word) != null) {
                    bw.write(id + "," + word + "," + df.format(scoreMap.get(word)).replace(",", ".") + "," + args[1] + "," + lineNo + "\n");
                }
            }

        }

        bw.close();
    }

    public static boolean isNumeric(String str) {
        for (char c : str.toCharArray()) {
            if (!Character.isDigit(c)) return false;
        }
        return true;
    }


    public static void main(String[] args) throws IOException{

        TfIdfManager tif = new TfIdfManager();

        tif.setDocs("haberler\\lemmatized_db2.txt");
        System.out.println("Calling tfIdf()!");
        tif.tfIdf();
        System.out.println("Pruning!");
        tif.pruneWords(4.5);
        System.out.println("Preparing input!");
        tif.prepareNeo4jInput("neo4j\\neo4j_input_tfidf_v2.csv");


        ///*
        // LinkedHashMap<String, Double> sortedMap = new LinkedHashMap<>();
        //tif.getScoreMap().entrySet().stream().sorted(Map.Entry.comparingByValue())
        //        .forEachOrdered(x -> sortedMap.put(x.getKey(), x.getValue()));

        LinkedHashMap<String, Double> reverseSortedMap = new LinkedHashMap<>();
        tif.getScoreMap().entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .forEachOrdered(x -> reverseSortedMap.put(x.getKey(), x.getValue()));


        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("tf_idf.txt"), StandardCharsets.UTF_8));

        for (Map.Entry<String, Double> entry : reverseSortedMap.entrySet()) {
            bw.write(entry.getKey() + "\t" +  df.format(entry.getValue()) + "\n");
        }

        bw.close();
        //*/


    }

}
