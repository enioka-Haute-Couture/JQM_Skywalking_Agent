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

public class RunningJobInstanceInterceptor implements InstanceMethodsAroundInterceptorV2 {

    public class FieldInfo {
    public Class<?> clazz;
    public Object object;

        public FieldInfo(Class<?> clazz, Object object) {
        this.clazz = clazz;
        this.object = object;
        }
    }

@Override
public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
                         Class<?>[] argumentsTypes, MethodInvocationContext context) {
    try {

        FieldInfo ji = getClassAndObject(objInst, "ji");
        FieldInfo jd = getClassAndObject(ji.object, "jd");

        String jobId = getFieldValue(ji.object, "id");
        String tmpSw8 = getFieldValue(ji.object, "sessionID");
        String applicationName = getFieldValue(jd.object, "applicationName");
        //String sw8 = getFieldValue(ji.object, "context");
        //System.out.println(sw8);
        Map<String, List<String>> map = new HashMap<>();
        map.put("sw8",  Arrays.asList(tmpSw8));
        ContextCarrier incomingCarrier = new ContextCarrier();
        CarrierItem next = incomingCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            List<String> values = map.get(next.getHeadKey());
            if (!CollectionUtil.isEmpty(values)) {
                next.setHeadValue(values.get(0));
            }
        }

        AbstractSpan entrySpan = ContextManager.createEntrySpan(applicationName + "/" + jobId, incomingCarrier);    
        entrySpan.setComponent(ComponentsDefine.JERSEY);
        entrySpan.setLayer(SpanLayer.RPC_FRAMEWORK);
        entrySpan.setOperationName(applicationName + " " + jobId);
        entrySpan.tag(Tags.ofKey("Id"),  "" + incomingCarrier.getSpanId());
        entrySpan.tag(Tags.ofKey("ParentEndpoint"), incomingCarrier.getParentEndpoint());
        entrySpan.tag(Tags.ofKey("getAddressUsedAtClient"), incomingCarrier.getAddressUsedAtClient());
        entrySpan.tag(Tags.ofKey("getParentServiceInstance"), incomingCarrier.getParentServiceInstance());
        entrySpan.tag(Tags.ofKey("getTraceId"), incomingCarrier.getTraceId());
        entrySpan.tag(Tags.ofKey("getTraceSegmentId"), incomingCarrier.getTraceSegmentId());
        entrySpan.tag(Tags.ofKey("getSpanId"), "" + incomingCarrier.getSpanId());

        int i = 0;
        next = incomingCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            i++;
            entrySpan.tag(Tags.ofKey("getHeadKey" + i), next.getHeadKey());
            entrySpan.tag(Tags.ofKey("getHeadValue" + i), next.getHeadValue());
        }

    } catch (Exception e) {
         e.printStackTrace();
    }
}

    @Override
    public Object afterMethod(EnhancedInstance objInst,
                              Method method,
                              Object[] allArguments,
                              Class<?>[] argumentsTypes,
                              Object ret,
                              MethodInvocationContext context) {
        try {
            //get all the objects we need
            Class<?> runningJobInstanceClass = objInst.getClass();
            FieldInfo ji = getClassAndObject(objInst, "ji");
            FieldInfo jd = getClassAndObject(ji.object, "jd");
            FieldInfo queue = getClassAndObject(ji.object, "q");
            FieldInfo node = getClassAndObject(ji.object, "q");

            //case for a enum
            Field resultStatusField = runningJobInstanceClass.getDeclaredField("resultStatus");
            resultStatusField.setAccessible(true);
            Object resultStatusObject = resultStatusField.get(objInst);
            String queueStatus = resultStatusObject != null ? resultStatusObject.toString() : null;

            Field applicationName = jd.clazz.getDeclaredField("applicationName");
            applicationName.setAccessible(true);

            //add Tags
            AbstractSpan span = ContextManager.activeSpan();
            span.tag(Tags.ofKey("Software"), getFieldValue(ji.object, "instanceApplication"));
            span.tag(Tags.ofKey("Module"), getFieldValue(ji.object, "instanceModule"));
            span.tag(Tags.ofKey("Application"), getFieldValue(jd.object, "applicationName"));
            span.tag(Tags.ofKey("Job id"), getFieldValue(ji.object, "id"));
            span.tag(Tags.ofKey("Priority"), getFieldValue(ji.object, "priority"));
            span.tag(Tags.ofKey("Parent"), getFieldValue(ji.object, "parentId"));
            span.tag(Tags.ofKey("Session id"), getFieldValue(ji.object, "sessionID"));
            span.tag(Tags.ofKey("Queue"), queueStatus);
            span.tag(Tags.ofKey("Progress"), getFieldValue(ji.object, "progress"));
            span.tag(Tags.ofKey("User"), getFieldValue(ji.object, "userName"));
            span.tag(Tags.ofKey("User Email"), getFieldValue(ji.object, "email"));
            span.tag(Tags.ofKey("Keyword 1"), getFieldValue(ji.object, "instanceKeyword1"));
            span.tag(Tags.ofKey("Keyword 2"), getFieldValue(ji.object, "instanceKeyword2"));
            span.tag(Tags.ofKey("Keyword 3"), getFieldValue(ji.object, "instanceKeyword3"));
            span.tag(Tags.ofKey("Node"), getFieldValue(node.object, "name"));
            span.tag(Tags.ofKey("Queue name"), getFieldValue(queue.object, "name"));
            span.tag(Tags.ofKey("description"), getFieldValue(jd.object, "description"));
            span.tag(Tags.ofKey("javaClassName"), getFieldValue(jd.object, "javaClassName"));
            span.tag(Tags.ofKey("pathType"), getFieldValue(jd.object, "pathType"));
            span.tag(Tags.ofKey("Queue name"), getFieldValue(jd.object, "jarPath"));
            span.tag(Tags.ofKey("Queue id"), getFieldValue(jd.object, "queue_id"));
            span.tag(Tags.ofKey("maxTimeRunning"), getFieldValue(jd.object, "maxTimeRunning"));
            span.tag(Tags.ofKey("javaOpts"), getFieldValue(jd.object, "javaOpts"));
            span.tag(Tags.ofKey("classLoader"), getFieldValue(jd.object, "classLoader"));

            HashMap<String, String> param = getFieldHashMap(ji.object, "prmCache");
            Integer tmp = 1;
            for (String i : param.keySet()) {
            span.tag(Tags.ofKey("Param" + tmp++), "name: " + i + " value: " + param.get(i));
            }

        } catch (Exception e) {
            System.out.println(e);
        }
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
        dealException(t);
    }

    private String getFieldValue(Object fieldObject,
                                String fieldName) {
                try {
                    Field field = fieldObject.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return String.valueOf(field.get(fieldObject));
                } catch (Exception e) {
                    dealException(e);
                }
                return null;
        }

    @SuppressWarnings("unchecked")
    private HashMap<String, String> getFieldHashMap(Object fieldObject, String fieldName) {
        if (fieldObject == null || fieldName == null) {
            return null;
        }
        try {
            Field field = fieldObject.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(fieldObject);
            if (value instanceof HashMap<?, ?>) {
                return (HashMap<String, String>) value;
            }
        } catch (Exception e) {
            dealException(e);
        }
        return null;
    }

    private FieldInfo getClassAndObject(Object parent, String fieldName) {
        try {
            Field field = parent.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);

            Object object = field.get(parent);
            Class<?> classInst = (object != null) ? object.getClass() : null;

            return new FieldInfo(classInst, object);
        } catch (Exception e) {
            dealException(e);
            return null;
        }
    }

    private void dealException(Throwable throwable) {
        AbstractSpan span = ContextManager.activeSpan();
        span.log(throwable);
        ContextManager.stopSpan(span);
    }
}
