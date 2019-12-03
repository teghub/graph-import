import zemberek.core.logging.Log;
import zemberek.core.turkish.Turkish;
import zemberek.morphology.TurkishMorphology;
import zemberek.morphology.analysis.SentenceAnalysis;
import zemberek.morphology.analysis.SingleAnalysis;
import zemberek.morphology.analysis.WordAnalysis;

import zemberek.morphology.lexicon.RootLexicon;
import zemberek.ner.*;
import zemberek.normalization.TurkishSpellChecker;
import zemberek.tokenization.Token;
import zemberek.tokenization.TurkishSentenceExtractor;
import zemberek.tokenization.TurkishTokenizer;
import zemberek.tokenization.antlr.TurkishLexer;
import zemberek.ner.NerDataSet.AnnotationStyle;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.*;

public class Zemberek {
    private TurkishMorphology morphology;
    private TurkishSentenceExtractor extractor;
    private TurkishTokenizer tokenizer;
    private TurkishSpellChecker spellChecker;

    public Zemberek() {
        morphology = TurkishMorphology.builder()
                .setLexicon(RootLexicon.getDefault())
                .useInformalAnalysis()
                .build();
        extractor = TurkishSentenceExtractor.DEFAULT;
        tokenizer = TurkishTokenizer.DEFAULT;
        try {
            spellChecker = new TurkishSpellChecker(morphology);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Split text into sentences
    public List<String> extractSentences(String text) { return extractor.fromParagraph(text);}
    // Tokenize the sentence
    public List<Token> tokenizeSentence(String sentence) { return tokenizer.tokenize(sentence);}
    // Check the sentence and correct misspelled words
    public String normalizeSentence(String sentence) {
        StringBuilder output = new StringBuilder();
        List<Token> tokens = tokenizeSentence(sentence);
        for (Token token : tokens) {
            String text = token.getText();
            if (analyzeToken(token) && !spellChecker.check(text)) {
                List<String> strings = spellChecker.suggestForWord(token.getText());
                if (!strings.isEmpty()) {
                    String suggestion = strings.get(0);
                    Log.info("Correction: " + text + " -> " + suggestion);
                    output.append(suggestion);
                } else {
                    output.append(text);
                }
            } else {
                output.append(text);
            }
        }
        return output.toString();
    }

    public boolean analyzeToken(Token token) {
        return token.getType() != Token.Type.NewLine
                && token.getType() != Token.Type.SpaceTab
                && token.getType() != Token.Type.UnknownWord
                && token.getType() != Token.Type.RomanNumeral
                && token.getType() != Token.Type.Unknown;
    }
    // Perform morphological analysis after resolving ambiguity
    public List<SingleAnalysis> disambiguateSentence (String sentence) {
        List<WordAnalysis> wordAnalysisList = morphology.analyzeSentence(sentence);
        SentenceAnalysis sentenceAnalysis = morphology.disambiguate(sentence, wordAnalysisList);
        return sentenceAnalysis.bestAnalysis();
    }

    // Perform morphological analysis after resolving ambiguity
    public WordAnalysis analyzeWord (String word) {
        return morphology.analyze(word);
    }
    // load NER model
    public List<zemberek.ner.NamedEntity> extractNamedEntities(String _modelPath, String sentence) {
        List<zemberek.ner.NamedEntity> namedEntities = null;
        try {
            Path modelRoot = Paths.get(_modelPath);
            PerceptronNer ner = PerceptronNer.loadModel(modelRoot, morphology);

            NerSentence result = ner.findNamedEntities(sentence);
            namedEntities = result.getNamedEntities();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return namedEntities;
    }
    // save NER model
    public void generateModel(String _trainPath, String _testPath, String _modelPath) {
        try {
            Path trainPath = Paths.get(_trainPath);
            Path testPath = Paths.get(_testPath);
            Path modelRoot = Paths.get(_modelPath);

            NerDataSet trainingSet = NerDataSet.load(trainPath, AnnotationStyle.ENAMEX);
            trainingSet.info(); // prints information

            NerDataSet testSet = NerDataSet.load(testPath, AnnotationStyle.ENAMEX);
            testSet.info();

            // Training occurs here. Result is a PerceptronNer instance.
            // There will be 7 iterations with 0.1 learning rate.
            PerceptronNer ner = new PerceptronNerTrainer(morphology)
                    .train(trainingSet, testSet, 7, 0.1f);

            Files.createDirectories(modelRoot);
            ner.saveModelAsText(modelRoot);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void printLemmas(String sentence) {
        List<SingleAnalysis> singleAnalysisList = morphology.analyzeAndDisambiguate(sentence).bestAnalysis();
        for (SingleAnalysis singleAnalysis : singleAnalysisList) {
            List<String> lemmas = singleAnalysis.getLemmas();
            System.out.println(singleAnalysis.surfaceForm());
            for (String lemma : lemmas) {
                System.out.println(lemma);
            }
            System.out.println("---------------------------------------------------------------");
        }
    }

    public String lemmatize(String word) {
        // return word;

        String lemma;
        try {
            // lemma = analyzeWord(word).getAnalysisResults().get(0).getLemmas().get(0);
            lemma = analyzeWord(word).getAnalysisResults().get(0).getDictionaryItem().normalizedLemma();
            if (lemma.equals("UNK")) {
                lemma = word;
            }
        } catch (Exception e) {
            //System.out.println("Can't lemmatize!: " + word);
            lemma = word;
        }

        return lemma.toLowerCase();

    }

}
