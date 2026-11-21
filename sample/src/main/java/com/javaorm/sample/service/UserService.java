package com.javaorm.sample.service;

import com.javaorm.core.JavaOrm;
import com.javaorm.core.pagination.Page;
import com.javaorm.sample.dto.MallUserDto;
import com.javaorm.sample.entity.MallUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class UserService {

    @Autowired
    private JavaOrm orm;

    public Page<MallUserDto> pageUsers(String keyword, Integer status, int page, int size) {
        boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
        Page<MallUser> userPage = orm.select(MallUser.class)
                .where()
                .like(hasKeyword, MallUser::getUsername, hasKeyword ? "%" + keyword.trim() + "%" : "")
                .eq(status != null, MallUser::getStatus, status)
                .orderBy(MallUser::getId, false)
                .toPage(page, size);

        List<MallUserDto> dtoList = new ArrayList<>();
        for (MallUser u : userPage.getRecords()) {
            MallUserDto dto = new MallUserDto();
            dto.setId(u.getId());
            dto.setUsername(u.getUsername());
            dto.setNickname(u.getNickname());
            dto.setPhone(u.getPhone());
            dto.setAvatar(u.getAvatar());
            dto.setStatus(u.getStatus());
            dto.setPoints(u.getPoints());
            dto.setBalance(u.getBalance());
            dtoList.add(dto);
        }

        return new com.javaorm.core.pagination.PageResult<>(
                userPage.getTotal(),
                dtoList,
                userPage.getPageNum(),
                userPage.getPageSize()
        );
    }

    public MallUser getById(Long id) {
        return orm.select(MallUser.class)
                .where()
                .eq(MallUser::getId, id)
                .firstOrNull();
    }

    @Transactional
    public Long create(MallUser user) {
        user.setCreateTime(new Date());
        user.setDeleted(0);
        if (user.getStatus() == null) user.setStatus(1);
        if (user.getPoints() == null) user.setPoints(0);
        if (user.getBalance() == null) user.setBalance(new java.math.BigDecimal("0.00"));
        orm.insert(user);
        return user.getId();
    }

    @Transactional
    public boolean toggleStatus(Long id, Integer status) {
        return orm.update(MallUser.class)
                .set(MallUser::getStatus, status)
                .where()
                .eq(MallUser::getId, id)
                .execute() > 0;
    }
}
