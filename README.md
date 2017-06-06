#cas client单点登出扩展

##原因
cas client 提供了一个单点登出的功能,一般情况下很好使用。但是在cas client对应的为集群时，则在退出会有问题，原因是cas server通过httpclient发送退出请求被nginx转发之后只能有一个节点处理该请求，而其他节点无法处理，即session不会消毁的。所以用户就不会真正的登出。

##基本原理
为了解决这个问题，所以需要对cas client 单点登出进行重构。所决方案基本原理，即cas server通过httpclient发送退出请求被nginx转发之后只能有一个节点处理该请求，如果该节点发现session不在该节点上，则进行cas client中所有集群节点广播退出请求。


##使用demo
	web.xml中配置

	<listener>
		<listener-class>com.aldb.cas.logout.extend.ClusterSingleSignOutHttpSessionListener</listener-class>
	</listener>
	
	<filter>
        <filter-name>ssoLogoutFilter</filter-name>
        <filter-class>
            org.springframework.web.filter.DelegatingFilterProxy
        </filter-class>
    </filter>
	
	<filter-mapping>
		<filter-name>ssoLogoutFilter</filter-name>
			<url-pattern>/*</url-pattern>
	</filter-mapping>


	spring.xml中配置
	<bean id="ssoLogoutFilter" class="com.aldb.cas.logout.extend.ClusterSigleSignOutFilter">
		<property name="casServerUrlPrefix" value="${sso.server.url}" />
		<property name="clusterNodeUrls" value="${client.clusternodeurls}"></property>
	</bean>
	其中sso.server.url为cas服务器的地址，示例http://192.168.2.100:8080/cas;
	client.clusternodeurls为所有单应用对应的节点地址列表，多个用英文逗号隔开,示例http://192.168.2.100:8060/test/,http://192.168.2.100:8070/test/；
	
	