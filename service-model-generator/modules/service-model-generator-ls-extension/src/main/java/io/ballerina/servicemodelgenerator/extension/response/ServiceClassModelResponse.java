/*
 *  Copyright (c) 2025, WSO2 LLC. (http://www.wso2.com)
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package io.ballerina.servicemodelgenerator.extension.response;

import io.ballerina.servicemodelgenerator.extension.model.ServiceClass;

import java.util.Arrays;

/**
 * ServiceClassModelResponse class to hold the service class model response.
 *
 * @param model service class model
 * @param errorMsg error message
 * @param stacktrace stack trace of the error
 */
public record ServiceClassModelResponse(ServiceClass model, String errorMsg, String stacktrace) {

    public ServiceClassModelResponse() {
        this(null, null, null);
    }

    public ServiceClassModelResponse(ServiceClass service) {
        this(service, null, null);
    }

    public ServiceClassModelResponse(Throwable e) {
        this(null, e.toString(), Arrays.toString(e.getStackTrace()));
    }
}
