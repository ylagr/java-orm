package com.javaorm.sample.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 会员 DTO（裁剪掉敏感列与版本信息）
 */
public class MallUserDto implements Serializable {

    private Long id;
    private String username;
    private String nickname;
    private String phone;
    private String avatar;
    private Integer status;
    private Integer points;
    private BigDecimal balance;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
}
