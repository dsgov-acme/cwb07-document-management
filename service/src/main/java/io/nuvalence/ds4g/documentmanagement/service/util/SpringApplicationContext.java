package io.nuvalence.ds4g.documentmanagement.service.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Provides convenient static access to pull beans from the Spring application context.
 */
@Component
public class SpringApplicationContext implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public synchronized void setApplicationContext(final ApplicationContext applicationContext)
            throws BeansException {
        if (SpringApplicationContext.context == null) {
            SpringApplicationContext.context = applicationContext;
        }
    }

    public static <T> T getBeanByClass(Class<T> clazz) throws BeansException {
        assertContextInitialized();
        return context.getBean(clazz);
    }

    private static void assertContextInitialized() {
        if (context == null) {
            throw new IllegalStateException("ApplicationContext not initialized");
        }
    }
}
