import java.util.Date;

public class News extends Node{
    private Date date;
    private String title;
    private String content;

    public News(Long id, Date date, String title, String content) {
        super(id, "News");
        this.date = date;
        this.title = "\"" + title + "\"";
        this.content = "\"" + content + "\"";
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
