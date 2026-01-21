package org.apache.skywalking.apm.plugin.jqm;

import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2.InstanceMethodsAroundInterceptorV2;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2.MethodInvocationContext;

import java.lang.reflect.Method;

public class JdbcClientInterceptor implements InstanceMethodsAroundInterceptorV2 {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInvocationContext context) {
        AbstractSpan exitSpan = null;

        try {
            if (allArguments.length == 0 || allArguments[0] == null) {
                return;
            }
            Object runRequest = allArguments[0];

            ContextCarrier contextCarrier = new ContextCarrier();
            exitSpan = ContextManager.createExitSpan("JobEnqueue", contextCarrier, "job-scheduler");


            CarrierItem item = contextCarrier.items();
            String sw8 = null;
            while (item.hasNext()) {
                item = item.next();
                if ("sw8".equals(item.getHeadKey())) {
                    sw8 = item.getHeadValue();
                    break;
                }
            }

            String currentTraceId = getTraceId(runRequest);


            if (sw8 != null && !sw8.isBlank()) {
                setTraceId(runRequest, sw8);
            }

        } catch (Exception e) {
            //Do nothing
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret,
                              MethodInvocationContext context) {
        if (ContextManager.isActive()) {
            ContextManager.stopSpan();
        }
        return ret;
    }


    private String getTraceId(Object runRequest) {
        try {
            Method getTraceIdMethod = runRequest.getClass().getMethod("getTraceId");
            return (String) getTraceIdMethod.invoke(runRequest);
        } catch (Exception e) {
            return null;
        }
    }

    private void setTraceId(Object runRequest, String traceId) {
        try {
            Method setTraceIdMethod = runRequest.getClass().getMethod("setTraceId", String.class);
            setTraceIdMethod.invoke(runRequest, traceId);
        } catch (Exception e) {
            // Do nothing
        }
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                                      Throwable t, MethodInvocationContext context) {
        dealException(t);
    }

    private void dealException(Throwable throwable) {
        AbstractSpan span = ContextManager.activeSpan();
        if (span != null) {
            span.log(throwable);
            ContextManager.stopSpan(span);
        }
    }
}
