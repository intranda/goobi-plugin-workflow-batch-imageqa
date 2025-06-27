package de.intranda.goobi.plugins.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.goobi.beans.Batch;

import de.sub.goobi.persistence.managers.ProcessManager;
import lombok.Getter;

public class QaBatch {

    @Getter
    private Batch batch;

    @Getter
    private Map<String, Integer> proceses;

    public QaBatch(Batch batch) {
        this.batch = batch;
        Map<String, Integer> map = new HashMap<>();
        String sql = "SELECT prozesseID, sortHelperImages FROM prozesse WHERE batchID = " + batch.getBatchId();
        @SuppressWarnings("rawtypes")
        List data = ProcessManager.runSQL(sql);
        for (Object obj : data) {
            Object[] objArr = (Object[]) obj;
            String processId = objArr[0].toString();
            String pages = objArr[1].toString();
            map.put(processId, Integer.valueOf(pages));
        }
        // sort process list by number of images ascending
        List<Entry<String, Integer>> list = new ArrayList<>(map.entrySet());
        list.sort(Entry.comparingByValue());

        proceses = new LinkedHashMap<>();
        for (Entry<String, Integer> entry : list) {
            proceses.put(entry.getKey(), entry.getValue());
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
