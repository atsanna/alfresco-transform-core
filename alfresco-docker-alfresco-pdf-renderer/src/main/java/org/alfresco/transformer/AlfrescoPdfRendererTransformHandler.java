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

import org.alfresco.transformer.executors.PdfRendererCommandExecutor;
import org.alfresco.transformer.probes.ProbeTestTransform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Map;

@Component
public class AlfrescoPdfRendererTransformHandler implements TransformHandler {
    private static final Logger logger = LoggerFactory.getLogger(AlfrescoPdfRendererTransformHandler.class);

    @Autowired
    private PdfRendererCommandExecutor commandExecutor;

    @Override
    public String version() {
        return commandExecutor.version();
    }

    @Override
    public void processTransform(final File sourceFile, final File targetFile,
                                 final String sourceMimetype, final String targetMimetype,
                                 final Map<String, String> transformOptions, final Long timeout) {
        logger.debug("Processing request with: sourceFile '{}', targetFile '{}', transformOptions" +
                " '{}', timeout {} ms", sourceFile, targetFile, transformOptions, timeout);

        final String options = OptionsBuilder
                .builder()
                .withPage(transformOptions.get("page"))
                .withWidth(transformOptions.get("width"))
                .withHeight(transformOptions.get("height"))
                .withAllowPdfEnlargement(transformOptions.get("allowPdfEnlargement"))
                .withMaintainPdfAspectRatio(transformOptions.get("maintainPdfAspectRatio"))
                .build();

        commandExecutor.run(options, sourceFile, targetFile, timeout);
    }

    @Override
    public ProbeTestTransform getProbeTestTransform()
    {
        // See the Javadoc on this method and Probes.md for the choice of these values.
        return new ProbeTestTransform("quick.pdf", "quick.png",
                7455, 1024, 150, 10240, 60 * 20 + 1, 60 * 15 - 15)
        {
            @Override
            protected void executeTransformCommand(File sourceFile, File targetFile)
            {
                commandExecutor.run("", sourceFile, targetFile, null);
            }
        };
    }
}
