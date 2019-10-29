package vanillagradle.remapper

import org.gradle.internal.impldep.com.google.common.collect.HashBiMap
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import org.objectweb.asm.commons.SimpleRemapper
import vanillagradle.VanillaGradleExtension
import vanillagradle.util.OtherUtil

import javax.inject.Inject
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

abstract class OfficialDeobfParser implements WorkAction {
    Path CLIENT_MAPPINGS = Path.of(OtherUtil.FINAL_GRADLE_CACHE.toString(), version, "client.txt")
    Path SERVER_MAPPINGS = Path.of(OtherUtil.FINAL_GRADLE_CACHE.toString(), version, "server.txt")
    Path MAPPED_CLIENT=Path.of(OtherUtil.FINAL_GRADLE_CACHE.toString(), version, "mapped_client.jar")
    Path MAPPED_SERVER=Path.of(OtherUtil.FINAL_GRADLE_CACHE.toString(), version, "mapped_server.jar")
    Path MAPPED_MINECRAFT = Path.of(OtherUtil.FINAL_GRADLE_CACHE.toString(), version, "mapped_minecraft.jar")
    VanillaGradleExtension vanillaGradleExtension
    WorkerExecutor workerExecutor

    @Inject
    OfficialDeobfParser(VanillaGradleExtension vanillaGradleExtension, WorkerExecutor workerExecutor) {
        this.vanillaGradleExtension = vanillaGradleExtension
        this.workerExecutor = workerExecutor
    }
    String version = vanillaGradleExtension.minecraftVersion

    Map parseMappings() {
        //key为未混淆名，value为已反混淆名
        def map = HashBiMap.create()
        def remapper = new SimpleRemapper(map)
        if (Files.exists(CLIENT_MAPPINGS) && Files.exists(SERVER_MAPPINGS)) {
            def mapClosure = {
                Path path -> try(def inputStream=Files.newInputStream(path)){
                    List<String> lines=inputStream.readLines()
                    for(def line:lines){
                        String[] twoNames=line.split(" -> ")
                        map.put(twoNames[1],twoNames[0])
                    }
                    map.forEach((String key,String value)->{
                        //确定类名和字段名
                        if(!value.contains("(")) {
                            String[] names=value.split("\\.")
                            if(names[names.length-1].contains(" ")) {
                                def mappedFieldName = (names[names.length - 1]).split(" ")[1]
                                map.replace(key,mappedFieldName)
                            }
                            else{
                                def mappedClassName=names[names.length - 1]
                                map.replace(key,mappedClassName)
                            }
                        }
                        //确定descriptor
                        def descriptor=value.find("\\([^)]*\\)")
                        //确定方法名
                        if(value.matches("\\d+:\\d+:[^(]*")) {
                            def mappedMethodName = value.find("\\d+:\\d+:[^(]*").split(" ")[1]
                            map.replace(key,mappedMethodName)
                        }
                        remapper.map(key)
                    })
                }
            }
            def fileVisitor=new SimpleFileVisitor<Path>() {
                @Override
                FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!dir.endsWith("class"))
                        return FileVisitResult.SKIP_SIBLINGS
                    else return FileVisitResult.CONTINUE
                }

                @Override
                FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    mapClosure(file)
                    return FileVisitResult.CONTINUE
                }
            }
            Files.walkFileTree(MAPPED_CLIENT, fileVisitor)
            Files.walkFileTree(MAPPED_SERVER, fileVisitor)
            workerExecutor.noIsolation().submit(JarMerger,()-> WorkParameters.None)
        }
        //todo 在这里将map写成csv或者别的形式，以防反复调用带来的反复反混淆和性能消耗
        return map
    }

    @Override
    void execute() {
        parseMappings()
    }
}
