## RPC之Invoker ##

Invoker我们之前提到很多，但是我们都没有对这个概念进行深入的探讨。

Invoker广泛应用于网络编程的双方，英文翻译调用者，对于网络的双方来说，总是通过一定的协议去获得远程的数据，Invoker就是其中的执行者。

由于网络的应答性，总是需要两边存在同样的概念，因此无论是客户端中还是服务端中，这个概念总是都存在。

我们现在来探讨下dubbo这个rpc框架中的Invoker


### 消费方之Invoker ###

----------
消费方我们之前也提到过来，其获得invoker的方式是通过refer方法调用得到的，我们慢慢来看。

### 消费方refer获得Invoker ###

----------
refer为**Protocol**接口的一个方法。我们首先贴出方法签名。

	@SPI("dubbo")
	public interface Protocol {

		//.....省略其他方法
	
		/**
	     * 引用远程服务：<br>
	     * 1. 当用户调用refer()所返回的Invoker对象的invoke()方法时，协议需相应执行同URL远端export()传入的Invoker对象的invoke()方法。<br>
	     * 2. refer()返回的Invoker由协议实现，协议通常需要在此Invoker中发送远程请求。<br>
	     * 3. 当url中有设置check=false时，连接失败不能抛出异常，并内部自动恢复。<br>
	     * 
	     * @param <T> 服务的类型
	     * @param type 服务的类型
	     * @param url 远程服务的URL地址
	     * @return invoker 服务的本地代理
	     * @throws RpcException 当连接服务提供方失败时抛出
	     */
	    @Adaptive
	    <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException;
	
	}

接口上dubbo官方已经做出了相应的解释。

refer的实现类有很多，为了更好的探索源码，每一个实现类我们都需要一一探索。我们先从我个人认为的最重要的关于注册中心的RegistryProtocol说起。

当然我们首先要引入一个tip

> RegistryProtocol和大多说Protocol一样，都只是ExtensionLoader<Protocol>对应的普通扩展类，获得普通扩展类之前，会被静态代理代理一把也就是**ProtocolFilterWrapper**和**ProtocolListenerWrapper**包装一把。

### RegistryProtocol的refer ###

----------
之前我说到这个是最为重要的Protocol实现，当然重要的原因，就是因为他是被特殊处理的。在静态代理的包装中，代理类面对这些什么都没做，而是委托给被代理对象处理。


	public <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException {
        url = url.setProtocol(url.getParameter(Constants.REGISTRY_KEY, Constants.DEFAULT_REGISTRY)).removeParameter(Constants.REGISTRY_KEY);
        Registry registry = registryFactory.getRegistry(url);
        if (RegistryService.class.equals(type)) {
            return proxyFactory.getInvoker((T) registry, type, url);
        }

        Map<String, String> qs = StringUtils.parseQueryString(url.getParameterAndDecoded(Constants.REFER_KEY));
        String group = qs.get(Constants.GROUP_KEY);
        if (group != null && group.length() > 0) {
            if ((Constants.COMMA_SPLIT_PATTERN.split(group)).length > 1
                    || "*".equals(group)) {
                return doRefer(getMergeableCluster(), registry, type, url);
            }
        }
        return doRefer(cluster, registry, type, url);
    }
以上就是RegistryProtocol的refer实现代码。逻辑代码比服务端也简单很多

1. 设置schemal: url中的registry代表了实际使用的注册协议(默认是dubbo，当然这里开发者会设置为zookeeper)
2. 从元信息中获得注册中心
3. 对应需要引用的接口是RegistryService，则使用proxyFactory来获得invoker直接返回
4. 对引用的参数信息(url中的refer键对应的映射)，处理group
5. 使用doRefer进行引用
	1. group配置为多个或者是通配符使用MergeableCluster
	2. 否则使用本身的cluster对应的属性


### refer中获得注册中心 ###

----------

注册中心在分布式程序扮演的角色相当的重要，通吃使用注册中心来实现分布式程序的高可用。
 
	Registry registry = registryFactory.getRegistry(url);

也就是上面提到的从元信息中获得注册中心，在这里对应代码中的结构Registry。

值得说明的是除了Redis之外，其他都是使用AbstractRegistryFactory的方法（包括dubbo自己实现的注册中心，以及zk注册中心和Multicast注册中心）

首先我们先来看下RegistryFactory的接口方法签名，代码如下:

	@SPI("dubbo")
	public interface RegistryFactory {
	
	    /**
	     * 连接注册中心.
	     * 
	     * 连接注册中心需处理契约：<br>
	     * 1. 当设置check=false时表示不检查连接，否则在连接不上时抛出异常。<br>
	     * 2. 支持URL上的username:password权限认证。<br>
	     * 3. 支持backup=10.20.153.10备选注册中心集群地址。<br>
	     * 4. 支持file=registry.cache本地磁盘文件缓存。<br>
	     * 5. 支持timeout=1000请求超时设置。<br>
	     * 6. 支持session=60000会话超时或过期设置。<br>
	     * 
	     * @param url 注册中心地址，不允许为空
	     * @return 注册中心引用，总不返回空
	     */
	    @Adaptive({"protocol"})
	    Registry getRegistry(URL url);
	
	}

我们可以看出官方对其进行了简单的说明，现在我们再贴出AbstractRegistryFactory的实现,进行深入探讨

 	public Registry getRegistry(URL url) {
    	url = url.setPath(RegistryService.class.getName())
    			.addParameter(Constants.INTERFACE_KEY, RegistryService.class.getName())
    			.removeParameters(Constants.EXPORT_KEY, Constants.REFER_KEY);
    	String key = url.toServiceString();
        LOCK.lock();
        try {
            Registry registry = REGISTRIES.get(key);
            if (registry != null) {
                return registry;
            }
            registry = createRegistry(url);
            if (registry == null) {
                throw new IllegalStateException("Can not create registry " + url);
            }
            REGISTRIES.put(key, registry);
            return registry;
        } finally {
            LOCK.unlock();
        }
    }

代码不是很多，也比较容易理解，这里我将娓娓道来。

1. 对url设定上下文，path为com.alibaba.dubbo.registry.RegistryService
2. 对url添加参数键值对（interface:com.alibaba.dubbo.registry.RegistryService）
3. 移除url上的服务端消费端信息(export;refer)
4. 获得服务地址(key) protocol://username:password@(ip or host):port/（path or serviceKey）?参数xxxx=xxxx
5. 缓存操作，无则新建

tip: url的每个操作都会进行浅copy生成新的url

### 注册中心新建 ###

----------

一般我们都会使用zookeeper作为注册中心，自然这个就是我们关注的重点

	public Registry createRegistry(URL url) {
        return new ZookeeperRegistry(url, zookeeperTransporter);
    }

zookeeper很简单，我们之前也详细的说过，我们继续整个流程


### 对于接口为RegistryService ###

----------
对于接口为RegistryService，不需要任何包装了，直接返回。


### 处理group ###

----------

熟悉dubbo配置童鞋知道，group是一个常用的配置。在这里进行了group的配置项处理

        // group="a,b" or group="*"
        //处理group配置项
        Map<String, String> qs = StringUtils.parseQueryString(url.getParameterAndDecoded(Constants.REFER_KEY));
        String group = qs.get(Constants.GROUP_KEY);
        if (group != null && group.length() > 0) {
            if ((Constants.COMMA_SPLIT_PATTERN.split(group)).length > 1
                    || "*".equals(group)) {
                return doRefer(getMergeableCluster(), registry, type, url);
            }
        }
        return doRefer(cluster, registry, type, url);

如同代码所说，当一个接口有多个group或者通配group时候，使用的策略是不一样的。
 1. 符合上面描述的时候使用，使用MergeableCluster进行包装
 2. 否则使用默认自带的FailoverCluster进行包装

### doRefer的处理 ###

----------

    private <T> Invoker<T> doRefer(Cluster cluster, Registry registry, Class<T> type, URL url) {
        RegistryDirectory<T> directory = new RegistryDirectory<T>(type, url);
        directory.setRegistry(registry);
        directory.setProtocol(protocol);
        URL subscribeUrl = new URL(Constants.CONSUMER_PROTOCOL, NetUtils.getLocalHost(), 0, type.getName(), directory.getUrl().getParameters());
        if (!Constants.ANY_VALUE.equals(url.getServiceInterface())
                && url.getParameter(Constants.REGISTER_KEY, true)) {
            registry.register(subscribeUrl.addParameters(Constants.CATEGORY_KEY, Constants.CONSUMERS_CATEGORY,
                    Constants.CHECK_KEY, String.valueOf(false)));
        }
        directory.subscribe(subscribeUrl.addParameter(Constants.CATEGORY_KEY,
                Constants.PROVIDERS_CATEGORY
                        + "," + Constants.CONFIGURATORS_CATEGORY
                        + "," + Constants.ROUTERS_CATEGORY));
        return cluster.join(directory);
    }

doRefer是最终的逻辑处理，逻辑如下:

1. 新建一个注册中心目录服务
2. 向目录服务设置注册中心
3. 向目录服务设置协议
4. 构建订阅的url信息，对于需要注册到注册中心进行注册
5. 向目录服务设置订阅url
4. 返回由cluster(默认是AvailableCluster)包装的invoker

