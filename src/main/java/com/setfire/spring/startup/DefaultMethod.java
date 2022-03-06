package com.setfire.spring.startup;

import java.lang.reflect.Method;

import static com.setfire.spring.startup.ConcurrentBeanFactory.waitProxyBeanAsyncMethodSuccess;

/**
 * @author setfire
 */
class DefaultMethod implements WrappedMethod {
    @Override
    public Object invoke(Object bean, Object[] objects, Method method) {
        long start = System.currentTimeMillis();
        Object originalBean = ((WrappedProxyBean) bean).getOriginalBean();
        try {
            if (method.getDeclaringClass() != Object.class) {
                if (waitProxyBeanAsyncMethodSuccess(bean)) {
                    long costs = System.currentTimeMillis() - start;
                    ConcurrentBeanFactory.LOGGER.info("{}#{} waiting for initialization costs {}ms", originalBean.getClass(), method.getName(), costs);
                }
            }
            return method.invoke(originalBean, objects);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}