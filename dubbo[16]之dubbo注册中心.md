## dubbo注册中心 ##
对于注册中心，相信读者总是有所耳闻，其是大规模分布式系统的重要组成部分,大多数分布式系统用其来解决系统的高可用性。

本文之前的文章中，也已经对相关源码进行了解释，该文力图单独的讲解注册中心，力求能让读者更好的了解dubbo中注册中心所做的事情。

本文默认使用zookeeper为注册中心，同时将会讨论如何对dubbo进行扩展，使其支持其他形式的注册中心比如etcd

### 几个过程 ###

---

第三方的注册中心的使用，在程序层面都会涉及到下面几个操作，这些操作贯穿了整个分布式系统的的生命周期。

1. 注册中心客户端的生成
2. 注册中心进行注册信息
3. 注册中心进行订阅信息

### 注册中心客户端生成 ###

---
注册中心在dubbo中以接口形式存在 

    public interface Registry extends Node, RegistryService {
    }

随着相关还有一个注册中心工厂的概念，工厂设计模式还是非常常见的设计模式，其接口如下:

    @SPI("dubbo")
    public interface RegistryFactory {
        @Adaptive({"protocol"})
        Registry getRegistry(URL url);
    }

前面我们提过我们将会使用zookeeper来进行探讨，这是大多数开发者使用dubbo的选择，自然我们讨论的重点也就是dubbo中关于zookeeper相关的代码和逻辑。

	public ZookeeperRegistry(URL url, ZookeeperTransporter zookeeperTransporter) {
        super(url);
        if (url.isAnyHost()) {
            throw new IllegalStateException("registry address == null");
        }
        String group = url.getParameter(Constants.GROUP_KEY, DEFAULT_ROOT);
        if (!group.startsWith(Constants.PATH_SEPARATOR)) {
            group = Constants.PATH_SEPARATOR + group;
        }
        this.root = group;
        zkClient = zookeeperTransporter.connect(url);
        zkClient.addStateListener(new StateListener() {
            public void stateChanged(int state) {
                if (state == RECONNECTED) {
                    recover();
                }
            }
        });
    }

以上是**ZookeeperRegistry**类构造函数，**ZookeeperRegistry**在dubbo框架中对应于**zookeeper**这个注册中心的角色。

我们将对其分析，然后引申出整个注册中心的创建过程。源码逻辑如下:

1. 父类的构造函数
2. 检验url的合法性
3. 获得url中分组信息,完成属性root的设置
4. zookeeper客户端代码的编写

逻辑看起来似乎很简单的样子，但是还是有很多我们需要深入的细节分析，详细的分析有助于我们扩展dubbo迁移到etcd上。

### 第一点，父类的构造 ###

---
父类即**FailbackRegistry**是dubbo框架中对可以进行失败回调重试的相关注册中心的抽象，
其还继承了一个更加抽象的类**AbstractRegistry**，该类则是对所有注册中心的抽象。

	public FailbackRegistry(URL url) {
        super(url);
        int retryPeriod = url.getParameter(Constants.REGISTRY_RETRY_PERIOD_KEY, Constants.DEFAULT_REGISTRY_RETRY_PERIOD);
        this.retryFuture = retryExecutor.scheduleWithFixedDelay(()->{retry();}, retryPeriod, retryPeriod, TimeUnit.MILLISECONDS);
    }

可以看到代码量不是很长，逻辑如下所述：

1. 调用父类构造函数(完成更加抽象的操作）
2. 获取失败回调(重试的时间)，由url中的参数信息提供，键为retry.period，默认5s
3. 使用线程池来异步定时执行失败重试操作（**retry()**）

同样，这里的逻辑看起来似乎很简单，但是还是需要进一步理解，因而我们继续来看，首先还是父类的构造函数:

    public AbstractRegistry(URL url) {
        setUrl(url);
        syncSaveFile = url.getParameter(Constants.REGISTRY_FILESAVE_SYNC_KEY, false);
        String filename = url.getParameter(Constants.FILE_KEY, System.getProperty("user.home") + "/.dubbo/dubbo-registry-" + url.getHost() + ".cache");
        File file = null;
        if (ConfigUtils.isNotEmpty(filename)) {
            file = new File(filename);
            if (!file.exists() && file.getParentFile() != null && !file.getParentFile().exists()) {
                if (!file.getParentFile().mkdirs()) {
                    throw new IllegalArgumentException("Invalid registry store file " + file + ", cause: Failed to create directory " + file.getParentFile() + "!");
                }
            }
        }
        this.file = file;
        loadProperties();
        notify(url.getBackupUrls());
    }

以上是AbstractRegistry这个所有注册中心的抽象构造函数，一眼代码看下，感觉做的事情会很多。 事实的确如此，我们慢慢分析。逻辑如下:

1. 完成属性registryUrl的赋值，其代表注册url,也就是代表了注册中心,当然其由入参赋值，入参不得为空。
2. 完成属性syncSaveFile的赋值，其代表了是否异步保存文件
	1. 	由url中的参数信息获得，键为save.file，默认值为false
3. 完成属性file对保存文件的持有
	1. 获得需要保存的文件名，由url中的参数信息获得，键为file，默认值为$user.home$/.dubbo/dubbo-registry-主机host.cache
	2. 文件名有效(文件名为false，0，null，N/A等情况就是无效)，尝试构建文件，有则处理，无则新建
4. 完成属性properties的设置，file文件是properties文件，加载其内容
5. 通知整个集群地址url，备机地址由url中的参数信息获得，键为backup，无默认值

>tip: 概念的区分，注册url和注册到注册中心的url是不同的概念，前者代表了url和注册中心的对应关系。    

>tip: 这里的函数有名字上的歧义，但是获得的是整个集群的地址，而不是单单是备机地址

这里的逻辑比其子类实现看起来要复杂的很多，我们还是要继续深入，深入点主要第5点，至于其他说明都比较清晰。

	 protected void notify(List<URL> urls) {
        //省略入参校验代码
        for (Map.Entry<URL, Set<NotifyListener>> entry : getSubscribed().entrySet()) {
            URL url = entry.getKey();
            if (!UrlUtils.isMatch(url, urls.get(0))) {
                continue;
            }
            Set<NotifyListener> listeners = entry.getValue();
            if (listeners != null) {
                for (NotifyListener listener : listeners) {
                	notify(url, listener, filterEmpty(url, urls));//省略try.catch代码
                }
            }
        }
    }

以上是对集群url列表的通知实现，代码不是很长，我们慢慢分析，逻辑如下:

1. 遍历订阅信息映射(其由键:被订阅的url；值:被订阅的url对应的监听者集合)，进行处理
	1. 订阅信息的构建过程我们将在后面分析，现在我们假设存在订阅信息
2. 筛选符合的订阅信息元素
	1. 匹配规则(受订阅url和提供方url(备份url列表的第一个）进行对比,一个符合就行了)
	2. 两者的url中interface信息一致，或者受订阅的interface值是*
	3. 两者的category信息一致
	4. 提供方url中的enable是true或者受订阅url的enable是true
	5. 版本信息完全一致
3. 通知受符合的订阅url对应的所有监听者

逻辑如上，继续深入第3点，FailbakcRegistyry对第3项进行涉及的方法进行重写，所以我们关注FailbakcRegistyry的实现。

	 protected void notify(URL url, NotifyListener listener, List<URL> urls) {
        //......省略了入参检查代码
        try {
            doNotify(url, listener, urls);
        } catch (Exception t) {
            Map<NotifyListener, List<URL>> listeners = failedNotified.get(url);
            if (listeners == null) {
                failedNotified.putIfAbsent(url, new ConcurrentHashMap<NotifyListener, List<URL>>());
                listeners = failedNotified.get(url);
            }
            listeners.put(listener, urls);
			//......省略日志代码
        }
    }

FailbakcRegistyry对应的重写，按照原来文章的逻辑，这里会回调子类实现，然而现实是子类没有对其进行覆写，而他自己实现了逻辑，简单的调用父类也就是**AbstractRegistry**的实现。 整体逻辑如下:

1. 调用父类进行通知
2. 异常处理
	1. 失败信息加入缓存中**failedNotified**，failedNotified是一个结构，其维护了受订阅url和一个复杂结构的映射，这个复杂结构则是监听者和受订阅的列表集合，也就是说受订阅的url起始维护了一个受订阅的列表

---

	protected void notify(URL url, NotifyListener listener, List<URL> urls) {
		
		//......省略了入参校验代码

        Map<String, List<URL>> result = new HashMap<String, List<URL>>();
        for (URL u : urls) {
            if (UrlUtils.isMatch(url, u)) {
                String category = u.getParameter(Constants.CATEGORY_KEY, Constants.DEFAULT_CATEGORY);
                List<URL> categoryList = result.get(category);
                if (categoryList == null) {
                    categoryList = new ArrayList<URL>();
                    result.put(category, categoryList);
                }
                categoryList.add(u);
            }
        }
        if (result.size() == 0) {
            return;
        }

        Map<String, List<URL>> categoryNotified = notified.get(url);
        if (categoryNotified == null) {
            notified.putIfAbsent(url, new ConcurrentHashMap<String, List<URL>>());
            categoryNotified = notified.get(url);
        }

        for (Map.Entry<String, List<URL>> entry : result.entrySet()) {
            String category = entry.getKey();
            List<URL> categoryList = entry.getValue();
            categoryNotified.put(category, categoryList);
            saveProperties(url);
            listener.notify(categoryList);
        }
    }

这里代码逻辑相当的复杂，主要是复杂的数据结构，因而需要读者再三阅读，逻辑如下

1. 遍历集群url列表进行处理
	1. 进行受订阅的url和列表元素进行匹配，匹配规则同之前所说
	2. 获得列表元素中的分组信息，键为category，默认值为provider
	3. 根据分组将这些列表元素加入进行分组，保存进变量result
2. 处理缓存**notified**，**notified**是一个结构，其维护了受订阅url和一个复杂结构的映射，这个复杂结构则是受订阅url对应的受订阅列表的分组表现。
3. 监听者通知分组列表

文章到这里整个通知过程就介绍完了，但是读者肯定一脸迷茫，最后监听者通知分组列表还没有描述，我们迟点再说，现在回过头来再看。


---

### ZookeeperRegistry ###

---
回到这个具体的注册中心，我们上面大量篇幅介绍了其父类的构造函数中做的大量事情，
现在来继续其构造函数内部流程。

对于第2点，检查url合法性没有什么好说。

对于第3点，完成属性root的设置，即group，由url信息中group决定，默认是dubbo，root可能的话会加上前缀/,如果没有前缀/。

对于第4点，那就更没有什么好说了，唯一值得注意的是一旦进入重试状态后，进行的恢复操作

	 protected void recover() throws Exception {
        // register
        Set<URL> recoverRegistered = new HashSet<URL>(getRegistered());
        if (!recoverRegistered.isEmpty()) {
			//省略日志
            for (URL url : recoverRegistered) {
                failedRegistered.add(url);
            }
        }
        // subscribe
        Map<URL, Set<NotifyListener>> recoverSubscribed = new HashMap<URL, Set<NotifyListener>>(getSubscribed());
        if (!recoverSubscribed.isEmpty()) {
            if (logger.isInfoEnabled()) {
                logger.info("Recover subscribe url " + recoverSubscribed.keySet());
            }
            for (Map.Entry<URL, Set<NotifyListener>> entry : recoverSubscribed.entrySet()) {
                URL url = entry.getKey();
                for (NotifyListener listener : entry.getValue()) {
                    addFailedSubscribed(url, listener);
                }
            }
        }
    }

以上就是其恢复操作实现代码，我们发现其逻辑很简单

1. 获得已注册的集合，移入失败注册的集合（failedRegistered）
2. 获得被订阅的集合，移入失败订阅的集合 (failedSubscribed)


### 注册中心的第2个过程 ###

---
上面我们已经分析了第一个注册中心的过程，也就是注册中心的新建过程，现在我们来对注册中心的第2个过程进行分析，也就是register过程。对于zookeeper为注册中心来说，其没有实现方法register，该方法使用了父类也就是FailbackRegistry的方法，我们慢慢来看。

	 public void register(URL url) {
        super.register(url);
        failedRegistered.remove(url);
        failedUnregistered.remove(url);
        try {
            // 子类实现，在注册中心上注册url
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
以上就是代码，我们分析上面的逻辑:

1. 调用父类的注册方法,简单将需要注册的url加入到已注册集合中(registered)
2. 从失败注册集合(failedRegistered)中移除
3. 从失败未注册集合(failedUnregistered)中移除
4. 回调子类实现
5. 异常处理
	1. url中设置了check以及注册的url的设置check，同时url的协议不是consumer，抛出异常
	2. 其余的情况，加入到失败注册集合（failedRegistered）中，定时重试

逻辑还是比较简单的，我们主要关注也就是第4点

	protected void doRegister(URL url) {
        try {
            zkClient.create(toUrlPath(url), url.getParameter(Constants.DYNAMIC_KEY, true));
        } catch (Throwable e) {
			//省略异常代码
        }
    }

源码如上，逻辑如下:

1. 在zk上创建相应的path，根据url中的信息决定叶子节点是否是永久或者临时节点
	1. path的生成规则
		1. url中的interface不是*
			1. 则为/{root}/{interface:url信息中}/{category:url信息中（默认provider）}/url.toString
		2. url中的interface是*
			1. 则为/{root}/{category:url信息中（默认provider）}/url.toString


### 注册中心的第3个过程 ###

---
注册中心的第3个过程适用于消费方，因为是订阅相关，所以消费方需要重点关注。

同样这个过程由抽象类FailbackRegistry的方法提供，然后回调子类的实现。

	public void subscribe(URL url, NotifyListener listener) {
        super.subscribe(url, listener);
        removeFailedSubscribed(url, listener);
        try {
            // 向服务器端发送订阅请求(回调不同的子类实现)
            doSubscribe(url, listener);
        } catch (Exception e) {
            Throwable t = e;

            List<URL> urls = getCacheUrls(url);
            if (urls != null && urls.size() > 0) {
                notify(url, listener, urls);
                logger.error("Failed to subscribe " + url + ", Using cached list: " + urls + " from cache file: " + getUrl().getParameter(Constants.FILE_KEY, System.getProperty("user.home") + "/dubbo-registry-" + url.getHost() + ".cache") + ", cause: " + t.getMessage(), t);
            } else {
                boolean check = getUrl().getParameter(Constants.CHECK_KEY, true)
                        && url.getParameter(Constants.CHECK_KEY, true);
                boolean skipFailback = t instanceof SkipFailbackWrapperException;
                if (check || skipFailback) {
                    if (skipFailback) {
                        t = t.getCause();
                    }
                    throw new IllegalStateException("Failed to subscribe " + url + ", cause: " + t.getMessage(), t);
                } else {
                    logger.error("Failed to subscribe " + url + ", waiting for retry, cause: " + t.getMessage(), t);
                }
            }
            addFailedSubscribed(url, listener);
        }
    }

代码相对较长，我们还是老规矩，一点点来解释。

1. 父类的订阅方法，简单将监听者加入受订阅的url的对应监听者集合中(涉及缓存subscribed)
2. 移除失败订阅的内信息
	1. 尝试从受订阅的url对应的失败订阅集合中移除(涉及缓存failedSubscribed)
	2. 尝试从受订阅的url对应的失败未订阅集合中移除(涉及缓存failedUnsubscribed)
	3. 尝试从受订阅的url对应的失败通知集合中移除(涉及缓存failedNotified)
3. 回调子类的订阅处理
4. 异常处理
	1. 获得缓存(从属性缓存中properties中获取并处理生成相应的url列表)
	2. 如果缓存存在，通知所有缓存
	3. 缓存不存在
		1. url中设置了check以及注册的url的设置check
		2. 其余的情况，加入到失败注册集合中，定时重试
	4. 将失败的订阅加入失败集合
	
逻辑不算太复杂，主要来关注上面所说的第3点，即子类进行回调也就是zookeeper

	protected void doSubscribe(final URL url, final NotifyListener listener) {
        try {
            if (Constants.ANY_VALUE.equals(url.getServiceInterface())) {
                String root = toRootPath();
                ConcurrentMap<NotifyListener, ChildListener> listeners = zkListeners.get(url);
                if (listeners == null) {
                    zkListeners.putIfAbsent(url, new ConcurrentHashMap<NotifyListener, ChildListener>());
                    listeners = zkListeners.get(url);
                }
                ChildListener zkListener = listeners.get(listener);
                if (zkListener == null) {
                    listeners.putIfAbsent(listener, new ChildListener() {
                        public void childChanged(String parentPath, List<String> currentChilds) {
                            for (String child : currentChilds) {
                                child = URL.decode(child);
                                if (!anyServices.contains(child)) {
                                    anyServices.add(child);
                                    subscribe(url.setPath(child).addParameters(Constants.INTERFACE_KEY, child,
                                            Constants.CHECK_KEY, String.valueOf(false)), listener);
                                }
                            }
                        }
                    });
                    zkListener = listeners.get(listener);
                }
                zkClient.create(root, false);
                List<String> services = zkClient.addChildListener(root, zkListener);
                if (services != null && services.size() > 0) {
                    for (String service : services) {
                        service = URL.decode(service);
                        anyServices.add(service);
                        subscribe(url.setPath(service).addParameters(Constants.INTERFACE_KEY, service,
                                Constants.CHECK_KEY, String.valueOf(false)), listener);
                    }
                }
            } else {
                List<URL> urls = new ArrayList<URL>();
                for (String path : toCategoriesPath(url)) {
                    ConcurrentMap<NotifyListener, ChildListener> listeners = zkListeners.get(url);
                    if (listeners == null) {
                        zkListeners.putIfAbsent(url, new ConcurrentHashMap<NotifyListener, ChildListener>());
                        listeners = zkListeners.get(url);
                    }
                    ChildListener zkListener = listeners.get(listener);
                    if (zkListener == null) {
                        listeners.putIfAbsent(listener, new ChildListener() {
                            public void childChanged(String parentPath, List<String> currentChilds) {
                                ZookeeperRegistry.this.notify(url, listener, toUrlsWithEmpty(url, parentPath, currentChilds));
                            }
                        });
                        zkListener = listeners.get(listener);
                    }
                    zkClient.create(path, false);
                    List<String> children = zkClient.addChildListener(path, zkListener);
                    if (children != null) {
                        urls.addAll(toUrlsWithEmpty(url, path, children));
                    }
                }
                notify(url, listener, urls);
            }
        } catch (Throwable e) {
            throw new RpcException("Failed to subscribe " + url + " to zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }

这里代码就老长老长了，我们还是继续慢慢分析整个逻辑

### url的interface是*的处理 ###

---
1. 使用url获得监听缓存，无则新建
2. 获得子监听器，无则新建
3. 创建zk节点，获得子路径
4. 子路径添加到缓存中
5. 递归处理

### url的interface不是是*的处理 ###

---
1. 使用url获得上下问路劲
2. 遍历路径，监听缓存，无则新建
3. 获得子监听器，无则新建
4. 子路径添加到缓存中
5. 通知其他

### 小结 ###

---
到了这里基本上注册中心的所有逻辑都介绍完毕了，相信读者对注册中心有了更好更深入的理解了。