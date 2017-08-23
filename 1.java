package com.alibaba.dubbo.rpc;

import com.alibaba.dubbo.common.extension.ExtensionLoader;

public class Protocol$Adpative implements com.alibaba.dubbo.rpc.Protocol {
    public void destroy() {
        throw new UnsupportedOperationException("method public abstract void com.alibaba.dubbo.rpc.Protocol.destroy() of interface com.alibaba.dubbo.rpc.Protocol is not adaptive method!");
    }

    public int getDefaultPort() {
        throw new UnsupportedOperationException("method public abstract int com.alibaba.dubbo.rpc.Protocol.getDefaultPort() of interface com.alibaba.dubbo.rpc.Protocol is not adaptive method!");
    }

    public com.alibaba.dubbo.rpc.Exporter export(com.alibaba.dubbo.rpc.Invoker arg0) throws com.alibaba.dubbo.rpc.RpcException {
        if (arg0 == null) throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument == null");
        if (arg0.getUrl() == null)
            throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument getUrl() == null");
        com.alibaba.dubbo.common.URL url = arg0.getUrl();
        String extName = (url.getProtocol() == null ? (dubbo) : url.getProtocol());
        if (extName == null)
            throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.Protocol) name from url(" + url.toString() + ") use keys([protocol])");
        com.alibaba.dubbo.rpc.Protocol extension = (com.alibaba.dubbo.rpc.Protocol) ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.rpc.Protocol.class).getExtension(extName);
        return extension.export(arg0);
    }

    public com.alibaba.dubbo.rpc.Invoker refer(java.lang.Class arg0, com.alibaba.dubbo.common.URL arg1) throws com.alibaba.dubbo.rpc.RpcException {
        if (arg1 == null) throw new IllegalArgumentException("url == null");
        com.alibaba.dubbo.common.URL url = arg1;
        String extName = (url.getProtocol() == null ? (dubbo) : url.getProtocol());
        if (extName == null)
            throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.Protocol) name from url(" + url.toString() + ") use keys([protocol])");
        com.alibaba.dubbo.rpc.Protocol extension = (com.alibaba.dubbo.rpc.Protocol) ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.rpc.Protocol.class).getExtension(extName);
        return extension.refer(arg0, arg1);
    }
}
,stack:javassist.CannotCompileException:[source error]no such field:dubbo

        javassist.CannotCompileException:[source error]no such field:dubbo
        at javassist.CtNewMethod.make(CtNewMethod.java:79)
        at javassist.CtNewMethod.make(CtNewMethod.java:45)
        at com.alibaba.dubbo.common.compiler.support.JavassistCompiler.doCompile(JavassistCompiler.java:119)
        at com.alibaba.dubbo.common.compiler.support.AbstractCompiler.compile(AbstractCompiler.java:59)
        at com.alibaba.dubbo.common.compiler.support.AdaptiveCompiler.compile(AdaptiveCompiler.java:46)
        at com.alibaba.dubbo.common.extension.ExtensionLoader.createAdaptiveExtensionClass(ExtensionLoader.java:978)
        at com.alibaba.dubbo.common.extension.ExtensionLoader.getAdaptiveExtensionClass(ExtensionLoader.java:964)
        at com.alibaba.dubbo.common.extension.ExtensionLoader.createAdaptiveExtension(ExtensionLoader.java:936)
        at com.alibaba.dubbo.common.extension.ExtensionLoader.getAdaptiveExtension(ExtensionLoader.java:561)
        at com.alibaba.dubbo.config.ServiceConfig.<clinit>(ServiceConfig.java:63)
        at sun.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)
        at sun.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:62)
        at sun.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:45)
        at java.lang.reflect.Constructor.newInstance(Constructor.java:423)
        at org.springframework.beans.BeanUtils.instantiateClass(BeanUtils.java:148)
        at org.springframework.beans.factory.support.SimpleInstantiationStrategy.instantiate(SimpleInstantiationStrategy.java:87)
        at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.instantiateBean(AbstractAutowireCapableBeanFactory.java:1032)
        at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.createBeanInstance(AbstractAutowireCapableBeanFactory.java:985)
        at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.doCreateBean(AbstractAutowireCapableBeanFactory.java:487)
        at org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory.createBean(AbstractAutowireCapableBeanFactory.java:458)
        at org.springframework.beans.factory.support.AbstractBeanFactory$1.getObject(AbstractBeanFactory.java:293)
        at org.springframework.beans.factory.support.DefaultSingletonBeanRegistry.getSingleton(DefaultSingletonBeanRegistry.java:223)
        at org.springframework.beans.factory.support.AbstractBeanFactory.doGetBean(AbstractBeanFactory.java:290)
        at org.springframework.beans.factory.support.AbstractBeanFactory.getBean(AbstractBeanFactory.java:191)
        at org.springframework.beans.factory.support.DefaultListableBeanFactory.preInstantiateSingletons(DefaultListableBeanFactory.java:636)
        at org.springframework.context.support.AbstractApplicationContext.finishBeanFactoryInitialization(AbstractApplicationContext.java:938)
        at org.springframework.context.support.AbstractApplicationContext.refresh(AbstractApplicationContext.java:479)
        at org.springframework.context.support.ClassPathXmlApplicationContext.<init>(ClassPathXmlApplicationContext.java:139)
        at org.springframework.context.support.ClassPathXmlApplicationContext.<init>(ClassPathXmlApplicationContext.java:93)
        at com.alibaba.dubbo.container.spring.SpringContainer.start(SpringContainer.java:50)
        at com.alibaba.dubbo.container.Main.main(Main.java:88)
        Caused by:compile error:no such field:dubbo
        at javassist.compiler.TypeChecker.fieldAccess(TypeChecker.java:845)
        at javassist.compiler.TypeChecker.atFieldRead(TypeChecker.java:803)
        at javassist.compiler.TypeChecker.atMember(TypeChecker.java:988)
        at javassist.compiler.JvstTypeChecker.atMember(JvstTypeChecker.java:66)
        at javassist.compiler.ast.Member.accept(Member.java:39)
        at javassist.compiler.TypeChecker.atCondExpr(TypeChecker.java:284)
        at javassist.compiler.ast.CondExpr.accept(CondExpr.java:43)
        at javassist.compiler.CodeGen.doTypeCheck(CodeGen.java:242)
        at javassist.compiler.CodeGen.atDeclarator(CodeGen.java:743)
        at javassist.compiler.ast.Declarator.accept(Declarator.java:100)
        at javassist.compiler.CodeGen.atStmnt(CodeGen.java:351)
        at javassist.compiler.ast.Stmnt.accept(Stmnt.java:50)
        at javassist.compiler.CodeGen.atStmnt(CodeGen.java:351)
        at javassist.compiler.ast.Stmnt.accept(Stmnt.java:50)
        at javassist.compiler.CodeGen.atMethodBody(CodeGen.java:292)
        at javassist.compiler.CodeGen.atMethodDecl(CodeGen.java:274)
        at javassist.compiler.ast.MethodDecl.accept(MethodDecl.java:44)
        at javassist.compiler.Javac.compileMethod(Javac.java:169)
        at javassist.compiler.Javac.compile(Javac.java:95)
        at javassist.CtNewMethod.make(CtNewMethod.java:74)
        ...30more

        at com.alibaba.dubbo.common.compiler.support.AbstractCompiler.compile(A