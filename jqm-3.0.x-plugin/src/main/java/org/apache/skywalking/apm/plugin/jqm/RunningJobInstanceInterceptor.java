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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2.InstanceMethodsAroundInterceptorV2;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2.MethodInvocationContext;
import org.apache.skywalking.apm.agent.core.util.CollectionUtil;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

public class RunningJobInstanceInterceptor implements InstanceMethodsAroundInterceptorV2
{

    public class FieldInfo
    {
        public Class<?> clazz;
        public Object object;

        public FieldInfo(Class<?> clazz, Object object)
        {
            this.clazz = clazz;
            this.object = object;
        }
    }

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            MethodInvocationContext context)
    {
        try
        {

            FieldInfo ji = getClassAndObject(objInst, "ji");
            FieldInfo jd = getClassAndObject(ji.object, "jd");

            String jobId = getFieldValue(ji.object, "id");
            String applicationName = getFieldValue(jd.object, "applicationName");
            String sw8 = getFieldValue(ji.object, "traceId");

            Map<String, List<String>> map = new HashMap<>();
            map.put("sw8", Arrays.asList(sw8));
            ContextCarrier incomingCarrier = new ContextCarrier();
            CarrierItem next = incomingCarrier.items();
            while (next.hasNext())
            {
                next = next.next();
                List<String> values = map.get(next.getHeadKey());
                if (!CollectionUtil.isEmpty(values))
                {
                    next.setHeadValue(values.get(0));
                }
            }

            AbstractSpan entrySpan = ContextManager.createEntrySpan(applicationName + "/" + jobId, incomingCarrier);
            entrySpan.setComponent(ComponentsDefine.JERSEY);
            entrySpan.setLayer(SpanLayer.RPC_FRAMEWORK);
            entrySpan.setOperationName(applicationName + " " + jobId);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret,
            MethodInvocationContext context)
    {
        try
        {
            Class<?> runningJobInstanceClass = objInst.getClass();
            FieldInfo ji = getClassAndObject(objInst, "ji");
            FieldInfo jd = getClassAndObject(ji.object, "jd");
            FieldInfo node = getClassAndObject(ji.object, "q");

            Field resultStatusField = runningJobInstanceClass.getDeclaredField("resultStatus");
            resultStatusField.setAccessible(true);
            Object resultStatusObject = resultStatusField.get(objInst);
            String queueStatus = resultStatusObject != null ? resultStatusObject.toString() : null;
            
            AbstractSpan span = ContextManager.activeSpan();

            List<String> key = List.of("Software", "Module", "Job id", "Priority", "Parent", "Session id", "Progress", "User", "User Email", "Keyword 1", "Keyword 2", "Keyword 3");
            List<String> fieldName = List.of("instanceApplication", "instanceModule", "id", "priority", "parentId", "sessionID", "progress", "userName", "email", "instanceKeyword1", "instanceKeyword2", "instanceKeyword3");

            for (int i = 0; i < key.size(); i++) {
                span.tag(Tags.ofKey(key.get(i)), getFieldValue(ji.object, fieldName.get(i)));
            }

            key = List.of("description", "Java Class Name", "Application", "Path Type", "Queue name", "Queue id", "Max Time Running", "JavaOpts", "Class Loader");
            fieldName = List.of("description", "javaClassName", "applicationName", "pathType", "jarPath", "queue_id", "maxTimeRunning", "javaOpts", "classLoader");

            for (int i = 0; i < key.size(); i++) {
                span.tag(Tags.ofKey(key.get(i)), getFieldValue(jd.object, fieldName.get(i)));
            }
            span.tag(Tags.ofKey("Queue"), queueStatus);
            span.tag(Tags.ofKey("Node"), getFieldValue(node.object, "name"));

            HashMap<String, String> param = getFieldHashMap(ji.object, "prmCache");
            Integer tmp = 1;
            for (String i : param.keySet())
            {
                span.tag(Tags.ofKey("Param" + tmp++), "name: " + i + " value: " + param.get(i));
            }

        }
        catch (Exception e)
        {
            dealException(e, true);
        }
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            Throwable t, MethodInvocationContext context)
    {
        dealException(t, true);
    }

    private String getFieldValue(Object fieldObject, String fieldName)
    {
        try
        {
            Field field = fieldObject.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return String.valueOf(field.get(fieldObject));
        }
        catch (Exception e)
        {
            dealException(e, false);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private HashMap<String, String> getFieldHashMap(Object fieldObject, String fieldName)
    {
        if (fieldObject == null || fieldName == null)
        {
            return null;
        }
        try
        {
            Field field = fieldObject.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(fieldObject);
            if (value instanceof HashMap<?, ?>)
            {
                return (HashMap<String, String>) value;
            }
        }
        catch (Exception e)
        {
            dealException(e, true);
        }
        return null;
    }

    private FieldInfo getClassAndObject(Object parent, String fieldName)
    {
        try
        {
            Field field = parent.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);

            Object object = field.get(parent);
            Class<?> classInst = (object != null) ? object.getClass() : null;

            return new FieldInfo(classInst, object);
        }
        catch (Exception e)
        {
            dealException(e, true);
            return null;
        }
    }

    private void dealException(Throwable throwable, Boolean stop)
    {
        AbstractSpan span = ContextManager.activeSpan();
        span.log(throwable);
        if (stop) {
            ContextManager.stopSpan(span);
        }
    }
}
