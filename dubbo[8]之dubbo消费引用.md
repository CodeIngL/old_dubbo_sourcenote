## dubbo之消费引用 ##
上一旁dubbo之消费方，我们简单介绍了消费方暴露的整个过程，但是其中的细节还是没有进行涉及，读者可能还存在很多疑惑。本文，我们将对消费引用这一环节展开详细描述。

### 消息引用入口:refer ###

---
该方法是消费方引用的入口，和我们之前的export功能在地位上功能相反，与export一样我们先来介绍最复杂的带注册中心的引用。

    refprotocol.refer(interfaceClass, url)

代码如同上，在我们基础上我们很容易明白refprotocol就是指
Protocol$Adaptive,其实际做的事情我们应该也很清楚了，根据url选择相应的普通扩展类，当然这个时候被包装了过滤器和监听器。
最最简单而又复杂的的协议实例，自然是RegistryProtocol。最最简单是因为在过滤器和监听器中，没有任何对其的操作，直接就到了其本身的处理中
复杂则体现在其本身代码的复杂程度

>tip：本身而言，在消费方，其并没有什么特权，不像服务方，如果有注册中心，一定是注册中心先操作。

### RegistryProtocol.refer ###

---
这个就是我们优先关注的对象，也就是注册中心配置类，可以通过消费方的属性url来配置，或者使用xml（spring来配置)

    public <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException {
        url = url.setProtocol(url.getParameter(Constants.REGISTRY_KEY, Constants.DEFAULT_REGISTRY)).removeParameter(Constants.REGISTRY_KEY);
        Registry registry = registryFactory.getRegistry(url);
        if (RegistryService.class.equals(type)) {
            return proxyFactory.getInvoker((T) registry, type, url);
        }
        Map<String, String> qs = StringUtils.parseQueryString(url.getParameterAndDecoded(Constants.REFER_KEY));
        String group = qs.get(Constants.GROUP_KEY);
        if (group != null && group.length() > 0 ) {
            if ( ( Constants.COMMA_SPLIT_PATTERN.split( group ) ).length > 1
                    || "*".equals( group ) ) {
                return doRefer( getMergeableCluster(), registry, type, url );
            }
        }
        return doRefer(cluster, registry, type, url);
    }

以上就是该方法的代码的实现，篇幅并不是很长，值得说明的是对于注册中心url，我们先前已经知道，在loadRegistry中其protocol都被设置为了registry，而
真正的的注册中心协议被其转移到了参数信息中，也就是键为registry的键值对。整体代码逻辑如下:

1. 处理url，我们上面已经提到过，这是为了转换回来真正的注册中心使用的协议，比如zookeeper。
2. 根据真正的注册中心的url来获得相应的注册中心。比如zookeeper
3. 如果type是RegistryService，获得invoker,因为本事就是注册中心，没有必要再做其他的操作
4. 获得url中refer的键信息，也就是消费方的所有信息，上面我们说过，消费方所有信息都会转换为一个参数映射，构建成为url的一个参数的键值对。而不像
服务方，构建了一个单独的url
5. 获得group的信息。
    1. 对应一个消费引用，如果他配置多个组，应该是使用不同的集群策略，这里使用mergeableCluster
    2. 如果只有一个组，那么使用本身cluster，里面会引向默认的集群策略

### RegistryProtocol.doRefer ###

---
该方法是真正的逻辑，这种编码上的设计在dubbo中很常见，spring里面也遍布这样的设计。

    private <T> Invoker<T> doRefer(Cluster cluster, Registry registry, Class<T> type, URL url) {
        RegistryDirectory<T> directory = new RegistryDirectory<T>(type, url);
        directory.setRegistry(registry);
        directory.setProtocol(protocol);
        URL subscribeUrl = new URL(Constants.CONSUMER_PROTOCOL, NetUtils.getLocalHost(), 0, type.getName(), directory.getUrl().getParameters());
        if (! Constants.ANY_VALUE.equals(url.getServiceInterface())
                && url.getParameter(Constants.REGISTER_KEY, true)) {
            registry.register(subscribeUrl.addParameters(Constants.CATEGORY_KEY, Constants.CONSUMERS_CATEGORY,
                    Constants.CHECK_KEY, String.valueOf(false)));
        }
        directory.subscribe(subscribeUrl.addParameter(Constants.CATEGORY_KEY, 
                Constants.PROVIDERS_CATEGORY 
                + "," + Constants.CONFIGURATORS_CATEGORY 
                + "," + Constants.ROUTERS_CATEGORY));
        return cluster.join(directory);
    }
以上就是逻辑的代码实现，看起来来代码篇幅不是很长，但是其中做的事情却非常的多，相比于服务方的暴露，消费方的引用显得更加的复杂，我们来整体介绍下这段代码所:做的工作:

1. 构建了一个注册中心的目录服务，什么是目录服务，请见dubbo目录服务一文
2. 为目录服务对象设置相关属性，比如注册中心，注册中心协议配置类
3. 构建一个需要被订阅的URL
4. 如果不是泛化调用，且需要被注册的话，将这个要被订阅的URL注册到注册中心上
5. 目录服务订阅该需要被订阅的URL
6. 使用cluster来聚集目录服务

逻辑说明如上，但是我相信读者肯定是一脸懵逼的感觉，这正验证了这里的逻辑的复杂性。我们慢慢展开其中的奥秘。当然如果读者看了我所提示的文章这里基本上没有什么问题了。

### 第一点的介绍 ###

---
第一点也就是构建了注册中心目录服务，在消费方，有目录服务来辅助注册中心进行相关的操作通知和回调，这里我们需要细细的说明。

### 第二点的介绍 ###

---
上述第一点构建了目录服务，该目录服务还没有设置某些特殊的属性，这里为目录服务设置了相应的注册中心实例，比如ZookeeperRegistry,这样目录服务就能通过注册中心做一些事情了。

### 第三点的介绍 ###

---
第三点也就是设置了protocol，我们知道相关的protocol都是被容器自动注入进来的。这个没有什么好说的

### 第三点的介绍 ###

---
上面的第三点，也就是构建一个需要被订阅的URL,这是一个比较关键的地方，我们要进行说明，这里我刚开始也踩了一个坑。 这里构建的url其协议是consumer，同时他的参数是消息引用接口的相关参数，同时里面去掉了监控相关的信息，重点是这个消息引用接口的相关参数，而不是注册中心的相关参数哦。

### 第四点的介绍 ###

---
上面的第四点，也就是关于注册到注册中心上的操作，对于非泛化调用，且配置了需要注册的的信息，则注册中心需要将该订阅的URL注册到注册中心上，当然额外的添加了其他的信息。实际上也就是多了两个信息，用于说明其的目录，是否需要校验。

### 第五点的介绍 ###

---
上面的第五点，订阅需要被订阅的url，这些url如何确定，通过前面的订阅url就能完成,只要订阅该url下目录为各种信息就可以了，也就是目录为providers,configurators,routers。其对应就是目录服务的的消费url，目录对这些url进行消费，同时使用注册中心对这个url进行订阅

### RegistryProtocol.refer的小结 ###

---
到这里关于消费方注册中心的refer就到此为止了。也就是说如果开发者只在xml中配置了注册中心，那么整个消费暴露也就在这里完成了，
读者肯定存在疑惑，服务方的暴露中除了注册中心，本身也暴露一个协议配置，比如dubbo。消费方好像看起来没有，怎么玩的转。

事实上这就是我们本文要讲述的重点的知识，我们现在开始深入，这个默认的dubbo协议是怎么转出来的。

### DubboProtocol.refer ###

---
配置DubboProtocol当然这里说的不是Dubbo注册中心，而是简单的DubboProtocol，配置该项需要使用url

    public <T> Invoker<T> refer(Class<T> serviceType, URL url) throws RpcException {
        DubboInvoker<T> invoker = new DubboInvoker<T>(serviceType, url, getClients(url), invokers);
        invokers.add(invoker);
        return invoker;
    }

代码很简单，比之前的注册中心好很多，但是需要注意的地方却更多

### getClient网络展开 ###

---
这就是我们需要注意的地方之一，网络细节全都封装在内部