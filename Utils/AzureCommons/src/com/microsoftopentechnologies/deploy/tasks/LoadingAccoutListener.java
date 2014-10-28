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
package com.microsoftopentechnologies.deploy.tasks;

import java.util.EventListener;


public interface LoadingAccoutListener extends EventListener {
	
	public void onLoadedSubscriptions();
	
	public void onLoadedStorageServices();
	
	public void onLoadedHostedServices();
	
	public void onLoadedLocations();
	
	public void onRestAPIError(AccountCachingExceptionEvent e);
	
	public void setNumberOfAccounts(int num);

}
