/**
 * Copyright Microsoft Corporation
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.microsoft.azure.storage.table;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.util.UUID;

import javax.xml.stream.XMLStreamException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.TestRunners.CloudTests;
import com.microsoft.azure.storage.TestRunners.DevFabricTests;
import com.microsoft.azure.storage.TestRunners.DevStoreTests;
import com.microsoft.azure.storage.table.TableTestHelper.Class1;
import com.microsoft.azure.storage.table.TableTestHelper.ComplexEntity;
import com.microsoft.azure.storage.table.TableTestHelper.IgnoreOnGetter;
import com.microsoft.azure.storage.table.TableTestHelper.IgnoreOnGetterAndSetter;
import com.microsoft.azure.storage.table.TableTestHelper.IgnoreOnSetter;
import com.microsoft.azure.storage.table.TableTestHelper.InvalidStoreAsEntity;
import com.microsoft.azure.storage.table.TableTestHelper.StoreAsEntity;
import com.microsoft.azure.storage.table.TableTestHelper.StrangeDoubles;

/**
 * Table Serializer Tests
 */
@Category({ DevFabricTests.class, DevStoreTests.class, CloudTests.class })
public class TableSerializerTests {

    private CloudTable table;

    @Before
    public void tableTestMethodSetUp() throws URISyntaxException, StorageException {
        table = TableTestHelper.getRandomTableReference();
        table.createIfNotExists();
    }

    @After
    public void tableTestMethodTearDown() throws StorageException {
        table.deleteIfExists();
    }

    /**
     * In some versions of Java, when more than 64000 XmlReaders are created with the same factory, bizarre
     * error messages are thrown on every subsequent attempt to make an XmlReader: “Message: JAXP00010001: The parser
     * has encountered more than "64000" entity expansions in this document; this is the limit imposed by the JDK.” This
     * is an issue with our library as high concurrency, or even simply long running processes, will have every service
     * request which must read a result fail after 64000 readers have been made. This tests that our workaround is
     * functioning correctly. The JDK bug applies to, at least, versions 6u65 and 7u45 and will be fixed in future
     * versions.
     * 
     * @see <a href="https://bugs.openjdk.java.net/browse/JDK-8028111">Oracle JDK Bug</a>
     * @throws XMLStreamException
     */
    @Test
    public void testStaxEntityExpansionExceptionHandling() throws XMLStreamException {
        String testXML = "<?xml version=\"1.0\"?><microsoftazurestorage>javasdk</microsoftazurestorage>";

        for (int i = 0; i < 64001; i++) {
            ByteArrayInputStream stream = new ByteArrayInputStream(testXML.getBytes());
            DeserializationHelper.createXMLStreamReaderFromStream(stream);
        }

        for (int i = 0; i < 64001; i++) {
            StringReader reader = new StringReader(testXML);
            DeserializationHelper.createXMLStreamReaderFromReader(reader);
        }
    }

    @Test
    public void testIgnoreAnnotation() throws StorageException {
        // Ignore On Getter
        IgnoreOnGetter ignoreGetter = new IgnoreOnGetter();
        ignoreGetter.setPartitionKey("jxscl_odata");
        ignoreGetter.setRowKey(UUID.randomUUID().toString());
        ignoreGetter.setIgnoreString("ignore data");

        table.execute(TableOperation.insert(ignoreGetter));

        TableResult res = table.execute(TableOperation.retrieve(ignoreGetter.getPartitionKey(),
                ignoreGetter.getRowKey(), IgnoreOnGetter.class));

        IgnoreOnGetter retrievedIgnoreG = res.getResultAsType();
        assertEquals(retrievedIgnoreG.getIgnoreString(), null);

        // Ignore On Setter
        IgnoreOnSetter ignoreSetter = new IgnoreOnSetter();
        ignoreSetter.setPartitionKey("jxscl_odata");
        ignoreSetter.setRowKey(UUID.randomUUID().toString());
        ignoreSetter.setIgnoreString("ignore data");

        table.execute(TableOperation.insert(ignoreSetter));

        res = table.execute(TableOperation.retrieve(ignoreSetter.getPartitionKey(), ignoreSetter.getRowKey(),
                IgnoreOnSetter.class));

        IgnoreOnSetter retrievedIgnoreS = res.getResultAsType();
        assertEquals(retrievedIgnoreS.getIgnoreString(), null);

        // Ignore On Getter AndSetter
        IgnoreOnGetterAndSetter ignoreGetterSetter = new IgnoreOnGetterAndSetter();
        ignoreGetterSetter.setPartitionKey("jxscl_odata");
        ignoreGetterSetter.setRowKey(UUID.randomUUID().toString());
        ignoreGetterSetter.setIgnoreString("ignore data");

        table.execute(TableOperation.insert(ignoreGetterSetter));

        res = table.execute(TableOperation.retrieve(ignoreGetterSetter.getPartitionKey(),
                ignoreGetterSetter.getRowKey(), IgnoreOnGetterAndSetter.class));

        IgnoreOnGetterAndSetter retrievedIgnoreGS = res.getResultAsType();
        assertEquals(retrievedIgnoreGS.getIgnoreString(), null);
    }

    @Test
    public void testStoreAsAnnotation() throws StorageException {
        StoreAsEntity ref = new StoreAsEntity();
        ref.setPartitionKey("jxscl_odata");
        ref.setRowKey(UUID.randomUUID().toString());
        ref.setStoreAsString("StoreAsOverride Data");
        ref.populateEntity();

        table.execute(TableOperation.insert(ref));

        TableResult res = table.execute(TableOperation.retrieve(ref.getPartitionKey(), ref.getRowKey(),
                StoreAsEntity.class));

        StoreAsEntity retrievedStoreAsRef = res.getResultAsType();
        assertEquals(retrievedStoreAsRef.getStoreAsString(), ref.getStoreAsString());

        // Same query with a class without the storeAs annotation
        res = table.execute(TableOperation.retrieve(ref.getPartitionKey(), ref.getRowKey(), ComplexEntity.class));

        ComplexEntity retrievedComplexRef = res.getResultAsType();
        assertEquals(retrievedComplexRef.getString(), ref.getStoreAsString());

        table.execute(TableOperation.delete(retrievedComplexRef));
    }

    @Test
    public void testInvalidStoreAsAnnotation() throws StorageException {
        InvalidStoreAsEntity ref = new InvalidStoreAsEntity();
        ref.setPartitionKey("jxscl_odata");
        ref.setRowKey(UUID.randomUUID().toString());
        ref.setStoreAsString("StoreAsOverride Data");
        ref.populateEntity();

        table.execute(TableOperation.insert(ref));

        TableResult res = table.execute(TableOperation.retrieve(ref.getPartitionKey(), ref.getRowKey(),
                InvalidStoreAsEntity.class));

        InvalidStoreAsEntity retrievedStoreAsRef = res.getResultAsType();
        assertEquals(retrievedStoreAsRef.getStoreAsString(), null);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testComplexEntityInsert() throws StorageException {
        TableRequestOptions options = new TableRequestOptions();

        options.setTablePayloadFormat(TablePayloadFormat.AtomPub);
        testComplexEntityInsert(options, false);

        options.setTablePayloadFormat(TablePayloadFormat.JsonFullMetadata);
        testComplexEntityInsert(options, false);

        options.setTablePayloadFormat(TablePayloadFormat.Json);
        testComplexEntityInsert(options, false);

        options.setTablePayloadFormat(TablePayloadFormat.JsonNoMetadata);
        testComplexEntityInsert(options, false);

        options.setTablePayloadFormat(TablePayloadFormat.JsonNoMetadata);
        testComplexEntityInsert(options, true);
    }

    private void testComplexEntityInsert(TableRequestOptions options, boolean usePropertyResolver)
            throws StorageException {
        ComplexEntity ref = new ComplexEntity();
        ref.setPartitionKey("jxscl_odata");
        ref.setRowKey(UUID.randomUUID().toString());
        ref.populateEntity();

        if (usePropertyResolver) {
            options.setPropertyResolver(ref);
        }

        table.execute(TableOperation.insert(ref), options, null);

        TableResult res = table.execute(
                TableOperation.retrieve(ref.getPartitionKey(), ref.getRowKey(), ComplexEntity.class), options, null);

        ComplexEntity retrievedComplexRef = res.getResultAsType();
        ref.assertEquality(retrievedComplexRef);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testDoubles() throws StorageException {
        TableRequestOptions options = new TableRequestOptions();

        options.setTablePayloadFormat(TablePayloadFormat.AtomPub);
        testDoubles(options, false);

        options.setTablePayloadFormat(TablePayloadFormat.JsonFullMetadata);
        testDoubles(options, false);

        options.setTablePayloadFormat(TablePayloadFormat.Json);
        testDoubles(options, false);

        options.setTablePayloadFormat(TablePayloadFormat.JsonNoMetadata);
        testDoubles(options, false);

        options.setTablePayloadFormat(TablePayloadFormat.JsonNoMetadata);
        testDoubles(options, true);
    }

    private void testDoubles(TableRequestOptions options, boolean usePropertyResolver) throws StorageException {
        StrangeDoubles ref = new StrangeDoubles();
        ref.setPartitionKey("jxscl_odata");
        ref.setRowKey(UUID.randomUUID().toString());
        ref.populateEntity();

        if (usePropertyResolver) {
            options.setPropertyResolver(ref);
        }

        // try with pojo
        table.execute(TableOperation.insert(ref), options, null);

        TableResult res = table.execute(
                TableOperation.retrieve(ref.getPartitionKey(), ref.getRowKey(), StrangeDoubles.class), options, null);

        StrangeDoubles retrievedComplexRef = res.getResultAsType();
        ref.assertEquality(retrievedComplexRef);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testWhitespaceTest() throws StorageException {
        TableRequestOptions options = new TableRequestOptions();

        options.setTablePayloadFormat(TablePayloadFormat.AtomPub);
        testWhitespaceTest(options, false);

        options.setTablePayloadFormat(TablePayloadFormat.JsonFullMetadata);
        testWhitespaceTest(options, false);

        options.setTablePayloadFormat(TablePayloadFormat.Json);
        testWhitespaceTest(options, false);

        options.setTablePayloadFormat(TablePayloadFormat.JsonNoMetadata);
        testWhitespaceTest(options, false);

        options.setTablePayloadFormat(TablePayloadFormat.JsonNoMetadata);
        testWhitespaceTest(options, true);
    }

    private void testWhitespaceTest(TableRequestOptions options, boolean usePropertyResolver) throws StorageException {
        Class1 ref = new Class1();

        ref.setA("B    ");
        ref.setB("    A   ");
        ref.setC(" ");
        ref.setD(new byte[] { 0, 1, 2 });
        ref.setPartitionKey("jxscl_odata");
        ref.setRowKey(UUID.randomUUID().toString());

        if (usePropertyResolver) {
            options.setPropertyResolver(ref);
        }

        table.execute(TableOperation.insert(ref), options, null);

        TableResult res = table.execute(TableOperation.retrieve(ref.getPartitionKey(), ref.getRowKey(), Class1.class),
                options, null);

        assertEquals(((Class1) res.getResult()).getA(), ref.getA());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testWhitespaceOnEmptyKeysTest() throws StorageException {
        TableRequestOptions options = new TableRequestOptions();

        options.setTablePayloadFormat(TablePayloadFormat.AtomPub);
        testWhitespaceOnEmptyKeysTest(options, false);

        options.setTablePayloadFormat(TablePayloadFormat.JsonFullMetadata);
        testWhitespaceOnEmptyKeysTest(options, false);

        options.setTablePayloadFormat(TablePayloadFormat.Json);
        testWhitespaceOnEmptyKeysTest(options, false);

        options.setTablePayloadFormat(TablePayloadFormat.JsonNoMetadata);
        testWhitespaceOnEmptyKeysTest(options, false);

        options.setTablePayloadFormat(TablePayloadFormat.JsonNoMetadata);
        testWhitespaceOnEmptyKeysTest(options, true);
    }

    private void testWhitespaceOnEmptyKeysTest(TableRequestOptions options, boolean usePropertyResolver)
            throws StorageException {
        Class1 ref = new Class1();

        ref.setA("B    ");
        ref.setB("    A   ");
        ref.setC(" ");
        ref.setD(new byte[] { 0, 1, 2 });
        ref.setPartitionKey("");
        ref.setRowKey("");

        if (usePropertyResolver) {
            options.setPropertyResolver(ref);
        }

        table.execute(TableOperation.insert(ref), options, null);

        TableResult res = table.execute(TableOperation.retrieve(ref.getPartitionKey(), ref.getRowKey(), Class1.class),
                options, null);

        assertEquals(((Class1) res.getResult()).getA(), ref.getA());

        table.execute(TableOperation.delete(ref), options, null);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testNewLineTest() throws StorageException {
        TableRequestOptions options = new TableRequestOptions();

        options.setTablePayloadFormat(TablePayloadFormat.AtomPub);
        testNewLineTest(options, false);

        options.setTablePayloadFormat(TablePayloadFormat.JsonFullMetadata);
        testNewLineTest(options, false);

        options.setTablePayloadFormat(TablePayloadFormat.Json);
        testNewLineTest(options, false);

        options.setTablePayloadFormat(TablePayloadFormat.JsonNoMetadata);
        testNewLineTest(options, false);

        options.setTablePayloadFormat(TablePayloadFormat.JsonNoMetadata);
        testNewLineTest(options, true);
    }

    private void testNewLineTest(TableRequestOptions options, boolean usePropertyResolver) throws StorageException {
        Class1 ref = new Class1();

        ref.setA("B    ");
        ref.setB("    A   ");
        ref.setC("\r\n");
        ref.setD(new byte[] { 0, 1, 2 });
        ref.setPartitionKey("jxscl_odata");
        ref.setRowKey(UUID.randomUUID().toString());

        if (usePropertyResolver) {
            options.setPropertyResolver(ref);
        }

        table.execute(TableOperation.insert(ref), options, null);

        TableResult res = table.execute(TableOperation.retrieve(ref.getPartitionKey(), ref.getRowKey(), Class1.class),
                options, null);

        assertEquals(((Class1) res.getResult()).getA(), ref.getA());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testNulls() throws StorageException {
        TableRequestOptions options = new TableRequestOptions();

        options.setTablePayloadFormat(TablePayloadFormat.AtomPub);
        testNulls(options, false);

        options.setTablePayloadFormat(TablePayloadFormat.JsonFullMetadata);
        testNulls(options, false);

        options.setTablePayloadFormat(TablePayloadFormat.Json);
        testNulls(options, false);

        options.setTablePayloadFormat(TablePayloadFormat.JsonNoMetadata);
        testNulls(options, false);

        options.setTablePayloadFormat(TablePayloadFormat.JsonNoMetadata);
        testNulls(options, true);
    }

    private void testNulls(TableRequestOptions options, boolean usePropertyResolver) throws StorageException {
        ComplexEntity ref = new ComplexEntity();
        ref.setPartitionKey("jxscl_odata");
        ref.setRowKey(UUID.randomUUID().toString());
        ref.populateEntity();

        if (usePropertyResolver) {
            options.setPropertyResolver(ref);
        }

        // Binary object
        ref.setBinary(null);

        table.execute(TableOperation.insert(ref));
        TableResult res = table.execute(
                TableOperation.retrieve(ref.getPartitionKey(), ref.getRowKey(), ComplexEntity.class), options, null);
        ref = res.getResultAsType();

        assertNull("Binary should be null", ref.getBinary());

        // Bool
        ref.setBool(null);
        table.execute(TableOperation.replace(ref), options, null);

        res = table.execute(TableOperation.retrieve(ref.getPartitionKey(), ref.getRowKey(), ComplexEntity.class),
                options, null);

        ref = res.getResultAsType();

        assertNull("Bool should be null", ref.getBool());

        // Date
        ref.setDateTime(null);
        table.execute(TableOperation.replace(ref), options, null);

        res = table.execute(TableOperation.retrieve(ref.getPartitionKey(), ref.getRowKey(), ComplexEntity.class),
                options, null);

        ref = res.getResultAsType();

        assertNull("Date should be null", ref.getDateTime());

        // Double
        ref.setDouble(null);
        table.execute(TableOperation.replace(ref), options, null);

        res = table.execute(TableOperation.retrieve(ref.getPartitionKey(), ref.getRowKey(), ComplexEntity.class),
                options, null);

        ref = res.getResultAsType();

        assertNull("Double should be null", ref.getDouble());

        // UUID
        ref.setGuid(null);
        table.execute(TableOperation.replace(ref), options, null);

        res = table.execute(TableOperation.retrieve(ref.getPartitionKey(), ref.getRowKey(), ComplexEntity.class),
                options, null);

        ref = res.getResultAsType();

        assertNull("UUID should be null", ref.getGuid());

        // Int32
        ref.setInt32(null);
        table.execute(TableOperation.replace(ref), options, null);

        res = table.execute(TableOperation.retrieve(ref.getPartitionKey(), ref.getRowKey(), ComplexEntity.class),
                options, null);

        ref = res.getResultAsType();

        assertNull("Int32 should be null", ref.getInt32());

        // Int64
        ref.setInt64(null);
        table.execute(TableOperation.replace(ref), options, null);

        res = table.execute(TableOperation.retrieve(ref.getPartitionKey(), ref.getRowKey(), ComplexEntity.class),
                options, null);

        ref = res.getResultAsType();

        assertNull("Int64 should be null", ref.getInt64());

        // String
        ref.setString(null);
        table.execute(TableOperation.replace(ref), options, null);

        res = table.execute(TableOperation.retrieve(ref.getPartitionKey(), ref.getRowKey(), ComplexEntity.class),
                options, null);

        ref = res.getResultAsType();

        assertNull("String should be null", ref.getString());
    }
}
