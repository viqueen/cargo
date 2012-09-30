/*
 * ========================================================================
 *
 * Codehaus CARGO, copyright 2004-2011 Vincent Massol.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ========================================================================
 */
package org.codehaus.cargo.container.websphere;

import java.io.File;
import java.io.FileNotFoundException;

import org.codehaus.cargo.container.ContainerCapability;
import org.codehaus.cargo.container.configuration.LocalConfiguration;
import org.codehaus.cargo.container.deployable.Deployable;
import org.codehaus.cargo.container.internal.J2EEContainerCapability;
import org.codehaus.cargo.container.spi.AbstractInstalledLocalContainer;
import org.codehaus.cargo.container.spi.jvm.JvmLauncher;
import org.codehaus.cargo.util.CargoException;

/**
 * IBM WebSphere 8.5 container implementation.
 * 
 * @version $Id$
 */
public class WebSphere85xInstalledLocalContainer extends AbstractInstalledLocalContainer
{
    /**
     * Unique container id.
     */
    public static final String ID = "websphere85x";

    /**
     * Container name (human-readable name).
     */
    public static final String NAME = "WebSphere 8.5";

    /**
     * Capabilities.
     */
    private ContainerCapability capability = new J2EEContainerCapability();

    /**
     * {@inheritDoc}
     * @see AbstractInstalledLocalContainer#AbstractInstalledLocalContainer(org.codehaus.cargo.container.configuration.LocalConfiguration)
     */
    public WebSphere85xInstalledLocalContainer(LocalConfiguration configuration)
    {
        super(configuration);
    }

    /**
     * Implementation of {@link org.codehaus.cargo.container.LocalContainer#start()} to all
     * container extending this class must implement.
     * 
     * @param java the predefined JVM launcher to use to start the container
     * @throws Exception if any error is raised during the container start
     */
    @Override
    public void doStart(JvmLauncher java) throws Exception
    {
        String libExt = getFileHandler().append(getHome(), "lib/ext");
        for (String extraClasspath : getExtraClasspath())
        {
            String destinationFile = getFileHandler().append(
                libExt, getFileHandler().getName(extraClasspath));
            getFileHandler().copyFile(extraClasspath, destinationFile);
        }

        prepareJvmLauncher(java);

        java.setSystemProperty("com.ibm.CORBA.ConfigURL",
            new File(getConfiguration().getHome(),
                "properties/sas.client.props").toURI().toURL().toString());
        java.setSystemProperty("com.ibm.SSL.ConfigURL",
            new File(getConfiguration().getHome(),
                "properties/ssl.client.props").toURI().toURL().toString());

        java.setMainClass("com.ibm.ws.bootstrap.WSLauncher");

        java.addAppArguments("com.ibm.ws.management.tools.WsServerLauncher");
        java.addAppArgument(new File(getConfiguration().getHome(), "config"));
        java.addAppArguments(getConfiguration().getPropertyValue(WebSpherePropertySet.CELL));
        java.addAppArguments(getConfiguration().getPropertyValue(WebSpherePropertySet.NODE));
        java.addAppArguments(getConfiguration().getPropertyValue(WebSpherePropertySet.SERVER));

        int returnCode = java.execute();
        if (returnCode != 0)
        {
            throw new CargoException(
                "WebSphere cannot be started: return code was " + returnCode);
        }

        WebSphere85xInstalledLocalDeployer deployer = new WebSphere85xInstalledLocalDeployer(this);
        for (Deployable deployable : getConfiguration().getDeployables())
        {
            deployer.redeploy(deployable);
        }
    }

    /**
     * Implementation of {@link org.codehaus.cargo.container.LocalContainer#stop()} to all container
     * extending this class must implement.
     * 
     * @param java the predefined JVM launcher to use to stop the container
     * @throws Exception if any error is raised during the container stop
     */
    @Override
    public void doStop(JvmLauncher java) throws Exception
    {
        prepareJvmLauncher(java);

        java.setSystemProperty("com.ibm.SOAP.ConfigURL",
            new File(getConfiguration().getHome(),
                "properties/soap.client.props").toURI().toURL().toString());
        java.setSystemProperty("com.ibm.CORBA.ConfigURL",
            new File(getConfiguration().getHome(),
                "properties/sas.client.props").toURI().toURL().toString());
        java.setSystemProperty("com.ibm.SSL.ConfigURL",
            new File(getConfiguration().getHome(),
                "properties/ssl.client.props").toURI().toURL().toString());
        java.setSystemProperty("java.security.auth.login.config",
            new File(getConfiguration().getHome(),
                "properties/wsjaas_client.conf").getAbsolutePath());

        java.setMainClass("com.ibm.wsspi.bootstrap.WSPreLauncher");

        java.addAppArguments("-nosplash");
        java.addAppArguments("-application");
        java.addAppArguments("com.ibm.ws.bootstrap.WSLauncher");
        java.addAppArguments("com.ibm.ws.admin.services.WsServerStop");
        java.addAppArgument(new File(getConfiguration().getHome(), "config"));
        java.addAppArguments(getConfiguration().getPropertyValue(WebSpherePropertySet.CELL));
        java.addAppArguments(getConfiguration().getPropertyValue(WebSpherePropertySet.NODE));
        java.addAppArguments(getConfiguration().getPropertyValue(WebSpherePropertySet.SERVER));

        int returnCode = java.execute();
        if (returnCode != 0)
        {
            throw new CargoException(
                "WebSphere cannot be stopped: return code was " + returnCode);
        }

        String libExt = getFileHandler().append(getHome(), "lib/ext");
        for (String extraClasspath : getExtraClasspath())
        {
            String destinationFile = getFileHandler().append(
                libExt, getFileHandler().getName(extraClasspath));
            getFileHandler().delete(destinationFile);
        }
    }

    /**
     * Creates a JVM launcher with the IBM WebSphere classpath.
     * 
     * @return JVM launcher with the IBM WebSphere classpath.
     */
    public JvmLauncher createJvmLauncher()
    {
        JvmLauncher java = createJvmLauncher(false);
        try
        {
            prepareJvmLauncher(java);
        }
        catch (FileNotFoundException e)
        {
            throw new CargoException("Cannot create JVM launcher", e);
        }
        return java;
    }

    /**
     * Prepares a JVM launcher for IBM WebSphere.
     * 
     * @param java JVM launcher to prepare for IBM WebSphere.
     * @throws FileNotFoundException If some of the classpath elements are missing.
     */
    protected void prepareJvmLauncher(JvmLauncher java) throws FileNotFoundException
    {
        File javaLib = new File(getJavaHome(), "lib");
        File serverLib = new File(getHome(), "lib/native");
        if (!serverLib.isDirectory())
        {
            throw new FileNotFoundException("Directory " + serverLib + " does not exist");
        }
        File[] serverLibContents = serverLib.listFiles();
        if (serverLibContents == null || serverLibContents.length != 1)
        {
            throw new FileNotFoundException("Directory " + serverLib
                + " is supposed to have only one sub-folder (with the OS name)");
        }
        serverLib = serverLibContents[0];
        serverLibContents = serverLib.listFiles();
        if (serverLibContents == null || serverLibContents.length != 1)
        {
            throw new FileNotFoundException("Directory " + serverLib
                + " is supposed to have only one sub-folder (with the processor type)");
        }
        serverLib = serverLibContents[0];

        String path = System.getenv("PATH");
        if (path == null || !path.contains(serverLib.getAbsolutePath()))
        {
            throw new CargoException("The PATH environment variable does not contain " + serverLib);
        }

        java.setSystemProperty("java.library.path",
            serverLib.getAbsolutePath().replace(File.separatorChar, '/'));
        java.setSystemProperty("java.endorsed.dirs",
            new File(getHome(), "endorsed_apis").getAbsolutePath().replace(File.separatorChar, '/')
            + File.pathSeparatorChar
            + new File(javaLib, "endorsed").getAbsolutePath().replace(File.separatorChar, '/'));
        java.setSystemProperty("was.install.root",
            new File(getHome()).getAbsolutePath().replace(File.separatorChar, '/'));
        java.setSystemProperty("WAS_HOME",
            new File(getHome()).getAbsolutePath().replace(File.separatorChar, '/'));
        java.setSystemProperty("user.install.root",
            new File(getConfiguration().getHome()).getAbsolutePath()
                .replace(File.separatorChar, '/'));

        addToolsJarToClasspath(java);
        java.addClasspathEntries(new File(getConfiguration().getHome(), "properties"));
        java.addClasspathEntries(new File(getHome(), "properties"));
        java.addClasspathEntries(new File(getHome(), "lib/startup.jar"));
        java.addClasspathEntries(new File(getHome(), "lib/bootstrap.jar"));
        java.addClasspathEntries(new File(getHome(), "lib/lmproxy.jar"));
        java.addClasspathEntries(new File(getHome(), "lib/urlprotocols.jar"));
        java.addClasspathEntries(new File(getHome(), "deploytool/itp/batchboot.jar"));
        java.addClasspathEntries(new File(getHome(), "deploytool/itp/batch2.jar"));
    }

    /**
     * {@inheritDoc}
     */
    public String getId()
    {
        return ID;
    }

    /**
     * @return Java home.
     */
    public String getJavaHome()
    {
        return super.getJavaHome();
    }

    /**
     * {@inheritDoc}
     */
    public String getName()
    {
        return NAME;
    }

    /**
     * {@inheritDoc}
     */
    public ContainerCapability getCapability()
    {
        return this.capability;
    }
}