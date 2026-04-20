package com.garward.wurmmodloader.client.core.bytecode.patches.serverpacks;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;

/**
 * Adds findPack(String) method to Resources class for server pack support.
 *
 * <p>This patch adds a utility method that allows finding a pack by name,
 * which is needed for cross-pack resource references (e.g., "~packname/resource.dds").
 *
 * @since 0.2.0
 */
public class ResourcesFindPackPatch implements BytecodePatch {

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.resources.Resources";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        CtClass ctPack = ctClass.getClassPool().get("com.wurmonline.client.resources.Pack");

        CtMethod findPackMethod = new CtMethod(
            ctPack,
            "findPack",
            new CtClass[]{ctClass.getClassPool().get("java.lang.String")},
            ctClass
        );

        findPackMethod.setBody(
            "{" +
            "    for (java.util.Iterator iterator = this.packs.iterator(); iterator.hasNext(); ) {" +
            "        com.wurmonline.client.resources.Pack pack = (com.wurmonline.client.resources.Pack) iterator.next();" +
            "        if (pack.getName().equals($1)) return pack;" +
            "    }" +
            "    return null;" +
            "}"
        );

        ctClass.addMethod(findPackMethod);
    }

    @Override
    public int getPriority() {
        return 100;  // High priority - must run before packs are loaded
    }

    @Override
    public String getDescription() {
        return "Resources.findPack() method for server packs";
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.resources.findpack");
    }
}
