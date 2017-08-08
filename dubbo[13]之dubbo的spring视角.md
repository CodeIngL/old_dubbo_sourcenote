## dubbo spring视角
看完了dubbo杂点，大概对内部的对象依赖构建有一定的理解了。

#### 切入dubbo-demo-provider视角
---
该工程是dubbo的官方例子，如何调试见**README.md**的**Quick debug**部分   
我们先看spring相关的配置文件

    <bean id="demoService" class="com.alibaba.dubbo.demo.provider.DemoServiceImpl"/>

    <dubbo:service interface="com.alibaba.dubbo.demo.DemoService" ref="demoService"/>

很简单，除了service标签，其他什么都没有。 对这个配置留存个印象，就是配置只有service标签。


#### 服务提供者入口:ServiceConfig.export()
---
该方法是服务提供者的入口，我们首先关注的是，上述的没有其他标签和代码的情况下，其他配置类实例如何将属性，或者实例本身设置和同步进服务提供者实例中。

- provider
	1. 自动生成实例，并使用**appendProperties**完成基本属性的设置
- application
	1. 尝试从provider配置类获取中获取
	2. 必须有dubbo.application.name配置项，自动生成实例，并使用**appendProperties**完成基本属性的设置
- module
	1. 尝试从provider配置类获取中获取
- registries
	1. 尝试从provider配置类获取中获取
	2. 尝试从module配置类获取中获取
	3. 尝试从application配置类获取中获取
	4. 必须有dubbo.registry.address配置项，自动生成实例，并使用**appendProperties**完成基本属性的设置
- monitor
	1. 尝试从provider配置类获取中获取
	2. 尝试从module配置类获取中获取
	3. 尝试从application配置类获取中获取
- protocols
	1. 尝试从provider配置类获取中获取
	2. 自动生成实例，name设置为dubbo，并使用**appendProperties**完成基本属性的设置

上面是一个Service拥有的配置类属性，值得一提的是上面我们多次提到了**appendProperties**方法。下面我们提下该方法。

#### 重点方法**appendProperties**
---
该方法涉及到了属性的设置，是一个重点方法，也是我们进行扩展时，必须掌握的方法。
该方法的参数是某一个配置类对象，包括简单or复杂。

- 获得前缀，对应api中的配置类XxxConfig或者Spring中扩展的XxxBean,前缀等价为dubbo.xxx.
- 对于所有的型如public setAaa(type value)进行设置，type必须是基本属性，也就是配置类的基本属性的set方法具体规则如下
	1. 存在id属性，使用dubbo.xxx.id.aaa作为key从系统获取，**注意id是不是字符串id，aaa对应型如setAaa**
	2. 不存在id属性，尝试使用dubbo.id.aaa作为key从系统获取，**注意id是不是字符串id，aaa对应型如setAaa**
	3. **忽略设置，前提使用放射调用setAaa()对应的getAaa或者IsAaa()的返回值不为NULL**
	4. 存在id属性，使用dubbo.xxx.id.aaa作为key参数**ConfigUtils.getProperty**获取，**注意id是不是字符串id，aaa对应型如setAaa**
	5. 不存在id属性，尝试使用dubbo.id.aaa作为key参数**ConfigUtils.getProperty**获取，**注意id是不是字符串id，aaa对应型如setAaa**
	6. 尝试使用dubbo.id.aaa作为key参数在legacyProperties本地map获得value，作为key，使用**ConfigUtils.getProperty**(猜测历史遗留问题)

小结:
> 对上面1，2给我们的启示，无论配不配相关属性，一旦在系统中设置后，就有绝对的优先级。  
> ConfigUtils.getProperty是从系统配置中获得封装，也会尝试从系统中获得，区别的是如系统中获得，会使用配置文件获得。
>   
> 配置文件的路径规则:系统dubbo.properties.file的值，环境变量dubbo.properties.file值，最后dubbo.properties
> 然后去读取配置文件（文件方式，jar包资源方式），注意这里有多个配置文件读到。  
> 
> 同时getProperty传入的参数也会进行占位符替换${xxx}，优先系统配置，其次配置文件中kv替换，没有的话直接替换成空串。  

这样方法appendProperties就说明完毕了。我们会过头了看服务导出，对于其他配置类设定，完毕后。
本身ServiceConfig也是配置类，本身的某些属性也需要设定。

#### ServiceConfig也是配置类
---
ServiceConfig也是配置类也是配置类，因此也要设定校验某些属性

- interfaceClass:接口类类型（必填）
- ref:具体实现类的实例（不是必填的，但是在框架流程后，一定是要存在，会校验）
- methods:简单配置类（可选）,如果配置，methods涉及到方法必须是interfaceClass方法的子集
- generic：标示服务接口的类型(框架内部使用)。true表示通用接口，false表示其他

上述属性是比较重要的属性，也不是基本属性，基本属性，基本在其父类中。属性的设置一会调用**appendProperties**进行设置。


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
对应URL的方法生成:首先registry配置类的地址不是N/A
1. 尝试获得系统dubbo.registry.address值作为address
2. registry本身的address属性
3. 生成地址相关map
	1. 将application配置类的信息追加的map中，使用**appendParameters**;
	2. 将registry配置类的信息追加的map中，使用**appendParameters**;
	3. 放置key:path,value:RegistryService
	4. 放置key:dubbo,value:2.0.0(Version.getVersion())
	5. 放置key:timestamp，value:当前时间
	6. 放置key:pid，value:ConfigUtils.getPid()
	7. map不否含有protocol的情况
		- 扩展类存在"remote"对应的配置，放置key:protocol,value:remote
		- 扩展类不存在"remote"对应的配置，放置key:protocol,value:dubbo
		
- address和map结合生成urls（address可以拆分多个的）
	- 详情见源码注释玩 
	
之后就是生成完整的url

#### doExportUrlsFor1Protocol
---
该方法是完整URL生成之后，进一步操作，也就是根据协议配置和URL导出服务

- 对协议配置类的host进行校验
	- 无配置，没有相关，使用URL中地址。并ping地址的联通性，得到返回地址对houst进行设置
	- 有配置跳过
- 获得端口
	1. 从配置类获得端口
	2. 从配置类对应扩展类中获得端口
	3. 随机生成端口
- 生成参数键值对
	- 对应协议配置类开始没有配置host，放入k:anyhost，v：true
	- 放入k:side,v:provider
	- 放入k:dubbo,v:2.0.0(Version.getVersion())
	- 放置key:timestamp，value:当前时间
	- 放置key:pid，value:ConfigUtils.getPid()
	- 将application配置类的信息追加的map中，使用**appendParameters**;
	- 将module配置类的信息追加的map中，使用**appendParameters**;
	- 将provider配置类的信息追加的map中，使用**appendParameters**;
	- 将**协议配置类**的信息追加的map中，使用**appendParameters**;
	- 将**serverConfig(this)**的信息追加的map中，使用**appendParameters**;
	- 对methods配置类处理
		-  将method配置类的信息追加的map中，使用**appendParameters**;
		-  对重试次数做处理，字符串做处理，false转为0
		-  对method配置类的嵌套ArgumentConfig配置类处理
			- 将ArgumentConfig的信息追加的map中，使用**appendParameters**;
	- 对通用接口处理（如果是）
		- 放置key:generic，value:generic变量
		- 放置key:methods，value:*
	- 放置key:methods，value:服务版本号
	- 对服务类包装获得method名字数组
		- 数组存在
			- 放置key:methods，value:*
		- 放置key:methods，value:数组拼接
	- 放置token
	- 协议是injvm
		- 放置notify，false
	- 获得上下文
		- 协议配置类获得
		- provider配置类获得
	- 生成元metaURL
	- 将URL中设置进相关的配置类


#### 暴露服务
----
校验url中的scope

- 对于本地直接使用url暴露
- 对于远程
	- registryURL存在，
		- 追加url中信息
			- 放置key：dynamic，v:registryURL.getParameter("dynamic")
			- 获得监控的URL，使用registryURL和monitor以及其他信息转换
			- 放置key:monitor value:监控URL
		- 放置key:export,value:url
		- 使用registryURL来获得Invoker
		- 导出Invoker实现服务暴露
	- 不存在
		- 使用URL来获得Invoker
		- 导出Invoker
		
registryURL意味着注册中心，自然监控少不了
