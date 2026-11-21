package com.javaorm.core.easyquery_cases.dto;

public class TopicDTO {
    private String id;
    private Integer stars;
    private String title;

    public TopicDTO() {
    }

    public TopicDTO(String id, Integer stars, String title) {
        this.id = id;
        this.stars = stars;
        this.title = title;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getStars() {
        return stars;
    }

    public void setStars(Integer stars) {
        this.stars = stars;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
