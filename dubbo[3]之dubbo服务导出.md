##dubbo 服务导出
上一篇文章中，我们通过服务提供者的复杂配置类进行了展开，介绍了dubbo部分运行流程。  
在本文章，我会继续分享dubbo的导出。

### ServiceConfig服务导出
---
上篇文章，我们已经指出了ServiceConfig是服务导出类。现在我们来看下是服务是如何导出的。

### 服务提供者入口:ServiceConfig.export()
---
该方法是dubbo导出服务的入口，其在第一篇中引用官方api方式开启中被使用。

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
                    try {
                        Thread.sleep(delay);
                    } catch (Throwable e) {
                    }
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

上述就是方法实现，逻辑清晰，一眼即明。

1. export（导出标志属性）为空，尝试使用provider的export配置。
2. delay（延迟属性）为，尝试使用provider的delay配置。
3. 对导出标志属性进行检查，对于已导出就直接返回。
4. 对延迟属性进行处理。

上述就是**export()**的代码功能介绍。我们可以看到没有什么复杂处理，真正的处理逻辑被委托给了方法**doExport()**

### 服务提供者导出逻辑:ServiceConfig.doExport()
---
该方法是服务导出的进一步逻辑处理。  
我们说到ServiceConfig还是个**复杂配置类**，意味着本质上还是配置类，因此相关配置属性校验是必不可少的。doExport()篇幅上也仅仅做了属性的校验和设置，最终真正的处理我们后续再说。以下是代码:

    protected synchronized void doExport() {
        
		//1.对（exported）导出标记和（unexported）未导出标记进行校验

        //2.对（interfaceName:必填）接口名字配置项校验
      
		//3.对 (provider:可选)**简单配置类**进行校验
		//----可选的含义，不存在则自动生成实例。
		//----对provider使用**appendProperties**完成基本属性的设置。

		//4.尝试从嵌套的简单配置中，完成配置的转移，即完成本身的配置类属性配置
		//----application为空，尝试从provider配置类中获取
		//----module为空，尝试从provider配置类中获取
		//----registries为空，尝试从provider，module，application配置类中获取
		//----monitor为空，尝试从provider，module，application配置类中获取
		//----protocols为空，尝试从provider配置类中获取

		//5.对配置项ref进行检验
		//----通用接口，属性**interfaceClass**为GenericService，generic为true
		//----非通用接口，属性**interfaceClass**为**interfaceName**的反射类，generic为false

		//6.对属性local和stub的处理

		//7.对 (application:可选)**简单配置类**进行校验

		//8.对 (registries:可选)**简单配置类的List**进行校验

		//9.对 (protocols:可选)**简单配置类的List**进行校验

		//10.使用**appendProperties**完成自己本身基本属性的设置

		//11.对属性local和stub的的校验，并生成mock

		//12.对属性path进行设置，为空，赋值为配置项interfaceName

        doExportUrls();
    }
1. 第5点的非通用接口的补充说明
	1. interfaceClass标记不为空，且是接口，必须包含配置类methods配置方法
	2. ref必须是interfaceClass的子类
2. 第7点的说明
	1. 不存在自动生成实例(存在dubbo.application.name配置项)
	2. 使用**appendProperties**完成基本属性的设置
3. 第8点的说明
	1. 不存在自动生成实例(存在dubbo.registry.address配置项,根据|拆分地址)
	2. 使用**appendProperties**完成基本属性的设置
4. 第9点的说明
	1. 不存在自动生成实例
	2. name属性为空，设定name为dubbo，id属性为空，设定id为name
	2. 使用**appendProperties**完成基本属性的设置
5. 第11点的说明
	1. 生成mock
		1. 含有return的字符串，配置中直接返回return后 字符串（数据json解析后）
		2. 加载mock类类型到jvm中，一定有公共默认构造函数

上面我们多次提到了**appendProperties**方法。下面我们详细介绍该方法。

#### 重点方法**appendProperties**
---
该方法与配置类基本属性设置相关，是一个重点方法，也是我们进行扩展时，可以考虑的扩展点。
参数是配置类。请看依据源码注释跟进。

- 获得前缀，对应api中的配置类XxxConfig或者Spring中扩展的XxxBean,前缀等价为dubbo.xxx.

- 对于所有的型如public setAaa(type value)进行设置，type必须是基本属性，也就是配置类的基本属性的set方法具体规则如下
	1. 存在id属性，使用dubbo.xxx.‘id’.aaa作为key从系统获取，**注意id是不是字符串id，aaa对应型如setAaa**。
	2. 不存在id属性，尝试使用dubbo.xxx.aaa作为key从系统获取。
	3. **忽略设置，如果调用setAaa()对应的getAaa或者IsAaa()的返回值为NULL**
	4. 存在id属性，使用dubbo.xxx.‘id’.aaa作为key参数**ConfigUtils.getProperty**获取，
	5. 不存在id属性，尝试使用dubbo.xxx.aaa作为key参数**ConfigUtils.getProperty**获取。
	6. 尝试使用dubbo.id.aaa作为key参数在legacyProperties本地map获得value，作为key，使用**ConfigUtils.getProperty**(猜测历史遗留问题)

小结:
> 对上面1，2给我们的启示，无论配不配相关属性，一旦在系统中设置后，就有绝对的优先级。  
> ConfigUtils.getProperty是从系统配置中获得封装，也会尝试从系统中获得，区别的是如系统中获得，会使用配置文件获得。
>   
> 配置文件的路径规则:系统dubbo.properties.file的值，环境变量dubbo.properties.file值，最后dubbo.properties
> 然后去读取配置文件（文件方式，jar包资源方式），注意这里有多个配置文件读到。  
> 
> 同时getProperty传入的参数也会进行占位符替换${xxx}，优先系统配置，其次配置文件中kv替换，没有的话直接替换成空串。  

这样方法appendProperties就说明完毕了。

#### 服务提供者入口:ServiceConfig.doExportUrls()
---
ServiceConfig.doExportUrls()该方法是export的核心逻辑委托。在上面的配置类的关系设置后，就会转入该方法执行。  
实现很简单：
	
	    private void doExportUrls() {
	        List<URL> registryURLs = loadRegistries(true);
	        //对每种协议都进行导出
	        for (ProtocolConfig protocolConfig : protocols) {
	            doExportUrlsFor1Protocol(protocolConfig, registryURLs);
	        }
	    }
对应URL的方法生成loadRegistries(true)的说明:

1. 校验registry，和上面说到的一致
2. 获得注册中心地址:address
	1. 尝试使用registry配置类的地址配置项address
	2. 为空，使用0.0.0.0
	3. 尝试获得系统dubbo.registry.address值（最高优先权）
3. 对有效的注册地址不是（N/A）进行处理（需要注册中心）（非常非常非常重要）
	1. 生成地址相关键值对map，该map做为默认的url参数键值对集合
		1. 将application配置类信息放入map中，使用**appendParameters**;
		2. 将registry配置类信息放入map中，使用**appendParameters**;
		3. 放置key:path,value:com.alibaba.dubbo.registry.RegistryService
		4. 放置key:dubbo,value:2.0.0(Version.getVersion())
		5. 放置key:timestamp，value:当前时间
		6. 放置key:pid，value:ConfigUtils.getPid()
		7. map没有key:protocol的情况
			- 扩展类存在"remote"对应的配置，放置key:protocol,value:remote
			- 扩展类不存在"remote"对应的配置，放置key:protocol,value:dubbo
	2. address和map结合生成urls（address可以拆分多个的）
		- 详情见源码注释玩 
	3. 继续处理URL
		1. 放置key:registry,value:URL的协议(url.getProtocol())
		2. 设置URL的协议为registry
		3. 加入URL最终列表
		
之后就是生成完整的url列表返回

>tip:上面使用appendParameters操作的配置类是application和registry

#### 插曲之appendParameters方法
---
上面很多说明，都提到了该方法，现在我们就此方法进行详细说明，因为后文也会继续说到该方法。
该方法在源码中注释非常详细，读者请按照注释跟进。


#### doExportUrlsFor1Protocol
---
该方法是完整URL生成之后，进一步操作，也就是根据协议配置类（protocolConfig）和URL导出服务

- 对协议配置类的host进行校验
	- 无配置，没有相关，使用URL中地址。并ping地址的联通性，得到返回地址对host进行设置
	- 有配置跳过
- 获得端口
	1. 从配置类获得端口
	2. 从配置类对应扩展类中获得端口
	3. 随机生成端口
	
- 生成参数键值对集合map
	- 对应协议配置类开始没有配置host，放入k:anyhost，v：true
	- 放入k:side,v:provider
	- 放入k:dubbo,v:2.0.0(Version.getVersion())
	- 放置key:timestamp，value:当前时间
	- 放置key:pid，value:ConfigUtils.getPid()
	
	- 将application配置类信息放入map中，使用**appendParameters**;
	 
	- 将module配置类信息放入map中，使用**appendParameters**;
	 
	- 将provider配置类信息放入map中，使用**appendParameters**，前缀default;
	 
	- 将protocol配置类信息放入map中，使用**appendParameters**;
	 
	- 将this配置类信息放入map中，使用**appendParameters**;
	 
	- 将method配置类信息放入map中,使用**appendParameters**，前缀方法名;
		-  key:"方法名.retry"的键做处理。如果v是false。转换为k:"方法名.retries",v:"0"
	- 将method配置类的嵌套ArgumentConfig配置类信息放入map，使用**appendParameters**
		- 变量方法的参数类型，根据ArgumentConfig的index属性来决定
		- 对于index不等于-1，找到方法参数的对应的位置的参数（一个），使用前缀"方法名.index",将ArgumentConfig配置类的信息放入map
		- 对于index等于-1，找到方法参数的对应的位置的参数(所有)，每个位置都使用前缀"方法名.参数位置",将ArgumentConfig配置类的信息放入map			
	- 对通用接口处理（如果是）
		- 放置key:generic，value:generic变量
		- 放置key:methods，value:*
	- 放置key:methods，value:服务版本号
	- 对服务类处理，包装服务类获得包装后的类的method名字数组
		- 数组不存在
			- 放置key:methods，value:*
		- 放置key:methods，value:数组拼接
	- 放置token（如果有的话）
	- 对协议配置类的协议是injvm的处理
		- 设定协议配置类为不注册
		- 放置key:notify，value:false
	- 获得协议配置类上下文路径
		- 协议配置类优先获得
		- provider配置类获得
	- 生成元信息URL
		- **tip**:**这个url和注册中心的url是由区别的**
	- 将URL中设置进相关的配置扩展类
#### 暴露服务
---
该部分依旧是doExportUrlsFor1Protocol的一部分，但是到了现在是真正的暴露服务了，上面大篇幅只是为了生成相应的元信息。 

- 校验url中的scope，对于scope为none是不暴露的，也就结束了。
	- 对于scope为local本地暴露，直接使用url，不需要注册中心
		- 暴露方法**exportLocal(url)**
	- 对于scope为remote远程暴露
		- registryURL存在，且配置协议类中设定的是允许使用注册中心
		- 不使用注册中心，典型的是直连暴露

上面就是真正的暴露，包含三种情况，但基本上都是一样的思路。本质上都使用元信息，该元信息是用URL来封装的。我们针对最为复杂的远程暴露来做解释。

#### 远程暴露服务
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



				

