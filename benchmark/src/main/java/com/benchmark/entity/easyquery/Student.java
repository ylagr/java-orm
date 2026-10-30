package com.benchmark.entity.easyquery;

import com.benchmark.entity.easyquery.proxy.StudentProxy;
import com.easy.query.core.annotation.*;
import com.easy.query.core.enums.RelationTypeEnum;
import com.easy.query.core.proxy.ProxyEntityAvailable;

import java.time.LocalDateTime;

@Table("t_student")
@EntityProxy
public class Student implements ProxyEntityAvailable<Student, StudentProxy> {

    @Column(primaryKey = true, generatedKey = true)
    private Long id;

    @Column("student_name")
    private String studentName;

    private Integer age;

    @Column("teacher_id")
    private Long teacherId;

    @Navigate(value = RelationTypeEnum.ManyToOne, selfProperty = "teacherId", targetProperty = "id")
    private Teacher teacher;

    @LogicDelete
    @Column("is_deleted")
    private Boolean isDeleted;

    @Column("create_time")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(Long teacherId) {
        this.teacherId = teacherId;
    }

    public Teacher getTeacher() {
        return teacher;
    }

    public void setTeacher(Teacher teacher) {
        this.teacher = teacher;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
