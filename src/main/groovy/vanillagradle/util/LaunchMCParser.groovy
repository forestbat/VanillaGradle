package vanillagradle.util

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import org.gradle.workers.WorkAction
import vanillagradle.VanillaGradleExtension

import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit

abstract class LaunchMCParser implements WorkAction {
    JSONObject launcherJson
    JSONObject versionJson
    VanillaGradleExtension extension
    LaunchMCParser(VanillaGradleExtension vanillaGradleExtension){
        this.extension=vanillaGradleExtension
    }

    def parseVersionJson(String version) {
        Path launcherJsonPath=Path.of(OtherUtil.FINAL_GRADLE_CACHE.toString(),"all-versions-manifest.json")
        if(!Files.exists(launcherJsonPath)) {
            def httpRequest = HttpRequest.newBuilder(OtherUtil.ALL_MANIFEST).header("If-Not-Match", OtherUtil.loadETag
                    (null, null)).build() //todo
            launcherJson = OtherUtil.HTTP_CLIENT.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofFileDownload(
                    OtherUtil.FINAL_GRADLE_CACHE, StandardOpenOption.CREATE)).
                    thenApply(() -> JSON.parseObject(launcherJsonPath.text)).get(600, TimeUnit.SECONDS)
            project.logger.debug("Getting Json....")
        }
        else{
            launcherJson=JSON.parseObject(launcherJsonPath.text)
        }
        for(Object object:launcherJson.values()) {
            Path versionJsonPath=Path.of(OtherUtil.FINAL_GRADLE_CACHE.toString(),version+"-vanilla.json")
            if (object instanceof String && ((String)object).contains(version+".json") && !Files.exists(versionJsonPath))
            {
                def uri = new URI(object)
                def httpRequestVersion = HttpRequest.newBuilder(uri).build()
                versionJson = OtherUtil.HTTP_CLIENT.sendAsync(httpRequestVersion, HttpResponse.BodyHandlers.ofFileDownload(
                        OtherUtil.FINAL_GRADLE_CACHE,StandardOpenOption.CREATE)).
                        thenApply(()->JSON.parseObject(versionJsonPath.text)).get(600, TimeUnit.SECONDS)
                return versionJson
            }
            else if(Files.exists(versionJsonPath)){
                return JSON.parseObject(versionJsonPath.text)
            }
            else return null
        }
    }

    def parseGameJson(){
        def gamePath= Path.of(OtherUtil.FINAL_GRADLE_CACHE.toString(),extension.minecraftVersion)
        def clientPath=Path.of(gamePath.toString(),"client.jar")
        def serverPath=Path.of(gamePath.toString(),"server.jar")
        def clientMappingPath=Path.of(gamePath.toString(),"client.txt")
        def serverMappingPath=Path.of(gamePath.toString(),"server.txt")
        if(!Files.exists(gamePath)) {
            gamePath=Files.createDirectory(gamePath)
            if(!Files.exists(clientPath)){
                String clientURL = versionJson.getJSONObject("downloads").getJSONObject("client").getString("url")
                def httpClientRequest = HttpRequest.newBuilder().uri(new URI(clientURL)).build()
                OtherUtil.HTTP_CLIENT.sendAsync(httpClientRequest, HttpResponse.BodyHandlers.ofFileDownload(gamePath, StandardOpenOption.CREATE))
            }
            if(!Files.exists(serverPath)){
                String serverURL = versionJson.getJSONObject("downloads").getJSONObject("server").getString("url")
                def httpServerRequest = HttpRequest.newBuilder().uri(new URI(serverURL)).build()
                OtherUtil.HTTP_CLIENT.sendAsync(httpServerRequest, HttpResponse.BodyHandlers.ofFileDownload(gamePath, StandardOpenOption.CREATE))
            }
            if(!Files.exists(clientMappingPath)){
                String clientMappingURL = versionJson.getJSONObject("downloads").getJSONObject("client_mappings").getString("url")
                def httpClientMappingRequest = HttpRequest.newBuilder().uri(new URI(clientMappingURL)).build()
                OtherUtil.HTTP_CLIENT.sendAsync(httpClientMappingRequest, HttpResponse.BodyHandlers.ofFileDownload(gamePath,
                        StandardOpenOption.CREATE))
            }
            if(!Files.exists(serverMappingPath)){
                String serverMappingURL = versionJson.getJSONObject("downloads").getJSONObject("server_mappings").getString("url")
                def httpServerMappingRequest = HttpRequest.newBuilder().uri(new URI(serverMappingURL)).build()
                OtherUtil.HTTP_CLIENT.sendAsync(httpServerMappingRequest, HttpResponse.BodyHandlers.ofFileDownload(gamePath,
                        StandardOpenOption.CREATE))
            }
        }
    }

    @Override
    void execute() {
        parseVersionJson(extension.minecraftVersion)
        parseGameJson()
    }
}
