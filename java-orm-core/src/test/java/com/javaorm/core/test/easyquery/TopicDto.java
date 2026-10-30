package com.javaorm.core.test.easyquery;

import java.time.LocalDateTime;

public class TopicDto {

    private String id;
    private Integer stars;
    private String title;
    private LocalDateTime createTime;
    private Long blogCount;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Integer getStars() { return stars; }
    public void setStars(Integer stars) { this.stars = stars; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public Long getBlogCount() { return blogCount; }
    public void setBlogCount(Long blogCount) { this.blogCount = blogCount; }
}
