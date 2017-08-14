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

最后开始我们的invoker调用之旅了，默认情况下，我们配置衣柜注册配置类，则不使用Cluster进行管理请求。所以



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

