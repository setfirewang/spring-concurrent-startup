package com.setfire.spring.startup;

/**
 * @author setfire
 */
public interface WrappedProxyBean {
    Object getOriginalBean();
    Object setOriginalBean(Object object);
}
