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

package org.pentaho.agilebi.spoon.publish;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.ws.rs.core.MediaType;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.dom4j.DocumentHelper;
import org.jfree.util.Log;
import org.pentaho.agilebi.modeler.ModelerException;
import org.pentaho.agilebi.modeler.ModelerPerspective;
import org.pentaho.agilebi.modeler.ModelerWorkspace;
import org.pentaho.agilebi.modeler.util.ISpoonModelerSource;
import org.pentaho.database.IDatabaseDialect;
import org.pentaho.database.model.DatabaseAccessType;
import org.pentaho.database.model.DatabaseConnection;
import org.pentaho.database.model.IDatabaseType;
import org.pentaho.database.service.DatabaseDialectService;
import org.pentaho.database.util.DatabaseTypeHelper;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.database.DatabaseInterface;
import org.pentaho.di.core.database.DatabaseMeta;
import org.pentaho.di.core.exception.KettleDatabaseException;
import org.pentaho.di.core.gui.SpoonFactory;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.metadata.model.LogicalModel;
import org.pentaho.metadata.model.concept.types.LocalizedString;
import org.pentaho.metadata.util.MondrianModelExporter;
import org.pentaho.platform.api.engine.PentahoAccessControlException;
import org.pentaho.platform.api.repository2.unified.RepositoryFile;
import org.pentaho.platform.dataaccess.datasource.wizard.service.ConnectionServiceException;
import org.pentaho.platform.plugin.services.importer.PlatformImportException;
import org.pentaho.platform.repository2.unified.webservices.RepositoryFileTreeDto;
import org.pentaho.platform.util.client.PublisherUtil;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.api.json.JSONConfiguration;
import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataMultiPart;

/**
 * A utility class for publishing models to a BI server. Also helps synchronize database connections.
 * @author jamesdixon
 * modified tyler band to support REST service calls to publish xmi, xanalyzer, xml, and kbd files
 *
 */
public class ModelServerPublish {

  private static final String REST_NAME_PARM = "?name=";

  private static final String MONDRIAN_POST_ANALYSIS_URL = "plugin/data-access/api/mondrian/postAnalysis";

  private static final String EXTENSION_XMI = ".xmi";

  private static final String PLUGIN_DATA_ACCESS_API_CONNECTION_ADD = "plugin/data-access/api/connection/add";

  private static final String PLUGIN_DATA_ACCESS_API_CONNECTION_UPDATE = "plugin/data-access/api/connection/update";

  private static final String DATA_ACCESS_API_CONNECTION_GET = "plugin/data-access/api/connection/getresponse";

  private static final String DATA_ACCESS_API_CONNECTION_LIST = "plugin/data-access/api/connection/list";

  private static final String REPO_FILES_PUBLISH = "api/repo/publish/publishfile";

  public static final int PUBLISH_UNKNOWN_PROBLEM = -1;

  public static final int PUBLISH_FILE_EXISTS = 1;

  public static final int PUBLISH_FAILED = 2;

  public static final int PUBLISH_SUCCESS = 3;

  public static final int PUBLISH_INVALID_PASSWORD = 4;

  public static final int PUBLISH_INVALID_USER_OR_PASSWORD = 5;

  public static final int PUBLISH_DATASOURCE_PROBLEM = 6;

  public static final int PUBLISH_CATALOG_EXISTS = 8;

  public static final int REMOTE_CONNECTION_MISSING = 1;

  public static final int REMOTE_CONNECTION_DIFFERENT = 2;

  public static final int REMOTE_CONNECTION_SAME = 4;

  public static final int REMOTE_CONNECTION_MUST_BE_JNDI = 8;

  private static final int DATASOURCE_DRIVER_MISSING = 9;

  private BiServerConnection biServerConnection;

  private DatabaseConnection remoteConnection;

  private ModelerWorkspace model;

  private int serviceClientStatus = 0;

  //TODO: find a better way to communicate the UI delegate
  public static PublishOverwriteDelegate overwriteDelegate;

  private Client client = null;

  public ModelServerPublish() {
    // get information about the remote connection
    ClientConfig clientConfig = new DefaultClientConfig();
    clientConfig.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);
    this.client = Client.create(clientConfig);
  }

  public ModelServerPublish(BiServerConnection aBiServerConnection) {
    super();
    this.setBiServerConnection(aBiServerConnection);
  }

  /**
   * Lists the database connections that are available on the current BI server
   * @return
   * @throws ConnectionServiceException
   */
  public List<DatabaseConnection> listRemoteConnections() throws ConnectionServiceException {
    DatabaseConnection[] connectionArray = null;
    List<DatabaseConnection> response = new ArrayList<DatabaseConnection>();
    String storeDomainUrl = biServerConnection.getUrl() + DATA_ACCESS_API_CONNECTION_LIST;
    WebResource resource = client.resource(storeDomainUrl);

    try {
      connectionArray = resource.type(MediaType.APPLICATION_JSON).get(DatabaseConnection[].class);
      response = Arrays.asList(connectionArray);
    } catch (Exception e) {
      Log.error(e.getMessage(),e);
    }
    return response;
  }

  /**
   * Returns the remote connection. If the force flag is set the connection is 
   * always refreshed from the remote BI server. If the force flag is not set
   * a cached connection is returned.
   * @return
   */
  public DatabaseConnection getRemoteConnection(String connectionName, boolean force) {
    if (remoteConnection == null || force) {
      // get information about the remote connection
      String storeDomainUrl = biServerConnection.getUrl() + DATA_ACCESS_API_CONNECTION_GET +REST_NAME_PARM+ connectionName;
      WebResource resource = client.resource(storeDomainUrl);
      ClientResponse response;
      try {
       response  = resource
        		.type(MediaType.APPLICATION_JSON)
        		.type(MediaType.APPLICATION_XML)        	
        		.get(ClientResponse.class);
       if(response.getStatus() == 200){
         remoteConnection = response.getEntity(DatabaseConnection.class); 
       } else {
         Log.error(response.getEntity(String.class));
       }
      } catch (Exception ex) {
        Log.error(ex.getMessage());
        remoteConnection = null;
      }
    }
    return remoteConnection;

  }

  /**
   * Compares a provided DatabaseMeta with the database connections available on the current BI server.
   * Returns the result of the comparison - missing, same, different.
   * This only works for native connections (JNDI)
   * @param databaseMeta
   * @return
   * @throws ConnectionServiceException
   * @throws KettleDatabaseException
   */
  public int compareDataSourceWithRemoteConnection(DatabaseMeta databaseMeta) throws ConnectionServiceException,
      KettleDatabaseException {

    int result = 0;
    if (databaseMeta.getAccessType() != DatabaseMeta.TYPE_ACCESS_NATIVE) {
      result += REMOTE_CONNECTION_MUST_BE_JNDI;
      return result;
    }

    // compare the local database meta with the remote connection
    String connectionName = PublisherHelper.getBiServerCompatibleDatabaseName(databaseMeta.getName());
    DatabaseConnection connection = getRemoteConnection(connectionName, false);
    if (connection == null) {
      // the connection does not exist (with the same name) on the remote BI server 
      result += REMOTE_CONNECTION_MISSING;
      return result;
    }
    // see if the driver, url, and user are the same for both connections...
    String dbName = databaseMeta.getDatabaseInterface().getDatabaseName();
    String userName = databaseMeta.getUsername();
    String driverClass = databaseMeta.getDriverClass();
    boolean dbMatch = dbName.equalsIgnoreCase(connection.getDatabaseName());
    
    boolean userMatch = (userName == null && connection != null && connection.getUsername() == null)
        || userName.equals(connection.getUsername());
    DatabaseDialectService dds = new DatabaseDialectService();
       
    boolean driverMatch = false;
    if(dds != null){
    IDatabaseDialect dialect = dds.getDialect(connection);
      if(dialect != null){      
        String remoteDriverClass = dialect.getDriverClass(connection);
        driverMatch =  (driverClass == null && remoteDriverClass == null)
            || driverClass.equals(remoteDriverClass);
      }
    }
    // return 'same' or 'different'
    if (dbMatch && userMatch && driverMatch) {
      result += REMOTE_CONNECTION_SAME;
    } else {
      result += REMOTE_CONNECTION_DIFFERENT;
    }

    return result;
  }

  /**
   * Uses /repo/files/publish service to create the file
   * @param repositoryPath
   * @param files
   * @param showFeedback
   * @return
   */
  public int publishFile(String repositoryPath, File[] files, boolean showFeedback)
      throws PentahoAccessControlException {
    
    boolean overwrite = false;
    int result = ModelServerPublish.PUBLISH_FAILED;

    try {
      for (File fileIS : files) {
        while (true) {
          ClientResponse response = attemptPublish(fileIS, repositoryPath, overwrite);
          if (response != null) {
            String responseCodeStr = response.getEntity(String.class);
            Log.info("Response was " + responseCodeStr);
            if (response.getStatus() == 200) {
              result = ModelServerPublish.PUBLISH_SUCCESS;
              break;
            } else {
              int responseCode = Integer.parseInt(responseCodeStr);
              if (responseCode == PlatformImportException.PUBLISH_USERNAME_PASSWORD_FAIL) {
                result = ModelServerPublish.PUBLISH_INVALID_USER_OR_PASSWORD;
                showFeedback = true;
                break;
              } else if (responseCode == PlatformImportException.PUBLISH_CONTENT_EXISTS_ERROR && !overwrite) {
                  overwrite = overwriteDelegate.handleOverwriteNotification(fileIS.getName());
                  if (overwrite == false) {
                    result = PublisherUtil.FILE_EXISTS;
                    break;
                  } 
              } else {
                result = ModelServerPublish.PUBLISH_FAILED;
                break;
              }
            }
          }
        }
        overwrite = false;
      }
    } catch (Exception ex) {
      Log.error(ex.getMessage(),ex);
      result = ModelServerPublish.PUBLISH_FAILED;
    }
    if (showFeedback) {
      showFeedback(result);
    }
    return result;
  }
  
  private ClientResponse attemptPublish(File fileIS, String repositoryPath, boolean overwrite) throws FileNotFoundException {
      InputStream in = new FileInputStream(fileIS);
      String DEFAULT_PUBLISH_URL = biServerConnection.getUrl() + REPO_FILES_PUBLISH; //$NON-NLS-1$
      WebResource resource = client.resource(DEFAULT_PUBLISH_URL);
      
      FormDataMultiPart part = new FormDataMultiPart();
      part.field("importPath", repositoryPath + "/" + fileIS.getName(), MediaType.MULTIPART_FORM_DATA_TYPE)
          .field("fileUpload", in, MediaType.MULTIPART_FORM_DATA_TYPE)
          .field("overwriteFile", String.valueOf(overwrite), MediaType.MULTIPART_FORM_DATA_TYPE);
  
      part.getField("fileUpload").setContentDisposition(
          FormDataContentDisposition.name("fileUpload")
          .fileName(fileIS.getName()).build());
  
      Builder builder = resource
          .type(MediaType.MULTIPART_FORM_DATA)
          .accept(MediaType.TEXT_PLAIN);
      return builder.post(ClientResponse.class, part);
  }
  
  /**
   * Publishes a datasource to the current BI server
   * @param databaseMeta
   * @param update
   * @return
   * @throws KettleDatabaseException
   */
  private boolean publishDataSource(DatabaseMeta databaseMeta, boolean update) throws KettleDatabaseException,
      ConnectionServiceException {

    // create a new connection object and populate it from the databaseMeta
    DatabaseConnection connection = new DatabaseConnection();
    DatabaseDialectService dds = new DatabaseDialectService();
    DatabaseTypeHelper dth = new DatabaseTypeHelper(dds.getDatabaseTypes());
    DatabaseInterface intf = databaseMeta.getDatabaseInterface();
    connection.setName(PublisherHelper.getBiServerCompatibleDatabaseName(databaseMeta.getName()));
    connection.setPassword(databaseMeta.getPassword());    
    connection.setUsername(databaseMeta.getUsername());   
    connection.setDatabaseName(intf.getDatabaseName());   
    connection.setDatabasePort(String.valueOf(intf.getAttributes().getProperty("PORT_NUMBER")));
    connection.setHostname(databaseMeta.getHostname());
    connection.setForcingIdentifiersToLowerCase("N".equals(intf.getAttributes().getProperty("FORCE_IDENTIFIERS_TO_LOWERCASE"))?false:true); 
    connection.setQuoteAllFields("N".equals(intf.getAttributes().getProperty("QUOTE_ALL_FIELDS"))?false:true);    
    connection.setAccessType(DatabaseAccessType.NATIVE);
    IDatabaseType driver = dth.getDatabaseTypeByShortName(intf.getPluginId());
    connection.setDatabaseType(driver);        
    return updateConnection(connection, update);
  }

  /**
   * Jersey call to add or update connection
   * @param connection
   * @param update
   * @return
   */
  private boolean updateConnection(DatabaseConnection connection, boolean update) {   
    String storeDomainUrl;
    try {
      if (update) {
        storeDomainUrl = biServerConnection.getUrl() + PLUGIN_DATA_ACCESS_API_CONNECTION_UPDATE;
      } else {

        storeDomainUrl = biServerConnection.getUrl() + PLUGIN_DATA_ACCESS_API_CONNECTION_ADD;
      }
      WebResource resource = client.resource(storeDomainUrl);
      Builder builder = resource
    		  .type(MediaType.APPLICATION_JSON)
    		  .entity(connection);
    		  ClientResponse resp = builder.post(ClientResponse.class);
    		  if(resp != null && resp.getStatus() != 200){
    		    return false;
    		  }
    } catch (Exception ex) {    	
      Log.error(ex.getMessage());
      return false;
    }
    return true;
  }

  /**
   * Jersey call to use the put service to load a mondrain file into the Jcr repsoitory
   * @param mondrianFile
   * @param catalogName
   * @param datasourceInfo
   * @param overwriteInRepos
   * @throws Exception
   */
  public int publishMondrainSchema(InputStream mondrianFile, String catalogName, String datasourceInfo,
      boolean overwriteInRepos) throws Exception {
    String storeDomainUrl = biServerConnection.getUrl() + MONDRIAN_POST_ANALYSIS_URL;
    WebResource resource = client.resource(storeDomainUrl);
    String parms = "Datasource=" + datasourceInfo;
    int response = ModelServerPublish.PUBLISH_FAILED;
    FormDataMultiPart part = new FormDataMultiPart();
    part.field("parameters", parms, MediaType.MULTIPART_FORM_DATA_TYPE)
        .field("uploadAnalysis", mondrianFile, MediaType.MULTIPART_FORM_DATA_TYPE)
        .field("catalogName", catalogName, MediaType.MULTIPART_FORM_DATA_TYPE)
        .field("overwrite", overwriteInRepos ? "true" : "false", MediaType.MULTIPART_FORM_DATA_TYPE)
        .field("xmlaEnabledFlag", "true", MediaType.MULTIPART_FORM_DATA_TYPE);

    // If the import service needs the file name do the following.
    part.getField("uploadAnalysis").setContentDisposition(
        FormDataContentDisposition.name("uploadAnalysis").fileName(catalogName).build());
    try {
      ClientResponse resp = resource
    		  .type(MediaType.MULTIPART_FORM_DATA_TYPE)
    		  .post(ClientResponse.class, part);
     String entity = null;
     if (resp != null && resp.getStatus() == 200){
       entity = resp.getEntity(String.class);
       if(entity.equals(String.valueOf(ModelServerPublish.PUBLISH_CATALOG_EXISTS))){
           response = ModelServerPublish.PUBLISH_CATALOG_EXISTS;
       } else {
           response = Integer.parseInt(entity);
       }
     } else {
       Log.info(resp);
     }
    } catch (Exception ex) {
      Log.error(ex.getMessage());
    }
    return response;
  }

  /**
   * Jersey call to use the put service to load a metadataFile file into the Jcr repsoitory
   * @param metadataFile
   * @param domainId is fileName
   * @throws Exception
   * return code to detrmine next step
   */
  public int publishMetaDataFile(InputStream metadataFile, String domainId) throws Exception {
    String storeDomainUrl = biServerConnection.getUrl() + "plugin/data-access/api/metadata/import";
    WebResource resource = client.resource(storeDomainUrl);

    int response = ModelServerPublish.PUBLISH_FAILED;
    FormDataMultiPart part = new FormDataMultiPart();
    part.field("domainId", domainId, MediaType.MULTIPART_FORM_DATA_TYPE)
      .field("metadataFile", metadataFile, MediaType.MULTIPART_FORM_DATA_TYPE);
    part.getField("metadataFile").setContentDisposition(
        FormDataContentDisposition.name("metadataFile")
        .fileName(domainId).build());
    try {
      ClientResponse resp = resource
          .type(MediaType.MULTIPART_FORM_DATA_TYPE)
          .put(ClientResponse.class, part);
      if(resp != null && resp.getStatus() == 200){
        response = ModelServerPublish.PUBLISH_SUCCESS;
      }
    } catch (Exception ex) {
      Log.error(ex.getMessage());      
    }
    return response;
  }


  /**
   * Publishes the specified file, model, schema, and connection to the current BI server
   * using new REST Services
   * @param schemaName
   * @param jndiName
   * @param modelName
   * @param repositoryPath
   * @param selectedPath
   * @param publishDatasource
   * @param showFeedback
   * @param isExistentDatasource
   * @param publishFile (e.g. XANALYZER)
   * @param publishModelFileName
   * @throws Exception
   */
  public void publishToServer(String schemaName, 
      String jndiName, String modelName, String repositoryPath,
      String selectedPath, boolean publishDatasource, 
      boolean showFeedback, boolean isExistentDatasource,
      boolean publishFile,
      String publishModelFileName) throws Exception {

    String publishModelXmiFileName = publishModelFileName;
    if(publishFile){
      File files[] = { new File(publishModelFileName) };
      int result = publishFile(selectedPath, files, false);
      if(result != ModelServerPublish.PUBLISH_SUCCESS){
        if(result ==  ModelServerPublish.PUBLISH_FILE_EXISTS || result == ModelServerPublish.PUBLISH_INVALID_USER_OR_PASSWORD) {
          return;//user has replied no do not overwrite
        } else {
          throw new Exception(BaseMessages.getString(this.getClass(), "ModelServerPublish.Publish.Failed"));
        }
      }
      publishModelXmiFileName = convertFileNameToXmi(publishModelFileName,modelName);
    }
    
    if (publishDatasource) {
      DatabaseMeta databaseMeta = ((ISpoonModelerSource) model.getModelSource()).getDatabaseMeta();
      publishDataSource(databaseMeta, isExistentDatasource); 
    }
    boolean overwriteInRepository = false;
    
    int result = publishOlapSchemaToServer(schemaName, jndiName, modelName, selectedPath, overwriteInRepository,
        showFeedback, isExistentDatasource, publishModelFileName);
    //only publish if schema is success
    if (result == ModelServerPublish.PUBLISH_SUCCESS) {
      publishMetaDatafile(publishModelXmiFileName, modelName+EXTENSION_XMI);
    }

  }

  private String convertFileNameToXmi(String publishModelFileName, String modelName) {
    int index = publishModelFileName.lastIndexOf(RepositoryFile.SEPARATOR);
    //windows fix
    if(index < 0){
      index = publishModelFileName.lastIndexOf("\\");
    }
    String filePath = publishModelFileName.substring(0,index+1);
    return filePath + modelName + EXTENSION_XMI;
  }

  /**
   * reports will be removed from agile-bi in future release
   * @param theXmiPublishingPath
   * @param thePrptPublishingPath
   * @param publishDatasource
   * @param isExistentDatasource
   * @param publishXmi
   * @param xmi
   * @param prpt
   * @throws Exception
   * 
   */
  @Deprecated
  public void publishPrptToServer(String theXmiPublishingPath, String thePrptPublishingPath, boolean publishDatasource,
      boolean isExistentDatasource, boolean publishXmi, String xmi, String prpt) throws Exception {

    File thePrpt[] = { new File(prpt) };
    int result = publishFile(thePrptPublishingPath, thePrpt, !publishXmi /*show feedback here if not publishing xmi*/);
    if (result != PublisherUtil.FILE_ADD_SUCCESSFUL) {
    	showFeedback(result);
      return;
    }

    if (publishXmi) {
      File theXmi[] = { new File(xmi) };
      //publishFile(theXmiPublishingPath, theXmi, true);
      InputStream metadataFile = new FileInputStream(model.getFileName());     
      publishMetaDataFile(metadataFile, model.getDomain().getId() );
    }
    if (publishDatasource) {
      DatabaseMeta databaseMeta = ((ISpoonModelerSource) model.getModelSource()).getDatabaseMeta();
      publishDataSource(databaseMeta, isExistentDatasource);
    }
  }

  /**
   * find a matching file/path combination by going back to server fileTree and matching name
   * @param path
   * @param name
   * @return true if file exists on path
   */
  public boolean checkForExistingFile(String path, String name) {
    boolean ans = false;
    if (path == null || name == null) {
      Log.error("path ["+path+"] and name ["+name+"] cannot be null");
      return false;
    }    
    try {
      RepositoryFileTreeDto tree = fetchRepositoryFileTree(path, -1, null , false);
      if (tree != null && tree.getFile() != null && !tree.getChildren().isEmpty()) {
        for(RepositoryFileTreeDto file :tree.getChildren()){
          if (!file.getFile().isFolder()) {
            if (file.getFile().getName().equals(name)) {             
              ans = true;
              break;
            }
          }        
        }
      }

    } catch (Exception e) {
      Log.error(e.getMessage(),e);
    }
    return ans;
  }

  public boolean checkDataSource(boolean autoMode) throws KettleDatabaseException, ConnectionServiceException {
    // check the data source

    DatabaseMeta databaseMeta = ((ISpoonModelerSource) model.getModelSource()).getDatabaseMeta();
    int compare = compareDataSourceWithRemoteConnection(databaseMeta);

    String serverName = biServerConnection.getName();

    boolean nonJndi = (compare & ModelServerPublish.REMOTE_CONNECTION_MUST_BE_JNDI) > 0;
    boolean missing = (compare & ModelServerPublish.REMOTE_CONNECTION_MISSING) > 0;
    boolean different = (compare & ModelServerPublish.REMOTE_CONNECTION_DIFFERENT) > 0;
    //    boolean same = (compare | ModelServerPublish.REMOTE_CONNECTION_SAME) > 0;

    if (missing && !nonJndi) {
      if (!autoMode
          && !SpoonFactory
              .getInstance()
              .messageBox(
                  BaseMessages.getString(this.getClass(), "ModelServerPublish.Datasource.OkToPublish"), //$NON-NLS-1$
                  BaseMessages.getString(this.getClass(), "ModelServerPublish.MessageBox.Title", serverName), true, Const.INFO)) { //$NON-NLS-1$
        SpoonFactory
            .getInstance()
            .messageBox(
                BaseMessages.getString(this.getClass(), "ModelServerPublish.Datasource.PublishCancelled"), //$NON-NLS-1$ 
                BaseMessages.getString(this.getClass(), "ModelServerPublish.MessageBox.Title", serverName), false, Const.ERROR); //$NON-NLS-1$
        return false;
      }
      boolean ok = publishDataSource(databaseMeta, false);
      if (!autoMode && ok) {
        SpoonFactory
            .getInstance()
            .messageBox(
                BaseMessages.getString(this.getClass(), "ModelServerPublish.Datasource.Added"), //$NON-NLS-1$
                BaseMessages.getString(this.getClass(), "ModelServerPublish.MessageBox.Title", serverName), false, Const.INFO); //$NON-NLS-1$
      }
      return ok;
    } else if (missing && nonJndi) {
      if (!autoMode) {
        SpoonFactory
            .getInstance()
            .messageBox(
                BaseMessages.getString(this.getClass(), "ModelServerPublish.Datasource.NonJNDI"), //$NON-NLS-1$
                BaseMessages.getString(this.getClass(), "ModelServerPublish.MessageBox.Title", serverName), false, Const.ERROR); //$NON-NLS-1$
      }
      return false;
    } else if (different && !nonJndi) {
      if (!autoMode
          && !SpoonFactory
              .getInstance()
              .messageBox(
                  BaseMessages.getString(this.getClass(), "ModelServerPublish.Datasource.IsDifferent"), //$NON-NLS-1$
                  BaseMessages.getString(this.getClass(), "ModelServerPublish.MessageBox.Title", serverName), true, Const.INFO)) { //$NON-NLS-1$
        return false;
      }
      // replace the data source
      boolean ok = publishDataSource(databaseMeta, true);
      if (!autoMode && ok) {
        SpoonFactory
            .getInstance()
            .messageBox(
                BaseMessages.getString(this.getClass(), "ModelServerPublish.Datasource.Updated"), //$NON-NLS-1$
                BaseMessages.getString(this.getClass(), "ModelServerPublish.MessageBox.Title", serverName), false, Const.ERROR); //$NON-NLS-1$
      }
      return ok;
    } else if (different && nonJndi) {
      if (!autoMode) {
        SpoonFactory
            .getInstance()
            .messageBox(
                BaseMessages.getString(this.getClass(), "ModelServerPublish.Datasource.CannotUpdate"), //$NON-NLS-1$
                BaseMessages.getString(this.getClass(), "ModelServerPublish.MessageBox.Title", serverName), false, Const.ERROR); //$NON-NLS-1$
      }
      return false;
    }
    return false;

  }

  protected boolean showFeedback(int result) {
    String serverName = biServerConnection.getName();
    String fileName = this.model.getModelName();
    switch (result) {
    // String message, String rememberText, String rememberPropertyName )
      case ModelServerPublish.PUBLISH_CATALOG_EXISTS: {
        boolean ans = SpoonFactory.getInstance().overwritePrompt(
            BaseMessages.getString(this.getClass(), "Publish.Overwrite.Title"),
            BaseMessages.getString(this.getClass(), "Publish.Overwrite.Message", fileName),
            BaseMessages.getString(this.getClass(), "ModelServerPublish.Publish.CatalogExists"));
         return ans;
        
      }
      case ModelServerPublish.PUBLISH_DATASOURCE_PROBLEM: {
        SpoonFactory
            .getInstance()
            .messageBox(
                BaseMessages.getString(this.getClass(), "ModelServerPublish.Publish.DataSourceProblem"), //$NON-NLS-1$
                BaseMessages.getString(this.getClass(), "ModelServerPublish.MessageBox.Title", serverName), false, Const.ERROR); //$NON-NLS-1$
        break;
      }
      case ModelServerPublish.DATASOURCE_DRIVER_MISSING: {
        SpoonFactory
            .getInstance()
            .messageBox(
                BaseMessages.getString(this.getClass(), "ModelServerPublish.Publish.DriverMissing"), //$NON-NLS-1$
                BaseMessages.getString(this.getClass(), "ModelServerPublish.MessageBox.Title", serverName), false, Const.ERROR); //$NON-NLS-1$
        break;
      }
      case ModelServerPublish.PUBLISH_FAILED: {
        SpoonFactory
            .getInstance()
            .messageBox(
                BaseMessages.getString(this.getClass(), "ModelServerPublish.Publish.Failed"), //$NON-NLS-1$  
                BaseMessages.getString(this.getClass(), "ModelServerPublish.MessageBox.Title", serverName), false, Const.ERROR); //$NON-NLS-1$
        break;
      }
      case ModelServerPublish.PUBLISH_FILE_EXISTS: {
        SpoonFactory
            .getInstance()
            .messageBox(
                BaseMessages.getString(this.getClass(), "ModelServerPublish.Publish.FileExists"), //$NON-NLS-1$  
                BaseMessages.getString(this.getClass(), "ModelServerPublish.MessageBox.Title", serverName), false, Const.ERROR); //$NON-NLS-1$
        break;
      }
      case ModelServerPublish.PUBLISH_INVALID_PASSWORD: {
        SpoonFactory
            .getInstance()
            .messageBox(
                BaseMessages.getString(this.getClass(), "ModelServerPublish.Publish.BadPassword"), //$NON-NLS-1$  
                BaseMessages.getString(this.getClass(), "ModelServerPublish.MessageBox.Title", serverName), false, Const.ERROR); //$NON-NLS-1$
        break;
      }
      case ModelServerPublish.PUBLISH_INVALID_USER_OR_PASSWORD: {
        SpoonFactory
            .getInstance()
            .messageBox(
                BaseMessages.getString(this.getClass(), "ModelServerPublish.Errors.InvalidUser"), //$NON-NLS-1$  
                BaseMessages.getString(this.getClass(), "ModelServerPublish.MessageBox.Title", serverName), false, Const.ERROR); //$NON-NLS-1$
        break;
      }
      case ModelServerPublish.PUBLISH_SUCCESS: {
        SpoonFactory
            .getInstance()
            .messageBox(
                BaseMessages.getString(this.getClass(), "ModelServerPublish.Publish.Success"), //$NON-NLS-1$  
                BaseMessages.getString(this.getClass(), "ModelServerPublish.MessageBox.Title", serverName), false, Const.INFO); //$NON-NLS-1$
        break;
      }
      case ModelServerPublish.PUBLISH_UNKNOWN_PROBLEM: {
        SpoonFactory
            .getInstance()
            .messageBox(
                BaseMessages.getString(this.getClass(), "ModelServerPublish.Publish.UnknownProblem"), //$NON-NLS-1$  
                BaseMessages.getString(this.getClass(), "ModelServerPublish.MessageBox.Title", serverName), false, Const.ERROR); //$NON-NLS-1$
        break;
      }
    }
    return false;
  }

  public int publishOlapSchemaToServer(String schemaName, String jndiName, String modelName, String schemaFilePath,
      boolean overwriteInRepository, boolean showFeedback, boolean isExistentDatasource, String publishModelFileName)
      throws Exception {

    File modelsDir = new File("models"); //$NON-NLS-1$
    if (!modelsDir.exists()) {
      modelsDir.mkdir();
    }
    File publishFile;
    publishFile = new File(modelsDir, schemaName);
    publishFile.createNewFile();

    LogicalModel lModel = this.model.getLogicalModel(ModelerPerspective.ANALYSIS);

    MondrianModelExporter exporter = new MondrianModelExporter(lModel, LocalizedString.DEFAULT_LOCALE);
    String mondrianSchema = exporter.createMondrianModelXML();

    org.dom4j.Document schemaDoc = DocumentHelper.parseText(mondrianSchema);
    byte schemaBytes[] = schemaDoc.asXML().getBytes();

    if (!publishFile.exists()) {
      throw new ModelerException("Schema file does not exist"); //$NON-NLS-1$
    }

    //local file
    OutputStream out = new FileOutputStream(publishFile);
    out.write(schemaBytes);
    out.flush();
    out.close();
    //file to send to Jcr Repository
    InputStream schema = new ByteArrayInputStream(schemaBytes);

    int result = publishMondrainSchema(schema, modelName, jndiName, overwriteInRepository);
    if (result != ModelServerPublish.PUBLISH_SUCCESS && result != ModelServerPublish.PUBLISH_CATALOG_EXISTS) {
      showFeedback(result);
      return result;
    }
    result = handleModelOverwrite(jndiName, modelName, showFeedback, schemaDoc, result);

    return result;
  }

  /**
   * convert the publish name to an inputstream to pass to Jersey
   * @param publishModelFileName
   * @param domainId
   * @throws FileNotFoundException
   * @throws Exception
   */
  private void publishMetaDatafile(String publishModelFileName, String domainId) throws FileNotFoundException,
      Exception {
    InputStream metadataFile = new FileInputStream(publishModelFileName);
    publishMetaDataFile(metadataFile, domainId);
  }

  private int handleModelOverwrite(String jndiName, String modelName, boolean showFeedback,
      org.dom4j.Document schemaDoc, int result) throws Exception {
    int response = result;
    if (showFeedback) {
      if (showFeedback(result)) {
        //Handle Overwrite the byte stream has already be read - need to re-read
        byte schemaBytes2[] = schemaDoc.asXML().getBytes();
        InputStream schema2 = new ByteArrayInputStream(schemaBytes2);
        response = publishMondrainSchema(schema2, modelName, jndiName, true);
        showFeedback(response);
      }
    }
    return response;
  }

  /**
   * Sets the current BI server connection
   * @param biServerConnection
   */
  public void setBiServerConnection(BiServerConnection biServerConnection) {
    this.biServerConnection = biServerConnection;
    if (this.client != null) {
      client.addFilter(new HTTPBasicAuthFilter(biServerConnection.getUserId(), biServerConnection.getPassword()));
    }
  }

  /**
   * Sets the metadata model
   * @param model
   */
  public void setModel(ModelerWorkspace model) {
    this.model = model;
  }

  public int getServerConnectionStatus() {
    return serviceClientStatus;
  }

  /**
   * 
   * @param model
   * @param folderTreeDepth
   * @throws PublishException
   */
  public void createSolutionTree(final XulDialogPublishModel dialogModel, final int folderTreeDepth) throws PublishException {
    try {

      RepositoryFileTreeDto tree = fetchRepositoryFileTree(null,folderTreeDepth, null, null);
      if (tree != null && tree.getFile() != null) {
        SolutionObject root = new SolutionObject();
        root.add(new SolutionObject(tree, folderTreeDepth));
        dialogModel.setSolutions(root);
      }

    } catch (Exception e) {
      throw new PublishException("Error building solution document", e);
    }

  }

  /**
   * Use the Jersey call to get a list of repository file and folder objects
   * @param callback
   * @param depth
   * @param filter
   * @param showHidden
   */
  private RepositoryFileTreeDto fetchRepositoryFileTree(String path, Integer depth, String filter, Boolean showHidden) {
    RepositoryFileTreeDto fileTree = new RepositoryFileTreeDto();
    String url = this.biServerConnection.getUrl() + "api/repo/files/"; //$NON-NLS-1$
    String repoPath = ":";
    if (depth == null) {
      depth = -1;
    }
    if (filter == null) {
      filter = "*"; //$NON-NLS-1$
    }
    if (showHidden == null) {
      showHidden = Boolean.FALSE;
    }
    if(path == null){
      repoPath = ":";
    } else {
      repoPath = path;
    }
   
    if(path != null && path.contains(RepositoryFile.SEPARATOR)){
        repoPath = path.replace(RepositoryFile.SEPARATOR,":");
    } 
    url = url + repoPath + RepositoryFile.SEPARATOR +"tree?depth=" + depth +
        "&filter=" + filter + "&showHidden=" + showHidden; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$  
    WebResource resource = client.resource(url);
    try {
      String json = resource.accept(MediaType.APPLICATION_JSON_TYPE, MediaType.APPLICATION_XML_TYPE)
          .type(MediaType.TEXT_PLAIN_TYPE).get(String.class);
      ObjectMapper mapper = new ObjectMapper();
      fileTree = (RepositoryFileTreeDto) mapper.readValue(json, new TypeReference<RepositoryFileTreeDto>() {
      });
    } catch (Exception e) {
     Log.error(e.getMessage(),e);
    }
    return fileTree;
  }
 
}
