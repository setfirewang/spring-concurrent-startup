package com.setfire.spring.startup;

import java.lang.reflect.Method;

/**
 * @author setfire
 */
class OriginalBeanSetterMethod implements WrappedMethod {

    @Override
    public Object invoke(Object object, Object[] objects, Method method) {
        return ConcurrentBeanFactory.proxyBeanMap.put(object, objects[0]);
    }
}