package de.intranda.goobi.plugins.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.goobi.beans.Batch;

import de.sub.goobi.persistence.managers.ProcessManager;
import lombok.Getter;

public class QaBatch {

    @Getter
    private Batch batch;

    @Getter
    private Map<String, Integer> proceses;

    public QaBatch(Batch batch, String stepTitle) {
        this.batch = batch;
        proceses = new LinkedHashMap<>();

        // order:
        //            1. task priority
        //            2. number of pages
        String sql =
                "SELECT p.prozesseID, p.sortHelperImages, s.prioritaet FROM prozesse p JOIN schritte s ON s.ProzesseID = p.ProzesseID AND s.titel = '"
                        + stepTitle + "' WHERE batchID = " + batch.getBatchId() + " ORDER BY s.prioritaet , p.sortHelperImages";

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
