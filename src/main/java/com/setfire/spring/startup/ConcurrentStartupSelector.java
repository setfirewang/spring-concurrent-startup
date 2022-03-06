package com.setfire.spring.startup;

import com.setfire.spring.startup.annotation.ConcurrentStartup;
import org.apache.commons.collections4.MapUtils;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

import java.util.Map;

/**
 * @author setfire
 */
public class ConcurrentStartupSelector implements ImportSelector {

    private static final String CONFIGURATION =
            "com.setfire.spring.startup.ConcurrentSpringConfiguration";

    private static final String EXCLUDE_PROPERTY = "exclude";

    private static final String TIMEOUT_PROPERTY = "deadLockCheckTimeOut";

    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        if (!importingClassMetadata.isAnnotated(ConcurrentStartup.class.getName())) {
            return new String[]{};
        }

        Map<String, Object> annotationAttributes = importingClassMetadata.getAnnotationAttributes(ConcurrentStartup.class.getName());
        if (MapUtils.isNotEmpty(annotationAttributes) ) {
            if (annotationAttributes.containsKey(EXCLUDE_PROPERTY)) {
                ConcurrentBeanFactory.setExcludeClass((Class<?>[]) annotationAttributes.get(EXCLUDE_PROPERTY));
            }
            if (annotationAttributes.containsKey(TIMEOUT_PROPERTY)) {
                ConcurrentBeanFactory.setDeadLockCheckTimeOut((Integer) annotationAttributes.get(TIMEOUT_PROPERTY));
            }
        }
        return new String[]{CONFIGURATION};
    }
}
