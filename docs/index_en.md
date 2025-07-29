---
title: Plugin for performing percentage-based quality control of deliveries
identifier: intranda_workflow_batch_imageqa
description: Workflow Plugin for performing percentage-based quality control of deliveries
published: false
---

## Einführung
This workflow plugin allows you to perform a percentage-based quality control check on deliveries. Many processes can be handled simultaneously.

## Installation
In order to use the plugin, the following files must be installed:

```bash
/opt/digiverso/goobi/plugins/workflow/plugin-workflow-batch-imageqa-base.jar
/opt/digiverso/goobi/plugins/GUI/plugin-workflow-batch-imageqa-gui.jar
/opt/digiverso/goobi/config/plugin_intranda_workflow_batch_imageqa.xml
```

To use this plugin, the user must have the correct role authorisation.

![The plugin cannot be used without correct authorisation](screen1_en.png)

Therefore, please assign the role `Plugin_workflow_batch_imageqa` to the group.

![Correctly assigned role for users](screen2_en.png)


## Overview and functionality
If the plugin has been installed and configured correctly, it can be found under the `Workflow` menu item.

![User interface of the plugin](screen3_en.png)

The overview page displays all deliveries for which all processes have reached the configured quality control step. If not all processes have progressed that far, the delivery will not yet be displayed here.

When a delivery is opened, the configured percentage is used to determine how many and which images are to be displayed. As many processes as necessary are displayed in full until the expected number of images is exceeded. The user has no influence on the selection of processes, but there are two exceptions: processes that have gone through an error loop and processes for which certain configurable metadata exists are always displayed, even if this exceeds the number of images to be displayed.

The title and metadata for each process to be displayed can be configured.

If a process is faulty, a message can be written for it. It is also possible to generate a CSV file with all error messages.

![Error message](screen4_en.png)

In the event of errors, the delivery can be rejected. Marked processes are then sent back to the configured step. Otherwise, the quality control step is completed for all processes in the delivery.


## Configuration
The plugin is configured in the file `plugin_intranda_workflow_ZZZ.xml` as shown here:

{{CONFIG_CONTENT}}

The following table contains a summary of the parameters and their descriptions:

Parameter               | Explanation
------------------------|------------------------------------
`qaTaskName`            | Processes must reach the configured step in order to be displayed in the list.
`errorStepName`         | This step is re-opened when processes are marked as faulty.
`percentage`            | configures the number of images displayed
`numberOfProcessesPerPage` | Number of processes displayed simultaneously. Additional processes can be accessed using the paginator.
`thumbnailSize`         | Size of images in pixels
`titleField`            | Metadata used for the box title
`metadata`              | Repeatable field. Metadata configured here is displayed in the order of the configuration.
`metadataToCheck`       | Repeatable field. If a process contains metadata that is configured here, it will always be displayed, even if the number of images to be displayed has already been exceeded.