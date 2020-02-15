package entity;

public class Relation {
    private static Long idGenerator = 1L;
    private Long id;
    private Node start;
    private Node end;
    private String label;
    private String weight;

    public Relation(Node start, Node end, String label) {
        this.id = idGenerator++;
        this.start = start;
        this.end = end;
        this.label = label;
        this.weight = "1.0";
    }

    public Long getId() { return id;}

    public void setId(Long id) {
        this.id = id;
    }

    public Node getStart() {
        return start;
    }

    public void setStart(Node start) {
        this.start = start;
    }

    public Node getEnd() {
        return end;
    }

    public void setEnd(Node end) {
        this.end = end;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getWeight() {
        return weight;
    }

    public void setWeight(String weight) {
        this.weight = weight;
    }
}
