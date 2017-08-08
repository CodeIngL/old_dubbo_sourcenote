## dubbo创建代理 ##

创建代理是dubbo内常见的用法，尤其在消费方，我们可以看到最终的接口就是通过创建代理来封装了所有的底层细节

	return (T) proxyFactory.getProxy(invoker);

一行代码，将invoker参数进行实现获得了其泛型具体类型的服务代理。Invoker我们已经在前面的文章解释过了，这里我们将展开创建代理的细节，最重要的是代理类的生成。

默认情况下，程序将生成ProxyFactory$Adpative,然后执行包装了JavassistProxyFactory的包装类StubProxyFactoryWrapper。

>tip: 忘记的童鞋请移步dubbo杂点一文

### StubProxyFactoryWrapper代理下的统一入口 ###

----------

我们知道StubProxyFactoryWrapper是ProxyFactory代理的入口，最终会因为开发者配置选择不一样的实现，默认情况下最终会委托给JavassistProxyFactory进行最终处理，这是后话，我们先看该类中所处理的事情。源码如下:

 	public <T> T getProxy(Invoker<T> invoker) throws RpcException {
        T proxy = proxyFactory.getProxy(invoker);
        if (GenericService.class != invoker.getInterface()) {
            String stub = invoker.getUrl().getParameter(Constants.STUB_KEY, invoker.getUrl().getParameter(Constants.LOCAL_KEY));
            if (ConfigUtils.isNotEmpty(stub)) {
                Class<?> serviceType = invoker.getInterface();
                if (ConfigUtils.isDefault(stub)) {
                    if (invoker.getUrl().hasParameter(Constants.STUB_KEY)) {
                        stub = serviceType.getName() + "Stub";
                    } else {
                        stub = serviceType.getName() + "Local";
                    }
                }
                try {
                    Class<?> stubClass = ReflectUtils.forName(stub);
                    if (! serviceType.isAssignableFrom(stubClass)) {
                        throw new IllegalStateException("The stub implemention class " + stubClass.getName() + " not implement interface " + serviceType.getName());
                    }
                    try {
                        Constructor<?> constructor = ReflectUtils.findConstructor(stubClass, serviceType);
                        proxy = (T) constructor.newInstance(new Object[] {proxy});
                        //export stub service
                        URL url = invoker.getUrl();
                        if (url.getParameter(Constants.STUB_EVENT_KEY, Constants.DEFAULT_STUB_EVENT)){
                            url = url.addParameter(Constants.STUB_EVENT_METHODS_KEY, StringUtils.join(Wrapper.getWrapper(proxy.getClass()).getDeclaredMethodNames(), ","));
                            url = url.addParameter(Constants.IS_SERVER_KEY, Boolean.FALSE.toString());
                            try{
                                export(proxy, (Class)invoker.getInterface(), url);
                            }catch (Exception e) {
                                //记录日志
                            }
                        }
                    } catch (NoSuchMethodException e) {
						//扔出非法异常
                    }
                } catch (Throwable t) {
                    //记录日志
                }
            }
        }
        return proxy;
    }

从源码看出与其他多数接口的代理不同，该类委托了内部调用，也就是被代理的属性proxyFactor(默认JavassistProxyFactory)
后又进行了收尾工作。对于收尾工作我们稍后再提，我们先关注我们该文的重点。

### AbstractProxyFactory的getProxy ###

----------

默认的JavassistProxyFactory和JdkProxyFactory一样。他们本身并没有实现getProxy方法，而是在其的共同的抽象父类中实现，然后父类暴露给他们抽象方法，让他们实现，在父类中回调。这种设计策略在dubbo整个框架中贯穿。话不多说，我们先看其实现吧

 	public <T> T getProxy(Invoker<T> invoker) throws RpcException {
        Class<?>[] interfaces = null;
        String config = invoker.getUrl().getParameter("interfaces");
        if (config != null && config.length() > 0) {
            String[] types = Constants.COMMA_SPLIT_PATTERN.split(config);
            interfaces = new Class<?>[types.length + 2];
            interfaces[0] = invoker.getInterface();
            interfaces[1] = EchoService.class;
            for (int i = 0; i < types.length; i++) {
                interfaces[i + 1] = ReflectUtils.forName(types[i]);
            }
        }
        if (interfaces == null) {
            interfaces = new Class<?>[]{invoker.getInterface(), EchoService.class};
        }
        return getProxy(invoker, interfaces);
    }
代码很短，如同我们上面所说的一样真正的逻辑回调给子类的实现。我们看看其额外的处理：

1. 处理元信息中的配置(key:interfaces;value:局部变量config)
2. 尝试构建一个Class的数组，作为参数传递
	1. config有配置，除了使用invoker携带的接口，加入config获得接口
	2. config无配置，使用invoker携带的接口和EchoService接口
3. 回调子类实现

### JavassistProxyFactory的getProxy ###

----------

该方法即是AbstractProxyFactory进行回调子类的方法，由子类自己实现。

    public <T> T getProxy(Invoker<T> invoker, Class<?>[] interfaces) {
        return (T) Proxy.getProxy(interfaces).newInstance(new InvokerInvocationHandler(invoker));
    }

仅仅一行代码就获得传递进来的接口实现，这里采取了Proxy给接口生成代理。与JdkProxyFactory不同的时，Jdk也是一行代码，但他使用JDK自动的Proxy进行生成


### Javassist之Proxy生成代理 ###

----------

我们讨论的是Javassist下的dubbo自带的Proxy生成代理和我们之前所说的Wrapper很像，但是和Wrapper肯定有所区别,我们娓娓道来:

	public static Proxy getProxy(Class<?>... ics){
		return getProxy(ClassHelper.getCallerClassLoader(Proxy.class), ics);
	}

以上代码实现，我们发现还是一行到代码，继续深入

	public static Proxy getProxy(ClassLoader cl, Class<?>... ics)

以上就是最终生成代理的方法了，值得注意的是这里贴出的是方法签名而不是代码实现，Wrapper一样，这个方法实现太长了。我们简单的以文字描述的口吻来实现整个逻辑的介绍，辅助一部分代码示意

1. 首先是入参的检验，ics包含的类型必须都是接口，接着通过反射使用入参（cl）类加载器尝试继续访问该类
2. 构建key:连接接口数组[com.codeL.A,com.codeL.B]-->com.codeL.A;com.codeL.B为key
3. 获得入参cl对应的缓存(ProxyCacheMap)，无则新建
4. 尝试从缓存(ProxyCacheMap中cl对应的映射)中使用key获得相应的代理，无则新建，这里使用了一个中间态变量来避免竞争
5. 具体新建过程代理实现接口方法的过程
6. 缓存操作进行收尾


### 实现接口方法的过程 ###

----------
最终的代理将实现所有传递进来的接口，并对接口之间的一致方法(包括方法名和入参)，进行去重后实现

1. 获得数字(线程安全)用来区分各种代理，保证了类的不同
2. 验证接口的可访问性（检验包名）:
	
		if (!Modifier.isPublic(ics[i].getModifiers())) {
        	//多个接口时，需要接口在同一个包中
            String npkg = ics[i].getPackage().getName();
            if (pkg == null) {
            	pkg = npkg;
            } else {
            	if (!pkg.equals(npkg))
            			throw new IllegalArgumentException("non-public interfaces from different packages");		
			}
		}
3. 为类添加接口信息
4. 实现接口的方法
	
		类型1 方法1(参数1，参数2){
			Object[] args = new Object[参数.length]
			args[0] = ($w)$1;
			args[2] = ($w)$2;
			...
			Object ret = handler.invoke(this, 方法1, args);
			对于void:结束了
			对于有返回值(基本类型特殊处理)
			return (类型1)ret
		}
5. 为类添加实现方法
6. 为类添加类名${pkg}.proxy${数字}
7. 为类添加字段
		1. public static java.lang.reflect.Method[] methods;
		2. private java.lang.reflect.InvocationHandler handler;
7. 为类添加构造函数
	1. 默认构造函数
	2. public 类1(java.lang.reflect.InvocationHandler handler){ handler=$1;}
8. 生成类，为methods添加默认值类实现的接口方法

9. 创建包装类com.alibaba.dubbo.common.bytecode.Proxy${数字}
10. 添加默认构造函数
11. 添加父类信息Proxy
12. 添加方法
	1. public Object newInstance(java.lang.reflect.InvocationHandler h){ return new ${pkg}.proxy${数字}($1); }
13. 生成类

整个过程逻辑基本就是这样，我们发现了里面创建两个类，最终Proxy的子类实现了外观暴露给使用

### 小结 ###

----------
到此dubbo中的创建代理基本就结束了，我们上面留了一个小尾巴，关于收尾的工作
也就是StubProxyFactoryWrapper中做的事情。我们现在来介绍

### StubProxyFactoryWrapper的收尾 ###

----------
收尾工作还是做了很多事情，这里的收尾针对的是invoker中持有的不是泛化接口。对于泛化接口，我们将在后续文章介绍。
继续介绍收尾，假设invoker中持有是普通的接口，我们来看收尾这个过程处理了什么事情

1. 从url(元信息)中获得桩点(stub)
	1. 在我们之前的文章中介绍到了配置项中的local和stub两项配置，local逐渐配废弃。
2. 在有桩点情况下需要暴露mock服务
	1. 获得mock服务的类名
	2. 检查mock类的合法性(mock服务还是需要实现invoker持有的接口，mock服务必须带有构造函数实现包装接口)
	3. 将返回的代理使用mock进行包装
	4. 获得invoker中持有的元信息
	5. 根据url信息是否持有dubbo.stub.event配置进行处理
		1. 持有的情况下，向url中添加参数("dubbo.stub.event.methods",代理暴露的方法)；（"isserver":"false"）
	6. 将mock服务进行暴露


### 总结 ###

----------

到这里dubbo的创建代理一文也就结束了，希望给读者带了对dubbo更深的了解

