package com.tap.synk.processor.filespec.encoder

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec

/**
 * FileSpec is intentionally agnostic of AnnotationProcessor types
 */
internal fun mapEncoderFileSpec(
    model: MapEncoder,
    configuration: TypeSpec.Builder.() -> Unit
) = FileSpec.builder(
    packageName = model.className.packageName,
    fileName = model.className.simpleName,
).apply {
    indent("    ")
    addFileComment("Code generated by SynkAdapter plugin. Do not edit this file.")
    model.enum?.let {
        addType(encoderEnum(model.enum))
    }
    addType(
        mapEncoderTypeSpec(
            model.className.simpleName,
            model.extends.typeName,
            encoderFunSpec(model.encodeFunction),
            encoderFunSpec(model.decodeFunction),
            constructor(encoderParameters(model.parameters)),
            encoderProperties(model.parameters),
            configuration
        )
    )
}.build()

/**
 * public class FooMapEncoder : MapEncoder<Foo> {
 *      public override fun encode(crdt: Foo): Map<String, String> {
 *              val map = mutableMapOf<String, String>()
 *              map["bar"] = crdt.bar
 *              map["baz"] = crdt.baz.toString()
 *              map["bim"] = crdt.bim.toString()
 *              return map
 *      }
 *
 *      public override fun decode(map: Map<String, String>): Foo {
 *              val crdt = Foo(map["bar"]!!, map["baz"]!!.toInt(), map["bim"]!!.toBoolean())
 *              return crdt
 *      }
 * }
 */
private fun mapEncoderTypeSpec(
    fileName: String,
    superInterface: TypeName,
    encodeFunction: FunSpec,
    decodeFunction: FunSpec,
    primaryConstructor: FunSpec? = null,
    properties: List<PropertySpec> = emptyList(),
    configuration: TypeSpec.Builder.() -> Unit
): TypeSpec {
    return TypeSpec.classBuilder(fileName).apply {
        primaryConstructor?.let { funSpec ->
            primaryConstructor(funSpec)
        }
        properties.forEach { propertySpec ->
            addProperty(propertySpec)
        }
        addSuperinterface(superInterface)
        addFunction(encodeFunction)
        addFunction(decodeFunction)
        configuration()
    }.build()
}

/**
 * public enum class FooMapEncoderType {
 *     Bar,
 *     Baz,
 * }
 */
private fun encoderEnum(
    encoderEnum: EncoderEnum
): TypeSpec {
    return TypeSpec.enumBuilder(encoderEnum.name).apply {
        encoderEnum.options.forEach { option ->
            addEnumConstant(option)
        }
    }.build()
}

/**
 * public class FooMapEncoder(
 *     listStringEncoder: MapEncoder<List<String>> = ListEncoder("paramName", StringEncoder),
 *     setIntEncoder: MapEncoder<Set<Int>> = SetEncoder("paramName", IntEncoder),
 * )
 */
private fun encoderParameters(paramEncoders: List<EncoderParameter>): List<ParameterSpec> {
    return paramEncoders.mapNotNull { encoderData ->

        when (encoderData) {
            is EncoderParameter.ParameterizedCollectionEncoder -> {
                ParameterSpec.builder(encoderData.variableName(), encoderData.variableType()).apply {
                    defaultValue(encoderDefaultType(encoderData))
                }.build()
            }
            is EncoderParameter.CompositeSubEncoder -> {
                ParameterSpec.builder(encoderData.variableName(), encoderData.variableType()).apply {
                    defaultValue(encoderDefaultType(encoderData))
                }.build()
            }
            is EncoderParameter.SubEncoder -> {
                ParameterSpec.builder(encoderData.variableName(), encoderData.variableType()).apply {
                    defaultValue(encoderDefaultType(encoderData))
                }.build()
            }
            is EncoderParameter.Serializer -> {
                ParameterSpec.builder(encoderData.variableName(), encoderData.variableType()).apply {
                    defaultValue(encoderDefaultType(encoderData))
                }.build()
            }
        }
    }
}

private fun encoderProperties(paramEncoders: List<EncoderParameter>): List<PropertySpec> {
    return paramEncoders.map { parameter ->
        PropertySpec.builder(parameter.variableName(), parameter.variableType()).apply {
            initializer(parameter.variableName())
            addModifiers(KModifier.PRIVATE)
        }.build()
    }
}

/**
 * ListEncoder("paramName", StringEncoder)
 */
private fun encoderDefaultType(paramEncoder: EncoderParameter.ParameterizedCollectionEncoder): CodeBlock {
    return CodeBlock.builder().apply {
        if(paramEncoder.instantiateNestedEncoder) {
            add("%T(%S,·%T())", paramEncoder.collectionEncoderTypeName, paramEncoder.parameterName, paramEncoder.genericEncoderTypeName)
        } else {
            add("%T(%S,·%T)", paramEncoder.collectionEncoderTypeName, paramEncoder.parameterName, paramEncoder.genericEncoderTypeName)
        }
    }.build()
}

/**
 * FooBarMapEncoder()
 */
private fun encoderDefaultType(paramEncoder: EncoderParameter.CompositeSubEncoder): CodeBlock {
    return CodeBlock.builder().apply {
        add("%T()", paramEncoder.encoderType)
    }.build()
}

/**
 * FooBarMapEncoder()
 */
private fun encoderDefaultType(paramEncoder: EncoderParameter.SubEncoder): CodeBlock {
    return CodeBlock.builder().apply {
        add("%T()", paramEncoder.concreteTypeName)
    }.build()
}

/**
 * FooBarMapEncoder()
 */
private fun encoderDefaultType(paramEncoder: EncoderParameter.Serializer): CodeBlock {
    return CodeBlock.builder().apply {
        if(paramEncoder.instantiateSerializer) {
            add("%T()", paramEncoder.concreteTypeName)
        } else {
            add("%T", paramEncoder.concreteTypeName)
        }
    }.build()
}


private fun constructor(parameterSpecs: List<ParameterSpec>): FunSpec? {
    return if (parameterSpecs.isNotEmpty()) {
        FunSpec.constructorBuilder().apply {
            parameterSpecs.forEach { parameterSpec ->
                addParameter(parameterSpec)
            }
        }.build()
    } else null
}

/**
 * public override fun encode(crdt: Foo): Map<String, String> {
 *      val map = mutableMapOf<String, String>()
 *      map["bar"] = crdt.bar
 *      map["baz"] = crdt.baz.toString()
 *      map["bim"] = crdt.bim.toString()
 *      return map
 * }
 */
private fun encoderFunSpec(encodeFunction: EncoderFunction): FunSpec {
    return FunSpec.builder(encodeFunction.type.name()).apply {
        addModifiers(KModifier.OVERRIDE)
        addParameter(encodeFunction.functionParameterName, encodeFunction.functionParameterTypeName)
        returns(encodeFunction.functionReturnTypeName)
        addCode(encoderFunCodeBlock(encodeFunction.type, encodeFunction.encoderFunctionCodeBlock))
    }.build()
}

private fun encoderFunCodeBlock(type: EncoderFunction.Type, encodeFunCodeBlock: EncoderFunctionCodeBlock): CodeBlock {
    return when (type) {
        EncoderFunction.Type.Encode -> {
            when (encodeFunCodeBlock) {
                is EncoderFunctionCodeBlock.Standard -> encodeFunStandardCodeBlock(encodeFunCodeBlock)
                is EncoderFunctionCodeBlock.Delegate -> encodeFunDelegateCodeBlock(encodeFunCodeBlock)
            }
        }
        EncoderFunction.Type.Decode -> {
            when (encodeFunCodeBlock) {
                is EncoderFunctionCodeBlock.Standard -> decodeFunStandardCodeBlock(encodeFunCodeBlock)
                is EncoderFunctionCodeBlock.Delegate -> decodeFunDelegateCodeBlock(encodeFunCodeBlock)
            }
        }
    }
}

/**
 * public override fun encode(crdt: Foo): Map<String, String> {
 *      val map = mutableMapOf<String, String>()
 *      map["bar"] = crdt.bar
 *      map["bim"] = crdt.bim.toString()
 *      return map + bazListEncoder.encode(crdt.baz)
 * }
 */
private fun encodeFunStandardCodeBlock(encodeFunCodeBlock: EncoderFunctionCodeBlock.Standard): CodeBlock {
    val primitives = encodeFunCodeBlock.encodables.filterIsInstance<EncoderFunctionCodeBlockStandardEncodable.Primitive>()
    val serializables = encodeFunCodeBlock.encodables.filterIsInstance<EncoderFunctionCodeBlockStandardEncodable.Serializable>()
    val collections = encodeFunCodeBlock.encodables.filterIsInstance<EncoderFunctionCodeBlockStandardEncodable.NestedClass>()

    val primitiveStatements = primitives.map { param ->
        "map[\"${param.encodedKey}\"] = crdt.${param.encodedKey}${param.conversion}"
    }

    val serializableStatements = serializables.map { param ->
         "map[\"${param.encodedKey}\"] = ${param.serializerVariableName}.serialize(crdt.${param.encodedKey})"
    }

    val aggregate = collections.fold("return map") { acc, param ->
        acc + " + " + param.encoderVariableName + ".encode(crdt." + param.encodedKey + ")"
    }

    return CodeBlock.builder().apply {
        addStatement("val map = mutableMapOf<String, String>()")
        primitiveStatements.forEach { statement ->
            addStatement(statement)
        }
        serializableStatements.forEach { statement ->
            addStatement(statement)
        }
        addStatement(aggregate)
    }.build()
}

/**
 * val map = when(crdt) {
 *      is Foo.Bar -> barEncoder.encode(crdt)
 *      is Foo.Baz -> bazEncoder.encode(crdt)
 * }
 * val type = when(crdt) {
 *      is Foo.Bar -> FooMapEncoderType.Bar.ordinal
 *      is Foo.Baz -> FooMapEncoderType.Baz.ordinal
 * }
 * return map + mutableMapOf("*type" to type.toString())
 */
private fun encodeFunDelegateCodeBlock(encodeFunCodeBlock: EncoderFunctionCodeBlock.Delegate): CodeBlock {
    val encoderStatements = encodeFunCodeBlock.subEncoders.map { subEncoder ->
        "is %T -> %L.encode(crdt)" to listOf(subEncoder.typeName, subEncoder.variableName)
    }

    val typeStatements = encodeFunCodeBlock.subEncoders.map { subEncoder ->
        "is %T -> %L.ordinal" to listOf(subEncoder.typeName, subEncoder.enumName)
    }

    return CodeBlock.builder().apply {
        beginControlFlow("val map = when(crdt)")
        encoderStatements.forEach { (statement, replacements) ->
            addStatement(statement, *replacements.toTypedArray())
        }
        endControlFlow()

        beginControlFlow("val type = when(crdt)")
        typeStatements.forEach { (statement, replacements) ->
            addStatement(statement, *replacements.toTypedArray())
        }
        endControlFlow()

        addStatement("return map + mutableMapOf(\"*type\" to type.toString())")
    }.build()
}

/**
 * public override fun decode(map: Map<String, String>): Foo {
 *      return Foo(
 *          map["bar"]!!,
 *          bazListEncoder.decode(map.filter { it.contains("baz") }),
 *          map["bim"]!!.toBoolean()
 *      )
 * }
 */
private fun decodeFunStandardCodeBlock(encodeFunCodeBlock: EncoderFunctionCodeBlock.Standard): CodeBlock {
    val params = encodeFunCodeBlock.encodables.map { encodable ->
        when (encodable) {
            is EncoderFunctionCodeBlockStandardEncodable.Primitive -> {
                CodeBlock.builder().apply {
                    add("map[%S]!!%L,\n", encodable.encodedKey, encodable.conversion)
                }.build()
            }
            is EncoderFunctionCodeBlockStandardEncodable.NestedClass -> {
                CodeBlock.builder().apply {
                    add("%L.decode(map.filter { it.key.contains(%S) }),\n", encodable.encoderVariableName, encodable.encodedKey + "|")
                }.build()
            }
            is EncoderFunctionCodeBlockStandardEncodable.Serializable -> {
                CodeBlock.builder().apply {
                    add("%L.deserialize(map[%S]!!),\n", encodable.serializerVariableName, encodable.encodedKey)
                }.build()
            }
        }
    }

    return CodeBlock.builder().apply {
        add("val crdt = %T(\n", encodeFunCodeBlock.type)
        indent()
        params.forEach { codeBlock ->
            add(codeBlock)
        }
        unindent()
        add(")\n")
        addStatement("return crdt")
    }.build()
}

/**
 * public override fun decode(map: Map<String, String>): Foo {
 *      val type = map["*type"]?.toIntOrNull() ?: 0
 *      val foo = when(type) {
 *          FooMapEncoderType.Bar.ordinal -> barMapEncoder.decode(map)
 *          FooMapEncoderType.Baz.ordinal -> bazMapEncoder.decode(map)
 *      }
 *      return foo
 * }
 */
private fun decodeFunDelegateCodeBlock(encodeFunCodeBlock: EncoderFunctionCodeBlock.Delegate): CodeBlock {
    val statements = encodeFunCodeBlock.subEncoders.map { subEncoder ->
        "%L.ordinal -> %L.decode(map)" to listOf(subEncoder.enumName, subEncoder.variableName)
    }

    return CodeBlock.builder().apply {
        addStatement("val type = map[\"*type\"]?.toIntOrNull() ?: 0")
        beginControlFlow("val crdt = when(type)")
        statements.forEach { statementPair ->
            addStatement(statementPair.first, *statementPair.second.toTypedArray())
        }
        addStatement("else -> throw Exception(\"Unknown encoded sealed class type\")")
        endControlFlow()
        addStatement("return crdt")
    }.build()
}
