package com.setfire.spring.startup;

import java.lang.reflect.Method;

/**
 * @author setfire
 */
class SyncMethod implements WrappedMethod {

    @Override
    public Object invoke(Object bean, Object[] objects, Method method) {
        Object originalBean = ((WrappedProxyBean) bean).getOriginalBean();
        try {
            return method.invoke(originalBean, objects);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}