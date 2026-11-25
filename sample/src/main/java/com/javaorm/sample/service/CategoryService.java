package com.javaorm.sample.service;

import com.javaorm.core.JavaOrm;
import com.javaorm.sample.entity.MallCategory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class CategoryService {

    @Autowired
    private JavaOrm orm;

    public List<MallCategory> listAll() {
        return orm.select(MallCategory.class)
                .orderBy(MallCategory::getSort, true)
                .orderBy(MallCategory::getId, true)
                .list();
    }

    public MallCategory getById(Long id) {
        return orm.select(MallCategory.class)
                .where()
                .eq(MallCategory::getId, id)
                .firstOrNull();
    }

    @Transactional
    public Long create(MallCategory category) {
        category.setCreateTime(new Date());
        category.setDeleted(0);
        if (category.getSort() == null) {
            category.setSort(0);
        }
        orm.insert(category).exec();
        return category.getId();
    }

    @Transactional
    public boolean update(MallCategory category) {
        return orm.update(category).exec() > 0;
    }

    @Transactional
    public boolean delete(Long id) {
        MallCategory category = getById(id);
        if (category != null) {
            // JavaORM 自动识别 @TableLogic，执行逻辑删除 UPDATE mall_category SET deleted = 1
            return orm.delete(category).exec() > 0;
        }
        return false;
    }
}
