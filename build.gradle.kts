import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	id("net.fabricmc.fabric-loom")
	`maven-publish`
	id("org.jetbrains.kotlin.jvm") version "2.4.0"
	kotlin("plugin.serialization") version "2.4.0"
}

version = providers.gradleProperty("mod_version").get()
group = providers.gradleProperty("maven_group").get()

loom {
	splitEnvironmentSourceSets()
	mods {
		register("mob-enchant") {
			sourceSet(sourceSets.main.get())
			sourceSet(sourceSets.getByName("client"))
		}
	}
}

fabricApi {
	configureDataGeneration {
		client = true
	}
}

dependencies {
	minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
	implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
	implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")
	implementation("net.fabricmc:fabric-language-kotlin:${providers.gradleProperty("fabric_kotlin_version").get()}")
}

tasks.processResources {
	val version = version
	inputs.property("version", version)
	filesMatching("fabric.mod.json") { expand("version" to version) }
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 25
}

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_25 } }

java {
	toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
	withSourcesJar()
	sourceCompatibility = JavaVersion.VERSION_25
	targetCompatibility = JavaVersion.VERSION_25
}

tasks.jar {
	val projectName = project.name
	inputs.property("projectName", projectName)
	from("LICENSE") { rename { "${it}_$projectName" } }
}

publishing {
	publications {
		register<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}
}

tasks.register<JavaExec>("runFindMethods") {
	classpath = sourceSets.getByName("client").compileClasspath + sourceSets.getByName("client").runtimeClasspath
	mainClass.set("FindMethods")
}
