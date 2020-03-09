# Demo for blue-green deployments using Spring Cloud

## Why blue-green deployments
Blue-green deployment is a technique that reduces downtime and risk by running two identical production environments, called for example Blue and Green.

At this time, only one of the environments is live, with the live environment serving all production traffic. For the example in this repo, Blue is considered the live versio of the service and Green the idle one.

As you prepare a new version of your service, deployment and the final stage of testing takes place in the environment which is not live: in this example, Green. Smoke tests can be run on the new version to verify its functionality, then have traffic slowly move from the old version to the new, until the new version is taking all traffic for the application, making it safe to retire the old version.

The Green environment serves for business acceptance testing of the new release of the service.

Please note that once you have deployed and fully tested the service in Green, you start the gradual switch from the (old) Blue version top the (new) Green version. This technique eliminates downtime due to app deployment. 

Finally, blue-green deployment reduces risk: if something unexpected happens with your new version on Green, you can immediately roll back to the last version by switching back to Blue.

## Blue-green deployment when using Service Discovery in Spring Cloud

When a service discovery service such as Eureka is in use blue-green deployment gets more complicated, as the services/applications are registered under specific names for their clients (for discovery), while services are explicitly registered under names different than the names know to service consumers.

This document details the steps required to deploy a service in a blue-green manner while Eureka service discovery is in play.

Spring Cloud Service Discovery features: 
* Eureka instances can be registered and clients can discover the instances using Spring-managed beans
* An embedded Eureka server can be created with declarative Java configuration

## Process 
1. A version of the service (Blue) is live in an environment, and available to be discovered in Eureka
2. A new version of the service is being built, configured and deployed (Green)
3. Green is not registered in Eureka with the UP status and is considered in an OUT_OF_SERVICE status, thus not making it available for service consumers to discover
4. Smoke testing commences against the Green version of the service, using the mapped route to the Green service
5. Once testing is considered successful, the **gradual blue-green switch process starts**
6. The status of the Green service version is set to UP in Eureka, from the current OUT_OF_SERVICE status
7. Both Blue and Green versions of the service are up and service requests in a load-balanced manner, round-robin by default
8. Decision can now be made to remove the Blue version of the service to an OUT_OF_SERVICE status, by changing its registration status in Eureka
9. Please note that the Blue version of the service is not servicing requests and is not available for discovery, however it is still in a started mode, allowing for a quick reversal in case of failures
10. The process can be completed by removing the Blue version of the service


## Running The Sample On Cloud Foundry
Cloud Foundry offers a more real world blue/green deployment scenario when compared to a local test. 
The functionality it offers in terms of app-scaling scaling apps and managing routes allows for more complicated deployment scenarios.

This sample uses the __Service Registry__ service: [Service Registry for Spring Cloud Applications](https://docs.pivotal.io/spring-cloud-services/2-1/common/index.html) as available in the [Pivotal Marketplace](https://pivotal.io/platform/services-marketplace).

It is essentially a productized version of Eureka running on Pivotal Cloud Foundry. You can also use plain Eureka and have it deployed on Cloud Foundry. 

This sample can simply be followed using a free account on the cloud hosted version of Pivotal Web Services or in the PCF installation of your choice.

#### Step 1: Pre-requisites
* Service Registry 

Create a Service Registry service from Spring Cloud Services. See the docs at the links above for information on how to do that. 
The important part when creating the service for this particular demo is to give it the name __bluegreen-registry__ . The deployment will fail otherwise.

To leverage this service you will need to add the Spring Cloud Service starter to your POM.
```
		<dependency>
			<groupId>io.pivotal.spring.cloud</groupId>
			<artifactId>spring-cloud-services-starter-service-registry</artifactId>
		</dependency>
```
The demo projects in this repo already have this so no need to do that.

* Cloud Foundry CLI

Install [Cloud Foundry CLI](http://docs.run.pivotal.io/cf-cli/) to deploy the apps.   Follow these [instructions](http://docs.run.pivotal.io/cf-cli/install-go-cli.html)
to install the CLI.

* Login to Cloud Foundry

```
    cf login -a https://api.run.pivotal.io
```

NOTE: Substitute your Cloud Foundry API URL if you are not using the cloud-hosted Pivotal Web Services.

You will be prompted for your email address and password to login.  After you
login you MAY also be prompted to select and organization and space.  This will
depend on your account.

#### Step 2: Service deployment

1. Deploy the Blue service version and register it in Eureka

```
    cd blueorgreenservice && ./mvnw clean package && cf push -f manifest_blue.yaml && cd ..
```

This will use the manifest_blue.yml file to deploy  to Cloud Foundry. You may run into issues during the deployment if there are other apps deployed to your Cloud Foundry instance already using the host names blueservice and/or greenservice. If this is the case, open the manifest.yml file and change the values in the hosts field so they are unique in your Cloud Foundry deployment.

In this example, the Blue service version will be available at: ```
https://blueservice_v1.cfapps.io```

Please note that a prudent approach is recommended to be taken: start the service with an OUT_OF_SERVICE status, to mitigate any risk.
```
eureka:
  client:
    serviceUrl:
      defaultZone: http://localhost:8761/eureka/
  instance:
    leaseRenewalIntervalInSeconds: 10
    metadataMap:
      instanceId: ${vcap.application.instance_id:${spring.application.name}:${spring.application.instance_id:${server.port}}}
   
    # Initially launch the service with the status of OUT_OF_SERVICE
    initial-status: out_of_service
```

The app will automatically register with the Spring Cloud Service service discovery registry. Check the management console for the service discovery registry and notice that the Blue service version registered with an OUT_OF_SERVICE status, when clicking **Manage** in the **bluegreen-registry** registry instance.

Lets now change the change the status to UP in Eureka, thus making Blue available for service discovery.

```
    curl -X "POST" "https://blueservice_v1.cfapps.io/service-registry/instance-status" \
        -H "Content-Type: text/plain; charset=utf-8" \
        -d "UP"
```

Make sure that you change the URL in the above cURL command to be the correct one for your deployed Blue service.


2. Deploy a front-end web application and validate that Blue is registered and can be discovered
```
    cd blueorgreenfrontend && ./mvnw clean package && cf push && cd ..
```
Again you may run into issues if the host name is already in use. If that is he case open the manifest.yml file and modify the hosts property.

Once the frontend web app is deployed are able to open the app using the URL from Cloud Foundry and see the web app in your browser at ```https://blueorgreenfrontend_v1.cfapps.io/```

Please note that the page displays a blue background.

--

3. Deploy the Green service version and register it in Eureka

```
    cd blueorgreenservice && ./mvnw clean package && cf push -f manifest_green.yaml && cd ..
```

This will use the manifest_green.yml file to deploy  to Cloud Foundry. You may run into issues during the deployment if there are other apps deployed to your Cloud Foundry instance already using the host names blueservice and/or greenservice. If this is the case, open the manifest.yml file and change the values in the hosts field so they are unique in your Cloud Foundry deployment.

In this example, the Green service version will be available at: ```
https://greenservice_v1.cfapps.io```

Please note that a prudent approach is recommended to be taken, even more important than with the Blue service version: start the service with an OUT_OF_SERVICE status, to mitigate any risk. You do not wish to open it up for service discovery in Eureka before testing.

The app will automatically register with the SCS service discovery registry. Check the management console for the service discovery registry and notice that the Green service version registered with an OUT_OF_SERVICE status, when clicking **Manage** in the **bluegreen-registry** registry instance.

Right now, since the green service is still labeled as OUT_OF_SERVICE you will just see BLUE displayed in the browser.

#### Step 3: start the blue-green switch

Lets change the status of the green service to UP.

```
    curl -X "POST" "https://greenservice_v2.cfapps.io/service-registry/instance-status" \
        -H "Content-Type: text/plain; charset=utf-8" \
        -d "UP"
```     
     
Make sure that you change the URL in the above cURL command to be the correct one for your green service.

You will have to wait for the frontend web app to refresh it's data from the service registry before it knows the green service is now available but once that happens you should be able to refresh the web app and see that the result will alternate between BLUE and GREEN.

**Important notes:**
* The consumer of the **bluegreen** service is not aware at any time that there are multiple versions of the service deployed in Cloud Foundry. All it knows that the service discovery process will always be the same in Eureka:
```code
	@RequestMapping("/color")
	public String color() {
		return rest.getForObject("http://blueorgreen", String.class);
	}
```

This is due to the fact that BOTH Blue and Green service versions are registered in Eureka using the same Spring application name, which is used for discovery:
```
    spring:
    application:
        name: blueorgreen
```
* Please note that the 2 service versions retain their individual deployment name and hosts


#### Step 4: Disable the Blue service version

The Blue service can now be disabled and there are multiple options to do that. 

An elegant option is to change the status of the blue service to OUT_OF_SERVICE and wait for that to propagate to the frontend web app. 
```
    curl -X "POST" "https://blueservice_v1.cfapps.io/service-registry/instance-status" \
        -H "Content-Type: text/plain; charset=utf-8" \
        -d "OUT_OF_SERVICE"
```

After this step completes we can stop/delete the app.

#### Step 5: Remove the Blue service version 

This status change may take a few minutes to propagate to the front end web app. Once this happens the web app will only display GREEN. At this point it is safe to shut down or delete the blue service.

```
    # unmap the route
    cf unmap-route greenservice cfapps.io --hostname blueservice_v1

    # delete the route 
    cf delete-route cfapps.io --hostname blueservice_v2 -f

    # delete the service
    cf delete delete service -f
```

## Scaling a service in Cloud Foundry using Service Discovery in Spring Cloud

One of the benefits of running applications in Cloud Foundry is the ease at which you can scale apps up and down. If you try to scale the BlueGreen Service app up in Cloud Foundry you will notice that each instance that is created will register with the service discovery registry service with a status of OUT_OF_SERVICE. This start with an OUT_OF_SERVICE status is a prudent approach followed throughout this demo.

Scaling the number of instances of the Blue service to 3 can simply be done:
```
    cf scale blueservice -i 3
```
We observe: 1 instance listed as UP and 2 instances as OUT_OF_SERVICE

When there is more than one instance of the service running using the ".../service-registry/instance-status HTTP endpoint will prove problematic. This is because the CloudFoundry router will route requests to each instance in a round robin fashion so you can't just target the instances that are currently OUT_OF_SERVICE.

To just target the instances that are currently OUT_OF_SERVICE you can use a special HTTP header containing the service GUID and the instance id. To figure out what the app GUID is, run the command:

```
    cf app blueservice --guid

    # sample response 
    be0866ea-bf9d-49db-8b26-f731d99d8346
```    
To find the instance id, which just a 0 based index, we can run:
```
    cf app blueservice
```
A sample response would be similar to:
```
    Showing health and status for app blueservice in org ... / space ddobrin as ...

    name:              blueservice
    requested state:   started
    routes:            blueservice_v1.cfapps.io
    last uploaded:     Mon 09 Mar 17:15:08 EDT 2020
    ...
    type:           web
    instances:      3/3
    memory usage:   1024M
        state     since                  cpu    memory         disk           details
    #0   running   2020-03-09T21:15:49Z   0.7%   254.2M of 1G   149.1M of 1G
    #1   running   2020-03-09T21:24:54Z   1.4%   240.3M of 1G   149.1M of 1G
    #2   running   2020-03-09T21:25:03Z   0.8%   241.4M of 1G   149.1M of 1G
```

The numbers, #0, #1, #2, are the instance ids. To make a request specifically to instance #1 to change the status to UP, a sample would look like this:
```
    # Please note the guid and instance numbers ...
    # X-CF-APP-INSTANCE header is what tells the CF router which instance to target. 
    # The value should take the format of APP_GUID:INSTANCE_ID

    # second instance
    curl -X "POST" "https://blueservice_v1.cfapps.io/service-registry/instance-status" \
        -H "X-CF-APP-INSTANCE: be0866ea-bf9d-49db-8b26-f731d99d8346:1" \
        -H "Content-Type: text/plain; charset=utf-8" \
        -d "UP"

    # third instance
    curl -X "POST" "https://blueservice_v1.cfapps.io/service-registry/instance-status" \
        -H "X-CF-APP-INSTANCE: be0866ea-bf9d-49db-8b26-f731d99d8346:2" \
        -H "Content-Type: text/plain; charset=utf-8" \
        -d "UP"

```
