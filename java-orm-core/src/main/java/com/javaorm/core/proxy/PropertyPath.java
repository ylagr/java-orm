package com.javaorm.core.proxy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 记录通过 Lambda 链式调用捕获的属性路径（例如 student -> teacher -> age）。
 */
public class PropertyPath {
    private final Class<?> rootEntityClass;
    private final List<String> relationSteps = new ArrayList<>();
    private String terminalProperty;
    private Class<?> terminalType;

    public PropertyPath(Class<?> rootEntityClass) {
        this.rootEntityClass = rootEntityClass;
    }

    public void addRelationStep(String relationProperty) {
        relationSteps.add(relationProperty);
    }

    public void setTerminal(String terminalProperty, Class<?> terminalType) {
        this.terminalProperty = terminalProperty;
        this.terminalType = terminalType;
    }

    public Class<?> getRootEntityClass() {
        return rootEntityClass;
    }

    public List<String> getRelationSteps() {
        return Collections.unmodifiableList(relationSteps);
    }

    public boolean hasRelations() {
        return !relationSteps.isEmpty();
    }

    public String getTerminalProperty() {
        return terminalProperty;
    }

    public Class<?> getTerminalType() {
        return terminalType;
    }

    public String getFullPath() {
        if (relationSteps.isEmpty()) {
            return terminalProperty != null ? terminalProperty : "";
        }
        StringBuilder sb = new StringBuilder();
        for (String step : relationSteps) {
            sb.append(step).append(".");
        }
        if (terminalProperty != null) {
            sb.append(terminalProperty);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return getFullPath();
    }
}
