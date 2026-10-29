package com.javaorm.core.test.entity;

import com.javaorm.core.annotation.Id;
import com.javaorm.core.annotation.Table;
import com.javaorm.core.annotation.TenantId;

@Table(name = "t_tenant_document")
public class TenantDocument {

    @Id
    private Long id;

    private String title;

    private String content;

    @TenantId
    private Long tenantId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }
}
