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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2.InstanceMethodsAroundInterceptorV2;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2.MethodInvocationContext;

public class RunningJobInstanceInterceptor implements InstanceMethodsAroundInterceptorV2 {

    public class FieldInfo {
    public Class<?> clazz;
    public Object object;

        public FieldInfo(Class<?> clazz, Object object) {
        this.clazz = clazz;
        this.object = object;
        }
    }

    private static final Map<String, ContextSnapshot> CONTEXT_SNAPSHOTS = new ConcurrentHashMap<>();

    @Override
    public void beforeMethod(EnhancedInstance objInst,
                             Method method,
                             Object[] allArguments,
                             Class<?>[] argumentsTypes,
                             MethodInvocationContext context) {
        try {
            FieldInfo ji = getClassAndObject(objInst, "ji");
            FieldInfo jd = getClassAndObject(ji.object, "jd");

            String jobId = getFieldValue(ji.object, "id");
            String parentId = getFieldValue(ji.object, "parentId");
            String applicationName = getFieldValue(jd.object, "applicationName");

            ContextManager.createLocalSpan(applicationName + " " + jobId);
            if (CONTEXT_SNAPSHOTS.get(parentId) != null && ContextManager.isActive()) {
                ContextManager.continued(CONTEXT_SNAPSHOTS.get(parentId));
            }
            CONTEXT_SNAPSHOTS.put(jobId, ContextManager.capture());
        } catch (Exception e) {
            dealException(e);
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

            Field field = ji.object.getClass().getDeclaredField("id");
            field.setAccessible(true);
            Long id = (Long) field.get(ji.object);

            Map<String, String> infoMap = new HashMap<>();
                infoMap.put("info", log(id, "stdout"));
                span.log(System.currentTimeMillis(), infoMap);

            String error = log(id, "stderr");
            if (error.length() >= 1) {
                Throwable errorThrowable = new Throwable(error);
                span.log(errorThrowable);
            }

        } catch (Exception e) {
            dealException(e);
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

    private String log(Long id, String logtype) {
        try {
            String file = String.format("%010d", id) + "." + logtype + ".log";
            Path path = Paths.get(System.getProperty("user.home"),
                            "jqm", "jqm-all", "jqm-service", "target",
                            "jqm-3.0.1-SNAPSHOT", "jqm-3.0.1-SNAPSHOT",
                                   "logs", file);
            return Files.readString(path);
        } catch (Exception e) {
            return null;
        }
    }

    private void dealException(Throwable throwable) {
        AbstractSpan span = ContextManager.activeSpan();
        span.log(throwable);
    }
}
