import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class Utils {

    // classify news according to their categories
    public static void getCategories(String inputPath) {
        BufferedReader br = null;
        Map<String, Integer> categoryMap = new HashMap<>();

        try {
            br = new BufferedReader( new InputStreamReader(new FileInputStream(inputPath), StandardCharsets.UTF_8));

            String line = br.readLine();

            int c = 1;
            while (line != null) {

                String title = line.split("\t")[2];
                //System.out.println(title);
                int index = title.indexOf("|");

                if (index > 0 && index < title.length()) {
                    String category = title.substring(index+2);

                    if (categoryMap.get(category) == null) {
                        categoryMap.put(category,1);
                        //System.out.println(category);
                    }
                    else {
                        int freq = categoryMap.get(category);
                        categoryMap.put(category, freq+1);
                    }
                }
                else {
                    if (categoryMap.get("NOT LISTED") == null) {
                        categoryMap.put("NOT LISTED", 1);
                    }
                    else {
                        int freq = categoryMap.get("NOT LISTED");
                        categoryMap.put("NOT LISTED", freq+1);
                    }
                }

                line = br.readLine();
                c++;
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally{
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            for (Map.Entry<String, Integer> entry : categoryMap.entrySet()) {
                System.out.println(entry.getKey() + "\t" + entry.getValue());
            }
        }
    }

    public static int countWordsUsingSplit(String input) {
        if (input == null || input.isEmpty()) {
            return 0;
        }
        /*
        String[] words = input.split("\\s+");
        return words.length;
        */
        StringTokenizer tokens = new StringTokenizer(input);
        return tokens.countTokens();
    }

    public static double wordCount(String inputPath) {
        BufferedReader br = null;

        long sum = 0;
        int newsCount = 32734;
        try {
            br = new BufferedReader( new InputStreamReader(new FileInputStream(inputPath), StandardCharsets.UTF_8));

            String line = br.readLine();

            while (line != null) {

                sum += countWordsUsingSplit(line);
                line = br.readLine();
            }


        } catch (IOException e) {
            e.printStackTrace();
        } finally{
            try {
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return sum*1.0/newsCount;
    }

    // merge files in the same directory
    public static void mergeFiles(String inputPath, String outputPath) {
        Map<String, String> replaceMap = new HashMap<>();
        replaceMap.put("ENAMEX TYPE","b_enamex TYPE");
        replaceMap.put("/ENAMEX", "e_enamex");
        replaceMap.put("NUMEX TYPE", "b_numex TYPE");
        replaceMap.put("/NUMEX", "e_numex");
        replaceMap.put("TIMEX TYPE", "b_timex TYPE");
        replaceMap.put("/TIMEX", "e_timex");

        BufferedWriter writer = null;
        File directory = new File(inputPath);
        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath), StandardCharsets.UTF_8));
            for (File file : directory.listFiles()) {
                if (!file.isDirectory()) {
                    BufferedReader br = null;
                    try {
                        br = new BufferedReader(new FileReader(file.getAbsolutePath()));
                        String line = br.readLine();
                        while (line != null) {
                            String replaced = replaceSequence(line, replaceMap);
                            writer.write(replaced + "\n");
                            line = br.readLine();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        try {
                            br.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // replace char sequences which are specified in the map
    public static String replaceSequence(String text, Map<String, String> sequenceMap) {
        String replaced = text;
        for (Map.Entry<String,String > entry : sequenceMap.entrySet()) {
            replaced = replaced.replaceAll(entry.getKey(), entry.getValue());
        }
        return replaced;
    }

    public static void main(String[] args) {
        // getCategories(NEWSPATH+"\\haberler_db.txt");
        // double avg = wordCount(NEWSPATH+"\\content_orig.txt");
        // System.out.println("Avg: " + avg);
    }
}
