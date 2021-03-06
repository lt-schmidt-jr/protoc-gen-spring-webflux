package com.example.demo;

import io.grpc.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.ResourceProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Bean
    ManagedChannel managedChannel(@Value("${grpc.port}") int port) {
        return ManagedChannelBuilder.forAddress("localhost", port)
                .usePlaintext()
                .build();
    }

    @Configuration
    class HandlerServerConfig {
        @Bean
        ExampleHandlers.EchoServiceHandler example(ManagedChannel channel) {
            EchoServiceGrpc.EchoServiceStub stub = EchoServiceGrpc.newStub(channel);
            return ExampleHandlers.EchoServiceHandler.newBuilder()
                    .setStub(stub)
                    .setIncludeHeaders(Collections.singletonList("my-header-2"))
                    .build();
        }

        @Bean
        RouterFunction<ServerResponse> routingServer(ExampleHandlers.EchoServiceHandler handler) {
            return RouterFunctions.route()
                    .add(handler.allRoutes())

                    // or manual router builder
//                    .add(ExampleHandlers.EchoServiceHandler.builder()
//                            .getEcho(handler::getEcho)
//                            .getEchoByContent(handler::getEchoByContent)
//                            .multiGetEcho(handler::multiGetEcho)
//                            .singleGetEcho(handler::singleGetEcho)
//                            .newEcho(handler::newEcho)
//                            .newEcho0(handler::newEcho0)
//                            .newEcho1(handler::newEcho1)
//                            .enumGetEcho(handler::enumGetEcho)
//                            .updateEcho(handler::updateEcho)
//                            .updateEcho0(handler::updateEcho0)
//                            .deleteEcho(handler::deleteEcho)
//                            .errorEcho(handler::errorEcho)
//                            .build())

                    // manual routing
                    .GET("/v1/EchoService/GetEcho", handler::getEcho)
                    .build();
        }
    }

    public static class HeaderInterceptor implements ServerInterceptor {
        public static final Context.Key<String> HEADER_2 = Context.key("my-header-2");
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            Metadata.Key<String> key = Metadata.Key.of("my-header-2", Metadata.ASCII_STRING_MARSHALLER);
            String authorization = headers.get(key);
            Context context = Context.current().withValue(HEADER_2, authorization);
            return Contexts.interceptCall(context, call, headers, next);
        }
    }

    @Bean
    @Order(-2)
    public ErrorWebExceptionHandler errorWebExceptionHandler(ErrorAttributes errorAttributes,
                                                             ResourceProperties resourceProperties,
                                                             ApplicationContext applicationContext,
                                                             ServerCodecConfigurer serverCodecConfigurer) {
        AbstractErrorWebExceptionHandler exceptionHandler
                = new AbstractErrorWebExceptionHandler(errorAttributes, resourceProperties, applicationContext) {

            @Override
            protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
                return RouterFunctions.route(RequestPredicates.all(), this::renderErrorResponse);
            }

            Mono<ServerResponse> renderErrorResponse(ServerRequest request) {
                Map<String, Object> errorPropertiesMap = getErrorAttributes(request, false);
                Throwable error = getError(request);
                HttpStatus status = (error instanceof StatusRuntimeException)
                        ? HttpStatus.valueOf(grpcCodeToHttpCode(error))
                        : httpStatus(errorPropertiesMap);

                return ServerResponse.status(status)
                        .contentType(MediaType.APPLICATION_JSON_UTF8)
                        .body(BodyInserters.fromObject(errorPropertiesMap));
            }

            int grpcCodeToHttpCode(Throwable error) {
                switch (((StatusRuntimeException) error).getStatus().getCode()) {
                    case CANCELLED:
                        return 499;
                    case UNKNOWN:
                        return 500;
                    case INVALID_ARGUMENT:
                        return 400;
                    case DEADLINE_EXCEEDED:
                        return 504;
                    case NOT_FOUND:
                        return 404;
                    case ALREADY_EXISTS:
                        return 409;
                    case PERMISSION_DENIED:
                        return 403;
                    case UNAUTHENTICATED:
                        return 401;
                    case RESOURCE_EXHAUSTED:
                        return 429;
                    case FAILED_PRECONDITION:
                        return 400;
                    case ABORTED:
                        return 409;
                    case OUT_OF_RANGE:
                        return 400;
                    case UNIMPLEMENTED:
                        return 501;
                    case INTERNAL:
                        return 500;
                    case UNAVAILABLE:
                        return 503;
                    case DATA_LOSS:
                        return 500;
                    default:
                        return 500;
                }
            }

            HttpStatus httpStatus(Map<String, Object> errorAttributes) {
                int statusCode = (int) errorAttributes.get("status");
                return HttpStatus.valueOf(statusCode);
            }
        };
        exceptionHandler.setMessageWriters(serverCodecConfigurer.getWriters());
        exceptionHandler.setMessageReaders(serverCodecConfigurer.getReaders());
        return exceptionHandler;
    }
}
