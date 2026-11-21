package com.javaorm.sample.dto;

import com.javaorm.core.annotation.JoNavigate;
import com.javaorm.core.annotation.RelationType;
import com.javaorm.sample.entity.MallOrderItem;
import com.javaorm.sample.entity.MallUser;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 订单综合 DTO（专用于 selectAutoInclude 自动级联装配与列裁剪）。
 * <p>
 * 仅投影订单基础字段，并自动通过 IN 子查询批量装配明细与所属会员信息，杜绝笛卡尔积。
 */
public class MallOrderDto implements Serializable {

    private Long id;
    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;
    private BigDecimal payAmount;
    private Integer status;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private Date createTime;
    private Date payTime;

    // 自动关联装配明细（一对多）
    @JoNavigate(value = RelationType.ONE_TO_MANY, selfProperty = "id", targetProperty = "orderId")
    private List<MallOrderItemDto> items;

    // 自动关联装配会员（多对一）
    @JoNavigate(value = RelationType.MANY_TO_ONE, selfProperty = "userId", targetProperty = "id")
    private MallUserDto user;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getPayAmount() { return payAmount; }
    public void setPayAmount(BigDecimal payAmount) { this.payAmount = payAmount; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }

    public String getReceiverPhone() { return receiverPhone; }
    public void setReceiverPhone(String receiverPhone) { this.receiverPhone = receiverPhone; }

    public String getReceiverAddress() { return receiverAddress; }
    public void setReceiverAddress(String receiverAddress) { this.receiverAddress = receiverAddress; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getPayTime() { return payTime; }
    public void setPayTime(Date payTime) { this.payTime = payTime; }

    public List<MallOrderItemDto> getItems() { return items; }
    public void setItems(List<MallOrderItemDto> items) { this.items = items; }

    public MallUserDto getUser() { return user; }
    public void setUser(MallUserDto user) { this.user = user; }
}
