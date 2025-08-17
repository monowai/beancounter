pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}
rootProject.name = "beancounter"

// Define projects in dependency order (this helps with build order)
include("jar-common")
include("jar-auth")
include("jar-client")
include("jar-shell")
include("svc-data")
include("svc-position")
include("svc-event")
