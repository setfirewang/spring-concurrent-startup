package com.setfire.spring.startup;


import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author setfire
 */
public class ConcurrentMethodInterceptor implements MethodInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrentMethodInterceptor.class);
    private Map<Method, WrappedMethod> methodStrategy = Maps.newHashMap();
    private WrappedMethod defaultMethodStrategy;

    ConcurrentMethodInterceptor() {
    }

    @Override
    public Object intercept(Object bean, Method method, Object[] objects, MethodProxy methodProxy)
            throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return methodProxy.invokeSuper(bean, objects);
        }
        if (!methodStrategy.containsKey(method)) {
            return defaultMethodStrategy.invoke(bean, objects, method);
        }
        return methodStrategy.get(method).invoke(bean, objects, method);
    }

    public void setDefaultMethodStrategy(WrappedMethod wrappedMethod) {
        defaultMethodStrategy = wrappedMethod;
    }

    public void putMethod(Method method, WrappedMethod wrappedMethod) {
        methodStrategy.put(method, wrappedMethod);
    }

    public WrappedMethod getMethod(Method method) {
        return methodStrategy.get(method);
    }

    public List<WrappedMethod> getMethods() {
        return new ArrayList<>(methodStrategy.values());
    }
}
