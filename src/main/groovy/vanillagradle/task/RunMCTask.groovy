package vanillagradle.task

import com.alibaba.fastjson.JSON
import org.gradle.api.tasks.JavaExec
import org.gradle.internal.os.OperatingSystem
import org.gradle.workers.WorkerExecutor
import vanillagradle.VanillaGradleExtension
import vanillagradle.util.LaunchMCParser
import vanillagradle.util.OtherUtil

import javax.inject.Inject
import java.nio.file.Files
import java.nio.file.Path

class RunMCTask extends JavaExec {
    private WorkerExecutor workExecutor
    private def version=project.extensions.getByType(VanillaGradleExtension).minecraftVersion
    private def versionJsonPath= Path.of(OtherUtil.FINAL_GRADLE_CACHE.toString(),version+"-vanilla.json")
    @Inject
    RunMCTask(WorkerExecutor workExecutor) {
        super()
        this.workExecutor=workExecutor
    }

    @Override
    void exec() {
        super.exec()
    }

    String getMain() {
        //todo
        if (project.configurations.getByName("compile").dependencies.contains("net.fabricmc.fabric"))
            return "net.fabricmc.loader.launch.knot.Knot"
        //1.15以上
        if (project.configurations.getByName("compile").dependencies.contains("net.minecraftforge.forge"))
            return "cpw.mods.modlauncher.Launcher"
        else return "net.minecraft.launchwrapper.Launch"
    }


    List<String> getJvmArgs() {
        def jvmList=new ArrayList()
        if(!Files.exists(versionJsonPath))
            workExecutor.noIsolation().submit(LaunchMCParser,parameter->{})
        else{
            //def versionJson=JSON.parseObject(versionJsonPath.text)
            if(OperatingSystem.current().isMacOsX())
                jvmList.add("-XstartOnFirstThread")
            if(OperatingSystem.current().isWindows()){
                jvmList.add("-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump")
                jvmList.add()
            }
        }
        return jvmList
    }
}
