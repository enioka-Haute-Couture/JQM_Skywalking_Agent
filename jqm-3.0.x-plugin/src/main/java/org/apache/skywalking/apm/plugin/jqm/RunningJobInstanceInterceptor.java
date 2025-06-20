package org.apache.skywalking.apm.plugin.jqm;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2.InstanceMethodsAroundInterceptorV2;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2.MethodInvocationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RunningJobInstanceInterceptor implements InstanceMethodsAroundInterceptorV2 {

    private static final Map<String, ContextSnapshot> CONTEXT_SNAPSHOTS = new ConcurrentHashMap<>();

    @Override
    public void beforeMethod(EnhancedInstance objInst,
                             Method method,
                             Object[] allArguments,
                             Class<?>[] argumentsTypes,
                             MethodInvocationContext context) {
        
        Logger logger = LoggerFactory.getLogger(RunningJobInstanceInterceptor.class);

        try {
            Class<?> metadata = objInst.getClass();
            Field attribute = metadata.getDeclaredField("ji");
            attribute.setAccessible(true);
            
            Object jiObject = attribute.get(objInst);
            Class<?> metadataJob = jiObject.getClass();

            Field id = metadataJob.getDeclaredField("id");
            id.setAccessible(true);
            long jobId = id.getLong(jiObject);

            Field sessionIdField = jiObject.getClass().getDeclaredField("sessionID");
            sessionIdField.setAccessible(true);
            String sessionId = String.valueOf(sessionIdField.get(jiObject));

            AbstractSpan span;

            if (sessionId != null && !sessionId.isEmpty()) {
                //si session ID 
                ContextSnapshot snapshot = CONTEXT_SNAPSHOTS.get(sessionId);
                if (snapshot == null) {
                    //si tête
                    span = ContextManager.createLocalSpan("Head:" + jobId);
                    CONTEXT_SNAPSHOTS.put(sessionId, ContextManager.capture());
                } else {
                    //sinon
                    span = ContextManager.createLocalSpan("Next:" + jobId);
                    ContextManager.continued(snapshot);
                }
            } else {
                //Si pas de sessionID
                span = ContextManager.createLocalSpan("" + jobId);
            }
        } catch (Exception e) {
            logger.error("Erreur dans RunningJobInstanceInterceptor", e);
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
