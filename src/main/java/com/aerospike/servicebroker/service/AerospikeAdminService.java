/*
 * Copyright 2012-2016 Aerospike, Inc.
 *
 * Portions may be licensed to Aerospike, Inc. under one or more contributor
 * license agreements WHICH ARE COMPATIBLE WITH THE APACHE LICENSE, VERSION 2.0.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.aerospike.servicebroker.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Bin;
import com.aerospike.client.Host;
import com.aerospike.client.Info;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.admin.Role;
import com.aerospike.client.cluster.Node;
import com.aerospike.client.policy.ClientPolicy;
import com.aerospike.servicebroker.config.AerospikeClientConfig;
import com.aerospike.servicebroker.exception.AerospikeServiceException;
import com.aerospike.servicebroker.model.ServiceInstance;
import com.aerospike.servicebroker.model.ServiceInstanceBinding;

@Service
public class AerospikeAdminService {
	// These could be externalized
	private static final String ADMIN_NAMESPACE = "cf_admin";
	private static final String ADMIN_BINDING = "binding";
	private static final String ADMIN_SERVICE = "service";

	private static final String SERVICE_INSTANCE_BINNAME = "instance";
	private static final String SERVICE_BINDING_BINNAME = "binding";
	
	private static final String NAMESPACES_INFO = "namespaces";
	
	private static final String ENTERPRISE = "enterprise";
	
	private Logger logger = LoggerFactory.getLogger(AerospikeAdminService.class);
	
	private AerospikeClient client;
	private Set<String>	    namespaces;
	
	private  String licenseType;
	
	@Autowired
	public AerospikeAdminService(AerospikeClientConfig config) {
		logger.info("Intializing Admin Service");
		ClientPolicy policy = new ClientPolicy();
		policy.failIfNotConnected = true;
		this.client =  new AerospikeClient(policy, config.hostname, config.port);
		this.licenseType = config.licenseType;
		
		namespaces = new HashSet<String>(Arrays.asList(Info.request(this.client.getNodes()[0], NAMESPACES_INFO).split(";")));
		
		if (!namespaces.contains(ADMIN_NAMESPACE)) {
			throw new AerospikeServiceException("Namspace cf_admin must be configured in order to use the service broker with this database.");
		}
	}
	
	public boolean serviceExists(String serviceId) {
		Key key = new Key(ADMIN_NAMESPACE, ADMIN_SERVICE, serviceId);
		return this.client.exists(null, key);
	}
	
	public boolean namespaceExists(String namespace) {
		return this.namespaces.contains(namespace);
	}

	public void createService(ServiceInstance serviceInstance) {
		Key key = new Key(ADMIN_NAMESPACE, ADMIN_SERVICE, serviceInstance.getServiceInstanceId());
		Bin bin = new Bin(SERVICE_INSTANCE_BINNAME, serviceInstance);
		this.client.put(null, key, bin);
	}
	
	public ServiceInstance getService(String serviceId) {
		ServiceInstance service = null;
		Key key = new Key(ADMIN_NAMESPACE, ADMIN_SERVICE, serviceId);
		Record record = this.client.get(null, key);
		if (record != null) {
			service = (ServiceInstance)record.getValue(SERVICE_INSTANCE_BINNAME);
		}
		return service;
	}
	
	public void deleteService(ServiceInstance serviceInstance) {
		Key key = new Key(ADMIN_NAMESPACE, ADMIN_SERVICE, serviceInstance.getServiceInstanceId());
		this.client.delete(null, key);
	}
	
	public boolean serviceBindingExists(String serviceBindingId) {
		Key key = new Key(ADMIN_NAMESPACE, ADMIN_BINDING, serviceBindingId);
		return this.client.exists(null, key);
	}
	
	public void createServiceBinding(ServiceInstanceBinding binding) {
		Key key = new Key(ADMIN_NAMESPACE, ADMIN_BINDING, binding.getId());
		Bin bin = new Bin(SERVICE_BINDING_BINNAME, binding);
		this.client.put(null, key, bin);
	}
	
	public ServiceInstanceBinding getServiceBinding(String serviceBindingId) {
		ServiceInstanceBinding binding = null;
		Key key = new Key(ADMIN_NAMESPACE, ADMIN_BINDING, serviceBindingId);
		Record record = this.client.get(null, key);
		if (record != null) {
			binding = (ServiceInstanceBinding)record.getValue(SERVICE_BINDING_BINNAME);
		}
		return binding;
	}
	
	public void deleteServiceBinding(ServiceInstanceBinding binding) {
		Key key = new Key(ADMIN_NAMESPACE, ADMIN_BINDING, binding.getId());
		this.client.delete(null, key);
	}
	
	public String getHostname() {
		return this.client.getNodes()[0].getHost().name;
	}
	
	public int getPort() {
		return this.client.getNodes()[0].getHost().port;
	}
	
	public Host[] getHosts() {
		Node[] nodes = this.client.getNodes();
		Host[] hosts = new Host[nodes.length];
		for (int i=0; i<nodes.length; i++) {
			hosts[i] = nodes[i].getHost();
		}
		
		return hosts;
	}
	
	public void createUser(String user, String password) {
		if (this.licenseType.equalsIgnoreCase(ENTERPRISE)) {
			this.client.createUser(null, user, password, Collections.singletonList(Role.ReadWrite));
		}
	}
	
	public void dropUser(String user) {
		if (this.licenseType.equalsIgnoreCase(ENTERPRISE)) {
			this.client.dropUser(null, user);
		}
	}
}