package org.allurereport.jenkins.callables;

import hudson.FilePath;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;

public class AllureReportArchiveTest {

    private static final String BIG = "allure-report/data/attachments/trace.zip";
    private static final String SMALL = "allure-report/index.html";
    private static final String OUT = "out/";
    private static final String ZIP = "allure-report.zip";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void filesAboveThresholdAreLeftOutOfZipAndReturned() throws Exception {
        final File workspace = folder.newFolder("ws");
        write(new File(workspace, OUT + BIG), 100);
        write(new File(workspace, OUT + SMALL), 10);

        final List<String> separate = new FilePath(workspace)
                .act(new AllureReportArchive(OUT + "allure-report", ZIP, 50));

        assertThat(separate).containsExactly(BIG);
        final List<String> entries = new ArrayList<>();
        try (ZipFile zip = new ZipFile(new File(workspace, ZIP))) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (!entry.isDirectory()) {
                    entries.add(entry.getName());
                }
            }
        }
        assertThat(entries).containsExactly(SMALL);
    }

    private static void write(final File file, final int size) throws Exception {
        Files.createDirectories(file.getParentFile().toPath());
        Files.write(file.toPath(), new byte[size]);
    }
}
