import entity.NamedEntity;
import entity.News;
import entity.Node;
import entity.Relation;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Neo4jManager {

    private Map<Long, List<Relation>> relationMap; // NE - relations
    private Map<String, Set<Long>> hyperRelationMap; // Annotation - NE
    private List<News> newsList;
    private Map<String, List<NamedEntity>> namedEntityMap;
    private Map<String, Node> hyperNodeMap;
    private Map<Long, Map<String, Set<Long>>> newsHyperMap; // newsID - annotation - set of NEs

    private int model = 1; // 1 : ME, 2: FG-AM

    public Neo4jManager() {
        relationMap = new HashMap<>();
        newsList = new ArrayList<>();
        newsHyperMap = new HashMap<>();
        namedEntityMap = new HashMap<>();

        namedEntityMap.put("PERSON", new ArrayList<>());
        namedEntityMap.put("LOCATION", new ArrayList<>());
        namedEntityMap.put("ORGANIZATION", new ArrayList<>());
        namedEntityMap.put("DATE", new ArrayList<>());
        namedEntityMap.put("TIME", new ArrayList<>());
        namedEntityMap.put("MONEY", new ArrayList<>());
        namedEntityMap.put("PERCENT", new ArrayList<>());

        namedEntityMap.put("O", new ArrayList<>());

        if (model == 1) {
            hyperNodeMap = new HashMap<>();
            hyperNodeMap.put("PERSON", new Node("PERSON"));
            hyperNodeMap.put("LOCATION", new Node("LOCATION"));
            hyperNodeMap.put("ORGANIZATION", new Node("ORGANIZATION"));
            hyperNodeMap.put("DATE", new Node("DATE"));
            hyperNodeMap.put("TIME", new Node("TIME"));
            hyperNodeMap.put("MONEY", new Node("MONEY"));
            hyperNodeMap.put("PERCENT", new Node("PERCENT"));

            hyperRelationMap = new HashMap<>();
            hyperRelationMap.putIfAbsent("PERSON", new HashSet<>());
            hyperRelationMap.putIfAbsent("LOCATION", new HashSet<>());
            hyperRelationMap.putIfAbsent("ORGANIZATION", new HashSet<>());
            hyperRelationMap.putIfAbsent("DATE", new HashSet<>());
            hyperRelationMap.putIfAbsent("TIME", new HashSet<>());
            hyperRelationMap.putIfAbsent("MONEY", new HashSet<>());
            hyperRelationMap.putIfAbsent("PERCENT", new HashSet<>());
        }

    }

    public List<News> getNewsList() { return newsList;}

    // write nodes and relations into files
    public void writeNodesAndRelations(String nodePath, String relationPath) {
        BufferedWriter nodeWriter = null;
        BufferedWriter relationWriter = null;
        try {
            nodeWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(nodePath), StandardCharsets.UTF_8));
            nodeWriter.write("nodeId:ID,:LABEL,description,content\n");

            relationWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(relationPath), StandardCharsets.UTF_8));
            relationWriter.write(":START_ID,:TYPE,:END_ID\n");

            Map<Long, List<Node>> hyperMap = new HashMap<>();
            for(Map.Entry<Long, Map<String, Set<Long>>> entry : newsHyperMap.entrySet()) {
                hyperMap.putIfAbsent(entry.getKey(), new ArrayList<>());
                // RELATIONS
                for (Map.Entry<String, Set<Long>> subEntry : entry.getValue().entrySet()) {
                    if (model == 1) {
                        for (Long id : subEntry.getValue()) { // relation
                            String str = entry.getKey() + "," + "CONTAINS" + "," + id + "\n"; // news - namedEntity relation
                            relationWriter.write(str);
                        }
                    }
                    else if (model == 2) {
                        Node node = null;
                        boolean isExists = false;
                        for (Node n : hyperMap.get(entry.getKey())) {
                            if (n.getLabel().equals(subEntry.getKey())) {
                                node = n;
                                isExists = true;
                                break;
                            }
                        }
                        if (!isExists) {
                            node = new Node(subEntry.getKey()); // hyperNode: annotations
                            hyperMap.get(entry.getKey()).add(node);
                        }


                        for (Long id : subEntry.getValue()) { // relation
                            String str = node.getId() + "," + "IS" + "," + id + "\n"; // hyperNode - namedEntity relation
                            relationWriter.write(str);
                        }
                        String str = entry.getKey() + "," + "CONTAINS" + "," + node.getId() + "\n"; // news - hyperNode relation
                        relationWriter.write(str);
                    }
                }
            }
            if (model == 1) {
                for (Map.Entry<String, Set<Long>> entry : hyperRelationMap.entrySet()) {
                    for (Long id : entry.getValue()) {
                        String str = id + "," + "IS" + "," + hyperNodeMap.get(entry.getKey()).getId() + "\n"; // namedEntity - hyperNode relation
                        relationWriter.write(str);
                    }
                }
                // HYPER NODE in model 1
                for(Map.Entry<String, Node> entry : hyperNodeMap.entrySet()) {
                    Node node = entry.getValue();
                    String str = node.getId() + "," + node.getLabel() + ",\"" + node.getLabel() + "\",\"" + entry.getKey() + "\"\n";
                    nodeWriter.write(str);
                }
            }


            if (model == 2) {
                // HYPER NODE in model 2
                for(Map.Entry<Long, List<Node>> entry : hyperMap.entrySet()) {
                    for (Node node : entry.getValue()) {
                        String str = node.getId() + "," + node.getLabel() + ",\"" + node.getLabel() + "\",\"" + entry.getKey() + "\"\n";
                        nodeWriter.write(str);
                    }
                }
            }

            // NODES
            for(Map.Entry<String, List<NamedEntity>> entry : namedEntityMap.entrySet()) {
                for (NamedEntity namedEntity : entry.getValue()) {
                    String str = namedEntity.getId() + "," + namedEntity.getAnnotation() + ",\"" + namedEntity.getLabel() + "\",\"" + namedEntity.getAnnotation() + "\"\n";
                    nodeWriter.write(str);
                }
            }
            for(News news : newsList) {
                String str = news.getId() + "," + news.getLabel() + "," + news.getId() + "," + news.getContent() + "\n";
                nodeWriter.write(str);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                nodeWriter.close();
                relationWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    // get the named entity with the given word and tag
    private NamedEntity getNamedEntity(String label, String annotation) {
        List<NamedEntity> namedEntityList = namedEntityMap.get(annotation);
        for (NamedEntity namedEntity : namedEntityList) {
            if (namedEntity.getLabel().equals(label)) {
                return namedEntity;
            }
        }
        return null;

    }

    // find named entities from sentences
    public void findNamedEntityMatches(BufferedReader br, News sourceNews, Zemberek zemberek) throws IOException{
        newsHyperMap.putIfAbsent(sourceNews.getId(), new HashMap<>());
        String line = br.readLine();
        StringBuilder sb = new StringBuilder();
        String annot = null;
        while(line != null && !line.isEmpty() /*!line.contains("<S>")*/ ) {
            String words[] = line.split("\t");
            String annotation = words[1];
            boolean eone = false;
            if (!annotation.equals("O") && !words[0].isEmpty()) { // named entity
                if (annot == null || annot.equals(annotation)) { // part of current named entity
                    //String lemma = zemberek.disambiguateSentence(words[0]).get(0).getDictionaryItem().lemma;

                    String lemma = zemberek.lemmatize(words[0]);
                    sb.append(" " + lemma);

                    annot = annotation;
                } else { // new named entity
                    eone = true; // end of named entity
                }

            }
            else {
                if (annot != null) { // end of named entity
                    eone = true;
                }
            }

            if (eone) {
                String token = sb.toString().substring(1, sb.length()).toLowerCase(); // first character is a whitespace
                token = token.replace("\"", "'"); // named entity
                NamedEntity namedEntity = getNamedEntity(token, annot);
                if (namedEntity == null){
                    namedEntity = new NamedEntity(token, annot);
                    namedEntityMap.get(annot).add(namedEntity);
                }

                Map<String, Set<Long>> map = newsHyperMap.get(sourceNews.getId());
                map.putIfAbsent(namedEntity.getAnnotation(), new HashSet<>());
                map.get(namedEntity.getAnnotation()).add(namedEntity.getId());

                Relation relation = new Relation(sourceNews, namedEntity, "CONTAINS");
                if (relationMap.get(namedEntity.getId()) == null) {
                    relationMap.put(namedEntity.getId(), new ArrayList<>());
                }
                relationMap.get(namedEntity.getId()).add(relation);

                if (model == 1) {
                    hyperRelationMap.get(namedEntity.getAnnotation()).add(namedEntity.getId());
                }

                sb.delete(0, sb.length());
                if (annotation.equals("O")) { // current word is not NE
                    annot = null;
                } else { // current word is NE
                    annot = annotation;
                    sb.append(" " + words[0]);
                }


            }
            line = br.readLine();
        }
    }

    private boolean isElementOf(List<NamedEntity> neList, NamedEntity namedEntity) {
        for(NamedEntity ne : neList ) {
            if (ne.getAnnotation().equalsIgnoreCase(namedEntity.getAnnotation())
                    && ne.getLabel().equals(namedEntity.getLabel())) {
                return true;
            }
        }
        return false;
    }
    // retrieve all named entities
    public void getNamedEntities(BufferedReader br,  List<NamedEntity> neList, boolean includeNoun, String sentence) throws IOException {
        String[] args = sentence.split(" ");
        String line;
        StringBuilder sb = new StringBuilder();
        String annot = null;
        boolean checkOnce = false;
        while((line  = br.readLine()) != null && !line.isEmpty() /*!line.contains("<S>")*/ ) {
            line = line.replaceAll("\"", "'");
            if (!includeNoun) {
                line = line.replaceAll("NOUN", "O");
            }
            String words[] = line.split("\t");
            String annotation = words[1];
            //String pos = words[2];

            if (!checkOnce) { // check whether first arguments are matched. If there is a mismatch, skip all mismatched arguments
                if (!words[0].contains(args[0].toLowerCase())) {
                    System.out.println("Not matched!");
                    while(true) {
                        line = br.readLine();
                        if (line == null)
                            return;
                        if (!line.contains("\t"))
                            continue;
                        line = line.replaceAll("\"", "'");
                        if (!includeNoun) {
                            line = line.replaceAll("NOUN", "O");
                        }
                        words = line.split("\t");
                        annotation = words[1];
                        if (words[0].contains(args[0].toLowerCase())) {
                            break;
                        }
                    }
                }
                checkOnce = true;
            }


            boolean eone = false;
            if (!annotation.equals("O") && !words[0].isEmpty()) { // named entity
                if (annot == null || annot.equals(annotation)) { // part of current named entity
                    String lemma = words[0];
                    sb.append(" " + lemma);

                    annot = annotation;
                } else { // new named entity
                    eone = true; // end of named entity
                }

            } else {
                if (annot != null) { // end of named entity
                    eone = true;
                }
            }

            if (eone) {
                String token = sb.toString().substring(1, sb.length()).toLowerCase(); // first character is a whitespace
                token = token.replace("\"", "'"); // named entity
                NamedEntity namedEntity = getNamedEntity(token, annot);
                if (namedEntity == null) {
                    namedEntity = new NamedEntity(token, annot);
                }
                //if(!isElementOf(neList, namedEntity)) {
                    neList.add(namedEntity);
                //}

                sb.delete(0, sb.length());
                if (annotation.equals("O")) { // current word is not NE
                    annot = null;
                } else { // current word is NE
                    annot = annotation;
                    sb.append(" " + words[0]);
                }
            }

            ///*
            if (includeNoun && annotation.equalsIgnoreCase("noun")) {
                NamedEntity namedEntity = getNamedEntity(words[0], "O");
                if (namedEntity == null) {
                    namedEntity = new NamedEntity(words[0], "O");
                }
                neList.add(namedEntity);
            }
            //*/

        }
    }
}
