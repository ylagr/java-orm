package com.javaorm.core.proxy;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.scaffold.InstrumentedType;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
import net.bytebuddy.implementation.bytecode.Duplication;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.TypeCreation;
import net.bytebuddy.implementation.bytecode.member.MethodInvocation;
import net.bytebuddy.implementation.bytecode.member.MethodReturn;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * 专用于在被代理类的方法中生成直接 new 当前生成类自身实例并返回的字节码实现。
 * 字节码对应:
 * NEW CurrentClass
 * DUP
 * INVOKESPECIAL CurrentClass.<init>()V
 * ARETURN
 */
public class ConstructSelfImplementation implements Implementation {

    public static final ConstructSelfImplementation INSTANCE = new ConstructSelfImplementation();

    @Override
    public InstrumentedType prepare(InstrumentedType instrumentedType) {
        return instrumentedType;
    }

    @Override
    public ByteCodeAppender appender(Target implementationTarget) {
        return (mv, context, method) -> {
            TypeDescription instrumentedType = context.getInstrumentedType();
            MethodDescription constructor = instrumentedType.getDeclaredMethods()
                    .filter(ElementMatchers.isConstructor().and(ElementMatchers.takesArguments(0)))
                    .getOnly();

            StackManipulation.Size size = new StackManipulation.Compound(
                    TypeCreation.of(instrumentedType),
                    Duplication.SINGLE,
                    MethodInvocation.invoke(constructor),
                    MethodReturn.REFERENCE
            ).apply(mv, context);

            return new ByteCodeAppender.Size(size.getMaximalSize(), method.getStackSize());
        };
    }
}
