## dubbo网络暴露

  对于一款rpc框架，网络通信这一块必不可少，也就是相关的网络编程。而对于网络编程，重要的因素包括:通讯协议，序列化方式和网络开发框架。这些因素对rpc框架的性能指标影响重大。

  dubbo的网络开发框架支持多种形式，比如netty(3.x)，mina等等,采用了自定义的通讯协议(dubbo协议包)，默认使用hession作为序列化方式。  

  同时dubbo本身对网络编程中相关概念例如channel，transport做了抽象处理，方便接入不同的网络框架，解除对某一特定网络框架的依赖。

### openServer(url)
----------
上一篇文章中，我们最后在这个DubboProtocol看到了这行代码。这就是网络服务的入口，在本篇中，我们将会详细探讨研究网络服务的开启。

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
以上是方法你内部实现，代码很少，逻辑也很简单，说明如下:

1. 获得url（元信息）中的地址信息（host+port），作为服务缓存的key值
2. 获得url（元信息）中的键为isserver的信息，默认为true。只有true的情况下，才会处理服务对象
	- tip:client端也可以设置为true，用来暴露一个只有server端可以调用的服务
3. 根据key来操作缓存，无则新建，有则根据url元信息来决定是否调整

我们主要需要关注的是**新建缓存对象**的过程。

### createServer(url)
---
这个方法就是新建缓存对象的过程了，我们将对方法的操作进行说明，然后循循渐进的说明整个网络服务导出的逻辑，方法源码入下：

    private ExchangeServer createServer(URL url) {
        url = url.addParameterIfAbsent(Constants.CHANNEL_READONLYEVENT_SENT_KEY, Boolean.TRUE.toString());
        
		url = url.addParameterIfAbsent(Constants.HEARTBEAT_KEY, String.valueOf(Constants.DEFAULT_HEARTBEAT));

        String str = url.getParameter(Constants.SERVER_KEY, Constants.DEFAULT_REMOTING_SERVER);
        if (str != null && str.length() > 0 && ! ExtensionLoader.getExtensionLoader(Transporter.class).hasExtension(str))
            throw new RpcException("Unsupported server type: " + str + ", url: " + url);

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

对于看过前几篇文章的童鞋，这些代码应该是没用任何问题的，我们简单的梳理下逻辑：

1. 改造元信息（url），添加readonly事件的信息(如果url中有配置就忽略)
	- (channel.readonly.sent：value)
2. 改造元信息（url），添加hearbeat心跳的信息(如果url中有配置就忽略)
	- (heartbeat:60000)
3. 获得url（元信息）中的键，来确定使用特定的Transporter(网络概念)(如果url中有配置就忽略)
	- k:server，value的默认值是netty
4. 改造元信息（url），添加准备使用解码编码器信息
	- key为codec，value需要处理
		- value根据是否有某宝某包的ConnectionRequest.class该类，有则是dubbo1compatible，无则是dubbo
		- 目测这样的处理是为了兼容dubbo1，但是我们已经不需要了，所以直接删掉它
	- 目前是(codec:dubbo)
5. **绑定url和requestHandler实例来获得Server**
	- requestHandler是内部类ExchangeHandlerAdapter实例
6. 检验元信息（url）中的键值对
	- key为client，代表了客户端协议实现，默认情况下也是netty
	- 该键对应的值，必须框架中支持

整个逻辑基本是这样的，我们关注我们需要重点关注的地方，也就是上面加粗部分:第5点。以下就是第5点的源码示意:

    public static ExchangeServer bind(URL url, ExchangeHandler handler) throws RemotingException {
 		//...入参校验
		url = url.addParameterIfAbsent(Constants.CODEC_KEY, "exchange");
        //使用HeaderExchanger绑定
        return getExchanger(url).bind(url, handler);
    }

我们继续说明逻辑:

1. 改造元信息（url），添加准备使用解码编码器信息，有则不处理
	- (codec:exchange)
	- 在dubbo默认协议下，默认的dubbo协议使用键值对（codec：dubbo）来标识相应的解码编码器

2. 最后使用实际的HeaderExchanger(开发者一般不会扩展)来完成对url和handler的绑定，从而导出具体的网络服务

逻辑说明完成，但是还是没见到服务开启的影子，只能继续:

   	public ExchangeServer bind(URL url, ExchangeHandler handler) throws RemotingException {
        return new HeaderExchangeServer(Transporters.bind(url, new DecodeHandler(new HeaderExchangeHandler(handler))));
    }
该方法源码就是上面第2点说HeaderExchanger类的绑定代码，短短一行，做的事情却是不少。

1. 将入参handler实例包装成HeaderExchangeHandler实例
	- 对于dubbo协议（dubboProtcol来说）入参是其内部类:ExchangeHandlerAdapter
	 
2. 将HeaderExchangeHandler实例继续包装为DecodeHandler实例

3. **使用Transporters.bind来对url和Handler类绑定获得server**
	- 该server也就是根元信息来确定，代表了确定的网络开发框架，默认是netty

4. 将server使用HeaderExchangeServer包装后返回.


一层嵌一层，代码越嵌越深，思路越来越混乱。这里显然我们看到了需要深入多层处理，但是在深入之前，我们，简单先从这里回归上文。也就是给童鞋出了一个问题

##### question：上面openServer中无则新建到这里进行回归，也就是返回了一个Server的实现（HeaderExchangeServer），网络编程涉及到的套路都还没出来呢，套路代码何时出来？

##### answer： 实际上就是在新建Server实现中，实现网络编程的套路，完成端口绑定，设定相应的处理逻辑等巴拉巴拉一坨。

##### so：我们只能继续看里面的代码了

---

### Transporters.bind获得起始的服务

----------
关键的server是从这里展出来的，这是我们最主要的关注的地方。

    public static Server bind(URL url, ChannelHandler... handlers) throws RemotingException {
        //...入参校验
        ChannelHandler handler;
        if (handlers.length == 1) {
            handler = handlers[0];
        } else {
            handler = new ChannelHandlerDispatcher(handlers);
        }
        //Transporter$Adpative导出，默认总是netty
        return getTransporter().bind(url, handler);
    }
以上是方法实现代码，基本上没什么逻辑:

1. 对入参handler进一步处理，单个则直接使用，多个则包装为ChannelHandlerDispatcher
2. 根据元信息获得相应网络框架传输实现类XXXTransporter，进行导出Server，默认是netty

逻辑到了这里，就是特定网络开发框架的处理了。默认是netty，我们也就对netty进行说明。

### netty的暴露

----------

上面说到最终使用NettyTransporter进行暴露网络服务，代码如下

    public Server bind(URL url, ChannelHandler listener) throws RemotingException {
        return new NettyServer(url, listener);
    }

简简单单一行代码，上面question和answer设计到东西依旧没有看到，因此我们继续深入。

tip：别忘记**这个Server返回后会被HeaderExchangeServer**包装

### dubbo抽象的实现nettyServer

----------
dubbo框架对各种网络框架做了一层抽象，这里nettyServer就是实现了dubbo抽象的Server。

    public NettyServer(URL url, ChannelHandler handler) throws RemotingException {
        super(url, ChannelHandlers.wrap(handler, ExecutorUtil.setThreadName(url, SERVER_THREAD_POOL_NAME)));
    }
又是简简单单的一行代码，还是使用关键词supper。但我们也看到了对入参handler的又一次处理。

1. 包装入参handler
	1. 改造url元信息，增加或补充key为threadname，value增加了特定地址
	2. 使用Dispatcher$Adaptive根元信息处理入参handler
		1. 默认使用AllDispatcher来包装入参handler为AllChannelHandler实例
	3. 将上述2点处理后的handler，包装成HeartbeatHandler实例
	4. 将HeartbeatHandler实例，包装成MultiMessageHandler实例
2. super处理

### super处理
---
nettyServer是dubbo针对netty的具体实现。于此同时，其继承抽象树上各种父类，接口(dubbo针对各类网络框架的抽象）

nettyServer可以使用dubbo统一封装的网络框架无关的抽象父类(not 抽象类)暴露的各种模板方法，完成特定网络框架的处理。比如监听代码的编写。

我们看一下该类的继承树
	nettyServer--->AbstractServer--->AbstractEndpoint--->AbstractPeer

### 网络的抽象结构

----------

上面AbstractPeer是最顶上的类（除去接口)。我们先看其类声明结构

	public abstract class AbstractPeer implements Endpoint, ChannelHandler

可以看到的是它实现了两个接口:Endpoint,ChannelHandler。这里我们将对这些接口展开，并适当讨论其设计思想，首先是ChannelHandler

### ChannelHandler


----------
首先上的是ChannelHandler，为什么不是Endpoint，因为后者依赖前面，为了阅读的简便性，我们先来探讨这个
，接口声明如下:
	
	@SPI
	public interface ChannelHandler {
	
	    void connected(Channel channel) throws RemotingException;
	
	    void disconnected(Channel channel) throws RemotingException;
	
	    void sent(Channel channel, Object message) throws RemotingException;
	
	    void received(Channel channel, Object message) throws RemotingException;
	
	    void caught(Channel channel, Throwable exception) throws RemotingException;
	
	}

我们首先可以看到的是@SPI注解,这可是内部注解，自然有很大的用处，然而我们关注的重点现在不在这。对于channel，本文将他译做通道，和网络流行的概念一样，通道是用来描述两个节点之间的桥梁，这种概念在各种实际的网络框架和网络原理中广泛涉及。ChannerlHandler，对于Channel的处理，一个channel按道理可以被若干个handler流式处理。

我们可以观察到上面的方法，连接，断开，发送，接收，抓住(异常处理)，包含了网络双方的所有动作。

### Endpoint

----------
Endpoint是我们涉及的另一个概念，首先还是粘贴接口声明。

	public interface Endpoint {

	    URL getUrl();
	
	    ChannelHandler getChannelHandler();
	
	    InetSocketAddress getLocalAddress();
	     
	    void send(Object message) throws RemotingException;
	
	    void send(Object message, boolean sent) throws RemotingException;
	
	    void close(int timeout);
	   
	    boolean isClosed();
	
	}
Endpoint，我们这里翻译成终端。代表了网络中各个节点，对于dubbo程序来说，无论是消费者还是服务方都是一个终端。对于这里的设计我们可以从接口看出，

1. url代表了终端持有的地址信息
2. channelHandler代表终端持有了对连接终端之间channel的处理器
3. InetSocketAddress代表终端持有本终端的socket的地址信息。
4. send能说明终端能进行发送实体消息
5. close的能使终端关闭，以及关闭的状态

不知道说完这些作者是否有些基本的概念，我们这里暂时仅涉及基本的东西。现在回过头来看
**AbstractPeer**

### AbstractPeer的理解

----------
我们上面说到AbstractPeer实现了channelHandler和endPoint两个接口。

peer是一个概念，更常见的是peer to peer 也就是俗称的点对点，因此peer可以说是一个抽象的终端。我们再来看一下其字段声明

    private final ChannelHandler handler;


    private volatile URL         url;


    private volatile boolean     closed;

基本上就是终端的实现。 我们在来看一下其构造函数：

	public AbstractPeer(URL url, ChannelHandler handler) {
        /**省略入参校验**/
        this.url = url;
        this.handler = handler;
    }
我们看到基本上是对应属性的赋值。现在回溯过程，现在继续探讨的就是AbstractEndpoint。

### AbstractEndpoint的理解

----------
上面我们说了AbstractPeer只是抽象的终端，那么这个AbstractEndpoint就是更近一步的稍微具体化的终端了。
对于AbstractPeer，其只拥有核心属性url和ChannelHandler。而AbstractEndpoint则更加具体，

	private Codec2                codec;

    private int                   timeout;

    private int                   connectTimeout;

从代码字段看出来，其拥有更加具体的属性，比如超时时间，以及编码解码器。我们在来关注下其构造函数

	public AbstractEndpoint(URL url, ChannelHandler handler) {
        super(url, handler);
        this.codec = getChannelCodec(url);
        this.timeout = url.getPositiveParameter(Constants.TIMEOUT_KEY, Constants.DEFAULT_TIMEOUT);
        this.connectTimeout = url.getPositiveParameter(Constants.CONNECT_TIMEOUT_KEY, Constants.DEFAULT_CONNECT_TIMEOUT);
    }

代码可以看出，除了调用父类的构造函数，也就是AbstractPeer，并且还完成了自身属性的设置，而这些属性信息正是来自核心url（元信息）。
该类实现了Resetable，代表这终端可以重置。继续回溯，终端这个概念需要更加的具体化，因而AbstractEndpoint直接子类分为两种。

1. AbstractClient
2. AbstractServer

在网络终端中，一些节点充当了服务端的概念，而另一些节点充当了消费方的概念。因而dubbo也针对其进行了实现，这里我们仅探讨服务方的概念

### AbstractServer的理解

----------
上面我们说到，这是终端的更加具体化，充当服务方的节点。因而我们在其类声明上又看到了不同的特点：

	public abstract class AbstractServer extends AbstractEndpoint implements Server

AbstractServer不仅是一个具体化的终端，更加是一个服务方，因而实现了Server接口，总之他离具体的服务节点只有一步之遥。

	private InetSocketAddress              localAddress;

    private InetSocketAddress              bindAddress;

    private int                            accepts;

    private int                            idleTimeout = 600; //600 seconds
    
    protected static final String SERVER_THREAD_POOL_NAME  ="DubboServerHandler";
    
    ExecutorService executor;

更加具体，即应该持有跟多的身份特征，AbstractServer亦是如此

1. localAddress代表了本地地址
2. bindAddress代表了监听端口的地址
3. accepts代表了最大channel数
4. idleTimeout代表了空闲超时时间
5. executor代表了管理线程的服务

继续来看其的构造函数

	public AbstractServer(URL url, ChannelHandler handler) throws RemotingException {
        super(url, handler);
        localAddress = getUrl().toInetSocketAddress();
        String host = url.getParameter(Constants.ANYHOST_KEY, false) 
                        || NetUtils.isInvalidLocalHost(getUrl().getHost()) 
                        ? NetUtils.ANYHOST : getUrl().getHost();
        bindAddress = new InetSocketAddress(host, getUrl().getPort());
        this.accepts = url.getParameter(Constants.ACCEPTS_KEY, Constants.DEFAULT_ACCEPTS);
        this.idleTimeout = url.getParameter(Constants.IDLE_TIMEOUT_KEY, Constants.DEFAULT_IDLE_TIMEOUT);
        try {
            doOpen();
            if (logger.isInfoEnabled()) {
                logger.info("Start " + getClass().getSimpleName() + " bind " + getBindAddress() + ", export " + getLocalAddress());
            }
        } catch (Throwable t) {
            throw new RemotingException(url.toInetSocketAddress(), null, "Failed to bind " + getClass().getSimpleName() 
                                        + " on " + getLocalAddress() + ", cause: " + t.getMessage(), t);
        }
        if (handler instanceof WrappedChannelHandler ){
            executor = ((WrappedChannelHandler)handler).getExecutor();
        }
    }
总体上和之前是一模一样的，唯一的区别是doOpen。我们发现，这个方法需要子类自己实现。我们现在来继续回溯。翻阅源码后，我们发现了
该抽象类有三个实现类:

1. GrizzlyServer
2. MinaServer
3. NettyServer

山重水复疑无路，柳暗花明又一村。最终我们看到了很熟悉的东西，具体化终端和各种框架相关。dubbo支持Grizzly、mina、netty等网络开发框架，除了netty本人不是很熟悉，自然不在我们讨论范围之内，何况netty是其中最好的一个。

### NettyServer的理解

----------
和前面一样，设计层次上越具体了，需要持有的属性也就更加多了。现在针对的是netty，自然需要和netty进行耦合。

	public NettyServer(URL url, ChannelHandler handler) throws RemotingException {
        super(url, ChannelHandlers.wrap(handler, ExecutorUtil.setThreadName(url, SERVER_THREAD_POOL_NAME)));
    }
我们再次回到最开始的地方，该构造函数代码，文章前面已经贴出，现在贴出我们将对此展开，我们发现，该构造函数除了对用父类构造函数，没有做其他的事情。

### doOpen回调开启服务

----------
我们介绍了这个东西，正是这里回调了子类的实现，开启了服务。我们关注下NettyServer中的这个方法。

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

上面就是网络服务开启的代码了，很符合普通的netty网络编程套路代码。但是这里需要的注意点还是蛮多的。

- 注意点：nettyHandler
	1. nettyHandler拥有url和nettyServer本身
	2. nettyServer本身实现了dubbo对Handler的抽象

- 注意点：nettyCodecAdapter
	1. NettyCodecAdapter适配了codec编码器，并携带了两个内部编码解码器handler
	2. nettyHandler包装了this（本身server和url，意味着拥有所有内部信息，比如上面所说的层层包装的handler）

本文叙述到这里，就暂时告一段落了，如果顺利的话网络也就因此开启了。然而网络开发，网络开启只是最为简单的一步骤，网络交互，
网络处理等等还有很细节的地方，将会在本系列后面的展开。

### 补充

----------

我们上面说了很多设计上的问题，也有漏掉的地方，比如Channel

### channel的设计

----------
我们之前介绍了channelHandler，也提到过channel，但没有深究。这里作为补充:

	public interface Channel extends Endpoint {

	    InetSocketAddress getRemoteAddress();
	
	    boolean isConnected();
	
	    boolean hasAttribute(String key);
	
	    Object getAttribute(String key);
	
	    void setAttribute(String key,Object value);
	    
	    void removeAttribute(String key);
	
	}

Channel作为两个终端直接的桥梁，本质上也算个终端，因而其继承接口Endpoint，我们不难想象。而其本身，持有了

1. 远程的地址
2. 是否连接的标志
3. channel携带的信息
4. channel上设置信息
5. channel删除信息

### channelHandler的包装

----------
我们在上面分析的时候，细心的读者这可能注意到channelHandler包装了很多层。在最早的时候，我们发现DubboProtocol内嵌了一个匿名类（实现该handler接口）

	private ExchangeHandler requestHandler = new ExchangeHandlerAdapter() {
     	/**省略代码**/
    };

我们不关心其实现怎么样，我们得出的结论下如果使用默认的DubboProtocol协议，这个就是最内部的channelHandler。然后他将经过一系列处理进行包装

1. HeaderExchangeHandler包装
2. DecodeHandler包装，
3. Transporters.bind处理
	1. 多个handler会被封装为ChannelHandlerDispatcher
	2. 单个维持原样
4. ChannelHandlers.wrap处理
	1. 
		    public static ChannelHandler wrap(ChannelHandler handler, URL url){
		        return ChannelHandlers.getInstance().wrapInternal(handler, url);
		    }
		
			protected ChannelHandler wrapInternal(ChannelHandler handler, URL url) {
		        return new MultiMessageHandler(new HeartbeatHandler(ExtensionLoader.getExtensionLoader(Dispatcher.class)
		                                        .getAdaptiveExtension().dispatch(handler, url)));
		    }
	1. AllChannelHandler包装下
		-  Dispatcher的扩展加载类实现对handler操作，默认情况下是AllDispatcher对应AllChannelHandler
	2. HeartbeatHandler包装下
	3. MultiMessageHandler包装下
5. NettyServer包装下
6. NettyHandler包装下

上面对channelHandler包装很多层，实际操作十分复杂。我们将在其他文章中展开。



