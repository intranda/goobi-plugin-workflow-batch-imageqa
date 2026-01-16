package de.intranda.goobi.plugins.model;

import java.util.List;

import org.goobi.beans.Batch;
import org.goobi.beans.GoobiProperty;
import org.goobi.beans.GoobiProperty.PropertyOwnerType;

import de.sub.goobi.helper.Helper;
import de.sub.goobi.persistence.managers.PropertyManager;
import de.sub.goobi.persistence.managers.QaPluginManager;
import lombok.Getter;
import lombok.Setter;

public class QaBatch {

    @Getter
    private Batch batch;

    @Getter
    private List<ProcessOverview> processes;

    @Getter
    private long totalNumberOfPages = 0;
    @Getter
    private long numberOfPagesToProcess = 0;

    @Getter
    private long finishedNumberOfPages = 0;
    @Getter
    private long errorNumberOfPages = 0;
    @Getter
    private long numberOfPagesInProcess = 0;
    @Getter
    private int percentage;

    @Getter
    @Setter
    private GoobiProperty percentageProperty = null;

    public QaBatch(Batch batch, String stepTitle, List<String> metadataToCheck, int defaultPercentage) {
        this.batch = batch;
        processes = QaPluginManager.getProcesses(stepTitle, batch.getBatchId(), metadataToCheck);
        calculateProgress();

        percentageProperty = null;
        for (GoobiProperty gp : batch.getProperties()) {
            // if yes, use this value
            if ("BatchPercentage".equals(gp.getPropertyName())) {
                percentageProperty = gp;
            }
        }
        // otherwise create new property with default value
        if (percentageProperty == null) {
            percentageProperty = new GoobiProperty(PropertyOwnerType.BATCH);
            percentageProperty.setOwner(batch);
            percentageProperty.setPropertyName("BatchPercentage");
            percentageProperty.setPropertyValue("" + defaultPercentage);
            PropertyManager.saveProperty(percentageProperty);
        }
        percentage = Integer.parseInt(percentageProperty.getPropertyValue());
        if (percentage < 1) {
            percentage = 1;
        }

    }

    public void calculateProgress() {

        totalNumberOfPages = 0;

        finishedNumberOfPages = 0;

        errorNumberOfPages = 0;
        numberOfPagesInProcess = 0;
        for (ProcessOverview proc : processes) {
            totalNumberOfPages += proc.getNumberOfPages();

            if ("accepted".equals(proc.getProcessStatus())) {
                finishedNumberOfPages += proc.getNumberOfPages();
            }
            if ("error".equals(proc.getProcessStatus())) {
                errorNumberOfPages += proc.getNumberOfPages();
            }
            if ("in progress".equals(proc.getProcessStatus())) {
                numberOfPagesInProcess += proc.getNumberOfPages();
            }
        }
    }

    public int getNumberOfProcesses() {
        return processes.size();
    }

    public double getAcceptedPercentage() {
        double d = 0;
        if (finishedNumberOfPages > 0) {
            d = finishedNumberOfPages * 100 / getProcessedPages();
        }
        if (d > 100) {
            d = 100;
        }
        return d;
    }

    public double getUnAcceptedPercentage() {
        double d = 100 - getAcceptedPercentage();
        if (d < 0) {
            d = 0.0;
        }
        return d;
    }

    public double getErrorPercentage() {
        double d = 0;
        if (errorNumberOfPages > 0) {
            d = errorNumberOfPages * 100 / getProcessedPages();
        }
        if (d > 100) {
            d = 100;
        }
        return d;
    }

    public double getNotErrorPercentage() {
        double d = 100 - getErrorPercentage();
        if (d < 0) {
            d = 0.0;
        }
        return d;
    }

    public double getInWorkPercentage() {
        double d = 0;
        if (numberOfPagesInProcess > 0) {
            d = numberOfPagesInProcess * 100 / getThresholdPages();
        }
        if (d > 100) {
            d = 100;
        }
        return d;
    }

    public double getNotInWorkPercentage() {
        double d = 100 - getInWorkPercentage();
        if (d < 0) {
            d = 0.0;
        }
        return d;
    }

    public double getOpenPercentage() {
        double d = 0;
        if (totalNumberOfPages > 0) {
            d = (getThresholdPages() - finishedNumberOfPages - errorNumberOfPages - numberOfPagesInProcess) * 100 / getThresholdPages();
        }
        if (d < 0) {
            d = 0;
        }
        return d;
    }

    public double getThresholdPages() {
        return totalNumberOfPages * percentage / 100;
    }

    public long getProcessedPages() {
        return finishedNumberOfPages + errorNumberOfPages;
    }

    public int getOpenNumberOfPages() {
        int pages = (int) (getThresholdPages() - finishedNumberOfPages - errorNumberOfPages - numberOfPagesInProcess);
        if (pages < 0) {
            pages = 0;
        }
        return pages;
    }

    public String getAcceptedPercentageDisplay() {
        return String.format("%.0f", getAcceptedPercentage());
    }

    public String getErrorPercentageDisplay() {
        return String.format("%.0f", getErrorPercentage());
    }

    public String getInWorkPercentageDisplay() {
        return String.format("%.0f", getInWorkPercentage());
    }

    public String getFinishedProgressbarTooltip() {
        return Helper.getTranslation("statusAbgeschlossen") + ": " + finishedNumberOfPages + " " + Helper.getTranslation("Images");
    }

    public String getInWorkProgressbarTooltip() {
        return Helper.getTranslation("statusInBearbeitung") + ": " + numberOfPagesInProcess + " " + Helper.getTranslation("Images");
    }

    public String getErrorProgressbarTooltip() {
        return Helper.getTranslation("plugin_workflow_batches_numberOfPagesError") + ": " + errorNumberOfPages + " "
                + Helper.getTranslation("Images");
    }

    public String getOpenProgressbarTooltip() {
        return Helper.getTranslation("lw_NOT_PROCESSED") + ": "
                + getOpenNumberOfPages() + " "
                + Helper.getTranslation("Images");
    }

    public boolean isFinished() {
        return finishedNumberOfPages + errorNumberOfPages + numberOfPagesInProcess == totalNumberOfPages;

    }

    public boolean isThresholdExceeded() {
        return finishedNumberOfPages + errorNumberOfPages + numberOfPagesInProcess >= getThresholdPages();
    }
}
