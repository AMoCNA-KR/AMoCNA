package com.kubiki.daedalus.spring;

import com.kubiki.daedalus.annotation.EnableDaedalusRepositories;
import com.kubiki.daedalus.context.GlobalTemplateContext;
import com.kubiki.daedalus.core.Formatter;
import com.kubiki.daedalus.core.format.CollectionFormatter;
import com.kubiki.daedalus.core.format.IriFormatter;
import com.kubiki.daedalus.core.format.LiteralFormatter;
import com.kubiki.daedalus.core.format.PlainFormatter;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static com.kubiki.daedalus.core.DaedalusConstants.DEFAULT_BASE_PACKAGE;

@Configuration
public class DaedalusAutoConfiguration implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        if (!registry.containsBeanDefinition(GlobalTemplateContext.class.getName())) {
            registry.registerBeanDefinition(GlobalTemplateContext.class.getName(),
                    BeanDefinitionBuilder.genericBeanDefinition(GlobalTemplateContext.class).getBeanDefinition());
        }

        // Register default formatters
        registerFormatter(registry, IriFormatter.class);
        registerFormatter(registry, LiteralFormatter.class);
        registerFormatter(registry, PlainFormatter.class);
        registerFormatter(registry, CollectionFormatter.class);

        if (!registry.containsBeanDefinition(Formatter.class.getName())) {
            registry.registerBeanDefinition(Formatter.class.getName(),
                    BeanDefinitionBuilder.genericBeanDefinition(Formatter.class).getBeanDefinition());
        }

        Set<String> basePackages = getBasePackages(importingClassMetadata);
        if (basePackages.isEmpty()) {
            basePackages.add(DEFAULT_BASE_PACKAGE);
        }

        DaedalusBeanRegistrar registrar = new DaedalusBeanRegistrar();
        for (String basePackage : basePackages) {
            registrar.registerRepositories(registry, null, basePackage);
        }
    }

    private Set<String> getBasePackages(AnnotationMetadata metadata) {
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                metadata.getAnnotationAttributes(EnableDaedalusRepositories.class.getName()));
        if (attributes != null) {
            return new HashSet<>(Arrays.asList(attributes.getStringArray("basePackages")));
        }
        return new HashSet<>();
    }

    private void registerFormatter(BeanDefinitionRegistry registry, Class<?> formatterClass) {
        if (!registry.containsBeanDefinition(formatterClass.getName())) {
            registry.registerBeanDefinition(formatterClass.getName(),
                    BeanDefinitionBuilder.genericBeanDefinition(formatterClass).getBeanDefinition());
        }
    }
}
