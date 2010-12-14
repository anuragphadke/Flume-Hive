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
package com.cloudera.flume.handlers.hdfs;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.CompressionCodecFactory;
import org.apache.hadoop.io.compress.Compressor;
import org.apache.hadoop.io.compress.GzipCodec;
import org.apache.http.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cloudera.flume.conf.Context;
import com.cloudera.flume.conf.FlumeConfiguration;
import com.cloudera.flume.conf.FlumeSpecException;
import com.cloudera.flume.conf.SinkFactory.SinkBuilder;
import com.cloudera.flume.core.Event;
import com.cloudera.flume.core.EventSink;
import com.cloudera.flume.handlers.hive.MarkerStore;
import com.cloudera.flume.handlers.text.FormatFactory;
import com.cloudera.flume.handlers.text.output.OutputFormat;
import com.cloudera.flume.reporter.ReportEvent;
import com.google.common.base.Preconditions;

/**
 * This creates a raw hadoop dfs file that outputs data formatted by the
 * provided OutputFormat. It is assumed that the output is a file of some sort.
 */
public class CustomDfsSink extends EventSink.Base {
  static final Logger LOG = LoggerFactory.getLogger(CustomDfsSink.class);

  private static final String A_OUTPUTFORMAT = "recordformat";
  private static final DateFormat dateFormatDay = new SimpleDateFormat("yyyy-MM-dd");
  private static final DateFormat dateFormatHourMinuteSecond = new SimpleDateFormat("HH-mm-ss");
  private static final DateFormat dateFormatHourMinute = new SimpleDateFormat("HH-mm");
  private static final DateFormat dateFormatHour = new SimpleDateFormat("HH");


  List <StringEntity> stringEntities = new ArrayList<StringEntity>();
  boolean compressOutput, hiveOutput = false;
  OutputFormat format;
  OutputStream writer;
  AtomicLong count = new AtomicLong();
  String path;
  Path dstPath;
  String hiveTableName;
  String machineHostName;
  Calendar cal;
  Event localEvent;
  FlumeConfiguration conf;
  MarkerStore hup;
  String hiveMarkerFolder, hiveMarkerPath;


  StringBuilder sb = new StringBuilder();
  String elasticIndex, elasticType, elasticSearchUrl;
  boolean runMarkerQueries = false;

  public CustomDfsSink(String path, OutputFormat format) {
    Preconditions.checkArgument(path != null);
    Preconditions.checkArgument(format != null);
    this.path = path;
    this.format = format;
    this.writer = null;
  }
  public CustomDfsSink(String path, OutputFormat format, Event event) {

    Preconditions.checkArgument(path != null);
    Preconditions.checkArgument(format != null);
    this.path = path;
    this.format = format;
    this.writer = null;
    this.localEvent = event;
    cal = Calendar.getInstance();
    cal.setTimeInMillis(localEvent.getTimestamp());      
    this.conf = FlumeConfiguration.get();
    try {
      machineHostName = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      LOG.error("Error getting hostname for local machine: " + e.getMessage());
    }


  }

  public CustomDfsSink(String path, OutputFormat format, Event event, String hiveTableName) {
    sb = new StringBuilder();    
    

    Preconditions.checkArgument(path != null);
    Preconditions.checkArgument(format != null);
    this.path = path;
    this.localEvent = event;

    cal = Calendar.getInstance();
    cal.setTimeInMillis(localEvent.getTimestamp());      
    this.format = format;
    this.writer = null;
    this.conf = FlumeConfiguration.get();
    this.hiveMarkerFolder = conf.getHiveDefaultMarkerFolder();

    if (StringUtils.isNotBlank(hiveTableName)) {
      this.hiveOutput = true;
      this.hiveTableName = hiveTableName;    
      hup = new MarkerStore(hiveTableName, null, false);
    }

    try {
      machineHostName = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      LOG.error("Error getting hostname for local machine: " + e.getMessage());
    }
  }

  public CustomDfsSink(String path, OutputFormat format, Event event, String hiveTableName, String elasticSearchUrl, String elasticIndex, String elasticType, boolean runMarkerQueries) {
    LOG.info("inside CUSTOMDFSSINK: URL: " + elasticSearchUrl + " Index: " + elasticIndex + " Type: " + elasticType, " RunMarkerQueries: " + runMarkerQueries);

    sb = new StringBuilder();    
    this.elasticSearchUrl = elasticSearchUrl;
    //    if (StringUtils.indexOf(this.elasticSearchUrl, "/", this.elasticSearchUrl.length() - 1) > -1) {
    //      elasticSearchUrl = StringUtils.replaceOnce(StringUtils.reverse(elasticSearchUrl), "/", "");
    //    }
    this.elasticIndex = elasticIndex;
    this.elasticType = elasticType;
    this.runMarkerQueries = runMarkerQueries;

    Preconditions.checkArgument(path != null);
    Preconditions.checkArgument(format != null);
    this.path = path;
    this.localEvent = event;

    cal = Calendar.getInstance();
    cal.setTimeInMillis(localEvent.getTimestamp());      
    this.format = format;
    this.writer = null;
    this.conf = FlumeConfiguration.get();
    this.hiveMarkerFolder = conf.getHiveDefaultMarkerFolder();

    if (StringUtils.isNotBlank(hiveTableName)) {
      this.hiveOutput = true;
      this.hiveTableName = hiveTableName;    
      this.elasticSearchUrl = elasticSearchUrl;
      hup = new MarkerStore(hiveTableName, elasticSearchUrl, runMarkerQueries);
    }

    try {
      machineHostName = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      LOG.error("Error getting hostname for local machine: " + e.getMessage());
    }
  }

  @Override
  public void append(Event e) throws IOException {
    if (writer == null) {
      throw new IOException("Append failed, did you open the writer?");
    }
    sb.append("{ \"index\" : { \"_index\" : \"" + elasticIndex + "\", \"_type\" : \"" + elasticType + "\" } }\n{ \"" + elasticType + "\" : " + new String(e.getBody()) + " }\n");

    format.format(writer, e);
    count.getAndIncrement();
    super.append(e);

  }

  @Override
  public void close()  {
    try {
      LOG.info("Closing HDFS file: " + dstPath);
      writer.flush();
      LOG.info("done writing raw file to hdfs");
      writer.close();

      if (StringUtils.isNotBlank(elasticSearchUrl) && StringUtils.isNotBlank(elasticIndex)  && StringUtils.isNotBlank(elasticType) ) {
        hup.sendESQuery(elasticSearchUrl, sb.toString());
      }


      if (!deleteEmptyFile(dstPath)) {
        if (localEvent != null && hiveOutput) {
          String dataFolder = StringUtils.substringBeforeLast(dstPath.toString(),"/");

          //String hqlQuery = "ALTER TABLE " + hiveTableName + " ADD IF NOT EXISTS PARTITION (ds='" + dateFormatDay.format(cal.getTime()) + "', ts='" + dateFormatHour.format(cal.getTime()) + "') LOCATION '" + dataFolder + "'";
          String hqlQuery = "ALTER TABLE " + hiveTableName + " ADD IF NOT EXISTS PARTITION (ds='" + dateFormatDay.format(localEvent.getTimestamp()) + "', ts='" + dateFormatHour.format(localEvent.getTimestamp()) + "') LOCATION '" + dataFolder + "'";          

          LOG.info("HQL Query: " + hqlQuery + "\n\n\n\n\n");

          hiveMarkerPath = hiveMarkerFolder + "/" + machineHostName + "-" + localEvent.getTimestamp() + ".marker";

          if (!hup.runHiveQuery(hqlQuery)) {
            writeHiveMarker(hqlQuery, dstPath.toString(), hiveMarkerFolder, hiveMarkerPath);
          }
          //      boolean hiveMarkerStatus = writeHiveMarker(hqlQuery, dstPath.toString(), hiveMarkerFolder, hiveMarkerPath);
          //
          //      if (hiveMarkerStatus) {
          //        if (hup.runHiveQuery(hqlQuery)) {
          //          hup.cleanHiveMarker(hiveMarkerPath);
          //        }
          //      }
        }
      } else {
        LOG.info("deleted empty file: " + dstPath);
      }
      localEvent = null;
      cal = null;

      writer = null;
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  
  private boolean deleteEmptyFile(Path dstPath) {
    try {
      FileSystem fs = dstPath.getFileSystem(conf);
      if (fs.getFileStatus(dstPath).getLen() == 0) { //empty file, needs to be deleted
        LOG.info("empty file: " + dstPath);
        return fs.delete(dstPath, false);
      }

    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    LOG.info("non-empty file: " + dstPath);
    return false;
  }



  private boolean writeHiveMarker(String hqlQuery, String filePath, String hiveMarkerFolder, String hiveMarkerPath) {
    LOG.info("writing to hiveMarker: " + hiveMarkerFolder);
    LOG.info("hiveMarkerPath: " + hiveMarkerPath);


    FileSystem hdfs;    
    dstPath = new Path(hiveMarkerFolder);
    try {
      hdfs = dstPath.getFileSystem(conf);

      if (!hdfs.exists(dstPath)) {
        hdfs.mkdirs(dstPath);
      }
      dstPath = new Path(hiveMarkerPath);
      FSDataOutputStream writer_marker = hdfs.create(dstPath);
      writer_marker.writeBytes(filePath + "\t" + hqlQuery + "\n");
      writer_marker.close();
      dstPath = null;
      writer_marker = null;

    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    return true;


  }




  @Override
  public void open() throws IOException {
    FlumeConfiguration conf = FlumeConfiguration.get();
    FileSystem hdfs;

    // use v0.9.1 compression settings
    if (conf.getCollectorDfsCompressGzipStatus()) {
      LOG.warn("Config property "
          + FlumeConfiguration.COLLECTOR_DFS_COMPRESS_GZIP
          + " is deprecated, please use "
          + FlumeConfiguration.COLLECTOR_DFS_COMPRESS_CODEC
          + " set to GzipCodec instead");
      CompressionCodec gzipC = new GzipCodec();
      Compressor gzCmp = gzipC.createCompressor();
      dstPath = new Path(path + gzipC.getDefaultExtension());
      hdfs = dstPath.getFileSystem(conf);
      writer = hdfs.create(dstPath);
      writer = gzipC.createOutputStream(writer, gzCmp);
      LOG.info("Creating HDFS gzip compressed file: " + dstPath.toString());
      return;
    }

    String codecName = conf.getCollectorDfsCompressCodec();
    List<Class<? extends CompressionCodec>> codecs = CompressionCodecFactory
    .getCodecClasses(FlumeConfiguration.get());
    CompressionCodec codec = null;
    ArrayList<String> codecStrs = new ArrayList<String>();
    codecStrs.add("None");
    for (Class<? extends CompressionCodec> cls : codecs) {
      codecStrs.add(cls.getSimpleName());

      if (cls.getSimpleName().equals(codecName)) {
        try {
          codec = cls.newInstance();
        } catch (InstantiationException e) {
          LOG.error("Unable to instantiate " + codec + " class");
        } catch (IllegalAccessException e) {
          LOG.error("Unable to access " + codec + " class");
        }
      }
    }

    if (codec == null) {
      if (!codecName.equals("None")) {
        LOG.warn("Unsupported compression codec " + codecName
            + ".  Please choose from: " + codecStrs);
      }
      dstPath = new Path(path);
      hdfs = dstPath.getFileSystem(conf);
      writer = hdfs.create(dstPath);
      LOG.info("Creating HDFS file: " + dstPath.toString());
      return;
    }

    Compressor cmp = codec.createCompressor();
    dstPath = new Path(path + codec.getDefaultExtension());
    hdfs = dstPath.getFileSystem(conf);
    writer = hdfs.create(dstPath);
    try {
      writer = codec.createOutputStream(writer, cmp);
    } catch (NullPointerException npe) {
      // tries to find "native" version of codec, if that fails, then tries to
      // find java version. If there is no java version, the createOutpuStream
      // exits via NPE. We capture this and convert it into a IOE with a more
      // useful error message.
      LOG.error("Unable to load compression codec " + codec);
      throw new IOException("Unable to load compression codec " + codec);
    }
    LOG.info("Creating " + codec + " compressed HDFS file: "
        + dstPath.toString());
  }

  public static SinkBuilder builder() {
    return new SinkBuilder() {
      @Override
      public EventSink build(Context context, String... args) {
        if (args.length != 2 && args.length != 1) {
          // TODO (jon) make this message easier.
          throw new IllegalArgumentException(
          "usage: customdfs(\"[(hdfs|file|s3n|...)://namenode[:port]]/path\", \"format\")");
        }

        String format = (args.length == 1) ? null : args[1];
        OutputFormat fmt;
        try {
          fmt = FormatFactory.get().getOutputFormat(format);
        } catch (FlumeSpecException e) {
          LOG.error("failed to load format " + format, e);
          throw new IllegalArgumentException("failed to load format " + format);
        }
        return new CustomDfsSink(args[0], fmt,null);
      }
    };
  }

  @Override
  public String getName() {
    return "CustomDfs";
  }

  @Override
  public ReportEvent getReport() {
    ReportEvent rpt = super.getReport();
    rpt.setStringMetric(A_OUTPUTFORMAT, format.getBuilder().getName());
    rpt.setLongMetric(ReportEvent.A_COUNT, count.get());
    return rpt;
  }
}
