package vanillagradle.task

import com.alibaba.fastjson.JSONObject
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import vanillagradle.VanillaGradleExtension
import vanillagradle.util.LaunchMCParser
import vanillagradle.remapper.JarMerger
import vanillagradle.util.OtherUtil

import javax.inject.Inject
import java.nio.file.Files
import java.nio.file.Path

class NewProjectWithDeobfTask extends SourceTask {
    Path MAPPED_MINECRAFT=Path.of(OtherUtil.FINAL_GRADLE_CACHE.toString(),version,"mapped.jar")
    WorkerExecutor workerExecutor
    Property<JSONObject> versionJson
    def version=extensions.getByType(VanillaGradleExtension).minecraftVersion

    @Inject
    NewProjectWithDeobfTask(WorkerExecutor workerExecutor) {
        this.workerExecutor = workerExecutor
    }

    void mergeVanillaJars() {
        if(!Files.exists(MAPPED_MINECRAFT)) {
            def merger = new JarMerger()
            if (!Files.exists(merger.getInputClient()) || !Files.exists(merger.getInputServer())) {
                workerExecutor.noIsolation().submit(LaunchMCParser, parameter -> { })
            }
        }
    }
    def createProject(){
        //project.properties.put()
    }
    @TaskAction
    void downloadDependencies(){
        this.project.repositories.mavenCentral()
        this.project.repositories.mavenLocal()
        this.project.repositories.maven {
            url= "https://libraries.minecraft.net/"
            url= 'https://maven.aliyun.com/repository/public/'
        }
        this.project.dependencies.create(()-> {
            def iterator = versionJson.entrySet().stream().filter(entry -> entry.key == "name").iterator()
            if(iterator.hasNext())
                url=iterator.next()
        })
    }
}




