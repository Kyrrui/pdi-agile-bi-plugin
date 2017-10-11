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

package org.pentaho.di.core.lifecycle.pdi;

import org.apache.commons.lang.ObjectUtils.Null;
import org.eclipse.swt.widgets.Display;
import org.pentaho.agilebi.modeler.util.ModelerSourceFactory;
import org.pentaho.agilebi.platform.JettyServer;
import org.pentaho.agilebi.spoon.KettleModelerSource;
import org.pentaho.agilebi.spoon.OutputStepModelerSource;
import org.pentaho.agilebi.spoon.perspective.AgileBiInstaPerspective;
import org.pentaho.agilebi.spoon.perspective.AgileBiModelerPerspective;
import org.pentaho.agilebi.spoon.perspective.AgileBiSpoonInstaPlugin;
import org.pentaho.agilebi.spoon.visualizations.IVisualization;
import org.pentaho.agilebi.spoon.visualizations.VisualizationManager;
import org.pentaho.di.core.annotations.LifecyclePlugin;
import org.pentaho.di.core.gui.GUIOption;
import org.pentaho.di.core.gui.SpoonFactory;
import org.pentaho.di.core.lifecycle.LifeEventHandler;
import org.pentaho.di.core.lifecycle.LifecycleException;
import org.pentaho.di.core.lifecycle.LifecycleListener;
import org.pentaho.di.core.plugins.PluginClassTypeMapping;
import org.pentaho.di.ui.core.PropsUI;
import org.pentaho.di.ui.spoon.Spoon;
import org.pentaho.platform.api.engine.IPluginManager;
import org.pentaho.platform.engine.core.system.PentahoSystem;

import java.net.Socket;

@LifecyclePlugin(id = "AgileBiPlugin")
@PluginClassTypeMapping(classTypes = { GUIOption.class }, implementationClass = { Null.class })
public class AgileBILifecycleListener implements LifecycleListener, GUIOption<Object> {
  public static int consolePort;

  private JettyServer server = null;

  private boolean showTips;

  private boolean showRepositoryDialog;

  public void onStart(final LifeEventHandler arg0) throws LifecycleException {
    // turn off tooltips and the repositories dialog
    Spoon spoon = Spoon.getInstance();
    if (spoon.getStartupPerspective() != null
        && spoon.getStartupPerspective().equals(AgileBiInstaPerspective.PERSPECTIVE_ID)) {
      PropsUI props = spoon.getProperties();
      showTips = props.showToolTips();
      showRepositoryDialog = props.showRepositoriesDialogAtStartup();
      props.setShowToolTips(false);
      props.setRepositoriesDialogAtStartupShown(false);
    }

    new Thread(new Runnable() {

      @Override
      public void run() {
        try {
          int port = 9999;
          boolean portFound = false;
          int tries = 100;
          while (portFound == false && tries > 0) {
            port++;
            tries--;
            Socket sock = null;
            try {
              sock = new Socket("localhost", port);
            } catch (Exception e) {
              portFound = true;
            } finally {
              if (sock != null) {
                sock.close();
              }
            }
          }
          if (!portFound) {
            throw new IllegalStateException("Could not find an open port to start the Agile-BI server on");
          }

          AgileBILifecycleListener.consolePort = port;
          server = new JettyServer("localhost", port); //$NON-NLS-1$
          server.startServer();

          // Only initialize the Instaview perspective if the Instaview plugin is registered
          if (AgileBiSpoonInstaPlugin.isInstaviewRegistered(PentahoSystem.get(IPluginManager.class))) {
            AgileBiInstaPerspective.getInstance().onStart();
          }
        } catch (Exception e) {
          e.printStackTrace();
        }

        Display.getDefault().asyncExec(new Runnable() {

          @Override
          public void run() {
            ModelerSourceFactory.registerSourceType(OutputStepModelerSource.OUTPUTSTEP_SOURCE_TYPE,
                OutputStepModelerSource.class);
            ModelerSourceFactory.registerSourceType(KettleModelerSource.SOURCE_TYPE, KettleModelerSource.class);
            if (SpoonFactory.getInstance() != null) { // condition if for unit testing
              ((Spoon) SpoonFactory.getInstance()).addFileListener(AgileBiModelerPerspective.getInstance());

              for (IVisualization viz : VisualizationManager.getInstance().getVisualizations()) {
                ((Spoon) SpoonFactory.getInstance()).addFileListener(viz);
              }
            }
          }
        });
      }
    }).start();
  }

  public void onExit(LifeEventHandler arg0) throws LifecycleException {
    server.stopServer();
    AgileBiInstaPerspective.getInstance().shutdown();

    // reset tooltips and the repositories dialog
    Spoon spoon = Spoon.getInstance();
    if (spoon.getStartupPerspective() != null
        && spoon.getStartupPerspective().equals(AgileBiInstaPerspective.PERSPECTIVE_ID)) {
      PropsUI props = spoon.getProperties();
      props.setShowToolTips(showTips);
      props.setRepositoriesDialogAtStartupShown(showRepositoryDialog);
      spoon.saveSettings();
    }
  }

  @Override
  public String getLabelText() {
    return null;
  }

  @Override
  public Object getLastValue() {
    return null;
  }

  @Override
  public org.pentaho.di.core.gui.GUIOption.DisplayType getType() {
    return null;
  }

  @Override
  public void setValue(Object arg0) {
  }
}
