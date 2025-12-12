package de.intranda.goobi.plugins.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ProcessOverview {

    private String processTitle;
    private String processid;
    private int numberOfPages;
    private boolean priorityStep;
    private boolean metadataAvailable;

    // possible values: '' (not processed at all), 'in work', 'done', 'error'
    private String processStatus;

    private String errorMessage;

}
