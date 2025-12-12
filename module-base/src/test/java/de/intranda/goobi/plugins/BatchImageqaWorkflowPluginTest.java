package de.intranda.goobi.plugins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.reloading.FileChangedReloadingStrategy;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.goobi.beans.Batch;
import org.goobi.beans.GoobiProperty;
import org.goobi.beans.GoobiProperty.PropertyOwnerType;
import org.goobi.beans.Process;
import org.goobi.production.cli.helper.StringPair;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import de.intranda.goobi.plugins.model.QaBatch;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.helper.StorageProviderInterface;
import de.sub.goobi.persistence.managers.MetadataManager;
import de.sub.goobi.persistence.managers.ProcessManager;
import de.sub.goobi.persistence.managers.PropertyManager;
import ugh.dl.Fileformat;
import ugh.dl.Prefs;
import ugh.fileformats.mets.MetsMods;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({ "javax.management.*", "javax.net.ssl.*", "jdk.internal.reflect.*" })

@PrepareForTest({ ConfigPlugins.class, ProcessManager.class, MetadataManager.class, ConfigurationHelper.class, StorageProvider.class,
        PropertyManager.class })

public class BatchImageqaWorkflowPluginTest {

    private static String resourcesFolder;

    @BeforeClass
    public static void setUpClass() throws Exception {
        resourcesFolder = "src/test/resources/"; // for junit tests in eclipse

        if (!Files.exists(Paths.get(resourcesFolder))) {
            resourcesFolder = "target/test-classes/"; // to run mvn test from cli or in jenkins
        }

        String log4jFile = resourcesFolder + "log4j2.xml"; // for junit tests in eclipse

        System.setProperty("log4j.configurationFile", log4jFile);
    }

    private static XMLConfiguration getConfig() {
        String file = "plugin_intranda_workflow_batch_imageqa.xml";
        XMLConfiguration config = new XMLConfiguration();
        config.setDelimiterParsingDisabled(true);
        try {
            config.load(resourcesFolder + file);
        } catch (ConfigurationException e) {
        }
        config.setReloadingStrategy(new FileChangedReloadingStrategy());
        return config;
    }

    @SuppressWarnings("rawtypes")
    @Before
    public void setUp() throws Exception {
        PowerMock.mockStatic(ConfigPlugins.class);
        EasyMock.expect(ConfigPlugins.getPluginConfig(EasyMock.anyString())).andReturn(getConfig()).anyTimes();

        PowerMock.mockStatic(ProcessManager.class);
        // TODO read mets file
        PowerMock.mockStatic(PropertyManager.class);
        // search for batches
        EasyMock.expect(ProcessManager.runSQL(EasyMock.anyString()))
                .andAnswer(
                        new IAnswer<List>() {
                            @SuppressWarnings("unchecked")
                            @Override
                            public List answer() throws Throwable {
                                String query = (String) EasyMock.getCurrentArguments()[0];
                                List answer = new ArrayList();
                                if (query.startsWith("SELECT COUNT")) {
                                    // first column: number of processes, second column: batch id
                                    String[] row1 = { "5", "1", "0", "1", null, "title", "" };
                                    String[] row2 = { "10", "2", "0", "0", null, "title", "" };
                                    String[] row3 = { "5", "3", "10", "0", null, "title", "" }; // only 5 of 10 reached the task
                                    answer.add(row1);
                                    answer.add(row2);
                                    answer.add(row3);
                                } else {
                                    String[] row1 = { "1", "5", "0", "0", "", "", "" };
                                    String[] row2 = { "2", "10", "0", "0", "", "", "" };
                                    String[] row3 = { "3", "10", "0", "0", "", "", "" };
                                    answer.add(row1);
                                    answer.add(row2);
                                    answer.add(row3);
                                }
                                return answer;
                            }
                        }

                )
                .anyTimes();

        Process proc = EasyMock.createMock(Process.class);
        EasyMock.expect(proc.getId()).andReturn(1).anyTimes();
        EasyMock.expect(proc.getTitel()).andReturn("title").anyTimes();
        EasyMock.expect(proc.getImagesOrigDirectory(false)).andReturn(resourcesFolder + "/tmp").anyTimes();
        EasyMock.expect(proc.getImagesDirectory()).andReturn(resourcesFolder + "/tmp").anyTimes();
        EasyMock.expect(proc.getThumbsDirectory()).andReturn(resourcesFolder + "/tmp").anyTimes();

        EasyMock.expect(ProcessManager.getProcessById(EasyMock.anyInt())).andReturn(proc).anyTimes();

        Prefs prefs = new Prefs();
        prefs.loadPrefs(resourcesFolder + "ruleset.xml");
        Fileformat fileformat = new MetsMods(prefs);
        fileformat.read(resourcesFolder + "/meta.xml");

        EasyMock.expect(proc.readMetadataFile()).andReturn(fileformat).anyTimes();
        PowerMock.mockStatic(MetadataManager.class);
        List<StringPair> metadataList = new ArrayList<>();
        metadataList.add(new StringPair("TitleDocMain", "label"));
        EasyMock.expect(MetadataManager.getMetadata(EasyMock.anyInt())).andReturn(metadataList).anyTimes();

        // initialize individual batches
        Batch b = new Batch();
        b.setBatchId(1);
        b.setBatchLabel("label");
        EasyMock.expect(ProcessManager.getBatchById(EasyMock.anyInt())).andReturn(b).anyTimes();
        EasyMock.replay(proc);

        List<GoobiProperty> gpl = new ArrayList<>();
        GoobiProperty gp = new GoobiProperty(PropertyOwnerType.BATCH);
        gp.setOwner(b);
        gp.setPropertyName("BatchPercentage");
        gp.setPropertyValue("20");
        gpl.add(gp);
        EasyMock.expect(PropertyManager.getPropertiesForObject(EasyMock.anyInt(), EasyMock.anyObject())).andReturn(gpl).anyTimes();
        PropertyManager.saveProperty(EasyMock.anyObject());

        PowerMock.mockStatic(StorageProvider.class);
        StorageProviderInterface spi = EasyMock.createMock(StorageProviderInterface.class);
        EasyMock.expect(StorageProvider.getInstance()).andReturn(spi).anyTimes();

        EasyMock.expect(spi.isFileExists(EasyMock.anyObject())).andReturn(false).anyTimes();
        EasyMock.replay(spi);
        PowerMock.replay(ProcessManager.class, ConfigPlugins.class, MetadataManager.class, StorageProvider.class, PropertyManager.class);
    }

    @Test
    public void testConstructor() throws IOException {
        BatchImageqaWorkflowPlugin fixture = new BatchImageqaWorkflowPlugin();
        assertNotNull(fixture);
    }

    @Test
    public void testAllBatches() throws IOException {
        BatchImageqaWorkflowPlugin fixture = new BatchImageqaWorkflowPlugin();
        List<QaBatch> batches = fixture.getAllBatches();
        assertEquals(2, batches.size());

    }

    @Test
    public void testDisplayType() {
        BatchImageqaWorkflowPlugin fixture = new BatchImageqaWorkflowPlugin();
        assertEquals("overview", fixture.getDisplayType());
        fixture.setDisplayType("other");
        assertEquals("other", fixture.getDisplayType());
    }

    @Test
    public void testCurrentBatch() {
        BatchImageqaWorkflowPlugin fixture = new BatchImageqaWorkflowPlugin();
        assertNull(fixture.getCurrentBatch());
        List<QaBatch> batches = fixture.getAllBatches();
        fixture.setCurrentBatch(batches.get(0));
        assertEquals("label", fixture.getCurrentBatch().getBatch().getBatchLabel());
    }

    @Test
    public void testOpenBatch() {
        BatchImageqaWorkflowPlugin fixture = new BatchImageqaWorkflowPlugin();
        fixture.setPercentage(50);
        List<QaBatch> batches = fixture.getAllBatches();
        fixture.setCurrentBatch(batches.get(0));
        fixture.openBatch();
        assertEquals(5, fixture.getNumberOfImagesToDisplay());
    }

    @Test
    public void testDisplayProcesses() {
        BatchImageqaWorkflowPlugin fixture = new BatchImageqaWorkflowPlugin();
        fixture.setPercentage(50);
        List<QaBatch> batches = fixture.getAllBatches();
        fixture.setCurrentBatch(batches.get(0));
        fixture.openBatch();
        assertEquals(1, fixture.getDisplayProcesses().size());
    }

    @Test
    public void testDisplayErrorReport() {
        BatchImageqaWorkflowPlugin fixture = new BatchImageqaWorkflowPlugin();
        fixture.setPercentage(50);
        List<QaBatch> batches = fixture.getAllBatches();
        fixture.setCurrentBatch(batches.get(0));
        fixture.openBatch();
        assertFalse(fixture.isDisplayErrorReport());
        fixture.getDisplayProcesses().get(0).setInvalid(true);
        assertTrue(fixture.isDisplayErrorReport());
    }

}
