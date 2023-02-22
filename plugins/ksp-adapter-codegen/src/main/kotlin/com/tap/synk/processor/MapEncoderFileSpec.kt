package com.tap.synk.processor

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toTypeName

context(ProcessorContext)
internal fun mapEncoderFileSpec(
    packageName: String,
    mapEncoderClassName: String,
    customMapEncoderTypeName: TypeName,
    crdtTypeName: TypeName,
    crdtClassDeclaration: KSClassDeclaration,
    originatingFile: KSFile
) = FileSpec.builder(
    packageName = packageName,
    fileName = mapEncoderClassName
).apply {

    val properties = crdtClassDeclaration.getAllProperties().toList()
    val stringTypeName = symbols.stringType.toTypeName()
    val mapTypeName = Map::class.asTypeName().parameterizedBy(stringTypeName, stringTypeName)

    indent("    ")
    addFileComment("Code generated by SynkAdapter plugin. Do not edit this file.")
    addType(
        TypeSpec.classBuilder(mapEncoderClassName)
            .addSuperinterface(customMapEncoderTypeName)
            .addFunction(
                FunSpec
                    .builder("encode")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("crdt", crdtTypeName)
                    .returns(mapTypeName)
                    .addCode(encodeFunCodeBlock(properties))
                    .build()
            )
            .addFunction(
                FunSpec
                    .builder("decode")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("map", mapTypeName)
                    .returns(crdtTypeName)
                    .addCode(decodeFunCodeBlock(crdtClassDeclaration))
                    .build()
            )
            .addOriginatingKSFile(originatingFile)
            .build()
    )
}.build()

/**
 TODO Support sealed classes, nested classes and array like properties
 */
context(ProcessorContext)
internal fun encodeFunCodeBlock(properties: List<KSPropertyDeclaration>) : CodeBlock {
    val statements = properties.map { property ->
        val name = property.key()
        val type = property.type.resolve()

        val conversion = if(symbols.isComposite(type) || symbols.isString(type)) {
            logger.warn("Synk Adapter Plugin does not currently support generating encoders for classes with nested classes or array properties", property)
            ""
        } else {
            ".toString()"
        }

        "map.put(\"$name\", crdt.$name$conversion)"
    }

    return CodeBlock.builder().apply {
        addStatement("val map = mutableMapOf<String, String>()")
        statements.forEach { statement ->
            addStatement(statement)
        }
        addStatement("return map")
    }.build()
}

context(ProcessorContext)
internal fun decodeFunCodeBlock(classDeclaration: KSClassDeclaration) : CodeBlock {
    val constructor = classDeclaration.primaryConstructor ?: run {
        logger.error("Failed to constructor for ${classDeclaration.simpleName.asString()}", classDeclaration)
        return CodeBlock.of("")
    }

    val params = constructor.parameters.map { it.name?.asString() ?: "" }
    val statement = params.joinToString(separator = "\"], map[\"", "map[\"", "\"]")
    return CodeBlock.builder().apply {
        addStatement("val crdt = %L(%L)", classDeclaration.simpleName.asString(), statement)
        addStatement("return crdt")
    }.build()
}


context(ProcessorContext)
private fun KSPropertyDeclaration.key() : String {
    return if(symbols.isComposite(type.resolve())) {
        logger.warn("Map key serialization of Composite structs unavailable")
        ""
    } else {
        simpleName.asString()
    }
}
