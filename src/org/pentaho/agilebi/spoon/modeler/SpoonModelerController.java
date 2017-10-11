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

package org.pentaho.agilebi.spoon.modeler;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.MessageBox;
import org.pentaho.agilebi.modeler.IModelerSource;
import org.pentaho.agilebi.modeler.ModelerController;
import org.pentaho.agilebi.modeler.ModelerException;
import org.pentaho.agilebi.modeler.ModelerMessagesHolder;
import org.pentaho.agilebi.modeler.ModelerMode;
import org.pentaho.agilebi.modeler.ModelerWorkspace;
import org.pentaho.agilebi.modeler.util.ISpoonModelerSource;
import org.pentaho.agilebi.modeler.util.ModelerWorkspaceUtil;
import org.pentaho.agilebi.modeler.util.TableModelerSource;
import org.pentaho.agilebi.spoon.OutputStepModelerSource;
import org.pentaho.agilebi.spoon.SpoonModelerWorkspaceHelper;
import org.pentaho.agilebi.spoon.publish.PublisherHelper;
import org.pentaho.agilebi.spoon.visualizations.IVisualization;
import org.pentaho.agilebi.spoon.visualizations.VisualizationManager;
import org.pentaho.di.core.EngineMetaInterface;
import org.pentaho.di.core.database.DatabaseMeta;
import org.pentaho.di.core.gui.SpoonFactory;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.trans.HasDatabasesInterface;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.ui.core.dialog.EnterSelectionDialog;
import org.pentaho.di.ui.core.dialog.ErrorDialog;
import org.pentaho.di.ui.spoon.MainSpoonPerspective;
import org.pentaho.di.ui.spoon.Spoon;
import org.pentaho.di.ui.spoon.SpoonPerspective;
import org.pentaho.di.ui.spoon.SpoonPerspectiveManager;
import org.pentaho.di.ui.spoon.delegates.SpoonDBDelegate;
import org.pentaho.metadata.model.IPhysicalModel;
import org.pentaho.metadata.model.IPhysicalTable;
import org.pentaho.metadata.model.concept.types.LocalizedString;
import org.pentaho.platform.api.repository2.unified.RepositoryFile;
import org.pentaho.reporting.libraries.base.util.StringUtils;
import org.pentaho.ui.xul.XulComponent;
import org.pentaho.ui.xul.binding.Binding;
import org.pentaho.ui.xul.components.XulLabel;
import org.pentaho.ui.xul.components.XulMenuList;
import org.pentaho.ui.xul.containers.XulEditpanel;
import org.pentaho.ui.xul.containers.XulVbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User: nbaker
 * Date: Jun 14, 2010
 */
public class SpoonModelerController extends ModelerController {
  private ModelerControllerDBRegistry databaseInterface = new ModelerControllerDBRegistry();
  private static Logger logger = LoggerFactory.getLogger(SpoonModelerController.class);
  private XulMenuList visualizationList;

  private List<String> visualizationNames;
  private Binding visualizationsBinding;

  private Binding datasourceButtonBinding;

  private XulEditpanel propPanel;

  public SpoonModelerController(){
    this(new ModelerWorkspace(new SpoonModelerWorkspaceHelper(), SpoonModelerWorkspaceHelper.initGeoContext()));
  }

  public SpoonModelerController(ModelerWorkspace work){
    super(work);
    work.setGeoContext(SpoonModelerWorkspaceHelper.initGeoContext());
  }

  public void init() throws ModelerException{

    propPanel = (XulEditpanel) document.getElementById("propertiesPanel"); //$NON-NLS-1$
    visualizationList = (XulMenuList)document.getElementById("visualizationlist"); //$NON-NLS-1$
    bf.createBinding(workspace, "selectedVisualization", visualizationList, "selectedItem"); //$NON-NLS-1$//$NON-NLS-2$
    bf.setBindingType(Binding.Type.ONE_WAY);
       visualizationsBinding = bf.createBinding(this, "visualizationNames", visualizationList, "elements"); //$NON-NLS-1$//$NON-NLS-2$


    //TODO: migrate this "source" code elsewhere or remove it entirely

    XulLabel sourceLabel = (XulLabel) document.getElementById(SOURCE_NAME_LABEL_ID);
    XulLabel relational_sourceLabel = (XulLabel) document.getElementById(this.RELATIONAL_NAME_LABEL_ID);

    String tableName = ""; //$NON-NLS-1$

    bf.createBinding(workspace, "sourceName", sourceLabel, "value"); //$NON-NLS-1$//$NON-NLS-2$
    bf.createBinding(workspace, "sourceName", relational_sourceLabel, "value");

    if( workspace.getModelSource() != null && workspace.getModelSource() instanceof OutputStepModelerSource) {
      // for now just list the first table in the first physical workspace
      DatabaseMeta databaseMeta = ((ISpoonModelerSource) workspace.getModelSource()).getDatabaseMeta();
      List<IPhysicalModel> physicalModels = workspace.getDomain().getPhysicalModels();
      if( physicalModels != null && physicalModels.size() > 0 ) {
        List<? extends IPhysicalTable> tables = physicalModels.get(0).getPhysicalTables();
        if( tables != null && tables.size() > 0 ) {
          tableName = tables.get(0).getName(LocalizedString.DEFAULT_LOCALE);
        }
      }
    } else if (workspace.getModelSource() != null && workspace.getModelSource() instanceof TableModelerSource) {
      tableName = workspace.getModelSource().getTableName();
    }

    if(StringUtils.isEmpty(tableName)) {
    	tableName = ModelerMessagesHolder.getMessages().getString("ModelerController.Datasource"); 
    }


    //TODO: move all this datasource stuff into models! use the existing property form validation to show messages.
    datasourceButtonBinding = bf.createBinding(workspace, "sourceName", this, "sourceNameForCheck");
    workspace.setSourceName(tableName);

    bf.setBindingType(Binding.Type.BI_DIRECTIONAL);
    bf.createBinding(this.propPanel, "visible", this, "propVisible"); //$NON-NLS-1$//$NON-NLS-2$

    try{
      datasourceButtonBinding.fireSourceChanged();
//      modelNameBinding.fireSourceChanged();
      visualizationsBinding.fireSourceChanged();
    } catch(Exception e){
      throw new ModelerException(e);
    }
    super.init();
    setModellingMode(ModelerMode.ANALYSIS_AND_REPORTING);
  }

  public void editDataSource() {
    try {
      Spoon theSpoon = Spoon.getInstance();
      Repository theRepository = theSpoon.getRepository();

      List<DatabaseMeta> theDatabases = new ArrayList<DatabaseMeta>();

      EngineMetaInterface theMeta = null;
      HasDatabasesInterface theDatabasesInterface = null;
      List<SpoonPerspective> thePerspectives = SpoonPerspectiveManager.getInstance().getPerspectives();
      for (SpoonPerspective thePerspective : thePerspectives) {
        if(thePerspective instanceof MainSpoonPerspective) {
           theMeta = thePerspective.getActiveMeta();
           break;
        }
      }
      if(theMeta != null) {
        theDatabasesInterface = (HasDatabasesInterface) theMeta;
      } else {
        theDatabasesInterface = this.databaseInterface;
      }

      if(theRepository != null) {
        TransMeta theTransMeta = new TransMeta();
        theRepository.readTransSharedObjects(theTransMeta);
        theDatabases.addAll(theTransMeta.getDatabases());
        theDatabasesInterface.setDatabases(theDatabases);
      } else {
        theDatabases.addAll(theDatabasesInterface.getDatabases());
      }

      String theSelectedTable = null;
      IModelerSource theModelerSource = this.workspace.getModelSource();
      if(theModelerSource != null) {
        theSelectedTable = theModelerSource.getDatabaseName();
      }
      int[] theSelectedIndexes = new int[1];
      String[] theNames = new String[theDatabases.size()];
      for (int i = 0; i < theDatabases.size(); i++) {
        theNames[i] = theDatabases.get(i).getName();
        if(theSelectedTable != null && theNames[i].equals(theSelectedTable)) {
          theSelectedIndexes[0] = i;
        }
      }

      EnterSelectionDialog theDialog = new EnterSelectionDialog(theSpoon.getShell(), theNames,
          BaseMessages.getString(Spoon.class ,"Spoon.ExploreDB.SelectDB.Title"), //$NON-NLS-1$
          BaseMessages.getString(Spoon.class, "Spoon.ExploreDB.SelectDB.Message"), theDatabasesInterface); //$NON-NLS-1$
      theDialog.setSelectedNrs(theSelectedIndexes);
      String theDBName = theDialog.open();

      if (theDBName != null) {
        SpoonDBDelegate theDelegate = new SpoonDBDelegate(theSpoon);
        DatabaseMeta theDBMeta = DatabaseMeta.findDatabase(theDatabasesInterface.getDatabases(), theDBName);
        String theTableAndSchema[] = theDelegate.exploreDB(theDBMeta, false);

        if (StringUtils.isEmpty(theTableAndSchema[1])) {
          MessageBox theMessageBox = new MessageBox(theSpoon.getShell(), SWT.ICON_ERROR | SWT.OK);
          theMessageBox.setText(BaseMessages.getString(Spoon.class, "Spoon.Message.Warning.Warning")); //$NON-NLS-1$
          theMessageBox.setMessage(BaseMessages.getString(ModelerWorkspace.class, "Spoon.Message.Model.EmptyTable")); //$NON-NLS-1$
          theMessageBox.open();
          return;
        }

        boolean refresh = this.workspace.getAvailableTables().isEmpty();
        if(!StringUtils.isEmpty(theTableAndSchema[1]) && !this.workspace.getAvailableTables().isEmpty()) {

          MessageBox theMessageBox = new MessageBox(theSpoon.getShell(), SWT.ICON_WARNING | SWT.OK | SWT.CANCEL);
          theMessageBox.setText(BaseMessages.getString(Spoon.class, "Spoon.Message.Warning.Warning")); //$NON-NLS-1$
          theMessageBox.setMessage(BaseMessages.getString(ModelerWorkspace.class, "Spoon.Message.Model.Warning")); //$NON-NLS-1$

          int theVal = theMessageBox.open();
          if(theVal == SWT.OK) {
            refresh = true;
          } else {
            refresh = false;
          }
        }
        if(refresh) {
          TableModelerSource theSource = new TableModelerSource(theDBMeta, theTableAndSchema[1], theTableAndSchema[0]);
          ModelerWorkspaceUtil.populateModelFromSource(this.workspace, theSource);
          workspace.setSourceName(theTableAndSchema[1]);
          datasourceButtonBinding.fireSourceChanged();
          fireBindings();
        }
      }
    } catch (Exception e) {
      new ErrorDialog(((Spoon) SpoonFactory.getInstance()).getShell(), "Error", "Error creating visualization", e); //$NON-NLS-1$
    }
  }


  public void visualize() throws ModelerException{
    try{
      openVisualizer();
    } catch(Exception e){
      logger.info("Error visualizing", e);
      throw new ModelerException(e);
    }
  }


  public void publish() throws ModelerException{
    String publishingFile = workspace.getFileName();
    int treeDepth = 0;
    DatabaseMeta databaseMeta = ((ISpoonModelerSource) workspace.getModelSource()).getDatabaseMeta();
    boolean checkDatasources = true;
    boolean showServerSelection = true;
    boolean showFolders = false; 
    boolean showCurrentFolder = false;
    String serverPathTemplate = "{path}" + RepositoryFile.SEPARATOR + //$NON-NLS-1$
      "resources" + RepositoryFile.SEPARATOR + "metadata"; //$NON-NLS-1$ //$NON-NLS-2$
    String databaseName = PublisherHelper.getBiServerCompatibleDatabaseName(workspace.getDatabaseName());
    String extension = ".xmi"; //$NON-NLS-1$
    String filename = workspace.getModelName();
    workspace.getWorkspaceHelper().populateDomain(workspace);
    boolean isExistentDatasource = false;//this is wrong - TO DO 
    String fileName = PublisherHelper.publish(workspace, publishingFile, treeDepth, databaseMeta, filename, 
        checkDatasources, false, showFolders, showCurrentFolder, isExistentDatasource ,serverPathTemplate, extension, databaseName);
      
    workspace.getModel().setName(fileName);
    workspace.setDirty(true);
  }

  public void setSourceNameForCheck(String name){

    boolean isVisible = (name == null || "".equals(name.toString()) || name.equals(ModelerMessagesHolder.getMessages().getString("ModelerController.Datasource")));
    XulVbox messageBox = (XulVbox) document.getElementById("main_message"); //$NON-NLS-1$
    messageBox.setVisible(isVisible);

    XulComponent addFieldButton = document.getElementById("addField"); //$NON-NLS-1$
    addFieldButton.setDisabled(isVisible);

    document.getElementById("datasource_button").setVisible(isVisible);
    document.getElementById("relational_datasource_button").setVisible(isVisible);

  }

  public void openVisualizer() {

    workspace.getModel().validateTree();
    if (workspace.isValid() == false) {
      showValidationMessages();
      return;
    }

    try {
      VisualizationManager theManager = VisualizationManager.getInstance();
      IVisualization theVisualization = theManager.getVisualization(visualizationList.getSelectedItem());
      if (workspace.getFileName() == null) { //temp model
        theVisualization.createVisualizationFromModel(workspace, true);
      } else {
        theVisualization.createVisualizationFromModel(workspace, false);
      }
      Spoon.getInstance().enableMenus();
      if (theVisualization != null) {
        // TODO: Find a better name for the cube, maybe just workspace name?
      }
    } catch (Exception e) {
      logger.error("Error visualizing", e);
    }
  }



  public List<String> getVisualizationNames() {
  	if(this.visualizationNames == null) {
  		VisualizationManager theManager = VisualizationManager.getInstance();
  		this.visualizationNames = theManager.getVisualizationNames();
  	}
  	return this.visualizationNames;
  }


  public boolean saveWorkspace( String fileName ) throws ModelerException {
    workspace.getModel().validateTree();
    if (workspace.isValid() == false) {
      showValidationMessages();
      return false;
    }
    ModelerWorkspaceUtil.saveWorkspace(workspace, fileName);
    workspace.setFileName(fileName);
    workspace.setDirty(false);
    workspace.setTemporary(false);
    return true;
  }

}
