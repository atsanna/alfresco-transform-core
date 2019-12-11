/*
 * Copyright 2015-2019 Alfresco Software, Ltd.  All rights reserved.
 *
 * License rights for this program may be obtained from Alfresco Software, Ltd.
 * pursuant to a written agreement and any use of this program without such an
 * agreement is prohibited.
 */
package org.alfresco.transformer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.text.MessageFormat.format;
import static java.util.Collections.emptyMap;
import static org.alfresco.transform.client.model.Mimetype.MIMETYPE_IMAGE_PNG;
import static org.alfresco.transform.client.model.Mimetype.MIMETYPE_PDF;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import java.util.UUID;

import org.alfresco.transform.client.model.InternalContext;
import org.alfresco.transform.client.model.TransformReply;
import org.alfresco.transform.client.model.TransformRequest;
import org.alfresco.transform.client.model.TransformRequestValidator;
import org.alfresco.transform.exceptions.TransformException;
import org.alfresco.transformer.clients.AlfrescoSharedFileStoreClient;
import org.alfresco.transformer.model.FileRefEntity;
import org.alfresco.transformer.model.FileRefResponse;
import org.alfresco.transformer.probes.ProbeTestTransform;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/**
 * @author Cezar Leahu
 */
public class TransformRequestHandlerTest
{
    @Mock
    private TransformHandler transformHandler;

    @Mock
    private AlfrescoSharedFileStoreClient alfrescoSharedFileStoreClient;

    @Spy
    private TransformRequestValidator transformRequestValidator;

    @InjectMocks
    private TransformRequestHandler transformRequestHandler; // = new TransformRequestHandler();

    @Before
    public void setup()
    {
        MockitoAnnotations.initMocks(this);
        doReturn(mock(ProbeTestTransform.class)).when(transformHandler).getProbeTestTransform();
    }

    @Test
    public void testTransform_success()
    {
        final long timeout = 27L;
        final TransformRequest req = TransformRequest
            .builder()
            .withRequestId(UUID.randomUUID().toString())
            .withSourceReference(UUID.randomUUID().toString())
            .withSourceMediaType(MIMETYPE_PDF)
            .withSourceExtension("pdf")
            .withSourceSize(13245L)
            .withTargetMediaType(MIMETYPE_IMAGE_PNG)
            .withTargetExtension("png")
            .withClientData("client data")
            .withInternalContext(new InternalContext())
            .withTransformRequestOptions(emptyMap())
            .withSchema(5)
            .build();

        { // mock setup
            final HttpHeaders headers = new HttpHeaders();
            headers.set(CONTENT_DISPOSITION, format("something; filename=\"fileName.{0}\"; " +
                                                    "foo=\"bar\"", req.getSourceExtension()));
            final Resource resource = new ByteArrayResource("Test file".getBytes(UTF_8));
            final ResponseEntity<Resource> responseEntity = new ResponseEntity<>(resource, headers,
                CREATED);

            doReturn(responseEntity)
                .when(alfrescoSharedFileStoreClient).retrieveFile(any());

            final FileRefResponse fileRefResponseMock = mock(FileRefResponse.class);
            final FileRefEntity fileRefEntity = new FileRefEntity(UUID.randomUUID().toString());
            doReturn(fileRefEntity).when(fileRefResponseMock).getEntry();
            doReturn(fileRefResponseMock).when(alfrescoSharedFileStoreClient).saveFile(any());
        }

        final ResponseEntity<TransformReply> response = transformRequestHandler.transform(req,
            timeout);
        assertEquals(CREATED, response.getStatusCode());
        final TransformReply reply = response.getBody();
        assertNotNull(reply);
        assertEquals(req.getRequestId(), reply.getRequestId());
        assertEquals(CREATED.value(), reply.getStatus());
        assertNull(reply.getErrorDetails());
        assertEquals(req.getSourceReference(), reply.getSourceReference());
        assertNotNull(reply.getTargetReference());
        assertEquals(req.getClientData(), reply.getClientData());
        assertEquals(req.getSchema(), reply.getSchema());
        assertSame(req.getInternalContext(), reply.getInternalContext());

        verify(transformRequestValidator, times(1))
            .validate(eq(req), any());
        verify(alfrescoSharedFileStoreClient, times(1))
            .retrieveFile(eq(req.getSourceReference()));
        verify(transformHandler, times(1)).getProbeTestTransform();
        verify(transformHandler, times(1))
            .processTransform(notNull(), notNull(),
                eq(req.getSourceMediaType()), eq(req.getTargetMediaType()),
                eq(req.getTransformRequestOptions()), eq(timeout));
        verify(alfrescoSharedFileStoreClient, times(1))
            .saveFile(notNull());

        verifyNoMoreInteractions(transformHandler);
        verifyNoMoreInteractions(transformRequestValidator);
        verifyNoMoreInteractions(alfrescoSharedFileStoreClient);
    }

    @Test
    public void testTransform_requestValidationFailure()
    {
        final long timeout = 27L;
        final TransformRequest req = TransformRequest
            .builder()
            .withRequestId("   ")
            .withSourceReference(UUID.randomUUID().toString())
            .withSourceMediaType(MIMETYPE_PDF)
            .withSourceExtension("pdf")
            .withSourceSize(13245L)
            .withTargetMediaType(MIMETYPE_IMAGE_PNG)
            .withTargetExtension("png")
            .withClientData("client data")
            .withInternalContext(new InternalContext())
            .withTransformRequestOptions(emptyMap())
            .withSchema(5)
            .build();

        final ResponseEntity<TransformReply> response = transformRequestHandler.transform(req,
            timeout);
        assertEquals(BAD_REQUEST, response.getStatusCode());
        final TransformReply reply = response.getBody();
        assertNotNull(reply);
        assertEquals(req.getRequestId(), reply.getRequestId());
        assertEquals(BAD_REQUEST.value(), reply.getStatus());
        assertNotNull(reply.getErrorDetails());
        assertFalse(reply.getErrorDetails().isBlank());
        assertEquals(req.getSourceReference(), reply.getSourceReference());
        assertNull(reply.getTargetReference());
        assertEquals(req.getClientData(), reply.getClientData());
        assertEquals(req.getSchema(), reply.getSchema());
        assertSame(req.getInternalContext(), reply.getInternalContext());

        verify(transformRequestValidator, times(1))
            .validate(eq(req), any());

        verifyNoMoreInteractions(transformHandler);
        verifyNoMoreInteractions(transformRequestValidator);
        verifyNoMoreInteractions(alfrescoSharedFileStoreClient);
    }

    @Test
    public void testTranform_sourceFileRetrievalFailure()
    {
        final long timeout = 27L;
        final TransformRequest req = TransformRequest
            .builder()
            .withRequestId(UUID.randomUUID().toString())
            .withSourceReference(UUID.randomUUID().toString())
            .withSourceMediaType(MIMETYPE_PDF)
            .withSourceExtension("pdf")
            .withSourceSize(13245L)
            .withTargetMediaType(MIMETYPE_IMAGE_PNG)
            .withTargetExtension("png")
            .withClientData("client data")
            .withInternalContext(new InternalContext())
            .withTransformRequestOptions(emptyMap())
            .withSchema(5)
            .build();

        { // mock setup
            doThrow(new TransformException(BAD_REQUEST.value(), "error", new Exception()))
                .when(alfrescoSharedFileStoreClient).retrieveFile(any());
        }

        final ResponseEntity<TransformReply> response = transformRequestHandler.transform(req,
            timeout);
        assertEquals(BAD_REQUEST, response.getStatusCode());
        final TransformReply reply = response.getBody();
        assertNotNull(reply);
        assertEquals(req.getRequestId(), reply.getRequestId());
        assertEquals(BAD_REQUEST.value(), reply.getStatus());
        assertNotNull(reply.getErrorDetails());
        assertFalse(reply.getErrorDetails().isBlank());
        assertEquals(req.getSourceReference(), reply.getSourceReference());
        assertNull(reply.getTargetReference());
        assertEquals(req.getClientData(), reply.getClientData());
        assertEquals(req.getSchema(), reply.getSchema());
        assertSame(req.getInternalContext(), reply.getInternalContext());

        verify(transformRequestValidator, times(1))
            .validate(eq(req), any());
        verify(alfrescoSharedFileStoreClient, times(1))
            .retrieveFile(eq(req.getSourceReference()));
        verifyNoMoreInteractions(transformHandler);
        verifyNoMoreInteractions(transformRequestValidator);
        verifyNoMoreInteractions(alfrescoSharedFileStoreClient);
    }

    @Test
    public void testTranform_targetFileSaveFailure()
    {
        final long timeout = 27L;
        final TransformRequest req = TransformRequest
            .builder()
            .withRequestId(UUID.randomUUID().toString())
            .withSourceReference(UUID.randomUUID().toString())
            .withSourceMediaType(MIMETYPE_PDF)
            .withSourceExtension("pdf")
            .withSourceSize(13245L)
            .withTargetMediaType(MIMETYPE_IMAGE_PNG)
            .withTargetExtension("png")
            .withClientData("client data")
            .withInternalContext(new InternalContext())
            .withTransformRequestOptions(emptyMap())
            .withSchema(5)
            .build();

        { // mock setup
            final HttpHeaders headers = new HttpHeaders();
            headers.set(CONTENT_DISPOSITION, format("something; filename=\"fileName.{0}\"; " +
                                                    "foo=\"bar\"", req.getSourceExtension()));
            final Resource resource = new ByteArrayResource("Test file".getBytes(UTF_8));
            final ResponseEntity<Resource> responseEntity = new ResponseEntity<>(resource, headers,
                CREATED);

            doReturn(responseEntity)
                .when(alfrescoSharedFileStoreClient).retrieveFile(any());

            final FileRefResponse fileRefResponseMock = mock(FileRefResponse.class);
            final FileRefEntity fileRefEntity = new FileRefEntity(UUID.randomUUID().toString());
            doReturn(fileRefEntity).when(fileRefResponseMock).getEntry();
            
            doThrow(new TransformException(BAD_REQUEST.value(), "error", new Exception()))
                .when(alfrescoSharedFileStoreClient).saveFile(any());
        }

        final ResponseEntity<TransformReply> response = transformRequestHandler.transform(req,
            timeout);
        assertEquals(BAD_REQUEST, response.getStatusCode());
        final TransformReply reply = response.getBody();
        assertNotNull(reply);
        assertEquals(req.getRequestId(), reply.getRequestId());
        assertEquals(BAD_REQUEST.value(), reply.getStatus());
        assertNotNull(reply.getErrorDetails());
        assertFalse(reply.getErrorDetails().isBlank());
        assertEquals(req.getSourceReference(), reply.getSourceReference());
        assertNull(reply.getTargetReference());
        assertEquals(req.getClientData(), reply.getClientData());
        assertEquals(req.getSchema(), reply.getSchema());
        assertSame(req.getInternalContext(), reply.getInternalContext());

        verify(transformRequestValidator, times(1))
            .validate(eq(req), any());
        verify(alfrescoSharedFileStoreClient, times(1))
            .retrieveFile(eq(req.getSourceReference()));
        verify(transformHandler, times(1)).getProbeTestTransform();
        verify(transformHandler, times(1))
            .processTransform(notNull(), notNull(),
                eq(req.getSourceMediaType()), eq(req.getTargetMediaType()),
                eq(req.getTransformRequestOptions()), eq(timeout));
        verify(alfrescoSharedFileStoreClient, times(1))
            .saveFile(notNull());

        verifyNoMoreInteractions(transformHandler);
        verifyNoMoreInteractions(transformRequestValidator);
        verifyNoMoreInteractions(alfrescoSharedFileStoreClient);
    }

    @Test
    public void testTranform_processTransformFailure()
    {
        final long timeout = 27L;
        final TransformRequest req = TransformRequest
            .builder()
            .withRequestId(UUID.randomUUID().toString())
            .withSourceReference(UUID.randomUUID().toString())
            .withSourceMediaType(MIMETYPE_PDF)
            .withSourceExtension("pdf")
            .withSourceSize(13245L)
            .withTargetMediaType(MIMETYPE_IMAGE_PNG)
            .withTargetExtension("png")
            .withClientData("client data")
            .withInternalContext(new InternalContext())
            .withTransformRequestOptions(emptyMap())
            .withSchema(5)
            .build();

        { // mock setup
            final HttpHeaders headers = new HttpHeaders();
            headers.set(CONTENT_DISPOSITION, format("something; filename=\"fileName.{0}\"; " +
                                                    "foo=\"bar\"", req.getSourceExtension()));
            final Resource resource = new ByteArrayResource("Test file".getBytes(UTF_8));
            final ResponseEntity<Resource> responseEntity = new ResponseEntity<>(resource, headers,
                CREATED);

            doReturn(responseEntity)
                .when(alfrescoSharedFileStoreClient).retrieveFile(any());
            
            doThrow(new RuntimeException("error"))
                .when(transformHandler).processTransform(any(), any(), any(), any(), any(), any());

            final FileRefResponse fileRefResponseMock = mock(FileRefResponse.class);
            final FileRefEntity fileRefEntity = new FileRefEntity(UUID.randomUUID().toString());
            doReturn(fileRefEntity).when(fileRefResponseMock).getEntry();
            doReturn(fileRefResponseMock).when(alfrescoSharedFileStoreClient).saveFile(any());
        }

        final ResponseEntity<TransformReply> response = transformRequestHandler.transform(req,
            timeout);
        assertEquals(INTERNAL_SERVER_ERROR, response.getStatusCode());
        final TransformReply reply = response.getBody();
        assertNotNull(reply);
        assertEquals(req.getRequestId(), reply.getRequestId());
        assertEquals(INTERNAL_SERVER_ERROR.value(), reply.getStatus());
        assertNotNull(reply.getErrorDetails());
        assertFalse(reply.getErrorDetails().isBlank());
        assertEquals(req.getSourceReference(), reply.getSourceReference());
        assertNull(reply.getTargetReference());
        assertEquals(req.getClientData(), reply.getClientData());
        assertEquals(req.getSchema(), reply.getSchema());
        assertSame(req.getInternalContext(), reply.getInternalContext());

        verify(transformRequestValidator, times(1))
            .validate(eq(req), any());
        verify(alfrescoSharedFileStoreClient, times(1))
            .retrieveFile(eq(req.getSourceReference()));
        verify(transformHandler, times(1)).getProbeTestTransform();
        verify(transformHandler, times(1))
            .processTransform(notNull(), notNull(),
                eq(req.getSourceMediaType()), eq(req.getTargetMediaType()),
                eq(req.getTransformRequestOptions()), eq(timeout));

        verifyNoMoreInteractions(transformHandler);
        verifyNoMoreInteractions(transformRequestValidator);
        verifyNoMoreInteractions(alfrescoSharedFileStoreClient);
    }
}
