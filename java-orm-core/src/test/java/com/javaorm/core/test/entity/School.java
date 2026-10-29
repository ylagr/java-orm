package com.javaorm.core.test.entity;

import com.javaorm.core.annotation.Id;
import com.javaorm.core.annotation.Table;

import java.util.List;

@Table(name = "t_school")
public class School {

    @Id
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
