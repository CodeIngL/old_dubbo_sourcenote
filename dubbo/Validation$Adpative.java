package com.alibaba.dubbo.validation;
        import com.alibaba.dubbo.common.extension.ExtensionLoader;

public class Validation$Adpative implements com.alibaba.dubbo.validation.Validation {
    public com.alibaba.dubbo.validation.Validator getValidator(com.alibaba.dubbo.common.URL arg0) {
        if (arg0 == null) throw new IllegalArgumentException("url == null");
        com.alibaba.dubbo.common.URL url = arg0;
        String extName = url.getParameter("validation", "jvalidation");
        if (extName == null)
            throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.validation.Validation) name from url(" + url.toString() + ") use keys([validation])");
        com.alibaba.dubbo.validation.Validation extension = (com.alibaba.dubbo.validation.Validation) ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.validation.Validation.class).getExtension(extName);
        return extension.getValidator(arg0);
    }
}