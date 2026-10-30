package com.javaorm.core.test.dto;

public class StatDto {
    private Long teacherId;
    private Long total;
    private Double avgAge;

    public StatDto() {
    }

    public StatDto(Long teacherId, Long total, Double avgAge) {
        this.teacherId = teacherId;
        this.total = total;
        this.avgAge = avgAge;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(Long teacherId) {
        this.teacherId = teacherId;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public Double getAvgAge() {
        return avgAge;
    }

    public void setAvgAge(Double avgAge) {
        this.avgAge = avgAge;
    }

    @Override
    public String toString() {
        return "StatDto{teacherId=" + teacherId + ", total=" + total + ", avgAge=" + avgAge + '}';
    }
}
