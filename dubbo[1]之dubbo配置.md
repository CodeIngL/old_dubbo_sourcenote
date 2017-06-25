## dubbo配置
dubbo对我们来说是一款比较优秀的RPC框架。dubbo配置，也是dubbo框架能运行起来的重点。我们详细来介绍配置。
>**tip**：***framework conf is not only for spring***


#### dubbo-config-api
---
dubbo-config-api包是dubbo配置模块的子包，里面包含dubbo中的配置类。  
对应配置类，读者首先必须有这样的概念：

1. 配置类极有可能是单纯的POJO。
2. 配置类也有可能嵌套配置类，本质是一个单纯的POJO。
3. 配置类如果嵌套复杂的非配置类属性，一般也会嵌套其他配置类，这个配置类一般是入口，或者辅助程序入口。
4. 配置类是顶层配置类，往往的作用也是同第三点一样。

这里单纯POJO的概念是，只拥有基本属性。  
为了下文描述的方便，对于符合上述第3，4项要求的配置类我们称**复杂配置类**，其他形式的称为**简单配置类**。

#### dubbo复杂配置类
---
dubbo中唯一两个称为复杂类属性的配置类，也正是dubbo服务，服务方和消费方各自的入口。

- ServiceConfig（服务方的入口）
	- 提供者必须使用的配置类
		- 复杂类属性：
			

			    //Protocl$Adaptive单例唯一
			    private static final Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
			
			    //ProxyFactory$Adaptive单例唯一
			    private static final ProxyFactory proxyFactory = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();

- ReferenceConfig（消费方的入口）
	- 消费者必须使用的配置类
		- 复杂类属性：
				
				//Protocl$Adaptive单例唯一
			    private static final Protocol refprotocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
				
				//Cluster$Adaptive单例唯一
			    private static final Cluster cluster = ExtensionLoader.getExtensionLoader(Cluster.class).getAdaptiveExtension();
			    
				//ProxyFactory$Adaptive单例唯一
			    private static final ProxyFactory proxyFactory = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();

这里读者不需要纠结这些复杂类属性，只需要对这两个复杂配置类有印象。

#### dubbo-api编码
---
dubbo支持不依赖spring框架而独立使用，我们以官网的例子来参考:  

- 服务提供者，请快速浏览，不需要理解

		// 服务实现
		XxxService xxxService = new XxxServiceImpl();
		 
		// 当前应用配置
		ApplicationConfig application = new ApplicationConfig();
		application.setName("xxx");
		 
		// 连接注册中心配置
		RegistryConfig registry = new RegistryConfig();
		registry.setAddress("10.20.130.230:9090");
		registry.setUsername("aaa");
		registry.setPassword("bbb");
		 
		// 服务提供者协议配置
		ProtocolConfig protocol = new ProtocolConfig();
		protocol.setName("dubbo");
		protocol.setPort(12345);
		protocol.setThreads(200);
		 
		// 注意：ServiceConfig为重对象，内部封装了与注册中心的连接，以及开启服务端口
		 
		// 服务提供者暴露服务配置
		ServiceConfig<XxxService> service = new ServiceConfig<XxxService>(); // 此实例很重，封装了与注册中心的连接，请自行缓存，否则可能造成内存和连接泄漏
		service.setApplication(application);
		service.setRegistry(registry); // 多个注册中心可以用setRegistries()
		service.setProtocol(protocol); // 多个协议可以用setProtocols()
		service.setInterface(XxxService.class);
		service.setRef(xxxService);
		service.setVersion("1.0.0");
		 
		// 暴露及注册服务
		service.export();

- 服务消费者，请快速浏览，不需要理解

		// 当前应用配置
		ApplicationConfig application = new ApplicationConfig();
		application.setName("yyy");
		 
		// 连接注册中心配置
		RegistryConfig registry = new RegistryConfig();
		registry.setAddress("10.20.130.230:9090");
		registry.setUsername("aaa");
		registry.setPassword("bbb");
		 
		// 注意：ReferenceConfig为重对象，内部封装了与注册中心的连接，以及与服务提供方的连接
		 
		// 引用远程服务
		ReferenceConfig<XxxService> reference = new ReferenceConfig<XxxService>(); // 此实例很重，封装了与注册中心的连接以及与提供者的连接，请自行缓存，否则可能造成内存和连接泄漏
		reference.setApplication(application);
		reference.setRegistry(registry); // 多个注册中心可以用setRegistries()
		reference.setInterface(XxxService.class);
		reference.setVersion("1.0.0");
		 
		// 和本地bean一样使用xxxService
		XxxService xxxService = reference.get(); // 注意：此代理对象内部封装了所有通讯细节，对象较重，请缓存复用

以上就是dubbo服务提供方和消费方各自程序编码。 
结合我们上面所说的config-api中配置的特征，是否对dubbo有了更好的理解。  
简单说明这两段代码，除了**ServiceConfig**和**ReferenceConfig**，其他XxxConfig都是 **简单配置类**。  

>**简单配置类**提供相应的基本字段给dubbo使用。  
>**复杂配置类**在属性链上，能够引用到所有的配置信息。


#### dubbo-config-spring
---
dubbo-config-spring包是dubbo配置模块的子包，该包是为了集成spring框架，而扩展dubbo-config-api包的结果。 
这里我们先列举，在spring应用能够使用的dubbo提供的自定义标签(来自官网)。

- dubbo标签:
	- <dubbo:service/>
	- <dubbo:reference/>
	- <dubbo:protocol/>
	- <dubbo:registry/>
	- <dubbo:monitor/>
	- <dubbo:application/>
	- <dubbo:module/>
	- <dubbo:provider/>
	- <dubbo:consumer/>
	- <dubbo:method/>
	- <dubbo:argument/>
	- <dubbo:parameter/>
	
为了更好给同学直观的感受，我再贴一份config-api中的配置类

- 能够实例化的配置类：
	- ServiceConfig
	- ReferenceConfig
	- ProtocolConfig
	- RegistryConfig
	- MonitorConfig
	- ApplicationConfig
	- ModuleConfig
	- ProviderConfig
	- MethodConfig
	- ArgumentConfig
	
两份进行比较同学应该有更加直观的感受。  
除了```<dubbo:parameter/>```其他貌似都有一一对应的情况。  
事实上```<dubbo:parameter/>```引用官方的话:用于配置自定义参数，该配置项将作为扩展点设置自定义参数使用。本质上也会转换为某几个**简单配置类**内部的一个map属性

#### config模块小结
---
dubbo官方推荐我们使用spring的方式使用dubbo。  
经我们上面粗略分析，仅仅也就是将api配置的依赖，从编码配置移动到spring配置。  
实际的重点依旧是**ServiceConfig**和**RegistryConfig**。   
因为他们带着 **dubbo杂点**相关复杂类属性，**dubbo杂点**见另一篇文章。  
也等价于 **程序的入口**