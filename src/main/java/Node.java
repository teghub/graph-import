public class Node {
    private static Long idGenerator=1L;
    private Long id;
    private String label;

    public Node(String label) {
        this.id = idGenerator++;
        //this.label = "\"" + label + "\"" ;
        this.label = label;
    }

    public Node(Long id, String label) {
        this.id = id;
        this.label = label ;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
