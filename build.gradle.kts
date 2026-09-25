// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

// Disable toolchain auto-provisioning to use local JDK
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Disable toolchain auto-provisioning
allprojects {
    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = JavaVersion.VERSION_17.toString()
        targetCompatibility = JavaVersion.VERSION_17.toString()
    }
}

// 构建路径护栏：原生 librime/Boost 构建对路径里的空格敏感，历史上有人用 `subst X: <仓库>`
// 映射一个无空格路径来绕过，结果 CMake / Kotlin 缓存里写入了 `X:\app\...` 绝对路径；
// 之后即使删掉映射，构建仍会去访问 X:\（Kotlin 报 "different roots"、CMake 找不到 boost 源目录），
// 表面现象像"部署反复创建 X 盘"。这里在配置阶段直接把风险说清楚，不再静默依赖映射。
run {
    val projectDirectory = rootProject.projectDir
    val absolute = projectDirectory.absoluteFile
    val canonical = projectDirectory.canonicalFile
    if (absolute != canonical) {
        logger.warn(
            "[CyIME] 正在通过映射盘/目录联接构建：$absolute -> $canonical\n" +
                "        这会把映射盘绝对路径写进 CMake/Kotlin 缓存（如 X:\\app\\...），删掉映射后构建即失效。\n" +
                "        请直接在真实路径构建；用过映射时先 `subst X: /D`，并删除 app/.cxx、app/build/intermediates/cxx、app/build/kotlin。"
        )
    }
    if (absolute.path.contains(' ')) {
        logger.warn(
            "[CyIME] 构建路径含空格：${absolute.path}\n" +
                "        原生 librime/Boost 构建对空格敏感，可能失败；建议把仓库放在不含空格的路径（例如 D:\\GitHub\\CyIME）。\n" +
                "        不要用 `subst` 映射盘绕过：映射会让 CMake/Kotlin 缓存写入映射盘绝对路径，之后删掉映射构建就会失效。"
        )
    }
}