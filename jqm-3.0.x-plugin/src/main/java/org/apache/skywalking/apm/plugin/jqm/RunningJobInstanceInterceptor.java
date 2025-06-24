package org.apache.skywalking.apm.plugin.jqm;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Properties;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2.InstanceMethodsAroundInterceptorV2;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2.MethodInvocationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;


public class RunningJobInstanceInterceptor implements InstanceMethodsAroundInterceptorV2 {

    private static final Map<String, ContextSnapshot> CONTEXT_SNAPSHOTS = new ConcurrentHashMap<>();
    private Logger logger = LoggerFactory.getLogger(RunningJobInstanceInterceptor.class);
    private Properties p =  new Properties();
    private WebTarget target;
    private Client client;

    @Override
    public void beforeMethod(EnhancedInstance objInst,
                             Method method,
                             Object[] allArguments,
                             Class<?>[] argumentsTypes,
                             MethodInvocationContext context) {
        

        try {
            Class<?> RunningJobInstanceClass = objInst.getClass();

            Field ji = RunningJobInstanceClass.getDeclaredField("ji");
            ji.setAccessible(true);
            Object jiObject = ji.get(objInst);
            Class<?> jiClass = jiObject.getClass();

            Field jd = jiClass.getDeclaredField("jd");
            jd.setAccessible(true);
            Object jdObject = jd.get(jiObject);
            Class<?> jdClass = jdObject.getClass();

            Field queue = jiClass.getDeclaredField("q");
            queue.setAccessible(true);
            Object queueObject = queue.get(jiObject);
            Class<?> queueClass = queueObject.getClass();

            String jobId = getFieldValue(jiObject, "id");
            String sessionId = getFieldValue(jiObject, "sessionID");
            String applicationName = getFieldValue(jdObject, "applicationName");

            AbstractSpan span;

            if (sessionId != null && !sessionId.isEmpty()) {
                //si session ID 
                ContextSnapshot snapshot = CONTEXT_SNAPSHOTS.get(sessionId);
                if (snapshot == null) {
                    //si tête
                    span = ContextManager.createLocalSpan(applicationName + " " + jobId);
                    CONTEXT_SNAPSHOTS.put(sessionId, ContextManager.capture());
                    ContextManager.continued(snapshot);
                } else {
                    //sinon
                    span = ContextManager.createLocalSpan(applicationName + " " + jobId);
                    ContextManager.continued(snapshot);
                }
            } else {
                //Si pas de sessionID
                span = ContextManager.createLocalSpan(applicationName + " " + jobId);
            }

            //span.tag(Tags.ofKey("Queue position"), getFieldValue(queueObject, "internalPosition"));

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
                              MethodInvocationContext context) {
        try {
            //get all the objects we need
            Class<?> RunningJobInstanceClass = objInst.getClass();

            Field ji = RunningJobInstanceClass.getDeclaredField("ji");
            ji.setAccessible(true);
            Object jiObject = ji.get(objInst);
            Class<?> jiClass = jiObject.getClass();

            Field node = jiClass.getDeclaredField("n");
            node.setAccessible(true);
            Object nodeObject = node.get(jiObject);
            Class<?> nodeClass = nodeObject.getClass();

            Field queue = jiClass.getDeclaredField("q");
            queue.setAccessible(true);
            Object queueObject = queue.get(jiObject);
            Class<?> queueClass = queueObject.getClass();

            Field jd = jiClass.getDeclaredField("jd");
            jd.setAccessible(true);
            Object jdObject = jd.get(jiObject);
            Class<?> jdClass = jdObject.getClass();

            //case for a enum
            Field resultStatusField = RunningJobInstanceClass.getDeclaredField("resultStatus");
            resultStatusField.setAccessible(true);
            Object resultStatusObject = resultStatusField.get(objInst);
            String QueueStatus = resultStatusObject != null ? resultStatusObject.toString() : null;

            Field applicationName = jdClass.getDeclaredField("applicationName");
            applicationName.setAccessible(true);
            String applicationNameValue = String.valueOf(applicationName.get(jdObject));

            //add Tags
            AbstractSpan span = ContextManager.activeSpan();
            span.tag(Tags.ofKey("Software"), getFieldValue(jiObject, "instanceApplication"));
            span.tag(Tags.ofKey("Module"), getFieldValue(jiObject, "instanceModule"));
            span.tag(Tags.ofKey("Application"), getFieldValue(jdObject, "applicationName"));
            span.tag(Tags.ofKey("Job id"), getFieldValue(jiObject, "id"));
            span.tag(Tags.ofKey("Priority"), getFieldValue(jiObject, "priority"));
            span.tag(Tags.ofKey("Parent"), getFieldValue(jiObject, "parentId"));
            span.tag(Tags.ofKey("Session id"), getFieldValue(jiObject, "sessionID"));
            span.tag(Tags.ofKey("Queue"), QueueStatus);
            span.tag(Tags.ofKey("Progress"), getFieldValue(jiObject, "progress"));
            span.tag(Tags.ofKey("User"), getFieldValue(jiObject, "userName"));
            span.tag(Tags.ofKey("User Email"), getFieldValue(jiObject, "email"));
            span.tag(Tags.ofKey("Keyword 1"), getFieldValue(jiObject, "instanceKeyword1"));
            span.tag(Tags.ofKey("Keyword 2"), getFieldValue(jiObject, "instanceKeyword2"));
            span.tag(Tags.ofKey("Keyword 3"), getFieldValue(jiObject, "instanceKeyword3"));
            span.tag(Tags.ofKey("Node"), getFieldValue(nodeObject, "name"));
            span.tag(Tags.ofKey("Queue name"), getFieldValue(queueObject, "name"));
            //TODO schedule 

            JerseyClient(p);

            /*List<Object> jobDefs = this.target.path("jd/" + getFieldValue(jdObject, "applicationName")).request().get(new GenericType<List<Object>>() {});
            for (Object jobDef:jobDefs) {
                logger.info("I pass here");
            }*/

            InputStream log = target.path("ji/" + getFieldValue(jiObject, "id") + "/stdout").request().get(InputStream.class);
            addLogStdOut(log);
            
            log = target.path("ji/" + getFieldValue(jiObject, "id") + "/stderr").request().get(InputStream.class);
            addLogStdErr(log);

        } catch (Exception e) {
            logger.error("Erreur dans RunningJobInstanceInterceptor", e);
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
        AbstractSpan activeSpan = ContextManager.activeSpan();
        activeSpan.errorOccurred();
        activeSpan.log(t);
    }

public String getFieldValue(Object fieldObject,
                              String fieldName) {
            try {
                Field field = fieldObject.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                return String.valueOf(field.get(fieldObject));
            } catch (Exception e) {
                logger.error("", e);
            }
            return null;
    }


    //Copy of the JerseyClient Class of JQM in order to be able to call the back and get the logs 
    public void JerseyClient(Properties p)
    {
        logger.trace("creating a new WS client");

        ///////////////////////////////////////
        // Property loading

        // Properties priority is: Given explicitly > Given as sys param > Given inside conf file.
        // They are therefore loaded in that reverse order.
        this.p = new Properties();

        // Configuration file
        InputStream fis = null;
        try
        {
            fis = this.getClass().getClassLoader().getResourceAsStream("jqm.properties");
            if (fis != null)
            {
                logger.info("Loading jqm properties file");
                this.p.load(fis);
            }
            else
            {
                logger.info("No jqm properties file for WS client found in class path");
            }
        }
        catch (Exception e)
        {
           logger.error("Erreur dans RunningJobInstanceInterceptor", e);
        }
        finally
        {
            try
            {
                fis.close();
            }
            catch (Exception e)
            {
                // Ignore.
            }
        }

        // System parameter (only for URL, not documented nor supported by the way)
        if (System.getProperty("com.enioka.jqm.ws.url") != null)
        {
            this.p.setProperty("com.enioka.jqm.ws.url", System.getProperty("com.enioka.jqm.ws.url"));
        }

        // Given explicitly
        this.p.putAll(p);

        // JAX-RS client builder comes from service loader (directly used by the jax-rs API)
        ClientBuilder bld = ClientBuilder.newBuilder();
        logger.info("Using JAX-RS client builder from service loader");

        ///////////////////////////////////////
        // SSL certificates
        if (this.p.containsKey("com.enioka.jqm.ws.truststoreFile"))
        {
            logger.info("A trustore was specified and will be loaded");
            KeyStore trust = null;
            InputStream trustIs = null;

            try
            {
                trust = KeyStore.getInstance(this.p.getProperty("com.enioka.jqm.ws.truststoreType", "JKS"));
            }
            catch (KeyStoreException e)
            {
                logger.error("Erreur dans RunningJobInstanceInterceptor", e);
            }

            try
            {
                trustIs = new FileInputStream(this.p.getProperty("com.enioka.jqm.ws.truststoreFile"));
            }
            catch (FileNotFoundException e)
            {
                logger.error("Erreur dans RunningJobInstanceInterceptor", e);
            }

            String trustp = this.p.getProperty("com.enioka.jqm.ws.truststorePass", null);
            try
            {
                trust.load(trustIs, (trustp == null ? null : trustp.toCharArray()));
            }
            catch (Exception e)
            {
                logger.error("Erreur dans RunningJobInstanceInterceptor", e);
            }
            finally
            {
                try
                {
                    trustIs.close();
                }
                catch (IOException e)
                {
                    // Nothing to do.
                }
            }

            bld.trustStore(trust);

            // Client certificate
            if (this.p.containsKey("com.enioka.jqm.ws.keystoreFile"))
            {
                logger.info("A keystore was specified and will be loaded");
                KeyStore keyStore = null;
                InputStream keyIs = null;

                try
                {
                    keyStore = KeyStore.getInstance(this.p.getProperty("com.enioka.jqm.ws.keystoreType", "JKS"));
                }
                catch (KeyStoreException e)
                {
                    logger.error("Erreur dans RunningJobInstanceInterceptor", e);
                }

                try
                {
                    keyIs = new FileInputStream(this.p.getProperty("com.enioka.jqm.ws.keystoreFile"));
                }
                catch (FileNotFoundException e)
                {
                    logger.error("Erreur dans RunningJobInstanceInterceptor", e);
                }

                String keyp = this.p.getProperty("com.enioka.jqm.ws.keystorePass", null);
                try
                {
                    keyStore.load(keyIs, (keyp == null ? null : keyp.toCharArray()));
                }
                catch (Exception e)
                {
                    logger.error("Erreur dans RunningJobInstanceInterceptor", e);
                }
                finally
                {
                    try
                    {
                        keyIs.close();
                    }
                    catch (IOException e)
                    {
                        // Nothing to do.
                    }
                }

                bld.keyStore(keyStore, keyp);
            }

            client = bld.build();
        }
        else
        {
            // No trust store given = no SSL allowed => default client
            client = bld.build();
        }
        ///////////////////////////////////////
        // Create client

        //Modification here on the Port value to put the proxy
        String url = this.p.getProperty("com.enioka.jqm.ws.url", "http://localhost:42849/ws/client");
        //End of modification

        logger.info("JAX-RS client was created. The following root service URL will be used: " + url);
        this.target = client.target(url);
    }

    public static void addLogStdOut(InputStream inputStream) throws IOException {
        AbstractSpan span = ContextManager.activeSpan();
        BufferedInputStream buffered = new BufferedInputStream(inputStream);
        buffered.mark(8192);

        int b = buffered.read();
        if (b != -1) {
            buffered.reset();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(buffered))) {
                StringBuilder exit = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    exit.append(line).append("\n");
                }
                Map<String, String> infoMap = new HashMap<>();
                infoMap.put("info", exit.toString());
                span.log(System.currentTimeMillis(), infoMap);
            }
        }
    }

    public static void addLogStdErr(InputStream inputStream) throws IOException {
        AbstractSpan span = ContextManager.activeSpan();
        BufferedInputStream buffered = new BufferedInputStream(inputStream);
        buffered.mark(8192);

        int b = buffered.read();
        if (b != -1) {
            buffered.reset();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(buffered))) {
                StringBuilder exit = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    exit.append(line).append("\n");
                }
                Throwable error = new Throwable(exit.toString());
                span.log(error);
            }
        }
    }

}

