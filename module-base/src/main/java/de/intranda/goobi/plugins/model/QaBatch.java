package de.intranda.goobi.plugins.model;

import java.util.Map;

import org.goobi.beans.Batch;

import de.sub.goobi.persistence.managers.QaPluginManager;
import lombok.Getter;

public class QaBatch {

    @Getter
    private Batch batch;

    @Getter
    private Map<String, Integer> processes;

    public QaBatch(Batch batch, String stepTitle) {
        this.batch = batch;
        processes = QaPluginManager.getProcesses(stepTitle, batch.getBatchId());
    }

    public int getNumberOfProcesses() {
        return processes.size();
    }

    public long getNumberOfPages() {
        long numberOfPages = 0;
        for (Integer pages : processes.values()) {
            numberOfPages += pages;
        }
        return numberOfPages;
    }

}
