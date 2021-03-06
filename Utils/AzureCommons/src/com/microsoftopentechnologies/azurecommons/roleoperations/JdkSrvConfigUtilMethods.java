/**
* Copyright 2015 Microsoft Open Technologies, Inc.
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
package com.microsoftopentechnologies.azurecommons.roleoperations;

import java.io.File;
import java.util.Arrays;


import com.interopbridges.tools.windowsazure.WARoleComponentCloudUploadMode;
import com.interopbridges.tools.windowsazure.WindowsAzureInvalidProjectOperationException;
import com.interopbridges.tools.windowsazure.WindowsAzureProjectManager;
import com.interopbridges.tools.windowsazure.WindowsAzureRole;
import com.microsoftopentechnologies.azurecommons.messagehandler.PropUtil;
import com.microsoftopentechnologies.azurecommons.storageregistry.StorageAccount;
import com.microsoftopentechnologies.azurecommons.storageregistry.StorageAccountRegistry;
import com.microsoftopentechnologies.azurecommons.storageregistry.StorageRegistryUtilMethods;

public class JdkSrvConfigUtilMethods {

	private static String eclipseDeployContainer = PropUtil.getValueFromFile("eclipseDeployContainer");
	private final static String FWD_SLASH = "/";

	// Method to be used inside urlModifyListner
	public static String getNameToSet(String url,
			String nameInUrl, String[] accNames) {
		String endpoint = StorageRegistryUtilMethods.
				getServiceEndpoint(url);
		String accNameToSet = accNames[0];
		if (nameInUrl != null
				&& !nameInUrl.isEmpty()
				&& endpoint != null) {
			// check storage account name present in list
			if (Arrays.asList(accNames).contains(nameInUrl)) {
				/*
				 * check endpoint of storage account from list
				 * and from URL matches then
				 * only select storage account otherwise select none.
				 */
				int index = Arrays.asList(accNames).indexOf(nameInUrl);
				String endpointInReg = StorageRegistryUtilMethods.
						getServiceEndpoint(StorageAccountRegistry.
								getStrgList().get(index - 1).getStrgUrl());
				if (endpoint.equalsIgnoreCase(endpointInReg)) {
					accNameToSet = nameInUrl;
				}
			} else if (StorageRegistryUtilMethods.
					isDuplicatePresent()) {
				/*
				 * If accounts with same name but
				 * different service URL exists
				 * then check concatenation of account name
				 * and service endpoint exists in list.
				 */
				String accAndUrl = StorageRegistryUtilMethods.
						getAccNmSrvcUrlToDisplay(nameInUrl, endpoint);
				if (Arrays.asList(accNames).contains(accAndUrl)) {
					accNameToSet = accAndUrl;
				}
			}
		}
		return accNameToSet;
	}

	/**
	 * This API appends eclipse container name and filename to url
	 * in order to construct blob url.
	 * @param filePath
	 * @param newUrl
	 * @return
	 */
	public static String prepareCloudBlobURL(String filePath,
			String newUrl) {
		if ((filePath == null || filePath.length() == 0)
				|| (newUrl == null || newUrl.length() == 0)) {
			return "";
		}

		File jdkPath = new File(filePath);
		return new StringBuilder(newUrl).append(eclipseDeployContainer)
				.append(FWD_SLASH)
				.append(jdkPath.getName().trim().replaceAll("\\s+", "-"))
				.append(".zip").toString();
	}

	/**
	 * This API appends eclipse container name and filename to url
	 * in order to construct blob url.
	 * @param asName
	 * @param url
	 * @return
	 */
	public static String prepareUrlForApp(String asName,
			String url) {
		if ((asName == null || asName.length() == 0)
				|| (url == null || url.length() == 0)) {
			return "";
		}

		return new StringBuilder(url)
		.append(eclipseDeployContainer)
		.append(FWD_SLASH)
		.append(asName).toString();
	}

	/**
	 * Method prepares third party JDK URL
	 * by appending eclipse container name and
	 * filename from third party URL.
	 * @param url
	 * @return
	 * @throws Exception 
	 */
	public static String prepareUrlForThirdPartyJdk(String jdkName, String url, File cmpntFile)
			throws Exception {
		String finalUrl = "";
		try {
			String cloudValue = WindowsAzureProjectManager.
					getCloudValue(jdkName, cmpntFile);
			String dirName = cloudValue.substring(cloudValue.lastIndexOf("\\") + 1,
					cloudValue.length());
			finalUrl = new StringBuilder(url)
			.append(eclipseDeployContainer)
			.append(FWD_SLASH)
			.append(dirName)
			.append(".zip").toString();
		} catch (Exception ex) {
			throw new Exception(ex.getMessage(), ex);
		}
		return finalUrl;
	}
	
	/**
	 * Method prepares third party server URL
	 * by appending eclipse container name and
	 * filename from third party URL.
	 * @param url
	 * @return
	 * @throws Exception 
	 */
	public static String prepareUrlForThirdPartySrv(String srvName, String url, File cmpntFile)
			throws Exception {
		String finalUrl = "";
		try {
			String cloudValue = WindowsAzureProjectManager.getThirdPartyServerHome(srvName, cmpntFile);
			String dirName = cloudValue.substring(cloudValue.lastIndexOf("\\") + 1,
					cloudValue.length());
			finalUrl = new StringBuilder(url)
			.append(eclipseDeployContainer).append(FWD_SLASH)
			.append(dirName).append(".zip").toString();
		} catch (Exception ex) {
			throw new Exception(ex.getMessage(), ex);
		}
		return finalUrl;
	}

	// Method to be used inside populateStrgNameAsPerKey
	public static String getNameToSetAsPerKey(String key, String[] accNames) {
		String accNameToSet = "";
		boolean isSet = false;
		String accName = accNames[0];
		if (key != null) {
			// get index of account which has matching access key
			int index = StorageRegistryUtilMethods.
					getStrgAccIndexAsPerKey(key);
			if (index >= 0) {
				StorageAccount account = StorageAccountRegistry.
						getStrgList().get(index);
				accName = account.getStrgName();
				// check storage account name present in list
				if (Arrays.asList(accNames).contains(accName)) {
					isSet = true;
				} else if (StorageRegistryUtilMethods.
						isDuplicatePresent()) {
					/*
					 * If accounts with same name but
					 * different service URL exists
					 * then check concatenation of account name
					 * and service endpoint exists in list.
					 */
					String endpoint = StorageRegistryUtilMethods.
							getServiceEndpoint(account.getStrgUrl());
					String accAndUrl = StorageRegistryUtilMethods.
							getAccNmSrvcUrlToDisplay(accName, endpoint);
					if (Arrays.asList(accNames).contains(accAndUrl)) {
						accName = accAndUrl;
						isSet = true;
					}
				}
			}
		}
		if (isSet) {
			accNameToSet = accName;
		} else {
			accNameToSet = accNames[0];
		}
		return accNameToSet;
	}

	/**
	 * Returns true if auto upload is selected for JDK else false.
	 * @return
	 * @throws WindowsAzureInvalidProjectOperationException
	 */
	public static boolean isJDKAutoUploadPrevSelected(WindowsAzureRole role)
			throws WindowsAzureInvalidProjectOperationException {
		WARoleComponentCloudUploadMode uploadMode =
				role.getJDKCloudUploadMode();
		if (uploadMode != null
				&& uploadMode.equals(WARoleComponentCloudUploadMode.auto)) {
			return true;
		}
		return false;
	}

	/**
	 * Returns true if auto upload is selected for Server else false.
	 * @return
	 * @throws WindowsAzureInvalidProjectOperationException
	 */
	public static boolean isServerAutoUploadPrevSelected(WindowsAzureRole role)
			throws WindowsAzureInvalidProjectOperationException {
		WARoleComponentCloudUploadMode uploadMode =
				role.getServerCloudUploadMode();
		if (uploadMode != null
				&& uploadMode.equals(WARoleComponentCloudUploadMode.auto)) {
			return true;
		}
		return false;
	}
}
