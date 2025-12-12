package de.intranda.goobi.plugins.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.easymock.EasyMock;
import org.goobi.beans.Batch;
import org.goobi.beans.GoobiProperty;
import org.goobi.beans.GoobiProperty.PropertyOwnerType;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import de.sub.goobi.persistence.managers.ProcessManager;
import de.sub.goobi.persistence.managers.PropertyManager;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ ProcessManager.class, PropertyManager.class })
@PowerMockIgnore({ "javax.management.*", "javax.net.ssl.*", "jdk.internal.reflect.*" })
public class QaBatchTest {

    private static String resourcesFolder;

    private Batch batch;

    @BeforeClass
    public static void setUpClass() throws Exception {
        resourcesFolder = "src/test/resources/"; // for junit tests in eclipse

        if (!Files.exists(Paths.get(resourcesFolder))) {
            resourcesFolder = "target/test-classes/"; // to run mvn test from cli or in jenkins
        }

        String log4jFile = resourcesFolder + "log4j2.xml"; // for junit tests in eclipse

        System.setProperty("log4j.configurationFile", log4jFile);

    }

    @Before
    public void setUp() throws Exception {
        PowerMock.mockStatic(ProcessManager.class);
        PowerMock.mockStatic(PropertyManager.class);

        List<Object[]> data = new ArrayList<>();

        Object[] row = new Object[7];
        row[0] = "1";
        row[1] = "100";
        row[2] = "0";
        row[3] = "0";
        row[4] = "done";
        row[5] = "title";
        row[6] = "error";
        data.add(row);

        Object[] row2 = new Object[7];
        row2[0] = "2";
        row2[1] = "50";
        row2[2] = "10";
        row2[3] = "1";
        row2[4] = null;
        row2[5] = "title";
        row2[6] = "error";
        data.add(row2);

        EasyMock.expect(ProcessManager.runSQL(EasyMock.anyString())).andReturn(data).anyTimes();

        List<GoobiProperty> gpl = new ArrayList<>();
        GoobiProperty gp = new GoobiProperty(PropertyOwnerType.BATCH);
        gp.setPropertyName("BatchPercentage");
        gp.setPropertyValue("20");
        gpl.add(gp);
        EasyMock.expect(PropertyManager.getPropertiesForObject(EasyMock.anyInt(), EasyMock.anyObject())).andReturn(gpl).anyTimes();
        PropertyManager.saveProperty(EasyMock.anyObject());

        PowerMock.replay(ProcessManager.class, PropertyManager.class);

        batch = new Batch();
        batch.setBatchId(1);
        batch.setBatchLabel("label");
    }

    @Test
    public void testConstructor() throws IOException {
        QaBatch fixture = new QaBatch(batch, "", null, 10);
        assertNotNull(fixture);
    }

    @Test
    public void testNumberOfProcesses() throws IOException {
        QaBatch fixture = new QaBatch(batch, "", Collections.emptyList(), 10);
        assertEquals(2, fixture.getNumberOfProcesses());
    }

    @Test
    public void testNumberOfPages() throws IOException {
        List<String> metadata = new ArrayList<>();
        metadata.add("md1");
        metadata.add("md2");
        QaBatch fixture = new QaBatch(batch, "", metadata, 10);
        assertEquals(150, fixture.getTotalNumberOfPages());
    }

    @Test
    public void testGetBatch() throws IOException {
        QaBatch fixture = new QaBatch(batch, "", Collections.emptyList(), 10);
        assertEquals("label", fixture.getBatch().getBatchLabel());
    }

    @Test
    public void testPooceses() {
        QaBatch fixture = new QaBatch(batch, "", Collections.emptyList(), 10);
        List<ProcessOverview> proceses = fixture.getProcesses();
        assertEquals(2, proceses.size());
    }
}
