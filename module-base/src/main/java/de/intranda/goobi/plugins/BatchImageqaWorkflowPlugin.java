package de.intranda.goobi.plugins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.goobi.beans.Batch;
import org.goobi.beans.Process;
import org.goobi.production.cli.helper.StringPair;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.goobi.production.plugin.interfaces.IWorkflowPlugin;

import de.intranda.goobi.plugins.model.DisplayProcess;
import de.intranda.goobi.plugins.model.QaBatch;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.persistence.managers.MetadataManager;
import de.sub.goobi.persistence.managers.ProcessManager;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
@Log4j2
public class BatchImageqaWorkflowPlugin implements IWorkflowPlugin, IPlugin {

    private static final long serialVersionUID = 6658238449519958476L;

    @Getter
    private String title = "intranda_workflow_batch_imageqa";

    private List<QaBatch> allBatches = null;

    private String qaStepName = "";

    private String openTaskBatchQuery;

    @Getter
    @Setter
    private int percentage;
    @Getter
    private int numberOfImagesToDisplay = 0;

    private XMLConfiguration config = null;

    @Getter
    @Setter
    private String displayType = "overview";

    @Getter
    @Setter
    private QaBatch currentBatch;

    @Getter
    private int thumbnailSize = 200;

    private List<String> processDisplayList;
    private List<DisplayProcess> displayProcess = new ArrayList<>();
    private List<String> metadataConfiguration;
    private String titleField;

    @Override
    public PluginType getType() {
        return PluginType.Workflow;
    }

    @Override
    public String getGui() {
        return "/uii/plugin_workflow_batch_imageqa.xhtml";
    }

    /**
     * Constructor
     */
    public BatchImageqaWorkflowPlugin() {
        log.trace("BatchImageqa workflow plugin started");
    }

    private void readConfig() {
        config = ConfigPlugins.getPluginConfig(title);
        config.setExpressionEngine(new XPathExpressionEngine());

        qaStepName = config.getString("/qaTaskName");
        percentage = config.getInt("/percentage", 10);

        thumbnailSize = config.getInt("/thumbnailSize", 200);

        metadataConfiguration = Arrays.asList(config.getStringArray("/metadata"));
        titleField = config.getString("/titleField", "");
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(DISTINCT p.prozesseid), p.batchid ");
        sql.append("FROM prozesse p ");
        sql.append("JOIN schritte s ON s.ProzesseID = p.ProzesseID ");
        sql.append("WHERE p.batchid IS NOT NULL ");
        sql.append("AND s.titel = '");
        sql.append(qaStepName);
        sql.append("' ");
        sql.append("AND s.Bearbeitungsstatus in (1,2,4) ");
        sql.append("GROUP BY p.batchid; ");
        openTaskBatchQuery = sql.toString();
    }

    public List<QaBatch> getAllBatches() {
        if (config == null) {
            readConfig();
        }

        if (allBatches == null) {
            // find all batches with open qa steps
            @SuppressWarnings("rawtypes")
            List result = ProcessManager.runSQL(openTaskBatchQuery);

            StringBuilder idList = new StringBuilder();
            Map<String, String> batches = new HashMap<>();
            for (Object obj : result) {
                Object[] objArr = (Object[]) obj;
                String numberOfProcesses = objArr[0].toString();
                String batchId = objArr[1].toString();
                batches.put(batchId, numberOfProcesses);
                if (!idList.isEmpty()) {
                    idList.append(", ");
                }
                idList.append(batchId);
            }
            if (!idList.isEmpty()) {
                allBatches = new ArrayList<>();
                // compare the number of processes with the total number in each batch

                String completeBatchQuery =
                        "select batchid, count(prozesseid) from prozesse where batchid in (" + idList.toString() + ") GROUP BY batchid;";

                result = ProcessManager.runSQL(completeBatchQuery);

                // if numbers are equal (all tasks reached the step), load batch, add to list
                for (Object obj : result) {
                    Object[] objArr = (Object[]) obj;
                    String batchId = objArr[0].toString();
                    String totalNumberOfProcesses = objArr[1].toString();
                    String currentNumber = batches.get(batchId);
                    if (totalNumberOfProcesses.equals(currentNumber)) {
                        Batch b = ProcessManager.getBatchById(Integer.parseInt(batchId));
                        allBatches.add(new QaBatch(b));
                    }

                }

            }

        }
        return allBatches;
    }

    public void reloadBatches() {
        allBatches = null;
    }

    public void openBatch() {
        double totalPages = currentBatch.getNumberOfPages();
        if (percentage < 1) {
            percentage = 1;
        }
        numberOfImagesToDisplay = 0;
        double imagesToDisplay = totalPages * percentage / 100;
        processDisplayList = new ArrayList<>();

        for (Entry<String, Integer> entry : currentBatch.getProceses().entrySet()) {
            String processid = entry.getKey();
            processDisplayList.add(processid);
            int numberOfPages = entry.getValue();
            numberOfImagesToDisplay = numberOfImagesToDisplay + numberOfPages;
            if (numberOfImagesToDisplay >= imagesToDisplay) {
                break;
            }
        }
        displayProcess.clear();
        // load processes
        // display configured metadata + title
        // show images
    }

    public List<DisplayProcess> getDisplayProcesses() {

        // TODO sub list with pagination
        if (displayProcess.isEmpty() && !processDisplayList.isEmpty()) {
            for (String processId : processDisplayList) {
                Process process = ProcessManager.getProcessById(Integer.parseInt(processId));

                List<StringPair> processMetadata = MetadataManager.getMetadata(process.getId());

                DisplayProcess dp = new DisplayProcess(process, thumbnailSize);
                displayProcess.add(dp);

                // use process title as default, if no field is configured or value is missing
                String title = process.getTitel();
                for (StringPair sp : processMetadata) {
                    if (sp.getOne().equals(titleField)) {
                        title = sp.getTwo();
                    }
                }
                dp.setTitle(title);

                // get order and diplayable fields from configuration
                List<StringPair> displayFields = new ArrayList<>();
                for (String metadataName : metadataConfiguration) {
                    StringBuilder values = new StringBuilder();

                    for (StringPair sp : processMetadata) {
                        if (sp.getOne().equals(metadataName)) {
                            if (!values.isEmpty()) {
                                values.append("; ");
                            }
                            values.append(sp.getTwo());
                        }
                    }
                    StringPair field = new StringPair(metadataName, values.toString());
                    displayFields.add(field);
                }

                dp.setMetadata(displayFields);

            }

        }

        return displayProcess;
    }

}
