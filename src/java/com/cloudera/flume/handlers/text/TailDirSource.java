/**
 * Licensed to Cloudera, Inc. under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  Cloudera, Inc. licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.cloudera.flume.handlers.text;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cloudera.flume.conf.SourceFactory.SourceBuilder;
import com.cloudera.flume.core.Event;
import com.cloudera.flume.core.EventSource;
import com.cloudera.flume.handlers.text.TailSource.Cursor;
import com.cloudera.flume.reporter.ReportEvent;
import com.cloudera.util.dirwatcher.DirChangeHandler;
import com.cloudera.util.dirwatcher.DirWatcher;
import com.cloudera.util.dirwatcher.RegexFileFilter;
import com.google.common.base.Preconditions;

/**
 * This source tails all the file in a directory that match a specified regular
 * expression.
 */
public class TailDirSource extends EventSource.Base {
  public static final Logger LOG = LoggerFactory.getLogger(TailDirSource.class);
  private DirWatcher watcher;
  private TailSource tail;
  private static final DateFormat dateFormatDayHour = new SimpleDateFormat("yyyy-MM-dd HH:mm");

  final private long startFromDateInEpoch;
  final private File dir;
  final private String regex;

  final private AtomicLong filesAdded = new AtomicLong();
  final private AtomicLong filesDeleted = new AtomicLong();

  final public static String A_FILESADDED = "filesAdded";
  final public static String A_FILESDELETED = "filesDeleted";
  final public static String A_FILESPRESENT = "filesPresent";

  public TailDirSource(File f, String regex, long startFromDateInEpoch) {
    Preconditions.checkArgument(f != null, "File should not be null!");
    Preconditions.checkArgument(regex != null,
    "Regex filter should not be null");

    this.dir = f;
    this.regex = regex;
    this.startFromDateInEpoch = startFromDateInEpoch;

    // 100 ms between checks
    this.tail = new TailSource(100);
  }

  /**
   * Must be synchronized to isolate watcher
   */
  @Override
  synchronized public void open() throws IOException {
    Preconditions.checkState(watcher == null,
        "Attempting to open an already open TailDirSource (" + dir + ", \""
        + regex + ", \"" + startFromDateInEpoch + "\")");
    // 250 ms between checks
    this.watcher = new DirWatcher(dir, new RegexFileFilter(regex), 250);
    synchronized (watcher) {
      this.watcher.addHandler(new DirChangeHandler() {
        Map<String, TailSource.Cursor> curmap = new HashMap<String, TailSource.Cursor>();

        @Override
        public void fileCreated(File f) {
          // Add a new file to the multi tail.
          if (f.isDirectory()) {
            LOG.debug("Tail dir will not read or recurse "
                + "into subdirectory " + f);
            return;
          }
          if (f.lastModified() >= startFromDateInEpoch) {
            LOG.info("File: " + f + " lastModifiedAt: " + f.lastModified() + " is younger than: " + startFromDateInEpoch + " adding to list");
            Cursor c = new Cursor(tail.sync, f);
            curmap.put(f.getName(), c);
            tail.addCursor(c);
            filesAdded.incrementAndGet();
          } else {
            LOG.info("File: " + f + " lastModifiedAt: " + f.lastModified() + " is older than: " + startFromDateInEpoch + " not adding to list");
          }
        }

        @Override
        public void fileDeleted(File f) {
          LOG.info("removed file " + f);
          Cursor c = curmap.remove(f.getName());
          tail.removeCursor(c);
          filesDeleted.incrementAndGet();
        }

      });

      this.watcher.start();
    }
    tail.open();
  }

  @Override
  synchronized public void close() throws IOException {
    tail.close();
    synchronized (watcher) {
      this.watcher.stop();
      this.watcher = null;
    }
  }

  @Override
  synchronized public ReportEvent getReport() {
    ReportEvent rpt = super.getReport();
    rpt.setLongMetric(A_FILESADDED, filesAdded.get());
    rpt.setLongMetric(A_FILESDELETED, filesDeleted.get());
    rpt.setLongMetric(A_FILESPRESENT, tail.cursors.size());
    return rpt;
  }

  @Override
  public Event next() throws IOException {
    // this cannot be in synchronized because it has a
    // blocking call to a queue inside it.
    Event e = tail.next();

    synchronized (this) {
      updateEventProcessingStats(e);
      return e;
    }
  }

  public static SourceBuilder builder() {
    return new SourceBuilder() {
      @Override
      public EventSource build(String... argv) throws IllegalArgumentException {
        if (argv.length == 2) {
        Preconditions.checkArgument(argv.length >= 1 && argv.length <= 2,
          "usage: tailDir(dir, regex, lastModifiedTime) ");
        }
        if (argv.length == 3) {
          Preconditions.checkArgument(argv.length >= 1 && argv.length <= 3,
          "usage: tailDir(dir, regex, lastModifiedTime=\"1970-01-01 00:00\") ");
          
        }
        String regex = ".*"; // default to accepting all
        String defaultStartDate = "1970-01-01 00:00"; //epoch time
        long startFromDateInEpoch = 0L;
        try {
          startFromDateInEpoch = dateFormatDayHour.parse(defaultStartDate).getTime();
          if (argv.length > 1) {
            regex = argv[1];
          }
          if (argv.length == 3) {
            startFromDateInEpoch = dateFormatDayHour.parse(argv[2]).getTime();
          }
        } catch (ParseException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
          throw new IllegalArgumentException("Date should be in the following format: yyyy-MM-dd HH:mm\nIncorrectly formatted date: " + argv[2]);
        }
        return new TailDirSource(new File(argv[0]), regex, startFromDateInEpoch );
      }
    };
  }
}
