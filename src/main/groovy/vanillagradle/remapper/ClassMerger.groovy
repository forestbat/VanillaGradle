package vanillagradle.remapper

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.InnerClassNode
import org.objectweb.asm.tree.MethodNode

class ClassMerger {
    private static final String SIDE_DESCRIPTOR = "Lnet/fabricmc/api/EnvType"
    private static final String ITF_DESCRIPTOR = "Lnet/fabricmc/api/EnvironmentInterface"
    private static final String ITF_LIST_DESCRIPTOR = "Lnet/fabricmc/api/EnvironmentInterfaces"
    private static final String SIDED_DESCRIPTOR = "Lnet/fabricmc/api/Environment"

    private abstract class Merger<T> {
        private final Map<String, T> entriesClient, entriesServer
        private final Set<String> entryNames

        Merger(Set<T> entriesClient, Set<T> entriesServer) {
            this.entriesClient = new LinkedHashMap<>()
            this.entriesServer = new LinkedHashMap<>()
            Set<String> listClient = toMap(entriesClient, this.entriesClient)
            Set<String> listServer = toMap(entriesServer, this.entriesServer)
            entryNames.addAll(listServer)
            entryNames.addAll(listClient)
        }

        abstract String getName(T entry)

        abstract void applySide(T entry, String side)

        private final Set<String> toMap(Set<T> entries, Map<String, T> map) {
            Set<String> list = new HashSet<>(entries.size())
            for (T entry : entries) {
                String name = getName(entry)
                map.put(name, entry)
                list.add(name)
            }
            return list
        }

        void merge(Set<T> list) {
            for (String s : entryNames) {
                T entryClient = entriesClient.get(s)
                T entryServer = entriesServer.get(s)

                if (entryClient != null && entryServer != null) {
                    list.add(entryClient)
                } else if (entryClient != null) {
                    applySide(entryClient, "CLIENT")
                    list.add(entryClient)
                } else {
                    applySide(entryServer, "SERVER")
                    list.add(entryServer)
                }
            }
        }
    }

    private static void visitSideAnnotation(AnnotationVisitor av, String side) {
        av.visitEnum("value", SIDE_DESCRIPTOR, side.toUpperCase(Locale.ROOT))
        av.visitEnd()
    }

    private static void visitItfAnnotation(AnnotationVisitor av, String side, Set<String> itfDescriptors) {
        for (String itf : itfDescriptors) {
            AnnotationVisitor avItf = av.visitAnnotation(null, ITF_DESCRIPTOR)
            avItf.visitEnum("value", SIDE_DESCRIPTOR, side.toUpperCase(Locale.ROOT))
            avItf.visit("itf", Type.getType("L" + itf + ""))
            avItf.visitEnd()
        }
    }

    static class SidedClassVisitor extends ClassVisitor {
        private final String side

        SidedClassVisitor(int api, ClassVisitor cv, String side) {
            super(api, cv)
            this.side = side
        }

        @Override
        void visitEnd() {
            AnnotationVisitor av = cv.visitAnnotation(SIDED_DESCRIPTOR, true)
            visitSideAnnotation(av, side)
            super.visitEnd()
        }
    }

    byte[] merge(byte[] classClient, byte[] classServer) {
        def readerC = new ClassReader(classClient)
        def readerS = new ClassReader(classServer)
        def writer = new ClassWriter(0)

        def nodeC = new ClassNode(Opcodes.ASM7)
        readerC.accept(nodeC, 0)

        def nodeS = new ClassNode(Opcodes.ASM7)
        readerS.accept(nodeS, 0)

        def nodeOut = new ClassNode(Opcodes.ASM7)
        nodeOut.version = nodeC.version
        nodeOut.access = nodeC.access
        nodeOut.name = nodeC.name
        nodeOut.signature = nodeC.signature
        nodeOut.superName = nodeC.superName
        nodeOut.sourceFile = nodeC.sourceFile
        nodeOut.sourceDebug = nodeC.sourceDebug
        nodeOut.outerClass = nodeC.outerClass
        nodeOut.outerMethod = nodeC.outerMethod
        nodeOut.outerMethodDesc = nodeC.outerMethodDesc
        nodeOut.module = nodeC.module
        nodeOut.nestHostClass = nodeC.nestHostClass
        nodeOut.nestMembers = nodeC.nestMembers
        nodeOut.attrs = nodeC.attrs

        if (nodeC.invisibleAnnotations != null) {
            nodeOut.invisibleAnnotations = new ArrayList<>()
            nodeOut.invisibleAnnotations.addAll(nodeC.invisibleAnnotations)
        }
        if (nodeC.invisibleTypeAnnotations != null) {
            nodeOut.invisibleTypeAnnotations = new ArrayList<>()
            nodeOut.invisibleTypeAnnotations.addAll(nodeC.invisibleTypeAnnotations)
        }
        if (nodeC.visibleAnnotations != null) {
            nodeOut.visibleAnnotations = new ArrayList<>()
            nodeOut.visibleAnnotations.addAll(nodeC.visibleAnnotations)
        }
        if (nodeC.visibleTypeAnnotations != null) {
            nodeOut.visibleTypeAnnotations = new ArrayList<>()
            nodeOut.visibleTypeAnnotations.addAll(nodeC.visibleTypeAnnotations)
        }

        nodeC.interfaces.addAll(nodeS.interfaces)
        //Set<String> mergeItfs=nodeC.interfaces
        nodeOut.interfaces = new ArrayList<>()

        Set<String> clientItfs = new HashSet<>()
        Set<String> serverItfs = new HashSet<>()

        for (String s : itfs) {
            boolean nc = nodeC.interfaces.contains(s)
            boolean ns = nodeS.interfaces.contains(s)
            nodeOut.interfaces.add(s)
            if (nc && !ns) {
                clientItfs.add(s)
            } else if (ns && !nc) {
                serverItfs.add(s)
            }
        }

        if (!clientItfs.isEmpty() || !serverItfs.isEmpty()) {
            def envInterfaces = nodeOut.visitAnnotation(ITF_LIST_DESCRIPTOR, false)
            def eiArray = envInterfaces.visitArray("value")

            if (!clientItfs.isEmpty()) {
                visitItfAnnotation(eiArray, "CLIENT", clientItfs)
            }
            if (!serverItfs.isEmpty()) {
                visitItfAnnotation(eiArray, "SERVER", serverItfs)
            }
            eiArray.visitEnd()
            envInterfaces.visitEnd()
        }

        new Merger<InnerClassNode>(nodeC.innerClasses.toSet(), nodeS.innerClasses.toSet()) {
            @Override
            String getName(InnerClassNode entry) {
                return entry.name
            }

            @Override
            void applySide(InnerClassNode entry, String side) {
            }
        }.merge(nodeOut.innerClasses.toSet())

        new Merger<FieldNode>(nodeC.fields.toSet(), nodeS.fields.toSet()) {
            @Override
            String getName(FieldNode entry) {
                return entry.name + "" + entry.desc
            }

            @Override
            void applySide(FieldNode entry, String side) {
                def av = entry.visitAnnotation(SIDED_DESCRIPTOR, false)
                visitSideAnnotation(av, side)
            }
        }.merge(nodeOut.fields.toSet())

        new Merger<MethodNode>(nodeC.methods.toSet(), nodeS.methods.toSet()) {
            @Override
            String getName(MethodNode entry) {
                return entry.name + entry.desc
            }

            @Override
            void applySide(MethodNode entry, String side) {
                def av = entry.visitAnnotation(SIDED_DESCRIPTOR, false)
                visitSideAnnotation(av, side)
            }
        }.merge(nodeOut.methods.toSet())

        nodeOut.accept(writer)
        return writer.toByteArray()
    }
}
