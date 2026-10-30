package com.javaorm.core.test.easyquery;

import com.javaorm.core.annotation.JoId;
import com.javaorm.core.annotation.JoLogicDelete;
import com.javaorm.core.annotation.JoTable;

import java.time.LocalDateTime;

@JoTable(name = "t_topic")
public class Topic {

    @JoId
    private String id;
    private Integer stars;
    private String title;
    private LocalDateTime createTime;

    @JoLogicDelete(normalValue = "0", deletedValue = "1")
    private Integer isDeleted;

    public Topic() {
    }

    public Topic(String id, Integer stars, String title, LocalDateTime createTime) {
        this.id = id;
        this.stars = stars;
        this.title = title;
        this.createTime = createTime;
        this.isDeleted = 0;
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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public Integer getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Integer isDeleted) {
        this.isDeleted = isDeleted;
    }
}
