## dubbo真正的导出
上一篇，我们详细的讲述了dubbo服务的导出，细心的读者，注意到其实都是元数据的生成，并没有涉及真正导出。上一篇结尾，我们已经提到了真正的导出，这一篇我们讲详细的叙述整个过程。

### dubbo导出代码
---
我们先贴出出上一篇结尾最后说道的代码,增加下印象:

	Invoker<?> invoker = proxyFactory.getInvoker(ref, (Class) interfaceClass, registryURL.addParameterAndEncoded(Constants.EXPORT_KEY, url.toFullString()));

    Exporter<?> exporter = protocol.export(invoker);

上述代码是最为复杂的使用注册中心的远程暴露，上一篇我们也说明了这两个实际的操作:

- 使用ProxyFactory$Adaptive根据registryURL来获得Invoker

- 使用Proxy$Adaptive将获得的Invoker导出实现服务暴露


### 导出之getInvoker
---
Invoker是dubbo中非常重要的一个概念。我们看一下上面的实际代码:

	public class ProxyFactory$Adpative implements com.alibaba.dubbo.rpc.ProxyFactory {
    
	    public com.alibaba.dubbo.rpc.Invoker getInvoker(java.lang.Object arg0, java.lang.Class arg1, com.alibaba.dubbo.common.URL arg2) throws com.alibaba.dubbo.rpc.RpcException {
	        if (arg2 == null) throw new IllegalArgumentException("url == null");
	        com.alibaba.dubbo.common.URL url = arg2;
	        String extName = url.getParameter("proxy", "javassist");
	        if (extName == null)
	            throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.ProxyFactory) name from url(" + url.toString() + ") use keys([proxy])");
	        com.alibaba.dubbo.rpc.ProxyFactory extension = (com.alibaba.dubbo.rpc.ProxyFactory) ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.rpc.ProxyFactory.class).getExtension(extName);
	        return extension.getInvoker(arg0, arg1, arg2);
	    }
		//...省略其他方法
	}
上述就是ProxyFactory$Adaptive的代码了，该类实际不存在，是运行时生成，我们看到其getInvoker的代码逻辑很简单，主要是为了获得扩展类。  
dubbo关于ProxyFactory普通的扩展类只有两个。封装包装类只有一个

- JavassistProxyFactory：普通扩展类
- JdkProxyFactory:普通扩展类
- StubProxyFactoryWrapper：封装包装类

这里读者可能忘记了这些的区别，请异步到dubbo杂点中重新看一下。

默认是javassist（StubProxyFactoryWrapper封装），可以通过url中的参数修改，但是一般没有必要，JavassistProxyFactory性能上是明显好于JdkProxyFactory的。


#### JavassistProxyFactory之getInvoker
---

	public <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url) {
        // TODO Wrapper类不能正确处理带$的类名
        final Wrapper wrapper = Wrapper.getWrapper(proxy.getClass().getName().indexOf('$') != -1 ? proxy.getClass() : type);
        return new AbstractProxyInvoker<T>(proxy, type, url) {
            @Override
            protected Object doInvoke(T proxy, String methodName, 
                                      Class<?>[] parameterTypes, 
                                      Object[] arguments) throws Throwable {
                return wrapper.invokeMethod(proxy, methodName, parameterTypes, arguments);
            }
        };
    }
代码也是很简单，主要功能如下:

- 从proxy获得类型，被代理的原始类型
	- proxy是原始类型，直接使用proxy
	- proxy是代理类型，直接使用type
	
- 生成wrapper

- 返回AbstractProxyInvoker

我们主要来看生成wrapper

		public static Wrapper getWrapper(Class<?> c)
	    {
	        while( ClassGenerator.isDynamicClass(c) ) // can not wrapper on dynamic class.
	            c = c.getSuperclass();
	
	        if( c == Object.class )
	            return OBJECT_WRAPPER;
	
	        Wrapper ret = WRAPPER_MAP.get(c);
	        if( ret == null )
	        {
	            ret = makeWrapper(c);
	            WRAPPER_MAP.put(c,ret);
	        }
	        return ret;
	    }
代码也贴上了，一眼见名，实际的操作还是在makewrapper中 

#### 重点方法之makewrapper
---
makewrapper实现了对一个类的封装，代理。代码过长，我们来叙述下这个过程

- 见代码注解

上述过程后，一个Inovker就顺利导出了。

### 导出之export
---
这个是服务导出的最终方法，也就是获得Exporter。我们看一下上面的实际代码:

	
	public class Protocol$Adpative implements com.alibaba.dubbo.rpc.Protocol {
	    
	    public com.alibaba.dubbo.rpc.Exporter export(com.alibaba.dubbo.rpc.Invoker arg0) throws com.alibaba.dubbo.rpc.RpcException {
	        if (arg0 == null) throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument == null");
	        if (arg0.getUrl() == null)
	            throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument getUrl() == null");
	        com.alibaba.dubbo.common.URL url = arg0.getUrl();
	        String extName = (url.getProtocol() == null ? "dubbo" : url.getProtocol());
	        if (extName == null)
	            throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.Protocol) name from url(" + url.toString() + ") use keys([protocol])");
	        com.alibaba.dubbo.rpc.Protocol extension = (com.alibaba.dubbo.rpc.Protocol) ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.rpc.Protocol.class).getExtension(extName);
	        return extension.export(arg0);
	    }
		//...省略其他方法
	}

上述就是Proxy$Adaptive的代码了，该类实际不存在，也是运行时生成，我们看到其export的代码逻辑很简单，主要是为了获得扩展类。  对于使用注册中心暴露的协议总是registry
dubbo关于Proxy普通的扩展类有很多。封装包装类只有两个

- ProtocolFilterWrapper：封装包装类
- ProtocolListenerWrapper：封装包装类

.getExtension(extName)获得相应的普通实现类，总是被包装类给包装。如何包装见dubbo杂点


对于实现类RegistryProtocol，这些包装类总是什么事情都不做，因而，我们之间看实现类中的导出


### 注册中心RegistryProtocol之export
---
上面我们说到这个类的处理有些特别，包装类总是对该类任何事情都不做。

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

以上就是注册中心export的全部代码了。

1. 获得ExporterChangeableWrapper
	1. 尝试使用缓存，缓存的key来自originInvoker中解析出来的协议配置url
	2. 没有则新建
		1. 新建一个Invoker委托，包装了originInvoker，和协议配置url
		2. 使用Protocol$Adpative导出委托inovker，获得一个exporter
		3. 使用exporter和originInvoker构建ExporterChangeableWrapper实例
		4. 将ExporterChangeableWrapper实例加入缓存
2. 获得注册中心实例
	1. 从originInvoker获得初始的注册的url
	2. 对注册的url进行简单处理
	3. 从RegistryFactory$Adpative中获得注册实例，对于zookeeper形式来说，等价于是用其父类的方法
3. 获得在注册中心上注册的协议配置URL
4. 使用相关注册实例对协议配置URL进行注册
5. 从协议配置URL中获得可覆盖订阅URL
6. 使用可覆盖的订阅URL来生成订阅者
7. 放置缓存中
8. 向注册中心订阅
9. 放回一个新建的Exporter

### 真实的导出之doLocalExport
---
在这个中，协议配置类才会被导出，也就是上面的第一小步，我们贴出相关代码

	    private <T> ExporterChangeableWrapper<T>  doLocalExport(final Invoker<T> originInvoker){
	        String key = getCacheKey(originInvoker);
	        ExporterChangeableWrapper<T> exporter = (ExporterChangeableWrapper<T>) bounds.get(key);
	        if (exporter == null) {
	            synchronized (bounds) {
	                exporter = (ExporterChangeableWrapper<T>) bounds.get(key);
	                if (exporter == null) {
	                    final Invoker<?> invokerDelegete = new InvokerDelegete<T>(originInvoker, getProviderUrl(originInvoker));
	                    exporter = new ExporterChangeableWrapper<T>((Exporter<T>)protocol.export(invokerDelegete), originInvoker);
	                    bounds.put(key, exporter);
	                }
	            }
	        }
	        return (ExporterChangeableWrapper<T>) exporter;
	    }
这一步中，尝试获得了协议配置类url，然后又使用protocol去导出invoker委托，这里实际上就是执行了默认情况下，DubboProtocol，当然还有过滤的几个包装类ProtocolFilterWrapper和ProtocolListenerWrapper。我们重点关注DubboProtocol，会过头来等下说明上面包装代理类


### DubboProtocol
---
这个是协议配置的导出类，也就是将服务暴露的关键

    public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {

        //获得协议url
        URL url = invoker.getUrl();
        
        // export service.
        //从url中获得key服务标识
        String key = serviceKey(url);

        //构建
        DubboExporter<T> exporter = new DubboExporter<T>(invoker, key, exporterMap);

        //放入缓存
        exporterMap.put(key, exporter);
        
        //export an stub service for dispaching event
        //从url中获得dubbo.stub.event的值 默认是false
        //从url中获得is_callback_service的值 默认是false
        Boolean isStubSupportEvent = url.getParameter(Constants.STUB_EVENT_KEY,Constants.DEFAULT_STUB_EVENT);
        Boolean isCallbackservice = url.getParameter(Constants.IS_CALLBACK_SERVICE, false);
        if (isStubSupportEvent && !isCallbackservice){
            //获得桩服务名字
            String stubServiceMethods = url.getParameter(Constants.STUB_EVENT_METHODS_KEY);
            if (stubServiceMethods == null || stubServiceMethods.length() == 0 ){
                if (logger.isWarnEnabled()){
                    logger.warn(new IllegalStateException("consumer [" +url.getParameter(Constants.INTERFACE_KEY) +
                            "], has set stubproxy support event ,but no stub methods founded."));
                }
            } else {
                //存在放入缓存
                stubServiceMethodsMap.put(url.getServiceKey(), stubServiceMethods);
            }
        }

        //open
        openServer(url);
        
        return exporter;
    }
上面就是该类关键的服务导出执行代码了，干了很多事情啊

1. 从invoker中获得相应的配置URL
2. 从配置url中获得服务标识
3. 构建dubboExporter
4. 放入缓存
5. 根据url的配置决定是否为服务打桩，就是在服务失败后回调
6. openServer(url)开启网络

