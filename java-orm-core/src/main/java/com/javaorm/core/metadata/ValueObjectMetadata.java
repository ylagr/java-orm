package com.javaorm.core.metadata;

import com.javaorm.core.fastbean.FastBeanCreator;
import com.javaorm.core.fastbean.FastPropertyGetter;
import com.javaorm.core.fastbean.FastPropertySetter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ValueObject 元数据描述。
 * 记录嵌入值对象的属性名、类型、构造器、直接子字段列以及嵌套子 VO。
 */
public class ValueObjectMetadata {

    private final String propertyName;
    private final String fullPropertyName;
    private final Class<?> valueObjectClass;
    private final Class<?> declaringClass;
    private final Field field;
    private final Method getter;
    private final Method setter;
    private final FastPropertyGetter fastGetter;
    private final FastPropertySetter fastSetter;
    private final FastBeanCreator<?> creator;

    private final List<ColumnMetadata> columns = new ArrayList<>();
    private final List<ValueObjectMetadata> nestedValueObjects = new ArrayList<>();

    public ValueObjectMetadata(String propertyName, String fullPropertyName,
                               Class<?> valueObjectClass, Class<?> declaringClass,
                               Field field, Method getter, Method setter,
                               FastPropertyGetter fastGetter, FastPropertySetter fastSetter,
                               FastBeanCreator<?> creator) {
        this.propertyName = propertyName;
        this.fullPropertyName = fullPropertyName;
        this.valueObjectClass = valueObjectClass;
        this.declaringClass = declaringClass;
        this.field = field;
        this.getter = getter;
        this.setter = setter;
        this.fastGetter = fastGetter;
        this.fastSetter = fastSetter;
        this.creator = creator;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public String getFullPropertyName() {
        return fullPropertyName;
    }

    public Class<?> getValueObjectClass() {
        return valueObjectClass;
    }

    public Class<?> getDeclaringClass() {
        return declaringClass;
    }

    public Field getField() {
        return field;
    }

    public Method getGetter() {
        return getter;
    }

    public Method getSetter() {
        return setter;
    }

    public FastPropertyGetter getFastGetter() {
        return fastGetter;
    }

    public FastPropertySetter getFastSetter() {
        return fastSetter;
    }

    public FastBeanCreator<?> getCreator() {
        return creator;
    }

    public List<ColumnMetadata> getColumns() {
        return columns;
    }

    public List<ValueObjectMetadata> getNestedValueObjects() {
        return nestedValueObjects;
    }

    public void addColumn(ColumnMetadata col) {
        columns.add(col);
    }

    public void addNestedValueObject(ValueObjectMetadata nested) {
        nestedValueObjects.add(nested);
    }

    public List<ColumnMetadata> getAllLeafColumns() {
        List<ColumnMetadata> list = new ArrayList<>(columns);
        for (ValueObjectMetadata nested : nestedValueObjects) {
            list.addAll(nested.getAllLeafColumns());
        }
        return Collections.unmodifiableList(list);
    }
}
