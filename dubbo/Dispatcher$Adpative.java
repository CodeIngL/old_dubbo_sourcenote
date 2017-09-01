package com.alibaba.dubbo.remoting;
        import com.alibaba.dubbo.common.extension.ExtensionLoader;

public class Dispatcher$Adpative implements com.alibaba.dubbo.remoting.Dispatcher {
    public com.alibaba.dubbo.remoting.ChannelHandler dispatch(com.alibaba.dubbo.remoting.ChannelHandler arg0, com.alibaba.dubbo.common.URL arg1) {
        if (arg1 == null) throw new IllegalArgumentException("url == null");
        com.alibaba.dubbo.common.URL url = arg1;
        String extName = url.getParameter("dispatcher", url.getParameter("dispather", url.getParameter("channel.handler", "all")));
        if (extName == null)
            throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.remoting.Dispatcher) name from url(" + url.toString() + ") use keys([dispatcher, dispather, channel.handler])");
        com.alibaba.dubbo.remoting.Dispatcher extension = (com.alibaba.dubbo.remoting.Dispatcher) ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.remoting.Dispatcher.class).getExtension(extName);
        return extension.dispatch(arg0, arg1);
    }
}