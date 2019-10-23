package vanillagradle.remapper


import org.gradle.workers.WorkerExecutor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.InstructionAdapter
import org.objectweb.asm.commons.MethodRemapper
import org.objectweb.asm.commons.SimpleRemapper
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import vanillagradle.util.OtherUtil
import vanillagradle.VanillaGradleExtension

import javax.inject.Inject
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.regex.Pattern

class OfficialDeobfParser {
    Path CLIENT_MAPPINGS=Path.of(OtherUtil.FINAL_GRADLE_CACHE.toString(),version,"client.txt")
    Path SERVER_MAPPINGS=Path.of(OtherUtil.FINAL_GRADLE_CACHE.toString(),version,"server.txt")
    Path MAPPED_MC_JAR=Path.of(OtherUtil.FINAL_GRADLE_CACHE.toString(),version,"mapped.jar")
    VanillaGradleExtension vanillaGradleExtension
    WorkerExecutor workerExecutor
    @Inject
    OfficialDeobfParser(VanillaGradleExtension vanillaGradleExtension,WorkerExecutor workerExecutor){
        this.vanillaGradleExtension=vanillaGradleExtension
        this.workerExecutor=workerExecutor
    }
    String version=vanillaGradleExtension.minecraftVersion
    void applyMappings(){
        if(!Files.exists(MAPPED_MC_JAR)){
            def merger=new JarMerger()
            merger.merge()
        }
        else{
            def remapper=new SimpleRemapper(parseMappings())
            def methodRemmaper=new MethodRemapper(new InstructionAdapter(),remapper)
            Files.walkFileTree(MAPPED_MC_JAR,new FileVisitor<Path>() {
                @Override
                FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if(dir.contains("mapped.jar"))
                        return FileVisitResult.CONTINUE
                    else return FileVisitResult.TERMINATE
                }

                @Override
                FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if(file.toString().endsWith(".class")){
                        byte[] classBytes=file.bytes
                        def classReader=new ClassReader(classBytes)
                        def methodList=classReader.metaClass.methods
                        for(Map.Entry entry:parseMappings()){
                            if(entry.getValue() instanceof MethodNode)
                                for(MetaMethod method:methodList)
                                    remapper.mapMethodName("",entry.key.toString(),entry.value.toString())
                        }
                        return FileVisitResult.CONTINUE
                    }
                    else return FileVisitResult.SKIP_SIBLINGS
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
    Map parseMappings(){
        def map=new HashMap()
        def pattern= Pattern.compile("-> \\w+")
        def methodPattern=Pattern.compile("\\w+\\(.*\\)")
        if(Files.exists(CLIENT_MAPPINGS) && Files.exists(SERVER_MAPPINGS)){
            def classVisitor=new ClassNode(Opcodes.ASM7)
            try(def stream= Files.newInputStream(CLIENT_MAPPINGS)){
                def mappingText=stream.readLines()
                for(String nodes:mappingText) {
                    for (String node : nodes.split(" ")) {
                        if (node.matches(pattern) && node.length() == 3) {
                            classVisitor.name = node
                            map.put(node,classVisitor)
                        }
                        if (node.matches(pattern) && node.length()==1){
                            def fieldVisitor=new FieldNode(Opcodes.ASM7,node,nodes[1],nodes[0],null) //todo
                            map.put(node,fieldVisitor)
                        }
                        if(node.matches(methodPattern) && node.contains('(') && node.contains(')')){
                            def methodVisitor=new MethodNode(Opcodes.ASM7,node,nodes[1],nodes[0]) //todo mistake
                            map.put(node,methodVisitor)
                        }
                    }
                }
            }
        }
        return map
    }
}
