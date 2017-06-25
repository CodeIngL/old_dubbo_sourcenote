
###重点方法之**getExtension**

	    public T getExtension(String name) {
	        if (name == null || name.length() == 0)
	            throw new IllegalArgumentException("Extension name == null");
	        if ("true".equals(name)) {
	            return getDefaultExtension();
	        }
	        //尝试从缓存中获得
	        Holder<Object> holder = cachedInstances.get(name);
	        if (holder == null) {
	            cachedInstances.putIfAbsent(name, new Holder<Object>());
	            holder = cachedInstances.get(name);
	        }
	        Object instance = holder.get();
	        if (instance == null) {
	            synchronized (holder) {
	                instance = holder.get();
	                if (instance == null) {
	                    //创建实例，
	                    instance = createExtension(name);
	                    holder.set(instance);
	                }
	            }
	        }
	        return (T) instance;
	    }

这个代码也是完整实现，和**getAdaptiveExtension**类似的这个的处理也是相似的，首先看对象属性的设置**cachedInstances**

	//缓存实例
    private final ConcurrentMap<String, Holder<Object>> cachedInstances = new ConcurrentHashMap<>();

值得说明的是这个Holder的包装存储的对象是非常复杂的。回头来看实现代码，关键全都委托给**createExtension**。该方法中做了具体如下的功能:

- 尝试从cachedClasses中获得对应的类型，找不到就出错了。
	- 涉及的类型都是前面诉说的v的普通类型。
- 尝试从缓存**EXTENSION_INSTANCES**中取得类类型对应的实例
- 完成对实例的注入，使用方法**injectExtension**
- 将实例注入代理类**cachedWrapperClasses**，并完成代理的实例注入
	- 循环整个代理类型集合
- 放回最后一个包装代理，如果**cachedWrapperClasses**不存在，则是本身。

###处处出现的SPI注解
---
dubbo中出现了很多实用SPI注解地方。几乎所有的interface都打上了@SPI注解，按住官方说明。这个注解是为了取代@Extension注解。这个注解代表了interface的默认扩展名，其值等价于**cachedDefaultName**，这个会映射配置文件中某个类（实现该interface），值得说明的是该类是一个普通实现实例，也就是上面我们所说的v的普通类型。
 - getDefaultExtension()会获得该实例。

###无处不在Protocol
---
我们经常在某些dubbo内部类中能看到:

	private static final Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
这里需要说明下，任何类带有这段编码的，其实际都是**Protocl$Adaptive**类实例。且这个实例是全局唯一的。这个类是dubbo内部编译生成的。生成情况如下:

	package com.alibaba.dubbo.rpc;

	import com.alibaba.dubbo.common.extension.ExtensionLoader;

	public class Protocol$Adpative implements com.alibaba.dubbo.rpc.Protocol {
		
		//tip:..............
	    //对于没带@Adaptive注解
		//其实现就是扔出异常:
		//UnsupportedOperationException
		//省略相关没带@Adaptive注解的方法


		//导出的方法
	    public com.alibaba.dubbo.rpc.Exporter export(com.alibaba.dubbo.rpc.Invoker arg0) throws com.alibaba.dubbo.rpc.RpcException {
	        if (arg0 == null) throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument == null");
	        if (arg0.getUrl() == null)
	            throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument getUrl() == null");
	        com.alibaba.dubbo.common.URL url = arg0.getUrl();
			//重点，选择Exporter，默认dubbo，看url中是否有protocol
	        String extName = (url.getProtocol() == null ? "dubbo" : url.getProtocol());
	        if (extName == null)
	            throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.Protocol) name from url(" + url.toString() + ") use keys([protocol])");
	        com.alibaba.dubbo.rpc.Protocol extension = (com.alibaba.dubbo.rpc.Protocol) ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.rpc.Protocol.class).getExtension(extName);
	        return extension.export(arg0);
	    }
		
		//导出的方法
	    public com.alibaba.dubbo.rpc.Invoker refer(java.lang.Class arg0, com.alibaba.dubbo.common.URL arg1) throws com.alibaba.dubbo.rpc.RpcException {
	        if (arg1 == null) throw new IllegalArgumentException("url == null");
	        com.alibaba.dubbo.common.URL url = arg1;
			//重点，选择Invoker，默认dubbo，看url中是否有protocol
	        String extName = (url.getProtocol() == null ? "dubbo" : url.getProtocol());
	        if (extName == null)
	            throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.Protocol) name from url(" + url.toString() + ") use keys([protocol])");
	        com.alibaba.dubbo.rpc.Protocol extension = (com.alibaba.dubbo.rpc.Protocol) ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.rpc.Protocol.class).getExtension(extName);
	        return extension.refer(arg0, arg1);
    }

###无处不在ProtocolFactory
---
这个也是经常dubbo内部类中能看到:

	private static final Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
这里需要说明下，任何类带有这段编码的，其实际都是**ProtoclFactory$Adaptive**类实例。且这个实例是全局唯一的。这个类是dubbo内部编译生成的。生成情况如下:

	package com.alibaba.dubbo.rpc;
        import com.alibaba.dubbo.common.extension.ExtensionLoader;

	public class ProxyFactory$Adpative implements com.alibaba.dubbo.rpc.ProxyFactory {
    
	    public com.alibaba.dubbo.rpc.Invoker getInvoker(java.lang.Object arg0, java.lang.Class arg1, com.alibaba.dubbo.common.URL arg2) throws com.alibaba.dubbo.rpc.RpcException {
	        if (arg2 == null) throw new IllegalArgumentException("url == null");
	        com.alibaba.dubbo.common.URL url = arg2;
			//重点默认总是JavassistProxyFactory
	        String extName = url.getParameter("proxy", "javassist");
	        if (extName == null)
	            throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.ProxyFactory) name from url(" + url.toString() + ") use keys([proxy])");
	        com.alibaba.dubbo.rpc.ProxyFactory extension = (com.alibaba.dubbo.rpc.ProxyFactory) ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.rpc.ProxyFactory.class).getExtension(extName);
	        return extension.getInvoker(arg0, arg1, arg2);
	    }
	
	    public java.lang.Object getProxy(com.alibaba.dubbo.rpc.Invoker arg0) throws com.alibaba.dubbo.rpc.RpcException {
	        if (arg0 == null) throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument == null");
	        if (arg0.getUrl() == null)
	            throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument getUrl() == null");
	        com.alibaba.dubbo.common.URL url = arg0.getUrl();
			//重点默认总是JavassistProxyFactory
	        String extName = url.getParameter("proxy", "javassist");
	        if (extName == null)
	            throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.ProxyFactory) name from url(" + url.toString() + ") use keys([proxy])");
	        com.alibaba.dubbo.rpc.ProxyFactory extension = (com.alibaba.dubbo.rpc.ProxyFactory) ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.rpc.ProxyFactory.class).getExtension(extName);
	        return extension.getProxy(arg0);
	    }
	}

### 封装封装，层层包装
---
我们首先来关注下，我们上面代码贴出的重点:

- DubboProtocol
- JavassistProxyFactory

####引起层层包装的缘由
dubbo总是包装我们上面所说的类，其来自

	//伪码
	ExtensionLoader.getExtension(name)
这是关键，这里会做出包装。


#### DubboProtocl的层层包装
---

对于DubboProtocol会被ProtocolFilterWrapper包装，继而被ProtocolListenerWrapper包装


#### DubboProtocl的层层包装
---

对于JavassistProxyFactory会被StubProxyFactoryWrapper包装

### 神秘的Invoker
---
Invoker是一个神秘的接口。借用官网的话：Invoker是Provider的一个可调用Service的抽象。
一般总是从ProxyFactory中获得

#### 默认的JavassistProxyFactory中的Invoker
---

    public <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url) {
        // TODO Wrapper类不能正确处理带$的类名
        final Wrapper wrapper = Wrapper.getWrapper(proxy.getClass().getName().indexOf('$') < 0 ? proxy.getClass() : type);
        return new AbstractProxyInvoker<T>(proxy, type, url) {
            @Override
            protected Object doInvoke(T proxy, String methodName, 
                                      Class<?>[] parameterTypes, 
                                      Object[] arguments) throws Throwable {
                return wrapper.invokeMethod(proxy, methodName, parameterTypes, arguments);
            }
        };
    }

Wrapper是一个特殊的类，也是负责包装的一个类。


### 服务的导出
服务导出是通过protocol.export实现的

#### 特殊的RegistryProtocol
---
对于一般的Protocol总是被ProtocolListenerWrapper和ProtocolFilterWrapper包装
对于ReigistryProtocol总是处理特殊的。尽管被包装了，事实上去没有做任何处理，而是简单链式传递给最终的RegistryProtocol处理

	    public <T> Exporter<T> export(final Invoker<T> originInvoker) throws RpcException {
	        //export invoker
	        final ExporterChangeableWrapper<T> exporter = doLocalExport(originInvoker);
	        //registry provider
	        final Registry registry = getRegistry(originInvoker);
	        final URL registedProviderUrl = getRegistedProviderUrl(originInvoker);
	        registry.register(registedProviderUrl);
	        // 订阅override数据
	        // FIXME 提供者订阅时，会影响同一JVM即暴露服务，又引用同一服务的的场景，因为subscribed以服务名为缓存的key，导致订阅信息覆盖。
	        final URL overrideSubscribeUrl = getSubscribedOverrideUrl(registedProviderUrl);
	        final OverrideListener overrideSubscribeListener = new OverrideListener(overrideSubscribeUrl);
	        overrideListeners.put(overrideSubscribeUrl, overrideSubscribeListener);
	        registry.subscribe(overrideSubscribeUrl, overrideSubscribeListener);
	        //保证每次export都返回一个新的exporter实例
	        return new Exporter<T>() {
	            public Invoker<T> getInvoker() {
	                return exporter.getInvoker();
	            }
	            public void unexport() {
	            	try {
	            		exporter.unexport();
	            	} catch (Throwable t) {
	                	logger.warn(t.getMessage(), t);
	                }
	                try {
	                	registry.unregister(registedProviderUrl);
	                } catch (Throwable t) {
	                	logger.warn(t.getMessage(), t);
	                }
	                try {
	                	overrideListeners.remove(overrideSubscribeUrl);
	                	registry.unsubscribe(overrideSubscribeUrl, overrideSubscribeListener);
	                } catch (Throwable t) {
	                	logger.warn(t.getMessage(), t);
	                }
	            }
	        };
	    }

总是先对Invoker处理