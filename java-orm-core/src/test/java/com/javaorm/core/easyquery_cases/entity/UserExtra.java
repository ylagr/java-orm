package com.javaorm.core.easyquery_cases.entity;

import com.javaorm.core.annotation.JoId;
import com.javaorm.core.annotation.JoTable;

@JoTable(name = "t_user_extra")
public class UserExtra {

    @JoId
    private String id;
    private String userId;
    private String idCard;
    private String city;

    public UserExtra() {
    }

    public UserExtra(String id, String userId, String idCard, String city) {
        this.id = id;
        this.userId = userId;
        this.idCard = idCard;
        this.city = city;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getIdCard() {
        return idCard;
    }

    public void setIdCard(String idCard) {
        this.idCard = idCard;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }
}
