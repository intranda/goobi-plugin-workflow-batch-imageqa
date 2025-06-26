package de.intranda.goobi.plugins.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.goobi.beans.Batch;

import de.sub.goobi.persistence.managers.ProcessManager;
import lombok.Getter;

public class QaBatch {

    @Getter
    private Batch batch;

    private Map<String, Integer> proceses;

    public QaBatch(Batch batch) {
        this.batch = batch;
        proceses = new HashMap<>();
        String sql = "SELECT prozesseID, sortHelperImages FROM prozesse WHERE batchID = " + batch.getBatchId();
        @SuppressWarnings("rawtypes")
        List data = ProcessManager.runSQL(sql);
        for (Object obj : data) {
            Object[] objArr = (Object[]) obj;
            String processId = objArr[0].toString();
            String pages = objArr[1].toString();
            proceses.put(processId, Integer.valueOf(pages));
        }
    }

    public int getNumberOfProcesses() {
        return proceses.size();
    }

    public long getNumberOfPages() {
        long numberOfPages = 0;
        for (Integer pages : proceses.values()) {
            numberOfPages += pages;
        }
        return numberOfPages;
    }

}
