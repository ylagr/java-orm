package com.javaorm.core.test.entity;

import com.javaorm.core.annotation.Id;
import com.javaorm.core.annotation.LogicDelete;
import com.javaorm.core.annotation.Table;

import java.time.LocalDateTime;

@Table(name = "t_student")
public class Student {

    @Id
    private Long id;

    // 省略 @Column，自动映射为 student_name
    private String studentName;

    private Integer age;

    // 省略 @Column，自动映射为 teacher_id
    private Long teacherId;

    // 多对一关联：默认外键匹配为 teacherId -> Teacher.id
    private Teacher teacher;

    @LogicDelete(normalValue = "0", deletedValue = "1")
    private Integer isDeleted;

    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }

    public Teacher getTeacher() { return teacher; }
    public void setTeacher(Teacher teacher) { this.teacher = teacher; }

    public Integer getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Integer isDeleted) { this.isDeleted = isDeleted; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
