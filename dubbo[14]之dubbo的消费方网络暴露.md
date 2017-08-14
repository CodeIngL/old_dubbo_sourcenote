### 消费方网络暴露 ###

----------
在rpc中，网络通讯的双方都是很重要的，除了服务方暴露网络提供服务之外，消费方也需暴露网络和服务方进行交互

### getClient网络的入口 ###

----------

	private ExchangeClient[] getClients(URL url) {
        boolean service_share_connect = false;
        int connections = url.getParameter(Constants.CONNECTIONS_KEY, 0);
        if (connections == 0) {
            service_share_connect = true;
            connections = 1;
        }

        ExchangeClient[] clients = new ExchangeClient[connections];
        for (int i = 0; i < clients.length; i++) {
            if (service_share_connect) {
                clients[i] = getSharedClient(url);
            } else {
                clients[i] = initClient(url);
            }
        }
        return clients;
    }
该方法就是消费端网络的入口，我慢慢来分析。

1. 根据url中的信息来确定是否进行共享连接还是每服务每连接
	1. url中的connections配置为0则是共享连接
	2. 反之。
2. 对于共享连接使用**getSharedClient**
3. 反之**initClient**

逻辑比较简单，我们具体来关注下共享连接，毕竟里面肯定包含了**initClient**方法


### getSharedClient共享连接 ###

----------

	private ExchangeClient getSharedClient(URL url) {
        String key = url.getAddress();
        ReferenceCountExchangeClient client = referenceClientMap.get(key);
        if (client != null) {
            if (!client.isClosed()) {
                client.incrementAndGetCount();
                return client;
            } else {
                referenceClientMap.remove(key);
            }
        }
        client = new ReferenceCountExchangeClient(initClient(url), ghostClientMap);
        referenceClientMap.put(key, client);
        ghostClientMap.remove(key);
        return client;
    }
代码如上，逻辑还是比较简单，维持一个缓存来获得连接，当然每次还是需要检测下相应的连接客户端，无效要及时移除并重新生成。

### initClient获得连接客户端 ###

----------

这个方法，细心的读者肯定在上面看见多回了。这里面包含了网络的细节，也是本变文章的重点中的重点。

	private ExchangeClient initClient(URL url) {
        url = url.addParameter(Constants.CODEC_KEY, DubboCodec.NAME);
        url = url.addParameterIfAbsent(Constants.HEARTBEAT_KEY, String.valueOf(Constants.DEFAULT_HEARTBEAT));

        // BIO存在严重性能问题，暂时不允许使用
        String str = url.getParameter(Constants.CLIENT_KEY, url.getParameter(Constants.SERVER_KEY, Constants.DEFAULT_REMOTING_CLIENT));
        if (!ExtensionLoader.getExtensionLoader(Transporter.class).hasExtension(str)) {
            throw new RpcException("Unsupported client type: " + str + "," +
                    " supported client type is " + StringUtils.join(ExtensionLoader.getExtensionLoader(Transporter.class).getSupportedExtensions(), " "));
        }

        ExchangeClient client;
        try {
            //设置连接应该是lazy的 
            if (url.getParameter(Constants.LAZY_CONNECT_KEY, false)) {
                client = new LazyConnectExchangeClient(url, requestHandler);
            } else {
                client = Exchangers.connect(url, requestHandler);
            }
        } catch (RemotingException e) {
            throw new RpcException("Fail to create remoting client for service(" + url + "): " + e.getMessage(), e);
        }
        return client;
    }

代码不算多，我们还是老规矩，分析代码。我们发现了很多和之前文章写的服务方的网络暴露很一致。细心的读者，可以看到其实基本上一致的。

### 小结 ###

----------

基本上消费方的网络暴露就结束了。 dubbo系列文章也要进入尾声了，最后几篇亦会补上

### 计划 ###

----------
dubbo要结束了，但是rpc的学习总是没有结束的，很快我们将开始新rpc的介绍。期待我的系列文章grpc源码分析。