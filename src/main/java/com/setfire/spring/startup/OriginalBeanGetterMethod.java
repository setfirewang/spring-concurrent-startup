package com.setfire.spring.startup;

import java.lang.reflect.Method;

import static com.setfire.spring.startup.ConcurrentBeanFactory.proxyBeanMap;

/**
 * @author setfire
 */
class OriginalBeanGetterMethod implements WrappedMethod {

    @Override
    public Object invoke(Object object, Object[] objects, Method method) {
        return proxyBeanMap.get(object);
    }
}