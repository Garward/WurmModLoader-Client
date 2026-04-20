package com.garward.wurmmodloader.client.core.bytecode.patches.serverpacks;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;

/**
 * Patches PackResourceUrl.derive() to support cross-pack derivation.
 *
 * <p>This patch allows derived resources (e.g., normal maps) to reference resources
 * from other packs using the "~packname/resource" syntax.
 *
 * @since 0.2.0
 */
public class PackResourceUrlDeriveCrossPackPatch implements BytecodePatch {

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.resources.PackResourceUrl";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        CtMethod deriveMethod = ctClass.getMethod("derive", "(Ljava/lang/String;)Lcom/wurmonline/client/resources/PackResourceUrl;");

        deriveMethod.insertBefore(
            "if ($1.startsWith(\"~\")) {" +
            "    int sep = $1.indexOf('/');" +
            "    com.wurmonline.client.resources.Pack pack = com.wurmonline.client.WurmClientBase.getResourceManager()" +
            "        .findPack(newFilename.substring(1,sep));" +
            "    if (pack!=null) {" +
            "        com.wurmonline.client.resources.PackResourceUrl nurl = new com.wurmonline.client.resources.PackResourceUrl(pack, $1.substring(sep+1));" +
            "        if (!nurl.exists()) throw com.wurmonline.client.GameCrashedException.forFailure(\"Derived cross-pack resource \" + nurl + \" does not exist (source \" + this + \")\");" +
            "        return nurl;" +
            "    }" +
            "}"
        );
    }

    @Override
    public int getPriority() {
        return 90;  // High priority - must run before resources are accessed
    }

    @Override
    public String getDescription() {
        return "PackResourceUrl.derive() cross-pack resolution";
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.resources.packresourceurl.derive");
    }
}
