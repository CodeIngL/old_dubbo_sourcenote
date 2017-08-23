## dubbo消费方 ##

前面我们已经简单的梳理了dubbo服务方的整个逻辑，现在我们从消费方的角度来观察下dubbo的另一半逻辑。

### 消费方复杂配置类 ###

----------

和我们之前对服务方的介绍一样，我们从消费方的复杂配置类入手，然后层层拨开dubbo消费方做的迷雾，如果读者对dubbo不是很熟悉，不能对服务方了然于胸的话，建议先看前几篇文章。

ReferenceConfig（消费方的入口）

- 消费者必须使用的配置类
		
	- 复杂的非配置属性：

			private static final Protocol refprotocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();

			private static final Cluster cluster = ExtensionLoader.getExtensionLoader(Cluster.class).getAdaptiveExtension();

			private static final ProxyFactory proxyFactory = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();

对于这些扩展配置项的代码，看过前几篇文章的童鞋应该相当的熟悉了，不熟悉的读者请阅读前面的文章。

现在我们把目光转移到消费方的相关启动代码上。

### 引用入口:get() ###

----------
引用入口:get();是dubbo使用api方式编程需要调用的方法，返回一个rpc接口的包装，通过返回值，开发者就可以黑盒的方式进行rpc调用了。源码如下:

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

		//4. 

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

在这里我们先采用注释的策略介绍整个方法，然后将源码串入其中,原因自然和我们之前介绍服务方一样:方法内实现逻辑代码比较长。

### 第1点的介绍 ###

---
第一点也就是，对初始化标志进行校验。代码如下:

     if (initialized) {
          return;
     }
     initialized = true;

初始化的过得服务引用自然需要一个标志来说明其初始化，这一点详细读者很容易理解。

### 第2点的介绍 ###

---
第二点也就是，对（interfaceName:必填）接口名字配置项校验。代码如下:

    //检验接口名(接口名是必填的配置项)
    if (interfaceName == null || interfaceName.length() == 0) {
        throw new IllegalStateException("<dubbo:reference interface=\"\" /> interface not allow null!");
    }

interfaceName属性的含义代表的意义，读者应该很熟悉了，和服务方一样，其代表了引用的接口。

### 第3点的介绍 ###

---
第3点也就是， 对 (consumer:可选)**简单配置类**进行校验。代码如下:

    if (consumer == null) {
        consumer = new ConsumerConfig();
    }
    appendProperties(consumer);
参照源码的童鞋可能看到是checkxxx，这里我将其实现贴出来，细心读者应该没有这样的疑惑。
这里和服务方一致，对于可选，总是能在用户不配置的情况下，自动创建出来，当然创建出来的对象的属性的填充自然交给了我们熟悉的方法**appendProperties**

### 第4点的介绍 ###

---
第4点也就是，使用**appendProperties**完成自己本身基本属性的设置。代码如下:

    appendProperties(this);
本身配置类某些基本属性还是需要填充或者设置，同样和服务方一致。

### 第5点的介绍 ###

---
第5点也就是，对(generic：可选)尝试设置。代码如下:

    if (generic == null) {
        if (consumer != null) {
           setGeneric(consumer.getGeneric());
        }
    }
这一点也很简单，当复杂配置类的某些属性不存在时，可以采用其持有的配置类中获取，同样这里和服务方类似

### 第6点的介绍 ###]

---
第6点也就是，对(generic：可选)进行处理。代码如下:

    if (ProtocolUtils.isGeneric(generic)) {
        interfaceClass = GenericService.class;
    } else {
        interfaceClass = Class.forName(interfaceName, true, Thread.currentThread().getContextClassLoader());
        checkInterfaceAndMethods(interfaceClass, methods);
    }
自然这里的逻辑也不是特别的复杂，对于泛化调用，interfaceClass自然是GenericService。同时其interfaceName是*。对于不是泛化调用，尝试使用interfaceName获得实际的类类型，同时还检查了interfaceClass和methods这个代表了方法配置的配置项，这一点我们等下单独来说。

### 第7点的介绍 ###

---
第7点也就是，尝试从系统以及配置文件中获得信息来构建url字符串的说明。相关代码如下:

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
            fis = new FileInputStream(new File(resolveFile));
            properties.load(fis);
            if (null != fis) fis.close();
            resolve = properties.getProperty(interfaceName);
        }
    }
    if (resolve != null && resolve.length() > 0) {
        url = resolve;
    }

这里就比较重要了，我们需要展开在这里的逻辑介绍，首先消费方持有一个String型的url(可选；下文我们统称**字符串url**)，这里的逻辑就是为了尝试获取这个字符串url的配置值。

1. 尝试使用属性interfaceName值做为key从系统环境中直接查找其值。
	1. 如果存在直接作为字符串url的值
2. 尝试从配置文件中获得url，使用属性interfaceName值做为key从配置文件对应的映射中获得**字符串url**。那么配置文件如何获得?
	1. 尝试通过使用dubbo.resolve.file做为key在系统中查找其文件路径
	2. 尝试使用用户目录下的dubbo-resolve.properties作为配置文件，获得其绝对路径


### 第8点的说明 ###

---
第8点也就是，尝试从嵌套的简单配置中，完成配置的转移，即完成本身的配置类属性配置。代码如下:

    if (consumer != null) {
        if (application == null) {
            application = consumer.getApplication();
        }
        if (module == null) {
            module = consumer.getModule();
        }
        if (registries == null) {
            registries = consumer.getRegistries();
        }
        if (monitor == null) {
            monitor = consumer.getMonitor();
        }
    }
    if (module != null) {
        if (registries == null) {
            registries = module.getRegistries();
        }
        if (monitor == null) {
            monitor = module.getMonitor();
        }
    }
    if (application != null) {
        if (registries == null) {
            registries = application.getRegistries();
        }
        if (monitor == null) {
            monitor = application.getMonitor();
        }
    }

对于这里，相信读者是否有很强烈的熟悉感，不错，在服务方的暴露代码里，我们基本上能看到一模一样的配置。因此这里就不详细说明了，忘记的童鞋，请阅读之前的文章。

### 第9点的介绍 ###

---
第9点也就是，对 (application:可选)**简单配置类**进行校验。代码如下:

    checkApplication();
这一点也就更不用说了，简单至极。

### 第10点的介绍 ###

---
第10点也就是，对属性local和stub的的校验，并生成mock。代码如下:

    protected void checkStubAndMock(Class<?> interfaceClass) {
        if (ConfigUtils.isNotEmpty(local)) {
            Class<?> localClass = ConfigUtils.isDefault(local) ? ReflectUtils.forName(interfaceClass.getName() + "Local") : ReflectUtils.forName(local);
            if (!interfaceClass.isAssignableFrom(localClass)) {
                //省略代码
            }
            ReflectUtils.findConstructor(localClass, interfaceClass);
        }
        if (ConfigUtils.isNotEmpty(stub)) {
            Class<?> localClass = ConfigUtils.isDefault(stub) ? ReflectUtils.forName(interfaceClass.getName() + "Stub") : ReflectUtils.forName(stub);
            if (!interfaceClass.isAssignableFrom(localClass)) {
                //省略代码
            }
            ReflectUtils.findConstructor(localClass, interfaceClass);
        }
        if (ConfigUtils.isNotEmpty(mock)) {
            if (mock.startsWith(Constants.RETURN_PREFIX)) {
                String value = mock.substring(Constants.RETURN_PREFIX.length());
                MockInvoker.parseMockValue(value);
            } else {
                Class<?> mockClass = ConfigUtils.isDefault(mock) ? ReflectUtils.forName(interfaceClass.getName() + "Mock") : ReflectUtils.forName(mock);
                if (!interfaceClass.isAssignableFrom(mockClass)) {
                    //省略代码
                }
                mockClass.getConstructor(new Class<?>[0]);
            }
        }
    }

这里也是一行代码，和上面一样，粘贴了其实现，做的事情也是比较的简单，对local，stub，mock三个属性进行校验，如果开发人员配置了的话。 

1. 对local进行校验
    1. 尝试加载其代表的类型到vm中，检测其构造函数
2. 对stub进行校验
    1. 同上
3. 对mock进行校验
    1. 对于mock配置为片段代码，则使用MockInvoker去解析
    2. 否则同上local和mock

### 第11点的介绍 ###

---

第11点也就是，构建关键的url（元信息）的参数映射，代码如下：

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

这里也和我们之前说的服务端代码十分的类型，甚至基本一样，总得来说，目的是构建url元信息的参数键值对。代码逻辑如下:

- 放入（side：consumer）代表是消费方（服务方为(side:provider)
- 放入（dubbo：2.0.0）版本信息(服务方一致）
- 放入（timestamp：当前时间）（服务方一致）
- 放入（pid：应用pid）进程信息（服务方一致）
- 对非通用接口处理(服务方一致）
    1. 放入调整的版本信息
    2. 使用Wrapper包装对应的class,并获得其方法集合
    3. 根据方法的大小做不同的操作
- 放入（interface：interfaceName）
- 将application配置类信息放入map中（服务方一致）
- 将module配置类信息放入map中（服务方一致）
- 将consumer配置类信息放入map中，前缀default（服务方式放入provider配置类)
- 将this配置类信息放入map中(服务方一致)
- 遍历methods配置类列表，将每个method的配置信息放入map中，前缀方法名(服务方一致)
    对每个method配置类构建，key:"方法名.retry"的键做处理，并删除该键，如果该键的值等于false，将其转换为（"方法名.retries":"0"）放入(服务方一致)

基本上和服务方的处理一致，当然我们需要说明其不同的地方：

1. 这里构建了一个映射attributes
	1. 使用**appendAttributes**对每一个method配置类的信息放入，前缀为group(对应值)/interface(对应值):version。
	2. 使用**checkAndConvertImplicitConfig**对每一个方法，以及map和attributes进行了处理。

### appendParameters与appendAttributes之间的事情 ###

---
appendAttributes和appendParameters很相像，但做的事情却少多了

	protected static void appendAttributes(Map<Object, Object> parameters, Object config, String prefix) {
        for (Method method : methods) {
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
        }
    }
以上就是其方法的代码实现,读者应该能看出其和appendParameter的共同值处，这里其逻辑如下:

1. 处理基本类型（基本配置属性）的get或者is方法，尝试从方法上获得注解@Parameter
1. 排除掉返回值是Object或者有注解并且注解attribute为false的这些方法
2. 构建key（将存入map:入参），有注解尝试使用注解的key的有效值，否则方法名中提取，例如getCodelDemo，提取为codel.demo
3. 放射获得基本配置属性值（不一定是String），对于合法的值，将会和key形成组合放入map中，合法值的处理
    1. 如果前缀存在，为key添加前缀
    2. 将key值和处理后的基本属性值放入map中

总之就是构建了一个key和配置类某个属性值的集合，在appendParameter中构建的是一个key和配置类某个属性的string值集合。

### 插曲之checkAndConvertImplicitConfig ###

---
介绍了上面其中之一的方法，我们再来看另一个函数的操作，也就是该节标题。

	private static void checkAndConvertImplicitConfig(MethodConfig method, Map<String, String> map, Map<Object, Object> attributes) {
        if (Boolean.FALSE.equals(method.isReturn()) && (method.getOnreturn() != null || method.getOnthrow() != null)) {
            throw new IllegalStateException("method config error : return attribute must be set true when onreturn or onthrow has been setted.");
        }
        String onReturnMethodKey = StaticContext.getKey(map, method.getName(), Constants.ON_RETURN_METHOD_KEY);
        Object onReturnMethod = attributes.get(onReturnMethodKey);
        if (onReturnMethod != null && onReturnMethod instanceof String) {
            attributes.put(onReturnMethodKey, getMethodByName(method.getOnreturn().getClass(), onReturnMethod.toString()));
        }
        String onThrowMethodKey = StaticContext.getKey(map, method.getName(), Constants.ON_THROW_METHOD_KEY);
        Object onThrowMethod = attributes.get(onThrowMethodKey);
        if (onThrowMethod != null && onThrowMethod instanceof String) {
            attributes.put(onThrowMethodKey, getMethodByName(method.getOnthrow().getClass(), onThrowMethod.toString()));
        }
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

### 最后一点的介绍 ###

---
最后一点的说明也就是下面的两行代码:

	StaticContext.getSystemContext().putAll(attributes); 
	ref = createProxy(map);

这个就是真正开始引用的地方了。

###  消费方服务入口之createProxy ###

---
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


第二点isJvmRefer的处理:
        
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

---
在服务篇我们提到过url(元信息)的重要性，当然这里也是，重点也就是构建url。我们来看逻辑：

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
            //省略部分代码
        }
        
代码也是很少，逻辑如下:

1. 尝试使用consumer配置类来配置check(check为null)
2. check=true(check为空的情况下）
3. 在check为true的情况下，会去验证服务的可用性

第四点创建服务代理

	return (T) proxyFactory.getProxy(invoker);

这里的代理的创建，请见dubbo之创建代理一文。

### 小结 ###

---
文章到这里，基本上介绍了下dubbo消费方的逻辑，当然还有很多细节没有描述到位，比如具体的网络暴露还是没有涉及。下一篇dubbo消费引用我们将展开细致的描述。

