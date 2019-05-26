## 完整流程 ##

之前的文章，总是单独的进行分析。读者可能没有整个范围上的理解，本篇将介绍一个完整的交互。

### 前提 ###

----------
使用dubbo协议，使用默认序列化协议hession，使用zookeeper注册中心
最重要的一点，服务方和消费方建了相应的长连接，其他配置都是默认的。

假设接口如下:

	package com.codeL.service

	interface AccountService {
	
		void delelteAccount(Account account)
	
	}

首先客户端持有的是的类是

	class com.codeL.service.proxy0 implements AccountService,EchoService{
		
		//这一项是伪码
		//delelteAccount来代表该方法
		public static java.lang.reflect.Method[] methods = new Method[]{delelteAccount,$echo};

		private InvocationHandler handler;

		public proxy0（InvocationHandler handler）{
			this.handler = handler;
		}

		public void delelteAccount(Account account){
			Object[] args = new Object[1];
			args[0] = account;
			Object ret = handler.invoke(this, methods[0], args);
		}
	
 		public Object $echo(Object message){
			Object[] args = new Object[1];
			args[0] = account;
			Object ret = handler.invoke(this, methods[1], args);
			return（Object）ret；
		}
	}

当客户端调用方法delelteAccount时，发生了很多步骤。

1. 调用proxy0.delelteAccount
2. 调用handler.invoke（handler实际类型为InvokerInvocationHandler）

		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
	        String methodName = method.getName();
	        Class<?>[] parameterTypes = method.getParameterTypes();
	        if (method.getDeclaringClass() == Object.class) {
	            return method.invoke(invoker, args);
	        }
	        if ("toString".equals(methodName) && parameterTypes.length == 0) {
	            return invoker.toString();
	        }
	        if ("hashCode".equals(methodName) && parameterTypes.length == 0) {
	            return invoker.hashCode();
	        }
	        if ("equals".equals(methodName) && parameterTypes.length == 1) {
	            return invoker.equals(args[0]);
	        }
	        return invoker.invoke(new RpcInvocation(method, args)).recreate();
	    }
代码很简单本质上是invoker调用，然后返回Result中的内容。

首先我们需要关注下**new RpcInvocation(method, args)**。 这个叫RpcInvocation是rpc调用对象，Invoker作为调用者对调用对象进行处理。

最后开始我们的invoker调用之旅了，默认情况下，我们配置的是注册配置类，则不使用Cluster进行管理请求。所以



4. 不使用cluster的invoker，也就是默认先是RegistryProtocol里面得到的invoker


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
	        directory.subscribe(subscribeUrl.addParameter(Constants.CATEGORY_KEY, Constants.PROVIDERS_CATEGORY
	                + "," + Constants.CONFIGURATORS_CATEGORY
	                + "," + Constants.ROUTERS_CATEGORY));
	        return cluster.join(directory);
	    }
也就是默认的
		
    public <T> Invoker<T> join(Directory<T> directory) throws RpcException {
        return new FailoverClusterInvoker<T>(directory);
    }

3. 默认情况下FailoverClusterInvoker合并调用，如同我们之前所说的，这些ClusterInvoker的实现类并没有实现invoker方法，而是使用父类的方法然后回调本身实现

	public Result invoke(final Invocation invocation) throws RpcException {

        checkWhetherDestroyed();

        LoadBalance loadbalance;
        
        List<Invoker<T>> invokers = list(invocation);
        if (invokers != null && invokers.size() > 0) {
            loadbalance = ExtensionLoader.getExtensionLoader(LoadBalance.class).getExtension(invokers.get(0).getUrl()
                    .getMethodParameter(invocation.getMethodName(),Constants.LOADBALANCE_KEY, Constants.DEFAULT_LOADBALANCE));
        } else {
            loadbalance = ExtensionLoader.getExtensionLoader(LoadBalance.class).getExtension(Constants.DEFAULT_LOADBALANCE);
        }
        RpcUtils.attachInvocationIdIfAsync(getUrl(), invocation);
        return doInvoke(invocation, invokers, loadbalance);
    }

代表不是很多，逻辑也不是很复杂，

1. 检查了是否已经销毁
2. 选择合适的负载均衡策略
3. 添加额外的信息
4. 回调子类的实现


我们重点来看回调子类的实现，也就是默认的FailoverClusterInvoker    

	public Result doInvoke(Invocation invocation, final List<Invoker<T>> invokers, LoadBalance loadbalance) throws RpcException {

        List<Invoker<T>> copyinvokers = invokers;
    	checkInvokers(copyinvokers, invocation);
        int len = getUrl().getMethodParameter(invocation.getMethodName(), Constants.RETRIES_KEY, Constants.DEFAULT_RETRIES) + 1;
        if (len <= 0) {
            len = 1;
        }
        // retry loop.
        RpcException le = null; // last exception.
        List<Invoker<T>> invoked = new ArrayList<Invoker<T>>(copyinvokers.size()); // invoked invokers.
        Set<String> providers = new HashSet<String>(len);
        for (int i = 0; i < len; i++) {
        	if (i > 0) {
        		checkWhetherDestroyed();
        		copyinvokers = list(invocation);
        		checkInvokers(copyinvokers, invocation);
        	}
            Invoker<T> invoker = select(loadbalance, invocation, copyinvokers, invoked);
            invoked.add(invoker);
            RpcContext.getContext().setInvokers((List)invoked);
            try {
                return invoker.invoke(invocation);
            } catch (RpcException e) {
                if (e.isBiz()) { // biz exception.
                    throw e;
                }
                le = e;
            } catch (Throwable e) {
                le = new RpcException(e.getMessage(), e);
            } finally {
                providers.add(invoker.getUrl().getAddress());
            }
        }
    }

进行一系列相关的选择后，使用负载均衡后，自然使用最为接近的invoker，也就是默认的DubboInvoker
当然这里我们省略了集群操作，过滤器操作，和监听器操作，我们先关注核心，最为简单的过程，最后回过头来串起来整个过程。

### DubboInvoker ###
---

dubboInvoker自己本身并没有实现相应的invoke方法，和其他很相似，由父类实现，子类进行回调

    public Result invoke(Invocation inv) throws RpcException {
        if (destroyed) {
			//省略了相关代码
        }
        RpcInvocation invocation = (RpcInvocation) inv;
        invocation.setInvoker(this);
        if (attachment != null && attachment.size() > 0) {
            invocation.addAttachmentsIfAbsent(attachment);
        }
        Map<String, String> context = RpcContext.getContext().getAttachments();
        if (context != null) {
            invocation.addAttachmentsIfAbsent(context);
        }
        if (getUrl().getMethodParameter(invocation.getMethodName(), Constants.ASYNC_KEY, false)) {
            invocation.setAttachment(Constants.ASYNC_KEY, Boolean.TRUE.toString());
        }
        RpcUtils.attachInvocationIdIfAsync(getUrl(), invocation);
		return doInvoke(invocation);
    }

上面就是父类也就是AbstractInvoker中实现的invoke方法，上面我们删除了部分代码，我们主要关注我们重点关心的对象，也就是子类的回调doInvoker

	protected Result doInvoke(final Invocation invocation) throws Throwable {
		RpcInvocation inv = (RpcInvocation) invocation;
		final String methodName = RpcUtils.getMethodName(invocation);
		inv.setAttachment(Constants.PATH_KEY, getUrl().getPath());
		inv.setAttachment(Constants.VERSION_KEY, version);

		ExchangeClient currentClient;
		if (clients.length == 1) {
			currentClient = clients[0];
		} else {
			currentClient = clients[index.getAndIncrement() % clients.length];
		}
		boolean isAsync = RpcUtils.isAsync(getUrl(), invocation);
		boolean isOneway = RpcUtils.isOneway(getUrl(), invocation);
		int timeout = getUrl().getMethodParameter(methodName, Constants.TIMEOUT_KEY, Constants.DEFAULT_TIMEOUT);
		if (isOneway) {
			boolean isSent = getUrl().getMethodParameter(methodName, Constants.SENT_KEY, false);
			currentClient.send(inv, isSent);
			RpcContext.getContext().setFuture(null);
			return new RpcResult();
		} else if (isAsync) {
			ResponseFuture future = currentClient.request(inv, timeout);
			RpcContext.getContext().setFuture(new FutureAdapter<Object>(future));
			return new RpcResult();
		} else {
			RpcContext.getContext().setFuture(null);
			return (Result) currentClient.request(inv, timeout).get();
		}
	}
以上就是dubboInvoker进行回调的方法，我们可以看到，内部还是做了不少事情，我们简单的来分析下，这里省略的try，catch的异常代码处理

1. 向rpc调用对象添加信息
	1. path，接口信息
	2. version，版本信息
2. 获得网络客户端
3. 获得url上不同的信息进行不同的处理

这里重点不多，主要是在网络客户端发送相关的消息上，
默认的这里的网络客户端可能存在两种形式的状态，

1. LazyConnectExchangeClient延迟连接的客户端
2. HeaderExchangeClient连接客户端

对于第1项，只是实际连接的时候才产生客户端。实际上最终的客户端都是HeaderExchangeClient该项

    public void send(Object message) throws RemotingException {
        channel.send(message);
    }

他对发送消息的处理很简单，直接委托了内部持有的属性，进行发送数据。
这个channel实际上是HeaderExchangeChannel

让我们来看一下HeaderExchangeChannel的send方法:

    public void send(Object message) throws RemotingException {
        send(message, getUrl().getParameter(Constants.SENT_KEY, false));
    }
我们可以看到并没有做很多的事情，继续看吧

	public void send(Object message, boolean sent) throws RemotingException {
        if (closed) {
            throw new RemotingException(this.getLocalAddress(), null, "Failed to send message " + message + ", cause: The channel " + this + " is closed!");
        }
        //根据message不同类型来发送不同的操作
        if (message instanceof Request || message instanceof Response || message instanceof String) {
            channel.send(message, sent);
        } else {
            Request request = new Request();
            request.setVersion("2.0.0");
            request.setTwoWay(false);
            request.setData(message);
            channel.send(request, sent);
        }
    }

逻辑还是10分的简单，简单检查了客户端是否已经关闭，然后根据不同的类性，进行不同的操作，实际上还是委托给内部属性的channel进行发送相关的消息。come on 继续

值得注意的的时候这个时候持有的channel对象实际上网络客户端对象，也就是说默认的情况下，是nettyClient，

对于特定的网络框架实现客户端，实际上并没有实现send方法，而是和我们之前反复提到的一样，使用了父类的方法，进行子类的回调实现。

首先我们来看父类的实现

    public void send(Object message, boolean sent) throws RemotingException {
        if (send_reconnect && !isConnected()) {
            connect();
        }
        Channel channel = getChannel();
        //TODO getChannel返回的状态是否包含null需要改进
        if (channel == null || !channel.isConnected()) {
            throw new RemotingException(this, "message can not send, because channel is closed . url:" + getUrl());
        }
        channel.send(message, sent);
    }

还是很短，我们主要看子类的实现，也就getChannel方法，话不多说，直接揭开面纱，这里的channel实际上是nettyChannel(默认情况下)

因此我们继续来看其实现

	public void send(Object message, boolean sent) throws RemotingException {
        super.send(message, sent);
        
        boolean success = true;
        int timeout = 0;
		ChannelFuture future = channel.write(message);
		if (sent) {
			timeout = getUrl().getPositiveParameter(Constants.TIMEOUT_KEY, Constants.DEFAULT_TIMEOUT);
			success = future.await(timeout);
		}
		Throwable cause = future.getCause();
		if (cause != null) {
			throw cause;
		}
    }

这里我们照样省略了部分异常代码的处理，因为这些都不是我们需要关注的重点，
我们继续来看，首先调用了父类的send方法，父类的send方法做的事情比较简单了
并没有做实际的事情，只是简单的进行校验

到这里我们看到了最终netty框架中的channel，并使用它进行发送，现在
发送流程已经结束，接下来就是就是我们队发送的东西的处理了，也就是解码器等等的handler的处理了