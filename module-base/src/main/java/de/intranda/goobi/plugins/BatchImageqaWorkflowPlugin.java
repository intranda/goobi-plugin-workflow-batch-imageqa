package de.intranda.goobi.plugins;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.configuration.tree.xpath.XPathExpressionEngine;
import org.apache.commons.lang3.StringUtils;
import org.goobi.api.mail.SendMail;
import org.goobi.beans.GoobiProperty;
import org.goobi.beans.GoobiProperty.PropertyOwnerType;
import org.goobi.beans.JournalEntry;
import org.goobi.beans.JournalEntry.EntryType;
import org.goobi.beans.Process;
import org.goobi.beans.Step;
import org.goobi.beans.User;
import org.goobi.production.cli.helper.StringPair;
import org.goobi.production.enums.LogType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.goobi.production.plugin.interfaces.IWorkflowPlugin;

import de.intranda.goobi.plugins.model.DisplayProcess;
import de.intranda.goobi.plugins.model.ProcessOverview;
import de.intranda.goobi.plugins.model.QaBatch;
import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.FacesContextHelper;
import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.HelperSchritte;
import de.sub.goobi.helper.ScriptThreadWithoutHibernate;
import de.sub.goobi.helper.enums.HistoryEventType;
import de.sub.goobi.helper.enums.PropertyType;
import de.sub.goobi.helper.enums.StepEditType;
import de.sub.goobi.helper.enums.StepStatus;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.metadaten.Image;
import de.sub.goobi.persistence.managers.HistoryManager;
import de.sub.goobi.persistence.managers.JournalManager;
import de.sub.goobi.persistence.managers.MetadataManager;
import de.sub.goobi.persistence.managers.ProcessManager;
import de.sub.goobi.persistence.managers.QaPluginManager;
import de.sub.goobi.persistence.managers.StepManager;
import jakarta.faces.context.FacesContext;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
@Log4j2
public class BatchImageqaWorkflowPlugin implements IWorkflowPlugin, IPlugin {

    private static final long serialVersionUID = 6658238449519958476L;

    private SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Getter
    private String title = "intranda_workflow_batch_imageqa";

    private List<QaBatch> allBatches = null;

    private String qaStepName = "";
    private String errorStepName;
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
    private List<DisplayProcess> displayProcesses = new ArrayList<>();
    private List<String> metadataConfiguration;
    private String titleField;
    private List<String> metadataToCheck;

    @Getter
    @Setter
    private Image currentImage;

    @Getter
    @Setter
    private int pageNo = 0;
    private int numberOfProcessesPerPage = 10;

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
        errorStepName = config.getString("/errorStepName");

        percentage = config.getInt("/percentage", 10);

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
    }

    public List<QaBatch> getAllBatches() {
        if (config == null) {
            readConfig();
        }
        if (allBatches == null) {
            allBatches = QaPluginManager.getAllQaBatches(openTaskBatchQuery, qaStepName, metadataToCheck);
        }
        return allBatches;
    }

    public void reloadBatches() {
        allBatches = null;
    }

    @Override
    public void finalize() {
        finishBatch();
    }

    public void finishBatch() {

        // get all tasks
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
        displayType = "overview";
    }

    public void errorBatch() {
        for (DisplayProcess dp : displayProcesses) {
            if (dp.isInvalid()) {
                Step qaStep = null;
                Step stepToOpen = null;
                for (Step step : dp.getProcess().getSchritte()) {
                    if (qaStepName.equals(step.getTitel())) {
                        qaStep = step;
                    } else if (errorStepName.equals(step.getTitel())) {
                        stepToOpen = step;
                    }
                }

                if (qaStep != null && stepToOpen != null) {
                    Date now = new Date();
                    qaStep.setBearbeitungsstatusEnum(StepStatus.LOCKED);
                    qaStep.setEditTypeEnum(StepEditType.MANUAL_SINGLE);
                    qaStep.setPrioritaet(10);
                    qaStep.setBearbeitungszeitpunkt(now);
                    User user = Helper.getCurrentUser();
                    if (user != null) {
                        qaStep.setBearbeitungsbenutzer(user);
                    }
                    qaStep.setBearbeitungsbeginn(null);
                    try {
                        SendMail.getInstance().sendMailToAssignedUser(stepToOpen, StepStatus.ERROR);
                        stepToOpen.setBearbeitungsstatusEnum(StepStatus.ERROR);
                        stepToOpen.setCorrectionStep();
                        stepToOpen.setBearbeitungsende(now);
                        GoobiProperty se = new GoobiProperty(PropertyOwnerType.ERROR);

                        String messageText = dp.getErrorMessage();

                        se.setPropertyName(Helper.getTranslation("Korrektur notwendig"));
                        if (user == null) {
                            se.setPropertyValue("[" + this.formatter.format(now) + "] " + messageText);
                        } else {
                            se.setPropertyValue("[" + this.formatter.format(now) + ", " + user.getNachVorname() + "] " + messageText);
                        }
                        se.setType(PropertyType.MESSAGE_ERROR);
                        se.setCreationDate(now);
                        se.setOwner(stepToOpen);
                        String message = Helper.getTranslation("KorrekturFuer") + " " + stepToOpen.getTitel() + ": " + messageText;
                        String username;
                        if (user != null) {
                            username = user.getNachVorname();
                        } else {
                            username = "-";
                        }
                        JournalEntry logEntry =
                                new JournalEntry(qaStep.getProzess().getId(), now, username, LogType.ERROR, message,
                                        EntryType.PROCESS);
                        JournalManager.saveJournalEntry(logEntry);

                        stepToOpen.getProperties().add(se);
                        StepManager.saveStep(stepToOpen);
                        HistoryManager.addHistory(now, stepToOpen.getReihenfolge().doubleValue(), stepToOpen.getTitel(),
                                HistoryEventType.stepError.getValue(),
                                stepToOpen.getProzess().getId());

                        List<Step> stepsBetween =
                                StepManager.getSteps("Reihenfolge desc",
                                        " schritte.prozesseID = " + qaStep.getProzess().getId() + " AND Reihenfolge <= "
                                                + qaStep.getReihenfolge() + "  AND Reihenfolge > " + stepToOpen.getReihenfolge(),
                                        0, Integer.MAX_VALUE, null);

                        for (Step step : stepsBetween) {
                            if (!StepStatus.DEACTIVATED.equals(step.getBearbeitungsstatusEnum())) {
                                step.setBearbeitungsstatusEnum(StepStatus.LOCKED);
                            }
                            step.setCorrectionStep();
                            step.setBearbeitungsende(null);
                            GoobiProperty seg = new GoobiProperty(PropertyOwnerType.ERROR);
                            seg.setPropertyName(Helper.getTranslation("Korrektur notwendig"));
                            seg.setPropertyValue(Helper.getTranslation("KorrekturFuer") + " " + stepToOpen.getTitel() + ": " + messageText);
                            seg.setOwner(step);
                            seg.setType(PropertyType.MESSAGE_IMPORTANT);
                            seg.setCreationDate(new Date());
                            step.getProperties().add(seg);
                            StepManager.saveStep(step);
                        }
                        if (stepToOpen.isTypAutomatisch()) {
                            ScriptThreadWithoutHibernate myThread = new ScriptThreadWithoutHibernate(stepToOpen);
                            myThread.startOrPutToQueue();
                        }

                        StepManager.saveStep(qaStep);
                        ProcessManager.saveProcessInformation(qaStep.getProzess());
                    } catch (DAOException e) {
                        log.error(e);
                    }
                }
            }

        }
        allBatches = null;
        displayType = "overview";
    }

    public void openBatch() {
        double totalPages = currentBatch.getNumberOfPages();
        if (percentage < 1) {
            percentage = 1;
        }
        numberOfImagesToDisplay = 0;
        double imagesToDisplay = totalPages * percentage / 100;
        processDisplayList = new ArrayList<>();

        for (ProcessOverview entry : currentBatch.getProcesses()) {
            if (entry.isMetadataAvailable() || entry.isPriorityStep() || (numberOfImagesToDisplay < imagesToDisplay)) {
                String processid = entry.getProcessid();
                processDisplayList.add(processid);
                int numberOfPages = entry.getNumberOfPages();
                numberOfImagesToDisplay = numberOfImagesToDisplay + numberOfPages;
            }
        }
        displayProcesses.clear();
        generateProcessList();
    }

    public List<DisplayProcess> generateProcessList() {

        if (displayProcesses.isEmpty() && !processDisplayList.isEmpty()) {
            for (String processId : processDisplayList) {
                Process process = ProcessManager.getProcessById(Integer.parseInt(processId));

                List<StringPair> processMetadata = MetadataManager.getMetadata(process.getId());

                DisplayProcess dp = new DisplayProcess(process, thumbnailSize);
                displayProcesses.add(dp);

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
        return displayProcesses;
    }

    /*
     * Pagination
     * 
     */

    public List<DisplayProcess> getDisplayProcesses() {

        List<DisplayProcess> subList;
        if (displayProcesses.size() > (pageNo * numberOfProcessesPerPage) + numberOfProcessesPerPage) {
            subList = displayProcesses.subList(pageNo * numberOfProcessesPerPage, (pageNo * numberOfProcessesPerPage) + numberOfProcessesPerPage);
        } else {
            // Sometimes pageNo is not zero here, although we only have 20 images or so.
            // This is a quick fix and we should find out why pageNo is not zero in some cases
            int startIdx = pageNo * numberOfProcessesPerPage;
            if (startIdx > displayProcesses.size()) {
                startIdx = Math.max(0, displayProcesses.size() - numberOfProcessesPerPage);
            }
            subList = displayProcesses.subList(startIdx, displayProcesses.size());
        }
        return subList;

    }

    public boolean isDisplayPaginator() {
        return displayProcesses.size() > numberOfProcessesPerPage;
    }

    public boolean isHasPreviousPages() {
        return pageNo > 0;
    }

    public void moveToFirstPage() {
        pageNo = 0;
    }

    public void moveToPreviousPage() {
        pageNo = pageNo - 1;
    }

    public int getLastPageNumber() {
        int ret = displayProcesses.size() / numberOfProcessesPerPage;
        if (displayProcesses.size() % numberOfProcessesPerPage == 0) {
            ret--;
        }
        return ret;
    }

    public boolean isLastPage() {
        return this.pageNo >= getLastPageNumber();
    }

    public boolean isHasNextPages() {
        return !isLastPage();
    }

    public void moveToNextPage() {
        if (!isLastPage()) {
            pageNo++;
        }
    }

    public void moveToLastPage() {
        if (pageNo != getLastPageNumber()) {
            pageNo = getLastPageNumber();
        }
    }

    /*
     * error report
     * 
     */

    public boolean isDisplayErrorReport() {
        for (DisplayProcess dp : displayProcesses) {
            if (dp.isInvalid()) {
                return true;
            }
        }
        return false;
    }

    public List<StringPair> getErrorMessage() {
        List<StringPair> errors = new ArrayList<>();
        for (DisplayProcess dp : displayProcesses) {
            if (dp.isInvalid()) {
                errors.add(new StringPair(dp.getTitle(), dp.getErrorMessage() == null ? "" : dp.getErrorMessage()));
            }
        }
        return errors;
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
        for (DisplayProcess dp : displayProcesses) {
            csv.append("\"" + dp.getTitle() + "\"");
            csv.append(",");
            csv.append("\"");
            csv.append(dp.getErrorMessage() == null ? "" : dp.getErrorMessage());
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
        }
    }
}
