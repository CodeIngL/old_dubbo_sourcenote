## dubbo 服务导出 ##

上一篇文章中，我们通过服务提供者的复杂配置类进行了展开，介绍了dubbo配置模块的运行流程。
在本篇文章中，我会通过服务方的复杂配置类来进行开启我们服务导出之旅。

### ServiceConfig服务导出 ###

---
在dubbo杂点中，我们已经指出了ServiceConfig是服务导出类。现在我们来看下是这个复杂配置类到底是如何进行服务导出的。

### 服务提供者入口:ServiceConfig.export() ###

---
该方法是dubbo导出服务的入口，其在第一篇中引用官方api编码后，应用启动后触发。

	public synchronized void export() {
        if (provider != null) {
            if (export == null) {
                export = provider.getExport();
            }
            if (delay == null) {
                delay = provider.getDelay();
            }
        }
        if (export != null && ! export.booleanValue()) {
            return;
        }
        if (delay != null && delay > 0) {
            Thread thread = new Thread(new Runnable() {
                public void run() {
                    Thread.sleep(delay);
                    doExport();
                }
            });
            thread.setDaemon(true);
            thread.setName("DelayExportServiceThread");
            thread.start();
        } else {
            doExport();
        }
    }

上述就是整个方法代码，代码很短，一眼见明。
1. export（暴露标志属性）为空，尝试使用provider的export配置(provider不为空的情况下)。
2. delay（延迟属性）为空，尝试使用provider的delay配置(provider不为空的情况下)。
3. 对暴露标志属性进行检查，对于已导出就直接返回。
4. 对延迟属性进行处理，是否开启线程来延迟导出。

以上就是方法的逻辑介绍，我们看到的真正的处理被隐藏在方法**doExport()**中

### 服务提供者导出逻辑:ServiceConfig.doExport() ###

---
该方法是服务导出的进一步逻辑处理。   
之前的文章中，我们提到ServiceConfig还是个**复杂配置类**，意味着本质上还是配置类，因此相关配置属性校验是必不可少的。    
**doExport()**篇幅上其实也仅仅做了属性的校验和设置，真正的处理被其他方法隐藏，我们稍后再说，先看代码逻辑:

    protected synchronized void doExport() {
        
        //1.对（exported）导出标记和（unexported）未导出标记进行校验

        //2.对（interfaceName:必填）接口名字配置项校验
        
        //3.对 (provider:可选)**简单配置类**进行校验

        //4.尝试从嵌套的简单配置中，完成配置的转移，即完成本身的配置类属性配置
        //----application为空，尝试从provider配置类中获取
        //----module为空，尝试从provider配置类中获取
        //----registries为空，尝试从provider，module，application配置类中获取
        //----monitor为空，尝试从provider，module，application配置类中获取
        //----protocols为空，尝试从provider配置类中获取

        //5.对配置项ref进行检验
        //----通用接口，属性**interfaceClass**为GenericService，generic为true
        //----非通用接口，属性**interfaceClass**为**interfaceName**的类类型，generic为false
                //interfaceClass必须存在，且是接口，接口必须包含配置类methods的全部方法（如果methods配置存在的话）
                //ref必须存在，并实现interfaceClass
        
        //6.对属性local和stub的处理
                //配置local=true，本地必须存在${interfaceName}Local的实现,且是interfaceClass的实现
                //配置stub=true，本地必须存在${interfaceName}Socal的实现,且是interfaceClass的实现

        //7.对 (application:可选)**简单配置类**进行校验

        //8.对 (registries:可选)**简单配置类的List**进行校验

        //9.对 (protocols:可选)**简单配置类的List**进行校验

        //10.使用**appendProperties**完成自己本身基本属性的设置

        //11.对属性local和stub的的校验，并生成mock

        //12.对属性path进行设置，为空，赋值为配置项interfaceName

        doExportUrls();
    }
以上就是方法的逻辑，都是一些配置项的校验和生成，真正的服务导出相关的逻辑隐藏在最后一行代码中**doExportUrls()**。

阅读仔细的读者，可能注意到了很多个可选和简单配置类的字眼。这个十分的重要，这些都是配置类，某些配置类可以不配置的，因而可选。但这里还做了很多事情，这个就比较重要的，他提供了我们如何灵活的改造配置项的方式。

这里总共有四个可选，也就是3，7，8，9。我们来探究下其做了什么事。

第3点(provider:可选):

	private void checkProvider() {
        if (provider == null) {
            provider = new ProviderConfig();
        }
        appendProperties(provider);
    }

第7点(application:可选):

	protected void checkApplication() {
        if (application == null) {
            String applicationName = ConfigUtils.getProperty("dubbo.application.name");
            if (applicationName != null && applicationName.length() > 0) {
                application = new ApplicationConfig();
            }
        }
        if (application == null) {
            //省略了异常代码
        }
        appendProperties(application);


        String wait = ConfigUtils.getProperty(Constants.SHUTDOWN_WAIT_KEY);
        if (wait != null && wait.trim().length() > 0) {
            System.setProperty(Constants.SHUTDOWN_WAIT_KEY, wait.trim());
        } else {
            wait = ConfigUtils.getProperty(Constants.SHUTDOWN_WAIT_SECONDS_KEY);
            if (wait != null && wait.trim().length() > 0) {
                System.setProperty(Constants.SHUTDOWN_WAIT_SECONDS_KEY, wait.trim());
            }
        }
    }

第8点(registries:可选):
	
	protected void checkRegistry() {
        // 兼容旧版本
        if (registries == null || registries.size() == 0) {
            String address = ConfigUtils.getProperty("dubbo.registry.address");
            if (address != null && address.length() > 0) {
                registries = new ArrayList<RegistryConfig>();
                String[] as = address.split("\\s*[|]+\\s*");
                for (String a : as) {
                    RegistryConfig registryConfig = new RegistryConfig();
                    registryConfig.setAddress(a);
                    registries.add(registryConfig);
                }
            }
        }
        if ((registries == null || registries.size() == 0)) {
            //省略了异常代码
        }
        for (RegistryConfig registryConfig : registries) {
            appendProperties(registryConfig);
        }
    }

第9点(protocols:可选):

	private void checkProtocol() {
        if (protocols == null || protocols.size() == 0) {
            setProtocol(new ProtocolConfig());
        }
        for (ProtocolConfig protocolConfig : protocols) {
            if (StringUtils.isEmpty(protocolConfig.getName())) {
                protocolConfig.setName("dubbo");
            }
            appendProperties(protocolConfig);
        }
    }

上面是3，7，8，9涉及的全部代码。稍微阅读，我们好像发现了很多共性，比如不存在则新建，比如**appendProperties**方法。当然也是有很多不同的地方。

第3点provider的新建，没有任何条件，不存在直接新建

第7点application的新建

1. 存在dubbo.application.name配置项，才能新建
2. 尝试取得dubbo.service.shutdown.wait的配置项，并对其值去两端空格后，重新写回，
3. 在上面的尝试失败后，尝试取得dubbo.service.shutdown.wait.seconds的配置项，并对其值去两端空格后，重新写回，


第8点registries的新建，这个不一样是因为其是list

1. 存在dubbo.registry.address配置项，才能新建
2. 获得配置项的值，使用|符号分割地址项，形成数组
3. 为每一个数组元素，新建RegistryConfig，并设定地址为相应的数组元素


第9点protocols的新建，这个不一样是因为其是list

1. 新建一个ProtocolConfig，并转换为list
2. 任何的ProtocolConfig配置类，无论是否是新建的，名字不存在，其默认名字为dubbo


上面是这些可选配置项，新建的一点点区别，可选项，我们看出来，有些还是要一些配置项的。provider和protocols是完全可选的。

同时在上面我们多次提到了xxx配置项，这些配置项提供了我们灵活的口子，也就是**ConfigUtils.getProperty**方法。这个方法在
**appendProperties**也多次用到。而这个**appendProperties**除了这里的共性以外，第10点的实现也是使用它。我们先介绍这个方法，因为
这个方法提供了我们灵活的设置配置类，我简称最后的后门。

#### 重点方法**appendProperties** ####
---
上面我们多次提到了**appendProperties**方法。该方法与配置类基本属性设置相关，是一个重点方法，也是我们进行扩展时，可以考虑的扩展点。
我们先看其代码:

	protected static void appendProperties(AbstractConfig config) {
        if (config == null) {
            return;
        }
        String prefix = "dubbo." + getTagName(config.getClass()) + ".";

        Method[] methods = config.getClass().getMethods();
        for (Method method : methods) {
            String name = method.getName();
            if (name.length() > 3 && name.startsWith("set") && Modifier.isPublic(method.getModifiers())
                    && method.getParameterTypes().length == 1 && isPrimitive(method.getParameterTypes()[0])) {
                String suffix = StringUtils.camelToSplitName(name.substring(3, 4).toLowerCase() + name.substring(4), "-");
                String value = null;
                if (config.getId() != null && config.getId().length() > 0) {
                    String pn = prefix + config.getId() + "." + suffix;
                    value = System.getProperty(pn);
                    if (!StringUtils.isBlank(value)) {
                        logger.info("Use System Property " + pn + " to config dubbo");
                    }
                }
                if (value == null || value.length() == 0) {
                    String pn = prefix + suffix;
                    value = System.getProperty(pn);
                    if (!StringUtils.isBlank(value)) {
                        logger.info("Use System Property " + pn + " to config dubbo");
                    }
                }
                if (value == null || value.length() == 0) {
                    Method getter;
                    try {
                        getter = config.getClass().getMethod("get" + name.substring(3), new Class<?>[0]);
                    } catch (NoSuchMethodException e) {
                        try {
                            getter = config.getClass().getMethod("is" + name.substring(3), new Class<?>[0]);
                        } catch (NoSuchMethodException e2) {
                            getter = null;
                        }
                    }
                    if (getter != null) {
                        if (getter.invoke(config, new Object[0]) == null) {
                            if (config.getId() != null && config.getId().length() > 0) {
                                value = ConfigUtils.getProperty(prefix + config.getId() + "." + suffix);
                            }
                            if (value == null || value.length() == 0) {
                                value = ConfigUtils.getProperty(prefix + suffix);
                            }
                            if (value == null || value.length() == 0) {
                                String legacyKey = legacyProperties.get(prefix + suffix);
                                if (legacyKey != null && legacyKey.length() > 0) {
                                    value = convertLegacyValue(legacyKey, ConfigUtils.getProperty(legacyKey));
                                }
                            }
                        }
                    }
                }
                if (value != null && value.length() > 0) {
                    method.invoke(config, new Object[]{convertPrimitive(method.getParameterTypes()[0], value)});
                }
            }
        }
    }

以上就是方法的全部代码，这些代码看起来似乎很啰嗦的样子，其实就是做了一件事情，尝试对配置类的基本属性进行设置，使用系统中的变量，或者配置文件中加载的变量。逻辑是这样的:

1. 获得前缀(用来组装键值)，对于配置类XxxConfig或者Spring中配置类XxxBean,前缀等价为dubbo.xxx.

- 遍历所有基本属性的公开的set方法，从方法中获得后缀，例如方法setCodelDemo，后缀则为codel-demo,注意分割符为“-”
	1. 存在id属性,使用**前缀+id属性+后缀**作为key从系统获取。
	2. 不存在id属性,使用**前缀+id属性+后缀**作为key从系统获取。
	3. **忽略设置，该基本属性存在值，否则，继续下面**
	4. 存在id属性，使用**前缀+id属性+后缀**作为key参数**ConfigUtils.getProperty**获取，
	5. 不存在id属性，尝试使用**前缀+id属性+后缀**作为key参数**ConfigUtils.getProperty**获取。
	6. 尝试使用**前缀+后缀**作为key，在legacyProperties本地map转换后获得key1，使用key1作为参数在**ConfigUtils.getProperty**(猜测历史遗留问题)

小结:
> 对上面1，2点给我们的启示，无论我们是否配置了相关属性(例如在spring中配置)，一旦在系统中设置后，就有绝对的优先级（很多框架都有这样的做法)。
> 对于第3点引发出来的思考是如果我们已经配置了属性，剩下的操作将被忽略。 

这样方法appendProperties就说明完毕了，但是遗留了一个问题，也就是方法**ConfigUtils.getProperty**的行为。

#### ConfigUtils.getProperty获得配置信息 ####

---
上面如同appendProperties方法，我们提到了很多次ConfigUtils.getProperty方法。这个也是比较重要的方法，对开发者来说。

	public static String getProperty(String key) 
	
	public static String getProperty(String key, String defaultValue)

上面是ConfigUtils.getProperty的方法签名，其中第一项等价于第二项中defaultValue为null的情况。现在我们针对第二项方法展开:

	public static String getProperty(String key, String defaultValue) {
        String value = System.getProperty(key);
        if (value != null && value.length() > 0) {
            return value;
        }
        Properties properties = getProperties();
        return replaceProperty(properties.getProperty(key, defaultValue), (Map) properties);
    }
代码也很简单，也是首先尝试从系统配置中获得属性值，当没有值的时候，尝试从配置文件中获得。

#### ConfigUtils.getProperties()获取配置文件信息 ####

    public static Properties getProperties() {
        if (PROPERTIES == null) {
            synchronized (ConfigUtils.class) {
                if (PROPERTIES == null) {
                    String path = System.getProperty(Constants.DUBBO_PROPERTIES_KEY);
                    if (path == null || path.length() == 0) {
                        path = System.getenv(Constants.DUBBO_PROPERTIES_KEY);
                        if (path == null || path.length() == 0) {
                            path = Constants.DEFAULT_DUBBO_PROPERTIES;
                        }
                    }
                    PROPERTIES = ConfigUtils.loadProperties(path, false, true);
                }
            }
        }
        return PROPERTIES;
    }

以上就是如何获得配置文件，我们可看到，使用
1. dubbo.properties.file作为key，尝试在系统中获得配置文件路径
2. dubbo.properties.file作为key，尝试在环境变量中获得配置文件路径
3. 使用dubbo.properties.file作为配置文件路径名
4. 委托给ConfigUtils.loadProperties()加载配置文件;

### ConfigUtils.loadProperties()加载配置文件 ###
---
这是加载配置文件的核心方法，我们先看方法签名

----------

    public static Properties loadProperties(String fileName, boolean allowMultiFile, boolean optional)

参数有点多，先来解释下，第一个参数自然是文件名或者路径名了，第二个参数代表是否允许多个文件，第三个参数无用参数。
由于代码篇幅过长，但是没重要的技巧。我们这里以文字描述其逻辑:

1. 根据第一个参数是否以/开头来确定是否是路径，是则之间以文件的形式读取并返回。
2. 使用java URL形式读取，读取符号的资源，根据第二个参数来是否允许多个资源。 

回过头来我们看**getProperty**，现在就是返回最终的配置了，通过传递进来的key在从配置文件的内存map中获得相应value，
值得注意的是，如果这个value含有${xxx}占位，会尝试从优先系统配置，然后内存map中提取来代替占位，没有则用空串替代。
	
#### 再谈服务导出逻辑 ####

----------
上面说了这么多，已经离我们先前的服务导出逻辑很久了。当然我们搞清了这些配置类的可选性，已经要求，以及其的属性如何设置。接下来也就是第11，12点的说明了


1. 第11点的说明，其检测了三个配置属性，local，stub，mock。这三个属性其实也是很重要的，其中前两个我们之前提到过。
	1. local的配置，true，default，xxxxxx(类名)：前两者对应xxxLocal(类名)
	2. stub的配置，true，default，xxxxxx(类名)：前两者对应xxxStub(类名)
	1. mock的配置
		1. 以return的字符串开头，配置中直接返回return后 字符串（数据解析结果）
		2. true，default，xxxxxx(类名):前两者对应xxxMock(类名)
tip：这些类必须是该其interfaceClass属性的实现类

2. 第12点的说明，path配置属性没有配置的情况下，使用interfaceName来作为配置名，也就是服务的接口名。

#### 服务提供者入口:ServiceConfig.doExportUrls() ####
---
上面我们顺利完成了服务导出逻辑的描述，也说道真正的服务导出，是最后一行代码，也就是现在我们需要详细探究的地方。ServiceConfig.doExportUrls()该方法是export的核心逻辑委托。在上面的配置类的关系设置后，就会转入该方法执行。  
实现很简单：
	
	    private void doExportUrls() {
	        List<URL> registryURLs = loadRegistries(true);
	        //对每种协议都进行导出
	        for (ProtocolConfig protocolConfig : protocols) {
	            doExportUrlsFor1Protocol(protocolConfig, registryURLs);
	        }
	    }
代码很短，隐藏的东西很多，主要关注的有两个方法，loadRegistries和doExportUrlsFor1Protocol，都很重要，我们慢慢道来吧。

#### 将注册配置类转换为元信息（URL）：loadRegistries ####
---
该方法是很重要的方法，它的指针如标题所说，将注册配置类转换为url。首先看一下签名

	protected List<URL> loadRegistries(boolean provider)

说明，服务方调用参数为true，消费方调用参数为false。接下来上源码:

	 protected List<URL> loadRegistries(boolean provider) {
        checkRegistry();
        List<URL> registryList = new ArrayList<URL>();
        for (RegistryConfig config : registries) {
            String address = config.getAddress();
            if (address == null || address.length() == 0) {
                address = Constants.ANYHOST_VALUE;
            }
            String sysaddress = System.getProperty("dubbo.registry.address");
            if (sysaddress != null && sysaddress.length() > 0) {
                address = sysaddress;
            }
            if (!RegistryConfig.NO_AVAILABLE.equalsIgnoreCase(address)) {
                Map<String, String> map = new HashMap<String, String>();
                appendParameters(map, application);
                appendParameters(map, config);
                map.put("path", RegistryService.class.getName());
                map.put("dubbo", Version.getVersion());
                map.put("timestamp", String.valueOf(System.currentTimeMillis()));
                map.put("pid", String.valueOf(ConfigUtils.getPid()));
                List<URL> urls = UrlUtils.parseURLs(address, map);
                for (URL url : urls) {
                    url = url.addParameter(Constants.REGISTRY_KEY, url.getProtocol());
                    url = url.setProtocol(Constants.REGISTRY_PROTOCOL);
                    if ((provider && url.getParameter(Constants.REGISTER_KEY, true))) {
                        registryList.add(url);
                    }
                    if (!provider && url.getParameter(Constants.SUBSCRIBE_KEY, true)) {
                        registryList.add(url);
                    }
                }
            }
        }
        return registryList;
    }

对应URL的方法生成loadRegistries(true)的说明:

1. 校验registry，也就是前面的第8点，为什么校验因为消费端代码之前没有校验过。
2. 获得“注册中心”地址:address
	1. 尝试使用registry配置类的地址配置项address，为空，使用0.0.0.0（开发人员自己配置了注册配置类，但是没有配置地址的情况下,如果配置注册配置类，系统自动生成一定会有地址)
	3. 尝试获得系统dubbo.registry.address值（最高优先权）和第1点一样
3. 对有效的注册地址不是（N/A）进行处理（需要注册中心）（非常非常非常重要）

第三点非常的重要，涉及的逻辑也十分的复杂，因此我们单独说明

	1. 生成相关键信息值对map，这个map中的元素很重要:
		1. 将application配置类信息放入map中，使用**appendParameters**;
		2. 将registry配置类信息放入map中，使用**appendParameters**;
		3. 放置key:path,value:com.alibaba.dubbo.registry.RegistryService
		4. 放置key:dubbo,value:2.0.0(Version.getVersion())
		5. 放置key:timestamp，value:当前时间
		6. 放置key:pid，value:ConfigUtils.getPid()(应用pid)
	2. 使用配置address和得到map结合生成url列表,使用**UrlUtils.parseURLs**
	3. 处理url列表中的每一个url
		1. 放置key:registry,value:URL的协议(url.getProtocol())
		2. 设置URL的协议为registry,(url.setProtocol()),隐藏了真实的注册中心协议，用registry来对外统一代表这是个注册中心，特定的注册中心的协议则转移到了参数registry对应的值上。
		3. 根据方法参数和url中的属性来确定是否加入要返回的registryUrl列表
			1. 入参为true，url需含有register(形式上)，默认为true(服务方)
			2. 入参为false，url需含有subscribe(形式上)，默认为true(消费方)
这样之后就是返回注册的URL列表(registryList)

细心的读者肯定发现了被加粗的方法，这些加粗的的确是比较重要的方法。我们一一道来

#### 插曲之appendParameters方法 ####
---
该方法是就是被加粗的方法之一，除了上面，dubbo源码很多地方也用到了这个方法，和**appendProperties**相似，
appendParameters方法是讲配置类的信息提取出来，而**appendProperties**则相反。现在我们就此方法进行详细说明:

    protected static void appendParameters(Map<String, String> parameters, Object config)

    protected static void appendParameters(Map<String, String> parameters, Object config, String prefix)
以上是同名方法签名，其中方法一等价于方法二的第三个入参为空，接下来我们看一下方法二。


	 protected static void appendParameters(Map<String, String> parameters, Object config, String prefix) {
        if (config == null) {
            return;
        }
        Method[] methods = config.getClass().getMethods();
        for (Method method : methods) {
            try {
                String name = method.getName();
                if ((name.startsWith("get") || name.startsWith("is"))
                        && !"getClass".equals(name)
                        && Modifier.isPublic(method.getModifiers())
                        && method.getParameterTypes().length == 0
                        && isPrimitive(method.getReturnType())) {
                    Parameter parameter = method.getAnnotation(Parameter.class);
                    if (method.getReturnType() == Object.class || parameter != null && parameter.excluded()) {
                        continue;
                    }
                    int i = name.startsWith("get") ? 3 : 2;
                    String prop = StringUtils.camelToSplitName(name.substring(i, i + 1).toLowerCase() + name.substring(i + 1), ".");
                    String key;
                    if (parameter != null && parameter.key() != null && parameter.key().length() > 0) {
                        key = parameter.key();
                    } else {
                        key = prop;
                    }
                    Object value = method.invoke(config, new Object[0]);
                    String str = String.valueOf(value).trim();
                    if (value != null && str.length() > 0) {
                        if (parameter != null && parameter.escaped()) {
                            str = URL.encode(str);
                        }
                        if (parameter != null && parameter.append()) {
                            String pre = (String) parameters.get(Constants.DEFAULT_KEY + "." + key);
                            if (pre != null && pre.length() > 0) {
                                str = pre + "," + str;
                            }
                            pre = (String) parameters.get(key);
                            if (pre != null && pre.length() > 0) {
                                str = pre + "," + str;
                            }
                        }
                        if (prefix != null && prefix.length() > 0) {
                            key = prefix + "." + key;
                        }
                        parameters.put(key, str);
                    } else if (parameter != null && parameter.required()) {
                        throw new IllegalStateException(config.getClass().getSimpleName() + "." + key + " == null");
                    }
                }
                else if ("getParameters".equals(name)
                        && Modifier.isPublic(method.getModifiers())
                        && method.getParameterTypes().length == 0
                        && method.getReturnType() == Map.class) {
                    Map<String, String> map = (Map<String, String>) method.invoke(config, new Object[0]);
                    if (map != null && map.size() > 0) {
                        String pre = (prefix != null && prefix.length() > 0 ? prefix + "." : "");
                        for (Map.Entry<String, String> entry : map.entrySet()) {
                            parameters.put(pre + entry.getKey().replace('-', '.'), entry.getValue());
                        }
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
    }

以上就是方法的全部代码，和**appendProperties**基本上就是相反操作，当然细节是不同的。我们一一道来

1. 处理基本类型（基本配置属性）的get或者is方法，尝试从方法上获得注解@Parameter
	1. 忽略返回值为Object或者有注解并且注解excluded为true的这些方法
	2. 获得key（存入入参parameters中的）
        1. 尝试使用注解的key值
        2. 从方法名中提取值，例如getCodelDemo，提取为codel.demo
	3. 构建key对应的value
        1. 放射获得基本配置属性值,并string化，尝试去掉空格
		2. 含有注解，且注解escaped为true，进行对该值URL方式编码
		3. 含有注解，且注解append为true，尝试先从map中获得值，依次使用default+key，key做为键取得值，和当前值进行追加，也就是多个键一样的值进行合并成统一个字符串。
		4. 如果前缀存在，为key添加前缀
		5. 将key和value放入入参parameters中
2. 处理配置类的getParameters并且返回值是Map的方法
	1. 遍历处理属性map，将其key转换为前缀+key（前缀有的话）的形式，并将key中-用.代替，将key和value加入入参parameters中

appendParameters方法到这里就说明完毕了，值得说明的是对于第二点的处理中的map，其实对应了dubbo的spring版的配置的agrument配置类。

#### 插曲之UrlUtils.parseURLs方法 ####

----------
这个方法可了不得，学好dubbo，你必须学好其的元信息是如何生成的，这样查错的时候，一眼简明。

	public static List<URL> parseURLs(String address, Map<String, String> defaults)

这个是方法签名，两个参数，地址信息(地址信息当然也可以自身携带参数信息，如同http的get方式携带参数)和其他信息的map组合

	public static List<URL> parseURLs(String address, Map<String, String> defaults) {
        //省略入参address检查
        String[] addresses = Constants.REGISTRY_SPLIT_PATTERN.split(address);
        List<URL> registries = new ArrayList<URL>();
        for (String addr : addresses) {
            registries.add(parseURL(addr, defaults));
        }
        return registries;
    }
我们可以看到传递进来的地址参数还能对应多个地址呢，分割地址符号为|或者；

#### 插曲之UrlUtils.parseURL方法 ####

---
这个方法才是真正的生成当url的方法，当然方法地址可以有很多形式的写法。我们慢慢道来

    public static URL parseURL(String address, Map<String, String> defaults) {
        if (address == null || address.length() == 0) {
            return null;
        }
        String url;
        if (address.indexOf("://") >= 0) {
            url = address;
        } else {
            String[] addresses = Constants.COMMA_SPLIT_PATTERN.split(address);
            url = addresses[0];
            if (addresses.length > 1) {
                StringBuilder backup = new StringBuilder();
                for (int i = 1; i < addresses.length; i++) {
                    if (i > 1) {
                        backup.append(",");
                    }
                    backup.append(addresses[i]);
                }
                url += "?" + Constants.BACKUP_KEY + "=" + backup.toString();
            }
        }
        boolean changed = false;
        URL u = URL.valueOf(url);
        String protocol = u.getProtocol();
        String username = u.getUsername();
        String password = u.getPassword();
        String host = u.getHost();
        int port = u.getPort();
        String path = u.getPath();
        Map<String, String> parameters = new HashMap<String, String>(u.getParameters());
        Map<String, String> defaultParameters = defaults == null ? null : new HashMap<String, String>(defaults);
        String defaultProtocol = null, defaultUsername = null, defaultPassword = null, defaultPath = null;
        int defaultPort = 0;
        if (defaultParameters != null) {
            defaultParameters.remove("host");
            defaultProtocol = defaultParameters.remove("protocol");
            defaultUsername = defaultParameters.remove("username");
            defaultPassword = defaultParameters.remove("password");
            defaultPort = StringUtils.parseInteger(defaultParameters.remove("port"));
            defaultPath = defaultParameters.remove("path");
            for (Map.Entry<String, String> entry : defaultParameters.entrySet()) {
                String key = entry.getKey();
                String defaultValue = entry.getValue();
                if (StringUtils.isNotEmpty(defaultValue) && StringUtils.isEmpty(parameters.get(key))) {
                    changed = true;
                    parameters.put(key, defaultValue);
                }
            }
        }
        if ((StringUtils.isEmpty(protocol))) {
            changed = true;
            if (StringUtils.isEmpty(defaultProtocol)) {
                defaultProtocol = "dubbo";
            }
            protocol = defaultProtocol;
        }
        if ((StringUtils.isEmpty(username)) && StringUtils.isNotEmpty(defaultUsername)) {
            changed = true;
            username = defaultUsername;
        }
        if ((StringUtils.isEmpty(password)) && StringUtils.isNotEmpty(defaultPassword)) {
            changed = true;
            password = defaultPassword;
        }
        if (port <= 0) {
            if (defaultPort > 0) {
                changed = true;
                port = defaultPort;
            } else {
                changed = true;
                port = 9090;
            }
        }
        if ((StringUtils.isEmpty(path)) && StringUtils.isNotEmpty(defaultPath)) {
            changed = true;
            path = defaultPath;
        }
        if (changed) {
            u = new URL(protocol, username, password, host, port, path, parameters);
        }
        return u;
    }
代码老长老长，但是做的事情还是比较简单的:
1. 对于url能够够找到://这样的符号的，就算你是合法地址了(当然程序依旧可能出错的)。
2. 对于地址拆分，使用，分割。分割后的数组第一个元素，其他作为backup追加到后面
3. 使用地址构建一个URL，使用方法**URL.valueOf(url)**，然后补上相关信息
4. 移除map中的 protocol，username，password，path, port，host元素,取得默认值，剩余的map的kv用来补充url的参数信息，前面的都是特别的信息。
5. 从url中获得 protocol，username，password，host，port，path，和其他信息的parameters(map)
6. 在url中上述配置不存在的情况下，使用map中的参数信息来补充，其他信息也是一样,并设定修改标记。
7. 返回新的url，如果修改过的话

这里的逻辑还是比较简单的，简单来说就是构建url的操作，其中还有一个值得注意的地方，也就是字符串直接生成URL类型的，也就是上面的第3点加粗的地方。

#### 插曲之UrlUtils.valueOf方法 ####

--- 
该方法将解析字符串到URL格式。

	 public static URL valueOf(String url) {
        //入参的校验
        String protocol = null;
        String username = null;
        String password = null;
        String host = null;
        int port = 0;
        String path = null;
        Map<String, String> parameters = null;
        int i = url.indexOf("?"); // seperator between body and parameters
        if (i >= 0) {
            String[] parts = url.substring(i + 1).split("\\&");
            parameters = new HashMap<String, String>();
            for (String part : parts) {
                part = part.trim();
                if (part.length() > 0) {
                    int j = part.indexOf('=');
                    if (j >= 0) {
                        parameters.put(part.substring(0, j), part.substring(j + 1));
                    } else {
                        parameters.put(part, part);
                    }
                }
            }
            url = url.substring(0, i);
        }

        i = url.indexOf("://");
        if (i >= 0) {
            if (i == 0) throw new IllegalStateException("url missing protocol: \"" + url + "\"");
            protocol = url.substring(0, i);
            url = url.substring(i + 3);
        } else {
            i = url.indexOf(":/");
            if (i >= 0) {
                if (i == 0) throw new IllegalStateException("url missing protocol: \"" + url + "\"");
                protocol = url.substring(0, i);
                url = url.substring(i + 1);
            }
        }

        i = url.indexOf("/");
        if (i >= 0) {
            path = url.substring(i + 1);//see doc ex:context/path
            url = url.substring(0, i);
        }
        i = url.indexOf("@");
        if (i >= 0) {
            username = url.substring(0, i);
            int j = username.indexOf(":");
            if (j >= 0) {
                password = username.substring(j + 1);
                username = username.substring(0, j);
            }
            url = url.substring(i + 1);
        }
        i = url.indexOf(":");
        if (i >= 0 && i < url.length() - 1) {
            port = Integer.parseInt(url.substring(i + 1));
            url = url.substring(0, i);
        }
        if (url.length() > 0) host = url;
        return new URL(protocol, username, password, host, port, path, parameters);
    }
代码很长，但是url解析就是如此，包含scheme(protocol)，username,password,host,port,parameters
合法的url如下:

- zookeeper://codeL:123456@127.0.0.1:2189/context/path?version=1.0.0&application=morgan
- file:/codeL:123456@127.0.0.1:2189/context/path?version=1.0.0&application=morgan
- codeL:123456@127.0.0.1:2189/context/path?version=1.0.0&application=morgan

#### 小结 ####

----------

到这里对registry生成的url操作就结束了，但是服务还没有导出，现在我们继续来看。

#### doExportUrlsFor1Protocol ####

---
该方法是完整URL生成之后，进一步操作，也就是根据协议配置类（protocolConfig）和注册url列表导出服务

	    String name = protocolConfig.getName();
        if (name == null || name.length() == 0) {
            name = "dubbo";
        }
        String host = protocolConfig.getHost();
        if (provider != null && (host == null || host.length() == 0)) {
            host = provider.getHost();
        }

        boolean anyhost = false;
        if (NetUtils.isInvalidLocalHost(host)) {
            anyhost = true;
            try {
                host = InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException e) {
                logger.warn(e.getMessage(), e);
            }
            if (NetUtils.isInvalidLocalHost(host)) {
                if (registryURLs != null && registryURLs.size() > 0) {
                    for (URL registryURL : registryURLs) {
                        try {
                            Socket socket = new Socket();
                            try {
                                SocketAddress addr = new InetSocketAddress(registryURL.getHost(), registryURL.getPort());
                                socket.connect(addr, 1000);
                                host = socket.getLocalAddress().getHostAddress();
                                break;
                            } finally {
                                try {
                                    socket.close();
                                } catch (Throwable e) {
                                }
                            }
                        } catch (Exception e) {
                            logger.warn(e.getMessage(), e);
                        }
                    }
                }
                if (NetUtils.isInvalidLocalHost(host)) {
                    host = NetUtils.getLocalHost();
                }
            }
        }
        Integer port = protocolConfig.getPort();
        if (provider != null && (port == null || port == 0)) {
            port = provider.getPort();
        }
        final int defaultPort = ExtensionLoader.getExtensionLoader(Protocol.class).getExtension(name).getDefaultPort();
        if (port == null || port == 0) {
            port = defaultPort;
        }
        if (port == null || port <= 0) {
            port = getRandomPort(name);
            if (port == null || port < 0) {
                port = NetUtils.getAvailablePort(defaultPort);
                putRandomPort(name, port);
            }
            logger.warn("Use random available port(" + port + ") for protocol " + name);
        }

由于代码过长，我们拆分多个部分进行说明，这里我们粘贴出了第一个部分，主要是一些配置的校验和默认值的生成，其逻辑如下:
- 对协议配置类的name进行校验，
	- 无配置，使用默认配配置dubbo
- 对协议配置类的host进行校验
	- 无配置尝试使用provider配置类的host属性
	- 无配置或者配置是本地相关的地址，尝试转换为本地的地址，并尝试网络链接注册URL的中的地址，并尝试使用socket对应的本地地址
	- 有其他合法配置，直接跳过
- 对协议配置类的port进行校验
	- 无配置尝试使用provider配置类的port属性
	- 无配置尝试从使用name获得的扩展类的默认端口属性
	- 无配置尝试随机生成端口

上面的逻辑就是一些配置校验的逻辑。接下来让我们展开另一部分代码：

        Map<String, String> map = new HashMap<String, String>();
        if (anyhost) {
            map.put(Constants.ANYHOST_KEY, "true");
        }
        map.put(Constants.SIDE_KEY, Constants.PROVIDER_SIDE);
        map.put(Constants.DUBBO_VERSION_KEY, Version.getVersion());
        map.put(Constants.TIMESTAMP_KEY, String.valueOf(System.currentTimeMillis()));
        if (ConfigUtils.getPid() > 0) {
            map.put(Constants.PID_KEY, String.valueOf(ConfigUtils.getPid()));
        }
        appendParameters(map, application);
        appendParameters(map, module);
        appendParameters(map, provider, Constants.DEFAULT_KEY);
        appendParameters(map, protocolConfig);
        appendParameters(map, this);
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
                List<ArgumentConfig> arguments = method.getArguments();
                if (arguments != null && arguments.size() > 0) {
                    for (ArgumentConfig argument : arguments) {
                        if (argument.getType() != null && argument.getType().length() > 0) {
                            Method[] methods = interfaceClass.getMethods();
                            if (methods != null && methods.length > 0) {
                                for (int i = 0; i < methods.length; i++) {
                                    String methodName = methods[i].getName();
                                    if (methodName.equals(method.getName())) {
                                        Class<?>[] argtypes = methods[i].getParameterTypes();
                                        if (argument.getIndex() != -1) {
                                            if (argtypes[argument.getIndex()].getName().equals(argument.getType())) {
                                                appendParameters(map, argument, method.getName() + "." + argument.getIndex());
                                            } else {
                                                throw new IllegalArgumentException("argument config error : the index attribute and type attirbute not match :index :" + argument.getIndex() + ", type:" + argument.getType());
                                            }
                                        } else {
                                            for (int j = 0; j < argtypes.length; j++) {
                                                Class<?> argclazz = argtypes[j];
                                                if (argclazz.getName().equals(argument.getType())) {
                                                    appendParameters(map, argument, method.getName() + "." + j);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (argument.getIndex() != -1) {
                            appendParameters(map, argument, method.getName() + "." + argument.getIndex());
                        } else {
                            throw new IllegalArgumentException("argument config must set index or type attribute.eg: <dubbo:argument index='0' .../> or <dubbo:argument type=xxx .../>");
                        }
                    }
                }
            }
        }
        if (ProtocolUtils.isGeneric(generic)) {
            map.put("generic", generic);
            map.put("methods", Constants.ANY_VALUE);
        } else {
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
        if (!ConfigUtils.isEmpty(token)) {
            if (ConfigUtils.isDefault(token)) {
                map.put("token", UUID.randomUUID().toString());
            } else {
                map.put("token", token);
            }
        }
        if ("injvm".equals(protocolConfig.getName())) {
            protocolConfig.setRegister(false);
            map.put("notify", "false");
        }
        String contextPath = protocolConfig.getContextpath();
        if ((contextPath == null || contextPath.length() == 0) && provider != null) {
            contextPath = provider.getContextpath();
        }
        URL url = new URL(name, host, port, (contextPath == null || contextPath.length() == 0 ? "" : contextPath + "/") + path, map);
以上是另一部分代码，这一部分代码就是构建关键的url（元信息），最主要的目的也就构建参数map，我们之前提到过。我们来参数键值对map的构建
- 生成参数键值对集合map
	- host的配置是本地配置，或者没有配置，放入（anyhost：true）
	- 放入（side：provider）代表是服务方
	- 放入（dubbo：2.0.0）版本信息
	- 放入（timestamp：当前时间）
	- 放入（pid：应用pid）进程信息
	- 将application配置类信息放入map中
	- 将module配置类信息放入map中
	- 将provider配置类信息放入map中，前缀default;
	- 将protocol配置类信息放入map中
	- 将this配置类信息放入map中
	- 遍历methods配置类列表，将每个method的配置信息放入map中，前缀方法名
	- 对每个method配置类构建，key:"方法名.retry"的键做处理，并删除该键，如果该键的值等于false，将其转换为（"方法名.retries":"0"）放入
	- 将method配置类的嵌套ArgumentConfig配置类列表进行处理，对每一个ArgumnetConfig进行处理（两种配置方法）。
		- 获得ArgumentConfig的type类型（优先），如果类型存在的话	
			- 获得其上级method配置类在interfaceClass对应的method方法。
			- 获得method方法的参数类型，如果argument的index属性有配置，寻找匹配的参数位置(type和参数类型匹配)，并校验，将argument配置类写入map，前缀方法名.index位置
			- 变量方法的参数类型，根据ArgumentConfig的index属性来决定
			- 对于index等于-1,type又存在的情况下，找到方法参数的对应的位置的参数(所有type和参数类型匹配)，每个位置都使用前缀"方法名.参数位置",将ArgumentConfig配置类的信息放入map		
		- 获得ArgumentConfig的index(type不存在)，加配置信息加入map，前缀方法名.index位置
	- 对通用接口处理（如果是）
		- 放置key:generic，value:generic变量
		- 放置key:methods，value:*
	- 放置key:revision，value:服务版本号
	- 对服务类处理，包装服务类获得包装后的类的method名字数组
		- 数组不存在
			- 放置key:methods，value:*
		- 放置key:methods，value:方法名数组的拼接字符串
	- 放置token（如果有的话）（token:值）
		- 如果默认值，随机生成uuid
		- 否则使用配置值
	- 对协议配置类的协议是injvm的处理
		- 设定协议配置类的register属性为false(不注册)
		- 放置key:notify，value:false
	- 获得协议配置类上下文路径,url中的path
		- 协议配置类优先获得
		- 上述不存在尝试从provider配置类获得
	- 生成元信息URL
		- **tip**:**这个url和注册中心的url是由区别的**

又是很多文字，但是url元信息的构造已经完成，到这里我们拿到了两种类型的url，一个是协议配置url，一个是注册配置url列表。 接下来就是url的处理了。


        if (ExtensionLoader.getExtensionLoader(ConfiguratorFactory.class)
                .hasExtension(url.getProtocol())) {
            url = ExtensionLoader.getExtensionLoader(ConfiguratorFactory.class)
                    .getExtension(url.getProtocol()).getConfigurator(url).configure(url);
        }
        String scope = url.getParameter(Constants.SCOPE_KEY);
        if (!Constants.SCOPE_NONE.toString().equalsIgnoreCase(scope)) {
            if (!Constants.SCOPE_REMOTE.toString().equalsIgnoreCase(scope)) {
                exportLocal(url);
            }
            if (!Constants.SCOPE_LOCAL.toString().equalsIgnoreCase(scope)) {
                if (logger.isInfoEnabled()) {
                    logger.info("Export dubbo service " + interfaceClass.getName() + " to url " + url);
                }
                if (registryURLs != null && registryURLs.size() > 0
                        && url.getParameter("register", true)) {
                    for (URL registryURL : registryURLs) {
                        url = url.addParameterIfAbsent("dynamic", registryURL.getParameter("dynamic"));
                        URL monitorUrl = loadMonitor(registryURL);
                        if (monitorUrl != null) {
                            url = url.addParameterAndEncoded(Constants.MONITOR_KEY, monitorUrl.toFullString());
                        }
                        if (logger.isInfoEnabled()) {
                            logger.info("Register dubbo service " + interfaceClass.getName() + " url " + url + " to registry " + registryURL);
                        }
                        Invoker<?> invoker = proxyFactory.getInvoker(ref, (Class) interfaceClass, registryURL.addParameterAndEncoded(Constants.EXPORT_KEY, url.toFullString()));
                        Exporter<?> exporter = protocol.export(invoker);
                        exporters.add(exporter);
                    }
                } else {
                    Invoker<?> invoker = proxyFactory.getInvoker(ref, (Class) interfaceClass, url);
                    Exporter<?> exporter = protocol.export(invoker);
                    exporters.add(exporter);
                }
            }
        }
        this.urls.add(url);

逻辑也到了最为关键的地方.

- 根据协议配置的url中protocol属性来获得ConfiguratorFactory的普通扩展，但是我们一般使用的protocol是dubbo，所以并没有什么软用了。我们直接忽略了，这里也可以改造url
- 获得url中的scope参数信息，根据scope进行不同的暴露方式
	- scope是none（不区分大小写）那么就这样了，不用暴露了
	- scope是local进行本地暴露，那么跟注册中心就没有半毛事了，之前的获得的注册的url，也没用了
只用自己的这个协议配置url了
	- scope是remote，那么协议配置url和注册配置url列表得结合使用了。

对于我们来说，第三种方式是最复杂的情况，自然也包含了前两者欠款，现在我们就探究第三种方式。

#### romete的暴露方式 ####

----------

                if (registryURLs != null && registryURLs.size() > 0
                        && url.getParameter("register", true)) {
                    for (URL registryURL : registryURLs) {
                        url = url.addParameterIfAbsent("dynamic", registryURL.getParameter("dynamic"));
                        URL monitorUrl = loadMonitor(registryURL);
                        if (monitorUrl != null) {
                            url = url.addParameterAndEncoded(Constants.MONITOR_KEY, monitorUrl.toFullString());
                        }
                        if (logger.isInfoEnabled()) {
                            logger.info("Register dubbo service " + interfaceClass.getName() + " url " + url + " to registry " + registryURL);
                        }
                        Invoker<?> invoker = proxyFactory.getInvoker(ref, (Class) interfaceClass, registryURL.addParameterAndEncoded(Constants.EXPORT_KEY, url.toFullString()));
                        Exporter<?> exporter = protocol.export(invoker);
                        exporters.add(exporter);
                    }
                } else {
                    Invoker<?> invoker = proxyFactory.getInvoker(ref, (Class) interfaceClass, url);
                    Exporter<?> exporter = protocol.export(invoker);
                    exporters.add(exporter);
                }
以上就是scope等于romete的相关代码了。

尽管你配置remote，你还可以有别的配置来实现不对外注册。

- 注册配置类url列表存在的情况下和协议配置中url的registrer是true情况下才会尝试注册
	- 这意味着，缺少注册配置url列表或者为N/A，或者你设置协议配置类的名字为injvm，只会本地暴露
	- 遍历注册列表，对每一个代表注册的url都要处理，因为我们可能是多注册中心。
	- 尝试将注册的url的dynamic属性加入协议配置url，当然如果已存在就忽略注册url的这个配置
	- 尝试从注册的url中发掘监控的url，对有注册中心的配置来说，监控url应该尽量配置
	- 尝试将监控url，加入到协议配置url中，通过（monitor，监控url）：该监控url会进行编码
	- 将协议配置url，加入到注册配置url中，桶过(export,协议配置url)：该协议配置url会进行编码

#### 远程暴露服务的细节逻辑 ####

---
1. 追加协议URL的信息
	- 放置key：dynamic，v:registryURL.getParameter("dynamic")
2. 尝试从注册URL中获得监控URL
	1. 监控配置类不存在，自动生成规则如下
		1. 获得监控地址，ConfigUtils.getProperty("dubbo.monitor.address")
		2. 获得监控协议，ConfigUtils.getProperty("dubbo.monitor.protocol")
		3. 上述存在，自动生成实例
	2. 填充监控配置类的基本属性，使用**appendProperties**
	3. 生成监控URL对于的参数键值对map
		1. 放入k:interface,v：com.alibaba.dubbo.monitor.MonitorService
		2. 放入k:dubbo,v:2.0.0(Version.getVersion())
		3. 放置key:pid，value:ConfigUtils.getPid()
		4. 放置key:timestamp，value:当前时间
		5. 将监控配置类信息放入map中，使用**appendParameters**;
	4. 获得地址
		1. 优先使用系统配置项dubbo.monitor.address的地址
		2. 使用监控配置类地址
	5. 处理地址
		1. 地址存在，不存在key:protocol
			1. 存在logstat的配置扩展类，放入key:protocol,v:logstat
			2. 不存在，放入key:protocol,v:dubbo
			3. 根据address和map生成监控URL
		2. 地址不存在，监控的协议是registry，且注册中心URL存在
			1. 注册中心地址协议设为dubbo，
			2. 添加键值对protocol:registry,
			3. 添加键值对refer:map的URL参数化
			4. 返回空
			
3. 监控URL存在
	1. 建监控URL以键值对形式添加进协议配置URL中
		1. key:monitor v:url字符串
		
4. 将协议配置的URL以键值对形式添加进注册中心URL中
	1. key：export v:协议配置类URL字符串形式

5. 使用ProxyFactory$Adaptive根据registryURL来获得Invoker

6. 使用Proxy$Adaptive将获得的Invoker导出实现服务暴露

#### 总结 ####

----
到这里整体的介绍就完成了，具体的导出我们下一篇再见。