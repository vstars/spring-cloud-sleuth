/*
 * Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.sleuth.instrument.web.client;

import static junitparams.JUnitParamsRunner.$;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.WebIntegrationTest;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.cloud.netflix.ribbon.RibbonClient;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.assertions.SleuthAssertions;
import org.springframework.cloud.sleuth.trace.TestSpanContextHolder;
import org.springframework.cloud.sleuth.util.ExceptionUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.client.RestTemplate;

import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
@SpringApplicationConfiguration(classes = {
		WebClientExceptionTests.TestConfiguration.class })
@WebIntegrationTest(value = {"ribbon.ConnectTimeout=30000",
		"spring.application.name=exceptionservice" }, randomPort = true)
public class WebClientExceptionTests {

	@ClassRule
	public static final SpringClassRule SCR = new SpringClassRule();
	@Rule
	public final SpringMethodRule springMethodRule = new SpringMethodRule();

	@Autowired
	TestFeignInterfaceWithException testFeignInterfaceWithException;
	@Autowired
	@LoadBalanced
	RestTemplate template;
	@Autowired
	Tracer tracer;

	@Before
	public void open() {
		TestSpanContextHolder.removeCurrentSpan();
		ExceptionUtils.setFail(true);
	}

	@After
	public void close() {
		ExceptionUtils.setFail(false);
		TestSpanContextHolder.removeCurrentSpan();
	}

	// issue #198
	@Test
	@Parameters
	public void shouldCloseSpanUponException(ResponseEntityProvider provider)
			throws IOException {
		Span span = this.tracer.createSpan("new trace");

		try {
			provider.get(this);
			Assert.fail("should throw an exception");
		}
		catch (RuntimeException e) {
			// SleuthAssertions.then(e).hasRootCauseInstanceOf(IOException.class);
		}

		assertThat(ExceptionUtils.getLastException(), is(nullValue()));

		SleuthAssertions.then(this.tracer.getCurrentSpan()).isEqualTo(span);
		this.tracer.close(span);
	}

	Object[] parametersForShouldCloseSpanUponException() {
		return $(
				(ResponseEntityProvider) (tests) -> tests.testFeignInterfaceWithException
						.shouldFailToConnect(),
				(ResponseEntityProvider) (tests) -> tests.template
						.getForEntity("http://exceptionservice/", Map.class));
	}

	@FeignClient("exceptionservice")
	public interface TestFeignInterfaceWithException {
		@RequestMapping(method = RequestMethod.GET, value = "/")
		ResponseEntity<String> shouldFailToConnect();
	}

	@Configuration
	@EnableAutoConfiguration
	@EnableFeignClients
	@RibbonClient(value = "exceptionservice", configuration = ExceptionServiceRibbonClientConfiguration.class)
	public static class TestConfiguration {

		@LoadBalanced
		@Bean
		public RestTemplate restTemplate() {
			return new RestTemplate();
		}
	}

	@Configuration
	public static class ExceptionServiceRibbonClientConfiguration {

		@Bean
		public ILoadBalancer exceptionServiceRibbonLoadBalancer() {
			BaseLoadBalancer balancer = new BaseLoadBalancer();
			balancer.setServersList(Collections
					.singletonList(new Server("invalid.host.to.break.tests", 1234)));
			return balancer;
		}

	}

	@FunctionalInterface
	interface ResponseEntityProvider {
		ResponseEntity<?> get(WebClientExceptionTests webClientTests);
	}
}
