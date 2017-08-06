## dubbo之Wrapper类
该类是一个非常重要的类，他对接口进行封装，然后传递给Invoker使用，但是其代码难以理解。因此我们单独开一篇进行描述。

### 获取Wrapper的入口
---
获取Wrapper的入口，总是被频繁使用，也是我们重点要关注的对象。代码如下所示:

	public static Wrapper getWrapper(Class<?> c)
    {
        while( ClassGenerator.isDynamicClass(c) ) // can not wrapper on dynamic class.
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
以上就是全部代码。逻辑如下

- 取得类型C的合适接口。不能是空接口，无法适配
- C类型是Object，返回内部默认实现Object_WRAPPER
- 缓存获取包装类，无则新建，有则返回缓存

逻辑很简单，我们需要重点关注的也就是无则新建的情况，也就是新建的过程

### 根据类型获得WRAPPER
---
根据获得类型WRAPPER也就是上面新建过程，她由静态函数makerWrapper实现。
但是该函数应该说是代码咋乱，难以理解。读者可以庚随我的解释一步步递进。

	private static Wrapper makeWrapper(Class<?> c)

我们先贴出方法声明，然后一步步逻辑递进。

- 对入参的检查，类型不能是私有以及装箱类型。否则将会抛出异常
- 尝试构建三个方法,方法签名如下:
	- public void setPropertyValue(Object o, String n, Object v)
	- public Object getPropertyValue(Object o, String n)
	- public Object invokeMethod(Object o, String n, Class[] p, Object[] v) throws InvocationTargetException
- 每个方法补上一段代码类似如下:
	
		try {
			w = ((c的全类名) $1);
		} catch (Throwable e) {
			throw new IllegalArgumentException(e);
		}
- 遍历处理c的公开所有字段，进行处理（忽略static和transient字段)为1，2方法追加代码
	- 方法1**setPropertyValue**：追加代码如下

			if($2.equals(字段名1)){
	         	w.字段名1=（字段类型1）$3;
	         	    return;
	        }
	        if($2.equals(字段名2)){
	         	w.字段名2=（字段类型2）$3;
	        	    return;
	        }
			......
	- 方法2**getPropertyValue**：追加代码如下
		
			if( $2.equals(字段名1)){ return ($w)w.字段名1;}
	        if( $2.equals(字段名2)){ return ($w)w.字段名2;}
			......
	- 将字段名和字段类型保存进**pts**映射中
		
			Map<String, Class<?>> pts = new HashMap<String, Class<?>>();
- 遍历处理c的方法签名，进行处理，为3方法追加代码
	- 方法3**invokeMethod(Object o, String n, Class[] p, Object[] v) throws InvocationTargetException**：追加代码如下

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
			if (方法名4.equals($2) && $3.length == 方法名4参数length&&$3[0].getName.equals(参数0.getName())&&$3[1].getName.equals(参数1.getName())...) {
				return ($w) w.方法名2((参数类型0)$4[0],(参数类型1)$4[1],...);
			}
	- 将遍历的有效方法签名名称加入**mns**列表中

		List<String> mns = new ArrayList<String>();
	- 对有效方法签名且其类声明是C的加入**dmns**列表中
	
		List<String> dmns = new ArrayList<String>()
	- 对有效的方法签名的签名描述和签名加入**ms**映射中

		Map<String, Method> ms = new LinkedHashMap<String, Method>(); // <method desc, Method instance>
- 为方法三追加大代码块套上try，catch

		try {
			代码3追加代码
		} catch (Throwable e) {
			throw new java.lang.reflect.InvocationTargetException(e);
		}*/
- 遍历映射**ms**为方法1方法2追加代码
	- 方法1**setPropertyValue**：追加代码如下。匹配的方法是set方法，将方法名和参数类型加入**pts**映射中

			if($2.equals("方法名1"）{
				w.方法名1((类型1)$3);
				return;
			}
	- 方法2**getPropertyValue**：追加代码如下。匹配的方法是get方法，将方法名和返回类型加入**pts**映射中
	
			if($2.equals("方法名1"){
				return ($w)w.方法名1();
			}
	- 方法2**getPropertyValue**：追加代码如下。匹配的方法是is|has|can方法，将方法名和返回类型加入**pts**映射中
	
			if($2.equals("方法名1"){
				return ($w)w.方法名1();
			}

- 为方法1,2追加代码

		throw new NoSuchPropertyException("Not found property $2 filed or setter method in class " + c.getName() + "."); }	
- 构建类
- 添加字段
	- public static String[] pns;
	- public static Map pts;
	- public static String[] mns;
	- public static String[] dmns
	- public static Class[] mts+i
- 添加方法
	- public String[] getPropertyNames(){ return pns; }
	- public boolean hasProperty(String n){ return pts.containsKey($1); }
	- public Class getPropertyType(String n){ return (Class)pts.get($1); }
	- public String[] getMethodNames(){ return mns; }
	- public String[] getDeclaredMethodNames(){ return dmns; }
- 添加方法1，方法2，方法3
- 利用放射将变量设置进添加的字段中。
