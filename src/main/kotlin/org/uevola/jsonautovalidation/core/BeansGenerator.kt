package org.uevola.jsonautovalidation.core

import mu.KLogging
import net.bytebuddy.ByteBuddy
import net.bytebuddy.description.modifier.Visibility
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.core.ResolvableType
import org.uevola.jsonautovalidation.common.extensions.getMethodsToValidate
import org.uevola.jsonautovalidation.common.extensions.getParamsToValidate
import org.uevola.jsonautovalidation.common.extensions.isIgnoredType
import org.uevola.jsonautovalidation.common.utils.ClassPathUtil.getControllersToValidate
import org.uevola.jsonautovalidation.strategies.validators.ValidatorStrategy
import java.lang.reflect.Parameter
import java.util.*
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

object BeansGenerator : KLogging() {

    private var nbAutoGeneratedBeans = 0

    @ExperimentalTime
    fun generateBeans(registry: BeanDefinitionRegistry) {
        logger.info { "Generation of Json Validator Bean..." }
        val elapsed: Duration = kotlin.time.measureTime {
            getControllersToValidate().forEach { controller ->
                controller.getMethodsToValidate().forEach { method ->
                    method.getParamsToValidate(controller)
                        .forEach { parameter -> generateBeanValidator(registry, parameter) }
                }
            }
        }
        logger.info { "Generation of Json Validator Beans completed in ${elapsed.inWholeMilliseconds}ms" }
        logger.info { "Number of Json Validator Bean generated: $nbAutoGeneratedBeans" }
    }

    private fun generateBeanValidator(registry: BeanDefinitionRegistry, parameter: Parameter) {
        if (parameter.type.isIgnoredType()) return
        val beanName =
            parameter.type.simpleName.replaceFirstChar { it.lowercase(Locale.getDefault()) }.plus("Validator")
        if (registry.containsBeanDefinition(beanName)) return
        val resolvableType = ResolvableType.forClassWithGenerics(ValidatorStrategy::class.java, parameter.type)
        val beanDefinition =
            RootBeanDefinition(ValidatorStrategy::class.java) { getJsonSchemaValidatorFor(parameter.type) }
        beanDefinition.setTargetType(resolvableType)
        registry.registerBeanDefinition(beanName, beanDefinition)
        nbAutoGeneratedBeans += 1
    }

    private fun getJsonSchemaValidatorFor(dtoClass: Class<*>?): ValidatorStrategy<*> {
        val generic =
            TypeDescription.Generic.Builder.parameterizedType(ValidatorStrategy::class.java, dtoClass).build()
        return ByteBuddy()
            .subclass(generic)
            .defineField("names", generic, Visibility.PRIVATE)
            .make()
            .load(ValidatorStrategy::class.java.classLoader, ClassLoadingStrategy.Default.WRAPPER)
            .loaded
            .getDeclaredConstructor()
            .apply { isAccessible = true }
            .newInstance() as ValidatorStrategy<*>
    }

}