## dubbo网络暴露

  对于一款rpc框架，网络通信这一块比不可少，也就是说涉及到网络编程是必不可少的。而对于网络编程，重要的考虑就是设计合适的通讯协议，选择合适的序列化框架，以及网络框架开发框架，这些因素对rpc框架的性能指标影响重大。

  dubbo的网络开发框架支持多种形式，比如netty，mina等等,采用了自定义的通讯协议，默认使用hession作为序列化协议。  

  dubbo本身对网络编程中相关概念例如channel，transport做了抽象处理，方便接入不同的网络框架，解除对某一特定网络框架的依赖。

### openServer(url)
---

这是DubboProtocol中开启网络服务的入口，在本篇中，我们将会详细探讨研究网络服务的开启。
以下是代码示意:

    private void openServer(URL url) {
        String key = url.getAddress();
        boolean isServer = url.getParameter(Constants.IS_SERVER_KEY,true);
        if (isServer) {
        	ExchangeServer server = serverMap.get(key);
        	if (server == null) {
        		serverMap.put(key, createServer(url));
        	} else {
        		server.reset(url);
        	}
        }
    }
我们可以发现代码量很少，逻辑十分清晰，说明如下:

1. 获得url（元信息）中的地址信息（host+port），作为服务缓存的key值
2. 获得url（元信息）中的键为isserver的信息，默认为true。只有true的情况下，才会处理服务对象
	- tip:client端也可以设置为true，用来暴露一个只有server端可以调用的服务
3. 根据key来操作缓存，无则新建，有则根据url元信息来决定是否调整

我们主要需要关注的是**无则新建**的过程，也就是 **createServer**涉及到的操作。

### createServer(url)
---
现在我们就来详细对这个方法的操作进行说明，循循渐进的说明整个网络服务导出的操作。该方法代码示意如下：

    private ExchangeServer createServer(URL url) {
        url = url.addParameterIfAbsent(Constants.CHANNEL_READONLYEVENT_SENT_KEY, Boolean.TRUE.toString());
        url = url.addParameterIfAbsent(Constants.HEARTBEAT_KEY, String.valueOf(Constants.DEFAULT_HEARTBEAT));

        String str = url.getParameter(Constants.SERVER_KEY, Constants.DEFAULT_REMOTING_SERVER);
        if (str != null && str.length() > 0 && ! ExtensionLoader.getExtensionLoader(Transporter.class).hasExtension(str))
            throw new RpcException("Unsupported server type: " + str + ", url: " + url);

		//我们不使用dubbo1
        //url = url.addParameter(Constants.CODEC_KEY, Version.isCompatibleVersion() ? COMPATIBLE_CODEC_NAME : DubboCodec.NAME);
        url = url.addParameter(Constants.CODEC_KEY, DubboCodec.NAME);

        ExchangeServer server;
        try {
            server = Exchangers.bind(url, requestHandler);
        } catch (RemotingException e) {
            throw new RpcException("Fail to start server(url: " + url + ") " + e.getMessage(), e);
        }

        str = url.getParameter(Constants.CLIENT_KEY);
        if (str != null && str.length() > 0) {
            Set<String> supportedTypes = ExtensionLoader.getExtensionLoader(Transporter.class).getSupportedExtensions();
            if (!supportedTypes.contains(str)) {
                throw new RpcException("Unsupported client type: " + str);
            }
        }
        return server;
    }

对于我们上面贴出来的代码，我们整理下逻辑：

1. 改造元信息（url），添加readonly事件的信息
	- key为channel.readonly.sent，value为true
	- 元信息已经有相关信息就不处理
	
2. 改造元信息（url），添加hearbeat心跳的信息
	- key为heartbeat，value为6s
	- 元信息已经有相关信息就不处理
	
3. 从元信息中通过相应的键值对获得准备使用网络框架
	- key为server，value的默认值是netty

4. 改造元信息（url），添加准备使用解码编码器信息
	- key为codec，value需要处理
		- value根据是否有某宝某包的ConnectionRequest.class该类，有则是dubbo1compatible，无则是dubbo
		- 目测这样的处理是为了兼容dubbo1，但是我们已经不需要了，所以直接删掉它
	
5. **绑定url和requestHandler实例来获得Server**
	- requestHandler是内部类ExchangeHandlerAdapter实例
	
6. 检验元信息（url）中的键值对
	- key为client，代表了客户端协议实现，默认情况下也是netty
	- 该键对应的值，必须框架中支持

这样整个方法的说明基本就明确了，我们给其中重要的的情况说明加粗，意味着这里还需要我们继续深入，也就是上面第5点。以下就是第5点的代码示意了

    public static ExchangeServer bind(URL url, ExchangeHandler handler) throws RemotingException {
 		//...忽略部分代码
		url = url.addParameterIfAbsent(Constants.CODEC_KEY, "exchange");
        //使用HeaderExchanger绑定
        return getExchanger(url).bind(url, handler);
    }

我们继续说明逻辑:

1. 首先是忽略的代码部分，这里做的主要是对入参的判空校验
2. 改造元信息（url），添加准备使用解码编码器信息
	- key为codec，value为exchange
	- 元信息已经有相关信息就不处理。也就是说再dubbo默认协议下，我们不需要处理，因为上面已经添加过了，默认的dubbo协议使用键值对（codec：dubbo）来标识相应的解码编码器

3. 最后使用实际的 HeaderExchanger 来完成对url和handler的绑定，从而导出具体的网络服务实现
	-网络服务实现是指ExchangeServer的事项类

这样又一个深入方法bind我们做出了说明，但是服务实现还没有出现，我们还需要继续深入。

   	public ExchangeServer bind(URL url, ExchangeHandler handler) throws RemotingException {
        return new HeaderExchangeServer(Transporters.bind(url, new DecodeHandler(new HeaderExchangeHandler(handler))));
    }
该类就是我们上面第三点说HeaderExchanger类的绑定代码，短短一行，做的事情却是不少。

1. 将入参handler实例包装成HeaderExchangeHandler实例
	- 对于dubbo协议（dubboProtcol来说）入参是其内部类:ExchangeHandlerAdapter
	 

2. 将HeaderExchangeHandler实例继续包装为DecodeHandler实例

3. **使用Transporters.bind来对url和Handler类绑定获得server**
	- 该server也就是根元信息来确定，代表了确定的网络开发框架，默认是netty

4. 将server使用HeaderExchangeServer包装后返回


一层嵌一层，代码越嵌越深，思路越来越混乱。这里显然我们看到了需要深入，但是在深入之前，我们，简单先从这里回归上文。也就是给童鞋出了一个问题

##### question：上面openServer中无则新建到这里回归，大概也就是返回了一个Server的实现（HeaderExchangeServer），网络编程涉及到的套路都还没出来呢，套路代码何时出来？

##### answer： 实际上就是在新建Server实现中，实现网络编程的套路，完成端口绑定，设定相应的处理逻辑等巴拉巴拉一坨。

##### so：我们只能继续看里面的代码了

---

### Transporters.bind获得起始的服务

    public static Server bind(URL url, ChannelHandler... handlers) throws RemotingException {
        //...忽略部分代码
        ChannelHandler handler;
        if (handlers.length == 1) {
            handler = handlers[0];
        } else {
            handler = new ChannelHandlerDispatcher(handlers);
        }
        //Transporter$Adpative导出，默认总是netty
        return getTransporter().bind(url, handler);
    }
我们继续说明逻辑:

1. 首先是忽略的代码部分，这里做的主要是对入参的判空校验
2. 对入参handler进一步处理
	- 入参为单个handler传递，直接使用(我们需要关注的)
	- 入参为多个handler传递，ChannelHandlerDispatcher包装这些handler
4. 使用Transporter$Adpative根据元信息获得相应网络框架传输实现类XXXTransporter，进行导出Server
	- 默认XXXTransporter是NettyTransporter

到了这里，接下来就是特定dubbo针对特定网络开发框架的处理了。默认是netty，我们也就对netty进行说明。

### netty的暴露
---

上面说到最终使用NettyTransporter进行暴露网络服务，代码如下。

    public Server bind(URL url, ChannelHandler listener) throws RemotingException {
        return new NettyServer(url, listener);
    }

只有一句简简单单的代码，上面question和answer设计到东西依旧没有看到，因此我们继续深入。

tip：**这个具体的Server返回后会被HeaderExchangeServer**包装

    public NettyServer(URL url, ChannelHandler handler) throws RemotingException {
        super(url, ChannelHandlers.wrap(handler, ExecutorUtil.setThreadName(url, SERVER_THREAD_POOL_NAME)));
    }
又是简简单单的一行代码，还是使用关键词supper。但我们也看到了对入参handler的又一次处理。

1. 包装入参handler
	1. 改造url元信息，增加键值对
		- key为threadname，value为原值-url地址，原值没有默认为DubboServerHandler-url地址
	2. 使用Dispatcher$Adaptive根元信息处理入参handler
		1. 默认使用AllDispatcher来包装入参handler为AllChannelHandler实例
	3. 将上述2点处理后的handler，包装成HeartbeatHandler实例
	4. 将HeartbeatHandler实例，包装成MultiMessageHandler实例
2. super处理

### super处理
---
nettyServer是dubbo针对netty的具体实现。于此同时，其继承树上的各式父类，接口都是dubbo针对各类网络框架的抽象。

具体实现类nettyServer可以使用父类暴露的各种模板方法，完成特定网络框架的处理。比如监听代码的编写。

具体的代码嵌套层次较深，我们这里不采用粘贴的方式，而是直接叙述。

### 抽象的网络处理逻辑

1. 对入参url和handler进行判空检查

2. 完成入参url元信息到对象属性url的引用

3. 完成入参handler信息对象属性handler引用
	- tip:该网络抽象类，也实现dubbo的抽象ChannelHandler，意味着这也是个handler

4. 从元信息获得解码编码器信息完成对象属性codec的设置，
	1. 对netty来说，默认是dubbocodec
	
5. 从url中获得timeout信息
	- key:timeout,value默认值为1s

6. 从url中获得connectTimeout信息
	- key:connect.timeout,value默认值为1s

7. 从url中获得localAddress信息
	- InetSocketAddress实例
	
8. 完成bindAddress信息的设置，元信息来自url
	- InetSocketAddress实例

9. 完成accpets的设置，元信息来自url
	- key为accepts，value默认为0

10. 完成idleTimeout的设置，元信息来自url
	- key为idle.timeout，value为60s
	
11. 完成executor的设置，如果handler实现了WrappedChannelHandler
	- 对netty来说请忽略

这样之后，会回调子类nettyServer的doOpen实现，完成网络服务开启


### nettyServer之doOpen
---
上面我们所说，dubbo框架为各种网络框架的适配暴露了模板方法，这里服务开启，就是其中的一个模板方法
我们接着来看dubbo的netty服务开启实现。

    protected void doOpen() throws Throwable {

        //设定factory
        NettyHelper.setNettyLoggerFactory();

        //设定boss
        ExecutorService boss = Executors.newCachedThreadPool(new NamedThreadFactory("NettyServerBoss", true));

        //设定worker
        ExecutorService worker = Executors.newCachedThreadPool(new NamedThreadFactory("NettyServerWorker", true));

        //设定channelFacotory
        ChannelFactory channelFactory = new NioServerSocketChannelFactory(boss, worker, getUrl().getPositiveParameter(Constants.IO_THREADS_KEY, Constants.DEFAULT_IO_THREADS));

        //设定开启类
        bootstrap = new ServerBootstrap(channelFactory);

        //nettyHandler
        final NettyHandler nettyHandler = new NettyHandler(getUrl(), this);

        channels = nettyHandler.getChannels();

        // https://issues.jboss.org/browse/NETTY-365
        // https://issues.jboss.org/browse/NETTY-379
        // final Timer timer = new HashedWheelTimer(new NamedThreadFactory("NettyIdleTimer", true));
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            public ChannelPipeline getPipeline() {

                //netty编码适配器
                NettyCodecAdapter adapter = new NettyCodecAdapter(getCodec(), getUrl(), NettyServer.this);

                //获得pipeline
                ChannelPipeline pipeline = Channels.pipeline();
                /*int idleTimeout = getIdleTimeout();
                if (idleTimeout > 10000) {
                    pipeline.addLast("timer", new IdleStateHandler(timer, idleTimeout / 1000, 0, 0));
                }*/
                pipeline.addLast("decoder", adapter.getDecoder());
                pipeline.addLast("encoder", adapter.getEncoder());
                pipeline.addLast("handler", nettyHandler);
                return pipeline;
            }
        });
        // bind
        //绑定地址
        channel = bootstrap.bind(getBindAddress());
    }

上面是网络服务开启的代码了，很符合普通的netty网络编程套路代码。但是这里需要的注意点还是蛮多的。

- 注意点：nettyHandler
	1. nettyHandler拥有url和nettyServer本身
	2. nettyServer本身实现了dubbo对Handler的抽象

- 注意点：nettyCodecAdapter
	1. NettyCodecAdapter适配了codec编码器，并携带了两个内部编码解码器handler
	2. nettyHandler包装了this（本身server和url，意味着拥有所有内部信息，比如上面所说的层层包装的handler）

叙述到这里，整个网络暴露就结束了。接下来就是网络交互了。


