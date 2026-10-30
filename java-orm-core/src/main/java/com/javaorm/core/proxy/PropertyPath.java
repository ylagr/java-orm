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
    private String explicitAlias;

    private final List<String> valueObjectSteps = new ArrayList<>();
    private boolean valueObject;

    public PropertyPath(Class<?> rootEntityClass) {
        this.rootEntityClass = rootEntityClass;
    }

    public String getExplicitAlias() {
        return explicitAlias;
    }

    public void setExplicitAlias(String explicitAlias) {
        this.explicitAlias = explicitAlias;
    }

    public void addRelationStep(String relationProperty) {
        relationSteps.add(relationProperty);
    }

    public void addValueObjectStep(String voProperty) {
        valueObjectSteps.add(voProperty);
    }

    public List<String> getValueObjectSteps() {
        return Collections.unmodifiableList(valueObjectSteps);
    }

    public boolean hasValueObjects() {
        return !valueObjectSteps.isEmpty() || valueObject;
    }

    public boolean isValueObject() {
        return valueObject;
    }

    public void setValueObject(boolean valueObject) {
        this.valueObject = valueObject;
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
        List<String> allSteps = new ArrayList<>(relationSteps);
        allSteps.addAll(valueObjectSteps);
        if (allSteps.isEmpty()) {
            return terminalProperty != null ? terminalProperty : "";
        }
        StringBuilder sb = new StringBuilder();
        for (String step : allSteps) {
            sb.append(step).append(".");
        }
        if (terminalProperty != null) {
            sb.append(terminalProperty);
        } else if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '.') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return getFullPath();
    }
}
