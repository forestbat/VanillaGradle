package vanillagradle.task

import org.gradle.api.tasks.JavaExec

class RunMCServerTask extends JavaExec {
    @Override
    void exec() {
        super.exec()
    }

    @Override
    void setJvmArgs(List<String> arguments) {
        super.setJvmArgs(arguments)
    }

    @Override
    String getMain() {
        return super.getMain()
    }

    @Override
    JavaExec setMain(String mainClassName) {
        return super.setMain(mainClassName)
    }
}
