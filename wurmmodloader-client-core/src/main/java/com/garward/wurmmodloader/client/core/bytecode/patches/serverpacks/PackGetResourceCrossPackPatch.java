package com.garward.wurmmodloader.client.core.bytecode.patches.serverpacks;

import com.garward.wurmmodloader.client.api.bytecode.BytecodePatch;
import javassist.CtClass;
import javassist.CtMethod;

import java.util.Collection;
import java.util.Collections;

/**
 * Patches Pack.getResource() to support cross-pack references.
 *
 * <p>This patch allows resources to reference other packs using the "~packname/resource" syntax.
 * When a resource starts with "~", it's resolved from the named pack instead of the current pack.
 *
 * @since 0.2.0
 */
public class PackGetResourceCrossPackPatch implements BytecodePatch {

    @Override
    public String getTargetClassName() {
        return "com.wurmonline.client.resources.Pack";
    }

    @Override
    public void apply(CtClass ctClass) throws Exception {
        CtMethod getResourceMethod = ctClass.getMethod("getResource", "(Ljava/lang/String;)Lcom/wurmonline/client/resources/ResourceUrl;");

        getResourceMethod.insertAfter(
            "if ($_ != null && ($_ instanceof com.wurmonline.client.resources.PackResourceUrl) && $_.getFilePath().startsWith(\"~\")) {" +
            "    int sep = $_.getFilePath().indexOf('/');" +
            "    com.wurmonline.client.resources.Pack pack = com.wurmonline.client.WurmClientBase.getResourceManager()" +
            "        .findPack($_.getFilePath().substring(1,sep));" +
            "    if (pack!=null)" +
            "        $_ = new com.wurmonline.client.resources.PackResourceUrl(pack, ((com.wurmonline.client.resources.PackResourceUrl)$_).rawFilePath.substring(sep+1).replace(\"~[local]/\",\"~\"+this.name+\"/\"));" +
            "};"
        );
    }

    @Override
    public int getPriority() {
        return 90;  // High priority - must run before resources are accessed
    }

    @Override
    public String getDescription() {
        return "Pack.getResource() cross-pack resolution";
    }

    @Override
    public Collection<String> getConflictKeys() {
        return Collections.singleton("client.resources.pack.getresource");
    }
}
