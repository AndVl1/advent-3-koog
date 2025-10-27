rootProject.name = "chatter"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":server")
include(":core")
include(":client")
include(":cli")
include(":koog-service")
include(":shared-models")
include(":mcp:github")
include(":mcp:test")
