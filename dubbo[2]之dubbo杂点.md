##dubbo 杂点
dubbo杂点，描述的是一些的零散的知识，上一篇**dubbo配置**我们留的疑问我们也将在该篇文章中一一解答。

虽然是杂点，但是只是名字上的而已，知识点还是比较重要的，这里会引出一个jdk中存在的一个概念。阅读的时候，读者应该尽量参考源码，[dubbo源码地址](http://192.168.110.114/laihj/dubbo "dubbo源码地址")。源码加上了相关的注释。

###dubbo复杂配置类
---
**dubbo配置**一文，我们已经向读者介绍了什么是**复杂配置类**，这里我们再次从服务提供者的角度来说明，消费方类似:

- ServiceConfig（服务方的入口：**复杂配置类**）
	- 提供者必须使用的复杂配置类
		- **复杂类属性之一**：
	
			    private static final Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();

上一篇文章中，对于上面的代码，我们让读者留有印象即可，而现在正是我们本文议论的出发点，本文也是围绕着该行代码展开。

### 超级重要的ExtensionLoader类
---
上面一行代码中我们注意到这样一个类**ExtensionLoader**。 在dubbo框架中，这个类地位举足轻重，dubbo也是依靠该类进行扩张。在这里，我给它取个名字:**扩展加载器类**。

>**tip**: ***阅读该类务必对照源码，源码已加丰富注释，促进理解***

首先我们先贴出类声明，类声明结构如下:

	public class ExtensionLoader<T>{...}

从声明结构我们可以看出，其带有泛型结构T，这里称之为T类型扩展加载器。

####重点方法**getExtensionLoader**
---
上述代码里，我们看到了该方法。这是**扩展加载器类**的一个非常重要的方法。
我们将对其详细深究，首先先贴出其实现代码。
	
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

以上就是**getExtensionLoader**内部实现代码。
同行人一看就知道是缓存操作，重点**EXTENSION_LOADERS**这个缓存结构。  

**EXTENSION_LOADERS**是**类属性**，说明如下:

	//全局，缓存了interface（这个class特指interface），与ExtensionLoader（interface的特殊实现类）的映射
    ConcurrentMap<Class<?>, ExtensionLoader<?>> EXTENSION_LOADERS = new ConcurrentHashMap<>();
也是就是说对于泛型T，**EXTENSION_LOADERS**结构缓存了T和T类型的扩展加载器。同时这个T必须是接口类型。

####重要方法**getAdaptiveExtension**
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
                        try {
                            instance = createAdaptiveExtension();
                            cachedAdaptiveInstance.set(instance);
                        } catch (Throwable t) {
                            createAdaptiveInstanceError = t;
                            throw new IllegalStateException("fail to create adaptive instance: " + t.toString(), t);
                        }
                    }
                }
            } else {
                throw new IllegalStateException("fail to create adaptive instance: " + createAdaptiveInstanceError.toString(), createAdaptiveInstanceError);
            }
        }
        return (T) instance;
    }
同样读者依然可以很明显的看出操作缓存的逻辑。因此我们需要的关注也就很明显了

1. 一是缓存结构**cachedAdaptiveInstance**，

2. 二是新建缓存对象的方法**createAdaptiveExtension**    

对于**cachedAdaptiveInstance**它是**对象属性**，先不做详细介绍，读者只要记住这是一个缓存属性。

对于获得缓存对象的方法**createAdaptiveExtension**，代码如下:

		T createAdaptiveExtension() {
	            return injectExtension((T) getAdaptiveExtensionClass().newInstance());
	    }
代码实现只有短短一行，重点不言而明。我们一一道来

####方法**getAdaptiveExtensionClass**
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
	
####超级重点方法**getExtensionClasses**
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

- 方法	**loadExtensionClasses**，其功能主要实现加载相关实现类，新建为缓存对象

####超级重点方法**loadExtensionClasses**
--- 
该方法涉及代码比较多，嵌套比较深，这里我们不贴出说明，读者请依照源码跟进。  
我们采用文章描述来介绍其功能：

1. 获取type上的@SPI注解，完成缓存属性**cachedDefaulName**值的设置，其值为@SPI的value值（非空串，不含分隔符‘，’）
	1. type是新建ExtensionLoader时，对应的泛型T

2. 解析所有jar包中的资源文件，文件路径依次为
	1. **"META-INF/dubbo/internal/"+type.getName()**
	2. **"META-INF/dubbo/"+type.getName()**
	3. **"META-INF/services/"+type.getName()**

对配置处理是较复杂的操作，本质上是kv（k-->name；v-->类全称）的解析，解析处理中，**最重要**的是完成了ExtensionLoader<T>该扩展加载器，几个**重要缓存对象属性**的设置，这里我们以文字的形式详细描述下：

- 对每一个kv对的v进行反射生成类类型
	- **tip**:v对应类型必须实现type接口
- 对带有注解**@Adaptive**的类类型，将**cachedAdaptiveClass**设置为该类型
	- **tip**:v的类型（所有符合的资源文件中的kv），只允许一个带有**@Aaptive**的实现
- 对不含**@Adaptive**的类型，但其构造函数存在（参数只有T类型）
	- 这些v的类型被加入到属性**cachedWrapperClasses**中
- 普通的v类型
	- 判断v类型是否有**@Activate**，对于含有注解的将其name，和类型注解加入属性**cachedActivates**中
		- **tip**:k可以代表多个名字，比如k=aa,bb,cc。那么**cachedActivates**中指name的是第一项aa
		- **tip**:k也可以不存在，那么会尝试生成
			- v类型上注解Extension的value值，如果value是空串，就是下面的小写名
			- 没有注解则是小写名：ex，type=Inovker，v=SimpleInovker。小写名等价于simple
	- 将v，name设置进cachedNames中
		- name也是有多个名字所有情况下的第一个
	- 返回name，v类型组成map
		- 这个name多个名字会变成多个key。
		- 这个map会被设置进cachedClasses中
			- key是name，value是v
				
#### 小结
---

我们这里可以归纳下，**getExtensionClasses**一次调用，对ExtensionLoader<T>具体实例的完成了重要的相关属性设置，主要如下：

- **cachedClasses**:资源文件中kv对形成的name和类型的键值对，k可能解析会被成多个name，v是普通实现。

- **cachedDefaultName**:type类型上@SPI注解的value值（非空串），不能含有分隔符‘,’。

- **cachedAdaptiveClass**:带有@Adaptive的v类型，同T类型对应的所有jar包下相关资源文件中扫描后只能有一个@Adaptive的v类型

- **cachedWrapperClasses**：能充当T类型静态代理的包装v类型，构造函数有T

- **cachedActivates**：name（k拆分多个名字那么name为第一个），和普通v类型（带有@Activate注解）

- **cachedNames**:普通v类型（包括有无@Activate注解）和k对应名字第一个name，k为所有type对应资源文件的第一个k。

>**tip**:这个方法调用，保存只是相关的扩展类的类类型，而不是对象实例。 
>其中**cachedClasses**上面已经提到过，现在对其做了解释

#### 再谈**getAdaptiveExtensionClass**
---
上面我们详细的介绍了的超级重要方法 **getExtensionClasses**。主要是一些缓存属性的设置，其中**cachedAdaptiveClass**正是**getAdaptiveExtensionClass**中会使用的。  

### 插曲之@Adaptive注解
---
这个是dubbo中一个比较重要的注解，我们上面也提到了很多次，这个注解。我们现在来详细的了解说明下。


	
	/**
	 * 在{@link ExtensionLoader}生成Extension的Adaptive Instance时，为{@link ExtensionLoader}提供信息。
	 * @see ExtensionLoader
	 * @see URL
	 */
	@Documented
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.TYPE, ElementType.METHOD})
	public @interface Adaptive {
	    
	    /**
	     * 从{@link URL}的Key名，对应的Value作为要Adapt成的Extension名。
	     * <p>
	     * 如果{@link URL}这些Key都没有Value，使用 用 缺省的扩展（在接口的{@link SPI}中设定的值）。<br>
	     * 比如，<code>String[] {"key1", "key2"}</code>，表示
	     * <ol>
	     * <li>先在URL上找key1的Value作为要Adapt成的Extension名；
	     * <li>key1没有Value，则使用key2的Value作为要Adapt成的Extension名。
	     * <li>key2没有Value，使用缺省的扩展。
	     * <li>如果没有设定缺省扩展，则方法调用会抛出{@link IllegalStateException}。
	     * </ol>
	     * <p>
	     * 如果不设置则缺省使用Extension接口类名的点分隔小写字串。<br>
	     * 即对于Extension接口{@code com.alibaba.dubbo.xxx.YyyInvokerWrapper}的缺省值为<code>String[] {"yyy.invoker.wrapper"}</code>
	     * 
	     * @see SPI#value()
	     */
	    String[] value() default {};
	    
	}

读者有兴趣可以看一下上面的，注释。不过和这里我们并没有任何相关联系，我们上面说到配置文件中实现类中要携带@Adaptive注解，我们简单搜一下全文，也就能发现，两个类实现类上标注了该注解。tip:不是接口，也不是在方法上标注。具体如下:

- AdaptiveCompiler
- AdaptiveExtensionFactory  

这里读者先留存印象，不需要思考，事实该注解另有所用。  
该注解也提到了另一个注解@SPI，这个注解也是十分重要的注解。

### 插曲之@SPI注解
---
@SPI注解同样是一个比较重要的注解，其代码如下所示

	/**
	 * 扩展点接口的标识。
	 * <p />
	 * 扩展点声明配置文件，格式修改。<br />
	 * 以Protocol示例，配置文件META-INF/dubbo/com.xxx.Protocol内容：<br />
	 * 由<br/>
	 * <pre><code>com.foo.XxxProtocol
	com.foo.YyyProtocol</code></pre><br/>
	 * 改成使用KV格式<br/>
	 * <pre><code>xxx=com.foo.XxxProtocol
	yyy=com.foo.YyyProtocol
	 * </code></pre>
	 * <br/>
	 * 原因：<br/>
	 * 当扩展点的static字段或方法签名上引用了三方库，
	 * 如果三方库不存在，会导致类初始化失败，
	 * Extension标识Dubbo就拿不到了，异常信息就和配置对应不起来。
	 * <br/>
	 * 比如:
	 * Extension("mina")加载失败，
	 * 当用户配置使用mina时，就会报找不到扩展点，
	 * 而不是报加载扩展点失败，以及失败原因。
	 *
	 * @author william.liangf
	 * @author ding.lid
	 * @export
	 */
	@Documented
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.TYPE})
	public @interface SPI {
	
	    /**
	     * 缺省扩展点名。
	     */
		String value() default "";
	
	}

同样对于该注解，读者留存印象即可，后面会详细展开。

#### 三说**getAdaptiveExtensionClass**
---
上面我们说到该方法返回是个从配置文件中获取的缓存（**cachedAdaptiveClass**），但是配置文件中没有一个实现类有这个注解怎么办。这时候我们看到了

	return cachedAdaptiveClass = createAdaptiveExtensionClass();

简单一行代码，完成了缓存属性**cachedAdaptiveClass**的设置，事实上dubbo是这样做的，他会根据type来在程序中产生相应的代码，然后使用编译器去编译它。没错，就是运行产生。具体流程如下描述
- 遍历type类型的所有方法
- 对带有@Adaptive注解方法进行实行
- 对不带有Adaptive进行默认实现，扔出异常

>**tip**:type至少在多个方法上，至少存在一个带有@Adaptive注解，否则程序就崩溃了。
>**tip**:带有@Adaptive注解方法，至少存在一个参数URL类型，或者某个参数能通过get方法获得URL类型，否则程序就崩溃了。

我们继续描述下对带有@Adaptive注解的方法如何实现的。

- 尝试寻找方法参数URL，并设定位置urlTypeIndex
	- 写入相关代码
- 尝试寻找方法的参数的get方法获得URL，并设定位置urlTypeIndex
	- 写入相关代码
- 获得@Adaptive注解的value值
	- tip:这个时候，读者请移到上文，看注解的注释，这里的value值就是注释说明的
- 寻找com.alibaba.dubbo.rpc.Invocation类型的参数，写入校验代码
- 对扩展名做处理，处理结果见@Adaptive注解
- 其余请对照源码，和我的代码集合

描述了如何生成代码后，系统之间使用**AdaptiveCompiler**对象编译返回了。
实际参与编译是**JavassistCompiler**类默认情况。  
到这里所有Adaptive扩展类类型都完成了。

#### 四说**getAdaptiveExtensionClass**
---
对于**cachedAdaptiveClass**要么从配置文件获得，要么程序生成，最终**Adaptive扩展类类型**已经拿到手了。
>**tip**：**对于配置文件获得只有AdaptiveCompiler和AdaptiveExtensionFactory**   
>**tip**：**这只是个类类型**

接下来就是**Adaptive扩展类**进行实例化，我们看到代码直接**newInstance**，然后调用了方法**injectExtension**。

###重点方法之**injectExtension**
---
这是个非常重要的方法，他对实例相关属性，进行了设置，注入之后才是一个完整的初始化后的依赖。代码如下:

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

该方法针对除```ExtensionLoader<ExtenionFactory>```外的所有扩展加载器类型，其中一个重要对象属性**objectFactory**，其说明如下:

	//T为ExtensionFactory类实例(应用单例)该项为空
    //others:AdaptiveExtensionFactory实例(单例)
    private final ExtensionFactory objectFactory;

**tip**：这个方法一旦被执行，objectFactory总是AdaptiveExtensionFactory实例。给属性细心的读者之前肯定看到了，这里我们对其做了说明。

最后该方法的逻辑如下: 

1. 遍历所有的公共的，set开头，参数长度为1的方法
2. 获得属性名字name，通过解析set方法名中取得。
3. 通过AdaptiveExtensionFactory获得
	- 这个factory本身委托给了其他v为ExtensionFactory普通实现类型，关于v的普通类型，请看前文介绍
	- 假设在spring应用中，那么会委托给SpiExtensionFactory，SpringExtensionFactory执行。
		- SpiExtensionFactory加载
			- 尝试获得pt类型的扩展加载器，然后加载所有配置扩展,在普通配置扩展有的情况下，触发加载重点方法之**getAdaptiveExtension**进行递归，取得Adaptive扩展
			- **tip**:这种方式是加载框架内部的配置
			- **tip**:这里有循环依赖的问题，dubbo团队内部配置类之间的关系没有循环依赖，改造代码时，一旦循环依赖，这里将出现问题，死锁
		- SpringExtensionFactory加载
			- 直接从spring容器获得bean
4. 使用反射将从容器取得的扩展实例适配注入到该实例中。
5. 返回实例。

### 最后**getAdaptiveExtension**
---
上面的方法返回之后，然后对**cachedAdaptiveInstance**完成缓存，现在读者应该对我上文让读者忽略的**cachedAdaptiveInstance**熟悉了。

### 再谈dubbo复杂配置类
---
我们这里重新贴一遍代码

- ServiceConfig（服务方的入口：**复杂配置类**）
	- 提供者必须使用的复杂配置类
		- **复杂类属性之一**：
	
			    private static final Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();

重要的类属性，相关的说明，我们已经做了详细的介绍。值得说明的是，由于是类final属性，在ServiceConfig被JVM装载的时候就开始调用了。

- protocol持有的是Protocl$Adaptive，其他复杂类属性类似

重点是程序生成的类，具体见我的资料，里面有这些类的代码。

### 总结
---
在该文中，我们其实只围绕了复杂配置类的复杂类属性展开，中间牵涉了部分的dubbo的配置运行流程以及知识点。正如我们所说，这些仅仅操作发生在ServiceConfig类的字节码被装载进JVM就开始，真正的服务还远着呢。下一篇，我们将揭开dubbo导出服务的过程。

