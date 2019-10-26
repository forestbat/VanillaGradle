package vanillagradle.task

import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import vanillagradle.VanillaGradleExtension
import vanillagradle.remapper.OfficialDeobfParser
import vanillagradle.util.LaunchMCParser
import vanillagradle.util.OtherUtil

import javax.inject.Inject
import java.nio.file.Files
import java.nio.file.Path

class MigrateMappingTask extends SourceTask {
    WorkerExecutor executor
    @Inject
    MigrateMappingTask(WorkerExecutor executor){
        this.executor=executor
    }
    @TaskAction
    void doTask() throws Throwable {
        def extension = project.extensions.getByType(VanillaGradleExtension)
        project.logger.lifecycle(":loading mappings")

        Path clientMappingPath = Path.of(OtherUtil.FINAL_GRADLE_CACHE.toString(),extension.minecraftVersion,"client.txt")
        Path serverMappingPath=Path.of(OtherUtil.FINAL_GRADLE_CACHE.toString(),extension.minecraftVersion,"server.txt")
        if(!Files.exists(clientMappingPath) || !Files.exists(serverMappingPath)){
            executor.noIsolation().submit(LaunchMCParser,()->WorkParameters.None)
        }
        else{
            def parser=new OfficialDeobfParser(extension,executor)
            def collection=project.files(project.projectDir)
            for(File file:collection){
                byte[] bytes=file.bytes
                //todo
            }
        }
    }
}
