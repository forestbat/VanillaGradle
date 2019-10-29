package vanillagradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.buildinit.tasks.InitBuild
import org.gradle.plugins.ide.eclipse.model.EclipseModel
import org.gradle.plugins.ide.idea.model.IdeaModel
import vanillagradle.task.MigrateMappingTask
import vanillagradle.task.NewProjectWithDeobfTask
import vanillagradle.task.Publish2MailTask
import vanillagradle.task.RemapJarTask
import vanillagradle.task.RemapSource2FabricTask
import vanillagradle.task.RunMCClientTask

import java.util.function.Predicate


class VanillaGradle implements Plugin<Project> {
    Project project
    @Override
    void apply(Project project) {
        this.project=project
        project.register("publish2Mail", Publish2MailTask)
        project.register("deobf", NewProjectWithDeobfTask)
        project.register("remapJar", RemapJarTask)
        project.register("migrate", MigrateMappingTask)
        project.register("publish-fabric", RemapSource2FabricTask)
        project.register("runMCClient", RunMCClientTask)

        project.extensions.add("minecraft",VanillaGradleExtension)
        project.tasks.getByName("NewProjectWithDeobfTask").dependsOn(InitBuild)
    }
    protected void configureIDEs() {
        // IDEA
        IdeaModel ideaModel = (IdeaModel) project.getExtensions().getByName("idea")
        ideaModel.getModule().getExcludeDirs().addAll(project.files(".gradle", "build", ".idea", "out").getFiles())
        ideaModel.getModule().setDownloadJavadoc(true)
        ideaModel.getModule().setDownloadSources(true)
        ideaModel.getModule().setInheritOutputDirs(true)

        // ECLIPSE
        EclipseModel eclipseModel = (EclipseModel) project.getExtensions().getByName("eclipse")
    }

    private void addModule(Project project, String configuration, DependencyResult module) {
        if (module instanceof ResolvedDependencyResult) {
            if (module.getFrom().getId() instanceof ModuleComponentIdentifier) {
                ModuleComponentIdentifier mci = ((ModuleComponentIdentifier) module.getFrom().getId())
                String moduleId = mci.getGroup() + ":" + mci.getModule() + ":" + mci.getVersion()
                project.getDependencies().add(configuration, project.getDependencies().module(moduleId))
                project.getLogger().debug("Loom addModule " + moduleId + " to " + configuration)
            }

            for (DependencyResult child : ((ResolvedDependencyResult) module).getSelected().getDependencies()) {
                addModule(project, configuration, child)
            }
        }
    }

    private boolean findAndAddModule(Project project, String configuration, DependencyResult dep, Predicate<ModuleComponentIdentifier> predicate) {
        boolean found = false

        if (dep instanceof ResolvedDependencyResult) {
            if (dep.getFrom().getId() instanceof ModuleComponentIdentifier) {
                ModuleComponentIdentifier mci = ((ModuleComponentIdentifier) dep.getFrom().getId())
                if (predicate.test(mci)) {
                    addModule(project, configuration, dep)
                    found = true
                }
            }

            for (DependencyResult child : ((ResolvedDependencyResult) dep).getSelected().getDependencies()) {
                findAndAddModule(project, configuration, child, predicate)
            }
        }
        return found
    }
}
