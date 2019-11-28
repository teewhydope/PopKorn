package cc.popkorn.compiler.generators

import cc.popkorn.compiler.utils.splitPackage
import cc.popkorn.mapping.Mapping
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import java.io.File
import javax.annotation.processing.Filer
import javax.lang.model.element.TypeElement
import javax.tools.StandardLocation
import kotlin.reflect.KClass

/**
 * Class to generate Mappings for this module
 *
 * @author Pau Corbella
 * @since 1.0
 */
internal class MappingGenerator(private val directory: File, private val filer:Filer) {
    private val moduleName = getModuleName()

    fun write(values : HashMap<TypeElement, String>, classSuffix:String, resMapping:String) : String {
        val filePackage = "cc.popkorn.mapping.$moduleName${classSuffix}Mapping"
        val code = getCode(values)
        val file = getFile(filePackage, code)
        file.writeTo(directory)

        filer.createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/$resMapping")
            .openWriter()
            .also { it.write("$filePackage;") }
            .close()

        return filePackage
    }



    private fun getCode(values : HashMap<TypeElement, String>):CodeBlock {
        val function = CodeBlock.builder()
        function.add("return when(original){\n")
        values.forEach { (type, resolver) ->
            function.add("    $type::class -> $resolver()\n")
        }
        function.add("    else -> null\n")
        function.add("}\n")
        return function.build()
    }

    private fun getFile(filePackage:String, creationCode:CodeBlock) : FileSpec {
        val producerOfAny = WildcardTypeName.producerOf(Any::class)
        val create = FunSpec.builder("find")
            .addParameter("original", KClass::class.asClassName().parameterizedBy(producerOfAny))
            .addModifiers(KModifier.OVERRIDE)
            .returns(Any::class.asClassName().copy(nullable = true))
            .addCode(creationCode)
            .build()


        val pack = filePackage.splitPackage()
        return FileSpec.builder(pack.first, pack.second)
            .addType(
                TypeSpec.classBuilder(pack.second)
                    .addSuperinterface(Mapping::class)
                    .addFunction(create)
                    .build()
            )
            .build()
    }


    private fun getModuleName() : String{
        val split = directory.absolutePath.split("/")
        split.forEachIndexed { index, s ->
            if (s == "build") return split[index-1].replace(Regex("[^a-zA-Z0-9]"), "").capitalize()
        }
        return "Unknown"
    }

//    internal fun Element.getModuleName(): String{
//        return get(Metadata::class)
//            ?.run { KotlinClassHeader(kind, metadataVersion, bytecodeVersion, data1, data2, extraString, packageName, extraInt) }
//            ?.let { KotlinClassMetadata.read(it) as? KotlinClassMetadata.Class }
//            ?.toKmClass()
//            ?.moduleName
//            ?: ""
//    }


}