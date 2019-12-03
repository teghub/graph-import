package entity;

public class NamedEntity extends Node{

    private String annotation;

    public NamedEntity(String label, String _annotation) {
        super(label);
        this.annotation = _annotation;
    }

    public String getAnnotation() {
        return this.annotation;
    }

    public void setAnnotation(String _annotation) {
        this.annotation = _annotation;
    }
}
