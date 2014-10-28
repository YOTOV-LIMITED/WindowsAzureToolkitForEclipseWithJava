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
package com.microsoftopentechnologies.deploy.wizard;

import java.net.URI;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.microsoft.windowsazure.Configuration;
import com.microsoft.windowsazure.core.OperationStatus;
import com.microsoft.windowsazure.core.OperationStatusResponse;
import com.microsoft.windowsazure.exception.ServiceException;
import com.microsoft.windowsazure.management.compute.models.HostedServiceCreateParameters;
import com.microsoft.windowsazure.management.compute.models.HostedServiceGetDetailedResponse;
import com.microsoft.windowsazure.management.compute.models.HostedServiceListResponse.HostedService;
import com.microsoft.windowsazure.management.compute.models.HostedServiceProperties;
import com.microsoft.windowsazure.management.compute.models.ServiceCertificateListResponse.Certificate;
import com.microsoft.windowsazure.management.models.LocationsListResponse.Location;
import com.microsoft.windowsazure.management.storage.models.StorageAccountCreateParameters;
import com.microsoft.windowsazure.management.storage.models.StorageAccountProperties;
import com.microsoftopentechnologies.deploy.util.PublishData;
import com.microsoftopentechnologies.exception.InvalidThumbprintException;
import com.microsoftopentechnologies.exception.RestAPIException;
import com.microsoftopentechnologies.model.KeyName;
import com.microsoftopentechnologies.model.StorageService;
import com.microsoftopentechnologies.model.StorageServices;
import com.microsoftopentechnologies.model.Subscription;
import com.microsoftopentechnologies.rest.WindowsAzureServiceManagement;
import com.microsoftopentechnologies.rest.WindowsAzureStorageServices;

public final class WizardCacheManagerUtilMethods {

	public static WindowsAzureStorageServices createStorageServiceHelper(
			PublishData currentPublishData,
			String currentStorageService,
			KeyName currentAccessKey) {
		if (currentPublishData != null) {
			StorageService storageService =
					getCurrentStorageAcount(currentPublishData, currentStorageService);
			try {
				String key = ""; //$NON-NLS-1$
				if (currentAccessKey == KeyName.Primary)
					key = storageService.getPrimaryKey();
				else
					key = storageService.getSecondaryKey();

				return new WindowsAzureStorageServices(
						storageService, key);
			} catch (InvalidKeyException e) {
				e.printStackTrace();
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	public static WindowsAzureServiceManagement createServiceManagementHelper(PublishData currentPublishData) {
		if (currentPublishData != null) {
			try {
				return new WindowsAzureServiceManagement();
			} catch (InvalidThumbprintException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	public static List<Location> getLocation(PublishData currentPublishData) {

		if (currentPublishData != null)
			return currentPublishData.getLocationsPerSubscription().get(
					currentPublishData.getCurrentSubscription().getId());

		return null;
	}

	public static Subscription findSubscriptionByName(String subscriptionName,
			List<PublishData> PUBLISHS) {

		for (PublishData pd : PUBLISHS) {
			List<Subscription> subscriptions = pd.getPublishProfile()
					.getSubscriptions();
			for (Subscription sub : subscriptions) {
				if (sub.getName().equals(subscriptionName))
					return sub;
			}
		}

		return null;
	}

	public static PublishData findPublishDataBySubscriptionId(String subscriptionId,
			List<PublishData> PUBLISHS) {
		for (PublishData pd : PUBLISHS) {
			if (pd.getSubscriptionIds().contains(subscriptionId)) {
				return pd;
			}
		}
		return null;
	}

	public static void changeCurrentSubscription(PublishData publishData, String subscriptionId) {
		if (publishData == null || subscriptionId == null) {
			return;
		}
		List<Subscription> subs = publishData.getPublishProfile().getSubscriptions();
		for (int i = 0; i < subs.size(); i++) {
			Subscription s = subs.get(i);

			if (s.getSubscriptionID().equals(subscriptionId)) {
				publishData.setCurrentSubscription(s);
				break;
			}
		}
	}

	public static StorageService getCurrentStorageAcount(PublishData currentPublishData,
			String currentStorageService) {

		if (currentPublishData != null && (currentStorageService != null && !currentStorageService.isEmpty())) {

			for (StorageService storageService : currentPublishData
					.getStoragesPerSubscription()
					.get(currentPublishData.getCurrentSubscription().getId())) {
				if (storageService.getServiceName().equalsIgnoreCase(
						currentStorageService))
					return storageService;
			}
		}
		return null;
	}

	public static HostedService getCurentHostedService(PublishData currentPublishData,
			String currentHostedService) {
		if (currentPublishData != null
				&& (currentHostedService != null && !currentHostedService
				.isEmpty())) {
			String subsId = currentPublishData.getCurrentSubscription().getId();

			for (HostedService hostedService : currentPublishData
					.getServicesPerSubscription().get(subsId)) {
				if (hostedService.getServiceName().equalsIgnoreCase(
						currentHostedService))
					return hostedService;
			}
		}
		return null;
	}

	public static HostedService getHostedServiceFromCurrentPublishData(final String hostedServiceName,
			PublishData currentPublishData) {
		if (currentPublishData != null) {
			String subsId = currentPublishData.getCurrentSubscription().getId();

			for (HostedService hostedService : currentPublishData
					.getServicesPerSubscription().get(subsId)) {
				if (hostedService.getServiceName().equalsIgnoreCase(
						hostedServiceName))
					return hostedService;
			}
		}
		return null;
	}

	/**
	 * Method uses REST API and returns already uploaded certificates
	 * from currently selected cloud service on wizard.
	 * @return
	 */
	public static List<Certificate> fetchUploadedCertificates(PublishData currentPublishData,
			String currentHostedService) {
		WindowsAzureServiceManagement service;
		List<Certificate> certsInService = null;
		try {
			service = new WindowsAzureServiceManagement();
			certsInService = service.listCertificates(currentPublishData.getCurrentConfiguration(),
					getCurentHostedService(currentPublishData, currentHostedService).getServiceName());
		} catch (Exception e) {
			e.printStackTrace();
		}
		return certsInService;
	}

	public static HostedService createHostedService(
			HostedServiceCreateParameters createHostedService,
			PublishData currentPublishData)
					throws Exception, ServiceException {

		WindowsAzureServiceManagement service;
		Subscription subscription = currentPublishData.getCurrentSubscription();

		try {
			service = new WindowsAzureServiceManagement();
			String subscriptionId = subscription.getId();
			service.createHostedService(currentPublishData.getCurrentConfiguration(), createHostedService);
			// todo?
			HostedServiceGetDetailedResponse hostedServiceGetDetailedResponse = service
					.getHostedServiceWithProperties(currentPublishData.getCurrentConfiguration(),
							createHostedService.getServiceName());
			HostedService hostedService = new HostedService();
			hostedService.setServiceName(hostedServiceGetDetailedResponse.getServiceName());
			hostedService.setUri(hostedServiceGetDetailedResponse.getUri());
			hostedService.setProperties(hostedServiceGetDetailedResponse.getProperties());

			// remove previos mock if existed
			for (HostedService hs : currentPublishData.getServicesPerSubscription().get(subscriptionId)) {
				if (hostedService.getServiceName().equals(hs.getServiceName())) {
					currentPublishData.getServicesPerSubscription().get(subscriptionId).remove(hs);
					break; // important to avoid exception
				}
			}
			return hostedService;
		} catch (InvalidThumbprintException e) {
			throw new Exception(e.getMessage(), e);
		}
	}

	public static StorageService createStorageAccount(StorageAccountCreateParameters accountParameters,
			PublishData currentPublishData)
					throws Exception, RestAPIException,
					InterruptedException, ServiceException {
		WindowsAzureServiceManagement service;
		Configuration configuration = currentPublishData.getCurrentConfiguration();
		try {
			service = new WindowsAzureServiceManagement();

			String requestId = service.createStorageAccount(currentPublishData.getCurrentConfiguration(), accountParameters);

			waitForStatus(configuration, service, requestId);

			StorageService storageAccount = service.getStorageAccount(configuration, accountParameters.getName());
			List<URI> endpoints = storageAccount.getStorageAccountProperties().getEndpoints();
			if (endpoints.get(0).toString().startsWith("https://")) {
				endpoints.set(0, URI.create(endpoints.get(0).toString().replaceFirst("https://", "http://")));
				endpoints.set(1, URI.create(endpoints.get(1).toString().replaceFirst("https://", "http://")));
				endpoints.set(2, URI.create(endpoints.get(2).toString().replaceFirst("https://", "http://")));
			}
			return storageAccount;
		}
		catch (InvalidThumbprintException e) {
			throw new Exception(e);
		}
	}

	private static OperationStatus waitForStatus(Configuration configuration,
			WindowsAzureServiceManagement service, String requestId)
					throws Exception {
		OperationStatusResponse op;
		OperationStatus status = null;
		do {
			op = service.getOperationStatus(configuration, requestId);
			status = op.getStatus();

			if (op.getError() != null) {
				throw new RestAPIException(op.getError().getMessage());
			}
			Thread.sleep(5000);

		} while (status == OperationStatus.InProgress);
		return status;
	}

	public static boolean isHostedServiceNameAvailable(final String hostedServiceName,
			PublishData currentPublishData)
					throws Exception, RestAPIException {
		WindowsAzureServiceManagement service;
		try {
			service = new WindowsAzureServiceManagement();
			return service.checkForCloudServiceDNSAvailability(
					currentPublishData.getCurrentConfiguration(),
					hostedServiceName);
		} catch (InvalidThumbprintException e) {
			throw new Exception(e);
		}
	}

	public static boolean isStorageAccountNameAvailable(final String storageAccountName,
			PublishData currentPublishData)
					throws Exception, ServiceException {
		WindowsAzureServiceManagement service;
		try {
			service = new WindowsAzureServiceManagement();
			return service.checkForStorageAccountDNSAvailability(
					currentPublishData.getCurrentConfiguration(),
					storageAccountName);
		} catch (InvalidThumbprintException e) {
			throw new Exception(e);
		}
	}

	public static StorageService createStorageServiceMock(String storageAccountNameToCreate,
			String storageAccountLocation,
			String description) {
		StorageAccountProperties props = new StorageAccountProperties();
		props.setDescription(description);
		props.setLocation(storageAccountLocation);

		StorageService storageService = new StorageService();
		storageService.setStorageAccountProperties(props);
		storageService.setServiceName(storageAccountNameToCreate);

		return storageService;
	}

	public static HostedService createHostedServiceMock(String hostedServiceNameToCreate,
			String hostedServiceLocation,
			String description) {
		HostedServiceProperties props = new HostedServiceProperties();
		props.setDescription(description);
		props.setLocation(hostedServiceLocation);

		HostedService hostedService = new HostedService();
		hostedService.setProperties(props);
		hostedService.setServiceName(hostedServiceNameToCreate);

		return hostedService;
	}

	public static List<HostedService> getHostedServices(PublishData currentPublishData) {
		if (currentPublishData == null)
			return null;
		String subbscriptionId = currentPublishData.getCurrentSubscription().getId();
		return currentPublishData.getServicesPerSubscription().get(subbscriptionId);
	}

	public static HostedServiceGetDetailedResponse getHostedServiceWithDeployments(
			String hostedService, PublishData currentPublishData)
					throws Exception, InvalidThumbprintException {
		WindowsAzureServiceManagement service = new WindowsAzureServiceManagement();
		return service.getHostedServiceWithProperties(currentPublishData.getCurrentConfiguration(), hostedService);
	}

	public static boolean empty(PublishData data) {

		Map<String, ArrayList<HostedService>> hostedServices = data.getServicesPerSubscription();
		if (hostedServices == null || hostedServices.keySet().isEmpty()) {
			return true;
		}
		Map<String, StorageServices> storageServices = data.getStoragesPerSubscription();
		if (storageServices == null || storageServices.keySet().isEmpty()) {
			return true;
		}
		Map<String , ArrayList<Location>> locations = data.getLocationsPerSubscription();
		return locations == null || locations.keySet().isEmpty();
	}

	public static StorageService getStorageAccountFromCurrentPublishData(String storageAccountName,
			PublishData currentPublishData) {
		if (currentPublishData != null) {
			for (StorageService storageService : currentPublishData
					.getStoragesPerSubscription()
					.get(currentPublishData.getCurrentSubscription().getId())) {
				if (storageService.getServiceName().equalsIgnoreCase(
						storageAccountName))
					return storageService;
			}
		}
		return null;
	}

	public static String checkSchemaVersionAndReturnUrl(PublishData currentPublishData) {
		String url = null;
		String schemaVer = currentPublishData.getPublishProfile().getSchemaVersion();
		if (schemaVer != null && !schemaVer.isEmpty() && schemaVer.equalsIgnoreCase("2.0")) {
			// publishsetting file is of schema version 2.0
			url = currentPublishData.getCurrentSubscription().getServiceManagementUrl();
		} else {
			url = currentPublishData.getPublishProfile().getUrl();
		}
		return url;
	}
	
	public static int getIndexOfPublishData(String subscriptionId, List<PublishData> PUBLISHS) {
		int index = 0;
		for (int i = 0; i < PUBLISHS.size(); i++) {
			if (PUBLISHS.get(i).getSubscriptionIds().contains(subscriptionId)) {
				index = i;
			}
		}
		return index;
	}
}
