package com.benchmark.entity.javaorm;

import com.javaorm.core.annotation.JoId;
import com.javaorm.core.annotation.JoTable;

import java.util.List;

@JoTable(name = "t_teacher")
public class Teacher {

    @JoId
    private Long id;

    private String teacherName;

    private Integer age;

    private String title;

    private List<Student> students;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public List<Student> getStudents() { return students; }
    public void setStudents(List<Student> students) { this.students = students; }
}
