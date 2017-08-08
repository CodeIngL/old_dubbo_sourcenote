## dubbo消费方 ##
有了前面的文章的铺垫，现在我们从消费方的角度来看dubbo的执行，之前我们将的都是服务方的故事

### 消费方复杂配置类 ###

----------

废话不多说，假定认为读者对dubbo已经有比较深入的了解。

ReferenceConfig（消费方的入口）

- 消费者必须使用的配置类
		
	- 复杂的非配置属性：

			private static final Protocol refprotocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();

			private static final Cluster cluster = ExtensionLoader.getExtensionLoader(Cluster.class).getAdaptiveExtension();

			private static final ProxyFactory proxyFactory = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();

对于这些扩展配置的代码，读者应该相当的熟悉了，不熟悉的读者请阅读前面的文章。
现在我们把目光转移到消费方的相关启动代码上

### 引用入口:get() ###

----------
引用入口是dubbo使用api方式编程需要调用的方法，返回一个rpc接口的包装，通过返回值，开发者就可以黑盒的方式进行rpc调用了。源码如下:

    public synchronized T get() {
        if (destroyed) {
            throw new IllegalStateException("Already destroyed!");
        }
        if (ref == null) {
            init();
        }
        return ref;
    }

代码不多，和服务方类似，最开始总是先做些配置和属性的校验，然后才是真正的网络通信的开启。

### 引用入口逻辑:init() ###

----------
该方法就是入口的逻辑，读者可以对比服务方导出的逻辑，基本上是做配置类的校验和设置，真正的引用将在最后暴露，我们先看代码逻辑

    private void init() {
        //1. 对初始化标志进行校验

		//2. 对（interfaceName:必填）接口名字配置项校验

		//3. 对 (consumer:可选)**简单配置类**进行校验

		//4. 使用**appendProperties**完成自己本身基本属性的设置

		//5. 对(generic：可选)尝试设置
		//----generic为null的情况下，尝试使用consumer中的配置

		//6. 对(generic：可选)进行处理
		//----通用接口，属性**interfaceClass**为GenericService
		//----非通用接口，属性**interfaceClass**为**interfaceName**的类类型
				//interfaceClass必须存在，且是接口，接口必须包含配置类methods的全部方法（如果methods配置存在的话）

		//7. 尝试从系统以及配置文件中获得信息来构建url字符串

		//8. 尝试从嵌套的简单配置中，完成配置的转移，即完成本身的配置类属性配置
		//----application为空，尝试consumer配置类中获取
		//----module为空，尝试从consumer配置类中获取
		//----registries为空，尝试从consumer，module，application配置类中获取
		//----monitor为空，尝试从consumer，module，application配置类中获取
		//----protocols为空，尝试从consumer配置类中获取

		//9 .对 (application:可选)**简单配置类**进行校验

		//10 .对属性local和stub的的校验，并生成mock

		//11. 构建关键的url（元信息）的参数map

        StaticContext.getSystemContext().putAll(attributes);
        ref = createProxy(map);	
    }

我们主要采用注释的方法介绍整个方法，原因和之前介绍服务方一样:方法内涉及代码比较长。现在我们选择其中关键点进行说明


----------

第7点（尝试从系统以及配置文件中获得信息来构建url字符串）的说明，首先贴出相关代码:

        String resolve = System.getProperty(interfaceName);
        String resolveFile = null;
        if (resolve == null || resolve.length() == 0) {
            resolveFile = System.getProperty("dubbo.resolve.file");
            if (resolveFile == null || resolveFile.length() == 0) {
                File userResolveFile = new File(new File(System.getProperty("user.home")), "dubbo-resolve.properties");
                if (userResolveFile.exists()) {
                    resolveFile = userResolveFile.getAbsolutePath();
                }
            }
            if (resolveFile != null && resolveFile.length() > 0) {
                Properties properties = new Properties();
                FileInputStream fis = null;
                try {
                    fis = new FileInputStream(new File(resolveFile));
                    properties.load(fis);
                } catch (IOException e) {
                    throw new IllegalStateException("Unload " + resolveFile + ", cause: " + e.getMessage(), e);
                } finally {
                    try {
                        if (null != fis) fis.close();
                    } catch (IOException e) {
                        logger.warn(e.getMessage(), e);
                    }
                }
                resolve = properties.getProperty(interfaceName);
            }
        }
        if (resolve != null && resolve.length() > 0) {
            url = resolve;
			//打印日志
        }
消费方持有一个String型的url(可选；下文我们统称**字符串url**)，消费方会尝试构建出来该字符串url。

1. 尝试使用属性interfaceName值做为key从系统环境中直接查找其值。
	1. 如果存在直接作为字符串url的值
2. 尝试从配置文件中获得url，使用属性interfaceName值做为key从配置文件对应的映射中获得**字符串url**。那么配置文件如何获得?
	1. 尝试通过使用dubbo.resolve.file做为key在系统中查找其文件路径
	2. 尝试使用用户目录下的dubbo-resolve.properties作为配置文件，构建配置文件的路径

----------

第11点（构建关键的url（元信息）的参数映射）的说明

服务方导出中，我们也提到了构建相关的参数映射，与这里基本相似。现在我们慢慢探究这个过程。首先先贴出相关代码:

	    Map<String, String> map = new HashMap<String, String>();
        map.put(Constants.SIDE_KEY, Constants.CONSUMER_SIDE);
        map.put(Constants.DUBBO_VERSION_KEY, Version.getVersion());
        map.put(Constants.TIMESTAMP_KEY, String.valueOf(System.currentTimeMillis()));
        if (ConfigUtils.getPid() > 0) {
            map.put(Constants.PID_KEY, String.valueOf(ConfigUtils.getPid()));
        }
        if (!isGeneric()) {
            String revision = Version.getVersion(interfaceClass, version);
            if (revision != null && revision.length() > 0) {
                map.put("revision", revision);
            }

            String[] methods = Wrapper.getWrapper(interfaceClass).getMethodNames();
            if (methods.length == 0) {
                logger.warn("NO method found in service interface " + interfaceClass.getName());
                map.put("methods", Constants.ANY_VALUE);
            } else {
                map.put("methods", StringUtils.join(new HashSet<String>(Arrays.asList(methods)), ","));
            }
        }
        map.put(Constants.INTERFACE_KEY, interfaceName);
        appendParameters(map, application);
        appendParameters(map, module);
        appendParameters(map, consumer, Constants.DEFAULT_KEY);
        appendParameters(map, this);
        String prefix = StringUtils.getServiceKey(map);
        Map<Object, Object> attributes = new HashMap<Object, Object>();
        if (methods != null && methods.size() > 0) {
            for (MethodConfig method : methods) {
                appendParameters(map, method, method.getName());
                String retryKey = method.getName() + ".retry";
                if (map.containsKey(retryKey)) {
                    String retryValue = map.remove(retryKey);
                    if ("false".equals(retryValue)) {
                        map.put(method.getName() + ".retries", "0");
                    }
                }
                appendAttributes(attributes, method, prefix + "." + method.getName());
                checkAndConvertImplicitConfig(method, map, attributes);
            }
        }

阅读前几篇文章的童鞋，肯定对此相当的眼熟。其逻辑如下:

	- 放入（side：consumer）代表是消费方（服务方为(side:provider)
	- 放入（dubbo：2.0.0）版本信息(服务方一致）
	- 放入（timestamp：当前时间）（服务方一致）
	- 放入（pid：应用pid）进程信息（服务方一致）
	- 对通用接口处理(服务方一致）
	- 放入（interface：interfaceName）
	- 将application配置类信息放入map中（服务方一致）
	- 将module配置类信息放入map中（服务方一致）
	- 将consumer配置类信息放入map中，前缀default（服务方式放入provider配置类)
	- 将this配置类信息放入map中(服务方一致)
	- 遍历methods配置类列表，将每个method的配置信息放入map中，前缀方法名(服务方一致)
	- 对每个method配置类构建，key:"方法名.retry"的键做处理，并删除该键，如果该键的值等于false，将其转换为（"方法名.retries":"0"）放入(服务方一致)

基本上和服务方的处理一致，当然我们需要说明其不同的地方：

1. 这里构建了一个映射attributes
	1. 使用**appendAttributes**对每一个method配置类的信息放入，前缀为group(对应值)/interface(对应值):version。
	2. 使用**checkAndConvertImplicitConfig**对每一个方法，以及map和attributes进行了处理。


### appendParameters与appendAttributes ###

----------
appendAttributes和appendParameters很相像，但做的事情却少多了

	protected static void appendAttributes(Map<Object, Object> parameters, Object config, String prefix) {
		//校验方法以及获得配置类的方法列表
        for (Method method : methods) {
            try {
                String name = method.getName();
                if ((name.startsWith("get") || name.startsWith("is"))
                        && !"getClass".equals(name)
                        && Modifier.isPublic(method.getModifiers())
                        && method.getParameterTypes().length == 0
                        && isPrimitive(method.getReturnType())) {
                    Parameter parameter = method.getAnnotation(Parameter.class);
                    if (parameter == null || !parameter.attribute())
                        continue;
                    String key;
                    if (parameter != null && parameter.key() != null && parameter.key().length() > 0) {
                        key = parameter.key();
                    } else {
                        int i = name.startsWith("get") ? 3 : 2;
                        key = name.substring(i, i + 1).toLowerCase() + name.substring(i + 1);
                    }
                    Object value = method.invoke(config, new Object[0]);
                    if (value != null) {
                        if (prefix != null && prefix.length() > 0) {
                            key = prefix + "." + key;
                        }
                        parameters.put(key, value);
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
    }

以上就是方法的代码实现,逻辑如下

	1. 处理基本类型（基本配置属性）的get或者is方法，尝试从方法上获得注解@Parameter
	1. 排除掉返回值是Object或者有注解并且注解attribute为false的这些方法
	2. 构建key（将存入map:入参），有注解尝试使用注解的key的有效值，否则方法名中提取，例如getCodelDemo，提取为codel.demo
	3. 放射获得基本配置属性值（不一定是String），对于合法的值，将会和key形成组合放入map中，合法值的处理
		1. 如果前缀存在，为key添加前缀
		2. 将key值和处理后的基本属性值放入map中

### 插曲之checkAndConvertImplicitConfig ###

----------
介绍了上面的方法，我们再来看另一个陌生的操作

	private static void checkAndConvertImplicitConfig(MethodConfig method, Map<String, String> map, Map<Object, Object> attributes) {
        //check config conflict
        if (Boolean.FALSE.equals(method.isReturn()) && (method.getOnreturn() != null || method.getOnthrow() != null)) {
            throw new IllegalStateException("method config error : return attribute must be set true when onreturn or onthrow has been setted.");
        }
        //convert onreturn methodName to Method
        String onReturnMethodKey = StaticContext.getKey(map, method.getName(), Constants.ON_RETURN_METHOD_KEY);
        Object onReturnMethod = attributes.get(onReturnMethodKey);
        if (onReturnMethod != null && onReturnMethod instanceof String) {
            attributes.put(onReturnMethodKey, getMethodByName(method.getOnreturn().getClass(), onReturnMethod.toString()));
        }
        //convert onthrow methodName to Method
        String onThrowMethodKey = StaticContext.getKey(map, method.getName(), Constants.ON_THROW_METHOD_KEY);
        Object onThrowMethod = attributes.get(onThrowMethodKey);
        if (onThrowMethod != null && onThrowMethod instanceof String) {
            attributes.put(onThrowMethodKey, getMethodByName(method.getOnthrow().getClass(), onThrowMethod.toString()));
        }
        //convert oninvoke methodName to Method
        String onInvokeMethodKey = StaticContext.getKey(map, method.getName(), Constants.ON_INVOKE_METHOD_KEY);
        Object onInvokeMethod = attributes.get(onInvokeMethodKey);
        if (onInvokeMethod != null && onInvokeMethod instanceof String) {
            attributes.put(onInvokeMethodKey, getMethodByName(method.getOninvoke().getClass(), onInvokeMethod.toString()));
        }
    }
代码不长，简单而言就是检验method配置类，并把额外的一些信息放入attributes中，逻辑如下。

1. 检验method配置类的配置，当其isReturn属性设置时，onreturn，onthrow属性之1，必须要被设置
2. 构建onReturnMethodKey，并尝试将onReturnMethodKey对应的方法放入attributes中
3. 构建onThrowMethodKey，并尝试将onThrowMethodKey对应的方法放入attributes中
4. 构建onInvokeMethodKey，并尝试将onInvokeMethodKey对应的方法放入attributes中

----------

最后一点的说明也就是下面的两行代码:

	StaticContext.getSystemContext().putAll(attributes); 
	ref = createProxy(map);

这个就是真正开始引用的地方了。

###  消费方服务入口之createProxy ###

----------

在该方法里，将会为相应的rpc接口创建相应的代理，从而在执行的时候调用远程实现，具体我们慢慢道来。

	private T createProxy(Map<String, String> map) {

			//1.对（injvm）标记进行处理
	
	        //2.对（isJvmRefer临时变量）进行处理
	      
			//3.对 (check)标记进行处理

			//4.创建服务代理
	
	}

创建代理基本就是这个4点过程了。但是就代码本身而言，逻辑还是比较复杂的。

第一点injvm的处理:
        
        final boolean isJvmRefer;
        if (injvm != null) {
            isJvmRefer = injvm.booleanValue();
        } else {
            if (url != null && url.length() > 0) { //指定URL的情况下，不做本地引用
                isJvmRefer = false;
            } else {
                isJvmRefer = InjvmProtocol.getInjvmProtocol().isInjvmRefer(new URL("temp", "localhost", 0, map));
            }
        }
代码逻辑还是比较清晰的，当然这里是为第2点服务，变量isJvmRefer将在第二步处理

1. 尝试使用injvm赋值isJvmRefer变量(injvm有值)

2. 尝试使用url属性来决定isJvmRefer的值，url存在，isJvmRefer=false

3. 尝试使用入参map构建临时url获取相关键值来决定isJvmRefer的值


第一点isJvmRefer的处理:
        
	 if (isJvmRefer) {
            //对内引用
        } else {
            if (url != null && url.length() > 0) {
                String[] us = Constants.SEMICOLON_SPLIT_PATTERN.split(url);
                if (us != null && us.length > 0) {
                    for (String u : us) {
                        URL url = URL.valueOf(u);
                        if (url.getPath() == null || url.getPath().length() == 0) {
                            url = url.setPath(interfaceName);
                        }
                        if (Constants.REGISTRY_PROTOCOL.equals(url.getProtocol())) {
                            urls.add(url.addParameterAndEncoded(Constants.REFER_KEY, StringUtils.toQueryString(map)));
                        } else {
                            urls.add(ClusterUtils.mergeUrl(url, map));
                        }
                    }
                }
            } else {
                List<URL> us = loadRegistries(false);
                if (us != null && us.size() > 0) {
                    for (URL u : us) {
                        URL monitorUrl = loadMonitor(u);
                        if (monitorUrl != null) {
                            map.put(Constants.MONITOR_KEY, URL.encode(monitorUrl.toFullString()));
                        }
                        urls.add(u.addParameterAndEncoded(Constants.REFER_KEY, StringUtils.toQueryString(map)));
                    }
                }
                if (urls == null || urls.size() == 0) {
                    throw new IllegalStateException("No such any registry to reference " + interfaceName + " on the consumer " + NetUtils.getLocalHost() + " use dubbo version " + Version.getVersion() + ", please config <dubbo:registry address=\"...\" /> to your spring config.");
                }
            }

            if (urls.size() == 1) {
                invoker = refprotocol.refer(interfaceClass, urls.get(0));
            } else {
                List<Invoker<?>> invokers = new ArrayList<Invoker<?>>();
                URL registryURL = null;
                for (URL url : urls) {
                    invokers.add(refprotocol.refer(interfaceClass, url));
                    if (Constants.REGISTRY_PROTOCOL.equals(url.getProtocol())) {
                        registryURL = url; 
                    }
                }
                if (registryURL != null) {
                    URL u = registryURL.addParameter(Constants.CLUSTER_KEY, AvailableCluster.NAME);
                    invoker = cluster.join(new StaticDirectory(u, invokers));
                } else {
                    invoker = cluster.join(new StaticDirectory(invokers));
                }
            }
        }
对isJvmRefer的处理代码就比较长了，这个值决定是对内引用服务还是对外引用服务。对内引用比较简单，我们直接省略

### 对外引用 ###

----------

在服务篇我们提到过url(元信息)的重要性，当然这里也是，重点也就是构建url。我们来看逻辑

1. 使用**字符串url**配置的处理
	1. **字符串url**可能代表多个地址，使用；分割为数组
	2. 对于每个数组元素，直接构建url(元信息)，
	3. 对于元信息中不含path，均设定为interfaceName，
	4. 对于元信息为使用注册中心的添加键值对(refer,入参map转换)(与之对应的服务方(export：配置url的toString))
	5. 对于元信息不使用注册中心，将入参map合并到元信息中
2. 使用注册配置类构建url
	1. **loadRegistries(false)**对注册配置类校验和转换为列表，并处理其中每个元素
	2. 构建出监控url，加入map中
	3. 将map转换为url中的信息(refer:入参map转换)
	4. 检验最终的urls（对外引用了，urls列表不能为空）
3. 对于只有一个元素urls，直接引用获得invoker
4. 对于多个元素的urls，构建invoker列表
	1. 对每个元素，都进行引用获得invoker
	2. 尝试保留最后一个registryURL
	3. registryURL为空
		1. 直接构建inovker
	4. registryURL不为空
		1. 使用registryURL构建url，加入键值对("cluster",available)
		2. 直接构建invoker
		
以上逻辑基本上描述了整个对外引用的引用过程，当然网络的服务代码也是这里暴露的，我们稍后再说，先回到开头，将整个流程过掉先。

第三点check属性的处理，这里比较简单，代码如下:

		Boolean c = check;
        if (c == null && consumer != null) {
            c = consumer.isCheck();
        }
        if (c == null) {
            c = true; 
        }
        if (c && !invoker.isAvailable()) {
            throw new IllegalStateException("Failed to check the status of the service " + interfaceName + ". No provider available for the service " + (group == null ? "" : group + "/") + interfaceName + (version == null ? "" : ":" + version) + " from the url " + invoker.getUrl() + " to the consumer " + NetUtils.getLocalHost() + " use dubbo version " + Version.getVersion());
        }
        
代码也是很少，逻辑如下:

1. 尝试使用consumer配置类来配置check(check为null)
2. check=true(check为空的情况下）
3. 在check为true的情况下，会去验证服务的可用性

