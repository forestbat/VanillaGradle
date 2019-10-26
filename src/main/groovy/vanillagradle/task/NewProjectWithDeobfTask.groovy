package vanillagradle.task

import com.alibaba.fastjson.JSONObject
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import vanillagradle.VanillaGradleExtension
import vanillagradle.util.LaunchMCParser
import vanillagradle.remapper.JarMerger
import vanillagradle.util.OtherUtil

import javax.inject.Inject
import java.nio.file.Files
import java.nio.file.Path

class NewProjectWithDeobfTask extends SourceTask {
    def MAPPED_MINECRAFT=Path.of(OtherUtil.FINAL_GRADLE_CACHE.toString(),version,"mapped.jar")
    def MINECRAFT_CLIENT=Path.of(OtherUtil.FINAL_GRADLE_CACHE.toString(),version,"client.jar")
    def MINECRAFT_SERVER=Path.of(OtherUtil.FINAL_GRADLE_CACHE.toString(),version,"server.jar")
    WorkerExecutor workerExecutor
    Property<JSONObject> versionJson
    def version=extensions.getByType(VanillaGradleExtension).minecraftVersion

    @Inject
    NewProjectWithDeobfTask(WorkerExecutor workerExecutor) {
        this.workerExecutor = workerExecutor
    }
    @TaskAction
    def preCreateProject(){
        if(!Files.exists(MAPPED_MINECRAFT)) {
            if (!Files.exists(MINECRAFT_CLIENT) || !Files.exists(MINECRAFT_SERVER)) {
                workerExecutor.noIsolation().submit(LaunchMCParser, () -> WorkParameters.None)
                workerExecutor.noIsolation().submit(JarMerger,()->WorkParameters.None)
            }
            else workerExecutor.noIsolation().submit(JarMerger,()->WorkParameters.None)
        }
    }
    @TaskAction
    void prepareDependencies(){
        this.project.repositories.mavenCentral()
        this.project.repositories.mavenLocal()
        this.project.repositories.maven {
            url= "https://libraries.minecraft.net/"
            url= 'https://maven.aliyun.com/repository/public/'
        }
        this.project.dependencies.create(depend -> {
            def iterator = versionJson.get().entrySet().stream().
                    filter(entry -> entry.key == "name").iterator()
            if(iterator.hasNext())
                depend=iterator.next()
        })
    }
}




