package de.intranda.goobi.plugins.model;

import java.util.List;

import org.goobi.beans.Batch;
import org.goobi.beans.GoobiProperty;
import org.goobi.beans.GoobiProperty.PropertyOwnerType;

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
        for (ProcessOverview proc : processes) {
            totalNumberOfPages += proc.getNumberOfPages();

            if ("done".equals(proc.getProcessStatus())) {
                finishedNumberOfPages += proc.getNumberOfPages();
            }
            if ("error".equals(proc.getProcessStatus())) {
                errorNumberOfPages += proc.getNumberOfPages();
            }
            if ("in progress".equals(proc.getProcessStatus())) {
                numberOfPagesInProcess += proc.getNumberOfPages();
            }
        }

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

    public int getNumberOfProcesses() {
        return processes.size();
    }

    public double getFinishedPercentage() {
        double d = 0;

        return d;
    }

    public double getErrorPercentage() {
        double d = 0;

        return d;
    }

    public double getInWorkPercentage() {
        double d = 0;

        return d;
    }

    public double getThresholdPages() {
        double imagesToDisplay = totalNumberOfPages * percentage / 100;
        return imagesToDisplay;
    }

}
