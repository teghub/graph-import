package entity;

import java.util.Date;

public class News extends Node{
    private String date;
    private String title;
    private String content;

    public News(Long id, String date, String title, String content) {
        super(id, "entity.News");
        this.date = date;
        this.title = "\"" + title + "\"";
        this.content = "\"" + content + "\"";
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
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
