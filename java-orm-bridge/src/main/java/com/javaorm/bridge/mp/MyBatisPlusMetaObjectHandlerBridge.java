package com.javaorm.bridge.mp;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.javaorm.core.handler.AutoFillHandler;
import com.javaorm.core.metadata.EntityMetadata;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

/**
 * MyBatis-Plus MetaObjectHandler 自动填充处理器桥接实现
 */
public class MyBatisPlusMetaObjectHandlerBridge implements AutoFillHandler {

    private final MetaObjectHandler mpHandler;

    public MyBatisPlusMetaObjectHandlerBridge(MetaObjectHandler mpHandler) {
        this.mpHandler = mpHandler;
    }

    @Override
    public void insertFill(Object entity, EntityMetadata metadata) {
        if (entity != null && mpHandler != null) {
            MetaObject metaObject = SystemMetaObject.forObject(entity);
            mpHandler.insertFill(metaObject);
        }
    }

    @Override
    public void updateFill(Object entity, EntityMetadata metadata) {
        if (entity != null && mpHandler != null) {
            MetaObject metaObject = SystemMetaObject.forObject(entity);
            mpHandler.updateFill(metaObject);
        }
    }
}
