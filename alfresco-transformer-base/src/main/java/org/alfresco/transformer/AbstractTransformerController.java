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
import org.alfresco.transform.client.model.config.TransformConfig;
import org.alfresco.transform.client.registry.TransformServiceRegistry;
import org.alfresco.transformer.probes.ProbeTestTransform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;

import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * <p>Abstract Controller, provides structure and helper methods to sub-class transformer controllers.</p>
 *
 * <p>Status Codes:</p>
 * <ul>
 * <li>200 Success</li>
 * <li>400 Bad Request: Request parameter <name> is missing (missing mandatory parameter)</li>
 * <li>400 Bad Request: Request parameter <name> is of the wrong type</li>
 * <li>400 Bad Request: Transformer exit code was not 0 (possible problem with the source file)</li>
 * <li>400 Bad Request: The source filename was not supplied</li>
 * <li>500 Internal Server Error: (no message with low level IO problems)</li>
 * <li>500 Internal Server Error: The target filename was not supplied (should not happen as targetExtension is checked)</li>
 * <li>500 Internal Server Error: Transformer version check exit code was not 0</li>
 * <li>500 Internal Server Error: Transformer version check failed to create any output</li>
 * <li>500 Internal Server Error: Could not read the target file</li>
 * <li>500 Internal Server Error: The target filename was malformed (should not happen because of other checks)</li>
 * <li>500 Internal Server Error: Transformer failed to create an output file (the exit code was 0, so there should be some content)</li>
 * <li>500 Internal Server Error: Filename encoding error</li>
 * <li>507 Insufficient Storage: Failed to store the source file</li>
 *
 * <li>408 Request Timeout         -- TODO implement general timeout mechanism rather than depend on transformer timeout
 * (might be possible for external processes)</li>
 * <li>415 Unsupported Media Type  -- TODO possibly implement a check on supported source and target mimetypes (probably not)</li>
 * <li>429 Too Many Requests: Returned by liveness probe</li>
 * </ul>
 * <p>Provides methods to help super classes perform /transform requests. Also responses to /version, /ready and /live
 * requests.</p>
 */
public abstract class AbstractTransformerController implements TransformController {
    private static final Logger logger = LoggerFactory.getLogger(
            AbstractTransformerController.class);

    @Autowired
    protected TransformHandler transformHandler;

    @Autowired
    private TransformRequestHandler transformRequestHandler;

    @Autowired
    private TransformServiceRegistry transformRegistry;

    protected ProbeTestTransform probeTestTransform;

    @PostConstruct
    private void init() {
        probeTestTransform = transformHandler.getProbeTestTransform();
    }

    @Override
    public ProbeTestTransform getProbeTestTransform() {
        return probeTestTransform;
    }

    @Override
    public String version()
    {
        return transformHandler.version();
    }

    @GetMapping(value = "/transform/config")
    @Override
    public ResponseEntity<TransformConfig> info() {
        logger.info("GET Transform Config.");
        final TransformConfig transformConfig = ((TransformRegistryImpl) transformRegistry).getTransformConfig();
        return new ResponseEntity<>(transformConfig, OK);
    }

    /**
     * '/transform' endpoint which consumes and produces 'application/json'
     * <p>
     * This is the way to tell Spring to redirect the request to this endpoint
     * instead of the older one, which produces 'html'
     *
     * @param request The transformation request
     * @param timeout Transformation timeout
     * @return A transformation reply
     */
    @PostMapping(value = "/transform", produces = APPLICATION_JSON_VALUE)
    @ResponseBody
    @Override
    public ResponseEntity<TransformReply> transform(@RequestBody TransformRequest request,
                                                    @RequestParam(value = "timeout", required = false) Long timeout) {
        return transformRequestHandler.transform(request, timeout);
    }
}
