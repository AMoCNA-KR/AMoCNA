package com.kubiki.daedalus.spring;

import com.kubiki.daedalus.context.GlobalTemplateContext;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

@Configuration
public class DaedalusAutoConfiguration implements ImportBeanDefinitionRegistrar {
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        if (!registry.containsBeanDefinition(GlobalTemplateContext.class.getName())) {
            registry.registerBeanDefinition(GlobalTemplateContext.class.getName(), 
                BeanDefinitionBuilder.genericBeanDefinition(GlobalTemplateContext.class).getBeanDefinition());
        }
        new DaedalusBeanRegistrar().registerRepositories(registry, null, "com.kubiki");
    }
}
