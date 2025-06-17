/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.plugin.jqm;

import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2.InstanceMethodsAroundInterceptorV2;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2.MethodInvocationContext;
import org.apache.skywalking.apm.dependencies.org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Field;

/**
 * {@link JDBCPrepareStatementWithStringArrayInterceptor} return {@link SWPreparedStatement} instance that wrapper the
 * real preparedStatement instance when the client call <code>org.postgresql.jdbc.PgConnection#prepareStatement(String,
 * String[]) </code> method. method.
 */

/**/
public class RunningJobInstanceInterceptor implements InstanceMethodsAroundInterceptorV2 {

    @Override
    public void beforeMethod(EnhancedInstance objInst,
                             Method method,
                             Object[] allArguments,
                            Class<?>[] argumentsTypes,
                            MethodInvocationContext context) throws Throwable {

        Logger logger = LoggerFactory.getLogger(RunningJobInstanceInterceptor.class);
        
        try {
            Class<?> metadata = objInst.getClass();
            Field attribute = metadata.getDeclaredField("ji");
            attribute.setAccessible(true);
            
            Object jiObject = attribute.get(objInst);
            Class<?> metadataJob = jiObject.getClass();
            Field id = metadataJob.getDeclaredField("id");
            id.setAccessible(true);

            long valueId = id.getLong(jiObject);
            ContextManager.createEntrySpan("" + valueId, null);
            ContextManager.activeSpan();
        } catch (Exception e) {
            logger.error("Erreur dans l'interception de l'instance", e);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst,
                               Method method,
                               Object[] allArguments,
                               Class<?>[] argumentsTypes,
                               Object ret,
                                MethodInvocationContext context) throws Throwable {
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst,
                                      Method method,
                                      Object[] allArguments,
                                      Class<?>[] argumentsTypes,
                                      Throwable t,
                                      MethodInvocationContext context) {
        AbstractSpan activeSpan = ContextManager.activeSpan();
        activeSpan.errorOccurred();
        activeSpan.log(t);
    }
}
