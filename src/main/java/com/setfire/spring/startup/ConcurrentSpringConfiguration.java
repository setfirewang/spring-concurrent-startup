package com.setfire.spring.startup;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.cglib.core.VisibilityPredicate;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.PriorityOrdered;
import org.springframework.util.ReflectionUtils;

import javax.annotation.PostConstruct;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;

/**
 * @author setfire
 */
public class ConcurrentSpringConfiguration implements
        ApplicationListener<ContextRefreshedEvent>, BeanPostProcessor, ApplicationContextAware,
        PriorityOrdered {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrentSpringConfiguration.class);

    private static final ConcurrentBeanFactory concurrentBeanFactory = new ConcurrentBeanFactory();

    private ApplicationContext context;

    private Field initMethodFiled;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (event.getApplicationContext().getParent() == null) {
            ConcurrentBeanFactory.waitForSuccess();
        }
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        try {
            if (!concurrentBeanFactory.hasRegisterClass(bean.getClass())) {
                registerConcurrentBeanClass(bean, beanName);
            }
            return concurrentBeanFactory.getProxyObject(bean);
        } catch (Exception e) {
            throw new BeanCreationException(String.format("%s create failed.", beanName), e);
        }
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        return bean;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.context = applicationContext;
    }

    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE;
    }

    private void registerConcurrentBeanClass(Object bean, String beanName) throws Exception {
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory)context.getAutowireCapableBeanFactory();
        AbstractBeanDefinition beanDefinition;
        try {
            beanDefinition = (AbstractBeanDefinition)beanFactory.getMergedBeanDefinition(beanName);
        } catch (NoSuchBeanDefinitionException e) {
            // 某些bean可能没有beanDefinition，例如org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver
            concurrentBeanFactory.registerUnProxyClass(bean.getClass());
            return;
        }
        if (!canBeAsyncInitialized(bean, beanDefinition, beanFactory, beanName)) {
            concurrentBeanFactory.registerUnProxyClass(bean.getClass());
            return;
        }
        final List<String> asyncInitMethods = Lists.newArrayList();
        final Class<?> targetClass = AopUtils.getTargetClass(bean);

        Class<?> clazz = targetClass;
        do {
            ReflectionUtils.doWithLocalMethods(clazz, method -> {
                if (method.isAnnotationPresent(PostConstruct.class)) {
                    asyncInitMethods.add(method.getName());
                }
            });
            clazz = clazz.getSuperclass();
        } while (clazz != null && clazz != Object.class);

        if (bean instanceof InitializingBean) {
            asyncInitMethods.add("afterPropertiesSet");
        }

        if (initMethodFiled == null ) {
            initMethodFiled = AbstractBeanDefinition.class.getDeclaredField("initMethodName");
            initMethodFiled.setAccessible(true);
        }
        String initMethodName = (String) initMethodFiled.get(beanDefinition);
        if (initMethodName != null && !initMethodName.isEmpty()) {
            asyncInitMethods.add(initMethodName);
        }

        for (String methodName : asyncInitMethods) {
            Class<?> currentClass = targetClass;
            while (currentClass != Object.class) {
                try {
                    Method method = currentClass.getDeclaredMethod(methodName);
                    if (Modifier.isPrivate(method.getModifiers())) {
                        concurrentBeanFactory.registerUnProxyClass(bean.getClass());
                        return;
                    }
                    if (Modifier.isStatic(method.getModifiers())) {
                        concurrentBeanFactory.registerUnProxyClass(bean.getClass());
                        return;
                    }
                    break;
                } catch (NoSuchMethodException e) {
                    if (currentClass.getSuperclass() == Object.class) {
                        concurrentBeanFactory.registerUnProxyClass(bean.getClass());
                        return;
                    }
                }
                currentClass = currentClass.getSuperclass();
            }
        }
        if (asyncInitMethods.isEmpty()) {
            concurrentBeanFactory.registerUnProxyClass(bean.getClass());
            return;
        }
        Class<?> directClass = bean.getClass();
        concurrentBeanFactory.registerNewClass(directClass, asyncInitMethods);
    }

    private boolean canBeAsyncInitialized(Object bean, BeanDefinition beanDefinition, DefaultListableBeanFactory beanFactory, String beanName) {
        if (AopUtils.isCglibProxy(bean)) {
            return false;
        }
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        String beanClassName = targetClass.getName();
        if (beanClassName.startsWith("org.springframework.")) {
            return false;
        }
        int classModifier = targetClass.getModifiers();
        if (Modifier.isFinal(classModifier) || !Modifier.isPublic(classModifier)) {
            return false;
        }
        VisibilityPredicate predicate = new VisibilityPredicate(bean.getClass(), true);
        try {
            if (!predicate.evaluate(bean.getClass().getDeclaredConstructor())) {
                return false;
            }
        } catch (NoSuchMethodException e) {
            // bean没有无参构造函数，无法创建代理
            return false;
        }
        if (!beanDefinition.isSingleton() || beanDefinition.isLazyInit()) {
            return false;
        }
        Set<String> dependentBeans = Sets.newHashSet(beanFactory.getDependentBeans(beanName));
        Set<String> dependenciesForBean = Sets.newHashSet(beanFactory.getDependenciesForBean(beanName));
        dependentBeans.retainAll(dependenciesForBean);
        if (!dependentBeans.isEmpty()) {
            return false;
        }
        while (targetClass != Object.class) {
            Method[] methods = targetClass.getDeclaredMethods();
            for (Method method : methods) {
                int modifiers = method.getModifiers();
                if (Modifier.isFinal(modifiers)) {
                    return false;
                }
            }
            targetClass = targetClass.getSuperclass();
        }
        return true;
    }
}
