package org.apache.jsp;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.jsp.*;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.util.*;
import java.text.DecimalFormat;
import com.cloudera.flume.master.FlumeMaster;

public final class flumeconfig_jsp extends org.apache.jasper.runtime.HttpJspBase
    implements org.apache.jasper.runtime.JspSourceDependent {

  private static java.util.Vector _jspx_dependants;

  public java.util.List getDependants() {
    return _jspx_dependants;
  }

  public void _jspService(HttpServletRequest request, HttpServletResponse response)
        throws java.io.IOException, ServletException {

    JspFactory _jspxFactory = null;
    PageContext pageContext = null;
    HttpSession session = null;
    ServletContext application = null;
    ServletConfig config = null;
    JspWriter out = null;
    Object page = this;
    JspWriter _jspx_out = null;
    PageContext _jspx_page_context = null;


    try {
      _jspxFactory = JspFactory.getDefaultFactory();
      response.setContentType("text/html; charset=UTF-8");
      pageContext = _jspxFactory.getPageContext(this, request, response,
      			null, true, 8192, true);
      _jspx_page_context = pageContext;
      application = pageContext.getServletContext();
      config = pageContext.getServletConfig();
      session = pageContext.getSession();
      out = pageContext.getOut();
      _jspx_out = out;

      out.write("<!--\n Licensed to Cloudera, Inc. under one\n or more contributor license agreements.  See the NOTICE file\n distributed with this work for additional information\n regarding copyright ownership.  Cloudera, Inc. licenses this file\n to you under the Apache License, Version 2.0 (the\n \"License\"); you may not use this file except in compliance\n with the License.  You may obtain a copy of the License at\n\n     http://www.apache.org/licenses/LICENSE-2.0\n\n Unless required by applicable law or agreed to in writing, software\n distributed under the License is distributed on an \"AS IS\" BASIS,\n WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n See the License for the specific language governing permissions and\n limitations under the License.\n-->\n<html>\n<link rel=\"stylesheet\" type=\"text/css\" href=\"/flume.css\">\n<head>\n<!--(c) Copyright (2009) Cloudera, Inc.    -->\n<!-- Retro web 1.0 flume reporter display -->\n<title>Flume Master: Configure Nodes</title>\n\n\n\n</head>\n<body>\n");
      org.apache.jasper.runtime.JspRuntimeLibrary.include(request, response, "menu.jsp", out, false);
      out.write("\n\n<h1> Flume Master: Configure Nodes </h1>\n\n<h2>Configure a single node</h2>\n<form method=post action=\"command.jsp\">\n<table>\n\t<tr>\n\t\t<td>Configure node:</td>\n\t\t<td>\n\t\t\t<select name=\"nodeChoice\">\n\t\t\t<option value=\"\">Choose from list</option>\n\t\t\t\t");
 for (String s : FlumeMaster.getInstance().getKnownNodes()) {
				
      out.write(" <option value=\"");
      out.print( s );
      out.write('"');
      out.write('>');
      out.print( s );
      out.write("</option>\n\t\t\t\t\t");

					}
					
      out.write("\n\t\t\t</select>\n\t\t</td>\n\t</tr>\n\t<tr>\n\t\t<td>\n\t\t\tor specify another node:\n\t\t</td>\n\t\t<td>\n\t\t\t<input type=\"text\" name=\"node\" size=128 />\n\t\t</td>\n\t</tr>\n\t<tr>\n\t\t<td>Source: </td>\n\t\t<td><input type=\"text\" name=\"source\" size=128/> </td>\n\t</tr>\n\t<tr>\n\t\t<td>Sink:</td>\n\t\t<td><input type=\"text\" name=\"sink\" size=128/> </td>\n\t</tr>\n\t<tr>\n\t\t<td>\n\t\t\t<input type=\"submit\"/>\n\t\t</td>\n\t</tr>\n\t</form>\n</table>\n\n<h2>Configure multiple nodes</h2>\n<form method=post action=\"fullspec.jsp\">\n<!-- TODO (jon) put current configuration in here -->\n<textarea rows=\"10\" cols=\"60\" name=\"specification\">\n</textarea>\n<input type=\"submit\"/>\n</form>\n\n\n</body></html>\n");
    } catch (Throwable t) {
      if (!(t instanceof SkipPageException)){
        out = _jspx_out;
        if (out != null && out.getBufferSize() != 0)
          out.clearBuffer();
        if (_jspx_page_context != null) _jspx_page_context.handlePageException(t);
      }
    } finally {
      if (_jspxFactory != null) _jspxFactory.releasePageContext(_jspx_page_context);
    }
  }
}
