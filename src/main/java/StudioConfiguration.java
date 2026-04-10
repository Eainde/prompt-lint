
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
}