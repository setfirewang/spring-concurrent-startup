package com.setfire.spring.startup;

import java.lang.reflect.Method;

/**
 * @author setfire
 */
public interface WrappedMethod {
    Object invoke(Object object, Object[] objects, Method method);
}
