import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.io.File

fun main(args: Array<String>) {
    val disposable = Disposer.newDisposable()
    try {
        val environment = KotlinCoreEnvironment.createForProduction(
            disposable, CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES
        )
        val factory = KtPsiFactory(environment.project, false)
        var count = 0
        args.forEach { path ->
            val source = File(path)
            val parsed = factory.createFile(source.name, source.readText())
            val errors = PsiTreeUtil.collectElementsOfType(parsed, PsiErrorElement::class.java)
            errors.forEach { error ->
                val line = parsed.text.take(error.textOffset).count { it == '\n' } + 1
                println("ERROR ${source.name}:$line ${error.errorDescription}")
            }
            count += errors.size
            if (errors.isEmpty()) println("PASS ${source.name}: Kotlin syntax")
        }
        check(count == 0) { "$count syntax errors" }
        println("RESULT ${args.size} files, 0 syntax errors (not full Android type checking)")
    } finally {
        Disposer.dispose(disposable)
    }
}
