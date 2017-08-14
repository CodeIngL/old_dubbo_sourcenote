## dubbo目录服务 ##

目录服务是dubbo中应用到一个概念，如同uri代表一个特定的资源，目录服务也是如此，在网络中唯一的标识了资源。并对资源进行操作。

### dubbo中的目录服务设计 ###

----------

我们知道网络系统中，很多终端都可以用一个名称来描述节点和我们前面所说的网络编程中的概念一样，dubbo也做了很多概念的抽象

	public interface Node {
	    URL getUrl();
	    boolean isAvailable();
	    void destroy();
	}
以上就是dubbo对网络终端的另一种抽象，Node。

1. getUrl获得了节点的标识
2. isAvalable标识了这个节点是否可用
3. destroy摧毁这个节点


### 目录服务接口:Directory<T> ###
----------

目录服务来定位各个不同网络节点上的资源，在dubbo设计中，它继承了Node接口。

	public interface Directory<T> extends Node {
	    
	    Class<T> getInterface();
	
	    List<Invoker<T>> list(Invocation invocation) throws RpcException;
	    
	}

接口声明如上。其签名方法我们暂不讨论因为是针对dubbo，其他框架目录服务一般不适用。继续看。


### 目录服务抽象类:AbstractDirectory<T> ###

----------


	public abstract class AbstractDirectory<T> implements Directory<T> {...}

这是一个抽象类，在dubbo完成了目录服务的基本操作，并暴露一些方法给子类实现，从而进行回调。

	
    private final URL url;

    private volatile boolean destroyed = false;

    private volatile URL consumerUrl;

    private volatile List<Router> routers;

以上是其拥有的几个字段

1. url代表了其持有的资源
2. destroyed代表了目录的可用性
3. consumerUrl代表了订阅的资源
4. routers增加了对资源的路由

我们再来看其构造函数，代码如下

	public AbstractDirectory(URL url, URL consumerUrl, List<Router> routers) {
        if (url == null)
            throw new IllegalArgumentException("url == null");
        this.url = url;
        this.consumerUrl = consumerUrl;
        setRouters(routers);
    }

我们发现，构造函数中只是简单完成了本身持有的特征属性的赋值。


### 注册中心目录服务:RegistryDirector<T> ###


----------

该类就是dubbo实现自带的注册中心的核心类。比起Zookeeper，这个自然是显得粗略的很多，从学习的角度出发，还是有很多东西值得我们学习

	public RegistryDirectory(Class<T> serviceType, URL url) {
        super(url);
        if (serviceType == null) {
            throw new IllegalArgumentException("service type is null.");
        }
        if (url.getServiceKey() == null || url.getServiceKey().length() == 0) {
            throw new IllegalArgumentException("registry serviceKey is null.");
        }
        this.serviceType = serviceType;
        this.serviceKey = url.getServiceKey();
        this.queryMap = StringUtils.parseQueryString(url.getParameterAndDecoded(Constants.REFER_KEY));
        this.overrideDirectoryUrl = this.directoryUrl = url.setPath(url.getServiceInterface()).clearParameters().addParameters(queryMap).removeParameter(Constants.MONITOR_KEY);
        String group = directoryUrl.getParameter(Constants.GROUP_KEY, "");
        this.multiGroup = group != null && ("*".equals(group) || group.contains(","));
        String methods = queryMap.get(Constants.METHODS_KEY);
        this.serviceMethods = methods == null ? null : Constants.COMMA_SPLIT_PATTERN.split(methods);
    }

以上是其构造函数，在构造函数中做的操作不是很多，主要完成了属性的设置

1. 父类的属性设置(super)
2. 校验入参
3. 目录的特定服务serviceType
4. 服务的特定标志serviceKey
5. 参数映射，url中refer参数映射
6. group多个标志，multiGroup
7. 方法属性,参数中methods对应
8. 方法列表，拆分方法属性


### 目录服务的引用 ###

----------
dubbo中目录服务一般是为消费方的角色进行相关资源的管理。对应服务提供方，并没有什么影响。我们以消费方展开，深入详解目录服务的功能。

	  private <T> Invoker<T> doRefer(Cluster cluster, Registry registry, Class<T> type, URL url) {
        //新建注册目录服务
        RegistryDirectory<T> directory = new RegistryDirectory<T>(type, url);
        //为目录服务设置注册中心
        directory.setRegistry(registry);
        //为目录服务设置协议配置类（默认Protocol$Adaptive)
        directory.setProtocol(protocol);
        //构建订阅的url
        URL subscribeUrl = new URL(Constants.CONSUMER_PROTOCOL, NetUtils.getLocalHost(), 0, type.getName(), directory.getUrl().getParameters());
        if (!Constants.ANY_VALUE.equals(url.getServiceInterface())
                && url.getParameter(Constants.REGISTER_KEY, true)) {
            //像注册中心注册地址
            registry.register(subscribeUrl.addParameters(Constants.CATEGORY_KEY, Constants.CONSUMERS_CATEGORY,
                    Constants.CHECK_KEY, String.valueOf(false)));
        }
        //目录服务进行订阅(category:providers,configurators,routers)
        directory.subscribe(subscribeUrl.addParameter(Constants.CATEGORY_KEY, Constants.PROVIDERS_CATEGORY
                + "," + Constants.CONFIGURATORS_CATEGORY
                + "," + Constants.ROUTERS_CATEGORY));
        return cluster.join(directory);
    }

这段代码在我们的文章中已经多次出现过了，读者可能有很大的印象了，我们慢慢展开。

首先其归属于RegistryProtocol这个类。

1. 新建了一个注册目录服务。
2. 为目录服务设置注册中心，只是简单完成目录服务对象的属性设置
3. 为目录服务设置协议配置类(Protocol$Adaptive)
4. 构建订阅的url
	1. 新建一个url("consumer","本地地址多网卡中第一个有效地址"，tyep类名，url的参数信息)
5. 对于入参url需要进行注册中心进行注册
	1. 添加目录信息("category":"consumers")
	2. 添加check信息("check","false")
6. 使用目录服务对订阅url的处理
	1. 订阅的url加入键值对信息("category","provider,configuartors,routers")
7. 将目录服务加入cluster

上面就是基本的逻辑，但是需要我们主要的地方还是有的。
主要来关注第5，6点

其中第6点内部其实也是使用注册中心进行订阅，同时设置了其属性consumer

	public void subscribe(URL url) {
        setConsumerUrl(url);
        registry.subscribe(url, this);
    }

也就是说我们现在的重点变成了的

	registry.register（url）
	registry.subscribe（url）

### 注册中心的注册和订阅 ###

----------

根据常用的场景，我们先从常用的注册中心进行介绍，也就是zookeeper

ZookeeperRegistry继承FailbackRegistry，其并没有实现register，而是使用了父类的实现，然后再回调自己的实现。这种模式，我们之前已经详细介绍了。现在慢慢来看

	 public void register(URL url) {
        super.register(url);
        failedRegistered.remove(url);
        failedUnregistered.remove(url);
        try {
            // 向服务器端发送注册请求
            doRegister(url);
        } catch (Exception e) {
            Throwable t = e;
            // 如果开启了启动时检测，则直接抛出异常
            boolean check = getUrl().getParameter(Constants.CHECK_KEY, true)
                    && url.getParameter(Constants.CHECK_KEY, true)
                    && !Constants.CONSUMER_PROTOCOL.equals(url.getProtocol());
            boolean skipFailback = t instanceof SkipFailbackWrapperException;
            if (check || skipFailback) {
                if (skipFailback) {
                    t = t.getCause();
                }
                throw new IllegalStateException("Failed to register " + url + " to registry " + getUrl().getAddress() + ", cause: " + t.getMessage(), t);
            } else {
                logger.error("Failed to register " + url + ", waiting for retry, cause: " + t.getMessage(), t);
            }
            // 将失败的注册请求记录到失败列表，定时重试
            failedRegistered.add(url);
        }
    }
以上就是FailbackRegistry的实现，我们慢慢来介绍整个逻辑。

1. 调用父类的构造方法（简单将url加入到已经注册的集合中）
2. 从失败的注册集合中移除url
3. 从失败的未注册集合中移除url
4. 回调子类实现进行处理
5. 异常处理
	1. url中设置了check以及注册的url的设置check，同时url的协议不是consumer，抛出异常
	2. 其余的情况，加入到失败注册集合中，定时重试


逻辑很简单，重点还是在子类的实现中，我们慢慢来看

	protected void doRegister(URL url) {
        try {
            zkClient.create(toUrlPath(url), url.getParameter(Constants.DYNAMIC_KEY, true));
        } catch (Throwable e) {
            throw new RpcException("Failed to register " + url + " to zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }

源码如上，看起来是比较简单的我们慢慢分析

1. 在zk上创建相应的path，根据url中的信息决定叶子节点是否是永久或者临时节点
	1. path的生成规则
		1. 

