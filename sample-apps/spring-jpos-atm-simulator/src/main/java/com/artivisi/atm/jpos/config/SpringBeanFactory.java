package com.artivisi.atm.jpos.config;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Bridge between jPOS components and Spring dependency injection.
 * Allows jPOS participants and other non-Spring-managed components
 * to access Spring beans.
 */
@Component
public class SpringBeanFactory implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }

    /**
     * Get a Spring bean by class type.
     *
     * @param beanClass the class of the bean to retrieve
     * @param <T> the type of the bean
     * @return the Spring bean instance
     */
    public static <T> T getBean(Class<T> beanClass) {
        return context.getBean(beanClass);
    }

    /**
     * Get a Spring bean by name.
     *
     * @param beanName the name of the bean to retrieve
     * @return the Spring bean instance
     */
    public static Object getBean(String beanName) {
        return context.getBean(beanName);
    }

    /**
     * Get a Spring bean by name and class type.
     *
     * @param beanName the name of the bean to retrieve
     * @param beanClass the class of the bean to retrieve
     * @param <T> the type of the bean
     * @return the Spring bean instance
     */
    public static <T> T getBean(String beanName, Class<T> beanClass) {
        return context.getBean(beanName, beanClass);
    }
}
