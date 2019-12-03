import entity.NamedEntity;
import entity.Relation;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class W2VFileManager {

    private List<NamedEntity> namedEntityList = new ArrayList<>();
    private List<Relation> relationList = new ArrayList<>();

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
            DecimalFormat df = new DecimalFormat("#.###");
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

    public static void main(String[] args) {
        W2VFileManager w2v = new W2VFileManager();
        w2v.readNodes("word2vec\\named_entities_10K.txt", "word2vec\\nodes_10k.csv");
        w2v.readRelations("word2vec\\similarities_10k_70t.txt", "word2vec\\relations_10k.csv");
    }
}
