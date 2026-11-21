package com.javaorm.core.easyquery_cases.dto;

import java.util.List;

public class CompanyDTO {
    private String id;
    private String name;
    private List<SysUserDTO> users;

    public CompanyDTO() {
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

    public List<SysUserDTO> getUsers() {
        return users;
    }

    public void setUsers(List<SysUserDTO> users) {
        this.users = users;
    }
}
