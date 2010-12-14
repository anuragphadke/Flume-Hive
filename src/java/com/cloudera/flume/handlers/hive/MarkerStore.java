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
package com.cloudera.flume.handlers.hive;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.cli.CliDriver;
import org.apache.hadoop.hive.service.HiveClient;
import org.apache.hadoop.hive.service.HiveServerException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.log4j.Logger;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

import com.cloudera.flume.conf.FlumeConfiguration;
import com.google.common.io.CharStreams;


/**
 * Writes events the a file give a hadoop uri path. If no uri is specified It
 * defaults to the set by the given configured by fs.default.name config
 * variable. The user can specify an output format for the file. If none is
 * specified the default set by flume.collector.outputformat in the flume
 * configuration file is used.
 * 
 * 
 * 
 * TODO (jon) refactor this to be sane. Not happening now.
 */
public class MarkerStore  {

  private static String hiveHost;
  private static int hivePort;
  private static TTransport transport;
  private static TProtocol protocol;
  HiveClient client;
  FlumeConfiguration conf;
  Path dstPath;
  String hiveTableName, hiveMarkerFolder, elasticsearchMarkerFolder, elasticsearchUrl;
  boolean runMarkerQueries;
  
  final static Logger LOG =
    Logger.getLogger(MarkerStore.class.getName());


  public MarkerStore(String hiveTableName, String elasticsearchUrl, boolean runMarkerQueries) {
    this.conf = FlumeConfiguration.get();

    hiveHost = conf.getHiveHost();
    hivePort = conf.getHivePort();

    transport = new TSocket(hiveHost, hivePort);
    protocol = new TBinaryProtocol(transport);
    client = new HiveClient(protocol);
    if (StringUtils.isNotEmpty(elasticsearchUrl)) {
      this.elasticsearchUrl = elasticsearchUrl;
      this.elasticsearchMarkerFolder = conf.getElasticSearchMarkerFolder();
      if (runMarkerQueries) {
        LOG.info("RUNNING ELASTICSEARCHMARKERQUERIES\n");
        runElasticSearchMarkerQueries();
      }
    }
    this.hiveTableName = hiveTableName;
    hiveMarkerFolder = conf.getHiveDefaultMarkerFolder();
    try {
      if (!transport.isOpen()) {
        LOG.error("hive transport is closed, re-opening");
        transport = new TSocket(hiveHost, hivePort);
        protocol = new TBinaryProtocol(transport);
        client = new HiveClient(protocol);
        transport.open();        
        if (runMarkerQueries) {
          LOG.info("RUNNING HIVEMARKERQUERIES\n");
          runHiveMarkerQueries();
        }
        
      }

    } catch (TTransportException e) {
      LOG.error("error opening transport layer to hive" + e.getMessage());
    }
    


  }

  private boolean runElasticSearchMarkerQueries() {
      boolean success = true;
      FileSystem hdfs;    
      FSDataInputStream in;
      dstPath = new Path(elasticsearchMarkerFolder);
      LOG.info("DSTPATH: " + dstPath);
      try {
        hdfs = dstPath.getFileSystem(conf);
        if (hdfs.exists(dstPath)) {
          FileStatus[] fileListing = hdfs.listStatus(dstPath);
          for (FileStatus fs : fileListing) {
            if (!fs.isDir()) {
              LOG.info("File marker path: " + fs.getPath());
              in = hdfs.open(fs.getPath());
              byte[] fileData = new byte[(int) fs.getLen()];
              in.readFully(fileData);
              in.close();
              LOG.info("cleaning markerfile @: " + fs.getPath().toString());
              cleanMarkerFile(fs.getPath().toString());
              sendESQuery(elasticsearchUrl, new String(fileData));
              
            }
          }
        }
      } catch (Exception e) {
        success = false;
      } 
      return success;
  }
  
  //you have indices, think of each index as a distributed database, a type is like a table in a database
  public boolean sendESQuery(String elasticSearchUrl, String sb) {
    boolean success = true; 
    LOG.info("sending batched stringentities");
    LOG.info("elasticSearchUrl: " + elasticSearchUrl);
    try {


      HttpClient httpClient = new DefaultHttpClient();
      HttpPost httpPost = new HttpPost(elasticSearchUrl);
      StringEntity se = new StringEntity(sb);    
      
      httpPost.setEntity(se);
      HttpResponse hr = httpClient.execute(httpPost);

      LOG.info("HTTP Response: " + hr.getStatusLine());
      LOG.info("Closing httpConnection");
      httpClient.getConnectionManager().shutdown();        
      LOG.info("booooooo: " + CharStreams.toString(new InputStreamReader(se.getContent())));
    } catch (IOException e) {
      e.printStackTrace();
      success = false;
    } finally {
      if (!success) {
        LOG.info("ESQuery wasn't successful, writing to markerfolder");
        writeElasticSearchToMarkerFolder(new StringBuilder(sb));
      }
    }
    LOG.info("ESQuery was successful, yay!");
    return success;

  }
  
  private boolean  writeElasticSearchToMarkerFolder(StringBuilder httpQuery) {
    FileSystem hdfs;
    try {
      String markerFolder = conf.getElasticSearchDefaultMarkerFolder();
      dstPath = new Path(markerFolder);
      hdfs = dstPath.getFileSystem(conf);
      if (!hdfs.exists(dstPath)) {
        hdfs.mkdirs(dstPath);
      }

      dstPath = new Path(markerFolder + "/es-" + System.currentTimeMillis() + ".marker");
      System.out.println("creating file at: " + dstPath.toString());
      FSDataOutputStream writer_marker = hdfs.create(dstPath);
      writer_marker.writeBytes(httpQuery + "\n");
      writer_marker.close();
      dstPath = null;
      writer_marker = null;
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      return false;
    }
    return true;
  }


  private boolean runHiveMarkerQueries() {
    boolean queryStatus = true;
    FileSystem hdfs;    
    FSDataInputStream in;
    dstPath = new Path(hiveMarkerFolder);
    LOG.info("DSTPATH: " + dstPath);
    try {
      hdfs = dstPath.getFileSystem(conf);
      if (hdfs.exists(dstPath)) {
        FileStatus[] fileListing = hdfs.listStatus(dstPath);
        for (FileStatus fs : fileListing) {
          if (!fs.isDir()) {
            LOG.info("File marker path: " + fs.getPath());
            in = hdfs.open(fs.getPath());
            byte[] fileData = new byte[(int) fs.getLen()];
            in.readFully(fileData);
            String[] splitTab = new String(fileData).split("\t");
            if (splitTab.length == 2) {
              dstPath = new Path(splitTab[0]);
              FileSystem hiveFile = dstPath.getFileSystem(conf);
              if (hiveFile.exists(dstPath)) {
                LOG.info("marker file data: " + splitTab[1]);
                if (runHiveQuery(splitTab[1])) {
                  LOG.info("Marker query is successful");
                  in.close();
                  cleanMarkerFile(fs.getPath().toString());
                } else {
                  LOG.info("Error running marker query, marker point not deleted");
                  queryStatus = false;
                }

              } else {
                LOG.info("marker points to invalid hive file location, deleting the marker");
                in.close();
                cleanMarkerFile(fs.getPath().toString());
              }
            }
            //in.close();
          }
        }
      }
      hdfs.close();        
    } catch (IOException e) {
      LOG.error("ERROR running runMarkerQueries:" + e.getMessage());
    }       

    return queryStatus;
  }


  public boolean cleanMarkerFile(String hiveMarkerPath) {
    LOG.debug("cleaning up hiveMarker: " + hiveMarkerPath);    
    FileSystem localHdfs;    
    Path deletePath = new Path(hiveMarkerPath);
    try {
      localHdfs = deletePath.getFileSystem(conf);
      if (localHdfs.delete(deletePath, false)) {
        LOG.debug("hiveMarker deleted successfully: " + hiveMarkerPath);
        return true;
      } else {
        LOG.error("error deleting hive marker: " + hiveMarkerPath);
      }
    } catch (IOException e) {
      // TODO Auto-generated catch block
      LOG.error("Error deleting hiveMarker: " + e.getMessage());
    }
    return false;
  }

  public boolean runHiveQuery(String query) {
//    CliDriver clidriver = new CliDriver();
//    LOG.error("QUery: " + query);
//    int cliStatus = clidriver.processLine(query);
//    LOG.error("cliStatus: " + cliStatus);

    
    try {
      if (!transport.isOpen()) {
        LOG.error("hive transport is closed, re-opening");
        transport = new TSocket(hiveHost, hivePort);
        protocol = new TBinaryProtocol(transport);
        client = new HiveClient(protocol);
        transport.open();

      }
      client.execute(query);
      transport.close();
      return true;
    } catch (TTransportException e) {
      // TODO Auto-generated catch block
      LOG.error("Error setting up transport with hive: " + e.getMessage());
      e.printStackTrace();
    } catch (HiveServerException e) {
      // TODO Auto-generated catch block
      LOG.error("HiveServerException: " + e.getMessage());
    } catch (TException e) {
      LOG.error("TException: " + e.getMessage());
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  

    return false;
  }


  public boolean writeHiveMarker(String hqlQuery, String filePath, String hiveMarkerFolder, String hiveMarkerPath) {
    LOG.debug("writing to hiveMarker: " + hiveMarkerFolder);


    FileSystem hdfs;    
    dstPath = new Path(hiveMarkerFolder);
    try {
      hdfs = dstPath.getFileSystem(conf);

      if (!hdfs.exists(dstPath)) {
        hdfs.mkdirs(dstPath);
      }
      dstPath = new Path(hiveMarkerPath);
      FSDataOutputStream writer = hdfs.create(dstPath);
      writer.writeBytes(filePath + "\t" + hqlQuery + "\n");
      writer.close();
      dstPath = null;
      writer = null;

    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }



    /*
      dstPath = new Path(hiveMarkerPath);
      hdfs = dstPath.getFileSystem(conf);

      writer = hdfs.create(dstPath);      
      writer.writeUTF(hqlQuery);
      writer.close();
      writer = null;
     */
    return true;


  }

  public boolean mergeFiles(String folder, Path file, String hiveOutputLocation) {
    FileSystem hdfs;    
    FSDataInputStream in;
    FSDataOutputStream out;
    List<Path> fileCollection = new ArrayList<Path>();
    dstPath = new Path(folder);
    LOG.info("mergeFiles DSTPATH: " + dstPath);
    try {
      hdfs = dstPath.getFileSystem(conf);
      
      if (hdfs.exists(dstPath)) {
        FileStatus[] fileListing = hdfs.listStatus(dstPath);
        LOG.error("Creating file @: " + hiveOutputLocation);
        out = hdfs.create(new Path(hiveOutputLocation));
        
        
        in = hdfs.open(file);
        byte[] fileData = new byte[(int) hdfs.getFileStatus(file).getLen()];
        in.readFully(fileData);
        out.write(fileData);
        
        
        
        for (FileStatus fs : fileListing) {
          if (!fs.isDir()) {
            LOG.info("mergeFiles File marker path: " + fs.getPath());
            fileCollection.add(fs.getPath());
            in = hdfs.open(fs.getPath());
            fileData = new byte[(int) fs.getLen()];
            in.readFully(fileData);
            out.write(fileData);
          }
        }
        out.close();
      }

      hdfs.close();
      LOG.error("Written file: " + hiveOutputLocation);
      
      //lets start the purge process, delete all files except the merged file
      hdfs = dstPath.getFileSystem(conf);
      for (Path p : fileCollection) {
        if (hdfs.delete(p,false)) {
          LOG.error("Successfully deleted: " + p);
        } else {
          LOG.error("Error deleting file: " + p);
        }
      }
      
    } catch (IOException e) {
      LOG.error("ERROR running runMarkerQueries:" + e.getMessage());
    }  
    LOG.error("mergeFiles Done merging files");
    return false;
  }
  
  public boolean checkIfPartitionExists(String filePath) {
    dstPath = new Path(filePath);
    FileSystem hdfs; 
    try {
      hdfs = dstPath.getFileSystem(conf);
      return hdfs.exists(dstPath);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    return false;
  }


}
