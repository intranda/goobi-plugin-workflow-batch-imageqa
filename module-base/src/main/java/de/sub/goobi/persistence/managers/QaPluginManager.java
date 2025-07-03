package de.sub.goobi.persistence.managers;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.dbutils.QueryRunner;
import org.goobi.beans.Batch;
import org.goobi.beans.Step;

import de.intranda.goobi.plugins.model.ProcessOverview;
import de.intranda.goobi.plugins.model.QaBatch;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class QaPluginManager {

    public static List<Step> getStepsForBatch(String stepTitle, int batchID) {
        Connection connection = null;
        String sql = "select s.* from schritte s left join prozesse p ON s.ProzesseID = p.ProzesseID AND s.titel = ? WHERE batchID = ?";
        try {
            connection = MySQLHelper.getInstance().getConnection();
            return new QueryRunner().query(connection, sql, StepMysqlHelper.resultSetToStepListHandler, stepTitle, batchID);
        } catch (SQLException e) {
            log.error(e);
        } finally {
            if (connection != null) {
                try {
                    MySQLHelper.closeConnection(connection);
                } catch (SQLException e) {
                    log.error(e);
                }
            }
        }
        return Collections.emptyList();
    }

    public static List<QaBatch> getAllQaBatches(String openTaskBatchQuery, String qaStepName) {
        List<QaBatch> answer = new ArrayList<>();
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
                    answer.add(new QaBatch(b, qaStepName));
                }
            }

        }
        return answer;
    }

    public static List<ProcessOverview> getProcesses(String stepTitle, int batchId) {
        List<ProcessOverview> processes = new ArrayList<>();
        // order:
        //            1. task priority
        //            2. number of pages

        // TODO
        String sql =
                "SELECT p.prozesseID, p.sortHelperImages, s.prioritaet FROM prozesse p JOIN schritte s ON s.ProzesseID = p.ProzesseID AND s.titel = '"
                        + stepTitle + "' WHERE batchID = " + batchId + " ORDER BY s.prioritaet desc , p.sortHelperImages";

        @SuppressWarnings("rawtypes")
        List data = ProcessManager.runSQL(sql);
        for (Object obj : data) {
            Object[] objArr = (Object[]) obj;
            String processId = objArr[0].toString();
            String pages = objArr[1].toString();
            String prio = objArr[2].toString();
            processes.add(new ProcessOverview(processId, Integer.parseInt(pages), "10".equals(prio), false));
        }
        return processes;

    }

}
