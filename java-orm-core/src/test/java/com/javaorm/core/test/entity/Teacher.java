package com.javaorm.core.test.entity;

import com.javaorm.core.annotation.JoId;
import com.javaorm.core.annotation.JoTable;

import java.util.List;

@JoTable(name = "t_teacher")
public class Teacher {

    @JoId
    private Long id;

    // 省略 @Column，自动根据 NameConversion 映射为 teacher_name
    private String teacherName;

    private Integer age;

    private String title;

    private Long schoolId;

    private School school;

    private List<Student> students;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSchoolId() { return schoolId; }
    public void setSchoolId(Long schoolId) { this.schoolId = schoolId; }

    public School getSchool() { return school; }
    public void setSchool(School school) { this.school = school; }

    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public List<Student> getStudents() { return students; }
    public void setStudents(List<Student> students) { this.students = students; }
}
