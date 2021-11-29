description = "Core API for testing MQTT Applications."

plugins {
    `java-library`
    id("com.github.hierynomus.license") version "0.16.1"
}

dependencies {
    api(project(":testcontainers"))
    api("org.jetbrains:annotations:${property("annotations.version")}")

    implementation("org.apache.commons:commons-lang3:${property("commons-lang.version")}")
    implementation("com.google.guava:guava:${property("guava.version")}")
    implementation("commons-io:commons-io:${property("commons-io.version")}")
    implementation("org.javassist:javassist:${property("javassist.version")}")
    implementation("org.jboss.shrinkwrap:shrinkwrap-api:${property("shrinkwrap.version")}")
    implementation("org.jboss.shrinkwrap:shrinkwrap-impl-base:${property("shrinkwrap.version")}")
    implementation("net.lingala.zip4j:zip4j:${property("zip4j.version")}")

    /*  This dependency needs to be explicitly added, because shrinkwrap-resolver-api-maven-embedded
        and shrinkwrap-resolver-impl-maven-embedded depend on different versions of it.
        This would lead to issues when the HiveMQ Testcontainer is included in maven projects. */
    implementation("org.codehaus.plexus:plexus-utils:3.2.1")
    implementation("org.jboss.shrinkwrap.resolver:shrinkwrap-resolver-api-maven-embedded:${property("shrinkwrap-resolver.version")}") {
        exclude("org.codehouse.plexus", "plexus-utils")
    }
    runtimeOnly("org.jboss.shrinkwrap.resolver:shrinkwrap-resolver-impl-maven-embedded:${property("shrinkwrap-resolver.version")}") {
        exclude("org.codehouse.plexus", "plexus-utils")
    }

    compileOnly("com.hivemq:hivemq-extension-sdk:${property("hivemq-extension-sdk.version")}")

    testImplementation("org.junit.jupiter:junit-jupiter-api:${property("junit5.version")}")
    testImplementation("com.hivemq:hivemq-extension-sdk:${property("hivemq-extension-sdk.version")}")
    testImplementation("com.hivemq:hivemq-mqtt-client:${property("hivemq-mqtt-client.version")}")
    testImplementation("org.apache.httpcomponents:httpclient:${property("httpclient.version")}")
    testImplementation("ch.qos.logback:logback-classic:${property("logback.version")}")

}

tasks.withType<Test> {
    useJUnitPlatform()
}

license {
    header = projectDir.resolve("HEADER")
    mapping("java", "SLASHSTAR_STYLE")
    excludes(mutableListOf("**/gradle-wrapper.properties", "**/gradlew", "**/gradlew.bat"))
}
