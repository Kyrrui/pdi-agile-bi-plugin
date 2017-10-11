/*!
* This program is free software; you can redistribute it and/or modify it under the
* terms of the GNU Lesser General Public License, version 2.1 as published by the Free Software
* Foundation.
*
* You should have received a copy of the GNU Lesser General Public License along with this
* program; if not, you can obtain a copy at http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
* or from the Free Software Foundation, Inc.,
* 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
*
* This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
* without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
* See the GNU Lesser General Public License for more details.
*
* Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
*/

package org.pentaho.agilebi.spoon.visualizations.analyzer;

import org.pentaho.agilebi.spoon.HasXulController;
import org.pentaho.agilebi.modeler.ModelerWorkspace;
import org.pentaho.agilebi.spoon.visualizations.SaveAwareMeta;
import org.pentaho.di.core.EngineMetaInterface;
import org.pentaho.di.core.ProgressMonitorListener;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.repository.*;
import org.pentaho.ui.xul.components.XulTab;
import org.pentaho.ui.xul.impl.XulEventHandler;

import java.io.File;
import java.util.Date;

public class AnalyzerVisualizationMeta implements EngineMetaInterface, HasXulController, SaveAwareMeta {

  AnalyzerVisualizationController browser;
  XulTab tab;
  
  public AnalyzerVisualizationMeta(AnalyzerVisualizationController browser) {
    this.browser = browser;
  }
  
  public void save(String filename) {
    this.browser.save(filename);
    browser.setDirty(false);
  }
  
  public void clearChanged() {
    // TODO Auto-generated method stub
    
  }

  public Date getCreatedDate() {
    // TODO Auto-generated method stub
    return null;
  }

  public String getCreatedUser() {
    // TODO Auto-generated method stub
    return null;
  }

  public String getDefaultExtension() {
    return browser.getVisualization().getExtension();
  }

  public String getFileType() {
    return getDefaultExtension();
  }

  public String getFilename() {
    return browser.getVisFileLocation();
  }

  public String[] getFilterExtensions() {
    return new String[]{"*." + getDefaultExtension()}; //$NON-NLS-1$
  }

  public String[] getFilterNames() {
    return new String[]{browser.getVisualization().getTitle()};
  }

  public Date getModifiedDate() {
    // TODO Auto-generated method stub
    return null;
  }

  public String getModifiedUser() {
    // TODO Auto-generated method stub
    return null;
  }

  public String getName() {
    // this uses the file name to determine the tab name
    if (browser.getFileName() == null) {
      return "Untitled";
    } else {
      File f = new File(browser.getFileName());
      String n = f.getName();
      // strip the file name of its extension
      if (n.length() > getDefaultExtension().length() + 1) {
        n = n.substring(0, n.length() - (getDefaultExtension().length() + 1));
      }
      return n;
    }
  }

  public String getXML() throws KettleException {
    // this is handled by the embedded browser
    throw new UnsupportedOperationException();
  }

  public void nameFromFilename() {
    // TODO Auto-generated method stub
    
  }

  public void saveRep(Repository arg0, ProgressMonitorListener arg1) throws KettleException {
    // TODO Auto-generated method stub
    
  }

  public void saveSharedObjects() {
  }

  public void setCreatedDate(Date arg0) {
    // TODO Auto-generated method stub
    
  }

  public void setCreatedUser(String arg0) {
    // TODO Auto-generated method stub
    
  }

  public void setFilename(String arg0) {
    browser.setVisFileLocation(arg0);
  }

  public void setID(long arg0) {
    // TODO Auto-generated method stub
    
  }

  public void setInternalKettleVariables() {
    // TODO Auto-generated method stub
    
  }

  public void setModifiedDate(Date arg0) {
    // TODO Auto-generated method stub
    
  }

  public void setModifiedUser(String arg0) {
    // TODO Auto-generated method stub
    
  }

  public boolean showReplaceWarning(Repository arg0) {
    // TODO Auto-generated method stub
    return false;
  }

  public XulTab getTab() {
    return tab;
  }

  public void setTab(XulTab tab) {
    this.tab = tab;
  }

  public RepositoryDirectoryInterface getRepositoryDirectory() {
    // TODO Auto-generated method stub
    return null;
  }

  public void setObjectId(ObjectId id) {
    // TODO Auto-generated method stub
    
  }

  public String getDescription() {
    // TODO Auto-generated method stub
    return null;
  }

  public ObjectId getObjectId() {
    // TODO Auto-generated method stub
    return null;
  }

  public ObjectRevision getObjectRevision() {
    // TODO Auto-generated method stub
    return null;
  }

  public void setDescription(String description) {
    // TODO Auto-generated method stub
    
  }

  public void setName(String name) {
    // TODO Auto-generated method stub
    
  }

  public void setObjectRevision(ObjectRevision objectRevision) {
    // TODO Auto-generated method stub
    
  }

  public void setRepositoryDirectory(RepositoryDirectoryInterface repositoryDirectory) {
    // TODO Auto-generated method stub
    
  }

  public RepositoryObjectType getRepositoryElementType() {
    // TODO Auto-generated method stub
    return null;
  }

  public XulEventHandler getController() {
    return browser;
  }
  
  public boolean canSave() {
    //TODO
    //Commented out due to requirements on AGILEBI-310.
    //The canSave() mechanism is left in place since it will be re implemented in GA
    //This code was here to prompt the user that the analyzer report he was trying to save was based
    //on a temporary model and asked if he wanted to go first to the modeling perspective to save
    //the model first.
    
    /*
    ModelerWorkspace workspace = browser.getModel();
    boolean isDirty = workspace.isDirty();
    try {
      if(isDirty) {
        Spoon theSpoon = Spoon.getInstance();
        MessageBox theMessageBox = new MessageBox(theSpoon.getShell(), SWT.ICON_WARNING | SWT.YES | SWT.NO);
        theMessageBox.setText(BaseMessages.getString(Spoon.class, "Spoon.Message.Warning.Warning")); //$NON-NLS-1$
        theMessageBox.setMessage(BaseMessages.getString(IVisualization.class, "AnalyzerViz.save_model")); //$NON-NLS-1$
        int theVal = theMessageBox.open();
        if(theVal == SWT.YES) {
          browser.editModel();
        }
      }
    } catch(Exception e) {
      return false;
    }
    return !isDirty;
    */
    
    ModelerWorkspace workspace = browser.getModel();
    return !workspace.isTemporary();    
  }

  public boolean isDirty() {
    return browser.isDirty();
  }
  
  
}
