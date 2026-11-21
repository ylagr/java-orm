package com.javaorm.sample.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 商品实体（展示 MyBatis-Plus @Version 乐观锁与 @TableLogic 逻辑删除自动生效）
 */
@TableName("mall_product")
public class MallProduct implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("category_id")
    private Long categoryId;

    private String name;

    private String title;

    private BigDecimal price;

    private Integer stock;

    private Integer sales;

    private String image;

    private Integer status; // 1-上架, 0-下架

    @Version
    private Integer version; // 乐观锁版本号

    @TableLogic(value = "0", delval = "1")
    private Integer deleted; // 逻辑删除

    @TableField("create_time")
    private Date createTime;

    @TableField("update_time")
    private Date updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }

    public Integer getSales() { return sales; }
    public void setSales(Integer sales) { this.sales = sales; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
