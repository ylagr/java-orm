package com.javaorm.core.easyquery_cases.dto;

public class SysUserDTO {
    private String id;
    private String name;
    private Integer age;
    private String phone;
    private String companyId;
    private String companyName;
    private CompanyDTO company;
    private UserExtraDTO extra;

    public SysUserDTO() {
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

    public String getCompanyId() {
        return companyId;
    }

    public void setCompanyId(String companyId) {
        this.companyId = companyId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public CompanyDTO getCompany() {
        return company;
    }

    public void setCompany(CompanyDTO company) {
        this.company = company;
    }

    public UserExtraDTO getExtra() {
        return extra;
    }

    public void setExtra(UserExtraDTO extra) {
        this.extra = extra;
    }
}
