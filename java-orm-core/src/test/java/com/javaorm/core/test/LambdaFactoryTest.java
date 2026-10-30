package com.javaorm.core.test;

import org.junit.Test;

import java.lang.invoke.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

public class LambdaFactoryTest {

    public static class SampleBean {
        private String name;
        private Integer wrapperAge;
        private int primitiveAge;

        public SampleBean() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Integer getWrapperAge() { return wrapperAge; }
        public void setWrapperAge(Integer wrapperAge) { this.wrapperAge = wrapperAge; }

        public int getPrimitiveAge() { return primitiveAge; }
        public void setPrimitiveAge(int primitiveAge) { this.primitiveAge = primitiveAge; }
    }

    @FunctionalInterface
    public interface BeanCreator {
        Object create();
    }

    @FunctionalInterface
    public interface PropertySetter {
        void set(Object bean, Object value);
    }

    @FunctionalInterface
    public interface PropertyGetter {
        Object get(Object bean);
    }

    @Test
    public void testBeanCreator() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Constructor<?> ctor = SampleBean.class.getDeclaredConstructor();
        MethodHandle mh = lookup.unreflectConstructor(ctor);
        CallSite site = LambdaMetafactory.metafactory(
                lookup,
                "create",
                MethodType.methodType(BeanCreator.class),
                MethodType.methodType(Object.class),
                mh,
                MethodType.methodType(SampleBean.class)
        );
        BeanCreator creator = (BeanCreator) site.getTarget().invokeExact();
        Object obj = creator.create();
        assertNotNull(obj);
        assertTrue(obj instanceof SampleBean);
    }

    @Test
    public void testStringSetter() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Method method = SampleBean.class.getMethod("setName", String.class);
        MethodHandle mh = lookup.unreflect(method);

        CallSite site = LambdaMetafactory.metafactory(
                lookup,
                "set",
                MethodType.methodType(PropertySetter.class),
                MethodType.methodType(void.class, Object.class, Object.class),
                mh,
                MethodType.methodType(void.class, SampleBean.class, String.class)
        );
        PropertySetter setter = (PropertySetter) site.getTarget().invokeExact();

        SampleBean bean = new SampleBean();
        setter.set(bean, "Tom");
        assertEquals("Tom", bean.getName());
    }

    @Test
    public void testWrapperSetter() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Method method = SampleBean.class.getMethod("setWrapperAge", Integer.class);
        MethodHandle mh = lookup.unreflect(method);

        CallSite site = LambdaMetafactory.metafactory(
                lookup,
                "set",
                MethodType.methodType(PropertySetter.class),
                MethodType.methodType(void.class, Object.class, Object.class),
                mh,
                MethodType.methodType(void.class, SampleBean.class, Integer.class)
        );
        PropertySetter setter = (PropertySetter) site.getTarget().invokeExact();

        SampleBean bean = new SampleBean();
        setter.set(bean, 25);
        assertEquals(Integer.valueOf(25), bean.getWrapperAge());
    }

    @Test
    public void testGetter() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Method method = SampleBean.class.getMethod("getName");
        MethodHandle mh = lookup.unreflect(method);

        CallSite site = LambdaMetafactory.metafactory(
                lookup,
                "get",
                MethodType.methodType(PropertyGetter.class),
                MethodType.methodType(Object.class, Object.class),
                mh,
                MethodType.methodType(String.class, SampleBean.class)
        );
        PropertyGetter getter = (PropertyGetter) site.getTarget().invokeExact();

        SampleBean bean = new SampleBean();
        bean.setName("Jerry");
        assertEquals("Jerry", getter.get(bean));
    }

    @Test
    public void testPrimitiveSetter() throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Method method = SampleBean.class.getMethod("setPrimitiveAge", int.class);
        MethodHandle mh = lookup.unreflect(method);

        try {
            CallSite site = LambdaMetafactory.metafactory(
                    lookup,
                    "set",
                    MethodType.methodType(PropertySetter.class),
                    MethodType.methodType(void.class, Object.class, Object.class),
                    mh,
                    MethodType.methodType(void.class, SampleBean.class, int.class)
            );
            PropertySetter setter = (PropertySetter) site.getTarget().invokeExact();
            SampleBean bean = new SampleBean();
            setter.set(bean, 30);
            assertEquals(30, bean.getPrimitiveAge());
            System.out.println("LambdaMetafactory supports primitive int directly!");
        } catch (LambdaConversionException e) {
            System.out.println("LambdaMetafactory threw LambdaConversionException for primitive int: " + e.getMessage());
            // Test MethodHandle.asType fallback
            MethodHandle adapted = mh.asType(MethodType.methodType(void.class, Object.class, Object.class));
            PropertySetter setter = (bean, val) -> {
                try {
                    adapted.invokeExact(bean, val);
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            };
            SampleBean bean = new SampleBean();
            setter.set(bean, 30);
            assertEquals(30, bean.getPrimitiveAge());
            System.out.println("MethodHandle.asType works for primitive int!");
        }
    }
}

