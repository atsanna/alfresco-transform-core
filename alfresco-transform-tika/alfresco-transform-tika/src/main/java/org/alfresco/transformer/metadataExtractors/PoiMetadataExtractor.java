/*
 * #%L
 * Alfresco Transform Core
 * %%
 * Copyright (C) 2005 - 2021 Alfresco Software Limited
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
package org.alfresco.transformer.metadataExtractors;

import org.apache.poi.ooxml.POIXMLProperties;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.tika.embedder.Embedder;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.microsoft.ooxml.OOXMLParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Set;
import java.util.StringJoiner;

/**
 * POI-based metadata extractor for Office 07 documents. See http://poi.apache.org/ for information on POI.
 *
 * Configuration:   (see PoiMetadataExtractor_metadata_extract.properties and tika_engine_config.json)
 *
 * <pre>
 *   <b>author:</b>                 --      cm:author
 *   <b>title:</b>                  --      cm:title
 *   <b>subject:</b>                --      cm:description
 *   <b>created:</b>                --      cm:created
 *   <b>Any custom property:</b>    --      [not mapped]
 * </pre>
 *
 * Uses Apache Tika
 *
 * Also includes a sample POI metadata embedder to demonstrate it is possible to add custom T-Engines that will add
 * metadata. This is not production code so no supported mimetypes exist in the {@code tika_engine_config.json}.
 * Adding the following would make it available:
 *
 * <pre>
 * {
 *   "transformOptions": {
 *     ...
 *     "metadataEmbedOptions": [
 *       {"value": {"name": "metadata", "required": true}}
 *     ]
 *   },
 *   "transformers": [
 *     ...
 *     {
 *       "transformerName": "SamplePoiMetadataEmbedder",
 *       "supportedSourceAndTargetList": [
 *         ...
 *         {"sourceMediaType": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "targetMediaType": "alfresco-metadata-embed"}
 *       ],
 *       "transformOptions": [
 *         "metadataEmbedOptions"
 *       ]
 *     }
 *   ]
 * }
 * </pre>

 * @author Nick Burch
 * @author Neil McErlean
 * @author Dmitry Velichkevich
 * @author adavis
 */
public class PoiMetadataExtractor extends AbstractTikaMetadataExtractor
{
    private static final Logger logger = LoggerFactory.getLogger(PoiMetadataExtractor.class);

    public PoiMetadataExtractor()
    {
        super(logger);
    }

    @Override
    protected Parser getParser()
    {
        return new OOXMLParser();
    }

    @Override
    protected Embedder getEmbedder()
    {
        return new SamplePoiEmbedder();
    }

    private static class SamplePoiEmbedder implements Embedder
    {
        private static final Set<MediaType> SUPPORTED_EMBED_TYPES =
                Collections.singleton(MediaType.application("vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        @Override
        public Set<MediaType> getSupportedEmbedTypes(ParseContext parseContext)
        {
            return SUPPORTED_EMBED_TYPES;
        }

        @Override
        public void embed(Metadata metadata, InputStream inputStream, OutputStream outputStream, ParseContext parseContext)
                throws IOException
        {
            XSSFWorkbook workbook = new XSSFWorkbook(inputStream);
            POIXMLProperties props = workbook.getProperties();

            POIXMLProperties.CoreProperties coreProp = props.getCoreProperties();
            POIXMLProperties.CustomProperties custProp = props.getCustomProperties();

            for (String name : metadata.names())
            {
                metadata.isMultiValued("description");
                String value = null;
                if (metadata.isMultiValued(name))
                {
                    String[] values = metadata.getValues(name);
                    StringJoiner sj = new StringJoiner(", ");
                    for (String s : values)
                    {
                        sj.add(s);
                    }
                    value = sj.toString();
                }
                else
                {
                    value = metadata.get(name);
                }
                switch (name)
                {
                    case "author":
                        coreProp.setCreator(value);
                        break;
                    case "title":
                        coreProp.setTitle(value);
                        break;
                    case "description":
                        coreProp.setDescription(value);
                        break;
                    // There are other core values but this is sample code, so we will assume it is a custom value.
                    default:
                        custProp.addProperty(name, value);
                        break;
                }
            }
            workbook.write(outputStream);
        }
    }
}
