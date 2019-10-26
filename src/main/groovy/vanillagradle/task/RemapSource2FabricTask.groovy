package vanillagradle.task

import org.gradle.api.tasks.SourceTask
import org.gradle.workers.WorkerExecutor

import javax.inject.Inject

class RemapSource2FabricTask extends SourceTask {
    WorkerExecutor workerExecutor
    //todo
    @Inject
    RemapSource2FabricTask(WorkerExecutor workerExecutor){
        this.workerExecutor=workerExecutor
    }
}
