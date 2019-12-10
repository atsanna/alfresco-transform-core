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

import org.alfresco.transform.client.model.TransformReply;
import org.alfresco.transform.client.model.TransformRequest;
import org.alfresco.transform.client.model.TransformRequestValidator;
import org.alfresco.transform.exceptions.TransformException;
import org.alfresco.transformer.clients.AlfrescoSharedFileStoreClient;
import org.alfresco.transformer.logging.LogEntry;
import org.alfresco.transformer.model.FileRefResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.validation.DirectFieldBindingResult;
import org.springframework.validation.Errors;
import org.springframework.web.client.HttpClientErrorException;

import java.io.File;

import static java.util.stream.Collectors.joining;
import static org.alfresco.transformer.fs.FileManager.TempFileProvider.createTempFile;
import static org.alfresco.transformer.fs.FileManager.*;
import static org.springframework.http.HttpStatus.*;
import static org.springframework.util.StringUtils.getFilenameExtension;

@Component
public class TransformRequestHandler {
    private static final Logger logger = LoggerFactory.getLogger(TransformRequestHandler.class);

    @Autowired
    private TransformHandler transformHandler;

    @Autowired
    private AlfrescoSharedFileStoreClient alfrescoSharedFileStoreClient;

    @Autowired
    private TransformRequestValidator transformRequestValidator;

    public ResponseEntity<TransformReply> transform(final TransformRequest request, final Long timeout) {
        logger.info("Received {}, timeout {} ms", request, timeout);

        final TransformReply reply = new TransformReply();
        reply.setInternalContext(request.getInternalContext());
        reply.setRequestId(request.getRequestId());
        reply.setSourceReference(request.getSourceReference());
        reply.setSchema(request.getSchema());
        reply.setClientData(request.getClientData());

        final Errors errors = validateTransformRequest(request);
        if (!errors.getAllErrors().isEmpty()) {
            reply.setStatus(BAD_REQUEST.value());
            reply.setErrorDetails(errors
                    .getAllErrors()
                    .stream()
                    .map(Object::toString)
                    .collect(joining(", ")));

            logger.error("Invalid request, sending {}", reply);
            return new ResponseEntity<>(reply, HttpStatus.valueOf(reply.getStatus()));
        }

        // Load the source file
        File sourceFile;
        try {
            sourceFile = loadSourceFile(request.getSourceReference());
        } catch (TransformException e) {
            reply.setStatus(e.getStatusCode());
            reply.setErrorDetails(messageWithCause("Failed at reading the source file", e));

            logger.error("Failed to load source file (TransformException), sending " + reply);
            return new ResponseEntity<>(reply, HttpStatus.valueOf(reply.getStatus()));
        } catch (HttpClientErrorException e) {
            reply.setStatus(e.getStatusCode().value());
            reply.setErrorDetails(messageWithCause("Failed at reading the source file", e));

            logger.error("Failed to load source file (HttpClientErrorException), sending " +
                    reply, e);
            return new ResponseEntity<>(reply, HttpStatus.valueOf(reply.getStatus()));
        } catch (Exception e) {
            reply.setStatus(INTERNAL_SERVER_ERROR.value());
            reply.setErrorDetails(messageWithCause("Failed at reading the source file", e));

            logger.error("Failed to load source file (Exception), sending " + reply, e);
            return new ResponseEntity<>(reply, HttpStatus.valueOf(reply.getStatus()));
        }

        // Create local temp target file in order to run the transformation
        final String targetFilename = createTargetFileName(sourceFile.getName(),
                request.getTargetExtension());
        final File targetFile = buildFile(targetFilename);

        // Run the transformation
        try {
            transformHandler.processTransform(sourceFile, targetFile, request.getSourceMediaType(),
                    request.getTargetMediaType(), request.getTransformRequestOptions(), timeout);
        } catch (TransformException e) {
            reply.setStatus(e.getStatusCode());
            reply.setErrorDetails(messageWithCause("Failed at processing transformation", e));

            logger.error("Failed to perform transform (TransformException), sending " + reply, e);
            return new ResponseEntity<>(reply, HttpStatus.valueOf(reply.getStatus()));
        } catch (Exception e) {
            reply.setStatus(INTERNAL_SERVER_ERROR.value());
            reply.setErrorDetails(messageWithCause("Failed at processing transformation", e));

            logger.error("Failed to perform transform (Exception), sending " + reply, e);
            return new ResponseEntity<>(reply, HttpStatus.valueOf(reply.getStatus()));
        }

        // Write the target file
        FileRefResponse targetRef;
        try {
            targetRef = alfrescoSharedFileStoreClient.saveFile(targetFile);
        } catch (TransformException e) {
            reply.setStatus(e.getStatusCode());
            reply.setErrorDetails(messageWithCause("Failed at writing the transformed file", e));

            logger.error("Failed to save target file (TransformException), sending " + reply, e);
            return new ResponseEntity<>(reply, HttpStatus.valueOf(reply.getStatus()));
        } catch (HttpClientErrorException e) {
            reply.setStatus(e.getStatusCode().value());
            reply.setErrorDetails(messageWithCause("Failed at writing the transformed file. ", e));

            logger.error("Failed to save target file (HttpClientErrorException), sending " + reply,
                    e);
            return new ResponseEntity<>(reply, HttpStatus.valueOf(reply.getStatus()));
        } catch (Exception e) {
            reply.setStatus(INTERNAL_SERVER_ERROR.value());
            reply.setErrorDetails(messageWithCause("Failed at writing the transformed file. ", e));

            logger.error("Failed to save target file (Exception), sending " + reply, e);
            return new ResponseEntity<>(reply, HttpStatus.valueOf(reply.getStatus()));
        }

        try {
            deleteFile(targetFile);
        } catch (Exception e) {
            logger.error("Failed to delete local temp target file '{}'. Error will be ignored ",
                    targetFile, e);
        }
        try {
            deleteFile(sourceFile);
        } catch (Exception e) {
            logger.error("Failed to delete source local temp file " + sourceFile, e);
        }

        reply.setTargetReference(targetRef.getEntry().getFileRef());
        reply.setStatus(CREATED.value());

        logger.info("Sending successful {}, timeout {} ms", reply, timeout);
        return new ResponseEntity<>(reply, HttpStatus.valueOf(reply.getStatus()));
    }


    private Errors validateTransformRequest(final TransformRequest transformRequest) {
        DirectFieldBindingResult errors = new DirectFieldBindingResult(transformRequest, "request");
        transformRequestValidator.validate(transformRequest, errors);
        return errors;
    }

    /**
     * Loads the file with the specified sourceReference from Alfresco Shared File Store
     *
     * @param sourceReference reference to the file in Alfresco Shared File Store
     * @return the file containing the source content for the transformation
     */
    private File loadSourceFile(final String sourceReference) {
        final ResponseEntity<Resource> responseEntity = alfrescoSharedFileStoreClient
                .retrieveFile(sourceReference);
        transformHandler.getProbeTestTransform().incrementTransformerCount();

        final HttpHeaders headers = responseEntity.getHeaders();
        final String filename = getFilenameFromContentDisposition(headers);

        String extension = getFilenameExtension(filename);
        MediaType contentType = headers.getContentType();
        long size = headers.getContentLength();

        final Resource body = responseEntity.getBody();
        if (body == null) {
            String message = "Source file with reference: " + sourceReference + " is null or empty. "
                    + "Transformation will fail and stop now as there is no content to be transformed.";
            logger.warn(message);
            throw new TransformException(BAD_REQUEST.value(), message);
        }
        final File file = createTempFile("source_", "." + extension);

        logger.debug("Read source content {} length={} contentType={}",
                sourceReference, size, contentType);

        save(body, file);
        LogEntry.setSource(filename, size);
        return file;
    }

    private static String messageWithCause(final String prefix, Throwable e) {
        final StringBuilder sb = new StringBuilder();
        sb.append(prefix).append(" - ")
                .append(e.getClass().getSimpleName()).append(": ")
                .append(e.getMessage());

        while (e.getCause() != null) {
            e = e.getCause();
            sb.append(", cause ")
                    .append(e.getClass().getSimpleName()).append(": ")
                    .append(e.getMessage());
        }

        return sb.toString();
    }
}
