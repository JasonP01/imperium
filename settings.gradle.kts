enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "imperium-parent"

includeBuild("imperium-build-logic")
include("imperium-common")
include("imperium-discord")
include("imperium-mindustry")
if (file("imperium-migrator").exists()) include("imperium-migrator")
