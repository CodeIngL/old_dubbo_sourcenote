## dubbo真正的导出 ##
在上一篇文章中，我们详细的讲述了dubbo服务的"导出"，细心的读者可能注意到所谓的其实都是元数据的生成，并没有涉及王网络编程相关的知识点，毕竟dubbo是rpc框架，我们却是没看到任何网络编程的影子。在上一篇的末尾，代码上其实进行了网络导出，短短的几行代码，屏蔽了大量的细节。

### dubbo导出代码 ###
---
我们先贴出上篇文章末尾，分装网络导出的细节的代码，如下所示

	Invoker<?> invoker = proxyFactory.getInvoker(ref, (Class) interfaceClass, registryURL.addParameterAndEncoded(Constants.EXPORT_KEY, url.toFullString()));
    Exporter<?> exporter = protocol.export(invoker);

上述代码是使用远程注册中心构建的，与本地构建本质上一样。现在我们对这两行代码进行详细说明:


### 获得Invoker ###
----------

Invoker是一种概念，RPC框架设计时的概念。对于这个概念，我们暂时不需要深究，后面我们将讨论RPC中一些设计。

	Invoker<?> invoker = proxyFactory.getInvoker(ref, (Class) interfaceClass, registryURL.addParameterAndEncoded(Constants.EXPORT_KEY, url.toFullString()));

该行代码是上面的第一行代码，参数看起来有点杂乱，我们首先来看参数:

1. 第一个入参：ref，即服务导出类具体实现类
2. 第二个入参：interfaceClass，即服务导出接口(ref是其实现类)
3. 第三个参数：registryURL.addParameterAndEncoded(Constants.EXPORT_KEY, url.toFullString()) 
	- 上一篇文章末尾提到，registryURL是注册配置类URL，而协议配置类URL转换为注册配置类URL中的一个键值对形式（export，协议配置URL）

讨论完参数，我们在来看聚焦该方法所属接口：ProxyFactory。

	@SPI("javassist")
	public interface ProxyFactory {
	
		/**省略其他接口方法**/
	
	    @Adaptive({Constants.PROXY_KEY})
	    <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url) throws RpcException;
	
	}

以上就是接口定义，我们可以看到**getInvoker**是一个泛型的方法，只有三个参数，参数意义抽象如下:

1. 实例对象proxy
2. 实例对象的类类型
3. url(元信息)

我们再来看下方法的调用者是具体是什么

	private static final ProxyFactory proxyFactory = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();

从代码中，我们看到了十分熟悉的样板代码(dubbo杂点)。得到结论是:proxyFactory其实"引用"ExtensionLoader<ProxFactory>的缓存属性cachedAdaptiveInstance持有的实例。而该实例的类类型是缓存属性**cachedAdaptiveClass**。

值得一提的是，前文(dubbo杂点)我们已经提过，对于**T类型的扩展加载器**，dubbo中只有两个扩展加载器，其对应的**cachedAdaptiveClass**属性是配置好的:

1. ExtensionLoader<Compiler>扩展加载器的cachedAdaptiveClass为AdaptiveCompiler
2. ExtensionLoader<ExtensionFactory>扩展加载气得cachedAdaptiveClass为AdaptiveExtensionFactory

其他类型的扩展加载器，都是由程序生成的。为了加深读者印象，针对我们关注的方法签名，来展现其代码实现。根据dubbo杂点一文对应的方法实现如下:

	if(arg2 == null) throw new IllegalArgumentException("url == null");
	URL url = arg2;
	String extName = url.getParameter("proxy", "javassist");
	if(extName == null) throw new IllegalStateException("Fail to get extension(Lcom.alibaba.dubbo.rpc.ProxFacotry) name from url(" + url.toString() + ") use keys(javassist)");
	Lcom.alibaba.dubbo.rpc.ProxFacotry extension = (Lcom.alibaba.dubbo.rpc.ProxFacotry)ExtensionLoader.getExtensionLoader(Lcom.alibaba.dubbo.rpc.ProxFacotry.class).getExtension(extName)
	return extension.getInvoker(arg0,arg1,arg2);

有了代码，自然更好的理解作者的意图。不难发现其目的是通过ExtensionLoader<ProxyFactory>通过**getExtension**方法获得扩展类，并执行扩展类对应方法。而具体是哪个扩展类取决于url中的信息。

可以看出来的结果是"javassist"对应的扩展器。而对整个源码进行搜索后，我们发现了:

	javassist=com.alibaba.dubbo.rpc.proxy.javassist.JavassistProxyFactory

一行配置，勾起了我们之前介绍的配置加载的回忆(dubbo杂点)。那么具体的扩展类应该是JavassistProxyFactory了，然而**getExtension**方法隐藏了一些秘密，通过解析该方法我们可以知道其实还是做过处理的。

> tip：上述代码是编译前代码，用来生成类的代码，不是运行时代码。

### **getExtension**的秘密处理 ###
---
本小点知识点应该作为dubbo杂点一文的补充。在dubbo杂点一文中我们提到了，加载配置文件后，相应的扩展器维持了很多信息，但是基本上是类型信息，而不是类型实例信息。因而读者可能以为上面的具体扩展类就是JavassistProxyFactory了。现在通过分析**getExtension**进一步加深读者对扩展加载器的理解。

	public T getExtension(String name) {
		/**
		* 省略了对name的校验部分代码
		*/
		if ("true".equals(name)) {
			return getDefaultExtension();
        }
        Holder<Object> holder = cachedInstances.get(name);
        if (holder == null) {
            cachedInstances.putIfAbsent(name, new Holder<Object>());
            holder = cachedInstances.get(name);
        }
        Object instance = holder.get();
        if (instance == null) {
            synchronized (holder) {
                instance = holder.get();
                if (instance == null) {
                    instance = createExtension(name);
                    holder.set(instance);
                }
            }
        }
        return (T) instance;
    }

以上就是方法的代码实现，我们还是很明显的看到了缓存操作的痕迹，我们简单来说明整个逻辑

1. name=true,使用**getDefaultExtension**，其等价于getExtension(cacheDefaultName),cacheDefaulteName来自@SPI注解
2. 缓存操作，有则取，无则新建放入缓存

当然我们主要关注的是新建缓存的操作（**createExtension**）

	private T createExtension(String name) {
		Class<?> clazz = getExtensionClasses().get(name);
		if (clazz == null) {
			throw findException(name);
		}
		T instance = (T) EXTENSION_INSTANCES.get(clazz);
		if (instance == null) {
			EXTENSION_INSTANCES.putIfAbsent(clazz, (T) clazz.newInstance());
			instance = (T) EXTENSION_INSTANCES.get(clazz);
		}
		injectExtension(instance);
		Set<Class<?>> wrapperClasses = cachedWrapperClasses;
		if (wrapperClasses != null && wrapperClasses.size() > 0) {
			for (Class<?> wrapperClass : wrapperClasses) {
				instance = injectExtension((T) wrapperClass.getConstructor(type).newInstance(instance));
			}
		}
		return instance;
	}

以上是新建缓存对象的代码，在这里我们看到了很熟悉的方法，比如**getExtensionClasses**,**injectExtension**。其逻辑如下

1. 尝试加载扩展类信息，通过name获得相应的扩展类类型
2. 对获得类类型和其实例化对象进行缓存操作，有则直接使用，无则直接生成实例化对象
3. 完成实例对象的属性注入(set注入)
4. 循环特殊的扩展类集合(cachedWrapperClasses)，进行对上面的实例对象层层包装，并完成包装对象的属性注入(set注入)

前三点我们在dubbo一文已经详细介绍过，唯一的区别就是第4点，然而在代码上并没有新的细节。

>tip:借鉴spring的话，这里应该提供相应的优先级来包装(值得改造的地方)

### **getExtension**秘密的结论 ###

----------
上面我们详细的解释了**getExtension**中做的事情，通过上面一系列的描述得到了如下的结论:

- 对于某个T类型的扩展加载器，其getExtension(name)得到配置文件中name=xxx，xxx对应的实例，如果T类型在配置文件中还存在存在包装类型实现话，需要将实例进行包装。


### 再谈"Javassist" ###

----------
上面我们介绍了getExtension的秘密处理后，我们现在把目光在转向ProxyFactory的程序生成代码。

对源码进行搜索，我们看到了这样一个类。

	public class StubProxyFactoryWrapper implements ProxyFactory {
		public StubProxyFactoryWrapper(ProxyFactory proxyFactory) {
	        this.proxyFactory = proxyFactory;
	    }
	}
读者应该有所猜测，的确StubProxyFactoryWrapper符合ProxyFactory包装类型，也就是说这个类会被添加到**cachedWrapperClasses**中。值得一提的是，对于ProxyFactory该类型仅有这一个类，也就意味着包装层次不深。我们来看其**getInvoker**实现。

	public <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url) throws RpcException {
        return proxyFactory.getInvoker(proxy, type, url);
    }

出乎意料的是，它什么也没做，直接让被包装对象来处理。被包装类自然是JavassistProxyFactory了。

	public <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url) {
		final Wrapper wrapper = Wrapper.getWrapper(proxy.getClass().getName().indexOf('$') != -1 ? proxy.getClass() : type);
		return new AbstractProxyInvoker<T>(proxy, type, url) {
			@Override
			protected Object doInvoke(T proxy, String methodName, 
				Class<?>[] parameterTypes, Object[] arguments) throws Throwable {
				    return wrapper.invokeMethod(proxy, methodName, parameterTypes, arguments);
			}
		};
	}

以上就是JavassistProxyFactory的代码实现，代码很简短，逻辑也很明白，但是还是有很深的细节:

1. 对入参进行包装产生wrapper实例，proxy的类类型不包含$(cglib生成)则对其类类型包装，否则对type类型包装
2. 新建AbstractProxyInvoker的匿名类实例，覆写doInvoke方法，调用wrapper的invokeMethod方法(java回调)

这里有一个Wrapper.getWrapper操作生成wrapper这个方法细心的读者可能在前面dubbo服务导出一文看到过，但我们没有做介绍，当然在这里我们要详细的展开。

### 插曲之Wrapper类 ###

----------
Wrapper类是一个特别的类，他也是负责生成代码的，其中她使用了javassist字节码操作技术。我们首先以getWrapper来展开描述:

		public static Wrapper getWrapper(Class<?> c){
	        while( ClassGenerator.isDynamicClass(c) )
	            c = c.getSuperclass();
	        if( c == Object.class )
	            return OBJECT_WRAPPER;
	        Wrapper ret = WRAPPER_MAP.get(c);
	        if( ret == null )
	        {
	            ret = makeWrapper(c);
	            WRAPPER_MAP.put(c,ret);
	        }
	        return ret;
	    }
代码很短，我们又看到了缓存操作的痕迹，没有过多解释，我们来看缓存的新建过程

#### 插曲之Wrapper类makeWrapper方法 ####

----------
该方法就是新建缓存的方法，我们来看看其内部到底做了什么事情。

	private static Wrapper makeWrapper(Class<?> c){

		if( c.isPrimitive() )
			throw new IllegalArgumentException("Can not create wrapper for primitive type: " + c);
		String name = c.getName();
		ClassLoader cl = ClassHelper.getClassLoader(c);
		StringBuilder c1 = new StringBuilder("public void setPropertyValue(Object o, String n, Object v){ ")
				.append(name).append(" w; try{ w = ((").append(name).append(")$1); }catch(Throwable e){ throw new IllegalArgumentException(e); }");;
		StringBuilder c2 = new StringBuilder("public Object getPropertyValue(Object o, String n){ ")
				.append(name).append(" w; try{ w = ((").append(name).append(")$1); }catch(Throwable e){ throw new IllegalArgumentException(e); }");
		StringBuilder c3 = new StringBuilder("public Object invokeMethod(Object o, String n, Class[] p, Object[] v) throws " + InvocationTargetException.class.getName() + "{ ")
				.append(name).append(" w; try{ w = ((").append(name).append(")$1); }catch(Throwable e){ throw new IllegalArgumentException(e); }");
		Map<String, Class<?>> pts = new HashMap<String, Class<?>>(); 
		Map<String, Method> ms = new LinkedHashMap<String, Method>();

		List<String> mns = new ArrayList<String>(); // method names.

		List<String> dmns = new ArrayList<String>(); // declaring method names.

		for( Field f : c.getFields() ){
			String fn = f.getName();
			Class<?> ft = f.getType();
			if( Modifier.isStatic(f.getModifiers()) || Modifier.isTransient(f.getModifiers()) )
				continue;
			c1.append(" if( $2.equals(\"").append(fn).append("\") ){ w.").append(fn).append("=").append(arg(ft, "$3")).append("; return; }");
			c2.append(" if( $2.equals(\"").append(fn).append("\") ){ return ($w)w.").append(fn).append("; }");
			pts.put(fn, ft);
		}
		Method[] methods = c.getMethods();
		boolean hasMethod = hasMethods(methods);
		if( hasMethod ){
		    c3.append(" try{");
		}
		for( Method m : methods ) {
			if( m.getDeclaringClass() == Object.class )
				continue;
			String mn = m.getName();
			c3.append(" if( \"").append(mn).append("\".equals( $2 ) ");
            int len = m.getParameterTypes().length;
            c3.append(" && ").append(" $3.length == ").append(len);
			boolean override = false;
			for( Method m2 : methods ) {
				if (m != m2 && m.getName().equals(m2.getName())) {
					override = true;
					break;
				}
			}
			if (override) {
				if (len > 0) {
					for (int l = 0; l < len; l ++) {
						c3.append(" && ").append(" $3[").append(l).append("].getName().equals(\"")
							.append(m.getParameterTypes()[l].getName()).append("\")");
					}
				}
			}
			
			c3.append(" ) { ");

			if( m.getReturnType() == Void.TYPE )
				c3.append(" w.").append(mn).append('(').append(args(m.getParameterTypes(), "$4")).append(");").append(" return null;"); //调用
			else
				c3.append(" return ($w)w.").append(mn).append('(').append(args(m.getParameterTypes(), "$4")).append(");");//调用

			c3.append(" }");
			
			mns.add(mn);

			if( m.getDeclaringClass() == c )
				dmns.add(mn);

			ms.put(ReflectUtils.getDesc(m), m);
		}

		if( hasMethod ){
		    c3.append(" } catch(Throwable e) { " );
		    c3.append("     throw new java.lang.reflect.InvocationTargetException(e); " );
	        c3.append(" }");
        }
		c3.append(" throw new " + NoSuchMethodException.class.getName() + "(\"Not found method \\\"\"+$2+\"\\\" in class " + c.getName() + ".\"); }");
		
		Matcher matcher;
		for( Map.Entry<String,Method> entry : ms.entrySet() )
		{
			String md = entry.getKey();
			Method method = (Method)entry.getValue();
			if( ( matcher = ReflectUtils.GETTER_METHOD_DESC_PATTERN.matcher(md) ).matches() )
			{
				String pn = propertyName(matcher.group(1));
				c2.append(" if( $2.equals(\"").append(pn).append("\") ){ return ($w)w.").append(method.getName()).append("(); }");
				pts.put(pn, method.getReturnType());
			}
			else if( ( matcher = ReflectUtils.IS_HAS_CAN_METHOD_DESC_PATTERN.matcher(md) ).matches() )
			{
				String pn = propertyName(matcher.group(1));
				c2.append(" if( $2.equals(\"").append(pn).append("\") ){ return ($w)w.").append(method.getName()).append("(); }");
				pts.put(pn, method.getReturnType());
			}
			else if( ( matcher = ReflectUtils.SETTER_METHOD_DESC_PATTERN.matcher(md) ).matches() )
			{
				Class<?> pt = method.getParameterTypes()[0];
				String pn = propertyName(matcher.group(1));
				c1.append(" if( $2.equals(\"").append(pn).append("\") ){ w.").append(method.getName()).append("(").append(arg(pt,"$3")).append("); return; }");
				pts.put(pn, pt);
			}
		}
		c1.append(" throw new " + NoSuchPropertyException.class.getName() + "(\"Not found property \\\"\"+$2+\"\\\" filed or setter method in class " + c.getName() + ".\"); }");
		c2.append(" throw new " + NoSuchPropertyException.class.getName() + "(\"Not found property \\\"\"+$2+\"\\\" filed or setter method in class " + c.getName() + ".\"); }");

		long id = WRAPPER_CLASS_COUNTER.getAndIncrement();
		ClassGenerator cc = ClassGenerator.newInstance(cl);
		cc.setClassName( ( Modifier.isPublic(c.getModifiers()) ? Wrapper.class.getName() : c.getName() + "$sw" ) + id );
		cc.setSuperClass(Wrapper.class);

		cc.addDefaultConstructor();
		cc.addField("public static String[] pns;"); // property name array.
		cc.addField("public static " + Map.class.getName() + " pts;"); // property type map.
		cc.addField("public static String[] mns;"); // all method name array.
		cc.addField("public static String[] dmns;"); // declared method name array.
		for(int i=0,len=ms.size();i<len;i++)
			cc.addField("public static Class[] mts" + i + ";");

		cc.addMethod("public String[] getPropertyNames(){ return pns; }");
		cc.addMethod("public boolean hasProperty(String n){ return pts.containsKey($1); }");
		cc.addMethod("public Class getPropertyType(String n){ return (Class)pts.get($1); }");
		cc.addMethod("public String[] getMethodNames(){ return mns; }");
		cc.addMethod("public String[] getDeclaredMethodNames(){ return dmns; }");
		cc.addMethod(c1.toString());
		cc.addMethod(c2.toString());
		cc.addMethod(c3.toString());

		try
		{
			Class<?> wc = cc.toClass();
			wc.getField("pts").set(null, pts);
			wc.getField("pns").set(null, pts.keySet().toArray(new String[0]));
			wc.getField("mns").set(null, mns.toArray(new String[0]));
			wc.getField("dmns").set(null, dmns.toArray(new String[0]));
			int ix = 0;
			for( Method m : ms.values() )
				wc.getField("mts" + ix++).set(null, m.getParameterTypes());
			return (Wrapper)wc.newInstance();
		}
		catch(RuntimeException e)
		{
			throw e;
		}
		catch(Throwable e)
		{
			throw new RuntimeException(e.getMessage(), e);
		}
		finally
		{
			cc.release();
			ms.clear();
			mns.clear();
			dmns.clear();
		}
	}

这个方法代码相当的长，我们先看一下这个类的类声明

	public abstract class Wrapper{...}
我们可以看到Wrapper是一个抽象类，同时上面这个方法返回的也是Wrapper类型，另外一点，这个方法是生成类的。自然生成了Wrapper子类。作为子类其要实现抽象父类的抽象方法。我们先列个清单关于该类的抽象方法:
	
	1. abstract public String[] getPropertyNames()
	2. abstract public Class<?> getPropertyType(String pn)
	3. abstract public boolean hasProperty(String name)
	4. abstract public Object getPropertyValue(Object instance, String pn) throws NoSuchPropertyException, IllegalArgumentException
	5. abstract public void setPropertyValue(Object instance, String pn, Object pv) throws NoSuchPropertyException, IllegalArgumentException
	6. abstract public String[] getMethodNames()
	7. abstract public String[] getDeclaredMethodNames()
	8. abstract public Object invokeMethod(Object instance, String mn, Class<?>[] types, Object[] args) throws NoSuchMethodException, InvocationTargetException

总共8个抽象方法，也就是说**makeWrapper**方法至少对这个8个方法进行了实现。
现在我们对上面这个方法进行拆分解释：

		if( c.isPrimitive() )
			throw new IllegalArgumentException("Can not create wrapper for primitive type: " + c);

该代码对入参进行校验，基本类型是不被允许的。

首先维护了一个字符串缓冲区**c1**代表上述第5个方法

	public void setPropertyValue(Object o, String n, Object v){ 	
		c的全类名 w;
		try {
			c的全类名 w = ((c的全类名) $1);
		} catch (Throwable e) {
			throw new IllegalArgumentException(e);
		}

接着维护了一个字符串缓冲区**c2**代表上述第4个方法

	public void getPropertyValue(Object instance, String pn, Object pv){ 	
		c的全类名 w;
		try {
			c的全类名 w = ((c的全类名) $1);
		} catch (Throwable e) {
			throw new IllegalArgumentException(e);
		}

然后维护了一个字符串缓冲区**c3**代表上述第8个方法

	abstract public Object invokeMethod(Object instance, String mn, Class<?>[] types, Object[] args) throws NoSuchMethodException, InvocationTargetException{ 	
		c的全类名 w;
		try {
			c的全类名 w = ((c的全类名) $1);
		} catch (Throwable e) {
			throw new IllegalArgumentException(e);
		}

维护多个变量，用于生成其他方法

1. Map<String, Class<?>> pts 维护了公开字段和公开字段类型的映射
2. Map<String, Method> ms 维护了方法签名和方法的映射
3. List<String> mns 维护了方法名
4. List<String> dmns 维护了公开的方法名，方法放回类型是入参类类型c

对入参的类类型c进行所有公开的字段进行遍历，跳过其中static或者transient的字段

1. 为c1缓冲区添加，其他符合的上述字段，形成如下代码

		   if($2.equals(字段名1)){
               w.字段名1=（字段类型1）$3;
               return;
           }
           if($2.equals(字段名2)){
               w.字段名2=（字段类型2）$3;
               return;
           }
		   ...
2. 为c2缓冲区添加，其他符合的上述字段，形成如下代码
			
	 	   if( $2.equals(字段名1)){ return ($w)w.字段名1;}
           if( $2.equals(字段名2)){ return ($w)w.字段名2;}
		   ...
3. 为pts变量添加其他符合的上述字段，添加内容如下

		   (字段名1,字段名1类型)
		   (字段名2,字段名2类型)

对入参的类类型c进行所有公开的方法进行遍历，跳过其中返回值为Object的方法
		
1. 为c3缓冲区添加，其他符合的上述方法，形成如下代码

		   try {
				if (方法名1.equals($2) && $3.length == 方法名1对应的参数长度) {
					return ($w) w.方法名1((参数类型0)$4[0],(参数类型1)$4[1],...);
				}
				if (方法名2.equals($2) && $3.length == 方法名2对应的参数长度) {
					return ($w) w.方法名2((参数类型0)$4[0],(参数类型1)$4[1],...);
				}
				//void形式
				if (方法名3.equals($2) && $3.length == 方法名3对应的参数长度) {
					w.方法名3((参数类型0)$4[0],(参数类型1)$4[1],...);
					return null
				}
				//有重名的方法
				if (方法名4.equals($2) && $3.length == 方法名4对应的参数长度&&$3[0].getName.equals(参数0.getName())&&$3[1].getName.equals(参数1.getName())...) {
					return ($w) w.方法名2((参数类型0)$4[0],(参数类型1)$4[1],...);
				}
			} catch (Throwable e) {
					throw new java.lang.reflect.InvocationTargetException(e);
			}
2. 为mns变量添加其他符合的上述方法的方法名，添加内容如下

			 (方法名1)
			 (方法名2)
3. 为dmns变量添加其他符合的上述方法的方法名，但是附加一个条件（该方法返回值类型是入参类类型c），添加内容如下

			 (方法名1)---方法名1对应的方法返回值类型是入参类类型c
			 (方法名2)---方法名2对应的方法返回值类型是入参类类型c
4. 为ms变量添加其他符合的上述方法的方法名的java描述和方法，添加内容如下

			 (方法名1的java描述:方法1)
			 (方法名2的java描述:方法2)

为缓冲区c3，添加如下代码

	throw new com.alibaba.dubbo.common.bytecode.NoSuchMethodException("Not found method \"" + $2 + "\" in class 入参c的全类名.");

遍历ms变量所维护的map(方法名的java描述:对应方法)

1. 对于所有符合get开头的方法，为缓冲区c2,添加如下代码
	
			  if( $2.equals("方法名1") ){ return ($w)w.方法1(); }
			  if( $2.equals("方法名2") ){ return ($w)w.方法2(); }
2. 对于所有符合is或者has或者can开头的方法，为缓冲区c2,添加如下代码

			  if( $2.equals("方法名1") ){ return ($w)w.方法1(); }
			  if( $2.equals("方法名2") ){ return ($w)w.方法2(); }
3. 对于所有符合set开头的方法，为缓冲区c1,添加如下代码

			  if( $2.equals("方法名1") ){ w.方法1((类型转换)$3); return; }

为c1和c2缓冲区添加如下代码
			 
			  throw new NoSuchPropertyException("Not found property "+$2+" filed or setter method in class 入参c的全类名.); }

接下来就是使用javassist技术生成对应的类了	  
- 见代码注解

上述过程后，一个Inovker就顺利导出了。

### 导出之export ###
---
这个是服务导出的最终方法，也就是获得Exporter。

	Exporter<?> exporter = protocol.export(invoker);
也就是这行代码,类似之前，protocol的属性其类是程序运行生成的，我们看生成的实际代码:

	
	public class Protocol$Adpative implements com.alibaba.dubbo.rpc.Protocol {
	    
	    public com.alibaba.dubbo.rpc.Exporter export(com.alibaba.dubbo.rpc.Invoker arg0) throws com.alibaba.dubbo.rpc.RpcException {
	        if (arg0 == null) throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument == null");
	        if (arg0.getUrl() == null)
	            throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument getUrl() == null");
	        com.alibaba.dubbo.common.URL url = arg0.getUrl();
	        String extName = (url.getProtocol() == null ? "dubbo" : url.getProtocol());
	        if (extName == null)
	            throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.Protocol) name from url(" + url.toString() + ") use keys([protocol])");
	        com.alibaba.dubbo.rpc.Protocol extension = (com.alibaba.dubbo.rpc.Protocol) ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.rpc.Protocol.class).getExtension(extName);
	        return extension.export(arg0);
	    }
		//...省略其他方法
	}

上述就是Proxy$Adaptive的代码了，该类实际不存在，也是运行时生成，我们看到其export的代码逻辑很简单，主要是为了获得扩展类。  
由于我们讨论的是带有注册中心的情景，之前我们也说得，协议配置类的url装换为注册url里面的一个键值对了（export：协议配置url），所以这个方法从Invoker中获得的也就是最前面传递进去的注册url，对于注册url，其protocol总是registry。

不出意外，实际上最终调用的就是名为registry的普通扩展实现中(RegistryProtocol)。事实上这个接口实现有两个我们上面所说的代理包装类。

1. ProtocolFilterWrapper
2. ProtocolListenerWrapper

然而对也名为registry，他们只是简单向内传递，并没有做任何逻辑，至于其他的名字，我们后面再说其如何处理的

### RegistryProtocol的export

----------
我们上面说过带有注册URL的总是服务导出的时候总是先来这行这个方法，方法代码如下:

	 public <T> Exporter<T> export(final Invoker<T> originInvoker) throws RpcException {
        final ExporterChangeableWrapper<T> exporter = doLocalExport(originInvoker);
        final Registry registry = getRegistry(originInvoker);
        final URL registedProviderUrl = getRegistedProviderUrl(originInvoker);
        registry.register(registedProviderUrl);

        final URL overrideSubscribeUrl = getSubscribedOverrideUrl(registedProviderUrl);
		
		final OverrideListener overrideSubscribeListener = new OverrideListener(overrideSubscribeUrl);

        overrideListeners.put(overrideSubscribeUrl, overrideSubscribeListener);

        registry.subscribe(overrideSubscribeUrl, overrideSubscribeListener);

        return new Exporter<T>() {

            public Invoker<T> getInvoker() {
                return exporter.getInvoker();
            }

            public void unexport() {
            	try {
            		exporter.unexport();
            	} catch (Throwable t) {
                	logger.warn(t.getMessage(), t);
                }
                try {
                	registry.unregister(registedProviderUrl);
                } catch (Throwable t) {
                	logger.warn(t.getMessage(), t);
                }
                try {
                	overrideListeners.remove(overrideSubscribeUrl);
                	registry.unsubscribe(overrideSubscribeUrl, overrideSubscribeListener);
                } catch (Throwable t) {
                	logger.warn(t.getMessage(), t);
                }
            }

        };
    }
代码似乎也不长，但是做了超级多的动作，为了更好的理解这些代码，我们使用多个部分来拆解解析:首先是

	final ExporterChangeableWrapper<T> exporter = doLocalExport(originInvoker);

这个相当的重要相当于正在进行暴露。

### 真实的导出之doLocalExport
---
在这个中，协议配置类才会被导出，也就是上面的第一小步，我们贴出相关代码

	    private <T> ExporterChangeableWrapper<T>  doLocalExport(final Invoker<T> originInvoker){
	        String key = getCacheKey(originInvoker);
	        ExporterChangeableWrapper<T> exporter = (ExporterChangeableWrapper<T>) bounds.get(key);
	        if (exporter == null) {
	            synchronized (bounds) {
	                exporter = (ExporterChangeableWrapper<T>) bounds.get(key);
	                if (exporter == null) {
	                    final Invoker<?> invokerDelegete = new InvokerDelegete<T>(originInvoker, getProviderUrl(originInvoker));
	                    exporter = new ExporterChangeableWrapper<T>((Exporter<T>)protocol.export(invokerDelegete), originInvoker);
	                    bounds.put(key, exporter);
	                }
	            }
	        }
	        return (ExporterChangeableWrapper<T>) exporter;
	    }
代码也很短，但是逻辑复杂,而且我们也可以很明显的看出这是缓存操作，
bounds维护了一个缓存。

1. key的生成，其实就是之前的配置url(providerUrl)，但是移除了两个键（dynamic和enable），因为dynamic是会变化的。
2. 缓存操作，新建过程，使用传递进来的invoker和协议配置url构建一个Invoker的委托，协议配置url此时没有移除上面两个key
3. 尝试将这个Invoker委托导出，获得相应的Exporter
4. 将这个获得相应Exporter和传递进来的invoker，包装成一个ExporterChangeableWrapper，放入缓存，并返回。

上面的第3个是一个重点，也就是

	(Exporter<T>)protocol.export(invokerDelegete)

因为这个invokerDelegete是由传递进来的invoker和协议配置URL构建的，所以可能涉及到了另一个类型URL而不再是注册配置URL
而invokerDelegete本质也是invoker，这个调用分方法也在上面说过了。唯一区别的是由于这里的URL换成了协议配置URL，那么其protocol属性也发生了变化，默认情况下该属性是dubbo。

如同我们上边所说一样，名字不为registry了，也就是包装类会采取一些动作

- ProtocolFilterWrapper的动作

		public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
	        if (Constants.REGISTRY_PROTOCOL.equals(invoker.getUrl().getProtocol())) {
	            return protocol.export(invoker);
	        }
	        return protocol.export(buildInvokerChain(invoker, Constants.SERVICE_FILTER_KEY, Constants.PROVIDER));
	    }
- ProtocolListenerWrapper的动作

		public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
	        if (Constants.REGISTRY_PROTOCOL.equals(invoker.getUrl().getProtocol())) {
	            return protocol.export(invoker);
	        }
	        return new ListenerExporterWrapper<T>(protocol.export(invoker), 
	                Collections.unmodifiableList(ExtensionLoader.getExtensionLoader(ExporterListener.class)
	                        .getActivateExtension(invoker.getUrl(), Constants.EXPORTER_LISTENER_KEY)));
	    }

FilterWrapper对Invoker进行了链式包装
ListenerWrapper对返回的Exporter做了包装
在这里我们不详细展开，感兴趣的童鞋，请看dubbo的包装一文。

### DubboProtocol ###
---
这个是协议配置的导出类，前面无论怎么对其进行包装，最后的代码都会执行到这里来（如果符合要求的话），
也就是将服务暴露的关键。

    public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {

        URL url = invoker.getUrl();
        
        String key = serviceKey(url);

        DubboExporter<T> exporter = new DubboExporter<T>(invoker, key, exporterMap);

        exporterMap.put(key, exporter);
        
        Boolean isStubSupportEvent = url.getParameter(Constants.STUB_EVENT_KEY,Constants.DEFAULT_STUB_EVENT);
        Boolean isCallbackservice = url.getParameter(Constants.IS_CALLBACK_SERVICE, false);
        if (isStubSupportEvent && !isCallbackservice){
            String stubServiceMethods = url.getParameter(Constants.STUB_EVENT_METHODS_KEY);
            if (stubServiceMethods == null || stubServiceMethods.length() == 0 ){
                if (logger.isWarnEnabled()){
                    logger.warn(new IllegalStateException("consumer [" +url.getParameter(Constants.INTERFACE_KEY) +
                            "], has set stubproxy support event ,but no stub methods founded."));
                }
            } else {
                stubServiceMethodsMap.put(url.getServiceKey(), stubServiceMethods);
            }
        }
        openServer(url);
        
        return exporter;
    }

代码也不多，但是还是这个问题，逻辑复杂，主要是元信息够复杂，所以处理也必须复杂。

- 获得url当然这个时候获得就是协议配置类url了
- 获得key代表了服务的标识，当然和之前是不一样的，这个key由协议配置类中的
	- group+"/"+path+":"+协议version(2.0.0)+":"+port组成
- 构建dubbo协议的Exporter
- 将key和exporter放入缓存
- 处理协议配置的URL（dubbo.stub.event）和(is_callback_service)
	- 对配置了某些特别属性的处理，比如桩服务(consumer使用)
- 开启服务

### 开启网络服务 ###

----------
为了的逻辑的的连贯性，该点请见，dubbo之真正的网络开启


### 小结 ###

----------
上面这样一番叙述下来，协议配置类貌似已经开启服务了，当然我们的注册配置类的URL还没处理完毕。我们需要回过头来看。

 	final Registry registry = getRegistry(originInvoker);

接下来是上面这行代码的解析，从名字来看似乎就是从invoker中获得注册信息实例了

	private Registry getRegistry(final Invoker<?> originInvoker){
        URL registryUrl = originInvoker.getUrl();
        if (Constants.REGISTRY_PROTOCOL.equals(registryUrl.getProtocol())) {
            String protocol = registryUrl.getParameter(Constants.REGISTRY_KEY, Constants.DEFAULT_DIRECTORY);
            registryUrl = registryUrl.setProtocol(protocol).removeParameter(Constants.REGISTRY_KEY);
        }
        return registryFactory.getRegistry(registryUrl);
    }
代码很短，逻辑如下:

1. 获得注册配置类的url
2. 从url中提取特定的注册协议，默认是dubbo，当然显然我们会使用zookeeper。
3. 移去url中代表协议类型的键值对(registry:协议类型)
4. 根据注册协议获得相应的注册实现。

第4点很重要，我们队其详细展开：

	@SPI("dubbo")
	public interface RegistryFactory {
	    @Adaptive({"protocol"})
	    Registry getRegistry(URL url);
	}
首先变量是registryFactory的实现肯定是RegistryFactory$Adaptive。
那么程序生成的实现应该是这样的：

	if(arg0 == null) throw new IllegalArgumentException("url == null");
	URL url = arg0;
	String extName = ( url.getProtocol() == null ? "dubbo" : url.getProtocol() )";
	if(extName == null) throw new IllegalStateException("Fail to get extension(Lcom.alibaba.dubbo.registry.RegistryFactory) name from url(" + url.toString() + ") use keys([dubbo])");
	Lcom.alibaba.dubbo.registry.RegistryFactory extension = (Lcom.alibaba.dubbo.registry.RegistryFactory)ExtensionLoader.getExtensionLoader(Lcom.alibaba.dubbo.registry.RegistryFactory.class).getExtension(extName)
	return extension.getRegistry(arg0);

获得具体扩展类就是

- DubboRegistryFactory
- MulticastRegistryFactory
- ZookeeperRegistryFactory
- RedisRegistryFactory

其中前三个都是实现了抽象类AbstractRegistryFactory，其getRegistry由抽象类提供

	 public Registry getRegistry(URL url) {
    	url = url.setPath(RegistryService.class.getName())
    			.addParameter(Constants.INTERFACE_KEY, RegistryService.class.getName())
    			.removeParameters(Constants.EXPORT_KEY, Constants.REFER_KEY);
    	String key = url.toServiceString();
        LOCK.lock();
        try {
            Registry registry = REGISTRIES.get(key);
            if (registry != null) {
                return registry;
            }
            registry = createRegistry(url);
            if (registry == null) {
                throw new IllegalStateException("Can not create registry " + url);
            }
            REGISTRIES.put(key, registry);
            return registry;
        } finally {
            LOCK.unlock();
        }
    }
以上是前三者的共同代码，总体上是缓存操作。

1. 对url的处理
2. 缓存操作

其中重点是缓存操作，也就是createRegistry(url)。这个方法由子类自己实现。这里我们例举zookeeper来说

	public Registry createRegistry(URL url) {
        return new ZookeeperRegistry(url, zookeeperTransporter);
    }
其实现超级简单，我们看到直接新建了一个对象。这样一来服务导出一部分代码又完成了。我们继续来看

	final URL registedProviderUrl = getRegistedProviderUrl(originInvoker);
 
尝试从invoker中获得协议配置url，当然有些信息是要被过滤掉的，注册中心用不到这些信息。

	private URL getRegistedProviderUrl(final Invoker<?> originInvoker){
        URL providerUrl = getProviderUrl(originInvoker);
        final URL registedProviderUrl = providerUrl.removeParameters(getFilteredKeys(providerUrl)).removeParameter(Constants.MONITOR_KEY);
        return registedProviderUrl;
    }
代码很短，逻辑如下:

1. 从invoker中 获得协议配置url，也就是从注册url中获得键为export的值信息，使用该值构造URL
2. 然后过滤掉协议配置url中以.开头的键值对（隐藏信息），在过滤掉监控的键值对(monitor,监控url)


回到服务export中，接下来自然是注册中心注册协议配置url了

 	registry.register(registedProviderUrl);

操作也很简单，只是将url简单的添加到已注册的列表中。

	final URL overrideSubscribeUrl = getSubscribedOverrideUrl(registedProviderUrl);

接下来使用协议配置url构建可以订阅的url
	
	其操作只是简单的将url的protocol改为provider，并添加两队键值对（category：configurators）
	（check：false）

最后的代码

        final OverrideListener overrideSubscribeListener = new OverrideListener(overrideSubscribeUrl);

		overrideListeners.put(overrideSubscribeUrl, overrideSubscribeListener);

        registry.subscribe(overrideSubscribeUrl, overrideSubscribeListener);

        return new Exporter<T>() {

            public Invoker<T> getInvoker() {
                return exporter.getInvoker();
            }

            public void unexport() {
            	try {
            		exporter.unexport();
            	} catch (Throwable t) {
                	logger.warn(t.getMessage(), t);
                }
                try {
                	registry.unregister(registedProviderUrl);
                } catch (Throwable t) {
                	logger.warn(t.getMessage(), t);
                }
                try {
                	overrideListeners.remove(overrideSubscribeUrl);
                	registry.unsubscribe(overrideSubscribeUrl, overrideSubscribeListener);
                } catch (Throwable t) {
                	logger.warn(t.getMessage(), t);
                }
            }
		};
新建订阅者，放入缓存，然后注册中心将订阅者的信息注册，最后返回new的export。

上面这里还有一个细节也就是订阅的实现，订阅还是一个非常重要的操作的。
也就是代码

	registry.unregister(registedProviderUrl);

和前面一样这个注册中心普遍使用了f父类的方法，然后在其中回调了自己的实现，我们慢慢来看:

	public void subscribe(URL url, NotifyListener listener) {
		super.subscribe(url, listener);
        removeFailedSubscribed(url, listener);
        try {
            doSubscribe(url, listener);
        } catch (Exception e) {
            Throwable t = e;

            List<URL> urls = getCacheUrls(url);
            if (urls != null && urls.size() > 0) {
                notify(url, listener, urls);
                logger.error("Failed to subscribe " + url + ", Using cached list: " + urls + " from cache file: " + getUrl().getParameter(Constants.FILE_KEY, System.getProperty("user.home") + "/dubbo-registry-" + url.getHost() + ".cache") + ", cause: " + t.getMessage(), t);
            } else {
                boolean check = getUrl().getParameter(Constants.CHECK_KEY, true)
                        && url.getParameter(Constants.CHECK_KEY, true);
                boolean skipFailback = t instanceof SkipFailbackWrapperException;
                if (check || skipFailback) {
                    if (skipFailback) {
                        t = t.getCause();
                    }
                    throw new IllegalStateException("Failed to subscribe " + url + ", cause: " + t.getMessage(), t);
                } else {
                    logger.error("Failed to subscribe " + url + ", waiting for retry, cause: " + t.getMessage(), t);
                }
            }
            addFailedSubscribed(url, listener);
        }
    }

我们看到实际上这个父类进行的订阅，我们整体来看整个逻辑

1. 继续调用父类的实现
2. 从失败的订阅列表移除相关的信息 
3. 回调子类的实现
4. 检测url信息对应的缓存信息，如果有对应的信息进行通知，否则使用相关的参数进行合法性的校验
5. 将url和监听者加入失败的订阅缓存中，定时进行重试


### 小结 ###
---
到此整个dubbo服务就导出完毕了。我们下一篇再会

