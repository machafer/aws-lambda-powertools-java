package software.amazon.lambda.tracing.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.xray.AWSXRay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import software.amazon.lambda.handlers.PowerToolDisabled;
import software.amazon.lambda.handlers.PowerToolDisabledForStream;
import software.amazon.lambda.handlers.PowerTracerToolEnabled;
import software.amazon.lambda.handlers.PowerTracerToolEnabledForStream;
import software.amazon.lambda.handlers.PowerTracerToolEnabledForStreamWithNoMetaData;
import software.amazon.lambda.handlers.PowerTracerToolEnabledWithException;
import software.amazon.lambda.handlers.PowerTracerToolEnabledWithNoMetaData;
import software.amazon.lambda.internal.LambdaHandlerProcessor;

import static org.apache.commons.lang3.reflect.FieldUtils.writeStaticField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

class LambdaTracingAspectTest {
    private RequestHandler<Object, Object> requestHandler;
    private RequestStreamHandler streamHandler;

    @Mock
    private Context context;

    @BeforeEach
    void setUp() throws IllegalAccessException {
        initMocks(this);
        writeStaticField(LambdaHandlerProcessor.class, "IS_COLD_START", null, true);
        setupContext();
        requestHandler = new PowerTracerToolEnabled();
        streamHandler = new PowerTracerToolEnabledForStream();
        AWSXRay.beginSegment(LambdaTracingAspectTest.class.getName());
    }

    @AfterEach
    void tearDown() {
        AWSXRay.endSegment();
    }

    @Test
    void shouldCaptureTraces() {
        requestHandler.handleRequest(new Object(), context);

        assertThat(AWSXRay.getTraceEntity().getSubsegments())
                .hasSize(1)
                .allSatisfy(subsegment -> {
                    assertThat(subsegment.getAnnotations())
                            .hasSize(1)
                            .containsEntry("ColdStart", true);

                    assertThat(subsegment.getMetadata())
                            .hasSize(1)
                            .containsKey("lambdaHandler");
                });
    }

    @Test
    void shouldCaptureTracesWithExceptionMetaData() {
        requestHandler = new PowerTracerToolEnabledWithException();

        Throwable exception = catchThrowable(() -> requestHandler.handleRequest(new Object(), context));

        assertThat(AWSXRay.getTraceEntity().getSubsegments())
                .hasSize(1)
                .allSatisfy(subsegment -> {
                    assertThat(subsegment.getAnnotations())
                            .hasSize(1)
                            .containsEntry("ColdStart", true);

                    assertThat(subsegment.getMetadata())
                            .hasSize(1)
                            .containsKey("lambdaHandler");

                    assertThat(subsegment.getMetadata().get("lambdaHandler"))
                            .satisfies(stringObjectMap -> assertThat(stringObjectMap)
                                    .containsEntry("handleRequest error", exception));
                });
    }

    @Test
    void shouldCaptureTracesForStream() throws IOException {
        streamHandler.handleRequest(new ByteArrayInputStream("test".getBytes()), new ByteArrayOutputStream(), context);

        assertThat(AWSXRay.getTraceEntity().getSubsegments())
                .hasSize(1)
                .allSatisfy(subsegment -> {
                    assertThat(subsegment.getAnnotations())
                            .hasSize(1)
                            .containsEntry("ColdStart", true);

                    assertThat(subsegment.getMetadata())
                            .hasSize(1)
                            .containsKey("streamHandler");
                });
    }

    @Test
    void shouldNotCaptureTracesNotEnabled() throws IOException {
        requestHandler = new PowerToolDisabled();
        requestHandler.handleRequest(new Object(), context);

        assertThat(AWSXRay.getTraceEntity().getSubsegments())
                .isEmpty();

        streamHandler = new PowerToolDisabledForStream();
        streamHandler.handleRequest(new ByteArrayInputStream("test".getBytes()), new ByteArrayOutputStream(), context);

        assertThat(AWSXRay.getTraceEntity().getSubsegments())
                .isEmpty();
    }

    @Test
    void shouldCaptureTracesWithNoMetadata() {
        requestHandler = new PowerTracerToolEnabledWithNoMetaData();

        requestHandler.handleRequest(new Object(), context);

        assertThat(AWSXRay.getTraceEntity().getSubsegments())
                .hasSize(1)
                .allSatisfy(subsegment -> {
                    assertThat(subsegment.getAnnotations())
                            .hasSize(1)
                            .containsEntry("ColdStart", true);

                    assertThat(subsegment.getMetadata())
                            .isEmpty();
                });
    }

    @Test
    void shouldCaptureTracesForStreamWithNoMetadata() throws IOException {
        streamHandler = new PowerTracerToolEnabledForStreamWithNoMetaData();

        streamHandler.handleRequest(new ByteArrayInputStream("test".getBytes()), new ByteArrayOutputStream(), context);

        assertThat(AWSXRay.getTraceEntity().getSubsegments())
                .hasSize(1)
                .allSatisfy(subsegment -> {
                    assertThat(subsegment.getAnnotations())
                            .hasSize(1)
                            .containsEntry("ColdStart", true);

                    assertThat(subsegment.getMetadata())
                            .isEmpty();
                });
    }


    private void setupContext() {
        when(context.getFunctionName()).thenReturn("testFunction");
        when(context.getInvokedFunctionArn()).thenReturn("testArn");
        when(context.getFunctionVersion()).thenReturn("1");
        when(context.getMemoryLimitInMB()).thenReturn(10);
    }
}