## dubbo之消费引用 ##
上一篇我们简单的介绍了服务引用行为。但是还没讨论关键引用的细节，本文中我们将展开详细描述.

### refer消息引用入口 ###

----------
该方法是服务引用的入口，和我们之前的export功能相反。
与export一样我们先来介绍最复杂的带注册中心的引用

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

