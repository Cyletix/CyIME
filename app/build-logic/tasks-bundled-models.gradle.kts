val bundleModels = providers.gradleProperty("bundleModels").orNull == "true"
if (bundleModels) {
    val prepare = tasks.register<Exec>("prepareBundledModels") {
        val python = providers.gradleProperty("pythonExecutable").orNull ?: "python"
        commandLine(python, rootProject.file("scripts/prepare-bundled-models.py").absolutePath)
        inputs.files(rootProject.file("scripts/prepare-bundled-models.py"),
            file("src/main/java/com/kingzcheung/xime/model/BuiltinModelCatalog.kt"),
            file("src/main/java/com/kingzcheung/xime/speech/SpeechModelCatalog.kt"))
        outputs.dir(layout.buildDirectory.dir("generated/bundled-model-assets"))
    }
    tasks.named("preBuild").configure { dependsOn(prepare) }
}
