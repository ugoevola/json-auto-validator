package org.uevola.jsonautovalidation.core

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import mu.KLogging
import org.springframework.context.annotation.Configuration
import org.uevola.jsonautovalidation.common.annotations.JsonValidation
import org.uevola.jsonautovalidation.common.annotations.jsonValidationAnnotation.IsJsonValidation
import org.uevola.jsonautovalidation.common.extensions.*
import org.uevola.jsonautovalidation.common.utils.ClassesUtil
import org.uevola.jsonautovalidation.common.utils.JsonUtil.newObjectNode
import org.uevola.jsonautovalidation.common.utils.JsonUtil.objectNodeFromString
import org.uevola.jsonautovalidation.common.utils.ResourcesUtil
import org.uevola.jsonautovalidation.configuration.JsonValidationConfig
import org.uevola.jsonautovalidation.strategies.schemaGenerators.JsonSchemaGeneratorStrategy
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@ExperimentalTime
@Configuration
open class SchemasGenerator(
    private val objectMapper: ObjectMapper,
    private val jsonGenerationStrategies: Set<JsonSchemaGeneratorStrategy>
) {

    private var getJsonSchema: (clazz: KClass<*>) -> ObjectNode?

    companion object : KLogging() {
        private val jsonSchemaBaseTemplate: ObjectNode = initBaseTemplate()

        private fun initBaseTemplate(): ObjectNode {
            val jsonString = ResourcesUtil
                .getResourceSchema("JsonSchemaBaseTemplate")
                ?.inputStream?.bufferedReader().use { it?.readText() }
            return objectNodeFromString(jsonString)
        }
    }

    init {
        getJsonSchema = getJsonSchema()
        generateJsonSchemaFiles()
    }

    private fun generateJsonSchemaFiles() {
        logger.info { "Json schemas generation..." }
        val elapsed: Duration = measureTime {
            val annotatedClasses =
                ClassesUtil.getAnnotatedClassesIn(JsonValidationConfig.dtoPackageName, JsonValidation::class.java)
            for (clazz in annotatedClasses) {
                ResourcesUtil.addSchemaResource(clazz.simpleName, getJsonSchema(clazz.kotlin)?.toString())
            }
        }
        logger.info { "Json schemas generation completed in ${elapsed.inWholeMilliseconds}ms" }
    }

    private fun getJsonSchema() = { clazz: KClass<*> ->
        val properties = clazz.memberProperties
            .map { getJsonForProperty(it) }
            .merge()
        val requiredProperties = clazz.getRequiredJsonPropertiesNames(objectMapper)
        val values = mapOf(
            "title" to clazz.qualifiedName,
            "required" to requiredProperties,
            "properties" to properties
        )
        jsonSchemaBaseTemplate.resolveTemplate(values)
    }

    private fun getJsonForProperty(property: KProperty1<out Any, *>): ObjectNode? {
        val value = property
            .getAnnotations()
            .filter { it.annotationClass.hasAnnotation<IsJsonValidation>() }
            .map { annotation ->
                jsonGenerationStrategies
                    .sortedBy { it.getOrdered() }
                    .find { it.resolve(annotation) }
                    ?.generate(annotation, property, getJsonSchema)
            }
            .merge()
        if (value.isEmpty) return null
        val json = newObjectNode()
        json.set<JsonNode>(property.getJsonPropertyName(objectMapper), value)
        return json
    }
}

