package vanillagradle.remapper

import groovy.json.internal.Charsets
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import vanillagradle.VanillaGradleExtension
import vanillagradle.util.OtherUtil

import javax.inject.Inject
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

class JarMerger implements AutoCloseable {
    VanillaGradleExtension extension
    @Inject
    JarMerger(VanillaGradleExtension extension){
        this.extension=extension
    }
    class Entry {
        final Path path
        final BasicFileAttributes metadata
        final byte[] data

        Entry(Path path, BasicFileAttributes metadata, byte[] data) {
            this.path = path
            this.metadata = metadata
            this.data = data
        }
    }

    private static final ClassMerger CLASS_MERGER = new ClassMerger()

    Path getInputClient() {
        return inputClient
    }

    Path getInputServer() {
        return inputServer
    }
    private final String version=extension.minecraftVersion
    private final Path inputClient = Path.of(OtherUtil.FINAL_GRADLE_CACHE.toString(),  version,"client.jar")
    private final Path inputServer = Path.of(OtherUtil.FINAL_GRADLE_CACHE.toString(),  version,"server.jar")
    //private final FileSystem output = FileSystems.default
    private final Map<String, Entry> entriesClient = new HashMap<>()
    private final Map<String, Entry> entriesServer = new HashMap<>()
    private final Set<String> entriesAll = new TreeSet<>()
    //private boolean removeSnowmen = false
    //private boolean offsetSyntheticsParams = false

    /* void enableSnowmanRemoval() {
         removeSnowmen = true
     }

     void enableSyntheticParamsOffset() {
         offsetSyntheticsParams = true
     }*/

    @Override
    void close() throws IOException {

    }

    private void readToMap(Map<String, Entry> map, Path input, boolean isServer) {
        try {
            Files.walkFileTree(input, new SimpleFileVisitor<Path>() {
                @Override
                FileVisitResult visitFile(Path path, BasicFileAttributes attr) throws IOException {
                    if (attr.isDirectory()) {
                        return FileVisitResult.CONTINUE
                    }

                    if (!path.getFileName().toString().endsWith(".class")) {
                        if (path.toString() == ("/META-INF/MANIFEST.MF")) {
                            map.put("META-INF/MANIFEST.MF", new Entry(path, attr,
                                    "Manifest-Version: 1.0\nMain-Class: net.minecraft.client.Main\n".getBytes(Charsets.UTF_8)))
                        } else {
                            if (path.toString().startsWith("/META-INF/")) {
                                if (path.toString().endsWith(".SF") || path.toString().endsWith(".RSA")) {
                                    return FileVisitResult.CONTINUE
                                }
                            }

                            map.put(path.toString().substring(1), new Entry(path, attr, null))
                        }

                        return FileVisitResult.CONTINUE
                    }

                    byte[] output = Files.readAllBytes(path)
                    map.put(path.toString().substring(1), new Entry(path, attr, output))
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (IOException e) {
            e.printStackTrace()
        }
    }

    private void add(Entry entry) throws IOException {
        Path outPath = outputFs.get().getPath(entry.path.toString())
        if (outPath.getParent() != null) {
            Files.createDirectories(outPath.getParent())
        }

        if (entry.data != null) {
            Files.write(outPath, entry.data, StandardOpenOption.CREATE_NEW)
        } else {
            Files.copy(entry.path, outPath)
        }

        Files.getFileAttributeView(entry.path, BasicFileAttributeView)
                .setTimes(
                        entry.metadata.creationTime(),
                        entry.metadata.lastAccessTime(),
                        entry.metadata.lastModifiedTime()
                )
    }

    void merge() throws IOException {
        ExecutorService service = Executors.newFixedThreadPool(4)
        service.submit(() -> readToMap(entriesClient, inputClient, false))
        service.submit(() -> readToMap(entriesServer, inputServer, true))
        service.shutdown()
        try {
            service.awaitTermination(1, TimeUnit.HOURS)
        } catch (InterruptedException e) {
            e.printStackTrace()
        }

        entriesAll.addAll(entriesClient.keySet())
        entriesAll.addAll(entriesServer.keySet())

        List<Entry> entries = entriesAll.parallelStream().map((entry) -> {
            boolean isClass = entry.endsWith(".class")
            boolean isMinecraft = entriesClient.containsKey(entry) || entry.startsWith("net/minecraft") || !entry.contains("/")
            Entry result
            String side = null

            Entry entry1 = entriesClient.get(entry)
            Entry entry2 = entriesServer.get(entry)

            if (entry1 != null && entry2 != null) {
                if (Arrays.equals(entry1.data, entry2.data)) {
                    result = entry1
                } else {
                    if (isClass) {
                        result = new Entry(entry1.path, entry1.metadata, CLASS_MERGER.merge(entry1.data, entry2.data))
                    } else {
                        // FIXME: More heuristics?
                        result = entry1
                    }
                }
            } else if ((result = entry1) != null) {
                side = "CLIENT"
            } else if ((result = entry2) != null) {
                side = "SERVER"
            }

            if (isClass && !isMinecraft && "SERVER" == (side)) {
                // Server bundles libraries, client doesn't - skip them
                return null
            }

            if (result != null) {
                if (isMinecraft && isClass) {
                    byte[] data = result.data
                    ClassReader reader = new ClassReader(data)
                    ClassWriter writer = new ClassWriter(0)
                    ClassVisitor visitor = writer

                    if (side != null) {
                        visitor = new ClassMerger.SidedClassVisitor(Opcodes.ASM7, visitor, side)
                    }

                    /*if (removeSnowmen) {
                        visitor = new SnowmanClassVisitor(Opcodes.ASM7, visitor)
                    }

                    if (offsetSyntheticsParams) {
                        visitor = new SyntheticParameterClassVisitor(Opcodes.ASM7, visitor)
                    }*/

                    if (visitor != writer) {
                        reader.accept(visitor, 0)
                        data = writer.toByteArray()
                        result = new Entry(result.path, result.metadata, data)
                    }
                }

                return result
            } else {
                return null
            }
        }).filter(Objects::nonNull).collect(Collectors.toList())

        for (Entry e : entries) {
            add(e)
        }
    }
}
