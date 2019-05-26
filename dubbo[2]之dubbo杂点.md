## dubbo 杂点 ##

dubbo杂点，是dubbo源码分析系列的第二篇文章,该文描述的是一些的零散但很重要的知识，上一篇**dubbo配置**我们留的疑问我们也将在该篇文章中一一解答。

虽然名字叫做杂点，但也只是我实在找不到合适的词来概括这一篇文章的内容，总的来说，这里的知识点是相当重要的，这里将会引出一个jdk中存在的一个概念。
阅读该文的时候，读者应该尽量参考源码进行阅读，[dubbo源码地址](http://192.168.110.114/laihj/dubbo "dubbo源码地址")。源码加上了相关的注释。

### dubbo复杂配置类 ###
---
**dubbo配置**一文，我们已经向读者介绍了什么是**复杂配置类**，这里我们再次从服务提供者的角度来说明，消费方类似:

- ServiceConfig（服务方的入口：**复杂配置类**）
	- 提供者必须使用的复杂配置类
		- **复杂类属性之一**：
	
			  private static final Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();

上一篇文章中，对于上面的代码，我们让读者留有印象即可，而现在正是我们本文议论的出发点，本文也是围绕着该行代码展开。

### ExtensionLoader类 ###
---
上面一行代码中我们注意到这样一个类**ExtensionLoader**。 在dubbo框架中，这个类地位举足轻重，dubbo也是依靠该类进行扩展，值得注意的是java自身也有相似的构造：ServiceLoader类。dubbo本身就是对该类功能的扩展（SPI机制），本章的内容也就是围绕着这个类展开。在这里，我们首先给它取个名字:**扩展加载器类**。

>**tip**: ***阅读该类务必对照源码，源码已加丰富注释，促进读者理解***

首先我们先贴出类声明，类声明结构如下:

	public class ExtensionLoader<T>{...}

从声明结构我们可以看出，其带有泛型结构T，这里称之为T类型扩展加载器。

#### 重点方法**getExtensionLoader** ####
---
上述一行代码中，我们看到对该方法的调用。这是**扩展加载器类**的一个非常重要的方法，
在这里我们将对其详细展开，首先贴出源码。
	
	 public static <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        
		/**
		 * ..省略的了部分代码,对入参的相关校验..
		 */
        
        ExtensionLoader<T> loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        if (loader == null) {
            EXTENSION_LOADERS.putIfAbsent(type, new ExtensionLoader<T>(type));
            loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        }
        return loader;
    }
对于实现源码，读者不难看出是缓存操作，重点**EXTENSION_LOADERS**这个缓存结构和新建缓存对象 **new ExtensionLoader<T>(type)**  

**EXTENSION_LOADERS**是**类属性**，说明如下:

	//缓存了interface（这个class特指interface），与ExtensionLoader（interface的特殊实现类）的映射
    ConcurrentMap<Class<?>, ExtensionLoader<?>> EXTENSION_LOADERS = new ConcurrentHashMap<>();
也是就是说对于泛型T，**EXTENSION_LOADERS**结构缓存了T和T类型的扩展加载器。同时这个T必须是接口类型。


#### 重要方法**getAdaptiveExtension** ####
--- 
重新回顾最开始的一行代码，我们可以看到当获取到特定（T为Protocol.class）的扩展加载器后立马调用
**getAdaptiveExtension**方法。该方法也是扩展加载器中一个非常的重要的方法。

	public T getAdaptiveExtension() {
        Object instance = cachedAdaptiveInstance.get();
        if (instance == null) {
            if (createAdaptiveInstanceError == null) {
                synchronized (cachedAdaptiveInstance) {
                    instance = cachedAdaptiveInstance.get();
                    if (instance == null) {
                        instance = createAdaptiveExtension();
                        cachedAdaptiveInstance.set(instance);
                        //省略了异常代码
                    }
                }
            } 
        }
        return (T) instance;
    }
同样读者依然可以很明显的看出操作缓存的逻辑。因此我们需要的关注也就很明显了

1. 缓存结构**cachedAdaptiveInstance**，

2. 新建缓存对象的方法**createAdaptiveExtension**    

对于**cachedAdaptiveInstance**它是**对象属性**，先不做详细介绍，读者只要记住这是一个缓存属性。

对于获得缓存对象的方法**createAdaptiveExtension**，代码如下:

	T createAdaptiveExtension() {
        return injectExtension((T) getAdaptiveExtensionClass().newInstance());
    }
代码实现只有短短一行，重点不言而明。我们一一道来

####  方法**getAdaptiveExtensionClass** ####
---
该方法是一行代码重点关注之一，代码如下:

    private Class<?> getAdaptiveExtensionClass() {
        getExtensionClasses();
        if (cachedAdaptiveClass != null) {
            return cachedAdaptiveClass;
        }
        return cachedAdaptiveClass = createAdaptiveExtensionClass();
    }

代码也是十分简短的，这里我们需要关注的有两点，一是方法的内部实现，二是方法返回值，返回值我们可以看出是返回类类型，因而一行代码中的**newInstance**的代码实现自然可以忽略了。现在我们把关注点转移到方法内部实现。
	
#### 超级重点方法**getExtensionClasses** ####
---
该函数是一个超级重要的方法，在上面方法内部实现调用，实际上也被广泛在其他方法中调用。该方法完成了dubbo配置类的加载，实现了对泛型T结构的实现类的类加载和管理，代码如下:

	 Map<String, Class<?>> getExtensionClasses() {
        Map<String, Class<?>> classes = cachedClasses.get();
        if (classes == null) {
            synchronized (cachedClasses) {
                classes = cachedClasses.get();
                if (classes == null) {
                    classes = loadExtensionClasses();
                    cachedClasses.set(classes);
                }
            }
        }
        return classes;
    }
这个方法是私有的，再次强调该方法是很多操作的**基石**。 逻辑也是缓存操作，同上我们需要关注缓存结构**cachedClasses**，和获得缓存对象的方法**loadExtensionClasses** 

- 字段 **cachedClasses**是**对象属性**，跟之前**cachedAdaptiveInstance**一样，读者先记住是缓存属性

- 方法 **loadExtensionClasses**，其功能主要实现加载相关实现类，新建为缓存对象

#### 超级重点方法**loadExtensionClasses** ####
--- 
	private Map<String, Class<?>> loadExtensionClasses() {

        /*
        ...省略部分代码
        获得具体T类型（type）上的@SPI注解，并尝试
        完成缓存属性**cachedDefaulName**值的设置，其值为@SPI的value值
        */

        Map<String, Class<?>> extensionClasses = new HashMap<String, Class<?>>();
        loadFile(extensionClasses, DUBBO_INTERNAL_DIRECTORY);
        loadFile(extensionClasses, DUBBO_DIRECTORY);
        loadFile(extensionClasses, SERVICES_DIRECTORY);
        return extensionClasses;
    }
以上是缩减的代码，省略的代码不是很重要，但完成了一个缓存属性的设置，重点是三个loadFile的调用。
首先loadFile的第二个参数，也就是配置文件路径为

- 所有jar包中的资源文件，文件路径:
	1. **"META-INF/dubbo/internal/"+泛型T的全类名**
	2. **"META-INF/dubbo/"+泛型T的全类名**
	3. **"META-INF/services/"+泛型T的全类名**

> loadfile方法内部做了很多操作，总体是完成了配置文件中的内容，到内存缓存属性的生成和设置。我们接下来以简要的文字描述其功能。

#### loadFile的逻辑 ####
---
本质是kv（k-->name；v-->类全称）的解析，目的是为了完成对**T类型扩展加载器**，几个**重要缓存对象属性**的设置，这里我们以文字的形式详细描述下：

- 对kv对中的v进行反射生成类类型，v必须是T类型的实现类

对T的实现类带有注解@Adaptive的处理:

	1. 将cachedAdaptiveClass设置为该类型
	2. n次loadFile的调用，对应T具体类型，只允许一个带有**@Aaptive**的实现
	3. dubbo中默认只有AdaptiveCompiler，AdaptiveExtensionFactory符合
	4. 其他T类型，在程序中都会使用**createAdaptiveExtensionClass方法**生成。   

对T的实现类不带有注解@Adaptive的处理:

	1. 实现类有构造函数,这些类型被加入缓存属性**cachedWrapperClasses**中
		- 例如A是T的实现，A存在构造签名: public A(T t){...}

对T的普普通通的实现类的处理:
	1. 带有注解@Activate的实现类，将其name，类上注解加入缓存属性cachedActivates中
	2. 将实现类型和name加入缓存cachedNames中	
	3. 返回name，v类型组成map

由于历史遗留和扩展性问题，这里的kv形式的k是有多种形式的,有以下几种配置方法:

1. com.codeL.dubbo.CodeLDemo
2. aa=com.codeL.dubbo.CodeLDemo
3. aa,bb=com.codeL.dubbo.CodeLDemo

对于第一种方式，我们这里提一下，这种已经被废弃了。

- 会尝试使用实现类上的注解@Extension的value值，如果value是空串，尝试用**小写名**,如果提取不了**小写名**就报错
- 没有注解则尝试用**小写名**,如果提取不了**小写名**，则用类名小写
	
**小写名**提取规则：ex，type=Inovker，v=SimpleInovker。小写名等价于simple，两者，后缀必须匹配。

对于第二和第三种方式，都是使用正常的方式

最终得到的k会被‘,’分割多个形成名字数组，然而这种方式仅仅给**T的普普通通的实现类**使用

---

### name的选择 ###

有多个name，**T的普普通通的实现类**使用的name也是有选择的。

对于1方式，该name是每次执行loadFile方法，有多个name的第一项。   
对于2方式，该name是多次执行loadFile方法，对于实现类的多个name的最早的第一项。   
对于3方式，任意执行loadFile方法，每一个name都放置一次。  

---
至此loadFile加载配置文件到内存类类型的设置就完成了。
	
回头看**loadExtensionClasses**，我们看到执行了多次    loadFile，且用一个Map,也就是loadFile的第一个参数，保存了**T的普普通通的实现类**也就是方式3的结果。

再从方法栈向外跳出，也就是**getExtensionClasses**方法里面，我们看到了这个map赋值给了缓存属性**cachedClasses**

#### 小结
---
上面大量分析后，我们可以得出，基石操作**getExtensionClasses**一次调用，对T具体类扩展加载器做了很多事情，我们简单归纳:

- **cachedClasses**:**T的普普通通的实现类**方式3的超集

- **cachedDefaultName**:T类型上@SPI的value值（非空串），不能含有分隔符‘,’。

- **cachedAdaptiveClass**:@Adaptive的实现类类型，具体T类型的所有实现，只允许一个带有@Adaptive注解

- **cachedWrapperClasses**：诸如: public A(T t){}的实现类

- **cachedActivates**：单次loadFile后，name和带有注解@Activates的实现类的映射

- **cachedNames**:见**name的选择**方式2

>**tip**:这个方法调用，保存和构建的只是类型，而不是实例。 


#### 再谈**getAdaptiveExtensionClass**
---

上面我们介绍了基石操作**getExtensionClasses**，现在回顾最早出现的问题，为了读者阅读方便，我再次粘贴了源码

	private Class<?> getAdaptiveExtensionClass() {
        getExtensionClasses();
        if (cachedAdaptiveClass != null) {
            return cachedAdaptiveClass;
        }
        return cachedAdaptiveClass = createAdaptiveExtensionClass();
    }
我们可以看到**getAdaptiveExtensionClass**就是返回缓存属性**cachedAdaptiveClass**，再根据我们上文所说，默认类上带有
@Adaptive注解的只有

	AdaptiveCompiler，对应T类型:Compiler,扩展加载器ExtendLoader<Compiler>
	AdaptiveExtensionFactory,对应T类型:ExtensionFactory,扩展加载器ExtendLoader<ExtensionFactory>
因此，其他的扩展都是通过**createAdaptiveExtensionClass**生成的。


### createAdaptiveExtensionClass()程序生成代码 ###

---
上面我们说到除了T类型为Compiler和ExtensionFactory有配置生成，其他都是程序运行生成，也就是方法**createAdaptiveExtensionClass()**，为什么要程序生成呢。因为有点灵活。因此其代码也是很恶心的，这里我们文字描述。

代码逻辑在**createAdaptiveExtensionClassCode**中

1. 遍历接口方法，对带有@Adaptive注解方法进行**特殊实现**，对不带有Adaptive进行**默认实现**(扔出异常UnsupportedOperationException)

>T具体接口的方法签名上，至少有一个带有@Adaptive注解，且该签名至少存在一个参数URL类型，或者某个参数能通过get方法获得URL类型。

我们继续描述下特殊实现。

- 尝试寻找方法参数URL，并设定位置urlTypeIndex
	- 对参数url的判空校验
	- 使用局部变量接收该url
- 尝试寻找方法的参数的get方法获得URL，并设定位置urlTypeIndex
	- 对参数的判空校验
	- 对参数的获得URL的get方法判空校验
	- 使用局部变量接收url
- 获得@Adaptive注解的value数组，没有生成一个String[]{接口名的改造（接口名为CodelDemo改造为codel.demo)}
- 寻找com.alibaba.dubbo.rpc.Invocation类型的参数，如果存在进行，如下操作
	- 对该参数进行判空校验，
	- 使用局部变量methodName获得该参数的getMethodName()返回值

### 获得最终扩展名 ###

---
Adaptive的实现类，最终是通过一些信息生成获得，最终的普通实现类。代码最重要的也就是如何获得普通实现类。本点是继续上面的特殊实现:

- 使用局部变量**defaultExtName**接收缓存属性**cachedDefaultName**
- 使用局部变量**getNameCode**接收**defaultExtName**
- 倒序遍历value数组，产生代码 
	- 数组元素和字符串protocol不等
		- 有Invocation参数   
			- getNameCode = url.getMethodParameter(methodName, 该项值, defaultExtName)  
		- 无Invocation参数  
			- getNameCode = url.getParameter(该项值, getNameCode)    
	- 数组元素和字符串protocol不等  
		- getNameCode = ( url.getProtocol() == null ? getNameCode : url.getProtocol() )  
- 使用局部变量**extName**，获得最终的扩展名（来自getNameCode）
- 对extName判空校验
- 使用局部变量**extension**，通过T类型具体扩展加载类getExtension()获得最终的扩展类（**T的普通实现**）
- 调用扩展类的的该实现方法，返回方法返回值。

至此，动态生成的cachedAdaptiveClass已经完成，这里缺少编译，我们一笔带过，dubbo支持jdk和javassistComplier，默认是后者，一般我们也不会对其扩展。

### 小结 ###

---
我们上面说了很多，也描绘了很多事情，最终都只是完成了配置文件到类类型的加载。类型实例化也没有讨论，
现在让我们把目光转向最开始的地方，也就是**getAdaptiveExtension**,本文的最开始部分。从方法签名来看，
返回是实例对象而不是类对象。然后我们引出了**createAdaptiveExtension**方法。

	 private T createAdaptiveExtension() {
        //获得封装扩展的实例，并注入。忽略了try，catch代码
        return injectExtension((T) getAdaptiveExtensionClass().newInstance());
    }
为了读者阅读的简便性，我们再次贴出。我们看到了我们熟悉的getAdaptiveExtensionClass()方法，也就是返回缓存属性cachedAdaptiveClass。

一行代码我们看出，她对cachedAdaptiveClass持有的类类型进行实例化，然后作为参数传入方法**injectExtension**中。

### 重点方法之**injectExtension** ###

---
这是个极为重要的方法，他的主要功能就是完成实例的“初始化”（相关的属性的注入如同spring中的bean有多个阶段）。代码如下:

  	private T injectExtension(T instance) {
         if (objectFactory != null) {
            for (Method method : instance.getClass().getMethods()) {
    			if (method.getName().startsWith("set")
                            && method.getParameterTypes().length == 1
                            && Modifier.isPublic(method.getModifiers())) {
                        Class<?> pt = method.getParameterTypes()[0];
                        String property = method.getName().length() > 3 ? method.getName().substring(3, 4).toLowerCase() + method.getName().substring(4) : "";
                        Object object = objectFactory.getExtension(pt, property);
                        if (object != null) {
                            method.invoke(instance, object);
                        }
                    }
                }
            }
        return instance;
    }

该方法针对T不为ExtenionFactory扩展加载器类型，那是因为

- T为ExtenionFactory，其**objectFactory**为空
- T不为ExtenionFactory，其**objectFactory**为**AdaptiveExtensionFactory**实例

>**tip**：代码见**getExtensionLoader**方法（关于获得T具体的扩展加载类型）

最后该方法的逻辑如下: 

1. 遍历所有的公共的，set开头，参数长度为1的方法
2. 获得属性名字name，通过解析set方法名中取得。
3. 通过AdaptiveExtensionFactory获得相关T的Adaptive扩展
	- 这个factory本身委托给了其他普通实现类型（SpiExtensionFactory，SpringExtensionFactory）顺序处理
		- SpiExtensionFactory加载
			- 尝试获得T为pt类型的扩展加载器，然后加载其所有配置对应类型，获得其Adaptive扩展(进入递归)
			- **tip**:加载框架内部结构，存在循环依赖，二次开发需要避免，否则死锁。
		- SpringExtensionFactory加载(如果在spring环境中，这个时候不一定是Adaptive扩展)
			- 直接从spring容器获得bean
4. 反射将的工厂中得到的对应实例注入。
5. 返回本实例。

### 最后的**getAdaptiveExtension** ###

---
上述过程之后，cachedAdaptiveClass实例化出来的对象完成，完整的初始化，接着完成了缓存属性****cachedAdaptiveInstance****，读者读到这里因而能知道，这个过程是递归的，如同上面所说，读者二次开发
需要注意循环依赖的问题。如果是Spring暴露出来的，那么没有任何问题。

### 再谈dubbo复杂配置类 ###

---
到此，最开始我所说的一行代码已经解决，我们这里重新贴一遍代码

- ServiceConfig（服务方的入口：**复杂配置类**）
	- 提供者必须使用的复杂配置类
		- **复杂类属性之一**：
	
			  private static final Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();

其做的工作我们都说明了，唯一遗漏的几点:

1. 由于类final属性，在ServiceConfig被JVM装载的时候就开始调用了。

2. protocol持有的是Protocl$Adaptive，其他复杂类属性类似

3. 想要看具体的程序生成Adaptive扩展，调试应用的时候，请扣出来。

### 总结
---
该文中，我们只围绕了复杂配置类的复杂类属性展开，由此迁出了dubbo的配置运行流程以及知识点。
正如我最后所说，这些仅仅操作发生在ServiceConfig类的字节码被装载进JVM就开始，真正的服务开启，消费开发还远着呢。
下一篇，我们将揭开dubbo导出服务之旅。