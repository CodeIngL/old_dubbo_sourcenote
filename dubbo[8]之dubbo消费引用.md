## dubbo之消费引用 ##
上一旁dubbo之消费方，我们简单介绍了消费方暴露的整个过程，但是其中的细节还是没有进行涉及，读者可能还存在很多疑惑。本文，我们将对消费引用这一环节展开详细描述。

### 消息引用入口:refer ###

----------
该方法是消费方引用的入口，和我们之前的export功能在地位上功能相反，与export一样我们先来介绍最复杂的带注册中心的引用。

	refprotocol.refer(interfaceClass, url)
代码如同上，在我们基础上我们很容易明白refprotocol就是指
Protocol$Adaptive,自然我们要联想到RegistryProtocol

### RegistryProtocol.refer ###

----------
根据我们的思路，这个会被优先调用

		public <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException {
	        url = url.setProtocol(url.getParameter(Constants.REGISTRY_KEY, Constants.DEFAULT_REGISTRY)).removeParameter(Constants.REGISTRY_KEY);
	        Registry registry = registryFactory.getRegistry(url);
	        if (RegistryService.class.equals(type)) {
	        	return proxyFactory.getInvoker((T) registry, type, url);
	        }
	
	        // group="a,b" or group="*"
	        Map<String, String> qs = StringUtils.parseQueryString(url.getParameterAndDecoded(Constants.REFER_KEY));
	        String group = qs.get(Constants.GROUP_KEY);
	        if (group != null && group.length() > 0 ) {
	            if ( ( Constants.COMMA_SPLIT_PATTERN.split( group ) ).length > 1
	                    || "*".equals( group ) ) {
	                return doRefer( getMergeableCluster(), registry, type, url );
	            }
	        }
	        return doRefer(cluster, registry, type, url);
	    }

以上就是该方法的代码，逻辑如下

1. 处理url，
2. 获得注册中心
3. 如果type是RegistryService，获得invoker
4. 获得url中refer的键信息
5. 获得group，对group操作，使用mergeableCluster
6. 使用本身cluster

### RegistryProtocol.doRefer ###


----------


    private <T> Invoker<T> doRefer(Cluster cluster, Registry registry, Class<T> type, URL url) {
        RegistryDirectory<T> directory = new RegistryDirectory<T>(type, url);
        directory.setRegistry(registry);
        directory.setProtocol(protocol);
        URL subscribeUrl = new URL(Constants.CONSUMER_PROTOCOL, NetUtils.getLocalHost(), 0, type.getName(), directory.getUrl().getParameters());
        if (! Constants.ANY_VALUE.equals(url.getServiceInterface())
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
代码如上:



### 复杂的Invoker的获得 ###

----------
invoker是rpc框架中的一个概念，上面我们介绍了invoker的生成，消费方的最后的代理也需要invoker来得到，所以我们不得去看到底生成
了什么样的invoker。老规矩，我们用最复杂的情景来描述，带注册的中心的invoker

### 带注册中心的Invoker ###
	

----------

带注册中心的Invoker，也就是下面这两行代码

	URL u = registryURL.addParameter(Constants.CLUSTER_KEY, AvailableCluster.NAME);
	invoker = cluster.join(new StaticDirectory(u, invokers));

当然这里已经生成了inovker，显然我们需要关注invoker的生成。

	refprotocol.refer(interfaceClass, url)

该行代码就是invoker的生成的入口了。我们慢慢来分析

首先来看refprotocol

	private static final Protocol refprotocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();

看到这里，又回到了最初的地方，我们不能再熟悉了。意味这个这个refprotocol就是Protocol$Adaptive程序生成的一个类
，我们知道这些类只是其了代理的功能，化不多说，我们看refer的实现(程序生成)

	 <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException;

方法签名如上，方法实现如下:

	if (arg1 == null) throw new IllegalArgumentException("url == null");
	Url url = arg1
	String extName = ( url.getProtocol() == null ? dubbo : url.getProtocol() )
	if(extName == null) throw new IllegalStateException("Fail to get extension() name from url(" + url.toString() + ") use keys([xxx.xxx])");
	Protocol extension = (Protocol)ExtensionLoader.getExtensionLoader(Protocol.class).getExtension(extName)
	return extension.refer（arg0，arg1);

对于注册中心的URL我们知道，其protocol属性为registry。因此逻辑自然进入到RegistryProtocol中的refer方法.我们继续来看:


	public <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException {
        url = url.setProtocol(url.getParameter(Constants.REGISTRY_KEY, Constants.DEFAULT_REGISTRY)).removeParameter(Constants.REGISTRY_KEY);
        Registry registry = registryFactory.getRegistry(url);
        if (RegistryService.class.equals(type)) {
        	return proxyFactory.getInvoker((T) registry, type, url);
        }

        // group="a,b" or group="*"
        Map<String, String> qs = StringUtils.parseQueryString(url.getParameterAndDecoded(Constants.REFER_KEY));
        String group = qs.get(Constants.GROUP_KEY);
        if (group != null && group.length() > 0 ) {
            if ( ( Constants.COMMA_SPLIT_PATTERN.split( group ) ).length > 1
                    || "*".equals( group ) ) {
                return doRefer( getMergeableCluster(), registry, type, url );
            }
        }
        return doRefer(cluster, registry, type, url);
    }

代码如上，逻辑如下

1. 改变url，协议转换回来，当protcol为registry只是临时代表这个需要注册到注册中心上，但是真正的协议类型还是元信息中的registry的值
2. 获得registry，这个时候协议已经被装换过了，默认是dubbo，当然我们一般配置zookeeper
3. 获得refer信息
4. 处理group，如果有的话
	1. 使用MergeableCluster处理
5. 使用本身的cluster处理


具体处理的方法


    private <T> Invoker<T> doRefer(Cluster cluster, Registry registry, Class<T> type, URL url) {
        RegistryDirectory<T> directory = new RegistryDirectory<T>(type, url);
        directory.setRegistry(registry);
        directory.setProtocol(protocol);
        URL subscribeUrl = new URL(Constants.CONSUMER_PROTOCOL, NetUtils.getLocalHost(), 0, type.getName(), directory.getUrl().getParameters());
        if (! Constants.ANY_VALUE.equals(url.getServiceInterface())
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

逻辑如下:

1. 构建RegistryDirectory，使用type和url
2. 设置RegistryDirectory的registry为注册中心
3. 设置RegistryDirectory的protocol属性
4. 构建订阅的url。
5. 对于需要被注册的url，使用注册中心registry进行注册
6. 使用RegistryDirectory进行订阅
7. 返回有cluster合并的Invoker

### RegistryProtocol.refer的小结 ###

----------
到这里关于消费方注册中心的refer就到此为止了。但是关于网络这一块还是没有涉及，接下来我们针对其默认的DubboProtocol进行网络的展开


### DubboProtocol.refer ###

----------
有了前面的基础，现在我们将快速对这些方面进行展开


    public <T> Invoker<T> refer(Class<T> serviceType, URL url) throws RpcException {
        DubboInvoker<T> invoker = new DubboInvoker<T>(serviceType, url, getClients(url), invokers);
        invokers.add(invoker);
        return invoker;
    }

代码很简单，比之前的注册中心好很多，但是需要注意的地方却更多

### getClient网络展开 ###

----------

这就是我们需要注意的地方之一，网络细节全都封装在内部