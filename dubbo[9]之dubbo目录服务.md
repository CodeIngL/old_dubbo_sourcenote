## dubbo目录服务 ##
目录服务是dubbo中应用到一个概念，如同uri代表一个特定的资源，目录服务也是如此，在网络中唯一的标识了资源。并对资源进行操作。

### dubbo中的目录服务设计 ###

---
我们知道网络系统中，很多终端都可以用一个名称来描述节点和我们前面所说的网络编程中的概念一样，dubbo也做了很多概念的抽象

	public interface Node {
	    URL getUrl();
	    boolean isAvailable();
	    void destroy();
	}
以上就是dubbo对网络终端的另一种抽象，Node。

1. getUrl获得了节点的标识
2. isAvalable标识了这个节点是否可用
3. destroy销毁这个节点使之不可用

### 目录服务接口:Directory<T> ###

---
目录服务来定位各个不同网络节点上的资源，在dubbo设计中，它继承了Node接口。

	public interface Directory<T> extends Node {
	    Class<T> getInterface();
	    List<Invoker<T>> list(Invocation invocation) throws RpcException;
	}

接口声明如上。其签名方法我们暂不讨论因为是针对dubbo，其他框架目录服务一般不适用。继续看。

### 目录服务抽象类:AbstractDirectory<T> ###

---
目录服务抽象类，提供了更高一层次抽象的操作。其内部实现篇幅较长，我们慢慢展开,首先贴出其类声明:

	public abstract class AbstractDirectory<T> implements Directory<T> {...}

通过声明代码我们可以看出这是一个抽象类，其dubbo完成了目录服务的基本操作，并暴露一些方法给子类实现，从而进行回调。
我们在来看其持有几个字段，代码如下：

    private final URL url;

    private volatile boolean destroyed = false;

    private volatile URL consumerUrl;

    private volatile List<Router> routers;

以上便是其拥有的几个字段

1. url代表了其持有的资源
2. destroyed代表了目录的可用性
3. consumerUrl代表了受订阅的资源
4. routers增加了对资源的路由

我们再来看其构造函数，代码如下

	public AbstractDirectory(URL url, URL consumerUrl, List<Router> routers) {
        if (url == null)
            throw new IllegalArgumentException("url == null");
        this.url = url;
        this.consumerUrl = consumerUrl;
        setRouters(routers);
    }

我们发现，构造函数中只是简单完成了本身持有的特征属性的赋值，并没有很复杂的操作。当然这里有个setRouters方法这里做的事情也是比较重要的。


### 设置路由 ###

---
设置路由，自然和我们说的上面的的setRouters有这千丝万缕的关系，自然这里就是介绍其内部的操作。首先看代码实现:

    protected void setRouters(List<Router> routers) {
        routers = routers == null ? new ArrayList<Router>() : new ArrayList<Router>(routers);
        String routerkey = url.geParameter(Constants.ROUTER_KEY);
        if (routerkey != null && routerkey.length() > 0) {
            RouterFactory routerFactory = ExtensionLoader.getExtensionLoader(RouterFactory.class).getExtension(routerkey);
            routers.add(routerFactory.getRouter(url));
        }
        routers.add(new MockInvokersSelector());
        Collections.sort(routers);
        this.routers = routers;
    }
对于入参，也就是代表了路由列表。里面做的事情也比较多。我们来简单的介绍下其逻辑:

1. 拷贝一份入参代表的路由列表
2. 获得url中的路由信息，存在的话，使用该信息加载相应的路由策略，并加入路由列表中
3. 为路由列表新增一个Mock的路由
4. 对路由进行排序，并将本实例持有的路由列表替换为上述路由列表

### 注册中心目录服务:RegistryDirector<T> ###

---
该类是目录服务的一个具体类，服务于注册中心，当然他的作用我们接下来回慢慢展开，首先我们来关注下其构造函数。

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

以上是其构造函数的实现代码，我们可以看到似乎逻辑内容不是很多，我们这里还是简单的阐述一下其逻辑：

1. 父类的属性设置(super)
2. 校验入参
3. 目录的特定服务serviceType(也就是消费方需要引用的接口类)
4. 服务的特定标志serviceKey(group/接口名:version)
5. 参数映射，url中refer参数映射（消费方的相关参数以及设置信息)
6. 设置可以被覆盖的目录url也就是目录url,入参去掉的相应的监控键
6. group多个标志，multiGroup(也就是消费方接口所说的group是否填了多项)
7. 方法属性,参数中methods对应
8. 方法列表，拆分方法属性

相信读者自然还是很不明白，这目录服务做了什么事情，当然这只能在有上下文环境中才能认识，我们稍后以消费方暴露来嵌入这个上下文，当然在这个之前我们需要提一下另一个目录服务。

### 静态目录服务 ###

---
静态目录服务即StaticDirector是另一种形式的目录服务，当然相比于注册目录服务，显得自然也是简单很多。同样我们在这里贴出其核心构造函数:

    public StaticDirectory(URL url, List<Invoker<T>> invokers, List<Router> routers) {
        super(url == null && invokers != null && invokers.size() > 0 ? invokers.get(0).getUrl() : url, routers);
        if (invokers == null || invokers.size() == 0)
            throw new IllegalArgumentException("invokers == null");
        this.invokers = invokers;
    }
从代码上看，显然比注册目录服务类简单了很多很多，基本上只是调用了父类的方法，然后设置了一个自己需要持有的调用者对象列表。

### 目录服务的上下文环境 ###

---
上面我们讲述的目录服务是一件很抽象的事情，自然需要嵌入上下文环境，让读者更好的理解其中的奥秘，现在我们以消费方的引用展开，当然部分内容会和前面的文章有
重复部分，当然这都是为了更好的使读者理解其中的意义。

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

这段代码在我们的文章中已经多次出现过了，读者可能有很大的印象了，说吧了也就是注册中心协议核心方法，这里我们和之前一样对其展开，唯一不同的地方，此刻的读者应该对目录服务有了一个概念了。

1. 新建了一个注册目录服务。
2. 为目录服务设置注册中心。
3. 为目录服务设置协议配置类(Protocol$Adaptive)
4. 构建订阅的url
	1. 新建一个url("consumer","本地地址多网卡中第一个有效地址"，tyep类名，url的参数信息)
5. 对于入参url需要进行注册中心进行注册
	1. 添加目录信息("category":"consumers")
	2. 添加check信息("check","false")
6. 使用目录服务对订阅url的处理
	1. 订阅的url加入键值对信息("category","provider,configuartors,routers")
7. 将目录服务加入cluster

上面就是基本的逻辑，需要我们一一的讲述。

### 第1点的介绍 ###

---
构建注册目录服务实例，我们开头已经详细介绍过了，不再介绍

### 第2点的介绍 ###

--- 
简单为目录服务实例完成属性的设置，这里设置了注册中心。

### 第3点的介绍 ###

---
同样是简单的为目录服务实例完成属性的设置，这里设置了协议扩展类

### 第4点的介绍 ###

---
构建一个受订阅的url，这个受订阅的url，其协议是consumer，地址为本地地址，端口为0（因为不需要),
上下文为接口名，参数为目录服务中持有的注册url的参数，也就是入参url的参数信息。

### 第5点的介绍 ###

---
对不是泛化调用的处理，需要注册到注册中心上去，注册上述的受订阅的url，但是增加了几个键值对
（category：consumer）（check：false)

### 第6点的介绍 ###

---
使用目录服务进行对受订阅的url完成订阅。但是增加了一个键值对
（category：provider，configurators，routers)

### 最后 ###

---
使用聚集策略来聚集目录服务。


### 小结 ###

---
这里我们简单的介绍了消费方引用真正的逻辑，但是我相信读者还是一脸迷茫，继续看，这里还有我们展开的地方。

### 目录服务订阅和注册中心的订阅 ###

---
这小点对应上面的最后一点，跟踪源码可以发现,事实上目录服务的订阅只是简单使用了其持有的注册中心进行订阅，当然这个时候也完成了另一个属性consumerUrl的最终设置。值得注意的是目录服务本身实现了监听，
也就是其本省对该受订阅的url进行监听,源码入下:

	public void subscribe(URL url) {
        setConsumerUrl(url);
        registry.subscribe(url, this);
    }

也就是说我们现在的重点变成了的

	registry.register（url）
	registry.subscribe（url）

### 注册中心的注册和订阅 ###

---
这一节我已经单独拎了出来，读者请看dubbo注册中心一文。

### 注册目录服务的特别之处 ###

---
如果你看到了注册中心一文，那么我想你对注册中心有了一定的了解，这里我们继续这个目录服务，
因为注册目录服务有些特殊，其实现了监听器。

注册没有什么值得很关注的地方，主要是订阅部分。

    registry.subscribe(url, this);

上面就是我们所说的重点关注的地方。订阅时发生了通知，也就是回调注册服务中的notify方法

### 注册目录服务的通知 ###

---
注册目录服务获得通知，进行一些操作，对消费方而言相当的重要，因为这里他将构建Invoker。

    public synchronized void notify(List<URL> urls) {
            List<URL> invokerUrls = new ArrayList<URL>();
            List<URL> routerUrls = new ArrayList<URL>();
            List<URL> configuratorUrls = new ArrayList<URL>();
            for (URL url : urls) {
                String protocol = url.getProtocol();
                String category = url.getParameter(Constants.CATEGORY_KEY, Constants.DEFAULT_CATEGORY);
                if (Constants.ROUTERS_CATEGORY.equals(category) || Constants.ROUTE_PROTOCOL.equals(protocol)) {
                    routerUrls.add(url);
                } else if (Constants.CONFIGURATORS_CATEGORY.equals(category) || Constants.OVERRIDE_PROTOCOL.equals(protocol)) {
                    configuratorUrls.add(url);
                } else if (Constants.PROVIDERS_CATEGORY.equals(category)) {
                    invokerUrls.add(url);
                } else {
                    //省略部分代码
                }
            }
            if (configuratorUrls != null && configuratorUrls.size() > 0) {
                this.configurators = toConfigurators(configuratorUrls);
            }
            if (routerUrls != null && routerUrls.size() > 0) {
                List<Router> routers = toRouters(routerUrls);
                if (routers != null) { // null - do nothing
                    setRouters(routers);
                }
            }
            List<Configurator> localConfigurators = this.configurators; 
            this.overrideDirectoryUrl = directoryUrl;
            if (localConfigurators != null && localConfigurators.size() > 0) {
                for (Configurator configurator : localConfigurators) {
                    this.overrideDirectoryUrl = configurator.configure(overrideDirectoryUrl);
                }
            }
            refreshInvoker(invokerUrls);
        }

代码几乎是这篇文章最长的部分了，我们整体的来看一下，其逻辑:

1. 构建三种形式的url列表
2. 对入参进行分类
    1. 是路由相关的放入路由
    2. 是配置相关的放入配置
    3. 是invoker相关的放入invoker的列表中
3. 处理配置相关的url
4. 处理路由相关的url
5. 处理可以覆盖的信息
6. 处理invoker相关的url

逻辑就是这样，细节确实很多，需要我们深入的探究，这里我们重点先关注我们需要关注的重点，也就时上述的第6项

### refreshInvoker刷新调用者 ###

---
这个方法比较重要，我们需要深入的学习,首先先贴出其源码:

    private void refreshInvoker(List<URL> invokerUrls) {
        if (invokerUrls != null && invokerUrls.size() == 1 && invokerUrls.get(0) != null
                && Constants.EMPTY_PROTOCOL.equals(invokerUrls.get(0).getProtocol())) {
            this.forbidden = true; // 禁止访问
            this.methodInvokerMap = null; // 置空列表
            destroyAllInvokers(); // 关闭所有Invoker
        } else {
            this.forbidden = false; // 允许访问
            Map<String, Invoker<T>> oldUrlInvokerMap = this.urlInvokerMap; // local reference
            if (invokerUrls.size() == 0 && this.cachedInvokerUrls != null) {
                invokerUrls.addAll(this.cachedInvokerUrls);
            } else {
                this.cachedInvokerUrls = new HashSet<URL>();
                this.cachedInvokerUrls.addAll(invokerUrls);//缓存invokerUrls列表，便于交叉对比
            }
            if (invokerUrls.size() == 0) {
                return;
            }
            Map<String, Invoker<T>> newUrlInvokerMap = toInvokers(invokerUrls);// 将URL列表转成Invoker列表
            Map<String, List<Invoker<T>>> newMethodInvokerMap = toMethodInvokers(newUrlInvokerMap); // 换方法名映射Invoker列表
            // state change
            //如果计算错误，则不进行处理.
            if (newUrlInvokerMap == null || newUrlInvokerMap.size() == 0) {
                logger.error(new IllegalStateException("urls to invokers error .invokerUrls.size :" + invokerUrls.size() + ", invoker.size :0. urls :" + invokerUrls.toString()));
                return;
            }
            this.methodInvokerMap = multiGroup ? toMergeMethodInvokerMap(newMethodInvokerMap) : newMethodInvokerMap;
            this.urlInvokerMap = newUrlInvokerMap;
            try {
                destroyUnusedInvokers(oldUrlInvokerMap, newUrlInvokerMap); // 关闭未使用的Invoker
            } catch (Exception e) {
                logger.warn("destroyUnusedInvokers error. ", e);
            }
        }
    }
### 总结 ###

---
这里讲述的是目录服务一文，事实上，目录服务只是对相关url的监听，本身也是通过注册中心来完成的。相信读者应该读dubbo的目录服务有了更深的印象。
