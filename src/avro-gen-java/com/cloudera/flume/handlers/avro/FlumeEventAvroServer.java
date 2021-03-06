/**
 * Autogenerated by Avro
 * 
 * DO NOT EDIT DIRECTLY
 */
package com.cloudera.flume.handlers.avro;

@SuppressWarnings("all")
public interface FlumeEventAvroServer {
  public static final org.apache.avro.Protocol PROTOCOL = org.apache.avro.Protocol.parse("{\"protocol\":\"FlumeEventAvroServer\",\"namespace\":\"com.cloudera.flume.handlers.avro\",\"types\":[{\"type\":\"enum\",\"name\":\"Priority\",\"symbols\":[\"FATAL\",\"ERROR\",\"WARN\",\"INFO\",\"DEBUG\",\"TRACE\"]},{\"type\":\"record\",\"name\":\"AvroFlumeEvent\",\"fields\":[{\"name\":\"timestamp\",\"type\":\"long\"},{\"name\":\"priority\",\"type\":\"Priority\"},{\"name\":\"body\",\"type\":\"bytes\"},{\"name\":\"nanos\",\"type\":\"long\"},{\"name\":\"host\",\"type\":\"string\"},{\"name\":\"fields\",\"type\":{\"type\":\"map\",\"values\":\"bytes\"}}]}],\"messages\":{\"append\":{\"request\":[{\"name\":\"evt\",\"type\":\"AvroFlumeEvent\"}],\"response\":\"null\",\"one-way\":true}}}");
  void append(com.cloudera.flume.handlers.avro.AvroFlumeEvent evt);
}
