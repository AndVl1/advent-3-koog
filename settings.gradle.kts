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
include(":mcp:telegraph")
include(":mcp:googledocs")
include(":mcp:test")
include(":telegram-bot")
include(":desktop-app")
