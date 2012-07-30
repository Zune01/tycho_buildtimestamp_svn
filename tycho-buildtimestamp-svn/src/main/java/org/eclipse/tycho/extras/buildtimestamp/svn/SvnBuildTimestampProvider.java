/*******************************************************************************
 * Copyright (c) 2012 Sonatype Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.extras.buildtimestamp.svn;

import java.io.File;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.tycho.buildversion.BuildTimestampProvider;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNInfoHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNWCClient;

/**
 * Build timestamp provider that returns date of the most recent commit that touches any file under
 * project basedir. File additional flexibility, some files can be ignored using gitignore patterns
 * specified in &lt;svn.ignore> element of tycho-packaging-plugin configuration block
 * 
 * <p>
 * Typical usage
 * 
 * <pre>
 * ...
 *       &lt;plugin>
 *         &lt;groupId>org.eclipse.tycho&lt;/groupId>
 *         &lt;artifactId>tycho-packaging-plugin&lt;/artifactId>
 *         &lt;version>${tycho-version}&lt;/version>
 *         &lt;dependencies>
 *           &lt;dependency>
 *             &lt;groupId>org.eclipse.tycho.extras&lt;/groupId>
 *             &lt;artifactId>tycho-buildtimestamp-svn&lt;/artifactId>
 *             &lt;version>${tycho-version}&lt;/version>
 *           &lt;/dependency>
 *         &lt;/dependencies>
 *         &lt;configuration>
 *           &lt;timestampProvider>svn&lt;/timestampProvider>
 *           &lt;svn.ignore>pom.xml&lt;/svn.ignore>
 *         &lt;/configuration>
 *       &lt;/plugin>
 * ...
 * </pre>
 */
@Component(role = BuildTimestampProvider.class, hint = "svn")
public class SvnBuildTimestampProvider implements BuildTimestampProvider {

  public Date getTimestamp(MavenSession session, MavenProject project, MojoExecution execution) throws MojoExecutionException {
    SVNClientManager clientManager = SVNClientManager.newInstance();
    SVNWCClient wcClient = clientManager.getWCClient();
    final Date[] result = { null };
    String ignoreFilter = getIgnoreFilter(execution);
    final Set<String> filterFiles = new HashSet<String>();
    
    StringTokenizer tokens = new StringTokenizer(ignoreFilter, "\n\r\f");
    while (tokens.hasMoreTokens()) {
      filterFiles.add(tokens.nextToken());
    }
    try {
      wcClient.doInfo(project.getBasedir(), null, null, SVNDepth.INFINITY, null, new ISVNInfoHandler() {
        public void handleInfo(SVNInfo info) throws SVNException {
          File file = info.getFile();
          if (filterFiles.contains(file.getName())) {
            return;
          }
          Date date = info.getCommittedDate();
          if (result[0] == null || date.after(result[0])) {
            result[0] = date;
          }
        }
      });
    } catch (SVNException e) {
      throw new MojoExecutionException("Failed to get info", e);
    }
    
    return result[0];
  }

  private String getIgnoreFilter(MojoExecution execution) {
    Xpp3Dom pluginConfiguration = (Xpp3Dom)execution.getPlugin().getConfiguration();
    Xpp3Dom ignoreDom = pluginConfiguration.getChild("svn.ignore");
    if (ignoreDom == null) {
      return null;
    }
    return ignoreDom.getValue();
  }
}
