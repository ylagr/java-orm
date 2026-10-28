package com.benchmark.entity.easyquery;

import com.benchmark.entity.easyquery.proxy.TeacherProxy;
import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Navigate;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.enums.RelationTypeEnum;
import com.easy.query.core.proxy.ProxyEntityAvailable;

import java.util.List;

@Table("t_teacher")
@EntityProxy
public class Teacher implements ProxyEntityAvailable<Teacher, TeacherProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    @Column("teacher_name")
    private String teacherName;

    private Integer age;

    private String title;

    @Navigate(value = RelationTypeEnum.OneToMany, selfProperty = "id", targetProperty = "teacherId")
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
