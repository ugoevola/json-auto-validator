package org.uevola.jsonautovalidation.core

import mu.KLogging
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.context.EnvironmentAware
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.uevola.jsonautovalidation.configuration.JsonValidationConfig
import kotlin.time.ExperimentalTime

@ExperimentalTime
@Configuration
open class JsonValidationInitializer :
    BeanDefinitionRegistryPostProcessor,
    BeanFactoryInitializationAotProcessor,
    EnvironmentAware {
    companion object : KLogging()

    override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {}

    override fun postProcessBeanDefinitionRegistry(registry: BeanDefinitionRegistry) {
        BeansGenerator.generateBeans(registry)
    }

    override fun setEnvironment(environment: Environment) {
        JsonValidationConfig.init(environment)
    }

    override fun processAheadOfTime(
        beanFactory: ConfigurableListableBeanFactory
    ): BeanFactoryInitializationAotContribution? {
        return null
    }
}