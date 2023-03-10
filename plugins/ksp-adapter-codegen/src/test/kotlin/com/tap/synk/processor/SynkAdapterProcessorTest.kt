package com.tap.synk.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.symbolProcessorProviders
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

internal class SynkAdapterProcessorTest {

    @Rule
    @JvmField
    var temporaryFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun `compilation fails when target does not implement IDResolver`() {
        val compilationResult = compile(FOO_NOT_IMPLEMENTED_ID_RESOLVER)

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, compilationResult.exitCode)
        val expectedMessage = "@SynkAdapter annotated class com.test.processor.FooResolver must implement IDResolver interface"
        assertTrue("Expected message containing text $expectedMessage but got: ${compilationResult.messages}") {
            compilationResult.messages.contains(expectedMessage)
        }
    }

    @Test
    fun `compilation succeeds when target implement IDResolver interface`() {
        val compilationResult = compile(FOO_DATA_CLASS, FOO_ID_RESOLVER)

        assertEquals(KotlinCompilation.ExitCode.OK, compilationResult.exitCode)
        assertSourceEquals(
            """
                // Code generated by SynkAdapter plugin. Do not edit this file.
                package com.test.processor
                
                import com.tap.synk.adapter.SynkAdapter
                import com.tap.synk.encode.MapEncoder
                import com.tap.synk.resolver.IDResolver
                
                public class FooSynkAdapter(
                    private val idResolver: IDResolver<Foo> = FooResolver(),
                    private val mapEncoder: MapEncoder<Foo> = FooMapEncoder(),
                ) : SynkAdapter<Foo>, IDResolver<Foo> by idResolver, MapEncoder<Foo> by mapEncoder
            """.trimIndent(),
            compilationResult.sourceFor("FooSynkAdapter.kt")
        )

        assertSourceEquals(
            """
                // Code generated by SynkAdapter plugin. Do not edit this file.
                package com.test.processor
                
                import com.tap.synk.encode.MapEncoder
                import kotlin.String
                import kotlin.collections.Map
                
                public class FooMapEncoder : MapEncoder<Foo> {
                    public override fun encode(crdt: Foo): Map<String, String> {
                        val map = mutableMapOf<String, String>()
                        map["bar"] = crdt.bar
                        map["baz"] = crdt.baz.toString()
                        map["bim"] = crdt.bim.toString()
                        return map
                    }

                    public override fun decode(map: Map<String, String>): Foo {
                        val crdt = Foo(
                            map["bar"]!!,
                            map["baz"]!!.toInt(),
                            map["bim"]!!.toBoolean(),
                        )
                        return crdt
                    }
                }
            """.trimIndent(),
            compilationResult.sourceFor("FooMapEncoder.kt")
        )
    }

    @Test
    fun `compilation succeeds when sealed target implement IDResolver interface`() {
        val compilationResult = compile(FOO_SEALED_CLASS, FOO_SEALED_ID_RESOLVER)

        assertEquals(KotlinCompilation.ExitCode.OK, compilationResult.exitCode)
        assertSourceEquals(
            """
                // Code generated by SynkAdapter plugin. Do not edit this file.
                package com.test.processor
                
                import com.tap.synk.adapter.SynkAdapter
                import com.tap.synk.encode.MapEncoder
                import com.tap.synk.resolver.IDResolver
                
                public class FooSynkAdapter(
                    private val idResolver: IDResolver<Foo> = FooResolver(),
                    private val mapEncoder: MapEncoder<Foo> = FooMapEncoder(),
                ) : SynkAdapter<Foo>, IDResolver<Foo> by idResolver, MapEncoder<Foo> by mapEncoder
            """.trimIndent(),
            compilationResult.sourceFor("FooSynkAdapter.kt")
        )

        assertSourceEquals(
            """
                // Code generated by SynkAdapter plugin. Do not edit this file.
                package com.test.processor
                
                import com.tap.synk.encode.MapEncoder
                import kotlin.String
                import kotlin.collections.Map
                
                public class BarMapEncoder : MapEncoder<Foo.Bar> {
                    public override fun encode(crdt: Foo.Bar): Map<String, String> {
                        val map = mutableMapOf<String, String>()
                        map["bar"] = crdt.bar
                        map["baz"] = crdt.baz.toString()
                        map["bim"] = crdt.bim.toString()
                        return map
                    }

                    public override fun decode(map: Map<String, String>): Foo.Bar {
                        val crdt = Foo.Bar(
                            map["bar"]!!,
                            map["baz"]!!.toInt(),
                            map["bim"]!!.toBoolean(),
                        )
                        return crdt
                    }
                }
            """.trimIndent(),
            compilationResult.sourceFor("BarMapEncoder.kt")
        )

        assertSourceEquals(
            """
                // Code generated by SynkAdapter plugin. Do not edit this file.
                package com.test.processor
                
                import com.tap.synk.encode.MapEncoder
                import kotlin.String
                import kotlin.collections.Map
                
                public class BazMapEncoder : MapEncoder<Foo.Baz> {
                    public override fun encode(crdt: Foo.Baz): Map<String, String> {
                        val map = mutableMapOf<String, String>()
                        map["bing"] = crdt.bing
                        map["bam"] = crdt.bam
                        return map
                    }

                    public override fun decode(map: Map<String, String>): Foo.Baz {
                        val crdt = Foo.Baz(
                            map["bing"]!!,
                            map["bam"]!!,
                        )
                        return crdt
                    }
                }
            """.trimIndent(),
            compilationResult.sourceFor("BazMapEncoder.kt")
        )

        assertSourceEquals(
            """
                // Code generated by SynkAdapter plugin. Do not edit this file.
                package com.test.processor

                import com.tap.synk.encode.MapEncoder
                import kotlin.String
                import kotlin.collections.Map

                public enum class FooMapEncoderType {
                    Bar,
                    Baz,
                }

                public class FooMapEncoder(
                    private val barEncoder: MapEncoder<Foo.Bar> = BarMapEncoder(),
                    private val bazEncoder: MapEncoder<Foo.Baz> = BazMapEncoder(),
                ) : MapEncoder<Foo> {
                    public override fun encode(crdt: Foo): Map<String, String> {
                        val map = when(crdt) {
                            is Foo.Bar -> barEncoder.encode(crdt)
                            is Foo.Baz -> bazEncoder.encode(crdt)
                        }
                        val type = when(crdt) {
                            is Foo.Bar -> FooMapEncoderType.Bar.ordinal
                            is Foo.Baz -> FooMapEncoderType.Baz.ordinal
                        }
                        return map + mutableMapOf("*type" to type.toString())
                    }

                    public override fun decode(map: Map<String, String>): Foo {
                        val type = map["*type"]?.toIntOrNull() ?: 0
                        val crdt = when(type) {
                            FooMapEncoderType.Bar.ordinal -> barEncoder.decode(map)
                            FooMapEncoderType.Baz.ordinal -> bazEncoder.decode(map)
                            else -> throw Exception("Unknown encoded sealed class type")
                        }
                        return crdt
                    }
                }
            """.trimIndent(),
            compilationResult.sourceFor("FooMapEncoder.kt")
        )
    }

    @Test
    fun `compilation succeeds when collection subtype target implements IDResolver interface`() {
        val compilationResult = compile(FOO_COLLECTION_CLASS, FOO_COLLECTION_ID_RESOLVER)

        assertEquals(KotlinCompilation.ExitCode.OK, compilationResult.exitCode)
        assertSourceEquals(
            """
                // Code generated by SynkAdapter plugin. Do not edit this file.
                package com.test.processor
                
                import com.tap.synk.adapter.SynkAdapter
                import com.tap.synk.encode.MapEncoder
                import com.tap.synk.resolver.IDResolver
                
                public class FooSynkAdapter(
                    private val idResolver: IDResolver<Foo> = FooResolver(),
                    private val mapEncoder: MapEncoder<Foo> = FooMapEncoder(),
                ) : SynkAdapter<Foo>, IDResolver<Foo> by idResolver, MapEncoder<Foo> by mapEncoder
            """.trimIndent(),
            compilationResult.sourceFor("FooSynkAdapter.kt")
        )

        assertSourceEquals(
            """
                // Code generated by SynkAdapter plugin. Do not edit this file.
                package com.test.processor
                
                import com.tap.synk.encode.BooleanEncoder
                import com.tap.synk.encode.ListEncoder
                import com.tap.synk.encode.MapEncoder
                import com.tap.synk.encode.SetEncoder
                import com.tap.synk.encode.StringEncoder
                import kotlin.Boolean
                import kotlin.String
                import kotlin.collections.List
                import kotlin.collections.Map
                import kotlin.collections.Set
                
                public class FooMapEncoder(
                    private val barListEncoderString: MapEncoder<List<String>> =
                            ListEncoder<String>("bar", StringEncoder),
                    private val bimSetEncoderBoolean: MapEncoder<Set<Boolean>> =
                            SetEncoder<Boolean>("bim", BooleanEncoder),
                ) : MapEncoder<Foo> {
                    public override fun encode(crdt: Foo): Map<String, String> {
                        val map = mutableMapOf<String, String>()
                        map["baz"] = crdt.baz
                        return map + barListEncoderString.encode(crdt.bar) + bimSetEncoderBoolean.encode(crdt.bim)
                    }

                    public override fun decode(map: Map<String, String>): Foo {
                        val crdt = Foo(
                            barListEncoderString.decode(map.filter { it.key.contains("bar|") }),
                            map["baz"]!!,
                            bimSetEncoderBoolean.decode(map.filter { it.key.contains("bim|") }),
                        )
                        return crdt
                    }
                }
            """.trimIndent(),
            compilationResult.sourceFor("FooMapEncoder.kt")
        )
    }

    @Test
    fun `compilation succeeds when target has layers of nested classes`() {
        val compilationResult = compile(FOO_BIM_SUB_CLASS, FOO_BAR_SUB_CLASS, FOO_DATA_SUB_CLASS, FOO_BAR_SUB_CLASS_RESOLVER)

        assertEquals(KotlinCompilation.ExitCode.OK, compilationResult.exitCode)
        assertSourceEquals(
            """
                // Code generated by SynkAdapter plugin. Do not edit this file.
                package com.test.processor
                
                import com.tap.synk.adapter.SynkAdapter
                import com.tap.synk.encode.MapEncoder
                import com.tap.synk.resolver.IDResolver
                
                public class FooSynkAdapter(
                    private val idResolver: IDResolver<Foo> = FooResolver(),
                    private val mapEncoder: MapEncoder<Foo> = FooMapEncoder(),
                ) : SynkAdapter<Foo>, IDResolver<Foo> by idResolver, MapEncoder<Foo> by mapEncoder
            """.trimIndent(),
            compilationResult.sourceFor("FooSynkAdapter.kt")
        )

        assertSourceEquals(
            """
                // Code generated by SynkAdapter plugin. Do not edit this file.
                package com.test.processor
                
                import com.tap.synk.encode.MapEncoder
                import kotlin.String
                import kotlin.collections.Map
                
                public class FooMapEncoder(
                    private val barMapEncoder: MapEncoder<Bar> = BarMapEncoder(),
                ) : MapEncoder<Foo> {
                    public override fun encode(crdt: Foo): Map<String, String> {
                        val map = mutableMapOf<String, String>()
                        map["baz"] = crdt.baz
                        return map + barMapEncoder.encode(crdt.bar)
                    }

                    public override fun decode(map: Map<String, String>): Foo {
                        val crdt = Foo(
                            barMapEncoder.decode(map.filter { it.key.contains("bar|") }),
                            map["baz"]!!,
                        )
                        return crdt
                    }
                }
            """.trimIndent(),
            compilationResult.sourceFor("FooMapEncoder.kt")
        )

        assertSourceEquals(
            """
                // Code generated by SynkAdapter plugin. Do not edit this file.
                package com.test.processor

                import com.tap.synk.encode.MapEncoder
                import kotlin.String
                import kotlin.collections.Map

                public class BarMapEncoder(
                    private val bimMapEncoder: MapEncoder<Bim> = BimMapEncoder(),
                ) : MapEncoder<Bar> {
                    public override fun encode(crdt: Bar): Map<String, String> {
                        val map = mutableMapOf<String, String>()
                        map["second"] = crdt.second
                        return map + bimMapEncoder.encode(crdt.bim)
                    }

                    public override fun decode(map: Map<String, String>): Bar {
                        val crdt = Bar(
                            bimMapEncoder.decode(map.filter { it.key.contains("bim|") }),
                            map["second"]!!,
                        )
                        return crdt
                    }
                }
            """.trimIndent(),
            compilationResult.sourceFor("BarMapEncoder.kt")
        )

        assertSourceEquals(
            """
                // Code generated by SynkAdapter plugin. Do not edit this file.
                package com.test.processor
                
                import com.tap.synk.encode.MapEncoder
                import kotlin.String
                import kotlin.collections.Map
                
                public class BimMapEncoder : MapEncoder<Bim> {
                    public override fun encode(crdt: Bim): Map<String, String> {
                        val map = mutableMapOf<String, String>()
                        map["first"] = crdt.first
                        map["second"] = crdt.second
                        return map
                    }

                    public override fun decode(map: Map<String, String>): Bim {
                        val crdt = Bim(
                            map["first"]!!,
                            map["second"]!!,
                        )
                        return crdt
                    }
                }
            """.trimIndent(),
            compilationResult.sourceFor("BimMapEncoder.kt")
        )
    }

    @Test
    fun `compilation succeeds when target has a collection with a data class as the generic type`() {
        val compilationResult = compile(BAR_COLLECTION_DATA_CLASS, FOO_COLLECTION_DATA_CLASS, FOO_COLLECTION_DATA_CLASS_RESOLVER)

        assertEquals(KotlinCompilation.ExitCode.OK, compilationResult.exitCode)
        assertSourceEquals(
            """
                // Code generated by SynkAdapter plugin. Do not edit this file.
                package com.test.processor
                
                import com.tap.synk.adapter.SynkAdapter
                import com.tap.synk.encode.MapEncoder
                import com.tap.synk.resolver.IDResolver
                
                public class FooSynkAdapter(
                    private val idResolver: IDResolver<Foo> = FooResolver(),
                    private val mapEncoder: MapEncoder<Foo> = FooMapEncoder(),
                ) : SynkAdapter<Foo>, IDResolver<Foo> by idResolver, MapEncoder<Foo> by mapEncoder
            """.trimIndent(),
            compilationResult.sourceFor("FooSynkAdapter.kt")
        )

        assertSourceEquals(
            """
                // Code generated by SynkAdapter plugin. Do not edit this file.
                package com.test.processor
                
                import com.tap.synk.encode.ListEncoder
                import com.tap.synk.encode.MapEncoder
                import kotlin.String
                import kotlin.collections.List
                import kotlin.collections.Map
                
                public class FooMapEncoder(
                    private val barListEncoderBar: MapEncoder<List<Bar>> = ListEncoder<Bar>("bar", BarMapEncoder()),
                ) : MapEncoder<Foo> {
                    public override fun encode(crdt: Foo): Map<String, String> {
                        val map = mutableMapOf<String, String>()
                        return map + barListEncoderBar.encode(crdt.bar)
                    }

                    public override fun decode(map: Map<String, String>): Foo {
                        val crdt = Foo(
                            barListEncoderBar.decode(map.filter { it.key.contains("bar|") }),
                        )
                        return crdt
                    }
                }
            """.trimIndent(),
            compilationResult.sourceFor("FooMapEncoder.kt")
        )

        assertSourceEquals(
            """
            // Code generated by SynkAdapter plugin. Do not edit this file.
            package com.test.processor

            import com.tap.synk.encode.MapEncoder
            import kotlin.String
            import kotlin.collections.Map

            public class BarMapEncoder : MapEncoder<Bar> {
                public override fun encode(crdt: Bar): Map<String, String> {
                    val map = mutableMapOf<String, String>()
                    map["bim"] = crdt.bim
                    return map
                }

                public override fun decode(map: Map<String, String>): Bar {
                    val crdt = Bar(
                        map["bim"]!!,
                    )
                    return crdt
                }
            }
            """.trimIndent(),
            compilationResult.sourceFor("BarMapEncoder.kt")
        )

    }

    private fun compile(vararg source: SourceFile) = KotlinCompilation().apply {
        sources = source.toList()
        symbolProcessorProviders = listOf(SynkAdapterProcessorProvider())
        workingDir = temporaryFolder.root
        inheritClassPath = true
        verbose = false
    }.compile()

    private fun assertSourceEquals(@Language("kotlin") expected: String, actual: String) {
        assertEquals(
            expected.trimIndent(),
            // unfortunate hack needed as we cannot enter expected text with tabs rather than spaces
            actual.trimIndent().replace("\t", "    ")
        )
    }

    private fun KotlinCompilation.Result.sourceFor(fileName: String): String {
        return kspGeneratedSources().find { it.name == fileName }
            ?.readText()
            ?: throw IllegalArgumentException("Could not find file $fileName in ${kspGeneratedSources()}")
    }

    private fun KotlinCompilation.Result.kspGeneratedSources(): List<File> {
        val kspWorkingDir = workingDir.resolve("ksp")
        val kspGeneratedDir = kspWorkingDir.resolve("sources")
        val kotlinGeneratedDir = kspGeneratedDir.resolve("kotlin")
        val javaGeneratedDir = kspGeneratedDir.resolve("java")
        return kotlinGeneratedDir.walk().toList() +
            javaGeneratedDir.walk().toList()
    }

    private val KotlinCompilation.Result.workingDir: File
        get() = checkNotNull(outputDirectory.parentFile)
}
