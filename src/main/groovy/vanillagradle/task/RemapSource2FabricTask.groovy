package vanillagradle.task

import com.alibaba.fastjson.JSON
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.impldep.com.google.common.collect.HashBiMap
import org.gradle.internal.impldep.org.objectweb.asm.Opcodes
import org.gradle.workers.WorkerExecutor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.commons.SimpleRemapper
import org.objectweb.asm.tree.ClassNode

import javax.inject.Inject
import java.nio.file.Path

class RemapSource2FabricTask extends SourceTask {
    WorkerExecutor workerExecutor
    @Inject
    RemapSource2FabricTask(WorkerExecutor workerExecutor){
        this.workerExecutor=workerExecutor
    }
    static def yarnMappingParser(Path mapping){
        def mappingJson= JSON.parseObject(mapping.text)
        //second is immediately
        return new Tuple4<>(null,null,null,null)
    }
    @TaskAction
    def doRemapClass(){
        def fileCollection=project.files(project.projectDir)
        fileCollection.forEach(file->{
            def map= HashBiMap.create()
            //todo
            //map.put(null,yarnMappingParser().getSecond())
            def remapper=new SimpleRemapper(map)
            def bytes=file.bytes
            def classReader=new ClassReader(bytes)
            def node=new ClassNode(Opcodes.ASM7)
            classReader.accept(node,2)
            node.methods.forEach(nodes->remapper.mapMethodName("",nodes.name,nodes.desc))
            node.fields.forEach(nodes->remapper.mapFieldName("",nodes.name,nodes.desc))
            node.innerClasses.forEach(nodes->remapper.mapInnerClassName(nodes.name,"",nodes.innerName))
        })
    }
}
