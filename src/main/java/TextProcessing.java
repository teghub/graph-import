import zemberek.morphology.analysis.SingleAnalysis;
import zemberek.tokenization.Token;

import java.io.BufferedReader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextProcessing {

    private Zemberek zemberek;
    private Neo4jManager neo4jManager;

    private static String REGEXPUNCT = "[\\p{Punct}\\p{IsPunctuation}]";
    private static String REGEXSPACE = "[\\p{Space}\\p{Blank}\\s+]";
    private static Pattern PATTERNAPOST =  Pattern.compile( "[\\pL\\pN]'.*"/*"[a-zA-Z0-9]'.*"*/);//Pattern.compile("[a-zA-Z]'.*");
    private static Pattern PATTERNDIGIT = Pattern.compile("[\\d+]'.*");
    private static Pattern APOST = Pattern.compile("^'.*");

    private HashMap<String, String> characterMap;

    public TextProcessing() {
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
        characterMap.put("■", "");


        characterMap.put("http.*?\\s", " ");
        //characterMap.put("http.*", "");
        characterMap.put("www.*?\\s", " ");
        characterMap.put("\\s*?.com", " ");
        characterMap.put("#.*?\\s", " ");
        characterMap.put("@.*?\\s", " ");
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

    public Zemberek getZemberek() {
        return zemberek;
    }

    public Neo4jManager getNeo4jManager() {
        return neo4jManager;
    }

    public HashMap<String, String> getCharacterMap() {
        return characterMap;
    }

    // normalizeSentence unicode characters
    public static String normalizedString(String word) {
        ///*
        String str = Normalizer.normalize(word, Normalizer.Form.NFD);
        str = str.replaceAll("\\p{Mn}", "");
        return str.toUpperCase();
        //*/
        // return word.toLowerCase(Turkish.LOCALE);
    }

    // Return tokens as a list
    public List<String> tokenizeSentence(String sentence) {
        List<Token> tokens = zemberek.tokenizeSentence(sentence);
        List<String> results = new ArrayList<>();
        for (Token token : tokens) {
            String tokenStr = token.getText().trim();
            if (tokenStr.startsWith("'") && tokenStr.length() > 1) {
                tokenStr = "' " + token.getText().substring(1);
            }
            boolean isMatched = false;
            Matcher matcher = PATTERNAPOST.matcher(tokenStr); // check whether token contains an apostrophe
            while (matcher.find()) {
                isMatched = true;
                //Prints the start index of the match. Split token according to the position apostrophe
                String prefix = tokenStr.substring(0, matcher.start() + 1);
                String suffix = tokenStr.substring(matcher.start() + 2);

                //bw.write(prefix + " ' " + suffix + " ");
                results.add(prefix);
                results.add("'");
                results.add(suffix);
            }
            if (!isMatched) {
                results.add(tokenStr);
            }
        }

        return results;
    }

    // Return lemmas as a list
    public List<String> lemmatizeSentence(String sentence) {
        List<String> lemmas = new ArrayList<>();
        List<SingleAnalysis> singleAnalysisList = zemberek.disambiguateSentence(sentence);
        for (SingleAnalysis singleAnalysis : singleAnalysisList) {
            String lemma = singleAnalysis.getDictionaryItem().normalizedLemma();//lemma;
            if (lemma.isEmpty() || lemma.equals("UNK") || lemma.contains("?")) { // unknown lemma
                //System.out.println("UnKnown Lemma! " + lemma + " || Surface Form: " + singleAnalysis.surfaceForm());
                lemma = singleAnalysis.surfaceForm();
            }

            lemma = lemma.trim();
            if (!lemma.isEmpty()) {
                lemmas.add(lemma);
            }
        }

        return lemmas;
    }
}
