package com.artivisi.atm.jpos;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SpringBeanFactory implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
        log.info("Spring ApplicationContext set in SpringBeanFactory");
    }

    public static <T> T getBean(Class<T> beanClass) {
        if (context == null) {
            throw new IllegalStateException("ApplicationContext not set");
        }
        return context.getBean(beanClass);
    }

    public static Object getBean(String beanName) {
        if (context == null) {
            throw new IllegalStateException("ApplicationContext not set");
        }
        return context.getBean(beanName);
    }
}
