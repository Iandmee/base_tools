/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.tasks.fd;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_PACKAGE;
import static com.android.SdkConstants.TAG_APPLICATION;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.LCONST_0;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_6;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.tasks.BaseTask;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.utils.XmlUtils;

import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.tooling.BuildException;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Reads the merged manifest file and creates an AppInfo class listing the applicationId and
 * application classes (if any).
 */
public class GenerateInstantRunAppInfoTask extends BaseTask {

    private File outputFile;
    private File mergedManifest;

    @OutputFile
    public File getOutputFile() {
        return outputFile;
    }

    @InputFile
    public File getMergedManifest() {
        return mergedManifest;
    }

    @TaskAction
    public void generateInfoTask() throws IOException {
        // Grab the application id and application class stashes away in the Android
        // manifest (not in the Android namespace) and generate an AppInfo class.
        // (Earlier, I did all the processing here - read the manifest, rewrite it by
        // generating the AppInfo class and rewriting the manifest on the fly to have
        // the new bootstrapping application, but we
        //  (1) need for this task to be done after manifest merging, and
        //  (2) need for it to be done before packaging
        // but when combined with the current task dependencies (e.g. compilation
        // depending on resource merging, such that R classes exist) this led to
        // circular task dependencies. So for now, this is split into two parts:
        // In manifest merging we stash away and replace the application id/class, and
        // here in a packaging task we inject runtime libraries.
        if (getMergedManifest().exists()) {
            try {
                Document document = XmlUtils.parseUtfXmlFile(getMergedManifest(), true);
                Element root = document.getDocumentElement();
                if (root != null) {
                    String applicationId = root.getAttribute(ATTR_PACKAGE);
                    String applicationClass = null;
                    NodeList children = root.getChildNodes();
                    for (int i = 0; i < children.getLength(); i++) {
                        Node node = children.item(i);
                        if (node.getNodeType() == Node.ELEMENT_NODE &&
                                node.getNodeName().equals(TAG_APPLICATION)) {
                            String applicationClass1 = null;

                            Element element = (Element) node;
                            if (element.hasAttribute(ATTR_NAME)) {
                                String name = element.getAttribute(ATTR_NAME);
                                assert !name.startsWith(".") : name;
                                if (!name.isEmpty()) {
                                    applicationClass1 = name;
                                }
                            }

                            applicationClass = applicationClass1;
                            break;
                        }
                    }

                    if (!applicationId.isEmpty()) {
                        // Must be *after* extractLibrary() to replace dummy version
                        writeAppInfoClass(applicationId, applicationClass, 0L);
                    }
                }
            } catch (ParserConfigurationException e) {
                throw new BuildException("Failed to inject bootstrapping application", e);
            } catch (IOException e) {
                throw new BuildException("Failed to inject bootstrapping application", e);
            } catch (SAXException e) {
                throw new BuildException("Failed to inject bootstrapping application", e);
            }
        }
    }

    void writeAppInfoClass(
            @NonNull String applicationId,
            @Nullable String applicationClass,
            long token)
            throws IOException {
        ClassWriter cw = new ClassWriter(0);
        FieldVisitor fv;
        MethodVisitor mv;

        String appInfoOwner = "com/android/tools/fd/runtime/AppInfo";
        cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, appInfoOwner, null, "java/lang/Object", null);

        fv = cw.visitField(ACC_PUBLIC + ACC_STATIC, "applicationId", "Ljava/lang/String;", null, null);
        fv.visitEnd();
        fv = cw.visitField(ACC_PUBLIC + ACC_STATIC, "applicationClass", "Ljava/lang/String;", null, null);
        fv.visitEnd();
        fv = cw.visitField(ACC_PUBLIC + ACC_STATIC, "token", "J", null, null);
        fv.visitEnd();
        mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        Label l0 = new Label();
        mv.visitLabel(l0);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(RETURN);
        Label l1 = new Label();
        mv.visitLabel(l1);
        mv.visitLocalVariable("this", "L" + appInfoOwner + ";", null, l0, l1, 0);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();
        mv.visitLdcInsn(applicationId);
        mv.visitFieldInsn(PUTSTATIC, appInfoOwner, "applicationId", "Ljava/lang/String;");
        if (applicationClass != null) {
            mv.visitLdcInsn(applicationClass);
        } else {
            mv.visitInsn(ACONST_NULL);
        }
        mv.visitFieldInsn(PUTSTATIC, appInfoOwner, "applicationClass", "Ljava/lang/String;");
        if (token != 0L) {
            mv.visitLdcInsn(token);
        } else {
            mv.visitInsn(LCONST_0);
        }
        mv.visitFieldInsn(PUTSTATIC, appInfoOwner, "token", "J");
        mv.visitInsn(RETURN);
        mv.visitMaxs(2, 0);
        mv.visitEnd();
        cw.visitEnd();

        byte[] bytes = cw.toByteArray();

        JarOutputStream outputStream = new JarOutputStream(new BufferedOutputStream(
                new FileOutputStream(getOutputFile())));
        try {
            outputStream.putNextEntry(new ZipEntry("com/android/tools/fd/runtime/AppInfo.class"));
            outputStream.write(bytes);
            outputStream.closeEntry();
        } finally {
            outputStream.close();
        }
    }

    public static class ConfigAction implements TaskConfigAction<GenerateInstantRunAppInfoTask> {
        private final VariantScope variantScope;

        public ConfigAction(VariantScope variantScope) {
            this.variantScope = variantScope;
        }

        @NonNull
        @Override
        public String getName() {
            return variantScope.getTaskName("generate", "InstantRunAppInfo");
        }

        @NonNull
        @Override
        public Class<GenerateInstantRunAppInfoTask> getType() {
            return GenerateInstantRunAppInfoTask.class;
        }

        @Override
        public void execute(@NonNull GenerateInstantRunAppInfoTask task) {
            task.setVariantName(variantScope.getVariantConfiguration().getFullName());
            task.outputFile =
                    new File(variantScope.getIncrementalApplicationSupportDir(), "classes.jar");

            BaseVariantOutputData variantOutput = variantScope.getVariantData()
                    .getOutputs().get(0);

            BaseVariantOutputData variantOutputData = variantOutput.getScope()
                    .getVariantOutputData();
            task.mergedManifest = variantOutputData.manifestProcessorTask.getManifestOutputFile();
        }
    }
}
