package com.javaorm.core.test.easyquery;

import com.javaorm.core.annotation.JoWhereCondition;

import java.time.LocalDateTime;
import java.util.List;

public class TopicSearchRequest {

    @JoWhereCondition(type = JoWhereCondition.Condition.LIKE_MATCH_LEFT, propName = "title")
    private String titlePrefix;

    @JoWhereCondition(type = JoWhereCondition.Condition.RANGE_LEFT_CLOSED, propName = "createTime")
    private LocalDateTime createTimeBegin;

    @JoWhereCondition(type = JoWhereCondition.Condition.RANGE_RIGHT_CLOSED, propName = "createTime")
    private LocalDateTime createTimeEnd;

    @JoWhereCondition(type = JoWhereCondition.Condition.IN, propName = "stars")
    private List<Integer> starList;

    private Integer stars;

    public String getTitlePrefix() { return titlePrefix; }
    public void setTitlePrefix(String titlePrefix) { this.titlePrefix = titlePrefix; }

    public LocalDateTime getCreateTimeBegin() { return createTimeBegin; }
    public void setCreateTimeBegin(LocalDateTime createTimeBegin) { this.createTimeBegin = createTimeBegin; }

    public LocalDateTime getCreateTimeEnd() { return createTimeEnd; }
    public void setCreateTimeEnd(LocalDateTime createTimeEnd) { this.createTimeEnd = createTimeEnd; }

    public List<Integer> getStarList() { return starList; }
    public void setStarList(List<Integer> starList) { this.starList = starList; }

    public Integer getStars() { return stars; }
    public void setStars(Integer stars) { this.stars = stars; }
}
