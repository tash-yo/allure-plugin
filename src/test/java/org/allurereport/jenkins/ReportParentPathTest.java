package org.allurereport.jenkins;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReportParentPathTest {

    @Test
    public void separateFilesArePrefixedWithReportDirectoryParent() {
        assertThat(AllureReportPublisher.reportParentPath("allure-report")).isEmpty();
        assertThat(AllureReportPublisher.reportParentPath("out/allure-report/")).isEqualTo("out/");
        assertThat(AllureReportPublisher.reportParentPath("a\\b\\allure-report")).isEqualTo("a/b/");
    }
}
