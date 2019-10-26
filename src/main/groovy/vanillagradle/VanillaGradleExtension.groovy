package vanillagradle

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.internal.impldep.com.google.gson.JsonObject
import vanillagradle.util.OtherUtil

import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.BiPredicate
import java.util.function.Function

class VanillaGradleExtension {
    //String runDir = "run"
    // String refMapName
    // String loaderLaunchMethod
    String minecraftVersion
    boolean autoGenIDERuns = true
    boolean extractJars = false
    //String customManifest = null
    private Project project
    private List<Path> unmappedModList = new ArrayList<>()
    private JsonObject jsonObject

    VanillaGradleExtension(Project project) {
        this.project = project
    }

    @Deprecated
    static Dependency getDependency(Project project, Collection<Configuration> configurations, BiPredicate<String, String> filter) {
        configurations.forEach({ configuration ->
            configuration.getDependencies().forEach({ dependency ->
                String group = dependency.getGroup()
                String name = dependency.getName()
                if (filter.test(group, name)) {
                    project.logger.debug("This is a dependency: " + group + ":" + name + ":" + dependency.getVersion());
                    return dependency
                }
            })
        })
        return null
    }

    def recurseProjects(Function<Project, Object> projectTFunction) {
        var project = this.project
        Object result
        while (project.getRootProject() != project) {
            if ((result = projectTFunction.apply(project)) != null) {
                return result
            }
            project = project.getRootProject()
        }
        result = projectTFunction.apply(project)
        return result
    }

    def getMixinDependency() {
        return recurseProjects({ project ->
            List<Configuration> configurations = new ArrayList<>()
            configurations.add(project.getConfigurations().getByName("compileClasspath"))
            configurations.addAll(project.getBuildscript().getConfigurations())
            return getDependency(project, configurations, { group, name ->
                if (name.equalsIgnoreCase("mixin") && group.equalsIgnoreCase("org.spongepowered"))
                    return true
                if (name.equalsIgnoreCase("sponge-mixin") && group.equalsIgnoreCase("net.fabricmc"))
                    return true
                return false
            }
            )
        })
    }

    def getMixinVersion() {
        Dependency dependency = getMixinDependency() as Dependency
        if (dependency != null) {
            if (dependency.getName().equalsIgnoreCase("net.vanilla"))
                if (Objects.requireNonNull(dependency.getVersion()).split("\\.").length >= 4)
                    return dependency.getVersion().substring(0, dependency.getVersion().lastIndexOf('.')) + "-SNAPSHOT"
            return dependency.getVersion()
        }
        return null
    }
}
