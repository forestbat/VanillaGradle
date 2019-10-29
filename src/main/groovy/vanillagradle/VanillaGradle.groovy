package vanillagradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.buildinit.tasks.InitBuild
import vanillagradle.task.MigrateMappingTask
import vanillagradle.task.NewProjectWithDeobfTask
import vanillagradle.task.Publish2MailTask
import vanillagradle.task.RemapJarTask
import vanillagradle.task.RemapSource2FabricTask
import vanillagradle.task.RunMCClientTask


class VanillaGradle implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.register("publish2Mail", Publish2MailTask)
        project.register("deobf", NewProjectWithDeobfTask)
        project.register("remapJar", RemapJarTask)
        project.register("migrate", MigrateMappingTask)
        project.register("publish-fabric", RemapSource2FabricTask)
        project.register("runMCClient", RunMCClientTask)

        project.extensions.add("minecraft",VanillaGradleExtension)
        project.tasks.getByName("NewProjectWithDeobfTask").dependsOn(InitBuild)
    }
}
