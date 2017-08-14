
### dubbo自带的注册中心新建 ###

----------

之前我们讨论了Zookeeper现在来说说Dubbo自己实现的简易注册中心

 	public Registry createRegistry(URL url) {
        url = getRegistryURL(url);
        List<URL> urls = new ArrayList<URL>();
        urls.add(url.removeParameter(Constants.BACKUP_KEY));
        String backup = url.getParameter(Constants.BACKUP_KEY);
        if (backup != null && backup.length() > 0) {
            String[] addre	sses = Constants.COMMA_SPLIT_PATTERN.split(backup);
            for (String address : addresses) {
                urls.add(url.setAddress(address));
            }
        }
        RegistryDirectory<RegistryService> directory = new RegistryDirectory<RegistryService>(RegistryService.class, url.addParameter(Constants.INTERFACE_KEY, RegistryService.class.getName()).addParameterAndEncoded(Constants.REFER_KEY, url.toParameterString()));
        Invoker<RegistryService> registryInvoker = cluster.join(directory);
        RegistryService registryService = proxyFactory.getProxy(registryInvoker);
        DubboRegistry registry = new DubboRegistry(registryInvoker, registryService);
        directory.setRegistry(registry);
        directory.setProtocol(protocol);
        directory.notify(urls);
        directory.subscribe(new URL(Constants.CONSUMER_PROTOCOL, NetUtils.getLocalHost(), 0, RegistryService.class.getName(), url.getParameters()));
        return registry;
    }

代码贴上了，逻辑上还是比较复杂的，比较其实现了注册中心，而不像zookeeper一样直接使用。

1. 通过入参url来构建注册中心url
2. 构建多个urls(将注册中心url尝试拆分成多个，包括主url和备份url，拆分依据在url上的参数backup所携带的信息)
3. 构建注册中心目录服务(目录服务是dubbo实现自带的注册中心的关键)
4. 使用cluster构建RegistryService服务的invoker
	1. 通过Porxy构建获得一个RegistryService代理
5. 新建一个dubbo注册中心
6. 设置目录服务的其他信息
	1. 注册中心信息
	2. 注册urls列表信息
	3. 订阅信息
7. 返回注册中心