package entity;

/**
 * Created by frat1 on 4.09.2018.
 */
public class Triplet {
    private String word;
    private String pos;
    private String annotation;

    private String morphology;

    public Triplet(String _word,/* String _morphology,*/ String _pos, String _annotation) {
        this.word = _word;
        // this.morphology = _morphology;
        this.pos = _pos;
        this.annotation = _annotation;
    }

    public String getWord() { return word;}

    public String getMorphology() {return morphology;}

    public String getPos() { return pos;}

    public String getAnnotation() { return annotation;}

    public void setPos(String _pos) {this.pos = _pos;}

    public void setAnnotation(String _annotation) {this.annotation = _annotation;}

    public void print(){
        System.out.println(word + " " + morphology + " " + pos + " " + annotation);
    }

}
