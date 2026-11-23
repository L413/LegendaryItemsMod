plugins {
    java
}

group = "com.l413"
version = project.property("pluginVersion").toString()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

// 1. Inject the current version into plugin.yml
tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

// 2. Auto-increment the patch number in gradle.properties after building the .jar
tasks.named("jar") {
    doLast {
        val propsFile = file("gradle.properties")
        val current = project.property("pluginVersion").toString()
        val parts = current.split(".").toMutableList()
        parts[2] = (parts[2].toInt() + 1).toString()
        val nextVersion = parts.joinToString(".")
        propsFile.writeText("pluginVersion=$nextVersion\n")
        println("Bumped next build version to $nextVersion")
    }
}