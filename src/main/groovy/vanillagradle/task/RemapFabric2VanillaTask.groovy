package vanillagradle.task

import com.alibaba.fastjson.JSON
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.impldep.com.google.common.collect.HashBiMap
import org.gradle.internal.impldep.org.objectweb.asm.Opcodes
import org.gradle.workers.WorkParameters
import org.objectweb.asm.ClassReader
import org.objectweb.asm.commons.SimpleRemapper
import org.objectweb.asm.tree.ClassNode
import vanillagradle.remapper.OfficialDeobfParser

import java.nio.file.Path

//是否实现ClassFileTransformer?
class RemapFabric2VanillaTask extends SourceTask{

}