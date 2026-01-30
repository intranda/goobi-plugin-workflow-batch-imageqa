package de.intranda.goobi.plugins.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProcessOverviewTest {

    @Test
    public void testConstructorAndGetters() {
        ProcessOverview overview = new ProcessOverview("title", "42", 100, true, false, "accepted", null);

        assertEquals("42", overview.getProcessid());
        assertEquals(100, overview.getNumberOfPages());
        assertTrue(overview.isPriorityStep());
        assertFalse(overview.isMetadataAvailable());
    }

    @Test
    public void testSetters() {
        ProcessOverview overview = new ProcessOverview("title", "1", 10, false, false, "in progress", null);

        overview.setProcessid("99");
        overview.setNumberOfPages(200);
        overview.setPriorityStep(true);
        overview.setMetadataAvailable(true);

        assertEquals("99", overview.getProcessid());
        assertEquals(200, overview.getNumberOfPages());
        assertTrue(overview.isPriorityStep());
        assertTrue(overview.isMetadataAvailable());
    }
}
