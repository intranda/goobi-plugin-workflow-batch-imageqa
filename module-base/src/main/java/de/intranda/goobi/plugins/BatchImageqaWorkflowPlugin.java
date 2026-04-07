package de.intranda.goobi.plugins;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import de.intranda.goobi.plugins.model.*;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang3.StringUtils;
import org.goobi.beans.GoobiProperty;
import org.goobi.beans.GoobiProperty.PropertyOwnerType;
import org.goobi.beans.Process;
import org.goobi.beans.Step;
import org.goobi.production.cli.helper.StringPair;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IWorkflowPlugin;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.BeanHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.FacesContextHelper;
import de.sub.goobi.helper.HelperSchritte;
import de.sub.goobi.helper.ScriptThreadWithoutHibernate;
import de.sub.goobi.helper.enums.StepEditType;
import de.sub.goobi.helper.enums.StepStatus;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.metadaten.Image;
import de.sub.goobi.persistence.managers.DatabaseVersion;
import de.sub.goobi.persistence.managers.ProcessManager;
import de.sub.goobi.persistence.managers.PropertyManager;
import de.sub.goobi.persistence.managers.QaPluginManager;
import de.sub.goobi.persistence.managers.StepManager;
import jakarta.faces.context.FacesContext;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataGroup;
import ugh.dl.Person;
import ugh.exceptions.UGHException;

@PluginImplementation
@Log4j2
public class BatchImageqaWorkflowPlugin implements IWorkflowPlugin {

    private static final long serialVersionUID = 6658238449519958476L;

    @Getter
    private String title = "intranda_workflow_batch_imageqa";

    @Getter
    private final String roleBase = "Plugin_workflow_batch_imageqa";
    @Getter
    private final String roleReject = "Plugin_workflow_batch_imageqa_reject";
    @Getter
    private final String roleFinish = "Plugin_workflow_batch_imageqa_finish";
    @Getter
    private final String roleViewFinish = "Plugin_workflow_batch_imageqa_view_finish";
    @Getter
    private final String roleEditFinish = "Plugin_workflow_batch_imageqa_edit_finish";
    @Getter
    private final String roleViewError = "Plugin_workflow_batch_imageqa_view_error";
    @Getter
    private final String roleEditError = "Plugin_workflow_batch_imageqa_edit_error";
    @Getter
    private final String roleViewInProcess = "Plugin_workflow_batch_imageqa_view_in_progress";
    @Getter
    private final String roleEditInProcess = "Plugin_workflow_batch_imageqa_edit_in_progress";
    @Getter
    private final String roleSampleSize = "Plugin_workflow_batch_imageqa_admin_sample_size";
    @Getter
    private final String roleViewCSV = "Plugin_workflow_batch_imageqa_view_csv";

    private List<QaBatch> allBatches = null;

    private String qaStepName = "";
    private String openTaskBatchQuery;

    private int defaultPercentage;

    private XMLConfiguration config = null;

    @Getter
    @Setter
    private QaBatch currentBatch;

    @Getter
    private int thumbnailSize = 200;

    private List<ProcessOverview> processDisplayList;
    @Getter
    private List<DisplayProcess> displayProcesses = new ArrayList<>();
    private List<String> metadataConfiguration;
    private String titleField;
    private List<String> metadataToCheck;
    private String inactiveProjectName;

    private Process inactiveProcessTemplate = null;

    @Getter
    @Setter
    private Image currentImage;

    private int numberOfProcessesPerPage = 10;

    @Getter
    @Setter
    private DisplayProcess currentProcess;

    @Getter
    @Setter
    private DetailScreenType detailScreenType = DetailScreenType.OVERVIEW;

    @Getter
    @Setter
    private boolean detailViewReadOnly = false;

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

    }

    private void readConfig() {
        config = ConfigPlugins.getPluginConfig(title);
        config.setExpressionEngine(new XPathExpressionEngine());

        qaStepName = config.getString("/qaTaskName");
        inactiveProjectName = config.getString("/inactiveProject");

        defaultPercentage = config.getInt("/percentage", 10);

        numberOfProcessesPerPage = config.getInt("/numberOfProcessesPerPage", 10);
        thumbnailSize = config.getInt("/thumbnailSize", 200);

        metadataConfiguration = Arrays.asList(config.getStringArray("/metadata"));

        metadataToCheck = Arrays.asList(config.getStringArray("/metadataToCheck"));

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

        String templateName = config.getString("/inactiveProcessTemplate");
        if (StringUtils.isNotBlank(templateName)) {
            inactiveProcessTemplate = ProcessManager.getProcessByExactTitle(templateName);
        }
    }

    public List<QaBatch> getAllBatches() {
        if (config == null) {
            readConfig();
        }
        if (allBatches == null) {
            allBatches = QaPluginManager.getAllQaBatches(openTaskBatchQuery, qaStepName, metadataToCheck, inactiveProjectName, defaultPercentage);
        }
        return allBatches;
    }

    public void reloadBatches() {
        allBatches = null;
    }

    public void loadFinishedProcessesEdit() {
        detailViewReadOnly = false;
        loadFinishedProcesses();
    }

    public void loadFinishedProcessesReadOnly() {
        detailViewReadOnly = true;
        loadFinishedProcesses();
    }

    private void loadFinishedProcesses() {
        detailScreenType = DetailScreenType.FINISH;
        processDisplayList = new ArrayList<>();
        for (ProcessOverview entry : currentBatch.getProcesses()) {
            if ("accepted".equals(entry.getProcessStatus()) || "error".equals(entry.getProcessStatus())) {
                processDisplayList.add(entry);
            }
        }
        displayProcesses.clear();
        generateProcessList();
    }

    public void loadErrorProcessesEdit() {
        detailViewReadOnly = false;
        loadErrorProcesses();
    }

    public void loadErrorProcessesReadOnly() {
        detailViewReadOnly = true;
        loadErrorProcesses();
    }

    private void loadErrorProcesses() {
        detailScreenType = DetailScreenType.ERROR;

        processDisplayList = new ArrayList<>();
        for (ProcessOverview entry : currentBatch.getProcesses()) {
            if ("error".equals(entry.getProcessStatus())) {
                processDisplayList.add(entry);
            }
        }
        displayProcesses.clear();
        generateProcessList();
    }

    public void loadInWorkProcessesEdit() {
        detailViewReadOnly = false;
        loadInWorkProcesses();
    }

    public void loadInWorkProcessesReadOnly() {
        detailViewReadOnly = true;
        loadInWorkProcesses();
    }

    private void loadInWorkProcesses() {
        detailScreenType = DetailScreenType.IN_PROGRESS;
        processDisplayList = new ArrayList<>();
        for (ProcessOverview entry : currentBatch.getProcesses()) {
            if ("in progress".equals(entry.getProcessStatus())) {
                processDisplayList.add(entry);
            }
        }
        displayProcesses.clear();
        generateProcessList();
    }

    public void continueBatch() {
        // mark new processed images as accepted
        for (DisplayProcess dp : displayProcesses) {
            // update status property
            try {
                if (dp.getValidity() == ProcessValidationState.INVALID) {
                    persistInvalidToDatabase(dp);
                } else if (dp.getValidity() == ProcessValidationState.VALID){
                    DatabaseVersion.runSql(
                            "update properties set property_value ='accepted' where property_name = 'QA-Status' and object_type='process' and object_id = "
                                    + dp.getProcess().getId());
                }
            } catch (SQLException e) {
                log.error("Error set Accept in property_value: ", e);
            }

            for (ProcessOverview entry : currentBatch.getProcesses()) {
                if (entry.getProcessid().equals(String.valueOf(dp.getProcess().getId()))) {
                    entry.setProcessStatus(dp.getValidity() == ProcessValidationState.INVALID ? "error" : "accepted");
                    break;
                }
            }
        }

        currentBatch.calculateProgress();

        double numberOfFinishedPages =
                currentBatch.getFinishedNumberOfPages() + currentBatch.getNumberOfPagesInProcess() + currentBatch.getErrorNumberOfPages();
        double imagesToDisplay = currentBatch.getThresholdPages();

        if (numberOfFinishedPages < imagesToDisplay) {
            // threshold not exceeded, get new set of images
            openBatch();
        } else {
            // batch is finished, move to overview screen
            allBatches = null;
            detailScreenType = DetailScreenType.OVERVIEW;
        }

    }

    public void finalizeBatch() {

        // if all processes are valid: close steps and continue to overview screen
        List<Step> steps = QaPluginManager.getStepsForBatch(qaStepName, currentBatch.getBatch().getBatchId());

        for (DisplayProcess dp : displayProcesses) {
            for (Step step : dp.getProcess().getSchritte()) {
                if (qaStepName.equals(step.getTitel())) {
                    steps.add(step);
                }
            }
        }
        // for each task call close step
        HelperSchritte helper = new HelperSchritte();
        for (Step step : steps) {
            helper.CloseStepObjectAutomatic(step);
        }

        allBatches = null;
        detailScreenType = DetailScreenType.OVERVIEW;
    }

    public void errorBatch() {

        StringBuilder ids = new StringBuilder();
        for (ProcessOverview po : currentBatch.getProcesses()) {
            if (!ids.isEmpty()) {
                ids.append(", ");
            }
            ids.append(po.getProcessid());
        }

        StringBuilder query = new StringBuilder();
        query.append("UPDATE prozesse SET projekteid = (SELECT ProjekteID FROM projekte WHERE titel = '")
                .append(inactiveProjectName)
                .append("') WHERE prozesseId in (")
                .append(ids.toString())
                .append(");");
        try {
            DatabaseVersion.runSql(query.toString());
        } catch (SQLException e) {
            log.error("Error update Prozesse: ", e);
        }

        allBatches = null;
        detailScreenType = DetailScreenType.OVERVIEW;

        // change workflow to a 6 weeks delay and deletion step
        if (inactiveProcessTemplate != null) {
            BeanHelper helper = new BeanHelper();
            for (ProcessOverview po : currentBatch.getProcesses()) {
                Process processToChange = ProcessManager.getProcessById(Integer.parseInt(po.getProcessid()));
                helper.changeProcessTemplate(processToChange, inactiveProcessTemplate);

                // find first open/inwork/locked step. Execute step, if it is an automatic step
                Step openStep = null;
                for (Step step : processToChange.getSchritte()) {
                    if (openStep == null) {
                        switch (step.getBearbeitungsstatusEnum()) {
                            case INWORK, LOCKED, OPEN:
                                if (step.isTypAutomatisch()) {
                                    step.setBearbeitungsbeginn(new Date());
                                    step.setBearbeitungsbenutzer(null);
                                    step.setBearbeitungsstatusEnum(StepStatus.INWORK);
                                    step.setEditTypeEnum(StepEditType.AUTOMATIC);

                                    try {
                                        StepManager.saveStep(step);
                                    } catch (DAOException e) {
                                        log.error("Errors set Accept in property_value: ", e);
                                    }
                                    openStep = step;
                                }
                            default:
                        }
                    }
                }
                ScriptThreadWithoutHibernate myThread = new ScriptThreadWithoutHibernate(openStep);
                myThread.startOrPutToQueue();
            }
        }

    }

    public void openBatch() {
        this.detailViewReadOnly = false;
        int percentage = currentBatch.getPercentage();
        // check if batch has a percentage property

        double numberOfFinishedPages = currentBatch.getFinishedNumberOfPages() + currentBatch.getErrorNumberOfPages();
        double imagesToDisplay = currentBatch.getThresholdPages();

        if (numberOfFinishedPages > imagesToDisplay) {
            imagesToDisplay = currentBatch.getTotalNumberOfPages();
        }
        if (percentage < 1) {
            percentage = 1;
        }

        processDisplayList = new ArrayList<>();

        for (ProcessOverview entry : currentBatch.getProcesses()) {

            if (entry.isMetadataAvailable() || entry.isPriorityStep() || (numberOfFinishedPages < imagesToDisplay)) {
                // exclude already processed images
                if (StringUtils.isBlank(entry.getProcessStatus()) && processDisplayList.size() < numberOfProcessesPerPage) {
                    numberOfFinishedPages = numberOfFinishedPages + entry.getNumberOfPages();
                    processDisplayList.add(entry);
                    entry.setProcessStatus("in progress");
                    GoobiProperty gp = new GoobiProperty(PropertyOwnerType.PROCESS);
                    gp.setPropertyName("QA-Status");
                    gp.setPropertyValue("in progress");
                    gp.setObjectId(Integer.valueOf(entry.getProcessid()));
                    PropertyManager.saveProperty(gp);
                }
            }
        }
        if (processDisplayList.isEmpty()) {
            // all processes are processed or currently in progress by someone else, stay on overview page
            detailScreenType = DetailScreenType.OVERVIEW;
            return;
        }

        detailScreenType = DetailScreenType.NORMAL;
        displayProcesses.clear();
        generateProcessList();
    }

    public List<DisplayProcess> generateProcessList() {

        if (displayProcesses.isEmpty() && !processDisplayList.isEmpty()) {
            for (ProcessOverview entry : processDisplayList) {
                Process process = ProcessManager.getProcessById(Integer.parseInt(entry.getProcessid()));

                DisplayProcess dp = new DisplayProcess(process, thumbnailSize, entry);
                dp.setMetadataStep(entry.isMetadataAvailable());
                dp.setErrorStep("error".equals(entry.getProcessStatus()));
                displayProcesses.add(dp);

                // use process title as default, if no field is configured or value is missing
                String title = process.getTitel();
                List<StringPair> displayFields = new ArrayList<>();
                try {
                    Fileformat ff = process.readMetadataFile();
                    DocStruct logical = ff.getDigitalDocument().getLogicalDocStruct();
                    if (logical.getType().isAnchor()) {
                        logical = logical.getAllChildren().get(0);
                    }
                    for (Metadata md : logical.getAllMetadata()) {
                        if (md.getType().getName().equals(titleField)) {
                            title = md.getValue();
                        }
                    }
                    // get order and diplayable fields from configuration
                    for (String metadataName : metadataConfiguration) {
                        StringBuilder values = new StringBuilder();
                        // first case: metadata
                        if (logical.getAllMetadata() != null) {
                            for (Metadata md : logical.getAllMetadata()) {
                                if (md.getType().getName().equals(metadataName)) {
                                    if (!values.isEmpty()) {
                                        values.append("; ");
                                    }
                                    values.append(md.getValue());
                                }
                            }
                        }

                        // second case: person
                        if (logical.getAllPersons() != null) {
                            for (Person p : logical.getAllPersons()) {
                                if (p.getType().getName().equals(metadataName)) {
                                    if (!values.isEmpty()) {
                                        values.append("; ");
                                    }
                                    if (StringUtils.isNotBlank(p.getLastname())) {
                                        values.append(p.getLastname());
                                    }
                                    if (StringUtils.isNotBlank(p.getFirstname())) {
                                        if (StringUtils.isNotBlank(p.getLastname())) {
                                            values.append(", ");
                                        }
                                        values.append(p.getFirstname());
                                    }
                                }
                            }
                        }

                        // third case: group
                        if (logical.getAllMetadataGroups() != null) {
                            for (MetadataGroup mg : logical.getAllMetadataGroups()) {
                                if (mg.getType().getName().equals(metadataName)) {
                                    if (!values.isEmpty()) {
                                        values.append("<br />");
                                    }
                                    for (Metadata md : mg.getMetadataList()) {
                                        if (StringUtils.isNotBlank(md.getValue())) {
                                            if (!values.isEmpty()) {
                                                values.append("<br />");
                                            }
                                            values.append(md.getType().getLanguage("de"));
                                            values.append(": ").append(md.getValue());
                                        }
                                    }
                                    for (MetadataGroup subGroup : mg.getAllMetadataGroups()) {
                                        StringBuilder subValues = new StringBuilder();
                                        for (Metadata md : subGroup.getMetadataList()) {
                                            if (StringUtils.isNotBlank(md.getValue())) {
                                                subValues.append("<br />");
                                                subValues.append(md.getType().getLanguage("de"));
                                                subValues.append(": ").append(md.getValue());
                                            }
                                        }

                                        if (!subValues.isEmpty()) {
                                            values.append("<br />");
                                            values.append(subValues.toString());
                                        }
                                    }
                                }
                            }
                        }

                        if (!values.isEmpty()) {
                            StringPair field = new StringPair(metadataName, values.toString());
                            displayFields.add(field);
                        }
                    }

                } catch (UGHException | IOException | SwapException e) {
                    log.error("Error generating Process List: ", e);
                }
                dp.setTitle(title);

                dp.setMetadata(displayFields);
            }

        }
        return displayProcesses;
    }

    /*
     * error report
     * 
     */

    public void setAllProcessesValid() {
        for (DisplayProcess dp : displayProcesses) {
            dp.setValidityValid();
        }
    }

    public boolean isAllProcessesMarked() {
        for (DisplayProcess dp : displayProcesses) {
            if (dp.getValidity() == ProcessValidationState.NOT_PROCESSED) {
                return false;
            }
        }
        return true;
    }

    public boolean isDisplayErrorReport() {
        for (DisplayProcess dp : displayProcesses) {
            if (dp.getValidity() == ProcessValidationState.INVALID) {
                return true;
            }
        }
        return false;
    }

    public List<StringPair> getErrorMessage() {
        List<StringPair> errors = new ArrayList<>();
        for (ProcessOverview po : processDisplayList) {
            if (po.getErrorMessage() != null) {
                errors.add(new StringPair(po.getProcessTitle(), po.getErrorMessage()));
            }
        }
        return errors;
    }

    public void reloadCurrentBatch() {
        if (currentBatch != null) {
            currentBatch.reload(qaStepName, metadataToCheck);
        }
    }

    public void generateCSV() {
        StringBuilder csv = new StringBuilder();

        String batchName = currentBatch.getBatch().getBatchName();
        if (StringUtils.isBlank(batchName)) {
            batchName = "report_" + currentBatch.getBatch().getBatchId();
        }
        batchName = batchName.replaceAll("\\W", "_");
        String reportName = batchName + ".csv";
        csv.append("Vorgang, Fehler");
        csv.append("\n");

        List<ProcessOverview> errorProcesses = new ArrayList<>();

        for (ProcessOverview po : currentBatch.getProcesses()) {
            if ("error".equals(po.getProcessStatus())) {
                errorProcesses.add(po);
            }
        }

        Collections.sort(errorProcesses);

        for (ProcessOverview po : errorProcesses) {
            csv.append("\"" + po.getProcessTitle() + "\"");
            csv.append(",");
            csv.append("\"");
            csv.append(po.getErrorMessage() == null ? "" : po.getErrorMessage());
            csv.append("\"");
            csv.append("\n");
        }

        FacesContext facesContext = FacesContextHelper.getCurrentFacesContext();
        try {
            if (!facesContext.getResponseComplete()) {
                HttpServletResponse response = (HttpServletResponse) facesContext.getExternalContext().getResponse();
                ServletContext servletContext = (ServletContext) facesContext.getExternalContext().getContext();
                String contentType = servletContext.getMimeType(reportName) + "; charset=UTF-8";
                response.setContentType(contentType);
                response.setHeader("Content-Disposition", "attachment;filename=\"" + reportName + "\"");

                PrintWriter pw = response.getWriter();
                pw.write(csv.toString());
                pw.close();
                facesContext.responseComplete();
            }
        } catch (IOException e) {
            log.error("Error writing CSV-File: ", e);
            Helper.setFehlerMeldung("plugin_workflow_batches_csv_error", e);
        }
    }

    public int getPercentage() {
        if (currentBatch == null) {
            return 0;
        }
        return Integer.parseInt(currentBatch.getPercentageProperty().getPropertyValue());
    }

    public void setPercentage(int percentage) {
        if (currentBatch.getPercentageProperty() != null) {
            currentBatch.getPercentageProperty().setPropertyValue("" + percentage);
            PropertyManager.saveProperty(currentBatch.getPercentageProperty());
        }
    }

    public void savePercentage() {
        if (currentBatch != null) {
            currentBatch.reload(qaStepName, metadataToCheck);
        }
        allBatches = null;
    }

    public void abortEdition() {
        // reset all processes from current view, so they can be picked up again
        for (ProcessOverview po : processDisplayList) {
            // remove status property
            try {
                DatabaseVersion.runSql(
                        "delete from properties where property_name in('QA-Status', 'QA-Note') and object_type='process' and object_id = "
                                + po.getProcessid());
            } catch (SQLException e) {
                log.error("Error SQL delete properties : ", e);
            }
            // set status back to open
            po.setProcessStatus("");
        }

        allBatches = null;
        detailScreenType = DetailScreenType.OVERVIEW;
    }

    public void cancelEdition() {
        allBatches = null;
        detailScreenType = DetailScreenType.OVERVIEW;
    }

    public void saveCurrentPageAndExit() {
        for (DisplayProcess dp : displayProcesses) {
            // update status property
            try {
                if (dp.getValidity() == ProcessValidationState.INVALID) {
                    persistInvalidToDatabase(dp);
                } else if (dp.getValidity() == ProcessValidationState.VALID) {
                    DatabaseVersion.runSql(
                            "update properties set property_value ='accepted' where property_name = 'QA-Status' and object_type='process' and object_id = "
                                    + dp.getProcess().getId());

                    DatabaseVersion.runSql(
                            "delete from properties where property_name = 'QA-Note' and object_type='process' and object_id = "
                                    + dp.getProcess().getId());
                }
            } catch (SQLException e) {
                log.error("Error sql save current page: ", e);
            }
        }

        cancelEdition();
    }

    private static void persistInvalidToDatabase(DisplayProcess dp) throws SQLException {
        DatabaseVersion.runSql(
                "update properties set property_value ='error' where property_name = 'QA-Status' and object_type='process' and object_id = "
                        + dp.getProcess().getId());

        //  store error message as property
        String errorMessage = dp.getProcessOverview().getErrorMessage();
        GoobiProperty errorProperty = null;
        for (GoobiProperty gp : dp.getProcess().getProperties()) {
            if ("QA-Note".equals(gp.getPropertyName())) {
                errorProperty = gp;
                break;
            }
        }
        if (errorProperty == null) {
            errorProperty = new GoobiProperty(PropertyOwnerType.PROCESS);
            errorProperty.setOwner(dp.getProcess());
            errorProperty.setPropertyName("QA-Note");
        }
        errorProperty.setPropertyValue(errorMessage);
        PropertyManager.saveProperty(errorProperty);
    }

}
