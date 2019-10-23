package vanillagradle.task

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import org.objectweb.asm.commons.SimpleRemapper
import vanillagradle.VanillaGradleExtension

import java.nio.file.Path

class RemapJarTask extends Jar {
    private RegularFileProperty input
    private Property<Boolean> addNestedDependencies

    RemapJarTask() {
        super()
        input = project.objects.fileProperty()
        addNestedDependencies = project.objects.property(Boolean)
    }

     @TaskAction
     void doTask() throws Throwable {
         def extension = project.extensions.getByType(VanillaGradleExtension)
         def input = input.getAsFile().get().toPath()
         def output = outputs
     }



    @InputFile
    RegularFileProperty getInput() {
        return input
    }

    @Input
     Property<Boolean> getAddNestedDependencies() {
        return addNestedDependencies
    }
}
