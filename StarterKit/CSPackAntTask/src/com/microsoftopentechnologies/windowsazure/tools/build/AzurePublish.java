/*
 Copyright 2015 Microsoft Open Technologies, Inc.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package com.microsoftopentechnologies.windowsazure.tools.build;

import java.io.*;
import java.net.URI;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import org.w3c.dom.Document;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

import com.microsoft.windowsazure.Configuration;
import com.microsoft.windowsazure.management.compute.models.DeploymentCreateParameters;
import com.microsoft.windowsazure.management.compute.models.DeploymentGetResponse;
import com.microsoft.windowsazure.management.compute.models.RoleInstance;
import com.microsoft.windowsazure.management.compute.models.ServiceCertificateListResponse.Certificate;
import com.microsoftopentechnologies.azuremanagementutil.model.InstanceStatus;
import com.microsoftopentechnologies.azuremanagementutil.model.StorageService;
import com.microsoftopentechnologies.azuremanagementutil.rest.WindowsAzureRestUtils;
import com.microsoftopentechnologies.azuremanagementutil.rest.WindowsAzureServiceManagement;
import com.microsoftopentechnologies.azuremanagementutil.rest.WindowsAzureStorageServices;

/*
 * A class representing Azure publish target
 */
public class AzurePublish extends Task {

	private String projectDir;
	private String publishSettingsPath;
	private String subscriptionId;
	private String cloudServiceName;
	private String region;
	private String storageAccountName;
	private String deploymentSlot;
	private String overwritePreviousDeployment;

	private String DEFAULT_FILE_NAME = "package.xml";
	private String WINAZURE_PACKAGE = "/project/target/parallel/windowsazurepackage";
	private String PACKAGE_TYPE = WINAZURE_PACKAGE + "/@packagetype";
	private String ROLE = "/ServiceConfiguration/Role";
	private String containerName = "antdeploy";
	private String cspckDefaultFileName = "WindowsAzurePackage.cspkg";
	private String cscfgDefaultFileName = "ServiceConfiguration.cscfg";
	private String samplePfxFileName = "SampleRemoteAccessPrivate.pfx";
	private String deployFolder = "deploy";
	private String defaultThumbprint = "875F1656A34D93B266E71BF19C116C39F16B6987";
	private String defaultPwd = "Password1";
	private String defaultDeploymentSlot = "Staging";

	public void setProjectDir(String projectDir) {
		this.projectDir = projectDir;
	}

	public String getPublishSettingsPath() {
		return publishSettingsPath;
	}

	public void setPublishSettingsPath(String publishSettingsPath) {
		this.publishSettingsPath = publishSettingsPath;
	}

	public String getSubscriptionId() {
		return subscriptionId;
	}

	public void setSubscriptionId(String subscriptionId) {
		this.subscriptionId = subscriptionId;
	}

	public String getCloudServiceName() {
		return cloudServiceName;
	}

	public void setCloudServiceName(String cloudServiceName) {
		this.cloudServiceName = cloudServiceName;
	}

	public String getStorageAccountName() {
		return storageAccountName;
	}

	public void setStorageAccountName(String storageAccountName) {
		this.storageAccountName = storageAccountName;
	}

	public String getRegion() {
		return region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public String getDeploymentSlot() {
		return deploymentSlot;
	}

	public void setDeploymentSlot(String deploymentSlot) {
		this.deploymentSlot = deploymentSlot;
	}

	public String getOverwritePreviousDeployment() {
		return overwritePreviousDeployment;
	}

	public void setOverwritePreviousDeployment(String overwritePreviousDeployment) {
		this.overwritePreviousDeployment = overwritePreviousDeployment;
	}

	/**
	 * WindowsAzurePackage constructor
	 */
	public AzurePublish() {

	}

	/**
	 * Initializes the task.
	 * Verifies that all package properties are valid
	 * and if they are not specified then initialize with default values. 
	 */
	private void initialize() throws BuildException {
		// Set projectDir to a default (based on basedir) if not set
		if (Utils.isNullOrEmpty(projectDir)) {
			projectDir = getProject().getBaseDir().getAbsolutePath();
		}

		if (Utils.isNotNullOrEmpty(publishSettingsPath)) {
			if (!Utils.isValidFilePath(publishSettingsPath)) {
				throw new BuildException("publishSettingsPath " +publishSettingsPath + " is not valid "
						+ "or points to a file that does not exist");
			}
		} else {
			throw new BuildException("publishSettingsPath is empty");
		}
		
		if (subscriptionId != null) {
			subscriptionId = subscriptionId.trim();
		}

		if (Utils.isNotNullOrEmpty(cloudServiceName)) {
			cloudServiceName = cloudServiceName.trim();
		} else {
			throw new BuildException("cloudServiceName is empty");
		}

		if (region != null) {
			region = region.trim();
		}

		if (Utils.isNotNullOrEmpty(storageAccountName)) {
			storageAccountName = storageAccountName.trim();
		} else {
			throw new BuildException("storageAccountName is empty");
		}

		if (Utils.isNullOrEmpty(deploymentSlot)) {
			deploymentSlot = defaultDeploymentSlot;
		} else {
			deploymentSlot = deploymentSlot.trim();
		}
		
		if (Utils.isNullOrEmpty(overwritePreviousDeployment)) {
			overwritePreviousDeployment = "true";
		} else {
			overwritePreviousDeployment = overwritePreviousDeployment.trim();
		}
	}

	/**
	 * Executes the task
	 */
	public void execute() throws BuildException {
		// workaround for azure libraries class loading issues.
		ClassLoader thread = Thread.currentThread().getContextClassLoader();
		
		try {
			Thread.currentThread().setContextClassLoader(AzurePublish.class.getClassLoader());
			
			initialize();
			
			String packageXmlPath = this.projectDir + File.separator + DEFAULT_FILE_NAME;
			Document doc = XMLUtil.parseXMLFile(new File(packageXmlPath));
			PackageType type = XMLUtil.getPackageType(doc, PACKAGE_TYPE);

			if (PackageType.local == type) {
				this.log("Can not proceed with cloud deployment since package type is 'local'.");
				this.log("Try setting package type to 'cloud'");
				throw new BuildException("Invalid packaga type for cloud deployments");
			} else {
				this.log("Using Publish settings file : " + this.publishSettingsPath);
				File pubFile =  new File(this.publishSettingsPath);
				
				if (Utils.isNullOrEmpty(subscriptionId)) {
					try {
						subscriptionId = XMLUtil.getDefaultSubscription(pubFile);
					} catch (Exception e) {
						throw new BuildException(e);
					}
				}
				this.log("Using subscription id : " + subscriptionId);
				// Get configuration object needed to call Azure management service APIS
				Configuration configuration = null;
				try {
					configuration = WindowsAzureRestUtils.getConfiguration(pubFile, subscriptionId);
				} catch (Exception e) {
					throw new BuildException("Error: Failed to load publish settings, ensure"
							+ " publish settings file "+pubFile.getAbsolutePath()+ 
							" and subscriptionId "+subscriptionId+ " is valid");
				}
				
				// check Azure services can be invoked with configuration object.
				pingAzure(configuration);

				WindowsAzureServiceManagement instance = Utils.getServiceInstance();
				this.log("Creating cloud service : '" + cloudServiceName + "' if does not exists");
				instance.createCloudServiceIfNotExists(configuration, cloudServiceName, region);
				uploadSampleCertIfNotPresent(configuration, instance);
				StorageService storageAccount = Utils.createStorageAccountIfNotExists(
						configuration, instance, storageAccountName, region);
				String deploymentName = createDeploymentService(configuration, instance, storageAccount);
				this.log("Waiting for deployment to be ready...");
				DeploymentGetResponse deployment = waitForDeployment(configuration,
						cloudServiceName, instance, deploymentName);
				this.log("Status : " + deployment.getStatus().toString());
				String deploymentURL = deployment.getUri().toString();
				String serverAppName = XMLUtil.getFirstApplicationName(doc);
				if (!serverAppName.isEmpty()) {
					if (!deploymentURL.endsWith("/")) {
						deploymentURL += "/";
					}
					deploymentURL += serverAppName + "/";
				}
				this.log("Site URL : " + deploymentURL);
			}
		} catch (Exception e) {
			throw new BuildException(e);
		} finally {
			Thread.currentThread().setContextClassLoader(thread);
		}

	}

	/**
	 * API to check connectivity to Azure
	 * @param configuration
	 * @throws Exception
	 */
	public static void pingAzure(Configuration configuration) {
		try {
			WindowsAzureRestUtils.getLocations(configuration);
		} catch (Exception e) {
			throw new BuildException("Error: Failed to call Azure Management Service, check network and proxy settings. "+e);
		}
	}

	private void uploadPackage(WindowsAzureStorageServices storageservices,
			String cspckgTargetName) throws Exception {
		storageservices.createContainer(containerName);
		// create cspkg target name and uploadPackageService
		this.log("Uploading deployment package to storage account.");
		storageservices.putBlob(containerName, cspckgTargetName,
				new File(constructCspckFilePath()), null);
		this.log("Uploaded deployment package.");
	}

	private String createDeployment(Configuration configuration,
			WindowsAzureServiceManagement instance,
			StorageService storageAccount,
			String cspckgTargetName) throws Exception {
		// Deployment creation
		String storageAccountURL = storageAccount.
				getStorageAccountProperties().getEndpoints().get(0).toString();
		String cspkgUrl = String.format("%s%s/%s", storageAccountURL,
				containerName.toLowerCase(), cspckgTargetName);
		DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
		String deploymentName = String.format("%s%s%s", cloudServiceName,
				deploymentSlot, dateFormat.format(new Date()));
		this.log("Deployment name : " + deploymentName);

		this.log("Creating deployment...");
		ProgressBar progressBar = new ProgressBar(10000, "Creating deployment");
		Thread progressBarThread = new Thread(progressBar);
		progressBarThread.start();

		String requestId = createDeployment(instance, cspkgUrl, deploymentName, configuration);

		progressBarThread.interrupt();
		try {
			progressBarThread.join();
		} catch (InterruptedException e) {
			;
		}

		this.log("Waiting for deployment status...");
		Utils.waitForStatus(configuration, instance, requestId);
		this.log("Successfully created deployment.");
		return deploymentName;
	}

	private String createDeploymentService(Configuration configuration,
			WindowsAzureServiceManagement instance,
			StorageService storageAccount) throws Exception {
		String cspckgTargetName = String.format("%s_%s.cspkg", cloudServiceName, deploymentSlot);
		WindowsAzureStorageServices storageservices = new WindowsAzureStorageServices(
				storageAccount, storageAccount.getPrimaryKey());
		uploadPackage(storageservices, cspckgTargetName);
		String deploymentName = createDeployment(configuration, instance, storageAccount, cspckgTargetName);
		storageservices.deleteBlob(containerName, cspckgTargetName, null);
		return deploymentName;
	}

	private void uploadSampleCertIfNotPresent(Configuration configuration,
			WindowsAzureServiceManagement instance) throws Exception {
		File cscfgFile = new File(constructCscfgFilePath());
		List<String> roleList = XMLUtil.getRoleList(ROLE, cscfgFile);
		// Certificates
		Document cscfg = XMLUtil.parseXMLFile(cscfgFile);
		if (XMLUtil.isSampleCertUsedInRole(cscfg, roleList)) {
			boolean isPresent = false;
			List<Certificate> certList = instance.listCertificates(configuration, cloudServiceName);
			for (Certificate cert : certList) {
				if (cert.getThumbprint().equalsIgnoreCase(defaultThumbprint)) {
					isPresent = true;
					break;
				}
			}

			if (!isPresent) {
				instance.uploadCertificate(configuration, cloudServiceName,
						constructPfxFilePath(), defaultPwd);
				this.log("Uploaded sample certificate.");
			}
		}

		this.log("Note : Ensure custom certificates(if any) are uploaded to cloud service "
				+ "using portal, else deployment may fail.");
	}

	private String constructCspckFilePath() {
		String projectLocation = this.projectDir;
		return projectLocation + File.separator + deployFolder + File.separator + cspckDefaultFileName;
	}

	private String constructPfxFilePath() {
		String projectLocation = this.projectDir;
		return projectLocation + File.separator + "cert" + File.separator + samplePfxFileName;
	}

	private String constructCscfgFilePath() {
		String projectLocation = this.projectDir;
		return projectLocation + File.separator + cscfgDefaultFileName;
	}
	
	private String constructDeployCscfgFilePath() {
		String projectLocation = this.projectDir;
		return projectLocation + File.separator + deployFolder + File.separator + cscfgDefaultFileName;
	}

	public String createDeployment(WindowsAzureServiceManagement service, String cspkgUrl,
			String deploymentName, Configuration configuration) throws Exception {
		File cscfgFile = new File(constructDeployCscfgFilePath());
		byte[] cscfgBuff = new byte[(int) cscfgFile.length()];
		FileInputStream fileInputStream = new FileInputStream(cscfgFile);
		DataInputStream dis = new DataInputStream((fileInputStream));
		try {
			dis.readFully(cscfgBuff);
			dis.close();
		}
		finally {
			if (dis != null) {
				dis.close();
			}
			if (fileInputStream != null) {
				fileInputStream.close();
			}
		}
		DeploymentCreateParameters parameters = new DeploymentCreateParameters();
		parameters.setName(deploymentName);
		parameters.setPackageUri(new URI(cspkgUrl));
		parameters.setLabel(cloudServiceName);
		parameters.setConfiguration(new String(cscfgBuff));
		parameters.setStartDeployment(true);
		return service.createDeployment(configuration, cloudServiceName, deploymentSlot,
				parameters, overwritePreviousDeployment);
	}

	private DeploymentGetResponse waitForDeployment(Configuration configuration,
			String cloudservicename, WindowsAzureServiceManagement service,
			String deploymentName) throws Exception {
		// Start showing progress bar
		ProgressBar progressBar = new ProgressBar(20000, "Waiting for instances to be ready");
		Thread progressBarThread = new Thread(progressBar);
		progressBarThread.start();

		DeploymentGetResponse deployment = null;
		String status = null;
		do {
			Thread.sleep(20000);
			deployment = service.getDeployment(configuration, cloudservicename, deploymentName);

			for (RoleInstance instance : deployment.getRoleInstances()) {
				status = instance.getInstanceStatus();
				if (isRoleStatus(status)) {
					break;
				}
			}
		} while (status != null && !(isRoleStatus(status)));
		if (!InstanceStatus.ReadyRole.getInstanceStatus().equals(status)) {
			// Stop the progress bar in case of exception also
			progressBarThread.interrupt();
			try {
				progressBarThread.join();
			} catch (InterruptedException e) {
				;
			}
			throw new Exception(status);
		}

		// Stop the progress bar
		progressBarThread.interrupt();
		try {
			progressBarThread.join();
		} catch (InterruptedException e) {
			;
		}
		return deployment;
	}

	/**
	 * Returns true if
	 * role status is ReadyRole, CyclingRole, FailedStartingVM and UnresponsiveRole.
	 * @param status
	 * @return
	 */
	private boolean isRoleStatus(String status) {
		boolean isRoleStatus = false;
		if (InstanceStatus.ReadyRole.getInstanceStatus().equals(status)
				|| InstanceStatus.CyclingRole.getInstanceStatus().equals(status)
				|| InstanceStatus.FailedStartingVM.getInstanceStatus().equals(status)
				|| InstanceStatus.UnresponsiveRole.getInstanceStatus().equals(status)) {
			isRoleStatus = true;
		}
		return isRoleStatus;
	}
}
