package vanillagradle.remapper

import org.gradle.internal.impldep.com.google.common.collect.HashBiMap
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.commons.InstructionAdapter
import org.objectweb.asm.commons.MethodRemapper
import org.objectweb.asm.commons.SimpleRemapper
import org.objectweb.asm.tree.MethodNode
import vanillagradle.VanillaGradleExtension
import vanillagradle.util.OtherUtil

import javax.inject.Inject
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

abstract class OfficialDeobfParser implements WorkAction {
    Path CLIENT_MAPPINGS = Path.of(OtherUtil.FINAL_GRADLE_CACHE.toString(), version, "client.txt")
    Path SERVER_MAPPINGS = Path.of(OtherUtil.FINAL_GRADLE_CACHE.toString(), version, "server.txt")
    Path MAPPED_MC_JAR = Path.of(OtherUtil.FINAL_GRADLE_CACHE.toString(), version, "mapped.jar")
    VanillaGradleExtension vanillaGradleExtension
    WorkerExecutor workerExecutor

    @Inject
    OfficialDeobfParser(VanillaGradleExtension vanillaGradleExtension, WorkerExecutor workerExecutor) {
        this.vanillaGradleExtension = vanillaGradleExtension
        this.workerExecutor = workerExecutor
    }
    String version = vanillaGradleExtension.minecraftVersion

    void applyMappings() {
        if (!Files.exists(MAPPED_MC_JAR))
            workerExecutor.noIsolation().submit(JarMerger, () -> WorkParameters.None)
        else {
            def remapper = new SimpleRemapper(parseMappings())
            def methodRemmaper = new MethodRemapper(new InstructionAdapter(), remapper)
            Files.walkFileTree(MAPPED_MC_JAR, new FileVisitor<Path>() {
                @Override
                FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (dir.contains("mapped.jar"))
                        return FileVisitResult.CONTINUE
                    else return FileVisitResult.TERMINATE
                }

                @Override
                FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toString().endsWith(".class")) {
                        byte[] classBytes = file.bytes
                        def classReader = new ClassReader(classBytes)
                        def methodList = classReader.metaClass.methods
                        for (Map.Entry entry : parseMappings()) {
                            if (entry.getValue() instanceof MethodNode)
                                for (MetaMethod method : methodList)
                                    remapper.mapMethodName("", entry.key.toString(), entry.value.toString())
                        }
                        return FileVisitResult.CONTINUE
                    } else return FileVisitResult.SKIP_SIBLINGS
                }

                @Override
                FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return null
                }

                @Override
                FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    return null
                }
            })
        }
    }

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
            Files.walkFileTree(CLIENT_MAPPINGS, new SimpleFileVisitor<Path>() {
                @Override
                FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!dir.endsWith("class"))
                        return FileVisitResult.SKIP_SIBLINGS
                    else return FileVisitResult.CONTINUE
                }

                @Override
                FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    def sequence = file.text
                    mapClosure(file)
                    return FileVisitResult.CONTINUE
                }
            })
        }
        //todo 在这里将map写成csv或者别的形式，以防反复调用带来的反复反混淆和性能消耗
        return map
    }

    @Override
    void execute() {
        parseMappings()
    }
}
