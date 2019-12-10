/*
 * #%L
 * Alfresco Transform Core
 * %%
 * Copyright (C) 2005 - 2019 Alfresco Software Limited
 * %%
 * This file is part of the Alfresco software.
 * -
 * If the software was purchased under a paid Alfresco license, the terms of
 * the paid license agreement will prevail.  Otherwise, the software is
 * provided under the following open source license terms:
 * -
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * -
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * -
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.alfresco.transformer;

import org.alfresco.transform.client.registry.TransformServiceRegistry;
import org.alfresco.transformer.executors.TikaJavaExecutor;
import org.alfresco.transformer.probes.ProbeTestTransform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Map;

import static java.lang.Boolean.parseBoolean;
import static org.alfresco.transformer.executors.Tika.*;
import static org.alfresco.transformer.util.MimetypeMap.MIMETYPE_TEXT_PLAIN;

@Component
public class TikaTransformHandler implements TransformHandler {
    private static final Logger logger = LoggerFactory.getLogger(TikaTransformHandler.class);

    @Autowired
    private TransformServiceRegistry transformRegistry;

    @Autowired
    private TikaJavaExecutor javaExecutor;

    @Override
    public String version() {
        return "Tika available";
    }

    @Override
    public void processTransform(final File sourceFile, final File targetFile,
                                 final String sourceMimetype, final String targetMimetype,
                                 final Map<String, String> transformOptions, final Long timeout) {
        logger.debug("Processing request with: sourceFile '{}', targetFile '{}', transformOptions" +
                " '{}', timeout {} ms", sourceFile, targetFile, transformOptions, timeout);

        final boolean includeContents = parseBoolean(
                transformOptions.getOrDefault("includeContents", "false"));
        final boolean notExtractBookmarksText = parseBoolean(
                transformOptions.getOrDefault("notExtractBookmarksText", "false"));
        final String targetEncoding = transformOptions.getOrDefault("targetEncoding", "UTF-8");

        final String transform = transformRegistry.getTransformerName(sourceFile, sourceMimetype, targetMimetype,
                transformOptions);

        javaExecutor.call(sourceFile, targetFile, transform,
                includeContents ? INCLUDE_CONTENTS : null,
                notExtractBookmarksText ? NOT_EXTRACT_BOOKMARKS_TEXT : null,
                TARGET_MIMETYPE + targetMimetype, TARGET_ENCODING + targetEncoding);
    }

    @Override
    public ProbeTestTransform getProbeTestTransform() {
        // See the Javadoc on this method and Probes.md for the choice of these values.
        // the livenessPercentage is a little large as Tika does tend to suffer from slow transforms that class with a gc.
        return new ProbeTestTransform("quick.pdf", "quick.txt",
                60, 16, 400, 10240, 60 * 30 + 1, 60 * 15 + 20) {
            @Override
            protected void executeTransformCommand(File sourceFile, File targetFile) {
                javaExecutor.call(sourceFile, targetFile, PDF_BOX,
                        TARGET_MIMETYPE + MIMETYPE_TEXT_PLAIN, TARGET_ENCODING + "UTF-8");
            }
        };
    }
}
