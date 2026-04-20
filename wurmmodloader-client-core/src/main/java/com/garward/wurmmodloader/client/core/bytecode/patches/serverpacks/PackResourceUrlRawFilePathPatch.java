package com.garward.wurmmodloader.client.core.bytecode.patches.serverpacks;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtField;
import javassist.Modifier;

import java.util.Collection;
import java.util.Collections;

/**
 * Makes rawFilePath field public in PackResourceUrl for server pack support.
 *
 * <p>This patch allows server packs to access the raw file path when resolving
 * cross-pack resource references.
 *
 * @since 0.2.0
 */
public class PackResourceUrlRawFilePathPatch implements BytecodePatch {

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.resources.PackResourceUrl";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        CtField field = ctClass.getDeclaredField("rawFilePath");
        field.setModifiers(Modifier.PUBLIC);
    }

    @Override
    public int getPriority() {
        return 100;  // High priority - must run before packs are accessed
    }

    @Override
    public String getDescription() {
        return "PackResourceUrl.rawFilePath visibility for server packs";
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.resources.packresourceurl");
    }
}
