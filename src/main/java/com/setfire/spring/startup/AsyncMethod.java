package com.setfire.spring.startup;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import java.lang.reflect.Method;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author setfire
 */
class AsyncMethod implements WrappedMethod {
    private static Table<Object, Method, Future<?>> beanFutureTable = HashBasedTable.create();
    Method targetMethod;
    AsyncMethod dependOnMethod;

    AsyncMethod(Method method, AsyncMethod dependOnMethod) {
        this.targetMethod = method;
        this.dependOnMethod = dependOnMethod;
    }

    @Override
    public Object invoke(Object bean, Object[] args, Method method) {
        Object originalBean = ((WrappedProxyBean) bean).getOriginalBean();
        dependOnMethodDone(bean);
        Future<?> future = ConcurrentBeanFactory.putTask(() -> {
            try {
                method.setAccessible(true);
                return method.invoke(originalBean, args);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        beanFutureTable.put(bean, targetMethod, future);
        return null;
    }

    public boolean getResult(Object bean) {
        Future<?> targetFuture = beanFutureTable.get(bean, targetMethod);
        if (targetFuture == null) {
            return false;
        }
        try{
            if (targetFuture.isDone()) {
                return true;
            }
            dependOnMethodDone(bean);
            targetFuture.get(ConcurrentBeanFactory.deadLockCheckTimeOut, TimeUnit.SECONDS);
            return true;
        } catch (TimeoutException e) {
            throw new IllegalMonitorStateException(String.format("class %s may create dead lock in async-init", targetMethod.getDeclaringClass()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public void dependOnMethodDone(Object bean) {
        if (dependOnMethod != null) {
            dependOnMethod.getResult(bean);
        }
    }
}