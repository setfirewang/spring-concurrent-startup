package com.setfire.spring.startup;

import com.google.common.collect.Maps;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * @author setfire
 */
class ObjectProxyMethod implements WrappedMethod {
    private static final Map<Class<?>, Enhancer> proxyMethodCachedMap = Maps.newConcurrentMap();
    Method getObjectMethod;
    Method getProxyTypeMethod;

    ObjectProxyMethod(Method method, Method getProxyTypeMethod) {
        this.getObjectMethod = method;
        this.getProxyTypeMethod = getProxyTypeMethod;
    }
    @Override
    public Object invoke(Object bean, Object[] objects, Method method) {
        Object originalBean = ((WrappedProxyBean) bean).getOriginalBean();
        Class<?> proxyClass;
        try{
            proxyClass = (Class<?>) getProxyTypeMethod.invoke(originalBean);
            if (proxyClass == null) {
                ConcurrentBeanFactory.waitProxyBeanAsyncMethodSuccess(bean);
                return method.invoke(originalBean, objects);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (proxyMethodCachedMap.containsKey(proxyClass)) {
            return proxyMethodCachedMap.get(proxyClass).create();
        }
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(proxyClass);
        enhancer.setCallback((MethodInterceptor) (o, method1, objects1, methodProxy) -> {
            ConcurrentBeanFactory.waitProxyBeanAsyncMethodSuccess(bean);
            return method1.invoke(getObjectMethod.invoke(originalBean), objects1);
        });
        proxyMethodCachedMap.put(proxyClass, enhancer);
        return enhancer.create();
    }
}