package com.javaorm.core.test.easyquery;

import com.javaorm.core.annotation.JoId;
import com.javaorm.core.annotation.JoTable;

import java.time.LocalDateTime;

@JoTable(name = "t_blog")
public class Blog {

    @JoId
    private String id;
    private String title;
    private String content;
    private Integer star;
    private String topicId;
    private LocalDateTime createTime;

    public Blog() {
    }

    public Blog(String id, String title, String content, Integer star, String topicId, LocalDateTime createTime) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.star = star;
        this.topicId = topicId;
        this.createTime = createTime;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public Integer getStar() {
        return star;
    }

    public void setStar(Integer star) {
        this.star = star;
    }

    public String getTopicId() {
        return topicId;
    }

    public void setTopicId(String topicId) {
        this.topicId = topicId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
