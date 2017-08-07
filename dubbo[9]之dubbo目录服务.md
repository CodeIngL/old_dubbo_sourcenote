## 目录服务 ##

目录服务是dubbo中应用到一个概念，如同uri代表一个特定的资源，目录服务也是如此，在网络中唯一的标识了资源。并对资源进行操作。

### dubbo中的目录服务设计 ###

----------

我们知道网络系统中，很多终端都可以用一个名称来描述节点和我们前面所说的网络编程中的概念一样，dubbo也做了很多概念的抽象

	public interface Node {
	    URL getUrl();
	    boolean isAvailable();
	    void destroy();
	}
以上就是dubbo对网络终端的另一种抽象，NOde。

1. getUrl获得了节点的标识
2. isAvalable标识了这个节点是否可用
3. destroy进行摧毁这个节点


----------
目录服务下节点的引申

	public interface Directory<T> extends Node {
	    
	    Class<T> getInterface();
	
	    List<Invoker<T>> list(Invocation invocation) throws RpcException;
	    
	}

目录服务的抽象接口如上。方法暂不讨论，针对dubbo，继续看

	public abstract class AbstractDirectory<T> implements Directory<T> {...}

这是一个抽象类，完成了目录服务的基本操作，并暴露一些方法给子类实现，从而进行回调。


