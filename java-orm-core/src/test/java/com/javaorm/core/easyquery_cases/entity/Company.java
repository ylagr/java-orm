package com.javaorm.core.easyquery_cases.entity;

import com.javaorm.core.annotation.JoId;
import com.javaorm.core.annotation.JoNavigate;
import com.javaorm.core.annotation.RelationType;
import com.javaorm.core.annotation.JoTable;

import java.time.LocalDateTime;
import java.util.List;

@JoTable(name = "t_company")
public class Company {

    @JoId
    private String id;
    private String name;
    private LocalDateTime createTime;

    @JoNavigate(value = RelationType.ONE_TO_MANY, selfProperty = "id", targetProperty = "companyId")
    private List<SysUser> users;

    public Company() {
    }

    public Company(String id, String name, LocalDateTime createTime) {
        this.id = id;
        this.name = name;
        this.createTime = createTime;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public List<SysUser> getUsers() {
        return users;
    }

    public void setUsers(List<SysUser> users) {
        this.users = users;
    }
}
