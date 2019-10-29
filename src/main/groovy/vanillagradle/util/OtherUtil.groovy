package vanillagradle.util

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.publish.maven.internal.artifact.ArchiveTaskBasedMavenArtifact
import org.gradle.api.tasks.SourceSet
import org.gradle.internal.impldep.org.apache.commons.compress.utils.IOUtils
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils
import org.gradle.internal.impldep.org.apache.commons.io.filefilter.AbstractFileFilter
import org.gradle.internal.impldep.org.apache.commons.io.filefilter.FalseFileFilter
import org.gradle.internal.impldep.org.apache.commons.io.filefilter.TrueFileFilter
import org.gradle.internal.impldep.org.jetbrains.annotations.NotNull
import org.gradle.internal.impldep.org.jetbrains.annotations.Nullable
import org.gradle.process.JavaForkOptions
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException

import javax.net.ssl.SSLContext
import java.net.http.HttpClient
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.time.Duration
import java.util.concurrent.Executors
import java.util.function.BiConsumer
import java.util.function.Predicate
import java.util.regex.Pattern
import java.util.zip.ZipException
import java.util.zip.ZipFile

class OtherUtil {
    private static final def changeCache = new HashMap<>()
    private static final def pathsObserved = new HashMap<>()
    static final def HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(60)).
            executor(Executors.newFixedThreadPool(4)).sslContext(SSLContext.getDefault()).build()
    private static Path GRADLE_CACHE = Path.of(System.getenv("GRADLE_USER_HOME"),"caches","vanilla")
    static final Path FINAL_GRADLE_CACHE=Files.exists(GRADLE_CACHE)?Files.createDirectory(GRADLE_CACHE):GRADLE_CACHE
    static final URI ALL_MANIFEST = new URI("https://launchermeta.mojang.com/mc/game/version_manifest.json")
    //static final Pattern VERSION_PATTERN = Pattern.compile('^([A-Z]{2})-([0-9.A-z]+)\\s*$')

    static String loadETag(Path to, Logger logger) {
        Path etagPath=Path.of(to.toString(),".etag")
        if (!Files.exists(etagPath))
            return null
        try {
            return Files.asCharSource(etagPath.toFile(), StandardCharsets.UTF_8).read()
        } catch (IOException ignored) {
            logger.warn("Error reading ETag file '{}'.", etagPath.toFile())
            return null
        }
    }
    static String toNiceSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B"
        } else if (bytes < 1024 * 1024) {
            return bytes / 1024 + " KB"
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
    static boolean hasFileChanged(Path filePath) {
        def service= FileSystems.getDefault().newWatchService()
        if(!Files.exists(filePath)){
            return true
        }
        WatchKey key
        while ((key = service.poll()) != null) {
            for (WatchEvent<?> event : key.pollEvents()) {
                Object ctx = event.context()
                if (ctx instanceof Path) {
                    changeCache.put(((Path) ctx).toAbsolutePath(), true)
                }
            }
        }

        filePath = filePath.toAbsolutePath()
        Path parentPath = filePath.getParent()
        if (changeCache.containsKey(filePath)) {
            return true
        } else {
            if (!pathsObserved.containsKey(parentPath)) {
                try {
                    pathsObserved.put(parentPath, parentPath.register(
                            service, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE
                    ))
                    return true
                } catch (IOException e) {
                    throw new RuntimeException(e)
                }
            } else {
                return false
            }
        }
    }
    static boolean containsEntry(Path zip, String name) {
        ZipFile zf = null
        try {
            zf = new ZipFile(zip.toFile())
            return zf.getEntry(name) != null
        }
        catch (IOException ignored) {
            throw new ZipException()
        }
        finally {
            zf.close()
        }
    }

    static byte[] unpackEntry(ZipFile zf, String name) throws IOException {
        def zipEntry = zf.getEntry(name)
        if (zipEntry == null) {
            return null
        }
        def inputStream = zf.getInputStream(zipEntry)
        try {
            return IOUtils.toByteArray(inputStream)
        }
        finally {
            IOUtils.closeQuietly(inputStream)
        }
    }
    @NotNull
    static SourceSet mainSourceSet(@NotNull Project project) {
        def javaConvention = project.convention.getPlugin(JavaPluginConvention)
        javaConvention.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
    }

    @NotNull
    static MavenArtifact createJarDependency(File file, String configuration, File baseDir, String classifier = null) {
        return createDependency(baseDir, file, configuration, "jar", classifier)
    }

    @NotNull
    static MavenArtifact createDirectoryDependency(File file, String configuration, File baseDir, String classifier = null) {
        return createDependency(baseDir, file, configuration, "",  classifier)
    }

    private static MavenArtifact createDependency(File baseDir, File file, String configuration, String extension,String classifier) {
        def relativePath = baseDir.toURI().relativize(file.toURI()).getPath()
        //def name = extension ? relativePath - ".$extension" : relativePath
        def artifact = new ArchiveTaskBasedMavenArtifact()
        artifact.conf = configuration
        artifact.classifier=classifier
        artifact.extension=extension
        return artifact
    }


    @NotNull
    static List<String> getJvmArgs(@NotNull JavaForkOptions options,
                                   @NotNull List<String> originalArguments,
                                   @NotNull File ideaDirectory) {
        if (options.maxHeapSize == null) options.maxHeapSize = "512m"
        if (options.minHeapSize == null) options.minHeapSize = "256m"
        List<String> result = new ArrayList<String>(originalArguments)
        def bootJar = new File(ideaDirectory, "lib/boot.jar")
        if (bootJar.exists()) result += "-Xbootclasspath/a:$bootJar.absolutePath"
        return result as List<String>
    }


    @NotNull
    static Node parseXml(Path file) {
        def parser = new XmlParser(false, true, true)
        parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        parser.setErrorHandler(new ErrorHandler() {
            @Override
            void warning(SAXParseException e) throws SAXParseException {

            }

            @Override
            void error(SAXParseException e) throws SAXParseException {
                throw e
            }

            @Override
            void fatalError(SAXParseException e) throws SAXParseException {
                throw e
            }
        })
        InputStream inputStream = file.newInputStream()
        InputSource input = new InputSource(inputStream.newReader("UTF-8"))
        input.setEncoding("UTF-8")
        try {
            return parser.parse(input)
        }
        finally {
            inputStream.close()
        }
    }

    @NotNull
    static Collection<File> collectJars(@NotNull Path directory, @NotNull final Predicate<File> filter,
                                        boolean recursively) {
        return FileUtils.listFiles(directory.toFile(), new AbstractFileFilter() {
            @Override
            boolean accept(File file) {
                return file.getName().endsWith(".jar")&& filter.test(file)
            }
        }, recursively ? TrueFileFilter.INSTANCE : FalseFileFilter.FALSE)
    }

    static def unzip(@NotNull File zipFile,
                     @NotNull File cacheDirectory,
                     @NotNull Project project,
                     @Nullable Predicate<File> isUpToDate,
                     @Nullable BiConsumer<File, File> markUpToDate) {
        def targetDirectory = new File(cacheDirectory, zipFile.name - ".zip")
        def markerFile = new File(targetDirectory, "markerFile")
        if (markerFile.exists() && (isUpToDate == null || isUpToDate.test(markerFile))) {
            return targetDirectory
        }

        if (targetDirectory.exists()) {
            targetDirectory.deleteDir()
        }
        targetDirectory.mkdir()

        project.getLogger().debug("Unzipping ${zipFile.name}")
        project.copy {
            it.from(project.zipTree(zipFile))
            it.into(targetDirectory)
        }
        project.getLogger().debug("Unzipped ${zipFile.name}")

        markerFile.createNewFile()
        if (markUpToDate != null) {
            markUpToDate.accept(targetDirectory, markerFile)
        }
        return targetDirectory
    }
}

