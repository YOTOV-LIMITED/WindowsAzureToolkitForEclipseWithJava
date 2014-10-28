/**
* Copyright 2014 Microsoft Open Technologies, Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
*  you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*	 http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
*  distributed under the License is distributed on an "AS IS" BASIS,
*  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*  See the License for the specific language governing permissions and
*  limitations under the License.
*/
package com.microsoftopentechnologies.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.microsoft.windowsazure.Configuration;
import com.microsoft.windowsazure.core.OperationResponse;
import com.microsoft.windowsazure.core.OperationStatusResponse;
import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.management.compute.models.DeploymentCreateParameters;
import com.microsoft.windowsazure.management.compute.models.DeploymentGetResponse;
import com.microsoft.windowsazure.management.compute.models.DeploymentSlot;
import com.microsoft.windowsazure.management.compute.models.DeploymentUpdateStatusParameters;
import com.microsoft.windowsazure.management.compute.models.HostedServiceCheckNameAvailabilityResponse;
import com.microsoft.windowsazure.management.compute.models.HostedServiceCreateParameters;
import com.microsoft.windowsazure.management.compute.models.HostedServiceGetDetailedResponse;
import com.microsoft.windowsazure.management.compute.models.HostedServiceListResponse;
import com.microsoft.windowsazure.management.compute.models.HostedServiceListResponse.HostedService;
import com.microsoft.windowsazure.management.compute.models.ServiceCertificateCreateParameters;
import com.microsoft.windowsazure.management.compute.models.ServiceCertificateListResponse.Certificate;
import com.microsoft.windowsazure.management.compute.models.UpdatedDeploymentStatus;
import com.microsoft.windowsazure.management.models.AffinityGroupListResponse;
import com.microsoft.windowsazure.management.models.LocationsListResponse;
import com.microsoft.windowsazure.management.models.LocationsListResponse.Location;
import com.microsoft.windowsazure.management.models.SubscriptionGetResponse;
import com.microsoft.windowsazure.management.storage.models.CheckNameAvailabilityResponse;
import com.microsoft.windowsazure.management.storage.models.StorageAccount;
import com.microsoft.windowsazure.management.storage.models.StorageAccountCreateParameters;
import com.microsoft.windowsazure.management.storage.models.StorageAccountGetKeysResponse;
import com.microsoft.windowsazure.management.storage.models.StorageAccountGetResponse;
import com.microsoft.windowsazure.management.storage.models.StorageAccountListResponse;
import com.microsoftopentechnologies.exception.InvalidThumbprintException;
import com.microsoftopentechnologies.model.ModelFactory;
import com.microsoftopentechnologies.model.StorageService;
import com.microsoftopentechnologies.model.Subscription;
import com.microsoftopentechnologies.task.LoadStorageServiceTask;


public class WindowsAzureServiceManagement extends WindowsAzureServiceImpl {

	public WindowsAzureServiceManagement() throws InvalidThumbprintException {
		super();
		context = ModelFactory.createInstance();
	}

	public Subscription getSubscription(Configuration configuration)
			throws Exception, ServiceException {
		SubscriptionGetResponse response;
		response = WindowsAzureRestUtils.getSubscription(configuration);
		return SubscriptionTransformer.transform(response);
	}

	public HostedServiceGetDetailedResponse getHostedServiceWithProperties(
			Configuration configuration, String serviceName)
					throws Exception {
		try {
			HostedServiceGetDetailedResponse response = WindowsAzureRestUtils.
					getHostedServicesDetailed(configuration, serviceName);
			return response;
		} catch (Exception ex) {
			throw new Exception("Exception when getting storage keys", ex);
		}
	}

	public StorageService getStorageAccount(Configuration configuration, String serviceName)
			throws Exception, ServiceException {
		StorageService storageService = getStorageKeys(configuration, serviceName);
		StorageAccountGetResponse response = WindowsAzureRestUtils.getStorageAccount(configuration, serviceName);
		storageService.setStorageAccountProperties(response.getStorageAccount().getProperties());
		return storageService;
	}

	public boolean checkForStorageAccountDNSAvailability(
			Configuration configuration, final String storageAccountName)
					throws Exception, ServiceException {
		CheckNameAvailabilityResponse response = WindowsAzureRestUtils.checkStorageNameAvailability(configuration, storageAccountName);
		return response.isAvailable();
	}

	public boolean checkForCloudServiceDNSAvailability(
			Configuration configuration, final String hostedServiceName)
					throws Exception, ServiceException {
		HostedServiceCheckNameAvailabilityResponse response =
				WindowsAzureRestUtils.checkHostedServiceNameAvailability(configuration, hostedServiceName);
		return response.isAvailable();
	}

	public OperationStatusResponse getOperationStatus(
			Configuration configuration, String requestId)
					throws Exception, ServiceException {
		OperationStatusResponse response = WindowsAzureRestUtils.getOperationStatus(configuration, requestId);
		return response;
	}
	
	public synchronized List<StorageService> listStorageAccounts(Configuration configuration)
			throws Exception, ServiceException {
        List<StorageService> storageServices = new ArrayList<StorageService>();
        StorageAccountListResponse response;
        try {
            response = WindowsAzureRestUtils.getStorageServices(configuration);
        } catch (Exception ex) {
        	throw new Exception("Exception when getting storage services", ex);
        }
        
        if (response.getStorageAccounts() != null && response.getStorageAccounts().size() > 0) {
	        ExecutorService executorService = Executors.newCachedThreadPool();
	        
	        List<Callable<StorageService>> taskList = new ArrayList<Callable<StorageService>>();
	        
	        // Prepare tasks for parallel execution
	        for (StorageAccount ss : response.getStorageAccounts()) {
	        	LoadStorageServiceTask storage = new LoadStorageServiceTask();
	        	storage.setConfiguration(configuration);
	        	storage.setStorageAccount(ss);
	        	taskList.add(storage);
	        }
	        
	        // Execute tasks
	        // see if it is possible to gracefully ignore individual task failures
	        try {
	        	List<Future<StorageService>> taskResultList = null;
				try {
					taskResultList = executorService.invokeAll(taskList);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
	        	
				if (taskResultList != null) {
		        	for(Future<StorageService> taskResult : taskResultList) {
		        		// Get will block until time expires or until task completes
				    	StorageService storageService = taskResult.get(60, TimeUnit.SECONDS);
				    	storageServices.add(storageService);
		        	}
				}
	        } catch (TimeoutException timeoutException) {
	        	throw new Exception("Timed out occurred while getting storage services information, please try again", timeoutException);
	        } catch (Exception e) {
	        	throw new Exception("Exception when getting storage services", e);
	        } finally {
	        	executorService.shutdown();
	        }
        }
		return storageServices;
	}

	public static StorageService getStorageKeys(Configuration configuration, String serviceName)
			throws ServiceException, Exception {
		StorageAccountGetKeysResponse response = WindowsAzureRestUtils.getStorageKeys(configuration, serviceName);
		return new StorageService(serviceName, response);
	}

	public ArrayList<Location> listLocations(Configuration configuration)
			throws Exception, ServiceException {
		LocationsListResponse response = WindowsAzureRestUtils.getLocations(configuration);
		return response.getLocations();
	}

	/**
	 * Note: this method is not currently used
	 * @param configuration
	 * @return
	 * @throws WACommonException
	 */
	public AffinityGroupListResponse listAffinityGroups(Configuration configuration)
			throws Exception, ServiceException {
		AffinityGroupListResponse response = WindowsAzureRestUtils.listAffinityGroups(configuration);
		return response;
	}

	public ArrayList<HostedService> listHostedServices(Configuration configuration)
			throws Exception, ServiceException {
		HostedServiceListResponse response = WindowsAzureRestUtils.getHostedServices(configuration);
		return response.getHostedServices();
	}

	public List<Certificate> listCertificates(Configuration configuration, String serviceName)
			throws Exception, ServiceException {
		List<Certificate> certificates = WindowsAzureRestUtils.getCertificates(configuration, serviceName);
		return certificates;
	}


	public String createHostedService(Configuration configuration,
			HostedServiceCreateParameters hostedServiceCreateParameters) throws
			Exception, ServiceException {
		OperationResponse response = WindowsAzureRestUtils.createHostedService(
				configuration, hostedServiceCreateParameters);
		return response.getRequestId();
	}


	public String createStorageAccount(Configuration configuration,
			StorageAccountCreateParameters accountParameters)
					throws ServiceException, Exception {
		return WindowsAzureRestUtils.createStorageAccount(configuration, accountParameters).getRequestId();
	}


	public DeploymentGetResponse getDeployment(Configuration configuration,
			String serviceName, String deploymentName)
					throws Exception, ServiceException {
		DeploymentGetResponse response = WindowsAzureRestUtils.
				getDeployment(configuration, serviceName, deploymentName);
		return response;
	}

	public String deleteDeployment(Configuration configuration,
			String serviceName, String deploymentName)
					throws Exception, ServiceException {
		OperationResponse response = WindowsAzureRestUtils.
				deleteDeployment(configuration, serviceName, deploymentName, true);
		return response.getRequestId();
	}

	public String updateDeploymentStatus(Configuration configuration,
			String serviceName, String deploymentName, UpdatedDeploymentStatus status)
					throws ServiceException, Exception {
		DeploymentUpdateStatusParameters deploymentStatus = new DeploymentUpdateStatusParameters();
		deploymentStatus.setStatus(status);
		OperationResponse response = WindowsAzureRestUtils.updateDeploymentStatus(
				configuration, serviceName, deploymentName, deploymentStatus);
		return response.getRequestId();
	}

	public String createDeployment(Configuration configuration,
			String serviceName,
			String slotName,
			DeploymentCreateParameters parameters,
			String unpublish)
					throws Exception, ServiceException {

		DeploymentSlot deploymentSlot;
		if (DeploymentSlot.Staging.toString().equalsIgnoreCase(slotName)) {
			deploymentSlot = DeploymentSlot.Staging;
		} else if (DeploymentSlot.Production.toString().equalsIgnoreCase(slotName)) {
			deploymentSlot = DeploymentSlot.Production;
		} else {
			throw new Exception("Invalid deployment slot name");
		}
		OperationStatusResponse response;
		try {
			response = WindowsAzureRestUtils.createDeployment(configuration, serviceName, deploymentSlot, parameters);
			return response.getRequestId();
		} catch (ServiceException ex) {
			/*
			 * If delete deployment option is selected and
			 * conflicting deployment exists then unpublish
			 * deployment first and then again try to publish.
			 */
			if (unpublish.equalsIgnoreCase("true") && ex.getHttpStatusCode() == 409) {
				HostedServiceGetDetailedResponse hostedServiceDetailed = getHostedServiceWithProperties(configuration, serviceName);
				List<HostedServiceGetDetailedResponse.Deployment> list = hostedServiceDetailed.getDeployments();
				String deploymentName = "";
				for (int i = 0; i < list.size(); i++) {
					HostedServiceGetDetailedResponse.Deployment deployment = list.get(i);
					if (deployment.getDeploymentSlot().name().equalsIgnoreCase(slotName)) {
						deploymentName = deployment.getName();
					}
				}
				deleteDeployment(configuration, serviceName, deploymentName);
				response = WindowsAzureRestUtils.createDeployment(configuration, serviceName, deploymentSlot, parameters);

				return response.getRequestId();
			} else {
				throw ex;
			}
		}
	}

	public String addCertificate(Configuration configuration,
			String serviceName,
			ServiceCertificateCreateParameters createParameters)
					throws Exception, ServiceException {
		return WindowsAzureRestUtils.addCertificate(configuration, serviceName, createParameters).getRequestId();
	}
}
