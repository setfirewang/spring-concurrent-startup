package com.setfire.spring.startup.annotation;

import com.setfire.spring.startup.ConcurrentStartupSelector;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author setfire
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(ConcurrentStartupSelector.class)
public @interface ConcurrentStartup {
  Class[] exclude() default {};
  int deadLockCheckTimeOut() default 30;
}
