group = "app.syncup"

patches {
    about {
        name = "Sync Up"
        description = "Morphe patches for Sync for Reddit"
        source = "git@github.com:RashKash103/sync-up.git"
        author = "RashKash103"
        contact = "https://github.com/RashKash103/sync-up/issues"
        website = "https://github.com/RashKash103/sync-up"
        license = "GPLv3, with additional conditions under Section 7 inherited from Patcheddit and Morphe: " +
                "attribution and project name restrictions. See the LICENSE and NOTICE files."
    }
}

dependencies {
    // Used by the patch list generator.
    implementation(libs.gson)

    // Required due to smali, or build fails. Can be removed once smali is bumped.
    implementation(libs.guava)

    implementation(libs.morphe.patches.library)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("app.morphe.util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs = listOf("-Xcontext-parameters")
    }
}
