package com.javaorm.core.easyquery_cases.entity;

import com.javaorm.core.annotation.JoId;
import com.javaorm.core.annotation.JoNavigate;
import com.javaorm.core.annotation.RelationType;
import com.javaorm.core.annotation.JoTable;

import java.time.LocalDateTime;

@JoTable(name = "t_sys_user")
public class SysUser {

    @JoId
    private String id;
    private String companyId;
    private String name;
    private Integer age;
    private String phone;
    private LocalDateTime createTime;

    @JoNavigate(value = RelationType.MANY_TO_ONE, selfProperty = "companyId", targetProperty = "id")
    private Company company;

    @JoNavigate(value = RelationType.ONE_TO_ONE, selfProperty = "id", targetProperty = "userId")
    private UserExtra extra;

    public SysUser() {
    }

    public SysUser(String id, String companyId, String name, Integer age, String phone, LocalDateTime createTime) {
        this.id = id;
        this.companyId = companyId;
        this.name = name;
        this.age = age;
        this.phone = phone;
        this.createTime = createTime;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCompanyId() {
        return companyId;
    }

    public void setCompanyId(String companyId) {
        this.companyId = companyId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public UserExtra getExtra() {
        return extra;
    }

    public void setExtra(UserExtra extra) {
        this.extra = extra;
    }
}
