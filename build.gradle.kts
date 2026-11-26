plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

val allowedProjectDependencies = buildMap {
    put(":app", setOf(":application", ":domain", ":infrastructure"))
    put(":application", setOf(":domain"))
    put(":domain", emptySet())
    put(":infrastructure", setOf(":application", ":domain"))
    if (findProject(":inference-spike") != null) put(":inference-spike", emptySet())
}
val androidModules = setOf(":app", ":infrastructure", ":inference-spike")

val verifyModuleDependencies by tasks.registering {
    group = "verification"
    description = "Checks the allowed layer dependencies and keeps the inner layers on pure Kotlin/JVM."

    doLast {
        check(subprojects.map { it.path }.toSet() == allowedProjectDependencies.keys) {
            "Declare the dependency rules for every workspace module."
        }

        for ((path, allowedTargets) in allowedProjectDependencies) {
            for (configuration in project(path).configurations) {
                for (dependency in configuration.dependencies.withType<ProjectDependency>()) {
                    // AGP adds a reference to the Android module under unit or instrumentation test.
                    val isAndroidTestSelfReference = path in androidModules &&
                        dependency.path == path &&
                        (configuration.name.contains("AndroidTest") || configuration.name.contains("UnitTest"))
                    check(dependency.path in allowedTargets || isAndroidTestSelfReference) {
                        "$path:${configuration.name} cannot depend on ${dependency.path}."
                    }
                }
            }
        }

        for (path in listOf(":application", ":domain")) {
            val module = project(path)
            check(module.pluginManager.hasPlugin("org.jetbrains.kotlin.jvm") &&
                !module.pluginManager.hasPlugin("com.android.base")) {
                "$path must use the Kotlin JVM plugin without an Android plugin."
            }

            val classpaths = listOf("compileClasspath", "runtimeClasspath", "testCompileClasspath", "testRuntimeClasspath")
            for (classpath in classpaths) {
                for (dependency in module.configurations.getByName(classpath).allDependencies) {
                    if (dependency is ProjectDependency) continue
                    val isKotlinStdlib = dependency.group == "org.jetbrains.kotlin" &&
                        dependency.name == "kotlin-stdlib"
                    val isTestLibrary = classpath.startsWith("test") &&
                        dependency.group == "junit" && dependency.name == "junit"

                    check(dependency is ExternalModuleDependency && (isKotlinStdlib || isTestLibrary)) {
                        "$path:$classpath cannot depend on ${dependency.group}:${dependency.name}."
                    }
                }
            }
        }
    }
}

tasks.check {
    dependsOn(
        verifyModuleDependencies,
        ":domain:check",
        ":application:check",
        ":infrastructure:testDebugUnitTest",
        ":infrastructure:lintDebug",
        ":app:testDebugUnitTest",
        ":app:lintDebug",
    )
    if (findProject(":inference-spike") != null) {
        dependsOn(":inference-spike:testDebugUnitTest", ":inference-spike:lintDebug")
    }
}
