   public void setPropertyValue(Object o, String n, Object v) {
        com.alibaba.dubbo.config.spring.impl.DemoServiceImpl w;
        try {
            w = ((com.alibaba.dubbo.config.spring.impl.DemoServiceImpl) $1);
        } catch (Throwable e) {
            throw new IllegalArgumentException(e);
        }
        if ($2.equals("prefix")) {
            w.setPrefix((java.lang.String) $3);
            return;
        }
        throw new com.alibaba.dubbo.common.bytecode.NoSuchPropertyException("Not found property \"" + $2 + "\" filed or setter method in class com.alibaba.dubbo.config.spring.impl.DemoServiceImpl.");
    }

    public Object getPropertyValue(Object o, String n) {
        com.alibaba.dubbo.config.spring.impl.DemoServiceImpl w;
        try {
            w = ((com.alibaba.dubbo.config.spring.impl.DemoServiceImpl) $1);
        } catch (Throwable e) {
            throw new IllegalArgumentException(e);
        }
        if ($2.equals("box")) {
            return ($w) w.getBox();
        }
        if ($2.equals("prefix")) {
            return ($w) w.getPrefix();
        }
        throw new com.alibaba.dubbo.common.bytecode.NoSuchPropertyException("Not found property \"" + $2 + "\" filed or setter method in class com.alibaba.dubbo.config.spring.impl.DemoServiceImpl.");
    }

    public Object invokeMethod(Object o, String n, Class[] p, Object[] v) throws java.lang.reflect.InvocationTargetException {
        com.alibaba.dubbo.config.spring.impl.DemoServiceImpl w;
        try {
            w = ((com.alibaba.dubbo.config.spring.impl.DemoServiceImpl) $1);
        } catch (Throwable e) {
            throw new IllegalArgumentException(e);
        }
        try {
            if ("sayName".equals($2) && $3.length == 1) {
                return ($w) w.sayName((java.lang.String) $4[0]);
            }
            if ("getBox".equals($2) && $3.length == 0) {
                return ($w) w.getBox();
            }
            if ("getPrefix".equals($2) && $3.length == 0) {
                return ($w) w.getPrefix();
            }
            if ("setPrefix".equals($2) && $3.length == 1) {
                w.setPrefix((java.lang.String) $4[0]);
                return null;
            }
        } catch (Throwable e) {
            throw new java.lang.reflect.InvocationTargetException(e);
        }
        throw new com.alibaba.dubbo.common.bytecode.NoSuchMethodException("Not found method \"" + $2 + "\" in class com.alibaba.dubbo.config.spring.impl.DemoServiceImpl.");
    }