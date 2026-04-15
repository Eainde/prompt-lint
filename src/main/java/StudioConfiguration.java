
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.studio.LangGraphStudioServer;
import org.bsc.langgraph4j.studio.springboot.LangGraphStudioConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

import static com.db.clm.kyc.ai.constants.CsmStateKeys.LOCAL_KYC_ID;
import static com.db.clm.kyc.ai.constants.CsmStateKeys.PARTY_ID;
import static com.db.clm.kyc.ai.constants.CsmStateKeys.PROFILE_VERSION_ID;

/**
 * ┌─────────────────────────────────┬──────────────┬─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
 *   │              Role               │     CSM?     │                                                                     Reason                                                                      │
 *   ├─────────────────────────────────┼──────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
 *   │ Client Senior Manager           │ Yes          │ This IS the CSM designation itself                                                                                                              │
 *   ├─────────────────────────────────┼──────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
 *   │ Authorised Signatory            │ No (usually) │ Signatory-only without executive title gets -0.25 penalty (rule D1). Below 0.70 threshold unless combined with a governance role                │
 *   ├─────────────────────────────────┼──────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
 *   │ Account Authorised Signatory    │ No           │ Banking/account-level authority, not executive governing body role. Not a KOS governance role                                                   │
 *   ├─────────────────────────────────┼──────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
 *   │ Banking Signatory               │ No           │ Banking operational role, not executive authority. Fails FC1 (no governance role)                                                               │
 *   ├─────────────────────────────────┼──────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
 *   │ Management Body                 │ Yes          │ Executive governing body — directly matches KOS definition of CSM (executive powers + day-to-day authority). Would be NNP CSM if it's an entity │
 *   ├─────────────────────────────────┼──────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
 *   │ Authorised Dealer               │ No           │ Operational/commercial role, not executive governance. Fails FC1                                                                                │
 *   ├─────────────────────────────────┼──────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
 *   │ Foreign Investor Representative │ No           │ Investor/representative role — ownership-only or advisory. KOS negative guardrail excludes ownership/investor/sponsor roles                     │
 *   └─────────────────────────────────┴──────────────┴─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
 *
 */
@Configuration
@RequiredArgsConstructor
public class StudioConfiguration extends LangGraphStudioConfig {

    private static final Logger log = LoggerFactory.getLogger(StudioConfiguration.class);

    private final CompiledGraph<CsmState> csmExtractionGraph;
    private final BaseCheckpointSaver checkpointSaver;
    private final WorkflowEngine workflowEngine;

    @Override
    public Map<String, LangGraphStudioServer.Instance> instanceMap() {
        final CompileConfig compileConfig =
                CompileConfig.builder()
                        .checkpointSaver(checkpointSaver)
                        .build();

        var instance = LangGraphStudioServer.Instance.builder()
                .title("CSM Workflow")
                .graph(csmExtractionGraph.stateGraph())
                .compileConfig(compileConfig)
                .addInputStringArg(PARTY_ID.getValue())
                .addInputStringArg(PROFILE_VERSION_ID.getValue())
                .addInputStringArg(LOCAL_KYC_ID.getValue())
                .build();

        return Map.of("csmExtraction", instance);
    }

    /**
     * Overrides the default streaming servlet bean to inject WorkflowEngine
     * pre-processing (DB insert, audit logging, Langfuse trace init, etc.)
     * before the Studio executes the graph via streamSnapshots().
     */
    @Bean
    @Override
    public ServletRegistrationBean<LangGraphStudioServer.GraphStreamServlet> streamingServletBean() {

        var customServlet = new LangGraphStudioServer.GraphStreamServlet(instanceMap()) {

            private final ObjectMapper objectMapper = new ObjectMapper();

            @Override
            protected void doPost(HttpServletRequest req, HttpServletResponse resp)
                    throws ServletException, IOException {

                // 1. Buffer the request body (InputStream can only be read once)
                byte[] bodyBytes = req.getInputStream().readAllBytes();

                // 2. Parse input data for pre-processing
                Map<String, Object> inputData = objectMapper.readValue(
                        bodyBytes, new TypeReference<>() {});

                // 3. Extract instanceId from URL path: /stream/csmExtraction -> csmExtraction
                String beanName = extractInstanceId(req);
                String threadId = req.getParameter("thread");
                boolean isResume = Boolean.parseBoolean(req.getParameter("resume"));

                // 4. Run WorkflowEngine pre-processing (DB insert, audit, Langfuse, etc.)
                //    Only on initial execution, not on resume
                if (!isResume) {
                    try {
                        log.info("Studio: Running WorkflowEngine.setupWorkflow for bean={}, thread={}",
                                beanName, threadId);
                        workflowEngine.setupWorkflow(beanName, inputData);
                    } catch (Exception e) {
                        log.error("Studio: WorkflowEngine pre-processing failed for bean={}", beanName, e);
                        resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        resp.getWriter().write("Pre-processing failed: " + e.getMessage());
                        return;
                    }
                }

                // 5. Wrap the request so super.doPost() can re-read the body
                HttpServletRequest wrappedRequest = wrapRequestWithBufferedBody(req, bodyBytes);

                // 6. Delegate to original GraphStreamServlet.doPost()
                //    which compiles the graph and streams snapshots back as SSE
                super.doPost(wrappedRequest, resp);
            }

            private String extractInstanceId(HttpServletRequest req) {
                String pathInfo = req.getPathInfo();
                if (pathInfo != null && pathInfo.startsWith("/")) {
                    return pathInfo.substring(1);
                }
                throw new IllegalStateException("Cannot extract instanceId from path: " + pathInfo);
            }

            private HttpServletRequest wrapRequestWithBufferedBody(
                    HttpServletRequest original, byte[] bodyBytes) {

                return new HttpServletRequestWrapper(original) {
                    @Override
                    public ServletInputStream getInputStream() {
                        ByteArrayInputStream bais = new ByteArrayInputStream(bodyBytes);
                        return new ServletInputStream() {
                            @Override
                            public int read() {
                                return bais.read();
                            }

                            @Override
                            public boolean isFinished() {
                                return bais.available() == 0;
                            }

                            @Override
                            public boolean isReady() {
                                return true;
                            }

                            @Override
                            public void setReadListener(ReadListener listener) {
                                // no-op for sync processing
                            }
                        };
                    }
                };
            }
        };

        var bean = new ServletRegistrationBean<>(customServlet, "/stream/*");
        bean.setLoadOnStartup(1);
        return bean;
    }



    @PostMapping("/admin/recreate-bean")
    public List<String> recreateBeanDeep(@RequestParam String beanName) {
        DefaultListableBeanFactory factory =
                (DefaultListableBeanFactory) context.getBeanFactory();

        // 1. Collect the full dependency tree (bottom-up order)
        Set<String> toRecreate = new LinkedHashSet<>();
        collectDependencies(factory, beanName, toRecreate);

        log.info("Beans to recreate for [{}]: {}", beanName, toRecreate);

        // 2. Destroy all in collected order (leaves first)
        List<String> results = new ArrayList<>();
        for (String name : toRecreate) {
            try {
                factory.destroySingleton(name);
                results.add(name + ": destroyed");
            } catch (Exception e) {
                results.add(name + ": destroy failed - " + e.getMessage());
                log.error("Failed to destroy bean: {}", name, e);
            }
        }

        // 3. Re-create the top-level bean — Spring auto-creates all dependencies
        try {
            factory.getBean(beanName);
            results.add(beanName + ": fully recreated with all dependencies");
            log.info("Successfully recreated bean tree rooted at: {}", beanName);
        } catch (Exception e) {
            results.add(beanName + ": recreation failed - " + e.getMessage());
            log.error("Failed to recreate bean: {}", beanName, e);
        }

        return results;
    }

    private void collectDependencies(DefaultListableBeanFactory factory,
                                     String beanName,
                                     Set<String> collected) {
        if (!factory.containsBeanDefinition(beanName)) {
            return; // skip framework/third-party beans without definitions
        }

        // Get beans that this bean depends on
        String[] dependencies = factory.getDependenciesForBean(beanName);
        for (String dep : dependencies) {
            if (!collected.contains(dep) && factory.containsBeanDefinition(dep)) {
                collectDependencies(factory, dep, collected); // depth-first
            }
        }

        collected.add(beanName); // add after its dependencies (bottom-up)
    }
}