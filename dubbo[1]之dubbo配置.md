# dubbo配置

dubbo对我们来说是一款比较优秀的RPC框架,现在也有人在维护。

dubbo配置模块，是dubbo众多的模块之一，当然也是dubbo框架核心模块之一。  

该篇文章作为dubbo源码分析的第一篇文章,我们将通过对配置模块的简单介绍来使读者由浅入深的接触整个dubbo源码。

> dubbo推荐使用spring来工作，spring不是dubbo的必须依赖

## dubbo-config-api

---

dubbo-config-api包是dubbo配置模块的子包，里面包含dubbo中的配置类。  

对应配置类，读者首先必须有这样的概念：

1. **配置类极有可能是单纯的POJO**

2. **配置类也有可能嵌套配置类，本质还是一个POJO**

3. **配置类如果嵌套复杂的非配置属性，这个配置类一般是入口，或者辅助程序入口**

4. **配置类是顶层配置类，往往的作用也是同第三点一样**

为了下文描述的方便，对于符合上述第3、4项要求的配置类我们称**复杂配置类**，其他形式的称为**简单配置类**。

#### dubbo复杂配置类
---
dubbo中唯一的两个**复杂配置类**，也正是dubbo服务，服务方和消费方各自的入口。

- ServiceConfig（服务方的入口）
	- 提供者必须使用的配置类
		- 复杂的非配置属性：

			  private static final Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
			
			  private static final ProxyFactory proxyFactory = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();

- ReferenceConfig（消费方的入口）
	- 消费者必须使用的配置类
		- 复杂的非配置属性：
				
			  private static final Protocol refprotocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
				
			  private static final Cluster cluster = ExtensionLoader.getExtensionLoader(Cluster.class).getAdaptiveExtension();
			    
			  private static final ProxyFactory proxyFactory = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();

读者请先**忽略**上述代码，仅仅存有印象即可。详细的解释我们将会慢慢展开

#### dubbo-api编码
---
dubbo支持不依赖spring框架而独立使用，当然这样你需要以编码的形式来进行配置，这里我们以官网的例子来参考:  

- 服务提供者，读者请快速浏览，不需要理解

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
		 
		// 服务提供者暴露服务配置
		ServiceConfig<XxxService> service = new ServiceConfig<XxxService>(); 
		service.setApplication(application);
		service.setRegistry(registry); // 多个注册中心可以用setRegistries()
		service.setProtocol(protocol); // 多个协议可以用setProtocols()
		service.setInterface(XxxService.class);
		service.setRef(xxxService);
		service.setVersion("1.0.0");
		 
		// 暴露及注册服务
		service.export();

以上就是dubbo服务提供方的编码实现，消费方编码也类似。 

结合我们上面所说的config-api中配置的特征，是否对配置模块（dubbo）的作用（不仅限于dubbo）有了更好的理解。  

简单说明这段代码，除了**ServiceConfig**，其他XxxConfig都是 **简单配置类**。  

>**简单配置类**提供相应的基本字段给dubbo使用。  
>**复杂配置类**在属性链上，能够引用到所有的配置信息。
> 
>    - 复杂配置类本身持有配置信息，通过嵌套的配置类间接持有其他信息或者补充本身的信息


#### dubbo-config-spring
---
dubbo-config-spring包同样也是是dubbo配置模块的子包，该包的目的就是api形式的spring加强版。 

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
	- <dubbo:method/>
	- <dubbo:argument/>
	- <dubbo:consumer/>
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
	- ConsumerConfig
	
两份进行比较同学应该有更加直观的感受。    
除了<dubbo:parameter/>，其他似乎都是一一对应。    
事实上<dubbo:parameter/>引用官方的话:用于配置自定义参数，该配置项将作为扩展点设置自定义参数使用。  
本质上其对应于某几个**简单配置类**内部的一个map配置属性。

#### config配置模块小结
---
dubbo推荐我们使用spring的方式，经我们分析仅仅是做了一层薄薄的封装，当然spring很擅长这活。
但我们比较重要的还是关注服务方的**ServiceConfig**，消费方的**RegistryConfig**。
因为他们是**程序的入口**，至于深层原因，请继续阅读文章**dubbo杂点**。