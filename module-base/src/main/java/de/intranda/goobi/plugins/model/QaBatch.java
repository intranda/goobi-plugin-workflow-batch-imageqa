package de.intranda.goobi.plugins.model;

import java.util.List;

import org.goobi.beans.Batch;

import de.sub.goobi.persistence.managers.QaPluginManager;
import lombok.Getter;

public class QaBatch {

    @Getter
    private Batch batch;

    @Getter
    private List<ProcessOverview> processes;

    public QaBatch(Batch batch, String stepTitle, List<String> metadataToCheck) {
        this.batch = batch;
        processes = QaPluginManager.getProcesses(stepTitle, batch.getBatchId(), metadataToCheck);
    }

    public int getNumberOfProcesses() {
        return processes.size();
    }

    public long getNumberOfPages() {
        long numberOfPages = 0;
        for (ProcessOverview proc : processes) {
            numberOfPages += proc.getNumberOfPages();
        }
        return numberOfPages;
    }

}
