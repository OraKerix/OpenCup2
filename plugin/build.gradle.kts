plugins {
    java
    id("com.gradleup.shadow") version "8.3.2"
}

dependencies {
    implementation(project(":framework"))   // pulls in framework classes

    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("net.dmulloy2:ProtocolLib:5.1.0")
    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveFileName.set("opencup-${project.version}.jar")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
