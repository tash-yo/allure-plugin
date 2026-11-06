/*
 *  Copyright 2016-2023 Qameta Software OÜ
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.allurereport.jenkins.callables;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import hudson.util.DirScanner;
import hudson.util.FileVisitor;
import jenkins.MasterToSlaveFileCallable;

import org.allurereport.jenkins.utils.TrueZipArchiver;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Creates Allure report archive on the build agent.
 *
 * <p>Files larger than {@link #DEFAULT_SEPARATE_FILE_THRESHOLD} (Playwright traces of failing tests
 * reach hundreds of MB each) are left out of the zip and returned, so the caller can archive each one
 * as its own artifact under its zip entry path. Object stores cap a single upload (S3: 5 GB), and one
 * zip holding every trace of a run with many failures goes over that cap.
 */
public class AllureReportArchive extends MasterToSlaveFileCallable<ArrayList<String>> {

    private static final long serialVersionUID = 2L;

    public static final String REPORT_DIRECTORY_NOT_FOUND = "Allure report directory not found: ";

    // ponytail: a fixed cutoff, not a running size budget. The zip still exceeds the 5 GB upload cap
    // if a report holds more than ~300 files just under 16 MiB; add a budget if that ever happens.
    // A single file over 5 GB still cannot be uploaded.
    public static final long DEFAULT_SEPARATE_FILE_THRESHOLD = 16L * 1024 * 1024;

    private final String reportDirectoryPath;
    private final String archiveFileName;
    private final long separateFileThreshold;

    public AllureReportArchive(final String reportDirectoryPath, final String archiveFileName) {
        this(reportDirectoryPath, archiveFileName, DEFAULT_SEPARATE_FILE_THRESHOLD);
    }

    public AllureReportArchive(final String reportDirectoryPath,
                               final String archiveFileName,
                               final long separateFileThreshold) {
        this.reportDirectoryPath = reportDirectoryPath;
        this.archiveFileName = archiveFileName;
        this.separateFileThreshold = separateFileThreshold;
    }

    /**
     * @return zip entry paths (relative to the report directory's parent, e.g.
     *     {@code allure-report/data/attachments/abc.zip}) of the files left out of the archive.
     */
    @Override
    public ArrayList<String> invoke(final File workspaceDirectory, final VirtualChannel channel)
        throws IOException, InterruptedException {

        final FilePath workspace = new FilePath(workspaceDirectory);
        final FilePath reportDirectory = workspace.child(reportDirectoryPath);

        if (!reportDirectory.exists()) {
            throw new IOException(REPORT_DIRECTORY_NOT_FOUND + reportDirectory.getRemote());
        }

        final FilePath archiveFilePath = workspace.child(archiveFileName);
        if (archiveFilePath.exists()) {
            archiveFilePath.delete();
        }

        final FilePath reportParentDirectory = reportDirectory.getParent();
        final ArrayList<String> separateFiles = new ArrayList<>();

        try (OutputStream outputStream = archiveFilePath.write()) {
            Objects.requireNonNull(reportParentDirectory).archive(
                TrueZipArchiver.FACTORY,
                outputStream,
                new SplittingScanner(reportDirectory.getName() + "/**", separateFileThreshold, separateFiles));
        }

        return separateFiles;
    }

    /**
     * Scans like {@link DirScanner.Glob} but diverts files above the threshold into a list instead of
     * the archive.
     */
    @SuppressFBWarnings(
            value = "SE_TRANSIENT_FIELD_NOT_RESTORED",
            justification = "Only used inside invoke() on the agent; never serialized.")
    private static final class SplittingScanner extends DirScanner {

        private static final long serialVersionUID = 1L;

        private final String includes;
        private final long threshold;
        private final transient List<String> separateFiles;

        SplittingScanner(final String includes, final long threshold, final List<String> separateFiles) {
            this.includes = includes;
            this.threshold = threshold;
            this.separateFiles = separateFiles;
        }

        @Override
        public void scan(final File dir, final FileVisitor visitor) throws IOException {
            new DirScanner.Glob(includes, null).scan(dir, new FileVisitor() {
                @Override
                public void visit(final File f, final String relativePath) throws IOException {
                    if (f.isFile() && f.length() > threshold) {
                        separateFiles.add(relativePath.replace('\\', '/'));
                        return;
                    }
                    visitor.visit(f, relativePath);
                }
            });
        }
    }
}
