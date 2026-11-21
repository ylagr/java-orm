package com.javaorm.sample.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 商品展示 DTO
 */
public class MallProductDto implements Serializable {

    private Long id;
    private Long categoryId;
    private String categoryName;
    private String name;
    private String title;
    private BigDecimal price;
    private Integer stock;
    private Integer sales;
    private String image;
    private Integer status;
    private Integer version;
    private Date createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

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

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
}
