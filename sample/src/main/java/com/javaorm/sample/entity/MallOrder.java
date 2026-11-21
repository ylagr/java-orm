package com.javaorm.sample.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.javaorm.core.annotation.JoNavigate;
import com.javaorm.core.annotation.RelationType;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 订单实体（演示 MyBatis-Plus 基础注解 + JavaORM 关系导航声明）
 */
@TableName("mall_order")
public class MallOrder implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("order_no")
    private String orderNo;

    @TableField("user_id")
    private Long userId;

    @TableField("total_amount")
    private BigDecimal totalAmount;

    @TableField("pay_amount")
    private BigDecimal payAmount;

    private Integer status; // 10-待付款, 20-待发货, 30-已发货, 40-已完成, 50-已取消

    @TableField("receiver_name")
    private String receiverName;

    @TableField("receiver_phone")
    private String receiverPhone;

    @TableField("receiver_address")
    private String receiverAddress;

    @Version
    private Integer version;

    @TableLogic(value = "0", delval = "1")
    private Integer deleted;

    @TableField("create_time")
    private Date createTime;

    @TableField("pay_time")
    private Date payTime;

    // 关联订单明细（一对多）
    @JoNavigate(value = RelationType.ONE_TO_MANY, selfProperty = "id", targetProperty = "orderId")
    @TableField(exist = false)
    private List<MallOrderItem> items;

    // 关联下单用户（多对一）
    @JoNavigate(value = RelationType.MANY_TO_ONE, selfProperty = "userId", targetProperty = "id")
    @TableField(exist = false)
    private MallUser user;

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

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getPayTime() { return payTime; }
    public void setPayTime(Date payTime) { this.payTime = payTime; }

    public List<MallOrderItem> getItems() { return items; }
    public void setItems(List<MallOrderItem> items) { this.items = items; }

    public MallUser getUser() { return user; }
    public void setUser(MallUser user) { this.user = user; }
}
