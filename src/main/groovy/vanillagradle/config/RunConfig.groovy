package vanillagradle.config

import com.alibaba.fastjson.annotation.JSONType
import com.alibaba.fastjson.parser.Feature
import com.alibaba.fastjson.serializer.SerializerFeature

@Deprecated
@JSONType(serialzeFeatures = SerializerFeature.BeanToArray,parseFeatures = Feature.SupportArrayToBean)
class RunConfig {
     String configName
     String projectName
     String mainClass //用于标记Knot这种特殊主类
     String runDirectory
     String programArgs
     String vmArgs

    String getConfigName() {
        return configName
    }

    void setConfigName(String configName) {
        this.configName = configName
    }

    String getProjectName() {
        return projectName
    }

    void setProjectName(String projectName) {
        this.projectName = projectName
    }

    String getMainClass() {
        return mainClass
    }

    void setMainClass(String mainClass) {
        this.mainClass = mainClass
    }

    String getRunDirectory() {
        return runDirectory
    }

    void setRunDirectory(String runDirectory) {
        this.runDirectory = runDirectory
    }

    String getProgramArgs() {
        return programArgs
    }

    void setProgramArgs(String programArgs) {
        this.programArgs = programArgs
    }

    String getVmArgs() {
        return vmArgs
    }

    void setVmArgs(String vmArgs) {
        this.vmArgs = vmArgs
    }
}
