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

import org.alfresco.transformer.logging.LogEntry;
import org.alfresco.transformer.probes.ProbeTestTransform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.alfresco.transform.client.model.Mimetype.MIMETYPE_HTML;
import static org.alfresco.transform.client.model.Mimetype.MIMETYPE_TEXT_PLAIN;
import static org.alfresco.transformer.TransformController.createTransformOptions;
import static org.alfresco.transformer.fs.FileManager.*;
import static org.alfresco.transformer.transformers.HtmlParserContentTransformer.SOURCE_ENCODING;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

@Controller
public class MiscController extends AbstractTransformerController {
    private static final Logger logger = LoggerFactory.getLogger(MiscController.class);

    @Override
    public String getTransformerName() {
        return "Miscellaneous Transformers";
    }

    @PostMapping(value = "/transform", consumes = MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Resource> transform(HttpServletRequest request,
                                              @RequestParam("file") MultipartFile sourceMultipartFile,
                                              @RequestParam("targetExtension") String targetExtension,
                                              @RequestParam("targetMimetype") String targetMimetype,
                                              @RequestParam(value = "targetEncoding", required = false) String targetEncoding,
                                              @RequestParam("sourceMimetype") String sourceMimetype,
                                              @RequestParam(value = "sourceEncoding", required = false) String sourceEncoding,
                                              @RequestParam(value = "pageLimit", required = false) String pageLimit,
                                              @RequestParam(value = "testDelay", required = false) Long testDelay) {
        final String targetFilename = createTargetFileName(sourceMultipartFile.getOriginalFilename(), targetExtension);
        getProbeTestTransform().incrementTransformerCount();
        final File sourceFile = createSourceFile(request, sourceMultipartFile);
        final File targetFile = createTargetFile(request, targetFilename);

        final Map<String, String> transformOptions = createTransformOptions(
                "sourceEncoding", sourceEncoding,
                "targetEncoding", targetEncoding,
                "pageLimit", pageLimit);

        transformHandler.processTransform(sourceFile, targetFile, sourceMimetype, targetMimetype, transformOptions, 0L);

        final ResponseEntity<Resource> body = createAttachment(targetFilename, targetFile);
        LogEntry.setTargetSize(targetFile.length());
        long time = LogEntry.setStatusCodeAndMessage(OK.value(), "Success");
        time += LogEntry.addDelay(testDelay);
        getProbeTestTransform().recordTransformTime(time);
        return body;
    }
}
