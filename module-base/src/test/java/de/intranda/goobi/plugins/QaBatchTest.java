package de.intranda.goobi.plugins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.easymock.EasyMock;
import org.goobi.beans.Batch;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import de.intranda.goobi.plugins.model.QaBatch;
import de.sub.goobi.persistence.managers.ProcessManager;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ ProcessManager.class })
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

        List<Object[]> data = new ArrayList<>();

        Object[] row = new Object[2];
        row[0] = "1";
        row[1] = "100";
        data.add(row);

        Object[] row2 = new Object[2];
        row2[0] = "2";
        row2[1] = "50";
        data.add(row2);

        EasyMock.expect(ProcessManager.runSQL(EasyMock.anyString())).andReturn(data).anyTimes();

        PowerMock.replay(ProcessManager.class);

        batch = new Batch();
        batch.setBatchId(1);
        batch.setBatchLabel("label");
    }

    @Test
    public void testConstructor() throws IOException {
        QaBatch fixture = new QaBatch(batch, "");
        assertNotNull(fixture);
    }

    @Test
    public void testNumberOfProcesses() throws IOException {
        QaBatch fixture = new QaBatch(batch, "");
        assertEquals(2, fixture.getNumberOfProcesses());
    }

    @Test
    public void testNumberOfPages() throws IOException {
        QaBatch fixture = new QaBatch(batch, "");
        assertEquals(150, fixture.getNumberOfPages());
    }

    @Test
    public void testGetBatch() throws IOException {
        QaBatch fixture = new QaBatch(batch, "");
        assertEquals("label", fixture.getBatch().getBatchLabel());
    }

    @Test
    public void testPooceses() {
        QaBatch fixture = new QaBatch(batch, "");
        Map<String, Integer> proceses = fixture.getProcesses();
        assertEquals(2, proceses.size());
    }
}
