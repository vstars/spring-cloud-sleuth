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

package org.springframework.cloud.sleuth.instrument.web.client.feign;

import org.springframework.cloud.sleuth.Tracer;

import feign.Response;
import feign.codec.ErrorDecoder;

/**
 * An {@link ErrorDecoder} that closes a span before returning the exception type.
 *
 * @author Marcin Grzejszczak
 *
 * @since 1.0.0
 */
final class TraceFeignErrorDecoder extends FeignEventPublisher implements ErrorDecoder {

	private final ErrorDecoder delegate;

	TraceFeignErrorDecoder(Tracer tracer) {
		super(tracer);
		this.delegate = new ErrorDecoder.Default();
	}

	TraceFeignErrorDecoder(Tracer tracer, ErrorDecoder delegate) {
		super(tracer);
		this.delegate = delegate;
	}

	@Override public Exception decode(String methodKey, Response response) {
		try {
			return this.delegate.decode(methodKey, response);
		} finally {
			finish();
		}
	}
}
