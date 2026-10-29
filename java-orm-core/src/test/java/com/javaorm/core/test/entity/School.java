package com.javaorm.core.test.entity;

import com.javaorm.core.annotation.JoId;
import com.javaorm.core.annotation.JoTable;

import java.util.List;

@JoTable(name = "t_school")
public class School {

    @JoId
    private Long id;

    private String schoolName;

    private String city;

    private List<Teacher> teachers;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSchoolName() {
        return schoolName;
    }

    public void setSchoolName(String schoolName) {
        this.schoolName = schoolName;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public List<Teacher> getTeachers() {
        return teachers;
    }

    public void setTeachers(List<Teacher> teachers) {
        this.teachers = teachers;
    }
}
