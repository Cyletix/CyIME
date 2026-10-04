// Both editions ship the same reviewed Windows transport, generated from its source.
val prepareBuiltinClipboardSync by tasks.registering(Zip::class) {
    from(rootProject.file("plugins/cyime-windows-sync")) {
        include("manifest.yaml", "main.lua")
    }
    archiveFileName.set("windows-clipboard.xipk")
    destinationDirectory.set(layout.buildDirectory.dir("generated/builtin-sync-assets/builtin-plugins"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
tasks.named("preBuild").configure { dependsOn(prepareBuiltinClipboardSync) }
