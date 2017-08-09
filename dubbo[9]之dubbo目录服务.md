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

以上是其构造函数，在构造函数中做的操作不是很多，主要完了属性的设置

1. 父类的属性设置(super)
2. 校验入参
3. 目录的特定服务serviceType
4. 服务的特定标志serviceKey
5. 参数映射，url中refer参数映射
6. group多个标志，multiGroup
7. 方法属性,参数中methods对应
8. 方法列表，拆分方法属性
