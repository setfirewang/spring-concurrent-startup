package com.setfire.spring.startup;

import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.core.Ordered;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author setfire
 */
public class ConcurrentBeanFactory {
    static final Logger LOGGER = LoggerFactory.getLogger(ConcurrentBeanFactory.class);
    static Map<Object, Object> proxyBeanMap = Maps.newConcurrentMap();
    private static final ExecutorService executorService;
    private static final List<Future<?>> FUTURE_LIST = new ArrayList<>();
    private static final Map<Class<?>, Enhancer> generatedClassMap = Maps.newHashMap();
    private static final Map<Class<?>, ConcurrentMethodInterceptor> interceptorMap = Maps.newHashMap();
    private static final Map<Object, Boolean> proxyBeanSuccessMap = Maps.newConcurrentMap();
    static int deadLockCheckTimeOut = 20;
    static {
        int threadPoolCoreSize = 2 * Runtime.getRuntime().availableProcessors() + 1;
        int threadPoolMaxSize = 2 * Runtime.getRuntime().availableProcessors() + 1;

        executorService = new ThreadPoolExecutor(threadPoolCoreSize, threadPoolMaxSize, 30,
                                                 TimeUnit.SECONDS, new SynchronousQueue<>(), new ThreadFactory() {

            final AtomicInteger index = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "ConcurrentBeanFactory" + index.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        }, new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public static void setExcludeClass(Class<?>[] classes) {
        for(Class<?> clazz : classes) {
            generatedClassMap.put(clazz, null);
        }
    }

    public static void setDeadLockCheckTimeOut(int deadLockCheckTimeOut) {
        ConcurrentBeanFactory.deadLockCheckTimeOut = deadLockCheckTimeOut;
    }

    public static void waitForSuccess() {
        FUTURE_LIST.forEach(future -> {
            try{
                future.get();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        executorService.shutdown();
    }

    public static Future<?> putTask(Callable<?> callable) {
        Future<?> future = executorService.submit(callable);
        FUTURE_LIST.add(future);
        return future;
    }

    public void registerNewClass(Class<?> clazz, List<String> asyncInitMethods) {
        if (generatedClassMap.containsKey(clazz)) {
            return;
        }
        ConcurrentMethodInterceptor interceptor = new ConcurrentMethodInterceptor();
        interceptor.setDefaultMethodStrategy(new DefaultMethod());
        try{
            interceptor.putMethod(WrappedProxyBean.class.getDeclaredMethod("getOriginalBean"), new OriginalBeanGetterMethod());
            interceptor.putMethod(WrappedProxyBean.class.getDeclaredMethod("setOriginalBean", Object.class), new OriginalBeanSetterMethod());
            Method preMethod = null;
            for (String method : asyncInitMethods) {
                Method curMethod = clazz.getDeclaredMethod(method);
                putAsyncMethod(interceptor, curMethod, preMethod);
                preMethod = curMethod;
            }
            if (FactoryBean.class.isAssignableFrom(clazz)) {
                interceptor.putMethod(clazz.getMethod("isSingleton"), new SyncMethod());
                interceptor.putMethod(clazz.getMethod("getObjectType"), new SyncMethod());
                Method getObject = clazz.getMethod("getObject");
                interceptor.putMethod(getObject, new ObjectProxyMethod(getObject, clazz.getMethod("getObjectType")));
            }
            if (Ordered.class.isAssignableFrom(clazz)) {
                interceptor.putMethod(clazz.getMethod("getOrder"), new SyncMethod());
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(clazz);
        enhancer.setCallback(interceptor);
        enhancer.setInterfaces(new Class[]{WrappedProxyBean.class});
        generatedClassMap.put(clazz, enhancer);
        interceptorMap.put(clazz, interceptor);
    }
    public void registerUnProxyClass(Class<?> clazz) {
        generatedClassMap.put(clazz, null);
    }
    public ConcurrentBeanFactory() {
    }

    boolean hasRegisterClass(Class<?> clazz) {
        return generatedClassMap.containsKey(clazz);
    }

    Object getProxyObject(Object bean) {
        Enhancer enhancer = generatedClassMap.getOrDefault(bean.getClass(), null);
        if (enhancer == null) {
            return bean;
        }
        WrappedProxyBean proxyBean = (WrappedProxyBean) enhancer.create();
        proxyBean.setOriginalBean(bean);
        return proxyBean;
    }

    private void putAsyncMethod(ConcurrentMethodInterceptor interceptor, Method method, Method dependOnMethod) {
        if (dependOnMethod == null) {
            interceptor.putMethod(method, new AsyncMethod(method,null));
            return;
        }
        WrappedMethod asyncMethod = interceptor.getMethod(dependOnMethod);
        if (asyncMethod == null) {
            throw new IllegalArgumentException(String.format("dependOnMethod %s must config before other method depending on it", dependOnMethod));
        }
        if (asyncMethod instanceof AsyncMethod) {
            interceptor.putMethod(method, new AsyncMethod(method, (AsyncMethod) asyncMethod));
        } else {
            throw new IllegalArgumentException(String.format("dependOnMethod %s must be a AsyncMethod", dependOnMethod));
        }
    }

    /**
     * 等待代理类的异步方法执行完毕
    * @param proxyBean
    * @return 实际是否发生阻塞等待
    */
    public static boolean waitProxyBeanAsyncMethodSuccess(Object proxyBean) {
        if (proxyBeanSuccessMap.containsKey(proxyBean)) {
            return false;
        }
        AtomicBoolean allDone = new AtomicBoolean(true);
        List<WrappedMethod> wrappedMethods = interceptorMap.get(proxyBean.getClass().getSuperclass()).getMethods();
        wrappedMethods.forEach(wrappedMethod -> {
            if (wrappedMethod instanceof AsyncMethod && !((AsyncMethod) wrappedMethod).getResult(proxyBean)) {
                allDone.set(false);
            }
        });
        if (allDone.get()) {
            proxyBeanSuccessMap.put(proxyBean, true);
        }
        return true;
    }
}
